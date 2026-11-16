package com.github.andreyasadchy.xtra.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.andreyasadchy.xtra.model.ui.Game

/** What is playing, as the page shows it. */
enum class PlayerKind { Stream, Video, Clip, Offline;

    val isLive get() = this == Stream
    /** Has a time line the viewer can move along. */
    val seekable get() = this != Stream

    companion object {
        fun of(type: String?) = when (type) {
            BasePlaybackService.STREAM -> Stream
            BasePlaybackService.CLIP -> Clip
            BasePlaybackService.OFFLINE_VIDEO -> Offline
            else -> Video
        }
    }
}

/** Subtitles of the current item: none in the stream, or present and shown or hidden. */
enum class SubtitleState { None, Off, On }

/** Whether the page fills the window, sits as the mini player, or is the picture-in-picture window. */
enum class PlayerMode { Maximized, Minimized }

/**
 * Everything the player page draws, in one place. [PlayerConnection] writes the playback part from the service's and
 * the player's callbacks (the U6 contract), the fragment writes the rest. Compose reads it directly.
 */
@Stable
class PlayerUiState {
    var kind by mutableStateOf(PlayerKind.Stream)

    // what is playing
    var title by mutableStateOf<String?>(null)
    var channelName by mutableStateOf<String?>(null)
    var channelLogin by mutableStateOf<String?>(null)
    var channelImage by mutableStateOf<String?>(null)
    var gameName by mutableStateOf<String?>(null)
    var tags by mutableStateOf<List<String>>(emptyList())
    var viewers by mutableStateOf<Int?>(null)
    /** Wall clock time the stream started, for the uptime. */
    var startedAt by mutableStateOf<Long?>(null)
    /** Upload date of a video or clip (epoch ms). */
    var uploadedAt by mutableStateOf<Long?>(null)
    var hasFullVideo by mutableStateOf(false)

    // playback
    var showPlayButton by mutableStateOf(true)
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var speed by mutableFloatStateOf(1f)
    /** Qualities are known: quality, download and audio only can be used. */
    var loaded by mutableStateOf(false)
    var qualityLabel by mutableStateOf<String?>(null)
    var audioOnly by mutableStateOf(false)
    /** Video rendering is switched off (audio only, or in the background); the surface is hidden. */
    var videoDisabled by mutableStateOf(false)
    var videoAspect by mutableFloatStateOf(16f / 9f)
    /** Short ad status for the corner of the video, null when there is nothing to say (plan stage 4 of the ad work). */
    var adStatus by mutableStateOf<CharSequence?>(null)
    var subtitles by mutableStateOf(SubtitleState.None)
    var compressorOn by mutableStateOf(false)

    // around the video
    var following by mutableStateOf<Boolean?>(null)
    var bookmarked by mutableStateOf<Boolean?>(null)
    var chapters by mutableStateOf<List<Game>>(emptyList())
    /** When the sleep timer ends (epoch ms), 0 when off. */
    var sleepTimerEnd by mutableLongStateOf(0L)
    var chatConnected by mutableStateOf(true)
    var chatBarVisible by mutableStateOf(true)
    var resizeMode by mutableStateOf(0)

    // page state
    var mode by mutableStateOf(PlayerMode.Maximized)
    var inPictureInPicture by mutableStateOf(false)
    var controlsVisible by mutableStateOf(false)
    var chatOpen by mutableStateOf(true)
    /** In phone landscape the chat lies over the video instead of beside it. */
    var chatOverlay by mutableStateOf(false)
    var chatWidthPx by mutableStateOf(0)
    var keyboardShown by mutableStateOf(false)
    /** Tablet in landscape: the video fills the window like on a phone (the fullscreen button). */
    var tabletFullscreen by mutableStateOf(false)
    /** The sheet open over the page, null when none. */
    var sheet by mutableStateOf<PlayerSheet?>(null)
}
