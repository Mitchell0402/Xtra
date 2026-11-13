package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.SuccessResult
import coil3.decode.DataSource
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonMotion
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes

/**
 * An image that fades in when it arrives instead of popping (plan 5). [model] is anything Coil loads (usually a URL),
 * or a [Painter] for local and test images. Images straight from the memory cache appear at once.
 */
@Composable
fun LagoonImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (model is Painter) {
        Image(model, contentDescription, modifier, contentScale = contentScale)
        return
    }
    val motion = Lagoon.motion
    val alpha = remember(model) { Animatable(0f) }
    var fadeIn by remember(model) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(fadeIn) {
        when (fadeIn) {
            true -> alpha.animateTo(1f, motion.fade(LagoonMotion.SHORT))
            false -> alpha.snapTo(1f)
            null -> Unit
        }
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer { this.alpha = alpha.value },
        contentScale = contentScale,
        onSuccess = { state -> fadeIn = (state.result as? SuccessResult)?.dataSource != DataSource.MEMORY_CACHE },
    )
}

/** Muted tile colours for avatars without a picture, picked by name so a channel always gets the same one. */
private val AvatarTiles = listOf(
    Color(0xFF5B4BB7), Color(0xFF2F7A6E), Color(0xFF3D5A99), Color(0xFF7A3D78), Color(0xFF2E6B7A), Color(0xFF6B5A2E),
)

/**
 * A channel avatar: a soft square, or its initial on a muted tile until the picture loads. [live] draws the teal ring
 * that marks a channel that is streaming now (plan 6, channel row).
 */
@Composable
fun Avatar(
    model: Any?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    live: Boolean = false,
) {
    val colors = Lagoon.colors
    val ring = if (live) 2.dp else 0.dp
    val gap = if (live) 2.dp else 0.dp
    val outer = size + (ring + gap) * 2
    Box(
        modifier
            .size(outer)
            .then(if (live) Modifier.border(ring, colors.accent, LagoonShapes.avatar(outer)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val shape = LagoonShapes.avatar(size)
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(AvatarTiles[Math.floorMod(name.hashCode(), AvatarTiles.size)]),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "",
                style = Lagoon.type.bodyStrong.copy(fontSize = (size.value * 0.38f).sp, lineHeight = (size.value * 0.5f).sp),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            if (model != null) {
                LagoonImage(model, contentDescription = null, modifier = Modifier.size(size))
            }
        }
    }
}

/** The soft red live dot. It breathes slowly (plan 5); with reduced motion it stays still. */
@Composable
fun LiveDot(modifier: Modifier = Modifier, size: Dp = 7.dp) {
    val motion = Lagoon.motion
    val alpha = if (motion.reduced) {
        1f
    } else {
        val breathing = rememberInfiniteTransition(label = "liveDot")
        val value by breathing.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
            label = "liveDotAlpha",
        )
        value
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .background(Lagoon.colors.danger, CircleShape)
    )
}

/**
 * Text whose characters roll vertically when they change, for viewer counts (plan 5). Only the changed characters
 * move; an increase rolls up, a decrease rolls down.
 */
@Composable
fun RollingNumber(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val motion = Lagoon.motion
    var previous by remember { mutableStateOf(text) }
    val up = remember(text) { compareCounts(previous, text) >= 0 }
    LaunchedEffect(text) { previous = text }
    Row(modifier.clearAndSetSemantics { contentDescription = text }) {
        // align from the end so a growing number keeps its last digits in place
        val padded = text.padStart(previous.length)
        padded.forEachIndexed { index, char ->
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    val dir = if (up) 1 else -1
                    (slideInVertically(motion.move(LagoonMotion.SHORT)) { dir * it } + fadeIn(motion.fade())) togetherWith
                        (slideOutVertically(motion.move(LagoonMotion.SHORT)) { -dir * it } + fadeOut(motion.fadeExit()))
                },
                label = "rolling$index",
            ) { c ->
                Text(if (c == ' ') "" else c.toString(), style = style, color = color)
            }
        }
    }
}

private fun compareCounts(old: String, new: String): Int {
    val a = old.filter { it.isDigit() }.toLongOrNull() ?: return 1
    val b = new.filter { it.isDigit() }.toLongOrNull() ?: return 1
    return b.compareTo(a)
}

/** Dark glass chip laid over an image: viewer count, duration, "live". */
@Composable
fun OverlayChip(
    text: String,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    rolling: Boolean = false,
) {
    Row(
        modifier
            .background(Lagoon.colors.overlay, LagoonShapes.pill)
            .padding(start = if (live) 8.dp else 10.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (live) LiveDot()
        val style = Lagoon.type.caption
        if (rolling) RollingNumber(text, style, color = Color.White) else Text(text, style = style, color = Color.White)
    }
}
