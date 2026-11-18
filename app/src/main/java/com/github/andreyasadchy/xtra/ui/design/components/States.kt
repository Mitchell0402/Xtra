package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonMotion
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.design.theme.ContinuousMotion
import com.github.andreyasadchy.xtra.ui.design.theme.continuousTransition
import kotlinx.coroutines.delay

/**
 * Skeleton fill with a soft highlight sweeping across (plan 5, first load). With reduced motion the block stays
 * still.
 */
fun Modifier.shimmer(shape: Shape = LagoonShapes.row): Modifier = composed {
    val colors = Lagoon.colors
    val motion = Lagoon.motion
    val progress = continuousTransition("shimmer", ContinuousMotion.Progress)?.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = motion.loop(LagoonMotion.ShimmerMs),
        label = "shimmerProgress",
    )?.value ?: -1f
    this
        .background(colors.skeleton, shape)
        .drawWithContent {
            drawContent()
            if (progress >= 0f) {
                val band = size.width * 0.6f
                val x = -band + (size.width + 2 * band) * progress
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, colors.skeletonHighlight.copy(alpha = 0.7f), Color.Transparent),
                        start = Offset(x - band / 2, 0f), end = Offset(x + band / 2, size.height),
                    )
                )
            }
        }
}

/** A grey bar standing in for one line of text. */
@Composable
fun SkeletonLine(width: Float, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    Box(modifier.fillMaxWidth(width).height(height).shimmer(LagoonShapes.pill))
}

/** Skeleton with the same shape as [LiveCard], for its first load. */
@Composable
fun SkeletonLiveCard(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.design_loading)
    Column(
        modifier
            .background(Lagoon.colors.card, LagoonShapes.card)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 14.dp)
            .semantics { contentDescription = label }
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).shimmer(LagoonShapes.cardMedia))
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).shimmer(LagoonShapes.avatar(42.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonLine(0.85f, Modifier.padding(top = 4.dp))
                SkeletonLine(0.4f)
            }
        }
    }
}

/** Skeleton with the shape of a [ChannelRow] or settings row. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).shimmer(LagoonShapes.avatar(40.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(0.5f)
            SkeletonLine(0.3f, height = 10.dp)
        }
    }
}

/**
 * Which lists of a page have already played their first-load fade (motion plan M1 item 1). Kept in saved state at
 * the page, outside segments and pagers, so going back, switching tabs or segments, scrolling an item away and back,
 * or search results arriving letter by letter never replay it.
 */
@Stable
class StaggerMemory(shown: List<String> = emptyList()) {
    internal val shown = mutableStateListOf<String>().apply { addAll(shown) }

    companion object {
        val Saver: Saver<StaggerMemory, Any> = listSaver(save = { it.shown.toList() }, restore = { StaggerMemory(it) })
    }
}

@Composable
fun rememberStaggerMemory(): StaggerMemory = rememberSaveable(saver = StaggerMemory.Saver) { StaggerMemory() }

/** The page's [StaggerMemory]; a [StaggerGate] without one keeps its own. */
val LocalStaggerMemory = staticCompositionLocalOf<StaggerMemory?> { null }

/** Whether the items under it may play the first-load fade now. Only [StaggerGate] turns it on. */
internal val LocalStaggerOpen = compositionLocalOf { false }

/**
 * The first-load fade of one list, played once (motion plan M1 item 1): it opens in the frame the list first has
 * items, stays open for the whole stagger, then closes for good. A later refresh, a list scrolled away and back, or
 * the page shown again do not open it. [key] names the list within the page, for example the segment.
 */
@Composable
fun StaggerGate(key: String, hasItems: Boolean, content: @Composable () -> Unit) {
    val memory = LocalStaggerMemory.current ?: rememberStaggerMemory()
    var open by remember(key) { mutableStateOf(false) }
    // decided in composition, so the first frame with items is already transparent
    val opening = hasItems && key !in memory.shown
    if (opening) {
        SideEffect {
            if (key !in memory.shown) memory.shown += key
            open = true
        }
    }
    LaunchedEffect(key, open) {
        if (open) {
            delay(StaggerWindowMs)
            open = false
        }
    }
    CompositionLocalProvider(LocalStaggerOpen provides (open || opening)) { content() }
}

/** Long enough for the last staggered item to finish, with two frames to spare. */
internal const val StaggerWindowMs = (LagoonMotion.StaggerLimit - 1) * LagoonMotion.StaggerMs + LagoonMotion.SHORT + 40L

/**
 * Fades an item in when its list first loads, each one [LagoonMotion.StaggerMs] after the previous (plan 5). Only the
 * first [LagoonMotion.StaggerLimit] items take part, and only while the list's [StaggerGate] is open; outside a gate
 * items appear at once.
 */
fun Modifier.staggeredAppear(index: Int, enabled: Boolean = true): Modifier = composed {
    if (!enabled || !LocalStaggerOpen.current || index >= LagoonMotion.StaggerLimit) return@composed this
    val motion = Lagoon.motion
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * LagoonMotion.StaggerMs.toLong())
        progress.animateTo(1f, motion.fade(LagoonMotion.SHORT))
    }
    graphicsLayer {
        alpha = progress.value
        if (!motion.reduced) translationY = (1f - progress.value) * 8.dp.toPx()
    }
}

/** Soft rings behind a big icon: the empty and error illustrations. */
@Composable
private fun Illustration(icon: ImageVector, tint: Color) {
    val colors = Lagoon.colors
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(120.dp)) {
            drawCircle(colors.card, radius = size.minDimension / 2)
            drawCircle(colors.control, radius = size.minDimension / 2 * 0.68f)
            drawCircle(colors.accent.copy(alpha = 0.10f), radius = size.minDimension / 2 * 0.68f, center = center + Offset(size.width * 0.12f, -size.height * 0.1f))
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
    }
}

/**
 * Empty state (plan 6, replaces "Nothing here"): an illustration, one sentence and at most one button.
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = LucideIcons.Sparkles,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = LagoonSpacing.xxl, vertical = LagoonSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Illustration(icon, Lagoon.colors.textSecondary)
        Spacer(Modifier.height(20.dp))
        Text(
            message, style = Lagoon.type.body, color = Lagoon.colors.textSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            LagoonButton(actionLabel, onAction, style = ButtonStyle.Secondary)
        }
    }
}

/** Error state with a retry button (plan 6 and 10.5: no crash, no blank page). */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.design_load_failed),
    icon: ImageVector = LucideIcons.WifiOff,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = LagoonSpacing.xxl, vertical = LagoonSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Illustration(icon, Lagoon.colors.danger)
        Spacer(Modifier.height(20.dp))
        Text(title, style = Lagoon.type.headline, color = Lagoon.colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            message, style = Lagoon.type.label, color = Lagoon.colors.textSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        Spacer(Modifier.height(20.dp))
        LagoonButton(stringResource(R.string.design_retry), onRetry, style = ButtonStyle.Secondary, icon = LucideIcons.RotateCcw)
    }
}

/**
 * Pull to refresh with the Lagoon indicator (plan 5): a round chip that follows the finger, stretches a little past
 * the trigger point, spins while loading, and gives one light haptic when the pull is released into a refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LagoonPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    /** Room above the indicator, for pages whose top bar floats over the list (stage G). */
    indicatorTop: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            onRefresh()
        },
        modifier = modifier,
        state = state,
        indicator = { RefreshIndicator(state, isRefreshing, Modifier.align(Alignment.TopCenter).padding(top = indicatorTop)) },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshIndicator(state: PullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    val colors = Lagoon.colors
    val motion = Lagoon.motion
    val spin = if (isRefreshing) {
        continuousTransition("refreshSpin", ContinuousMotion.Progress)
            ?.animateFloat(0f, 360f, motion.loop(LagoonMotion.RefreshSpinMs), label = "refreshAngle")?.value ?: 0f
    } else 0f
    val label = stringResource(R.string.design_pull_to_refresh)
    Box(
        modifier
            .graphicsLayer {
                val f = if (isRefreshing) 1f else state.distanceFraction
                translationY = f.coerceAtMost(1.6f) * 72.dp.toPx() - 44.dp.toPx()
                alpha = f.coerceIn(0f, 1f)
                // rubber band: stretch a little once pulled past the trigger point
                val stretch = 1f + (f - 1f).coerceIn(0f, 0.6f) * 0.25f
                scaleY = stretch
                scaleX = 1f / stretch
            }
            .size(40.dp)
            .background(colors.control, LagoonShapes.pill)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            LucideIcons.RefreshCw, contentDescription = null, tint = colors.accent,
            modifier = Modifier.size(20.dp).graphicsLayer {
                rotationZ = if (isRefreshing) spin else state.distanceFraction * 270f
            },
        )
    }
}
