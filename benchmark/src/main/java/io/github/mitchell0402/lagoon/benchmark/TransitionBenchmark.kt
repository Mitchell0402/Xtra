package io.github.mitchell0402.lagoon.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.regex.Pattern

/**
 * Motion plan M0 item 3 (gate table 6.2): frame timing of the page and player transitions, the v0.2 build and this
 * checkout's build alternating run by run in one session (the cold-start fairness rule), at least 10 runs each;
 * compare jank percentage and frameOverrunMs P90 per package.
 *
 * The selectors only use what v0.2 already has, because v0.2 does not expose test tags as resource ids: the bottom
 * pill's labels (English or Chinese), twitch.tv links the app handles, the back key, and the player's SurfaceView.
 * Install both packages first: this checkout's benchmark build and the v0.2 one built from tag v0.2 with
 * `-Plagoon.perfId=v02` (io.github.mitchell0402.lagoon.bench.perf.v02). Run on the phone with
 *   gradlew :benchmark:connectedBenchmarkAndroidTest
 *     -Pandroid.testInstrumentationRunnerArguments.class=io.github.mitchell0402.lagoon.benchmark.TransitionBenchmark
 *     -Pandroid.testInstrumentationRunnerArguments.transitionPackages=<v0.2 package>,<this package>
 *     -Pandroid.testInstrumentationRunnerArguments.channel=<login>        (channel page in and out)
 *     -Pandroid.testInstrumentationRunnerArguments.videoId=<VOD id>       (player maximize and minimize)
 * Without `channel` or `videoId` those cases are skipped. Later stages add "open the player" (M2a) and
 * "page swipe" (M3, against the v0.1 ViewPager build).
 */
@RunWith(Parameterized::class)
class TransitionBenchmark(private val run: Int, private val pkg: String) {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private fun measure(setup: MacrobenchmarkScope.() -> Unit, block: MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = pkg,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.WARM,
        iterations = 1,
        setupBlock = setup,
        measureBlock = block,
    )

    private fun MacrobenchmarkScope.openStart() {
        pressHome()
        startMainActivity()
        device.wait(Until.hasObject(By.text(FOLLOWING)), 15_000)
        Thread.sleep(3_000) // first page and its images
    }

    /** The bottom pill's item with this label: the lowest match on screen (page titles use the same words). */
    private fun MacrobenchmarkScope.tab(label: Pattern): UiObject2 =
        device.findObjects(By.text(label)).maxByOrNull { it.visibleBounds.bottom } ?: error("tab $label not found")

    private fun MacrobenchmarkScope.openLink(url: String) {
        device.executeShellCommand("am start -a android.intent.action.VIEW -d $url $pkg")
    }

    /** Following -> Browse -> Me -> Following, twice: sibling transitions, the last one is the pop back to the start tab (N2). */
    @Test
    fun switchTabs() = measure({ openStart() }) {
        repeat(2) {
            for (label in listOf(BROWSE, MINE, FOLLOWING)) {
                tab(label).click()
                device.waitForIdle()
                Thread.sleep(SETTLE_MS)
            }
        }
    }

    /** Opens a channel page from the start tab (forward) and goes back (back), three times. */
    @Test
    fun channelInOut() {
        val channel = arg("channel")
        assumeTrue("pass -e channel <login>", channel != null)
        measure({ openStart() }) {
            repeat(3) {
                openLink("https://www.twitch.tv/$channel")
                Thread.sleep(PAGE_LOAD_MS)
                device.pressBack()
                device.waitForIdle()
                Thread.sleep(SETTLE_MS)
            }
        }
    }

    /** With a VOD playing maximized: back minimizes it, a tap on the mini video maximizes it again, three times. */
    @Test
    fun playerMaximizeMinimize() {
        val videoId = arg("videoId")
        assumeTrue("pass -e videoId <VOD id>", videoId != null)
        measure({
            openStart()
            openLink("https://www.twitch.tv/videos/$videoId")
            device.wait(Until.hasObject(By.clazz(SURFACE_VIEW)), 15_000)
            Thread.sleep(PLAYER_LOAD_MS)
        }) {
            repeat(3) {
                device.pressBack()
                device.waitForIdle()
                Thread.sleep(SETTLE_MS)
                (device.findObject(By.clazz(SURFACE_VIEW)) ?: error("mini player not found")).click()
                device.waitForIdle()
                Thread.sleep(SETTLE_MS)
            }
        }
    }

    companion object {
        private val FOLLOWING: Pattern = Pattern.compile("Following|关注")
        private val BROWSE: Pattern = Pattern.compile("Browse|浏览")
        private val MINE: Pattern = Pattern.compile("Me|我的")
        private const val SURFACE_VIEW = "android.view.SurfaceView"
        private const val SETTLE_MS = 700L
        private const val PAGE_LOAD_MS = 2_500L
        private const val PLAYER_LOAD_MS = 4_000L

        private fun arg(name: String): String? = InstrumentationRegistry.getArguments().getString(name)?.takeIf { it.isNotBlank() }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}")
        fun runs(): List<Array<Any>> {
            val packages = arg("transitionPackages")?.split(',')?.map { it.trim() } ?: listOf(TARGET_PACKAGE)
            val count = arg("transitionRuns")?.toInt() ?: 10
            return (1..count).flatMap { run -> packages.map { arrayOf<Any>(run, it) } }
        }
    }
}
