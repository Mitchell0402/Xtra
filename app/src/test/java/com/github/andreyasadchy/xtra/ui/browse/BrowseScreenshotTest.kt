package com.github.andreyasadchy.xtra.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.testing.FormFactor
import com.github.andreyasadchy.xtra.testing.ScreenshotRules
import com.github.andreyasadchy.xtra.ui.common.StreamSorts
import com.github.andreyasadchy.xtra.ui.design.components.SheetSurface
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.following.FollowingFixtures
import com.github.andreyasadchy.xtra.ui.following.ShellFrame
import com.github.andreyasadchy.xtra.ui.following.testOptions
import com.github.andreyasadchy.xtra.ui.game.GameFilters
import com.github.andreyasadchy.xtra.ui.game.GameHeader
import com.github.andreyasadchy.xtra.ui.game.GameLists
import com.github.andreyasadchy.xtra.ui.game.GameScreen
import com.github.andreyasadchy.xtra.ui.game.GameSegment
import com.github.andreyasadchy.xtra.ui.games.GamesScreen
import com.github.andreyasadchy.xtra.ui.search.SearchGroup
import com.github.andreyasadchy.xtra.ui.search.SearchResults
import com.github.andreyasadchy.xtra.ui.search.SearchScreen
import com.github.andreyasadchy.xtra.ui.top.TopStreamsScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines of the U4 pages inside the shell (plan 10.2): the games grid, a game page, streams by tag,
 * search and the filter sheet, loading, empty, failed and with content, in phone portrait, phone landscape and
 * tablet landscape, plus phone portrait at 1.3x font size. Fixed clock, fixed pictures, reduced motion.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowseScreenshotTest(private val case: Case, private val variant: Variant) {

    enum class Case {
        GamesLoading, GamesEmpty, GamesError, Games,
        GameLoading, GameEmpty, GameError, GameLive, GameVideos, GameClips,
        TopStreams, SearchRecent, SearchResults, SearchEmpty, FilterSheet,
    }

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
        variant.formFactor.apply()
        RuntimeEnvironment.setFontScale(variant.fontScale)
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                ShellFrame(Modifier.testTag("page")) { bottom -> Page(case, bottom) }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("page").captureRoboImage(ScreenshotRules.path("Browse-${case.name}", variant.formFactor).let {
            if (variant.fontScale != 1f) it.replace(".png", "-Font130.png") else it
        })
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun params(): List<Array<Any>> = Case.entries.flatMap { c -> Variant.entries.map { v -> arrayOf<Any>(c, v) } }
    }
}

@Composable
private fun Page(case: BrowseScreenshotTest.Case, bottom: Dp) {
    val actions = RecordingBrowseActions()
    when (case) {
        BrowseScreenshotTest.Case.GamesLoading, BrowseScreenshotTest.Case.GamesEmpty, BrowseScreenshotTest.Case.GamesError, BrowseScreenshotTest.Case.Games -> {
            val games = when (case) {
                BrowseScreenshotTest.Case.GamesLoading -> FollowingFixtures.paged(emptyList<Game>(), FollowingFixtures.loading)
                BrowseScreenshotTest.Case.GamesEmpty -> FollowingFixtures.paged(emptyList<Game>())
                BrowseScreenshotTest.Case.GamesError -> FollowingFixtures.paged(emptyList<Game>(), FollowingFixtures.error)
                else -> FollowingFixtures.paged(BrowseFixtures.games)
            }
            GamesScreen(
                root = true,
                games = games,
                filter = if (case == BrowseScreenshotTest.Case.GamesEmpty) BrowseFilter(tags = listOf(FilterTag("t1", "IRL"), FilterTag("t9", "Cozy"))) else BrowseFilter(),
                options = testOptions,
                actions = actions,
                grid = rememberLazyGridState(),
                bottomPadding = bottom,
            )
        }
        BrowseScreenshotTest.Case.GameLoading, BrowseScreenshotTest.Case.GameEmpty, BrowseScreenshotTest.Case.GameError,
        BrowseScreenshotTest.Case.GameLive, BrowseScreenshotTest.Case.GameVideos, BrowseScreenshotTest.Case.GameClips -> {
            val header = if (case == BrowseScreenshotTest.Case.GameLoading) GameHeader(id = "g2", slug = null, name = "League of Legends", boxArt = BrowseFixtures.header.boxArt)
            else BrowseFixtures.header
            val streams = when (case) {
                BrowseScreenshotTest.Case.GameLoading -> null
                BrowseScreenshotTest.Case.GameEmpty -> FollowingFixtures.paged(emptyList<Stream>())
                BrowseScreenshotTest.Case.GameError -> FollowingFixtures.paged(emptyList<Stream>(), FollowingFixtures.error)
                else -> FollowingFixtures.paged(BrowseFixtures.streams)
            }
            val selected = when (case) {
                BrowseScreenshotTest.Case.GameVideos -> GameSegment.Videos
                BrowseScreenshotTest.Case.GameClips -> GameSegment.Clips
                else -> GameSegment.Live
            }
            GameScreen(
                header = header,
                segments = listOf(GameSegment.Videos, GameSegment.Live, GameSegment.Clips),
                selected = selected,
                onSelect = {},
                lists = GameLists(streams, FollowingFixtures.paged(BrowseFixtures.videos), FollowingFixtures.paged(BrowseFixtures.clips)),
                filters = GameFilters(
                    streams = if (case == BrowseScreenshotTest.Case.GameEmpty) BrowseFilter(sort = StreamSorts.RECENT, languages = listOf("ZH", "JA"), tags = listOf(FilterTag(null, "排位")))
                    else BrowseFilter(),
                    videos = BrowseFilter(), clips = BrowseFilter(),
                    streamsConfig = FilterSheetConfig(FilterKind.Streams, canRemember = true, canSaveFilters = true),
                    videosConfig = FilterSheetConfig(FilterKind.Videos, canRemember = true),
                    clipsConfig = FilterSheetConfig(FilterKind.Clips, canRemember = true),
                ),
                options = testOptions,
                compactStreams = false,
                actions = actions,
                following = if (case == BrowseScreenshotTest.Case.GameLoading) null else case == BrowseScreenshotTest.Case.GameVideos,
                videoPositions = FollowingFixtures.positions,
                bottomPadding = bottom,
            )
        }
        BrowseScreenshotTest.Case.TopStreams -> TopStreamsScreen(
            root = false,
            streams = FollowingFixtures.paged(BrowseFixtures.streams),
            filter = BrowseFilter(tags = listOf(FilterTag(null, "中文"))),
            options = testOptions,
            compactStreams = false,
            actions = actions,
            onApplyFilter = { _, _ -> },
            bottomPadding = bottom,
        )
        BrowseScreenshotTest.Case.SearchRecent, BrowseScreenshotTest.Case.SearchResults, BrowseScreenshotTest.Case.SearchEmpty -> SearchScreen(
            groups = listOf(SearchGroup.Channels, SearchGroup.Streams, SearchGroup.Games, SearchGroup.Videos),
            results = if (case == BrowseScreenshotTest.Case.SearchEmpty) {
                SearchResults(FollowingFixtures.paged(emptyList()), FollowingFixtures.paged(emptyList()), FollowingFixtures.paged(emptyList()), FollowingFixtures.paged(emptyList()))
            } else {
                SearchResults(
                    FollowingFixtures.paged(BrowseFixtures.users), FollowingFixtures.paged(BrowseFixtures.streams),
                    FollowingFixtures.paged(BrowseFixtures.games), FollowingFixtures.paged(BrowseFixtures.videos),
                )
            },
            recent = listOf("xqc", "英雄联盟", "just chatting", "A very long recent search that keeps going"),
            options = testOptions,
            compactStreams = false,
            actions = actions,
            initialQuery = when (case) {
                BrowseScreenshotTest.Case.SearchResults -> "night"
                BrowseScreenshotTest.Case.SearchEmpty -> "zzzz"
                else -> ""
            },
            autoFocus = false,
            bottomPadding = bottom,
        )
        BrowseScreenshotTest.Case.FilterSheet -> Box(Modifier.fillMaxSize().background(Lagoon.colors.page)) {
            SheetSurface(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                FilterSheetContent(
                    config = FilterSheetConfig(FilterKind.Streams, canRemember = true, remembered = true, canSaveFilters = true),
                    current = BrowseFilter(sort = StreamSorts.RECENT, languages = listOf("ZH"), tags = listOf(FilterTag(null, "排位"), FilterTag(null, "English"))),
                    onApply = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
    }
}
