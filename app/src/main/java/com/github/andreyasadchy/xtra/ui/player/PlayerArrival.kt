package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.size.Scale
import com.github.andreyasadchy.xtra.ui.design.components.PressOrigin
import kotlin.math.max

/**
 * How a player page arrives on the screen (motion plan M2a items 3 and 4). MainActivity sets it on the new fragment
 * before adding it; the fragment reads it once. A player restored after a restart has none and appears as it is (P24).
 */
sealed interface PlayerArrival {

    /**
     * Opened with no player on the screen: the video grows from [entrance], with [cover] (the content's picture,
     * an image model) over it until the first video frame. [coverWidth] and [coverHeight] are the tapped picture's
     * size in pixels, so the picture comes from the same memory cache entry the card drew.
     */
    data class Open(val entrance: PlayerEntrance, val cover: Any?, val coverWidth: Int, val coverHeight: Int) : PlayerArrival

    /**
     * Replaced the content of a player on the screen (raid, full video, another card while the mini player shows):
     * [video] is the old picture (or null, then [oldCover] stands in), which fades into [cover] and the new video.
     * The new page keeps the old one's mode ([startMinimized]); [expand] grows it from the mini player at once.
     */
    class Swap(
        val video: Bitmap?,
        val videoAspect: Float,
        val oldCover: Any?,
        val cover: Any?,
        val startMinimized: Boolean,
        val expand: Boolean,
    ) : PlayerArrival
}

/** Pure decisions about how a new player arrives (PlayerArrivalTest). */
object PlayerArrivalRules {

    /** What happens to a new content while a player is on the screen. */
    data class SwapPlan(val startMinimized: Boolean, val expand: Boolean)

    /**
     * A player on the screen always changes in place and keeps its mode; the tapped card is ignored. The one
     * exception to "keeps its mode": a card tapped while the mini player shows (the viewer asked for this content)
     * also expands it, at the same time as the swap (motion plan decision 5). A raid that switches the mini player
     * leaves it small.
     */
    fun swap(playerMinimized: Boolean, tap: PressOrigin.Tap?) = SwapPlan(startMinimized = playerMinimized, expand = playerMinimized && tap != null)

    /**
     * The picture to cover the video with, or null for Twitch's "no picture" placeholders (a stream or video without
     * a thumbnail gets a grey 404 image, which is worse than the black it would cover).
     */
    fun coverModel(model: Any?): Any? = model.takeUnless { it is String && (it.contains("/_404/") || it.contains("/404_preview")) }

    /** The entrance of a player opened with none on the screen: from the tapped picture, else rising from the bottom. */
    fun entrance(tap: PressOrigin.Tap?) = PlayerEntrance(tap?.bounds)
}

/**
 * When the picture over the video slot (the "cover", motion plan M2a item 2) goes up and comes down. It hides the
 * black wait for the first video frame when a player opens, when its content is replaced, and when the app comes
 * back from the background with the video on. Pure, so the rules are tested without a player (PlayerCoverTest).
 */
class CoverGate(private val timeoutMs: Long = TimeoutMs) {

    enum class Phase { Idle, Waiting, Showing }

    /** What the page does with the cover view. */
    enum class Action { None, Fade, Remove }

    var phase = Phase.Idle
        private set
    private var armedAt = 0L

    /** A cover is wanted from [nowMs]; the picture is on its way from the image cache. */
    fun arm(nowMs: Long) {
        phase = Phase.Waiting
        armedAt = nowMs
    }

    /** The picture arrived: true when it should show (no first frame, timeout or cancel came first). */
    fun imageReady(): Boolean {
        if (phase != Phase.Waiting) return false
        phase = Phase.Showing
        return true
    }

    /** The video drew its first frame: the cover fades. */
    fun firstFrame(): Action = finish(Action.Fade)

    /** No first frame within [timeoutMs] of arming: fade anyway, back to how it looked before (never worse). */
    fun tick(nowMs: Long): Action = if (phase != Phase.Idle && nowMs - armedAt >= timeoutMs) finish(Action.Fade) else Action.None

    /** Paused, an error, audio only or chat only: the cover goes at once (it would hide the page's own state). */
    fun cancel(): Action = finish(Action.Remove)

    private fun finish(action: Action): Action {
        val showing = phase == Phase.Showing
        phase = Phase.Idle
        return if (showing) action else Action.None
    }

    companion object {
        const val TimeoutMs = 3000L

        /**
         * Cover on the way back from the background only when the video was on and playing as the page left: the
         * viewer's own "audio only" keeps its backdrop, and a paused video may not draw a new frame to end the cover.
         */
        fun coverOnReturn(videoOn: Boolean, playing: Boolean) = videoOn && playing
    }
}

/**
 * A picture from Coil's caches only (motion plan M2a: never the network, which would be slower than the first video
 * frame). The memory cache is read, not written, so a request at another size never replaces the card's own entry
 * (the poster blink found in U4).
 */
object CachedImage {
    /** Size asked for when the card's size is unknown: small enough that any card's cached picture is big enough. */
    const val DefaultWidth = 480
    const val DefaultHeight = 270

    fun load(context: Context, model: Any?, widthPx: Int, heightPx: Int, onImage: (Drawable) -> Unit): Disposable? {
        if (model == null) return null
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(max(widthPx, 1), max(heightPx, 1))
            .scale(Scale.FILL)
            .memoryCachePolicy(CachePolicy.READ_ONLY)
            .diskCachePolicy(CachePolicy.READ_ONLY)
            .networkCachePolicy(CachePolicy.DISABLED)
            .target(onSuccess = { image -> onImage(image.asDrawable(context.resources)) })
            .build()
        return context.imageLoader.enqueue(request)
    }
}
