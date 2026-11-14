package com.github.andreyasadchy.xtra.ui.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.andreyasadchy.xtra.ui.settings.model.PageId
import com.github.andreyasadchy.xtra.ui.settings.model.SettingsCatalog
import com.github.andreyasadchy.xtra.ui.settings.model.SettingsPageSpec

/** One thing the settings search can find: a row, or a page itself. */
@Immutable
data class SearchEntry(
    /** The row id (storage key or action id); the page id's name for a page. */
    val id: String,
    val page: PageId,
    val title: String,
    val summary: String?,
    /** "Playback › Player buttons": where the row is. */
    val path: String,
    val icon: ImageVector?,
    val isPage: Boolean = false,
)

/**
 * Settings search (plan 7.7): every row on every page, by title or explanation, in the current language. Matches on
 * the title rank before matches on the explanation; words may come in any order ("画质 默认").
 */
object SettingsSearch {

    fun index(context: Context, environment: SettingsEnvironment): List<SearchEntry> {
        val entries = mutableListOf<SearchEntry>()
        SettingsCatalog.pages.forEach { page ->
            val path = path(context, page)
            entries += SearchEntry(page.id.name, page.id, context.getString(page.title), page.summary?.let(context::getString), parentPath(context, page), page.icon, isPage = true)
            page.items.filter { environment.allows(it.condition) }.forEach { item ->
                entries += SearchEntry(
                    id = item.id,
                    page = page.id,
                    title = context.getString(item.title),
                    summary = item.summary?.let { runCatching { context.getString(it) }.getOrNull() },
                    path = path,
                    icon = item.icon ?: page.icon,
                )
            }
        }
        return entries
    }

    private fun path(context: Context, page: SettingsPageSpec): String =
        generateSequence(page) { p -> p.parent?.let(SettingsCatalog::page) }.toList().asReversed()
            .joinToString(" › ") { context.getString(it.title) }

    private fun parentPath(context: Context, page: SettingsPageSpec): String =
        page.parent?.let { path(context, SettingsCatalog.page(it)) } ?: ""

    fun search(query: String, index: List<SearchEntry>): List<SearchEntry> {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return index
            .mapNotNull { entry ->
                val title = words.all { entry.title.contains(it, ignoreCase = true) }
                val any = title || words.all { word ->
                    entry.title.contains(word, true) || entry.summary?.contains(word, true) == true || entry.path.contains(word, true)
                }
                when {
                    title -> entry to (if (entry.isPage) 0 else 1)
                    any -> entry to 2
                    else -> null
                }
            }
            .sortedBy { it.second }
            .map { it.first }
            .distinctBy { it.page to it.id }
    }
}
