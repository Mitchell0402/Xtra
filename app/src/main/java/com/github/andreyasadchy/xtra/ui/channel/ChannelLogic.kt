package com.github.andreyasadchy.xtra.ui.channel

import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.ui.browse.BrowseFilter
import com.github.andreyasadchy.xtra.ui.common.VideoFilters
import com.github.andreyasadchy.xtra.ui.main.shell.ShellTabs
import com.github.andreyasadchy.xtra.util.C

/** The segments of a channel page (setting `ui_channel_tabs`, "key:default:enabled" like the navigation list). */
enum class ChannelSegment(val key: String) {
    Suggestions("0"), Videos("1"), Clips("2"), Chat("3"), About("4");

    companion object {
        fun of(key: String) = entries.find { it.key == key }

        fun list(pref: String?): List<ChannelSegment> =
            ShellTabs.normalize(pref, C.DEFAULT_CHANNEL_TABS).map { it.split(':') }.mapNotNull { split ->
                if (split.getOrNull(2) == "0") null else of(split[0])
            }.ifEmpty { listOf(Videos) }

        /** v0.1: the tab marked as default, else Videos, else the first. */
        fun default(pref: String?, segments: List<ChannelSegment>): ChannelSegment {
            val key = ShellTabs.normalize(pref, C.DEFAULT_CHANNEL_TABS).map { it.split(':') }.find { it.getOrNull(1) != "0" }?.get(0)
            return of(key ?: "")?.takeIf { it in segments } ?: Videos.takeIf { it in segments } ?: segments.first()
        }
    }
}

/**
 * The v0.1 ChannelVideosFragment and ChannelClipsFragment sort code (VideosSortDialog with the period or the sort
 * hidden, no languages) as pure functions, so the Compose channel page sorts and saves exactly as before. The rows
 * live in `sort_channel`: one per channel id and one called "default". Unit tested in ChannelLogicTest.
 */
object ChannelFilterLogic {
    const val DEFAULT_ID = "default"

    /** The channel's own row, else the default row, as v0.1's initialize() read it. */
    fun initialVideos(saved: ChannelSort?) = BrowseFilter(sort = saved?.videoSort, type = saved?.videoType)

    fun initialClips(saved: ChannelSort?) = BrowseFilter(period = saved?.clipPeriod)

    /** ChannelVideosViewModel's getters: newest first, all types. */
    fun effectiveVideoSort(filter: BrowseFilter) = filter.sort ?: VideoFilters.SORT_TIME
    fun effectiveType(filter: BrowseFilter) = filter.type ?: VideoFilters.VIDEO_TYPE_ALL

    /** ChannelClipsViewModel's getter: the last week. */
    fun effectivePeriod(filter: BrowseFilter) = filter.period ?: VideoFilters.PERIOD_WEEK

    fun videosChanged(old: BrowseFilter, new: BrowseFilter) =
        effectiveVideoSort(old) != effectiveVideoSort(new) || effectiveType(old) != effectiveType(new)

    fun clipsChanged(old: BrowseFilter, new: BrowseFilter) = effectivePeriod(old) != effectivePeriod(new)

    /** "Save sort" / "Save default" of the videos list: sort and type into the row, creating it if needed. */
    fun saveVideos(row: ChannelSort?, id: String, filter: BrowseFilter): ChannelSort {
        val sort = effectiveVideoSort(filter)
        val type = effectiveType(filter)
        return row?.apply {
            videoSort = sort
            videoType = type
        } ?: ChannelSort(id = id, videoSort = sort, videoType = type)
    }

    fun saveClips(row: ChannelSort?, id: String, filter: BrowseFilter): ChannelSort {
        val period = effectivePeriod(filter)
        return row?.apply { clipPeriod = period } ?: ChannelSort(id = id, clipPeriod = period)
    }
}
