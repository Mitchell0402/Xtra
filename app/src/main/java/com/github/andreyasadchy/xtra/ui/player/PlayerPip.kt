package com.github.andreyasadchy.xtra.ui.player

import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import kotlin.math.roundToInt

/**
 * The picture-in-picture parameters (motion plan M2b item 2, P10). Every place that sets them goes through [params],
 * so the system always knows where the picture is ([Hint]) and can shrink it from there instead of from the whole
 * window. Android merges the parameters: a call that leaves a field out keeps the earlier value.
 */
object PlayerPip {

    /** Android's limits for the window's aspect ratio. */
    const val MaxRatio = 2.39f
    const val MinRatio = 1f / MaxRatio

    /** Where the picture is, in window pixels, and its aspect ratio as the system wants it (same shape as the rect). */
    data class Hint(val left: Int, val top: Int, val right: Int, val bottom: Int, val ratioWidth: Int, val ratioHeight: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /**
     * The picture inside the video view at [placement]: in the fit mode the [videoAspect] picture letterboxed in the
     * middle, otherwise the whole view (zoom and fill cover it). Clamped to Android's ratio limits by trimming the
     * long side, so the hint and the ratio always agree (else the system crops). Null when there is nothing to show.
     */
    fun hint(placement: VideoPlacement?, videoAspect: Float, fit: Boolean): Hint? {
        if (placement == null || !placement.visible) return null
        val width = placement.width * placement.scale
        val height = placement.height * placement.scale
        if (width < 1f || height < 1f || videoAspect <= 0f) return null
        val left = placement.left + placement.translationX
        val top = placement.top + placement.translationY
        var pictureWidth = width
        var pictureHeight = height
        if (fit) {
            if (videoAspect > width / height) pictureHeight = width / videoAspect else pictureWidth = height * videoAspect
        }
        val ratio = pictureWidth / pictureHeight
        if (ratio > MaxRatio) pictureWidth = pictureHeight * MaxRatio
        if (ratio < MinRatio) pictureHeight = pictureWidth / MinRatio
        val l = (left + (width - pictureWidth) / 2f).roundToInt()
        val t = (top + (height - pictureHeight) / 2f).roundToInt()
        var w = pictureWidth.roundToInt().coerceAtLeast(1)
        var h = pictureHeight.roundToInt().coerceAtLeast(1)
        // rounding must not push the shape past the limits (the system would refuse the parameters)
        if (w.toFloat() / h > MaxRatio) w = (h * MaxRatio).toInt().coerceAtLeast(1)
        if (w.toFloat() / h < MinRatio) h = (w / MinRatio).toInt().coerceAtLeast(1)
        return Hint(l, t, l + w, t + h, w, h)
    }

    /**
     * The one constructor for the parameters. [autoEnter] and [actions] are left as they were when null. Seamless
     * resizing is off: Android advises it for video, which would otherwise be cross-faded while it resizes.
     */
    fun params(hint: Hint? = null, autoEnter: Boolean? = null, actions: List<RemoteAction>? = null): PictureInPictureParams =
        PictureInPictureParams.Builder().apply {
            if (hint != null) {
                setSourceRectHint(Rect(hint.left, hint.top, hint.right, hint.bottom))
                setAspectRatio(Rational(hint.ratioWidth, hint.ratioHeight))
            }
            if (actions != null) setActions(actions)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (autoEnter != null) setAutoEnterEnabled(autoEnter)
                setSeamlessResizeEnabled(false)
            }
        }.build()
}
