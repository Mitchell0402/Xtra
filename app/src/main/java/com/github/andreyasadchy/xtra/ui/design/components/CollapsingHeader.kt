package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.runtime.MutableFloatState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * A page header above swipeable segment pages that collapses under the top bar as any page's list scrolls up and
 * comes back once a list is at its top (motion plan M3, shared rule 3). The header lives outside the pager, so every
 * page drives the same [offset] through nested scrolling: 0 shown, -[range] under the bar. A page switch keeps it
 * where it is, like v0.1's app bar over a view pager.
 */
class HeaderCollapseScroll(private val offset: MutableFloatState) : NestedScrollConnection {
    /** How far the header can move: its height and the gap under it, in pixels. */
    var range = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // up: the header goes first, then the list scrolls
        if (available.y >= 0f) return Offset.Zero
        val consumed = HeaderCollapseRules.collapse(offset.floatValue, available.y, range)
        offset.floatValue += consumed
        return Offset(0f, consumed)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // down past the list's top: the header comes back, before pull to refresh gets the rest
        if (available.y <= 0f) return Offset.Zero
        val taken = HeaderCollapseRules.expand(offset.floatValue, available.y)
        offset.floatValue += taken
        return Offset(0f, taken)
    }
}

/** The header's share of a vertical scroll (pure, GamePagerTest). */
object HeaderCollapseRules {
    /** Of an upward scroll [dy] (negative), what the header at [offset] (0 shown, -range hidden) takes. */
    fun collapse(offset: Float, dy: Float, range: Float): Float = dy.coerceAtLeast(-(range + offset)).coerceAtMost(0f)

    /** Of a downward scroll [dy] the list did not use (it is at its top), what the header takes to come back. */
    fun expand(offset: Float, dy: Float): Float = dy.coerceAtMost(-offset).coerceAtLeast(0f)
}
