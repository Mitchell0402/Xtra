package com.github.andreyasadchy.xtra.player.ads

import com.github.andreyasadchy.xtra.player.ads.AdController.Action
import com.github.andreyasadchy.xtra.player.ads.AdController.AdNotice
import com.github.andreyasadchy.xtra.player.ads.AdController.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdControllerTest {

    private val proxy = AdSource.StreamProxy("proxy.example", 2023, null, null)
    private val swap1 = AdSource.VideoSwap("android", "autoplay")
    private val swap2 = AdSource.VideoSwap("web", "popout")

    private fun controller(vararg sources: AdSource, hideAds: Boolean = true) =
        AdController(AdSourceQueue { sources.toList() }, hideAds)

    private fun List<Action>.withoutNotices() = filterNot { it is Action.Notice }

    @Test
    fun coversAdWithFirstSourceAndRestoresAfterIt() {
        val c = controller(swap1)
        assertEquals(emptyList<Action>(), c.onPrimaryPlaylist(ads = false, restoreDelayMs = 0))
        val start = c.onPrimaryPlaylist(ads = true, restoreDelayMs = 0)
        assertEquals(
            listOf(
                Action.Notice(AdNotice.STARTING_VIDEO_SWAP),
                Action.MutePrimary,
                Action.StartSecondaryTimeout(AdController.SECONDARY_TIMEOUT_MS),
                Action.StartSecondary(swap1),
            ),
            start
        )
        assertEquals(State.Covering(swap1), c.state)
        assertTrue(c.isActive)
        assertTrue(c.isCoveringWithVideoSwap)
        // still ads: nothing new
        assertEquals(emptyList<Action>(), c.onPrimaryPlaylist(ads = true, restoreDelayMs = 0))
        // ads gone: wait until the player is past the last ad segment
        assertEquals(listOf(Action.ScheduleRestore(4000)), c.onPrimaryPlaylist(ads = false, restoreDelayMs = 4000))
        // no second schedule while one is pending
        assertEquals(emptyList<Action>(), c.onPrimaryPlaylist(ads = false, restoreDelayMs = 2000))
        assertEquals(
            listOf(Action.CancelSecondaryTimeout, Action.StopSecondary, Action.RestorePrimary),
            c.onRestoreDelayElapsed().withoutNotices()
        )
        assertEquals(State.Idle, c.state)
        assertFalse(c.isActive)
    }

    @Test
    fun adComingBackCancelsRestore() {
        val c = controller(swap1)
        c.onPrimaryPlaylist(ads = true, restoreDelayMs = 0)
        c.onPrimaryPlaylist(ads = false, restoreDelayMs = 4000)
        assertEquals(listOf(Action.CancelRestore), c.onPrimaryPlaylist(ads = true, restoreDelayMs = 0))
        assertEquals(State.Covering(swap1), c.state)
    }

    @Test
    fun fallsThroughSourcesThenHides() {
        val c = controller(proxy, swap1, swap2)
        c.onPrimaryPlaylist(ads = true, restoreDelayMs = 0)
        assertEquals(State.Covering(proxy), c.state)
        assertTrue(c.isCoveringWithProxy)
        // the proxy stream has ads too: next source, the primary stays muted
        val next = c.onSecondaryAds()
        assertEquals(listOf(Action.StartSecondaryTimeout(AdController.SECONDARY_TIMEOUT_MS), Action.StartSecondary(swap1)), next.withoutNotices())
        assertEquals(State.Covering(swap1), c.state)
        c.onSecondaryFailed()
        assertEquals(State.Covering(swap2), c.state)
        // nothing left: stop the secondary and keep the primary hidden
        val hide = c.onSecondaryAds()
        assertEquals(listOf(Action.CancelSecondaryTimeout, Action.StopSecondary), hide.withoutNotices())
        assertTrue(Action.Notice(AdNotice.WAITING_FOR_ADS) in hide)
        assertEquals(State.Hidden, c.state)
        assertTrue(c.isActive)
        // the ad ends
        assertEquals(listOf(Action.ScheduleRestore(1000)), c.onPrimaryPlaylist(ads = false, restoreDelayMs = 1000))
        assertEquals(listOf(Action.RestorePrimary), c.onRestoreDelayElapsed())
        assertEquals(State.Idle, c.state)
    }

    @Test
    fun proxyWithAdsIsSkippedForTheRestOfTheStreamSwapsAreRetried() {
        val c = controller(proxy, swap1)
        c.onPrimaryPlaylist(ads = true, restoreDelayMs = 0)
        c.onSecondaryAds() // proxy bad
        c.onSecondaryAds() // swap1 had ads this time -> hidden
        assertEquals(State.Hidden, c.state)
        c.onPrimaryPlaylist(ads = false, restoreDelayMs = 0)
        c.onRestoreDelayElapsed()
        // next break: proxy is skipped, swap1 gets another try
        c.onPrimaryPlaylist(ads = true, restoreDelayMs = 0)
        assertEquals(State.Covering(swap1), c.state)
    }

    @Test
    fun noSourcesHidesRightAway() {
        val c = controller()
        assertEquals(
            listOf(Action.Notice(AdNotice.WAITING_FOR_ADS), Action.MutePrimary),
            c.onPrimaryPlaylist(ads = true, restoreDelayMs = 0)
        )
        assertEquals(State.Hidden, c.state)
    }

    @Test
    fun hidingTurnedOffShowsTheAd() {
        val c = controller(hideAds = false)
        assertEquals(emptyList<Action>(), c.onPrimaryPlaylist(ads = true, restoreDelayMs = 0))
        assertEquals(State.Idle, c.state)
        assertFalse(c.isActive)
    }

    @Test
    fun timeoutDuringAdHidesTimeoutAfterAdRestores() {
        val c = controller(swap1)
        c.onPrimaryPlaylist(ads = true, restoreDelayMs = 0)
        assertEquals(listOf(Action.StopSecondary), c.onSecondaryTimeout().withoutNotices())
        assertEquals(State.Hidden, c.state)

        val d = controller(swap1)
        d.onPrimaryPlaylist(ads = true, restoreDelayMs = 0)
        d.onPrimaryPlaylist(ads = false, restoreDelayMs = 20_000)
        assertEquals(listOf(Action.StopSecondary, Action.CancelRestore, Action.RestorePrimary), d.onSecondaryTimeout().withoutNotices())
        assertEquals(State.Idle, d.state)
    }

    @Test
    fun lateEventsAreIgnored() {
        val c = controller(swap1)
        assertEquals(emptyList<Action>(), c.onSecondaryAds())
        assertEquals(emptyList<Action>(), c.onSecondaryFailed())
        assertEquals(emptyList<Action>(), c.onSecondaryTimeout())
        assertEquals(emptyList<Action>(), c.onRestoreDelayElapsed())
    }

    @Test
    fun resetRestoresEverything() {
        val c = controller(swap1)
        c.onPrimaryPlaylist(ads = true, restoreDelayMs = 0)
        c.onPrimaryPlaylist(ads = false, restoreDelayMs = 3000)
        assertEquals(
            listOf(Action.CancelRestore, Action.CancelSecondaryTimeout, Action.StopSecondary, Action.RestorePrimary),
            c.reset()
        )
        assertEquals(State.Idle, c.state)
        assertFalse(c.playingAds)
        assertEquals(emptyList<Action>(), c.reset())
    }
}
