package com.github.andreyasadchy.xtra.ui.design.theme

import android.view.View
import android.view.animation.PathInterpolator

/**
 * The motion system's values for the few plain Views that animate (the player's cover and snapshot layers, motion
 * plan M2a). Same durations and curves as [LagoonMotion]; fades are kept when animations are reduced, as there.
 */
object LagoonViewMotion {

    private val Exit = PathInterpolator(0.3f, 0f, 1f, 1f)

    /**
     * Fades [view] out over [durationMs] on the exit curve, then hides it and runs [then]. A fade already running on
     * the view is replaced.
     */
    fun fadeOut(view: View, durationMs: Int = LagoonMotion.SHORT, then: () -> Unit = {}) {
        view.animate().cancel()
        if (view.visibility != View.VISIBLE || view.alpha == 0f) {
            view.visibility = View.GONE
            then()
            return
        }
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        view.animate()
            .alpha(0f)
            .setDuration(durationMs.toLong())
            .setInterpolator(Exit)
            .withEndAction {
                view.setLayerType(View.LAYER_TYPE_NONE, null)
                view.visibility = View.GONE
                then()
            }
            .start()
    }

    /** Shows [view] at full opacity at once, cancelling a fade. */
    fun show(view: View) {
        view.animate().cancel()
        view.setLayerType(View.LAYER_TYPE_NONE, null)
        view.alpha = 1f
        view.visibility = View.VISIBLE
    }

    /** Hides [view] at once, cancelling a fade. */
    fun hide(view: View) {
        view.animate().cancel()
        view.setLayerType(View.LAYER_TYPE_NONE, null)
        view.visibility = View.GONE
    }
}
