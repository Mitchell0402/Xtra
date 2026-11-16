package com.github.andreyasadchy.xtra.ui.saved

import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.ui.saved.bookmarks.BookmarkSorts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

/**
 * The Me page must list, sort and offer the same actions as v0.1's bookmarks and downloads lists (plan 10.1: no
 * feature lost). Each expectation is what the v0.1 adapters and fragments computed.
 */
class SavedLogicTest {

    private val now = Instant.parse("2026-11-15T12:00:00Z").toEpochMilliseconds()

    private fun bookmark(id: Int, created: String?, type: String? = "archive", userType: String? = null) =
        Bookmark(videoId = "v$id", createdAt = created, type = type, userType = userType).apply { this.id = id }

    @Test
    fun segmentsFollowTheStoredListAndItsDefault() {
        assertEquals(listOf(SavedSegment.Bookmarks, SavedSegment.Downloads, SavedSegment.Filters), SavedSegment.list(null))
        // v0.1 default: bookmarks ("0:1:1")
        assertEquals(SavedSegment.Bookmarks, SavedSegment.default(null, SavedSegment.list(null)))
        val pref = "0:0:0,1:1:1,2:0:1"
        assertEquals(listOf(SavedSegment.Downloads, SavedSegment.Filters), SavedSegment.list(pref))
        assertEquals(SavedSegment.Downloads, SavedSegment.default(pref, SavedSegment.list(pref)))
    }

    @Test
    fun pastBroadcastsAreKeptSevenFourteenOrSixtyDays() {
        val created = "2026-11-10T12:00:00Z"
        assertEquals(2L * 86400, BookmarkLogic.secondsLeft(bookmark(1, created), now))
        assertEquals(9L * 86400, BookmarkLogic.secondsLeft(bookmark(1, created, userType = "affiliate"), now))
        assertEquals(55L * 86400, BookmarkLogic.secondsLeft(bookmark(1, created, userType = "partner"), now))
        assertNull(BookmarkLogic.secondsLeft(bookmark(1, created, type = "highlight"), now))
        assertNull(BookmarkLogic.secondsLeft(bookmark(1, "2026-11-01T12:00:00Z"), now))
    }

    @Test
    fun bookmarksSortAsTheOldListDid() {
        val a = bookmark(1, "2026-11-10T12:00:00Z")
        val b = bookmark(2, "2026-11-12T12:00:00Z", type = "upload")
        val c = bookmark(3, null)
        // saved date is the row id
        assertEquals(listOf(3, 2, 1), BookmarkLogic.sorted(listOf(a, b, c), BookmarkSorts.SORT_SAVED_AT, BookmarkSorts.ORDER_DESC, now).map { it.id })
        assertEquals(listOf(1, 2, 3), BookmarkLogic.sorted(listOf(c, b, a), BookmarkSorts.SORT_SAVED_AT, BookmarkSorts.ORDER_ASC, now).map { it.id })
        // created date: unknown last either way (v0.1 reversed a nulls-first comparator)
        assertEquals(listOf(2, 1, 3), BookmarkLogic.sorted(listOf(a, b, c), BookmarkSorts.SORT_CREATED_AT, BookmarkSorts.ORDER_DESC, now).map { it.id })
        assertEquals(listOf(1, 2, 3), BookmarkLogic.sorted(listOf(c, b, a), BookmarkSorts.SORT_CREATED_AT, BookmarkSorts.ORDER_ASC, now).map { it.id })
        // deletion date: only past broadcasts have one
        assertEquals(listOf(1, 2, 3), BookmarkLogic.sorted(listOf(b, a, c), BookmarkSorts.SORT_EXPIRES_AT, BookmarkSorts.ORDER_ASC, now).map { it.id })
        assertEquals(listOf(1, 2, 3), BookmarkLogic.sorted(listOf(b, a, c), BookmarkSorts.SORT_EXPIRES_AT, BookmarkSorts.ORDER_DESC, now).map { it.id })
    }

    @Test
    fun timeLeftIsShownToTheTwoLargestUnits() {
        assertEquals(2L * 86400 + 3 * 3600, BookmarkLogic.roughly(2L * 86400 + 3 * 3600 + 25 * 60 + 7))
        assertEquals(5L * 3600 + 25 * 60, BookmarkLogic.roughly(5L * 3600 + 25 * 60 + 7))
        assertEquals(125L, BookmarkLogic.roughly(125))
    }

    private fun video(status: Int, live: Boolean = false, url: String? = "/data/video.m3u8") =
        OfflineVideo(url = url, status = status, live = live)

    @Test
    fun aDownloadTheServiceDoesNotReportIsPending() {
        assertEquals(OfflineVideo.STATUS_PENDING, DownloadLogic.shownStatus(video(OfflineVideo.STATUS_DOWNLOADING), active = false))
        assertEquals(OfflineVideo.STATUS_DOWNLOADING, DownloadLogic.shownStatus(video(OfflineVideo.STATUS_DOWNLOADING), active = true))
        assertEquals(OfflineVideo.STATUS_DOWNLOADED, DownloadLogic.shownStatus(video(OfflineVideo.STATUS_DOWNLOADED), active = false))
    }

    @Test
    fun theMenuOffersWhatTheOldMenuOffered() {
        assertEquals(listOf(DownloadAction.Stop, DownloadAction.Delete), DownloadLogic.actions(video(OfflineVideo.STATUS_DOWNLOADING), OfflineVideo.STATUS_DOWNLOADING))
        assertEquals(listOf(DownloadAction.Resume, DownloadAction.Delete), DownloadLogic.actions(video(OfflineVideo.STATUS_PENDING), OfflineVideo.STATUS_PENDING))
        assertEquals(listOf(DownloadAction.Stop, DownloadAction.Resume, DownloadAction.Delete), DownloadLogic.actions(video(OfflineVideo.STATUS_PENDING, live = true), OfflineVideo.STATUS_PENDING))
        assertEquals(
            listOf(DownloadAction.MoveToShared, DownloadAction.Convert, DownloadAction.ChatFile, DownloadAction.Delete),
            DownloadLogic.actions(video(OfflineVideo.STATUS_DOWNLOADED), OfflineVideo.STATUS_DOWNLOADED),
        )
        val shared = video(OfflineVideo.STATUS_DOWNLOADED, url = "content://com.android.externalstorage/tree/a/b.mp4")
        assertEquals(
            listOf(DownloadAction.MoveToApp, DownloadAction.ChatFile, DownloadAction.Share, DownloadAction.Delete),
            DownloadLogic.actions(shared, OfflineVideo.STATUS_DOWNLOADED),
        )
    }

    @Test
    fun aWatchedDownloadStartsFromTheBeginning() {
        assertEquals(0L, DownloadLogic.startPosition(OfflineVideo(duration = 1000, lastWatchPosition = 1000)))
        assertNull(DownloadLogic.startPosition(OfflineVideo(duration = 1000, lastWatchPosition = 500)))
        assertNull(DownloadLogic.startPosition(OfflineVideo()))
    }

    @Test
    fun paceGivesSpeedAndTimeLeft() {
        var pace = DownloadLogic.Pace(0, 0, 0)
        pace = pace.next(1000, 2_000_000, 10)
        assertEquals(2_000_000.0, pace.bytesPerSecond, 1.0)
        assertEquals(10.0, pace.stepsPerSecond, 0.01)
        assertEquals(9L, pace.secondsLeft(100))
        // samples closer than half a second are skipped
        assertEquals(pace, pace.next(1200, 3_000_000, 12))
        assertNull(DownloadLogic.Pace(0, 0, 0).secondsLeft(100))
        assertEquals("2.0 MB", DownloadLogic.formatBytes(2_000_000.0))
        assertEquals("1:05:09", DownloadLogic.formatClock(3909))
        assertEquals("5:09", DownloadLogic.formatClock(309))
    }
}
