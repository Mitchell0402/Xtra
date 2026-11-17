package com.github.andreyasadchy.xtra.ui.design

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.components.LagoonToastOverlay
import com.github.andreyasadchy.xtra.ui.design.components.LagoonToasts
import com.github.andreyasadchy.xtra.ui.design.components.LocalLagoonToasts
import com.github.andreyasadchy.xtra.ui.design.components.ToastHost
import com.github.andreyasadchy.xtra.ui.design.components.lagoonToast
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** U9 B1 (plan gate 4): one toast queue per activity, drawn on top, inside an open sheet, system toast outside. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h900dp")
class U9ToastTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun toasts(overlay: Boolean = true) = LagoonToasts(compose.activity, hasOverlay = overlay)

    @Test
    fun aStartedActivityShowsALagoonToast() {
        val toasts = toasts()
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                CompositionLocalProvider(LocalLagoonToasts provides toasts) { LagoonToastOverlay(toasts, 0.dp) }
            }
        }
        compose.runOnUiThread { toasts.show("Now following Riverbank") }
        compose.waitForIdle()
        compose.onNodeWithText("Now following Riverbank").assertExists()
        assertNull("no system toast", ShadowToast.getLatestToast())
    }

    @Test
    fun withoutALagoonActivityTheSystemToastIsUsed() {
        // the test activity is no LagoonToastsOwner: a service-like caller falls back to the system toast
        compose.runOnUiThread { compose.activity.lagoonToast("Stream ended") }
        assertEquals("Stream ended", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun aPageHostStandsBackForTheOverlay() {
        val toasts = toasts()
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                CompositionLocalProvider(LocalLagoonToasts provides toasts) {
                    Box(Modifier.fillMaxSize()) {
                        ToastHost(toasts.state) // a page's own host
                        LagoonToastOverlay(toasts, 0.dp)
                    }
                }
            }
        }
        compose.runOnUiThread { toasts.show("Connection restored") }
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithText("Connection restored").fetchSemanticsNodes().size)
    }

    @Test
    fun anOpenSheetDrawsTheToast() {
        val toasts = toasts()
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                CompositionLocalProvider(LocalLagoonToasts provides toasts) {
                    Box(Modifier.fillMaxSize()) {
                        LagoonToastOverlay(toasts, 0.dp)
                        LagoonBottomSheet(onDismiss = {}) { Text("Download") }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertTrue(toasts.drawingSheet != null)
        compose.runOnUiThread { toasts.show("Invalid directory") }
        compose.waitForIdle()
        // drawn once, by the sheet (the overlay stands back while a sheet is open)
        assertEquals(1, compose.onAllNodesWithText("Invalid directory").fetchSemanticsNodes().size)
    }

    @Test
    fun touchesPassThroughTheOverlay() {
        val toasts = toasts()
        var taps by mutableStateOf(0)
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                CompositionLocalProvider(LocalLagoonToasts provides toasts) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(80.dp).align(Alignment.TopCenter).clickable { taps++ }) { Text("Under") }
                        LagoonToastOverlay(toasts, 0.dp)
                    }
                }
            }
        }
        compose.runOnUiThread { toasts.show("Timer canceled") }
        compose.waitForIdle()
        compose.onNodeWithText("Timer canceled").assertExists()
        compose.onNodeWithText("Under").performClick()
        assertEquals(1, taps)
    }
}
