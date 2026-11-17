package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.ui.design.theme.GlassLevel
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import kotlinx.coroutines.launch

/**
 * The bottom sheet that replaces the old dialogs and system pop-ups (plan 6): rounded top, drag handle, half and full
 * height, drag down to close, dimmed background.
 *
 * [skipHalfHeight] opens it straight at full height (long forms); otherwise it opens at half height when the content
 * is taller than half the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LagoonBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    skipHalfHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) = LagoonBottomSheet(onDismiss, rememberModalBottomSheetState(skipPartiallyExpanded = skipHalfHeight), modifier, title, content)

// Material's SheetState is still experimental, so it stays out of the public signature
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LagoonBottomSheet(
    onDismiss: () -> Unit,
    state: SheetState,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Lagoon.colors
    // the sheet is its own window over the 60 % scrim, so it is the thick grade: solid glass, never blurred
    // (glass plan sections 5 and 6); the material is drawn by our surface, Material's container stays empty
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = state,
        shape = LagoonShapes.sheet,
        containerColor = Color.Transparent,
        contentColor = colors.textPrimary,
        scrimColor = colors.scrim,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        // U9 C2: the sheet stops below the status bar and the camera cutout and rides above the keyboard; its
        // content sizes itself from LocalSheetMaxHeight, never from the screen height
        val maxHeight = windowSheetMaxHeight()
        // U9 B1: while open, the top sheet draws the activity's toasts (the sheet window would cover any other)
        val toasts = LocalLagoonToasts.current
        var toastId by remember { mutableStateOf<Long?>(null) }
        DisposableEffect(toasts) {
            val id = toasts?.sheetOpened()
            toastId = id
            onDispose { if (id != null) toasts.sheetClosed(id) }
        }
        CompositionLocalProvider(LocalSheetMaxHeight provides maxHeight) {
            GlassSurface(GlassLevel.Thick, LagoonShapes.sheet, Modifier.fillMaxWidth().heightIn(max = maxHeight + imeHeight())) {
                Box {
                    Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
                        SheetHandle()
                        SheetBody(title, content = content)
                    }
                    if (toasts != null && toastId != null && toasts.drawingSheet == toastId) {
                        ToastHost(
                            toasts.state,
                            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
                            owner = true,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Height a sheet may take: the window minus the top safe area (status bar or camera cutout, whichever is lower),
 * the keyboard and an 8 dp gap. Sheet contents size their lists as a share of this (U9 C2).
 */
fun sheetMaxHeight(windowHeight: Dp, topInset: Dp, imeInset: Dp): Dp =
    (windowHeight - topInset - imeInset - 8.dp).coerceAtLeast(MinSheetHeight)

private val MinSheetHeight = 120.dp

/** Set by [LagoonBottomSheet] for its content; outside a sheet window [currentSheetMaxHeight] works it out. */
val LocalSheetMaxHeight = compositionLocalOf { Dp.Unspecified }

/** The height the current sheet's content may use (see [sheetMaxHeight]). */
@Composable
fun currentSheetMaxHeight(): Dp = LocalSheetMaxHeight.current.takeIf { it.isSpecified } ?: windowSheetMaxHeight()

@Composable
private fun imeHeight(): Dp = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }

@Composable
private fun windowSheetMaxHeight(): Dp {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val top = WindowInsets.statusBars.union(WindowInsets.displayCutout).getTop(density)
    return with(density) { sheetMaxHeight(window.height.toDp(), top.toDp(), imeHeight()) }
}

/** A sheet's state for hosts outside ui/design that slide the sheet away themselves (ComposeSheetDialog). */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class LagoonSheetState internal constructor(internal val state: SheetState) {
    suspend fun hide() = state.hide()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberLagoonSheetState(): LagoonSheetState {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    return remember(state) { LagoonSheetState(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LagoonBottomSheet(
    onDismiss: () -> Unit,
    state: LagoonSheetState,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) = LagoonBottomSheet(onDismiss, state.state, modifier, title, content)

/** The grabber at the top of a sheet. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 36.dp, height = 4.dp).background(Lagoon.colors.textHint, LagoonShapes.pill))
    }
}

/** What a sheet shows under its handle: an optional title and the content. Also used to draw a sheet in place (tests, showcase). */
@Composable
fun SheetBody(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(bottom = LagoonSpacing.m)) {
        if (title != null) {
            Text(
                title,
                style = Lagoon.type.headline,
                color = Lagoon.colors.textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() },
            )
        }
        content()
    }
}

/** A sheet drawn in place, without a window: for the showcase and screenshot tests. */
@Composable
fun SheetSurface(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(GlassLevel.Thick, LagoonShapes.sheet, modifier) {
        Column {
            SheetHandle()
            SheetBody(title, content = content)
        }
    }
}

@Immutable
data class ActionItem(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The list of actions in an action sheet (plan 6, replaces the three-dot menus and PopupMenus): icon and label per
 * row, destructive actions in soft red.
 */
@Composable
fun ActionMenu(actions: List<ActionItem>, modifier: Modifier = Modifier, onActionClick: (ActionItem) -> Unit = { it.onClick() }) {
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        actions.forEach { action ->
            val tint = if (action.destructive) Lagoon.colors.danger else Lagoon.colors.textPrimary
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable(onClick = { onActionClick(action) }, shape = LagoonShapes.row, scaleOnPress = false)
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Text(action.label, style = Lagoon.type.body, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** An action sheet: the sheet closes first, then the chosen action runs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(actions: List<ActionItem>, onDismiss: () -> Unit, title: String? = null) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    LagoonBottomSheet(onDismiss = onDismiss, state = state, title = title) {
        ActionMenu(actions) { action ->
            scope.launch { state.hide() }.invokeOnCompletion {
                onDismiss()
                action.onClick()
            }
        }
    }
}
