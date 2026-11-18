package com.github.andreyasadchy.xtra.ui.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.ui.design.components.LocalStaggerMemory
import com.github.andreyasadchy.xtra.ui.design.components.LocalStaggerOpen
import com.github.andreyasadchy.xtra.ui.design.components.StaggerGate
import com.github.andreyasadchy.xtra.ui.design.components.StaggerMemory
import com.github.andreyasadchy.xtra.ui.design.components.StaggerWindowMs
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Motion plan M1 item 1: a list's first-load fade plays once, not again on refresh, return, or another segment's visit. */
@RunWith(AndroidJUnit4::class)
class StaggerGateInteractionTest {

    @get:Rule
    val rule = createComposeRule()

    private val opened = mutableListOf<Boolean>()

    /** One frame, after handing the test's state writes to the recomposer (the clock is paused). */
    private fun frame() {
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun opensInTheFirstFrameWithItemsThenClosesForGood() {
        var hasItems by mutableStateOf(false)
        var shown by mutableStateOf(true)
        val memory = StaggerMemory()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            LagoonTheme {
                CompositionLocalProvider(LocalStaggerMemory provides memory) {
                    if (shown) StaggerGate("live", hasItems) { opened += LocalStaggerOpen.current }
                }
            }
        }
        frame()
        assertFalse(opened.last()) // still loading: nothing to fade

        hasItems = true
        frame()
        assertTrue(opened.last()) // the very frame the items arrive is already open
        assertEquals(listOf("live"), memory.shown.toList())

        rule.mainClock.advanceTimeBy(StaggerWindowMs + 100)
        assertFalse(opened.last())

        // a refresh that empties and refills the list, and the list leaving and coming back: no replay
        hasItems = false
        frame()
        hasItems = true
        frame()
        assertFalse(opened.last())
        shown = false
        frame()
        shown = true
        frame()
        assertFalse(opened.last())
    }

    @Test
    fun eachListOfAPageFadesOnce() {
        var key by mutableStateOf("live")
        val memory = StaggerMemory()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            LagoonTheme {
                CompositionLocalProvider(LocalStaggerMemory provides memory) {
                    StaggerGate(key, hasItems = true) { opened += LocalStaggerOpen.current }
                }
            }
        }
        frame()
        assertTrue(opened.last())
        rule.mainClock.advanceTimeBy(StaggerWindowMs + 100)
        key = "videos" // first visit of another segment
        frame()
        assertTrue(opened.last())
        rule.mainClock.advanceTimeBy(StaggerWindowMs + 100)
        key = "live" // back to the first one
        frame()
        assertFalse(opened.last())
    }

    @Test
    fun theMemorySurvivesSavedState() {
        val memory = StaggerMemory(listOf("bookmarks"))
        val saved = with(StaggerMemory.Saver) { SaverScope { true }.save(memory) }!!
        val restored = StaggerMemory.Saver.restore(saved)!!
        assertEquals(listOf("bookmarks"), restored.shown.toList())
    }
}
