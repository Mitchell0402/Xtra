package com.github.andreyasadchy.xtra.ui.design

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.github.andreyasadchy.xtra.ui.design.components.HideOnScrollState
import com.github.andreyasadchy.xtra.ui.design.components.TopBarState
import com.github.andreyasadchy.xtra.ui.design.theme.DarkLagoonColors
import com.github.andreyasadchy.xtra.ui.design.theme.DefaultLagoonType
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonMotion
import com.github.andreyasadchy.xtra.ui.design.theme.WidthClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The token rules of plan sections 4, 5 and 10.5, checked as numbers. */
class DesignTokensTest {

    private fun contrast(fg: Color, bg: Color): Double {
        val a = fg.compositeOver(bg).luminance() + 0.05
        val b = bg.luminance() + 0.05
        return (maxOf(a, b) / minOf(a, b)).toDouble()
    }

    @Test
    fun textReachesContrastOnEveryLayer() {
        val c = DarkLagoonColors
        for (layer in listOf(c.page, c.card, c.control)) {
            for (text in listOf(c.textPrimary, c.textSecondary, c.textHint)) {
                val ratio = contrast(text, layer)
                assertTrue("text $text on $layer is $ratio:1", ratio >= 4.5)
            }
        }
        assertTrue("accent text on page", contrast(c.accent, c.page) >= 4.5)
        assertTrue("text on accent buttons", contrast(c.onAccent, c.accent) >= 4.5)
        assertTrue("danger text on card", contrast(c.danger, c.card) >= 4.5)
        assertTrue("white chip text on overlay", contrast(Color.White, c.overlay.compositeOver(Color.White)) >= 4.5)
    }

    @Test
    fun layersGetLighterStepByStep() {
        val c = DarkLagoonColors
        val steps = listOf(c.page, c.card, c.control, c.pressed).map { it.luminance() }
        assertEquals(steps.sorted(), steps)
    }

    @Test
    fun onlySixTextSizes() {
        val t = DefaultLagoonType
        val sizes = listOf(t.display, t.title, t.headline, t.body, t.bodyStrong, t.label, t.labelStrong, t.caption).map { it.fontSize.value }.toSet()
        assertEquals(setOf(28f, 22f, 17f, 15f, 13f, 11f), sizes)
    }

    @Test
    fun motionValuesMatchThePlan() {
        assertEquals(listOf(100, 200, 300, 450), listOf(LagoonMotion.INSTANT, LagoonMotion.SHORT, LagoonMotion.MEDIUM, LagoonMotion.LONG))
        val steady = LagoonMotion(reduced = false).steady<Float>() as SpringSpec<Float>
        assertEquals(0.85f, steady.dampingRatio)
        assertEquals(380f, steady.stiffness)
        val bouncy = LagoonMotion(reduced = false).bouncy<Float>() as SpringSpec<Float>
        assertEquals(0.6f, bouncy.dampingRatio)
        assertEquals(300f, bouncy.stiffness)
    }

    @Test
    fun reducedMotionSnapsMovementButKeepsFades() {
        val reduced = LagoonMotion(reduced = true)
        assertTrue(reduced.steady<Float>() is SnapSpec)
        assertTrue(reduced.bouncy<Float>() is SnapSpec)
        assertTrue(reduced.move<Float>() is SnapSpec)
        assertFalse(reduced.fade<Float>() is SnapSpec)
    }

    @Test
    fun widthClassBreakpoints() {
        assertEquals(WidthClass.Compact, WidthClass.of(411))
        assertEquals(WidthClass.Medium, WidthClass.of(600))
        assertEquals(WidthClass.Medium, WidthClass.of(839))
        assertEquals(WidthClass.Expanded, WidthClass.of(840))
    }

    @Test
    fun topBarCollapsesBeforeContentAndExpandsAtTheTop() {
        val state = TopBarState(collapseRangePx = 100f)
        val c = state.nestedScrollConnection
        // scrolling up (finger moves up) collapses the bar first
        assertEquals(Offset(0f, -60f), c.onPreScroll(Offset(0f, -60f), NestedScrollSource.UserInput))
        assertEquals(0.6f, state.collapsedFraction, 0.001f)
        c.onPreScroll(Offset(0f, -80f), NestedScrollSource.UserInput)
        assertEquals(1f, state.collapsedFraction, 0.001f)
        // scrolling down does nothing until the list is back at its top and leaves some scroll over
        assertEquals(Offset.Zero, c.onPreScroll(Offset(0f, 50f), NestedScrollSource.UserInput))
        c.onPostScroll(Offset.Zero, Offset(0f, 50f), NestedScrollSource.UserInput)
        assertEquals(0.5f, state.collapsedFraction, 0.001f)
    }

    @Test
    fun navHidesOnScrollDownUnlessLocked() {
        val state = HideOnScrollState()
        val c = state.nestedScrollConnection
        c.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        assertFalse(state.visible)
        c.onPreScroll(Offset(0f, 40f), NestedScrollSource.UserInput)
        assertTrue(state.visible)
        state.locked = true
        c.onPreScroll(Offset(0f, -100f), NestedScrollSource.UserInput)
        assertTrue(state.visible)
    }
}
