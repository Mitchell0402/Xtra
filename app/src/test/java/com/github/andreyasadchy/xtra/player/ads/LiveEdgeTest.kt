package com.github.andreyasadchy.xtra.player.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveEdgeTest {

    @Test
    fun catchesUpWhenTheWindowMovedPastThePlayer() {
        // logged after a real 159 s mid-roll (v0.1, 2026-11-13) and a 180 s fake one: the player
        // held its position at the ad gaps and ended up before the window start
        assertTrue(LiveEdge.needsCatchUp(positionMs = -129_623, liveOffsetMs = 160_000, targetOffsetMs = 2000))
        assertTrue(LiveEdge.needsCatchUp(positionMs = -184_099, liveOffsetMs = null, targetOffsetMs = null))
    }

    @Test
    fun catchesUpWhenStillInTheWindowButFarBehindLive() {
        // the position is still in the playlist, but the player would replay content the
        // secondary player already showed
        assertTrue(LiveEdge.needsCatchUp(positionMs = 3000, liveOffsetMs = 29_000, targetOffsetMs = 2000))
        assertTrue(LiveEdge.needsCatchUp(positionMs = 3000, liveOffsetMs = 12_001, targetOffsetMs = null))
    }

    @Test
    fun staysWhenNearLive() {
        assertFalse(LiveEdge.needsCatchUp(positionMs = 24_000, liveOffsetMs = 4000, targetOffsetMs = 2000))
        assertFalse(LiveEdge.needsCatchUp(positionMs = 20_000, liveOffsetMs = 12_000, targetOffsetMs = 2000))
        // a larger target offset (buffer setting) moves the limit with it
        assertFalse(LiveEdge.needsCatchUp(positionMs = 10_000, liveOffsetMs = 20_000, targetOffsetMs = 15_000))
    }

    @Test
    fun staysWhenTheLiveOffsetIsUnknownAndThePositionIsInTheWindow() {
        assertFalse(LiveEdge.needsCatchUp(positionMs = 0, liveOffsetMs = null, targetOffsetMs = 2000))
    }
}
