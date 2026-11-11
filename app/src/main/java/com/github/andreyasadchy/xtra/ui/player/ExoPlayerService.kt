package com.github.andreyasadchy.xtra.ui.player

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.audiofx.DynamicsProcessing
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.ParsingLoadable
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.model.ui.CustomProxy
import com.github.andreyasadchy.xtra.model.ui.StreamProxy
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.player.ads.AdBlockConfig
import com.github.andreyasadchy.xtra.player.ads.AdController
import com.github.andreyasadchy.xtra.player.ads.AdDetector
import com.github.andreyasadchy.xtra.player.ads.AdLog
import com.github.andreyasadchy.xtra.player.ads.AdPlaylist
import com.github.andreyasadchy.xtra.player.ads.AdSource
import com.github.andreyasadchy.xtra.player.ads.AdSourceQueue
import com.github.andreyasadchy.xtra.player.ads.AdSourceResolver
import com.github.andreyasadchy.xtra.player.ads.StartupRace
import com.github.andreyasadchy.xtra.player.ads.StreamDataSources
import com.github.andreyasadchy.xtra.player.lowlatency.HlsPlaylistParser
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.MediaButtonReceiver
import com.github.andreyasadchy.xtra.util.NetworkUtils
import com.github.andreyasadchy.xtra.util.NetworkUtils.executeAsync
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.FileInputStream
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
class ExoPlayerService : BasePlaybackService() {

    var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var notificationManager: NotificationManager? = null
    private var artworkUri: String? = null
    private var cachedBitmap: Bitmap? = null
    private var bitmapLoadJob: Job? = null
    private var showStreamNotificationSeekbar = true

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var sleepTimer: Timer? = null
    private var sleepTimerEndTime = 0L
    private var lastSavedPosition: Long? = null
    private var savePositionTimer: Timer? = null
    private var stopServiceTimer: Timer? = null

    private var adConfig: AdBlockConfig? = null
    private var adController: AdController? = null
    /** The secondary player while it is on screen. */
    var secondaryPlayer: ExoPlayer? = null
    /** The secondary player while it warms up off screen, before the primary reaches the ad. */
    private var pendingSecondaryPlayer: ExoPlayer? = null
    private var surfaceOnSecondary = false
    private var adBoundaryJob: Job? = null
    private var adRestoreJob: Job? = null
    private var adStatusJob: Job? = null
    /** Wall clock end of the newest ad break in the primary's playlist. */
    private var adEndWallMs: Long? = null
    private var coveredSinceMs: Long? = null

    private var backupQualities: List<String>? = null
    private var updateQualities = false
    private var ignorePlaylistUpdate = false
    private var created = false

    interface Listener {
        fun started()
        fun loaded()
        fun changePlayerMode()
        fun toast(resId: Int, duration: Int)
        fun toast(text: CharSequence, duration: Int)
        fun updateVideoInfo()
        fun setPlayerSurface(secondaryPlayer: Boolean)
        /** A short ad status for the corner of the player, null to hide it. */
        fun adStatus(text: CharSequence?)
    }

    var serviceListener: Listener? = null

    override fun onCreate() {
        super.onCreate()
        xtraModule = (application as XtraApp).xtraModule
    }

    private fun create(restorePauseState: Boolean) {
        if (!created) {
            created = true
            showStreamNotificationSeekbar = prefs().getBoolean(C.PLAYER_SHOW_STREAM_NOTIFICATION_SEEKBAR, true)
            val playerListener = object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    dynamicsProcessing?.let {
                        it.release()
                        dynamicsProcessing = null
                    }
                    if (prefs().getBoolean(C.PLAYER_AUDIO_COMPRESSOR, false)) {
                        reinitializeDynamicsProcessing(audioSessionId)
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updatePlaybackState()
                    updateMetadata()
                }

                override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                    updateMetadata()
                    updateNotification()
                }

                override fun onTracksChanged(tracks: Tracks) {
                    if (!tracks.isEmpty) {
                        if (!loaded) {
                            loaded = true
                            serviceListener?.loaded()
                            toggleSubtitles(prefs().getBoolean(C.PLAYER_SUBTITLES_ENABLED, false))
                        }
                        // not while an ad is hidden or replaced: changeQuality would turn the video back on
                        // (unhidePrimaryPlayer applies the quality again once the ad is over)
                        if (qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null && quality?.name != VideoQuality.AUDIO_ONLY_QUALITY
                            && !(type == STREAM && adController?.isActive == true)
                        ) {
                            changeQuality(quality)
                        }
                    }
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    updatePlaybackState()
                    updateMetadata()
                    updateNotification()
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && !timeline.isEmpty && qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null) {
                        if (ignorePlaylistUpdate) {
                            ignorePlaylistUpdate = false
                        } else {
                            if (type == STREAM) {
                                updateQualities = quality?.name != VideoQuality.AUDIO_ONLY_QUALITY
                            }
                        }
                    }
                    if (qualities.isNullOrEmpty() || updateQualities) {
                        val playlist = (player?.currentManifest as? HlsManifest)?.multivariantPlaylist
                        val list = playlist?.variants?.mapNotNull { variant ->
                            val name = variant.stableVariantId?.takeIf { it.isNotBlank() }
                                ?: playlist.videos.find { it.groupId == variant.videoGroupId }?.name?.takeIf { it.isNotBlank() }
                            if (name != null) {
                                VideoQuality(name, variant.format.height, variant.format.frameRate, variant.format.bitrate, variant.format.codecs, variant.url.toString())
                            } else null
                        }
                        if (!list.isNullOrEmpty()) {
                            qualities = list
                                .sortedByDescending { it.bitrate }
                                .sortedByDescending { it.frameRate }
                                .sortedByDescending { it.resolution }
                                .toMutableList().apply {
                                    add(0, VideoQuality(VideoQuality.AUTO_QUALITY))
                                    find { it.name.equals("source", true) }?.let { source ->
                                        remove(source)
                                        add(1, VideoQuality(VideoQuality.SOURCE_QUALITY, source.resolution, source.frameRate, source.bitrate, source.codecs, source.url))
                                    }
                                    val audio = find { it.name?.startsWith("audio", true) == true }
                                    audio?.let { remove(it) }
                                    add(VideoQuality(VideoQuality.AUDIO_ONLY_QUALITY, audio?.resolution, audio?.frameRate, audio?.bitrate, audio?.codecs, audio?.url))
                                    if (type == STREAM) {
                                        add(VideoQuality(VideoQuality.CHAT_ONLY_QUALITY))
                                    }
                                }
                            setDefaultQuality()
                            serviceListener?.changePlayerMode()
                            if (quality?.name == VideoQuality.AUDIO_ONLY_QUALITY) {
                                changeQuality(quality)
                            }
                        }
                        if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
                            updateQualities = false
                        }
                    }
                    if (type == STREAM) {
                        handleStreamAds((player?.currentManifest as? HlsManifest)?.mediaPlaylist)
                    }
                }

                override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                    updatePlaybackState()
                }

                override fun onPlayerError(error: PlaybackException) {
                    updatePlaybackState()
                    when (type) {
                        STREAM -> {
                            val responseCode = (player?.playerError?.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0
                            val timeout = (player?.playerError?.cause as? DataSourceException)?.reason?.let {
                                it == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                                        || it == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                            } == true
                            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                            val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                            val isNetworkAvailable = networkCapabilities != null
                                    && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                    && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            if (isNetworkAvailable) {
                                when {
                                    responseCode == 404 -> {
                                        serviceListener?.toast(R.string.stream_ended, Toast.LENGTH_LONG)
                                    }
                                    useCustomProxy && (responseCode >= 400 || timeout) -> {
                                        val host = adConfig?.customProxies?.getOrNull(currentCustomProxy)?.url?.let {
                                            it.toUri().host ?: "https://$it".toUri().host
                                        }
                                        currentCustomProxy += 1
                                        if (host != null) {
                                            AdLog.w("proxy $host failed, trying the next source") // no toast: the next source takes over
                                        }
                                        lifecycleScope.launch {
                                            delay(1500.milliseconds)
                                            restartPlayer()
                                        }
                                    }
                                    useStreamProxy && (responseCode >= 400 || timeout) -> {
                                        val host = adConfig?.streamProxies?.getOrNull(currentStreamProxy)?.host
                                        currentStreamProxy += 1
                                        if (host != null) {
                                            AdLog.w("proxy $host failed, trying the next source") // no toast: the next source takes over
                                        }
                                        lifecycleScope.launch {
                                            delay(1500.milliseconds)
                                            restartPlayer()
                                        }
                                    }
                                    else -> {
                                        serviceListener?.toast(R.string.player_error, Toast.LENGTH_SHORT)
                                        lifecycleScope.launch {
                                            delay(1500.milliseconds)
                                            restartPlayer()
                                        }
                                    }
                                }
                            }
                        }
                        VIDEO -> {
                            val responseCode = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0
                            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                            val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                            val isNetworkAvailable = networkCapabilities != null
                                    && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                    && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            if (isNetworkAvailable) {
                                when {
                                    !skipAccessToken && responseCode != 0 && videoAnimatedPreviewURL != null -> {
                                        skipAccessToken = true
                                        videoAnimatedPreviewURL?.let { preview ->
                                            val backupQualities = backupQualities
                                            val list = (backupQualities ?: TwitchApiHelper.defaultQualityList).map { quality ->
                                                val split = quality.split("p")
                                                val resolution = split.getOrNull(0)?.takeWhile { it.isDigit() }?.toIntOrNull()
                                                val frameRate = split.getOrNull(1)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 30
                                                val url = preview
                                                    .replace("storyboards", quality)
                                                    .replaceAfterLast(
                                                        "/",
                                                        if (videoType?.lowercase() == "highlight") {
                                                            "highlight-${preview.substringAfterLast("/").substringBefore("-")}.m3u8"
                                                        } else {
                                                            "index-dvr.m3u8"
                                                        }
                                                    )
                                                val name = if (quality == "chunked") {
                                                    "source"
                                                } else {
                                                    quality
                                                }
                                                VideoQuality(name, resolution, frameRate.toFloat(), url = url)
                                            }
                                            qualities = list
                                                .sortedByDescending { it.bitrate }
                                                .sortedByDescending { it.frameRate }
                                                .sortedByDescending { it.resolution }
                                                .toMutableList().apply {
                                                    find { it.name.equals("source", true) }?.let { source ->
                                                        remove(source)
                                                        add(0, VideoQuality(VideoQuality.SOURCE_QUALITY, source.resolution, source.frameRate, source.bitrate, source.codecs, source.url))
                                                    }
                                                    val audio = find { it.name?.startsWith("audio", true) == true }
                                                    audio?.let { remove(it) }
                                                    add(VideoQuality(VideoQuality.AUDIO_ONLY_QUALITY, audio?.resolution, audio?.frameRate, audio?.bitrate, audio?.codecs, audio?.url))
                                                }
                                            if (backupQualities != null) {
                                                setDefaultQuality()
                                            } else {
                                                quality = qualities?.firstOrNull()
                                            }
                                            serviceListener?.changePlayerMode()
                                            val url = quality?.url
                                            if (url != null) {
                                                player?.let { player ->
                                                    val playbackPosition = player.currentPosition
                                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                                        setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                                    }.build()
                                                    val networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP)
                                                    player.setMediaSource(
                                                        HlsMediaSource.Factory(
                                                            DefaultDataSource.Factory(
                                                                this@ExoPlayerService,
                                                                StreamDataSources.factory(application, xtraModule, networkLibrary, 0)
                                                            )
                                                        ).apply {
                                                            setPlaylistParserFactory(CustomHlsPlaylistParserFactory())
                                                        }.createMediaSource(
                                                            MediaItem.fromUri(url)
                                                        )
                                                    )
                                                    player.volume = prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
                                                    player.setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                                                    player.prepare()
                                                    player.playWhenReady = true
                                                    player.seekTo(playbackPosition)
                                                }
                                            }
                                        }
                                    }
                                    responseCode == 403 -> {
                                        serviceListener?.toast(R.string.video_subscribers_only, Toast.LENGTH_LONG)
                                    }
                                    else -> {
                                        serviceListener?.toast(R.string.player_error, Toast.LENGTH_SHORT)
                                        lifecycleScope.launch {
                                            delay(1500.milliseconds)
                                            player?.prepare()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    updatePlaybackState()
                    updateNotification()
                }

                override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                    updatePlaybackState()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updatePlaybackState()
                    updateNotification()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlaybackState()
                    if (isPlaying) {
                        if (savePositionTimer == null && type != STREAM) {
                            savePositionTimer = Timer().apply {
                                scheduleAtFixedRate(30000, 30000) {
                                    Handler(Looper.getMainLooper()).post {
                                        updateSavedPosition()
                                    }
                                }
                            }
                        }
                        stopServiceTimer?.cancel()
                        stopServiceTimer = null
                    } else {
                        savePositionTimer?.cancel()
                        savePositionTimer = null
                        updateSavedPosition()
                        if (stopServiceTimer == null && serviceListener == null) {
                            stopServiceTimer = Timer().apply {
                                schedule(600000) {
                                    Handler(Looper.getMainLooper()).post {
                                        stopSelf()
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    updatePlaybackState()
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    updatePlaybackState()
                }
            }
            val sessionCallback = object : MediaSession.Callback() {
                override fun onPrepare() {
                    player?.prepare()
                }

                override fun onPlay() {
                    togglePause()
                }

                override fun onPause() {
                    pause()
                }

                override fun onSkipToNext() {
                    seekForward()
                }

                override fun onSkipToPrevious() {
                    seekBack()
                }

                override fun onFastForward() {
                    seekForward()
                }

                override fun onRewind() {
                    seekBack()
                }

                override fun onStop() {
                    stop()
                }

                override fun onSeekTo(pos: Long) {
                    seekTo(pos)
                }

                override fun onSetPlaybackSpeed(speed: Float) {
                    player?.setPlaybackSpeed(speed)
                }

                override fun onCustomAction(action: String, extras: Bundle?) {
                    when (action) {
                        INTENT_REWIND -> seekBack()
                        INTENT_FAST_FORWARD -> seekForward()
                    }
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val eventHandled = super.onMediaButtonEvent(mediaButtonIntent)
                    return if (eventHandled) {
                        true
                    } else {
                        if (mediaButtonIntent.action == Intent.ACTION_MEDIA_BUTTON) {
                            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                            }
                            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (keyEvent.keyCode) {
                                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                        seekBack()
                                        true
                                    }
                                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                        seekForward()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        } else false
                    }
                }
            }
            val player = buildPlayer()
            this.player = player
            player.addListener(playerListener)
            val session = MediaSession(this, "ExoPlayerService")
            this.session = session
            session.setCallback(sessionCallback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    session.setMediaButtonBroadcastReceiver(ComponentName(this, MediaButtonReceiver::class.java))
                } catch (e: IllegalArgumentException) {
                    // https://github.com/androidx/media/issues/1730
                }
            } else {
                @Suppress("DEPRECATION")
                session.setMediaButtonReceiver(
                    PendingIntent.getBroadcast(this, 0, Intent(Intent.ACTION_MEDIA_BUTTON).setClass(this, MediaButtonReceiver::class.java), PendingIntent.FLAG_MUTABLE)
                )
            }
            session.isActive = true
            notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channelId = getString(R.string.notification_playback_channel_id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager?.getNotificationChannel(channelId) == null) {
                notificationManager?.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        ContextCompat.getString(this, R.string.notification_playback_channel_title),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                            setShowBadge(false)
                        }
                    }
                )
            }
            start(restorePauseState)
        }
    }

    private fun start(restorePauseState: Boolean) {
        lifecycleScope.launch {
            restorePlaybackState()
            if (type != STREAM || qualities.isNullOrEmpty()) {
                // a new item: never carry a hidden/muted player over from a previous stream
                adController?.let { applyAdActions(it.reset()) }
                adController = null
            }
            when (type) {
                STREAM -> {
                    started = true
                    serviceListener?.started()
                    if (!qualities.isNullOrEmpty()) {
                        if (quality?.name == VideoQuality.AUDIO_ONLY_QUALITY) {
                            serviceListener?.changePlayerMode()
                            player?.let { player ->
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                }.build()
                            }
                        }
                    } else {
                        useCustomProxy = prefs().getBoolean(C.PLAYER_USE_CUSTOM_PROXY, true)
                        if (!useCustomProxy) {
                            useStreamProxy = prefs().getBoolean(C.PLAYER_USE_STREAM_PROXY, false)
                        }
                    }
                    val config = AdBlockConfig.load(prefs(), xtraModule.playerRepository)
                    adConfig = config
                    // useStreamProxy is read at every ad break: it turns off when every stream proxy failed
                    adController = AdController(AdSourceQueue { config.adSources(useStreamProxy) }, config.hideAds)
                    loadStream(restorePauseState)
                }
                VIDEO -> {
                    started = true
                    serviceListener?.started()
                    if (videoId != null) {
                        if (!qualities.isNullOrEmpty() && quality?.name == VideoQuality.AUDIO_ONLY_QUALITY) {
                            serviceListener?.changePlayerMode()
                            player?.let { player ->
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                }.build()
                            }
                        }
                        loadVideo(restorePauseState)
                        if (title == null) {
                            updateVideoInfo()
                        }
                    } else {
                        qualities?.let { list ->
                            qualities = list
                                .sortedByDescending { it.bitrate }
                                .sortedByDescending { it.frameRate }
                                .sortedByDescending { it.resolution }
                                .toMutableList().apply {
                                    find { it.name.equals("source", true) }?.let { source ->
                                        remove(source)
                                        add(0, VideoQuality(VideoQuality.SOURCE_QUALITY, source.resolution, source.frameRate, source.bitrate, source.codecs, source.url))
                                    }
                                    val audio = find { it.name?.startsWith("audio", true) == true }
                                    audio?.let { remove(it) }
                                    add(VideoQuality(VideoQuality.AUDIO_ONLY_QUALITY, audio?.resolution, audio?.frameRate, audio?.bitrate, audio?.codecs, audio?.url))
                                }
                            setDefaultQuality()
                            serviceListener?.changePlayerMode()
                            val url = quality?.url
                            if (url != null) {
                                player?.let { player ->
                                    if (quality?.name == VideoQuality.AUDIO_ONLY_QUALITY) {
                                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                            setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                        }.build()
                                    }
                                    val networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP)
                                    player.setMediaSource(
                                        HlsMediaSource.Factory(
                                            DefaultDataSource.Factory(
                                                this@ExoPlayerService,
                                                StreamDataSources.factory(application, xtraModule, networkLibrary, 0)
                                            )
                                        ).apply {
                                            setPlaylistParserFactory(CustomHlsPlaylistParserFactory())
                                        }.createMediaSource(
                                            MediaItem.fromUri(url)
                                        )
                                    )
                                    player.volume = prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
                                    player.setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                                    player.prepare()
                                    player.playWhenReady = !restorePauseState || !paused
                                    player.seekTo(savedPosition ?: 0)
                                }
                            }
                        }
                    }
                }
                CLIP -> {
                    started = true
                    serviceListener?.started()
                    loadClip(restorePauseState)
                }
                OFFLINE_VIDEO -> {
                    offlineVideoId?.let { id ->
                        val video = xtraModule.offlineVideosRepository.getById(id)
                        if (video != null) {
                            val playbackPosition = if (prefs().getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true)) {
                                video.lastWatchPosition
                            } else {
                                null
                            } ?: savedPosition ?: 0
                            chatUrl = video.chatUrl
                            started = true
                            serviceListener?.started()
                            if (qualities.isNullOrEmpty()) {
                                qualities = listOf(
                                    VideoQuality(VideoQuality.SOURCE_QUALITY, url = video.url),
                                    VideoQuality(VideoQuality.AUDIO_ONLY_QUALITY),
                                )
                                setDefaultQuality()
                            }
                            serviceListener?.changePlayerMode()
                            val url = quality?.url ?: qualities?.firstOrNull()?.url
                            if (url != null) {
                                player?.let { player ->
                                    if (quality?.name == VideoQuality.AUDIO_ONLY_QUALITY) {
                                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                            setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                        }.build()
                                    }
                                    player.setMediaItem(MediaItem.fromUri(url))
                                    player.volume = prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
                                    player.setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                                    player.prepare()
                                    player.playWhenReady = true
                                    player.seekTo(playbackPosition)
                                }
                            }
                        }
                    }
                }
                else -> stopSelf()
            }
        }
    }

    private suspend fun loadStream(restorePauseState: Boolean = false, restart: Boolean = false) {
        channelLogin?.let { channelLogin ->
            val streamProxy = if (useStreamProxy) {
                adConfig?.streamProxies?.getOrNull(currentStreamProxy).also {
                    if (it == null) {
                        useStreamProxy = false
                    }
                }
            } else null
            if (restart || qualities.isNullOrEmpty()) {
                playlistUrl = raceStartupSources(channelLogin, streamProxy)
            }
            val url = playlistUrl
            if (url != null) {
                player?.let { player ->
                    val networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP)
                    val customProxyUrl = if (useCustomProxy) {
                        url
                    } else null
                    val proxyTimeout = prefs().getString(C.PROXY_TIMEOUT, "3000")?.toIntOrNull() ?: 3000
                    val multivariantProxy = streamProxy?.takeIf { it.proxyMultivariantPlaylist }?.let { proxy ->
                        val host = proxy.host
                        val port = proxy.port
                        if (!host.isNullOrBlank() && port != null) StreamDataSources.HttpProxy(host, port, proxy.username, proxy.password) else null
                    }
                    player.setMediaSource(
                        HlsMediaSource.Factory(
                            DefaultDataSource.Factory(
                                this@ExoPlayerService,
                                StreamDataSources.factory(application, xtraModule, networkLibrary, proxyTimeout, customProxyUrl, multivariantProxy)
                            )
                        ).apply {
                            setPlaylistParserFactory(CustomHlsPlaylistParserFactory(filterAds = adConfig?.filterAdSegments == true))
                            setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(
                                if (useCustomProxy || multivariantProxy != null) {
                                    1
                                } else {
                                    6
                                }
                            ))
                        }.createMediaSource(
                            MediaItem.Builder().apply {
                                setUri(url.toUri())
                                setMimeType(MimeTypes.APPLICATION_M3U8)
                                setLiveConfiguration(MediaItem.LiveConfiguration.Builder().apply {
                                    prefs().getString(C.PLAYER_LIVE_MIN_SPEED, "")?.toFloatOrNull()?.let { setMinPlaybackSpeed(it) }
                                    prefs().getString(C.PLAYER_LIVE_MAX_SPEED, "")?.toFloatOrNull()?.let { setMaxPlaybackSpeed(it) }
                                    prefs().getString(C.PLAYER_LIVE_TARGET_OFFSET, "2000")?.toLongOrNull()?.let { setTargetOffsetMs(it) }
                                }.build())
                            }.build()
                        )
                    )
                    player.volume = prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
                    player.setPlaybackSpeed(1f)
                    player.prepare()
                    player.playWhenReady = !restorePauseState || !paused
                }
            }
        }
    }

    private sealed class StartupSource {
        data class Custom(val index: Int) : StartupSource()
        object TokenProxy : StartupSource()
        object Direct : StartupSource()
        object VideoSwap : StartupSource()
    }

    private fun customProxyUrl(proxy: CustomProxy, channelLogin: String): String? {
        val proxyUrl = proxy.url ?: return null
        val uri = proxyUrl.toUri().takeIf { it.host != null } ?: "https://$proxyUrl".toUri()
        return if (proxy.addQueryParams) {
            val supportedCodecs = prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264")
            uri.buildUpon().apply {
                if (uri.getQueryParameter("allow_source") == null) appendQueryParameter("allow_source", "true")
                if (uri.getQueryParameter("allow_audio_only") == null) appendQueryParameter("allow_audio_only", "true")
                if (uri.getQueryParameter("fast_bread") == null) appendQueryParameter("fast_bread", "true")
                if (uri.getQueryParameter("include_unavailable") == null) appendQueryParameter("include_unavailable", "true")
                if (!supportedCodecs.isNullOrBlank() && uri.getQueryParameter("supported_codecs") == null) appendQueryParameter("supported_codecs", supportedCodecs)
            }.build()
        } else {
            uri
        }.toString().replace("\$channel", channelLogin)
    }

    /**
     * Layer 1 of the ad pipeline, see [StartupRace]: custom proxies (from [currentCustomProxy]
     * on), the token through the stream proxy and the direct token are tried at the same time.
     * Sets [useCustomProxy] and [currentCustomProxy] for the winner and returns its url.
     */
    private suspend fun raceStartupSources(channelLogin: String, streamProxy: StreamProxy?): String? {
        val candidates = mutableListOf<StartupRace.Candidate>()
        if (useCustomProxy) {
            adConfig?.customProxies?.forEachIndexed { index, proxy ->
                if (index >= currentCustomProxy) {
                    val host = proxy.url?.let { it.toUri().host ?: "https://$it".toUri().host } ?: "proxy"
                    candidates.add(StartupRace.Candidate(host, index, { customProxyUrl(proxy, channelLogin) }, StartupSource.Custom(index)))
                }
            }
        }
        if (streamProxy?.proxyPlaybackAccessToken == true) {
            candidates.add(StartupRace.Candidate("token via ${streamProxy.host}", 1000, { getStreamPlaylistUrl(channelLogin, streamProxy) }, StartupSource.TokenProxy))
        }
        candidates.add(StartupRace.Candidate("direct", 1001, { getStreamPlaylistUrl(channelLogin) }, StartupSource.Direct))
        // a clean video swap source wins when the direct stream starts with an ad (anonymous tokens)
        if (adConfig?.useVideoSwap == true) {
            adConfig?.videoSwaps?.forEachIndexed { index, item ->
                candidates.add(StartupRace.Candidate("swap ${item.platform}/${item.playerType}", 2000 + index, {
                    try {
                        xtraModule.playerRepository.loadStreamPlaylistUrl(
                            context = this,
                            networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                            gqlHeaders = TwitchApiHelper.getGQLHeaders(this, false),
                            channelLogin = channelLogin,
                            platform = item.platform,
                            playerType = item.playerType,
                            supportedCodecs = prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                            proxyPlaybackAccessToken = false,
                            proxyHost = null,
                            proxyPort = null,
                            proxyUser = null,
                            proxyPassword = null,
                            proxyTimeout = null,
                            enableIntegrity = false
                        )
                    } catch (e: Exception) {
                        null
                    }
                }, StartupSource.VideoSwap))
            }
        }
        val resolver = AdSourceResolver(this, xtraModule, prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP))
        val (winner, results) = StartupRace(fetch = { resolver.fetch(it) }).run(candidates)
        results.sortedBy { it.candidate.priority }.forEach {
            AdLog.i("startup ${it.candidate.label}: ${if (it.ok) "ads=${it.ads} max=${it.maxHeight}p" else it.error} ${it.elapsedMs}ms")
        }
        AdLog.i("startup winner: ${winner?.candidate?.label}")
        when (val source = winner?.candidate?.tag) {
            is StartupSource.Custom -> {
                useCustomProxy = true
                currentCustomProxy = source.index
            }
            else -> useCustomProxy = false
        }
        return winner?.url
    }

    private suspend fun getStreamPlaylistUrl(channelLogin: String, streamProxy: StreamProxy? = null): String? {
        return try {
            xtraModule.playerRepository.loadStreamPlaylistUrl(
                context = this,
                networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                gqlHeaders = TwitchApiHelper.getGQLHeaders(this, prefs().getBoolean(C.TOKEN_INCLUDE_TOKEN_STREAM, true)),
                channelLogin = channelLogin,
                platform = prefs().getString(C.TOKEN_PLATFORM, "web"),
                playerType = prefs().getString(C.TOKEN_PLAYER_TYPE, "site"),
                supportedCodecs = prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                proxyPlaybackAccessToken = streamProxy != null,
                proxyHost = streamProxy?.host,
                proxyPort = streamProxy?.port,
                proxyUser = streamProxy?.username,
                proxyPassword = streamProxy?.password,
                proxyTimeout = prefs().getString(C.PROXY_TIMEOUT, "3000")?.toIntOrNull() ?: 3000,
                enableIntegrity = prefs().getBoolean(C.ENABLE_INTEGRITY, false) && streamProxy == null
            )
        } catch (e: Exception) {
            if (e.message == C.FAILED_INTEGRITY_CHECK) {
                integrity.emit("refreshStream")
            }
            null
        }
    }

    private suspend fun loadVideo(restorePauseState: Boolean = false) {
        videoId?.let { videoId ->
            val playbackPosition = if (prefs().getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true)) {
                videoId.toLongOrNull()?.let { xtraModule.playerRepository.getVideoPosition(it)?.position }
            } else {
                null
            } ?: savedPosition ?: 0
            if (qualities.isNullOrEmpty()) {
                val result = try {
                    xtraModule.playerRepository.loadVideoPlaylistUrl(
                        networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                        gqlHeaders = TwitchApiHelper.getGQLHeaders(this@ExoPlayerService, prefs().getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)),
                        videoId = videoId,
                        supportedCodecs = prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                        enableIntegrity = prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                    )
                } catch (e: Exception) {
                    if (e.message == C.FAILED_INTEGRITY_CHECK) {
                        integrity.emit("refreshVideo")
                    }
                    null
                }
                if (result != null) {
                    playlistUrl = result.first
                    backupQualities = result.second
                }
            }
            val url = if (skipAccessToken) {
                quality?.url
            } else {
                playlistUrl
            }
            if (url != null) {
                player?.let { player ->
                    val networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP)
                    player.setMediaSource(
                        HlsMediaSource.Factory(
                            DefaultDataSource.Factory(
                                this@ExoPlayerService,
                                StreamDataSources.factory(application, xtraModule, networkLibrary, 0)
                            )
                        ).apply {
                            setPlaylistParserFactory(CustomHlsPlaylistParserFactory())
                        }.createMediaSource(
                            MediaItem.fromUri(url)
                        )
                    )
                    player.volume = prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
                    player.setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                    player.prepare()
                    player.playWhenReady = !restorePauseState || !paused
                    player.seekTo(playbackPosition)
                }
            }
        }
    }

    private suspend fun updateVideoInfo() {
        val video = try {
            val response = xtraModule.graphQLRepository.loadQueryVideo(
                networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                headers = TwitchApiHelper.getGQLHeaders(this),
                id = videoId
            )
            if (prefs().getBoolean(C.ENABLE_INTEGRITY, false)) {
                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                    integrity.emit("refresh")
                    return
                }
            }
            response.data!!.let { item ->
                item.video?.let {
                    Video(
                        id = videoId,
                        channelId = it.owner?.id,
                        channelLogin = it.owner?.login,
                        channelName = it.owner?.displayName,
                        channelImageURL = it.owner?.profileImageURL,
                        gameId = it.game?.id,
                        gameSlug = it.game?.slug,
                        gameName = it.game?.displayName,
                        title = it.title,
                        thumbnailURL = it.previewThumbnailURL,
                        createdAt = it.createdAt?.toString(),
                        durationSeconds = it.lengthSeconds,
                        type = it.broadcastType?.toString(),
                        animatedPreviewURL = it.animatedPreviewURL,
                    )
                }
            }
        } catch (e: Exception) {
            val helixHeaders = TwitchApiHelper.getHelixHeaders(this)
            if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                try {
                    xtraModule.helixRepository.getVideos(
                        networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                        headers = helixHeaders,
                        ids = videoId?.let { listOf(it) }
                    ).data.firstOrNull()?.let {
                        Video(
                            id = it.id,
                            channelId = it.channelId,
                            channelLogin = it.channelLogin,
                            channelName = it.channelName,
                            title = it.title,
                            thumbnailURL = it.thumbnailURL,
                            createdAt = it.createdAt,
                            viewCount = it.viewCount,
                            durationSeconds = it.duration?.let { duration -> TwitchApiHelper.getDuration(duration) },
                        )
                    }
                } catch (e: Exception) {
                    null
                }
            } else null
        }
        if (video != null) {
            channelId = video.channelId
            channelLogin = video.channelLogin
            channelName = video.channelName
            channelImage = video.channelImage
            gameId = video.gameId
            gameSlug = video.gameSlug
            gameName = video.gameName
            title = video.title
            thumbnail = video.thumbnail
            createdAt = video.createdAt
            durationSeconds = video.durationSeconds
            videoType = video.type
            videoAnimatedPreviewURL = video.animatedPreviewURL
            updateMetadata()
            updateNotification()
            serviceListener?.updateVideoInfo()
        }
    }

    private suspend fun loadClip(restorePauseState: Boolean = false) {
        clipId?.let { clipId ->
            val networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP)
            if (qualities.isNullOrEmpty()) {
                val list = try {
                    xtraModule.playerRepository.loadClipQualities(
                        networkLibrary = networkLibrary,
                        gqlHeaders = TwitchApiHelper.getGQLHeaders(this@ExoPlayerService),
                        clipId = clipId,
                        supportedCodecs = prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                        enableIntegrity = prefs().getBoolean(C.ENABLE_INTEGRITY, false)
                    )
                } catch (e: Exception) {
                    if (e.message == C.FAILED_INTEGRITY_CHECK) {
                        integrity.emit("refreshClip")
                    }
                    null
                }
                if (list != null) {
                    val supportedCodecs = prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264")?.split(',') ?: emptyList()
                    val filtered = list.filterNot {
                        it.codecs?.substringBefore('.').let { codec ->
                            (codec == "av01" && !supportedCodecs.contains("av1")) || ((codec == "hev1" || codec == "hvc1") && !supportedCodecs.contains("h265"))
                        }
                    }
                    qualities = filtered
                        .sortedByDescending { it.bitrate }
                        .sortedByDescending { it.frameRate }
                        .sortedByDescending { it.resolution }
                        .toMutableList().apply {
                            add(VideoQuality(VideoQuality.AUDIO_ONLY_QUALITY))
                        }
                    setDefaultQuality()
                }
            }
            serviceListener?.changePlayerMode()
            val url = quality?.url ?: qualities?.firstOrNull()?.url
            if (url != null) {
                player?.let { player ->
                    if (quality?.name == VideoQuality.AUDIO_ONLY_QUALITY) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                            setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                        }.build()
                    }
                    player.setMediaSource(
                        ProgressiveMediaSource.Factory(
                            DefaultDataSource.Factory(
                                this@ExoPlayerService,
                                StreamDataSources.factory(application, xtraModule, networkLibrary, 0)
                            )
                        ).createMediaSource(
                            MediaItem.fromUri(url)
                        )
                    )
                    player.volume = prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
                    player.setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                    player.prepare()
                    player.playWhenReady = !restorePauseState || !paused
                    player.seekTo(savedPosition ?: 0)
                }
            }
        }
    }

    fun retry(item: String) {
        when (item) {
            "refreshStream" -> {
                lifecycleScope.launch {
                    loadStream()
                }
            }
            "refreshVideo" -> {
                lifecycleScope.launch {
                    loadVideo()
                }
            }
            "refreshClip" -> {
                lifecycleScope.launch {
                    loadClip()
                }
            }
        }
    }

    fun changeQuality(selectedQuality: VideoQuality?) {
        previousQuality = quality
        quality = selectedQuality
        quality?.let { quality ->
            player?.let { player ->
                player.currentMediaItem?.let { mediaItem ->
                    when (quality.name) {
                        VideoQuality.AUTO_QUALITY -> {
                            if (restorePlaylist) {
                                restorePlaylist = false
                                playlistUrl?.let { uri ->
                                    if (mediaItem.localConfiguration?.uri != uri.toUri()) {
                                        ignorePlaylistUpdate = true
                                        val position = player.currentPosition
                                        player.setMediaItem(mediaItem.buildUpon().setUri(uri).build())
                                        player.prepare()
                                        player.seekTo(position)
                                    }
                                }
                            } else {
                                player.prepare()
                            }
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
                            }.build()
                        }
                        VideoQuality.AUDIO_ONLY_QUALITY -> {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                            }.build()
                            quality.url?.let {
                                val position = player.currentPosition
                                if (qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null) {
                                    restorePlaylist = true
                                    ignorePlaylistUpdate = true
                                }
                                player.setMediaItem(mediaItem.buildUpon().setUri(it).build())
                                player.prepare()
                                player.seekTo(position)
                            }
                        }
                        VideoQuality.CHAT_ONLY_QUALITY -> {
                            player.stop()
                        }
                        else -> {
                            if (qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null) {
                                if (restorePlaylist) {
                                    restorePlaylist = false
                                    playlistUrl?.let { uri ->
                                        ignorePlaylistUpdate = true
                                        val position = player.currentPosition
                                        player.setMediaItem(mediaItem.buildUpon().setUri(uri).build())
                                        player.prepare()
                                        player.seekTo(position)
                                    }
                                } else {
                                    player.prepare()
                                }
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                    if (!player.currentTracks.isEmpty) {
                                        player.currentTracks.groups.find { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }?.let { trackGroup ->
                                            if (trackGroup.mediaTrackGroup.length > 0) {
                                                if (quality.resolution != null) {
                                                    val formats = mutableListOf<Pair<Int, Format>>()
                                                    for (i in 0 until trackGroup.mediaTrackGroup.length) {
                                                        formats.add(i to trackGroup.mediaTrackGroup.getFormat(i))
                                                    }
                                                    val list = formats
                                                        .sortedByDescending { it.second.bitrate }
                                                        .sortedByDescending { it.second.frameRate }
                                                        .sortedByDescending { it.second.height }
                                                    list.find {
                                                        (quality.resolution == it.second.height
                                                                && (quality.frameRate?.let { fps -> floor(fps) } ?: 30f) >= floor(it.second.frameRate)
                                                                && (quality.bitrate == null || quality.bitrate >= it.second.bitrate))
                                                                || quality.resolution > it.second.height
                                                                || it == list.last()
                                                    }?.first?.let { index ->
                                                        setOverrideForType(TrackSelectionOverride(trackGroup.mediaTrackGroup, index))
                                                    }
                                                } else {
                                                    setOverrideForType(TrackSelectionOverride(trackGroup.mediaTrackGroup, 0))
                                                }
                                            }
                                        }
                                    }
                                }.build()
                            } else {
                                player.currentMediaItem?.let {
                                    if (it.localConfiguration?.uri?.toString() != quality.url) {
                                        val position = player.currentPosition
                                        player.setMediaItem(it.buildUpon().setUri(quality.url).build())
                                        player.prepare()
                                        player.seekTo(position)
                                    }
                                }
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                }.build()
                            }
                        }
                    }
                    val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                    val cellular = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                    if ((!cellular && prefs().getString(C.PLAYER_DEFAULT_QUALITY, "saved") == "saved") || (cellular && prefs().getString(C.PLAYER_DEFAULT_CELLULAR_QUALITY, "saved") == "saved")) {
                        prefs().edit { putString(C.PLAYER_QUALITY, quality.name) }
                    }
                }
            }
        }
        // Tracks change at every ad boundary and onTracksChanged calls this, which re-enables video.
        // Keep the primary player's video off while an ad is being hidden or replaced.
        if (type == STREAM && adController?.isHidingPrimaryVideo == true) {
            hidePrimaryVideo()
        }
    }

    fun toggleSubtitles(enabled: Boolean) {
        player?.let { player ->
            if (enabled) {
                player.currentTracks.groups.find { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }?.let {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0))
                        .build()
                }
            } else {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                    .build()
            }
        }
    }

    /** The emulator's goldfish decoders fail on Twitch streams, prefer the software ones there. No effect on real devices. */
    private fun renderersFactory() = DefaultRenderersFactory(this).setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        decoders.filterNot { it.name.startsWith("c2.goldfish.") }.ifEmpty { decoders }
    }

    private fun buildPlayer() = ExoPlayer.Builder(this).apply {
        setRenderersFactory(renderersFactory())
        setLoadControl(
            DefaultLoadControl.Builder().apply {
                setBufferDurationsMs(
                    prefs().getString(C.PLAYER_BUFFER_MIN, "15000")?.toIntOrNull() ?: 15000,
                    prefs().getString(C.PLAYER_BUFFER_MAX, "50000")?.toIntOrNull() ?: 50000,
                    prefs().getString(C.PLAYER_BUFFER_PLAYBACK, "2000")?.toIntOrNull() ?: 2000,
                    prefs().getString(C.PLAYER_BUFFER_REBUFFER, "2000")?.toIntOrNull() ?: 2000
                )
            }.build()
        )
        setAudioAttributes(AudioAttributes.DEFAULT, prefs().getBoolean(C.PLAYER_AUDIO_FOCUS, false))
        setHandleAudioBecomingNoisy(prefs().getBoolean(C.PLAYER_HANDLE_AUDIO_BECOMING_NOISY, true))
        setSeekBackIncrementMs((prefs().getString(C.PLAYER_REWIND, "10")?.toLongOrNull() ?: 10) * 1000)
        setSeekForwardIncrementMs((prefs().getString(C.PLAYER_FORWARD, "10")?.toLongOrNull() ?: 10) * 1000)
    }.build()

    /** Ad handling for live streams, see [AdController]. */
    private fun handleStreamAds(playlist: HlsMediaPlaylist?) {
        val controller = adController ?: return
        val position = player?.currentPosition ?: 0
        val adPlaylist = playlist?.let { AdPlaylist.from(it) }
        val ads = AdDetector.hasAds(adPlaylist, position)
        if (ads != controller.playingAds) {
            AdLog.i("ads=$ads pos=$position segments=${adPlaylist?.segments?.size} last=${adPlaylist?.segments?.lastOrNull()?.title} adIn=${AdDetector.adStartDelayMs(adPlaylist, position)}ms state=${controller.state}")
        }
        if (ads) {
            adEndWallMs = adPlaylist?.dateRanges?.filter { it.className == "twitch-stitched-ad" || it.id.startsWith("stitched-ad-") }
                ?.mapNotNull { it.endDateUs }?.maxOrNull()?.div(1000)
        }
        applyAdActions(controller.onPrimaryPlaylist(ads, AdDetector.adStartDelayMs(adPlaylist, position), AdDetector.adEndDelayMs(adPlaylist, position)))
        updateAdStatus()
    }

    /**
     * The corner status that replaces the old toasts: "clean stream" for a few seconds after a
     * switch, "back in about N s" while the ad is hidden or filtered out.
     */
    private fun updateAdStatus() {
        val controller = adController
        val now = System.currentTimeMillis()
        if (controller?.isCovered == true) {
            if (coveredSinceMs == null) {
                coveredSinceMs = now
            }
        } else {
            coveredSinceMs = null
        }
        val coveredSince = coveredSinceMs
        val text = when {
            controller == null -> null
            coveredSince != null -> if (now - coveredSince < 3000) getString(R.string.ad_status_covered) else null
            controller.isHidingPrimaryVideo || controller.playingAds && adConfig?.filterAdSegments == true && player?.playbackState == Player.STATE_BUFFERING -> {
                val seconds = adEndWallMs?.let { (it - now) / 1000 }?.takeIf { it > 0 }
                if (seconds != null) getString(R.string.ad_status_waiting, seconds) else getString(R.string.ad_status_waiting_unknown)
            }
            else -> null
        }
        serviceListener?.adStatus(text)
        if (text != null || controller?.isActive == true) {
            if (adStatusJob?.isActive != true) {
                adStatusJob = lifecycleScope.launch {
                    delay(1000)
                    adStatusJob = null
                    updateAdStatus()
                }
            }
        }
    }

    private fun applyAdActions(actions: List<AdController.Action>) {
        actions.forEach { action ->
            when (action) {
                is AdController.Action.StartSecondary -> startSecondaryPlayer(action.source, action.show)
                AdController.Action.ShowSecondary -> showSecondaryPlayer()
                AdController.Action.StopSecondary -> stopSecondaryPlayer()
                AdController.Action.MutePrimary -> player?.volume = 0f
                AdController.Action.HidePrimary -> hidePrimaryVideo()
                is AdController.Action.RestorePrimary -> restorePrimaryPlayer(action.videoHidden)
                is AdController.Action.ScheduleBoundary -> {
                    adBoundaryJob?.cancel()
                    adBoundaryJob = lifecycleScope.launch {
                        delay(action.delayMs.milliseconds)
                        adBoundaryJob = null
                        AdLog.i("ad boundary, secondary ready=${pendingSecondaryPlayer?.playbackState == Player.STATE_READY}")
                        adController?.let { applyAdActions(it.onAdBoundary()) }
                    }
                }
                AdController.Action.CancelBoundary -> {
                    adBoundaryJob?.cancel()
                    adBoundaryJob = null
                }
                is AdController.Action.ScheduleRestore -> {
                    adRestoreJob?.cancel()
                    adRestoreJob = lifecycleScope.launch {
                        delay(action.delayMs.milliseconds)
                        adRestoreJob = null
                        adController?.let { applyAdActions(it.onRestoreDelayElapsed()) }
                        AdLog.i("restored primary player")
                    }
                }
                AdController.Action.CancelRestore -> {
                    adRestoreJob?.cancel()
                    adRestoreJob = null
                }
                // shown as the corner status (updateAdStatus) instead of toasts
                is AdController.Action.Notice -> AdLog.i("notice ${action.notice}")
            }
        }
    }

    private fun hidePrimaryVideo() {
        player?.let { player ->
            if (quality?.name != VideoQuality.AUDIO_ONLY_QUALITY) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                }.build()
            }
            player.volume = 0f
        }
    }

    private fun restorePrimaryPlayer(videoHidden: Boolean) {
        player?.let { player ->
            val quality = quality
            if (videoHidden) {
                if (quality != null && quality.name != VideoQuality.AUDIO_ONLY_QUALITY && quality.name != VideoQuality.CHAT_ONLY_QUALITY
                    && qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null && !player.currentTracks.isEmpty
                ) {
                    // the track group changed during the ad, so apply the selected quality again
                    val previous = previousQuality
                    changeQuality(quality)
                    previousQuality = previous
                } else if (quality?.name != VideoQuality.AUDIO_ONLY_QUALITY) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                        setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                    }.build()
                }
            } else if (quality != null && quality.name != VideoQuality.AUDIO_ONLY_QUALITY && quality.name != VideoQuality.CHAT_ONLY_QUALITY
                && qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null && !player.currentTracks.isEmpty
            ) {
                // tracks changed at the ad boundaries while changeQuality was skipped
                val previous = previousQuality
                changeQuality(quality)
                previousQuality = previous
            }
            player.volume = prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
        }
    }

    private fun isCurrentAdSource(source: AdSource) = (adController?.state as? AdController.State.Covering)?.source == source

    private fun isSecondary(player: ExoPlayer) = player === secondaryPlayer || player === pendingSecondaryPlayer

    /**
     * Starts the secondary player for [source], replacing a running one. Off screen and silent
     * until [showSecondaryPlayer] unless [show].
     */
    private fun startSecondaryPlayer(source: AdSource, show: Boolean) {
        stopSecondaryPlayer(keepSurface = show)
        lifecycleScope.launch {
            val networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP)
            val url = when (source) {
                // the primary's media playlist, requested through the proxy
                is AdSource.StreamProxy -> quality?.url ?: qualities?.firstOrNull {
                    it.url != null && it.name != VideoQuality.AUDIO_ONLY_QUALITY
                }?.url
                // anonymous: only the primary stream's token request carries the login
                is AdSource.VideoSwap -> channelLogin?.let { channelLogin ->
                    AdSourceResolver(this@ExoPlayerService, xtraModule, networkLibrary).resolveVideoSwap(
                        channelLogin = channelLogin,
                        source = source,
                        gqlHeaders = TwitchApiHelper.getGQLHeaders(this@ExoPlayerService, false),
                        supportedCodecs = prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                        // the same quality as the primary; auto and source get the best one
                        maxHeight = quality?.takeIf { it.name != VideoQuality.AUTO_QUALITY && it.name != VideoQuality.SOURCE_QUALITY }?.resolution,
                    )
                }
            }
            if (!isCurrentAdSource(source)) {
                return@launch
            }
            if (url == null) {
                AdLog.w("${source.label}: no stream")
                adController?.let { applyAdActions(it.onSecondaryFailed()) }
                return@launch
            }
            val player = buildPlayer()
            player.addListener(object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    if (!isSecondary(player)) return
                    val playlist = (player.currentManifest as? HlsManifest)?.mediaPlaylist?.let { AdPlaylist.from(it) }
                    if (AdDetector.hasAds(playlist, player.currentPosition)) {
                        AdLog.i("${source.label}: secondary player has ads")
                        adController?.let { applyAdActions(it.onSecondaryAds()) }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!isSecondary(player)) return
                    AdLog.w("${source.label}: secondary player error", error)
                    adController?.let { applyAdActions(it.onSecondaryFailed()) }
                }
            })
            val proxy = (source as? AdSource.StreamProxy)?.let {
                StreamDataSources.HttpProxy(it.host, it.port, it.username, it.password)
            }
            player.setMediaSource(
                HlsMediaSource.Factory(
                    DefaultDataSource.Factory(
                        this@ExoPlayerService,
                        StreamDataSources.factory(
                            application = application,
                            xtraModule = xtraModule,
                            networkLibrary = networkLibrary,
                            proxyTimeoutMs = adConfig?.proxyTimeoutMs ?: 3000,
                            proxy = proxy,
                            proxyTarget = StreamDataSources.ProxyTarget.MEDIA_PLAYLIST,
                            useMediaPlaylistProxy = { adController?.isCoveringWithProxy == true },
                        )
                    )
                ).apply {
                    setPlaylistParserFactory(CustomHlsPlaylistParserFactory(filterAds = adConfig?.filterAdSegments == true))
                    setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(if (proxy != null) 1 else 6))
                }.createMediaSource(
                    MediaItem.Builder().apply {
                        setUri(url.toUri())
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                        setLiveConfiguration(MediaItem.LiveConfiguration.Builder().apply {
                            prefs().getString(C.PLAYER_LIVE_MIN_SPEED, "")?.toFloatOrNull()?.let { setMinPlaybackSpeed(it) }
                            prefs().getString(C.PLAYER_LIVE_MAX_SPEED, "")?.toFloatOrNull()?.let { setMaxPlaybackSpeed(it) }
                            prefs().getString(C.PLAYER_LIVE_TARGET_OFFSET, "2000")?.toLongOrNull()?.let { setTargetOffsetMs(it) }
                        }.build())
                    }.build()
                )
            )
            player.volume = 0f
            player.setPlaybackSpeed(1f)
            player.prepare()
            player.playWhenReady = true
            pendingSecondaryPlayer = player
            AdLog.i("${source.label}: secondary player started")
            if (show || adController?.isCovered == true) {
                showSecondaryPlayer()
            }
        }
    }

    /**
     * Puts the secondary player on screen. When it is still resolving, the primary player only
     * loses the surface (the last content frame stays) and the secondary gets it once it starts.
     */
    private fun showSecondaryPlayer() {
        val player = pendingSecondaryPlayer
        if (player != null) {
            pendingSecondaryPlayer = null
            player.volume = prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
            secondaryPlayer = player
        }
        surfaceOnSecondary = true
        serviceListener?.setPlayerSurface(true)
        AdLog.i("secondary player shown, ready=${player?.playbackState == Player.STATE_READY}")
    }

    /**
     * @param keepSurface a replacement player follows right away: keep the primary off screen
     */
    private fun stopSecondaryPlayer(keepSurface: Boolean = false) {
        pendingSecondaryPlayer?.let {
            pendingSecondaryPlayer = null
            it.release()
        }
        val player = secondaryPlayer
        player?.playWhenReady = false
        if (surfaceOnSecondary && !keepSurface) {
            surfaceOnSecondary = false
            // the fragment moves the surface and its listener off secondaryPlayer, so clear it afterwards
            serviceListener?.setPlayerSurface(false)
        }
        secondaryPlayer = null
        player?.release()
    }

    fun getCurrentVolume() = secondaryPlayer?.volume ?: player?.volume

    fun togglePause() {
        if (secondaryPlayer != null) {
            Util.handlePlayPauseButtonAction(secondaryPlayer)
        }
        Util.handlePlayPauseButtonAction(player)
    }

    fun pause() {
        if (secondaryPlayer != null) {
            secondaryPlayer?.pause()
        }
        player?.pause()
    }

    fun stop() {
        if (secondaryPlayer != null) {
            secondaryPlayer?.stop()
        }
        player?.stop()
    }

    fun seekBack() {
        if (secondaryPlayer != null) {
            secondaryPlayer?.seekBack()
        }
        player?.seekBack()
    }

    fun seekForward() {
        if (secondaryPlayer != null) {
            secondaryPlayer?.seekForward()
        }
        player?.seekForward()
    }

    fun seekTo(position: Long) {
        if (secondaryPlayer != null) {
            secondaryPlayer?.seekTo(position)
        }
        player?.seekTo(position)
    }

    fun seekToDefaultPosition() {
        if (secondaryPlayer != null) {
            secondaryPlayer?.seekToDefaultPosition()
        }
        player?.seekToDefaultPosition()
    }

    fun changeVolume(volume: Float) {
        val secondaryPlayer = secondaryPlayer
        if (secondaryPlayer != null) {
            secondaryPlayer.volume = volume
        } else {
            player?.volume = volume
        }
    }

    fun getPlaylistTags(mediaPlaylist: Boolean): String? {
        return if (mediaPlaylist) {
            ((secondaryPlayer ?: player)?.currentManifest as? HlsManifest)?.mediaPlaylist?.tags?.toTypedArray()
        } else {
            val player = if (adController?.isCoveringWithVideoSwap == true && secondaryPlayer != null) {
                secondaryPlayer
            } else {
                player
            }
            (player?.currentManifest as? HlsManifest)?.multivariantPlaylist?.tags?.toTypedArray()
        }?.joinToString("\n")
    }

    fun restartPlayer() {
        if (quality?.name != VideoQuality.CHAT_ONLY_QUALITY) {
            lifecycleScope.launch {
                // a new stream session: its ads are detected from scratch
                adController?.let { applyAdActions(it.reset()) }
                stopSecondaryPlayer()
                loadStream(restart = true)
            }
        }
    }

    fun startAudioOnly() {
        player?.let { player ->
            if (quality?.name != VideoQuality.AUDIO_ONLY_QUALITY) {
                restoreQuality = true
                previousQuality = quality
                quality = qualities?.find { it.name == VideoQuality.AUDIO_ONLY_QUALITY }
                quality?.let { quality ->
                    player.currentMediaItem?.let { mediaItem ->
                        if (prefs().getBoolean(C.PLAYER_DISABLE_BACKGROUND_VIDEO, true)) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                            }.build()
                        }
                        if (prefs().getBoolean(C.PLAYER_USE_BACKGROUND_AUDIO_TRACK, false)) {
                            quality.url?.let { url ->
                                val position = player.currentPosition
                                if (qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null) {
                                    restorePlaylist = true
                                    ignorePlaylistUpdate = true
                                }
                                player.setMediaItem(mediaItem.buildUpon().setUri(url).build())
                                player.prepare()
                                player.seekTo(position)
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop(isInPIPMode: Boolean) {
        player?.let { player ->
            val isInteractive = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
            if ((!isInPIPMode && isInteractive && prefs().getBoolean(C.PLAYER_BACKGROUND_AUDIO, true))
                || (!isInPIPMode && !isInteractive && prefs().getBoolean(C.PLAYER_BACKGROUND_AUDIO_LOCKED, true))
                || (isInPIPMode && isInteractive && prefs().getBoolean(C.PLAYER_BACKGROUND_AUDIO_PIP_CLOSED, false))
                || (isInPIPMode && !isInteractive && prefs().getBoolean(C.PLAYER_BACKGROUND_AUDIO_PIP_LOCKED, true))) {
                if (player.playWhenReady && quality?.name != VideoQuality.AUDIO_ONLY_QUALITY) {
                    restoreQuality = true
                    previousQuality = quality
                    quality = qualities?.find { it.name == VideoQuality.AUDIO_ONLY_QUALITY }
                    quality?.let { quality ->
                        player.currentMediaItem?.let { mediaItem ->
                            if (prefs().getBoolean(C.PLAYER_DISABLE_BACKGROUND_VIDEO, true)) {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                                }.build()
                            }
                            if (prefs().getBoolean(C.PLAYER_USE_BACKGROUND_AUDIO_TRACK, false)) {
                                quality.url?.let { url ->
                                    val position = player.currentPosition
                                    if (qualities?.find { it.name == VideoQuality.AUTO_QUALITY } != null) {
                                        restorePlaylist = true
                                        ignorePlaylistUpdate = true
                                    }
                                    player.setMediaItem(mediaItem.buildUpon().setUri(url).build())
                                    player.prepare()
                                    player.seekTo(position)
                                }
                            }
                        }
                    }
                }
            } else {
                pause()
            }
        }
    }

    private fun updatePlaybackState() {
        (secondaryPlayer ?: player)?.let { player ->
            val showSeekbar = showStreamNotificationSeekbar || !player.isCurrentMediaItemLive
            session?.setPlaybackState(
                PlaybackState.Builder().apply {
                    setState(
                        when (player.playbackState) {
                            Player.STATE_IDLE -> PlaybackState.STATE_NONE
                            Player.STATE_BUFFERING -> {
                                if (Util.shouldShowPlayButton(player)) {
                                    PlaybackState.STATE_PAUSED
                                } else {
                                    PlaybackState.STATE_BUFFERING
                                }
                            }
                            Player.STATE_READY -> {
                                if (Util.shouldShowPlayButton(player)) {
                                    PlaybackState.STATE_PAUSED
                                } else {
                                    PlaybackState.STATE_PLAYING
                                }
                            }
                            Player.STATE_ENDED -> PlaybackState.STATE_STOPPED
                            else -> PlaybackState.STATE_NONE
                        },
                        if (showSeekbar) {
                            player.currentPosition
                        } else {
                            -1
                        },
                        if (player.isPlaying && showSeekbar) {
                            player.playbackParameters.speed
                        } else {
                            0f
                        }
                    )
                    setBufferedPosition(
                        if (showSeekbar) {
                            player.bufferedPosition
                        } else {
                            -1
                        }
                    )
                    setActions(
                        (PlaybackState.ACTION_STOP
                                or PlaybackState.ACTION_PAUSE
                                or PlaybackState.ACTION_PLAY
                                or PlaybackState.ACTION_REWIND
                                or PlaybackState.ACTION_FAST_FORWARD
                                or PlaybackState.ACTION_SET_RATING
                                or PlaybackState.ACTION_PLAY_PAUSE
                                or PlaybackState.ACTION_PREPARE).let {
                            if (showSeekbar) {
                                it or PlaybackState.ACTION_SEEK_TO
                            } else {
                                it
                            }.let {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    it or PlaybackState.ACTION_SET_PLAYBACK_SPEED
                                } else {
                                    it
                                }
                            }
                        }
                    )
                    addCustomAction(INTENT_REWIND, ContextCompat.getString(this@ExoPlayerService, R.string.rewind), androidx.media3.session.R.drawable.media3_icon_rewind)
                    addCustomAction(INTENT_FAST_FORWARD, ContextCompat.getString(this@ExoPlayerService, R.string.forward), androidx.media3.session.R.drawable.media3_icon_fast_forward)
                }.build()
            )
        }
    }

    private fun updateMetadata() {
        val url = channelImage
        val bitmap = if (!url.isNullOrBlank()) {
            if (url == artworkUri && cachedBitmap != null) {
                cachedBitmap
            } else {
                val networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP)
                artworkUri = url
                bitmapLoadJob?.cancel()
                bitmapLoadJob = lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val scheme = url.toUri().scheme
                        val response = if (scheme == "https" || scheme == "http") {
                            when {
                                networkLibrary == C.HTTP_ENGINE && xtraModule.httpEngine.value != null -> @SuppressLint("NewApi") {
                                    val response = suspendCancellableCoroutine { continuation ->
                                        val timeout = NetworkUtils.HttpEngineTimeout()
                                        val request = xtraModule.httpEngine.value!!.newUrlRequestBuilder(
                                            url,
                                            xtraModule.cronetExecutor.value,
                                            NetworkUtils.ByteArrayUrlCallback(continuation, timeout)
                                        ).build()
                                        timeout.start(request, continuation)
                                        request.start()
                                        continuation.invokeOnCancellation {
                                            request.cancel()
                                            timeout.stop()
                                        }
                                    }
                                    if (response.info.httpStatusCode in 200..299) {
                                        response.body
                                    } else null
                                }
                                networkLibrary == C.CRONET && xtraModule.cronetEngine.value != null -> {
                                    val response = suspendCancellableCoroutine { continuation ->
                                        val timeout = NetworkUtils.CronetTimeout()
                                        val request = xtraModule.cronetEngine.value!!.newUrlRequestBuilder(
                                            url,
                                            NetworkUtils.ByteArrayCronetCallback(continuation, timeout),
                                            xtraModule.cronetExecutor.value
                                        ).build()
                                        timeout.start(request, continuation)
                                        request.start()
                                        continuation.invokeOnCancellation {
                                            request.cancel()
                                            timeout.stop()
                                        }
                                    }
                                    if (response.info.httpStatusCode in 200..299) {
                                        response.body
                                    } else null
                                }
                                else -> {
                                    xtraModule.okHttpClient.value.newCall(Request.Builder().url(url).build()).executeAsync().use { response ->
                                        if (response.isSuccessful) {
                                            response.body.bytes()
                                        } else null
                                    }
                                }
                            }
                        } else {
                            FileInputStream(url).use {
                                it.readBytes()
                            }
                        }
                        if (response != null) {
                            val bitmap = BitmapFactory.decodeByteArray(response, 0, response.size)
                            if (bitmap != null) {
                                cachedBitmap = bitmap
                                withContext(Dispatchers.Main) {
                                    setMetadata(bitmap)
                                }
                            }
                        }
                    } catch (e: Exception) {

                    }
                }
                null
            }
        } else null
        setMetadata(bitmap)
    }

    private fun setMetadata(bitmap: Bitmap?) {
        (secondaryPlayer ?: player)?.let { player ->
            session?.setMetadata(
                MediaMetadata.Builder().apply {
                    putText(MediaMetadata.METADATA_KEY_TITLE, title)
                    putText(MediaMetadata.METADATA_KEY_ARTIST, channelName)
                    if (bitmap != null) {
                        putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, bitmap)
                        putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                    }
                    putLong(
                        MediaMetadata.METADATA_KEY_DURATION,
                        if (showStreamNotificationSeekbar || !player.isCurrentMediaItemLive) {
                            player.duration
                        } else {
                            -1
                        }
                    )
                }.build()
            )
        }
    }

    private fun updateNotification() {
        val url = channelImage
        val bitmap = if (!url.isNullOrBlank()) {
            if (url == artworkUri && cachedBitmap != null) {
                cachedBitmap
            } else {
                val networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP)
                artworkUri = url
                bitmapLoadJob?.cancel()
                bitmapLoadJob = lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val scheme = url.toUri().scheme
                        val response = if (scheme == "https" || scheme == "http") {
                            when {
                                networkLibrary == C.HTTP_ENGINE && xtraModule.httpEngine.value != null -> @SuppressLint("NewApi") {
                                    val response = suspendCancellableCoroutine { continuation ->
                                        val timeout = NetworkUtils.HttpEngineTimeout()
                                        val request = xtraModule.httpEngine.value!!.newUrlRequestBuilder(
                                            url,
                                            xtraModule.cronetExecutor.value,
                                            NetworkUtils.ByteArrayUrlCallback(continuation, timeout)
                                        ).build()
                                        timeout.start(request, continuation)
                                        request.start()
                                        continuation.invokeOnCancellation {
                                            request.cancel()
                                            timeout.stop()
                                        }
                                    }
                                    if (response.info.httpStatusCode in 200..299) {
                                        response.body
                                    } else null
                                }
                                networkLibrary == C.CRONET && xtraModule.cronetEngine.value != null -> {
                                    val response = suspendCancellableCoroutine { continuation ->
                                        val timeout = NetworkUtils.CronetTimeout()
                                        val request = xtraModule.cronetEngine.value!!.newUrlRequestBuilder(
                                            url,
                                            NetworkUtils.ByteArrayCronetCallback(continuation, timeout),
                                            xtraModule.cronetExecutor.value
                                        ).build()
                                        timeout.start(request, continuation)
                                        request.start()
                                        continuation.invokeOnCancellation {
                                            request.cancel()
                                            timeout.stop()
                                        }
                                    }
                                    if (response.info.httpStatusCode in 200..299) {
                                        response.body
                                    } else null
                                }
                                else -> {
                                    xtraModule.okHttpClient.value.newCall(Request.Builder().url(url).build()).executeAsync().use { response ->
                                        if (response.isSuccessful) {
                                            response.body.bytes()
                                        } else null
                                    }
                                }
                            }
                        } else {
                            FileInputStream(url).use {
                                it.readBytes()
                            }
                        }
                        if (response != null) {
                            val bitmap = BitmapFactory.decodeByteArray(response, 0, response.size)
                            if (bitmap != null) {
                                cachedBitmap = bitmap
                                withContext(Dispatchers.Main) {
                                    sendNotification(bitmap)
                                }
                            }
                        }
                    } catch (e: Exception) {

                    }
                }
                null
            }
        } else null
        sendNotification(bitmap)
    }

    private fun sendNotification(bitmap: Bitmap?) {
        (secondaryPlayer ?: player)?.let { player ->
            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, getString(R.string.notification_playback_channel_id))
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }.apply {
                setContentTitle(title)
                setContentText(channelName)
                setSmallIcon(R.drawable.notification_icon)
                if (bitmap != null) {
                    setLargeIcon(bitmap)
                }
                setGroup(GROUP_KEY)
                setVisibility(Notification.VISIBILITY_PUBLIC)
                setOngoing(false)
                setOnlyAlertOnce(true)
                if (player.isPlaying && player.playbackParameters.speed == 1f) {
                    setWhen(System.currentTimeMillis() - player.currentPosition)
                    setShowWhen(true)
                    setUsesChronometer(true)
                }
                setStyle(
                    Notification.MediaStyle()
                        .setMediaSession(session?.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                setContentIntent(
                    PendingIntent.getActivity(
                        this@ExoPlayerService,
                        REQUEST_CODE_RESUME,
                        Intent(this@ExoPlayerService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                            action = MainActivity.INTENT_OPEN_PLAYER
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this@ExoPlayerService, androidx.media3.session.R.drawable.media3_icon_rewind),
                        ContextCompat.getString(this@ExoPlayerService, R.string.rewind),
                        PendingIntent.getService(
                            this@ExoPlayerService,
                            REQUEST_CODE_REWIND,
                            Intent(this@ExoPlayerService, ExoPlayerService::class.java).apply {
                                action = INTENT_REWIND
                            },
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    ).build()
                )
                if (Util.shouldShowPlayButton(player)) {
                    addAction(
                        Notification.Action.Builder(
                            Icon.createWithResource(this@ExoPlayerService, androidx.media3.session.R.drawable.media3_icon_play),
                            ContextCompat.getString(this@ExoPlayerService, R.string.resume),
                            PendingIntent.getService(
                                this@ExoPlayerService,
                                REQUEST_CODE_PLAY_PAUSE,
                                Intent(this@ExoPlayerService, ExoPlayerService::class.java).apply {
                                    action = INTENT_PLAY_PAUSE
                                },
                                PendingIntent.FLAG_IMMUTABLE
                            )
                        ).build()
                    )
                } else {
                    addAction(
                        Notification.Action.Builder(
                            Icon.createWithResource(this@ExoPlayerService, androidx.media3.session.R.drawable.media3_icon_pause),
                            ContextCompat.getString(this@ExoPlayerService, R.string.pause),
                            PendingIntent.getService(
                                this@ExoPlayerService,
                                REQUEST_CODE_PLAY_PAUSE,
                                Intent(this@ExoPlayerService, ExoPlayerService::class.java).apply {
                                    action = INTENT_PLAY_PAUSE
                                },
                                PendingIntent.FLAG_IMMUTABLE
                            )
                        ).build()
                    )
                }
                addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this@ExoPlayerService, androidx.media3.session.R.drawable.media3_icon_fast_forward),
                        ContextCompat.getString(this@ExoPlayerService, R.string.forward),
                        PendingIntent.getService(
                            this@ExoPlayerService,
                            REQUEST_CODE_FAST_FORWARD,
                            Intent(this@ExoPlayerService, ExoPlayerService::class.java).apply {
                                action = INTENT_FAST_FORWARD
                            },
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    ).build()
                )
            }.build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    fun setSleepTimer(duration: Long): Long {
        val endTime = sleepTimerEndTime
        sleepTimer?.cancel()
        sleepTimerEndTime = 0L
        if (duration > 0L) {
            sleepTimer = Timer().apply {
                schedule(duration) {
                    Handler(Looper.getMainLooper()).post {
                        savePosition()
                        player?.clearMediaItems()
                        player?.playWhenReady = false
                        stopSelf()
                    }
                }
            }
            sleepTimerEndTime = System.currentTimeMillis() + duration
        }
        return endTime
    }

    fun setStopServiceTimer(start: Boolean) {
        if (start) {
            if (stopServiceTimer == null && player?.isPlaying == false) {
                stopServiceTimer = Timer().apply {
                    schedule(600000) {
                        Handler(Looper.getMainLooper()).post {
                            stopSelf()
                        }
                    }
                }
            }
        } else {
            stopServiceTimer?.cancel()
            stopServiceTimer = null
        }
    }

    fun toggleDynamicsProcessing(): Boolean {
        if (dynamicsProcessing?.enabled == true) {
            dynamicsProcessing?.enabled = false
        } else {
            if (dynamicsProcessing == null) {
                player?.audioSessionId?.let { reinitializeDynamicsProcessing(it) }
            } else {
                dynamicsProcessing?.enabled = true
            }
        }
        val enabled = dynamicsProcessing?.enabled == true
        prefs().edit { putBoolean(C.PLAYER_AUDIO_COMPRESSOR, enabled) }
        return enabled
    }

    private fun reinitializeDynamicsProcessing(audioSessionId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamicsProcessing = DynamicsProcessing(0, audioSessionId, null).apply {
                for (channelIdx in 0 until channelCount) {
                    for (bandIdx in 0 until getMbcByChannelIndex(channelIdx).bandCount) {
                        setMbcBandByChannelIndex(
                            channelIdx,
                            bandIdx,
                            getMbcBandByChannelIndex(channelIdx, bandIdx).apply {
                                attackTime = 0f
                                releaseTime = 0.25f
                                ratio = 1.6f
                                threshold = -50f
                                kneeWidth = 40f
                                preGain = 0f
                                postGain = 10f
                            }
                        )
                    }
                }
                enabled = true
            }
        }
    }

    private fun savePosition() {
        player?.let { player ->
            if (!player.currentTracks.isEmpty) {
                if (prefs().getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true)) {
                    when (type) {
                        VIDEO -> {
                            videoId?.toLongOrNull()?.let {
                                runBlocking {
                                    xtraModule.playerRepository.saveVideoPosition(VideoPosition(it, player.currentPosition))
                                }
                            }
                        }
                        OFFLINE_VIDEO -> {
                            offlineVideoId?.let {
                                runBlocking {
                                    xtraModule.offlineVideosRepository.updatePosition(it, player.currentPosition)
                                }
                            }
                        }
                    }
                }
                runBlocking {
                    xtraModule.playerRepository.deletePlaybackStates()
                }
            }
        }
    }

    private fun updateSavedPosition() {
        player?.let { player ->
            if (!player.currentTracks.isEmpty) {
                val currentPosition = player.currentPosition
                val savedPosition = lastSavedPosition
                if (savedPosition == null || currentPosition - savedPosition !in 0..2000) {
                    lastSavedPosition = currentPosition
                    if (prefs().getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true)) {
                        when (type) {
                            VIDEO -> {
                                videoId?.toLongOrNull()?.let {
                                    runBlocking {
                                        xtraModule.playerRepository.saveVideoPosition(VideoPosition(it, currentPosition))
                                    }
                                }
                            }
                            OFFLINE_VIDEO -> {
                                offlineVideoId?.let {
                                    runBlocking {
                                        xtraModule.offlineVideosRepository.updatePosition(it, currentPosition)
                                    }
                                }
                            }
                        }
                    }
                    runBlocking {
                        savePlaybackState(currentPosition, !player.playWhenReady)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            INTENT_REWIND -> seekBack()
            INTENT_PLAY_PAUSE -> togglePause()
            INTENT_FAST_FORWARD -> seekForward()
            INTENT_START -> create(restorePauseState = true)
            Intent.ACTION_MEDIA_BUTTON -> create(restorePauseState = false)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return ServiceBinder()
    }

    inner class ServiceBinder : Binder() {
        fun getService() = this@ExoPlayerService
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePosition()
        player?.playWhenReady = false
        secondaryPlayer?.playWhenReady = false
        pendingSecondaryPlayer?.playWhenReady = false
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        secondaryPlayer?.release()
        pendingSecondaryPlayer?.release()
        session?.release()
        bitmapLoadJob?.cancel()
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    /** @param filterAds live streams only: ad segments become gaps, see [com.github.andreyasadchy.xtra.player.ads.AdSegmentFilter] */
    class CustomHlsPlaylistParserFactory(private val filterAds: Boolean = false) : HlsPlaylistParserFactory {
        override fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist> {
            return HlsPlaylistParser(filterAds)
        }

        override fun createPlaylistParser(multivariantPlaylist: HlsMultivariantPlaylist, previousMediaPlaylist: HlsMediaPlaylist?): ParsingLoadable.Parser<HlsPlaylist> {
            return HlsPlaylistParser(multivariantPlaylist, previousMediaPlaylist, filterAds)
        }
    }

    companion object {
        const val MULTIVARIANT_PLAYLIST_REGEX = "^usher\\.ttvnw\\.net$"
        const val MEDIA_PLAYLIST_REGEX = "^(?:[a-z0-9-]+\\.playlist\\.(?:live-video|ttvnw)\\.net|video-weaver\\.[a-z0-9-]+\\.hls\\.ttvnw\\.net)$"

        private const val NOTIFICATION_ID = 1001
        private const val GROUP_KEY = "com.github.andreyasadchy.xtra.PLAYBACK_NOTIFICATIONS"

        private const val REQUEST_CODE_RESUME = 0
        private const val REQUEST_CODE_REWIND = 1
        private const val REQUEST_CODE_PLAY_PAUSE = 2
        private const val REQUEST_CODE_FAST_FORWARD = 3

        private const val INTENT_REWIND = "com.github.andreyasadchy.xtra.REWIND"
        private const val INTENT_PLAY_PAUSE = "com.github.andreyasadchy.xtra.PLAY_PAUSE"
        private const val INTENT_FAST_FORWARD = "com.github.andreyasadchy.xtra.FAST_FORWARD"
        const val INTENT_START = "com.github.andreyasadchy.xtra.START_PLAYBACK_SERVICE"
    }
}