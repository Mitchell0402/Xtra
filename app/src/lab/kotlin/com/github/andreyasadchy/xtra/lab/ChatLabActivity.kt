package com.github.andreyasadchy.xtra.lab

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.github.andreyasadchy.xtra.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Chat list stress test (U0 validation 1). Replays a recorded chat stream at a fixed rate into either the
 * current RecyclerView + ChatAdapter list ("legacy") or the Compose prototype ("compose"), so both can be
 * measured with the same input by the :benchmark module.
 *
 * adb shell am start -n <pkg>/com.github.andreyasadchy.xtra.lab.ChatLabActivity
 *     --es mode compose|legacy --ei rate 30 --ei seconds 60 --ei limit 600 --ez animated true --es sample <file in tools/chat-probe/samples>
 *
 * The status line reads "loading", then "running n/total", then "DONE n" when the replay ends.
 */
class ChatLabActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private var legacy: LegacyChatLabFragment? = null
    private val composeRows = mutableStateListOf<ComposeChatRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.DarkTheme)
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "compose"
        val rate = intent.getIntExtra("rate", 30)
        val seconds = intent.getIntExtra("seconds", 60)
        val limit = intent.getIntExtra("limit", 600)
        val animated = intent.getBooleanExtra("animated", true)
        val sample = intent.getStringExtra("sample") ?: assets.list("")!!.first { it.endsWith(".irc") }

        val root = FrameLayout(this)
        val container = FrameLayout(this).apply { id = View.generateViewId() }
        status = TextView(this).apply {
            text = "loading"
            setBackgroundColor(0xCC000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        root.addView(container, FrameLayout.LayoutParams(-1, -1))
        root.addView(status, FrameLayout.LayoutParams(-2, -2))
        setContentView(root)

        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) { ChatLabData.load(this@ChatLabActivity, sample) }
            val builder = ComposeChatRowBuilder(this@ChatLabActivity, data)
            if (mode == "legacy") {
                val f = LegacyChatLabFragment()
                f.data = data
                f.animated = animated
                supportFragmentManager.beginTransaction().replace(container.id, f).commitNow()
                legacy = f
            } else {
                container.addView(ComposeView(this@ChatLabActivity).apply { setContent { ComposeChatList(composeRows, animated) } })
            }
            // message i is due at start + i / rate seconds
            val total = rate * seconds
            val start = SystemClock.uptimeMillis()
            for (i in 0 until total) {
                val due = start + i * 1000L / rate
                val wait = due - SystemClock.uptimeMillis()
                if (wait > 0) delay(wait)
                val message = data.messages[i % data.messages.size]
                if (mode == "legacy") {
                    legacy?.add(message, limit)
                } else {
                    val row = withContext(Dispatchers.Default) { builder.build(message) }
                    composeRows.add(row)
                    if (composeRows.size > limit) composeRows.removeRange(0, composeRows.size - limit)
                }
                if (i % rate == 0) status.text = "running $i/$total"
            }
            val took = SystemClock.uptimeMillis() - start
            status.text = "DONE $total"
            android.util.Log.i("ChatLab", "DONE mode=$mode messages=$total took=${took}ms (planned ${seconds * 1000}ms)")
            if (intent.getBooleanExtra("finish", false)) finish()
        }
    }
}
