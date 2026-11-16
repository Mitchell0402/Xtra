package com.github.andreyasadchy.xtra.ui.download

import com.github.andreyasadchy.xtra.model.VideoQuality

/**
 * The v0.1 download dialog's rules (DownloadDialog.init, parseTime and the download button's checks) as pure
 * functions, so the sheet that replaces it (U5) names qualities, reads times and refuses ranges exactly as before.
 * Unit tested in DownloadSheetLogicTest.
 */
object DownloadSheetLogic {

    /**
     * The names in the quality list: "Source", "Audio only", "720p" (frame rates of 30 and below left out), codecs
     * when the list mixes them, and the bitrate when two qualities would share a name.
     */
    fun qualityLabels(qualities: List<VideoQuality>, sourceLabel: String, audioLabel: String): List<Pair<String, VideoQuality>> {
        val hideCodecs = qualities.all {
            val codec = it.codecs?.substringBefore('.')
            codec == "avc1" || codec == "mp4a" || codec.isNullOrBlank()
        }
        val result = mutableListOf<Pair<String, VideoQuality>>()
        qualities.forEach { quality ->
            val name = when (quality.name) {
                VideoQuality.SOURCE_QUALITY -> sourceLabel
                VideoQuality.AUDIO_ONLY_QUALITY -> audioLabel
                else -> {
                    val frameRate = quality.name?.substringAfter("p", "")?.takeWhile { it.isDigit() }?.toIntOrNull()
                    val qualityName = if (frameRate != null && frameRate <= 30) {
                        quality.name.substring(0, quality.name.indexOf('p') + 1)
                    } else {
                        quality.name.toString()
                    }
                    if (hideCodecs) {
                        qualityName
                    } else {
                        val codec = quality.codecs?.substringBefore('.')
                        val codecName = when {
                            codec == "av01" -> "AV1"
                            codec == "hev1" || codec == "hvc1" -> "H.265"
                            codec == "avc1" || codec.isNullOrBlank() -> "H.264"
                            else -> codec
                        }
                        "$qualityName $codecName"
                    }
                }
            }
            result.add((if (result.any { it.first == name }) "$name ${quality.bitrate?.div(1000)} Kbps" else name) to quality)
        }
        return result
    }

    /** "1:02:03", "62:03" is refused (minutes and seconds stop at 59), "45" is 45 seconds. Milliseconds, null when invalid. */
    fun parseTime(text: CharSequence): Long? {
        val list = text.split(':', limit = 3).reversed()
        val seconds = list.getOrNull(0)?.let { string -> string.toLongOrNull()?.takeIf { it in 0..59 } ?: return null } ?: 0
        val minutes = list.getOrNull(1)?.let { string -> string.toLongOrNull()?.takeIf { it in 0..59 } ?: return null } ?: 0
        val hours = list.getOrNull(2)?.let { string -> string.toLongOrNull() ?: return null } ?: 0
        return ((hours * 3600) + (minutes * 60) + seconds) * 1000
    }

    /** "00:05:09" for the time fields (v0.1 padded the short form with hours). */
    fun formatTime(millis: Long): String {
        val total = (millis / 1000).coerceAtLeast(0)
        return String.format("%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }

    enum class RangeError { FromInvalid, ToInvalid, ToTooLong, FromNotBeforeTo }

    /** v0.1's checks, in its order: unreadable times, an end past the video, a start not before the end. */
    fun checkRange(fromText: String, toText: String, defaultFrom: Long, total: Long): Result {
        val from = if (fromText.isEmpty()) defaultFrom else parseTime(fromText) ?: return Result(error = RangeError.FromInvalid)
        val to = if (toText.isEmpty()) total else parseTime(toText) ?: return Result(error = RangeError.ToInvalid)
        return when {
            to > total -> Result(error = RangeError.ToTooLong)
            from < to -> Result(from, to)
            else -> Result(error = RangeError.FromNotBeforeTo)
        }
    }

    data class Result(val from: Long = 0, val to: Long = 0, val error: RangeError? = null)
}

/**
 * A VOD's seek-preview sprite sheets (Twitch "storyboards", next to the animated preview the video lists already
 * carry): [images] hold [rows] x [cols] frames of [width] x [height] px, one every [interval] seconds.
 */
data class Storyboard(
    val baseUrl: String,
    val images: List<String>,
    val width: Int,
    val height: Int,
    val rows: Int,
    val cols: Int,
    val count: Int,
    val interval: Double,
) {
    /** The sheet URL and the frame's top-left corner in it for [millis] into the video. */
    fun frameAt(millis: Long): Triple<String, Int, Int>? {
        if (images.isEmpty() || rows <= 0 || cols <= 0 || count <= 0 || interval <= 0) return null
        val index = ((millis / 1000.0) / interval).toInt().coerceIn(0, count - 1)
        val perSheet = rows * cols
        val sheet = (index / perSheet).coerceAtMost(images.lastIndex)
        val within = index % perSheet
        return Triple(baseUrl + images[sheet], (within % cols) * width, (within / cols) * height)
    }

    companion object {
        /** ".../storyboards/123-strip-0.jpg" to ".../storyboards/123-info.json"; null for anything else. */
        fun infoUrl(animatedPreviewUrl: String?): String? {
            val url = animatedPreviewUrl ?: return null
            if (!url.contains("/storyboards/")) return null
            val name = url.substringAfterLast('/')
            val id = name.substringBefore("-strip", "").ifEmpty { return null }
            return url.substringBeforeLast('/') + "/$id-info.json"
        }

        /** The "high" quality entry of the info JSON (the first one when there is none), null when unreadable. */
        fun parse(infoUrl: String, json: String): Storyboard? = try {
            val array = org.json.JSONArray(json)
            val entries = (0 until array.length()).map { array.getJSONObject(it) }
            val entry = entries.find { it.optString("quality") == "high" } ?: entries.firstOrNull()
            entry?.let {
                val images = it.getJSONArray("images").let { list -> (0 until list.length()).map { i -> list.getString(i) } }
                Storyboard(
                    baseUrl = infoUrl.substringBeforeLast('/') + "/",
                    images = images,
                    width = it.getInt("width"),
                    height = it.getInt("height"),
                    rows = it.getInt("rows"),
                    cols = it.getInt("cols"),
                    count = it.getInt("count"),
                    interval = it.getDouble("interval"),
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
