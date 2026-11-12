package com.github.andreyasadchy.xtra.lab

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LabHomeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun labHome() {
        compose.setContent { LabHome() }
        compose.onRoot().captureRoboImage()
    }
}
