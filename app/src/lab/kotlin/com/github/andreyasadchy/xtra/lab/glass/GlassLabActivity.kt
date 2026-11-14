package com.github.andreyasadchy.xtra.lab.glass

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.Modifier
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.andreyasadchy.xtra.lab.showcase.Samples
import com.github.andreyasadchy.xtra.lab.showcase.cover
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.design.components.GlassContext
import com.github.andreyasadchy.xtra.ui.design.components.LocalGlass
import com.github.andreyasadchy.xtra.ui.design.components.NavItem
import com.github.andreyasadchy.xtra.ui.design.components.FloatingNavBar
import com.github.andreyasadchy.xtra.ui.design.components.glassWindowAllowsBlur
import com.github.andreyasadchy.xtra.ui.design.components.lagoonHazeState
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.following.ChannelSortChoice
import com.github.andreyasadchy.xtra.ui.following.FollowingAccount
import com.github.andreyasadchy.xtra.ui.following.FollowingActions
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.ui.following.FollowingLists
import com.github.andreyasadchy.xtra.ui.following.FollowingScreen
import com.github.andreyasadchy.xtra.ui.following.VideoSortChoice
import com.github.andreyasadchy.xtra.ui.main.shell.FollowingSegment
import com.github.andreyasadchy.xtra.ui.main.shell.GlassPolicy
import kotlinx.coroutines.flow.flowOf

/**
 * Stage G performance lab: the Following page with 60 fixed live cards, laid out like the real shell (the page in one
 * ComposeView, the pill in another, so the pill blurs across roots), without needing a logged-in account. The
 * :benchmark module's GlassBenchmark scrolls it with glass forced on and off (extra `lagoon.glass`).
 */
class GlassLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val policy = GlassPolicy(this).also { lifecycle.addObserver(it) }
        val hazeState = lagoonHazeState()
        var navHeight by mutableIntStateOf(0)
        val root = FrameLayout(this)
        val page = ComposeView(this).apply {
            setContent {
                LagoonTheme {
                    CompositionLocalProvider(LocalGlass provides GlassContext(hazeState, policy.conditions.blurAllowed && glassWindowAllowsBlur())) {
                        LabFollowing(with(LocalDensity.current) { navHeight.toDp() })
                    }
                }
            }
        }
        val nav = ComposeView(this).apply {
            setContent {
                LagoonTheme {
                    CompositionLocalProvider(LocalGlass provides GlassContext(hazeState, policy.conditions.blurAllowed && glassWindowAllowsBlur())) {
                        var selected by remember { mutableIntStateOf(0) }
                        FloatingNavBar(
                            listOf(
                                NavItem("关注", LucideIcons.Heart, LucideIcons.HeartFilled),
                                NavItem("浏览", LucideIcons.LayoutGrid, LucideIcons.LayoutGridFilled),
                                NavItem("我的", LucideIcons.User, LucideIcons.UserFilled),
                            ),
                            selected, { selected = it },
                        )
                    }
                }
            }
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> navHeight = v.height }
        }
        root.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(nav, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(root)
    }
}

private object NoActions : FollowingActions {
    override fun search() = Unit
    override fun settings() = Unit
    override fun logIn() = Unit
    override fun logOut() = Unit
    override fun openStream(stream: Stream) = Unit
    override fun openChannel(id: String?, login: String?, name: String?, image: String?, streamId: String?) = Unit
    override fun openGame(id: String?, slug: String?, name: String?, updateLocal: Boolean) = Unit
    override fun openStreamTag(tag: String) = Unit
    override fun openGameTag(tag: Tag) = Unit
    override fun record(stream: Stream) = Unit
    override fun share(url: String, title: String?) = Unit
    override fun unfollow(channelId: String?, name: String) = Unit
    override fun openVideo(video: Video, position: Long?) = Unit
    override fun download(video: Video) = Unit
    override fun toggleBookmark(video: Video) = Unit
    override fun applyVideoSort(choice: VideoSortChoice, saveDefault: Boolean) = Unit
    override fun applyChannelSort(choice: ChannelSortChoice, saveDefault: Boolean) = Unit
    override fun integrityCheckFailed() = Unit
}

private val done = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))

private val labStreams = List(60) { i ->
    val s = Samples.streams[i % Samples.streams.size]
    Stream(
        id = "lab$i", thumbnailURL = "lab:$i", channelId = "c$i", channelLogin = "channel$i", channelName = s.channel,
        gameName = s.game, title = s.title, viewerCount = 1000 + i * 37, createdAt = "2026-11-13T16:17:50Z", tags = s.tags,
    )
}

@Composable
private fun LabFollowing(bottomPadding: androidx.compose.ui.unit.Dp) {
    val streams = flowOf(PagingData.from(labStreams, done)).collectAsLazyPagingItems()
    val empty = flowOf(PagingData.empty<User>(done)).collectAsLazyPagingItems()
    val games = flowOf(PagingData.empty<Game>(done)).collectAsLazyPagingItems()
    var segment by remember { mutableStateOf(FollowingSegment.Live) }
    FollowingScreen(
        segments = listOf(FollowingSegment.Live, FollowingSegment.Channels, FollowingSegment.Games),
        selected = segment,
        onSelectSegment = { segment = it },
        account = FollowingAccount(true, "lab"),
        lists = FollowingLists(streams, null, empty, games, empty),
        options = FollowingListOptions(
            nowMillis = { 1794600000000L },
            image = { url -> url?.removePrefix("lab:")?.toIntOrNull()?.let { cover(it) } },
        ),
        videoSort = VideoSortChoice(),
        channelSort = ChannelSortChoice(),
        videoPositions = emptyMap(),
        bookmarkedVideoIds = emptySet(),
        actions = NoActions,
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("glassLab"),
        bottomPadding = bottomPadding,
    )
}
