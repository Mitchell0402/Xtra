package com.github.andreyasadchy.xtra.player.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real Twitch playlists from tools/ad-probe/samples (stage 0, US exit, 2026-11-11). */
class AdDetectorTest {

    private fun sample(name: String): AdPlaylist {
        val text = javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()
        return AdPlaylist.parse(text)
    }

    private fun flags(playlist: AdPlaylist) = AdDetector.adSegments(playlist).joinToString("") { if (it) "A" else "-" }

    @Test
    fun midrollWithAdTitles() {
        // 14 content segments, then 3 segments of an Amazon ad
        val playlist = sample("hutchmf_web_embed_1794413033.m3u8")
        assertEquals("-".repeat(14) + "AAA", flags(playlist))
        assertTrue(AdDetector.hasAds(playlist, 0))
    }

    @Test
    fun midrollWithNumericAdTitles() {
        assertEquals("-".repeat(14) + "AAA", flags(sample("jokerdtv_web_site_stitched-ad-1794413449-30234000000.m3u8")))
        assertEquals("-".repeat(15) + "AA", flags(sample("jokerdtv_web_site_stitched-ad-1794414593-30267000000.m3u8")))
        assertEquals("-".repeat(14) + "AAA", flags(sample("nickmercs_web_site_stitched-ad-1794413587-30334000000.m3u8")))
    }

    @Test
    fun adOnlyInPrefetchSegments() {
        // 7 content segments of ~4.17 s and a content prefetch segment, then the ad starts with a
        // discontinuity and two prefetch segments; no EXTINF segment of the ad is listed yet
        val playlist = sample("payo_web_site_stitched-ad-1794413571-15433999999.m3u8")
        assertEquals(10, playlist.segments.size)
        assertEquals("-".repeat(8) + "AA", flags(playlist))
        assertTrue(AdDetector.hasAds(playlist, 0))
    }

    @Test
    fun noAdsWithoutAdSegments() {
        val playlist = sample("hutchmf_web_embed_1794413033.m3u8")
        val contentOnly = playlist.copy(segments = playlist.segments.take(14))
        assertEquals("-".repeat(14), flags(contentOnly))
        // the ad date range alone, without segments in it, is not an ad yet
        assertFalse(AdDetector.hasAds(contentOnly, 0))
    }

    @Test
    fun noAdsInPlainPlaylist() {
        val playlist = AdPlaylist.parse(
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-PROGRAM-DATE-TIME:2026-11-11T16:00:00.000Z
            #EXTINF:2.000,live
            https://example.com/1.ts
            #EXTINF:2.000,live
            https://example.com/2.ts
            #EXT-X-TWITCH-PREFETCH:https://example.com/3.ts
            """.trimIndent()
        )
        assertEquals("---", flags(playlist))
        assertFalse(AdDetector.hasAds(playlist, 0))
        assertFalse(AdDetector.hasAds(null, 0))
    }

    @Test
    fun adEndsAfterLastAdSegment() {
        val playlist = sample("hutchmf_web_embed_1794413033.m3u8")
        // 17 segments of 2 s: the last ad segment ends at 34 s
        assertEquals(34_000, AdDetector.adEndDelayMs(playlist, 0, maxMs = 60_000))
        assertEquals(4_000, AdDetector.adEndDelayMs(playlist, 30_000))
        assertEquals(30_000, AdDetector.adEndDelayMs(playlist, 0))
        assertEquals(0, AdDetector.adEndDelayMs(playlist, 40_000))
    }

    @Test
    fun adBehindPlayerIsOverButNewestAdSegmentCounts() {
        val playlist = sample("hutchmf_web_embed_1794413033.m3u8")
        // the newest segment is an ad, so the break is still on whatever the position
        assertTrue(AdDetector.hasAds(playlist, 40_000))
        // ad segments followed by content after the 30 s ad (starts at 28 s): over once the
        // player is past the ad segments
        val adThenContent = playlist.copy(
            segments = playlist.segments + AdPlaylist.Segment("https://example.com/next.ts", "live", 60_000_000, 2_000_000, 2)
        )
        assertTrue(AdDetector.hasAds(adThenContent, 30_000))
        assertFalse(AdDetector.hasAds(adThenContent, 34_000))
    }

    @Test
    fun parsesDates() {
        assertEquals(1794413031658000L, AdPlaylist.parseDateUs("2026-11-11T16:03:51.658Z"))
        assertEquals(1794413031658000L, AdPlaylist.parseDateUs("2026-11-11T17:03:51.658+01:00"))
        assertEquals(0L, AdPlaylist.parseDateUs("1970-01-01T00:00:00Z"))
    }

    @Test
    fun parsesAdDateRange() {
        val playlist = sample("hutchmf_web_embed_1794413033.m3u8")
        // the ad and its first quartile marker (X-TV-TWITCH-AD-QUARTILE)
        assertEquals(2, playlist.dateRanges.count { AdDetector.isAdDateRange(it) })
        val ad = playlist.dateRanges.single { it.className == "twitch-stitched-ad" }
        assertEquals("stitched-ad-1794413031-30235000000", ad.id)
        assertEquals(1794413031658000L, ad.startDateUs)
        assertEquals(1794413031658000L + 30_235_000, ad.endDateUs)
        assertNotNull(playlist.startTimeUs)
    }
}
