@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.github.andreyasadchy.xtra.ui.design.components

import dev.chrisbanes.haze.HazePositionStrategy
import dev.chrisbanes.haze.HazeState

/**
 * A [HazeState] for Lagoon glass. The navigation pill sits in the activity's own ComposeView while the page it blurs
 * is a fragment's ComposeView: two roots in the same window. Haze's automatic strategy only switches to screen
 * coordinates across windows (it compares window ids), so inside one window it would compare positions in two
 * different roots and draw the page shifted by the pill's offset. Screen coordinates are right in every case; Haze
 * keeps the setter internal, hence the suppression in this one file (checked by GlassCrossRootTest).
 */
fun lagoonHazeState(): HazeState = HazeState().apply { positionStrategy = HazePositionStrategy.Screen }
