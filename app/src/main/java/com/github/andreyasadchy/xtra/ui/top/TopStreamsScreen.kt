package com.github.andreyasadchy.xtra.ui.top

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.browse.BrowseActions
import com.github.andreyasadchy.xtra.ui.browse.BrowseFilter
import com.github.andreyasadchy.xtra.ui.browse.FilterKind
import com.github.andreyasadchy.xtra.ui.browse.FilterLogic
import com.github.andreyasadchy.xtra.ui.browse.FilterSaves
import com.github.andreyasadchy.xtra.ui.browse.FilterSheet
import com.github.andreyasadchy.xtra.ui.browse.FilterSheetConfig
import com.github.andreyasadchy.xtra.ui.browse.IntegrityWatch
import com.github.andreyasadchy.xtra.ui.browse.LocalBrowseOffline
import com.github.andreyasadchy.xtra.ui.browse.StreamItem
import com.github.andreyasadchy.xtra.ui.browse.cardColumns
import com.github.andreyasadchy.xtra.ui.browse.filterChips
import com.github.andreyasadchy.xtra.ui.browse.pagedItems
import com.github.andreyasadchy.xtra.ui.browse.streamMenu
import com.github.andreyasadchy.xtra.ui.design.components.StaggerGate
import com.github.andreyasadchy.xtra.ui.design.components.ActionSheet
import com.github.andreyasadchy.xtra.ui.design.components.EmptyState
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipRow
import com.github.andreyasadchy.xtra.ui.design.components.GlassBackdrop
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonPullToRefresh
import com.github.andreyasadchy.xtra.ui.design.components.LargeTopBar
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonLiveCard
import com.github.andreyasadchy.xtra.ui.design.components.TopBarCollapsed
import com.github.andreyasadchy.xtra.ui.design.components.TopBarExpanded
import com.github.andreyasadchy.xtra.ui.design.components.rememberTopBarState
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions

/** Test tags. */
const val TopStreamsListTag = "topStreamsList"

/**
 * Live streams across Twitch, by viewers or by tag (U4): v0.1's popular streams page, which stays as the page a
 * stream tag opens. Live cards in a grid, the filter chips and the one filter sheet.
 */
@Composable
fun TopStreamsScreen(
    root: Boolean,
    streams: LazyPagingItems<Stream>?,
    filter: BrowseFilter,
    options: FollowingListOptions,
    compactStreams: Boolean,
    actions: BrowseActions,
    onApplyFilter: (BrowseFilter, FilterSaves) -> Unit,
    modifier: Modifier = Modifier,
    tagResults: LazyPagingItems<Tag>? = null,
    onTagQuery: (String) -> Unit = {},
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    navScroll: NestedScrollConnection? = null,
    scrollToTopRequests: Int = 0,
    offline: Boolean = false,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val density = LocalDensity.current
    val columns = cardColumns(LocalConfiguration.current.screenWidthDp)
    val topBar = rememberTopBarState()
    val grid = gridState
    var filterSheet by rememberSaveable { mutableStateOf(false) }
    IntegrityWatch(streams, actions)
    LaunchedEffect(scrollToTopRequests) {
        if (scrollToTopRequests > 0) {
            topBar.snapTo(0f)
            grid.animateScrollToItem(0)
        }
    }
    val collapseRange = with(density) { (TopBarExpanded - TopBarCollapsed).toPx() }
    LaunchedEffect(grid, collapseRange) {
        snapshotFlow { if (grid.firstVisibleItemIndex > 0) collapseRange else grid.firstVisibleItemScrollOffset.toFloat() }
            .collect { topBar.snapTo(it / collapseRange) }
    }
    val listTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarExpanded
    val config = FilterSheetConfig(FilterKind.Streams, canSaveFilters = true)
    val title = filter.tags.takeIf { it.isNotEmpty() }?.joinToString { it.name } ?: stringResource(R.string.popular)
    Box(modifier.fillMaxSize()) {
        GlassBackdrop(Modifier.fillMaxSize().then(if (navScroll != null) Modifier.nestedScroll(navScroll) else Modifier)) {
            CompositionLocalProvider(LocalBrowseOffline provides offline) {
                LagoonPullToRefresh(
                    isRefreshing = streams?.loadState?.refresh is LoadState.Loading && (streams?.itemCount ?: 0) > 0,
                    onRefresh = { streams?.refresh() },
                    modifier = Modifier.fillMaxSize(),
                    indicatorTop = listTop,
                ) {
                    StaggerGate("top", hasItems = (streams?.itemCount ?: 0) > 0) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = grid,
                            modifier = Modifier.fillMaxSize().testTag(TopStreamsListTag),
                            contentPadding = PaddingValues(
                                start = LagoonSpacing.pageSide, end = LagoonSpacing.pageSide, top = listTop + LagoonSpacing.xs,
                                bottom = bottomPadding + LagoonSpacing.m,
                            ),
                            verticalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap),
                            horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap),
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "chips", contentType = "chips") {
                                FilterChipRow(
                                    filterChips(
                                        FilterKind.Streams, filter, config,
                                        onOpen = { filterSheet = true },
                                        onRemoveTag = { tag -> onApplyFilter(filter.copy(tags = filter.tags - tag), FilterSaves()) },
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                )
                            }
                            if (streams == null) {
                                items(6, contentType = { "skeleton" }) { SkeletonLiveCard() }
                            } else {
                                pagedItems(
                                    streams, offline, "stream",
                                    skeleton = { SkeletonLiveCard() },
                                    empty = { EmptyState(stringResource(R.string.browse_streams_empty), icon = LucideIcons.Radio, modifier = Modifier.padding(top = 24.dp)) },
                                ) { index, stream ->
                                    var menuOpen by remember { mutableStateOf(false) }
                                    val menu = streamMenu(stream, options, actions, showGame = true) { tag ->
                                        onApplyFilter(FilterLogic.addStreamTag(filter, tag), FilterSaves())
                                    }
                                    StreamItem(stream, index, options, compactStreams, showGame = true, menu = menu, onClick = { actions.openStream(stream) }, onLongClick = { menuOpen = true })
                                    if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = stream.channelName ?: stream.channelLogin)
                                }
                            }
                        }
                    }
                }
            }
        }
        LargeTopBar(
            title = title,
            state = topBar,
            floating = true,
            modifier = Modifier.semantics { isTraversalGroup = true; traversalIndex = -1f },
            navigationIcon = if (root) null else {
                { LagoonIconButton(LucideIcons.ArrowLeft, stringResource(R.string.browse_back), actions::back) }
            },
            actions = { LagoonIconButton(LucideIcons.Search, stringResource(R.string.search), actions::search) },
        )
    }
    if (filterSheet) {
        FilterSheet(
            config = config,
            current = filter,
            onApply = onApplyFilter,
            onDismiss = { filterSheet = false },
            tagResults = tagResults,
            onTagQuery = onTagQuery,
        )
    }
}
