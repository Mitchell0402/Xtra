package com.github.andreyasadchy.xtra.ui.saved

import androidx.compose.runtime.Composable
import com.github.andreyasadchy.xtra.ui.main.nav.ComposePage
import com.github.andreyasadchy.xtra.ui.main.nav.PageHost
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StatFs
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.model.ui.DownloadProgress
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.SavedFilter
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.design.components.GlassContext
import com.github.andreyasadchy.xtra.ui.design.components.LocalGlass
import com.github.andreyasadchy.xtra.ui.design.components.rememberToastHostState
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.download.StorageOption
import com.github.andreyasadchy.xtra.ui.download.StreamDownloadService
import com.github.andreyasadchy.xtra.ui.download.VideoDownloadService
import com.github.andreyasadchy.xtra.ui.following.FollowingAccount
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.saved.bookmarks.BookmarksViewModel
import com.github.andreyasadchy.xtra.ui.saved.downloads.DownloadsViewModel
import com.github.andreyasadchy.xtra.ui.saved.filters.FiltersViewModel
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * The Me page in Compose (U5, plan 7.6), in place of the v0.1 SavedPagerFragment / SavedMediaFragment and their
 * bookmarks, downloads and filters fragments.
 * It binds to the download services while it is started, as v0.1's downloads list did, to show live progress.
 */
class SavedDestination(host: PageHost) : ComposePage(host), SavedActions {

    private val viewModel: SavedPagerViewModel by viewModels { SavedPagerViewModel.Companion.SavedPagerViewModelFactory }
    private val downloadsViewModel: DownloadsViewModel by viewModels { DownloadsViewModel.Companion.DownloadsViewModelFactory }
    private val bookmarksViewModel: BookmarksViewModel by viewModels { BookmarksViewModel.Companion.BookmarksViewModelFactory }
    private val filtersViewModel: FiltersViewModel by viewModels { FiltersViewModel.Companion.FiltersViewModelFactory }
    private val scrollToTopRequests = mutableIntStateOf(0)

    private var folderResultLauncher: ActivityResultLauncher<Intent>? = null
    private var fileResultLauncher: ActivityResultLauncher<Intent>? = null
    private var moveResultLauncher: ActivityResultLauncher<Intent>? = null
    private var chatFileResultLauncher: ActivityResultLauncher<Intent>? = null

    private var videoDownloadService: VideoDownloadService? = null
    private var videoDownloadServiceConnection: ServiceConnection? = null
    private var streamDownloadService: StreamDownloadService? = null
    private var streamDownloadServiceConnection: ServiceConnection? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The services' progress as the rows read it; the pace of each download is worked out here. */
    private val downloadStates = mutableStateMapOf<Int, DownloadState>()
    private val paces = mutableMapOf<Int, DownloadLogic.Pace>()

    override fun onCreate() {
        // the bookmarks' saved sort and v0.1's refreshes of their users and videos
        val context = requireContext()
        lifecycleScope.launch {
            if (bookmarksViewModel.filter.value == null) {
                val saved = bookmarksViewModel.getChannelSort(BookmarkLogic.SORT_ID)
                bookmarksViewModel.setFilter(sort = saved?.videoSort, order = saved?.videoType)
            }
        }
        if (context.prefs().getBoolean(C.UI_BOOKMARK_TIME_LEFT, true)) updateBookmarkUsers()
        val helixHeaders = TwitchApiHelper.getHelixHeaders(context)
        if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            bookmarksViewModel.updateVideos(context.filesDir.path, context.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP), helixHeaders)
        }
    }

    private fun onFolderResult(result: ActivityResult) {
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    viewModel.saveFolders(it.toString())
                }
            }
    }

    private fun onFileResult(result: ActivityResult) {
            if (result.resultCode == Activity.RESULT_OK) {
                val list = mutableListOf<String>()
                result.data?.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let {
                            requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            list.add(it.toString())
                        }
                    }
                } ?: result.data?.data?.let {
                    requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    list.add(it.toString())
                }
                viewModel.saveVideos(list)
            }
    }

    private fun onMoveResult(result: ActivityResult) {
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    downloadsViewModel.selectedVideo?.let { video ->
                        requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        downloadsViewModel.moveToSharedStorage(it, video)
                    }
                }
            }
    }

    private fun onChatFileResult(result: ActivityResult) {
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    downloadsViewModel.selectedVideo?.let { video ->
                        requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        downloadsViewModel.updateChatUrl(it, video)
                    }
                }
            }
    }

    private fun updateBookmarkUsers() {
        val context = requireContext()
        bookmarksViewModel.updateUsers(
            context.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            TwitchApiHelper.getGQLHeaders(context),
            TwitchApiHelper.getHelixHeaders(context),
            context.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
    }

    @Composable
    override fun Content() {
        val context = requireContext()
        val prefs = context.prefs()
        val loggedIn = !TwitchApiHelper.getGQLHeaders(context, true)[C.HEADER_TOKEN].isNullOrBlank() ||
                !TwitchApiHelper.getHelixHeaders(context)[C.HEADER_TOKEN].isNullOrBlank()
        val account = FollowingAccount(loggedIn, context.tokenPrefs().getString(C.USERNAME, null))
        val segmentsPref = prefs.getString(C.UI_SAVED_TABS, null)
        val segments = SavedSegment.list(segmentsPref)
        val defaultSegment = SavedSegment.default(segmentsPref, segments)
        val options = FollowingListOptions(
            nameDisplay = prefs.getString(C.UI_NAME_DISPLAY, "0") ?: "0",
            truncateCounts = prefs.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true),
            usePositions = prefs.getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true),
        )
        val savedOptions = SavedOptions(bookmarkTimeLeft = prefs.getBoolean(C.UI_BOOKMARK_TIME_LEFT, true))
        var segmentKey by rememberSaveable { mutableStateOf<String?>(null) }
        val contract = ActivityResultContracts.StartActivityForResult()
        folderResultLauncher = rememberLauncherForActivityResult(contract, ::onFolderResult)
        fileResultLauncher = rememberLauncherForActivityResult(contract, ::onFileResult)
        moveResultLauncher = rememberLauncherForActivityResult(contract, ::onMoveResult)
        chatFileResultLauncher = rememberLauncherForActivityResult(contract, ::onChatFileResult)
        val selected = segmentKey?.let { SavedSegment.of(it) }?.takeIf { it in segments } ?: defaultSegment
        val downloads = downloadsViewModel.flow.collectAsLazyPagingItems()
        val filters = filtersViewModel.flow.collectAsLazyPagingItems()
        val bookmarkFilter by bookmarksViewModel.filter.collectAsState()
        val bookmarkList by remember { bookmarksViewModel.flow }.collectAsState(null)
        val positions by remember { bookmarksViewModel.positions }.collectAsState(emptyList())
        val ignored by remember { bookmarksViewModel.ignoredUsers }.collectAsState(emptyList())
        val toast = rememberToastHostState()
        LaunchedEffect(Unit) {
            bookmarksViewModel.integrity.collect { activity.getNewIntegrityToken(it, childFragmentManager) }
        }
        // recordings: no progress callbacks, so their time and size are read once a second
        LaunchedEffect(Unit) {
            while (true) {
                refreshRecordings()
                delay(1000)
            }
        }
        val sort = bookmarksViewModel.sort
        val order = bookmarksViewModel.order
        val sortedBookmarks = remember(bookmarkList, bookmarkFilter) {
            bookmarkList?.let { BookmarkLogic.sorted(it, sort, order, System.currentTimeMillis()) }
        }
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottom = shell?.let { with(LocalDensity.current) { it.bottomBarHeightPx.toDp() } }?.takeIf { it.value > 0f } ?: navBottom
        CompositionLocalProvider(LocalGlass provides (shell?.pageGlass() ?: GlassContext.Solid)) {
            SavedScreen(
                segments = segments,
                selected = selected,
                onSelect = { segmentKey = it.key },
                account = account,
                bookmarks = sortedBookmarks,
                bookmarkSort = BookmarkSortChoice(sort, order),
                downloads = downloads.takeIf { SavedSegment.Downloads in segments },
                downloadStates = downloadStates,
                filters = filters.takeIf { SavedSegment.Filters in segments },
                options = options,
                savedOptions = savedOptions,
                actions = this@SavedDestination,
                storages = remember { storages() },
                storageMounted = remember { Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED },
                videoPositions = positions.associate { it.id to it.position },
                ignoredUserIds = ignored.map { it.userId }.toSet(),
                bottomPadding = bottom,
                navScroll = shell?.hideOnScroll?.nestedScrollConnection,
                scrollToTopRequests = scrollToTopRequests.intValue,
                toast = toast,
            )
        }
    }

    private fun storages(): List<StorageOption> =
        requireContext().getExternalFilesDirs(".downloads").mapIndexedNotNull { index, file ->
            file?.absolutePath?.let { path ->
                val free = try { StatFs(path).availableBytes } catch (e: Exception) { null }
                if (index == 0) {
                    StorageOption(getString(R.string.internal_storage), path, free)
                } else {
                    path.substringBefore("/Android/data", "").takeIf { it.isNotBlank() }?.let {
                        StorageOption(it.substringAfterLast(File.separatorChar), path, free)
                    }
                }
            }
        }

    // -- download services (v0.1 DownloadsFragment) ------------------------------------------------------------------

    /** Between onStart and onStop: a late service connection after the page went away is dropped (v0.1 checked its view). */
    private var isStarted = false

    override fun onStart() {
        isStarted = true
        lifecycleScope.launch {
            val activeDownloads = downloadsViewModel.getActiveDownloads()
            if (activeDownloads.isNotEmpty()) {
                if (activeDownloads.any { it.live }) bindStreamDownloadService()
                if (activeDownloads.any { !it.live }) bindVideoDownloadService()
            }
        }
    }

    override fun onStop() {
        isStarted = false
        videoDownloadService?.listener = null
        videoDownloadServiceConnection?.let { requireContext().unbindService(it) }
        videoDownloadServiceConnection = null
        videoDownloadService = null
        streamDownloadService?.listener = null
        streamDownloadServiceConnection?.let { requireContext().unbindService(it) }
        streamDownloadServiceConnection = null
        streamDownloadService = null
        downloadStates.clear()
        paces.clear()
    }

    /** A copy of the service's numbers, taken on the service's thread and applied on the main thread. */
    private fun onProgress(progress: DownloadProgress) {
        val id = progress.id
        val now = System.currentTimeMillis()
        val bytes = progress.transferred.get().takeIf { it > 0 } ?: progress.bytes
        val done = progress.progress
        val max = progress.maxProgress
        val chat = progress.chatProgress
        val maxChat = progress.maxChatProgress
        mainHandler.post {
            val pace = paces[id]?.next(now, bytes, done) ?: DownloadLogic.Pace(now, bytes, done)
            paces[id] = pace
            downloadStates[id] = DownloadState(
                active = true, progress = done, max = max, chatProgress = chat, maxChat = maxChat, bytes = bytes,
                bytesPerSecond = pace.bytesPerSecond, secondsLeft = pace.secondsLeft(max),
            )
        }
    }

    /** Video downloads the service no longer reports are gone from the states (finished, stopped). */
    private fun pruneVideoStates(active: List<DownloadProgress>) {
        val ids = active.map { it.id }.toSet()
        downloadStates.keys.filter { id -> id !in ids && downloadStates[id]?.recordedSeconds == null }.forEach {
            downloadStates.remove(it)
            paces.remove(it)
        }
    }

    private fun refreshRecordings() {
        val now = System.currentTimeMillis()
        val active = streamDownloadService?.activeDownloads?.toList().orEmpty()
        val ids = active.map { it.id }.toSet()
        active.forEach { progress ->
            downloadStates[progress.id] = DownloadState(
                active = true,
                bytes = progress.bytes,
                recordedSeconds = progress.startedAt.takeIf { it > 0 }?.let { ((now - it) / 1000).coerceAtLeast(0) },
            )
        }
        downloadStates.keys.filter { downloadStates[it]?.recordedSeconds != null && it !in ids }.forEach { downloadStates.remove(it) }
        videoDownloadService?.activeDownloads?.toList()?.let { pruneVideoStates(it) }
        // the service reports a recording before its first segment: still "active"
        active.filter { it.startedAt == 0L }.forEach { downloadStates[it.id] = DownloadState(active = true) }
    }

    fun bindVideoDownloadService(started: Boolean = false) {
        if (videoDownloadServiceConnection == null) {
            val listener = object : VideoDownloadService.Listener {
                override fun update(downloadProgress: DownloadProgress) = onProgress(downloadProgress)

                override fun unbind() {
                    mainHandler.post {
                        videoDownloadService?.listener = null
                        videoDownloadServiceConnection?.let { requireContext().unbindService(it) }
                        videoDownloadServiceConnection = null
                        videoDownloadService = null
                        pruneVideoStates(emptyList())
                    }
                }
            }
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (isStarted) {
                        val binder = service as VideoDownloadService.ServiceBinder
                        videoDownloadService = binder.getService()
                        if (started || !videoDownloadService?.activeDownloads.isNullOrEmpty()) {
                            videoDownloadService?.listener = listener
                            videoDownloadService?.activeDownloads?.toList()?.forEach { listener.update(it) }
                        } else {
                            videoDownloadServiceConnection?.let { requireContext().unbindService(it) }
                            videoDownloadServiceConnection = null
                            videoDownloadService?.stopSelf()
                            videoDownloadService = null
                        }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    videoDownloadService = null
                }
            }
            requireContext().bindService(Intent(requireContext(), VideoDownloadService::class.java), connection, Context.BIND_AUTO_CREATE)
            videoDownloadServiceConnection = connection
        }
    }

    fun bindStreamDownloadService(started: Boolean = false) {
        if (streamDownloadServiceConnection == null) {
            val listener = object : StreamDownloadService.Listener {
                override fun unbind() {
                    mainHandler.post {
                        streamDownloadService?.listener = null
                        streamDownloadServiceConnection?.let { requireContext().unbindService(it) }
                        streamDownloadServiceConnection = null
                        streamDownloadService?.stopSelf()
                        streamDownloadService = null
                    }
                }
            }
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (isStarted) {
                        val binder = service as StreamDownloadService.ServiceBinder
                        streamDownloadService = binder.getService()
                        if (started || !streamDownloadService?.activeDownloads.isNullOrEmpty()) {
                            streamDownloadService?.listener = listener
                            refreshRecordings()
                        } else {
                            streamDownloadServiceConnection?.let { requireContext().unbindService(it) }
                            streamDownloadServiceConnection = null
                            streamDownloadService = null
                        }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    streamDownloadService = null
                }
            }
            requireContext().bindService(Intent(requireContext(), StreamDownloadService::class.java), connection, Context.BIND_AUTO_CREATE)
            streamDownloadServiceConnection = connection
        }
    }

    // -- SavedActions: what the v0.1 fragments, adapters and menus did ----------------------------------------------

    override fun search() {
        nav.openSearch()
    }

    override fun settings() {
        activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java))
    }

    override fun logIn() {
        activity.loginResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
    }

    override fun logOut() {
        activity.logoutResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
    }

    override fun importFolders() {
        folderResultLauncher?.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    override fun importFiles() {
        fileResultLauncher?.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        })
    }

    /** v0.1 opened channels from the Me page with `updateLocal`, so the local copy of the channel gets refreshed. */
    override fun openChannel(id: String?, login: String?, name: String?, image: String?) {
        nav.openChannel(id = id, login = login, name = name, image = image, updateLocal = true)
    }

    override fun openGame(id: String?, slug: String?, name: String?) {
        nav.openGame(id = id, slug = slug, name = name)
    }

    override fun playOffline(video: OfflineVideo) {
        activity.startOfflineVideo(video, DownloadLogic.startPosition(video))
    }

    override fun stopDownload(video: OfflineVideo) {
        if (video.status == OfflineVideo.STATUS_WAITING_FOR_NETWORK || video.status == OfflineVideo.STATUS_WAITING_FOR_WIFI) {
            downloadsViewModel.updateDownloadStatus(video, false)
        } else if (video.live) {
            if (video.status == OfflineVideo.STATUS_PENDING ||
                ((video.status == OfflineVideo.STATUS_DOWNLOADING || video.status == OfflineVideo.STATUS_QUEUED || video.status == OfflineVideo.STATUS_WAITING_FOR_STREAM) &&
                        streamDownloadService?.activeDownloads?.toList()?.find { it.id == video.id } == null)
            ) {
                downloadsViewModel.finishDownload(video)
            } else {
                requireContext().startService(Intent(requireContext(), StreamDownloadService::class.java).apply {
                    action = StreamDownloadService.INTENT_STOP
                    putExtra(StreamDownloadService.KEY_VIDEO_ID, video.id)
                })
                bindStreamDownloadService(true)
            }
        } else {
            requireContext().startService(Intent(requireContext(), VideoDownloadService::class.java).apply {
                action = VideoDownloadService.INTENT_STOP
                putExtra(VideoDownloadService.KEY_VIDEO_ID, video.id)
            })
            bindVideoDownloadService(true)
        }
    }

    override fun resumeDownload(video: OfflineVideo) {
        val waitForWifi = if (requireContext().prefs().getBoolean(C.DOWNLOAD_WIFI_ONLY, false)) {
            val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } else false
        if (waitForWifi) {
            downloadsViewModel.updateDownloadStatus(video, true)
        } else if (video.live) {
            requireContext().startService(Intent(requireContext(), StreamDownloadService::class.java).apply {
                action = StreamDownloadService.INTENT_START
                putExtra(StreamDownloadService.KEY_VIDEO_ID, video.id)
            })
            bindStreamDownloadService(true)
        } else {
            requireContext().startService(Intent(requireContext(), VideoDownloadService::class.java).apply {
                action = VideoDownloadService.INTENT_START
                putExtra(VideoDownloadService.KEY_VIDEO_ID, video.id)
            })
            bindVideoDownloadService(true)
        }
    }

    override fun convert(video: OfflineVideo) = downloadsViewModel.convertToFile(video)

    override fun moveToShared(video: OfflineVideo) {
        downloadsViewModel.selectedVideo = video
        moveResultLauncher?.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }

    override fun moveToApp(video: OfflineVideo, index: Int) {
        val storages = storages()
        val checked = if (storages.size == 1) 0 else index
        storages.getOrNull(checked)?.let { storage ->
            requireContext().prefs().edit { putInt(C.DOWNLOAD_STORAGE, checked) }
            downloadsViewModel.moveToAppStorage(storage.path, video)
        }
    }

    override fun changeChatFile(video: OfflineVideo) {
        downloadsViewModel.selectedVideo = video
        chatFileResultLauncher?.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        })
    }

    override fun shareDownload(video: OfflineVideo) {
        video.url?.let { videoUrl ->
            val uri = if (videoUrl.endsWith(".m3u8")) videoUrl.substringBefore("%2F").toUri() else videoUrl.toUri()
            startActivity(Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                setDataAndType(uri, requireContext().contentResolver.getType(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                video.name?.let { putExtra(Intent.EXTRA_TITLE, it) }
            }, null))
        }
    }

    /** v0.1's delete: cancel the download in its service first, then remove the row (and the files unless kept). */
    override fun deleteDownload(video: OfflineVideo, keepFiles: Boolean) {
        if (video.live) {
            if (streamDownloadService?.activeDownloads?.toList()?.find { it.id == video.id } != null) {
                requireContext().startService(Intent(requireContext(), StreamDownloadService::class.java).apply {
                    action = StreamDownloadService.INTENT_CANCEL
                    putExtra(StreamDownloadService.KEY_VIDEO_ID, video.id)
                })
                bindStreamDownloadService(true)
            }
        } else {
            if (videoDownloadService?.activeDownloads?.toList()?.find { it.id == video.id } != null) {
                requireContext().startService(Intent(requireContext(), VideoDownloadService::class.java).apply {
                    action = VideoDownloadService.INTENT_CANCEL
                    putExtra(VideoDownloadService.KEY_VIDEO_ID, video.id)
                })
                bindVideoDownloadService(true)
            }
        }
        downloadsViewModel.delete(video, keepFiles)
    }

    private fun Bookmark.toVideo() = Video(
        id = videoId,
        channelId = userId,
        channelLogin = userLogin,
        channelName = userName,
        channelImageURL = userLogo,
        gameId = gameId,
        gameSlug = gameSlug,
        gameName = gameName,
        title = title,
        thumbnailURL = thumbnail,
        createdAt = createdAt,
        durationSeconds = duration?.let { it.toIntOrNull() ?: TwitchApiHelper.getDuration(it) },
        type = type,
        animatedPreviewURL = animatedPreviewURL,
    )

    override fun openBookmark(bookmark: Bookmark, position: Long?) {
        activity.startVideo(bookmark.toVideo(), position, false)
    }

    override fun refreshBookmark(bookmark: Bookmark) {
        val context = requireContext()
        bookmarksViewModel.updateVideo(
            context.filesDir.path,
            bookmark.videoId,
            context.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            TwitchApiHelper.getGQLHeaders(context),
            TwitchApiHelper.getHelixHeaders(context),
            context.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
    }

    /** v0.1's bookmark download left out the game slug; it is passed now (it was only used to open the game). */
    override fun downloadBookmark(bookmark: Bookmark) {
        val video = bookmark.toVideo()
        DownloadDialog.newVideoInstance(
            id = video.id,
            channelId = video.channelId,
            channelLogin = video.channelLogin,
            channelName = video.channelName,
            channelImage = video.channelImage,
            gameId = video.gameId,
            gameSlug = video.gameSlug,
            gameName = video.gameName,
            title = video.title,
            thumbnail = video.thumbnail,
            createdAt = video.createdAt,
            durationSeconds = video.durationSeconds,
            type = video.type,
            animatedPreviewUrl = video.animatedPreviewURL,
        ).show(childFragmentManager, null)
    }

    override fun toggleIgnore(userId: String) = bookmarksViewModel.vodIgnoreUser(userId)

    override fun deleteBookmark(bookmark: Bookmark) = bookmarksViewModel.delete(bookmark)

    override fun applyBookmarkSort(sort: String, order: String, saveDefault: Boolean) {
        lifecycleScope.launch {
            if (sort != bookmarksViewModel.sort || order != bookmarksViewModel.order) {
                bookmarksViewModel.setFilter(sort, order)
            }
            if (saveDefault) {
                val item = bookmarksViewModel.getChannelSort(BookmarkLogic.SORT_ID)?.apply {
                    videoSort = sort
                    videoType = order
                } ?: ChannelSort(id = BookmarkLogic.SORT_ID, videoSort = sort, videoType = order)
                bookmarksViewModel.saveChannelSort(item)
            }
        }
    }

    override fun openFilter(filter: SavedFilter) {
        val tags = filter.tags?.split(',')
        val languages = filter.languages?.split(',')
        if (filter.gameId != null || filter.gameSlug != null || filter.gameName != null) {
            nav.openGame(id = filter.gameId, slug = filter.gameSlug, name = filter.gameName, tags = tags, languages = languages)
        } else {
            nav.openTopStreams(tags = tags, languages = languages)
        }
    }

    override fun deleteFilter(filter: SavedFilter) = filtersViewModel.delete(filter)

    override fun scrollToTop() {
        scrollToTopRequests.intValue++
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        if (callback == "users") updateBookmarkUsers()
    }
}
