package com.github.andreyasadchy.xtra.ui.saved

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.SavedFilter
import com.github.andreyasadchy.xtra.ui.browse.actionsForAccessibility
import com.github.andreyasadchy.xtra.ui.browse.cardColumns
import com.github.andreyasadchy.xtra.ui.design.components.ActionItem
import com.github.andreyasadchy.xtra.ui.design.components.ActionSheet
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle
import com.github.andreyasadchy.xtra.ui.design.components.ChoiceOption
import com.github.andreyasadchy.xtra.ui.design.components.EmptyState
import com.github.andreyasadchy.xtra.ui.design.components.ErrorState
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipItem
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipRow
import com.github.andreyasadchy.xtra.ui.design.components.GlassBackdrop
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LargeTopBar
import com.github.andreyasadchy.xtra.ui.design.components.LiveDot
import com.github.andreyasadchy.xtra.ui.design.components.OverlayChip
import com.github.andreyasadchy.xtra.ui.design.components.ProgressRing
import com.github.andreyasadchy.xtra.ui.design.components.SegmentedControl
import com.github.andreyasadchy.xtra.ui.design.components.SettingIconTile
import com.github.andreyasadchy.xtra.ui.design.components.SettingRow
import com.github.andreyasadchy.xtra.ui.design.components.SettingTrailing
import com.github.andreyasadchy.xtra.ui.design.components.SingleChoiceList
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonLiveCard
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonRow
import com.github.andreyasadchy.xtra.ui.design.components.ToastHost
import com.github.andreyasadchy.xtra.ui.design.components.ToastHostState
import com.github.andreyasadchy.xtra.ui.design.components.ToastResult
import com.github.andreyasadchy.xtra.ui.design.components.TopBarCollapsed
import com.github.andreyasadchy.xtra.ui.design.components.TopBarExpanded
import com.github.andreyasadchy.xtra.ui.design.components.VideoCard
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.components.rememberTopBarState
import com.github.andreyasadchy.xtra.ui.design.components.shimmer
import com.github.andreyasadchy.xtra.ui.design.components.staggeredAppear
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.design.theme.siblingBy
import com.github.andreyasadchy.xtra.ui.download.StorageOption
import com.github.andreyasadchy.xtra.ui.following.AccountSheet
import com.github.andreyasadchy.xtra.ui.following.ConfirmSheet
import com.github.andreyasadchy.xtra.ui.following.FollowingAccount
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.ui.following.displayName
import com.github.andreyasadchy.xtra.ui.saved.bookmarks.BookmarkSorts
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlinx.coroutines.launch
import kotlin.time.Instant

/** What the Me page asks its host for (the v0.1 pager, list fragments, adapters and menus, call for call). */
interface SavedActions {
    fun search()
    fun settings()
    fun logIn()
    fun logOut()
    fun importFolders()
    fun importFiles()
    fun openChannel(id: String?, login: String?, name: String?, image: String?)
    fun openGame(id: String?, slug: String?, name: String?)

    fun playOffline(video: OfflineVideo)
    fun stopDownload(video: OfflineVideo)
    fun resumeDownload(video: OfflineVideo)
    fun convert(video: OfflineVideo)
    fun moveToShared(video: OfflineVideo)
    fun moveToApp(video: OfflineVideo, index: Int)
    fun changeChatFile(video: OfflineVideo)
    fun shareDownload(video: OfflineVideo)
    fun deleteDownload(video: OfflineVideo, keepFiles: Boolean)

    fun openBookmark(bookmark: Bookmark, position: Long?)
    fun refreshBookmark(bookmark: Bookmark)
    fun downloadBookmark(bookmark: Bookmark)
    fun toggleIgnore(userId: String)
    fun deleteBookmark(bookmark: Bookmark)
    fun applyBookmarkSort(sort: String, order: String, saveDefault: Boolean)

    fun openFilter(filter: SavedFilter)
    fun deleteFilter(filter: SavedFilter)
}

/**
 * What a download in progress shows: the service's numbers ([active] false when the service does not report it),
 * the pace worked out from them, and how long a recording has been running.
 */
@Immutable
data class DownloadState(
    val active: Boolean = false,
    val progress: Int = 0,
    val max: Int = 100,
    val chatProgress: Int = 0,
    val maxChat: Int = 100,
    val bytes: Long = 0,
    val bytesPerSecond: Double = 0.0,
    val secondsLeft: Long? = null,
    val recordedSeconds: Long? = null,
)

@Immutable
data class BookmarkSortChoice(val sort: String = BookmarkSorts.SORT_SAVED_AT, val order: String = BookmarkSorts.ORDER_DESC)

/** Settings the lists read (v0.1: the time-left line and the "ignore" item only show with `ui_bookmark_time_left`). */
@Immutable
data class SavedOptions(val bookmarkTimeLeft: Boolean = true)

/** Test tags. */
const val SavedSegmentsTag = "savedSegments"
const val SavedListTag = "savedList"

private val LocalSavedOffline = staticCompositionLocalOf { false }

/**
 * The Me page (plan 7.6, U5): large title with search, import (downloads) and account on the right; bookmarks,
 * downloads and filters as segments, all as video cards except filters. Downloads in progress show a ring, the speed
 * and the time left; recordings a red dot and how long they have run. Long press opens each item's menu; a swipe to
 * the left removes it, with an undo in the toast.
 */
@Composable
fun SavedScreen(
    segments: List<SavedSegment>,
    selected: SavedSegment,
    onSelect: (SavedSegment) -> Unit,
    account: FollowingAccount,
    bookmarks: List<Bookmark>?,
    bookmarkSort: BookmarkSortChoice,
    downloads: LazyPagingItems<OfflineVideo>?,
    downloadStates: Map<Int, DownloadState>,
    filters: LazyPagingItems<SavedFilter>?,
    options: FollowingListOptions,
    savedOptions: SavedOptions,
    actions: SavedActions,
    modifier: Modifier = Modifier,
    storages: List<StorageOption> = emptyList(),
    storageMounted: Boolean = true,
    videoPositions: Map<Long, Long> = emptyMap(),
    ignoredUserIds: Set<String> = emptySet(),
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    navScroll: NestedScrollConnection? = null,
    scrollToTopRequests: Int = 0,
    toast: ToastHostState? = null,
) {
    val motion = Lagoon.motion
    val density = LocalDensity.current
    val topBar = rememberTopBarState()
    var accountSheet by rememberSaveable { mutableStateOf(false) }
    var logOutSheet by rememberSaveable { mutableStateOf(false) }
    var importSheet by rememberSaveable { mutableStateOf(false) }
    val gridStates = SavedSegment.entries.associateWith { rememberLazyGridState() }
    // items swiped away while their undo toast shows (and after, until the list drops them)
    val pending = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val removeWithUndo: (String, String, () -> Unit) -> Unit = { key, message, remove ->
        pending += key
        scope.launch {
            val result = toast?.show(message, resources.getString(R.string.saved_undo)) ?: ToastResult.Dismissed
            if (result == ToastResult.ActionPerformed) pending -= key else remove()
        }
    }

    LaunchedEffect(scrollToTopRequests) {
        if (scrollToTopRequests > 0) {
            topBar.snapTo(0f)
            gridStates[selected]?.animateScrollToItem(0)
        }
    }
    val collapseRange = with(density) { (TopBarExpanded - TopBarCollapsed).toPx() }
    val shownGrid = gridStates.getValue(selected)
    LaunchedEffect(shownGrid, collapseRange) {
        snapshotFlow { if (shownGrid.firstVisibleItemIndex > 0) collapseRange else shownGrid.firstVisibleItemScrollOffset.toFloat() }
            .collect { topBar.snapTo(it / collapseRange) }
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val segmentBlock = if (segments.size > 1) LagoonSpacing.touchTarget + LagoonSpacing.xs * 2 else 0.dp
    val listTop = statusTop + TopBarExpanded + segmentBlock
    val columns = cardColumns(LocalConfiguration.current.screenWidthDp)
    Box(modifier.fillMaxSize()) {
        GlassBackdrop(Modifier.fillMaxSize().then(if (navScroll != null) Modifier.nestedScroll(navScroll) else Modifier)) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { siblingBy(motion, density) { segments.indexOf(it) } },
                label = "savedSegment",
                modifier = Modifier.fillMaxSize(),
            ) { segment ->
                val state = gridStates.getValue(segment)
                when (segment) {
                    SavedSegment.Bookmarks -> BookmarksList(
                        bookmarks, bookmarkSort, options, savedOptions, videoPositions, ignoredUserIds, actions, state, columns,
                        listTop, bottomPadding, pending, removeWithUndo,
                    )
                    SavedSegment.Downloads -> DownloadsList(
                        downloads, downloadStates, options, actions, state, columns, listTop, bottomPadding, pending, removeWithUndo,
                        storages, storageMounted,
                    )
                    SavedSegment.Filters -> FiltersList(filters, options, actions, state, columns, listTop, bottomPadding, pending, removeWithUndo)
                }
            }
        }
        LargeTopBar(
            title = stringResource(R.string.shell_nav_mine),
            state = topBar,
            floating = true,
            modifier = Modifier.semantics { isTraversalGroup = true; traversalIndex = -1f },
            actions = {
                LagoonIconButton(LucideIcons.Search, stringResource(R.string.search), actions::search)
                if (selected == SavedSegment.Downloads) {
                    LagoonIconButton(LucideIcons.Plus, stringResource(R.string.saved_import), { importSheet = true }, Modifier.testTag("savedImport"))
                }
                LagoonIconButton(LucideIcons.User, stringResource(R.string.following_account), { accountSheet = true }, Modifier.testTag("savedAccount"))
            },
            bottom = if (segments.size > 1) {
                {
                    SegmentedControl(
                        options = segments.map { stringResource(it.label) },
                        selectedIndex = segments.indexOf(selected).coerceAtLeast(0),
                        onSelect = { onSelect(segments[it]) },
                        glass = true,
                        modifier = Modifier
                            .padding(horizontal = LagoonSpacing.pageSide, vertical = LagoonSpacing.xs)
                            .widthIn(max = 560.dp)
                            .testTag(SavedSegmentsTag),
                    )
                }
            } else null,
        )
        if (toast != null) {
            ToastHost(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding))
        }
    }

    if (accountSheet) {
        AccountSheet(account, actions::settings, actions::logIn, onDismiss = { accountSheet = false }, onLogOut = { logOutSheet = true })
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
    if (importSheet) {
        ActionSheet(
            listOf(
                ActionItem(stringResource(R.string.import_folders), LucideIcons.Folder) { actions.importFolders() },
                ActionItem(stringResource(R.string.import_files), LucideIcons.Film) { actions.importFiles() },
            ),
            onDismiss = { importSheet = false },
            title = stringResource(R.string.saved_import),
        )
    }
}

val SavedSegment.label: Int
    get() = when (this) {
        SavedSegment.Bookmarks -> R.string.bookmarks
        SavedSegment.Downloads -> R.string.downloads
        SavedSegment.Filters -> R.string.filters
    }

// ---------------------------------------------------------------------------------------------------------------------
// Shared list frame

@Composable
private fun SavedGrid(
    state: LazyGridState,
    columns: Int,
    top: Dp,
    bottom: Dp,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = Modifier.fillMaxSize().testTag(SavedListTag),
        contentPadding = PaddingValues(start = LagoonSpacing.pageSide, end = LagoonSpacing.pageSide, top = top + LagoonSpacing.xs, bottom = bottom + LagoonSpacing.m),
        verticalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap),
        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap),
        content = content,
    )
}

/** A swipe to the left reveals the red delete tint and removes the item (the host shows the undo). */
@Composable
private fun SwipeToRemove(itemKey: String, label: String, onRemove: () -> Unit, content: @Composable () -> Unit) {
    key(itemKey) {
        val state = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    onRemove()
                    true
                } else false
            },
        )
        SwipeToDismissBox(
            state = state,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                Box(
                    Modifier.fillMaxSize().background(Lagoon.colors.dangerSoft, LagoonShapes.card).padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(LucideIcons.Trash2, contentDescription = label, tint = Lagoon.colors.danger)
                }
            },
        ) { content() }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Downloads

@Composable
private fun DownloadsList(
    downloads: LazyPagingItems<OfflineVideo>?,
    states: Map<Int, DownloadState>,
    options: FollowingListOptions,
    actions: SavedActions,
    state: LazyGridState,
    columns: Int,
    top: Dp,
    bottom: Dp,
    pending: List<String>,
    removeWithUndo: (String, String, () -> Unit) -> Unit,
    storages: List<StorageOption>,
    storageMounted: Boolean,
) {
    var deleting by remember { mutableStateOf<OfflineVideo?>(null) }
    var converting by remember { mutableStateOf<OfflineVideo?>(null) }
    var moving by remember { mutableStateOf<OfflineVideo?>(null) }
    val context = LocalContext.current
    val resources = LocalResources.current
    // v0.1 scrolled to a new download when it was added while the list was at its top
    val firstId = downloads?.takeIf { it.itemCount > 0 }?.peek(0)?.id
    LaunchedEffect(firstId) {
        if (firstId != null && state.firstVisibleItemIndex <= 1) state.animateScrollToItem(0)
    }
    SavedGrid(state, columns, top, bottom) {
        val first = downloads == null || downloads.itemCount == 0
        when {
            downloads == null -> Unit
            first && downloads.loadState.refresh is LoadState.Loading -> items(3, contentType = { "skeleton" }) { SkeletonLiveCard() }
            first -> item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                EmptyState(stringResource(R.string.saved_downloads_empty), icon = LucideIcons.Download, modifier = Modifier.padding(top = 32.dp))
            }
            else -> items(downloads.itemCount, key = { index -> downloads.peek(index)?.id?.let { "d$it" } ?: "i$index" }, contentType = { "download" }) { index ->
                val video = downloads[index] ?: return@items
                val itemKey = "d${video.id}"
                if (itemKey in pending) return@items
                val name = video.name?.trim().orEmpty()
                SwipeToRemove(itemKey, stringResource(R.string.delete), onRemove = {
                    removeWithUndo(itemKey, resources.getString(R.string.saved_download_removed, name)) { actions.deleteDownload(video, keepFiles = true) }
                }) {
                    DownloadCell(video, index, states[video.id], options, actions,
                        onDelete = { deleting = video }, onConvert = { converting = video }, onMoveToApp = { moving = video })
                }
            }
        }
    }
    deleting?.let { video ->
        DeleteDownloadSheet(video, onDelete = { keep -> actions.deleteDownload(video, keep) }, onDismiss = { deleting = null })
    }
    converting?.let { video ->
        QuestionSheet(
            title = stringResource(R.string.convert),
            message = stringResource(R.string.convert_message),
            confirm = stringResource(R.string.convert),
            onConfirm = { actions.convert(video) },
            onDismiss = { converting = null },
        )
    }
    moving?.let { video ->
        MoveSheet(storages, storageMounted, onMove = { actions.moveToApp(video, it) }, onDismiss = { moving = null })
    }
}

/** The words for a download's state (v0.1's status line). */
@Composable
private fun statusText(video: OfflineVideo, status: Int, state: DownloadState?): String? = when (status) {
    OfflineVideo.STATUS_DOWNLOADED -> null
    OfflineVideo.STATUS_DOWNLOADING -> if (video.live || state == null) stringResource(R.string.downloading) else stringResource(R.string.downloading_progress, DownloadLogic.percent(state.progress, state.max))
    OfflineVideo.STATUS_MOVING -> stringResource(R.string.download_moving, DownloadLogic.percent(video.progress, video.maxProgress))
    OfflineVideo.STATUS_DELETING -> stringResource(R.string.download_deleting, DownloadLogic.percent(video.progress, video.maxProgress))
    OfflineVideo.STATUS_CONVERTING -> stringResource(R.string.download_converting, DownloadLogic.percent(video.progress, video.maxProgress))
    OfflineVideo.STATUS_QUEUED -> stringResource(R.string.download_queued)
    OfflineVideo.STATUS_WAITING_FOR_NETWORK -> stringResource(R.string.download_blocked)
    OfflineVideo.STATUS_WAITING_FOR_WIFI -> stringResource(R.string.download_blocked_wifi)
    OfflineVideo.STATUS_WAITING_FOR_STREAM -> stringResource(R.string.download_waiting_for_stream)
    else -> stringResource(R.string.download_pending)
}

@Composable
private fun DownloadCell(
    video: OfflineVideo,
    index: Int,
    state: DownloadState?,
    options: FollowingListOptions,
    actions: SavedActions,
    onDelete: () -> Unit,
    onConvert: () -> Unit,
    onMoveToApp: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var menuOpen by remember { mutableStateOf(false) }
    val status = DownloadLogic.shownStatus(video, state?.active == true)
    val recording = video.live && status == OfflineVideo.STATUS_DOWNLOADING
    val menu = buildList {
        DownloadLogic.actions(video, status).forEach { action ->
            add(
                when (action) {
                    DownloadAction.Stop -> ActionItem(stringResource(R.string.stop_download), LucideIcons.PauseFilled) { actions.stopDownload(video) }
                    DownloadAction.Resume -> ActionItem(stringResource(R.string.resume_download), LucideIcons.PlayFilled) { actions.resumeDownload(video) }
                    DownloadAction.Convert -> ActionItem(stringResource(R.string.convert), LucideIcons.Film) { onConvert() }
                    DownloadAction.MoveToApp -> ActionItem(stringResource(R.string.move_to_app_storage), LucideIcons.HardDrive) { onMoveToApp() }
                    DownloadAction.MoveToShared -> ActionItem(stringResource(R.string.move_to_shared_storage), LucideIcons.Folder) { actions.moveToShared(video) }
                    DownloadAction.ChatFile -> ActionItem(stringResource(R.string.change_chat_file), LucideIcons.MessageSquare) { actions.changeChatFile(video) }
                    DownloadAction.Share -> ActionItem(stringResource(R.string.share), LucideIcons.Share2) { actions.shareDownload(video) }
                    DownloadAction.Delete -> ActionItem(stringResource(R.string.delete), LucideIcons.Trash2, destructive = true) { onDelete() }
                }
            )
        }
        if (video.channelId != null || video.channelLogin != null) {
            add(size - 1, ActionItem(stringResource(R.string.following_open_channel), LucideIcons.User) {
                actions.openChannel(video.channelId, video.channelLogin, video.channelName, video.channelLogo)
            })
        }
        video.gameName?.let { game ->
            add(size - 1, ActionItem(stringResource(R.string.following_open_game, game), LucideIcons.Gamepad2) {
                actions.openGame(video.gameId, video.gameSlug, video.gameName)
            })
        }
    }
    val duration = video.duration
    val position = video.lastWatchPosition
    val channel = displayName(video.channelName, video.channelLogin, options.nameDisplay).takeIf { it.isNotEmpty() }
    val statusLine = statusText(video, status, state)
    val meta = when {
        recording -> listOfNotNull(
            state?.recordedSeconds?.let { stringResource(R.string.saved_recorded, DownloadLogic.formatClock(it)) },
            state?.bytes?.takeIf { it > 0 }?.let { DownloadLogic.formatBytes(it.toDouble()) },
        ).ifEmpty { listOf(stringResource(R.string.downloading)) }.joinToString(" · ")
        status == OfflineVideo.STATUS_DOWNLOADING && state != null -> listOfNotNull(
            "${DownloadLogic.percent(state.progress, state.max)}%",
            state.bytesPerSecond.takeIf { it > 0 }?.let { stringResource(R.string.saved_speed, DownloadLogic.formatBytes(it)) },
            state.secondsLeft?.let { stringResource(R.string.saved_time_left, DownloadLogic.formatClock(it)) },
            if (video.downloadChat) stringResource(R.string.chat_downloading_progress, DownloadLogic.percent(state.chatProgress, state.maxChat).coerceAtMost(100)) else null,
        ).joinToString(" · ")
        statusLine != null -> listOfNotNull(statusLine, channel).joinToString(" · ")
        else -> listOfNotNull(
            channel,
            video.type?.let { TwitchApiHelper.getType(context, it) },
            video.downloadDate?.let { stringResource(R.string.downloaded_date, TwitchApiHelper.formatDate(context, it)) },
            video.sourceStartPosition?.let { start ->
                duration?.let { stringResource(R.string.saved_source_range, DateUtils.formatElapsedTime(start / 1000L), DateUtils.formatElapsedTime((start + it) / 1000L)) }
            },
        ).joinToString(" · ")
    }
    VideoCard(
        title = video.name?.trim().orEmpty(),
        meta = meta,
        onClick = { actions.playOffline(video) },
        modifier = Modifier.staggeredAppear(index).actionsForAccessibility(menu),
        thumbnail = options.image(video.thumbnail),
        duration = duration?.takeIf { !recording }?.let { DateUtils.formatElapsedTime(it / 1000L) },
        progress = if (options.usePositions && position != null && duration != null && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null,
        onLongClick = { menuOpen = true },
        overlay = when {
            recording -> { { RecordingChip(state?.recordedSeconds, Modifier.align(Alignment.TopStart).padding(10.dp)) } }
            statusLine != null -> { { OverlayChip(statusLine, Modifier.align(Alignment.TopStart).padding(10.dp)) } }
            else -> null
        },
        trailing = when {
            status == OfflineVideo.STATUS_DOWNLOADED -> null
            recording -> null
            status == OfflineVideo.STATUS_DOWNLOADING && state != null && !video.live -> {
                { ProgressRing(state.progress.toFloat() / state.max.coerceAtLeast(1), label = statusLine) }
            }
            status == OfflineVideo.STATUS_MOVING || status == OfflineVideo.STATUS_CONVERTING || status == OfflineVideo.STATUS_DELETING -> {
                { ProgressRing(video.progress.toFloat() / video.maxProgress.coerceAtLeast(1), label = statusLine) }
            }
            status == OfflineVideo.STATUS_DOWNLOADING || status == OfflineVideo.STATUS_QUEUED -> { { ProgressRing(null, label = statusLine) } }
            else -> null
        },
    )
    if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = video.name?.trim())
}

/** The red "recording" chip with how long the recording has run. */
@Composable
private fun RecordingChip(seconds: Long?, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Lagoon.colors.overlay, LagoonShapes.pill).padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        LiveDot()
        Text(
            seconds?.let { stringResource(R.string.saved_recording_for, DownloadLogic.formatClock(it)) } ?: stringResource(R.string.saved_recording),
            style = Lagoon.type.labelStrong, color = Lagoon.colors.textPrimary, maxLines = 1,
        )
    }
}

/** v0.1's delete dialog: "keep files" checked by default. */
@Composable
private fun DeleteDownloadSheet(video: OfflineVideo, onDelete: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var keep by rememberSaveable { mutableStateOf(true) }
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.delete), skipHalfHeight = true) {
        Text(
            stringResource(R.string.are_you_sure), style = Lagoon.type.body, color = Lagoon.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            SettingRow(
                stringResource(R.string.keep_files),
                description = stringResource(R.string.saved_keep_files_description),
                icon = LucideIcons.HardDrive,
                trailing = SettingTrailing.Toggle(keep) { keep = it },
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s)) {
            LagoonButton(stringResource(android.R.string.cancel), onDismiss, Modifier.weight(1f), style = ButtonStyle.Secondary)
            LagoonButton(stringResource(R.string.delete), { onDismiss(); onDelete(keep) }, Modifier.weight(1f).testTag("deleteDownloadConfirm"), style = ButtonStyle.Danger)
        }
    }
}

/** v0.1's "move to app storage" dialog: the app's storage places, the chosen one remembered. */
@Composable
private fun MoveSheet(storages: List<StorageOption>, mounted: Boolean, onMove: (Int) -> Unit, onDismiss: () -> Unit) {
    var choice by rememberSaveable { mutableStateOf(0) }
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.move_to_app_storage), skipHalfHeight = true) {
        if (!mounted || storages.isEmpty()) {
            Text(
                stringResource(R.string.no_storage_detected), style = Lagoon.type.body, color = Lagoon.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else {
            SingleChoiceList(
                storages.map { ChoiceOption(it.name, it.freeBytes?.let { free -> stringResource(R.string.download_sheet_free, com.github.andreyasadchy.xtra.ui.download.formatSize(free)) }) },
                selected = choice.coerceIn(0, storages.lastIndex),
                onSelect = { choice = it },
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s)) {
            LagoonButton(stringResource(android.R.string.cancel), onDismiss, Modifier.weight(1f), style = ButtonStyle.Secondary)
            if (mounted && storages.isNotEmpty()) {
                LagoonButton(stringResource(android.R.string.ok), { onDismiss(); onMove(choice) }, Modifier.weight(1f))
            }
        }
    }
}

/** A question with a harmless answer (convert), as a sheet. */
@Composable
private fun QuestionSheet(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    LagoonBottomSheet(onDismiss = onDismiss, title = title, skipHalfHeight = true) {
        Text(message, style = Lagoon.type.body, color = Lagoon.colors.textSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s)) {
            LagoonButton(stringResource(android.R.string.cancel), onDismiss, Modifier.weight(1f), style = ButtonStyle.Secondary)
            LagoonButton(confirm, { onDismiss(); onConfirm() }, Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Bookmarks

@Composable
private fun BookmarksList(
    bookmarks: List<Bookmark>?,
    sort: BookmarkSortChoice,
    options: FollowingListOptions,
    savedOptions: SavedOptions,
    positions: Map<Long, Long>,
    ignored: Set<String>,
    actions: SavedActions,
    state: LazyGridState,
    columns: Int,
    top: Dp,
    bottom: Dp,
    pending: List<String>,
    removeWithUndo: (String, String, () -> Unit) -> Unit,
) {
    var sortSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val firstId = bookmarks?.firstOrNull()?.id
    LaunchedEffect(firstId) {
        if (firstId != null && state.firstVisibleItemIndex <= 2) state.animateScrollToItem(0)
    }
    SavedGrid(state, columns, top, bottom) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "chips", contentType = "chips") {
            FilterChipRow(
                listOf(
                    FilterChipItem(
                        "${stringResource(bookmarkSortLabel(sort.sort))} · ${stringResource(if (sort.order == BookmarkSorts.ORDER_ASC) R.string.ascending else R.string.descending)}",
                        icon = LucideIcons.ArrowUpDown, dropdown = true, onClick = { sortSheet = true },
                    )
                ),
                contentPadding = PaddingValues(0.dp),
            )
        }
        when {
            bookmarks == null -> items(3, contentType = { "skeleton" }) { SkeletonLiveCard() }
            bookmarks.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                EmptyState(stringResource(R.string.saved_bookmarks_empty), icon = LucideIcons.Bookmark, modifier = Modifier.padding(top = 32.dp))
            }
            else -> items(bookmarks.size, key = { "b${bookmarks[it].id}" }, contentType = { "bookmark" }) { index ->
                val bookmark = bookmarks[index]
                val itemKey = "b${bookmark.id}"
                if (itemKey in pending) return@items
                SwipeToRemove(itemKey, stringResource(R.string.delete), onRemove = {
                    removeWithUndo(itemKey, resources.getString(R.string.saved_bookmark_removed, bookmark.title?.trim().orEmpty())) { actions.deleteBookmark(bookmark) }
                }) {
                    BookmarkCell(bookmark, index, options, savedOptions, positions, bookmark.userId in ignored, actions, onDelete = {
                        removeWithUndo(itemKey, resources.getString(R.string.saved_bookmark_removed, bookmark.title?.trim().orEmpty())) { actions.deleteBookmark(bookmark) }
                    })
                }
            }
        }
    }
    if (sortSheet) {
        BookmarkSortSheet(sort, onApply = { choice, saveDefault -> actions.applyBookmarkSort(choice.sort, choice.order, saveDefault) }, onDismiss = { sortSheet = false })
    }
}

private fun bookmarkSortLabel(sort: String) = when (sort) {
    BookmarkSorts.SORT_EXPIRES_AT -> R.string.deletion_date
    BookmarkSorts.SORT_CREATED_AT -> R.string.creation_date
    else -> R.string.saved_date
}

@Composable
private fun BookmarkCell(
    bookmark: Bookmark,
    index: Int,
    options: FollowingListOptions,
    savedOptions: SavedOptions,
    positions: Map<Long, Long>,
    ignored: Boolean,
    actions: SavedActions,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var menuOpen by remember { mutableStateOf(false) }
    val durationSeconds = bookmark.duration?.let { it.toIntOrNull() ?: TwitchApiHelper.getDuration(it) }
    val position = bookmark.videoId?.toLongOrNull()?.let { positions[it] }
    val menu = buildList {
        if (!bookmark.videoId.isNullOrBlank()) {
            add(ActionItem(stringResource(R.string.refresh), LucideIcons.RefreshCw) { actions.refreshBookmark(bookmark) })
            add(ActionItem(stringResource(R.string.download), LucideIcons.Download) { actions.downloadBookmark(bookmark) })
        }
        val userId = bookmark.userId
        if (bookmark.type?.lowercase() == "archive" && userId != null && savedOptions.bookmarkTimeLeft) {
            add(ActionItem(stringResource(if (ignored) R.string.vod_remove_ignore else R.string.vod_ignore_user), LucideIcons.Clock) { actions.toggleIgnore(userId) })
        }
        if (bookmark.userId != null || bookmark.userLogin != null) {
            add(ActionItem(stringResource(R.string.following_open_channel), LucideIcons.User) {
                actions.openChannel(bookmark.userId, bookmark.userLogin, bookmark.userName, bookmark.userLogo)
            })
        }
        bookmark.gameName?.let { game ->
            add(ActionItem(stringResource(R.string.following_open_game, game), LucideIcons.Gamepad2) { actions.openGame(bookmark.gameId, bookmark.gameSlug, bookmark.gameName) })
        }
        add(ActionItem(stringResource(R.string.delete), LucideIcons.Trash2, destructive = true) { onDelete() })
    }
    val timeLeft = if (savedOptions.bookmarkTimeLeft && !ignored) {
        BookmarkLogic.secondsLeft(bookmark, options.nowMillis())?.let { TwitchApiHelper.getDurationFromSeconds(context, BookmarkLogic.roughly(it).toString()) }
    } else null
    val meta = listOfNotNull(
        displayName(bookmark.userName, bookmark.userLogin, options.nameDisplay).takeIf { it.isNotEmpty() },
        bookmark.type?.let { TwitchApiHelper.getType(context, it) },
        bookmark.createdAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds()?.takeIf { it > 0 }?.let { TwitchApiHelper.formatDate(context, it) },
    ).joinToString(" · ")
    VideoCard(
        title = bookmark.title?.trim().orEmpty(),
        meta = meta,
        onClick = { actions.openBookmark(bookmark, position) },
        modifier = Modifier.staggeredAppear(index).actionsForAccessibility(menu),
        thumbnail = options.image(bookmark.thumbnail),
        duration = durationSeconds?.let { DateUtils.formatElapsedTime(it.toLong()) },
        progress = if (options.usePositions && position != null && durationSeconds != null && durationSeconds > 0) (position / (durationSeconds * 1000f)).coerceIn(0f, 1f) else null,
        onLongClick = { menuOpen = true },
        overlay = timeLeft?.let { { OverlayChip(stringResource(R.string.vod_time_left, it), Modifier.align(Alignment.TopStart).padding(10.dp)) } },
    )
    if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = bookmark.title?.trim())
}

/** Replaces BookmarksSortDialog: sort and order, apply or save as default. */
@Composable
private fun BookmarkSortSheet(current: BookmarkSortChoice, onApply: (BookmarkSortChoice, Boolean) -> Unit, onDismiss: () -> Unit) {
    val sorts = listOf(BookmarkSorts.SORT_SAVED_AT, BookmarkSorts.SORT_CREATED_AT, BookmarkSorts.SORT_EXPIRES_AT)
    val orders = listOf(BookmarkSorts.ORDER_DESC, BookmarkSorts.ORDER_ASC)
    var sort by rememberSaveable { mutableStateOf(current.sort) }
    var order by rememberSaveable { mutableStateOf(current.order) }
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.sort), skipHalfHeight = true) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetSection(stringResource(R.string.sort))
            SingleChoiceList(sorts.map { ChoiceOption(stringResource(bookmarkSortLabel(it))) }, selected = sorts.indexOf(sort).coerceAtLeast(0), onSelect = { sort = sorts[it] })
            SheetSection(stringResource(R.string.order))
            SingleChoiceList(
                listOf(ChoiceOption(stringResource(R.string.descending)), ChoiceOption(stringResource(R.string.ascending))),
                selected = orders.indexOf(order).coerceAtLeast(0),
                onSelect = { order = orders[it] },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s)) {
                LagoonButton(stringResource(R.string.save_default), { onDismiss(); onApply(BookmarkSortChoice(sort, order), true) }, Modifier.weight(1f), style = ButtonStyle.Secondary)
                LagoonButton(stringResource(R.string.apply), { onDismiss(); onApply(BookmarkSortChoice(sort, order), false) }, Modifier.weight(1f))
            }
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

// ---------------------------------------------------------------------------------------------------------------------
// Filters

@Composable
private fun FiltersList(
    filters: LazyPagingItems<SavedFilter>?,
    options: FollowingListOptions,
    actions: SavedActions,
    state: LazyGridState,
    columns: Int,
    top: Dp,
    bottom: Dp,
    pending: List<String>,
    removeWithUndo: (String, String, () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    SavedGrid(state, columns, top, bottom) {
        val first = filters == null || filters.itemCount == 0
        when {
            filters == null -> Unit
            first && filters.loadState.refresh is LoadState.Loading -> items(3, contentType = { "skeleton" }) { SkeletonRow() }
            first -> item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                EmptyState(stringResource(R.string.saved_filters_empty), icon = LucideIcons.ListFilter, modifier = Modifier.padding(top = 32.dp))
            }
            else -> items(filters.itemCount, key = { index -> filters.peek(index)?.id?.let { "f$it" } ?: "i$index" }, contentType = { "filter" }) { index ->
                val filter = filters[index] ?: return@items
                val itemKey = "f${filter.id}"
                if (itemKey in pending) return@items
                val title = filter.gameName ?: stringResource(R.string.saved_filter_all_games)
                val remove = { removeWithUndo(itemKey, resources.getString(R.string.saved_filter_removed, title)) { actions.deleteFilter(filter) } }
                SwipeToRemove(itemKey, stringResource(R.string.delete), onRemove = remove) {
                    FilterCell(filter, title, index, actions, onDelete = remove)
                }
            }
        }
    }
}

@Composable
private fun FilterCell(filter: SavedFilter, title: String, index: Int, actions: SavedActions, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val tags = filter.tags?.split(',')?.filter { it.isNotBlank() }.orEmpty()
    val languages = filter.languages?.split(',')?.filter { it.isNotBlank() }.orEmpty()
    val lines = listOfNotNull(
        tags.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.tags, it.size, it.joinToString()) },
        languages.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.languages, it.size, it.joinToString()) },
    )
    val menu = listOf(
        ActionItem(stringResource(R.string.saved_filter_open), LucideIcons.ListFilter) { actions.openFilter(filter) },
        ActionItem(stringResource(R.string.delete), LucideIcons.Trash2, destructive = true) { onDelete() },
    )
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredAppear(index)
            .pressable(onClick = { actions.openFilter(filter) }, onLongClick = { menuOpen = true }, shape = LagoonShapes.card)
            .background(Lagoon.colors.card)
            .heightIn(min = 72.dp)
            .padding(horizontal = LagoonSpacing.cardPadding, vertical = LagoonSpacing.s)
            .actionsForAccessibility(menu)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
    ) {
        SettingIconTile(if (filter.gameName != null) LucideIcons.Gamepad2 else LucideIcons.Radio)
        Column(Modifier.weight(1f)) {
            Text(title, style = Lagoon.type.bodyStrong, color = Lagoon.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            lines.forEach {
                Text(it, style = Lagoon.type.label, color = Lagoon.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(LucideIcons.ChevronRight, contentDescription = null, tint = Lagoon.colors.textHint, modifier = Modifier.size(18.dp))
    }
    if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = title)
}
