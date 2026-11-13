package com.github.andreyasadchy.xtra.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.use
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.github.andreyasadchy.xtra.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

fun Context.prefs(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

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
    // finds them (plan 10.1); the other theme styles are deleted with the old layouts in U8.
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

/** Lagoon always runs the Material 3 dark theme; the old "Material 3 theme" switch is retired (U1). */
const val USE_MATERIAL3 = true

fun Context.getAlertDialogBuilder(): AlertDialog.Builder {
    return if (USE_MATERIAL3) {
        MaterialAlertDialogBuilder(this)
    } else {
        AlertDialog.Builder(this)
    }
}