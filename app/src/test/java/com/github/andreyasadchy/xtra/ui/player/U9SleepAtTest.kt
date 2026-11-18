package com.github.andreyasadchy.xtra.ui.player

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.ui.design.components.TimeWheelLogic
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** U9 A1 ("at a set time" sheet page instead of Material's clock picker) and A2 (playlist tags page), plan gate 4. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h900dp")
class U9SleepAtTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingPlayerActions()

    private fun page(page: PlayerSheet, state: PlayerUiState, source: FakeSheetSource) {
        state.sheet = page
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                PlayerSheetPage(state.sheet ?: PlayerSheet.Settings, state, source, actions, PlayerLayout.Portrait) { state.sheet = null }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun twelveHourClockConversion() {
        assertEquals(12 to false, TimeWheelLogic.to12(0))
        assertEquals(12 to true, TimeWheelLogic.to12(12))
        assertEquals(11 to true, TimeWheelLogic.to12(23))
        assertEquals(0, TimeWheelLogic.to24(12, pm = false))
        assertEquals(12, TimeWheelLogic.to24(12, pm = true))
        assertEquals(13, TimeWheelLogic.to24(1, pm = true))
        for (h in 0..23) assertEquals(h, TimeWheelLogic.to12(h).let { TimeWheelLogic.to24(it.first, it.second) })
    }

    @Test
    fun minutesUntilCrossesMidnight() {
        assertEquals(20, TimeWheelLogic.minutesUntil(23 * 60 + 50, 10))
        assertEquals(0, TimeWheelLogic.minutesUntil(600, 600))
        assertEquals(1439, TimeWheelLogic.minutesUntil(601, 600))
        assertEquals(90, TimeWheelLogic.minutesUntil(22 * 60, 23 * 60 + 30))
    }

    @Test
    fun startsTheTimerForThePickedTime() {
        val source = FakeSheetSource().apply { now = 22 * 60; sleepAt = 23 * 60 + 30 }
        val state = U6Fixtures.live()
        page(PlayerSheet.SleepAt, state, source)
        compose.onNodeWithTag("sleepAtSummary").assertTextContains("1:30", substring = true)
        compose.onNodeWithTag("sleepAtStart").assertIsEnabled().performClick()
        assertEquals(listOf("sleepAt:1410"), actions.calls)
        assertNull(state.sheet)
    }

    @Test
    fun theCurrentMinuteCannotBeStarted() {
        val source = FakeSheetSource().apply { now = 600; sleepAt = 600 }
        page(PlayerSheet.SleepAt, U6Fixtures.live(), source)
        compose.onNodeWithText("Pick a later time").assertExists()
        compose.onNodeWithTag("sleepAtStart").assertIsNotEnabled()
        compose.onNodeWithTag("sleepAtStart").performClick()
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    fun wheelsAreAdjustableWithAScreenReader() {
        val source = FakeSheetSource().apply { now = 600; sleepAt = 600 }
        page(PlayerSheet.SleepAt, U6Fixtures.live(), source)
        val minutes = compose.onNodeWithTag("wheelMinutes", useUnmergedTree = true)
        val increase = minutes.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Increase" }
        compose.runOnUiThread { increase.action() }
        compose.waitForIdle()
        compose.onNodeWithTag("sleepAtStart").assertIsEnabled()
        compose.onNodeWithTag("sleepAtStart").performClick()
        assertEquals(listOf("sleepAt:601"), actions.calls)
    }

    @Test
    fun twelveHourModeShowsAmPmAndKeepsTheHour() {
        val source = FakeSheetSource().apply { is24 = false; now = 600; sleepAt = 13 * 60 + 5 }
        page(PlayerSheet.SleepAt, U6Fixtures.live(), source)
        compose.onNodeWithTag("wheelAmPm").assertExists()
        assertEquals("1", compose.onNodeWithTag("wheelHours", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        compose.onNodeWithText(java.text.DateFormatSymbols.getInstance().amPmStrings[0]).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("sleepAtStart").performClick()
        assertEquals(listOf("sleepAt:65"), actions.calls) // 1:05 AM
    }

    @Test
    fun sleepPageOpensTheTimePage() {
        val state = U6Fixtures.live()
        page(PlayerSheet.Sleep, state, FakeSheetSource())
        compose.onNodeWithText("At a set time").performClick()
        assertEquals(PlayerSheet.SleepAt, state.sheet)
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    fun playlistTagsCopy() {
        val state = U6Fixtures.live()
        page(PlayerSheet.PlaylistTags, state, FakeSheetSource())
        compose.onNodeWithTag("playlistTags").assertExists()
        compose.onNodeWithText("Copy to clipboard").performClick()
        assertEquals(listOf("copy"), actions.calls)
        assertNull(state.sheet)
    }
}
