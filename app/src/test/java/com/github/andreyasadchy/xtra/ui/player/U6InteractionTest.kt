package com.github.andreyasadchy.xtra.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.following.FollowingFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The player page's behaviour (plan 10.2 interaction tests, U6): controls, gestures, mini player and the sheet. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h900dp")
class U6InteractionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val actions = RecordingPlayerActions()

    private fun show(state: PlayerUiState, options: PlayerOptions = PlayerOptions(loggedIn = true)) {
        compose.setContent { LagoonTheme(reducedMotion = true) { PlayerFrame(state, options, actions) } }
        compose.waitForIdle()
    }

    /** Shows the page [state] asks for (the list when it asks for none), as the sheet window would. */
    private fun sheet(page: PlayerSheet, state: PlayerUiState, layout: PlayerLayout = PlayerLayout.Portrait, back: () -> Unit = {}) {
        state.sheet = page
        compose.setContent {
            LagoonTheme(reducedMotion = true) { PlayerSheetPage(state.sheet ?: PlayerSheet.Settings, state, FakeSheetSource(), actions, layout, back) }
        }
        compose.waitForIdle()
    }

    @Test
    fun aTapShowsAndHidesTheControls() {
        val state = U6Fixtures.live(controls = false)
        show(state)
        compose.onNodeWithTag("playerVideo").performTouchInput { click(center) }
        compose.waitForIdle()
        assertTrue(state.controlsVisible)
        compose.onNodeWithTag("playerSettings").assertIsDisplayed()
        compose.onNodeWithTag("playerVideo").performTouchInput { click(Offset(width * 0.5f, height * 0.4f)) }
        compose.waitForIdle()
        assertFalse(state.controlsVisible)
    }

    @Test
    fun theSettingsButtonOpensTheSheet() {
        val state = U6Fixtures.live()
        show(state)
        compose.onNodeWithTag("playerSettings").performClick()
        compose.waitForIdle()
        assertEquals(PlayerSheet.Settings, state.sheet)
    }

    @Test
    fun minimizeFullscreenAndPlayPause() {
        val state = U6Fixtures.video()
        show(state)
        compose.onNodeWithContentDescription("Minimize").performClick()
        compose.onNodeWithTag("fullscreen").performClick()
        compose.onNodeWithTag("playPause").performClick()
        compose.onNodeWithContentDescription("10 s back").performClick()
        compose.onNodeWithContentDescription("10 s forward").performClick()
        assertEquals(listOf("minimize", "fullscreen", "playPause", "rewind", "forward"), actions.calls)
    }

    @Test
    fun doubleTapOnTheSidesSeeksAVideo() {
        val state = U6Fixtures.video().apply { controlsVisible = false; showPlayButton = false }
        show(state)
        compose.onNodeWithTag("playerVideo").performTouchInput { doubleClick(Offset(width * 0.9f, height * 0.5f)) }
        compose.onNodeWithTag("playerVideo").performTouchInput { doubleClick(Offset(width * 0.1f, height * 0.5f)) }
        compose.waitForIdle()
        assertEquals(listOf("forward", "rewind"), actions.calls)
    }

    @Test
    fun liveStreamsHaveNoTimeLineOrSeekButtons() {
        show(U6Fixtures.live())
        compose.onNodeWithTag("seekBar").assertDoesNotExist()
        compose.onNodeWithContentDescription("10 s back").assertDoesNotExist()
        // v0.1 player_pause off: no pause button on a playing live stream
        compose.onNodeWithTag("playPause").assertDoesNotExist()
        compose.onNodeWithText("Live").assertIsDisplayed()
    }

    @Test
    fun quickButtonsShowOnlyWhenPinned() {
        show(U6Fixtures.live(), PlayerOptions(loggedIn = true))
        compose.onNodeWithTag("quickSleepTimer").assertDoesNotExist()
    }

    @Test
    fun pinnedQuickButtonsWork() {
        show(U6Fixtures.live(), PlayerOptions(loggedIn = true, quick = setOf(QuickButton.SleepTimer, QuickButton.AudioOnly, QuickButton.Subtitles)))
        compose.onNodeWithTag("quickSleepTimer").performClick()
        compose.onNodeWithTag("quickAudioOnly").performClick()
        // no subtitles in this stream: no button
        compose.onNodeWithTag("quickSubtitles").assertDoesNotExist()
        assertEquals(listOf("quick:SleepTimer", "quick:AudioOnly"), actions.calls)
    }

    @Test
    fun theMiniPlayerOpensPausesAndCloses() {
        val state = U6Fixtures.live(mode = PlayerMode.Minimized, controls = false)
        show(state)
        compose.onNodeWithTag("miniPlayPause").performClick()
        compose.onNodeWithTag("miniClose").performClick()
        compose.onNodeWithTag("miniPlayer").performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        compose.waitForIdle()
        assertEquals(listOf("playPause", "dismiss", "maximize"), actions.calls)
    }

    @Test
    fun swipingTheMiniPlayerAwayClosesIt() {
        val state = U6Fixtures.live(mode = PlayerMode.Minimized, controls = false)
        show(state)
        compose.onNodeWithTag("miniPlayer").performTouchInput { swipeLeft(durationMillis = 100) }
        compose.waitForIdle()
        assertEquals(listOf("dismiss"), actions.calls)
    }

    @Test
    fun theInfoLineOpensTheChannelAndFollows() {
        val state = U6Fixtures.live(controls = false)
        show(state)
        compose.onNodeWithText("Follow").performClick()
        compose.onNodeWithText("Just Chatting ·", substring = true).performClick()
        compose.waitForIdle()
        // the line opened: the title shows
        compose.onNodeWithText(U6Fixtures.live().title!!).assertIsDisplayed()
        assertEquals(listOf("follow:true"), actions.calls)
    }

    @Test
    fun sheetQualityPageSelectsAQuality() {
        val state = U6Fixtures.live().apply { sheet = PlayerSheet.Settings }
        sheet(PlayerSheet.Settings, state)
        compose.onNodeWithTag("menuQuality").performClick()
        assertEquals(PlayerSheet.Quality, state.sheet)
        compose.waitForIdle()
        compose.onNodeWithText("720p60").performClick()
        assertEquals(listOf("quality:720p60"), actions.calls)
        assertNull(state.sheet)
    }

    @Test
    fun sheetToggleAndActionRows() {
        val state = U6Fixtures.live().apply { sheet = PlayerSheet.Settings }
        sheet(PlayerSheet.Settings, state)
        compose.onNodeWithTag("menuAudioOnly").performClick()
        assertEquals(PlayerSheet.Settings, state.sheet)
        compose.onNodeWithTag("menuDownload").performClick()
        assertNull(state.sheet)
        assertEquals(listOf("menu:AudioOnly", "menu:Download"), actions.calls)
    }

    @Test
    fun sheetShowsLiveRowsAndNotVideoRows() {
        val state = U6Fixtures.live()
        sheet(PlayerSheet.Settings, state)
        compose.onNodeWithTag("menuAdBlock").assertIsDisplayed()
        compose.onNodeWithTag("menuSpeed").assertDoesNotExist()
        compose.onNodeWithTag("menuViewerList").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("menuBookmark").assertDoesNotExist()
    }

    @Test
    fun speedAndSleepPages() {
        val state = U6Fixtures.video().apply { sheet = PlayerSheet.Speed }
        sheet(PlayerSheet.Speed, state)
        compose.onNodeWithText("2×").performClick()
        state.sheet = PlayerSheet.Sleep
        compose.waitForIdle()
        compose.onNodeWithText("45 min").performClick()
        compose.onNodeWithTag("sleepStart").performClick()
        assertEquals(listOf("speed:2.0", "sleep:45:false"), actions.calls)
    }

    @Test
    fun chaptersSeek() {
        val state = U6Fixtures.video()
        sheet(PlayerSheet.Chapters, state)
        compose.onNodeWithText("Just Chatting").performClick()
        assertEquals(listOf("chapter:3000000"), actions.calls)
    }

    @Test
    fun aSubPageGoesBackToTheList() {
        var back = 0
        sheet(PlayerSheet.Volume, U6Fixtures.live()) { back++ }
        compose.onNodeWithTag("sheetBack").performClick()
        assertEquals(1, back)
    }

    @Test
    fun landscapeSheetSwitchesTheChatLayout() {
        val state = U6Fixtures.live()
        sheet(PlayerSheet.Settings, state, PlayerLayout.Landscape)
        compose.onNodeWithTag("menuChatLayout").performScrollTo().performClick()
        assertEquals(listOf("overlay:true"), actions.calls)
    }

    @Test
    fun listenOnlyIsInTheLiveLongPressMenu() {
        val following = com.github.andreyasadchy.xtra.ui.following.RecordingActions()
        val stream = Stream(id = "s1", channelId = "c1", channelLogin = "nightowl", channelName = "Nightowl", viewerCount = 10)
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                com.github.andreyasadchy.xtra.ui.design.components.ActionMenu(
                    com.github.andreyasadchy.xtra.ui.following.streamActions(stream, FollowingFixtures.options, following),
                )
            }
        }
        compose.onNodeWithText("Listen only").performClick()
        assertEquals(listOf("listen:s1"), following.calls)
    }
}
