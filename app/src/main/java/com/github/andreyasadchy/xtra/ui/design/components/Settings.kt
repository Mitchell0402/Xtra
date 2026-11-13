package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing

/** What sits at the end of a settings row. */
sealed interface SettingTrailing {
    /** Opens a sub-page. */
    data object Chevron : SettingTrailing
    /** Shows the current value; [chevron] when tapping opens a picker or page. */
    data class Value(val text: String, val chevron: Boolean = true) : SettingTrailing
    data class Toggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : SettingTrailing
    data object None : SettingTrailing
}

/** Rounded tile behind a settings icon. [highlight] tints it teal for the one row a page wants to stand out. */
@Composable
fun SettingIconTile(icon: ImageVector, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val colors = Lagoon.colors
    Box(
        modifier
            .size(34.dp)
            .background(if (highlight) colors.accentSoft else colors.control, LagoonShapes.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (highlight) colors.accent else colors.textPrimary, modifier = Modifier.size(18.dp))
    }
}

/**
 * One settings item (plan 6 and 7.7): icon tile, title, one line of explanation, and the current value, a switch or
 * an arrow. A row with a switch toggles when tapped anywhere.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    highlight: Boolean = false,
    trailing: SettingTrailing = SettingTrailing.Chevron,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = Lagoon.colors
    val type = Lagoon.type
    val toggle = trailing as? SettingTrailing.Toggle
    val click: (() -> Unit)? = when {
        toggle != null -> { { toggle.onCheckedChange(!toggle.checked) } }
        else -> onClick
    }
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .then(
                if (click != null) {
                    Modifier.pressable(
                        onClick = click,
                        shape = LagoonShapes.row,
                        enabled = enabled,
                        role = if (toggle != null) Role.Switch else Role.Button,
                        scaleOnPress = false,
                    )
                } else Modifier
            )
            .heightIn(min = if (description != null) 64.dp else 56.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                if (toggle != null) toggleableState = ToggleableState(toggle.checked)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
    ) {
        if (icon != null) SettingIconTile(icon, highlight = highlight)
        Column(Modifier.weight(1f)) {
            Text(title, style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
            if (description != null) {
                Text(description, style = type.label, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        when (trailing) {
            is SettingTrailing.Value -> {
                Text(
                    trailing.text, style = type.label, color = colors.textSecondary, textAlign = TextAlign.End,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp),
                )
                if (trailing.chevron) Chevron()
            }
            SettingTrailing.Chevron -> Chevron()
            is SettingTrailing.Toggle -> LagoonSwitch(
                checked = trailing.checked,
                // the whole row handles the tap; the switch only shows the state
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {},
            )
            SettingTrailing.None -> Unit
        }
    }
}

@Composable
private fun Chevron() {
    Icon(LucideIcons.ChevronRight, contentDescription = null, tint = Lagoon.colors.textHint, modifier = Modifier.size(16.dp))
}

/**
 * A rounded group of settings rows with an optional title above and explanation below (plan 6, replaces the
 * preference category headers).
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title, style = Lagoon.type.labelStrong, color = Lagoon.colors.textSecondary,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp).semantics { heading() },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Lagoon.colors.card, LagoonShapes.card)
                .padding(vertical = 4.dp),
            content = content,
        )
        if (description != null) {
            Text(
                description, style = Lagoon.type.label, color = Lagoon.colors.textHint,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
            )
        }
    }
}

/** Rows that only matter when a switch is on: they expand and collapse instead of greying out (plan 5). */
@Composable
fun SettingDependents(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val motion = Lagoon.motion
    AnimatedVisibility(visible = visible, enter = motion.expand(), exit = motion.collapse()) {
        Column(content = content)
    }
}
