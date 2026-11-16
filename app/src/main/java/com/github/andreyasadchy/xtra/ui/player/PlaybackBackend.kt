package com.github.andreyasadchy.xtra.ui.player

import androidx.media3.common.Player
import com.github.andreyasadchy.xtra.model.VideoQuality
import kotlinx.coroutines.flow.Flow

/**
 * The player page's side of the contract with the playback service (plan 8 and 10.1, red line 1). Everything the page
 * reads from or asks of [ExoPlayerService] goes through here, so the U6 contract tests can drive the page with a fake
 * service. [ExoPlaybackBackend] is the real one; it only forwards, the service itself is unchanged.
 *
 * The service talks back through the eight [ExoPlayerService.Listener] callbacks, see [PlayerConnection].
 */
interface PlaybackBackend {
    /** [BasePlaybackService.STREAM], VIDEO, CLIP or OFFLINE_VIDEO. */
    val type: String?
    var streamId: String?
    val videoId: String?
    val clipId: String?
    val channelId: String?
    val channelLogin: String?
    val channelName: String?
    val channelImage: String?
    var gameId: String?
    var gameSlug: String?
    var gameName: String?
    var title: String?
    val thumbnail: String?
    val createdAt: String?
    val viewerCount: Int?
    val durationSeconds: Int?
    val videoType: String?
    val videoOffsetSeconds: Int?
    val videoCreatedAt: String?
    val videoAnimatedPreviewURL: String?
    val chatUrl: String?

    val qualities: List<VideoQuality>?
    val quality: VideoQuality?
    val previousQuality: VideoQuality?
    var restoreQuality: Boolean
    val started: Boolean
    val loaded: Boolean

    /** The main player. */
    val player: Player?
    /** The player the ad blocker swaps to during an ad break (plan 8); null when there is none. */
    val secondaryPlayer: Player?
    val integrity: Flow<String?>

    fun setListener(listener: ExoPlayerService.Listener?)

    fun togglePause()
    fun pause()
    fun seekBack()
    fun seekForward()
    fun seekTo(position: Long)
    fun seekToDefaultPosition()
    fun setPlaybackSpeed(speed: Float)
    fun changeVolume(volume: Float)
    fun getCurrentVolume(): Float?
    fun restartPlayer()
    fun toggleDynamicsProcessing(): Boolean
    fun toggleSubtitles(enabled: Boolean)
    fun getPlaylistTags(mediaPlaylist: Boolean): String?
    fun changeQuality(selectedQuality: VideoQuality?)
    fun startAudioOnly()
    fun setSleepTimer(duration: Long): Long
    fun setStopServiceTimer(start: Boolean)
    fun stop()
    fun stop(isInPIPMode: Boolean)
    fun stopSelf()
    fun retry(item: String)
}

/** Forwards to the real service, one line per member. */
class ExoPlaybackBackend(val service: ExoPlayerService) : PlaybackBackend {
    override val type get() = service.type
    override var streamId: String?
        get() = service.streamId
        set(value) { service.streamId = value }
    override val videoId get() = service.videoId
    override val clipId get() = service.clipId
    override val channelId get() = service.channelId
    override val channelLogin get() = service.channelLogin
    override val channelName get() = service.channelName
    override val channelImage get() = service.channelImage
    override var gameId: String?
        get() = service.gameId
        set(value) { service.gameId = value }
    override var gameSlug: String?
        get() = service.gameSlug
        set(value) { service.gameSlug = value }
    override var gameName: String?
        get() = service.gameName
        set(value) { service.gameName = value }
    override var title: String?
        get() = service.title
        set(value) { service.title = value }
    override val thumbnail get() = service.thumbnail
    override val createdAt get() = service.createdAt
    override val viewerCount get() = service.viewerCount
    override val durationSeconds get() = service.durationSeconds
    override val videoType get() = service.videoType
    override val videoOffsetSeconds get() = service.videoOffsetSeconds
    override val videoCreatedAt get() = service.videoCreatedAt
    override val videoAnimatedPreviewURL get() = service.videoAnimatedPreviewURL
    override val chatUrl get() = service.chatUrl
    override val qualities get() = service.qualities
    override val quality get() = service.quality
    override val previousQuality get() = service.previousQuality
    override var restoreQuality: Boolean
        get() = service.restoreQuality
        set(value) { service.restoreQuality = value }
    override val started get() = service.started
    override val loaded get() = service.loaded
    override val player: Player? get() = service.player
    override val secondaryPlayer: Player? get() = service.secondaryPlayer
    override val integrity: Flow<String?> get() = service.integrity

    override fun setListener(listener: ExoPlayerService.Listener?) { service.serviceListener = listener }
    override fun togglePause() = service.togglePause()
    override fun pause() = service.pause()
    override fun seekBack() = service.seekBack()
    override fun seekForward() = service.seekForward()
    override fun seekTo(position: Long) = service.seekTo(position)
    override fun seekToDefaultPosition() = service.seekToDefaultPosition()
    override fun setPlaybackSpeed(speed: Float) { service.player?.setPlaybackSpeed(speed) }
    override fun changeVolume(volume: Float) = service.changeVolume(volume)
    override fun getCurrentVolume() = service.getCurrentVolume()
    override fun restartPlayer() = service.restartPlayer()
    override fun toggleDynamicsProcessing() = service.toggleDynamicsProcessing()
    override fun toggleSubtitles(enabled: Boolean) = service.toggleSubtitles(enabled)
    override fun getPlaylistTags(mediaPlaylist: Boolean) = service.getPlaylistTags(mediaPlaylist)
    override fun changeQuality(selectedQuality: VideoQuality?) = service.changeQuality(selectedQuality)
    override fun startAudioOnly() = service.startAudioOnly()
    override fun setSleepTimer(duration: Long) = service.setSleepTimer(duration)
    override fun setStopServiceTimer(start: Boolean) = service.setStopServiceTimer(start)
    override fun stop() = service.stop()
    override fun stop(isInPIPMode: Boolean) = service.stop(isInPIPMode)
    override fun stopSelf() = service.stopSelf()
    override fun retry(item: String) = service.retry(item)
}
