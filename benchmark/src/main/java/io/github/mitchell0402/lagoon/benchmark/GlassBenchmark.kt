package io.github.mitchell0402.lagoon.benchmark

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Glass stage G, plan section 7: the same benchmark build with glass forced on and off (launch extra `lagoon.glass`,
 * read only by the src/lab override), alternating run by run, at least 10 runs each; compare the medians.
 *
 * Blur numbers only count on a real device with API 32 or newer (the emulator renders in software). Run with
 *   gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.mitchell0402.lagoon.benchmark.GlassBenchmark
 *
 * Scrolling and the idle page use the glass lab (Following with 60 fixed live cards, the pill in its own root as in
 * the shell), so no account is needed; cold start uses the real start page.
 */
@RunWith(Parameterized::class)
class GlassBenchmark(private val run: Int, private val glass: String) {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private fun MacrobenchmarkScope.openLab() {
        pressHome()
        startActivityAndWait(
            Intent().setClassName(TARGET_PACKAGE, LAB).putExtra(EXTRA, glass)
        )
        device.wait(Until.hasObject(By.res("glassLab")), 10_000)
        Thread.sleep(1500)
    }

    /** Gate: jank below 5 % and at most 1 point above glass off. */
    @Test
    fun scrollFollowing() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.WARM,
        iterations = 1,
        setupBlock = { openLab() },
    ) {
        val x = device.displayWidth / 2
        val h = device.displayHeight
        repeat(4) { device.swipe(x, h * 3 / 4, x, h / 4, 12); device.waitForIdle() }
        repeat(4) { device.swipe(x, h / 4, x, h * 3 / 4, 12); device.waitForIdle() }
    }

    /**
     * Gate: the page standing still with the pill shown, GPU time per frame at most 1.5x glass off (or at most 1 ms
     * more). Not a FrameTimingMetric: with glass on the page is meant to render nothing at all while still (live dots
     * stop breathing under real glass, decision 4a), and the metric refuses a run without frames. Reads
     * `dumpsys gfxinfo` over 10 seconds instead and reports frames and GPU percentiles as instrumentation status.
     */
    @Test
    fun idleFollowing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        device.executeShellCommand("am start -W -n $TARGET_PACKAGE/$LAB --es $EXTRA $glass")
        device.wait(Until.hasObject(By.res("glassLab")), 10_000)
        Thread.sleep(4_000)
        device.executeShellCommand("dumpsys gfxinfo $TARGET_PACKAGE reset")
        Thread.sleep(10_000)
        val out = device.executeShellCommand("dumpsys gfxinfo $TARGET_PACKAGE")
        fun value(label: String) = Regex("$label: ([0-9.]+)").find(out)?.groupValues?.get(1) ?: "-"
        val result = Bundle().apply {
            putString("glass_idle", "run=$run glass=$glass frames=${value("Total frames rendered")} " +
                "gpu50=${value("50th gpu percentile")} gpu90=${value("90th gpu percentile")} " +
                "cpu50=${value("50th percentile")} cpu90=${value("90th percentile")}")
        }
        instrumentation.sendStatus(2, result)
        Log.i("GlassBenchmark", result.getString("glass_idle")!!)
    }

    /** Gate: sheet animation jank below 5 %. Opens and closes the account sheet. */
    @Test
    fun accountSheet() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.WARM,
        iterations = 1,
        setupBlock = { openLab() },
    ) {
        repeat(4) {
            device.findObject(By.res(ACCOUNT))?.click() ?: error("account button not found")
            Thread.sleep(900)
            device.pressBack()
            Thread.sleep(700)
        }
    }

    /** Gate: at most 5 % slower than glass off. The real app's start page. */
    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.COLD,
        iterations = 1,
    ) {
        pressHome()
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN).setClassName(TARGET_PACKAGE, MAIN).putExtra(EXTRA, glass)
        )
    }

    companion object {
        const val EXTRA = "lagoon.glass"
        const val LAB = "com.github.andreyasadchy.xtra.lab.glass.GlassLabActivity"
        const val MAIN = "com.github.andreyasadchy.xtra.ui.main.MainActivity"
        /** Test tag of the account button on the Following page (exposed as a resource id by the lab). */
        const val ACCOUNT = "followingAccount"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-glass-{1}")
        fun runs(): List<Array<Any>> {
            // runner argument glassRuns=N for quick experiments; the gate uses the default 10
            val n = InstrumentationRegistry.getArguments().getString("glassRuns")?.toIntOrNull() ?: 10
            return (1..n).flatMap { listOf(arrayOf<Any>(it, "on"), arrayOf<Any>(it, "off")) }
        }
    }
}
