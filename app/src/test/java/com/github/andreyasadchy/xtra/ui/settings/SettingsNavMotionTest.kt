package com.github.andreyasadchy.xtra.ui.settings

import com.github.andreyasadchy.xtra.ui.settings.model.SettingsCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Motion plan M5 item 4: where back goes while the gesture previews it, and how the wide layout's pages move. */
class SettingsNavMotionTest {

    private val category = SettingsCatalog.categories.first()
    private val subPage = SettingsCatalog.pages.first { it.parent != null }

    @Test
    fun backPreviewsThePageUnderneath() {
        val nav = SettingsNav(listOf(SettingsNav.HOME))
        assertFalse(nav.canGoBack())
        assertNull(nav.previous())
        nav.open(category.id)
        assertTrue(nav.canGoBack())
        assertEquals(SettingsNav.HOME, nav.previous())
        nav.previewBack()
        assertFalse(nav.forward)
        // the preview does not change the stack; the gesture's end does
        assertEquals(category.id.name, nav.top)
        assertTrue(nav.back())
        assertEquals(SettingsNav.HOME, nav.top)
    }

    @Test
    fun aPageOpenedFromElsewhereLeavesSettingsOnBack() {
        val nav = SettingsNav(listOf(SettingsNav.HOME, category.id.name)).apply { exitAtHome = true }
        assertFalse(nav.canGoBack())
        assertNull(nav.previous())
    }

    @Test
    fun widePagesKnowTheirDepthAndCategory() {
        assertEquals(1, SettingsPaneRules.depth(category.id.name))
        assertTrue(SettingsPaneRules.depth(subPage.id.name) > 1)
        assertEquals(0, SettingsPaneRules.depth(SettingsNav.HOME))
        assertEquals(0, SettingsPaneRules.categoryIndex(category.id.name))
        assertTrue(SettingsPaneRules.categoryIndex(subPage.id.name) >= 0)
        assertEquals(-1, SettingsPaneRules.categoryIndex(SettingsNav.HOME))
    }
}
