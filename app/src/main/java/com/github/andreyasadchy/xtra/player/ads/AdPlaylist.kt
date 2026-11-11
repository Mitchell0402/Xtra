package com.github.andreyasadchy.xtra.player.ads

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist

/**
 * The parts of a live media playlist that ad detection looks at, independent of Media3 so the
 * detector can be unit tested and used on raw playlist text (stream downloads).
 * All times are in microseconds; [startTimeUs] is the wall clock time of relative time 0.
 */
data class AdPlaylist(
    val startTimeUs: Long?,
    val segments: List<Segment>,
    val dateRanges: List<DateRange>,
) {
    data class Segment(
        val url: String,
        val title: String,
        val relativeStartTimeUs: Long,
        val durationUs: Long,
        /** Counts EXT-X-DISCONTINUITY tags before the segment; ads start and end with one. */
        val discontinuitySequence: Int = 0,
        val prefetch: Boolean = false,
    ) {
        val relativeEndTimeUs get() = relativeStartTimeUs + durationUs
    }

    data class DateRange(
        val id: String,
        val className: String,
        val startDateUs: Long?,
        val endDateUs: Long?,
        val attributes: Map<String, String>,
    )

    companion object {
        @UnstableApi
        fun from(playlist: HlsMediaPlaylist): AdPlaylist {
            return from(playlist.segments, playlist.interstitials, playlist.startTimeUs.takeIf { playlist.hasProgramDateTime })
        }

        @UnstableApi
        fun from(segments: List<HlsMediaPlaylist.Segment>, interstitials: List<HlsMediaPlaylist.Interstitial>, startTimeUs: Long?): AdPlaylist {
            return AdPlaylist(
                startTimeUs = startTimeUs,
                segments = segments.map {
                    Segment(it.url, it.title, it.relativeStartTimeUs, it.durationUs, it.relativeDiscontinuitySequence)
                },
                dateRanges = interstitials.map { dateRange ->
                    val start = dateRange.startDateUnixUs.takeIf { it != C.TIME_UNSET }
                    val end = dateRange.endDateUnixUs.takeIf { it != C.TIME_UNSET }
                        ?: start?.let { start ->
                            dateRange.durationUs.takeIf { it != C.TIME_UNSET }?.let { start + it }
                                ?: dateRange.plannedDurationUs.takeIf { it != C.TIME_UNSET }?.let { start + it }
                        }
                    val attributes = dateRange.clientDefinedAttributes.associate { it.name to (if (it.type == HlsMediaPlaylist.ClientDefinedAttribute.TYPE_DOUBLE) it.doubleValue.toString() else it.textValue) }
                    DateRange(
                        id = dateRange.id,
                        className = attributes["CLASS"] ?: "",
                        startDateUs = start,
                        endDateUs = end,
                        attributes = attributes,
                    )
                },
            )
        }

        /**
         * Parses media playlist text the way the app's HlsPlaylistParser does for ad detection:
         * EXTINF titles, program date times, DATERANGE tags and Twitch prefetch segments
         * (2 seconds each, no title).
         */
        fun parse(text: String): AdPlaylist {
            val segments = mutableListOf<Segment>()
            val dateRanges = mutableListOf<DateRange>()
            var startTimeUs: Long? = null
            var relativeTimeUs = 0L
            var durationUs = 0L
            var title = ""
            var discontinuitySequence = 0
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                when {
                    line.startsWith("#EXTINF:") -> {
                        val value = line.substringAfter(':')
                        durationUs = ((value.substringBefore(',').toDoubleOrNull() ?: 0.0) * 1_000_000).toLong()
                        title = if (value.contains(',')) value.substringAfter(',') else ""
                    }
                    line == "#EXT-X-DISCONTINUITY" -> discontinuitySequence++
                    line.startsWith("#EXT-X-PROGRAM-DATE-TIME:") -> {
                        if (startTimeUs == null) {
                            parseDateUs(line.substringAfter(':'))?.let { startTimeUs = it - relativeTimeUs }
                        }
                    }
                    line.startsWith("#EXT-X-DATERANGE:") -> {
                        val attributes = parseAttributes(line.substringAfter(':'))
                        val start = attributes["START-DATE"]?.let { parseDateUs(it) }
                        val end = attributes["END-DATE"]?.let { parseDateUs(it) }
                            ?: start?.let { start ->
                                (attributes["DURATION"] ?: attributes["PLANNED-DURATION"])?.toDoubleOrNull()?.let { start + (it * 1_000_000).toLong() }
                            }
                        dateRanges.add(
                            DateRange(
                                id = attributes["ID"] ?: "",
                                className = attributes["CLASS"] ?: "",
                                startDateUs = start,
                                endDateUs = end,
                                attributes = attributes,
                            )
                        )
                    }
                    line.startsWith("#EXT-X-TWITCH-PREFETCH:") -> {
                        segments.add(Segment(line.substringAfter(':'), "", relativeTimeUs, 2_000_000, discontinuitySequence, prefetch = true))
                        relativeTimeUs += 2_000_000
                    }
                    !line.startsWith("#") -> {
                        segments.add(Segment(line, title, relativeTimeUs, durationUs, discontinuitySequence))
                        relativeTimeUs += durationUs
                        durationUs = 0
                        title = ""
                    }
                }
            }
            return AdPlaylist(startTimeUs, segments, dateRanges)
        }

        private val dateRegex = Regex("""(\d{4})-(\d\d)-(\d\d)[Tt](\d\d):(\d\d):(\d\d)(\.\d+)?([Zz]|([+-])(\d\d):?(\d\d))?""")

        /** ISO 8601 date time to unix microseconds (java.time needs API 26). */
        fun parseDateUs(value: String): Long? {
            val match = dateRegex.matchEntire(value.trim()) ?: return null
            val g = match.groupValues
            val year = g[1].toLong()
            val month = g[2].toLong()
            val day = g[3].toLong()
            // days from civil, http://howardhinnant.github.io/date_algorithms.html
            val y = if (month <= 2) year - 1 else year
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            val days = era * 146097 + doe - 719468
            var seconds = days * 86400 + g[4].toLong() * 3600 + g[5].toLong() * 60 + g[6].toLong()
            if (g[9].isNotEmpty()) {
                val offset = g[10].toLong() * 3600 + g[11].toLong() * 60
                seconds -= if (g[9] == "+") offset else -offset
            }
            val fractionUs = g[7].takeIf { it.isNotEmpty() }?.drop(1)?.padEnd(6, '0')?.take(6)?.toLong() ?: 0
            return seconds * 1_000_000 + fractionUs
        }

        private val attributeRegex = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""")

        private fun parseAttributes(value: String): Map<String, String> {
            return attributeRegex.findAll(value).associate { match ->
                match.groupValues[1] to match.groupValues[2].removeSurrounding("\"")
            }
        }
    }
}
