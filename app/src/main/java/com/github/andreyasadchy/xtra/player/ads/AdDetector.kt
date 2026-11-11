package com.github.andreyasadchy.xtra.player.ads

/**
 * Finds Twitch server side stitched ads in a live media playlist.
 *
 * A segment is an ad when any rule matches:
 * - its title is not "live" (content segments are titled "live", stitched ads carry the ad
 *   server or creative id, e.g. "Amazon|2474283100495.0" or "4031897999"); prefetch segments
 *   have no title and are judged by the date ranges only
 * - its start time falls inside an ad date range: ID "stitched-ad-*", CLASS "twitch-stitched-ad"
 *   or any X-TV-TWITCH-AD-* attribute
 * - it belongs to a discontinuity that starts within [AD_START_TOLERANCE_US] of an ad date range
 *   and it starts before the range ends. Prefetch segments are assumed to be 2 seconds long
 *   (their real length is unknown, often 4), so an ad that so far only shows up as prefetch
 *   segments would otherwise start "later" than its segments.
 */
object AdDetector {

    private const val CONTENT_TITLE = "live"
    private const val AD_ID_PREFIX = "stitched-ad-"
    private const val AD_CLASS = "twitch-stitched-ad"
    private const val AD_ATTRIBUTE_PREFIX = "X-TV-TWITCH-AD-"
    private const val AD_START_TOLERANCE_US = 6_000_000L

    fun isAdDateRange(dateRange: AdPlaylist.DateRange): Boolean {
        return dateRange.id.startsWith(AD_ID_PREFIX)
                || dateRange.className == AD_CLASS
                || dateRange.attributes.keys.any { it.startsWith(AD_ATTRIBUTE_PREFIX) }
    }

    fun isAdSegment(playlist: AdPlaylist, segment: AdPlaylist.Segment): Boolean {
        val title = segment.title.substringBefore('|')
        if (title.isNotBlank() && title != CONTENT_TITLE) {
            return true
        }
        val playlistStart = playlist.startTimeUs ?: return false
        val segmentStart = playlistStart + segment.relativeStartTimeUs
        // where this segment's discontinuity starts, if it starts inside the playlist
        val first = playlist.segments.first()
        val discontinuityStart = if (segment.discontinuitySequence != first.discontinuitySequence) {
            playlist.segments.firstOrNull { it.discontinuitySequence == segment.discontinuitySequence }?.let { playlistStart + it.relativeStartTimeUs }
        } else null
        return playlist.dateRanges.any { dateRange ->
            if (isAdDateRange(dateRange)) {
                val start = dateRange.startDateUs
                val end = dateRange.endDateUs
                start != null && end != null && (segmentStart in start..<end
                        || (discontinuityStart != null && kotlin.math.abs(discontinuityStart - start) <= AD_START_TOLERANCE_US && segmentStart < end))
            } else false
        }
    }

    /** Ad flag for every segment, in playlist order. */
    fun adSegments(playlist: AdPlaylist): List<Boolean> = playlist.segments.map { isAdSegment(playlist, it) }

    /**
     * True when the newest segment, or any segment the player has not finished yet
     * (ends after [positionMs]), is an ad.
     */
    fun hasAds(playlist: AdPlaylist?, positionMs: Long): Boolean {
        val segments = playlist?.segments ?: return false
        val last = segments.lastOrNull() ?: return false
        if (isAdSegment(playlist, last)) {
            return true
        }
        val positionUs = positionMs * 1000
        return segments.any { it.relativeEndTimeUs > positionUs && isAdSegment(playlist, it) }
    }

    /**
     * How long until the player at [positionMs] reaches the first ad segment it has not finished
     * yet; 0 when it is already in one or there is none.
     */
    fun adStartDelayMs(playlist: AdPlaylist?, positionMs: Long): Long {
        val segments = playlist?.segments ?: return 0
        val positionUs = positionMs * 1000
        val first = segments.firstOrNull { it.relativeEndTimeUs > positionUs && isAdSegment(playlist, it) } ?: return 0
        return ((first.relativeStartTimeUs - positionUs) / 1000).coerceAtLeast(0)
    }

    /**
     * True when the newest [windowMs] of the playlist contain an ad: what a player starting at
     * the live edge would play first.
     */
    fun hasAdsNearLiveEdge(playlist: AdPlaylist?, windowMs: Long = 10_000): Boolean {
        val end = playlist?.segments?.lastOrNull()?.relativeEndTimeUs ?: return false
        return hasAds(playlist, (end / 1000 - windowMs).coerceAtLeast(0))
    }

    /**
     * How long until the player at [positionMs] has played past the last ad segment still in
     * the playlist, capped at [maxMs].
     */
    fun adEndDelayMs(playlist: AdPlaylist?, positionMs: Long, maxMs: Long = 30_000): Long {
        val segments = playlist?.segments ?: return 0
        val adEndMs = segments.filter { isAdSegment(playlist, it) }.maxOfOrNull { it.relativeEndTimeUs }?.div(1000) ?: return 0
        return (adEndMs - positionMs).coerceIn(0, maxMs)
    }
}
