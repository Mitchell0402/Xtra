package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.runtime.Immutable

/**
 * The tab lists of v0.1 ("key:default:enabled" items joined by commas): parsing repairs a stored list the same way the
 * old drag-list dialogs did (unknown keys dropped, missing tabs put back at their default place), and [format] writes
 * the same format back.
 */
object TabOrder {

    @Immutable
    data class Tab(val key: String, val isDefault: Boolean, val enabled: Boolean)

    fun parse(value: String?, default: String): List<Tab> {
        val defaults = default.split(',')
        val list = if (value != null) {
            val kept = value.split(',').filter { item -> item.isNotEmpty() && defaults.any { it.first() == item.first() } }.toMutableList()
            defaults.forEachIndexed { index, item ->
                if (kept.none { it.first() == item.first() }) kept.add(index.coerceAtMost(kept.size), item)
            }
            kept
        } else defaults
        return list.map { item ->
            val split = item.split(':')
            Tab(split[0], split.getOrNull(1) != "0", split.getOrNull(2) != "0")
        }
    }

    fun format(tabs: List<Tab>): String =
        tabs.joinToString(",") { "${it.key}:${if (it.isDefault) "1" else "0"}:${if (it.enabled) "1" else "0"}" }

    fun move(tabs: List<Tab>, from: Int, to: Int): List<Tab> {
        if (from !in tabs.indices || to !in tabs.indices) return tabs
        return tabs.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** One default at a time; the default tab is always shown. */
    fun setDefault(tabs: List<Tab>, key: String): List<Tab> =
        tabs.map { it.copy(isDefault = it.key == key, enabled = it.enabled || it.key == key) }

    /** Hiding the default tab hands the default to the first tab still shown. */
    fun setEnabled(tabs: List<Tab>, key: String, enabled: Boolean): List<Tab> {
        val updated = tabs.map { if (it.key == key) it.copy(enabled = enabled) else it }
        if (enabled || updated.none { it.key == key && it.isDefault }) return updated
        val next = updated.firstOrNull { it.enabled }?.key ?: return updated
        return updated.map { it.copy(isDefault = it.key == next) }
    }
}
