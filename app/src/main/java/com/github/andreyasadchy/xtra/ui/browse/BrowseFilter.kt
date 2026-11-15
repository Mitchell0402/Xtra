package com.github.andreyasadchy.xtra.ui.browse

import androidx.compose.runtime.Immutable
import com.github.andreyasadchy.xtra.model.ui.GameSort
import com.github.andreyasadchy.xtra.model.ui.SavedFilter
import com.github.andreyasadchy.xtra.ui.common.StreamSorts
import com.github.andreyasadchy.xtra.ui.common.VideoFilters

/** Which list a filter belongs to; decides the sections of the filter sheet (plan 7.2). */
enum class FilterKind { Games, Streams, Videos, Clips }

/** A tag in a filter: streams filter by tag name, games by tag id (the name is only shown). */
@Immutable
data class FilterTag(val id: String?, val name: String)

/**
 * What a list is sorted and filtered by: the values the v0.1 sort dialogs handed to the lists, unchanged
 * (StreamsSortDialog, VideosSortDialog, GamesSortDialog, SelectLanguagesDialog and SearchTagsDialog).
 */
@Immutable
data class BrowseFilter(
    /** Streams: [StreamSorts.SORT_VIEWERS] and the others; videos: [VideoFilters.SORT_TIME] or views. */
    val sort: String? = null,
    /** Videos and clips: [VideoFilters.PERIOD_WEEK] and the others. */
    val period: String? = null,
    /** Videos: [VideoFilters.VIDEO_TYPE_ALL] and the others. */
    val type: String? = null,
    /** Language codes as in R.array.gqlUserLanguageValues, sorted as the language dialog sorted them. */
    val languages: List<String> = emptyList(),
    val tags: List<FilterTag> = emptyList(),
) {
    val tagNames: List<String> get() = tags.map { it.name }
}

/** What the sheet may offer besides applying: remember for this game, save as default, keep in "Filters". */
@Immutable
data class FilterSheetConfig(
    val kind: FilterKind,
    /** Videos only: v0.1 hid the period without a Helix token. */
    val showPeriod: Boolean = true,
    /** The list belongs to a game with an id (v0.1 showed "Remember choices for this game" then). */
    val canRemember: Boolean = false,
    /** This game already has remembered choices (v0.1 offered to delete them). */
    val remembered: Boolean = false,
    val canSaveDefault: Boolean = true,
    /** Streams: keep the tags and languages in the saved filters of the Me page. */
    val canSaveFilters: Boolean = false,
    /** Channel lists: v0.1's channel sort dialog had no languages. */
    val showLanguages: Boolean = true,
    /** Channel lists remember their choices for the channel, not the game (U5). */
    val rememberForChannel: Boolean = false,
)

/** The extra steps the user asked for when applying (v0.1 had one button for each). */
@Immutable
data class FilterSaves(
    val remember: Boolean = false,
    /** Delete this game's remembered choices (v0.1 "delete saved sort": the whole row of the game). */
    val forget: Boolean = false,
    val saveDefault: Boolean = false,
    val saveFilters: Boolean = false,
)

/**
 * The v0.1 lists' onChange code as pure functions, so the new pages keep sorting, filtering and saving exactly as
 * before (plan 10.6 U4: sort and filter results match the old version). Unit tested in BrowseFilterTest.
 */
object FilterLogic {
    const val DEFAULT_ID = "default"
    const val TOP_STREAMS_ID = "top_streams"

    /** v0.1 reloaded the list only when something in the dialog differed from what it opened with. */
    fun changed(old: BrowseFilter, new: BrowseFilter): Boolean =
        old.sort != new.sort || old.period != new.period || old.type != new.type || old.languages != new.languages ||
                old.tags.map { it.id to it.name } != new.tags.map { it.id to it.name }

    /** StreamsSortDialog returned the tags sorted by name, the languages dialog its codes sorted. */
    fun normalizeStreams(filter: BrowseFilter) = filter.copy(
        tags = filter.tags.distinctBy { it.name }.sortedBy { it.name },
        languages = filter.languages.distinct().sorted(),
    )

    /** GamesSortDialog returned the tags sorted by id. */
    fun normalizeGames(filter: BrowseFilter) = filter.copy(tags = filter.tags.filter { it.id != null }.distinctBy { it.id }.sortedBy { it.id })

    fun normalizeVideos(filter: BrowseFilter) = filter.copy(languages = filter.languages.distinct().sorted())

    /** The tag chips in the v0.1 lists added the tag and kept the list sorted (now without adding it twice). */
    fun addStreamTag(filter: BrowseFilter, tag: String) = normalizeStreams(filter.copy(tags = filter.tags + FilterTag(null, tag)))

    fun addGameTag(filter: BrowseFilter, tag: FilterTag) = normalizeGames(filter.copy(tags = filter.tags + tag))

    private fun List<String>.joined() = takeIf { it.isNotEmpty() }?.joinToString(",")

    private fun String?.split() = this?.split(',')?.filter { it.isNotEmpty() }.orEmpty()

    // -- what the lists start with -------------------------------------------------------------------------------

    /** Game streams: the game's remembered choices, else the defaults; tags and languages from the link win. */
    fun initialGameStreams(saved: GameSort?, argTags: List<String>?, argLanguages: List<String>?) = BrowseFilter(
        sort = saved?.streamSort,
        languages = argLanguages ?: saved?.streamLanguages.split(),
        tags = (argTags ?: saved?.streamTags.split()).map { FilterTag(null, it) },
    )

    /** Game videos: the period only counts with a Helix token. */
    fun initialGameVideos(saved: GameSort?, hasHelixToken: Boolean) = BrowseFilter(
        sort = saved?.videoSort,
        period = if (hasHelixToken) saved?.videoPeriod else null,
        type = saved?.videoType,
        languages = saved?.videoLanguages.split(),
    )

    fun initialGameClips(saved: GameSort?) = BrowseFilter(
        period = saved?.clipPeriod,
        languages = saved?.clipLanguages.split(),
    )

    /** Streams by tag ("top streams"): its own default row; tags and languages from the link win. */
    fun initialTopStreams(saved: GameSort?, argTags: List<String>?, argLanguages: List<String>?) =
        initialGameStreams(saved, argTags, argLanguages)

    // -- what the VMs are given (their getters fall back to the same defaults as v0.1) -----------------------------

    fun effectiveStreamSort(filter: BrowseFilter) = filter.sort ?: StreamSorts.SORT_VIEWERS
    fun effectiveVideoSort(filter: BrowseFilter) = filter.sort ?: VideoFilters.SORT_VIEWS
    fun effectivePeriod(filter: BrowseFilter) = filter.period ?: VideoFilters.PERIOD_WEEK
    fun effectiveType(filter: BrowseFilter) = filter.type ?: VideoFilters.VIDEO_TYPE_ALL

    // -- what gets saved -----------------------------------------------------------------------------------------

    /** GameStreamsFragment / TopStreamsFragment: save sort, tags and languages into a row, creating it if needed. */
    fun saveStreams(row: GameSort?, id: String, filter: BrowseFilter): GameSort {
        val sort = effectiveStreamSort(filter)
        return row?.apply {
            streamSort = sort
            streamTags = filter.tagNames.joined()
            streamLanguages = filter.languages.joined()
        } ?: GameSort(id = id, streamSort = sort, streamTags = filter.tagNames.joined(), streamLanguages = filter.languages.joined())
    }

    /** GameVideosFragment: the period is only written with a Helix token. */
    fun saveVideos(row: GameSort?, id: String, filter: BrowseFilter, hasHelixToken: Boolean): GameSort {
        val sort = effectiveVideoSort(filter)
        val period = effectivePeriod(filter)
        val type = effectiveType(filter)
        return row?.apply {
            videoSort = sort
            if (hasHelixToken) videoPeriod = period
            videoType = type
            videoLanguages = filter.languages.joined()
        } ?: GameSort(
            id = id, videoSort = sort, videoPeriod = if (hasHelixToken) period else null, videoType = type,
            videoLanguages = filter.languages.joined(),
        )
    }

    fun saveClips(row: GameSort?, id: String, filter: BrowseFilter): GameSort {
        val period = effectivePeriod(filter)
        return row?.apply {
            clipPeriod = period
            clipLanguages = filter.languages.joined()
        } ?: GameSort(id = id, clipPeriod = period, clipLanguages = filter.languages.joined())
    }

    /** "Save filters" kept only filters with tags or languages. */
    fun savedFilter(gameId: String?, gameSlug: String?, gameName: String?, filter: BrowseFilter): SavedFilter? =
        if (filter.tags.isEmpty() && filter.languages.isEmpty()) null
        else SavedFilter(gameId = gameId, gameSlug = gameSlug, gameName = gameName, tags = filter.tagNames.joined(), languages = filter.languages.joined())
}
