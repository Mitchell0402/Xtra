package com.github.andreyasadchy.xtra.ui.game

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.ui.browse.BrowseFilter
import com.github.andreyasadchy.xtra.ui.browse.BrowseFixtures
import com.github.andreyasadchy.xtra.ui.browse.FilterKind
import com.github.andreyasadchy.xtra.ui.browse.FilterSheetConfig
import com.github.andreyasadchy.xtra.ui.browse.RecordingBrowseActions
import com.github.andreyasadchy.xtra.ui.design.components.HeaderCollapseRules
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.following.FollowingFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Motion plan M3a: the game page's segments are pages. A swipe moves between them and tells the page which one shows,
 * a tap on a tab still works, a swipe over a card never opens it, a swipe opens the next segment's list as soon as it
 * heads there, and the header collapses with any page's list. Plus the header's share of a scroll as pure rules.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h1600dp")
class GamePagerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val actions = RecordingBrowseActions()
    private val options = FollowingFixtures.options.copy(image = { null })
    private val visited = mutableListOf<GameSegment>()
    private var shown: GameSegment = GameSegment.Live

    private fun showGame(selected: GameSegment = GameSegment.Live) {
        shown = selected
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                var segment by remember { mutableStateOf(selected) }
                GameScreen(
                    header = BrowseFixtures.header,
                    segments = listOf(GameSegment.Videos, GameSegment.Live, GameSegment.Clips),
                    selected = segment,
                    onSelect = { segment = it; shown = it },
                    onVisit = { visited += it },
                    lists = GameLists(
                        streams = FollowingFixtures.paged(BrowseFixtures.streams),
                        videos = FollowingFixtures.paged(BrowseFixtures.videos),
                        clips = FollowingFixtures.paged(BrowseFixtures.clips),
                    ),
                    filters = GameFilters(
                        streams = BrowseFilter(), videos = BrowseFilter(), clips = BrowseFilter(),
                        streamsConfig = FilterSheetConfig(FilterKind.Streams),
                        videosConfig = FilterSheetConfig(FilterKind.Videos),
                        clipsConfig = FilterSheetConfig(FilterKind.Clips),
                    ),
                    options = options,
                    compactStreams = false,
                    actions = actions,
                    following = false,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aSwipeMovesToTheNextSegmentAndBack() {
        showGame()
        compose.onNodeWithTag(GamePagerTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(GameSegment.Clips, shown)
        compose.onNodeWithText("Clips").assertIsSelected()
        compose.onNodeWithTag(GamePagerTag).performTouchInput { swipeRight() }
        compose.onNodeWithTag(GamePagerTag).performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(GameSegment.Videos, shown)
    }

    @Test
    fun aSwipeBackToTheFirstSegmentSelectsItAgain() {
        showGame()
        compose.onNodeWithTag(GamePagerTag).performTouchInput { swipeLeft(startX = width * 0.85f, endX = width * 0.15f) }
        compose.waitForIdle()
        compose.onNodeWithTag(GamePagerTag).performTouchInput { swipeRight(startX = width * 0.15f, endX = width * 0.85f) }
        compose.waitForIdle()
        assertEquals(GameSegment.Live, shown)
        compose.onNodeWithText("Live").assertIsSelected()
    }

    @Test
    fun aTapOnATabStillSwitches() {
        showGame()
        compose.onNodeWithText("Videos").performClick()
        compose.waitForIdle()
        assertEquals(GameSegment.Videos, shown)
        compose.onNodeWithText("Videos").assertIsSelected()
    }

    @Test
    fun aSwipeOverACardDoesNotOpenIt() {
        showGame()
        compose.onNodeWithText("排位冲大师，今天不上分不下播").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertFalse(actions.calls.toString(), actions.calls.any { it.startsWith("openStream") })
        assertEquals(GameSegment.Clips, shown)
    }

    @Test
    fun aSwipeOpensTheNextSegmentsList() {
        showGame()
        compose.onNodeWithTag(GamePagerTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue(visited.toString(), GameSegment.Clips in visited)
    }

    @Test
    fun theHeaderCollapsesWithTheListAndStaysOnAPageSwitch() {
        showGame()
        val before = compose.onNodeWithTag(GameHeaderTag).getUnclippedBoundsInRoot().top
        compose.onNodeWithTag(GameListTag).performTouchInput { swipeUp(startY = bottom - 10f, endY = top + 10f) }
        compose.waitForIdle()
        val after = compose.onNodeWithTag(GameHeaderTag).getUnclippedBoundsInRoot().top
        assertTrue("header $before -> $after", after < before - 50.dp)
        // another page keeps the header where it is
        compose.onNodeWithText("Clips").performClick()
        compose.waitForIdle()
        assertEquals(after, compose.onNodeWithTag(GameHeaderTag).getUnclippedBoundsInRoot().top)
    }

    @Test
    fun theHeaderTakesTheScrollFirstOnTheWayUpAndLastOnTheWayDown() {
        // up 300 with 200 left to hide: the header takes 200
        assertEquals(-200f, HeaderCollapseRules.collapse(offset = -100f, dy = -300f, range = 300f), 0f)
        // already hidden: nothing
        assertEquals(0f, HeaderCollapseRules.collapse(offset = -300f, dy = -50f, range = 300f), 0f)
        // down what the list did not use: back up to fully shown
        assertEquals(100f, HeaderCollapseRules.expand(offset = -100f, dy = 250f), 0f)
        assertEquals(40f, HeaderCollapseRules.expand(offset = -100f, dy = 40f), 0f)
        assertEquals(0f, HeaderCollapseRules.expand(offset = 0f, dy = 40f), 0f)
    }
}
