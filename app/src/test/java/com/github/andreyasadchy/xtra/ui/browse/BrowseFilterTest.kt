package com.github.andreyasadchy.xtra.ui.browse

import com.github.andreyasadchy.xtra.model.ui.GameSort
import com.github.andreyasadchy.xtra.ui.common.StreamSorts
import com.github.andreyasadchy.xtra.ui.common.VideosSortDialog
import com.github.andreyasadchy.xtra.ui.game.GameSegment
import com.github.andreyasadchy.xtra.ui.search.SearchGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The new filter sheet must sort, filter and save exactly as the v0.1 dialogs and list fragments did (plan 10.6 U4:
 * "sort and filter results match the old version"). Each expectation below is what the v0.1 code computed.
 */
class BrowseFilterTest {

    @Test
    fun streamTagsAndLanguagesAreSortedAsTheOldDialogsReturnedThem() {
        val filter = FilterLogic.normalizeStreams(
            BrowseFilter(tags = listOf(FilterTag(null, "中文"), FilterTag(null, "English"), FilterTag(null, "English")), languages = listOf("ZH", "EN", "EN"))
        )
        // StreamsSortDialog: selectedTags.sortedArray(); SelectLanguagesDialog: sortedArray()
        assertEquals(listOf("English", "中文"), filter.tagNames)
        assertEquals(listOf("EN", "ZH"), filter.languages)
    }

    @Test
    fun gameTagsAreSortedByIdAndNeedOne() {
        val filter = FilterLogic.normalizeGames(
            BrowseFilter(tags = listOf(FilterTag("b", "Shooter"), FilterTag(null, "no id"), FilterTag("a", "RPG"), FilterTag("a", "RPG")))
        )
        assertEquals(listOf("a", "b"), filter.tags.map { it.id })
    }

    @Test
    fun aTagChipAddsTheTagOnce() {
        val start = BrowseFilter(sort = StreamSorts.RECENT, tags = listOf(FilterTag(null, "English")))
        val once = FilterLogic.addStreamTag(start, "Chill")
        assertEquals(listOf("Chill", "English"), once.tagNames)
        assertEquals(StreamSorts.RECENT, once.sort)
        // v0.1 appended a tag that was already there a second time
        assertEquals(once, FilterLogic.addStreamTag(once, "Chill"))
    }

    @Test
    fun theListReloadsOnlyWhenSomethingChanged() {
        val a = BrowseFilter(sort = StreamSorts.SORT_VIEWERS, languages = listOf("EN"))
        assertFalse(FilterLogic.changed(a, a.copy()))
        assertTrue(FilterLogic.changed(a, a.copy(sort = StreamSorts.RECENT)))
        assertTrue(FilterLogic.changed(a, a.copy(languages = emptyList())))
        assertTrue(FilterLogic.changed(a, a.copy(tags = listOf(FilterTag(null, "x")))))
    }

    @Test
    fun defaultsAreTheOldViewModelDefaults() {
        val none = BrowseFilter()
        assertEquals(StreamSorts.SORT_VIEWERS, FilterLogic.effectiveStreamSort(none))
        assertEquals(VideosSortDialog.SORT_VIEWS, FilterLogic.effectiveVideoSort(none))
        assertEquals(VideosSortDialog.PERIOD_WEEK, FilterLogic.effectivePeriod(none))
        assertEquals(VideosSortDialog.VIDEO_TYPE_ALL, FilterLogic.effectiveType(none))
    }

    @Test
    fun gameStreamsStartFromTheRememberedRowButTheLinkWins() {
        val saved = GameSort(id = "g1", streamSort = StreamSorts.RECENT, streamTags = "English,Chill", streamLanguages = "EN")
        val fromRow = FilterLogic.initialGameStreams(saved, null, null)
        assertEquals(StreamSorts.RECENT, fromRow.sort)
        assertEquals(listOf("English", "Chill"), fromRow.tagNames)
        assertEquals(listOf("EN"), fromRow.languages)
        val fromLink = FilterLogic.initialGameStreams(saved, listOf("Speedrun"), listOf("JA"))
        assertEquals(listOf("Speedrun"), fromLink.tagNames)
        assertEquals(listOf("JA"), fromLink.languages)
        assertEquals(BrowseFilter(), FilterLogic.initialGameStreams(null, null, null))
    }

    @Test
    fun gameVideosIgnoreTheRememberedPeriodWithoutHelixToken() {
        val saved = GameSort(id = "g1", videoSort = VideosSortDialog.SORT_TIME, videoPeriod = VideosSortDialog.PERIOD_MONTH, videoType = VideosSortDialog.VIDEO_TYPE_HIGHLIGHT, videoLanguages = "EN,DE")
        assertEquals(VideosSortDialog.PERIOD_MONTH, FilterLogic.initialGameVideos(saved, hasHelixToken = true).period)
        val noHelix = FilterLogic.initialGameVideos(saved, hasHelixToken = false)
        assertNull(noHelix.period)
        assertEquals(VideosSortDialog.SORT_TIME, noHelix.sort)
        assertEquals(VideosSortDialog.VIDEO_TYPE_HIGHLIGHT, noHelix.type)
        assertEquals(listOf("EN", "DE"), noHelix.languages)
    }

    @Test
    fun savingStreamsUpdatesTheRowInPlaceOrCreatesIt() {
        val row = GameSort(id = "g1", videoSort = VideosSortDialog.SORT_TIME)
        val filter = BrowseFilter(sort = StreamSorts.SORT_VIEWERS_ASC, tags = listOf(FilterTag(null, "A"), FilterTag(null, "B")), languages = listOf("EN"))
        val saved = FilterLogic.saveStreams(row, "g1", filter)
        assertSame(row, saved)
        assertEquals(StreamSorts.SORT_VIEWERS_ASC, saved.streamSort)
        assertEquals("A,B", saved.streamTags)
        assertEquals("EN", saved.streamLanguages)
        // the other lists' choices in the same row stay
        assertEquals(VideosSortDialog.SORT_TIME, saved.videoSort)
        val created = FilterLogic.saveStreams(null, FilterLogic.DEFAULT_ID, BrowseFilter())
        assertEquals(FilterLogic.DEFAULT_ID, created.id)
        assertEquals(StreamSorts.SORT_VIEWERS, created.streamSort)
        assertNull(created.streamTags)
        assertNull(created.streamLanguages)
    }

    @Test
    fun savingVideosWritesThePeriodOnlyWithHelixToken() {
        val filter = BrowseFilter(sort = VideosSortDialog.SORT_TIME, period = VideosSortDialog.PERIOD_DAY, type = VideosSortDialog.VIDEO_TYPE_UPLOAD)
        val withToken = FilterLogic.saveVideos(null, "g1", filter, hasHelixToken = true)
        assertEquals(VideosSortDialog.PERIOD_DAY, withToken.videoPeriod)
        val row = GameSort(id = "g1", videoPeriod = VideosSortDialog.PERIOD_ALL)
        val without = FilterLogic.saveVideos(row, "g1", filter, hasHelixToken = false)
        assertEquals(VideosSortDialog.PERIOD_ALL, without.videoPeriod)
        assertEquals(VideosSortDialog.SORT_TIME, without.videoSort)
        assertEquals(VideosSortDialog.VIDEO_TYPE_UPLOAD, without.videoType)
        assertNull(FilterLogic.saveVideos(null, "g1", filter, hasHelixToken = false).videoPeriod)
    }

    @Test
    fun savingClipsKeepsPeriodAndLanguages() {
        val saved = FilterLogic.saveClips(null, "g1", BrowseFilter(languages = listOf("EN", "JA")))
        assertEquals(VideosSortDialog.PERIOD_WEEK, saved.clipPeriod)
        assertEquals("EN,JA", saved.clipLanguages)
    }

    @Test
    fun savedFiltersNeedTagsOrLanguages() {
        assertNull(FilterLogic.savedFilter("g1", "slug", "Game", BrowseFilter(sort = StreamSorts.RECENT)))
        val kept = FilterLogic.savedFilter("g1", "slug", "Game", BrowseFilter(tags = listOf(FilterTag(null, "A")), languages = listOf("EN", "DE")))!!
        assertEquals("g1", kept.gameId)
        assertEquals("A", kept.tags)
        assertEquals("EN,DE", kept.languages)
    }

    @Test
    fun gameSegmentsFollowTheTabSetting() {
        assertEquals(listOf(GameSegment.Videos, GameSegment.Live, GameSegment.Clips), GameSegment.list(null))
        assertEquals(GameSegment.Live, GameSegment.default(null, GameSegment.list(null)))
        val custom = "2:1:1,1:0:1,0:0:0"
        assertEquals(listOf(GameSegment.Clips, GameSegment.Live), GameSegment.list(custom))
        assertEquals(GameSegment.Clips, GameSegment.default(custom, GameSegment.list(custom)))
        // everything off: v0.1 would have shown an empty pager; the page keeps Live
        assertEquals(listOf(GameSegment.Live), GameSegment.list("0:0:0,1:1:0,2:0:0"))
    }

    @Test
    fun searchGroupsPutTheDefaultTabFirstAndDropSwitchedOffOnes() {
        assertEquals(listOf(SearchGroup.Channels, SearchGroup.Videos, SearchGroup.Streams, SearchGroup.Games), SearchGroup.list(null))
        assertEquals(listOf(SearchGroup.Games, SearchGroup.Videos), SearchGroup.list("0:0:1,1:0:0,2:0:0,3:1:1"))
        assertEquals(listOf(SearchGroup.Channels), SearchGroup.list("0:0:0,1:0:0,2:0:0,3:0:0"))
    }
}
