package com.github.andreyasadchy.xtra.ui.saved

import android.content.ContentResolver
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.ui.main.shell.ShellTabs
import com.github.andreyasadchy.xtra.ui.saved.bookmarks.BookmarkSorts
import com.github.andreyasadchy.xtra.util.C
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** The segments of the Me page (setting `ui_saved_tabs`, "key:default:enabled"). */
enum class SavedSegment(val key: String) {
    Bookmarks("0"), Downloads("1"), Filters("2");

    companion object {
        fun of(key: String) = entries.find { it.key == key }

        fun list(pref: String?): List<SavedSegment> =
            ShellTabs.normalize(pref, C.DEFAULT_SAVED_TABS).map { it.split(':') }.mapNotNull { split ->
                if (split.getOrNull(2) == "0") null else of(split[0])
            }.ifEmpty { listOf(Downloads) }

        /** v0.1: the tab marked as default, else Downloads, else the first. */
        fun default(pref: String?, segments: List<SavedSegment>): SavedSegment {
            val key = ShellTabs.normalize(pref, C.DEFAULT_SAVED_TABS).map { it.split(':') }.find { it.getOrNull(1) != "0" }?.get(0)
            return of(key ?: "")?.takeIf { it in segments } ?: Downloads.takeIf { it in segments } ?: segments.first()
        }
    }
}

/**
 * The v0.1 bookmark list's sorting and its "time left" line (BookmarksFragment, BookmarksAdapter) as pure functions.
 * Unit tested in SavedLogicTest.
 */
object BookmarkLogic {
    const val SORT_ID = "bookmarks"

    /** Twitch keeps past broadcasts 7 days, 14 for affiliates, 60 for partners, Prime and Turbo. */
    fun keepDays(bookmark: Bookmark): Int {
        val userType = bookmark.userType ?: bookmark.userBroadcasterType
        return if (userType.isNullOrBlank()) 7 else when (userType.lowercase()) {
            "affiliate" -> 14
            else -> 60
        }
    }

    /** Seconds until a past broadcast is deleted, null for other videos and for ones already past it. */
    fun secondsLeft(bookmark: Bookmark, nowMillis: Long): Long? {
        if (bookmark.type?.lowercase() != "archive") return null
        val created = bookmark.createdAt?.let { Instant.parseOrNull(it) }?.takeIf { it.toEpochMilliseconds() > 0 } ?: return null
        val left = (created + keepDays(bookmark).days).toEpochMilliseconds() - nowMillis
        return if (left > 0) left / 1000 else null
    }

    private fun created(bookmark: Bookmark) = bookmark.createdAt?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } }

    fun sorted(list: List<Bookmark>, sort: String, order: String, nowMillis: Long): List<Bookmark> =
        if (order == BookmarkSorts.ORDER_ASC) {
            when (sort) {
                BookmarkSorts.SORT_EXPIRES_AT -> list.sortedWith(compareBy(nullsLast()) { secondsLeft(it, nowMillis) })
                BookmarkSorts.SORT_CREATED_AT -> list.sortedWith(compareBy(nullsLast()) { created(it) })
                else -> list.sortedWith(compareBy(nullsLast()) { it.id })
            }
        } else {
            when (sort) {
                BookmarkSorts.SORT_EXPIRES_AT -> list.sortedWith(compareByDescending(nullsFirst()) { secondsLeft(it, nowMillis) })
                BookmarkSorts.SORT_CREATED_AT -> list.sortedWith(compareByDescending(nullsFirst()) { created(it) })
                else -> list.sortedWith(compareByDescending(nullsFirst()) { it.id })
            }
        }
}

/** What a download's long-press menu offers (v0.1 DownloadsAdapter's popup menu, item for item). */
enum class DownloadAction { Stop, Resume, Convert, MoveToApp, MoveToShared, ChatFile, Share, Delete }

/**
 * The v0.1 downloads list's status and menu rules, and the numbers the new rows show (speed, time left), as pure
 * functions. Unit tested in SavedLogicTest.
 */
object DownloadLogic {

    /** An active download the service does not report (yet) shows as pending, as v0.1 did. */
    fun shownStatus(video: OfflineVideo, active: Boolean): Int =
        if ((video.status == OfflineVideo.STATUS_DOWNLOADING || video.status == OfflineVideo.STATUS_QUEUED ||
                    video.status == OfflineVideo.STATUS_WAITING_FOR_STREAM) && !active) {
            OfflineVideo.STATUS_PENDING
        } else {
            video.status
        }

    fun isContent(url: String?) = url?.substringBefore(':') == ContentResolver.SCHEME_CONTENT

    fun actions(video: OfflineVideo, status: Int): List<DownloadAction> = buildList {
        when (status) {
            OfflineVideo.STATUS_DOWNLOADING, OfflineVideo.STATUS_QUEUED, OfflineVideo.STATUS_WAITING_FOR_NETWORK,
            OfflineVideo.STATUS_WAITING_FOR_WIFI, OfflineVideo.STATUS_WAITING_FOR_STREAM -> add(DownloadAction.Stop)
            OfflineVideo.STATUS_PENDING -> {
                if (video.live) add(DownloadAction.Stop)
                add(DownloadAction.Resume)
            }
            // moving, deleting and converting have only delete, as before
            OfflineVideo.STATUS_MOVING, OfflineVideo.STATUS_DELETING, OfflineVideo.STATUS_CONVERTING -> Unit
            else -> {
                add(if (isContent(video.url)) DownloadAction.MoveToApp else DownloadAction.MoveToShared)
                if (video.url?.endsWith(".m3u8") == true) add(DownloadAction.Convert)
                add(DownloadAction.ChatFile)
                if (isContent(video.url)) add(DownloadAction.Share)
            }
        }
        add(DownloadAction.Delete)
    }

    /** v0.1 played a finished video from the start again. */
    fun startPosition(video: OfflineVideo): Long? {
        val duration = video.duration
        val position = video.lastWatchPosition
        return if (position != null && duration != null && duration > 0 && position >= duration) 0 else null
    }

    fun percent(progress: Int, max: Int): Int = if (max <= 0) 0 else ((progress.toFloat() / max) * 100f).toInt().coerceIn(0, 100)

    /**
     * A moving average of a download's pace: bytes per second, and progress steps (segments) per second, from which
     * the time left follows. [Pace.next] takes the newest sample.
     */
    data class Pace(val atMillis: Long, val bytes: Long, val progress: Int, val bytesPerSecond: Double = 0.0, val stepsPerSecond: Double = 0.0) {
        fun next(atMillis: Long, bytes: Long, progress: Int): Pace {
            val seconds = (atMillis - this.atMillis) / 1000.0
            if (seconds < 0.5) return this
            val byteRate = ((bytes - this.bytes).coerceAtLeast(0)) / seconds
            val stepRate = ((progress - this.progress).coerceAtLeast(0)) / seconds
            val keep = if (this.bytesPerSecond == 0.0 && this.stepsPerSecond == 0.0) 0.0 else 0.7
            return Pace(
                atMillis, bytes, progress,
                bytesPerSecond = this.bytesPerSecond * keep + byteRate * (1 - keep),
                stepsPerSecond = this.stepsPerSecond * keep + stepRate * (1 - keep),
            )
        }

        /** Seconds left for [max] steps, null until the pace is known. */
        fun secondsLeft(max: Int): Long? = if (stepsPerSecond <= 0.0 || max <= progress) null else ((max - progress) / stepsPerSecond).toLong()
    }

    /** 1.2 MB/s style, in decimal units like the system's storage screens. */
    fun formatBytes(bytes: Double): String = when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000)
        bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000)
        bytes >= 1_000 -> String.format("%.0f KB", bytes / 1_000)
        else -> String.format("%.0f B", bytes)
    }

    /** 1:05:09 or 5:09. */
    fun formatClock(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }
}
