package com.github.andreyasadchy.xtra.ui.design

import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.github.andreyasadchy.xtra.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * U9 plan 4.5 item 5: the platform attributes the window theme hands to what is still drawn by the platform (window
 * and splash background, system bars, text selection, links, web views, activity animations). The values were
 * recorded before the window theme left Material (step 2); every difference since is listed and explained in the
 * U9 report. A change to styles.xml that moves one of them fails here.
 *
 * A failing run prints the current values in the assertion message.
 */
@RunWith(RobolectricTestRunner::class)
class DarkThemeAttributesTest {

    @Suppress("DEPRECATION") // the bar colour attributes are deprecated but still read on Android 10-14
    private val attrs = linkedMapOf(
        "windowBackground" to android.R.attr.windowBackground,
        "colorBackground" to android.R.attr.colorBackground,
        "statusBarColor" to android.R.attr.statusBarColor,
        "navigationBarColor" to android.R.attr.navigationBarColor,
        "windowLightStatusBar" to android.R.attr.windowLightStatusBar,
        "windowLightNavigationBar" to android.R.attr.windowLightNavigationBar,
        "enforceStatusBarContrast" to android.R.attr.enforceStatusBarContrast,
        "enforceNavigationBarContrast" to android.R.attr.enforceNavigationBarContrast,
        "colorAccent" to android.R.attr.colorAccent,
        "colorControlActivated" to android.R.attr.colorControlActivated,
        "colorControlNormal" to android.R.attr.colorControlNormal,
        "colorControlHighlight" to android.R.attr.colorControlHighlight,
        "textColorHighlight" to android.R.attr.textColorHighlight,
        "textColorLink" to android.R.attr.textColorLink,
        "textColorPrimary" to android.R.attr.textColorPrimary,
        "windowAnimationStyle" to android.R.attr.windowAnimationStyle,
        "windowSplashScreenBackground" to android.R.attr.windowSplashScreenBackground,
        "isLightTheme" to android.R.attr.isLightTheme,
        "windowIsFloating" to android.R.attr.windowIsFloating,
        "backgroundDimEnabled" to android.R.attr.backgroundDimEnabled,
        "windowNoTitle" to android.R.attr.windowNoTitle,
    )

    private fun describe(context: Context, name: String, attr: Int): String {
        val tv = TypedValue()
        if (!context.theme.resolveAttribute(attr, tv, true)) return "unset"
        return when {
            tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> "#%08X".format(tv.data)
            tv.type == TypedValue.TYPE_INT_BOOLEAN -> (tv.data != 0).toString()
            tv.type == TypedValue.TYPE_REFERENCE || tv.resourceId != 0 -> {
                val a = context.obtainStyledAttributes(intArrayOf(attr))
                try {
                    val csl = runCatching { a.getColorStateList(0) }.getOrNull()
                    when {
                        name == "windowAnimationStyle" -> context.resources.getResourceEntryName(tv.resourceId)
                        csl != null -> "#%08X".format(csl.defaultColor)
                        else -> context.resources.getResourceEntryName(tv.resourceId)
                    }
                } finally {
                    a.recycle()
                }
            }
            else -> "type${tv.type}:${tv.data}"
        }
    }

    private fun actual(style: Int): Map<String, String> {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), style)
        return attrs.mapValues { (name, attr) -> describe(context, name, attr) }
    }

    private fun check(label: String, style: Int, expected: Map<String, String>) {
        assertEquals(label, expected, actual(style))
    }

    @Test
    fun darkTheme() = check("DarkTheme", R.style.DarkTheme, DARK_THEME)

    /** U9 A4: the two dialog windows behave as with the device default theme (window shape, dim, animation)... */
    @Test
    fun dialogWindowBehavesAsBefore() {
        val keys = listOf("windowIsFloating", "backgroundDimEnabled", "windowAnimationStyle", "windowNoTitle")
        @Suppress("DEPRECATION")
        val before = actual(android.R.style.Theme_DeviceDefault_NoActionBar).filterKeys { it in keys }
        assertEquals(before.mapValues { (k, v) -> if (k == "backgroundDimEnabled" && v == "unset") "false" else v },
            actual(R.style.Theme_Lagoon_Dialog).filterKeys { it in keys }.mapValues { (k, v) -> if (k == "backgroundDimEnabled" && v == "unset") "false" else v })
    }

    /** ...but take their colours from Lagoon: same as the main window except the transparent background. */
    @Test
    fun dialogWindowColoursAreLagoons() {
        val expected = DARK_THEME + mapOf("windowBackground" to "#00000000", "backgroundDimEnabled" to "false")
        check("Theme.Lagoon.Dialog", R.style.Theme_Lagoon_Dialog, expected)
    }

    companion object {
        /**
         * Recorded on master after v0.2 (window theme Theme.Material3.Dark.NoActionBar), see the class comment; the five
         * values marked 4a changed on purpose when the parent became Theme.AppCompat.NoActionBar (U9 step 4a).
         */
        val DARK_THEME = mapOf(
            "windowBackground" to "#FF111113",
            "colorBackground" to "#FF111113",
            "statusBarColor" to "#00000000",
            "navigationBarColor" to "#00000000",
            "windowLightStatusBar" to "unset",
            "windowLightNavigationBar" to "unset",
            "enforceStatusBarContrast" to "unset",
            "enforceNavigationBarContrast" to "unset",
            "colorAccent" to "#FF5EE2C4", // 4a: was Material lavender #FFCCC2DC
            "colorControlActivated" to "#FF5EE2C4", // 4a: was #FFCCC2DC (handles, cursor)
            "colorControlNormal" to "#9EFFFFFF", // 4a: was #FFCAC4D0, now textSecondary
            "colorControlHighlight" to "#33FFFFFF",
            "textColorHighlight" to "#665EE2C4", // 4a: was purple #99D0BCFF, now accent 40 % as in Compose
            "textColorLink" to "#FF5EE2C4",
            "textColorPrimary" to "#EBFFFFFF", // 4a: was Material #FFE6E0E9, now textPrimary
            "windowAnimationStyle" to "Animation.Material.Activity",
            "windowSplashScreenBackground" to "#00000000",
            "isLightTheme" to "false",
            "windowIsFloating" to "false",
            "backgroundDimEnabled" to "unset",
            "windowNoTitle" to "true",
        )
    }
}
