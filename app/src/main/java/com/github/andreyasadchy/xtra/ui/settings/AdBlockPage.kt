package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.player.ads.AdBlockPreset
import com.github.andreyasadchy.xtra.player.ads.AdStats
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.components.SettingIconTile
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.settings.model.SettingsPageSpec
import com.github.andreyasadchy.xtra.ui.settings.model.PrefStore

const val AdBlockPresetTagPrefix = "adBlockPreset_"

/** The preset the switches currently match, as the home screen and the page show it. */
@Composable
fun adBlockPresetLabel(store: SettingsStore): String {
    store.observe()
    val key = AdBlockPreset.match { key, default -> store.bool(key, default) }
    return stringResource(presetName(key))
}

private fun presetName(key: String) = when (key) {
    AdBlockPreset.AGGRESSIVE.key -> R.string.st_preset_aggressive
    AdBlockPreset.BALANCED.key -> R.string.st_preset_balanced
    AdBlockPreset.HIDE_ONLY.key -> R.string.st_preset_hide_only
    else -> R.string.ad_block_preset_custom
}

private fun presetDescription(preset: AdBlockPreset) = when (preset) {
    AdBlockPreset.AGGRESSIVE -> R.string.st_preset_aggressive_desc
    AdBlockPreset.BALANCED -> R.string.st_preset_balanced_desc
    AdBlockPreset.HIDE_ONLY -> R.string.st_preset_hide_only_desc
}

/**
 * Ad blocking (finalized design B-AdBlock and Tablet-B-Settings): a status card with what has been blocked so far,
 * the three presets as radio cards, then the switches the presets set, the source lists and the advanced rows.
 * The statistics are the real counters (AdStats); there is no per-day history, so there is no daily chart.
 */
@Composable
fun AdBlockPage(
    page: SettingsPageSpec,
    store: SettingsStore,
    host: SettingsHost,
    nav: SettingsNav,
    actions: PageActions,
    onBack: (() -> Unit)?,
) {
    SettingsPage(page, store, host, nav, actions, onBack, headerItems = 2) {
        item(key = "status") { AdStatusCard(store) }
        item(key = "presets") { PresetPicker(store) }
    }
}

@Composable
private fun AdStatusCard(store: SettingsStore) {
    val colors = Lagoon.colors
    val type = Lagoon.type
    store.observe()
    val stats = AdStats.summary(store.prefs(PrefStore.App))
    Column(
        Modifier.fillMaxWidth().background(colors.card, LagoonShapes.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s)) {
            SettingIconTile(LucideIcons.ShieldCheck, highlight = true)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.st_ad_status_title), style = type.label, color = colors.textSecondary)
                Text(adBlockPresetLabel(store), style = type.bodyStrong, color = colors.textPrimary)
            }
        }
        if (stats.breaks > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                BigNumber(stats.breaks.toString(), stringResource(R.string.st_ad_breaks_unit), colors.accent)
                BigNumber((stats.seconds / 60).toString(), stringResource(R.string.st_ad_minutes_unit), colors.textPrimary)
            }
            val top = stats.byHandler.entries.take(4)
            val max = top.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
            Column(
                Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.st_ad_by_handler), style = type.label, color = colors.textSecondary)
                top.forEach { (handler, count) ->
                    HandlerBar(handlerLabel(handler), count, count.toFloat() / max, shown = handler == "shown")
                }
            }
        } else {
            Text(stringResource(R.string.st_ad_none_yet), style = type.label, color = colors.textSecondary)
        }
    }
}

@Composable
private fun BigNumber(value: String, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = Lagoon.type.display, color = color)
        Text(unit, style = Lagoon.type.label, color = Lagoon.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
private fun HandlerBar(label: String, count: Int, fraction: Float, shown: Boolean) {
    val colors = Lagoon.colors
    val width by animateFloatAsState(fraction.coerceIn(0.04f, 1f), Lagoon.motion.move(), label = "handlerBar")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = Lagoon.type.label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
        BoxWithConstraints(Modifier.weight(1f).height(8.dp).background(colors.control, LagoonShapes.pill)) {
            Box(Modifier.width(maxWidth * width).fillMaxHeight().background(if (shown) colors.danger else colors.accent, LagoonShapes.pill))
        }
        Text(count.toString(), style = Lagoon.type.labelStrong, color = colors.textSecondary)
    }
}

/** AdStats handler ids ("swap web/highlighter", "proxy host:port", "hidden", "filtered", "shown") as readable labels. */
@Composable
private fun handlerLabel(handler: String): String = when {
    handler.startsWith("swap") -> stringResource(R.string.st_handler_swap, handler.removePrefix("swap").trim())
    handler.startsWith("proxy") -> stringResource(R.string.st_handler_proxy, handler.removePrefix("proxy").trim())
    handler == "hidden" -> stringResource(R.string.st_handler_hidden)
    handler == "filtered" -> stringResource(R.string.st_handler_filtered)
    handler == "shown" -> stringResource(R.string.st_handler_shown)
    else -> handler
}

@Composable
private fun PresetPicker(store: SettingsStore) {
    store.observe()
    val current = AdBlockPreset.match { key, default -> store.bool(key, default) }
    Column {
        GroupTitle(stringResource(R.string.st_group_ad_mode))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 600.dp) 3 else 1
            val presets = AdBlockPreset.entries
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { preset ->
                            PresetCard(preset, selected = preset.key == current, Modifier.weight(1f)) { store.putBooleans(preset.values) }
                        }
                    }
                }
            }
        }
        if (current == AdBlockPreset.CUSTOM) {
            Text(stringResource(R.string.st_preset_custom_note), style = Lagoon.type.label, color = Lagoon.colors.textHint, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
        }
    }
}

@Composable
private fun PresetCard(preset: AdBlockPreset, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val colors = Lagoon.colors
    val ring by animateColorAsState(if (selected) colors.accent else Color.Transparent, Lagoon.motion.fade(), label = "presetRing")
    val dot by animateColorAsState(if (selected) colors.accent else colors.textHint, Lagoon.motion.fade(), label = "presetDot")
    Row(
        modifier
            .clip(LagoonShapes.card)
            .background(if (selected) colors.card else colors.card.copy(alpha = 0.6f))
            .border(2.dp, ring, LagoonShapes.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.padding(top = 2.dp).size(20.dp).border(if (selected) 6.dp else 2.dp, dot, LagoonShapes.pill),
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(presetName(preset.key)), style = Lagoon.type.bodyStrong, color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(presetDescription(preset)), style = Lagoon.type.label, color = colors.textSecondary)
        }
    }
}

/** The app icon (teal gradient, soft card, play button, wave), drawn from the launcher layers. */
@Composable
fun LagoonLogo(modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Box(modifier.size(size).clip(LagoonShapes.avatar(size)), contentAlignment = Alignment.Center) {
        // adaptive icon layers are 108 dp with the visible part in the middle 72 dp
        val layer = size * 1.5f
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(layer))
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(layer))
    }
}
