package com.github.andreyasadchy.xtra.ui.channel

import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.ui.browse.BrowseFilter
import com.github.andreyasadchy.xtra.ui.common.VideoFilters
import com.github.andreyasadchy.xtra.util.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The channel page must sort and save like v0.1's ChannelVideosFragment and ChannelClipsFragment (plan 10.1: no
 * feature lost, stored values unchanged). Each expectation is what the v0.1 code computed.
 */
class ChannelLogicTest {

    @Test
    fun segmentsFollowTheStoredListAndItsDefault() {
        assertEquals(
            listOf(ChannelSegment.About, ChannelSegment.Suggestions, ChannelSegment.Videos, ChannelSegment.Clips, ChannelSegment.Chat),
            ChannelSegment.list(null),
        )
        assertEquals(ChannelSegment.Videos, ChannelSegment.default(null, ChannelSegment.list(null)))
        // chat off, clips the default, order changed
        val pref = "2:1:1,1:0:1,3:0:0,4:0:1,0:0:1"
        val segments = ChannelSegment.list(pref)
        assertEquals(listOf(ChannelSegment.Clips, ChannelSegment.Videos, ChannelSegment.About, ChannelSegment.Suggestions), segments)
        assertEquals(ChannelSegment.Clips, ChannelSegment.default(pref, segments))
    }

    @Test
    fun aDisabledDefaultFallsBackToVideosThenTheFirst() {
        val pref = "1:0:0,2:1:0,3:0:0,4:0:1,0:0:1"
        val segments = ChannelSegment.list(pref)
        assertEquals(ChannelSegment.About, ChannelSegment.default(pref, segments))
        assertEquals(listOf(ChannelSegment.Videos), ChannelSegment.list("1:0:0,2:0:0,3:0:0,4:0:0,0:0:0"))
        assertTrue(C.DEFAULT_CHANNEL_TABS.isNotEmpty())
    }

    @Test
    fun theListsStartFromTheChannelRowOrTheDefaultRow() {
        val row = ChannelSort("123", videoSort = VideoFilters.SORT_VIEWS, videoType = VideoFilters.VIDEO_TYPE_HIGHLIGHT, clipPeriod = VideoFilters.PERIOD_MONTH)
        assertEquals(BrowseFilter(sort = VideoFilters.SORT_VIEWS, type = VideoFilters.VIDEO_TYPE_HIGHLIGHT), ChannelFilterLogic.initialVideos(row))
        assertEquals(BrowseFilter(period = VideoFilters.PERIOD_MONTH), ChannelFilterLogic.initialClips(row))
        assertEquals(BrowseFilter(), ChannelFilterLogic.initialVideos(null))
    }

    @Test
    fun effectiveValuesAreTheViewModelGetters() {
        // ChannelVideosViewModel: time, all types; ChannelClipsViewModel: a week
        assertEquals(VideoFilters.SORT_TIME, ChannelFilterLogic.effectiveVideoSort(BrowseFilter()))
        assertEquals(VideoFilters.VIDEO_TYPE_ALL, ChannelFilterLogic.effectiveType(BrowseFilter()))
        assertEquals(VideoFilters.PERIOD_WEEK, ChannelFilterLogic.effectivePeriod(BrowseFilter()))
    }

    @Test
    fun onlyAChangeReloadsTheList() {
        assertFalse(ChannelFilterLogic.videosChanged(BrowseFilter(), BrowseFilter(sort = VideoFilters.SORT_TIME, type = VideoFilters.VIDEO_TYPE_ALL)))
        assertTrue(ChannelFilterLogic.videosChanged(BrowseFilter(), BrowseFilter(sort = VideoFilters.SORT_VIEWS)))
        assertFalse(ChannelFilterLogic.clipsChanged(BrowseFilter(), BrowseFilter(period = VideoFilters.PERIOD_WEEK)))
        assertTrue(ChannelFilterLogic.clipsChanged(BrowseFilter(), BrowseFilter(period = VideoFilters.PERIOD_ALL)))
    }

    @Test
    fun savingUpdatesTheExistingRowAndKeepsTheOtherList() {
        val row = ChannelSort("123", clipPeriod = VideoFilters.PERIOD_DAY)
        val saved = ChannelFilterLogic.saveVideos(row, "123", BrowseFilter(sort = VideoFilters.SORT_VIEWS))
        assertSame(row, saved)
        assertEquals(VideoFilters.SORT_VIEWS, saved.videoSort)
        assertEquals(VideoFilters.VIDEO_TYPE_ALL, saved.videoType)
        assertEquals(VideoFilters.PERIOD_DAY, saved.clipPeriod)
        val created = ChannelFilterLogic.saveClips(null, ChannelFilterLogic.DEFAULT_ID, BrowseFilter(period = VideoFilters.PERIOD_MONTH))
        assertEquals("default", created.id)
        assertEquals(VideoFilters.PERIOD_MONTH, created.clipPeriod)
        assertEquals(null, created.videoSort)
    }
}
