package com.github.andreyasadchy.xtra.lab

import android.content.Context
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.chat.ChatUtils
import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull

/** A recorded chat stream (tools/chat-probe/samples) plus the emotes and badges it needs. */
class ChatLabData(
    val messages: List<ChatMessage>,
    val roomId: String?,
    val thirdPartyEmotes: List<Emote>,
    val globalBadges: List<TwitchBadge>,
    val channelBadges: List<TwitchBadge>,
) {
    companion object {
        private suspend fun <T> step(into: MutableList<T>, block: suspend () -> List<T>) {
            val start = System.currentTimeMillis()
            val result = runCatching { withTimeoutOrNull(10_000) { block() } }
            Log.i("ChatLab", "loaded ${result.getOrNull()?.size} in ${System.currentTimeMillis() - start} ms ${result.exceptionOrNull() ?: ""}")
            result.getOrNull()?.let { into += it }
        }

        fun readSample(context: Context, name: String): Pair<List<ChatMessage>, String?> {
            var roomId: String? = null
            val messages = context.assets.open(name).bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
                    val raw = line.substringAfter('\t')
                    val irc = ChatUtils.parseIRCMessage(raw)
                    if (roomId == null) roomId = irc.tags["room-id"]
                    if (irc.command == "PRIVMSG" || irc.command == "USERNOTICE") ChatUtils.parseChatMessage(irc) else null
                }.toList()
            }
            return messages to roomId
        }

        /** Loads the same emote and badge sets the chat screen loads, through the app's own repository. */
        suspend fun load(context: Context, sample: String): ChatLabData {
            val (messages, roomId) = readSample(context, sample)
            val repo = (context.applicationContext as XtraApp).xtraModule.playerRepository
            val net = "0"
            val helix = TwitchApiHelper.getHelixHeaders(context)
            val gql = TwitchApiHelper.getGQLHeaders(context, false)
            val emotes = mutableListOf<Emote>()
            step(emotes) { repo.loadSTVEmoteSet(repo.loadGlobalSTVEmoteSetResponse(net), true, true).second }
            step(emotes) { repo.loadGlobalBTTVEmotes(repo.loadGlobalBTTVEmotesResponse(net), true) }
            step(emotes) { repo.loadGlobalFFZEmotes(repo.loadGlobalFFZEmotesResponse(net), true) }
            if (roomId != null) {
                step(emotes) { repo.loadSTVUser(repo.loadSTVUserResponse(net, roomId), true).second.orEmpty() }
                step(emotes) { repo.loadBTTVEmotes(repo.loadBTTVEmotesResponse(net, roomId), true) }
                step(emotes) { repo.loadFFZEmotes(repo.loadFFZEmotesResponse(net, roomId), true) }
            }
            val global = mutableListOf<TwitchBadge>().also { l -> step(l) { repo.loadGlobalBadges(net, helix, gql, "4", false) } }
            val channel = mutableListOf<TwitchBadge>().also { l -> step(l) { repo.loadChannelBadges(net, helix, gql, roomId, null, "4", false) } }
            Log.i("ChatLab", "messages=${messages.size} emotes=${emotes.size} badges=${global.size}+${channel.size}")
            return ChatLabData(messages, roomId, emotes.sortedBy { it.source }, global, channel)
        }
    }
}
