package com.github.andreyasadchy.xtra.ui.following

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.paging.LoadStates
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.main.shell.FollowingSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Following page's behaviour (plan 10.2, interaction tests): segments, long-press menus, sheets, empty states. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h1600dp")
class FollowingInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()

    private fun show(
        loggedIn: Boolean = true,
        start: FollowingSegment = FollowingSegment.Live,
        emptyLive: Boolean = false,
        liveState: LoadStates? = null,
        offline: Boolean = false,
    ) {
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                var selected by remember { mutableStateOf(start) }
                FollowingScreen(
                    segments = listOf(FollowingSegment.Games, FollowingSegment.Live, FollowingSegment.Videos, FollowingSegment.Channels),
                    selected = selected,
                    onSelectSegment = { selected = it },
                    account = FollowingAccount(loggedIn, if (loggedIn) "mitchell" else null),
                    lists = if (liveState != null) FollowingFixtures.lists(streams = emptyList(), state = liveState)
                    else FollowingFixtures.lists(streams = if (emptyLive) emptyList() else FollowingFixtures.streams),
                    options = FollowingFixtures.options.copy(image = { null }),
                    videoSort = VideoSortChoice(),
                    channelSort = ChannelSortChoice(),
                    videoPositions = FollowingFixtures.positions,
                    bookmarkedVideoIds = emptySet(),
                    actions = actions,
                    offline = offline,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun segmentsFollowTheStoredOrderAndSwitch() {
        show()
        compose.onNodeWithText("Live").assertIsSelected()
        compose.onNodeWithText("Channels").performClick()
        compose.onNodeWithText("Channels").assertIsSelected()
        compose.onNodeWithText("Sora").assertIsDisplayed()
        compose.onNodeWithText("Games").performClick()
        compose.onNodeWithText("League of Legends").assertIsDisplayed()
    }

    @Test
    fun tapOnLiveCardOpensTheStream() {
        show()
        compose.onNodeWithText("Night build day 42: new map, new base").performClick()
        assertEquals(listOf("openStream:2"), actions.calls)
    }

    @Test
    fun tapOnLiveAvatarOpensTheStream() {
        show()
        compose.onNodeWithText("Live now · 4").assertIsDisplayed()
        // the avatar row comes first, above the cards
        compose.onAllNodesWithText("Mika_cn").onFirst().performClick()
        assertEquals(listOf("openStream:3"), actions.calls)
    }

    @Test
    fun longPressOnLiveCardOpensItsActions() {
        show()
        compose.onNodeWithText("Night build day 42: new map, new base").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Open channel").assertIsDisplayed()
        compose.onNodeWithText("Open Minecraft").assertIsDisplayed()
        compose.onNodeWithText("Record").assertIsDisplayed()
        compose.onNodeWithText("Share").assertIsDisplayed()
        compose.onNodeWithText("Unfollow").performClick()
        compose.waitForIdle()
        assertEquals(listOf("unfollow:c2"), actions.calls)
    }

    @Test
    fun emptyLiveShowsRecentlyLiveChannels() {
        show(emptyLive = true)
        compose.onNodeWithText("None of the channels you follow is live right now.").assertIsDisplayed()
        compose.onNodeWithText("Recently live").assertIsDisplayed()
        compose.onAllNodesWithText("夜航Nightowl(nightowl)").onFirst().performClick()
        assertEquals(listOf("openChannel:c1"), actions.calls)
    }

    @Test
    fun loggedOutEmptyStateOffersLogIn() {
        show(loggedIn = false, emptyLive = true)
        compose.onNode(hasText("Log in") and androidx.compose.ui.test.hasClickAction()).performClick()
        assertEquals(listOf("logIn"), actions.calls)
    }

    @Test
    fun failedLoadShowsRetry() {
        show(liveState = FollowingFixtures.error)
        compose.onNodeWithText("Retry", substring = true).assertIsDisplayed()
    }

    @Test
    fun emptyListWhileOfflineShowsTheConnectionError() {
        show(emptyLive = true, offline = true)
        compose.onNodeWithText("No Internet connection").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun accountButtonOpensSettingsAndLogOut() {
        show()
        compose.onNodeWithContentDescription("Account and settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Logged in as mitchell").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.waitForIdle()
        assertEquals(listOf("settings"), actions.calls)
    }

    @Test
    fun logOutAsksFirst() {
        show()
        compose.onNodeWithContentDescription("Account and settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Log out").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Log out of mitchell?").assertIsDisplayed()
        assertTrue(actions.calls.isEmpty())
        compose.onAllNodesWithText("Log out").onFirst().performClick()
        compose.waitForIdle()
        assertEquals(listOf("logOut"), actions.calls)
    }

    @Test
    fun searchButtonOpensSearch() {
        show()
        compose.onNodeWithContentDescription("Search").performClick()
        assertEquals(listOf("search"), actions.calls)
    }

    @Test
    fun channelSortSheetAppliesAndSaves() {
        show(start = FollowingSegment.Channels)
        compose.onNodeWithText("Last broadcast").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Alphabetically").performClick()
        compose.onNodeWithText("Ascending").performClick()
        compose.onNodeWithText("Save as default").performClick()
        compose.waitForIdle()
        assertEquals(listOf("channelSort:login:asc:true"), actions.calls)
    }

    @Test
    fun videoSortSheetApplies() {
        show(start = FollowingSegment.Videos)
        compose.onNodeWithText("Upload date").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("View count").performClick()
        compose.onNodeWithText("Highlight").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitForIdle()
        assertEquals(listOf("videoSort:views:highlight:false"), actions.calls)
    }

    @Test
    fun videoResumesFromItsPosition() {
        show(start = FollowingSegment.Videos)
        compose.onNodeWithText("昨晚冲分全程，最后一把翻盘").performClick()
        assertEquals(listOf("openVideo:101:${15150L * 620}"), actions.calls)
    }

    @Test
    fun longPressOnVideoOffersDownloadAndBookmark() {
        show(start = FollowingSegment.Videos)
        compose.onNodeWithText("昨晚冲分全程，最后一把翻盘").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Add bookmark").performClick()
        compose.waitForIdle()
        assertEquals(listOf("bookmark:101"), actions.calls)
    }

    @Test
    fun tapOnGameOpensIt() {
        show(start = FollowingSegment.Games)
        compose.onNodeWithText("无畏契约").performClick()
        assertEquals(listOf("openGame:无畏契约"), actions.calls)
    }

    @Test
    fun displayNameFollowsTheSetting() {
        assertEquals("夜航(nightowl)", displayName("夜航", "nightowl", "0"))
        assertEquals("夜航", displayName("夜航", "nightowl", "1"))
        assertEquals("nightowl", displayName("夜航", "nightowl", "2"))
        assertEquals("Sora", displayName("Sora", "sora", "0"))
        assertEquals("sora", displayName(null, "sora", "0"))
    }
}
