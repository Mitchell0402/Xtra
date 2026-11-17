package com.github.andreyasadchy.xtra.ui.settings

import com.github.andreyasadchy.xtra.ui.settings.model.PageId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** U9 C4: the player's "Ad blocking settings" opens Settings on that page, and back from it leaves settings. */
class U9SettingsNavTest {

    @Test
    fun openedOnAPageBackLeavesSettings() {
        val nav = SettingsNav(listOf(SettingsNav.HOME, PageId.AdBlock.name)).apply { exitAtHome = true }
        assertEquals(PageId.AdBlock.name, nav.top)
        nav.open(PageId.VideoSwap)
        assertTrue(nav.back())
        assertEquals(PageId.AdBlock.name, nav.top)
        assertFalse("back from the first page closes settings", nav.back())
        assertEquals(PageId.AdBlock.name, nav.top)
    }

    @Test
    fun openedNormallyBackReturnsHome() {
        val nav = SettingsNav(listOf(SettingsNav.HOME))
        nav.openCategory(PageId.AdBlock)
        assertTrue(nav.back())
        assertEquals(SettingsNav.HOME, nav.top)
        assertFalse(nav.back())
    }
}
