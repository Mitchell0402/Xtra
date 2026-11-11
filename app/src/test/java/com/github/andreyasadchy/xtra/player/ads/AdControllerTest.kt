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
    private val swap2 = AdSource.VideoSwap("web", "highlighter")

    private fun controller(vararg sources: AdSource, hideAds: Boolean = true) =
        AdController(AdSourceQueue { sources.toList() }, hideAds)

    private fun AdController.ads(adInMs: Long = 0) = onPrimaryPlaylist(ads = true, adStartDelayMs = adInMs, restoreDelayMs = 0)
    private fun AdController.noAds(restoreInMs: Long = 0) = onPrimaryPlaylist(ads = false, adStartDelayMs = 0, restoreDelayMs = restoreInMs)

    private fun List<Action>.withoutNotices() = filterNot { it is Action.Notice }

    @Test
    fun warmsUpSecondaryAndSwitchesAtTheAdBoundary() {
        val c = controller(swap1)
        assertEquals(emptyList<Action>(), c.noAds())
        // the ad is listed 4 s before the player gets there: start the secondary off screen
        assertEquals(
            listOf(Action.Notice(AdNotice.STARTING_VIDEO_SWAP), Action.StartSecondary(swap1, show = false), Action.ScheduleBoundary(4000)),
            c.ads(adInMs = 4000)
        )
        assertEquals(State.Covering(swap1, shown = false), c.state)
        assertTrue(c.isActive)
        assertFalse(c.isCovered)
        // still ads: nothing new
        assertEquals(emptyList<Action>(), c.ads(adInMs = 2000))
        // the primary reaches the ad: mute it, show the secondary
        assertEquals(listOf(Action.MutePrimary, Action.ShowSecondary), c.onAdBoundary())
        assertTrue(c.isCovered)
        assertFalse(c.isHidingPrimaryVideo)
        assertEquals(emptyList<Action>(), c.onAdBoundary())
        // ads gone: wait until the player is past the last ad segment
        assertEquals(listOf(Action.ScheduleRestore(4000)), c.noAds(restoreInMs = 4000))
        assertEquals(emptyList<Action>(), c.noAds(restoreInMs = 2000))
        assertEquals(
            listOf(Action.StopSecondary, Action.RestorePrimary(videoHidden = false)),
            c.onRestoreDelayElapsed().withoutNotices()
        )
        assertEquals(State.Idle, c.state)
        assertFalse(c.isActive)
    }

    @Test
    fun adComingBackCancelsRestore() {
        val c = controller(swap1)
        c.ads()
        c.onAdBoundary()
        c.noAds(restoreInMs = 4000)
        assertEquals(listOf(Action.CancelRestore), c.ads())
        assertEquals(State.Covering(swap1, shown = true), c.state)
    }

    @Test
    fun fallsThroughSourcesBeforeTheBoundary() {
        val c = controller(proxy, swap1, swap2)
        c.ads(adInMs = 5000)
        assertTrue(c.isCoveringWithProxy)
        // the proxy stream has ads too: next source, still off screen
        assertEquals(listOf(Action.StartSecondary(swap1, show = false)), c.onSecondaryAds().withoutNotices())
        c.onSecondaryFailed()
        assertEquals(State.Covering(swap2, shown = false), c.state)
        // nothing left before the boundary: hide when the primary gets there
        assertEquals(listOf(Action.StopSecondary), c.onSecondaryAds().withoutNotices())
        assertEquals(State.Hidden(shown = false), c.state)
        assertEquals(listOf(Action.Notice(AdNotice.WAITING_FOR_ADS), Action.HidePrimary), c.onAdBoundary())
        assertTrue(c.isHidingPrimaryVideo)
        assertEquals(listOf(Action.ScheduleRestore(1000)), c.noAds(restoreInMs = 1000))
        assertEquals(listOf(Action.RestorePrimary(videoHidden = true)), c.onRestoreDelayElapsed())
        assertEquals(State.Idle, c.state)
    }

    @Test
    fun replacementAfterTheBoundaryGoesStraightOnScreen() {
        val c = controller(swap1, swap2)
        c.ads()
        c.onAdBoundary()
        assertEquals(listOf(Action.StartSecondary(swap2, show = true)), c.onSecondaryAds().withoutNotices())
        assertEquals(State.Covering(swap2, shown = true), c.state)
        // and when nothing is left, the primary is hidden right away
        val hide = c.onSecondaryFailed()
        assertEquals(listOf(Action.StopSecondary, Action.HidePrimary), hide.withoutNotices())
        assertTrue(Action.Notice(AdNotice.WAITING_FOR_ADS) in hide)
        assertEquals(State.Hidden(shown = true), c.state)
    }

    @Test
    fun proxyWithAdsIsSkippedForTheRestOfTheStreamSwapsAreRetried() {
        val c = controller(proxy, swap1)
        c.ads()
        c.onSecondaryAds() // proxy bad
        c.onSecondaryAds() // swap1 had ads this time -> hidden
        assertEquals(State.Hidden(shown = false), c.state)
        c.noAds()
        c.onRestoreDelayElapsed()
        // next break: proxy is skipped, swap1 gets another try
        c.ads()
        assertEquals(State.Covering(swap1, shown = false), c.state)
    }

    @Test
    fun noSourcesHidesAtTheBoundary() {
        val c = controller()
        assertEquals(listOf(Action.ScheduleBoundary(3000)), c.ads(adInMs = 3000))
        assertEquals(State.Hidden(shown = false), c.state)
        assertFalse(c.isHidingPrimaryVideo)
        c.onAdBoundary()
        assertTrue(c.isHidingPrimaryVideo)
    }

    @Test
    fun hidingTurnedOffShowsTheAd() {
        val c = controller(hideAds = false)
        assertEquals(emptyList<Action>(), c.ads())
        assertEquals(State.Idle, c.state)
        val d = controller(swap1, hideAds = false)
        d.ads()
        d.onAdBoundary()
        assertEquals(listOf(Action.StopSecondary, Action.RestorePrimary(videoHidden = false)), d.onSecondaryAds().withoutNotices())
        assertEquals(State.Idle, d.state)
    }

    @Test
    fun adOverBeforeTheBoundary() {
        val c = controller(swap1)
        c.ads(adInMs = 5000)
        c.noAds()
        assertEquals(listOf(Action.StopSecondary, Action.CancelBoundary), c.onRestoreDelayElapsed().withoutNotices())
        assertEquals(State.Idle, c.state)
    }

    @Test
    fun lateEventsAreIgnored() {
        val c = controller(swap1)
        assertEquals(emptyList<Action>(), c.onSecondaryAds())
        assertEquals(emptyList<Action>(), c.onSecondaryFailed())
        assertEquals(emptyList<Action>(), c.onAdBoundary())
        assertEquals(emptyList<Action>(), c.onRestoreDelayElapsed())
    }

    @Test
    fun resetRestoresEverything() {
        val c = controller(swap1)
        c.ads()
        c.onAdBoundary()
        c.noAds(restoreInMs = 3000)
        assertEquals(
            listOf(Action.CancelRestore, Action.CancelBoundary, Action.StopSecondary, Action.RestorePrimary(videoHidden = false)),
            c.reset()
        )
        assertEquals(State.Idle, c.state)
        assertFalse(c.playingAds)
        assertEquals(emptyList<Action>(), c.reset())
    }
}
