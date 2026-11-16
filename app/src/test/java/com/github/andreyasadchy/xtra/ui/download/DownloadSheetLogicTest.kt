package com.github.andreyasadchy.xtra.ui.download

import com.github.andreyasadchy.xtra.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The download sheet must name qualities, read times and refuse ranges exactly as the v0.1 download dialog did, and
 * read Twitch's storyboard info for the range slider's frames.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadSheetLogicTest {

    @Test
    fun qualitiesAreNamedAsBefore() {
        val labels = DownloadSheetLogic.qualityLabels(
            listOf(
                VideoQuality(VideoQuality.SOURCE_QUALITY, codecs = "avc1.64002A"),
                VideoQuality("720p60", codecs = "avc1.4D401F"),
                VideoQuality("480p30", codecs = "avc1.4D401E"),
                VideoQuality(VideoQuality.AUDIO_ONLY_QUALITY, codecs = "mp4a.40.2"),
            ),
            "Source", "Audio only",
        ).map { it.first }
        assertEquals(listOf("Source", "720p60", "480p", "Audio only"), labels)
    }

    @Test
    fun mixedCodecsAndSameNamesGetTold() {
        val labels = DownloadSheetLogic.qualityLabels(
            listOf(
                VideoQuality("1080p60", bitrate = 8_000_000, codecs = "hev1.1.6.L120"),
                VideoQuality("1080p60", bitrate = 6_000_000, codecs = "avc1.64002A"),
                VideoQuality("1080p60", bitrate = 4_000_000, codecs = "avc1.64002A"),
            ),
            "Source", "Audio only",
        ).map { it.first }
        assertEquals(listOf("1080p60 H.265", "1080p60 H.264", "1080p60 H.264 4000 Kbps"), labels)
    }

    @Test
    fun timesAreReadAsBefore() {
        assertEquals(3723_000L, DownloadSheetLogic.parseTime("1:02:03"))
        assertEquals(45_000L, DownloadSheetLogic.parseTime("45"))
        assertEquals(600_000L, DownloadSheetLogic.parseTime("10:00"))
        assertNull(DownloadSheetLogic.parseTime("62:03"))
        assertNull(DownloadSheetLogic.parseTime("a:00"))
        assertEquals("01:02:03", DownloadSheetLogic.formatTime(3723_000L))
    }

    @Test
    fun rangesAreCheckedInTheOldOrder() {
        val total = 3600_000L
        assertEquals(DownloadSheetLogic.Result(600_000, 630_000), DownloadSheetLogic.checkRange("00:10:00", "00:10:30", 0, total))
        // empty fields: from where the video was left, to the end
        assertEquals(DownloadSheetLogic.Result(120_000, total), DownloadSheetLogic.checkRange("", "", 120_000, total))
        assertEquals(DownloadSheetLogic.RangeError.FromInvalid, DownloadSheetLogic.checkRange("x", "", 0, total).error)
        assertEquals(DownloadSheetLogic.RangeError.ToInvalid, DownloadSheetLogic.checkRange("", "1:99", 0, total).error)
        assertEquals(DownloadSheetLogic.RangeError.ToTooLong, DownloadSheetLogic.checkRange("", "2:00:00", 0, total).error)
        assertEquals(DownloadSheetLogic.RangeError.FromNotBeforeTo, DownloadSheetLogic.checkRange("20:00", "10:00", 0, total).error)
    }

    @Test
    fun storyboardsAreFoundAndRead() {
        val preview = "https://d1m7jfoe9zdc1j.cloudfront.net/abc_123/storyboards/2600000000-strip-0.jpg"
        val info = Storyboard.infoUrl(preview)
        assertEquals("https://d1m7jfoe9zdc1j.cloudfront.net/abc_123/storyboards/2600000000-info.json", info)
        assertNull(Storyboard.infoUrl("https://static-cdn.jtvnw.net/cf_vods/x/thumb/thumb0-320x180.jpg"))
        val json = """[{"count":250,"width":110,"rows":5,"images":["2600000000-low-0.jpg"],"interval":20,"quality":"low","cols":5,"height":62},
            {"count":250,"width":220,"rows":10,"images":["2600000000-high-0.jpg","2600000000-high-1.jpg","2600000000-high-2.jpg"],"interval":20,"quality":"high","cols":10,"height":124}]"""
        val board = Storyboard.parse(info!!, json)!!
        assertEquals(220, board.width)
        assertEquals(3, board.images.size)
        // 2 040 s / 20 s = frame 102: second sheet, row 0, column 2
        assertEquals(Triple("https://d1m7jfoe9zdc1j.cloudfront.net/abc_123/storyboards/2600000000-high-1.jpg", 440, 0), board.frameAt(2040_000L))
        // past the end: the last frame
        assertEquals(Triple("https://d1m7jfoe9zdc1j.cloudfront.net/abc_123/storyboards/2600000000-high-2.jpg", 9 * 220, 4 * 124), board.frameAt(99_999_000L))
        assertNull(Storyboard.parse(info, "not json"))
    }
}
