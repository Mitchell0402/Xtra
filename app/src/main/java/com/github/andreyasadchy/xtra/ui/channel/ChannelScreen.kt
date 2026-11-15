package com.github.andreyasadchy.xtra.ui.channel

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.browse.BrowseActions
import com.github.andreyasadchy.xtra.ui.browse.BrowseFilter
import com.github.andreyasadchy.xtra.ui.browse.ClipItem
import com.github.andreyasadchy.xtra.ui.browse.DetailTopBar
import com.github.andreyasadchy.xtra.ui.browse.DetailTopBarHeight
import com.github.andreyasadchy.xtra.ui.browse.FilterKind
import com.github.andreyasadchy.xtra.ui.browse.FilterSaves
import com.github.andreyasadchy.xtra.ui.browse.FilterSheet
import com.github.andreyasadchy.xtra.ui.browse.FilterSheetConfig
import com.github.andreyasadchy.xtra.ui.browse.IntegrityWatch
import com.github.andreyasadchy.xtra.ui.browse.LocalBrowseOffline
import com.github.andreyasadchy.xtra.ui.browse.StreamItem
import com.github.andreyasadchy.xtra.ui.browse.VideoItem
import com.github.andreyasadchy.xtra.ui.browse.actionsForAccessibility
import com.github.andreyasadchy.xtra.ui.browse.cardColumns
import com.github.andreyasadchy.xtra.ui.browse.clipMenu
import com.github.andreyasadchy.xtra.ui.browse.filterChips
import com.github.andreyasadchy.xtra.ui.browse.pagedItems
import com.github.andreyasadchy.xtra.ui.browse.rememberCoverColor
import com.github.andreyasadchy.xtra.ui.browse.streamMenu
import com.github.andreyasadchy.xtra.ui.browse.uptime
import com.github.andreyasadchy.xtra.ui.browse.videoMenu
import com.github.andreyasadchy.xtra.ui.design.components.ActionItem
import com.github.andreyasadchy.xtra.ui.design.components.ActionSheet
import com.github.andreyasadchy.xtra.ui.design.components.Avatar
import com.github.andreyasadchy.xtra.ui.design.components.EmptyState
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipRow
import com.github.andreyasadchy.xtra.ui.design.components.FollowButton
import com.github.andreyasadchy.xtra.ui.design.components.GlassBackdrop
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonImage
import com.github.andreyasadchy.xtra.ui.design.components.LagoonPullToRefresh
import com.github.andreyasadchy.xtra.ui.design.components.LiveCard
import com.github.andreyasadchy.xtra.ui.design.components.SegmentedControl
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonLiveCard
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonLine
import com.github.andreyasadchy.xtra.ui.design.components.ToastHost
import com.github.andreyasadchy.xtra.ui.design.components.ToastHostState
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.design.theme.WidthClass
import com.github.andreyasadchy.xtra.ui.following.ConfirmSheet
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.ui.following.displayName
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.linkify.LinkifyPlugin

/** What the channel page asks its host for besides the list actions. */
interface ChannelActions : BrowseActions {
    fun applyFilter(kind: FilterKind, filter: BrowseFilter, saves: FilterSaves)
    fun setFollowing(follow: Boolean)
    fun toggleNotifications()
    fun watchLive()
    fun shareChannel()
    fun openTeam(name: String)
    fun openUrl(url: String)
    fun openSettings()
    fun openLogIn()
    fun openLogOut()
}

/** What the header shows: from the link first, completed by the channel's data when it arrives. */
@Immutable
data class ChannelHeader(
    val id: String?,
    val login: String?,
    val name: String?,
    val avatar: String?,
    val banner: String? = null,
    val followers: Int? = null,
    /** Partner, affiliate and staff, already as words. */
    val roles: String? = null,
    val createdAt: Long? = null,
    /** Shown while the channel is offline. */
    val lastBroadcast: Long? = null,
    val description: String? = null,
    /** The stream while the channel is live. */
    val stream: Stream? = null,
)

/** The About segment (v0.1 ChannelAboutFragment): null fields are not shown. */
@Immutable
data class ChannelAbout(
    val description: String? = null,
    val socialMedias: List<Pair<String, String?>> = emptyList(),
    /** Team login name and display name. */
    val team: Pair<String?, String>? = null,
    val originalName: String? = null,
    val panels: List<com.github.andreyasadchy.xtra.model.ui.ChannelPanel> = emptyList(),
    val loaded: Boolean = false,
)

/** The channel's lists; a segment that is switched off passes null. */
class ChannelLists(
    val suggestions: LazyPagingItems<Stream>?,
    val videos: LazyPagingItems<Video>?,
    val clips: LazyPagingItems<Clip>?,
)

@Immutable
data class ChannelFilters(
    val videos: BrowseFilter,
    val clips: BrowseFilter,
    val videosConfig: FilterSheetConfig,
    val clipsConfig: FilterSheetConfig,
)

/** Where the legacy chat (U7) sits over the page, in px of the page's window. */
@Immutable
data class ChatArea(val left: Int, val top: Int, val bottom: Int, val visible: Boolean)

/** Test tags. */
const val ChannelListTag = "channelList"
const val ChannelSegmentsTag = "channelSegments"
const val ChannelHeaderTag = "channelHeader"
const val ChannelLiveCardTag = "channelLiveCard"

/** Width of the channel column of the two-pane tablet layout (plan 7.3). */
private val InfoPaneWidth = 380.dp

/**
 * A channel's page (plan 7.3, U5): banner, big avatar (teal ring while live), name, followers, two lines of
 * description, the follow button and the notification bell; a live card when the channel streams; then the
 * suggestions, videos, clips, chat and about segments on floating glass tabs that pin under the bar. On tablets in
 * landscape the channel stays in a column on the left and the segments fill the right.
 *
 * The chat segment is still the old chat (U7): the page leaves [onChatArea] room for it under the tabs.
 */
@Composable
fun ChannelScreen(
    header: ChannelHeader,
    segments: List<ChannelSegment>,
    selected: ChannelSegment,
    onSelect: (ChannelSegment) -> Unit,
    lists: ChannelLists,
    filters: ChannelFilters,
    about: ChannelAbout,
    options: FollowingListOptions,
    actions: ChannelActions,
    modifier: Modifier = Modifier,
    loggedIn: Boolean = false,
    accountName: String? = null,
    showFollow: Boolean = true,
    following: Boolean? = null,
    notifications: Boolean? = null,
    videoPositions: Map<Long, Long> = emptyMap(),
    bookmarkedVideoIds: Set<String> = emptySet(),
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    navScroll: NestedScrollConnection? = null,
    scrollToTopRequests: Int = 0,
    toast: ToastHostState? = null,
    offline: Boolean = false,
    onChatArea: (ChatArea) -> Unit = {},
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val twoPane = WidthClass.of(configuration.screenWidthDp) == WidthClass.Expanded
    val grid = rememberLazyGridState()
    var headerHeight by remember { mutableIntStateOf(0) }
    var filterSheet by rememberSaveable { mutableStateOf(false) }
    var unfollowSheet by rememberSaveable { mutableStateOf(false) }
    var moreSheet by rememberSaveable { mutableStateOf(false) }
    var logOutSheet by rememberSaveable { mutableStateOf(false) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barTop = statusTop + DetailTopBarHeight
    val barTopPx = with(density) { barTop.roundToPx() }
    val gapPx = with(density) { LagoonSpacing.cardGap.roundToPx() }
    val tabs = segments.size > 1
    val tabsBlock = if (tabs) LagoonSpacing.touchTarget else 0.dp
    val chat = selected == ChannelSegment.Chat
    IntegrityWatch(lists.suggestions, actions)
    IntegrityWatch(lists.videos, actions)
    IntegrityWatch(lists.clips, actions)

    LaunchedEffect(scrollToTopRequests) {
        if (scrollToTopRequests > 0) grid.animateScrollToItem(0)
    }
    // a new segment starts at its top, right under the pinned tabs, unless the header is still in view
    var lastSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected != lastSelected) {
            lastSelected = selected
            if (!twoPane && grid.firstVisibleItemIndex >= 1) grid.scrollToItem(1)
        }
    }
    val paneWidthPx = if (twoPane) with(density) { InfoPaneWidth.roundToPx() } else 0
    val bottomPx = with(density) { bottomPadding.roundToPx() }
    val chatTop = barTopPx + with(density) { (tabsBlock + LagoonSpacing.xs).roundToPx() }
    LaunchedEffect(chat, paneWidthPx, chatTop, bottomPx) {
        onChatArea(ChatArea(paneWidthPx, chatTop, bottomPx, chat))
    }
    val coverColor = rememberCoverColor(options.image(header.banner ?: header.avatar)) ?: Lagoon.colors.card
    val collapseDistancePx = with(density) { (BannerMaxHeight + AvatarSize * 0.6f).toPx() }
    // 0 while the header shows, 1 once it has scrolled under the bar (always 1 in chat, where the header is gone)
    val collapsed: () -> Float = {
        when {
            chat || twoPane -> 1f
            grid.firstVisibleItemIndex > 0 -> 1f
            // the bar is solid once the name has gone under it, not only once the tall header (live card) has
            else -> grid.firstVisibleItemScrollOffset / minOf(headerHeight * 0.6f, collapseDistancePx).coerceAtLeast(1f)
        }
    }
    val name = displayName(header.name, header.login, options.nameDisplay)
    val onFollow: (Boolean) -> Unit = { if (it) actions.setFollowing(true) else unfollowSheet = true }

    Box(modifier.fillMaxSize()) {
        GlassBackdrop(Modifier.fillMaxSize().then(if (navScroll != null) Modifier.nestedScroll(navScroll) else Modifier)) {
            CompositionLocalProvider(LocalBrowseOffline provides offline) {
                val headerBlock: @Composable (Modifier) -> Unit = { mod ->
                    ChannelHeaderBlock(
                        header = header, coverColor = coverColor, options = options, showFollow = showFollow,
                        following = following, notifications = notifications, onFollow = onFollow,
                        actions = actions, gradientTop = barTop, modifier = mod,
                    )
                }
                if (twoPane) {
                    Row(Modifier.fillMaxSize()) {
                        LazyColumn(
                            Modifier.width(InfoPaneWidth).fillMaxHeight(),
                            state = rememberLazyListState(),
                            contentPadding = PaddingValues(start = LagoonSpacing.pageSide, end = LagoonSpacing.xs, top = barTop, bottom = bottomPadding + LagoonSpacing.m),
                        ) {
                            item(key = "header") { headerBlock(Modifier) }
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (!chat) {
                                SegmentList(
                                    selected, lists, filters, about, options, actions, grid, videoPositions, bookmarkedVideoIds,
                                    columns = maxOf(1, cardColumns(configuration.screenWidthDp - InfoPaneWidth.value.toInt())),
                                    top = barTop + tabsBlock + LagoonSpacing.xs, bottom = bottomPadding, offline = offline,
                                    onOpenFilter = { filterSheet = true }, header = null,
                                )
                            }
                        }
                    }
                } else if (!chat) {
                    SegmentList(
                        selected, lists, filters, about, options, actions, grid, videoPositions, bookmarkedVideoIds,
                        columns = cardColumns(configuration.screenWidthDp), top = barTop, bottom = bottomPadding, offline = offline,
                        onOpenFilter = { filterSheet = true },
                        header = {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "header", contentType = "header") {
                                headerBlock(Modifier.onSizeChanged { headerHeight = it.height })
                            }
                            // the floating tabs sit over this gap
                            item(span = { GridItemSpan(maxLineSpan) }, key = "tabs", contentType = "tabs") {
                                Spacer(Modifier.height(tabsBlock))
                            }
                        },
                    )
                }
            }
        }
        // floating layers: next to the backdrop, never inside it, so the glass does not sample itself
        DetailTopBar(
            title = name,
            collapsed = collapsed,
            onBack = actions::back,
            modifier = Modifier.semantics { isTraversalGroup = true; traversalIndex = -1f },
            actions = {
                LagoonIconButton(LucideIcons.Search, stringResource(R.string.search), actions::search)
                LagoonIconButton(LucideIcons.Ellipsis, stringResource(R.string.channel_more), { moreSheet = true }, Modifier.testTag("channelMore"))
            },
        )
        if (tabs) {
            SegmentedControl(
                options = segments.map { stringResource(it.label) },
                selectedIndex = segments.indexOf(selected).coerceAtLeast(0),
                onSelect = { onSelect(segments[it]) },
                glass = true,
                modifier = Modifier
                    .offset {
                        // under the header while it shows, then pinned under the bar (always pinned in chat and on tablets)
                        val inList = if (!chat && !twoPane && grid.firstVisibleItemIndex == 0) {
                            barTopPx + headerHeight + gapPx - grid.firstVisibleItemScrollOffset
                        } else Int.MIN_VALUE
                        IntOffset(paneWidthPx, maxOf(barTopPx, inList))
                    }
                    .padding(horizontal = LagoonSpacing.pageSide)
                    .widthIn(max = 560.dp)
                    .semantics { isTraversalGroup = true; traversalIndex = -0.5f }
                    .testTag(ChannelSegmentsTag),
            )
        }
        if (toast != null) {
            ToastHost(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding))
        }
    }

    if (filterSheet && (selected == ChannelSegment.Videos || selected == ChannelSegment.Clips)) {
        val kind = if (selected == ChannelSegment.Videos) FilterKind.Videos else FilterKind.Clips
        FilterSheet(
            config = if (kind == FilterKind.Videos) filters.videosConfig else filters.clipsConfig,
            current = if (kind == FilterKind.Videos) filters.videos else filters.clips,
            onApply = { filter, saves -> actions.applyFilter(kind, filter, saves) },
            onDismiss = { filterSheet = false },
        )
    }
    if (unfollowSheet) {
        ConfirmSheet(
            title = stringResource(R.string.unfollow_channel, name),
            message = null,
            confirm = stringResource(R.string.unfollow),
            onConfirm = { actions.setFollowing(false) },
            onDismiss = { unfollowSheet = false },
        )
    }
    if (moreSheet) {
        val stream = header.stream
        val items = buildList {
            add(ActionItem(stringResource(R.string.share), LucideIcons.Share2) { actions.shareChannel() })
            if (stream?.viewerCount != null) {
                add(ActionItem(stringResource(R.string.following_record), LucideIcons.CircleDot) { actions.record(stream) })
            }
            add(ActionItem(stringResource(R.string.settings), LucideIcons.Settings) { actions.openSettings() })
            if (loggedIn) {
                add(ActionItem(stringResource(R.string.following_log_out), LucideIcons.LogOut, destructive = true) { logOutSheet = true })
            } else {
                add(ActionItem(stringResource(R.string.log_in), LucideIcons.LogIn) { actions.openLogIn() })
            }
        }
        ActionSheet(items, onDismiss = { moreSheet = false }, title = name)
    }
    if (logOutSheet) {
        ConfirmSheet(
            title = stringResource(R.string.following_log_out_confirm, accountName.orEmpty()),
            message = stringResource(R.string.following_log_out_message),
            confirm = stringResource(R.string.following_log_out),
            onConfirm = actions::openLogOut,
            onDismiss = { logOutSheet = false },
        )
    }
}

val ChannelSegment.label: Int
    get() = when (this) {
        ChannelSegment.Suggestions -> R.string.channel_suggestions
        ChannelSegment.Videos -> R.string.videos
        ChannelSegment.Clips -> R.string.clips
        ChannelSegment.Chat -> R.string.chat
        ChannelSegment.About -> R.string.about
    }

/** The grid of one segment, with the header items in front of it on phones. */
@Composable
private fun SegmentList(
    selected: ChannelSegment,
    lists: ChannelLists,
    filters: ChannelFilters,
    about: ChannelAbout,
    options: FollowingListOptions,
    actions: ChannelActions,
    grid: LazyGridState,
    videoPositions: Map<Long, Long>,
    bookmarkedVideoIds: Set<String>,
    columns: Int,
    top: Dp,
    bottom: Dp,
    offline: Boolean,
    onOpenFilter: () -> Unit,
    header: (LazyGridScope.() -> Unit)?,
) {
    val current = when (selected) {
        ChannelSegment.Suggestions -> lists.suggestions
        ChannelSegment.Videos -> lists.videos
        ChannelSegment.Clips -> lists.clips
        else -> null
    }
    LagoonPullToRefresh(
        isRefreshing = current?.loadState?.refresh is LoadState.Loading && (current?.itemCount ?: 0) > 0,
        onRefresh = { current?.refresh() },
        modifier = Modifier.fillMaxSize(),
        indicatorTop = top,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = grid,
            modifier = Modifier.fillMaxSize().testTag(ChannelListTag),
            contentPadding = PaddingValues(
                start = LagoonSpacing.pageSide, end = LagoonSpacing.pageSide, top = top,
                bottom = bottom + LagoonSpacing.m,
            ),
            verticalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap),
            horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap),
        ) {
            header?.invoke(this)
            when (selected) {
                ChannelSegment.Videos -> {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "chips", contentType = "chips") {
                        FilterChipRow(
                            filterChips(FilterKind.Videos, filters.videos, filters.videosConfig, onOpen = onOpenFilter, onRemoveTag = {}),
                            contentPadding = PaddingValues(0.dp),
                        )
                    }
                    val videos = lists.videos
                    if (videos == null) items(6, contentType = { "skeleton" }) { SkeletonLiveCard() } else pagedItems(
                        videos, offline, "video",
                        skeleton = { SkeletonLiveCard() },
                        empty = { EmptyState(stringResource(R.string.channel_empty_videos), icon = LucideIcons.Film) },
                    ) { index, video ->
                        ChannelVideoCell(video, index, options, videoPositions, bookmarkedVideoIds, actions)
                    }
                }
                ChannelSegment.Clips -> {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "chips", contentType = "chips") {
                        FilterChipRow(
                            filterChips(FilterKind.Clips, filters.clips, filters.clipsConfig, onOpen = onOpenFilter, onRemoveTag = {}),
                            contentPadding = PaddingValues(0.dp),
                        )
                    }
                    val clips = lists.clips
                    if (clips == null) items(6, contentType = { "skeleton" }) { SkeletonLiveCard() } else pagedItems(
                        clips, offline, "clip",
                        skeleton = { SkeletonLiveCard() },
                        empty = { EmptyState(stringResource(R.string.channel_empty_clips), icon = LucideIcons.Scissors) },
                    ) { index, clip ->
                        ChannelClipCell(clip, index, options, actions)
                    }
                }
                ChannelSegment.Suggestions -> {
                    val suggestions = lists.suggestions
                    if (suggestions == null) items(6, contentType = { "skeleton" }) { SkeletonLiveCard() } else pagedItems(
                        suggestions, offline, "stream",
                        skeleton = { SkeletonLiveCard() },
                        empty = { EmptyState(stringResource(R.string.channel_empty_suggestions), icon = LucideIcons.Radio) },
                    ) { index, stream ->
                        SuggestionCell(stream, index, options, actions)
                    }
                }
                ChannelSegment.About -> aboutItems(about, options, actions, offline)
                ChannelSegment.Chat -> Unit
            }
        }
    }
}

@Composable
private fun ChannelVideoCell(video: Video, index: Int, options: FollowingListOptions, positions: Map<Long, Long>, bookmarked: Set<String>, actions: ChannelActions) {
    var menuOpen by remember { mutableStateOf(false) }
    val position = if (options.usePositions) video.id?.toLongOrNull()?.let { positions[it] } else null
    val menu = videoMenu(video, video.id in bookmarked, actions, showGame = true).filterNot { it.icon == LucideIcons.User }
    VideoItem(video, index, options, position, showChannel = false, menu = menu, onClick = { actions.openVideo(video, position) }, onLongClick = { menuOpen = true })
    if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = video.title?.trim())
}

@Composable
private fun ChannelClipCell(clip: Clip, index: Int, options: FollowingListOptions, actions: ChannelActions) {
    var menuOpen by remember { mutableStateOf(false) }
    val menu = clipMenu(clip, actions, showGame = true).filterNot { it.icon == LucideIcons.User }
    ClipItem(clip, index, options, showChannel = false, menu = menu, onClick = { actions.openClip(clip) }, onLongClick = { menuOpen = true })
    if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = clip.title?.trim())
}

/** v0.1 showed suggestions as compact stream rows; the tags opened the streams with that tag. */
@Composable
private fun SuggestionCell(stream: Stream, index: Int, options: FollowingListOptions, actions: ChannelActions) {
    var menuOpen by remember { mutableStateOf(false) }
    val menu = streamMenu(stream, options, actions, showGame = true, onTag = actions::openStreamTag)
    StreamItem(stream, index, options, compact = true, showGame = true, menu = menu, onClick = { actions.openStream(stream) }, onLongClick = { menuOpen = true })
    if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = stream.channelName ?: stream.channelLogin)
}

/** Height of the banner area on phones: a 5:2 strip, never taller than this. */
private val BannerMaxHeight = 220.dp
private val AvatarSize = 84.dp

/**
 * The top of the page: the banner (or the avatar's colour) running up behind the bar and fading into the page, the
 * avatar over its lower edge, name, followers and roles, the dates, two lines of description that open on tap, the
 * follow button and the bell, and the live card while the channel streams.
 */
@Composable
private fun ChannelHeaderBlock(
    header: ChannelHeader,
    coverColor: Color,
    options: FollowingListOptions,
    showFollow: Boolean,
    following: Boolean?,
    notifications: Boolean?,
    onFollow: (Boolean) -> Unit,
    actions: ChannelActions,
    gradientTop: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = Lagoon.colors
    val context = LocalContext.current
    val page = colors.page
    val live = header.stream?.viewerCount != null
    val name = displayName(header.name, header.login, options.nameDisplay)
    Column(modifier.fillMaxWidth().testTag(ChannelHeaderTag)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val bannerHeight = minOf(maxWidth * 0.4f, BannerMaxHeight)
            // the banner: past the grid's side padding and up behind the bar, fading into the page at its foot
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(bannerHeight)
                    .drawBehind {
                        val side = LagoonSpacing.pageSide.toPx()
                        val top = gradientTop.toPx()
                        drawRect(
                            Brush.verticalGradient(
                                0f to coverColor.copy(alpha = 0.9f),
                                0.7f to coverColor.copy(alpha = 0.45f),
                                1f to page.copy(alpha = 0f),
                                startY = -top, endY = size.height,
                            ),
                            topLeft = Offset(-side, -top),
                            size = Size(size.width + side * 2, size.height + top),
                        )
                    },
            ) {
                val banner = options.image(header.banner)
                if (banner != null) {
                    Box(Modifier.fillMaxSize().clip(LagoonShapes.card)) {
                        LagoonImage(banner, contentDescription = null, modifier = Modifier.fillMaxSize())
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(0.45f to Color.Transparent, 1f to page.copy(alpha = 0.85f))
                            )
                        )
                    }
                }
            }
            Avatar(
                options.image(header.avatar), name, size = AvatarSize, live = live,
                modifier = Modifier
                    .padding(start = LagoonSpacing.xs)
                    .offset(y = bannerHeight - AvatarSize * 0.6f),
            )
        }
        Spacer(Modifier.height(AvatarSize * 0.6f + LagoonSpacing.s))
        Text(
            name, style = Lagoon.type.title, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        val counts = listOfNotNull(
            header.followers?.let { pluralStringResource(R.plurals.followers, it, TwitchApiHelper.formatCount(it, options.truncateCounts)) },
            header.roles,
        )
        if (counts.isNotEmpty()) {
            Text(counts.joinToString(" · "), style = Lagoon.type.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val dates = listOfNotNull(
            header.lastBroadcast?.takeIf { !live }?.let { stringResource(R.string.last_broadcast_date, TwitchApiHelper.formatDate(context, it)) },
            header.createdAt?.let { stringResource(R.string.created_at, TwitchApiHelper.formatDate(context, it)) },
        )
        if (dates.isNotEmpty()) {
            Text(dates.joinToString(" · "), style = Lagoon.type.caption, color = colors.textHint, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val description = header.description?.trim()
        if (!description.isNullOrEmpty()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            Text(
                description,
                style = Lagoon.type.body,
                color = colors.textSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = LagoonSpacing.xs)
                    .animateContentSize(Lagoon.motion.move())
                    .pressable(onClick = { expanded = !expanded }, shape = LagoonShapes.row, scaleOnPress = false),
            )
        }
        Row(
            Modifier.padding(top = LagoonSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.xs),
        ) {
            // shown once the follow state is known, so it never flips under the finger
            if (showFollow && header.id != null && following != null) {
                FollowButton(following = following, onToggle = onFollow)
            }
            if (notifications != null) {
                LagoonIconButton(
                    if (notifications) LucideIcons.BellRing else LucideIcons.Bell,
                    stringResource(if (notifications) R.string.disable_notifications else R.string.enable_notifications),
                    actions::toggleNotifications,
                    tint = if (notifications) colors.accent else Color.Unspecified,
                    modifier = Modifier.testTag("channelBell"),
                )
            }
        }
        val stream = header.stream
        if (stream != null && live) {
            var menuOpen by remember { mutableStateOf(false) }
            val menu = liveMenu(stream, actions)
            LiveCard(
                title = stream.title?.trim().orEmpty(),
                channelName = stream.gameName?.let { stringResource(R.string.channel_live_playing, it) } ?: stringResource(R.string.watch_live),
                viewers = TwitchApiHelper.formatCount(stream.viewerCount ?: 0, options.truncateCounts),
                onClick = actions::watchLive,
                thumbnail = options.image(stream.thumbnail),
                avatar = options.image(header.avatar),
                uptime = if (options.showUptime) uptime(stream.createdAt, options.nowMillis()) else null,
                tags = if (options.showTags) stream.tags.orEmpty() else emptyList(),
                onLongClick = { menuOpen = true },
                modifier = Modifier
                    .padding(top = LagoonSpacing.m)
                    .actionsForAccessibility(menu)
                    .semantics { contentDescription = context.getString(R.string.watch_live) }
                    .testTag(ChannelLiveCardTag),
            )
            if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = stream.title?.trim())
        }
        Spacer(Modifier.height(LagoonSpacing.xs))
    }
}

/** The live card's menu: watch, the game, record (v0.1's "download" in the channel menu) and share. */
@Composable
private fun liveMenu(stream: Stream, actions: ChannelActions): List<ActionItem> = buildList {
    add(ActionItem(stringResource(R.string.watch_live), LucideIcons.Play) { actions.watchLive() })
    stream.gameName?.let { game ->
        add(ActionItem(stringResource(R.string.following_open_game, game), LucideIcons.Gamepad2) { actions.openGame(stream.gameId, stream.gameSlug, stream.gameName) })
    }
    add(ActionItem(stringResource(R.string.following_record), LucideIcons.CircleDot) { actions.record(stream) })
    add(ActionItem(stringResource(R.string.share), LucideIcons.Share2) { actions.shareChannel() })
}

/**
 * The About segment as a grid of cards (plan 7.3): the description, the team, the old name, the links, then the
 * channel's panels with their picture, title and text.
 */
private fun LazyGridScope.aboutItems(about: ChannelAbout, options: FollowingListOptions, actions: ChannelActions, offline: Boolean) {
    val empty = about.description.isNullOrBlank() && about.team == null && about.originalName.isNullOrBlank() &&
            about.socialMedias.isEmpty() && about.panels.isEmpty()
    if (!about.loaded && empty) {
        items(3, contentType = { "skeleton" }) {
            Column(Modifier.fillMaxWidth().background(Lagoon.colors.card, LagoonShapes.card).padding(LagoonSpacing.cardPadding)) {
                SkeletonLine(0.4f)
                Spacer(Modifier.height(10.dp))
                SkeletonLine(0.9f)
                Spacer(Modifier.height(6.dp))
                SkeletonLine(0.7f)
            }
        }
        return
    }
    if (empty) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
            EmptyState(
                stringResource(if (offline) R.string.no_connection else R.string.channel_about_empty),
                icon = if (offline) LucideIcons.WifiOff else LucideIcons.Info,
            )
        }
        return
    }
    val description = about.description?.trim()
    if (!description.isNullOrEmpty() || about.team != null || !about.originalName.isNullOrBlank()) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "aboutInfo", contentType = "aboutCard") {
            AboutCard {
                if (!description.isNullOrEmpty()) {
                    Text(description, style = Lagoon.type.body, color = Lagoon.colors.textPrimary)
                }
                about.originalName?.takeIf { it.isNotBlank() }?.let {
                    Text(stringResource(R.string.old_username, it), style = Lagoon.type.label, color = Lagoon.colors.textSecondary)
                }
                about.team?.let { (teamName, teamDisplay) ->
                    AboutLinkRow(
                        icon = LucideIcons.Users,
                        title = stringResource(R.string.team, teamDisplay),
                        subtitle = null,
                        onClick = teamName?.let { { actions.openTeam(it) } },
                    )
                }
            }
        }
    }
    if (about.socialMedias.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "aboutLinks", contentType = "aboutCard") {
            AboutCard(title = stringResource(R.string.channel_about_links)) {
                about.socialMedias.forEach { (title, url) ->
                    AboutLinkRow(
                        icon = LucideIcons.ExternalLink,
                        title = title,
                        subtitle = url?.let { runCatching { android.net.Uri.parse(it).host?.removePrefix("www.") }.getOrNull() },
                        onClick = url?.let { { actions.openUrl(it) } },
                    )
                }
            }
        }
    }
    about.panels.forEachIndexed { index, panel ->
        item(key = "panel$index", contentType = "panel") {
            PanelCard(panel, options, actions)
        }
    }
}

@Composable
private fun AboutCard(title: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Lagoon.colors.card, LagoonShapes.card).padding(LagoonSpacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(LagoonSpacing.xs),
    ) {
        if (title != null) {
            Text(title, style = Lagoon.type.headline, color = Lagoon.colors.textPrimary, modifier = Modifier.semantics { heading() })
        }
        content()
    }
}

@Composable
private fun AboutLinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick = onClick, shape = LagoonShapes.row, scaleOnPress = false) else Modifier)
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
    ) {
        Box(Modifier.size(36.dp).background(Lagoon.colors.control, LagoonShapes.tile), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Lagoon.colors.textSecondary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Lagoon.type.bodyStrong, color = Lagoon.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = Lagoon.type.label, color = Lagoon.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onClick != null) {
            Icon(LucideIcons.ChevronRight, contentDescription = null, tint = Lagoon.colors.textHint, modifier = Modifier.size(18.dp))
        }
    }
}

/** A channel panel (v0.1 ChannelPanelAdapter): the picture opens its link, the text is Markdown with links. */
@Composable
private fun PanelCard(panel: com.github.andreyasadchy.xtra.model.ui.ChannelPanel, options: FollowingListOptions, actions: ChannelActions) {
    val colors = Lagoon.colors
    Column(
        Modifier.fillMaxWidth().background(colors.card, LagoonShapes.card).padding(LagoonSpacing.cardMediaInset),
        verticalArrangement = Arrangement.spacedBy(LagoonSpacing.xs),
    ) {
        val image = options.image(panel.imageUrl)
        if (image != null) {
            val link = panel.linkUrl
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(LagoonShapes.cardMedia)
                    .then(if (link != null) Modifier.pressable(onClick = { actions.openUrl(link) }, shape = LagoonShapes.cardMedia) else Modifier)
            ) {
                LagoonImage(image, contentDescription = panel.title, modifier = Modifier.fillMaxWidth().aspectRatio(2f), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            }
        }
        if (!panel.title.isNullOrBlank()) {
            Text(
                panel.title, style = Lagoon.type.bodyStrong, color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = LagoonSpacing.xs).semantics { heading() },
            )
        }
        if (!panel.description.isNullOrBlank()) {
            MarkdownText(panel.description, Modifier.padding(horizontal = LagoonSpacing.xs, vertical = 4.dp))
        }
    }
}

/** Markdown as v0.1 drew it (Markwon in a text view), in the page's colours. */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    val colors = Lagoon.colors
    val text = colors.textSecondary.toArgb()
    val link = colors.accent.toArgb()
    val size = Lagoon.type.body.fontSize.value
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                tag = Markwon.builder(context)
                    .usePlugin(SoftBreakAddsNewLinePlugin.create())
                    .usePlugin(LinkifyPlugin.create())
                    .usePlugin(com.github.andreyasadchy.xtra.util.HeadingFixPlugin())
                    .build()
            }
        },
        update = { view ->
            view.setTextColor(text)
            view.setLinkTextColor(link)
            view.textSize = size
            view.maxLines = maxLines
            view.ellipsize = if (maxLines != Int.MAX_VALUE) android.text.TextUtils.TruncateAt.END else null
            (view.tag as Markwon).setMarkdown(view, markdown)
        },
        modifier = modifier.fillMaxWidth(),
    )
}
