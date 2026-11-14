package com.github.andreyasadchy.xtra.ui.main.shell

import androidx.annotation.IdRes
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.C

/**
 * One item of the shell navigation (plan 7.1: Following, Browse, Me). [key] is the key used by the stored tab list
 * setting (`ui_navigation_tab_list`, "key:default:enabled" per tab); [destinationId] is the legacy navigation graph
 * destination the item opens.
 */
data class ShellTab(val key: String, @param:IdRes val destinationId: Int)

/**
 * Reads the stored tab list settings. The setting keys, their format and their defaults are unchanged from v0.1
 * (plan 10.1, rule 2); only the way the app draws them changed.
 */
object ShellTabs {
    const val KEY_BROWSE = "0"
    /** The removed Popular tab. Its entry may still be stored; it never shows. */
    const val KEY_POPULAR = "1"
    const val KEY_FOLLOWING = "2"
    const val KEY_MINE = "3"

    /** A stored "key:default:enabled" list, repaired the same way v0.1 did: unknown keys dropped, missing ones added. */
    fun normalize(pref: String?, defaults: String): List<String> {
        val defaultTabs = defaults.split(',')
        if (pref == null) return defaultTabs
        val list = pref.split(',').filter { item ->
            item.isNotEmpty() && defaultTabs.any { it.first() == item.first() }
        }.toMutableList()
        defaultTabs.forEachIndexed { index, item ->
            if (list.none { it.first() == item.first() }) {
                list.add(index.coerceAtMost(list.size), item)
            }
        }
        return list
    }

    private fun List<String>.parts() = map { it.split(':') }

    /** The navigation items in the stored order, without disabled ones and without the Popular tab. */
    fun tabs(pref: String?, savedPager: Boolean): List<ShellTab> =
        normalize(pref, C.DEFAULT_NAVIGATION_TAB_LIST).parts().mapNotNull { split ->
            if (split.getOrNull(2) == "0") return@mapNotNull null
            when (split[0]) {
                KEY_BROWSE -> ShellTab(KEY_BROWSE, R.id.rootGamesFragment)
                KEY_FOLLOWING -> ShellTab(KEY_FOLLOWING, R.id.followPagerFragment)
                KEY_MINE -> ShellTab(KEY_MINE, if (savedPager) R.id.savedPagerFragment else R.id.savedMediaFragment)
                else -> null
            }
        }

    /** v0.1 hid the whole bar when every tab was switched off. */
    fun showNavigation(pref: String?): Boolean =
        normalize(pref, C.DEFAULT_NAVIGATION_TAB_LIST).parts().any { it.getOrNull(2) != "0" }

    /** The page the app opens on: the tab marked as default, Following when none is (there is no Popular tab). */
    @IdRes
    fun startDestination(pref: String?, savedPager: Boolean): Int {
        val defaultKey = normalize(pref, C.DEFAULT_NAVIGATION_TAB_LIST).parts().find { it.getOrNull(1) != "0" }?.get(0) ?: KEY_FOLLOWING
        return when (defaultKey) {
            KEY_BROWSE -> R.id.rootGamesFragment
            KEY_MINE -> if (savedPager) R.id.savedPagerFragment else R.id.savedMediaFragment
            else -> R.id.followPagerFragment
        }
    }

    /** Which tab a destination belongs to, or -1 for pages that are not a tab root. */
    fun indexOf(tabs: List<ShellTab>, @IdRes destinationId: Int): Int {
        val id = if (destinationId == R.id.followMediaFragment) R.id.followPagerFragment else destinationId
        return tabs.indexOfFirst { it.destinationId == id }
    }
}

/** The segments of the Following page (setting `ui_following_tabs`, same format as the navigation list). */
enum class FollowingSegment(val key: String) {
    Games("0"), Live("1"), Videos("2"), Channels("3");

    companion object {
        fun of(key: String) = entries.find { it.key == key }

        /**
         * The segments in the stored order. [showVideos] is false when there is no web token: followed videos need it,
         * so v0.1 hid that tab then.
         */
        fun list(pref: String?, showVideos: Boolean): List<FollowingSegment> =
            ShellTabs.normalize(pref, C.DEFAULT_FOLLOWING_TABS).map { it.split(':') }.mapNotNull { split ->
                val segment = of(split[0]) ?: return@mapNotNull null
                if (split.getOrNull(2) == "0" || (segment == Videos && !showVideos)) null else segment
            }.ifEmpty { listOf(Live) }

        /** The segment marked as default, Live when none is or it is hidden. */
        fun default(pref: String?, segments: List<FollowingSegment>): FollowingSegment {
            val key = ShellTabs.normalize(pref, C.DEFAULT_FOLLOWING_TABS).map { it.split(':') }.find { it.getOrNull(1) != "0" }?.get(0)
            return of(key ?: "")?.takeIf { it in segments } ?: Live.takeIf { it in segments } ?: segments.first()
        }
    }
}
