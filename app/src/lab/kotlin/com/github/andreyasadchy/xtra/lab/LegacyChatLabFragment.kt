package com.github.andreyasadchy.xtra.lab

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.ui.chat.ChatAdapter
import com.google.android.material.color.MaterialColors

/** The current chat list (ChatAdapter + RecyclerView), configured with the app's default chat settings. */
class LegacyChatLabFragment : Fragment() {
    lateinit var data: ChatLabData
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ChatAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        recycler = RecyclerView(requireContext())
        return recycler
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val dm = resources.displayMetrics
        fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, dm).toInt()
        adapter = ChatAdapter(
            messages = messages, localTwitchEmotes = emptyList(), thirdPartyEmotes = data.thirdPartyEmotes,
            globalBadges = data.globalBadges, channelBadges = data.channelBadges, cheerEmotes = emptyList(), namePaints = emptyList(),
            stvBadges = emptyList(), personalEmoteSets = emptyMap(), stvUsers = emptyList(), enableTimestamps = false, timestampFormat = "0",
            firstMsgVisibility = 0, firstChatMsg = getString(R.string.chat_first), redeemedChatMsg = getString(R.string.redeemed),
            redeemedNoMsg = getString(R.string.user_redeemed), rewardChatMsg = getString(R.string.chat_reward),
            replyMessage = getString(R.string.replying_to_message), useRandomColors = true, useReadableColors = true, isLightTheme = false,
            nameDisplay = "0", useBoldNames = false, showNamePaints = true, showSTVBadges = true, showPersonalEmotes = true,
            showSystemMessageEmotes = true, chatUrl = null, getEmoteBytes = null, fragment = this,
            backgroundColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface),
            dialogBackgroundColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface),
            imageLibrary = "0", messageTextSize = 14f, emoteSize = dp(29.5f), badgeSize = dp(18.5f), gifSize = dp(79.5f),
            showGifMessages = true, emoteQuality = "4", animateGifs = true, enableOverlayEmotes = true,
            translateMessage = { _, _ -> }, showLanguageDownloadDialog = { _, _ -> }, channelId = data.roomId, loggedInUser = null,
            messageClickListener = null, replyClickListener = null, imageClickListener = null,
        )
        recycler.adapter = adapter
        recycler.itemAnimator = null
        recycler.layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
    }

    /** Same append / trim / scroll sequence as ChatFragment's newMessage collector. */
    fun add(message: ChatMessage, limit: Int) {
        messages.add(message)
        val lastIndex = messages.lastIndex
        adapter.notifyItemInserted(lastIndex)
        val removeCount = (messages.size - limit).coerceAtLeast(0)
        if (removeCount > 0) {
            repeat(removeCount) { messages.removeAt(0) }
            adapter.notifyItemRangeRemoved(0, removeCount)
        }
        recycler.scrollToPosition(lastIndex - removeCount)
    }
}
