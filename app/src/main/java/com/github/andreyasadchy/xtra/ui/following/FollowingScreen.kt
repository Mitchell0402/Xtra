package com.github.andreyasadchy.xtra.ui.following

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Tag as GameTag
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.common.VideosSortDialog
import com.github.andreyasadchy.xtra.ui.design.components.ActionItem
import com.github.andreyasadchy.xtra.ui.design.components.ActionSheet
import com.github.andreyasadchy.xtra.ui.design.components.Avatar
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle
import com.github.andreyasadchy.xtra.ui.design.components.ChannelRow
import com.github.andreyasadchy.xtra.ui.design.components.ChoiceOption
import com.github.andreyasadchy.xtra.ui.design.components.EmptyState
import com.github.andreyasadchy.xtra.ui.design.components.ErrorState
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipItem
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipRow
import com.github.andreyasadchy.xtra.ui.design.components.GamePoster
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonPullToRefresh
import com.github.andreyasadchy.xtra.ui.design.components.LargeTopBar
import com.github.andreyasadchy.xtra.ui.design.components.LiveCard
import com.github.andreyasadchy.xtra.ui.design.components.LiveCardCompact
import com.github.andreyasadchy.xtra.ui.design.components.SegmentedControl
import com.github.andreyasadchy.xtra.ui.design.components.SingleChoiceList
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonLiveCard
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonRow
import com.github.andreyasadchy.xtra.ui.design.components.Tag
import com.github.andreyasadchy.xtra.ui.design.components.ToastHost
import com.github.andreyasadchy.xtra.ui.design.components.ToastHostState
import com.github.andreyasadchy.xtra.ui.design.components.VideoCard
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.components.rememberTopBarState
import com.github.andreyasadchy.xtra.ui.design.components.shimmer
import com.github.andreyasadchy.xtra.ui.design.components.staggeredAppear
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.design.theme.WidthClass
import com.github.andreyasadchy.xtra.ui.design.theme.siblingBy
import com.github.andreyasadchy.xtra.ui.following.channels.FollowedChannelsSorts
import com.github.andreyasadchy.xtra.ui.main.shell.FollowingSegment
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlin.time.Instant

/** v0.1 list settings that change how items read (kept as they are, plan 10.1 rule 2). */
@Immutable
data class FollowingListOptions(
    /** `ui_name_display`: 0 = "Name(login)", 1 = name, 2 = login. */
    val nameDisplay: String = "0",
    val showUptime: Boolean = true,
    val showTags: Boolean = true,
    val truncateCounts: Boolean = true,
    val showBroadcasters: Boolean = true,
    /** `compact_streams` is not "disabled": live items as compact rows. */
    val compactStreams: Boolean = false,
    /** `player_use_video_positions`: show how much of a video was watched. */
    val usePositions: Boolean = true,
    /** Fixed in screenshot tests (plan 10.2: fixed clock). */
    val nowMillis: () -> Long = System::currentTimeMillis,
    /** What to load for an image URL; screenshot tests swap in fixed pictures (plan 10.2: fixed image data). */
    val image: (String?) -> Any? = { it },
)

@Immutable
data class FollowingAccount(val loggedIn: Boolean, val name: String?)

@Immutable
data class VideoSortChoice(val sort: String = VideosSortDialog.SORT_TIME, val type: String = VideosSortDialog.VIDEO_TYPE_ALL)

@Immutable
data class ChannelSortChoice(val sort: String = FollowedChannelsSorts.SORT_LAST_BROADCAST, val order: String = FollowedChannelsSorts.ORDER_DESC)

/** What the Following page can ask its host to do. */
interface FollowingActions {
    fun search()
    fun settings()
    fun logIn()
    fun logOut()
    fun openStream(stream: Stream)
    fun openChannel(id: String?, login: String?, name: String?, image: String?, streamId: String? = null)
    fun openGame(id: String?, slug: String?, name: String?, updateLocal: Boolean = false)
    fun openStreamTag(tag: String)
    fun openGameTag(tag: GameTag)
    fun record(stream: Stream)
    fun share(url: String, title: String?)
    fun unfollow(channelId: String?, name: String)
    fun openVideo(video: Video, position: Long?)
    fun download(video: Video)
    fun toggleBookmark(video: Video)
    fun applyVideoSort(choice: VideoSortChoice, saveDefault: Boolean)
    fun applyChannelSort(choice: ChannelSortChoice, saveDefault: Boolean)
    fun integrityCheckFailed()
}

/** The lists the page shows; a segment that is switched off or hidden passes null. */
class FollowingLists(
    val streams: LazyPagingItems<Stream>?,
    val videos: LazyPagingItems<Video>?,
    val channels: LazyPagingItems<User>?,
    val games: LazyPagingItems<Game>?,
    /** Channels by last broadcast, for the empty live segment. */
    val recentlyLive: LazyPagingItems<User>?,
)

/** "Name(login)", name or login, as the `ui_name_display` setting asks (same rule as the v0.1 list items). */
fun displayName(name: String?, login: String?, mode: String): String = when {
    name == null -> login ?: ""
    login != null && !login.equals(name, true) -> when (mode) {
        "0" -> "$name($login)"
        "1" -> name
        else -> login
    }
    else -> name
}

private fun liveColumns(widthClass: WidthClass, widthDp: Int) = when (widthClass) {
    WidthClass.Expanded -> if (widthDp >= 1200) 4 else 3
    else -> widthClass.cardColumns
}

private fun gameColumns(widthClass: WidthClass) = when (widthClass) {
    WidthClass.Compact -> 3
    WidthClass.Medium -> 4
    WidthClass.Expanded -> 6
}

private val LoadState.integrityFailed get() = (this as? LoadState.Error)?.error?.message == com.github.andreyasadchy.xtra.util.C.FAILED_INTEGRITY_CHECK

/**
 * The Following page (plan 7.1, design "B · 关注"): large title with search and account on the right, segments as a
 * pill control, then the chosen list. Live shows a row of live avatars and live cards, an empty state with recently
 * live channels when nobody streams; every card opens an action sheet on long press.
 *
 * @param bottomPadding room under the content for the floating navigation (the page draws under it).
 * @param navScroll the shell's hide-on-scroll connection, null when the navigation stays put.
 * @param scrollToTopRequests a counter; every change scrolls the shown list to the top (tap on the current tab).
 */
@Composable
fun FollowingScreen(
    segments: List<FollowingSegment>,
    selected: FollowingSegment,
    onSelectSegment: (FollowingSegment) -> Unit,
    account: FollowingAccount,
    lists: FollowingLists,
    options: FollowingListOptions,
    videoSort: VideoSortChoice,
    channelSort: ChannelSortChoice,
    videoPositions: Map<Long, Long>,
    bookmarkedVideoIds: Set<String>,
    actions: FollowingActions,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    navScroll: NestedScrollConnection? = null,
    scrollToTopRequests: Int = 0,
    toast: ToastHostState? = null,
) {
    val colors = Lagoon.colors
    val motion = Lagoon.motion
    val density = LocalDensity.current
    val topBar = rememberTopBarState()
    var accountSheet by rememberSaveable { mutableStateOf(false) }
    var logOutSheet by rememberSaveable { mutableStateOf(false) }
    val gridStates = FollowingSegment.entries.associateWith { rememberLazyGridState() }

    LaunchedEffect(scrollToTopRequests) {
        if (scrollToTopRequests > 0) {
            topBar.snapTo(0f)
            gridStates[selected]?.animateScrollToItem(0)
        }
    }
    // v0.1 asked for a new integrity token from the list fragments when a page failed the check
    listOfNotNull(lists.streams, lists.videos, lists.channels, lists.games).forEach { items ->
        val failed = items.loadState.refresh.integrityFailed || items.loadState.append.integrityFailed
        LaunchedEffect(items, failed) { if (failed) actions.integrityCheckFailed() }
    }

    Box(modifier.fillMaxSize().background(colors.page)) {
    Column(
        Modifier
            .fillMaxSize()
            .then(if (navScroll != null) Modifier.nestedScroll(navScroll) else Modifier)
            .nestedScroll(topBar.nestedScrollConnection)
    ) {
        LargeTopBar(
            title = stringResource(R.string.following),
            state = topBar,
            actions = {
                LagoonIconButton(LucideIcons.Search, stringResource(R.string.search), actions::search)
                LagoonIconButton(LucideIcons.User, stringResource(R.string.following_account), { accountSheet = true })
            },
        )
        if (segments.size > 1) {
            SegmentedControl(
                options = segments.map { stringResource(it.label) },
                selectedIndex = segments.indexOf(selected).coerceAtLeast(0),
                onSelect = { onSelectSegment(segments[it]) },
                modifier = Modifier
                    .padding(horizontal = LagoonSpacing.pageSide, vertical = LagoonSpacing.xs)
                    .widthIn(max = 560.dp),
            )
        }
        AnimatedContent(
            targetState = selected,
            transitionSpec = { siblingBy(motion, density) { segments.indexOf(it) } },
            label = "followingSegment",
            modifier = Modifier.fillMaxSize(),
        ) { segment ->
            val state = gridStates.getValue(segment)
            when (segment) {
                FollowingSegment.Live -> LiveSegment(lists.streams, lists.recentlyLive, account, options, actions, state, bottomPadding)
                FollowingSegment.Videos -> VideosSegment(lists.videos, options, videoSort, videoPositions, bookmarkedVideoIds, actions, state, bottomPadding)
                FollowingSegment.Channels -> ChannelsSegment(lists.channels, options, channelSort, actions, state, bottomPadding)
                FollowingSegment.Games -> GamesSegment(lists.games, options, actions, state, bottomPadding)
            }
        }
    }
    if (toast != null) {
        ToastHost(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding))
    }
    }

    if (accountSheet) {
        AccountSheet(account, actions, onDismiss = { accountSheet = false }, onLogOut = { logOutSheet = true })
    }
    if (logOutSheet) {
        ConfirmSheet(
            title = stringResource(R.string.following_log_out_confirm, account.name ?: ""),
            message = stringResource(R.string.following_log_out_message),
            confirm = stringResource(R.string.following_log_out),
            onConfirm = actions::logOut,
            onDismiss = { logOutSheet = false },
        )
    }
}

private val FollowingSegment.label: Int
    get() = when (this) {
        FollowingSegment.Live -> R.string.live
        FollowingSegment.Videos -> R.string.videos
        FollowingSegment.Channels -> R.string.channels
        FollowingSegment.Games -> R.string.games
    }

// ---------------------------------------------------------------------------------------------------------------------
// Shared list scaffolding

/**
 * One segment's list: pull to refresh around a grid, the first-load skeleton, the error and empty states, and the
 * footer while the next page loads or fails.
 */
@Composable
private fun <T : Any> SegmentGrid(
    items: LazyPagingItems<T>?,
    columns: Int,
    state: LazyGridState,
    bottomPadding: Dp,
    skeleton: @Composable (Int) -> Unit,
    empty: LazyGridScope.() -> Unit,
    header: (LazyGridScope.() -> Unit)? = null,
    gap: Dp = LagoonSpacing.cardGap,
    content: LazyGridScope.(LazyPagingItems<T>) -> Unit,
) {
    val refresh = items?.loadState?.refresh
    val first = items == null || items.itemCount == 0
    LagoonPullToRefresh(
        isRefreshing = refresh is LoadState.Loading && !first,
        onRefresh = { items?.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LagoonSpacing.pageSide, end = LagoonSpacing.pageSide, top = LagoonSpacing.xs,
                bottom = bottomPadding + LagoonSpacing.m,
            ),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            header?.invoke(this)
            when {
                items == null -> empty()
                first && refresh is LoadState.Loading -> items(6, contentType = { "skeleton" }) { skeleton(it) }
                first && refresh is LoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }, contentType = "error") {
                    ErrorState(
                        message = refresh.error.message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.no_connection),
                        onRetry = { items.retry() },
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
                first -> empty()
                else -> {
                    content(items)
                    when (items.loadState.append) {
                        is LoadState.Loading -> item(span = { GridItemSpan(maxLineSpan) }, contentType = "more") {
                            Box(Modifier.fillMaxWidth().height(4.dp).shimmer(LagoonShapes.pill))
                        }
                        is LoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }, contentType = "more") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                LagoonButton(
                                    stringResource(R.string.following_retry_more), { items.retry() },
                                    style = ButtonStyle.Secondary, icon = LucideIcons.RotateCcw,
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

/** Long-press actions also reachable by screen readers (plan 10.5). */
private fun Modifier.actionsForAccessibility(actions: List<ActionItem>) = semantics {
    customActions = actions.map { CustomAccessibilityAction(it.label) { it.onClick(); true } }
}

// ---------------------------------------------------------------------------------------------------------------------
// Live

@Composable
private fun LiveSegment(
    streams: LazyPagingItems<Stream>?,
    recentlyLive: LazyPagingItems<User>?,
    account: FollowingAccount,
    options: FollowingListOptions,
    actions: FollowingActions,
    state: LazyGridState,
    bottomPadding: Dp,
) {
    val configuration = LocalConfiguration.current
    val columns = liveColumns(WidthClass.of(configuration.screenWidthDp), configuration.screenWidthDp)
    var menuFor by remember { mutableStateOf<Stream?>(null) }
    SegmentGrid(
        items = streams,
        columns = columns,
        state = state,
        bottomPadding = bottomPadding,
        skeleton = { SkeletonLiveCard() },
        empty = {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                EmptyState(
                    message = stringResource(if (account.loggedIn) R.string.following_empty_live else R.string.following_empty_live_logged_out),
                    icon = LucideIcons.Radio,
                    actionLabel = if (account.loggedIn) null else stringResource(R.string.log_in),
                    onAction = if (account.loggedIn) null else actions::logIn,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            if (recentlyLive != null && recentlyLive.itemCount > 0) {
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "recent") {
                    RecentlyLive(recentlyLive, options, actions)
                }
            }
        },
    ) { items ->
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "liveNow") {
            LiveNowRow(items, options, actions)
        }
        items(items.itemCount, contentType = { "live" }) { index ->
            val stream = items[index] ?: return@items
            val menu = streamActions(stream, options, actions)
            val viewers = TwitchApiHelper.formatCount(stream.viewerCount ?: 0, options.truncateCounts)
            val uptime = if (options.showUptime) uptime(stream.createdAt, options.nowMillis()) else null
            val name = displayName(stream.channelName, stream.channelLogin, options.nameDisplay)
            val cardModifier = Modifier.staggeredAppear(index).actionsForAccessibility(menu)
            if (options.compactStreams) {
                LiveCardCompact(
                    title = stream.title?.trim().orEmpty(), channelName = name, viewers = viewers,
                    onClick = { actions.openStream(stream) }, modifier = cardModifier, thumbnail = options.image(stream.thumbnail),
                    gameName = stream.gameName, uptime = uptime, onLongClick = { menuFor = stream },
                )
            } else {
                LiveCard(
                    title = stream.title?.trim().orEmpty(), channelName = name, viewers = viewers,
                    onClick = { actions.openStream(stream) }, modifier = cardModifier, thumbnail = options.image(stream.thumbnail),
                    avatar = options.image(stream.channelImage), gameName = stream.gameName, uptime = uptime,
                    tags = if (options.showTags) stream.tags.orEmpty() else emptyList(), onLongClick = { menuFor = stream },
                )
            }
        }
    }
    menuFor?.let { stream ->
        ActionSheet(
            streamActions(stream, options, actions),
            onDismiss = { menuFor = null },
            title = displayName(stream.channelName, stream.channelLogin, options.nameDisplay),
        )
    }
}

/** The card's long-press menu (plan 7.1), plus the jumps v0.1 had as taps on the avatar, game and tags. */
@Composable
private fun streamActions(stream: Stream, options: FollowingListOptions, actions: FollowingActions): List<ActionItem> {
    val name = displayName(stream.channelName, stream.channelLogin, options.nameDisplay)
    val list = mutableListOf(
        ActionItem(stringResource(R.string.following_open_channel), LucideIcons.User) {
            actions.openChannel(stream.channelId, stream.channelLogin, stream.channelName, stream.channelImage, stream.id)
        },
    )
    stream.gameName?.let { game ->
        list += ActionItem(stringResource(R.string.following_open_game, game), LucideIcons.Gamepad2) {
            actions.openGame(stream.gameId, stream.gameSlug, stream.gameName)
        }
    }
    if (options.showTags) {
        stream.tags.orEmpty().take(3).forEach { tag ->
            list += ActionItem(stringResource(R.string.following_browse_tag, tag), LucideIcons.ListFilter) { actions.openStreamTag(tag) }
        }
    }
    list += ActionItem(stringResource(R.string.following_record), LucideIcons.CircleDot) { actions.record(stream) }
    list += ActionItem(stringResource(R.string.share), LucideIcons.Share2) {
        actions.share("https://twitch.tv/${stream.channelLogin}", stream.title)
    }
    list += ActionItem(stringResource(R.string.unfollow), LucideIcons.Heart, destructive = true) {
        actions.unfollow(stream.channelId, name)
    }
    return list
}

/** The row of live avatars at the top (plan 7.1): a tap goes straight into the stream. */
@Composable
private fun LiveNowRow(items: LazyPagingItems<Stream>, options: FollowingListOptions, actions: FollowingActions) {
    val streams = items.itemSnapshotList.items
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.following_live_now_count, streams.size),
            style = Lagoon.type.labelStrong,
            color = Lagoon.colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).semantics { heading() },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.xs)) {
            items(streams, contentType = { "avatar" }) { stream ->
                val name = displayName(stream.channelName, stream.channelLogin, "1")
                Column(
                    Modifier
                        .width(72.dp)
                        .pressable(onClick = { actions.openStream(stream) }, shape = LagoonShapes.row)
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(options.image(stream.channelImage), name, size = 52.dp, live = true)
                    Text(
                        name, style = Lagoon.type.caption, color = Lagoon.colors.textSecondary, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
                    )
                }
            }
        }
    }
}

/** "Recently live" under the empty state: the channels that streamed last, newest first. */
@Composable
private fun RecentlyLive(items: LazyPagingItems<User>, options: FollowingListOptions, actions: FollowingActions) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Lagoon.colors.card, LagoonShapes.card)
            .padding(vertical = 8.dp)
    ) {
        Text(
            stringResource(R.string.following_recently_live),
            style = Lagoon.type.bodyStrong,
            color = Lagoon.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
        )
        val count = minOf(items.itemCount, 8)
        for (index in 0 until count) {
            val user = items[index] ?: continue
            val name = displayName(user.name, user.login, options.nameDisplay)
            ChannelRow(
                name = name,
                subtitle = user.lastBroadcast?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds()?.takeIf { it > 0 }?.let {
                    stringResource(R.string.last_broadcast_date, TwitchApiHelper.formatDate(context, it))
                },
                onClick = { actions.openChannel(user.id, user.login, user.name, user.profileImage) },
                avatar = options.image(user.profileImage),
                live = user.isLive == true,
            )
        }
    }
}

private fun uptime(createdAt: String?, now: Long): String? =
    createdAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds()?.takeIf { it > 0 }?.let { start ->
        val seconds = (now - start) / 1000
        if (seconds > 0) DateUtils.formatElapsedTime(seconds) else null
    }

// ---------------------------------------------------------------------------------------------------------------------
// Videos

@Composable
private fun VideosSegment(
    videos: LazyPagingItems<Video>?,
    options: FollowingListOptions,
    sort: VideoSortChoice,
    positions: Map<Long, Long>,
    bookmarked: Set<String>,
    actions: FollowingActions,
    state: LazyGridState,
    bottomPadding: Dp,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val columns = liveColumns(WidthClass.of(configuration.screenWidthDp), configuration.screenWidthDp)
    var menuFor by remember { mutableStateOf<Video?>(null) }
    var sortSheet by rememberSaveable { mutableStateOf(false) }
    val sortLabel = stringResource(if (sort.sort == VideosSortDialog.SORT_VIEWS) R.string.view_count else R.string.upload_date)
    val typeLabel = stringResource(videoTypeLabel(sort.type))
    SegmentGrid(
        items = videos,
        columns = columns,
        state = state,
        bottomPadding = bottomPadding,
        skeleton = { SkeletonLiveCard() },
        header = {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "chips") {
                FilterChipRow(
                    listOf(
                        FilterChipItem(sortLabel, icon = LucideIcons.ArrowUpDown, dropdown = true, onClick = { sortSheet = true }),
                        FilterChipItem(typeLabel, icon = LucideIcons.Film, dropdown = true, onClick = { sortSheet = true }),
                    ),
                    contentPadding = PaddingValues(0.dp),
                )
            }
        },
        empty = {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                EmptyState(stringResource(R.string.following_empty_videos), icon = LucideIcons.Film, modifier = Modifier.padding(top = 24.dp))
            }
        },
    ) { items ->
        items(items.itemCount, contentType = { "video" }) { index ->
            val video = items[index] ?: return@items
            val position = if (options.usePositions) video.id?.toLongOrNull()?.let { positions[it] } else null
            val duration = video.durationSeconds
            val meta = listOfNotNull(
                displayName(video.channelName, video.channelLogin, options.nameDisplay).takeIf { it.isNotEmpty() },
                TwitchApiHelper.getType(context, video.type),
                video.createdAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds()?.takeIf { it > 0 }?.let { TwitchApiHelper.formatDate(context, it) },
                video.viewCount?.let { pluralStringResource(R.plurals.views, it, TwitchApiHelper.formatCount(it, options.truncateCounts)) },
            ).joinToString(" · ")
            val menu = videoActions(video, bookmarked, actions)
            VideoCard(
                title = video.title?.trim().orEmpty(),
                meta = meta,
                onClick = { actions.openVideo(video, position) },
                modifier = Modifier.staggeredAppear(index).actionsForAccessibility(menu),
                thumbnail = options.image(video.thumbnail),
                duration = duration?.let { DateUtils.formatElapsedTime(it.toLong()) },
                progress = if (position != null && duration != null && duration > 0) (position / (duration * 1000f)).coerceIn(0f, 1f) else null,
                onLongClick = { menuFor = video },
            )
        }
    }
    menuFor?.let { video ->
        ActionSheet(videoActions(video, bookmarked, actions), onDismiss = { menuFor = null }, title = video.title?.trim())
    }
    if (sortSheet) {
        VideoSortSheet(sort, onApply = actions::applyVideoSort, onDismiss = { sortSheet = false })
    }
}

/** v0.1's video item menu (download, bookmark, share) plus the jumps it had as taps on the channel and game. */
@Composable
private fun videoActions(video: Video, bookmarked: Set<String>, actions: FollowingActions): List<ActionItem> {
    val list = mutableListOf(
        ActionItem(stringResource(R.string.download), LucideIcons.Download) { actions.download(video) },
    )
    if (!video.id.isNullOrBlank()) {
        val isBookmarked = video.id in bookmarked
        list += ActionItem(
            stringResource(if (isBookmarked) R.string.remove_bookmark else R.string.add_bookmark),
            if (isBookmarked) LucideIcons.BookmarkFilled else LucideIcons.Bookmark,
        ) { actions.toggleBookmark(video) }
    }
    list += ActionItem(stringResource(R.string.share), LucideIcons.Share2) {
        actions.share("https://twitch.tv/videos/${video.id}", video.title)
    }
    if (video.channelId != null || video.channelLogin != null) {
        list += ActionItem(stringResource(R.string.following_open_channel), LucideIcons.User) {
            actions.openChannel(video.channelId, video.channelLogin, video.channelName, video.channelImage)
        }
    }
    video.gameName?.let { game ->
        list += ActionItem(stringResource(R.string.following_open_game, game), LucideIcons.Gamepad2) {
            actions.openGame(video.gameId, video.gameSlug, video.gameName)
        }
    }
    return list
}

private fun videoTypeLabel(type: String) = when (type) {
    VideosSortDialog.VIDEO_TYPE_ARCHIVE -> R.string.video_type_archive
    VideosSortDialog.VIDEO_TYPE_HIGHLIGHT -> R.string.video_type_highlight
    VideosSortDialog.VIDEO_TYPE_UPLOAD -> R.string.video_type_upload
    else -> R.string.all
}

/** Replaces VideosSortDialog for followed videos: sort and type (v0.1 hid period and languages here). */
@Composable
private fun VideoSortSheet(current: VideoSortChoice, onApply: (VideoSortChoice, Boolean) -> Unit, onDismiss: () -> Unit) {
    val sorts = listOf(VideosSortDialog.SORT_TIME, VideosSortDialog.SORT_VIEWS)
    val types = listOf(VideosSortDialog.VIDEO_TYPE_ALL, VideosSortDialog.VIDEO_TYPE_ARCHIVE, VideosSortDialog.VIDEO_TYPE_HIGHLIGHT, VideosSortDialog.VIDEO_TYPE_UPLOAD)
    var sort by rememberSaveable { mutableStateOf(current.sort) }
    var type by rememberSaveable { mutableStateOf(current.type) }
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.sort), skipHalfHeight = true) {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        SheetSection(stringResource(R.string.sort))
        SingleChoiceList(
            listOf(ChoiceOption(stringResource(R.string.upload_date)), ChoiceOption(stringResource(R.string.view_count))),
            selected = sorts.indexOf(sort).coerceAtLeast(0),
            onSelect = { sort = sorts[it] },
        )
        SheetSection(stringResource(R.string.type))
        SingleChoiceList(
            types.map { ChoiceOption(stringResource(videoTypeLabel(it))) },
            selected = types.indexOf(type).coerceAtLeast(0),
            onSelect = { type = types[it] },
        )
        SheetButtons(
            onSaveDefault = { onApply(VideoSortChoice(sort, type), true); onDismiss() },
            onApply = { onApply(VideoSortChoice(sort, type), false); onDismiss() },
        )
      }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Channels

@Composable
private fun ChannelsSegment(
    channels: LazyPagingItems<User>?,
    options: FollowingListOptions,
    sort: ChannelSortChoice,
    actions: FollowingActions,
    state: LazyGridState,
    bottomPadding: Dp,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val columns = WidthClass.of(configuration.screenWidthDp).cardColumns
    var menuFor by remember { mutableStateOf<User?>(null) }
    var sortSheet by rememberSaveable { mutableStateOf(false) }
    val sortLabel = stringResource(channelSortLabel(sort.sort))
    val orderLabel = stringResource(if (sort.order == FollowedChannelsSorts.ORDER_ASC) R.string.ascending else R.string.descending)
    SegmentGrid(
        items = channels,
        columns = columns,
        state = state,
        bottomPadding = bottomPadding,
        gap = LagoonSpacing.xs,
        skeleton = { SkeletonRow(Modifier.background(Lagoon.colors.card, LagoonShapes.row)) },
        header = {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "chips") {
                FilterChipRow(
                    listOf(
                        FilterChipItem(sortLabel, icon = LucideIcons.ArrowUpDown, dropdown = true, onClick = { sortSheet = true }),
                        FilterChipItem(orderLabel, dropdown = true, onClick = { sortSheet = true }),
                    ),
                    contentPadding = PaddingValues(0.dp),
                )
            }
        },
        empty = {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                EmptyState(stringResource(R.string.following_empty_channels), icon = LucideIcons.Users, modifier = Modifier.padding(top = 24.dp))
            }
        },
    ) { items ->
        items(items.itemCount, contentType = { "channel" }) { index ->
            val user = items[index] ?: return@items
            val name = displayName(user.name, user.login, options.nameDisplay)
            val lastBroadcast = user.lastBroadcast?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds()?.takeIf { it > 0 }
            val followedAt = user.followedAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds()?.takeIf { it > 0 }
            val subtitle = when {
                lastBroadcast != null -> stringResource(R.string.last_broadcast_date, TwitchApiHelper.formatDate(context, lastBroadcast))
                followedAt != null -> stringResource(R.string.followed_at, TwitchApiHelper.formatDate(context, followedAt))
                else -> null
            }
            val menu = channelActions(user, name, actions)
            ChannelRow(
                name = name,
                subtitle = subtitle,
                onClick = { actions.openChannel(user.id, user.login, user.name, user.profileImage) },
                modifier = Modifier
                    .background(Lagoon.colors.card, LagoonShapes.row)
                    .staggeredAppear(index)
                    .actionsForAccessibility(menu),
                avatar = options.image(user.profileImage),
                live = user.isLive == true,
                onLongClick = { menuFor = user },
                trailing = if (user.accountFollow || user.localFollow) {
                    {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (user.accountFollow) Tag(stringResource(R.string.following_follow_account), quiet = true)
                            if (user.localFollow) Tag(stringResource(R.string.following_follow_local), quiet = true)
                        }
                    }
                } else null,
            )
        }
    }
    menuFor?.let { user ->
        val name = displayName(user.name, user.login, options.nameDisplay)
        ActionSheet(channelActions(user, name, actions), onDismiss = { menuFor = null }, title = name)
    }
    if (sortSheet) {
        ChannelSortSheet(sort, onApply = actions::applyChannelSort, onDismiss = { sortSheet = false })
    }
}

@Composable
private fun channelActions(user: User, name: String, actions: FollowingActions): List<ActionItem> {
    val list = mutableListOf(
        ActionItem(stringResource(R.string.following_open_channel), LucideIcons.User) {
            actions.openChannel(user.id, user.login, user.name, user.profileImage)
        },
        ActionItem(stringResource(R.string.share), LucideIcons.Share2) { actions.share("https://twitch.tv/${user.login}", name) },
    )
    if (user.accountFollow || user.localFollow) {
        list += ActionItem(stringResource(R.string.unfollow), LucideIcons.Heart, destructive = true) { actions.unfollow(user.id, name) }
    }
    return list
}

private fun channelSortLabel(sort: String) = when (sort) {
    FollowedChannelsSorts.SORT_FOLLOWED_AT -> R.string.time_followed
    FollowedChannelsSorts.SORT_ALPHABETICALLY -> R.string.alphabetically
    else -> R.string.last_broadcast
}

/** Replaces FollowedChannelsSortDialog: sort by last broadcast, time followed or name, in either order. */
@Composable
private fun ChannelSortSheet(current: ChannelSortChoice, onApply: (ChannelSortChoice, Boolean) -> Unit, onDismiss: () -> Unit) {
    val sorts = listOf(FollowedChannelsSorts.SORT_LAST_BROADCAST, FollowedChannelsSorts.SORT_FOLLOWED_AT, FollowedChannelsSorts.SORT_ALPHABETICALLY)
    val orders = listOf(FollowedChannelsSorts.ORDER_DESC, FollowedChannelsSorts.ORDER_ASC)
    var sort by rememberSaveable { mutableStateOf(current.sort) }
    var order by rememberSaveable { mutableStateOf(current.order) }
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.sort), skipHalfHeight = true) {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        SheetSection(stringResource(R.string.sort))
        SingleChoiceList(
            sorts.map { ChoiceOption(stringResource(channelSortLabel(it))) },
            selected = sorts.indexOf(sort).coerceAtLeast(0),
            onSelect = { sort = sorts[it] },
        )
        SheetSection(stringResource(R.string.order))
        SingleChoiceList(
            listOf(ChoiceOption(stringResource(R.string.descending)), ChoiceOption(stringResource(R.string.ascending))),
            selected = orders.indexOf(order).coerceAtLeast(0),
            onSelect = { order = orders[it] },
        )
        SheetButtons(
            onSaveDefault = { onApply(ChannelSortChoice(sort, order), true); onDismiss() },
            onApply = { onApply(ChannelSortChoice(sort, order), false); onDismiss() },
        )
      }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Games

@Composable
private fun GamesSegment(
    games: LazyPagingItems<Game>?,
    options: FollowingListOptions,
    actions: FollowingActions,
    state: LazyGridState,
    bottomPadding: Dp,
) {
    val columns = gameColumns(WidthClass.of(LocalConfiguration.current.screenWidthDp))
    var menuFor by remember { mutableStateOf<Game?>(null) }
    SegmentGrid(
        items = games,
        columns = columns,
        state = state,
        bottomPadding = bottomPadding,
        skeleton = { Box(Modifier.fillMaxWidth().height(180.dp).shimmer(LagoonShapes.card)) },
        empty = {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                EmptyState(stringResource(R.string.following_empty_games), icon = LucideIcons.Gamepad2, modifier = Modifier.padding(top = 24.dp))
            }
        },
    ) { items ->
        items(items.itemCount, contentType = { "game" }) { index ->
            val game = items[index] ?: return@items
            val menu = gameActions(game, options, actions)
            GamePoster(
                name = game.name.orEmpty(),
                onClick = { actions.openGame(game.id, game.slug, game.name, game.localFollow) },
                modifier = Modifier.staggeredAppear(index).actionsForAccessibility(menu),
                boxArt = options.image(game.boxArt),
                viewers = game.viewerCount?.let { TwitchApiHelper.formatCount(it, options.truncateCounts) },
                subtitle = if (options.showBroadcasters) game.broadcasterCount?.let {
                    pluralStringResource(R.plurals.broadcasters, it, TwitchApiHelper.formatCount(it, options.truncateCounts))
                } else null,
                onLongClick = { menuFor = game },
            )
        }
    }
    menuFor?.let { game ->
        ActionSheet(gameActions(game, options, actions), onDismiss = { menuFor = null }, title = game.name)
    }
}

@Composable
private fun gameActions(game: Game, options: FollowingListOptions, actions: FollowingActions): List<ActionItem> {
    val list = mutableListOf(
        ActionItem(stringResource(R.string.following_open_game, game.name.orEmpty()), LucideIcons.Gamepad2) {
            actions.openGame(game.id, game.slug, game.name, game.localFollow)
        },
    )
    if (options.showTags) {
        game.tags.orEmpty().filter { it.id != null }.take(4).forEach { tag ->
            list += ActionItem(stringResource(R.string.following_browse_tag, tag.name.orEmpty()), LucideIcons.ListFilter) { actions.openGameTag(tag) }
        }
    }
    return list
}

// ---------------------------------------------------------------------------------------------------------------------
// Sheets

@Composable
private fun AccountSheet(account: FollowingAccount, actions: FollowingActions, onDismiss: () -> Unit, onLogOut: () -> Unit) {
    val items = buildList {
        add(ActionItem(stringResource(R.string.settings), LucideIcons.Settings) { actions.settings() })
        if (account.loggedIn) {
            add(ActionItem(stringResource(R.string.following_log_out), LucideIcons.LogOut, destructive = true) { onLogOut() })
        } else {
            add(ActionItem(stringResource(R.string.log_in), LucideIcons.LogIn) { actions.logIn() })
        }
    }
    ActionSheet(
        items,
        onDismiss = onDismiss,
        title = if (account.loggedIn) stringResource(R.string.following_logged_in_as, account.name.orEmpty()) else stringResource(R.string.following_not_logged_in),
    )
}

/** A question with one dangerous answer (log out, unfollow), as a sheet instead of a system dialog. */
@Composable
fun ConfirmSheet(title: String, message: String?, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    LagoonBottomSheet(onDismiss = onDismiss, title = title, skipHalfHeight = true) {
        if (message != null) {
            Text(
                message, style = Lagoon.type.body, color = Lagoon.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
        ) {
            LagoonButton(stringResource(R.string.following_cancel), onDismiss, Modifier.weight(1f), style = ButtonStyle.Secondary)
            LagoonButton(confirm, { onDismiss(); onConfirm() }, Modifier.weight(1f), style = ButtonStyle.Danger)
        }
    }
}

@Composable
private fun SheetSection(text: String) {
    Text(
        text, style = Lagoon.type.labelStrong, color = Lagoon.colors.textSecondary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun SheetButtons(onSaveDefault: () -> Unit, onApply: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
    ) {
        LagoonButton(stringResource(R.string.save_default), onSaveDefault, Modifier.weight(1f), style = ButtonStyle.Secondary)
        LagoonButton(stringResource(R.string.apply), onApply, Modifier.weight(1f))
    }
}
