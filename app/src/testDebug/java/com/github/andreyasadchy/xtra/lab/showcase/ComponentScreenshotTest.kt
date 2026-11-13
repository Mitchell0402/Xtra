package com.github.andreyasadchy.xtra.lab.showcase

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.andreyasadchy.xtra.testing.FormFactor
import com.github.andreyasadchy.xtra.testing.ScreenshotRules
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines of the 14 components (plan 10.2 and the U1 gate in 10.6): every specimen sheet in the three
 * form factors, plus phone portrait at 1.3x font size. The sheets include very long Chinese and English titles.
 * Motion is reduced, so every image shows settled states.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentScreenshotTest(private val specimen: Specimen, private val variant: Variant) {

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
        variant.formFactor.apply(heightDp = 3000)
        RuntimeEnvironment.setFontScale(variant.fontScale)
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                SpecimenPage(specimen, Modifier.testTag("specimen"))
            }
        }
        compose.onNodeWithTag("specimen").captureRoboImage("src/testDebug/screenshots/Component-${specimen.name}-${variant.name}.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun params(): List<Array<Any>> = Specimen.entries.flatMap { s -> Variant.entries.map { v -> arrayOf<Any>(s, v) } }
    }
}
