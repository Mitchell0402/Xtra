package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.GlassLevel
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonGlass
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing

@Immutable
data class ChoiceOption(val label: String, val description: String? = null)

@Composable
private fun ChoiceRow(
    option: ChoiceOption,
    modifier: Modifier,
    mark: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (option.description != null) 64.dp else 52.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
    ) {
        Column(Modifier.weight(1f)) {
            Text(option.label, style = Lagoon.type.body, color = Lagoon.colors.textPrimary)
            if (option.description != null) {
                Text(option.description, style = Lagoon.type.label, color = Lagoon.colors.textSecondary)
            }
        }
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { mark() }
    }
}

/** Single choice (plan 6, replaces the radio dialogs): the chosen row gets a check that pops in with a bounce. */
@Composable
fun SingleChoiceList(
    options: List<ChoiceOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = Lagoon.motion
    val haptics = LocalHapticFeedback.current
    Column(modifier.selectableGroup()) {
        options.forEachIndexed { index, option ->
            ChoiceRow(
                option,
                Modifier
                    .selectable(selected = index == selected, role = Role.RadioButton, onClick = {
                        if (index != selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(index)
                    })
            ) {
                AnimatedVisibility(
                    visible = index == selected,
                    enter = scaleIn(motion.bouncy(), initialScale = 0.4f) + fadeIn(motion.fade()),
                    exit = scaleOut(motion.move(), targetScale = 0.6f) + fadeOut(motion.fadeExit()),
                ) {
                    Icon(LucideIcons.Check, contentDescription = null, tint = Lagoon.colors.accent, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** Multiple choice: rounded boxes that fill teal and show a bouncing check. */
@Composable
fun MultiChoiceList(
    options: List<ChoiceOption>,
    selected: Set<Int>,
    onToggle: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Lagoon.colors
    val motion = Lagoon.motion
    Column(modifier) {
        options.forEachIndexed { index, option ->
            val on = index in selected
            ChoiceRow(option, Modifier.toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(index, it) })) {
                val fill by animateColorAsState(if (on) colors.accent else Color.Transparent, motion.fade(), label = "checkFill")
                Box(
                    Modifier
                        .size(22.dp)
                        .background(fill, RoundedCornerShape(7.dp))
                        .then(if (on) Modifier else Modifier.border(2.dp, colors.textHint, RoundedCornerShape(7.dp))),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = on,
                        enter = scaleIn(motion.bouncy(), initialScale = 0.4f) + fadeIn(motion.fade()),
                        exit = scaleOut(motion.move()) + fadeOut(motion.fadeExit()),
                    ) {
                        Icon(LucideIcons.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** A number setting: title and current value above a teal slider. */
@Composable
fun LagoonSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = Lagoon.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Lagoon.type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(valueLabel, style = Lagoon.type.labelStrong, color = colors.accent)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.pressed,
                activeTickColor = colors.onAccent.copy(alpha = 0.5f),
                inactiveTickColor = colors.textHint,
            ),
        )
    }
}

/**
 * Segmented control (plan 6, "segmented buttons", and the pill tabs under top bars in 7.1): a grey track with a
 * lighter block that slides to the chosen segment on the steady spring.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
) {
    if (glass) {
        // floating over a list (glass plan, Mitchell's note: switchable tabs are glass): the selected segment is a
        // lighter lens, so the teal block stays the navigation's alone
        GlassSurface(GlassLevel.Float, LagoonShapes.pill, modifier.fillMaxWidth().height(LagoonSpacing.touchTarget)) {
            SegmentedTrack(options, selectedIndex, onSelect, LagoonGlass.segmentSelected)
        }
    } else {
        Box(modifier.fillMaxWidth().height(LagoonSpacing.touchTarget).background(Lagoon.colors.card, LagoonShapes.pill)) {
            SegmentedTrack(options, selectedIndex, onSelect, Lagoon.colors.selectedTrack)
        }
    }
}

@Composable
private fun SegmentedTrack(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, selectedColor: Color) {
    val motion = Lagoon.motion
    val measurer = rememberTextMeasurer()
    val style = Lagoon.type.labelStrong
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
        val equal = maxWidth / options.size.coerceAtLeast(1)
        // labels that do not fit an equal share (five channel segments in English) make the track scroll, each
        // segment as wide as its label; otherwise every segment gets the same share, as before
        val natural = remember(options, style, density) {
            options.map { with(density) { measurer.measure(it, style, maxLines = 1).size.width.toDp() } }
        }
        // a label fits when it does not need its ellipsis (the text keeps 8 dp on each side)
        if (natural.all { it + 16.dp <= equal }) {
            val offset by animateDpAsState(equal * selectedIndex, motion.steady(), label = "segmentOffset")
            Box(
                Modifier
                    .offset(x = offset)
                    .width(equal)
                    .fillMaxHeight()
                    .background(selectedColor, LagoonShapes.pill)
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
                options.forEachIndexed { index, label ->
                    SegmentLabel(label, index == selectedIndex, { onSelect(index) }, Modifier.weight(1f))
                }
            }
        } else {
            val widths = natural.map { maxOf(it + SegmentLabelPadding * 2, equal) }
            val starts = widths.runningFold(0.dp) { acc, w -> acc + w }
            val index = selectedIndex.coerceIn(0, options.lastIndex)
            val offset by animateDpAsState(starts[index], motion.steady(), label = "segmentOffset")
            val width by animateDpAsState(widths[index], motion.steady(), label = "segmentWidth")
            val scroll = rememberScrollState()
            val viewport = maxWidth
            LaunchedEffect(index, viewport) {
                // keep the chosen segment in view, centred where the track allows
                val target = with(density) { (starts[index] - (viewport - widths[index]) / 2).roundToPx() }
                scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue.coerceAtLeast(0)))
            }
            Box(Modifier.fillMaxHeight().horizontalScroll(scroll)) {
                Box(
                    Modifier
                        .offset(x = offset)
                        .width(width)
                        .fillMaxHeight()
                        .background(selectedColor, LagoonShapes.pill)
                )
                Row(Modifier.fillMaxHeight().selectableGroup()) {
                    options.forEachIndexed { i, label ->
                        SegmentLabel(label, i == selectedIndex, { onSelect(i) }, Modifier.width(widths[i]))
                    }
                }
            }
        }
    }
}

private val SegmentLabelPadding = 12.dp

@Composable
private fun SegmentLabel(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = Lagoon.colors
    val motion = Lagoon.motion
    val haptics = LocalHapticFeedback.current
    val textColor by animateColorAsState(
        if (selected) colors.textPrimary else colors.textSecondary, motion.fade(), label = "segmentText",
    )
    Box(
        modifier
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, indication = null, interactionSource = null, onClick = {
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = Lagoon.type.labelStrong, color = textColor, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
