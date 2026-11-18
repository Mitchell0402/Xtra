package com.github.andreyasadchy.xtra.ui.design.theme

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What a continuous (never-ending) animation is for. Every one in the app goes through [continuousTransition], and
 * `tools/ci/motion_guard.py` fails a build that calls `rememberInfiniteTransition` anywhere else (motion plan M0).
 */
enum class ContinuousMotion {
    /** Decoration that runs while nothing else happens (the live dot breathing). Stops over a glass backdrop. */
    Ambient,

    /** Shows that something is in progress (spinners, the skeleton sweep). It ends when the work ends, so it keeps running over glass. */
    Progress,
}

/**
 * Whether [ContinuousMotion.Ambient] animations may run here. A page whose content is the backdrop of real glass
 * turns it off (glass decision 4a): every frame of the animation redraws the page, and the glass over it re-blurs 60
 * times a second while nothing else moves. This is what made the first G-stage phone check fail.
 */
val LocalAmbientMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { true }

/**
 * The infinite transition for a continuous animation of [kind], or null when it must not run: motion is reduced, or
 * [kind] is [ContinuousMotion.Ambient] over a glass backdrop. Callers then draw the still state. Cycle lengths come
 * from [LagoonMotion.loop].
 */
@Composable
fun continuousTransition(label: String, kind: ContinuousMotion): InfiniteTransition? {
    if (Lagoon.motion.reduced) return null
    if (kind == ContinuousMotion.Ambient && !LocalAmbientMotion.current) return null
    return rememberInfiniteTransition(label = label)
}
