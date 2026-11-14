package com.github.andreyasadchy.xtra.ui.following

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.testing.FormFactor
import com.github.andreyasadchy.xtra.testing.ScreenshotRules
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.design.theme.WidthClass
import com.github.andreyasadchy.xtra.ui.main.shell.FollowingSegment
import com.github.andreyasadchy.xtra.ui.main.shell.ShellBottomBar
import com.github.andreyasadchy.xtra.ui.main.shell.ShellRail
import com.github.andreyasadchy.xtra.ui.main.shell.ShellState
import com.github.andreyasadchy.xtra.ui.main.shell.ShellTab
import com.github.andreyasadchy.xtra.ui.main.shell.ShellTabs
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines of the Following page inside the shell (plan 10.2; U2): the live segment loading, empty,
 * failed and with content, and the other three segments with content, in phone portrait, phone landscape and tablet
 * landscape, plus phone portrait at 1.3x font size. Fixed clock, no network images, reduced motion.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FollowingScreenshotTest(private val case: Case, private val variant: Variant) {

    enum class Case { LiveLoading, LiveEmpty, LiveEmptyLoggedOut, LiveError, Live, Videos, Channels, Games }

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
                ShellFrame(Modifier.testTag("page")) { bottom ->
                    Page(case, bottom)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("page").captureRoboImage(ScreenshotRules.path("Following-${case.name}", variant.formFactor).let {
            if (variant.fontScale != 1f) it.replace(".png", "-Font130.png") else it
        })
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun params(): List<Array<Any>> = Case.entries.flatMap { c -> Variant.entries.map { v -> arrayOf<Any>(c, v) } }
    }
}

private val tabs = listOf(
    ShellTab(ShellTabs.KEY_FOLLOWING, R.id.followPagerFragment),
    ShellTab(ShellTabs.KEY_BROWSE, R.id.rootGamesFragment),
    ShellTab(ShellTabs.KEY_MINE, R.id.savedPagerFragment),
)

/** The page as MainActivity shows it: the floating pill over the content, or the rail beside it on wide windows. */
@Composable
internal fun ShellFrame(modifier: Modifier = Modifier, content: @Composable (bottomPadding: androidx.compose.ui.unit.Dp) -> Unit) {
    val state = ShellState(tabs).apply { legacyPage = false }
    val wide = WidthClass.of(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp) == WidthClass.Expanded
    if (wide) {
        Row(modifier.fillMaxSize().background(Lagoon.colors.page)) {
            ShellRail(state, onSelect = {})
            Box(Modifier.weight(1f)) { content(16.dp) }
        }
    } else {
        Box(modifier.fillMaxSize()) {
            content(96.dp)
            ShellBottomBar(state, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

private class Cover(seed: Int) : Painter() {
    private val palette = listOf(
        Triple(Color(0xFF1E1B2E), Color(0xFF3B3366), Color(0xFF2A2545)),
        Triple(Color(0xFF13262A), Color(0xFF1F4A4F), Color(0xFF183338)),
        Triple(Color(0xFF1A2033), Color(0xFF34406A), Color(0xFF232B45)),
        Triple(Color(0xFF2B1A2A), Color(0xFF5C2E5A), Color(0xFF3A2238)),
    )[Math.floorMod(seed, 4)]
    override val intrinsicSize: Size = Size.Unspecified
    override fun DrawScope.onDraw() {
        drawRect(palette.first)
        drawCircle(palette.second, radius = size.minDimension * 0.55f, center = Offset(size.width * 0.68f, size.height * 0.12f))
        drawRect(palette.third, topLeft = Offset(0f, size.height * 0.64f), size = Size(size.width, size.height * 0.36f))
    }
}

/** Thumbnails and box art become fixed pictures; avatars have no URL in the fixtures and show their initial. */
internal val testOptions = FollowingFixtures.options.copy(image = { url -> url?.let { Cover(it.hashCode()) } })

@Composable
private fun Page(case: FollowingScreenshotTest.Case, bottomPadding: androidx.compose.ui.unit.Dp) {
    val segments = listOf(FollowingSegment.Live, FollowingSegment.Videos, FollowingSegment.Channels, FollowingSegment.Games)
    val loggedIn = case != FollowingScreenshotTest.Case.LiveEmptyLoggedOut
    val lists = when (case) {
        FollowingScreenshotTest.Case.LiveLoading -> FollowingFixtures.lists(streams = emptyList(), state = FollowingFixtures.loading)
        FollowingScreenshotTest.Case.LiveError -> FollowingFixtures.lists(streams = emptyList(), state = FollowingFixtures.error)
        FollowingScreenshotTest.Case.LiveEmpty -> FollowingFixtures.lists(streams = emptyList())
        FollowingScreenshotTest.Case.LiveEmptyLoggedOut -> FollowingFixtures.lists(streams = emptyList(), recentlyLive = emptyList())
        else -> FollowingFixtures.lists()
    }
    val selected = when (case) {
        FollowingScreenshotTest.Case.Videos -> FollowingSegment.Videos
        FollowingScreenshotTest.Case.Channels -> FollowingSegment.Channels
        FollowingScreenshotTest.Case.Games -> FollowingSegment.Games
        else -> FollowingSegment.Live
    }
    FollowingScreen(
        segments = segments,
        selected = selected,
        onSelectSegment = {},
        account = FollowingAccount(loggedIn, if (loggedIn) "mitchell" else null),
        lists = lists,
        options = testOptions,
        videoSort = VideoSortChoice(),
        channelSort = ChannelSortChoice(),
        videoPositions = FollowingFixtures.positions,
        bookmarkedVideoIds = setOf("102"),
        actions = RecordingActions(),
        bottomPadding = bottomPadding,
    )
}
