package com.github.andreyasadchy.xtra.player.ads

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Blocked ad breaks, kept in the app's preferences: how many, how long, and which layer handled
 * them ("swap web/highlighter", "proxy host:port", "hidden", "filtered", "shown").
 */
object AdStats {
    private const val BREAKS = "ad_stats_breaks"
    private const val SECONDS = "ad_stats_seconds"
    private const val BY_PREFIX = "ad_stats_by_"

    data class Summary(val breaks: Int, val seconds: Long, val byHandler: Map<String, Int>)

    fun record(prefs: SharedPreferences, durationMs: Long, handler: String) {
        prefs.edit {
            putInt(BREAKS, prefs.getInt(BREAKS, 0) + 1)
            putLong(SECONDS, prefs.getLong(SECONDS, 0) + durationMs / 1000)
            putInt(BY_PREFIX + handler, prefs.getInt(BY_PREFIX + handler, 0) + 1)
        }
    }

    fun summary(prefs: SharedPreferences): Summary {
        return Summary(
            breaks = prefs.getInt(BREAKS, 0),
            seconds = prefs.getLong(SECONDS, 0),
            byHandler = prefs.all.filterKeys { it.startsWith(BY_PREFIX) }
                .mapNotNull { (key, value) -> (value as? Int)?.let { key.removePrefix(BY_PREFIX) to it } }
                .sortedByDescending { it.second }
                .toMap(),
        )
    }

    fun reset(prefs: SharedPreferences) {
        prefs.edit {
            prefs.all.keys.filter { it == BREAKS || it == SECONDS || it.startsWith(BY_PREFIX) }.forEach { remove(it) }
        }
    }
}
