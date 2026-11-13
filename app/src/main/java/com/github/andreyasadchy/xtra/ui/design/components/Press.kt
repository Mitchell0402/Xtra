package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonMotion

/** Lightening drawn over a pressed surface: one layer step (card to pressed). */
private val PressHighlight = Color.White.copy(alpha = 0.07f)

/**
 * Press feedback for every tappable card and row (plan 5): it shrinks to 97 % and lightens one step while held, then
 * springs back on release. No ripple. A long press gives a haptic tick before [onLongClick] runs.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    scaleOnPress: Boolean = true,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = Lagoon.motion
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && scaleOnPress) LagoonMotion.PressedScale else 1f,
        animationSpec = if (pressed) motion.press() else motion.bouncy(),
        label = "pressScale",
    )
    val highlight by animateFloatAsState(if (pressed) 1f else 0f, motion.fade(LagoonMotion.INSTANT), label = "pressHighlight")
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .drawWithContent {
            drawContent()
            if (highlight > 0f) drawRect(PressHighlight.copy(alpha = PressHighlight.alpha * highlight))
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick?.let { longClick ->
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    longClick()
                }
            },
            onClick = onClick,
        )
}
