package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = state,
        shape = LagoonShapes.sheet,
        containerColor = colors.card,
        contentColor = colors.textPrimary,
        scrimColor = colors.scrim,
        dragHandle = { SheetHandle() },
    ) {
        SheetBody(title, content = content)
    }
}

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
    Column(modifier.background(Lagoon.colors.card, LagoonShapes.sheet)) {
        SheetHandle()
        SheetBody(title, content = content)
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
