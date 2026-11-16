package com.github.andreyasadchy.xtra.ui.player

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.util.C

/**
 * Quick buttons a viewer can pin to the top of the video (U6 decision, option 1). These are the v0.1 player buttons
 * that were off by default; the ones that were on by default are always in the settings sheet instead.
 */
enum class QuickButton(val key: String) {
    Download(C.PLAYER_DOWNLOAD),
    Follow(C.PLAYER_FOLLOW),
    SleepTimer(C.PLAYER_SLEEP),
    SeekLive(C.PLAYER_SEEK_LIVE),
    AudioOnly(C.PLAYER_MODE),
    Subtitles(C.PLAYER_SUBTITLES),
    ChatBar(C.PLAYER_CHAT_BAR_TOGGLE);

    companion object {
        fun pinned(prefs: SharedPreferences) = entries.filter { prefs.getBoolean(it.key, false) }.toSet()
    }
}

/** The player settings the page reads once when it opens (keys, types and defaults as in v0.1). */
@Immutable
data class PlayerOptions(
    val showChannel: Boolean = true,
    val showTitle: Boolean = true,
    val showCategory: Boolean = true,
    val showUptime: Boolean = true,
    val showViewerIcon: Boolean = true,
    val viewerListFromCount: Boolean = false,
    val pauseOnLive: Boolean = false,
    val rewindSeconds: Long = 10,
    val forwardSeconds: Long = 10,
    /** Double tap toggles the chat in landscape (v0.1 player_doubletap). */
    val doubleTapChat: Boolean = true,
    val showMinimize: Boolean = true,
    val showFullscreen: Boolean = true,
    val showChatToggle: Boolean = true,
    val chatDisabled: Boolean = false,
    val loggedIn: Boolean = false,
    /** ui_follow_button 0 or 1: the follow button shows (2 hides it). */
    val showFollow: Boolean = true,
    val moveFreely: Boolean = false,
    val truncateCounts: Boolean = true,
    val nameDisplay: String = "0",
    val quick: Set<QuickButton> = emptySet(),
) {
    companion object {
        fun from(prefs: SharedPreferences, loggedIn: Boolean) = PlayerOptions(
            showChannel = prefs.getBoolean(C.PLAYER_CHANNEL, true),
            showTitle = prefs.getBoolean(C.PLAYER_TITLE, true),
            showCategory = prefs.getBoolean(C.PLAYER_CATEGORY, true),
            showUptime = prefs.getBoolean(C.PLAYER_SHOW_UPTIME, true),
            showViewerIcon = prefs.getBoolean(C.PLAYER_VIEWER_ICON, true),
            viewerListFromCount = prefs.getBoolean(C.PLAYER_VIEWER_LIST, false),
            pauseOnLive = prefs.getBoolean(C.PLAYER_PAUSE, false),
            rewindSeconds = prefs.getString(C.PLAYER_REWIND, "10")?.toLongOrNull() ?: 10,
            forwardSeconds = prefs.getString(C.PLAYER_FORWARD, "10")?.toLongOrNull() ?: 10,
            doubleTapChat = prefs.getBoolean(C.PLAYER_DOUBLE_TAP, true) && !prefs.getBoolean(C.CHAT_DISABLE, false),
            showMinimize = prefs.getBoolean(C.PLAYER_MINIMIZE, true),
            showFullscreen = prefs.getBoolean(C.PLAYER_FULLSCREEN, true),
            showChatToggle = prefs.getBoolean(C.PLAYER_CHAT_TOGGLE, true),
            chatDisabled = prefs.getBoolean(C.CHAT_DISABLE, false),
            loggedIn = loggedIn,
            showFollow = (prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0) < 2,
            moveFreely = prefs.getBoolean(C.PLAYER_MOVE_FREELY, false),
            truncateCounts = prefs.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true),
            nameDisplay = prefs.getString(C.UI_NAME_DISPLAY, "0") ?: "0",
            quick = QuickButton.pinned(prefs),
        )
    }
}

/** The rows of the player settings sheet (plan 7.4), in their order. */
enum class MenuEntry {
    // the main group: icon, name and current value
    Quality, Speed, SleepTimer, AudioOnly, Download, AdBlock,
    // the rest
    FullVideo, Subtitles, Compressor, Volume, Chapters, ViewerList, Bookmark, Share, FindVideo, SeekLive, Restart,
    Aspect, ChatLayout, ChatToggle, ChatBar, ChatConnection, ReloadEmotes,
    MediaPlaylistTags, MultivariantPlaylistTags;
}

/** What decides which rows the sheet shows. */
data class MenuContext(
    val landscape: Boolean,
    /** Phone landscape, where the chat can lie over the video. */
    val phoneLandscape: Boolean,
    val loggedIn: Boolean,
    val chatDisabled: Boolean,
    val hasAudioOnly: Boolean,
    val hasChapters: Boolean,
    val subtitles: SubtitleState,
    val debugPlaylistTags: Boolean,
    /** A clip of a video that still exists: "watch full video" (v0.1's button under the chat). */
    val hasFullVideo: Boolean = false,
)

object PlayerLogic {

    const val DEFAULT_SPEEDS = "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0"

    /**
     * v0.1 PlayerSettingsDialog's rules for each row, without its seventeen player_menu_* switches (U6 decision): the
     * sheet lists everything that applies to what is playing.
     */
    fun menuEntries(kind: PlayerKind, c: MenuContext): List<MenuEntry> = buildList {
        add(MenuEntry.Quality)
        if (kind != PlayerKind.Stream) add(MenuEntry.Speed)
        if (kind != PlayerKind.Clip) add(MenuEntry.SleepTimer)
        if (c.hasAudioOnly) add(MenuEntry.AudioOnly)
        if (kind != PlayerKind.Offline) add(MenuEntry.Download)
        if (kind == PlayerKind.Stream) add(MenuEntry.AdBlock)
        if (kind == PlayerKind.Clip && c.hasFullVideo) add(MenuEntry.FullVideo)
        if (c.subtitles != SubtitleState.None) add(MenuEntry.Subtitles)
        add(MenuEntry.Compressor)
        add(MenuEntry.Volume)
        if (kind == PlayerKind.Video && c.hasChapters) add(MenuEntry.Chapters)
        if (kind == PlayerKind.Stream) add(MenuEntry.ViewerList)
        if (kind == PlayerKind.Video) add(MenuEntry.Bookmark)
        add(MenuEntry.Share)
        if (kind == PlayerKind.Stream) {
            add(MenuEntry.FindVideo)
            add(MenuEntry.SeekLive)
            add(MenuEntry.Restart)
        }
        if (c.landscape) {
            add(MenuEntry.Aspect)
            if (!c.chatDisabled) {
                if (c.phoneLandscape) add(MenuEntry.ChatLayout)
                add(MenuEntry.ChatToggle)
            }
        }
        if (kind == PlayerKind.Stream && !c.chatDisabled) {
            if (c.loggedIn) add(MenuEntry.ChatBar)
            add(MenuEntry.ChatConnection)
        }
        if ((kind == PlayerKind.Stream || kind == PlayerKind.Video) && !c.chatDisabled) add(MenuEntry.ReloadEmotes)
        if (kind == PlayerKind.Stream && c.debugPlaylistTags) {
            add(MenuEntry.MediaPlaylistTags)
            add(MenuEntry.MultivariantPlaylistTags)
        }
    }

    /** Index where the sheet's second group starts. */
    fun isMainEntry(entry: MenuEntry) = entry.ordinal <= MenuEntry.AdBlock.ordinal

    /** v0.1 PlayerFragment.getQualities: auto, source, audio only, chat only by name; codecs only when they differ. */
    fun qualityLabels(
        qualities: List<VideoQuality>?,
        auto: String,
        source: String,
        audioOnly: String,
        chatOnly: String,
    ): List<Pair<String, VideoQuality>> {
        if (qualities.isNullOrEmpty()) return emptyList()
        val hideCodecs = qualities.all {
            val codec = it.codecs?.substringBefore('.')
            codec == "avc1" || codec == "mp4a" || codec.isNullOrBlank()
        }
        return qualities.map { quality ->
            when (quality.name) {
                VideoQuality.AUTO_QUALITY -> auto
                VideoQuality.SOURCE_QUALITY -> source
                VideoQuality.AUDIO_ONLY_QUALITY -> audioOnly
                VideoQuality.CHAT_ONLY_QUALITY -> chatOnly
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
            } to quality
        }
    }

    fun speeds(pref: String?): List<String> = (pref ?: DEFAULT_SPEEDS).split("\n").filter { it.isNotBlank() }

    /** "1.5×" for the sheet; v0.1 matched the list entry by its text. */
    fun speedLabel(speed: Float) = speed.toString().removeSuffix(".0") + "×"

    /** 1:23:45 or 12:34, as v0.1's DateUtils.formatElapsedTime. */
    fun time(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** v0.1 share link of a video: ?t=01h02m03s at the current position. */
    fun videoShareTime(positionMs: Long): String {
        val totalSeconds = positionMs / 1000
        return "?t=%02dh%02dm%02ds".format(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
    }

    /** v0.1 ui_name_display: "Name(login)", the name only, or the login only. */
    fun displayName(name: String?, login: String?, nameDisplay: String): String? =
        if (login != null && !login.equals(name, true)) {
            when (nameDisplay) {
                "0" -> "$name($login)"
                "1" -> name
                else -> login
            }
        } else name

    /** Next of the five resize modes (fit, fixed width, fixed height, fill, zoom), as v0.1 setResizeMode. */
    fun nextResizeMode(mode: Int) = (mode + 1).let { if (it < 5) it else 0 }

    /** Width of the chat beside the video, kept between a quarter and two thirds of the window (tablet divider). */
    fun clampChatWidth(widthPx: Int, windowWidthPx: Int): Int =
        widthPx.coerceIn((windowWidthPx * 0.25f).toInt(), (windowWidthPx * 0.66f).toInt())

    /** Minutes until hh:mm (sleep timer with the time picker); v0.1 TwitchApiHelper.getMinutesLeft. */
    fun minutesUntil(nowMinutes: Int, hour: Int, minute: Int): Int {
        val target = hour * 60 + minute
        return if (target > nowMinutes) target - nowMinutes else 24 * 60 - nowMinutes + target
    }
}
