package com.github.andreyasadchy.xtra.lab

import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.chat.ChatAssets
import com.github.andreyasadchy.xtra.ui.chat.ChatLineBuilder
import com.github.andreyasadchy.xtra.ui.chat.ChatList
import com.github.andreyasadchy.xtra.ui.chat.ChatState
import com.github.andreyasadchy.xtra.ui.chat.ChatStrings
import com.github.andreyasadchy.xtra.ui.chat.ChatStyle
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Chat list stress test (U0 validation 1, U7 gate). Replays a recorded chat stream at a fixed rate into the app's
 * chat list ([ChatList] with lines from [ChatLineBuilder], the same code the chat page runs), so the :benchmark module
 * can measure it. Until U6 this screen compared the RecyclerView list ("legacy") with a prototype ("compose"); the
 * U6 benchmark APK still has those modes for side-by-side runs.
 *
 * adb shell am start -n <pkg>/com.github.andreyasadchy.xtra.lab.ChatLabActivity
 *     --es mode u7 --ei rate 30 --ei seconds 60 --ei limit 600 --ez animated true --es sample <file in tools/chat-probe/samples>
 *
 * The status line reads "loading", then "running n/total", then "DONE n" when the replay ends.
 */
class ChatLabActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private val state = ChatState()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.DarkTheme)
        super.onCreate(savedInstanceState)
        val rate = intent.getIntExtra("rate", 30)
        val seconds = intent.getIntExtra("seconds", 60)
        val limit = intent.getIntExtra("limit", 600)
        val animated = intent.getBooleanExtra("animated", true)
        val sample = intent.getStringExtra("sample") ?: assets.list("")!!.first { it.endsWith(".irc") }

        val root = FrameLayout(this)
        status = TextView(this).apply {
            text = "loading"
            setBackgroundColor(0xCC000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        val noActions = LabChatActions
        root.addView(ComposeView(this).apply { setContent { LagoonTheme { ChatList(state, noActions) } } }, FrameLayout.LayoutParams(-1, -1))
        root.addView(status, FrameLayout.LayoutParams(-2, -2))
        setContentView(root)

        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) { ChatLabData.load(this@ChatLabActivity, sample) }
            val style = ChatStyle(animated = animated, messageLimit = limit)
            state.style = style
            val builder = ChatLineBuilder(
                style, ChatStrings.from(this@ChatLabActivity),
                ChatAssets.of(emptyList(), data.thirdPartyEmotes, data.globalBadges, data.channelBadges, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap()),
                loggedInUser = null,
            )
            // message i is due at start + i / rate seconds; lines are built off the main thread like the chat page does
            val total = rate * seconds
            val start = SystemClock.uptimeMillis()
            val arrivals = ArrayDeque<Long>()
            for (i in 0 until total) {
                val due = start + i * 1000L / rate
                val wait = due - SystemClock.uptimeMillis()
                if (wait > 0) delay(wait)
                val message = data.messages[i % data.messages.size]
                val line = withContext(Dispatchers.Default) { builder.build(message) }
                state.lines.add(line)
                if (state.lines.size > limit) state.lines.removeRange(0, state.lines.size - limit)
                val now = SystemClock.uptimeMillis()
                arrivals.addLast(now)
                while (now - arrivals.first() > 1000) arrivals.removeFirst()
                state.fast = arrivals.size > 4
                if (i % rate == 0) status.text = "running $i/$total"
            }
            val took = SystemClock.uptimeMillis() - start
            status.text = "DONE $total"
            android.util.Log.i("ChatLab", "DONE mode=u7 messages=$total took=${took}ms (planned ${seconds * 1000}ms)")
            if (intent.getBooleanExtra("finish", false)) finish()
        }
    }
}

private object LabChatActions : com.github.andreyasadchy.xtra.ui.chat.ChatActions {
    override fun openUserCard(message: com.github.andreyasadchy.xtra.model.chat.ChatMessage) = Unit
    override fun send() = Unit
    override fun onInputChange(value: androidx.compose.ui.text.input.TextFieldValue) = Unit
    override fun deleteWord() = Unit
    override fun clearInput() = Unit
    override fun cancelReply() = Unit
    override fun toggleEmotePanel() = Unit
    override fun onInputFocused() = Unit
    override fun insert(text: String) = Unit
    override fun applySuggestion(s: com.github.andreyasadchy.xtra.ui.chat.Suggestion) = Unit
    override fun openPoints() = Unit
    override fun claimPoints() = Unit
    override fun openRaid() = Unit
    override fun closeRaid() = Unit
    override fun closePoll() = Unit
    override fun closePrediction() = Unit
    override fun closeSheet() = Unit
    override fun openSheet(sheet: com.github.andreyasadchy.xtra.ui.chat.ChatSheet) = Unit
}
