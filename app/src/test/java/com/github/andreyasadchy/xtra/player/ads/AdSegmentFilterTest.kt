package com.github.andreyasadchy.xtra.player.ads

import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AdSegmentFilterTest {

    private fun segment(index: Int, title: String, discontinuity: Int = 0) = HlsMediaPlaylist.Segment(
        "https://example.com/$index.ts", null, title, 2_000_000, discontinuity, index * 2_000_000L,
        null, null, null, 0, -1, false, emptyList()
    )

    @Test
    fun adSegmentsBecomeGapsInPlace() {
        val segments = listOf(segment(0, "live"), segment(1, "live"), segment(2, "Amazon|1", 1), segment(3, "Amazon|1", 1), segment(4, "live", 2))
        val result = AdSegmentFilter.markAdsAsGaps(segments, emptyList(), null)
        assertEquals(listOf(false, false, true, true, false), result.map { it.hasGapTag })
        // nothing else changes: urls, order and times stay, so media sequence numbers stay valid
        assertEquals(segments.map { it.url }, result.map { it.url })
        assertEquals(segments.map { it.relativeStartTimeUs }, result.map { it.relativeStartTimeUs })
        assertEquals(4_000L, AdSegmentFilter.filteredMs)
    }

    @Test
    fun cleanPlaylistIsUntouched() {
        val segments = listOf(segment(0, "live"), segment(1, "live"))
        assertSame(segments, AdSegmentFilter.markAdsAsGaps(segments, emptyList(), null))
    }
}
