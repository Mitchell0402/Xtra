package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.GlassLevel
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing

enum class ButtonStyle { Primary, Secondary, Danger }

/**
 * Fully round button. Primary (teal fill) is for the one main action of a screen (plan 1, "one primary action per
 * screen"); Secondary is the grey control layer; Danger is soft red text on a red tint.
 */
@Composable
fun LagoonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = Lagoon.colors
    val fg = when (style) {
        ButtonStyle.Primary -> colors.onAccent
        ButtonStyle.Secondary -> colors.textPrimary
        ButtonStyle.Danger -> colors.danger
    }
    // secondary buttons are glass without the blur (glass plan, stage G: actionable buttons get the material)
    val surface = when (style) {
        ButtonStyle.Primary -> Modifier.background(colors.accent)
        ButtonStyle.Secondary -> Modifier.glassSolid(GlassLevel.Float, LagoonShapes.pill)
        ButtonStyle.Danger -> Modifier.background(colors.dangerSoft)
    }
    Row(
        modifier
            .defaultMinSize(minHeight = LagoonSpacing.touchTarget)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .pressable(onClick = onClick, shape = LagoonShapes.pill, enabled = enabled)
            .then(surface)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, style = Lagoon.type.bodyStrong, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Round icon button. The visible circle is [size] (44 dp by default) inside a 48 dp touch target (plan 10.5).
 * [filled] gives it the glass material, as in top bars (stage G); otherwise only the icon shows.
 */
@Composable
fun LagoonIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    tint: Color = Color.Unspecified,
    size: Dp = 44.dp,
    enabled: Boolean = true,
) {
    Box(modifier.size(maxOf(size, LagoonSpacing.touchTarget)), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
                .pressable(onClick = onClick, shape = LagoonShapes.pill, enabled = enabled)
                .then(if (filled) Modifier.glassSolid(GlassLevel.Float, LagoonShapes.pill) else Modifier)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (tint == Color.Unspecified) Lagoon.colors.textPrimary else tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The app's switch: teal track when on, haptic tick on change. */
@Composable
fun LagoonSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Lagoon.colors
    val haptics = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { change ->
            { value: Boolean ->
                haptics.performHapticFeedback(if (value) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                change(value)
            }
        },
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onAccent,
            checkedTrackColor = colors.accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = colors.textSecondary,
            uncheckedTrackColor = colors.pressed,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Single-line input on the card layer: search fields and short text settings. */
@Composable
fun LagoonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = LucideIcons.Search,
    imeAction: ImeAction = ImeAction.Search,
    onImeAction: () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = imeAction),
) {
    val colors = Lagoon.colors
    val type = Lagoon.type
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = type.body.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        decorationBox = { inner ->
            Row(
                Modifier
                    .height(LagoonSpacing.touchTarget)
                    .background(colors.card, LagoonShapes.input)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (leadingIcon != null) Icon(leadingIcon, null, tint = colors.textHint, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = type.body, color = colors.textHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
                AnimatedVisibility(value.isNotEmpty(), enter = fadeIn(Lagoon.motion.fade()), exit = fadeOut(Lagoon.motion.fadeExit())) {
                    LagoonIconButton(
                        LucideIcons.X, stringResource(R.string.design_clear), onClick = { onValueChange("") },
                        filled = false, tint = colors.textSecondary, size = 40.dp,
                    )
                }
            }
        },
    )
}

/**
 * Follow toggle (plan 5): the heart goes from outline to filled with one bounce and a haptic tick. Unfollowing is
 * quiet. Used for follow and, with another icon pair, for bookmarks.
 */
@Composable
fun FollowButton(
    following: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    followLabel: String = stringResource(R.string.design_follow),
    followingLabel: String = stringResource(R.string.design_following),
    icon: ImageVector = LucideIcons.Heart,
    iconSelected: ImageVector = LucideIcons.HeartFilled,
) {
    val colors = Lagoon.colors
    val motion = Lagoon.motion
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val previous = remember { booleanArrayOf(following) }
    LaunchedEffect(following) {
        val became = following && !previous[0]
        previous[0] = following
        if (became && !motion.reduced) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, motion.bouncy())
        }
    }
    Row(
        modifier
            .defaultMinSize(minHeight = LagoonSpacing.touchTarget)
            .pressable(
                onClick = {
                    if (!following) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onToggle(!following)
                },
                shape = LagoonShapes.pill,
                role = Role.Switch,
            )
            .background(colors.card)
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .semantics { stateDescription = if (following) followingLabel else followLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            if (following) iconSelected else icon,
            contentDescription = null,
            tint = if (following) colors.accent else colors.textPrimary,
            modifier = Modifier.size(18.dp).graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        )
        Text(if (following) followingLabel else followLabel, style = Lagoon.type.bodyStrong, color = colors.textPrimary)
    }
}
