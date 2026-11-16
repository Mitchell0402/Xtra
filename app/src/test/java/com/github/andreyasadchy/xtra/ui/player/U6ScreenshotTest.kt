package com.github.andreyasadchy.xtra.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.testing.FormFactor
import com.github.andreyasadchy.xtra.testing.ScreenshotRules
import com.github.andreyasadchy.xtra.ui.design.components.SheetSurface
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.following.ShellFrame
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines of the player page (plan 10.2, U6): a live stream with its controls, a paused video with the
 * time line, audio only, the mini player over a page, the settings sheet for a live stream and a video, the quality
 * and sleep timer pages, and the controls preview of the player buttons settings; in phone portrait, phone
 * landscape and tablet landscape, plus phone portrait at 1.3x font size. The video and the old chat are stand-ins
 * placed where the page puts its two plain views.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class U6ScreenshotTest(private val case: Case, private val variant: Variant) {

    enum class Case { Live, LiveNoControls, VideoPaused, AudioOnly, Mini, SheetLive, SheetVideo, Quality, Sleep, ButtonsPreview }

    enum class Variant(val formFactor: FormFactor, val fontScale: Float) {
        PhonePortrait(FormFactor.PhonePortrait, 1f),
        PhoneLandscape(FormFactor.PhoneLandscape, 1f),
        TabletLandscape(FormFactor.TabletLandscape, 1f),
        PhonePortraitFont130(FormFactor.PhonePortrait, 1.3f),
    }

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val rules = ScreenshotRules()

    @Test
    fun capture() {
        variant.formFactor.apply()
        RuntimeEnvironment.setFontScale(variant.fontScale)
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                Box(Modifier.fillMaxSize().background(Lagoon.colors.page).testTag("page")) { Page(case) }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("page").captureRoboImage(ScreenshotRules.path("U6-${case.name}", variant.formFactor).let {
            if (variant.fontScale != 1f) it.replace(".png", "-Font130.png") else it
        })
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun params(): List<Array<Any>> = Case.entries.flatMap { c -> Variant.entries.map { v -> arrayOf<Any>(c, v) } }
    }
}

@Composable
private fun Page(case: U6ScreenshotTest.Case) {
    val source = FakeSheetSource()
    val actions = RecordingPlayerActions()
    when (case) {
        U6ScreenshotTest.Case.Live -> PlayerFrame(U6Fixtures.live())
        U6ScreenshotTest.Case.LiveNoControls -> PlayerFrame(
            U6Fixtures.live(controls = false).apply { adStatus = "Ad blocked · clean stream" },
            options = PlayerOptions(loggedIn = true, quick = setOf(QuickButton.SleepTimer, QuickButton.Follow)),
        )
        U6ScreenshotTest.Case.VideoPaused -> PlayerFrame(U6Fixtures.video())
        U6ScreenshotTest.Case.AudioOnly -> PlayerFrame(
            U6Fixtures.live().apply { audioOnly = true; videoDisabled = true },
            options = PlayerOptions(loggedIn = true, quick = setOf(QuickButton.AudioOnly)),
        )
        U6ScreenshotTest.Case.Mini -> ShellFrame { bottom ->
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Following", style = Lagoon.type.display, color = Lagoon.colors.textPrimary, modifier = Modifier.padding(top = 48.dp))
            }
            val bottomPx = with(LocalDensity.current) { bottom.roundToPx() }
            PlayerFrame(U6Fixtures.live(mode = PlayerMode.Minimized, controls = false), bottomBarPx = bottomPx)
        }
        U6ScreenshotTest.Case.SheetLive, U6ScreenshotTest.Case.SheetVideo, U6ScreenshotTest.Case.Quality, U6ScreenshotTest.Case.Sleep -> {
            val state = if (case == U6ScreenshotTest.Case.SheetVideo) U6Fixtures.video() else U6Fixtures.live()
            val page = when (case) {
                U6ScreenshotTest.Case.Quality -> PlayerSheet.Quality
                U6ScreenshotTest.Case.Sleep -> PlayerSheet.Sleep
                else -> PlayerSheet.Settings
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                SheetSurface(Modifier.fillMaxWidth()) {
                    PlayerSheetPage(page, state, source, actions, PlayerLayout.Portrait, back = {})
                }
            }
        }
        U6ScreenshotTest.Case.ButtonsPreview -> Column(Modifier.padding(16.dp)) {
            PlayerControlsPreview(PlayerOptions(loggedIn = true, quick = setOf(QuickButton.Download, QuickButton.SleepTimer, QuickButton.AudioOnly)), now = { U6Fixtures.NOW })
        }
    }
}
