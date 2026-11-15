package com.github.andreyasadchy.xtra.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.browse.BrowseFilter
import com.github.andreyasadchy.xtra.ui.browse.FilterKind
import com.github.andreyasadchy.xtra.ui.browse.FilterLogic
import com.github.andreyasadchy.xtra.ui.browse.FilterSaves
import com.github.andreyasadchy.xtra.ui.browse.FilterSheet
import com.github.andreyasadchy.xtra.ui.browse.FilterSheetConfig
import com.github.andreyasadchy.xtra.ui.browse.FilterTag
import com.github.andreyasadchy.xtra.ui.browse.LocalBrowseOffline
import com.github.andreyasadchy.xtra.ui.browse.actionsForAccessibility
import com.github.andreyasadchy.xtra.ui.browse.filterChips
import com.github.andreyasadchy.xtra.ui.browse.pagedItems
import com.github.andreyasadchy.xtra.ui.browse.posterColumns
import com.github.andreyasadchy.xtra.ui.design.components.ActionItem
import com.github.andreyasadchy.xtra.ui.design.components.ActionSheet
import com.github.andreyasadchy.xtra.ui.design.components.EmptyState
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipRow
import com.github.andreyasadchy.xtra.ui.design.components.GamePoster
import com.github.andreyasadchy.xtra.ui.design.components.GlassBackdrop
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonPullToRefresh
import com.github.andreyasadchy.xtra.ui.design.components.LargeTopBar
import com.github.andreyasadchy.xtra.ui.design.components.ToastHost
import com.github.andreyasadchy.xtra.ui.design.components.ToastHostState
import com.github.andreyasadchy.xtra.ui.design.components.TopBarCollapsed
import com.github.andreyasadchy.xtra.ui.design.components.TopBarExpanded
import com.github.andreyasadchy.xtra.ui.design.components.rememberTopBarState
import com.github.andreyasadchy.xtra.ui.design.components.shimmer
import com.github.andreyasadchy.xtra.ui.design.components.staggeredAppear
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.util.TwitchApiHelper

/** What the games grid asks its host for. */
interface GamesGridActions {
    fun openGame(game: Game)
    fun search()
    fun account()
    fun back()
    fun applyFilter(filter: BrowseFilter)
    fun tagQuery(query: String)
    fun integrityCheckFailed()
}

/** Test tags. */
const val GamesGridTag = "gamesGrid"

/**
 * The Browse page (plan 7.2, U4): every game as a poster in a grid (three columns on phones, six on tablets), the
 * chosen tags as chips above it and the one filter sheet behind them. It is also the "games with this tag" page;
 * [root] says which (the root page has search and account in the bar, the other a back button).
 *
 * @param posterModifier marks a poster's box art as the shared element of the poster-to-page transition.
 */
@Composable
fun GamesScreen(
    root: Boolean,
    games: LazyPagingItems<Game>?,
    filter: BrowseFilter,
    options: FollowingListOptions,
    actions: GamesGridActions,
    grid: LazyGridState,
    modifier: Modifier = Modifier,
    tagResults: LazyPagingItems<Tag>? = null,
    posterModifier: @Composable (Game) -> Modifier = { Modifier },
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    navScroll: NestedScrollConnection? = null,
    scrollToTopRequests: Int = 0,
    toast: ToastHostState? = null,
    offline: Boolean = false,
) {
    val density = LocalDensity.current
    val columns = posterColumns(LocalConfiguration.current.screenWidthDp)
    val topBar = rememberTopBarState()
    var filterSheet by rememberSaveable { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Game?>(null) }
    val failed = games?.loadState?.let { (it.refresh as? LoadState.Error ?: it.append as? LoadState.Error)?.error?.message } ==
            com.github.andreyasadchy.xtra.util.C.FAILED_INTEGRITY_CHECK
    LaunchedEffect(games, failed) { if (failed) actions.integrityCheckFailed() }

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
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listTop = statusTop + TopBarExpanded
    val removeTag: (FilterTag) -> Unit = { tag -> actions.applyFilter(filter.copy(tags = filter.tags - tag)) }
    Box(modifier.fillMaxSize()) {
        GlassBackdrop(Modifier.fillMaxSize().then(if (navScroll != null) Modifier.nestedScroll(navScroll) else Modifier)) {
            CompositionLocalProvider(LocalBrowseOffline provides offline) {
                LagoonPullToRefresh(
                    isRefreshing = games?.loadState?.refresh is LoadState.Loading && (games?.itemCount ?: 0) > 0,
                    onRefresh = { games?.refresh() },
                    modifier = Modifier.fillMaxSize(),
                    indicatorTop = listTop,
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = grid,
                        modifier = Modifier.fillMaxSize().testTag(GamesGridTag),
                        contentPadding = PaddingValues(
                            start = LagoonSpacing.pageSide, end = LagoonSpacing.pageSide, top = listTop + LagoonSpacing.xs,
                            bottom = bottomPadding + LagoonSpacing.m,
                        ),
                        verticalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap),
                        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "chips", contentType = "chips") {
                            FilterChipRow(
                                filterChips(FilterKind.Games, filter, FilterSheetConfig(FilterKind.Games), onOpen = { filterSheet = true }, onRemoveTag = removeTag),
                                contentPadding = PaddingValues(0.dp),
                            )
                        }
                        if (games == null) {
                            items(12, contentType = { "skeleton" }) { Box(Modifier.fillMaxWidth().height(180.dp).shimmer(LagoonShapes.card)) }
                        } else {
                            pagedItems(
                                games, offline, "game",
                                skeletonCount = 12,
                                skeleton = { Box(Modifier.fillMaxWidth().height(180.dp).shimmer(LagoonShapes.card)) },
                                empty = {
                                    EmptyState(
                                        stringResource(if (filter.tags.isEmpty()) R.string.browse_games_empty else R.string.browse_games_empty_tags),
                                        icon = LucideIcons.Gamepad2,
                                        modifier = Modifier.padding(top = 24.dp),
                                    )
                                },
                            ) { index, game ->
                                val menu = gameMenu(game, filter, options, actions)
                                GamePoster(
                                    name = game.name.orEmpty(),
                                    onClick = { actions.openGame(game) },
                                    modifier = Modifier.staggeredAppear(index).actionsForAccessibility(menu),
                                    boxArt = options.image(game.boxArt),
                                    viewers = game.viewerCount?.let { TwitchApiHelper.formatCount(it, options.truncateCounts) },
                                    subtitle = if (options.showBroadcasters) game.broadcasterCount?.let {
                                        pluralStringResource(R.plurals.broadcasters, it, TwitchApiHelper.formatCount(it, options.truncateCounts))
                                    } else null,
                                    onLongClick = { menuFor = game },
                                    boxArtModifier = posterModifier(game),
                                )
                            }
                        }
                    }
                }
            }
        }
        LargeTopBar(
            title = stringResource(if (root) R.string.shell_nav_browse else R.string.games),
            state = topBar,
            floating = true,
            modifier = Modifier.semantics { isTraversalGroup = true; traversalIndex = -1f },
            navigationIcon = if (root) null else {
                { LagoonIconButton(LucideIcons.ArrowLeft, stringResource(R.string.browse_back), actions::back) }
            },
            actions = {
                LagoonIconButton(LucideIcons.Search, stringResource(R.string.search), actions::search)
                if (root) LagoonIconButton(LucideIcons.User, stringResource(R.string.following_account), actions::account)
            },
        )
        if (toast != null) {
            ToastHost(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding))
        }
    }

    menuFor?.let { game ->
        ActionSheet(gameMenu(game, filter, options, actions), onDismiss = { menuFor = null }, title = game.name)
    }
    if (filterSheet) {
        FilterSheet(
            config = FilterSheetConfig(FilterKind.Games, canSaveDefault = false),
            current = filter,
            onApply = { new, _: FilterSaves -> actions.applyFilter(new) },
            onDismiss = { filterSheet = false },
            tagResults = tagResults,
            onTagQuery = actions::tagQuery,
        )
    }
}

/** Open the game, or narrow the grid to one of its tags (v0.1: the tag chips on each game item). */
@Composable
private fun gameMenu(game: Game, filter: BrowseFilter, options: FollowingListOptions, actions: GamesGridActions): List<ActionItem> {
    val list = mutableListOf(
        ActionItem(stringResource(R.string.following_open_game, game.name.orEmpty()), LucideIcons.Gamepad2) { actions.openGame(game) },
    )
    if (options.showTags) {
        game.tags.orEmpty().filter { it.id != null && !it.name.isNullOrBlank() }.take(4).forEach { tag ->
            list += ActionItem(stringResource(R.string.following_browse_tag, tag.name.orEmpty()), LucideIcons.ListFilter) {
                actions.applyFilter(FilterLogic.addGameTag(filter, FilterTag(tag.id, tag.name.orEmpty())))
            }
        }
    }
    return list
}
