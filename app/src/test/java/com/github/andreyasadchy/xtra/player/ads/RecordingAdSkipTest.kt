package com.github.andreyasadchy.xtra.player.ads

import com.github.andreyasadchy.xtra.util.m3u8.PlaylistUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stream recordings leave out ad segments (StreamDownloadService). */
class RecordingAdSkipTest {

    private fun sample(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!

    @Test
    fun adSegmentsAreLeftOut() {
        val all = PlaylistUtils.parseMediaPlaylist(sample("hutchmf_web_embed_1794413033.m3u8"))
        val recorded = PlaylistUtils.parseMediaPlaylist(sample("hutchmf_web_embed_1794413033.m3u8"), skipAds = true)
        assertEquals(17, all.segments.size)
        assertEquals(14, recorded.segments.size)
        assertTrue(recorded.segments.all { it.title == "live" })
        assertEquals(3, recorded.skippedAdSegments)
    }

    @Test
    fun onlyAdsIsNotTheEndOfTheStream() {
        // a playlist with nothing but ad segments must keep the recording polling, not end it
        val text = """
            #EXTM3U
            #EXT-X-PROGRAM-DATE-TIME:2026-11-11T16:00:00.000Z
            #EXTINF:2.000,Amazon|1
            https://example.com/ad1.ts
            #EXTINF:2.000,Amazon|1
            https://example.com/ad2.ts
        """.trimIndent()
        val recorded = PlaylistUtils.parseMediaPlaylist(text.byteInputStream(), skipAds = true)
        assertEquals(0, recorded.segments.size)
        assertEquals(2, recorded.skippedAdSegments)
        assertEquals(false, recorded.end)
    }

    @Test
    fun contentSegmentsStay() {
        // the ad is only in prefetch segments, which recordings never download
        val recorded = PlaylistUtils.parseMediaPlaylist(sample("payo_web_site_stitched-ad-1794413571-15433999999.m3u8"), skipAds = true)
        assertEquals(7, recorded.segments.size)
    }
}
