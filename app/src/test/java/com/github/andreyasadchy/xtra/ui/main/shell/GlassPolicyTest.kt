package com.github.andreyasadchy.xtra.ui.main.shell

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** When glass may blur (glass plan section 6): every fallback condition alone turns blur off. */
@RunWith(AndroidJUnit4::class)
class GlassPolicyTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @After
    fun reset() {
        GlassOverride.current = GlassOverride.None
        context.prefs().edit().remove(C.UI_GLASS).commit()
    }

    @Test
    fun anyConditionTurnsBlurOff() {
        assertTrue(GlassConditions().blurAllowed)
        listOf(
            GlassConditions(oldSystem = true), GlassConditions(windowBlurOff = true), GlassConditions(powerSave = true),
            GlassConditions(lowRam = true), GlassConditions(highContrastText = true), GlassConditions(settingOff = true),
        ).forEach {
            assertFalse(it.toString(), it.blurAllowed)
            assertEquals(1, it.reasons().size)
        }
    }

    @Test
    fun settingIsOnByDefaultAndSwitchesLive() {
        val policy = GlassPolicy(context)
        assertFalse(policy.conditions.settingOff)
        context.prefs().edit().putBoolean(C.UI_GLASS, false).commit()
        policy.refresh()
        assertTrue(policy.conditions.settingOff)
        assertFalse(policy.conditions.blurAllowed)
    }

    @Test
    fun benchmarkOverrideWinsOverTheSetting() {
        context.prefs().edit().putBoolean(C.UI_GLASS, false).commit()
        GlassOverride.current = GlassOverride { true }
        assertFalse(GlassPolicy(context).conditions.settingOff)
        GlassOverride.current = GlassOverride { false }
        context.prefs().edit().putBoolean(C.UI_GLASS, true).commit()
        assertTrue(GlassPolicy(context).conditions.settingOff)
    }
}
