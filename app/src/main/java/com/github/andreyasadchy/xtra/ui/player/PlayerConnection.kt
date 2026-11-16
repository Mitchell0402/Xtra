package com.github.andreyasadchy.xtra.ui.player

import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.github.andreyasadchy.xtra.model.VideoQuality

/**
 * The layer between the player page and the playback service (plan 8, U6 contract; it counts as ad-block code under
 * red line 1). It carries over v0.1's ExoPlayerFragment wiring unchanged in behaviour:
 *
 * - the one video surface sits on the secondary player while the ad blocker has swapped to it, else on the main one,
 *   and the page's player listener moves with it ([ExoPlayerService.Listener.setPlayerSurface]);
 * - the ad status shows in the corner while the service reports one ([ExoPlayerService.Listener.adStatus]);
 * - when the service restarts with [PlaybackBackend.restoreQuality] set (after audio only in the background, or
 *   after an ad break), the previous quality comes back;
 * - the other five service callbacks and the player's events become [PlayerUiState] fields or [Host] calls.
 *
 * The contract tests (PlayerContractTest) drive it with a fake service and fake players.
 */
@OptIn(UnstableApi::class)
class PlayerConnection(private val state: PlayerUiState, private val host: Host) {

    /** What the page does with events that are not plain state. */
    interface Host {
        val surface: SurfaceView
        /** The page's view still exists (v0.1 guarded every callback with view != null). */
        val isViewAlive: Boolean
        /** v0.1: started once the network check let the page initialize (or when it is off). */
        val canStart: Boolean
        /** The first start: load stream info, games, chat. */
        fun onFirstStart()
        /** A later start: the replay chat reloads. */
        fun onLaterStart()
        /** The qualities are known. */
        fun onLoaded()
        fun changePlayerMode()
        fun toast(resId: Int, duration: Int)
        fun toast(text: CharSequence, duration: Int)
        /** The viewer moved along the time line: the replay chat follows. */
        fun onSeek(positionMs: Long)
        fun onSpeedChanged(speed: Float)
        fun onTracksLoaded()
        fun onCues(cues: List<Cue>)
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onPlayButtonChanged(showPlayButton: Boolean, playbackState: Int)
        fun qualityLabel(quality: VideoQuality?): String?
    }

    var backend: PlaybackBackend? = null
        private set
    /** True once [Host.onFirstStart] ran for this page. */
    var started = false
    private var playerListener: Player.Listener? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlayButton()
            updateProgress()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updatePlayButton()
            updateProgress()
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            updatePlayButton()
            updateDuration()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize != VideoSize.UNKNOWN && backend?.player?.let { it.playbackState != Player.STATE_IDLE } == true) {
                state.videoAspect = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            host.onCues(cueGroup.cues)
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            updateDuration()
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                host.onSeek(newPosition.positionMs)
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            state.speed = playbackParameters.speed
            host.onSpeedChanged(playbackParameters.speed)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            state.isPlaying = isPlaying
            updateProgress()
            host.onIsPlayingChanged(isPlaying)
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateSubtitles()
            if (!tracks.isEmpty) {
                host.onTracksLoaded()
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            updateDuration()
        }

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
            state.videoDisabled = parameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO)
        }
    }

    /** The eight service callbacks. */
    val serviceListener = object : ExoPlayerService.Listener {
        override fun started() {
            if (host.isViewAlive) onServiceStarted()
        }

        override fun loaded() {
            if (host.isViewAlive) {
                state.loaded = true
                updateQuality()
                host.onLoaded()
            }
        }

        override fun changePlayerMode() {
            if (host.isViewAlive) {
                updateQuality()
                host.changePlayerMode()
            }
        }

        override fun toast(resId: Int, duration: Int) {
            if (host.isViewAlive) host.toast(resId, duration)
        }

        override fun toast(text: CharSequence, duration: Int) {
            if (host.isViewAlive) host.toast(text, duration)
        }

        override fun adStatus(text: CharSequence?) {
            if (host.isViewAlive) state.adStatus = text
        }

        override fun updateVideoInfo() {
            if (host.isViewAlive) {
                state.title = backend?.title?.trim()?.takeIf { it.isNotBlank() }
                state.gameName = backend?.gameName?.takeIf { it.isNotBlank() }
            }
        }

        override fun setPlayerSurface(secondaryPlayer: Boolean) {
            val backend = backend ?: return
            if (secondaryPlayer) {
                state.videoDisabled = false
                backend.player?.setVideoSurfaceView(null)
                backend.secondaryPlayer?.setVideoSurfaceView(host.surface)
                playerListener?.let {
                    backend.player?.removeListener(it)
                    backend.secondaryPlayer?.addListener(it)
                }
            } else {
                backend.secondaryPlayer?.setVideoSurfaceView(null)
                backend.player?.setVideoSurfaceView(host.surface)
                playerListener?.let {
                    backend.secondaryPlayer?.removeListener(it)
                    backend.player?.addListener(it)
                }
            }
            host.onIsPlayingChanged(backend.player?.isPlaying == true)
        }
    }

    /** The player the surface and the listener are on: the secondary one during a swapped ad break. */
    private val currentPlayer get() = backend?.let { it.secondaryPlayer ?: it.player }

    /** v0.1 onServiceConnected: take over the surface and the listener, then start if the service already has. */
    fun attach(backend: PlaybackBackend) {
        this.backend = backend
        state.kind = PlayerKind.of(backend.type)
        backend.setListener(serviceListener)
        currentPlayer?.setVideoSurfaceView(host.surface)
        currentPlayer?.addListener(listener)
        playerListener = listener
        currentPlayer?.let { host.onIsPlayingChanged(it.isPlaying) }
        state.loaded = backend.loaded
        updatePlayButton()
        updateProgress()
        updateDuration()
        updateSubtitles()
        updateQuality()
        if (backend.started) onServiceStarted()
    }

    /** v0.1 onStop and close: the listener leaves the player, the service forgets the page. */
    fun detach() {
        playerListener?.let { currentPlayer?.removeListener(it) }
        playerListener = null
        backend?.setListener(null)
        backend = null
    }

    private fun onServiceStarted() {
        if (!started) {
            if (host.canStart) {
                started = true
                host.onFirstStart()
                restoreQualityIfNeeded()
            }
        } else {
            host.onLaterStart()
            restoreQualityIfNeeded()
        }
    }

    /** The service asks for the quality from before audio only (background, or an ad break) to come back. */
    fun restoreQualityIfNeeded() {
        val backend = backend ?: return
        if (backend.restoreQuality) {
            backend.restoreQuality = false
            backend.changeQuality(backend.previousQuality)
            updateQuality()
        }
    }

    fun updatePlayButton() {
        val player = backend?.player
        val show = Util.shouldShowPlayButton(player)
        state.showPlayButton = show
        state.buffering = player?.playbackState == Player.STATE_BUFFERING
        state.ended = player?.playbackState == Player.STATE_ENDED
        host.onPlayButtonChanged(show, player?.playbackState ?: Player.STATE_IDLE)
    }

    fun updateProgress() {
        val player = backend?.player
        state.positionMs = player?.currentPosition ?: 0L
        state.bufferedMs = player?.bufferedPosition ?: 0L
    }

    fun updateDuration() {
        state.durationMs = backend?.player?.duration?.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0L
        updateProgress()
    }

    fun updateSubtitles() {
        val textTracks = backend?.player?.currentTracks?.groups?.find { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
        state.subtitles = when {
            textTracks == null -> SubtitleState.None
            textTracks.isSelected -> SubtitleState.On
            else -> SubtitleState.Off
        }
    }

    fun updateQuality() {
        val quality = backend?.quality
        state.audioOnly = quality?.name == VideoQuality.AUDIO_ONLY_QUALITY
        state.qualityLabel = host.qualityLabel(quality)
    }
}
