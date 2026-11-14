package com.github.andreyasadchy.xtra.lab.glass

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import com.github.andreyasadchy.xtra.ui.main.shell.GlassOverride

/**
 * Debug and benchmark builds only (src/lab): lets a launch intent force glass on or off with the extra
 * `lagoon.glass` = "on" | "off", so the benchmark module can compare both with one APK (glass plan section 7).
 * Qa and release builds have no such entry point: GlassOverride stays None there.
 */
object LabGlassOverride {
    const val EXTRA = "lagoon.glass"

    @Volatile
    var forced: Boolean? = null

    fun readIntent(intent: android.content.Intent?) {
        when (intent?.getStringExtra(EXTRA)) {
            "on" -> forced = true
            "off" -> forced = false
        }
    }
}

/** Installs the override at process start (a provider runs before any activity). */
class LabGlassInit : ContentProvider() {
    override fun onCreate(): Boolean {
        GlassOverride.current = GlassOverride { intent ->
            LabGlassOverride.readIntent(intent)
            LabGlassOverride.forced
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
