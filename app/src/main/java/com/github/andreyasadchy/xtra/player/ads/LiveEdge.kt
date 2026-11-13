package com.github.andreyasadchy.xtra.player.ads

/**
 * Whether the primary player has to jump back to the live edge after an ad break.
 *
 * Ad segments are gaps (see [AdSegmentFilter]). A player that reaches them waits at the ad start
 * until the content after the ad is loaded, but the gaps still count as buffered media. On a
 * break longer than the maximum buffer (50 s by default) the gaps alone fill it, the player
 * stops loading and never gets the content: it stays at the ad start while the live window
 * moves on, ends up before the window start (negative position) and buffers forever. Seen after
 * a real 159 s mid-roll (position -129623 ms, black screen) and a 180 s fake one (position
 * -214099 ms, buffered position exactly 50 s ahead of it). Breaks shorter than the buffer play
 * through and come back at the live edge.
 *
 * Plain values only, so the rule can be tested without a player.
 */
object LiveEdge {

    /** Behind the live edge by more than this beyond the target offset: catch up. */
    const val MAX_EXTRA_LAG_MS = 10_000L

    /**
     * @param positionMs player position relative to the live window start, negative when the
     *   window has moved past it
     * @param liveOffsetMs how far the position is behind live, or null when unknown
     * @param targetOffsetMs the live offset the player aims for, or null when unknown
     */
    fun needsCatchUp(positionMs: Long, liveOffsetMs: Long?, targetOffsetMs: Long?): Boolean {
        if (positionMs < 0) {
            return true
        }
        if (liveOffsetMs == null) {
            return false
        }
        return liveOffsetMs > (targetOffsetMs ?: 0).coerceAtLeast(0) + MAX_EXTRA_LAG_MS
    }
}
