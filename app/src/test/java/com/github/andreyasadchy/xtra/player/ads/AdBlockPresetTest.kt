package com.github.andreyasadchy.xtra.player.ads

import com.github.andreyasadchy.xtra.util.C
import org.junit.Assert.assertEquals
import org.junit.Test

class AdBlockPresetTest {

    @Test
    fun defaultsAreAggressive() {
        assertEquals("aggressive", AdBlockPreset.match { _, default -> default })
    }

    @Test
    fun matchesPresetsAndCustom() {
        AdBlockPreset.entries.forEach { preset ->
            assertEquals(preset.key, AdBlockPreset.match { key, default -> preset.values[key] ?: default })
        }
        val custom = AdBlockPreset.BALANCED.values + (C.PLAYER_HIDE_ADS to false)
        assertEquals(AdBlockPreset.CUSTOM, AdBlockPreset.match { key, default -> custom[key] ?: default })
    }
}
