package com.github.andreyasadchy.xtra.ui.browse

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.ui.common.StreamSorts
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.following.FollowingFixtures
import com.github.andreyasadchy.xtra.ui.game.GameFilters
import com.github.andreyasadchy.xtra.ui.game.GameLists
import com.github.andreyasadchy.xtra.ui.game.GameListTag
import com.github.andreyasadchy.xtra.ui.game.GameScreen
import com.github.andreyasadchy.xtra.ui.game.GameSegment
import com.github.andreyasadchy.xtra.ui.games.BrowseDetailHost
import com.github.andreyasadchy.xtra.ui.games.GameRef
import com.github.andreyasadchy.xtra.ui.games.GamesScreen
import com.github.andreyasadchy.xtra.ui.search.SearchFieldTag
import com.github.andreyasadchy.xtra.ui.search.SearchGroup
import com.github.andreyasadchy.xtra.ui.search.SearchResults
import com.github.andreyasadchy.xtra.ui.search.SearchScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The U4 pages' behaviour (plan 10.2 interaction tests): grid, game page, filter sheet, search, detail back. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h1600dp")
class BrowseInteractionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val actions = RecordingBrowseActions()
    private val options = FollowingFixtures.options.copy(image = { null })

    private fun showGrid(filter: BrowseFilter = BrowseFilter()) {
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                GamesScreen(
                    root = true,
                    games = FollowingFixtures.paged(BrowseFixtures.games),
                    filter = filter,
                    options = options,
                    actions = actions,
                    grid = rememberLazyGridState(),
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun gridOpensAGameAndNarrowsToAGameTag() {
        showGrid()
        compose.onNodeWithText("League of Legends").performClick()
        assertTrue("openGame:League of Legends" in actions.calls)
        compose.onNodeWithText("只是聊天").performTouchInput { longClick() }
        compose.onNodeWithText("Browse tag IRL").performClick()
        compose.waitForIdle()
        assertTrue(actions.calls.toString(), "gamesFilter:t1/IRL" in actions.calls)
    }

    @Test
    fun aChosenTagChipRemovesItsTag() {
        showGrid(BrowseFilter(tags = listOf(FilterTag("t1", "IRL"), FilterTag("t9", "Cozy"))))
        compose.onNodeWithText("Cozy").performClick()
        assertEquals("gamesFilter:t1/IRL", actions.calls.last())
    }

    private fun showGame(selected: GameSegment = GameSegment.Live, following: Boolean? = false, remembered: Boolean = false) {
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                var segment by remember { mutableStateOf(selected) }
                GameScreen(
                    header = BrowseFixtures.header,
                    segments = listOf(GameSegment.Videos, GameSegment.Live, GameSegment.Clips),
                    selected = segment,
                    onSelect = { segment = it },
                    lists = GameLists(
                        streams = FollowingFixtures.paged(BrowseFixtures.streams),
                        videos = FollowingFixtures.paged(BrowseFixtures.videos),
                        clips = FollowingFixtures.paged(BrowseFixtures.clips),
                    ),
                    filters = GameFilters(
                        streams = BrowseFilter(), videos = BrowseFilter(), clips = BrowseFilter(),
                        streamsConfig = FilterSheetConfig(FilterKind.Streams, canRemember = true, remembered = remembered, canSaveFilters = true),
                        videosConfig = FilterSheetConfig(FilterKind.Videos, canRemember = true, remembered = remembered),
                        clipsConfig = FilterSheetConfig(FilterKind.Clips, canRemember = true, remembered = remembered),
                    ),
                    options = options,
                    compactStreams = false,
                    actions = actions,
                    following = following,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun gamePageSwitchesSegmentsAndOpensItems() {
        showGame()
        compose.onNodeWithText("Live").assertIsSelected()
        compose.onNodeWithTag(GameListTag).performScrollToNode(hasText("排位冲大师，今天不上分不下播"))
        compose.onNodeWithText("排位冲大师，今天不上分不下播").performClick()
        assertTrue("openStream:1" in actions.calls)
        compose.onNodeWithText("Clips").performClick()
        compose.onNodeWithTag(GameListTag).performScrollToNode(hasText("最后一秒翻盘，全场沸腾"))
        compose.onNodeWithText("最后一秒翻盘，全场沸腾").performClick()
        assertTrue("openClip:c101" in actions.calls)
        compose.onNodeWithText("Videos").performClick()
        compose.onNodeWithTag(GameListTag).performScrollToNode(hasText("昨晚冲分全程，最后一把翻盘"))
        compose.onNodeWithText("昨晚冲分全程，最后一把翻盘").assertIsDisplayed()
    }

    @Test
    fun headerTagsOpenTheGamesWithThatTagAndFollowFollows() {
        showGame(following = false)
        compose.onNodeWithText("MOBA").performClick()
        assertTrue("gameTag:MOBA" in actions.calls)
        compose.onNodeWithText("Follow").performClick()
        assertTrue("follow:true" in actions.calls)
    }

    @Test
    fun unfollowingAsksFirst() {
        showGame(following = true)
        compose.onNodeWithText("Following").performClick()
        compose.onNodeWithText("Unfollow").performClick()
        compose.waitForIdle()
        assertTrue(actions.calls.toString(), "follow:false" in actions.calls)
    }

    @Test
    fun filterSheetAppliesTheSortAndRemembersItForTheGame() {
        showGame()
        compose.onNodeWithText("Viewers (descending)").performClick()
        compose.onNodeWithText("Recently started").performClick()
        compose.onNodeWithText("Remember choices for this game").performClick()
        compose.onNodeWithTag(FilterApplyTag).performClick()
        compose.waitForIdle()
        assertEquals(
            "filter:Streams:${StreamSorts.RECENT}:null:null:::remember=true,forget=false,default=false,filters=false",
            actions.calls.last(),
        )
    }

    @Test
    fun turningRememberOffForgetsTheGamesChoices() {
        showGame(selected = GameSegment.Clips, remembered = true)
        compose.onNodeWithText("This week").performClick()
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Remember choices for this game").performClick()
        compose.onNodeWithTag(FilterApplyTag).performClick()
        compose.waitForIdle()
        assertEquals("filter:Clips:null:day:null:::remember=false,forget=true,default=false,filters=false", actions.calls.last())
    }

    @Test
    fun theStreamMenuNarrowsTheListToATag() {
        showGame()
        compose.onNodeWithTag(GameListTag).performScrollToNode(hasText("排位冲大师，今天不上分不下播"))
        compose.onNodeWithText("排位冲大师，今天不上分不下播").performTouchInput { longClick() }
        compose.onNodeWithText("Browse tag 排位").performClick()
        compose.waitForIdle()
        assertEquals("filter:Streams:null:null:null::排位:remember=false,forget=false,default=false,filters=false", actions.calls.last())
    }

    private fun showSearch(recent: List<String> = listOf("xqc", "英雄联盟"), initialQuery: String = "") {
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                SearchScreen(
                    groups = listOf(SearchGroup.Channels, SearchGroup.Streams, SearchGroup.Games, SearchGroup.Videos),
                    results = SearchResults(
                        channels = FollowingFixtures.paged(BrowseFixtures.users),
                        streams = FollowingFixtures.paged(BrowseFixtures.streams),
                        games = FollowingFixtures.paged(BrowseFixtures.games),
                        videos = FollowingFixtures.paged(BrowseFixtures.videos),
                    ),
                    recent = recent,
                    options = options,
                    compactStreams = false,
                    actions = actions,
                    initialQuery = initialQuery,
                    autoFocus = false,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aRecentSearchFillsTheField() {
        showSearch()
        compose.onNodeWithText("xqc").performClick()
        // results follow the typing 300 ms after the last change
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        assertTrue("remember:xqc" in actions.calls)
        assertTrue(actions.calls.toString(), "query:xqc" in actions.calls)
    }

    @Test
    fun aRecentSearchCanBeRemoved() {
        showSearch()
        compose.onNodeWithContentDescription("Remove 英雄联盟 from recent searches").performClick()
        assertTrue("forget:英雄联盟" in actions.calls)
    }

    @Test
    fun typingSearchesEveryGroupAndOpeningAResultRemembersTheQuery() {
        showSearch()
        compose.onNodeWithTag(SearchFieldTag).performTextInput("night")
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        assertTrue(actions.calls.toString(), "query:night" in actions.calls)
        // grouped results: channels first, with "see all"
        compose.onAllNodesWithText("Channels").onFirst().assertIsDisplayed()
        // streams have more than the preview shows: "see all" switches the page to them
        compose.onAllNodesWithText("See all").onFirst().performClick()
        compose.onAllNodesWithText("Live").onFirst().assertIsSelected()
        // the group chips switch between groups
        compose.onAllNodesWithText("Channels").onFirst().performClick()
        compose.onNodeWithText("nightowl_clips").performClick()
        assertTrue("remember:night" in actions.calls)
        assertTrue("openChannel:c8" in actions.calls)
    }

    @Test
    fun userLookupOpensTheProfile() {
        showSearch()
        compose.onNodeWithContentDescription("Get user by ID/login").performClick()
        compose.onNodeWithText("User ID").performClick()
        compose.onAllNodesWithText("User ID").onFirst().assertIsDisplayed()
        compose.onNode(hasText("User ID").and(androidx.compose.ui.test.hasSetTextAction())).performTextInput("12345")
        compose.onNodeWithText("Look up").performClick()
        assertTrue(actions.calls.toString(), "lookUp:true:12345" in actions.calls)
        compose.onNodeWithText("View profile").performClick()
        assertTrue("openUser:true:12345" in actions.calls)
    }

    @Test
    fun backClosesTheGamePageOverTheGrid() {
        var closed = 0
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                var open by remember { mutableStateOf<GameRef?>(GameRef("g2", null, "League of Legends", null)) }
                BrowseDetailHost(
                    open = open,
                    onClose = { closed++; open = null },
                    onSettled = {},
                    backEnabled = true,
                    grid = { Text("grid") },
                    detail = { ref, _ -> Text("page ${ref.name}") },
                )
            }
        }
        compose.onNodeWithText("page League of Legends").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, closed)
        compose.onNodeWithText("grid").assertIsDisplayed()
    }

    @Test
    fun aClosedPageLeavesBackToTheActivity() {
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                BrowseDetailHost(open = null, onClose = {}, onSettled = {}, backEnabled = true, grid = { Text("grid") }, detail = { _, _ -> })
            }
        }
        compose.waitForIdle()
        assertEquals(false, compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun theMaximizedPlayerKeepsBack() {
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                BrowseDetailHost(open = GameRef("g", null, "G", null), onClose = {}, onSettled = {}, backEnabled = false, grid = {}, detail = { _, _ -> Text("page") })
            }
        }
        compose.waitForIdle()
        assertEquals(false, compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }
}
