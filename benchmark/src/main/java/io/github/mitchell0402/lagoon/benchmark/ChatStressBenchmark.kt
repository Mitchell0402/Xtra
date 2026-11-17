package io.github.mitchell0402.lagoon.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * U0 validation 1 and the U7 chat gate (plan 10.4): a recorded chat stream replayed at 30 messages/s for 60 s into
 * the chat list, measured with frame metrics. Needs the lab screens (benchmark build).
 *
 * Modes: "u7" is the chat list the app ships since U7. Benchmark APKs up to U6 have "legacy" (RecyclerView +
 * ChatAdapter, v0.1) and "compose" (the U0 prototype) instead; pick them with the chatModes argument when the
 * installed target is such an APK.
 *
 * Runner arguments: chatModes (u7, comma-separated), chatRate (default 30), chatSeconds (60), chatLimit (600),
 * chatSample (xqc-20261112.irc), chatAnimated (true), chatIterations (3).
 */
@RunWith(Parameterized::class)
class ChatStressBenchmark(private val mode: String) {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private val args = InstrumentationRegistry.getArguments()
    private val rate = args.getString("chatRate")?.toInt() ?: 30
    private val seconds = args.getString("chatSeconds")?.toInt() ?: 60
    private val limit = args.getString("chatLimit")?.toInt() ?: 600
    private val sample = args.getString("chatSample") ?: "xqc-20261112.irc"
    private val animated = args.getString("chatAnimated")?.toBoolean() ?: true

    @Test
    fun chatReplay() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.WARM,
        iterations = args.getString("chatIterations")?.toInt() ?: 3,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait(Intent().apply {
            component = ComponentName(TARGET_PACKAGE, "com.github.andreyasadchy.xtra.lab.ChatLabActivity")
            putExtra("mode", mode)
            putExtra("rate", rate)
            putExtra("seconds", seconds)
            putExtra("limit", limit)
            putExtra("sample", sample)
            putExtra("animated", animated)
            putExtra("finish", true)
        })
        // the lab activity finishes itself when the replay is over
        check(device.wait(Until.gone(By.pkg(TARGET_PACKAGE).depth(0)), (seconds + 60) * 1000L)) { "replay did not finish" }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes() = (InstrumentationRegistry.getArguments().getString("chatModes") ?: "u7").split(',')
    }
}
