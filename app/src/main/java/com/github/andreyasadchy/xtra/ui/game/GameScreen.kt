package com.github.andreyasadchy.xtra.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.browse.BrowseActions
import com.github.andreyasadchy.xtra.ui.browse.BrowseFilter
import com.github.andreyasadchy.xtra.ui.browse.ClipItem
import com.github.andreyasadchy.xtra.ui.browse.DetailTopBar
import com.github.andreyasadchy.xtra.ui.browse.DetailTopBarHeight
import com.github.andreyasadchy.xtra.ui.browse.FilterKind
import com.github.andreyasadchy.xtra.ui.browse.FilterLogic
import com.github.andreyasadchy.xtra.ui.browse.FilterSaves
import com.github.andreyasadchy.xtra.ui.browse.FilterSheet
import com.github.andreyasadchy.xtra.ui.browse.FilterSheetConfig
import com.github.andreyasadchy.xtra.ui.browse.IntegrityWatch
import com.github.andreyasadchy.xtra.ui.browse.StreamItem
import com.github.andreyasadchy.xtra.ui.browse.VideoItem
import com.github.andreyasadchy.xtra.ui.browse.cardColumns
import com.github.andreyasadchy.xtra.ui.browse.clipMenu
import com.github.andreyasadchy.xtra.ui.browse.filterChips
import com.github.andreyasadchy.xtra.ui.browse.pagedItems
import com.github.andreyasadchy.xtra.ui.browse.rememberCoverColor
import com.github.andreyasadchy.xtra.ui.browse.streamMenu
import com.github.andreyasadchy.xtra.ui.browse.videoMenu
import com.github.andreyasadchy.xtra.ui.design.components.StaggerGate
import com.github.andreyasadchy.xtra.ui.design.components.ActionSheet
import com.github.andreyasadchy.xtra.ui.design.components.EmptyState
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipRow
import com.github.andreyasadchy.xtra.ui.design.components.FollowButton
import com.github.andreyasadchy.xtra.ui.design.components.GlassBackdrop
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonImage
import com.github.andreyasadchy.xtra.ui.design.components.LagoonPullToRefresh
import com.github.andreyasadchy.xtra.ui.design.components.LiveDot
import com.github.andreyasadchy.xtra.ui.design.components.SegmentedControl
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonLiveCard
import com.github.andreyasadchy.xtra.ui.design.components.ToastHost
import com.github.andreyasadchy.xtra.ui.design.components.ToastHostState
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.following.ConfirmSheet
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.ui.browse.LocalBrowseOffline
import com.github.andreyasadchy.xtra.ui.main.shell.ShellTabs
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper

/** The segments of a game page (setting `ui_game_tabs`, "key:default:enabled" like the navigation list). */
enum class GameSegment(val key: String, val kind: FilterKind) {
    Videos("0", FilterKind.Videos), Live("1", FilterKind.Streams), Clips("2", FilterKind.Clips);

    companion object {
        fun of(key: String) = entries.find { it.key == key }

        fun list(pref: String?): List<GameSegment> =
            ShellTabs.normalize(pref, C.DEFAULT_GAME_TABS).map { it.split(':') }.mapNotNull { split ->
                if (split.getOrNull(2) == "0") null else of(split[0])
            }.ifEmpty { listOf(Live) }

        /** v0.1: the tab marked as default, else Live, else the first. */
        fun default(pref: String?, segments: List<GameSegment>): GameSegment {
            val key = ShellTabs.normalize(pref, C.DEFAULT_GAME_TABS).map { it.split(':') }.find { it.getOrNull(1) != "0" }?.get(0)
            return of(key ?: "")?.takeIf { it in segments } ?: Live.takeIf { it in segments } ?: segments.first()
        }
    }
}

/** What the header shows: from the link first, completed by the game's own data when it arrives. */
@Immutable
data class GameHeader(
    val id: String?,
    val slug: String?,
    val name: String?,
    val boxArt: String?,
    val viewers: Int? = null,
    val broadcasters: Int? = null,
    val followers: Int? = null,
    val tags: List<Tag> = emptyList(),
)

/** The three lists of a game page; a segment that is switched off passes null. */
class GameLists(
    val streams: LazyPagingItems<Stream>?,
    val videos: LazyPagingItems<Video>?,
    val clips: LazyPagingItems<Clip>?,
)

@Immutable
data class GameFilters(
    val streams: BrowseFilter,
    val videos: BrowseFilter,
    val clips: BrowseFilter,
    val streamsConfig: FilterSheetConfig,
    val videosConfig: FilterSheetConfig,
    val clipsConfig: FilterSheetConfig,
) {
    fun of(kind: FilterKind) = when (kind) {
        FilterKind.Videos -> videos
        FilterKind.Clips -> clips
        else -> streams
    }

    fun configOf(kind: FilterKind) = when (kind) {
        FilterKind.Videos -> videosConfig
        FilterKind.Clips -> clipsConfig
        else -> streamsConfig
    }
}

/** What the game page asks its host for besides the list actions. */
interface GameActions : BrowseActions {
    fun applyFilter(kind: FilterKind, filter: BrowseFilter, saves: FilterSaves)
    fun setFollowing(follow: Boolean)
    /** Games with this tag (v0.1: the tags under the game's name). */
    fun openGameTag(tag: Tag)
    fun tagQuery(query: String)
}

/** Test tags. */
const val GameListTag = "gameList"
const val GameSegmentsTag = "gameSegments"
const val GameHeaderTag = "gameHeader"

/**
 * A game's page (plan 7.2, U4): the box art and its colour behind the name, viewer, broadcaster and follower counts,
 * the game's tags and the follow button; below it the live, video and clip segments on floating glass tabs that pin
 * under the bar, each with its filter chips. One grid holds header and list, so the header scrolls away with it.
 *
 * @param boxArtModifier marks the box art as the shared element of the poster-to-page transition.
 * @param showFollow the `ui_follow_button` setting allows following games; [following] is null until known.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameScreen(
    header: GameHeader,
    segments: List<GameSegment>,
    selected: GameSegment,
    onSelect: (GameSegment) -> Unit,
    lists: GameLists,
    filters: GameFilters,
    options: FollowingListOptions,
    compactStreams: Boolean,
    actions: GameActions,
    modifier: Modifier = Modifier,
    showFollow: Boolean = true,
    following: Boolean? = null,
    tagResults: LazyPagingItems<Tag>? = null,
    videoPositions: Map<Long, Long> = emptyMap(),
    bookmarkedVideoIds: Set<String> = emptySet(),
    boxArtModifier: Modifier = Modifier,
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    navScroll: NestedScrollConnection? = null,
    scrollToTopRequests: Int = 0,
    toast: ToastHostState? = null,
    offline: Boolean = false,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val columns = cardColumns(configuration.screenWidthDp)
    val grid = rememberLazyGridState()
    var headerHeight by remember { mutableIntStateOf(0) }
    var filterSheet by rememberSaveable { mutableStateOf(false) }
    var unfollowSheet by rememberSaveable { mutableStateOf(false) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barTop = statusTop + DetailTopBarHeight
    val barTopPx = with(density) { barTop.roundToPx() }
    val gapPx = with(density) { LagoonSpacing.cardGap.roundToPx() }
    val tabs = segments.size > 1
    val tabsBlock = if (tabs) LagoonSpacing.touchTarget else 0.dp
    val current = when (selected) {
        GameSegment.Live -> lists.streams
        GameSegment.Videos -> lists.videos
        GameSegment.Clips -> lists.clips
    }
    IntegrityWatch(lists.streams, actions)
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
            if (grid.firstVisibleItemIndex >= 1) grid.scrollToItem(1)
        }
    }
    val coverColor = rememberCoverColor(options.image(header.boxArt)) ?: Lagoon.colors.card
    // 0 while the header shows, 1 once it has scrolled under the bar
    val collapsed: () -> Float = {
        if (grid.firstVisibleItemIndex > 0) 1f
        else grid.firstVisibleItemScrollOffset / (headerHeight * 0.6f).coerceAtLeast(1f)
    }
    Box(modifier.fillMaxSize()) {
        GlassBackdrop(Modifier.fillMaxSize().then(if (navScroll != null) Modifier.nestedScroll(navScroll) else Modifier)) {
            CompositionLocalProvider(LocalBrowseOffline provides offline) {
                LagoonPullToRefresh(
                    isRefreshing = current?.loadState?.refresh is LoadState.Loading && (current?.itemCount ?: 0) > 0,
                    onRefresh = { current?.refresh() },
                    modifier = Modifier.fillMaxSize(),
                    indicatorTop = barTop,
                ) {
                    StaggerGate("game:${selected.name}", hasItems = (current?.itemCount ?: 0) > 0) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = grid,
                            modifier = Modifier.fillMaxSize().testTag(GameListTag),
                            contentPadding = PaddingValues(
                                start = LagoonSpacing.pageSide, end = LagoonSpacing.pageSide, top = barTop,
                                bottom = bottomPadding + LagoonSpacing.m,
                            ),
                            verticalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap),
                            horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap),
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "header", contentType = "header") {
                                GameHeaderBlock(
                                    header = header,
                                    coverColor = coverColor,
                                    options = options,
                                    showFollow = showFollow,
                                    following = following,
                                    onFollow = { if (it) actions.setFollowing(true) else unfollowSheet = true },
                                    onTag = actions::openGameTag,
                                    boxArtModifier = boxArtModifier,
                                    gradientTop = barTop,
                                    modifier = Modifier.onSizeChanged { headerHeight = it.height },
                                )
                            }
                            // the floating tabs sit over this gap
                            item(span = { GridItemSpan(maxLineSpan) }, key = "tabs", contentType = "tabs") {
                                Spacer(Modifier.height(tabsBlock))
                            }
                            item(span = { GridItemSpan(maxLineSpan) }, key = "chips", contentType = "chips") {
                                val kind = selected.kind
                                FilterChipRow(
                                    filterChips(
                                        kind = kind,
                                        filter = filters.of(kind),
                                        config = filters.configOf(kind),
                                        onOpen = { filterSheet = true },
                                        onRemoveTag = { tag ->
                                            val filter = filters.of(kind)
                                            actions.applyFilter(kind, filter.copy(tags = filter.tags - tag), FilterSaves())
                                        },
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                )
                            }
                            when (selected) {
                                GameSegment.Live -> if (lists.streams == null) items(6, contentType = { "skeleton" }) { SkeletonLiveCard() } else lists.streams.let { streams ->
                                    pagedItems(
                                        streams, offline, "stream",
                                        skeleton = { SkeletonLiveCard() },
                                        empty = { EmptyState(stringResource(R.string.browse_game_empty_live), icon = LucideIcons.Radio) },
                                    ) { index, stream ->
                                        StreamCell(stream, index, options, compactStreams, filters.streams, actions)
                                    }
                                }
                                GameSegment.Videos -> if (lists.videos == null) items(6, contentType = { "skeleton" }) { SkeletonLiveCard() } else lists.videos.let { videos ->
                                    pagedItems(
                                        videos, offline, "video",
                                        skeleton = { SkeletonLiveCard() },
                                        empty = { EmptyState(stringResource(R.string.browse_game_empty_videos), icon = LucideIcons.Film) },
                                    ) { index, video ->
                                        VideoCell(video, index, options, videoPositions, bookmarkedVideoIds, actions)
                                    }
                                }
                                GameSegment.Clips -> if (lists.clips == null) items(6, contentType = { "skeleton" }) { SkeletonLiveCard() } else lists.clips.let { clips ->
                                    pagedItems(
                                        clips, offline, "clip",
                                        skeleton = { SkeletonLiveCard() },
                                        empty = { EmptyState(stringResource(R.string.browse_game_empty_clips), icon = LucideIcons.Scissors) },
                                    ) { index, clip ->
                                        ClipCell(clip, index, options, actions)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // floating layers: next to the backdrop, never inside it, so the glass does not sample itself
        DetailTopBar(
            title = header.name.orEmpty(),
            collapsed = collapsed,
            onBack = actions::back,
            modifier = Modifier.semantics { isTraversalGroup = true; traversalIndex = -1f },
            actions = { LagoonIconButton(LucideIcons.Search, stringResource(R.string.search), actions::search) },
        )
        if (tabs) {
            SegmentedControl(
                options = segments.map { stringResource(it.label) },
                selectedIndex = segments.indexOf(selected).coerceAtLeast(0),
                onSelect = { onSelect(segments[it]) },
                glass = true,
                modifier = Modifier
                    .offset {
                        // under the header while it shows, then pinned under the bar
                        val inList = if (grid.firstVisibleItemIndex == 0) barTopPx + headerHeight + gapPx - grid.firstVisibleItemScrollOffset else Int.MIN_VALUE
                        IntOffset(0, maxOf(barTopPx, inList))
                    }
                    .padding(horizontal = LagoonSpacing.pageSide)
                    .widthIn(max = 560.dp)
                    .semantics { isTraversalGroup = true; traversalIndex = -0.5f }
                    .testTag(GameSegmentsTag),
            )
        }
        if (toast != null) {
            ToastHost(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding))
        }
    }

    if (filterSheet) {
        val kind = selected.kind
        FilterSheet(
            config = filters.configOf(kind),
            current = filters.of(kind),
            onApply = { filter, saves -> actions.applyFilter(kind, filter, saves) },
            onDismiss = { filterSheet = false },
            tagResults = if (kind == FilterKind.Streams) tagResults else null,
            onTagQuery = actions::tagQuery,
        )
    }
    if (unfollowSheet) {
        ConfirmSheet(
            title = stringResource(R.string.unfollow_channel, header.name.orEmpty()),
            message = null,
            confirm = stringResource(R.string.unfollow),
            onConfirm = { actions.setFollowing(false) },
            onDismiss = { unfollowSheet = false },
        )
    }
}

private val GameSegment.label: Int
    get() = when (this) {
        GameSegment.Live -> R.string.live
        GameSegment.Videos -> R.string.videos
        GameSegment.Clips -> R.string.clips
    }

@Composable
private fun StreamCell(stream: Stream, index: Int, options: FollowingListOptions, compact: Boolean, filter: BrowseFilter, actions: GameActions) {
    var menuOpen by remember { mutableStateOf(false) }
    val onTag: (String) -> Unit = { tag -> actions.applyFilter(FilterKind.Streams, FilterLogic.addStreamTag(filter, tag), FilterSaves()) }
    val menu = streamMenu(stream, options, actions, showGame = false, onTag = onTag)
    StreamItem(stream, index, options, compact, showGame = false, menu = menu, onClick = { actions.openStream(stream) }, onLongClick = { menuOpen = true })
    if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = stream.channelName ?: stream.channelLogin)
}

@Composable
private fun VideoCell(video: Video, index: Int, options: FollowingListOptions, positions: Map<Long, Long>, bookmarked: Set<String>, actions: GameActions) {
    var menuOpen by remember { mutableStateOf(false) }
    val position = if (options.usePositions) video.id?.toLongOrNull()?.let { positions[it] } else null
    val menu = videoMenu(video, video.id in bookmarked, actions, showGame = false)
    VideoItem(video, index, options, position, showChannel = true, menu = menu, onClick = { actions.openVideo(video, position) }, onLongClick = { menuOpen = true })
    if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = video.title?.trim())
}

@Composable
private fun ClipCell(clip: Clip, index: Int, options: FollowingListOptions, actions: GameActions) {
    var menuOpen by remember { mutableStateOf(false) }
    val menu = clipMenu(clip, actions, showGame = false)
    ClipItem(clip, index, options, showChannel = true, menu = menu, onClick = { actions.openClip(clip) }, onLongClick = { menuOpen = true })
    if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = clip.title?.trim())
}

/** Width of the box art in the header. */
val GameBoxArtWidth = 112.dp

/**
 * The top of the page: the box art on the left, name and counts beside it, then the game's tags and the follow
 * button. Its background is a soft gradient of the box art's colour that also runs up behind the bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameHeaderBlock(
    header: GameHeader,
    coverColor: Color,
    options: FollowingListOptions,
    showFollow: Boolean,
    following: Boolean?,
    onFollow: (Boolean) -> Unit,
    onTag: (Tag) -> Unit,
    boxArtModifier: Modifier,
    gradientTop: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = Lagoon.colors
    val page = colors.page
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                // past the grid's side padding and up behind the bar, fading into the page below the header
                val side = LagoonSpacing.pageSide.toPx()
                val top = gradientTop.toPx()
                drawRect(
                    Brush.verticalGradient(
                        0f to coverColor.copy(alpha = 0.85f),
                        0.55f to coverColor.copy(alpha = 0.35f),
                        1f to page.copy(alpha = 0f),
                        startY = -top, endY = size.height,
                    ),
                    topLeft = Offset(-side, -top),
                    size = Size(size.width + side * 2, size.height + top),
                )
            }
            .padding(top = LagoonSpacing.xs, bottom = LagoonSpacing.xs)
            .testTag(GameHeaderTag),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            // short windows (phone landscape) get a smaller cover, so the list starts on the first screen
            val short = LocalConfiguration.current.screenHeightDp < 480
            Box(
                boxArtModifier
                    .width(if (short) 80.dp else GameBoxArtWidth)
                    .aspectRatio(3f / 4f)
                    .clip(LagoonShapes.cardMedia)
                    .background(colors.control),
            ) {
                val image = options.image(header.boxArt)
                if (image != null) LagoonImage(image, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(LagoonSpacing.m))
            Column(Modifier.weight(1f).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    header.name.orEmpty(), style = Lagoon.type.title, color = colors.textPrimary, maxLines = 3,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                header.viewers?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LiveDot()
                        Text(
                            pluralStringResource(R.plurals.viewers, it, TwitchApiHelper.formatCount(it, options.truncateCounts)),
                            style = Lagoon.type.label, color = colors.textSecondary,
                        )
                    }
                }
                val counts = listOfNotNull(
                    header.broadcasters?.takeIf { options.showBroadcasters }?.let {
                        pluralStringResource(R.plurals.broadcasters, it, TwitchApiHelper.formatCount(it, options.truncateCounts))
                    },
                    header.followers?.let { pluralStringResource(R.plurals.followers, it, TwitchApiHelper.formatCount(it, options.truncateCounts)) },
                )
                if (counts.isNotEmpty()) {
                    Text(counts.joinToString(" · "), style = Lagoon.type.label, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val tags = if (options.showTags) header.tags.filter { !it.name.isNullOrBlank() } else emptyList()
        if (tags.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = LagoonSpacing.s),
                horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(LagoonSpacing.xs),
            ) {
                tags.forEach { tag ->
                    val clickable = tag.id != null
                    Text(
                        tag.name.orEmpty(),
                        style = Lagoon.type.labelStrong,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(LagoonShapes.pill)
                            .then(if (clickable) Modifier.pressable(onClick = { onTag(tag) }, shape = LagoonShapes.pill) else Modifier)
                            .background(colors.control)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        // shown once the follow state is known, so it never flips under the finger
        if (showFollow && header.id != null && following != null) {
            FollowButton(
                following = following == true,
                onToggle = onFollow,
                modifier = Modifier.padding(top = LagoonSpacing.s),
            )
        }
    }
}
