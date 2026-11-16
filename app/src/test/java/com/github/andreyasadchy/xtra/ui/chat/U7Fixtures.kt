package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.ui.text.input.TextFieldValue
import com.github.andreyasadchy.xtra.model.chat.Badge
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.Poll
import com.github.andreyasadchy.xtra.model.chat.Prediction
import com.github.andreyasadchy.xtra.model.chat.Reply
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import java.util.Random

/** Sample chats for the U7 tests: no network, pictures are placeholders. */
object U7Fixtures {
    val emotes = listOf(
        Emote("PepeHands", url1x = "https://example.com/pepehands", source = Emote.CHANNEL_STV),
        Emote("KEKW", url1x = "https://example.com/kekw", source = Emote.GLOBAL_BTTV),
        Emote("RainTime", url1x = "https://example.com/raintime", source = Emote.GLOBAL_STV, isOverlayEmote = true),
        Emote("monkaS", url1x = "https://example.com/monkas", source = Emote.GLOBAL_FFZ),
    )
    val badges = listOf(TwitchBadge("subscriber", "12", url1x = "https://example.com/sub", title = "1-Year Subscriber"))

    fun assets() = ChatAssets.of(emptyList(), emotes, badges, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())

    fun builder(style: ChatStyle = ChatStyle(), loggedIn: String? = "viewer") =
        ChatLineBuilder(style, ChatStrings.Plain, assets(), loggedInUser = loggedIn, random = Random(1))

    private val tidalReply = ChatMessage(type = ChatMessage.USER_MESSAGE, id = "3", userId = "13", userLogin = "tidal", userName = "Tidal", message = "@Riverbank it really was",
        color = "#8A2BE2", reply = Reply("1", "riverbank", "Riverbank", "that was close KEKW"))

    fun messages(): List<ChatMessage> = listOf(
        ChatMessage(type = ChatMessage.USER_MESSAGE, id = "1", userId = "11", userLogin = "riverbank", userName = "Riverbank", message = "that was close KEKW", color = "#1E90FF",
            badges = listOf(Badge("subscriber", "12"))),
        ChatMessage(type = ChatMessage.USER_MESSAGE, id = "2", userId = "12", userLogin = "kelp", userName = "Kelp", message = "PepeHands RainTime"),
        ChatMessage(type = ChatMessage.REPLY_MESSAGE, reply = tidalReply.reply, replyParent = tidalReply),
        tidalReply,
        ChatMessage(type = ChatMessage.USER_MESSAGE, id = "4", userId = "14", userLogin = "sandbar", userName = "Sandbar", message = "hey @viewer did you see that", color = "#FF4500"),
        ChatMessage(type = ChatMessage.USER_MESSAGE, id = "5", userId = "15", userLogin = "newfish", userName = "NewFish", message = "hello everyone, first time here", isFirst = true),
        ChatMessage(systemMsg = "Riverbank subscribed for 12 months!", msgId = "resub", userLogin = "riverbank", userName = "Riverbank", message = "love this channel", type = ChatMessage.USER_MESSAGE),
        ChatMessage(type = ChatMessage.USER_MESSAGE, id = "6", userId = "16", userLogin = "linkdrop", userName = "LinkDrop", message = "schedule at https://example.com/schedule monkaS", color = "#2E8B57"),
        ChatMessage(systemMsg = "Connected to lagoonfm"),
    )

    fun state(
        loggedIn: Boolean = true,
        points: ChannelPoints? = ChannelPoints(balance = 12480, name = "Pearls"),
        modes: RoomModes = RoomModes(followersMinutes = 600, slowSeconds = 30),
    ): ChatState = ChatState().apply {
        isLive = true
        messagingEnabled = loggedIn
        this.points = if (loggedIn) points else null
        this.modes = modes
        nameDisplay = "0"
        val b = builder()
        lines.addAll(messages().map { b.build(it) })
        emoteSections = EmoteSections.build(listOf("KEKW"), listOf(Emote("Kappa", url1x = "https://example.com/kappa"), Emote("LagoonWave", url1x = "https://example.com/wave")), setOf("LagoonWave"), emptyList(), emotes)
    }

    val poll = Poll("p", "Next map?", "ACTIVE", listOf(Poll.PollChoice("Mirage", 640), Poll.PollChoice("Inferno", 310), Poll.PollChoice("Nuke", 50)), 1000, 75_000)
    val prediction = Prediction("d", System.currentTimeMillis(), listOf(
        Prediction.PredictionOutcome("a", "We win", 182_400, 812),
        Prediction.PredictionOutcome("b", "We lose", 95_100, 402),
    ), 120, "LOCKED", "Will we win this round?", null)
}

/** Records what the chat page asked for. */
class RecordingChatActions(private val state: ChatState) : ChatActions, ChatSheetActions {
    val calls = mutableListOf<String>()
    override val sheetData = ChatSheetData()
    override fun openUserCard(message: ChatMessage) { calls += "card:${message.userLogin}"; state.sheet = ChatSheet.UserCard(message) }
    override fun send() { calls += "send:${state.input.text}" }
    override fun onInputChange(value: TextFieldValue) {
        state.input = value
        val token = ChatSuggestions.tokenAt(value)
        state.suggestions = if (token != null) ChatSuggestions.find(token.second, U7Fixtures.emotes + listOf(com.github.andreyasadchy.xtra.model.chat.Chatter("Riverbank"))) else emptyList()
    }
    override fun deleteWord() { state.input = TextFieldValue(ChatSuggestions.deleteWord(state.input.text)) }
    override fun clearInput() { state.input = TextFieldValue("") }
    override fun cancelReply() { state.reply = null }
    override fun toggleEmotePanel() { state.emotePanel = !state.emotePanel }
    override fun onInputFocused() { state.emotePanel = false }
    override fun insert(text: String) { calls += "insert:$text"; state.input = TextFieldValue(state.input.text + text + " ") }
    override fun applySuggestion(s: Suggestion) { state.input = ChatSuggestions.apply(state.input, s.text); state.suggestions = emptyList() }
    override fun openPoints() { calls += "points"; state.sheet = ChatSheet.Points }
    override fun claimPoints() { calls += "claim" }
    override fun openRaid() { calls += "raid" }
    override fun closeRaid() { state.raid = null }
    override fun closePoll() { state.poll = null }
    override fun closePrediction() { state.prediction = null }
    override fun closeSheet() { state.sheet = null }
    override fun openSheet(sheet: ChatSheet) { calls += "sheet:${sheet::class.simpleName}"; state.sheet = sheet }
    override fun reply(message: ChatMessage) { calls += "reply:${message.id}"; state.reply = ReplyTarget(message.id!!, message.userLogin, message.userName, message.message); state.sheet = null }
    override fun mention(message: ChatMessage) { calls += "mention:${message.userLogin}" }
    override fun copy(message: ChatMessage) { calls += "copy:${message.id}" }
    override fun copyToInput(message: ChatMessage) { calls += "repeat:${message.id}" }
    override fun copyFull(message: ChatMessage) { calls += "full:${message.id}" }
    override fun viewChannel(id: String?, login: String?, name: String?, image: String?) { calls += "channel:$login" }
    override fun setAutoClaim(on: Boolean) { calls += "auto:$on"; state.points = state.points?.copy(autoClaim = on) }
    override fun setNotifyPoints(on: Boolean) { calls += "notify:$on" }
    override val notifyPoints = false
    override val showFullMessage = false
}

@androidx.compose.runtime.Composable
fun ChatScreenInPlace(state: ChatState, actions: ChatActions) = ChatScreen(state, actions, sheets = false)
