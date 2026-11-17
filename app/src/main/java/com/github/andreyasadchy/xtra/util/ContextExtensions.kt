package com.github.andreyasadchy.xtra.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.use
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.andreyasadchy.xtra.R
import java.util.Locale

/** The app settings: the same file PreferenceManager.getDefaultSharedPreferences used (U8 dropped androidx.preference). */
fun Context.prefs(): SharedPreferences = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)

fun Context.tokenPrefs(): SharedPreferences = getSharedPreferences("prefs2", Context.MODE_PRIVATE)

/** Applies the app theme. Every screen uses the fixed Lagoon dark theme. */
fun Activity.applyTheme() {
    // On Android 15, wrong language is used when multiple languages are set in device settings
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        val lang = AppCompatDelegate.getApplicationLocales()
        resources.configuration.setLocale(
            if (!lang.isEmpty) {
                Locale.forLanguageTag(lang.toLanguageTags())
            } else {
                Locale.getDefault()
            }
        )
    }
    // Lagoon is dark only (plan section 4, U1): the theme, follow-system, corner, padding, compact-text and
    // Material 3 settings are no longer read or shown. Their stored values stay untouched so an older build still
    // finds them (plan 10.1); the other theme styles were deleted in U8.
    setTheme(R.style.DarkTheme)
    val isLightTheme = obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.isLightTheme)).use {
        it.getBoolean(0, false)
    }
    WindowInsetsControllerCompat(window, window.decorView).run {
        isAppearanceLightStatusBars = isLightTheme
        isAppearanceLightNavigationBars = isLightTheme
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}

/** Lagoon's own releases: the in-app update check reads the latest one (needs an .apk asset). */
const val DEFAULT_UPDATE_URL = "https://api.github.com/repos/Mitchell0402/Xtra/releases/latest"

/**
 * The update check's URL. v0.1's update settings page carried the upstream project's address as its default and
 * stored it as soon as the page was opened; that address never offers a Lagoon build, so it counts as unset and the
 * check goes to Lagoon's releases (U8, plan 10.7). The stored value itself is left alone (plan 10.1).
 */
fun SharedPreferences.updateUrl(): String =
    getString(C.UPDATE_URL, null)?.takeIf { it.isNotBlank() && !it.contains("/repos/crackededed/", ignoreCase = true) } ?: DEFAULT_UPDATE_URL
