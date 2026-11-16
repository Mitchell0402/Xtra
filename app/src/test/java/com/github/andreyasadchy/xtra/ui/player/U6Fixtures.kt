package com.github.andreyasadchy.xtra.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import kotlin.math.roundToInt

/** Fixed data for the U6 tests: a live stream, a video, the sheet's source, and a recorder of the page's requests. */
object U6Fixtures {
    const val NOW = 1_763_300_000_000L

    val qualities = listOf(
        VideoQuality(name = "auto", url = "a"),
        VideoQuality(name = VideoQuality.SOURCE_QUALITY, resolution = 1080, frameRate = 60f, url = "s"),
        VideoQuality(name = "720p60", url = "7"),
        VideoQuality(name = "480p30", url = "4"),
        VideoQuality(name = "audio_only", url = "o"),
    )

    fun live(mode: PlayerMode = PlayerMode.Maximized, controls: Boolean = true) = PlayerUiState().apply {
        kind = PlayerKind.Stream
        title = "夜航电台 · 深夜聊天和独立游戏 Late night radio and indie games"
        channelName = "夜航Nightowl"
        channelLogin = "nightowl"
        gameName = "Just Chatting"
        tags = listOf("中文", "English", "Chill")
        viewers = 12_480
        startedAt = NOW - 5_025_000L
        showPlayButton = false
        isPlaying = true
        loaded = true
        qualityLabel = "Source"
        following = false
        this.mode = mode
        controlsVisible = controls
        chatWidthPx = 900
    }

    fun video() = PlayerUiState().apply {
        kind = PlayerKind.Video
        title = "Speedrun 100% — any% world record attempt"
        channelName = "Lagoon"
        channelLogin = "lagoon"
        gameName = "Celeste"
        positionMs = 1_534_000L
        bufferedMs = 1_720_000L
        durationMs = 5_400_000L
        showPlayButton = true
        loaded = true
        qualityLabel = "720p60"
        speed = 1.25f
        following = true
        bookmarked = false
        subtitles = SubtitleState.Off
        controlsVisible = true
        chapters = listOf(
            Game(name = "Celeste", vodPosition = 0, vodDuration = 3_000_000),
            Game(name = "Just Chatting", vodPosition = 3_000_000, vodDuration = 2_400_000),
        )
    }
}

class FakeSheetSource(private val layoutIsLandscape: Boolean = false) : PlayerSheetSource {
    override fun menuContext(layout: PlayerLayout) = MenuContext(
        landscape = layout == PlayerLayout.Landscape || layout == PlayerLayout.Tablet,
        phoneLandscape = layout == PlayerLayout.Landscape,
        loggedIn = true,
        chatDisabled = false,
        hasAudioOnly = true,
        hasChapters = true,
        subtitles = SubtitleState.Off,
        translateAvailable = false,
        debugPlaylistTags = false,
    )
    override fun qualities() = PlayerLogic.qualityLabels(U6Fixtures.qualities, "Auto", "Source", "Audio only", "Chat only")
    override fun currentQuality() = U6Fixtures.qualities[1]
    override fun speeds() = PlayerLogic.speeds(null)
    override fun playerVolume() = 0.8f
    override fun sleepMinutes() = 30
    override fun sleepLock() = false
    override fun sleepLockAllowed() = false
    override fun adBlockPreset() = "Balanced"
    override fun adBlockSummary() = "3 ad breaks, 4 min of ads"
    override fun adLog() = listOf("12:00:01 ad break detected, 30 s", "12:00:02 swap web/highlighter ready")
    override fun translateAll(): Boolean? = null
}

/** Records what the page asked for. */
class RecordingPlayerActions : PlayerActions {
    val calls = mutableListOf<String>()
    override fun playPause() { calls += "playPause" }
    override fun rewind() { calls += "rewind" }
    override fun forward() { calls += "forward" }
    override fun seekTo(positionMs: Long) { calls += "seek:$positionMs" }
    override fun minimize() { calls += "minimize" }
    override fun maximize() { calls += "maximize" }
    override fun dismiss() { calls += "dismiss" }
    override fun toggleFullscreen() { calls += "fullscreen" }
    override fun openChannel() { calls += "channel" }
    override fun openGame() { calls += "game" }
    override fun setFollowing(follow: Boolean) { calls += "follow:$follow" }
    override fun openViewerList() { calls += "viewers" }
    override fun watchFullVideo() { calls += "fullVideo" }
    override fun setChatOpen(open: Boolean) { calls += "chat:$open" }
    override fun setChatWidth(widthPx: Int, done: Boolean) { if (done) calls += "chatWidth" }
    override fun adjustBrightness(delta: Float): Float { calls += "brightness"; return 0.5f }
    override fun adjustVolume(delta: Float): Float { calls += "volume"; return 0.5f }
    override fun quick(button: QuickButton) { calls += "quick:$button" }
    override fun menu(entry: MenuEntry) { calls += "menu:$entry" }
    override fun selectQuality(quality: VideoQuality) { calls += "quality:${quality.name}" }
    override fun selectSpeed(speed: Float) { calls += "speed:$speed" }
    override fun setPlayerVolume(volume: Float, done: Boolean) { if (done) calls += "playerVolume" }
    override fun setSleepTimer(minutes: Int, lockScreen: Boolean) { calls += "sleep:$minutes:$lockScreen" }
    override fun pickSleepTime() { calls += "sleepAt" }
    override fun requestSleepLockPermission() { calls += "lockPermission" }
    override fun seekToChapter(positionMs: Long) { calls += "chapter:$positionMs" }
    override fun setChatOverlay(overlay: Boolean) { calls += "overlay:$overlay" }
    override fun setResizeMode(mode: Int) { calls += "resize:$mode" }
    override fun openAdBlockSettings() { calls += "adSettings" }
}

/**
 * The page as the fragment shows it: a stand-in picture where the video view goes, a stand-in chat where the old chat
 * goes, and the Compose page on top.
 */
@Composable
fun PlayerFrame(
    state: PlayerUiState,
    options: PlayerOptions = PlayerOptions(loggedIn = true),
    actions: PlayerActions = RecordingPlayerActions(),
    sheets: PlayerSheetSource = FakeSheetSource(),
    bottomBarPx: Int = 0,
) {
    var video by remember { mutableStateOf<VideoPlacement?>(null) }
    var chat by remember { mutableStateOf(ChatPlacement()) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        video?.let { v ->
            Box(
                Modifier
                    .offset { IntOffset((v.left + v.translationX).roundToInt(), (v.top + v.translationY).roundToInt()) }
                    .size(with(density) { (v.width * v.scale).toDp() }, with(density) { (v.height * v.scale).toDp() })
                    .background(if (state.videoDisabled) Brush.linearGradient(listOf(Color.Black, Color.Black)) else Brush.linearGradient(listOf(Color(0xFF1E4D47), Color(0xFF203050), Color(0xFF4A2B55))))
            )
        }
        PlayerScreen(
            state = state,
            options = options,
            actions = actions,
            sheets = sheets,
            bottomBarPx = bottomBarPx,
            onVideoPlacement = { video = it },
            onChatPlacement = { chat = it },
            sideInsets = 0 to 0,
            now = { U6Fixtures.NOW },
        )
        // the old chat's container lies over the Compose page, as in the fragment
        if (chat.visible) {
            Column(
                Modifier
                    .offset { IntOffset(chat.left, chat.top) }
                    .size(with(density) { chat.width.toDp() }, with(density) { chat.height.toDp() })
                    .background(if (chat.overlay) Color(0xB3111113) else Lagoon.colors.page)
                    .padding(8.dp)
            ) {
                listOf("nightowl_fan: 晚上好", "lagoon: the new player looks clean", "viewer42: PogChamp", "夜猫子: 这首歌叫什么").forEach {
                    Text(it, style = Lagoon.type.body, color = Lagoon.colors.textPrimary, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}
