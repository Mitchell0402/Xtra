package com.github.andreyasadchy.xtra.lab

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * U0 validation 2: moving the picture between the primary and secondary player, as ExoPlayerService does at an ad
 * boundary. "legacy" moves one SurfaceView with setVideoSurfaceView like ExoPlayerFragment.setPlayerSurface;
 * "compose" changes the player of Media3's Compose PlayerSurface. Both players play the same live channel from two
 * sources (web/site and android/autoplay, the real primary and fallback), the secondary muted and off screen.
 *
 * After every switch the surface is sampled with PixelCopy every 16 ms for 1 s; a sample whose 7x7 grid is all near
 * black counts as a black frame. The same sampling runs for 1 s before each switch as a control.
 *
 * adb shell am start -n <pkg>/com.github.andreyasadchy.xtra.lab.PlayerSwitchLabActivity --es mode compose|legacy
 *     --es channel <login> --ei switches 20 --ei interval 4000
 * Results go to logcat (tag PlayerSwitchLab) and the status line ("DONE ...").
 */
@UnstableApi
class PlayerSwitchLabActivity : ComponentActivity() {

    private lateinit var status: TextView
    private val composePlayer = mutableStateOf<Player?>(null)
    private val players = arrayOfNulls<ExoPlayer>(2)
    private val copyThread = HandlerThread("pixelcopy").apply { start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "compose"
        val channel = intent.getStringExtra("channel") ?: "xqc"
        val switches = intent.getIntExtra("switches", 20)
        val interval = intent.getLongExtra("interval", intent.getIntExtra("interval", 4000).toLong())

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        val videoBox = FrameLayout(this)
        val legacySurface = SurfaceView(this)
        if (mode == "legacy") {
            videoBox.addView(legacySurface, FrameLayout.LayoutParams(-1, -1))
        } else {
            videoBox.addView(ComposeView(this).apply {
                setContent { PlayerSurface(player = composePlayer.value, surfaceType = SURFACE_TYPE_SURFACE_VIEW) }
            }, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(videoBox, FrameLayout.LayoutParams(-1, resources.displayMetrics.widthPixels * 9 / 16))
        status = TextView(this).apply { setTextColor(0xFFFFFFFF.toInt()); text = "loading" }
        root.addView(status, FrameLayout.LayoutParams(-2, -2).apply { topMargin = resources.displayMetrics.widthPixels * 9 / 16 + 40 })
        setContentView(root)

        lifecycleScope.launch {
            val repo = (application as XtraApp).xtraModule.playerRepository
            val gql = TwitchApiHelper.getGQLHeaders(this@PlayerSwitchLabActivity, false)
            val urls = withContext(Dispatchers.IO) {
                listOf("web" to "site", "android" to "autoplay").map { (platform, type) ->
                    repo.loadStreamPlaylistUrl(this@PlayerSwitchLabActivity, "0", gql, channel, platform, type, null, false, null, null, null, null, null, false)
                }
            }
            for (i in 0..1) {
                players[i] = buildPlayer().apply {
                    setMediaSource(HlsMediaSource.Factory(DefaultDataSource.Factory(this@PlayerSwitchLabActivity)).createMediaSource(MediaItem.fromUri(urls[i])))
                    volume = if (i == 0) 1f else 0f
                    prepare()
                    playWhenReady = true
                }
            }
            var current = 0
            attach(mode, legacySurface, null, players[0]!!)
            status.text = "warming up"
            waitReady(players[0]!!); waitReady(players[1]!!)
            delay(3000)
            var blackAfter = 0
            var blackBefore = 0
            var samples = 0
            val firstFrameMs = mutableListOf<Long>()
            for (s in 1..switches) {
                val surface = surfaceView(mode, legacySurface, videoBox) ?: break
                blackBefore += sample(surface, 1000).first
                delay((interval - 2000).coerceAtLeast(0))
                val from = players[current]!!
                val to = players[1 - current]!!
                var firstFrameAt = 0L
                val l = object : Player.Listener {
                    override fun onRenderedFirstFrame() { firstFrameAt = SystemClock.uptimeMillis() }
                }
                to.addListener(l)
                val t0 = SystemClock.uptimeMillis()
                attach(mode, legacySurface, from, to)
                to.volume = 1f; from.volume = 0f
                val (black, n) = sample(surfaceView(mode, legacySurface, videoBox) ?: surface, 1000)
                to.removeListener(l)
                blackAfter += black
                samples += n
                if (firstFrameAt > 0) firstFrameMs += firstFrameAt - t0
                current = 1 - current
                Log.i(TAG, "switch $s mode=$mode black=$black/$n firstFrame=${if (firstFrameAt > 0) firstFrameAt - t0 else -1}ms")
                status.text = "running $s/$switches"
            }
            val sorted = firstFrameMs.sorted()
            val result = "DONE mode=$mode switches=$switches blackAfter=$blackAfter blackBefore=$blackBefore samplesPerSwitch=${samples / switches.coerceAtLeast(1)} " +
                "firstFrameMedian=${sorted.getOrNull(sorted.size / 2)}ms firstFrameMax=${sorted.lastOrNull()}ms"
            Log.i(TAG, result)
            status.text = result
        }
    }

    private fun attach(mode: String, legacySurface: SurfaceView, from: ExoPlayer?, to: ExoPlayer) {
        if (mode == "legacy") {
            from?.setVideoSurfaceView(null)
            to.setVideoSurfaceView(legacySurface)
        } else {
            composePlayer.value = to
        }
    }

    private fun surfaceView(mode: String, legacy: SurfaceView, box: ViewGroup): SurfaceView? =
        if (mode == "legacy") legacy else findSurface(box)

    private fun findSurface(v: View): SurfaceView? = when (v) {
        is SurfaceView -> v
        is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { findSurface(v.getChildAt(it)) }
        else -> null
    }

    /** @return black samples and total samples over [durationMs] */
    private suspend fun sample(surface: SurfaceView, durationMs: Long): Pair<Int, Int> {
        val end = SystemClock.uptimeMillis() + durationMs
        val bmp = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)
        var black = 0
        var n = 0
        while (SystemClock.uptimeMillis() < end) {
            val ok = suspendCancellableCoroutine { c ->
                PixelCopy.request(surface, bmp, { r -> c.resume(r == PixelCopy.SUCCESS) }, Handler(copyThread.looper))
            }
            if (ok) {
                n++
                var max = 0
                for (y in 0 until 7) for (x in 0 until 7) {
                    val p = bmp.getPixel(4 + x * 9, 2 + y * 5)
                    max = maxOf(max, (p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF))
                }
                if (max < 3 * 16) black++
            } else {
                n++; black++ // nothing to copy means nothing on screen
            }
            delay(16)
        }
        return black to n
    }

    private suspend fun waitReady(p: ExoPlayer) {
        while (p.playbackState != Player.STATE_READY) delay(100)
    }

    private fun buildPlayer() = ExoPlayer.Builder(this).setRenderersFactory(
        DefaultRenderersFactory(this).setMediaCodecSelector { mime, secure, tunneling ->
            val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
            decoders.filterNot { it.name.startsWith("c2.goldfish.") }.ifEmpty { decoders }
        }
    ).build()

    override fun onDestroy() {
        players.forEach { it?.release() }
        copyThread.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val TAG = "PlayerSwitchLab"
    }
}
