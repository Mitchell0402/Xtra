package com.github.andreyasadchy.xtra.ui.following

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.design.components.FloatingNavBar
import com.github.andreyasadchy.xtra.ui.design.components.GlassBlurKey
import com.github.andreyasadchy.xtra.ui.design.components.GlassContext
import com.github.andreyasadchy.xtra.ui.design.components.LocalGlass
import com.github.andreyasadchy.xtra.ui.design.components.NavItem
import com.github.andreyasadchy.xtra.ui.design.components.TopBarCollapsed
import com.github.andreyasadchy.xtra.ui.design.components.TopBarExpanded
import com.github.andreyasadchy.xtra.ui.design.components.glassWindowAllowsBlur
import com.github.andreyasadchy.xtra.ui.design.components.lagoonHazeState
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.design.theme.WidthClass
import com.github.andreyasadchy.xtra.ui.main.shell.FollowingSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private val navItems = listOf(
    NavItem("Following", LucideIcons.Heart, LucideIcons.HeartFilled),
    NavItem("Browse", LucideIcons.LayoutGrid),
    NavItem("Me", LucideIcons.User),
)

private val manyStreams = List(8) { i ->
    val s = FollowingFixtures.streams[i % FollowingFixtures.streams.size]
    Stream(
        id = "s$i", channelId = "c$i", channelLogin = s.channelLogin, channelName = s.channelName, gameName = s.gameName,
        title = s.title, viewerCount = s.viewerCount, createdAt = s.createdAt, tags = s.tags,
    )
}

/**
 * Glass stage G on the Following page: how much of the window really blurs (glass plan section 7, area gate) and where
 * the floating bar puts things (section 11: first item not covered, tabs fixed while switching, collapse over the
 * list), in the four representative windows: narrow, medium, wide and short (most phones in landscape).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class FollowingGlassTest(private val window: Window) {

    /** Plan section 7's windows and their blur area limits, measured at 1.3x font size. */
    enum class Window(val widthDp: Int, val heightDp: Int, val maxBlurShare: Float) {
        Narrow(412, 915, 0.30f),
        Medium(700, 1000, 0.30f),
        Wide(1280, 800, 0.25f),
        Short(915, 412, 0f),
    }

    @get:Rule
    val compose = createComposeRule()

    private fun show(blur: Boolean = true) {
        RuntimeEnvironment.setQualifiers("w${window.widthDp}dp-h${window.heightDp}dp")
        RuntimeEnvironment.setFontScale(1.3f)
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                val state = remember { lagoonHazeState() }
                CompositionLocalProvider(LocalGlass provides GlassContext(state, blur && glassWindowAllowsBlur())) {
                    Box(Modifier.fillMaxSize()) {
                        var selected by remember { mutableStateOf(FollowingSegment.Live) }
                        FollowingScreen(
                            segments = listOf(FollowingSegment.Games, FollowingSegment.Live, FollowingSegment.Videos, FollowingSegment.Channels),
                            selected = selected,
                            onSelectSegment = { selected = it },
                            account = FollowingAccount(true, "mitchell"),
                            lists = FollowingFixtures.lists(streams = manyStreams),
                            options = FollowingFixtures.options.copy(image = { null }),
                            videoSort = VideoSortChoice(),
                            channelSort = ChannelSortChoice(),
                            videoPositions = emptyMap(),
                            bookmarkedVideoIds = emptySet(),
                            actions = RecordingActions(),
                            bottomPadding = 104.dp,
                        )
                        // the pill below 840 dp; the rail never blurs, so wide windows have no pill here
                        if (WidthClass.of(window.widthDp) != WidthClass.Expanded) {
                            FloatingNavBar(navItems, 0, {}, Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun blurAreaStaysUnderTheLimit() {
        show()
        val nodes = compose.onAllNodes(SemanticsMatcher.expectValue(GlassBlurKey, true)).fetchSemanticsNodes()
        val root = compose.onRoot().getBoundsInRoot()
        val windowArea = (root.right - root.left).value * (root.bottom - root.top).value
        val blurArea = nodes.sumOf { node ->
            val b = node.boundsInRoot
            (b.width * b.height).toDouble()
        } / (compose.density.density * compose.density.density)
        val share = (blurArea / windowArea).toFloat()
        println("glass area ${window.name}: ${nodes.size} blurred surfaces, ${"%.1f".format(share * 100)} % of the window")
        if (window.maxBlurShare == 0f) assertEquals("short windows never blur", 0, nodes.size)
        else {
            assertTrue("no blurred surface found", nodes.isNotEmpty())
            assertTrue("blur covers ${share * 100} % of ${window.name}", share <= window.maxBlurShare)
        }
    }

    @Test
    fun firstItemStartsBelowTheTabsAndTheBarCollapsesOverTheList() {
        show(blur = false)
        val tabs = compose.onNodeWithTag(SegmentTabsTag).getBoundsInRoot()
        val heading = compose.onNodeWithText("Live now · 8").getBoundsInRoot()
        assertTrue("first item ${heading.top} under the tabs ${tabs.bottom}", heading.top >= tabs.bottom)
        // scrolling the list collapses the title: the tabs move up by the difference and the list runs under them
        compose.onNodeWithTag(FollowingListTag).performScrollToIndex(4)
        compose.waitForIdle()
        val collapsedTabs = compose.onNodeWithTag(SegmentTabsTag).getBoundsInRoot()
        assertEquals((tabs.top - (TopBarExpanded - TopBarCollapsed)).value, collapsedTabs.top.value, 1f)
        // switching segments keeps the tabs where they are and starts the new list below them
        compose.onNodeWithText("Channels").performClick()
        compose.waitForIdle()
        val tabsAfter = compose.onNodeWithTag(SegmentTabsTag).getBoundsInRoot()
        val channel = compose.onNodeWithText("Sora").getBoundsInRoot()
        assertTrue("first channel row ${channel.top} under the tabs ${tabsAfter.bottom}", channel.top >= tabsAfter.bottom)
        assertEquals(tabs.top.value, tabsAfter.top.value, 1f)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun windows() = Window.entries.map { arrayOf<Any>(it) }
    }
}
