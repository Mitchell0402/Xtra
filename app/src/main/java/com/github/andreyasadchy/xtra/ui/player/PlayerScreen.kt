package com.github.andreyasadchy.xtra.ui.player

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import com.github.andreyasadchy.xtra.ui.design.components.LagoonSpinner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.components.Avatar
import com.github.andreyasadchy.xtra.ui.design.components.FollowButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LiveDot
import com.github.andreyasadchy.xtra.ui.design.components.OverlayChip
import com.github.andreyasadchy.xtra.ui.design.components.Tag
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where the fragment puts its plain video view: laid out at the maximized slot, moved and scaled into the mini player. */
@Immutable
data class VideoPlacement(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val scale: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val visible: Boolean = true,
)

/** Where the fragment puts the old chat (U7 rewrites it); [overlay] lays it over the video in phone landscape. */
@Immutable
data class ChatPlacement(
    val left: Int = 0,
    val top: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val visible: Boolean = false,
    val overlay: Boolean = false,
)

/** The four arrangements of the page (plan 7.4). */
enum class PlayerLayout { Portrait, Landscape, Tablet, Pip }

/** What the page asks the fragment to do. */
interface PlayerActions {
    fun playPause()
    fun rewind()
    fun forward()
    fun seekTo(positionMs: Long)
    fun minimize()
    fun maximize()
    /** Closes the player (close button, swipe on the mini player). */
    fun dismiss()
    fun toggleFullscreen()
    fun openChannel()
    fun openGame()
    fun setFollowing(follow: Boolean)
    fun openViewerList()
    fun watchFullVideo()
    fun setChatOpen(open: Boolean)
    fun setChatWidth(widthPx: Int, done: Boolean)
    /** Changes the window brightness by [delta] (a fraction of the full range) and returns the new level, 0 to 1. */
    fun adjustBrightness(delta: Float): Float
    /** Changes the media volume by [delta] and returns the new level, 0 to 1. */
    fun adjustVolume(delta: Float): Float
    fun quick(button: QuickButton)
    fun menu(entry: MenuEntry)
    fun selectQuality(quality: com.github.andreyasadchy.xtra.model.VideoQuality)
    fun selectSpeed(speed: Float)
    fun setPlayerVolume(volume: Float, done: Boolean)
    /** Starts the sleep timer, or stops it when [minutes] is 0. */
    fun setSleepTimer(minutes: Int, lockScreen: Boolean)
    /** Starts the sleep timer for the next [minuteOfDay] ("at a set time") and remembers that time. */
    fun setSleepTimerAt(minuteOfDay: Int)
    /** Copies [text] to the clipboard (playlist tags). */
    fun copyText(text: String)
    /** Asks for the device admin permission that locking the screen after the sleep timer needs (v0.1). */
    fun requestSleepLockPermission()
    fun seekToChapter(positionMs: Long)
    fun setChatOverlay(overlay: Boolean)
    fun setResizeMode(mode: Int)
    fun openAdBlockSettings()
}

/** The geometry of one frame of the page, in pixels. */
@Immutable
data class PlayerGeometry(
    val layout: PlayerLayout,
    /** The video in the maximized page. */
    val slot: Rect,
    /** The mini player card and the video inside it. */
    val miniCard: Rect,
    val miniThumb: Rect,
    val chat: ChatPlacement,
    /** Tablet: where the left column ends and the chat begins. */
    val paneWidth: Float,
)

/** Layout rules, pure so the tests can check them without drawing (PlayerLayoutTest). */
object PlayerGeometryRules {

    /** A tablet: wider than 840 dp in landscape and at least 600 dp on its short side (big phones are wide but not tall). */
    fun isTablet(screenWidthDp: Int, smallestWidthDp: Int) = screenWidthDp >= 840 && smallestWidthDp >= 600

    fun layoutOf(widthPx: Int, heightPx: Int, screenWidthDp: Int, smallestWidthDp: Int, pip: Boolean, tabletFullscreen: Boolean): PlayerLayout = when {
        pip -> PlayerLayout.Pip
        widthPx <= heightPx -> PlayerLayout.Portrait
        isTablet(screenWidthDp, smallestWidthDp) && !tabletFullscreen -> PlayerLayout.Tablet
        else -> PlayerLayout.Landscape
    }

    /**
     * [chatWidthPx] is v0.1's landscape_chat_width; [sides] the cutout or rounded corner padding in landscape;
     * [bottomBarPx] the navigation's height the mini player sits above.
     */
    fun compute(
        layout: PlayerLayout,
        width: Float,
        height: Float,
        density: Density,
        statusTop: Float,
        sideLeft: Float,
        sideRight: Float,
        bottomInset: Float,
        imeBottom: Float,
        bottomBarPx: Float,
        chatOpen: Boolean,
        chatOverlay: Boolean,
        chatDisabled: Boolean,
        chatWidthPx: Int,
        keyboardShown: Boolean,
        infoHeight: Float,
        miniOffset: Offset,
    ): PlayerGeometry = with(density) {
        val showChat = chatOpen && !chatDisabled
        var paneWidth = width
        val chat: ChatPlacement
        val slot: Rect
        when (layout) {
            PlayerLayout.Pip -> {
                slot = Rect(0f, 0f, width, height)
                chat = ChatPlacement()
            }
            PlayerLayout.Portrait -> {
                val videoHeight = width * 9f / 16f
                slot = Rect(0f, statusTop, width, statusTop + videoHeight)
                val chatTop = slot.bottom + infoHeight
                val chatBottom = height - max(imeBottom, bottomInset)
                chat = ChatPlacement(0, chatTop.roundToInt(), width.roundToInt(), (chatBottom - chatTop).roundToInt().coerceAtLeast(0), visible = !chatDisabled)
            }
            PlayerLayout.Landscape -> {
                val chatWidth = (if (keyboardShown) width / 1.8f else chatWidthPx.toFloat()).coerceAtMost(width * 0.7f)
                val side = showChat && (!chatOverlay || keyboardShown)
                val right = width - sideRight
                slot = Rect(sideLeft, 0f, if (side) right - chatWidth else right, height)
                chat = ChatPlacement(
                    left = (right - chatWidth).roundToInt(),
                    top = 0,
                    width = chatWidth.roundToInt(),
                    height = (height - max(imeBottom, bottomInset)).roundToInt(),
                    visible = showChat,
                    overlay = showChat && !side,
                )
            }
            PlayerLayout.Tablet -> {
                val chatWidth = PlayerLogic.clampChatWidth(chatWidthPx, width.roundToInt()).toFloat()
                paneWidth = if (showChat) width - chatWidth else width
                val minInfo = 160.dp.toPx()
                val videoHeight = min(paneWidth * 9f / 16f, height - statusTop - minInfo).coerceAtLeast(paneWidth * 9f / 32f)
                slot = Rect(0f, statusTop, paneWidth, statusTop + videoHeight)
                chat = ChatPlacement(
                    left = paneWidth.roundToInt(),
                    top = statusTop.roundToInt(),
                    width = chatWidth.roundToInt(),
                    height = (height - statusTop - max(imeBottom, bottomInset)).roundToInt(),
                    visible = showChat,
                )
            }
        }
        // the mini player: a card above the navigation, the video at its start (plan 7.4)
        val margin = 12.dp.toPx()
        val pad = 8.dp.toPx()
        val cardWidth = (if (layout == PlayerLayout.Portrait) min(width - margin * 2, 480.dp.toPx()) else min(360.dp.toPx(), width - margin * 2)).coerceAtLeast(1f)
        val thumbWidth = 112.dp.toPx()
        val thumbHeight = thumbWidth * (slot.height / slot.width.coerceAtLeast(1f)).coerceIn(0.3f, 1f)
        val cardHeight = max(thumbHeight, 52.dp.toPx()) + pad * 2
        // a tiny window (picture in picture) may be smaller than the card: keep the ranges valid
        val cardLeft = (width - sideRight - margin - cardWidth + miniOffset.x).coerceIn(0f, max(0f, width - cardWidth))
        val cardTop = (height - bottomBarPx - margin - cardHeight + miniOffset.y).coerceIn(statusTop, max(statusTop, height - cardHeight))
        val card = Rect(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
        val thumb = Rect(card.left + pad, card.top + (cardHeight - thumbHeight) / 2, card.left + pad + thumbWidth, card.top + (cardHeight + thumbHeight) / 2)
        PlayerGeometry(layout, slot, card, thumb, chat, paneWidth)
    }

    /** The video view at [progress] between the maximized slot (0) and the mini player (1). */
    fun videoPlacement(g: PlayerGeometry, progress: Float, dismissOffset: Float, visible: Boolean): VideoPlacement {
        val s = g.slot
        val t = g.miniThumb
        val scale = lerp(1f, t.width / s.width.coerceAtLeast(1f), progress)
        return VideoPlacement(
            left = s.left.roundToInt(),
            top = s.top.roundToInt(),
            width = s.width.roundToInt(),
            height = s.height.roundToInt(),
            scale = scale,
            translationX = lerp(0f, t.left - s.left, progress) + dismissOffset,
            translationY = lerp(0f, t.top - s.top, progress),
            visible = visible,
        )
    }

    fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
}

/**
 * The player page (plan 7.4, U6): flat, no cards. The video sits flush under the status bar with the controls on
 * gradient scrims; under it one line of channel info that opens into the title and tags, then the chat. Landscape on a
 * phone is full screen with the chat beside or over the video; a tablet in landscape keeps a video column with the
 * chat on the right behind a draggable divider. Minimized, the page is a card above the navigation with the title,
 * pause and close, swiped sideways to dismiss.
 *
 * The video itself is a plain SurfaceView below this composition ([onVideoPlacement] says where), so the ad
 * blocker's surface switch stays v0.1's (see [PlayerConnection]); this layer never paints over the video slot.
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    options: PlayerOptions,
    actions: PlayerActions,
    sheets: PlayerSheetSource,
    bottomBarPx: Int,
    onVideoPlacement: (VideoPlacement) -> Unit,
    onChatPlacement: (ChatPlacement) -> Unit,
    modifier: Modifier = Modifier,
    sideInsets: Pair<Int, Int>? = null,
    now: () -> Long = System::currentTimeMillis,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val configuration = LocalConfiguration.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val layout = PlayerGeometryRules.layoutOf(constraints.maxWidth, constraints.maxHeight, configuration.screenWidthDp, configuration.smallestScreenWidthDp, state.inPictureInPicture, state.tabletFullscreen)
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val cutout = WindowInsets.displayCutout
        val sides = sideInsets ?: (cutout.getLeft(density, direction) to cutout.getRight(density, direction))
        val landscapeSides = layout == PlayerLayout.Landscape
        val imeBottom = WindowInsets.ime.getBottom(density).toFloat()
        val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
        var infoHeight by remember { mutableIntStateOf(with(density) { 64.dp.roundToPx() }) }
        var miniOffset by remember { mutableStateOf(Offset.Zero) }
        val geometry = PlayerGeometryRules.compute(
            layout = layout,
            width = width,
            height = height,
            density = density,
            statusTop = if (layout == PlayerLayout.Landscape || layout == PlayerLayout.Pip) 0f else statusTop,
            sideLeft = if (landscapeSides) sides.first.toFloat() else 0f,
            sideRight = if (landscapeSides) sides.second.toFloat() else 0f,
            bottomInset = navBottom,
            imeBottom = imeBottom,
            bottomBarPx = bottomBarPx.toFloat(),
            chatOpen = state.chatOpen,
            chatOverlay = state.chatOverlay,
            chatDisabled = options.chatDisabled,
            chatWidthPx = state.chatWidthPx,
            keyboardShown = state.keyboardShown,
            infoHeight = infoHeight.toFloat(),
            miniOffset = miniOffset,
        )

        // 0 = maximized, 1 = mini player; follows the mode, and the finger while the video is dragged down
        val progress = remember { Animatable(if (state.mode == PlayerMode.Minimized) 1f else 0f).apply { updateBounds(0f, 1f) } }
        // the finger's speed when a drag down let go (progress per second), handed to the spring that finishes it
        val release = remember { mutableFloatStateOf(0f) }
        val dismiss = remember { Animatable(0f) }
        var dragging by remember { mutableStateOf(false) }
        val motion = Lagoon.motion
        LaunchedEffect(state.mode) {
            val target = if (state.mode == PlayerMode.Minimized) 1f else 0f
            if (state.mode == PlayerMode.Maximized) {
                miniOffset = Offset.Zero
                dismiss.snapTo(0f)
            }
            val velocity = release.floatValue.also { release.floatValue = 0f }
            if (motion.reduced) progress.snapTo(target) else progress.animateTo(target, motion.playerSpring(), initialVelocity = velocity)
        }
        val p = if (state.inPictureInPicture) 0f else progress.value
        val maximized = p == 0f && state.mode == PlayerMode.Maximized && !dragging

        val placement = PlayerGeometryRules.videoPlacement(geometry, p, dismiss.value, visible = true)
        SideEffect { onVideoPlacement(placement) }
        val chat = if (maximized && !state.inPictureInPicture) geometry.chat else geometry.chat.copy(visible = false)
        SideEffect { onChatPlacement(chat) }

        // everything around the video: black above it, the page below it (portrait), black beside it (landscape)
        if (p < 1f && !state.inPictureInPicture) {
            val page = Lagoon.colors.page
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - p }
                    // the maximized page takes every touch so nothing reaches the pages underneath
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                clipRect(geometry.slot.left, geometry.slot.top, geometry.slot.right, geometry.slot.bottom, ClipOp.Difference) {
                    when (layout) {
                        PlayerLayout.Portrait, PlayerLayout.Tablet -> {
                            drawRect(Color.Black, size = Size(size.width, geometry.slot.top))
                            drawRect(page, topLeft = Offset(0f, geometry.slot.top), size = Size(size.width, size.height - geometry.slot.top))
                        }
                        else -> drawRect(Color.Black)
                    }
                }
            }
        }

        if (layout == PlayerLayout.Portrait || layout == PlayerLayout.Tablet) {
            if (p < 1f && !state.inPictureInPicture) {
                PlayerInfo(
                    state = state,
                    options = options,
                    actions = actions,
                    now = now,
                    modifier = Modifier
                        .offset { IntOffset(0, geometry.slot.bottom.roundToInt()) }
                        .width(with(density) { geometry.paneWidth.toDp() })
                        .graphicsLayer { alpha = (1f - p * 3f).coerceAtLeast(0f) }
                        .onSizeChanged { if (it.height > 0) infoHeight = it.height },
                    scrollable = layout == PlayerLayout.Tablet,
                    maxHeight = with(density) { (height - geometry.slot.bottom).toDp() },
                )
            }
        }

        if (layout == PlayerLayout.Tablet && maximized && state.chatOpen && !options.chatDisabled) {
            ChatDivider(geometry, width, actions)
        }

        if (state.mode == PlayerMode.Maximized || state.inPictureInPicture || p < 1f) {
            VideoLayer(
                state = state,
                options = options,
                actions = actions,
                geometry = geometry,
                interactive = state.mode == PlayerMode.Maximized && !state.inPictureInPicture && (p == 0f || dragging),
                progress = p,
                setDragging = { dragging = it },
                progressAnim = progress,
                onRelease = { release.floatValue = it },
                now = now,
            )
        }

        if (p > 0f && !state.inPictureInPicture) {
            MiniPlayer(
                state = state,
                options = options,
                actions = actions,
                geometry = geometry,
                progress = p,
                dismiss = dismiss,
                onMove = { delta -> miniOffset += delta },
            )
        }

        if (!state.inPictureInPicture) {
            PlayerSheetHost(state = state, source = sheets, actions = actions, layout = layout)
        }
    }
}

/** Draws nothing over the video; holds the gestures, the controls, the ad status and the indicators. */
@Composable
private fun VideoLayer(
    state: PlayerUiState,
    options: PlayerOptions,
    actions: PlayerActions,
    geometry: PlayerGeometry,
    interactive: Boolean,
    progress: Float,
    setDragging: (Boolean) -> Unit,
    progressAnim: Animatable<Float, *>,
    onRelease: (Float) -> Unit,
    now: () -> Long,
) {
    val density = LocalDensity.current
    val motion = Lagoon.motion
    val slot = geometry.slot
    val scope = rememberCoroutineScope()
    var touchStamp by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var seekHint by remember { mutableStateOf<SeekHint?>(null) }
    var level by remember { mutableStateOf<LevelHint?>(null) }
    // v0.1: no picture to watch (audio only, chat only) or a finished video keeps the controls up
    val pinned = state.audioOnly || state.chatOnly || (state.ended && state.kind.seekable)
    val visible = interactive && progress == 0f && (state.controlsVisible || pinned)

    // v0.1: the controls hide 3 s after the last touch while playing; paused, ended or audio only they stay
    LaunchedEffect(state.controlsVisible, state.showPlayButton, touchStamp, scrubbing, pinned) {
        if (state.controlsVisible && !state.showPlayButton && !scrubbing && !pinned) {
            delay(3000)
            state.controlsVisible = false
        }
    }
    LaunchedEffect(seekHint) { if (seekHint != null) { delay(650); seekHint = null } }
    LaunchedEffect(level) { if (level != null) { delay(900); level = null } }

    val touch = { touchStamp = System.nanoTime() }
    val layout = geometry.layout
    val dragRange = with(density) { 360.dp.toPx() }
    val fullscreenSwipe = with(density) { 72.dp.toPx() }
    val topEdge = with(density) { 36.dp.toPx() }

    Box(
        Modifier
            .offset { IntOffset(slot.left.roundToInt(), slot.top.roundToInt()) }
            .size(with(density) { slot.width.toDp() }, with(density) { slot.height.toDp() })
            .graphicsLayer { alpha = 1f - progress }
            .testTag("playerVideo")
    ) {
        if (interactive) {
            val seekable = state.kind.seekable
            val doubleTapChat = options.doubleTapChat && layout != PlayerLayout.Portrait
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(seekable, doubleTapChat, layout) {
                        detectTapGestures(
                            onTap = {
                                state.controlsVisible = !state.controlsVisible
                                touch()
                            },
                            onDoubleTap = if (seekable || doubleTapChat) {
                                { offset ->
                                    val third = size.width / 3f
                                    when {
                                        seekable && offset.x < third -> {
                                            actions.rewind(); seekHint = SeekHint(false, options.rewindSeconds)
                                        }
                                        seekable && offset.x > third * 2 -> {
                                            actions.forward(); seekHint = SeekHint(true, options.forwardSeconds)
                                        }
                                        doubleTapChat -> actions.setChatOpen(!state.chatOpen)
                                        else -> state.controlsVisible = !state.controlsVisible
                                    }
                                }
                            } else null,
                        )
                    }
                    .pointerInput(layout) {
                        var startX = 0f
                        var total = 0f
                        var kind = 0 // 1 minimize/fullscreen, 2 brightness, 3 volume
                        val tracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = { start ->
                                startX = start.x
                                total = 0f
                                tracker.resetTracking()
                                kind = when {
                                    // v0.1 statusBarSwipe: in landscape the bars are hidden and come back with a swipe
                                    // from the top edge, which the page still receives; leave that swipe alone
                                    layout == PlayerLayout.Landscape && start.y < topEdge -> 0
                                    layout != PlayerLayout.Landscape -> 1
                                    start.x < size.width / 3f -> 2
                                    start.x > size.width * 2 / 3f -> 3
                                    else -> 1
                                }
                                if (kind == 1) {
                                    setDragging(true)
                                    state.controlsVisible = false
                                }
                            },
                            onDragEnd = {
                                if (kind == 1) {
                                    // progress per second; launched after the queued snapTo calls, so it continues from the last one
                                    val velocity = tracker.calculateVelocity().y / dragRange
                                    when {
                                        // decided on the distance: the snapTo calls below are still queued after a fast flick
                                        total / dragRange > 0.15f -> scope.launch {
                                            onRelease(velocity.coerceAtLeast(0f))
                                            setDragging(false)
                                            actions.minimize()
                                        }
                                        total < -fullscreenSwipe && layout == PlayerLayout.Portrait -> {
                                            setDragging(false)
                                            actions.toggleFullscreen()
                                        }
                                        else -> scope.launch {
                                            progressAnim.animateTo(0f, motion.playerSpring(), initialVelocity = velocity.coerceAtMost(0f))
                                            setDragging(false)
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                if (kind == 1) scope.launch { progressAnim.snapTo(0f); setDragging(false) }
                            },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                total += amount
                                tracker.addPosition(change.uptimeMillis, Offset(0f, total))
                                when (kind) {
                                    1 -> if (total > 0f) scope.launch { progressAnim.snapTo((total / dragRange).coerceIn(0f, 1f)) }
                                    2 -> level = LevelHint(LucideIcons.SunMoon, actions.adjustBrightness(-amount / size.height))
                                    3 -> level = LevelHint(LucideIcons.Volume2, actions.adjustVolume(-amount / size.height))
                                }
                            },
                        )
                    }
            )
        }

        // phone landscape: the closed chat can be pulled out from the right edge (plan 7.4)
        if (interactive && layout == PlayerLayout.Landscape && !state.chatOpen && !options.chatDisabled) {
            val pull = with(density) { 48.dp.toPx() }
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(28.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total < -pull) actions.setChatOpen(true) },
                            onHorizontalDrag = { change, amount -> change.consume(); total += amount },
                        )
                    }
                    .testTag("chatEdge")
            )
        }

        if (state.videoDisabled && progress < 1f) {
            AudioOnlyBackdrop(Modifier.align(Alignment.Center))
        }

        if (state.buffering && !state.showPlayButton) {
            LagoonSpinner(Modifier.align(Alignment.Center).size(44.dp), color = Color.White, strokeWidth = 3.dp)
        }

        AnimatedVisibility(visible, enter = fadeIn(motion.fade()), exit = fadeOut(motion.fadeExit()), modifier = Modifier.fillMaxSize()) {
            PlayerControls(
                state = state,
                options = options,
                actions = actions,
                layout = layout,
                onTouch = touch,
                onScrub = { scrubbing = it; touch() },
                now = now,
            )
        }

        state.adStatus?.let { status ->
            // moves below the controls' top row instead of jumping there (P13)
            val top by animateDpAsState(if (visible) 56.dp else 8.dp, motion.layout(Dp.VisibilityThreshold), label = "adStatusTop")
            OverlayChip(
                text = status.toString(),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = top).testTag("adStatus"),
            )
        }

        // the hints fade in and out (P12); while one shows, a new tap or swipe only changes its text
        FadingHint(seekHint, { Modifier.align(if (it.forward) Alignment.CenterEnd else Alignment.CenterStart) }) { SeekIndicator(it, Modifier) }
        FadingHint(level, { Modifier.align(Alignment.Center) }) { LevelIndicator(it, Modifier) }
    }
}

/** Shows [value] while it is not null, fading in and out; the last value stays on screen while it fades out. */
@Composable
private fun <T : Any> FadingHint(value: T?, place: (T) -> Modifier, content: @Composable (T) -> Unit) {
    val motion = Lagoon.motion
    var last by remember { mutableStateOf<T?>(null) }
    if (value != null) SideEffect { last = value }
    val shown = value ?: last ?: return
    AnimatedVisibility(value != null, modifier = place(shown), enter = fadeIn(motion.fade()), exit = fadeOut(motion.fadeExit())) {
        content(shown)
    }
}

private data class SeekHint(val forward: Boolean, val seconds: Long)
private data class LevelHint(val icon: ImageVector, val level: Float)

@Composable
private fun SeekIndicator(hint: SeekHint, modifier: Modifier) {
    Column(
        modifier
            .padding(horizontal = 32.dp)
            .size(96.dp)
            .background(Color.Black.copy(alpha = 0.45f), LagoonShapes.pill),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (hint.forward) LucideIcons.RotateCw else LucideIcons.RotateCcw, null, tint = Color.White, modifier = Modifier.size(26.dp))
        Text(
            stringResource(if (hint.forward) R.string.pl_seek_forward else R.string.pl_seek_back, hint.seconds.toInt()),
            style = Lagoon.type.caption,
            color = Color.White,
        )
    }
}

@Composable
private fun LevelIndicator(hint: LevelHint, modifier: Modifier) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.6f), LagoonShapes.pill)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(hint.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Box(Modifier.width(120.dp).height(4.dp).background(Color.White.copy(alpha = 0.3f), LagoonShapes.pill)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(hint.level.coerceIn(0f, 1f)).background(Lagoon.colors.accent, LagoonShapes.pill))
        }
    }
}

@Composable
private fun AudioOnlyBackdrop(modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(LucideIcons.Headphones, null, tint = Lagoon.colors.textSecondary, modifier = Modifier.size(36.dp))
        Text(stringResource(R.string.pl_audio_only_playing), style = Lagoon.type.bodyStrong, color = Lagoon.colors.textPrimary)
        Text(stringResource(R.string.pl_audio_only_desc), style = Lagoon.type.label, color = Lagoon.colors.textSecondary)
    }
}

/** The controls over the video: gradient scrims, top row, play and seek in the middle, time line or live row at the bottom. */
@Composable
private fun PlayerControls(
    state: PlayerUiState,
    options: PlayerOptions,
    actions: PlayerActions,
    layout: PlayerLayout,
    onTouch: () -> Unit,
    onScrub: (Boolean) -> Unit,
    now: () -> Long,
) {
    val tap = { action: () -> Unit -> { onTouch(); action() } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f))) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)))
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
        )

        // top row
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (options.showMinimize) {
                VideoButton(LucideIcons.ChevronDown, stringResource(R.string.minimize), tap { actions.minimize() })
            }
            if (layout == PlayerLayout.Landscape) {
                TopInfo(state, options, actions, Modifier.weight(1f).padding(start = 4.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }
            QuickButtons(state, options, actions, onTouch)
            if (layout == PlayerLayout.Landscape && options.showChatToggle && !options.chatDisabled) {
                VideoButton(
                    if (state.chatOpen) LucideIcons.MessageSquare else LucideIcons.MessageCircle,
                    stringResource(if (state.chatOpen) R.string.pl_hide_chat else R.string.pl_show_chat),
                    tap { actions.setChatOpen(!state.chatOpen) },
                )
            }
            VideoButton(LucideIcons.Settings, stringResource(R.string.pl_settings), tap { state.sheet = PlayerSheet.Settings }, tag = "playerSettings")
        }

        // middle
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            if (state.kind.seekable) {
                SeekButton(false, options.rewindSeconds, tap { actions.rewind() })
            }
            if (state.kind.seekable || options.pauseOnLive || state.showPlayButton) {
                val play = state.showPlayButton
                Box(
                    Modifier
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.38f), LagoonShapes.pill)
                        .pressable(onClick = tap { actions.playPause() }, shape = LagoonShapes.pill)
                        .semantics { contentDescription = "" }
                        .testTag("playPause"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (play) LucideIcons.PlayFilled else LucideIcons.PauseFilled,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                    val label = stringResource(if (play) R.string.resume else R.string.pause)
                    Box(Modifier.matchParentSize().semantics { contentDescription = label })
                }
            }
            if (state.kind.seekable) {
                SeekButton(true, options.forwardSeconds, tap { actions.forward() })
            }
        }

        // bottom
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.kind.seekable) {
                var scrubPosition by remember { mutableLongStateOf(-1L) }
                val shown = if (scrubPosition >= 0) scrubPosition else state.positionMs
                Text(PlayerLogic.time(shown), style = Lagoon.type.caption, color = Color.White)
                SeekBar(
                    position = shown,
                    buffered = state.bufferedMs,
                    duration = state.durationMs,
                    onScrub = { onScrub(true); scrubPosition = it },
                    onScrubEnd = { onScrub(false); scrubPosition = -1L; actions.seekTo(it) },
                    modifier = Modifier.weight(1f),
                )
                Text(PlayerLogic.time(state.durationMs), style = Lagoon.type.caption, color = Color.White)
            } else {
                LiveRow(state, options, actions, now, Modifier.weight(1f))
            }
            if (options.showFullscreen) {
                VideoButton(
                    if (layout == PlayerLayout.Portrait || layout == PlayerLayout.Tablet) LucideIcons.Maximize else LucideIcons.Minimize,
                    stringResource(if (layout == PlayerLayout.Portrait || layout == PlayerLayout.Tablet) R.string.fullscreen else R.string.pl_exit_fullscreen),
                    tap { actions.toggleFullscreen() },
                    tag = "fullscreen",
                )
            }
        }
    }
}

@Composable
private fun TopInfo(state: PlayerUiState, options: PlayerOptions, actions: PlayerActions, modifier: Modifier) {
    Column(modifier) {
        if (options.showTitle) state.title?.let {
            Text(it, style = Lagoon.type.bodyStrong, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (options.showChannel) {
                PlayerLogic.displayName(state.channelName, state.channelLogin, options.nameDisplay)?.let {
                    Text(
                        it, style = Lagoon.type.label, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.pressable(onClick = { actions.openChannel() }, shape = LagoonShapes.pill, scaleOnPress = false),
                    )
                }
            }
            if (options.showCategory) state.gameName?.let {
                Text(
                    "· $it", style = Lagoon.type.label, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.pressable(onClick = { actions.openGame() }, shape = LagoonShapes.pill, scaleOnPress = false),
                )
            }
        }
    }
}

@Composable
private fun QuickButtons(state: PlayerUiState, options: PlayerOptions, actions: PlayerActions, onTouch: () -> Unit) {
    val kind = state.kind
    options.quick.forEach { button ->
        val show = when (button) {
            QuickButton.Download -> kind != PlayerKind.Offline
            QuickButton.Follow -> kind != PlayerKind.Offline && options.showFollow && state.following != null
            QuickButton.SleepTimer -> kind != PlayerKind.Clip
            QuickButton.SeekLive -> kind == PlayerKind.Stream
            QuickButton.AudioOnly -> kind != PlayerKind.Offline
            QuickButton.Subtitles -> state.subtitles != SubtitleState.None
            QuickButton.ChatBar -> kind == PlayerKind.Stream && options.loggedIn && !options.chatDisabled
        }
        if (show) {
            val (icon, label) = when (button) {
                QuickButton.Download -> (if (kind == PlayerKind.Stream) LucideIcons.CircleDot else LucideIcons.Download) to stringResource(if (kind == PlayerKind.Stream) R.string.pl_record else R.string.download)
                QuickButton.Follow -> (if (state.following == true) LucideIcons.HeartFilled else LucideIcons.Heart) to stringResource(R.string.follow)
                QuickButton.SleepTimer -> LucideIcons.Moon to stringResource(R.string.pl_sleep)
                QuickButton.SeekLive -> LucideIcons.Radio to stringResource(R.string.pl_seek_live)
                QuickButton.AudioOnly -> LucideIcons.Headphones to stringResource(R.string.audio_only)
                QuickButton.Subtitles -> LucideIcons.Captions to stringResource(R.string.pl_subtitles)
                QuickButton.ChatBar -> LucideIcons.Keyboard to stringResource(R.string.chat_input_toggle)
            }
            val active = (button == QuickButton.AudioOnly && state.audioOnly) || (button == QuickButton.Subtitles && state.subtitles == SubtitleState.On) ||
                    (button == QuickButton.SleepTimer && state.sleepTimerEnd > 0L)
            val enabled = button !in listOf(QuickButton.Download, QuickButton.AudioOnly) || state.loaded
            VideoButton(icon, label, { onTouch(); actions.quick(button) }, active = active, enabled = enabled, tag = "quick${button.name}")
        }
    }
}

@Composable
private fun LiveRow(state: PlayerUiState, options: PlayerOptions, actions: PlayerActions, now: () -> Long, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.background(Lagoon.colors.danger, LagoonShapes.pill).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(6.dp).background(Color.White, LagoonShapes.pill))
            Text(stringResource(R.string.pl_live), style = Lagoon.type.caption, color = Color.White)
        }
        if (options.showUptime) state.startedAt?.let { started ->
            Uptime(started, now) { Text(it, style = Lagoon.type.caption, color = Color.White) }
        }
        state.viewers?.let { viewers ->
            Row(
                Modifier.then(
                    if (options.viewerListFromCount) Modifier.pressable(onClick = { actions.openViewerList() }, shape = LagoonShapes.pill, scaleOnPress = false) else Modifier
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (options.showViewerIcon) Icon(LucideIcons.Eye, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(TwitchApiHelper.formatCount(viewers, options.truncateCounts), style = Lagoon.type.caption, color = Color.White)
            }
        }
    }
}

/** The stream's uptime, ticking once a second. */
@Composable
private fun Uptime(startedAt: Long, now: () -> Long, content: @Composable (String) -> Unit) {
    var tick by remember { mutableLongStateOf(now()) }
    LaunchedEffect(startedAt) {
        while (true) {
            tick = now()
            delay(1000)
        }
    }
    content(PlayerLogic.time(tick - startedAt))
}

@Composable
private fun SeekButton(forward: Boolean, seconds: Long, onClick: () -> Unit) {
    val label = stringResource(if (forward) R.string.pl_seek_forward else R.string.pl_seek_back, seconds.toInt())
    Box(
        Modifier
            .size(52.dp)
            .pressable(onClick = onClick, shape = LagoonShapes.pill)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (forward) LucideIcons.RotateCw else LucideIcons.RotateCcw, null, tint = Color.White, modifier = Modifier.size(30.dp))
        Text(seconds.toString(), style = Lagoon.type.caption, color = Color.White, modifier = Modifier.padding(top = 2.dp))
    }
}

/** An icon button on the video: white icon, no fill (the scrim behind is enough), 48 dp touch target. */
@Composable
private fun VideoButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
    tag: String? = null,
) {
    LagoonIconButton(
        icon = icon,
        contentDescription = label,
        onClick = onClick,
        filled = false,
        enabled = enabled,
        tint = if (active) Lagoon.colors.accent else Color.White,
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
    )
}

/** The time line of a video: buffered and played parts, a thumb that grows while scrubbing. */
@Composable
private fun SeekBar(
    position: Long,
    buffered: Long,
    duration: Long,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Lagoon.colors.accent
    var active by remember { mutableStateOf(false) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val label = stringResource(R.string.pl_seek_bar)
    fun at(x: Float) = if (duration > 0) ((x / widthPx).coerceIn(0f, 1f) * duration).toLong() else 0L
    Box(
        modifier
            .height(36.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(duration) {
                detectTapGestures(onTap = { onScrubEnd(at(it.x)) })
            }
            .pointerInput(duration) {
                var last = 0L
                detectHorizontalDragGestures(
                    onDragStart = { active = true; last = at(it.x); onScrub(last) },
                    onDragEnd = { active = false; onScrubEnd(last) },
                    onDragCancel = { active = false; onScrubEnd(last) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        last = at(change.position.x)
                        onScrub(last)
                    },
                )
            }
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(position.toFloat(), 0f..duration.toFloat().coerceAtLeast(1f))
                setProgress { value -> onScrubEnd(value.toLong()); true }
            }
            .testTag("seekBar"),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2
            val h = 3.dp.toPx()
            val total = duration.coerceAtLeast(1).toFloat()
            fun bar(fraction: Float, color: Color) = drawRoundRect(
                color, topLeft = Offset(0f, y - h / 2), size = Size(size.width * fraction.coerceIn(0f, 1f), h), cornerRadius = CornerRadius(h / 2),
            )
            bar(1f, Color.White.copy(alpha = 0.28f))
            bar(buffered / total, Color.White.copy(alpha = 0.5f))
            bar(position / total, accent)
            drawCircle(accent, radius = (if (active) 8.dp else 6.dp).toPx(), center = Offset(size.width * (position / total).coerceIn(0f, 1f), y))
        }
    }
}

/** Under the video: avatar, name, game and viewers, the follow button; a tap opens the title and the tags (plan 7.4). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerInfo(
    state: PlayerUiState,
    options: PlayerOptions,
    actions: PlayerActions,
    now: () -> Long,
    modifier: Modifier,
    scrollable: Boolean,
    maxHeight: Dp,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = Lagoon.colors
    val name = PlayerLogic.displayName(state.channelName, state.channelLogin, options.nameDisplay) ?: ""
    Column(
        modifier
            .background(colors.page)
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .testTag("playerInfo")
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressable(onClick = { expanded = !expanded }, shape = LagoonShapes.row, scaleOnPress = false)
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(
                state.channelImage, name, size = 36.dp, live = state.kind == PlayerKind.Stream,
                modifier = Modifier.pressable(onClick = { actions.openChannel() }, shape = LagoonShapes.avatar(36.dp)),
            )
            Column(Modifier.weight(1f)) {
                if (options.showChannel) {
                    Text(name, style = Lagoon.type.bodyStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val second = buildList {
                    if (options.showCategory) state.gameName?.let { add(it) }
                    state.viewers?.takeIf { state.kind == PlayerKind.Stream }?.let {
                        add(stringResource(R.string.pl_watching, TwitchApiHelper.formatCount(it, options.truncateCounts)))
                    }
                }.joinToString(" · ")
                if (second.isNotEmpty()) {
                    Text(second, style = Lagoon.type.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (!options.showChannel && options.showTitle) {
                    state.title?.let { Text(it, style = Lagoon.type.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            if (options.showFollow && state.kind != PlayerKind.Offline && state.following != null) {
                FollowButton(following = state.following == true, onToggle = { actions.setFollowing(it) })
            }
            Icon(
                LucideIcons.ChevronDown, null, tint = colors.textHint,
                modifier = Modifier.size(18.dp).rotate(if (expanded) 180f else 0f),
            )
        }
        AnimatedVisibility(expanded || scrollable, enter = Lagoon.motion.expand(), exit = Lagoon.motion.collapse()) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (options.showTitle) state.title?.let {
                    Text(it, style = Lagoon.type.body, color = colors.textPrimary)
                }
                if (state.kind == PlayerKind.Stream && options.showUptime) state.startedAt?.let { started ->
                    Uptime(started, now) { Text(stringResource(R.string.pl_live_for, it), style = Lagoon.type.label, color = colors.textSecondary) }
                }
                if (state.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.tags.forEach { Tag(it) }
                    }
                }
                if (state.kind == PlayerKind.Clip && state.hasFullVideo) {
                    LagoonButton(
                        stringResource(R.string.watch_video), onClick = { actions.watchFullVideo() },
                        style = ButtonStyle.Secondary, icon = LucideIcons.Film,
                    )
                }
            }
        }
    }
}

/** Tablet: the bar between the video column and the chat; dragging it sets the chat's width (landscape_chat_width). */
@Composable
private fun ChatDivider(geometry: PlayerGeometry, windowWidth: Float, actions: PlayerActions) {
    val density = LocalDensity.current
    // the pointer handler outlives recompositions: read the current widths through state
    val chatWidth by rememberUpdatedState(windowWidth - geometry.paneWidth)
    val label = stringResource(R.string.pl_chat_divider)
    val handleWidth = 20.dp
    Box(
        Modifier
            .offset { IntOffset((geometry.paneWidth - with(density) { handleWidth.toPx() } / 2).roundToInt(), geometry.chat.top) }
            .width(handleWidth)
            .height(with(density) { geometry.chat.height.toDp() })
            .pointerInput(Unit) {
                var startWidth = 0f
                var dragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { startWidth = chatWidth; dragX = 0f },
                    onDragEnd = { actions.setChatWidth((startWidth - dragX).roundToInt(), true) },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragX += amount
                        actions.setChatWidth((startWidth - dragX).roundToInt(), false)
                    },
                )
            }
            .semantics { contentDescription = label }
            .testTag("chatDivider"),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(4.dp).height(40.dp).background(Lagoon.colors.textHint, LagoonShapes.pill))
    }
}

/** The mini player: a card above the navigation with the video at its start, the title, pause and close. */
@Composable
private fun MiniPlayer(
    state: PlayerUiState,
    options: PlayerOptions,
    actions: PlayerActions,
    geometry: PlayerGeometry,
    progress: Float,
    dismiss: Animatable<Float, *>,
    onMove: (Offset) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val card = geometry.miniCard
    val thumb = geometry.miniThumb
    val colors = Lagoon.colors
    val cardColor = colors.card
    val name = PlayerLogic.displayName(state.channelName, state.channelLogin, options.nameDisplay)
    val motion = Lagoon.motion
    Box(
        Modifier
            .offset { IntOffset((card.left + dismiss.value).roundToInt(), card.top.roundToInt()) }
            .size(with(density) { card.width.toDp() }, with(density) { card.height.toDp() })
            .graphicsLayer { alpha = progress * (1f - (abs(dismiss.value) / card.width).coerceIn(0f, 1f)) }
            .pointerInput(options.moveFreely) {
                val tracker = VelocityTracker()
                var dx = 0f
                detectDragGestures(
                    onDragStart = { tracker.resetTracking(); dx = 0f },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity().x
                        scope.launch {
                            if (abs(velocity) > 1500f || (!options.moveFreely && abs(dx) > card.width * 0.4f)) {
                                val target = if ((velocity.takeIf { abs(it) > 1500f } ?: dx) > 0) card.width * 1.2f else -card.width * 1.2f
                                dismiss.animateTo(target, motion.playerSpring(), initialVelocity = velocity)
                                actions.dismiss()
                            } else if (options.moveFreely) {
                                onMove(Offset(dx, 0f))
                                dismiss.snapTo(0f)
                            } else {
                                dismiss.animateTo(0f, motion.playerSpring(), initialVelocity = velocity)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { dismiss.animateTo(0f, motion.playerSpring()) } },
                    onDrag = { change, amount ->
                        change.consume()
                        dx += amount.x
                        // the card moves under the finger: track the distance, not the position inside the card
                        tracker.addPosition(change.uptimeMillis, Offset(dx, 0f))
                        val target = dx
                        scope.launch { dismiss.snapTo(target) }
                        if (options.moveFreely && amount.y != 0f) onMove(Offset(0f, amount.y))
                    },
                )
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { actions.maximize() }) }
            .testTag("miniPlayer"),
    ) {
        // the card, with a hole where the video shows through
        Canvas(Modifier.fillMaxSize()) {
            val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(16.dp.toPx()))) }
            val hole = Path().apply {
                addRect(Rect(thumb.left - card.left, thumb.top - card.top, thumb.right - card.left, thumb.bottom - card.top))
            }
            drawPath(Path.combine(PathOperation.Difference, outer, hole), cardColor)
        }
        Row(
            Modifier.fillMaxSize().padding(start = with(density) { (thumb.right - card.left).toDp() } + 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                (state.title ?: name)?.let { Text(it, style = Lagoon.type.label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                name?.takeIf { state.title != null }?.let { Text(it, style = Lagoon.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            LagoonIconButton(
                if (state.showPlayButton) LucideIcons.PlayFilled else LucideIcons.PauseFilled,
                stringResource(if (state.showPlayButton) R.string.resume else R.string.pause),
                onClick = { actions.playPause() },
                filled = false,
                size = 40.dp,
                modifier = Modifier.testTag("miniPlayPause"),
            )
            LagoonIconButton(
                LucideIcons.X, stringResource(R.string.pl_close),
                // the same exit as a swipe (motion plan M1 item 5): slide out and fade, then close
                onClick = { scope.launch { dismiss.animateTo(card.width * 1.2f, motion.playerSpring()); actions.dismiss() } },
                filled = false, size = 40.dp, modifier = Modifier.testTag("miniClose"),
            )
        }
    }
}

/** Actions that do nothing: the settings preview and the screenshot tests draw the controls with them. */
object NoPlayerActions : PlayerActions {
    override fun playPause() = Unit
    override fun rewind() = Unit
    override fun forward() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun minimize() = Unit
    override fun maximize() = Unit
    override fun dismiss() = Unit
    override fun toggleFullscreen() = Unit
    override fun openChannel() = Unit
    override fun openGame() = Unit
    override fun setFollowing(follow: Boolean) = Unit
    override fun openViewerList() = Unit
    override fun watchFullVideo() = Unit
    override fun setChatOpen(open: Boolean) = Unit
    override fun setChatWidth(widthPx: Int, done: Boolean) = Unit
    override fun adjustBrightness(delta: Float) = 0.5f
    override fun adjustVolume(delta: Float) = 0.5f
    override fun quick(button: QuickButton) = Unit
    override fun menu(entry: MenuEntry) = Unit
    override fun selectQuality(quality: com.github.andreyasadchy.xtra.model.VideoQuality) = Unit
    override fun selectSpeed(speed: Float) = Unit
    override fun setPlayerVolume(volume: Float, done: Boolean) = Unit
    override fun setSleepTimer(minutes: Int, lockScreen: Boolean) = Unit
    override fun setSleepTimerAt(minuteOfDay: Int) = Unit
    override fun copyText(text: String) = Unit
    override fun requestSleepLockPermission() = Unit
    override fun seekToChapter(positionMs: Long) = Unit
    override fun setChatOverlay(overlay: Boolean) = Unit
    override fun setResizeMode(mode: Int) = Unit
    override fun openAdBlockSettings() = Unit
}

/**
 * The controls of a live stream in landscape as these [options] draw them, over a stand-in picture: the live preview
 * at the top of the player buttons settings (plan 9, U6 "实时预览").
 */
@Composable
fun PlayerControlsPreview(options: PlayerOptions, modifier: Modifier = Modifier, now: () -> Long = { 1_763_300_000_000L }) {
    val state = remember {
        PlayerUiState().apply {
            kind = PlayerKind.Stream
            title = "Lagoon"
            channelName = "Lagoon"
            channelLogin = "lagoon"
            gameName = "Just Chatting"
            viewers = 12_480
            startedAt = now() - 5_025_000L
            showPlayButton = false
            loaded = true
            following = false
            subtitles = SubtitleState.Off
            controlsVisible = true
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(LagoonShapes.cardMedia)
            .background(Brush.linearGradient(Lagoon.colors.previewGradient))
            .testTag("playerControlsPreview")
    ) {
        PlayerControls(state, options, NoPlayerActions, PlayerLayout.Landscape, onTouch = {}, onScrub = {}, now = now)
    }
}
