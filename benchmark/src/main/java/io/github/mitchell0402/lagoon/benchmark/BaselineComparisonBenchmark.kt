package io.github.mitchell0402.lagoon.benchmark

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Plan 10.4: the kept v0.1 baseline build and this checkout's build, alternating run by run in one session, so the
 * emulator's drift hits both equally. Each parameter is one run of one package; compare the medians per package.
 *
 * Install both first: the v0.1 baseline (io.github.mitchell0402.lagoon.bench.v01, kept in E:\Projects\Lagoon-baseline)
 * and this checkout's benchmark build. Run with
 *   gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.mitchell0402.lagoon.benchmark.BaselineComparisonBenchmark
 */
@RunWith(Parameterized::class)
class BaselineComparisonBenchmark(private val run: Int, private val pkg: String) {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Cold start to the first frame of the start page. */
    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = pkg,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.COLD,
        iterations = 1,
    ) {
        pressHome()
        startActivityAndWait()
    }

    /** Opens the games directory (a link the app handles) and flings the grid down and up. */
    @Test
    fun scrollGames() = rule.measureRepeated(
        packageName = pkg,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.WARM,
        iterations = 1,
        setupBlock = {
            pressHome()
            startActivityAndWait(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.twitch.tv/directory")).setPackage(pkg))
            device.wait(Until.hasObject(By.res(pkg, "recyclerView")), 15_000)
            Thread.sleep(3000) // let the first page and its images arrive
        },
    ) {
        val list = device.findObject(By.res(pkg, "recyclerView")) ?: error("games list not found")
        list.setGestureMargin(device.displayWidth / 5)
        repeat(3) { list.fling(Direction.DOWN); device.waitForIdle() }
        repeat(3) { list.fling(Direction.UP); device.waitForIdle() }
    }

    companion object {
        const val BASELINE = "io.github.mitchell0402.lagoon.bench.v01"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}")
        fun runs() = (1..10).flatMap { listOf(arrayOf<Any>(it, BASELINE), arrayOf<Any>(it, TARGET_PACKAGE)) }
    }
}
