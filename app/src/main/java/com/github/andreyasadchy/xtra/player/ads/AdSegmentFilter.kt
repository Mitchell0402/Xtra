package com.github.andreyasadchy.xtra.player.ads

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import java.util.concurrent.atomic.AtomicLong

/**
 * Layer 3: ad segments of live media playlists are turned into gaps (EXT-X-GAP), which Media3
 * never downloads or decodes. The player waits at the gap until content segments follow, so
 * whatever the other layers miss is never shown or heard. Segments keep their place, so media
 * sequence numbers stay valid.
 */
@OptIn(UnstableApi::class)
object AdSegmentFilter {

    private val filteredUs = AtomicLong()
    private val filteredUrls = object : LinkedHashMap<String, Long>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 200
    }

    /** Total length of the ad segments that were turned into gaps, counted once per segment. */
    val filteredMs get() = filteredUs.get() / 1000

    @JvmStatic
    fun markAdsAsGaps(segments: List<HlsMediaPlaylist.Segment>, interstitials: List<HlsMediaPlaylist.Interstitial>, startTimeUs: Long?): List<HlsMediaPlaylist.Segment> {
        if (segments.isEmpty()) {
            return segments
        }
        val playlist = AdPlaylist.from(segments, interstitials, startTimeUs)
        val ads = AdDetector.adSegments(playlist)
        if (ads.none { it }) {
            return segments
        }
        return segments.mapIndexed { index, segment ->
            if (ads[index] && !segment.hasGapTag) {
                count(segment)
                HlsMediaPlaylist.Segment(
                    segment.url,
                    segment.initializationSegment,
                    segment.title,
                    segment.durationUs,
                    segment.relativeDiscontinuitySequence,
                    segment.relativeStartTimeUs,
                    segment.drmInitData,
                    segment.fullSegmentEncryptionKeyUri,
                    segment.encryptionIV,
                    segment.byteRangeOffset,
                    segment.byteRangeLength,
                    /* hasGapTag= */ true,
                    segment.parts,
                )
            } else segment
        }
    }

    private fun count(segment: HlsMediaPlaylist.Segment) {
        synchronized(filteredUrls) {
            if (filteredUrls.put(segment.url, segment.durationUs) == null) {
                filteredUs.addAndGet(segment.durationUs)
            }
        }
    }
}
