package com.github.andreyasadchy.xtra.lab.showcase

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.testing.FormFactor
import com.github.andreyasadchy.xtra.testing.ScreenshotRules
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The showcase page as a whole: floating pill on phones, rail on tablets (plan 4, three widths). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShowcaseScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val rules = ScreenshotRules()

    private fun capture(formFactor: FormFactor, tab: String? = null) {
        formFactor.apply()
        compose.setContent { LagoonTheme(reducedMotion = true) { ShowcaseApp() } }
        if (tab != null) compose.onNodeWithText(tab).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(ScreenshotRules.path("Showcase${tab?.let { "-Tokens" } ?: ""}", formFactor))
    }

    @Test
    fun phonePortrait() = capture(FormFactor.PhonePortrait)

    @Test
    fun phoneLandscape() = capture(FormFactor.PhoneLandscape)

    @Test
    fun tabletLandscape() = capture(FormFactor.TabletLandscape)

    @Test
    fun tokensTabletLandscape() = capture(FormFactor.TabletLandscape, tab = "参数")
}
