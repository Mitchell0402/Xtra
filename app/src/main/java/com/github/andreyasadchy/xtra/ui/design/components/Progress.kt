package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes

/*
 * Material 3 pieces the pages use, wrapped with Lagoon colours (U9 gate 1: outside ui/design no page imports a
 * Material component, so a default purple can never come back through one).
 */

/** Indeterminate spinner: accent on dark pages; [color] white over video. */
@Composable
fun LagoonSpinner(modifier: Modifier = Modifier, color: Color = Lagoon.colors.accent, strokeWidth: Dp = 4.dp) {
    CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
}

/** Progress bar: [progress] from 0 to 1, or null for indeterminate. */
@Composable
fun LagoonProgressBar(progress: (() -> Float)?, modifier: Modifier = Modifier) {
    val colors = Lagoon.colors
    if (progress != null) {
        LinearProgressIndicator(
            progress = progress, modifier = modifier, color = colors.accent, trackColor = colors.control,
            strokeCap = StrokeCap.Round, gapSize = 0.dp,
        )
    } else {
        LinearProgressIndicator(modifier, color = colors.accent, trackColor = colors.control)
    }
}

/** Two-thumb slider (download range). */
@Composable
fun LagoonRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = Lagoon.colors
    RangeSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.control),
        modifier = modifier,
    )
}

/** A swipe to the left reveals the red delete tint and calls [onRemove] (the host shows the undo). */
@Composable
fun SwipeToDelete(label: String, onRemove: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(Lagoon.colors.dangerSoft, LagoonShapes.card).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(LucideIcons.Trash2, contentDescription = label, tint = Lagoon.colors.danger)
            }
        },
    ) { content() }
}
