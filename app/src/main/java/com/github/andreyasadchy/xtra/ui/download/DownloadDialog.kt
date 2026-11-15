package com.github.andreyasadchy.xtra.ui.download

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.ui.common.ComposeSheetDialog
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.download.DownloadViewModel.Companion.DownloadViewModelFactory
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * The download and record sheet (plan 7.6, U5) in place of the v0.1 download dialog. The factory methods and their
 * arguments are unchanged, so the player and every page open it as before; the qualities, checks, MainActivity calls
 * and stored choices are v0.1's. The look is [DownloadSheetContent].
 */
class DownloadDialog : ComposeSheetDialog(), IntegrityDialog.Listener {

    companion object {
        private const val STATE_QUALITY = "state_quality"
        private const val STATE_LOCATION = "state_location"
        private const val STATE_SHARED_PATH = "state_shared_path"
        private const val STATE_STORAGE = "state_storage"
        private const val STATE_CHAT = "state_chat"
        private const val STATE_CHAT_EMOTES = "state_chat_emotes"
        private const val STATE_FROM = "state_from"
        private const val STATE_TO = "state_to"

        private const val STREAM = "stream"
        private const val VIDEO = "video"
        private const val CLIP = "clip"

        private const val KEY_TYPE = "type"
        private const val KEY_STREAM_ID = "streamId"
        private const val KEY_VIDEO_ID = "videoId"
        private const val KEY_CLIP_ID = "clipId"
        private const val KEY_CHANNEL_ID = "channelId"
        private const val KEY_CHANNEL_LOGIN = "channelLogin"
        private const val KEY_CHANNEL_NAME = "channelName"
        private const val KEY_CHANNEL_IMAGE = "channelImage"
        private const val KEY_GAME_ID = "gameId"
        private const val KEY_GAME_SLUG = "gameSlug"
        private const val KEY_GAME_NAME = "gameName"
        private const val KEY_TITLE = "title"
        private const val KEY_THUMBNAIL = "thumbnail"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_DURATION_SECONDS = "durationSeconds"
        private const val KEY_VIDEO_TYPE = "videoType"
        private const val KEY_VIDEO_OFFSET_SECONDS = "videoOffsetSeconds"
        private const val KEY_VIDEO_CREATED_AT = "videoCreatedAt"
        private const val KEY_VIDEO_ANIMATED_PREVIEW = "animatedPreviewUrl"
        private const val KEY_VIDEO_TOTAL_DURATION = "totalDuration"
        private const val KEY_VIDEO_CURRENT_POSITION = "currentPosition"
        private const val KEY_QUALITY_NAMES = "quality_names"
        private const val KEY_QUALITY_RESOLUTIONS = "quality_resolutions"
        private const val KEY_QUALITY_FRAME_RATES = "quality_frame_rates"
        private const val KEY_QUALITY_BITRATES = "quality_bitrates"
        private const val KEY_QUALITY_CODECS = "quality_codecs"
        private const val KEY_QUALITY_URLS = "quality_urls"

        fun newStreamInstance(id: String?, channelId: String?, channelLogin: String?, channelName: String?, channelImage: String?, gameId: String?, gameSlug: String?, gameName: String?, title: String?, thumbnail: String?, createdAt: String?, qualityNames: Array<String>? = null, qualityResolutions: Array<String>? = null, qualityFrameRates: Array<String>? = null, qualityBitrates: Array<String>? = null, qualityCodecs: Array<String>? = null, qualityUrls: Array<String>? = null): DownloadDialog {
            return DownloadDialog().apply {
                arguments = Bundle().apply {
                    putString(KEY_TYPE, STREAM)
                    putString(KEY_STREAM_ID, id)
                    putString(KEY_CHANNEL_ID, channelId)
                    putString(KEY_CHANNEL_LOGIN, channelLogin)
                    putString(KEY_CHANNEL_NAME, channelName)
                    putString(KEY_CHANNEL_IMAGE, channelImage)
                    putString(KEY_GAME_ID, gameId)
                    putString(KEY_GAME_SLUG, gameSlug)
                    putString(KEY_GAME_NAME, gameName)
                    putString(KEY_TITLE, title)
                    putString(KEY_THUMBNAIL, thumbnail)
                    putString(KEY_CREATED_AT, createdAt)
                    putStringArray(KEY_QUALITY_NAMES, qualityNames)
                    putStringArray(KEY_QUALITY_RESOLUTIONS, qualityResolutions)
                    putStringArray(KEY_QUALITY_FRAME_RATES, qualityFrameRates)
                    putStringArray(KEY_QUALITY_BITRATES, qualityBitrates)
                    putStringArray(KEY_QUALITY_CODECS, qualityCodecs)
                    putStringArray(KEY_QUALITY_URLS, qualityUrls)
                }
            }
        }

        fun newVideoInstance(id: String?, channelId: String?, channelLogin: String?, channelName: String?, channelImage: String?, gameId: String?, gameSlug: String?, gameName: String?, title: String?, thumbnail: String?, createdAt: String?, durationSeconds: Int?, type: String?, animatedPreviewUrl: String?, totalDuration: Long? = null, currentPosition: Long? = null, qualityNames: Array<String>? = null, qualityResolutions: Array<String>? = null, qualityFrameRates: Array<String>? = null, qualityBitrates: Array<String>? = null, qualityCodecs: Array<String>? = null, qualityUrls: Array<String>? = null): DownloadDialog {
            return DownloadDialog().apply {
                arguments = Bundle().apply {
                    putString(KEY_TYPE, VIDEO)
                    putString(KEY_VIDEO_ID, id)
                    putString(KEY_CHANNEL_ID, channelId)
                    putString(KEY_CHANNEL_LOGIN, channelLogin)
                    putString(KEY_CHANNEL_NAME, channelName)
                    putString(KEY_CHANNEL_IMAGE, channelImage)
                    putString(KEY_GAME_ID, gameId)
                    putString(KEY_GAME_SLUG, gameSlug)
                    putString(KEY_GAME_NAME, gameName)
                    putString(KEY_TITLE, title)
                    putString(KEY_THUMBNAIL, thumbnail)
                    putString(KEY_CREATED_AT, createdAt)
                    putInt(KEY_DURATION_SECONDS, durationSeconds ?: -1)
                    putString(KEY_VIDEO_TYPE, type)
                    putString(KEY_VIDEO_ANIMATED_PREVIEW, animatedPreviewUrl)
                    putLong(KEY_VIDEO_TOTAL_DURATION, totalDuration ?: -1)
                    putLong(KEY_VIDEO_CURRENT_POSITION, currentPosition ?: -1)
                    putStringArray(KEY_QUALITY_NAMES, qualityNames)
                    putStringArray(KEY_QUALITY_RESOLUTIONS, qualityResolutions)
                    putStringArray(KEY_QUALITY_FRAME_RATES, qualityFrameRates)
                    putStringArray(KEY_QUALITY_BITRATES, qualityBitrates)
                    putStringArray(KEY_QUALITY_CODECS, qualityCodecs)
                    putStringArray(KEY_QUALITY_URLS, qualityUrls)
                }
            }
        }

        fun newClipInstance(id: String?, channelId: String?, channelLogin: String?, channelName: String?, channelImage: String?, gameId: String?, gameSlug: String?, gameName: String?, title: String?, thumbnail: String?, createdAt: String?, durationSeconds: Int?, videoId: String?, videoOffsetSeconds: Int?, videoCreatedAt: String?, qualityNames: Array<String>? = null, qualityResolutions: Array<String>? = null, qualityFrameRates: Array<String>? = null, qualityBitrates: Array<String>? = null, qualityCodecs: Array<String>? = null, qualityUrls: Array<String>? = null): DownloadDialog {
            return DownloadDialog().apply {
                arguments = Bundle().apply {
                    putString(KEY_TYPE, CLIP)
                    putString(KEY_CLIP_ID, id)
                    putString(KEY_CHANNEL_ID, channelId)
                    putString(KEY_CHANNEL_LOGIN, channelLogin)
                    putString(KEY_CHANNEL_NAME, channelName)
                    putString(KEY_CHANNEL_IMAGE, channelImage)
                    putString(KEY_GAME_ID, gameId)
                    putString(KEY_GAME_SLUG, gameSlug)
                    putString(KEY_GAME_NAME, gameName)
                    putString(KEY_TITLE, title)
                    putString(KEY_THUMBNAIL, thumbnail)
                    putString(KEY_CREATED_AT, createdAt)
                    putInt(KEY_DURATION_SECONDS, durationSeconds ?: -1)
                    putString(KEY_VIDEO_ID, videoId)
                    putInt(KEY_VIDEO_OFFSET_SECONDS, videoOffsetSeconds ?: -1)
                    putString(KEY_VIDEO_CREATED_AT, videoCreatedAt)
                    putStringArray(KEY_QUALITY_NAMES, qualityNames)
                    putStringArray(KEY_QUALITY_RESOLUTIONS, qualityResolutions)
                    putStringArray(KEY_QUALITY_FRAME_RATES, qualityFrameRates)
                    putStringArray(KEY_QUALITY_BITRATES, qualityBitrates)
                    putStringArray(KEY_QUALITY_CODECS, qualityCodecs)
                    putStringArray(KEY_QUALITY_URLS, qualityUrls)
                }
            }
        }
    }

    private val viewModel: DownloadViewModel by viewModels { DownloadViewModelFactory }
    private var directoryResultLauncher: ActivityResultLauncher<Intent>? = null
    private val form = mutableStateOf(DownloadForm())
    private val page = mutableStateOf(Page.Form)
    private val rangeError = mutableStateOf<String?>(null)
    private var formRestored = false

    private enum class Page { Form, Permission }

    private val type get() = requireArguments().getString(KEY_TYPE)
    private val args get() = requireArguments()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        directoryResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    when {
                        it.authority?.startsWith("com.android.providers") == true -> Toast.makeText(requireActivity(), R.string.invalid_directory, Toast.LENGTH_LONG).show()
                        else -> {
                            requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            form.value = form.value.copy(sharedPath = it.toString())
                        }
                    }
                }
            }
        }
        savedInstanceState?.let { state ->
            form.value = DownloadForm(
                quality = state.getString(STATE_QUALITY),
                location = state.getInt(STATE_LOCATION),
                sharedPath = state.getString(STATE_SHARED_PATH),
                storageIndex = state.getInt(STATE_STORAGE),
                downloadChat = state.getBoolean(STATE_CHAT),
                downloadChatEmotes = state.getBoolean(STATE_CHAT_EMOTES),
                fromText = state.getString(STATE_FROM).orEmpty(),
                toText = state.getString(STATE_TO).orEmpty(),
            )
            formRestored = true
        }
        if (!formRestored) {
            val prefs = requireContext().prefs()
            form.value = DownloadForm(
                location = prefs.getInt(C.DOWNLOAD_LOCATION, 0),
                sharedPath = prefs.getString(C.DOWNLOAD_SHARED_PATH, null),
                storageIndex = prefs.getInt(C.DOWNLOAD_STORAGE, 0),
                downloadChat = prefs.getBoolean(C.DOWNLOAD_CHAT, false),
                downloadChatEmotes = prefs.getBoolean(C.DOWNLOAD_CHAT_EMOTES, false),
            )
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.integrity.collect {
                    (requireActivity() as? MainActivity)?.getNewIntegrityToken(it, childFragmentManager)
                }
            }
        }
        if (type == VIDEO) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.dismiss.collectLatest {
                        if (it) {
                            Toast.makeText(requireActivity(), R.string.video_subscribers_only, Toast.LENGTH_LONG).show()
                            dismissAllowingStateLoss()
                        }
                    }
                }
            }
            viewModel.loadStoryboard(args.getString(KEY_VIDEO_ANIMATED_PREVIEW))
        }
        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val value = form.value
        outState.putString(STATE_QUALITY, value.quality)
        outState.putInt(STATE_LOCATION, value.location)
        outState.putString(STATE_SHARED_PATH, value.sharedPath)
        outState.putInt(STATE_STORAGE, value.storageIndex)
        outState.putBoolean(STATE_CHAT, value.downloadChat)
        outState.putBoolean(STATE_CHAT_EMOTES, value.downloadChatEmotes)
        outState.putString(STATE_FROM, value.fromText)
        outState.putString(STATE_TO, value.toText)
    }

    /** The qualities the caller passed (the player), as v0.1 read them from the arguments. */
    private fun passedQualities(): List<VideoQuality>? =
        args.getStringArray(KEY_QUALITY_NAMES)?.let { names ->
            args.getStringArray(KEY_QUALITY_RESOLUTIONS)?.let { resolutions ->
                args.getStringArray(KEY_QUALITY_FRAME_RATES)?.let { frameRates ->
                    args.getStringArray(KEY_QUALITY_BITRATES)?.let { bitrates ->
                        args.getStringArray(KEY_QUALITY_CODECS)?.let { codecs ->
                            args.getStringArray(KEY_QUALITY_URLS)?.let { urls ->
                                names.mapIndexed { index, name ->
                                    VideoQuality(name, resolutions.getOrNull(index).takeIf { it != "null" }?.toIntOrNull(), frameRates.getOrNull(index).takeIf { it != "null" }?.toFloatOrNull(), bitrates.getOrNull(index).takeIf { it != "null" }?.toIntOrNull(), codecs.getOrNull(index).takeIf { it != "null" }, urls.getOrNull(index))
                                }
                            }
                        }
                    }
                }
            }
        }

    /** v0.1's setStream / setVideo / setClip calls, unchanged. */
    private fun load() {
        val context = requireContext()
        val prefs = context.prefs()
        when (type) {
            STREAM -> viewModel.setStream(
                networkLibrary = prefs.getString(C.NETWORK_LIBRARY, C.OKHTTP),
                gqlHeaders = TwitchApiHelper.getGQLHeaders(context, prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_STREAM, true)),
                channelLogin = args.getString(KEY_CHANNEL_LOGIN),
                qualities = passedQualities(),
                platform = prefs.getString(C.TOKEN_PLATFORM, "web"),
                playerType = prefs.getString(C.TOKEN_PLAYER_TYPE, "site"),
                supportedCodecs = prefs.getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false),
            )
            VIDEO -> viewModel.setVideo(
                networkLibrary = prefs.getString(C.NETWORK_LIBRARY, C.OKHTTP),
                gqlHeaders = TwitchApiHelper.getGQLHeaders(context, prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)),
                videoId = args.getString(KEY_VIDEO_ID),
                animatedPreviewUrl = args.getString(KEY_VIDEO_ANIMATED_PREVIEW),
                videoType = args.getString(KEY_VIDEO_TYPE),
                qualities = passedQualities(),
                supportedCodecs = prefs.getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false),
            )
            CLIP -> viewModel.setClip(
                networkLibrary = prefs.getString(C.NETWORK_LIBRARY, C.OKHTTP),
                gqlHeaders = TwitchApiHelper.getGQLHeaders(context),
                clipId = args.getString(KEY_CLIP_ID),
                qualities = passedQualities(),
                supportedCodecs = prefs.getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false),
            )
        }
    }

    private val totalDuration: Long
        get() = args.getLong(KEY_VIDEO_TOTAL_DURATION, -1).takeIf { it != -1L }
            ?: args.getInt(KEY_DURATION_SECONDS, -1).takeIf { it != -1 }?.times(1000L)
            ?: 0

    private val currentPosition: Long get() = args.getLong(KEY_VIDEO_CURRENT_POSITION).coerceAtLeast(0)

    /** The app's storage places: internal first, then removable cards (v0.1's list). */
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

    @Composable
    override fun ColumnScope.SheetContent() {
        val qualities by viewModel.qualities.collectAsState()
        val storyboard by viewModel.storyboard.collectAsState()
        val labels = remember(qualities) {
            qualities?.takeIf { it.isNotEmpty() }?.let { DownloadSheetLogic.qualityLabels(it, getString(R.string.source), getString(R.string.audio_only)) }
        }
        val storages = remember { storages() }
        val mounted = remember { Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED }
        val kind = when (type) {
            STREAM -> DownloadKind.Stream
            CLIP -> DownloadKind.Clip
            else -> DownloadKind.Video
        }
        val item = remember {
            DownloadItem(
                kind = kind,
                title = args.getString(KEY_TITLE),
                subtitle = args.getString(KEY_CHANNEL_NAME) ?: args.getString(KEY_CHANNEL_LOGIN),
                thumbnail = args.getString(KEY_THUMBNAIL),
            )
        }
        when (page.value) {
            Page.Form -> DownloadSheetContent(
                item = item,
                qualities = labels?.map { it.first },
                form = form.value,
                onForm = { form.value = it; rangeError.value = null; viewModel.selectedQuality = it.quality },
                storages = storages,
                storageMounted = mounted,
                onPickFolder = ::pickFolder,
                onDownload = { labels?.let { download(it, storages) } },
                onCancel = ::close,
                totalMillis = totalDuration,
                defaultFromMillis = currentPosition,
                rangeError = rangeError.value,
                storyboard = storyboard,
                folderLabel = form.value.sharedPath?.let { Uri.decode(it.substringAfter("/tree/")) },
            )
            Page.Permission -> NotificationPermissionContent(
                onOk = {
                    ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                    close()
                },
                onCancel = ::close,
            )
        }
    }

    /** v0.1 saved the choices before opening the folder picker, so they come back with the dialog. */
    private fun pickFolder() {
        saveChoices(form.value, storages())
        directoryResultLauncher?.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, form.value.sharedPath)
        })
    }

    private fun saveChoices(value: DownloadForm, storages: List<StorageOption>) {
        requireContext().prefs().edit {
            putInt(C.DOWNLOAD_LOCATION, value.location)
            when (value.location) {
                0 -> putString(C.DOWNLOAD_SHARED_PATH, value.sharedPath)
                1 -> putInt(C.DOWNLOAD_STORAGE, if (storages.size > 1) value.storageIndex else 0)
            }
            putBoolean(C.DOWNLOAD_CHAT, value.downloadChat)
            putBoolean(C.DOWNLOAD_CHAT_EMOTES, value.downloadChatEmotes)
        }
    }

    /** v0.1's download button: the same checks, the same MainActivity calls, the same settings written. */
    private fun download(labels: List<Pair<String, VideoQuality>>, storages: List<StorageOption>) {
        val value = form.value
        val quality = labels.find { it.first == (value.quality ?: labels.first().first) }?.second
        val path = when (value.location) {
            0 -> value.sharedPath
            1 -> storages.getOrNull(if (storages.size > 1) value.storageIndex else 0)?.path
            else -> null
        }
        if (quality?.name != null && quality.url != null && !path.isNullOrBlank()) {
            val activity = requireActivity() as? MainActivity
            val prefs = requireContext().prefs()
            val downloadChat = value.downloadChat
            val downloadChatEmotes = value.downloadChatEmotes && downloadChat
            when (type) {
                STREAM -> activity?.downloadStream(
                    filesDir = requireContext().filesDir.path,
                    id = args.getString(KEY_STREAM_ID),
                    title = args.getString(KEY_TITLE),
                    createdAt = args.getString(KEY_CREATED_AT),
                    channelId = args.getString(KEY_CHANNEL_ID),
                    channelLogin = args.getString(KEY_CHANNEL_LOGIN),
                    channelName = args.getString(KEY_CHANNEL_NAME),
                    channelImage = args.getString(KEY_CHANNEL_IMAGE),
                    thumbnail = args.getString(KEY_THUMBNAIL),
                    gameId = args.getString(KEY_GAME_ID),
                    gameSlug = args.getString(KEY_GAME_SLUG),
                    gameName = args.getString(KEY_GAME_NAME),
                    downloadPath = path,
                    quality = quality.name,
                    downloadChat = downloadChat,
                    downloadChatEmotes = downloadChatEmotes,
                    wifiOnly = prefs.getBoolean(C.DOWNLOAD_WIFI_ONLY, false)
                )
                VIDEO -> {
                    val range = DownloadSheetLogic.checkRange(value.fromText, value.toText, currentPosition, totalDuration)
                    when (range.error) {
                        DownloadSheetLogic.RangeError.FromInvalid, DownloadSheetLogic.RangeError.ToInvalid -> {
                            rangeError.value = getString(R.string.invalid_time); return
                        }
                        DownloadSheetLogic.RangeError.ToTooLong -> {
                            rangeError.value = getString(R.string.to_is_longer); return
                        }
                        DownloadSheetLogic.RangeError.FromNotBeforeTo -> {
                            rangeError.value = getString(R.string.from_is_greater); return
                        }
                        null -> activity?.downloadVideo(
                            filesDir = requireContext().filesDir.path,
                            id = args.getString(KEY_VIDEO_ID),
                            title = args.getString(KEY_TITLE),
                            createdAt = args.getString(KEY_CREATED_AT),
                            type = args.getString(KEY_VIDEO_TYPE),
                            channelId = args.getString(KEY_CHANNEL_ID),
                            channelLogin = args.getString(KEY_CHANNEL_LOGIN),
                            channelName = args.getString(KEY_CHANNEL_NAME),
                            channelImage = args.getString(KEY_CHANNEL_IMAGE),
                            thumbnail = args.getString(KEY_THUMBNAIL),
                            gameId = args.getString(KEY_GAME_ID),
                            gameSlug = args.getString(KEY_GAME_SLUG),
                            gameName = args.getString(KEY_GAME_NAME),
                            url = quality.url,
                            downloadPath = path,
                            quality = quality.name,
                            from = range.from,
                            to = range.to,
                            downloadChat = downloadChat,
                            downloadChatEmotes = downloadChatEmotes,
                            playlistToFile = prefs.getBoolean(C.DOWNLOAD_PLAYLIST_TO_FILE, false),
                            wifiOnly = prefs.getBoolean(C.DOWNLOAD_WIFI_ONLY, false)
                        )
                    }
                }
                CLIP -> activity?.downloadClip(
                    filesDir = requireContext().filesDir.path,
                    clipId = args.getString(KEY_CLIP_ID),
                    title = args.getString(KEY_TITLE),
                    createdAt = args.getString(KEY_CREATED_AT),
                    durationSeconds = args.getInt(KEY_DURATION_SECONDS),
                    videoId = args.getString(KEY_VIDEO_ID),
                    videoOffsetSeconds = args.getInt(KEY_VIDEO_OFFSET_SECONDS),
                    videoCreatedAt = args.getString(KEY_VIDEO_CREATED_AT),
                    channelId = args.getString(KEY_CHANNEL_ID),
                    channelLogin = args.getString(KEY_CHANNEL_LOGIN),
                    channelName = args.getString(KEY_CHANNEL_NAME),
                    channelImage = args.getString(KEY_CHANNEL_IMAGE),
                    thumbnail = args.getString(KEY_THUMBNAIL),
                    gameId = args.getString(KEY_GAME_ID),
                    gameSlug = args.getString(KEY_GAME_SLUG),
                    gameName = args.getString(KEY_GAME_NAME),
                    url = quality.url,
                    downloadPath = path,
                    quality = quality.name,
                    downloadChat = downloadChat,
                    downloadChatEmotes = downloadChatEmotes,
                    wifiOnly = prefs.getBoolean(C.DOWNLOAD_WIFI_ONLY, false)
                )
            }
            saveChoices(value, storages)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                !prefs.getBoolean(C.DOWNLOAD_NOTIFICATION_REQUESTED, false)
            ) {
                prefs.edit { putBoolean(C.DOWNLOAD_NOTIFICATION_REQUESTED, true) }
                page.value = Page.Permission
                return
            }
        }
        close()
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        when (callback) {
            "stream", "video", "clip" -> load()
        }
    }
}
