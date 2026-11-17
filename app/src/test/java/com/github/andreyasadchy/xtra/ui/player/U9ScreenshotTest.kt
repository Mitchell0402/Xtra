package com.github.andreyasadchy.xtra.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.andreyasadchy.xtra.testing.FormFactor
import com.github.andreyasadchy.xtra.testing.ScreenshotRules
import com.github.andreyasadchy.xtra.ui.design.components.SheetSurface
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines of the player sheet pages added in U9 (plan gate 3): "at a set time" in 24-hour and 12-hour
 * mode and with the current minute picked (button disabled), and the debug playlist tags page; phone portrait,
 * phone landscape (short window: three wheel rows) and phone portrait at 1.3x font size.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class U9ScreenshotTest(private val case: Case, private val variant: Variant) {

    enum class Case { SleepAt24, SleepAt12, SleepAtNow, PlaylistTags }

    enum class Variant(val formFactor: FormFactor, val fontScale: Float) {
        PhonePortrait(FormFactor.PhonePortrait, 1f),
        PhoneLandscape(FormFactor.PhoneLandscape, 1f),
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
        compose.onNodeWithTag("page").captureRoboImage(ScreenshotRules.path("U9-${case.name}", variant.formFactor).let {
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
private fun Page(case: U9ScreenshotTest.Case) {
    val source = FakeSheetSource().apply {
        now = 22 * 60
        when (case) {
            U9ScreenshotTest.Case.SleepAt24 -> sleepAt = 23 * 60 + 30
            U9ScreenshotTest.Case.SleepAt12 -> { is24 = false; sleepAt = 6 * 60 + 45 }
            U9ScreenshotTest.Case.SleepAtNow -> sleepAt = 22 * 60
            U9ScreenshotTest.Case.PlaylistTags -> Unit
        }
    }
    val page = if (case == U9ScreenshotTest.Case.PlaylistTags) PlayerSheet.PlaylistTags else PlayerSheet.SleepAt
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SheetSurface(Modifier.fillMaxWidth()) {
            PlayerSheetPage(page, U6Fixtures.live(), source, RecordingPlayerActions(), PlayerLayout.Portrait, back = {})
        }
    }
}
