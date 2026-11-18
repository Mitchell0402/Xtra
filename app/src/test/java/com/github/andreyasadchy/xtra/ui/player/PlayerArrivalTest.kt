package com.github.andreyasadchy.xtra.ui.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import com.github.andreyasadchy.xtra.ui.design.components.PressOrigin
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonMotion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Motion plan M2a: the chat's fade curve, the path from a tapped card into the slot, the tap slot, how a new content
 * arrives while a player shows, and when the cover over the video goes up and comes down. Pure rules, no player.
 */
class PlayerArrivalTest {

    private val density = Density(2.625f)

    @After
    fun tearDown() = PressOrigin.clear()

    private fun portrait() = PlayerGeometryRules.compute(
        layout = PlayerLayout.Portrait, width = 1080f, height = 2400f, density = density, statusTop = 63f,
        sideLeft = 0f, sideRight = 0f, bottomInset = 63f, imeBottom = 0f, bottomBarPx = 200f, chatOpen = true,
        chatOverlay = false, chatDisabled = false, chatWidthPx = 0, keyboardShown = false, infoHeight = 168f,
        miniOffset = Offset.Zero,
    )

    // --- item 1: the chat fades with the page ---

    @Test
    fun chatIsOpaqueMaximizedAndGoneByThirtyPercent() {
        assertEquals(1f, PlayerGeometryRules.chatAlpha(0f))
        assertEquals(0.5f, PlayerGeometryRules.chatAlpha(0.15f), 0.0001f)
        assertEquals(0f, PlayerGeometryRules.chatAlpha(PlayerGeometryRules.ChatFadeSpan))
        assertEquals(0f, PlayerGeometryRules.chatAlpha(0.6f))
        assertEquals(0f, PlayerGeometryRules.chatAlpha(1f))
    }

    @Test
    fun chatFadeNeverLeavesZeroToOne() {
        var last = 1f
        for (i in 0..100) {
            val alpha = PlayerGeometryRules.chatAlpha(i / 100f)
            assertTrue(alpha in 0f..1f)
            // the same curve both ways: falls while minimizing, so rises while maximizing
            assertTrue(alpha <= last)
            last = alpha
        }
    }

    @Test
    fun chatSlidesDownAsItFades() {
        assertEquals(0f, PlayerGeometryRules.chatShift(0f, 42f))
        assertEquals(21f, PlayerGeometryRules.chatShift(0.15f, 42f), 0.001f)
        assertEquals(42f, PlayerGeometryRules.chatShift(1f, 42f))
    }

    // --- item 3: from the card into the slot ---

    @Test
    fun fitToAspectKeepsTheWidthAndCentre() {
        val slot = Rect(0f, 63f, 1080f, 63f + 607.5f)
        val card = Rect(100f, 500f, 500f, 800f) // 4:3, taller than 16:9
        val fitted = PlayerGeometryRules.fitToAspect(card, slot)
        assertEquals(card.left, fitted.left)
        assertEquals(card.right, fitted.right)
        assertEquals(card.center.y, fitted.center.y, 0.01f)
        assertEquals(slot.height / slot.width, fitted.height / fitted.width, 0.0001f)
    }

    @Test
    fun entranceStartsOnTheCardAndEndsInTheSlot() {
        val g = portrait()
        val card = PlayerGeometryRules.fitToAspect(Rect(40f, 1200f, 1040f, 1762.5f), g.slot)
        val start = PlayerGeometryRules.videoPlacement(g, 0f, 0f, true, from = card, enter = 1f)
        assertEquals(card.width / g.slot.width, start.scale, 0.0001f)
        assertEquals(card.left - g.slot.left, start.translationX, 0.01f)
        assertEquals(card.top - g.slot.top, start.translationY, 0.01f)
        val half = PlayerGeometryRules.videoPlacement(g, 0f, 0f, true, from = card, enter = 0.5f)
        assertEquals((card.top - g.slot.top) / 2f, half.translationY, 0.01f)
        val end = PlayerGeometryRules.videoPlacement(g, 0f, 0f, true, from = card, enter = 0f)
        assertEquals(PlayerGeometryRules.videoPlacement(g, 0f, 0f, true), end)
        // the view is laid out once at the slot; the entrance only changes its transform
        assertEquals(end.width, start.width)
        assertEquals(end.height, start.height)
    }

    @Test
    fun entranceWithoutACardRisesFromTheBottom() {
        val g = portrait()
        val from = LagoonMotion.playerRiseStart(g.slot, 2400f)
        val start = PlayerGeometryRules.videoPlacement(g, 0f, 0f, true, from = from, enter = 1f)
        assertEquals(1f, start.scale, 0.0001f)
        assertEquals(0f, start.translationX, 0.01f)
        assertEquals(2400f - g.slot.top, start.translationY, 0.01f)
    }

    @Test
    fun minimizingDuringTheEntranceHeadsForTheMiniPlayer() {
        val g = portrait()
        val card = PlayerGeometryRules.fitToAspect(Rect(40f, 1200f, 1040f, 1762.5f), g.slot)
        val mini = PlayerGeometryRules.videoPlacement(g, 1f, 0f, true, from = card, enter = 0.7f)
        assertEquals(g.miniThumb.left - g.slot.left, mini.translationX, 0.5f)
        assertEquals(g.miniThumb.top - g.slot.top, mini.translationY, 0.5f)
    }

    // --- the tap slot ---

    @Test
    fun aTapIsTakenOnceWithinHalfASecond() {
        val bounds = Rect(10f, 20f, 330f, 200f)
        PressOrigin.record(bounds, "https://example.invalid/a.jpg", nowMs = 1_000L)
        val tap = PressOrigin.take(nowMs = 1_000L + PressOrigin.MaxAgeMs)
        assertNotNull(tap)
        assertEquals(bounds, tap!!.bounds)
        assertEquals("https://example.invalid/a.jpg", tap.model)
        assertNull(PressOrigin.take(nowMs = 1_100L))
    }

    @Test
    fun anOldTapIsIgnored() {
        PressOrigin.record(Rect(10f, 20f, 330f, 200f), null, nowMs = 1_000L)
        assertNull(PressOrigin.take(nowMs = 1_000L + PressOrigin.MaxAgeMs + 1))
        // and it is gone afterwards
        assertNull(PressOrigin.take(nowMs = 1_001L))
    }

    @Test
    fun anEmptyRectIsIgnored() {
        PressOrigin.record(Rect(10f, 20f, 10f, 200f), null, nowMs = 1_000L)
        assertNull(PressOrigin.take(nowMs = 1_000L))
    }

    @Test
    fun noPlayerOpensFromTheTapOrRises() {
        val tap = PressOrigin.Tap(Rect(10f, 20f, 330f, 200f), null, 0L)
        assertEquals(tap.bounds, PlayerArrivalRules.entrance(tap).from)
        assertNull(PlayerArrivalRules.entrance(null).from)
    }

    @Test
    fun twitchPlaceholdersAreNoCover() {
        assertNull(PlayerArrivalRules.coverModel("https://vod-secure.twitch.tv/_404/404_processing_320x180.png"))
        assertNull(PlayerArrivalRules.coverModel("https://static-cdn.jtvnw.net/ttv-static/404_preview-440x248.jpg"))
        assertNull(PlayerArrivalRules.coverModel(null))
        val real = "https://static-cdn.jtvnw.net/previews-ttv/live_user_x-1280x720.jpg"
        assertEquals(real, PlayerArrivalRules.coverModel(real))
    }

    // --- item 4: a player on the screen changes in place ---

    @Test
    fun aMaximizedPlayerSwapsInPlaceAndIgnoresTheTap() {
        val tap = PressOrigin.Tap(Rect(10f, 20f, 330f, 200f), null, 0L)
        assertEquals(PlayerArrivalRules.SwapPlan(startMinimized = false, expand = false), PlayerArrivalRules.swap(playerMinimized = false, tap = tap))
        assertEquals(PlayerArrivalRules.SwapPlan(startMinimized = false, expand = false), PlayerArrivalRules.swap(playerMinimized = false, tap = null))
    }

    @Test
    fun aCardTappedUnderTheMiniPlayerSwapsAndExpands() {
        val tap = PressOrigin.Tap(Rect(10f, 20f, 330f, 200f), null, 0L)
        assertEquals(PlayerArrivalRules.SwapPlan(startMinimized = true, expand = true), PlayerArrivalRules.swap(playerMinimized = true, tap = tap))
    }

    @Test
    fun aRaidUnderTheMiniPlayerStaysSmall() {
        assertEquals(PlayerArrivalRules.SwapPlan(startMinimized = true, expand = false), PlayerArrivalRules.swap(playerMinimized = true, tap = null))
    }

    // --- item 2: the cover ---

    @Test
    fun coverOnReturnOnlyWithVideoOnAndPlaying() {
        assertTrue(CoverGate.coverOnReturn(videoOn = true, playing = true))
        assertFalse(CoverGate.coverOnReturn(videoOn = false, playing = true)) // the viewer's own audio only
        assertFalse(CoverGate.coverOnReturn(videoOn = true, playing = false)) // paused
        assertFalse(CoverGate.coverOnReturn(videoOn = false, playing = false))
    }

    @Test
    fun coverShowsWhenThePictureArrivesAndFadesOnTheFirstFrame() {
        val gate = CoverGate()
        gate.arm(0L)
        assertEquals(CoverGate.Phase.Waiting, gate.phase)
        assertTrue(gate.imageReady())
        assertEquals(CoverGate.Phase.Showing, gate.phase)
        assertEquals(CoverGate.Action.Fade, gate.firstFrame())
        assertEquals(CoverGate.Phase.Idle, gate.phase)
        // a later first frame (an ad break's surface switch) does nothing
        assertEquals(CoverGate.Action.None, gate.firstFrame())
    }

    @Test
    fun aFirstFrameBeforeThePictureMeansNoCover() {
        val gate = CoverGate()
        gate.arm(0L)
        assertEquals(CoverGate.Action.None, gate.firstFrame())
        assertFalse(gate.imageReady())
        assertEquals(CoverGate.Phase.Idle, gate.phase)
    }

    @Test
    fun coverFadesAfterThreeSecondsWithoutAFrame() {
        val gate = CoverGate()
        gate.arm(1_000L)
        gate.imageReady()
        assertEquals(CoverGate.Action.None, gate.tick(1_000L + CoverGate.TimeoutMs - 1))
        assertEquals(CoverGate.Action.Fade, gate.tick(1_000L + CoverGate.TimeoutMs))
        assertEquals(CoverGate.Phase.Idle, gate.phase)
    }

    @Test
    fun aPictureAfterTheTimeoutIsNotShown() {
        val gate = CoverGate()
        gate.arm(0L)
        assertEquals(CoverGate.Action.None, gate.tick(CoverGate.TimeoutMs))
        assertFalse(gate.imageReady())
    }

    @Test
    fun pauseErrorOrAudioOnlyRemoveTheCoverAtOnce() {
        val gate = CoverGate()
        gate.arm(0L)
        gate.imageReady()
        assertEquals(CoverGate.Action.Remove, gate.cancel())
        assertEquals(CoverGate.Phase.Idle, gate.phase)
        // cancelling a cover that is still on its way just drops it
        gate.arm(0L)
        assertEquals(CoverGate.Action.None, gate.cancel())
        assertFalse(gate.imageReady())
    }
}
