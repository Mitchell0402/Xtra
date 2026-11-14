package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.player.ads.AdLog
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle
import com.github.andreyasadchy.xtra.ui.design.components.ChoiceOption
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonSlider
import com.github.andreyasadchy.xtra.ui.design.components.LagoonSwitch
import com.github.andreyasadchy.xtra.ui.design.components.MultiChoiceList
import com.github.andreyasadchy.xtra.ui.design.components.SingleChoiceList
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.settings.model.ChoiceSetting
import com.github.andreyasadchy.xtra.ui.settings.model.SettingAction
import com.github.andreyasadchy.xtra.ui.settings.model.SliderSetting
import com.github.andreyasadchy.xtra.ui.settings.model.TabOrderSetting
import com.github.andreyasadchy.xtra.ui.settings.model.TextKind
import com.github.andreyasadchy.xtra.ui.settings.model.TextSetting
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val SettingsTextFieldTag = "settingsTextField"
const val SettingsSheetSaveTag = "settingsSheetSave"

@Composable
fun SettingsSheet(request: SheetRequest, store: SettingsStore, host: SettingsHost, onDismiss: () -> Unit, onRequest: (SheetRequest) -> Unit = {}) {
    when (request) {
        is SheetRequest.Choice -> ChoiceSheet(request.setting, store, host, onDismiss)
        is SheetRequest.Slider -> SliderSheet(request.setting, store, host, onDismiss)
        is SheetRequest.Text -> TextSheet(request.setting, store, host, onDismiss)
        is SheetRequest.Tabs -> TabsSheet(request.setting, store, host, onDismiss)
        is SheetRequest.Confirm -> ConfirmSheet(
            title = stringResource(request.row.title),
            message = stringResource(confirmMessage(request.row.action)),
            confirm = stringResource(R.string.st_confirm_delete),
            onConfirm = { host.runAction(request.row.action) },
            onDismiss = onDismiss,
        )
        SheetRequest.AdLog -> AdLogSheet(onDismiss)
        SheetRequest.Languages -> LanguagesSheet(host, onDismiss)
        SheetRequest.Account -> AccountSheet(host.account, host, onDismiss, onLogout = { onRequest(SheetRequest.ConfirmLogout) })
        SheetRequest.ConfirmLogout -> ConfirmSheet(
            title = stringResource(R.string.log_out),
            message = stringResource(R.string.st_logout_confirm),
            confirm = stringResource(R.string.log_out),
            onConfirm = host::logout,
            onDismiss = onDismiss,
        )
    }
}

private fun confirmMessage(action: SettingAction) = when (action) {
    SettingAction.DeleteRecentSearches -> R.string.delete_recent_searches_message
    SettingAction.DeleteVideoPositions -> R.string.delete_video_positions_message
    else -> R.string.st_reset_stats_message
}

@Composable
private fun ChoiceSheet(setting: ChoiceSetting, store: SettingsStore, host: SettingsHost, onDismiss: () -> Unit) {
    val options = choiceOptions(setting, host)
    val current = choiceValue(setting, store, host)
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(setting.title)) {
        setting.summary?.let { SheetExplanation(stringResource(it)) }
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            SingleChoiceList(
                options = options.map { ChoiceOption(it.second) },
                selected = options.indexOfFirst { it.first == current },
                onSelect = { index ->
                    val value = options[index].first
                    store.put(setting, value)
                    host.afterChange(setting, value)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun SliderSheet(setting: SliderSetting, store: SettingsStore, host: SettingsHost, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(store.int(setting).toFloat()) }
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(setting.title)) {
        setting.summary?.let { SheetExplanation(stringResource(it)) }
        LagoonSlider(
            title = stringResource(R.string.st_value),
            value = value,
            onValueChange = { value = it },
            valueLabel = sliderLabel(setting, value.toInt()),
            valueRange = setting.min.toFloat()..setting.max.toFloat(),
        )
        SheetButtons(
            secondary = stringResource(R.string.st_reset_default) to { value = setting.default.toFloat() },
            primary = stringResource(R.string.st_save) to {
                val v = value.toInt().coerceIn(setting.min, setting.max)
                store.put(setting, v)
                host.afterChange(setting, v)
                onDismiss()
            },
        )
    }
}

/** Whether [text] is acceptable for a setting of [kind]: numbers must parse, an empty field restores the default. */
fun isValidSettingText(kind: TextKind, text: String): Boolean = when (kind) {
    TextKind.Integer -> text.isEmpty() || text.toLongOrNull() != null
    TextKind.Decimal -> text.isEmpty() || text.toDoubleOrNull() != null
    else -> true
}

@Composable
private fun TextSheet(setting: TextSetting, store: SettingsStore, host: SettingsHost, onDismiss: () -> Unit) {
    val colors = Lagoon.colors
    // the cursor starts at the end, so the keyboard's delete key edits the current value
    var field by remember { (store.string(setting) ?: "").let { mutableStateOf(TextFieldValue(it, TextRange(it.length))) } }
    val text = field.text
    var reveal by remember { mutableStateOf(setting.kind != TextKind.Secret) }
    val focus = remember { FocusRequester() }
    val valid = isValidSettingText(setting.kind, text)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(setting.title), skipHalfHeight = true) {
        setting.summary?.let { SheetExplanation(stringResource(it)) }
        Row(
            Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .fillMaxWidth()
                .background(colors.card, LagoonShapes.input)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = field,
                onValueChange = { field = it },
                modifier = Modifier.weight(1f).padding(vertical = 10.dp).focusRequester(focus).testTag(SettingsTextFieldTag),
                textStyle = Lagoon.type.body.copy(color = colors.textPrimary, fontFamily = if (setting.kind == TextKind.Secret || setting.kind == TextKind.Url) FontFamily.Monospace else Lagoon.type.body.fontFamily),
                cursorBrush = SolidColor(colors.accent),
                singleLine = setting.kind != TextKind.Multiline,
                minLines = if (setting.kind == TextKind.Multiline) 4 else 1,
                maxLines = if (setting.kind == TextKind.Multiline) 10 else 1,
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (setting.kind) {
                        TextKind.Integer -> KeyboardType.Number
                        TextKind.Decimal -> KeyboardType.Decimal
                        TextKind.Url -> KeyboardType.Uri
                        TextKind.Secret -> KeyboardType.Password
                        else -> KeyboardType.Text
                    },
                    autoCorrectEnabled = false,
                ),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                setting.default?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.st_default_value, it.lines().first()) } ?: stringResource(R.string.st_not_set),
                                style = Lagoon.type.body, color = colors.textHint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            if (setting.kind == TextKind.Secret) {
                LagoonIconButton(
                    LucideIcons.Eye, stringResource(if (reveal) R.string.st_hide_value else R.string.st_show_value),
                    onClick = { reveal = !reveal }, filled = false, tint = colors.textSecondary, size = 40.dp,
                )
            }
        }
        if (!valid) {
            Text(
                stringResource(if (setting.kind == TextKind.Integer) R.string.st_invalid_integer else R.string.st_invalid_number),
                style = Lagoon.type.label, color = colors.danger, modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        SheetButtons(
            secondary = stringResource(if (setting.default == null) R.string.st_clear else R.string.st_reset_default) to { field = TextFieldValue("") },
            primary = stringResource(R.string.st_save) to {
                // an empty field goes back to the default: the key is removed, as if never set
                val value = text.takeIf { it.isNotEmpty() || setting.default == "" }
                store.put(setting, value)
                host.afterChange(setting, value)
                onDismiss()
            },
            primaryEnabled = valid,
        )
    }
}

@Composable
private fun TabsSheet(setting: TabOrderSetting, store: SettingsStore, host: SettingsHost, onDismiss: () -> Unit) {
    val colors = Lagoon.colors
    var tabs by remember { mutableStateOf(TabOrder.parse(store.string(setting), setting.default)) }
    val labels = setting.tabs.toMap()
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(setting.title), skipHalfHeight = true) {
        SheetExplanation(stringResource(R.string.st_tabs_sheet_summary))
        Column(Modifier.padding(horizontal = 12.dp)) {
            tabs.forEachIndexed { index, tab ->
                val label = labels[tab.key]?.let { stringResource(it) } ?: tab.key
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(label, style = Lagoon.type.bodyStrong, color = if (tab.enabled) colors.textPrimary else colors.textHint)
                        if (tab.isDefault) Text(stringResource(R.string.st_tab_default), style = Lagoon.type.label, color = colors.accent)
                    }
                    LagoonIconButton(
                        LucideIcons.House, stringResource(R.string.st_tab_make_default, label),
                        onClick = { tabs = TabOrder.setDefault(tabs, tab.key) }, filled = false,
                        tint = if (tab.isDefault) colors.accent else colors.textSecondary, size = 40.dp,
                    )
                    LagoonIconButton(
                        LucideIcons.ArrowUp, stringResource(R.string.st_move_up, label),
                        onClick = { tabs = TabOrder.move(tabs, index, index - 1) }, filled = false,
                        tint = colors.textSecondary, size = 40.dp, enabled = index > 0,
                    )
                    LagoonIconButton(
                        LucideIcons.ArrowDown, stringResource(R.string.st_move_down, label),
                        onClick = { tabs = TabOrder.move(tabs, index, index + 1) }, filled = false,
                        tint = colors.textSecondary, size = 40.dp, enabled = index < tabs.lastIndex,
                    )
                    LagoonSwitch(
                        checked = tab.enabled,
                        onCheckedChange = { tabs = TabOrder.setEnabled(tabs, tab.key, it) },
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
        }
        SheetButtons(
            secondary = stringResource(R.string.st_reset_default) to { tabs = TabOrder.parse(null, setting.default) },
            primary = stringResource(R.string.st_save) to {
                val value = TabOrder.format(tabs)
                store.put(setting, value)
                host.afterChange(setting, value)
                onDismiss()
            },
        )
    }
}

@Composable
fun ConfirmSheet(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = true) {
    LagoonBottomSheet(onDismiss = onDismiss, title = title) {
        SheetExplanation(message)
        SheetButtons(
            secondary = stringResource(R.string.st_cancel) to onDismiss,
            primary = confirm to {
                onConfirm()
                onDismiss()
            },
            primaryStyle = if (destructive) ButtonStyle.Danger else ButtonStyle.Primary,
        )
    }
}

@Composable
private fun AdLogSheet(onDismiss: () -> Unit) {
    val colors = Lagoon.colors
    val lines = remember { AdLog.lines() }
    val format = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.ad_block_log), skipHalfHeight = true) {
        if (lines.isEmpty()) {
            SheetExplanation(stringResource(R.string.ad_block_log_empty))
        } else {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .background(colors.card, LagoonShapes.row)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                lines.asReversed().forEach { line ->
                    val time = line.substringBefore(' ').toLongOrNull()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (time != null) Text(format.format(Date(time)), style = Lagoon.type.caption.copy(fontFamily = FontFamily.Monospace), color = colors.textHint)
                        Text(if (time != null) line.substringAfter(' ') else line, style = Lagoon.type.caption.copy(fontFamily = FontFamily.Monospace), color = colors.textSecondary, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagesSheet(host: SettingsHost, onDismiss: () -> Unit) {
    val languages = host.environment.translateLanguages
    var selected by remember { mutableStateOf<Set<Int>?>(null) }
    LaunchedEffect(Unit) {
        val downloaded = host.downloadedLanguages()
        selected = languages.indices.filter { languages[it].first in downloaded }.toSet()
    }
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.downloaded_languages), skipHalfHeight = true) {
        SheetExplanation(stringResource(R.string.st_languages_summary))
        val current = selected
        if (current != null) {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                MultiChoiceList(
                    options = languages.map { ChoiceOption(it.second) },
                    selected = current,
                    onToggle = { index, on -> selected = if (on) current + index else current - index },
                )
            }
        }
        SheetButtons(
            secondary = stringResource(R.string.st_cancel) to onDismiss,
            primary = stringResource(R.string.st_save) to {
                selected?.let { set -> host.setDownloadedLanguages(set.map { languages[it].first }) }
                onDismiss()
            },
            primaryEnabled = current != null,
        )
    }
}

@Composable
fun SheetExplanation(text: String) {
    Text(text, style = Lagoon.type.label, color = Lagoon.colors.textSecondary, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))
}

@Composable
fun SheetButtons(
    secondary: Pair<String, () -> Unit>?,
    primary: Pair<String, () -> Unit>,
    primaryEnabled: Boolean = true,
    primaryStyle: ButtonStyle = ButtonStyle.Primary,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
    ) {
        if (secondary != null) {
            LagoonButton(secondary.first, secondary.second, Modifier.weight(1f), style = ButtonStyle.Secondary)
        }
        LagoonButton(primary.first, primary.second, Modifier.weight(1f).testTag(SettingsSheetSaveTag), style = primaryStyle, enabled = primaryEnabled)
    }
}
