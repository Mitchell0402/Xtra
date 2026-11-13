package com.github.andreyasadchy.xtra.lab

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.testing.FormFactor
import com.github.andreyasadchy.xtra.testing.ScreenshotRules
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Sample of the screenshot test pattern every stage follows: one image per form factor. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LabHomeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val rules = ScreenshotRules()

    private fun capture(formFactor: FormFactor) {
        formFactor.apply()
        compose.setContent { LabHome() }
        compose.onRoot().captureRoboImage(ScreenshotRules.path("LabHome", formFactor))
    }

    @Test
    fun phonePortrait() = capture(FormFactor.PhonePortrait)

    @Test
    fun phoneLandscape() = capture(FormFactor.PhoneLandscape)

    @Test
    fun tabletLandscape() = capture(FormFactor.TabletLandscape)
}
