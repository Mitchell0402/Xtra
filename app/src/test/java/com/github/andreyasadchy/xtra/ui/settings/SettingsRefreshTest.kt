package com.github.andreyasadchy.xtra.ui.settings

import com.github.andreyasadchy.xtra.ui.settings.model.Refresh
import com.github.andreyasadchy.xtra.ui.settings.model.SettingsCatalog
import com.github.andreyasadchy.xtra.ui.settings.model.TabOrderSetting
import com.github.andreyasadchy.xtra.util.C
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Motion plan M5 item 3 (N10, conservative): which settings change without rebuilding the main screen, and what the
 * main screen does with settings' result.
 */
class SettingsRefreshTest {

    private val stored = SettingsCatalog.stored.map { it.second }

    @Test
    fun onlyTheFirstFiveCheckedSettingsChangeInPlace() {
        val live = stored.filter { it.refresh == Refresh.Live }.map { it.key }.toSet()
        assertEquals(setOf(C.UI_UPTIME, C.UI_TAGS, C.UI_BROADCASTERS_COUNT, C.COMPACT_STREAMS, C.UI_BOOKMARK_TIME_LEFT), live)
    }

    @Test
    fun whatThePlayerOrChatAlsoReadStillRebuilds() {
        val byKey = stored.associateBy { it.key }
        // the player builds its options once and the chat reads these too (review 2 N5)
        assertEquals(Refresh.Recreate, byKey.getValue(C.UI_NAME_DISPLAY).refresh)
        assertEquals(Refresh.Recreate, byKey.getValue(C.UI_TRUNCATE_VIEW_COUNT).refresh)
        assertEquals(Refresh.Recreate, byKey.getValue(C.UI_DRAW_BEHIND_CUTOUTS).refresh)
        // tab order and visibility rebuild the navigation
        stored.filterIsInstance<TabOrderSetting>().forEach { assertEquals(it.key, Refresh.Recreate, it.refresh) }
    }

    @Test
    fun theMainScreenDoesOnlyWhatTheResultAsks() {
        assertEquals(Refresh.None, SettingsResult.refresh(ok = false, extra = null))
        assertEquals(Refresh.None, SettingsResult.refresh(ok = false, extra = "Recreate"))
        assertEquals(Refresh.Live, SettingsResult.refresh(ok = true, extra = "Live"))
        assertEquals(Refresh.None, SettingsResult.refresh(ok = true, extra = "None"))
        // an old result without the level, or an unknown one, rebuilds as before
        assertEquals(Refresh.Recreate, SettingsResult.refresh(ok = true, extra = null))
        assertEquals(Refresh.Recreate, SettingsResult.refresh(ok = true, extra = "Sideways"))
    }
}
