package com.github.andreyasadchy.xtra.ui.design.theme

import android.app.Activity
import android.os.Build
import android.view.View
import com.github.andreyasadchy.xtra.R
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

    /**
     * Settings and login open and close like a page going one level deeper and back ([LagoonMotion.forward] and
     * [LagoonMotion.back]: 300 ms, the new page slides in from the end, the old one shrinks to 96 % and dims; motion plan
     * M5 item 1). Android 14 and up; before that the system's own activity animation stays. Call in onCreate.
     */
    fun pageTransitions(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.lagoon_forward_enter, R.anim.lagoon_forward_exit)
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, R.anim.lagoon_back_enter, R.anim.lagoon_back_exit)
        }
    }

    /** Hides [view] at once, cancelling a fade. */
    fun hide(view: View) {
        view.animate().cancel()
        view.setLayerType(View.LAYER_TYPE_NONE, null)
        view.visibility = View.GONE
    }
}
