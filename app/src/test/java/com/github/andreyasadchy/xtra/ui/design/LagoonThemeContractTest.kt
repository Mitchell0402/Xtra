package com.github.andreyasadchy.xtra.ui.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.github.andreyasadchy.xtra.ui.design.theme.DarkLagoonColors
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonColors
import com.github.andreyasadchy.xtra.ui.design.theme.lagoonColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U9 plan gate 2: every Material 3 colour slot is a Lagoon token, none is a Material default. Slots are found by
 * reflection, so a slot that a later Material version adds is checked without anyone remembering to list it.
 */
class LagoonThemeContractTest {

    /** Colour-valued getters of a class (Color is an inline class, so they return a raw long). */
    private fun colorGetters(type: Class<*>) = type.methods
        .filter { it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE && it.name.startsWith("get") }
        .associateBy { it.name.removePrefix("get").substringBefore('-') }

    private fun slots(scheme: ColorScheme): Map<String, Color> =
        colorGetters(ColorScheme::class.java).mapValues { (_, m) -> Color((m.invoke(scheme) as Long).toULong()) }

    private fun tokens(colors: LagoonColors): Map<String, Color> =
        colorGetters(LagoonColors::class.java).mapValues { (_, m) -> Color((m.invoke(colors) as Long).toULong()) }

    @Test
    fun reflectionSeesEverySlot() {
        // 48 slots in material3 1.4; fewer means the reflection broke, not that slots went away
        assertTrue("found ${slots(lagoonColorScheme(DarkLagoonColors)).size} slots", slots(lagoonColorScheme(DarkLagoonColors)).size >= 48)
        assertTrue(tokens(DarkLagoonColors).containsKey("Accent"))
    }

    @Test
    fun everySlotIsALagoonToken() {
        val tokens = tokens(DarkLagoonColors)
        val values = tokens.values.toSet()
        val bad = slots(lagoonColorScheme(DarkLagoonColors)).filterValues { it !in values }
        assertEquals("slots not mapped to a Lagoon token: $bad", emptyMap<String, Color>(), bad)
    }

    @Test
    fun noSlotKeepsTheMaterialDefault() {
        val defaults = slots(darkColorScheme())
        val tokenValues = tokens(DarkLagoonColors).values.toSet()
        val left = slots(lagoonColorScheme(DarkLagoonColors)).filter { (name, value) ->
            value == defaults[name] && value !in tokenValues
        }
        assertEquals("slots still on Material's default: $left", emptyMap<String, Color>(), left)
    }
}
