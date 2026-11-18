package com.github.andreyasadchy.xtra.ui.player

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Motion plan M2b: the back gesture's preview, the swipe up on the mini player, which layout changes animate and how
 * the video follows them, the picture-in-picture hint, and where back goes in the player's sheet. Pure rules.
 */
class PlayerGesturesTest {

    // --- item 1: back preview ---

    @Test
    fun backPreviewShrinksAQuarterOfTheWayAtMost() {
        assertEquals(0f, PlayerGeometryRules.backPreview(0f))
        assertEquals(0.125f, PlayerGeometryRules.backPreview(0.5f), 0.0001f)
        assertEquals(PlayerGeometryRules.BackPreviewSpan, PlayerGeometryRules.backPreview(1f))
        assertEquals(PlayerGeometryRules.BackPreviewSpan, PlayerGeometryRules.backPreview(3f))
        assertEquals(0f, PlayerGeometryRules.backPreview(-1f))
    }

    // --- item 4: swipe up on the mini player ---

    @Test
    fun swipeUpFollowsTheFinger() {
        assertEquals(1f, PlayerGeometryRules.miniSwipeProgress(0f, 900f))
        assertEquals(0.5f, PlayerGeometryRules.miniSwipeProgress(-450f, 900f), 0.0001f)
        assertEquals(0f, PlayerGeometryRules.miniSwipeProgress(-2000f, 900f))
        // pulling down past the card stays at the mini player
        assertEquals(1f, PlayerGeometryRules.miniSwipeProgress(300f, 900f))
    }

    @Test
    fun swipeUpExpandsPastFifteenPercentOrOnAFling() {
        assertFalse(PlayerGeometryRules.miniSwipeExpands(share = 0.1f, velocity = 0f))
        assertTrue(PlayerGeometryRules.miniSwipeExpands(share = 0.2f, velocity = 0f))
        // a quick flick up expands even when short
        assertTrue(PlayerGeometryRules.miniSwipeExpands(share = 0.05f, velocity = -2f))
        // a flick back down springs back even when far
        assertFalse(PlayerGeometryRules.miniSwipeExpands(share = 0.5f, velocity = 2f))
    }

    // --- item 3: layout changes ---

    @Test
    fun onlyWideLayoutChangesInTheSameWindowAnimate() {
        val L = PlayerLayout.Landscape
        val T = PlayerLayout.Tablet
        assertTrue(PlayerGeometryRules.animateLayoutChange(L, L, sameWindow = true, chatWidthDragged = false, settled = true))
        // tablet full screen toggle
        assertTrue(PlayerGeometryRules.animateLayoutChange(T, L, sameWindow = true, chatWidthDragged = false, settled = true))
        // rotation
        assertFalse(PlayerGeometryRules.animateLayoutChange(PlayerLayout.Portrait, L, sameWindow = false, chatWidthDragged = false, settled = true))
        assertFalse(PlayerGeometryRules.animateLayoutChange(L, L, sameWindow = false, chatWidthDragged = false, settled = true))
        // the divider follows the finger
        assertFalse(PlayerGeometryRules.animateLayoutChange(T, T, sameWindow = true, chatWidthDragged = true, settled = true))
        // mini player, picture in picture, portrait
        assertFalse(PlayerGeometryRules.animateLayoutChange(L, L, sameWindow = true, chatWidthDragged = false, settled = false))
        assertFalse(PlayerGeometryRules.animateLayoutChange(L, PlayerLayout.Pip, sameWindow = true, chatWidthDragged = false, settled = true))
        assertFalse(PlayerGeometryRules.animateLayoutChange(PlayerLayout.Portrait, PlayerLayout.Portrait, sameWindow = true, chatWidthDragged = false, settled = true))
    }

    @Test
    fun fitRectLetterboxesInTheMiddle() {
        val r = PlayerGeometryRules.fitRect(Rect(0f, 0f, 2000f, 900f), 16f / 9f)
        assertEquals(1600f, r.width, 0.01f)
        assertEquals(900f, r.height, 0.01f)
        assertEquals(200f, r.left, 0.01f)
    }

    @Test
    fun slotTransitionEndsAsThePlainSlot() {
        val slot = Rect(0f, 0f, 1500f, 1344f)
        val at = PlayerGeometryRules.slotTransition(slot, slot, 16f / 9f, fit = true)
        assertEquals(1f, at.scale, 0.0001f)
        assertEquals(0f, at.translationX, 0.01f)
        assertEquals(0f, at.translationY, 0.01f)
        assertEquals(1500, at.width)
    }

    @Test
    fun slotTransitionDrawsThePictureWhereTheShownSlotWouldHaveIt() {
        val aspect = 16f / 9f
        val slot = Rect(0f, 0f, 1700f, 1344f) // chat opened: narrower final slot
        val shown = Rect(0f, 0f, 2992f, 1344f) // still the whole width at the start
        val placement = PlayerGeometryRules.slotTransition(slot, shown, aspect, fit = true)
        val inFinal = PlayerGeometryRules.fitRect(slot, aspect)
        val wanted = PlayerGeometryRules.fitRect(shown, aspect)
        // where the final picture's corners land after the view's scale (pivot at its top left) and translation
        val left = slot.left + placement.translationX + placement.scale * (inFinal.left - slot.left)
        val top = slot.top + placement.translationY + placement.scale * (inFinal.top - slot.top)
        assertEquals(wanted.left, left, 0.5f)
        assertEquals(wanted.top, top, 0.5f)
        assertEquals(wanted.width, inFinal.width * placement.scale, 0.5f)
        // laid out once at the final size
        assertEquals(1700, placement.width)
        assertEquals(1344, placement.height)
    }

    @Test
    fun drawnRectIsTheScaledAndMovedView() {
        val r = VideoPlacement(left = 10, top = 20, width = 1000, height = 500, scale = 1.5f, translationX = -5f, translationY = 30f).drawnRect()
        assertEquals(5f, r.left, 0.001f)
        assertEquals(50f, r.top, 0.001f)
        assertEquals(1500f, r.width, 0.001f)
        assertEquals(750f, r.height, 0.001f)
    }

    // --- item 2: picture in picture ---

    @Test
    fun pipHintIsThePictureNotTheBlackBars() {
        val placement = VideoPlacement(left = 0, top = 63, width = 1344, height = 756)
        val hint = PlayerPip.hint(placement, 4f / 3f, fit = true)!!
        assertEquals(756, hint.height)
        assertEquals(1008, hint.width)
        assertEquals(168, hint.left)
        assertEquals(63, hint.top)
        assertEquals(hint.width, hint.ratioWidth)
        assertEquals(hint.height, hint.ratioHeight)
    }

    @Test
    fun pipHintFollowsTheMiniPlayer() {
        // the maximized slot scaled into the mini thumbnail
        val placement = VideoPlacement(left = 0, top = 63, width = 1344, height = 756, scale = 0.25f, translationX = 60f, translationY = 2400f)
        val hint = PlayerPip.hint(placement, 16f / 9f, fit = true)!!
        assertEquals(60, hint.left)
        assertEquals(2463, hint.top)
        assertEquals(336, hint.width)
        assertEquals(189, hint.height)
    }

    @Test
    fun pipHintInZoomModeIsTheWholeView() {
        val placement = VideoPlacement(left = 100, top = 0, width = 2000, height = 1000)
        val hint = PlayerPip.hint(placement, 16f / 9f, fit = false)!!
        assertEquals(100, hint.left)
        assertEquals(2000, hint.width)
        assertEquals(1000, hint.height)
    }

    @Test
    fun pipHintStaysWithinAndroidsRatioLimits() {
        val wide = PlayerPip.hint(VideoPlacement(left = 0, top = 0, width = 3000, height = 500), 6f, fit = true)!!
        assertTrue(wide.ratioWidth.toFloat() / wide.ratioHeight <= PlayerPip.MaxRatio)
        val tall = PlayerPip.hint(VideoPlacement(left = 0, top = 0, width = 500, height = 3000), 0.1f, fit = true)!!
        assertTrue(tall.ratioWidth.toFloat() / tall.ratioHeight >= PlayerPip.MinRatio)
        // the hint and the ratio always have the same shape
        assertEquals(wide.width, wide.ratioWidth)
        assertEquals(tall.height, tall.ratioHeight)
    }

    @Test
    fun noPipHintWithoutAVideo() {
        assertNull(PlayerPip.hint(null, 16f / 9f, fit = true))
        assertNull(PlayerPip.hint(VideoPlacement(0, 0, 0, 0), 16f / 9f, fit = true))
        assertNull(PlayerPip.hint(VideoPlacement(0, 0, 100, 100, visible = false), 16f / 9f, fit = true))
    }

    // --- item 5: sheet pages ---

    @Test
    fun backInTheSheetReturnsToTheMenu() {
        assertEquals(PlayerSheet.Settings, PlayerSheetNav.backTarget(PlayerSheet.Quality, fromMenu = true, sleepAtFromSleep = false))
        assertNull(PlayerSheetNav.backTarget(PlayerSheet.Settings, fromMenu = true, sleepAtFromSleep = false))
        // opened from a quick button: back closes the sheet
        assertNull(PlayerSheetNav.backTarget(PlayerSheet.Quality, fromMenu = false, sleepAtFromSleep = false))
        assertEquals(PlayerSheet.Sleep, PlayerSheetNav.backTarget(PlayerSheet.SleepAt, fromMenu = false, sleepAtFromSleep = true))
    }

    @Test
    fun sheetDirection() {
        assertTrue(PlayerSheetNav.isBack(PlayerSheet.Quality, PlayerSheet.Settings))
        assertTrue(PlayerSheetNav.isBack(PlayerSheet.SleepAt, PlayerSheet.Sleep))
        assertFalse(PlayerSheetNav.isBack(PlayerSheet.Settings, PlayerSheet.Quality))
        assertFalse(PlayerSheetNav.isBack(PlayerSheet.Sleep, PlayerSheet.SleepAt))
    }
}
