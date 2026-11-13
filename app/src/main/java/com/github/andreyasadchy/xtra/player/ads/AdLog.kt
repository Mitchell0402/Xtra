// trial PR: deliberate change to protected code, never merged
package com.github.andreyasadchy.xtra.player.ads

import android.util.Log

/**
 * Ad state changes and ad source failures: logcat tag [TAG] plus the last [CAPACITY] lines in
 * memory, so a debug page can show why an ad got through.
 */
object AdLog {
    const val TAG = "XtraAds"
    private const val CAPACITY = 200
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun i(message: String) {
        add(message)
        try {
            Log.i(TAG, message)
        } catch (e: RuntimeException) {
            // unit tests: android.util.Log is not available
        }
    }

    @Synchronized
    fun w(message: String, throwable: Throwable? = null) {
        add(if (throwable != null) "$message: ${throwable::class.java.simpleName} ${throwable.message}" else message)
        try {
            Log.w(TAG, message, throwable)
        } catch (e: RuntimeException) {
        }
    }

    @Synchronized
    fun lines(): List<String> = lines.toList()

    private fun add(message: String) {
        if (lines.size >= CAPACITY) {
            lines.removeFirst()
        }
        lines.addLast("${System.currentTimeMillis()} $message")
    }
}
