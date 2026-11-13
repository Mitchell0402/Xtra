package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing

/**
 * One pill in a filter strip. [dropdown] marks chips that open a picker (sort, language) and shows a small chevron;
 * plain chips toggle.
 */
@Immutable
data class FilterChipItem(
    val label: String,
    val selected: Boolean = false,
    val icon: ImageVector? = null,
    val dropdown: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun FilterChip(item: FilterChipItem, modifier: Modifier = Modifier) {
    val colors = Lagoon.colors
    val motion = Lagoon.motion
    val bg by animateColorAsState(if (item.selected) colors.accent else colors.card, motion.fade(), label = "chipBg")
    val fg by animateColorAsState(if (item.selected) colors.onAccent else colors.textPrimary, motion.fade(), label = "chipFg")
    // 36 dp pill, centred in a 48 dp touch target
    Box(modifier.heightIn(min = LagoonSpacing.touchTarget), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .pressable(onClick = item.onClick, shape = LagoonShapes.pill, role = if (item.dropdown) Role.DropdownList else Role.Checkbox)
                .background(bg)
                .heightIn(min = 36.dp)
                .padding(start = if (item.icon != null) 12.dp else 16.dp, end = if (item.dropdown) 10.dp else 16.dp)
                .semantics { if (!item.dropdown) selected = item.selected },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (item.icon != null) Icon(item.icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Text(item.label, style = Lagoon.type.labelStrong, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.dropdown) Icon(LucideIcons.ChevronDown, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        }
    }
}

/** Horizontally scrolling strip of filter and sort chips (plan 6, replaces the sort bar). */
@Composable
fun FilterChipRow(
    chips: List<FilterChipItem>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = LagoonSpacing.pageSide),
) {
    LazyRow(
        modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(chips, key = { index, chip -> "$index:${chip.label}" }) { _, chip -> FilterChip(chip) }
    }
}
