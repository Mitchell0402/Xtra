package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.util.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The player page's rules: which sheet rows show (v0.1's), names, settings read once, and the layout geometry. */
@RunWith(AndroidJUnit4::class)
class PlayerLogicTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("player-logic-test", Context.MODE_PRIVATE).also { it.edit { clear() } }

    private fun ctx(
        landscape: Boolean = false,
        phoneLandscape: Boolean = landscape,
        loggedIn: Boolean = true,
        chatDisabled: Boolean = false,
        hasAudioOnly: Boolean = true,
        hasChapters: Boolean = false,
        subtitles: SubtitleState = SubtitleState.None,
        translate: Boolean = false,
        debug: Boolean = false,
    ) = MenuContext(landscape, phoneLandscape, loggedIn, chatDisabled, hasAudioOnly, hasChapters, subtitles, translate, debug)

    @Test
    fun liveStreamRowsMatchV01() {
        assertEquals(
            listOf(
                MenuEntry.Quality, MenuEntry.SleepTimer, MenuEntry.AudioOnly, MenuEntry.Download, MenuEntry.AdBlock,
                MenuEntry.Compressor, MenuEntry.Volume, MenuEntry.ViewerList, MenuEntry.Share, MenuEntry.FindVideo,
                MenuEntry.SeekLive, MenuEntry.Restart, MenuEntry.ChatBar, MenuEntry.ChatConnection, MenuEntry.ReloadEmotes,
            ),
            PlayerLogic.menuEntries(PlayerKind.Stream, ctx()),
        )
    }

    @Test
    fun videoHasSpeedBookmarkAndChapters() {
        val rows = PlayerLogic.menuEntries(PlayerKind.Video, ctx(hasChapters = true, subtitles = SubtitleState.Off))
        assertTrue(MenuEntry.Speed in rows)
        assertTrue(MenuEntry.Bookmark in rows)
        assertTrue(MenuEntry.Chapters in rows)
        assertTrue(MenuEntry.Subtitles in rows)
        assertTrue(MenuEntry.ReloadEmotes in rows)
        assertFalse(MenuEntry.ViewerList in rows)
        assertFalse(MenuEntry.AdBlock in rows)
        assertFalse(MenuEntry.Chapters in PlayerLogic.menuEntries(PlayerKind.Video, ctx(hasChapters = false)))
    }

    @Test
    fun clipHasNoSleepTimerAndOfflineNoDownload() {
        assertFalse(MenuEntry.SleepTimer in PlayerLogic.menuEntries(PlayerKind.Clip, ctx()))
        assertFalse(MenuEntry.ReloadEmotes in PlayerLogic.menuEntries(PlayerKind.Clip, ctx()))
        val offline = PlayerLogic.menuEntries(PlayerKind.Offline, ctx(hasAudioOnly = false))
        assertFalse(MenuEntry.Download in offline)
        assertFalse(MenuEntry.AudioOnly in offline)
        assertTrue(MenuEntry.SleepTimer in offline)
    }

    @Test
    fun landscapeRowsAndChatRules() {
        val phone = PlayerLogic.menuEntries(PlayerKind.Stream, ctx(landscape = true))
        assertTrue(MenuEntry.Aspect in phone)
        assertTrue(MenuEntry.ChatLayout in phone)
        assertTrue(MenuEntry.ChatToggle in phone)
        val tablet = PlayerLogic.menuEntries(PlayerKind.Stream, ctx(landscape = true, phoneLandscape = false))
        assertFalse(MenuEntry.ChatLayout in tablet)
        assertTrue(MenuEntry.ChatToggle in tablet)
        val noChat = PlayerLogic.menuEntries(PlayerKind.Stream, ctx(landscape = true, chatDisabled = true))
        assertFalse(MenuEntry.ChatToggle in noChat)
        assertFalse(MenuEntry.ChatConnection in noChat)
        assertFalse(MenuEntry.ReloadEmotes in noChat)
        assertFalse(MenuEntry.ChatBar in PlayerLogic.menuEntries(PlayerKind.Stream, ctx(loggedIn = false)))
        assertTrue(MenuEntry.MediaPlaylistTags in PlayerLogic.menuEntries(PlayerKind.Stream, ctx(debug = true)))
    }

    @Test
    fun mainGroupComesFirst() {
        val rows = PlayerLogic.menuEntries(PlayerKind.Video, ctx(hasChapters = true))
        val main = rows.takeWhile { PlayerLogic.isMainEntry(it) }
        assertEquals(listOf(MenuEntry.Quality, MenuEntry.Speed, MenuEntry.SleepTimer, MenuEntry.AudioOnly, MenuEntry.Download), main)
        assertTrue(rows.drop(main.size).none { PlayerLogic.isMainEntry(it) })
    }

    @Test
    fun qualityNamesAsV01() {
        val labels = PlayerLogic.qualityLabels(
            listOf(
                VideoQuality(name = "auto"), VideoQuality(name = "source"), VideoQuality(name = "1080p60", codecs = "avc1.64002A"),
                VideoQuality(name = "720p30", codecs = "avc1.4D401F"), VideoQuality(name = "audio_only", codecs = "mp4a.40.2"),
            ),
            "Auto", "Source", "Audio only", "Chat only",
        ).map { it.first }
        assertEquals(listOf("Auto", "Source", "1080p60", "720p", "Audio only"), labels)
        val mixed = PlayerLogic.qualityLabels(
            listOf(VideoQuality(name = "1440p60", codecs = "hev1.1.6"), VideoQuality(name = "1080p60", codecs = "avc1.64002A"), VideoQuality(name = "1080p60", codecs = "av01.0")),
            "Auto", "Source", "Audio only", "Chat only",
        ).map { it.first }
        assertEquals(listOf("1440p60 H.265", "1080p60 H.264", "1080p60 AV1"), mixed)
        assertTrue(PlayerLogic.qualityLabels(null, "", "", "", "").isEmpty())
    }

    @Test
    fun speedsTimesAndNames() {
        assertEquals(11, PlayerLogic.speeds(null).size)
        assertEquals(listOf("1.0", "2.0"), PlayerLogic.speeds("1.0\n\n2.0"))
        assertEquals("1.5×", PlayerLogic.speedLabel(1.5f))
        assertEquals("1×", PlayerLogic.speedLabel(1f))
        assertEquals("00:09", PlayerLogic.time(9_400))
        assertEquals("1:02:03", PlayerLogic.time(3_723_000))
        assertEquals("?t=01h02m03s", PlayerLogic.videoShareTime(3_723_999))
        assertEquals("Name(login)", PlayerLogic.displayName("Name", "login", "0"))
        assertEquals("Name", PlayerLogic.displayName("Name", "login", "1"))
        assertEquals("login", PlayerLogic.displayName("Name", "login", "2"))
        assertEquals("Login", PlayerLogic.displayName("Login", "login", "0"))
        assertEquals(1, PlayerLogic.nextResizeMode(0))
        assertEquals(0, PlayerLogic.nextResizeMode(4))
        assertEquals(250, PlayerLogic.clampChatWidth(10, 1000))
        assertEquals(660, PlayerLogic.clampChatWidth(900, 1000))
        assertEquals(400, PlayerLogic.clampChatWidth(400, 1000))
    }

    @Test
    fun optionsReadV01KeysAndDefaults() {
        val defaults = PlayerOptions.from(prefs, loggedIn = false)
        assertEquals(PlayerOptions(), defaults)
        prefs.edit {
            putBoolean(C.PLAYER_DOWNLOAD, true)
            putBoolean(C.PLAYER_SLEEP, true)
            putString(C.PLAYER_REWIND, "5")
            putString(C.UI_FOLLOW_BUTTON, "2")
            putBoolean(C.CHAT_DISABLE, true)
        }
        val changed = PlayerOptions.from(prefs, loggedIn = true)
        assertEquals(setOf(QuickButton.Download, QuickButton.SleepTimer), changed.quick)
        assertEquals(5L, changed.rewindSeconds)
        assertFalse(changed.showFollow)
        assertTrue(changed.chatDisabled)
        // v0.1: double tap toggled the chat only while the chat was on
        assertFalse(changed.doubleTapChat)
    }

    // --- geometry ---

    private val density = Density(2f)

    private fun geometry(
        layout: PlayerLayout, width: Float, height: Float, chatOpen: Boolean = true, overlay: Boolean = false, keyboard: Boolean = false,
        chatWidth: Int = 600, bottomBar: Float = 200f, statusTop: Float = 60f,
    ) = PlayerGeometryRules.compute(
        layout = layout, width = width, height = height, density = density, statusTop = statusTop, sideLeft = 0f, sideRight = 0f,
        bottomInset = 40f, imeBottom = 0f, bottomBarPx = bottomBar, chatOpen = chatOpen, chatOverlay = overlay, chatDisabled = false,
        chatWidthPx = chatWidth, keyboardShown = keyboard, infoHeight = 120f, miniOffset = Offset.Zero,
    )

    @Test
    fun layoutClasses() {
        assertEquals(PlayerLayout.Portrait, PlayerGeometryRules.layoutOf(1080, 2400, 411, 411, pip = false, tabletFullscreen = false))
        assertEquals(PlayerLayout.Landscape, PlayerGeometryRules.layoutOf(2400, 1080, 900, 411, pip = false, tabletFullscreen = false))
        assertEquals(PlayerLayout.Tablet, PlayerGeometryRules.layoutOf(2560, 1600, 1280, 800, pip = false, tabletFullscreen = false))
        assertEquals(PlayerLayout.Landscape, PlayerGeometryRules.layoutOf(2560, 1600, 1280, 800, pip = false, tabletFullscreen = true))
        assertEquals(PlayerLayout.Pip, PlayerGeometryRules.layoutOf(400, 225, 200, 100, pip = true, tabletFullscreen = false))
    }

    @Test
    fun portraitVideoIs16by9UnderTheStatusBarAndChatBelowTheInfo() {
        val g = geometry(PlayerLayout.Portrait, 1080f, 2400f)
        assertEquals(60f, g.slot.top)
        assertEquals(1080f * 9 / 16, g.slot.height, 0.5f)
        assertEquals(g.slot.bottom + 120f, g.chat.top.toFloat(), 1f)
        assertEquals((2400 - 40 - g.chat.top).toFloat(), g.chat.height.toFloat(), 1f)
        assertTrue(g.chat.visible)
        // the mini player sits above the navigation
        assertTrue(g.miniCard.bottom <= 2400f - 200f)
        assertTrue(g.miniThumb.left >= g.miniCard.left && g.miniThumb.bottom <= g.miniCard.bottom)
    }

    @Test
    fun phoneLandscapeChatBesideOrOver() {
        val side = geometry(PlayerLayout.Landscape, 2400f, 1080f)
        assertEquals(1800f, side.slot.right)
        assertFalse(side.chat.overlay)
        val over = geometry(PlayerLayout.Landscape, 2400f, 1080f, overlay = true)
        assertEquals(2400f, over.slot.right)
        assertTrue(over.chat.overlay)
        val closed = geometry(PlayerLayout.Landscape, 2400f, 1080f, chatOpen = false)
        assertEquals(2400f, closed.slot.right)
        assertFalse(closed.chat.visible)
        // typing: the chat widens beside the video (v0.1: width / 1.8), also in overlay mode
        val typing = geometry(PlayerLayout.Landscape, 2400f, 1080f, overlay = true, keyboard = true)
        assertEquals((2400 / 1.8f).toInt().toFloat(), typing.chat.width.toFloat(), 1f)
        assertFalse(typing.chat.overlay)
    }

    @Test
    fun tabletKeepsAVideoColumnBesideTheChat() {
        val g = geometry(PlayerLayout.Tablet, 2560f, 1600f, chatWidth = 800)
        assertEquals(1760f, g.paneWidth)
        assertEquals(1760, g.chat.left)
        assertEquals(800, g.chat.width)
        assertTrue(g.slot.height <= 1760f * 9 / 16 + 0.5f)
        assertTrue(g.slot.bottom <= 1600f - 160f * 2f + 0.5f)
    }

    @Test
    fun aTinyWindowDoesNotBreakTheMiniPlayer() {
        // picture in picture: the window is smaller than the card
        val g = geometry(PlayerLayout.Pip, 300f, 170f, bottomBar = 0f, statusTop = 0f)
        assertTrue(g.miniCard.width > 0f)
        assertEquals(300f, g.slot.width)
    }

    @Test
    fun videoPlacementMovesFromTheSlotIntoTheMiniPlayer() {
        val g = geometry(PlayerLayout.Portrait, 1080f, 2400f)
        val max = PlayerGeometryRules.videoPlacement(g, 0f, 0f, true)
        assertEquals(1f, max.scale)
        assertEquals(0f, max.translationX)
        assertEquals(1080, max.width)
        val mini = PlayerGeometryRules.videoPlacement(g, 1f, 0f, true)
        assertEquals(g.miniThumb.width / g.slot.width, mini.scale, 0.001f)
        assertEquals(g.miniThumb.left - g.slot.left, mini.translationX, 0.5f)
        assertEquals(g.miniThumb.top - g.slot.top, mini.translationY, 0.5f)
        // the view keeps its size: only its transform changes while it moves
        assertEquals(max.width, mini.width)
        assertEquals(max.height, mini.height)
    }
}
