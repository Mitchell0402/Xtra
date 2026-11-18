package com.github.andreyasadchy.xtra.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.browse.BrowseActions
import com.github.andreyasadchy.xtra.ui.browse.DetailTopBarHeight
import com.github.andreyasadchy.xtra.ui.browse.IntegrityWatch
import com.github.andreyasadchy.xtra.ui.browse.LocalBrowseOffline
import com.github.andreyasadchy.xtra.ui.browse.SectionHeading
import com.github.andreyasadchy.xtra.ui.browse.StreamItem
import com.github.andreyasadchy.xtra.ui.browse.VideoItem
import com.github.andreyasadchy.xtra.ui.browse.cardColumns
import com.github.andreyasadchy.xtra.ui.browse.posterColumns
import com.github.andreyasadchy.xtra.ui.browse.streamMenu
import com.github.andreyasadchy.xtra.ui.browse.videoMenu
import com.github.andreyasadchy.xtra.ui.design.components.StaggerGate
import com.github.andreyasadchy.xtra.ui.design.components.ActionSheet
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle
import com.github.andreyasadchy.xtra.ui.design.components.ChannelRow
import com.github.andreyasadchy.xtra.ui.design.components.ChoiceOption
import com.github.andreyasadchy.xtra.ui.design.components.EmptyState
import com.github.andreyasadchy.xtra.ui.design.components.ErrorState
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipItem
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipRow
import com.github.andreyasadchy.xtra.ui.design.components.GamePoster
import com.github.andreyasadchy.xtra.ui.design.components.GlassBackdrop
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonTextField
import com.github.andreyasadchy.xtra.ui.design.components.SingleChoiceList
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonLiveCard
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonRow
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.components.shimmer
import com.github.andreyasadchy.xtra.ui.design.components.staggeredAppear
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.ui.following.displayName
import com.github.andreyasadchy.xtra.ui.main.shell.ShellTabs
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlinx.coroutines.delay

/** The kinds of results (setting `ui_search_tabs`: "0" videos, "1" streams, "2" channels, "3" games). */
enum class SearchGroup(val key: String) {
    Videos("0"), Streams("1"), Channels("2"), Games("3");

    companion object {
        fun of(key: String) = entries.find { it.key == key }

        /**
         * The groups without the switched-off ones: the one marked as default first (v0.1 opened its tab first; the
         * results now show all groups at once), then the others in the stored order.
         */
        fun list(pref: String?): List<SearchGroup> {
            val split = ShellTabs.normalize(pref, C.DEFAULT_SEARCH_TABS).map { it.split(':') }
            val shown = split.mapNotNull { if (it.getOrNull(2) == "0") null else of(it[0]) }.ifEmpty { listOf(Channels) }
            val default = split.find { it.getOrNull(1) != "0" }?.let { of(it[0]) }?.takeIf { it in shown }
            return if (default != null) listOf(default) + (shown - default) else shown
        }
    }
}

/** The result lists; a group that is switched off passes null. */
class SearchResults(
    val channels: LazyPagingItems<User>?,
    val streams: LazyPagingItems<Stream>?,
    val games: LazyPagingItems<Game>?,
    val videos: LazyPagingItems<Video>?,
) {
    fun of(group: SearchGroup): LazyPagingItems<*>? = when (group) {
        SearchGroup.Channels -> channels
        SearchGroup.Streams -> streams
        SearchGroup.Games -> games
        SearchGroup.Videos -> videos
    }
}

/** What the search page asks its host for besides the list actions. */
interface SearchActions : BrowseActions {
    /** Searches as the user types; the host passes it on to every result list. */
    fun query(text: String)
    /** The user submitted or opened a result: keep the query among the recent searches (when that is on). */
    fun remember(text: String)
    fun forget(text: String)
    fun forgetAll()
    /** v0.1's "Get user by ID/login": [byId] looks the user up by id, else by login. */
    fun lookUpUser(byId: Boolean, value: String)
    fun openUser(byId: Boolean, value: String)
}

/** The answer to a user lookup: null while nothing was asked, [error] set when the user does not exist. */
@Immutable
data class UserLookup(val byId: Boolean, val value: String, val error: String?, val reason: String?)

/** Test tags. */
const val SearchFieldTag = "searchField"
const val SearchResultsTag = "searchResults"
const val SearchGroupChipsTag = "searchGroups"

/**
 * The full-screen search page (plan 7.2, U4): the field takes focus at once; with nothing typed it shows the recent
 * searches as chips; while typing, results update as you go, grouped into channels, streams, games and videos, each
 * group a few items with "see all" that switches the page to that group's full list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    groups: List<SearchGroup>,
    results: SearchResults,
    recent: List<String>,
    options: FollowingListOptions,
    compactStreams: Boolean,
    actions: SearchActions,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    showRecent: Boolean = true,
    videoPositions: Map<Long, Long> = emptyMap(),
    bookmarkedVideoIds: Set<String> = emptySet(),
    lookup: UserLookup? = null,
    onLookupShown: () -> Unit = {},
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    navScroll: NestedScrollConnection? = null,
    offline: Boolean = false,
    autoFocus: Boolean = true,
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var groupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lookupSheet by rememberSaveable { mutableStateOf(false) }
    val shown = groupKey?.let { SearchGroup.of(it) }?.takeIf { it in groups }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val grid = rememberLazyGridState()
    val widthDp = LocalConfiguration.current.screenWidthDp
    val cardColumns = cardColumns(widthDp)
    // a common multiple, so card rows and poster rows can share one grid
    val posterColumns = posterColumns(widthDp)
    val columns = lcm(cardColumns, posterColumns)
    // the keyboard comes up for a new search, not when coming back to results
    LaunchedEffect(Unit) { if (autoFocus && query.isEmpty()) focus.requestFocus() }
    // v0.1 searched 750 ms after the last key; results now follow the typing more closely
    LaunchedEffect(query) {
        if (query.isNotBlank()) delay(300)
        actions.query(query.trim())
    }
    LaunchedEffect(shown) { grid.scrollToItem(0) }
    listOfNotNull(results.channels, results.streams, results.games, results.videos).forEach { IntegrityWatch(it, actions) }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barTop = statusTop + DetailTopBarHeight
    val searching = query.isNotBlank()
    val open: (Boolean) -> Unit = { if (searching) actions.remember(query.trim()) }
    Box(modifier.fillMaxSize()) {
        GlassBackdrop(Modifier.fillMaxSize().then(if (navScroll != null) Modifier.nestedScroll(navScroll) else Modifier)) {
            CompositionLocalProvider(LocalBrowseOffline provides offline) {
                StaggerGate("search", hasItems = searching && listOfNotNull(results.channels, results.streams, results.games, results.videos).any { it.itemCount > 0 }) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = grid,
                        modifier = Modifier.fillMaxSize().imePadding().testTag(SearchResultsTag),
                        contentPadding = PaddingValues(
                            start = LagoonSpacing.pageSide, end = LagoonSpacing.pageSide, top = barTop + LagoonSpacing.xs,
                            bottom = bottomPadding + LagoonSpacing.m,
                        ),
                        verticalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap),
                        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap),
                    ) {
                        when {
                            !searching -> {
                                if (showRecent && recent.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }, contentType = "recentHeading") {
                                        SectionHeading(stringResource(R.string.browse_search_recent)) {
                                            LagoonButton(stringResource(R.string.browse_search_clear_recent), actions::forgetAll, style = ButtonStyle.Secondary)
                                        }
                                    }
                                    item(span = { GridItemSpan(maxLineSpan) }, contentType = "recent") {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.xs), verticalArrangement = Arrangement.spacedBy(LagoonSpacing.xs)) {
                                            recent.forEach { text ->
                                                RecentChip(text, onClick = { query = text; actions.remember(text) }, onRemove = { actions.forget(text) })
                                            }
                                        }
                                    }
                                } else {
                                    item(span = { GridItemSpan(maxLineSpan) }, contentType = "hint") {
                                        EmptyState(stringResource(R.string.browse_search_hint), icon = LucideIcons.Search, modifier = Modifier.padding(top = 24.dp))
                                    }
                                }
                            }
                            else -> {
                                if (groups.size > 1) {
                                    item(span = { GridItemSpan(maxLineSpan) }, contentType = "groups") {
                                        FilterChipRow(
                                            buildList {
                                                add(FilterChipItem(stringResource(R.string.all), selected = shown == null, onClick = { groupKey = null }))
                                                groups.forEach { group ->
                                                    add(FilterChipItem(stringResource(group.label), selected = shown == group, onClick = { groupKey = group.key }))
                                                }
                                            },
                                            modifier = Modifier.testTag(SearchGroupChipsTag),
                                            contentPadding = PaddingValues(0.dp),
                                        )
                                    }
                                }
                                val single = shown ?: groups.singleOrNull()
                                if (single != null) {
                                    fullGroup(single, results, columns, cardColumns, posterColumns, options, compactStreams, videoPositions, bookmarkedVideoIds, actions, offline, open, query)
                                } else {
                                    val allEmpty = groups.all { group ->
                                        val items = results.of(group)
                                        items == null || (items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading)
                                    }
                                    val allFailed = groups.all { group ->
                                        val items = results.of(group)
                                        items == null || (items.itemCount == 0 && items.loadState.refresh is LoadState.Error)
                                    }
                                    if (allFailed) {
                                        item(span = { GridItemSpan(maxLineSpan) }, contentType = "error") {
                                            ErrorState(
                                                message = stringResource(if (offline) R.string.no_connection else R.string.following_load_error),
                                                onRetry = { groups.forEach { results.of(it)?.refresh() } },
                                                modifier = Modifier.padding(top = 32.dp),
                                            )
                                        }
                                    } else if (allEmpty) {
                                        item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                                            EmptyState(stringResource(R.string.browse_search_empty, query.trim()), icon = LucideIcons.Search, modifier = Modifier.padding(top = 24.dp))
                                        }
                                    } else {
                                        groups.forEach { group ->
                                            groupPreview(group, results, columns, cardColumns, posterColumns, options, compactStreams, videoPositions, bookmarkedVideoIds, actions, open) { groupKey = group.key }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SearchBar(
            query = query,
            onQuery = { query = it },
            onSubmit = { keyboard?.hide(); if (query.isNotBlank()) actions.remember(query.trim()) },
            onBack = actions::back,
            onLookUp = { lookupSheet = true },
            focus = focus,
        )
    }
    if (lookupSheet) {
        UserLookupSheet(
            lookup = lookup,
            onLookUp = actions::lookUpUser,
            onOpen = { byId, value -> lookupSheet = false; actions.openUser(byId, value) },
            onDismiss = { lookupSheet = false; onLookupShown() },
        )
    }
}

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
private fun lcm(a: Int, b: Int) = a / gcd(a, b) * b

val SearchGroup.label: Int
    get() = when (this) {
        SearchGroup.Videos -> R.string.videos
        SearchGroup.Streams -> R.string.live
        SearchGroup.Channels -> R.string.channels
        SearchGroup.Games -> R.string.games
    }

@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onLookUp: () -> Unit,
    focus: FocusRequester,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Lagoon.colors.page)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(DetailTopBarHeight)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LagoonIconButton(LucideIcons.ArrowLeft, stringResource(R.string.browse_back), onBack)
        LagoonTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = stringResource(R.string.browse_search_placeholder),
            modifier = Modifier.weight(1f).focusRequester(focus).testTag(SearchFieldTag),
            onImeAction = onSubmit,
        )
        LagoonIconButton(LucideIcons.UserCheck, stringResource(R.string.search_user), onLookUp)
    }
}

@Composable
private fun RecentChip(text: String, onClick: () -> Unit, onRemove: () -> Unit) {
    val colors = Lagoon.colors
    Row(
        Modifier
            .heightIn(min = LagoonSpacing.touchTarget)
            .padding(vertical = 6.dp)
            .background(colors.card, LagoonShapes.pill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .pressable(onClick = onClick, shape = LagoonShapes.pill)
                .heightIn(min = 36.dp)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(LucideIcons.History, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Text(text, style = Lagoon.type.labelStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val label = stringResource(R.string.browse_search_forget, text)
        Box(
            Modifier
                .size(36.dp)
                .pressable(onClick = onRemove, shape = LagoonShapes.pill)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(LucideIcons.X, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
        }
    }
}

/** How many items a group shows before "see all". */
private fun previewCount(group: SearchGroup, cardColumns: Int, posterColumns: Int) = when (group) {
    SearchGroup.Channels -> 3
    SearchGroup.Games -> posterColumns
    else -> maxOf(2, cardColumns)
}

private fun spanOf(group: SearchGroup, columns: Int, cardColumns: Int, posterColumns: Int) = when (group) {
    SearchGroup.Channels -> columns
    SearchGroup.Games -> columns / posterColumns
    else -> columns / cardColumns
}

/** A group on the "all" page: heading with "see all", a few results, or a short loading or empty line. */
private fun LazyGridScope.groupPreview(
    group: SearchGroup,
    results: SearchResults,
    columns: Int,
    cardColumns: Int,
    posterColumns: Int,
    options: FollowingListOptions,
    compact: Boolean,
    positions: Map<Long, Long>,
    bookmarked: Set<String>,
    actions: SearchActions,
    open: (Boolean) -> Unit,
    onSeeAll: () -> Unit,
) {
    val items = results.of(group) ?: return
    val count = minOf(items.itemCount, previewCount(group, cardColumns, posterColumns))
    val loading = items.itemCount == 0 && items.loadState.refresh is LoadState.Loading
    if (items.itemCount == 0 && !loading) return
    item(span = { GridItemSpan(maxLineSpan) }, contentType = "groupHeading") {
        SectionHeading(stringResource(group.label)) {
            if (items.itemCount > count || items.loadState.append !is LoadState.NotLoading || !items.loadState.append.endOfPaginationReached) {
                LagoonButton(stringResource(R.string.browse_search_see_all), onSeeAll, style = ButtonStyle.Secondary)
            }
        }
    }
    val span = spanOf(group, columns, cardColumns, posterColumns)
    if (loading) {
        items(previewCount(group, cardColumns, posterColumns), span = { GridItemSpan(span) }, contentType = { "skeleton" }) { skeletonFor(group) }
        return
    }
    items(count, span = { GridItemSpan(span) }, contentType = { group.name }) { index ->
        ResultItem(group, results, index, options, compact, positions, bookmarked, actions, open)
    }
}

/** One group's full, paged list. */
private fun LazyGridScope.fullGroup(
    group: SearchGroup,
    results: SearchResults,
    columns: Int,
    cardColumns: Int,
    posterColumns: Int,
    options: FollowingListOptions,
    compact: Boolean,
    positions: Map<Long, Long>,
    bookmarked: Set<String>,
    actions: SearchActions,
    offline: Boolean,
    open: (Boolean) -> Unit,
    query: String,
) {
    val span = spanOf(group, columns, cardColumns, posterColumns)
    val empty: @Composable () -> Unit = {
        EmptyState(stringResource(R.string.browse_search_empty, query.trim()), icon = LucideIcons.Search, modifier = Modifier.padding(top = 24.dp))
    }
    @Suppress("UNCHECKED_CAST")
    val items = results.of(group) as? LazyPagingItems<Any> ?: return
    pagedItemsSpanned(items, span, offline, group, empty) { index, _ ->
        ResultItem(group, results, index, options, compact, positions, bookmarked, actions, open)
    }
}

/** [pagedItems] with every item taking [span] columns of the shared grid. */
private fun LazyGridScope.pagedItemsSpanned(
    items: LazyPagingItems<Any>,
    span: Int,
    offline: Boolean,
    group: SearchGroup,
    empty: @Composable () -> Unit,
    item: @Composable (Int, Any) -> Unit,
) {
    val refresh = items.loadState.refresh
    if (items.itemCount == 0 && refresh is LoadState.Loading) {
        items(6, span = { GridItemSpan(span) }, contentType = { "skeleton" }) { skeletonFor(group) }
        return
    }
    if (items.itemCount == 0) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
            if (refresh is LoadState.Error) {
                ErrorState(
                    message = stringResource(if (offline) R.string.no_connection else R.string.following_load_error),
                    onRetry = { items.refresh() },
                    modifier = Modifier.padding(top = 32.dp),
                )
            } else {
                empty()
            }
        }
        return
    }
    items(items.itemCount, span = { GridItemSpan(span) }, contentType = { group.name }) { index ->
        val value = items[index] ?: return@items
        item(index, value)
    }
    when (items.loadState.append) {
        is LoadState.Loading -> item(span = { GridItemSpan(maxLineSpan) }, contentType = "more") {
            Box(Modifier.fillMaxWidth().height(4.dp).shimmer(LagoonShapes.pill))
        }
        is LoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }, contentType = "more") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                LagoonButton(stringResource(R.string.following_retry_more), { items.retry() }, style = ButtonStyle.Secondary, icon = LucideIcons.RotateCcw)
            }
        }
        else -> {}
    }
}

@Composable
private fun skeletonFor(group: SearchGroup) {
    when (group) {
        SearchGroup.Channels -> SkeletonRow(Modifier.background(Lagoon.colors.card, LagoonShapes.row))
        SearchGroup.Games -> Box(Modifier.fillMaxWidth().height(180.dp).shimmer(LagoonShapes.card))
        else -> SkeletonLiveCard()
    }
}

@Composable
private fun ResultItem(
    group: SearchGroup,
    results: SearchResults,
    index: Int,
    options: FollowingListOptions,
    compact: Boolean,
    positions: Map<Long, Long>,
    bookmarked: Set<String>,
    actions: SearchActions,
    open: (Boolean) -> Unit,
) {
    when (group) {
        SearchGroup.Channels -> {
            val user = results.channels?.get(index) ?: return
            ChannelRow(
                name = displayName(user.name, user.login, options.nameDisplay),
                subtitle = listOfNotNull(
                    if (user.isLive == true) stringResource(R.string.live) else null,
                    user.followerCount?.let { pluralStringResource(R.plurals.followers, it, TwitchApiHelper.formatCount(it, options.truncateCounts)) },
                ).joinToString(" · ").ifEmpty { null },
                onClick = { open(true); actions.openChannel(user.id, user.login, user.name, user.profileImage) },
                modifier = Modifier.background(Lagoon.colors.card, LagoonShapes.row).staggeredAppear(index),
                avatar = options.image(user.profileImage),
                live = user.isLive == true,
            )
        }
        SearchGroup.Streams -> {
            val stream = results.streams?.get(index) ?: return
            var menuOpen by remember { mutableStateOf(false) }
            val menu = streamMenu(stream, options, actions, showGame = true, onTag = actions::openStreamTag)
            StreamItem(stream, index, options, compact, showGame = true, menu = menu, onClick = { open(true); actions.openStream(stream) }, onLongClick = { menuOpen = true })
            if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = stream.channelName ?: stream.channelLogin)
        }
        SearchGroup.Games -> {
            val game = results.games?.get(index) ?: return
            GamePoster(
                name = game.name.orEmpty(),
                onClick = { open(true); actions.openGame(game.id, game.slug, game.name, game.boxArt) },
                modifier = Modifier.staggeredAppear(index),
                boxArt = options.image(game.boxArt),
                viewers = game.viewerCount?.let { TwitchApiHelper.formatCount(it, options.truncateCounts) },
                subtitle = if (options.showBroadcasters) game.broadcasterCount?.let {
                    pluralStringResource(R.plurals.broadcasters, it, TwitchApiHelper.formatCount(it, options.truncateCounts))
                } else null,
            )
        }
        SearchGroup.Videos -> {
            val video = results.videos?.get(index) ?: return
            var menuOpen by remember { mutableStateOf(false) }
            val position = if (options.usePositions) video.id?.toLongOrNull()?.let { positions[it] } else null
            val menu = videoMenu(video, video.id in bookmarked, actions, showGame = true)
            VideoItem(video, index, options, position, showChannel = true, menu = menu, onClick = { open(true); actions.openVideo(video, position) }, onLongClick = { menuOpen = true })
            if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = video.title?.trim())
        }
    }
}

/** v0.1's "Get user by ID/login" dialog as a sheet: look the user up, or open the channel page straight away. */
@Composable
private fun UserLookupSheet(
    lookup: UserLookup?,
    onLookUp: (Boolean, String) -> Unit,
    onOpen: (Boolean, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var byId by rememberSaveable { mutableStateOf(false) }
    var value by rememberSaveable { mutableStateOf("") }
    val answer = lookup?.takeIf { it.byId == byId && it.value == value.trim() }
    LaunchedEffect(answer) {
        // the user exists: go to the channel, as v0.1 did when the lookup came back without an error
        if (answer != null && answer.error == null) onOpen(answer.byId, answer.value)
    }
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.search_user), skipHalfHeight = true) {
        Column(Modifier.imePadding()) {
            SingleChoiceList(
                listOf(ChoiceOption(stringResource(R.string.user_login)), ChoiceOption(stringResource(R.string.user_id))),
                selected = if (byId) 1 else 0,
                onSelect = { byId = it == 1 },
            )
            LagoonTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = stringResource(if (byId) R.string.user_id else R.string.user_login),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                leadingIcon = LucideIcons.User,
                onImeAction = { if (value.isNotBlank()) onLookUp(byId, value.trim()) },
            )
            if (answer?.error != null) {
                Text(
                    listOfNotNull(answer.error, answer.reason).joinToString(": "),
                    style = Lagoon.type.label, color = Lagoon.colors.danger,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
            ) {
                LagoonButton(
                    stringResource(R.string.view_profile), { if (value.isNotBlank()) onOpen(byId, value.trim()) }, Modifier.weight(1f),
                    style = ButtonStyle.Secondary,
                )
                LagoonButton(stringResource(R.string.browse_search_look_up), { if (value.isNotBlank()) onLookUp(byId, value.trim()) }, Modifier.weight(1f))
            }
        }
    }
}
