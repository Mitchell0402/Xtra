package com.github.andreyasadchy.xtra.ui.design.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * The motion system (plan section 5): four durations, two curves, two springs. Screens take every value from here and
 * never hard-code a number.
 *
 * [reduced] is true when the system "Remove animations" switch is on. Movement and scaling then snap and only fades
 * remain (plan 5 and 10.5).
 */
@Immutable
class LagoonMotion(val reduced: Boolean) {

    /** Steady spring: drag-following, sheets, the mini player, sliding selection blocks. */
    fun <T> steady(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(SteadyDamping, SteadyStiffness, visibilityThreshold)

    /** Bouncy spring: follow and bookmark, small celebratory moves. */
    fun <T> bouncy(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(BouncyDamping, BouncyStiffness, visibilityThreshold)

    /** A movement or size change of [durationMs] on the standard curve. */
    fun <T> move(durationMs: Int = MEDIUM): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMs, easing = Standard)

    /** A fade or colour change; kept when animations are reduced. */
    fun <T> fade(durationMs: Int = SHORT): FiniteAnimationSpec<T> = tween(durationMs, easing = Standard)

    /** A fade of something leaving the screen. */
    fun <T> fadeExit(durationMs: Int = SHORT): FiniteAnimationSpec<T> = tween(durationMs, easing = Exit)

    /** Press feedback: scale and lightening (100 ms). */
    fun <T> press(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(INSTANT, easing = Standard)

    /**
     * Sibling switch (bottom nav, top tabs): cross-fade and a 24 dp shift in the direction of travel.
     * [forward] is true when the new item sits after the old one.
     */
    fun sibling(density: Density, forward: Boolean): ContentTransform {
        val shift = with(density) { SiblingShift.roundToPx() }
        val dir = if (forward) 1 else -1
        val enter = fadeIn(fade(SHORT)) + if (reduced) EnterTransition.None else slideInHorizontally(move(SHORT)) { dir * shift }
        val exit = fadeOut(fadeExit(SHORT)) + if (reduced) ExitTransition.None else slideOutHorizontally(move(SHORT)) { -dir * shift }
        return ContentTransform(enter, exit, sizeTransform = SizeTransform(clip = false))
    }

    /** Going one level deeper (list to sub-page): the new page slides in from the end, the old one shrinks a little and dims. */
    fun forward(): ContentTransform {
        val enter = fadeIn(fade(MEDIUM)) + if (reduced) EnterTransition.None else slideInHorizontally(move(MEDIUM)) { it }
        val exit = fadeOut(fadeExit(MEDIUM), targetAlpha = 0.4f) + if (reduced) ExitTransition.None else scaleOut(move(MEDIUM), targetScale = 0.96f)
        return enter togetherWith exit
    }

    /** Going back: the reverse of [forward]. */
    fun back(): ContentTransform {
        val enter = fadeIn(fade(MEDIUM), initialAlpha = 0.4f)
        val exit = fadeOut(fadeExit(MEDIUM)) + if (reduced) ExitTransition.None else slideOutHorizontally(move(MEDIUM)) { it }
        return (enter togetherWith exit).apply { targetContentZIndex = -1f }
    }

    /** Rows that a switch controls expand and collapse instead of turning grey (plan 5, settings). */
    fun expand(): EnterTransition = fadeIn(fade(SHORT)) + if (reduced) EnterTransition.None else expandVertically(move(MEDIUM))
    fun collapse(): ExitTransition = fadeOut(fadeExit(SHORT)) + if (reduced) ExitTransition.None else shrinkVertically(move(MEDIUM))

    companion object {
        /** Press, switches, colour changes. */
        const val INSTANT = 100
        /** Fades, tab switches, small movements. */
        const val SHORT = 200
        /** Page forward and back, sheets. */
        const val MEDIUM = 300
        /** Card expanding into a page, thumbnail expanding into the player. */
        const val LONG = 450

        /** Things entering and moving. */
        val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        /** Things leaving the screen. */
        val Exit = CubicBezierEasing(0.3f, 0f, 1f, 1f)

        const val SteadyDamping = 0.85f
        const val SteadyStiffness = 380f
        const val BouncyDamping = 0.6f
        const val BouncyStiffness = 300f

        val SiblingShift = 24.dp
        /** Pressed cards and rows scale to 97 %. */
        const val PressedScale = 0.97f
        /** First-load items fade in one after another, this far apart, on the first screen only. */
        const val StaggerMs = 30
        /** Items past this index appear at once. */
        const val StaggerLimit = 12

        /** Reads the system "Remove animations" switch (animator duration scale 0). */
        fun isReduced(context: Context): Boolean =
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

        /** For the rare place that needs a spec outside a composition. */
        fun <T> steadySpring(): AnimationSpec<T> = spring(SteadyDamping, SteadyStiffness)
    }
}

/** Shorthand used by AnimatedContent callers that only know the index of the old and new item. */
fun <S> AnimatedContentTransitionScope<S>.siblingBy(motion: LagoonMotion, density: Density, index: (S) -> Int): ContentTransform =
    motion.sibling(density, index(targetState) >= index(initialState))
