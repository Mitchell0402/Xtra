package com.github.andreyasadchy.xtra.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Motion plan M4 item 2: the emote panel and the keyboard share one space, so the input never jumps between them. */
class ChatMotionTest {

    @Test
    fun thePanelTakesTheKeyboardsLastHeight() {
        assertEquals(812, EmotePanelRules.panelHeight(keyboardPx = 812, fallbackPx = 683))
        // before the keyboard has been up once
        assertEquals(683, EmotePanelRules.panelHeight(keyboardPx = 0, fallbackPx = 683))
    }

    @Test
    fun panelAndKeyboardTogetherKeepTheirHeight() {
        val panel = 800
        for (ime in listOf(0, 120, 400, 799, 800, 900)) {
            val space = EmotePanelRules.space(panel, ime)
            assertTrue(space >= 0)
            // the input sits on top of whichever is taller: the panel's space plus the keyboard never changes while
            // the keyboard is below the panel's height
            if (ime <= panel) assertEquals(panel, space + ime)
        }
    }

    @Test
    fun theReadingsOnTheWayDownDoNotShrinkTheRememberedHeight() {
        var height = 0
        // up: 100, 400, 800; then down: 600, 200 (the "shown" readings while it hides)
        height = EmotePanelRules.keyboardHeight(height, 100, startOfShowing = true)
        height = EmotePanelRules.keyboardHeight(height, 400, startOfShowing = false)
        height = EmotePanelRules.keyboardHeight(height, 800, startOfShowing = false)
        height = EmotePanelRules.keyboardHeight(height, 600, startOfShowing = false)
        height = EmotePanelRules.keyboardHeight(height, 200, startOfShowing = false)
        assertEquals(800, height)
        // another keyboard, shorter: a new showing starts over
        height = EmotePanelRules.keyboardHeight(height, 50, startOfShowing = true)
        height = EmotePanelRules.keyboardHeight(height, 700, startOfShowing = false)
        assertEquals(700, height)
    }

    @Test
    fun theHandOverEndsWhenTheKeyboardIsUpOrGone() {
        assertFalse(EmotePanelRules.handOffDone(imeNowPx = 300, keyboardPx = 800))
        assertTrue(EmotePanelRules.handOffDone(imeNowPx = 799, keyboardPx = 800))
        assertTrue(EmotePanelRules.handOffDone(imeNowPx = 0, keyboardPx = 800))
    }
}
