package com.github.andreyasadchy.xtra.ui.player

import android.view.SurfaceView
import androidx.media3.common.C as MediaC
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.model.VideoQuality
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

/**
 * The U6 contract between the player page and the playback service (plan 10.2 "播放契约测试", red line 1): the video
 * surface follows the ad blocker's swap to the secondary player and back, the ad status shows and goes, the quality
 * from before an ad break or the background comes back, and each of the eight service callbacks reaches the page.
 * A fake service and fake players stand in for ExoPlayerService.
 */
@RunWith(AndroidJUnit4::class)
class PlayerContractTest {

    private val surface = SurfaceView(ApplicationProvider.getApplicationContext())
    private val host = FakeHost(surface)
    private val state = PlayerUiState()
    private val connection = PlayerConnection(state, host)

    private val source = VideoQuality(name = "chunked", url = "https://example.com/source.m3u8")
    private val p720 = VideoQuality(name = "720p60", url = "https://example.com/720.m3u8")
    private val audio = VideoQuality(name = VideoQuality.AUDIO_ONLY_QUALITY, url = "https://example.com/audio.m3u8")

    @Test
    fun surfaceAndListenerGoToTheMainPlayer() {
        val backend = FakeBackend()
        connection.attach(backend)
        assertSame(surface, backend.main.surface)
        assertEquals(1, backend.main.listeners.size)
        assertSame(connection.serviceListener, backend.registered)
    }

    @Test
    fun aPageOpenedDuringASwappedAdBreakShowsTheSecondaryPlayer() {
        val backend = FakeBackend(withSecondary = true)
        connection.attach(backend)
        assertSame(surface, backend.secondary!!.surface)
        assertNull(backend.main.surface)
        assertEquals(1, backend.secondary!!.listeners.size)
        assertTrue(backend.main.listeners.isEmpty())
    }

    @Test
    fun theSurfaceFollowsTheAdSwapAndComesBack() {
        val backend = FakeBackend()
        connection.attach(backend)
        backend.secondary = FakePlayer()
        backend.registered!!.setPlayerSurface(true)
        assertNull(backend.main.surface)
        assertSame(surface, backend.secondary!!.surface)
        assertTrue(backend.main.listeners.isEmpty())
        assertEquals(1, backend.secondary!!.listeners.size)

        backend.registered!!.setPlayerSurface(false)
        assertNull(backend.secondary!!.surface)
        assertSame(surface, backend.main.surface)
        assertEquals(1, backend.main.listeners.size)
        assertTrue(backend.secondary!!.listeners.isEmpty())
    }

    @Test
    fun swappingToTheSecondaryPlayerShowsVideoEvenAfterAudioOnly() {
        val backend = FakeBackend()
        connection.attach(backend)
        state.videoDisabled = true
        backend.secondary = FakePlayer()
        backend.registered!!.setPlayerSurface(true)
        assertFalse(state.videoDisabled)
    }

    @Test
    fun adStatusAppearsAndGoes() {
        val backend = FakeBackend()
        connection.attach(backend)
        backend.registered!!.adStatus("Ad blocked · clean stream")
        assertEquals("Ad blocked · clean stream", state.adStatus)
        backend.registered!!.adStatus(null)
        assertNull(state.adStatus)
    }

    @Test
    fun theQualityBeforeTheAdBreakComesBackWhenTheServiceRestarts() {
        val backend = FakeBackend(started = true)
        connection.attach(backend)
        assertEquals(listOf("firstStart"), host.calls)
        // an ad break (or the background) switched to audio only and asked for the old quality back
        backend.quality = audio
        backend.previousQuality = p720
        backend.restoreQuality = true
        backend.registered!!.started()
        assertEquals(listOf("firstStart", "laterStart"), host.calls)
        assertEquals(listOf(p720), backend.qualityChanges)
        assertFalse(backend.restoreQuality)
        assertEquals("720p60", state.qualityLabel)
        assertFalse(state.audioOnly)
    }

    @Test
    fun theFirstStartAlsoRestoresTheQuality() {
        val backend = FakeBackend(started = true).apply {
            quality = audio
            previousQuality = source
            restoreQuality = true
        }
        connection.attach(backend)
        assertEquals(listOf(source), backend.qualityChanges)
        assertFalse(backend.restoreQuality)
    }

    @Test
    fun theFirstStartWaitsForTheNetworkCheck() {
        host.canStart = false
        val backend = FakeBackend(started = true)
        connection.attach(backend)
        assertTrue(host.calls.isEmpty())
        assertFalse(connection.started)
        host.canStart = true
        backend.registered!!.started()
        assertEquals(listOf("firstStart"), host.calls)
        assertTrue(connection.started)
    }

    @Test
    fun loadedEnablesQualityAndTellsThePage() {
        val backend = FakeBackend()
        connection.attach(backend)
        assertFalse(state.loaded)
        backend.loaded = true
        backend.quality = source
        backend.registered!!.loaded()
        assertTrue(state.loaded)
        assertEquals("chunked", state.qualityLabel)
        assertEquals(listOf("loaded"), host.calls)
    }

    @Test
    fun changePlayerModeFollowsAudioOnly() {
        val backend = FakeBackend()
        connection.attach(backend)
        backend.quality = audio
        backend.registered!!.changePlayerMode()
        assertTrue(state.audioOnly)
        assertEquals(listOf("changePlayerMode"), host.calls)
    }

    @Test
    fun toastsReachThePage() {
        val backend = FakeBackend()
        connection.attach(backend)
        backend.registered!!.toast(42, 0)
        backend.registered!!.toast("player error", 1)
        assertEquals(listOf("toast:42", "toast:player error"), host.calls)
    }

    @Test
    fun updateVideoInfoShowsTheNewTitleAndGame() {
        val backend = FakeBackend()
        connection.attach(backend)
        backend.title = "  New title  "
        backend.gameName = "Minecraft"
        backend.registered!!.updateVideoInfo()
        assertEquals("New title", state.title)
        assertEquals("Minecraft", state.gameName)
    }

    @Test
    fun callbacksAfterThePageIsGoneAreIgnored() {
        val backend = FakeBackend()
        connection.attach(backend)
        host.viewAlive = false
        backend.registered!!.adStatus("Ad")
        backend.registered!!.toast("x", 0)
        backend.registered!!.loaded()
        assertNull(state.adStatus)
        assertFalse(state.loaded)
        assertTrue(host.calls.isEmpty())
    }

    @Test
    fun detachLeavesThePlayerAndTheService() {
        val backend = FakeBackend(withSecondary = true)
        connection.attach(backend)
        connection.detach()
        assertTrue(backend.secondary!!.listeners.isEmpty())
        assertNull(backend.registered)
        assertNull(connection.backend)
    }

    @Test
    fun playerEventsBecomePageState() {
        val backend = FakeBackend()
        connection.attach(backend)
        val listener = backend.main.listeners.single()
        backend.main.state = Player.STATE_BUFFERING
        backend.main.playWhenReady = true
        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        assertTrue(state.buffering)
        assertFalse(state.showPlayButton)

        backend.main.playWhenReady = false
        backend.main.state = Player.STATE_READY
        listener.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        assertFalse(state.buffering)
        assertTrue(state.showPlayButton)

        listener.onTrackSelectionParametersChanged(
            TrackSelectionParameters.Builder().setTrackTypeDisabled(MediaC.TRACK_TYPE_VIDEO, true).build()
        )
        assertTrue(state.videoDisabled)
        listener.onTrackSelectionParametersChanged(TrackSelectionParameters.Builder().build())
        assertFalse(state.videoDisabled)

        listener.onPlaybackParametersChanged(PlaybackParameters(1.5f))
        assertEquals(1.5f, state.speed)
        listener.onIsPlayingChanged(true)
        assertTrue(state.isPlaying)
    }

    @Test
    fun aSeekMovesTheReplayChat() {
        val backend = FakeBackend()
        connection.attach(backend)
        val listener = backend.main.listeners.single()
        val old = Player.PositionInfo(null, 0, null, null, 0, 1_000, 1_000, -1, -1)
        val new = Player.PositionInfo(null, 0, null, null, 0, 61_000, 61_000, -1, -1)
        listener.onPositionDiscontinuity(old, new, Player.DISCONTINUITY_REASON_SEEK)
        listener.onPositionDiscontinuity(old, new, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
        assertEquals(listOf("seek:61000"), host.calls.filter { it.startsWith("seek") })
    }

    // --- fakes ---

    private inner class FakeBackend(withSecondary: Boolean = false, override var started: Boolean = false) : PlaybackBackend {
        val main = FakePlayer()
        var secondary: FakePlayer? = if (withSecondary) FakePlayer() else null
        var registered: ExoPlayerService.Listener? = null
        val qualityChanges = mutableListOf<VideoQuality?>()

        override val type: String? = BasePlaybackService.STREAM
        override var streamId: String? = null
        override val videoId: String? = null
        override val clipId: String? = null
        override val channelId: String? = "1"
        override val channelLogin: String? = "lagoon"
        override val channelName: String? = "Lagoon"
        override val channelImage: String? = null
        override var gameId: String? = null
        override var gameSlug: String? = null
        override var gameName: String? = null
        override var title: String? = null
        override val thumbnail: String? = null
        override val createdAt: String? = null
        override val viewerCount: Int? = null
        override val durationSeconds: Int? = null
        override val videoType: String? = null
        override val videoOffsetSeconds: Int? = null
        override val videoCreatedAt: String? = null
        override val videoAnimatedPreviewURL: String? = null
        override val chatUrl: String? = null
        override val qualities: List<VideoQuality> = listOf(source, p720, audio)
        override var quality: VideoQuality? = source
        override var previousQuality: VideoQuality? = null
        override var restoreQuality: Boolean = false
        override var loaded: Boolean = false
        override val player: Player get() = main.player
        override val secondaryPlayer: Player? get() = secondary?.player
        override val integrity = MutableSharedFlow<String?>()

        override fun setListener(listener: ExoPlayerService.Listener?) { registered = listener }
        override fun togglePause() = Unit
        override fun pause() = Unit
        override fun seekBack() = Unit
        override fun seekForward() = Unit
        override fun seekTo(position: Long) = Unit
        override fun seekToDefaultPosition() = Unit
        override fun setPlaybackSpeed(speed: Float) = Unit
        override fun changeVolume(volume: Float) = Unit
        override fun getCurrentVolume(): Float? = 1f
        override fun restartPlayer() = Unit
        override fun toggleDynamicsProcessing() = false
        override fun toggleSubtitles(enabled: Boolean) = Unit
        override fun getPlaylistTags(mediaPlaylist: Boolean): String? = null
        override fun changeQuality(selectedQuality: VideoQuality?) {
            qualityChanges += selectedQuality
            previousQuality = quality
            quality = selectedQuality
        }
        override fun startAudioOnly() = Unit
        override fun setSleepTimer(duration: Long) = 0L
        override fun setStopServiceTimer(start: Boolean) = Unit
        override fun stop() = Unit
        override fun stop(isInPIPMode: Boolean) = Unit
        override fun stopSelf() = Unit
        override fun retry(item: String) = Unit
    }

    /** A Player that records its surface and listeners; everything else answers with defaults. */
    private class FakePlayer {
        var surface: SurfaceView? = null
        val listeners = mutableListOf<Player.Listener>()
        var state = Player.STATE_READY
        var playWhenReady = true

        val player: Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "setVideoSurfaceView" -> { surface = args?.get(0) as SurfaceView?; null }
                "addListener" -> { listeners += args!![0] as Player.Listener; null }
                "removeListener" -> { listeners -= args!![0] as Player.Listener; null }
                "getPlaybackState" -> state
                "getPlayWhenReady" -> playWhenReady
                "isCommandAvailable" -> true
                "getAvailableCommands" -> Player.Commands.EMPTY
                "getCurrentTracks" -> Tracks.EMPTY
                "getPlaybackParameters" -> PlaybackParameters.DEFAULT
                "getDuration" -> MediaC.TIME_UNSET
                "getPlaybackSuppressionReason" -> Player.PLAYBACK_SUPPRESSION_REASON_NONE
                "hashCode" -> System.identityHashCode(this)
                "equals" -> args?.get(0) === this
                "toString" -> "FakePlayer"
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 0f
                    java.lang.Double.TYPE -> 0.0
                    else -> null
                }
            }
        } as Player
    }

    private class FakeHost(override val surface: SurfaceView) : PlayerConnection.Host {
        val calls = mutableListOf<String>()
        var viewAlive = true
        override var canStart = true
        override val isViewAlive get() = viewAlive
        override fun onFirstStart() { calls += "firstStart" }
        override fun onLaterStart() { calls += "laterStart" }
        override fun onLoaded() { calls += "loaded" }
        override fun changePlayerMode() { calls += "changePlayerMode" }
        override fun toast(resId: Int, duration: Int) { calls += "toast:$resId" }
        override fun toast(text: CharSequence, duration: Int) { calls += "toast:$text" }
        override fun onSeek(positionMs: Long) { calls += "seek:$positionMs" }
        override fun onSpeedChanged(speed: Float) = Unit
        override fun onTracksLoaded() = Unit
        override fun onCues(cues: List<Cue>) = Unit
        override fun onIsPlayingChanged(isPlaying: Boolean) = Unit
        override fun onPlayButtonChanged(showPlayButton: Boolean, playbackState: Int) = Unit
        override fun qualityLabel(quality: VideoQuality?) = quality?.name
    }
}
