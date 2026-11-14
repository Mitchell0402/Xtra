package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.github.andreyasadchy.xtra.ui.design.theme.GlassLevel
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing

@Immutable
data class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/** Where the sliding selection block sits, in px along the bar's main axis. */
private class IndicatorTracker {
    val starts = mutableStateMapOf<Int, Float>()
    val sizes = mutableStateMapOf<Int, Float>()
}

/**
 * The phone navigation (plan 6 and 7.1): a floating pill above the gesture bar. The chosen item widens and shows its
 * label; the teal block slides to it on the steady spring. [visible] false slides it away (hide on scroll).
 */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val colors = Lagoon.colors
    val motion = Lagoon.motion
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val tracker = remember { IndicatorTracker() }
    val hide by animateFloatAsState(if (visible) 0f else 1f, motion.steady(), label = "navHide")
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = LagoonSpacing.xl, vertical = 16.dp)
            .graphicsLayer {
                translationY = hide * (size.height + 48.dp.toPx())
                alpha = 1f - hide
            }
    ) {
        GlassSurface(
            GlassLevel.Float,
            LagoonShapes.pill,
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(24.dp, LagoonShapes.pill, ambientColor = Color.Black, spotColor = Color.Black)
                .testTag(NavBarTag),
        ) {
          Box(Modifier.fillMaxSize().padding(6.dp)) {
            val start by animateFloatAsState(tracker.starts[selectedIndex] ?: 0f, motion.steady(), label = "navIndicatorX")
            val width by animateFloatAsState(tracker.sizes[selectedIndex] ?: 0f, motion.steady(), label = "navIndicatorW")
            if (width > 0f) {
                Box(
                    Modifier
                        .graphicsLayer { translationX = start }
                        .width(with(density) { width.toDp() })
                        .fillMaxHeight()
                        .background(colors.accent, LagoonShapes.pill)
                )
            }
            Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val weight by animateFloatAsState(if (selected) 1.3f else 1f, motion.steady(), label = "navWeight")
                    val tint by animateColorAsState(if (selected) colors.onAccent else Lagoon.colors.textSecondary, motion.fade(), label = "navTint")
                    Row(
                        Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .onPlaced {
                                tracker.starts[index] = it.positionInParent().x
                                tracker.sizes[index] = it.size.width.toFloat()
                            }
                            .selectable(selected = selected, role = Role.Tab, indication = null, interactionSource = null, onClick = {
                                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(index)
                            })
                            .semantics { contentDescription = item.label },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(motion.fade()) + expandHorizontally(motion.steady()),
                            exit = fadeOut(motion.fadeExit()) + shrinkHorizontally(motion.steady()),
                        ) {
                            Text(
                                item.label, style = Lagoon.type.labelStrong, color = tint, maxLines = 1,
                                overflow = TextOverflow.Clip, modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
          }
        }
    }
}

/**
 * The tablet navigation (plan 4 and 7.1, wider than 840 dp): a floating vertical pill on the left. Each item shows its
 * icon and a short label; the teal block slides between them.
 */
@Composable
fun FloatingNavRail(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val colors = Lagoon.colors
    val motion = Lagoon.motion
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val tracker = remember { IndicatorTracker() }
    Box(
        modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // nothing ever scrolls behind the rail (pages start to its right), so it is glass without the blur
        GlassSurface(
            GlassLevel.Float,
            LagoonShapes.card,
            Modifier
                .width(80.dp)
                .shadow(24.dp, LagoonShapes.card, ambientColor = Color.Black, spotColor = Color.Black)
                .testTag(NavRailTag),
            blur = false,
        ) {
        Column(
            Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (header != null) {
                header()
                Spacer(Modifier.height(8.dp))
            }
            Box {
                val top by animateFloatAsState(tracker.starts[selectedIndex] ?: 0f, motion.steady(), label = "railIndicatorY")
                val height by animateFloatAsState(tracker.sizes[selectedIndex] ?: 0f, motion.steady(), label = "railIndicatorH")
                if (height > 0f) {
                    Box(
                        Modifier
                            .graphicsLayer { translationY = top }
                            .fillMaxWidth()
                            .height(with(density) { height.toDp() })
                            .background(colors.accent, LagoonShapes.row)
                    )
                }
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.forEachIndexed { index, item ->
                        val selected = index == selectedIndex
                        val tint by animateColorAsState(if (selected) colors.onAccent else Lagoon.colors.textSecondary, motion.fade(), label = "railTint")
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .onPlaced {
                                    tracker.starts[index] = it.positionInParent().y
                                    tracker.sizes[index] = it.size.height.toFloat()
                                }
                                .selectable(selected = selected, role = Role.Tab, indication = null, interactionSource = null, onClick = {
                                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelect(index)
                                })
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                            Text(item.label, style = Lagoon.type.caption, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        }
    }
}

/** Test tags of the navigation surfaces (blur-area and layout tests). */
const val NavBarTag = "lagoonNavBar"
const val NavRailTag = "lagoonNavRail"

/**
 * Collapse state of a [LargeTopBar]. Scrolling the content up collapses the bar first; it expands again only when the
 * content is back at its top. Attach [nestedScrollConnection] to the scrolling container's parent.
 */
@Stable
class TopBarState(private val collapseRangePx: Float) {
    var offsetPx by mutableFloatStateOf(0f)
        private set

    /** 0 = expanded, 1 = collapsed. */
    val collapsedFraction: Float get() = if (collapseRangePx == 0f) 0f else -offsetPx / collapseRangePx

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f) return Offset.Zero
            val old = offsetPx
            offsetPx = (offsetPx + available.y).coerceIn(-collapseRangePx, 0f)
            return Offset(0f, offsetPx - old)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f) return Offset.Zero
            val old = offsetPx
            offsetPx = (offsetPx + available.y).coerceIn(-collapseRangePx, 0f)
            return Offset(0f, offsetPx - old)
        }
    }

    fun snapTo(fraction: Float) {
        offsetPx = -fraction.coerceIn(0f, 1f) * collapseRangePx
    }
}

/** Height of a [LargeTopBar]'s title area, expanded and collapsed (pages with a floating bar pad their lists by it). */
val TopBarExpanded = 112.dp
val TopBarCollapsed = 64.dp

@Composable
fun rememberTopBarState(): TopBarState {
    val range = with(LocalDensity.current) { (TopBarExpanded - TopBarCollapsed).toPx() }
    return remember(range) { TopBarState(range) }
}

/**
 * Top bar with a large page title that shrinks into a small one as the page scrolls (plan 5 and 6). Actions sit on
 * the right as round icon buttons; [navigationIcon] usually holds the back button. [bottom] holds controls that
 * belong to the bar, such as the segment tabs (glass plan section 9).
 *
 * [floating]: the bar is drawn over the list, which scrolls underneath (stage G). It is clear while expanded; as it
 * collapses, the page colour fades in behind the title and fades out again across [bottom], so the floating glass
 * tabs keep content around and behind them. The bar itself is not glass: glass is for what can be tapped.
 */
@Composable
fun LargeTopBar(
    title: String,
    state: TopBarState,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: (@Composable () -> Unit)? = null,
    floating: Boolean = false,
) {
    val fraction = state.collapsedFraction
    val page = Lagoon.colors.page
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (floating) {
                    Modifier.drawBehind {
                        if (fraction > 0f) {
                            val titleBottom = statusTop + lerp(TopBarExpanded.value, TopBarCollapsed.value, fraction).dp.toPx()
                            val fadeEnd = maxOf(size.height, titleBottom + 16.dp.toPx())
                            drawRect(page.copy(alpha = fraction), size = Size(size.width, titleBottom))
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(page.copy(alpha = fraction), page.copy(alpha = 0f)),
                                    startY = titleBottom, endY = fadeEnd,
                                ),
                                topLeft = Offset(0f, titleBottom),
                                size = Size(size.width, fadeEnd - titleBottom),
                            )
                        }
                    }
                } else {
                    Modifier.background(page)
                }
            )
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        TopBarTitle(title, fraction, navigationIcon, actions)
        bottom?.invoke()
    }
}

@Composable
private fun TopBarTitle(
    title: String,
    fraction: Float,
    navigationIcon: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    val type = Lagoon.type
    val smallScale = type.headline.fontSize.value / type.display.fontSize.value
    Box(
        Modifier
            .fillMaxWidth()
            .height(lerp(TopBarExpanded.value, TopBarCollapsed.value, fraction).dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(TopBarCollapsed).padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null) navigationIcon() else Spacer(Modifier.width(8.dp))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        val startExpanded = 20.dp
        val startCollapsed = if (navigationIcon != null) 60.dp else 20.dp
        Text(
            title,
            style = type.display,
            color = Lagoon.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = lerp(startExpanded.value, startCollapsed.value, fraction).dp,
                    end = lerp(20f, 120f, fraction).dp,
                    bottom = lerp(8f, 21f, fraction).dp,
                )
                // measure at the size it is drawn: the scaled-down title may use the room its full size would not
                .layout { measurable, constraints ->
                    val s = lerp(1f, smallScale, fraction)
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = (constraints.maxWidth / s).toInt()))
                    layout(minOf(placeable.width, constraints.maxWidth), placeable.height) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val s = lerp(1f, smallScale, fraction)
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 1f)
                }
                .semantics { heading() },
        )
    }
}

/**
 * Hides the bottom navigation while the user scrolls down a list and brings it back on the way up (plan 5).
 * [locked] keeps it shown, for when the mini player sits above it (plan 7.4).
 */
@Stable
class HideOnScrollState {
    var visible by mutableStateOf(true)
        private set
    var locked by mutableStateOf(false)

    private var travel = 0f

    /** Brings the bar back at once, e.g. when the page changes or the lock changes. */
    fun show() {
        visible = true
        travel = 0f
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (locked) {
                visible = true
                return Offset.Zero
            }
            travel = if (available.y * travel < 0f) available.y else travel + available.y
            if (travel < -Threshold) visible = false else if (travel > Threshold) visible = true
            return Offset.Zero
        }
    }

    private companion object {
        const val Threshold = 24f
    }
}

@Composable
fun rememberHideOnScrollState() = remember { HideOnScrollState() }
