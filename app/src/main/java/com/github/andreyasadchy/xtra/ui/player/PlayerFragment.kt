package com.github.andreyasadchy.xtra.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.RoundedCorner
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.trackPipAnimationHintView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.SubtitleView
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.player.ads.AdBlockPreset
import com.github.andreyasadchy.xtra.player.ads.AdLog
import com.github.andreyasadchy.xtra.player.ads.AdStats
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.ui.chat.ViewerListSheet
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import android.app.admin.DeviceAdminReceiver
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.following.ConfirmSheet
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.main.shell.ShellHost
import com.github.andreyasadchy.xtra.ui.player.PlayerViewModel.Companion.PlayerViewModelFactory
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * The player page (plan 7.4, U6). It stays the Fragment MainActivity hosts in its player container, with v0.1's
 * public methods (minimize, maximize, close, audio only, the chat's callbacks), so the activity, the chat and the
 * notifications work as before. Inside, the views are three: the video (a plain SurfaceView, see [PlayerConnection]),
 * the Compose page over it ([PlayerScreen]) and the old chat's container (U7 rewrites the chat).
 *
 * The service binding is [ExoPlayerFragment]'s; this class only talks to the service through [PlaybackBackend].
 */
@OptIn(UnstableApi::class)
abstract class PlayerFragment : BaseNetworkFragment(), IntegrityDialog.Listener, PlayerConnection.Host, PlayerActions, PlayerSheetSource {

    protected val viewModel: PlayerViewModel by viewModels { PlayerViewModelFactory }
    private val viewerListViewModel: PlayerViewerListViewModel by viewModels { PlayerViewerListViewModel.PlayerViewerListViewModelFactory }
    protected var chatFragment: ChatFragment? = null
    protected val state = PlayerUiState()
    protected val connection = PlayerConnection(state, this)
    protected val backend: PlaybackBackend? get() = connection.backend
    protected var started
        get() = connection.started
        set(value) { connection.started = value }

    private var videoView: FrameLayout? = null
    private var aspectFrame: AspectRatioFrameLayout? = null
    private var surfaceView: SurfaceView? = null
    private var subtitleView: SubtitleView? = null
    private var chatContainer: FragmentContainerView? = null
    private var lastVideo: VideoPlacement? = null
    private var lastChat: ChatPlacement? = null
    private var options = PlayerOptions()
    private var isPortrait = true
    private var confirmUnfollow by mutableStateOf(false)
    private var volumeLevel = -1f
    private var lastVolumeChange = 0L
    private var audioOnlyApplied = false

    protected var isMaximized = true
        set(value) {
            val changed = field != value
            field = value
            state.mode = if (value) PlayerMode.Maximized else PlayerMode.Minimized
            // the shell keeps the navigation still under the mini player and hides it from screen readers when maximized
            if (changed) (activity as? MainActivity)?.onPlayerModeChanged()
        }

    fun isPlayerMaximized() = isMaximized

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (state.sheet != null) state.sheet = null else minimize()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (arguments?.getBoolean(KEY_OFFLINE) == true) {
            enableNetworkCheck = false
        }
        super.onCreate(savedInstanceState)
        audioOnlyApplied = savedInstanceState?.getBoolean(KEY_AUDIO_ONLY_APPLIED) == true
        isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
        WindowCompat.getInsetsController(requireActivity().window, requireActivity().window.decorView).systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_AUDIO_ONLY_APPLIED, audioOnlyApplied)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val prefs = context.prefs()
        options = PlayerOptions.from(prefs, isLoggedIn())
        state.chatOpen = prefs.getBoolean(C.KEY_CHAT_OPENED, true) && !prefs.getBoolean(C.CHAT_DISABLE, false)
        state.chatOverlay = prefs.getBoolean(C.PLAYER_LANDSCAPE_CHAT_OVERLAY, false)
        state.chatWidthPx = prefs.getInt(C.LANDSCAPE_CHAT_WIDTH, 0)
        state.resizeMode = prefs.getInt(C.ASPECT_RATIO_LANDSCAPE, AspectRatioFrameLayout.RESIZE_MODE_FIT)
        state.compressorOn = prefs.getBoolean(C.PLAYER_AUDIO_COMPRESSOR, false)
        state.chatBarVisible = prefs.getBoolean(C.KEY_CHAT_BAR_VISIBLE, true)

        val root = FrameLayout(context)
        val surface = SurfaceView(context)
        val aspect = AspectRatioFrameLayout(context).apply {
            setAspectRatio(16f / 9f)
            addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val subtitles = SubtitleView(context).apply {
            setUserDefaultStyle()
            setUserDefaultTextSize()
        }
        val video = FrameLayout(context).apply {
            id = R.id.playerVideo
            setBackgroundColor(Color.BLACK)
            pivotX = 0f
            pivotY = 0f
            addView(aspect, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
            addView(subtitles, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        root.addView(video, FrameLayout.LayoutParams(0, 0))
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { LagoonTheme { PageContent() } }
        }
        root.addView(compose, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val chat = FragmentContainerView(context).apply {
            id = R.id.chatFragmentContainer
            setBackgroundColor(PAGE_COLOR)
            isVisible = false
        }
        root.addView(chat, FrameLayout.LayoutParams(0, 0))
        // v0.1 consumed the insets at the player's root: the chat and the video never pad themselves
        ViewCompat.setOnApplyWindowInsetsListener(chat) { _, _ -> WindowInsetsCompat.CONSUMED }
        // the video passes them on untouched: before Android 11 a consumed result stops the dispatch to the views after
        // it, and the Compose page (the next view) reads the status bar, navigation and keyboard from them
        ViewCompat.setOnApplyWindowInsetsListener(video) { _, insets -> insets }
        videoView = video
        aspectFrame = aspect
        surfaceView = surface
        subtitleView = subtitles
        chatContainer = chat
        return root
    }

    @kotlin.OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun PageContent() {
        val shell = (activity as? ShellHost)?.shell
        val ime = WindowInsets.isImeVisible
        LaunchedEffect(ime) { onKeyboard(ime) }
        LaunchedEffect(state.isPlaying, state.controlsVisible, state.mode) {
            // the time line moves while the controls show; the mini player only needs the play state
            while (state.isPlaying && state.controlsVisible) {
                connection.updateProgress()
                delay(500)
            }
        }
        LaunchedEffect(state.sheet) {
            if (state.sheet == PlayerSheet.Settings) {
                state.chatConnected = chatFragment?.isActive() != false
                state.chatBarVisible = requireContext().prefs().getBoolean(C.KEY_CHAT_BAR_VISIBLE, true)
                if (state.kind == PlayerKind.Video) backend?.videoId?.let { viewModel.checkBookmark(it) }
            }
        }
        val layout = PlayerGeometryRules.layoutOf(
            resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels,
            resources.configuration.screenWidthDp, resources.configuration.smallestScreenWidthDp, state.inPictureInPicture, state.tabletFullscreen,
        )
        SideEffect {
            aspectFrame?.resizeMode = if (layout == PlayerLayout.Landscape) state.resizeMode else AspectRatioFrameLayout.RESIZE_MODE_FIT
            aspectFrame?.setAspectRatio(state.videoAspect)
            surfaceView?.isVisible = !state.videoDisabled
        }
        PlayerScreen(
            state = state,
            options = options,
            actions = this,
            sheets = this,
            bottomBarPx = shell?.bottomBarHeightPx ?: 0,
            onVideoPlacement = ::placeVideo,
            onChatPlacement = ::placeChat,
            sideInsets = sideInsets(),
        )
        if (state.viewerListOpen) {
            val list by viewerListViewModel.viewerList.collectAsState()
            ViewerListSheet(
                list = list,
                compactCount = requireContext().prefs().getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true),
                onDismiss = { state.viewerListOpen = false },
                onOpen = { login ->
                    state.viewerListOpen = false
                    findNavController().navigate(ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(channelLogin = login))
                    minimize()
                },
            )
        }
        if (confirmUnfollow) {
            ConfirmSheet(
                title = stringResource(R.string.unfollow_channel, channelDisplayName() ?: ""),
                message = null,
                confirm = stringResource(R.string.unfollow),
                onConfirm = { unfollow() },
                onDismiss = { confirmUnfollow = false },
            )
        }
    }

    /** Landscape padding for display cutouts and, when asked, the screen's rounded corners (v0.1). */
    private fun sideInsets(): Pair<Int, Int>? {
        val prefs = requireContext().prefs()
        val ignoreCutouts = prefs.getBoolean(C.UI_DRAW_BEHIND_CUTOUTS, false)
        val cornerPadding = prefs.getBoolean(C.PLAYER_ROUNDED_CORNER_PADDING, false)
        val insets = view?.rootWindowInsets ?: return null
        val cutout = WindowInsetsCompat.toWindowInsetsCompat(insets).getInsets(WindowInsetsCompat.Type.displayCutout())
        var left = if (ignoreCutouts) 0 else cutout.left
        var right = if (ignoreCutouts) 0 else cutout.right
        if (cornerPadding && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val leftRadius = max(insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0, insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0)
            val rightRadius = max(insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0, insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0)
            left = max(left, leftRadius)
            right = max(right, rightRadius)
        }
        return left to right
    }

    private fun placeVideo(placement: VideoPlacement) {
        val video = videoView ?: return
        if (lastVideo?.let { it.left != placement.left || it.top != placement.top || it.width != placement.width || it.height != placement.height } != false) {
            video.layoutParams = FrameLayout.LayoutParams(placement.width, placement.height).apply {
                leftMargin = placement.left
                topMargin = placement.top
            }
        }
        video.scaleX = placement.scale
        video.scaleY = placement.scale
        video.translationX = placement.translationX
        video.translationY = placement.translationY
        video.isVisible = placement.visible
        lastVideo = placement
    }

    private fun placeChat(placement: ChatPlacement) {
        val chat = chatContainer ?: return
        if (placement != lastChat) {
            if (placement.visible) {
                chat.layoutParams = FrameLayout.LayoutParams(placement.width, placement.height).apply {
                    leftMargin = placement.left
                    topMargin = placement.top
                }
                chat.setBackgroundColor(if (placement.overlay) OVERLAY_COLOR else PAGE_COLOR)
            } else if (chat.isVisible) {
                (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(chat.windowToken, 0)
                chat.clearFocus()
            }
            chat.isVisible = placement.visible
            // v0.1 showChat jumped to the newest message here; the Compose chat list stays pinned to it while it rests at the bottom
            lastChat = placement
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.integrity.collect { (requireActivity() as? MainActivity)?.getNewIntegrityToken(it, childFragmentManager) }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    videoView?.let { requireActivity().trackPipAnimationHintView(it) }
                }
            }
        }
        if (requireContext().prefs().getBoolean(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false)) {
            view.keepScreenOn = true
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isFollowing.collectLatest { state.following = it }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isBookmarked.collectLatest { if (it != null) state.bookmarked = it }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.gamesList.collectLatest { state.chapters = it.orEmpty() }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.follow.collectLatest { pair ->
                    if (pair != null) {
                        val (following, errorMessage) = pair
                        val name = channelDisplayName()
                        Toast.makeText(
                            requireContext(),
                            when {
                                !errorMessage.isNullOrBlank() -> errorMessage
                                following -> getString(R.string.now_following, name)
                                else -> getString(R.string.unfollowed, name)
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                        viewModel.follow.value = null
                    }
                }
            }
        }
        // a page rebuilt while the activity's sleep timer runs shows it
        ((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0L).takeIf { it > 0L }?.let { state.sleepTimerEnd = System.currentTimeMillis() + it }
        initLayout()
        changePlayerMode()
    }

    // --- the contract's host side (PlayerConnection.Host) ---

    override val surface: SurfaceView get() = surfaceView!!
    override val isViewAlive: Boolean get() = view != null
    override val canStart: Boolean get() = isInitialized || !enableNetworkCheck

    /** v0.1 start(): stream info, follow state, chapters, the page's texts and the chat. */
    override fun onFirstStart() = startPage()

    override fun onLaterStart() {
        chatFragment?.startReplayChatLoad()
    }

    override fun onLoaded() = applyAudioOnlyStart()

    override fun toast(resId: Int, duration: Int) {
        Toast.makeText(requireContext(), resId, duration).show()
    }

    override fun toast(text: CharSequence, duration: Int) {
        Toast.makeText(requireContext(), text, duration).show()
    }

    override fun onSeek(positionMs: Long) {
        if (chatFragment?.context != null) chatFragment?.updatePosition(positionMs)
    }

    override fun onSpeedChanged(speed: Float) {
        if (chatFragment?.context != null) chatFragment?.updateSpeed(speed)
    }

    override fun onTracksLoaded() {
        chatFragment?.startReplayChatLoad()
    }

    override fun onCues(cues: List<Cue>) {
        subtitleView?.setCues(cues)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!requireContext().prefs().getBoolean(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false) && canEnterPictureInPicture()) {
            view?.keepScreenOn = isPlaying
        }
    }

    override fun onPlayButtonChanged(showPlayButton: Boolean, playbackState: Int) {
        setPipActions(!showPlayButton)
        // v0.1: an ended video or clip shows its controls
        if (!state.inPictureInPicture && state.kind != PlayerKind.Stream && playbackState == Player.STATE_ENDED) {
            state.controlsVisible = true
        }
    }

    override fun qualityLabel(quality: VideoQuality?): String? =
        quality?.let { q -> qualities().find { it.second.name == q.name && it.second.url == q.url }?.first }

    /** The service started: same as v0.1's start(). */
    protected fun startPage() {
        val backend = backend ?: return
        val context = requireContext()
        val prefs = context.prefs()
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                backend.integrity.collect { (requireActivity() as? MainActivity)?.getNewIntegrityToken(it, childFragmentManager) }
            }
        }
        state.kind = PlayerKind.of(backend.type)
        state.channelName = backend.channelName
        state.channelLogin = backend.channelLogin
        state.channelImage = backend.channelImage
        state.hasFullVideo = state.kind == PlayerKind.Clip && !backend.videoId.isNullOrBlank()
        updateStreamInfo(backend.title, backend.gameId, backend.gameSlug, backend.gameName)
        if (backend.type != BasePlaybackService.OFFLINE_VIDEO) {
            viewModel.isFollowingChannel(
                context.tokenPrefs().getString(C.USER_ID, null),
                backend.channelId,
                backend.channelLogin,
                prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                prefs.getString(C.NETWORK_LIBRARY, C.OKHTTP),
                TwitchApiHelper.getGQLHeaders(context, true),
                TwitchApiHelper.getHelixHeaders(context),
            )
            when (backend.type) {
                BasePlaybackService.STREAM -> backend.channelLogin?.let {
                    viewModel.loadStreamInfo(
                        channelId = backend.channelId,
                        channelLogin = it,
                        viewerCount = backend.viewerCount,
                        loop = prefs.getBoolean(C.CHAT_DISABLE, false) || !prefs.getBoolean(C.CHAT_PUB_SUB_ENABLED, true),
                        networkLibrary = prefs.getString(C.NETWORK_LIBRARY, C.OKHTTP),
                        helixHeaders = TwitchApiHelper.getHelixHeaders(context),
                        gqlHeaders = TwitchApiHelper.getGQLHeaders(context),
                        enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false),
                    )
                }
                BasePlaybackService.VIDEO -> loadChapters()
            }
        }
        if (backend.type == BasePlaybackService.STREAM) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.stream.collectLatest { stream ->
                        if (stream != null) {
                            stream.id?.let {
                                this@PlayerFragment.backend?.streamId = it
                                chatFragment?.updateStreamId(it)
                            }
                            val chatUpdates = !prefs.getBoolean(C.CHAT_DISABLE, false) && prefs.getBoolean(C.CHAT_PUB_SUB_ENABLED, true)
                            if (!chatUpdates || state.viewers == null) updateViewerCount(stream.viewerCount)
                            if (!chatUpdates || state.title == null || state.gameName == null) {
                                updateStreamInfo(stream.title, stream.gameId, stream.gameSlug, stream.gameName)
                            }
                            if (state.startedAt == null) {
                                stream.createdAt?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } }?.let { state.startedAt = it }
                            }
                            stream.tags?.let { state.tags = it }
                            stream.channelImage?.let { if (state.channelImage == null) state.channelImage = it }
                        }
                    }
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.unlistedVideoQualities.collectLatest { qualities ->
                        if (qualities != null) {
                            if (qualities.isNotBlank()) {
                                (requireActivity() as MainActivity).startVideo(Video(), 0, qualities = qualities)
                            } else {
                                Toast.makeText(requireContext(), R.string.video_not_found, Toast.LENGTH_SHORT).show()
                            }
                            viewModel.unlistedVideoQualities.value = null
                        }
                    }
                }
            }
            backend.createdAt?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } }?.let { state.startedAt = it }
            updateViewerCount(backend.viewerCount)
        } else {
            state.uploadedAt = (backend.videoCreatedAt ?: backend.createdAt)?.let { Instant.parseOrNull(it)?.toEpochMilliseconds() }
        }
        attachChat(backend)
    }

    private fun loadChapters() {
        val backend = backend ?: return
        val videoId = backend.videoId
        if (!videoId.isNullOrBlank()) {
            viewModel.loadGamesList(
                videoId,
                requireContext().prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                TwitchApiHelper.getGQLHeaders(requireContext()),
                requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            )
        }
    }

    private fun attachChat(backend: PlaybackBackend) {
        val current = childFragmentManager.findFragmentById(R.id.chatFragmentContainer) as? ChatFragment
        if (current != null) {
            chatFragment = current
            return
        }
        val fragment = when (backend.type) {
            BasePlaybackService.STREAM -> ChatFragment.newInstance(backend.channelId, backend.channelLogin, backend.channelName, backend.streamId)
            BasePlaybackService.VIDEO -> ChatFragment.newInstance(backend.channelId, backend.channelLogin, backend.videoId, backend.createdAt, 0)
            BasePlaybackService.CLIP -> ChatFragment.newInstance(
                backend.channelId, backend.channelLogin, backend.videoId, backend.videoCreatedAt, backend.videoOffsetSeconds.takeIf { it != -1 },
            )
            BasePlaybackService.OFFLINE_VIDEO -> ChatFragment.newLocalInstance(
                backend.channelId,
                backend.channelLogin,
                backend.videoCreatedAt ?: backend.createdAt?.takeIf { backend.clipId == null },
                backend.chatUrl,
            )
            else -> null
        }
        if (fragment != null) {
            childFragmentManager.beginTransaction().replace(R.id.chatFragmentContainer, fragment).commit()
        }
        chatFragment = fragment
    }

    private fun initLayout() {
        if (isPortrait) {
            showStatusBar()
        } else if (isMaximized && !isTabletLayout()) {
            hideStatusBar()
        } else {
            showStatusBar()
        }
    }

    private fun isTablet() = PlayerGeometryRules.isTablet(resources.configuration.screenWidthDp, resources.configuration.smallestScreenWidthDp)

    private fun isTabletLayout() = !isPortrait && isTablet() && !state.tabletFullscreen

    private fun onKeyboard(shown: Boolean) {
        if (state.keyboardShown == shown) return
        state.keyboardShown = shown
        if (!isPortrait) {
            if (shown) showStatusBar() else if (isMaximized && !isTabletLayout()) hideStatusBar()
        }
        if (!shown) chatContainer?.clearFocus()
    }

    // --- PlayerActions ---

    override fun playPause() {
        backend?.togglePause()
    }

    override fun rewind() {
        backend?.seekBack()
    }

    override fun forward() {
        backend?.seekForward()
    }

    override fun seekTo(positionMs: Long) {
        backend?.seekTo(positionMs)
    }

    /** v0.1 name, still called by the chapters and the chat. */
    fun seek(position: Long) = seekTo(position)

    override fun seekToChapter(positionMs: Long) = seekTo(positionMs)

    override fun maximize() {
        isMaximized = true
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
        if (state.kind == PlayerKind.Stream && chatFragment?.emoteMenuIsVisible() == true) {
            chatFragment?.toggleBackPressedCallback(true)
        }
        initLayout()
    }

    override fun minimize() {
        // can be called from a deep link before the restored player fragment has a view
        if (view == null) return
        isMaximized = false
        if (state.kind == PlayerKind.Stream && chatFragment?.emoteMenuIsVisible() == true) {
            chatFragment?.toggleBackPressedCallback(false)
        }
        backPressedCallback.remove()
        state.controlsVisible = false
        state.sheet = null
        resetBrightness()
        showStatusBar()
        if (!isPortrait) {
            val activity = requireActivity()
            activity.lifecycleScope.launch {
                delay(500.milliseconds)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    override fun dismiss() {
        close(deleteStates = true)
        (activity as? MainActivity)?.closePlayer()
    }

    open fun close(deleteStates: Boolean = true) {}

    open fun startAudioOnly() {}

    override fun toggleFullscreen() {
        val activity = requireActivity()
        when {
            isPortrait -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            isTablet() -> {
                state.tabletFullscreen = !state.tabletFullscreen
                initLayout()
            }
            else -> {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    override fun openChannel() {
        val backend = backend ?: return
        findNavController().navigate(
            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                channelId = backend.channelId,
                channelLogin = backend.channelLogin,
                channelName = backend.channelName,
            )
        )
        minimize()
    }

    override fun openGame() {
        val backend = backend ?: return
        val gameName = backend.gameName ?: return
        findNavController().navigate(
            if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(gameId = backend.gameId, gameSlug = backend.gameSlug, gameName = gameName)
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(gameId = backend.gameId, gameSlug = backend.gameSlug, gameName = gameName)
            }
        )
        minimize()
    }

    override fun setFollowing(follow: Boolean) {
        if (follow) {
            val backend = backend ?: return
            val prefs = requireContext().prefs()
            viewModel.saveFollowChannel(
                requireContext().tokenPrefs().getString(C.USER_ID, null),
                backend.channelId,
                backend.channelLogin,
                backend.channelName,
                prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                prefs.getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false),
                !prefs.getBoolean(C.UI_ACTIVATE_NOTIFICATIONS_WHEN_FOLLOWING, true),
                backend.createdAt,
                prefs.getString(C.NETWORK_LIBRARY, C.OKHTTP),
                TwitchApiHelper.getGQLHeaders(requireContext(), true),
                prefs.getBoolean(C.ENABLE_INTEGRITY, false),
            )
        } else {
            // v0.1 asked first
            confirmUnfollow = true
        }
    }

    private fun unfollow() {
        val backend = backend ?: return
        val prefs = requireContext().prefs()
        viewModel.deleteFollowChannel(
            requireContext().tokenPrefs().getString(C.USER_ID, null),
            backend.channelId,
            prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
            prefs.getString(C.NETWORK_LIBRARY, C.OKHTTP),
            TwitchApiHelper.getGQLHeaders(requireContext(), true),
            prefs.getBoolean(C.ENABLE_INTEGRITY, false),
        )
    }

    override fun openViewerList() {
        val login = backend?.channelLogin ?: return
        val prefs = requireContext().prefs()
        viewerListViewModel.loadViewerList(login, prefs.getString(C.NETWORK_LIBRARY, C.OKHTTP), TwitchApiHelper.getGQLHeaders(requireContext()), prefs.getBoolean(C.ENABLE_INTEGRITY, false))
        state.viewerListOpen = true
    }

    override fun watchFullVideo() {
        val backend = backend ?: return
        val videoId = backend.videoId ?: return
        val offset = backend.videoOffsetSeconds?.let { (it * 1000) + (getCurrentPosition() ?: 0) } ?: 0
        (requireActivity() as MainActivity).startVideo(
            Video(
                id = videoId,
                channelId = backend.channelId,
                channelLogin = backend.channelLogin,
                channelName = backend.channelName,
                channelImageURL = backend.channelImage,
                createdAt = backend.videoCreatedAt,
                animatedPreviewURL = backend.videoAnimatedPreviewURL,
            ),
            offset,
            true,
        )
    }

    override fun setChatOpen(open: Boolean) {
        if (open == state.chatOpen) return
        if (!open) {
            (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(chatContainer?.windowToken, 0)
            chatContainer?.clearFocus()
        }
        state.chatOpen = open
        requireContext().prefs().edit { putBoolean(C.KEY_CHAT_OPENED, open) }
    }

    override fun setChatOverlay(overlay: Boolean) {
        state.chatOverlay = overlay
        requireContext().prefs().edit { putBoolean(C.PLAYER_LANDSCAPE_CHAT_OVERLAY, overlay) }
    }

    override fun setChatWidth(widthPx: Int, done: Boolean) {
        val width = PlayerLogic.clampChatWidth(widthPx, resources.displayMetrics.widthPixels)
        state.chatWidthPx = width
        if (done) requireContext().prefs().edit { putInt(C.LANDSCAPE_CHAT_WIDTH, width) }
    }

    override fun adjustBrightness(delta: Float): Float {
        val window = requireActivity().window
        val attributes = window.attributes
        val current = attributes.screenBrightness.takeIf { it >= 0f }
            ?: (Settings.System.getInt(requireContext().contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
        val level = (current + delta).coerceIn(0.01f, 1f)
        window.attributes = attributes.apply { screenBrightness = level }
        return level
    }

    private fun resetBrightness() {
        val window = activity?.window ?: return
        if (window.attributes.screenBrightness >= 0f) {
            window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }

    override fun adjustVolume(delta: Float): Float {
        val audio = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        // a new swipe starts from the system level: the volume keys may have changed it in between
        val now = android.os.SystemClock.uptimeMillis()
        if (volumeLevel < 0f || now - lastVolumeChange > 1000) volumeLevel = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat()
        lastVolumeChange = now
        volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volumeLevel * maxVolume).roundToInt(), 0)
        return volumeLevel
    }

    override fun quick(button: QuickButton) {
        when (button) {
            QuickButton.Download -> showDownloadDialog()
            QuickButton.Follow -> setFollowing(state.following != true)
            QuickButton.SleepTimer -> menu(MenuEntry.SleepTimer)
            QuickButton.SeekLive -> backend?.seekToDefaultPosition()
            QuickButton.AudioOnly -> menu(MenuEntry.AudioOnly)
            QuickButton.Subtitles -> menu(MenuEntry.Subtitles)
            QuickButton.ChatBar -> toggleChatBar()
        }
    }

    override fun menu(entry: MenuEntry) {
        val backend = backend
        when (entry) {
            MenuEntry.Quality -> state.sheet = PlayerSheet.Quality
            MenuEntry.Speed -> state.sheet = PlayerSheet.Speed
            MenuEntry.SleepTimer -> {
                val running = ((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0) > 0L
                if (requireContext().prefs().getBoolean(C.SLEEP_TIMER_USE_TIME_PICKER, false) && !running) {
                    state.sheet = null
                    pickSleepTime()
                } else {
                    state.sheet = PlayerSheet.Sleep
                }
            }
            MenuEntry.AudioOnly -> {
                if (backend?.quality?.name == VideoQuality.AUDIO_ONLY_QUALITY) {
                    changeQuality(backend.previousQuality)
                } else {
                    changeQuality(backend?.qualities?.find { it.name == VideoQuality.AUDIO_ONLY_QUALITY })
                }
                changePlayerMode()
            }
            MenuEntry.Download -> showDownloadDialog()
            MenuEntry.AdBlock -> state.sheet = PlayerSheet.AdBlock
            MenuEntry.Subtitles -> {
                val enable = state.subtitles != SubtitleState.On
                backend?.toggleSubtitles(enable)
                requireContext().prefs().edit { putBoolean(C.PLAYER_SUBTITLES_ENABLED, enable) }
                connection.updateSubtitles()
            }
            MenuEntry.Compressor -> state.compressorOn = backend?.toggleDynamicsProcessing() == true
            MenuEntry.Volume -> state.sheet = PlayerSheet.Volume
            MenuEntry.Chapters -> state.sheet = PlayerSheet.Chapters
            MenuEntry.ViewerList -> openViewerList()
            MenuEntry.Bookmark -> saveBookmark()
            MenuEntry.Share -> share()
            MenuEntry.FindVideo -> findUnlistedVideo()
            MenuEntry.SeekLive -> backend?.seekToDefaultPosition()
            MenuEntry.Restart -> backend?.restartPlayer()
            MenuEntry.FullVideo -> watchFullVideo()
            MenuEntry.Aspect -> state.sheet = PlayerSheet.Aspect
            MenuEntry.ChatLayout -> setChatOverlay(!state.chatOverlay)
            MenuEntry.ChatToggle -> setChatOpen(!state.chatOpen)
            MenuEntry.ChatBar -> toggleChatBar()
            MenuEntry.ChatConnection -> if (chatFragment?.isActive() == true) chatFragment?.disconnect() else chatFragment?.reconnect()
            MenuEntry.ReloadEmotes -> chatFragment?.reloadEmotes()
            MenuEntry.MediaPlaylistTags -> showPlaylistTags(true)
            MenuEntry.MultivariantPlaylistTags -> showPlaylistTags(false)
        }
    }

    override fun selectQuality(quality: VideoQuality) {
        changeQuality(backend?.qualities?.find { it.name == quality.name && it.url == quality.url })
        changePlayerMode()
    }

    override fun selectSpeed(speed: Float) {
        backend?.setPlaybackSpeed(speed)
        requireContext().prefs().edit { putFloat(C.PLAYER_SPEED, speed) }
    }

    override fun setPlayerVolume(volume: Float, done: Boolean) {
        backend?.changeVolume(volume)
        if (done) requireContext().prefs().edit { putInt(C.PLAYER_VOLUME, (volume * 100).roundToInt()) }
    }

    override fun setSleepTimer(minutes: Int, lockScreen: Boolean) {
        if (minutes > 0) {
            requireContext().prefs().edit { putInt(C.SLEEP_TIMER_MINUTES, minutes) }
            onSleepTimerChanged(minutes * 60_000L, minutes / 60, minutes % 60, lockScreen)
        } else {
            onSleepTimerChanged(-1L, 0, 0, lockScreen)
        }
    }

    /** v0.1's clock picker for the sleep timer (sleep_timer_use_time_picker), kept as a second way in. */
    override fun pickSleepTime() {
        val savedValue = requireContext().prefs().getInt(C.SLEEP_TIMER_TIME, 15)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .setHour(savedValue / 60)
            .setMinute(savedValue % 60)
            .build()
        picker.addOnPositiveButtonClickListener {
            val minutes = TwitchApiHelper.getMinutesLeft(picker.hour, picker.minute)
            onSleepTimerChanged(minutes * 60_000L, minutes / 60, minutes % 60, requireContext().prefs().getBoolean(C.SLEEP_TIMER_LOCK, false))
            requireContext().prefs().edit { putInt(C.SLEEP_TIMER_TIME, picker.hour * 60 + picker.minute) }
        }
        picker.show(childFragmentManager, "closeOnPip")
    }

    override fun requestSleepLockPermission() {
        val admin = ComponentName(requireContext(), DeviceAdminReceiver::class.java)
        try {
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin))
        } catch (_: android.content.ActivityNotFoundException) {
            // no device admin screen on this device: the switch simply stays off, as in v0.1's settings
        }
    }

    override fun setResizeMode(mode: Int) {
        state.resizeMode = mode
        requireContext().prefs().edit { putInt(C.ASPECT_RATIO_LANDSCAPE, mode) }
    }

    override fun openAdBlockSettings() {
        startActivity(Intent(requireContext(), SettingsActivity::class.java))
    }

    // --- PlayerSheetSource ---

    override fun menuContext(layout: PlayerLayout): MenuContext {
        val prefs = requireContext().prefs()
        return MenuContext(
            landscape = layout == PlayerLayout.Landscape || layout == PlayerLayout.Tablet,
            phoneLandscape = layout == PlayerLayout.Landscape,
            loggedIn = isLoggedIn(),
            chatDisabled = prefs.getBoolean(C.CHAT_DISABLE, false),
            hasAudioOnly = backend?.qualities?.any { it.name == VideoQuality.AUDIO_ONLY_QUALITY } == true,
            hasChapters = state.chapters.isNotEmpty(),
            subtitles = state.subtitles,
            debugPlaylistTags = prefs.getBoolean(C.DEBUG_PLAYER_MENU_PLAYLIST_TAGS, false),
            hasFullVideo = state.hasFullVideo,
        )
    }

    override fun qualities(): List<Pair<String, VideoQuality>> = PlayerLogic.qualityLabels(
        backend?.qualities,
        getString(R.string.auto),
        getString(R.string.source),
        getString(R.string.audio_only),
        getString(R.string.chat_only),
    )

    override fun currentQuality() = backend?.quality

    override fun speeds() = PlayerLogic.speeds(requireContext().prefs().getString(C.PLAYER_SPEED_LIST, PlayerLogic.DEFAULT_SPEEDS))

    override fun playerVolume() = backend?.getCurrentVolume() ?: 1f

    override fun sleepMinutes() = requireContext().prefs().getInt(C.SLEEP_TIMER_MINUTES, 15)

    override fun sleepLock() = requireContext().prefs().getBoolean(C.SLEEP_TIMER_LOCK, false)

    override fun sleepLockAllowed(): Boolean {
        val devicePolicyManager = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return devicePolicyManager.isAdminActive(ComponentName(requireContext(), DeviceAdminReceiver::class.java))
    }

    override fun adBlockPreset(): String {
        val prefs = requireContext().prefs()
        return getString(
            when (AdBlockPreset.match { key, default -> prefs.getBoolean(key, default) }) {
                AdBlockPreset.AGGRESSIVE.key -> R.string.st_preset_aggressive
                AdBlockPreset.BALANCED.key -> R.string.st_preset_balanced
                AdBlockPreset.HIDE_ONLY.key -> R.string.st_preset_hide_only
                else -> R.string.ad_block_preset_custom
            }
        )
    }

    override fun adBlockSummary(): String? {
        val summary = AdStats.summary(requireContext().prefs())
        return if (summary.breaks > 0) {
            getString(R.string.ad_block_stats_summary, summary.breaks, DateUtilsCompat.duration(summary.seconds))
        } else {
            getString(R.string.ad_block_stats_none)
        }
    }

    override fun adLog() = AdLog.lines()

    // --- v0.1 methods the activity, the chat and the menu still call ---

    fun getCurrentPosition(): Long? = backend?.player?.currentPosition

    fun getCurrentSpeed(): Float? = backend?.player?.playbackParameters?.speed

    private fun getTotalDuration() = (backend?.player?.currentManifest as? HlsManifest)?.mediaPlaylist?.durationUs?.div(1000)

    protected fun changeQuality(selectedQuality: VideoQuality?) {
        backend?.changeQuality(selectedQuality)
        connection.updateQuality()
    }

    fun canEnterPictureInPicture(): Boolean {
        val quality = backend?.quality
        return quality?.name != VideoQuality.AUDIO_ONLY_QUALITY && quality?.name != VideoQuality.CHAT_ONLY_QUALITY
    }

    fun secondViewIsHidden() = lastChat?.visible != true && isMaximized

    fun getIsPortrait() = isPortrait

    fun reloadEmotes() = chatFragment?.reloadEmotes()

    fun isActive() = chatFragment?.isActive()

    fun disconnect() = chatFragment?.disconnect()

    fun reconnect() = chatFragment?.reconnect()

    override fun changePlayerMode() {
        if (view == null) return
        connection.updateQuality()
        val pip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        if (canEnterPictureInPicture()) {
            if (pip && requireContext().prefs().getBoolean(C.PLAYER_PICTURE_IN_PICTURE, true)) {
                requireActivity().setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(true).build())
            }
        } else {
            // audio only: the controls stay (PlayerScreen pins them) and the screen stays on
            requireView().keepScreenOn = true
            if (pip) {
                requireActivity().setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
            }
        }
    }

    /** "Listen only" from a long press: switch to audio once the qualities are known (U6). */
    protected fun applyAudioOnlyStart() {
        if (arguments?.getBoolean(KEY_AUDIO_ONLY) == true && !audioOnlyApplied && backend?.loaded == true) {
            backend?.qualities?.find { it.name == VideoQuality.AUDIO_ONLY_QUALITY }?.let {
                audioOnlyApplied = true
                changeQuality(it)
                changePlayerMode()
            }
        }
    }

    fun toggleChatBar() {
        val visible = !(chatFragment?.chatBarVisible() ?: state.chatBarVisible)
        chatFragment?.setChatBarVisible(visible)
        requireContext().prefs().edit { putBoolean(C.KEY_CHAT_BAR_VISIBLE, visible) }
        state.chatBarVisible = visible
    }

    fun updateViewerCount(viewerCount: Int?) {
        state.viewers = viewerCount
    }

    fun updateLiveStatus(live: Boolean, serverTime: Long?, channelLogin: String?) {
        if (channelLogin == backend?.channelLogin) {
            if (live) {
                backend?.restartPlayer()
            }
            state.startedAt = serverTime?.times(1000)
        }
    }

    fun updateStreamInfo(title: String?, gameId: String?, gameSlug: String?, gameName: String?) {
        backend?.let {
            it.title = title
            it.gameId = gameId
            it.gameSlug = gameSlug
            it.gameName = gameName
        }
        state.title = title?.trim()?.takeIf { it.isNotBlank() }
        state.gameName = gameName?.takeIf { it.isNotBlank() }
    }

    private fun saveBookmark() {
        val backend = backend ?: return
        viewModel.saveBookmark(
            filesDir = requireContext().filesDir.path,
            networkLibrary = requireContext().prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            helixHeaders = TwitchApiHelper.getHelixHeaders(requireContext()),
            gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
            videoId = backend.videoId,
            title = backend.title,
            uploadDate = backend.createdAt,
            durationSeconds = backend.durationSeconds,
            type = backend.videoType,
            animatedPreviewUrl = backend.videoAnimatedPreviewURL,
            channelId = backend.channelId,
            channelLogin = backend.channelLogin,
            channelName = backend.channelName,
            channelImage = backend.channelImage,
            thumbnail = backend.thumbnail,
            gameId = backend.gameId,
            gameSlug = backend.gameSlug,
            gameName = backend.gameName,
        )
        state.bookmarked = state.bookmarked != true
    }

    private fun share() {
        val backend = backend ?: return
        when (backend.type) {
            BasePlaybackService.STREAM -> backend.channelLogin?.let { channelLogin ->
                startActivity(Intent.createChooser(Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "https://twitch.tv/${channelLogin}")
                    backend.channelName?.let { putExtra(Intent.EXTRA_TITLE, it) }
                    type = "text/plain"
                }, null))
            }
            BasePlaybackService.VIDEO -> backend.videoId?.let { videoId ->
                val position = getCurrentPosition()?.let { PlayerLogic.videoShareTime(it) } ?: ""
                startActivity(Intent.createChooser(Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "https://twitch.tv/videos/${videoId}${position}")
                    backend.title?.let { putExtra(Intent.EXTRA_TITLE, it) }
                    type = "text/plain"
                }, null))
            }
            BasePlaybackService.CLIP -> backend.clipId?.let { clipId ->
                backend.channelLogin?.let { channelLogin ->
                    startActivity(Intent.createChooser(Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "https://twitch.tv/${channelLogin}/clip/${clipId}")
                        backend.title?.let { putExtra(Intent.EXTRA_TITLE, it) }
                        type = "text/plain"
                    }, null))
                }
            }
            BasePlaybackService.OFFLINE_VIDEO -> backend.quality?.url?.let { videoUrl ->
                val uri = if (videoUrl.endsWith(".m3u8")) videoUrl.substringBefore("%2F").toUri() else videoUrl.toUri()
                startActivity(Intent.createChooser(Intent().apply {
                    action = Intent.ACTION_SEND
                    setDataAndType(uri, requireContext().contentResolver.getType(uri))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    backend.title?.let { putExtra(Intent.EXTRA_TITLE, it) }
                }, null))
            }
        }
    }

    private fun getUnavailableQualities(): List<VideoQuality> {
        val stableVariantIds = mutableListOf<VideoQuality>()
        (backend?.player?.currentManifest as? HlsManifest)?.multivariantPlaylist?.tags?.let { playlist ->
            playlist.filter { it.startsWith("#EXT-X-SESSION-DATA") }.forEach { line ->
                val id = Regex("DATA-ID=\"(.+?)\"").find(line)?.groups?.get(1)?.value
                if (id == "com.amazon.ivs.unavailable-media") {
                    val value = Regex("VALUE=\"(.+?)\"").find(line)?.groups?.get(1)?.value ?: return@forEach
                    val bytes = try {
                        Base64.decode(value, Base64.DEFAULT)
                    } catch (e: IllegalArgumentException) {
                        null
                    } ?: return@forEach
                    val array = try {
                        JSONArray(String(bytes))
                    } catch (e: JSONException) {
                        null
                    } ?: return@forEach
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val filterReasons = obj.optJSONArray("FILTER_REASONS")
                        val skip = filterReasons != null && (0 until filterReasons.length()).any {
                            val filter = filterReasons.optString(it)
                            filter == "FR_CODEC_NOT_REQUESTED" || filter == "FR_DUALFORMAT_INCOMPATIBLE" // portrait
                        }
                        if (!skip) {
                            val newVariantId = obj.optString("STABLE-VARIANT-ID")
                            val resolution = obj.optString("RESOLUTION")
                            val frameRate = obj.optString("FRAME-RATE").toFloatOrNull()
                            val bitrate = obj.optInt("BANDWIDTH")
                            val codec = obj.optString("CODECS")
                            if (!newVariantId.isNullOrBlank()) {
                                stableVariantIds.add(VideoQuality(
                                    name = newVariantId,
                                    resolution = resolution.takeIf { !it.isNullOrBlank() }?.substringAfter('x')?.toIntOrNull(),
                                    frameRate = frameRate.takeIf { it != null && it > 0 },
                                    bitrate = bitrate.takeIf { it > 0 },
                                    codecs = codec.takeIf { !it.isNullOrBlank() },
                                ))
                            }
                        }
                    }
                }
            }
        }
        return stableVariantIds
    }

    private fun findUnlistedVideo() {
        val backend = backend ?: return
        val unavailableQualities = getUnavailableQualities()
        val qualities = if (unavailableQualities.isNotEmpty()) {
            mutableListOf<VideoQuality>().apply {
                backend.qualities?.filter { !it.url.isNullOrBlank() }?.let { addAll(it) }
                addAll(unavailableQualities)
            }
                .sortedByDescending { it.bitrate }
                .sortedByDescending { it.frameRate }
                .sortedByDescending { it.resolution }
                .toMutableList().apply {
                    find { it.name?.startsWith("audio", true) == true }?.let { audio ->
                        remove(audio)
                        add(audio)
                    }
                }
        } else {
            backend.qualities?.filter { !it.url.isNullOrBlank() }
        }
        viewModel.findUnlistedVideo(
            requireContext().prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            qualities,
            backend.streamId,
            backend.channelLogin,
            backend.createdAt,
        )
    }

    private fun showPlaylistTags(mediaPlaylist: Boolean) {
        val tags = backend?.getPlaylistTags(mediaPlaylist)
        if (!tags.isNullOrBlank()) {
            requireContext().getAlertDialogBuilder().apply {
                setView(NestedScrollView(context).apply {
                    addView(HorizontalScrollView(context).apply {
                        addView(TextView(context).apply {
                            text = tags
                            textSize = 12F
                            setTextIsSelectable(true)
                        })
                    })
                })
                setNegativeButton(R.string.copy_clip) { _, _ ->
                    val clipboard = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("label", tags))
                }
                setPositiveButton(android.R.string.ok, null)
            }.show()
        }
    }

    fun showDownloadDialog() {
        val backend = backend ?: return
        if (!backend.loaded) return
        val qualities = backend.qualities?.filter { !it.url.isNullOrBlank() }
        val names = qualities?.map { it.name.toString() }?.toTypedArray()
        val resolutions = qualities?.map { it.resolution.toString() }?.toTypedArray()
        val frameRates = qualities?.map { it.frameRate.toString() }?.toTypedArray()
        val bitrates = qualities?.map { it.bitrate.toString() }?.toTypedArray()
        val codecs = qualities?.map { it.codecs.toString() }?.toTypedArray()
        val urls = qualities?.map { it.url.toString() }?.toTypedArray()
        when (backend.type) {
            BasePlaybackService.STREAM -> DownloadDialog.newStreamInstance(
                id = backend.streamId, channelId = backend.channelId, channelLogin = backend.channelLogin, channelName = backend.channelName,
                channelImage = backend.channelImage, gameId = backend.gameId, gameSlug = backend.gameSlug, gameName = backend.gameName,
                title = backend.title, thumbnail = backend.thumbnail, createdAt = backend.createdAt,
                qualityNames = names, qualityResolutions = resolutions, qualityFrameRates = frameRates, qualityBitrates = bitrates,
                qualityCodecs = codecs, qualityUrls = urls,
            ).show(childFragmentManager, null)
            BasePlaybackService.VIDEO -> DownloadDialog.newVideoInstance(
                id = backend.videoId, channelId = backend.channelId, channelLogin = backend.channelLogin, channelName = backend.channelName,
                channelImage = backend.channelImage, gameId = backend.gameId, gameSlug = backend.gameSlug, gameName = backend.gameName,
                title = backend.title, thumbnail = backend.thumbnail, createdAt = backend.createdAt, durationSeconds = backend.durationSeconds,
                type = backend.videoType, animatedPreviewUrl = backend.videoAnimatedPreviewURL, totalDuration = getTotalDuration(),
                currentPosition = getCurrentPosition(), qualityNames = names, qualityResolutions = resolutions, qualityFrameRates = frameRates,
                qualityBitrates = bitrates, qualityCodecs = codecs, qualityUrls = urls,
            ).show(childFragmentManager, null)
            BasePlaybackService.CLIP -> DownloadDialog.newClipInstance(
                id = backend.clipId, channelId = backend.channelId, channelLogin = backend.channelLogin, channelName = backend.channelName,
                channelImage = backend.channelImage, gameId = backend.gameId, gameSlug = backend.gameSlug, gameName = backend.gameName,
                title = backend.title, thumbnail = backend.thumbnail, createdAt = backend.createdAt, durationSeconds = backend.durationSeconds,
                videoId = backend.videoId, videoOffsetSeconds = backend.videoOffsetSeconds, videoCreatedAt = backend.videoCreatedAt,
                qualityNames = names, qualityResolutions = resolutions, qualityFrameRates = frameRates, qualityBitrates = bitrates,
                qualityCodecs = codecs, qualityUrls = urls,
            ).show(childFragmentManager, null)
        }
    }

    fun onSleepTimerChanged(durationMs: Long, hours: Int, minutes: Int, lockScreen: Boolean) {
        if (durationMs > 0L) {
            Toast.makeText(
                requireContext(),
                when {
                    hours == 0 -> getString(R.string.playback_will_stop, resources.getQuantityString(R.plurals.minutes, minutes, minutes))
                    minutes == 0 -> getString(R.string.playback_will_stop, resources.getQuantityString(R.plurals.hours, hours, hours))
                    else -> getString(
                        R.string.playback_will_stop_hours_minutes,
                        resources.getQuantityString(R.plurals.hours, hours, hours),
                        resources.getQuantityString(R.plurals.minutes, minutes, minutes),
                    )
                },
                Toast.LENGTH_LONG,
            ).show()
        } else if (((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0) > 0L) {
            Toast.makeText(requireContext(), R.string.timer_canceled, Toast.LENGTH_LONG).show()
        }
        if (lockScreen != requireContext().prefs().getBoolean(C.SLEEP_TIMER_LOCK, false)) {
            requireContext().prefs().edit { putBoolean(C.SLEEP_TIMER_LOCK, lockScreen) }
        }
        (activity as? MainActivity)?.setSleepTimer(durationMs)
        state.sleepTimerEnd = if (durationMs > 0L) System.currentTimeMillis() + durationMs else 0L
    }

    protected fun setPipActions(playing: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            requireContext().prefs().getBoolean(C.PLAYER_PICTURE_IN_PICTURE, true)
        ) {
            requireActivity().setPictureInPictureParams(
                PictureInPictureParams.Builder().apply {
                    setActions(listOf(
                        RemoteAction(
                            Icon.createWithResource(requireContext(), R.drawable.baseline_audiotrack_black_24),
                            getString(R.string.audio_only),
                            getString(R.string.audio_only),
                            PendingIntent.getBroadcast(
                                requireContext(),
                                REQUEST_CODE_AUDIO_ONLY,
                                Intent(MainActivity.INTENT_START_AUDIO_ONLY).setPackage(requireContext().packageName),
                                PendingIntent.FLAG_IMMUTABLE,
                            ),
                        ),
                        RemoteAction(
                            Icon.createWithResource(requireContext(), if (playing) R.drawable.baseline_pause_black_48 else R.drawable.baseline_play_arrow_black_48),
                            getString(if (playing) R.string.pause else R.string.resume),
                            getString(if (playing) R.string.pause else R.string.resume),
                            PendingIntent.getBroadcast(
                                requireContext(),
                                REQUEST_CODE_PLAY_PAUSE,
                                Intent(MainActivity.INTENT_PLAY_PAUSE_PLAYER).setPackage(requireContext().packageName),
                                PendingIntent.FLAG_IMMUTABLE,
                            ),
                        ),
                    ))
                }.build()
            )
        }
    }

    private fun isLoggedIn(): Boolean {
        val context = requireContext()
        return !context.tokenPrefs().getString(C.USERNAME, null).isNullOrBlank() &&
                (!TwitchApiHelper.getGQLHeaders(context, true)[C.HEADER_TOKEN].isNullOrBlank() ||
                        !TwitchApiHelper.getHelixHeaders(context)[C.HEADER_TOKEN].isNullOrBlank())
    }

    private fun channelDisplayName() = PlayerLogic.displayName(backend?.channelName, backend?.channelLogin, options.nameDisplay)

    private fun showStatusBar() {
        if (isAdded) WindowCompat.getInsetsController(requireActivity().window, requireActivity().window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun hideStatusBar() {
        if (isAdded) WindowCompat.getInsetsController(requireActivity().window, requireActivity().window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }

    /** v0.1: Android 11 and up ask the activity; before that the flag is unreliable and the page's own state is used. */
    private fun isInPipMode() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        requireActivity().isInPictureInPictureMode
    } else {
        state.inPictureInPicture && isMaximized
    }

    override fun onResume() {
        super.onResume()
        if (isInPipMode()) {
            state.inPictureInPicture = true
            state.controlsVisible = false
        }
    }

    override fun initialize() {
        if (!started && backend?.started == true) {
            started = true
            startPage()
            connection.restoreQualityIfNeeded()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        if (isPortrait) state.tabletFullscreen = false
        if (!isInPipMode()) {
            (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(chatContainer?.windowToken, 0)
            chatContainer?.clearFocus()
            initLayout()
        }
        state.sheet = null
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        state.inPictureInPicture = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            if (!isMaximized) {
                isMaximized = true
                requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
                if (state.kind == PlayerKind.Stream && chatFragment?.emoteMenuIsVisible() == true) {
                    chatFragment?.toggleBackPressedCallback(true)
                }
            }
            state.controlsVisible = false
            state.sheet = null
            confirmUnfollow = false
            // player dialogs and the chat's sheets
            (childFragmentManager.findFragmentByTag("closeOnPip") as? DialogFragment)?.dismiss()
            state.viewerListOpen = false
            chatFragment?.closeSheets()
        }
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        val backend = backend
        val context = requireContext()
        val prefs = context.prefs()
        fun refreshFollow() = viewModel.isFollowingChannel(
            context.tokenPrefs().getString(C.USER_ID, null),
            backend?.channelId,
            backend?.channelLogin,
            prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
            prefs.getString(C.NETWORK_LIBRARY, C.OKHTTP),
            TwitchApiHelper.getGQLHeaders(context, true),
            TwitchApiHelper.getHelixHeaders(context),
        )
        when (callback) {
            "refreshStream", "refreshClip" -> {
                backend?.retry(callback)
                refreshFollow()
            }
            "refreshVideo" -> {
                backend?.retry(callback)
                refreshFollow()
                loadChapters()
            }
            "follow" -> setFollowing(true)
            "unfollow" -> unfollow()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        resetBrightness()
        videoView = null
        aspectFrame = null
        surfaceView = null
        subtitleView = null
        chatContainer = null
        lastVideo = null
        lastChat = null
    }

    companion object {
        private const val REQUEST_CODE_AUDIO_ONLY = 2
        private const val REQUEST_CODE_PLAY_PAUSE = 3
        private const val KEY_AUDIO_ONLY_APPLIED = "audioOnlyApplied"
        const val KEY_OFFLINE = "offline"
        /** Start a stream as audio only ("Listen only" in the long-press menus, U6). */
        const val KEY_AUDIO_ONLY = "audioOnly"
        private const val PAGE_COLOR = 0xFF111113.toInt()
        private const val OVERLAY_COLOR = 0xB3111113.toInt()
    }
}

/** Ad statistics' total ad time, as "3 min" or "1 h 5 min". */
private object DateUtilsCompat {
    fun duration(seconds: Long): String {
        val minutes = seconds / 60
        return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    }
}
