package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.ui.design.components.currentSheetMaxHeight
import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle
import com.github.andreyasadchy.xtra.ui.design.components.ChoiceOption
import com.github.andreyasadchy.xtra.ui.design.components.FilterChip
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipItem
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonImage
import com.github.andreyasadchy.xtra.ui.design.components.LagoonSlider
import com.github.andreyasadchy.xtra.ui.design.components.LagoonTimeWheel
import com.github.andreyasadchy.xtra.ui.design.components.TimeWheelLogic
import com.github.andreyasadchy.xtra.ui.design.components.SettingRow
import com.github.andreyasadchy.xtra.ui.design.components.SettingTrailing
import com.github.andreyasadchy.xtra.ui.design.components.SettingsGroup
import com.github.andreyasadchy.xtra.ui.design.components.SingleChoiceList
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import kotlin.math.roundToInt

/** The pages of the player's sheet: the settings list and the choices it opens in place (plan 7.4). */
enum class PlayerSheet { Settings, Quality, Speed, Sleep, SleepAt, Volume, Chapters, AdBlock, Aspect, PlaylistTags }

/** What the sheet reads when it opens. */
interface PlayerSheetSource {
    fun menuContext(layout: PlayerLayout): MenuContext
    fun qualities(): List<Pair<String, VideoQuality>>
    fun currentQuality(): VideoQuality?
    fun speeds(): List<String>
    fun playerVolume(): Float
    /** v0.1 sleep_timer_minutes, the last length chosen. */
    fun sleepMinutes(): Int
    fun sleepLock(): Boolean
    /** Locking the screen needs the device admin permission. */
    fun sleepLockAllowed(): Boolean
    /** v0.1 sleep_timer_time: the last time of day picked for "at a set time", minutes since midnight. */
    fun sleepAtTime(): Int
    /** The system's 12/24-hour setting. */
    fun is24HourClock(): Boolean
    /** The current time of day, minutes since midnight. */
    fun nowMinuteOfDay(): Int
    /** The playlist text shown by the debug "playlist tags" page (U9 A2). */
    fun playlistTags(): String
    fun adBlockPreset(): String
    fun adBlockSummary(): String?
    fun adLog(): List<String>
}

/** One sheet window; its pages switch inside it, the main list stays one step back. */
@Composable
fun PlayerSheetHost(state: PlayerUiState, source: PlayerSheetSource, actions: PlayerActions, layout: PlayerLayout) {
    val page = state.sheet ?: return
    // pages opened straight from a quick button close instead of going back to the list
    val fromMenu = remember { mutableStateOf(page == PlayerSheet.Settings) }
    if (page == PlayerSheet.Settings) fromMenu.value = true
    val back = { if (fromMenu.value) state.sheet = PlayerSheet.Settings else state.sheet = null }
    // "at a set time" goes back to the sleep timer page when opened from it, else like any other page
    val sleepAtFromSleep = remember { mutableStateOf(false) }
    if (page == PlayerSheet.Sleep) sleepAtFromSleep.value = true
    if (page != PlayerSheet.Sleep && page != PlayerSheet.SleepAt) sleepAtFromSleep.value = false
    val motion = Lagoon.motion
    LagoonBottomSheet(onDismiss = { state.sheet = null }, skipHalfHeight = true) {
        // inside the sheet's own window: back returns to the page this one was opened from before it closes the sheet
        // (P18, like the filter sheet); a page opened straight from a quick button has nowhere to go back to
        val upTo = PlayerSheetNav.backTarget(page, fromMenu.value, sleepAtFromSleep.value)
        BackHandler(enabled = upTo != null) { state.sheet = upTo }
        AnimatedContent(
            targetState = page,
            // deeper pages come in from the end, going back reverses it (motion plan M2b item 5)
            transitionSpec = { if (PlayerSheetNav.isBack(initialState, targetState)) motion.sheetBack() else motion.sheetForward() },
            label = "playerSheet",
        ) { current ->
            PlayerSheetPage(current, state, source, actions, layout, if (current == PlayerSheet.SleepAt && sleepAtFromSleep.value) {
                { state.sheet = PlayerSheet.Sleep }
            } else back)
        }
    }
}

/** One page of the sheet, drawn in the sheet window or in place (tests). */
@Composable
fun PlayerSheetPage(page: PlayerSheet, state: PlayerUiState, source: PlayerSheetSource, actions: PlayerActions, layout: PlayerLayout, back: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = currentSheetMaxHeight() * 0.85f)
            .verticalScroll(rememberScrollState())
            .testTag("playerSheet-${page.name}")
    ) {
        when (page) {
            PlayerSheet.Settings -> SettingsPage(state, source, actions, layout)
            PlayerSheet.Quality -> SubPage(stringResource(R.string.pl_quality), back) { QualityPage(state, source, actions) }
            PlayerSheet.Speed -> SubPage(stringResource(R.string.pl_speed), back) { SpeedPage(state, source, actions) }
            PlayerSheet.Sleep -> SubPage(stringResource(R.string.pl_sleep), back) { SleepPage(state, source, actions) }
            PlayerSheet.SleepAt -> SubPage(stringResource(R.string.pl_sleep_at), back) { SleepAtPage(state, source, actions) }
            PlayerSheet.Volume -> SubPage(stringResource(R.string.pl_volume), back) { VolumePage(source, actions) }
            PlayerSheet.Chapters -> SubPage(stringResource(R.string.pl_chapters), back) { ChaptersPage(state.chapters, state, actions) }
            PlayerSheet.AdBlock -> SubPage(stringResource(R.string.ad_block_settings), back) { AdBlockPage(state, source, actions) }
            PlayerSheet.Aspect -> SubPage(stringResource(R.string.pl_aspect), back) { AspectPage(state, actions) }
            PlayerSheet.PlaylistTags -> SubPage(stringResource(R.string.pl_playlist_tags), back) { PlaylistTagsPage(state, source, actions) }
        }
    }
}

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        LagoonIconButton(LucideIcons.ChevronLeft, stringResource(R.string.pl_back), onClick = onBack, filled = false, modifier = Modifier.testTag("sheetBack"))
        Text(title, style = Lagoon.type.headline, color = Lagoon.colors.textPrimary)
    }
    Column(content = content)
}

/** The settings list: quality, speed, sleep timer, audio only, record or download, ad blocking; then the rest. */
@Composable
private fun SettingsPage(state: PlayerUiState, source: PlayerSheetSource, actions: PlayerActions, layout: PlayerLayout) {
    val entries = PlayerLogic.menuEntries(state.kind, source.menuContext(layout))
    Text(
        stringResource(R.string.pl_settings), style = Lagoon.type.headline, color = Lagoon.colors.textPrimary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    val run = { entry: MenuEntry -> state.sheet = null; actions.menu(entry) }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGroup {
            entries.filter { PlayerLogic.isMainEntry(it) }.forEach { MenuRow(it, state, source, actions, run) }
        }
        val rest = entries.filterNot { PlayerLogic.isMainEntry(it) }
        if (rest.isNotEmpty()) {
            SettingsGroup(title = stringResource(R.string.pl_more)) {
                rest.forEach { MenuRow(it, state, source, actions, run) }
            }
        }
    }
}

@Composable
private fun MenuRow(entry: MenuEntry, state: PlayerUiState, source: PlayerSheetSource, actions: PlayerActions, run: (MenuEntry) -> Unit) {
    val m = Modifier.testTag("menu${entry.name}")
    when (entry) {
        MenuEntry.Quality -> SettingRow(
            stringResource(R.string.pl_quality), m, icon = LucideIcons.SlidersHorizontal, enabled = state.loaded,
            trailing = SettingTrailing.Value(state.qualityLabel ?: ""), onClick = { state.sheet = PlayerSheet.Quality },
        )
        MenuEntry.Speed -> SettingRow(
            stringResource(R.string.pl_speed), m, icon = LucideIcons.Gauge,
            trailing = SettingTrailing.Value(PlayerLogic.speedLabel(state.speed)), onClick = { state.sheet = PlayerSheet.Speed },
        )
        MenuEntry.SleepTimer -> {
            val remaining = (state.sleepTimerEnd - System.currentTimeMillis()).takeIf { state.sleepTimerEnd > 0L && it > 0 }
            SettingRow(
                stringResource(R.string.pl_sleep), m, icon = LucideIcons.Moon,
                trailing = SettingTrailing.Value(
                    remaining?.let { stringResource(R.string.pl_sleep_in, PlayerLogic.time(it)) } ?: stringResource(R.string.pl_off)
                ),
                onClick = { actions.menu(MenuEntry.SleepTimer) },
            )
        }
        MenuEntry.AudioOnly -> SettingRow(
            stringResource(R.string.audio_only), m, icon = LucideIcons.Headphones, enabled = state.loaded,
            trailing = SettingTrailing.Toggle(state.audioOnly) { if (state.loaded) actions.menu(MenuEntry.AudioOnly) },
        )
        MenuEntry.Download -> SettingRow(
            stringResource(if (state.kind == PlayerKind.Stream) R.string.pl_record else R.string.download), m,
            icon = if (state.kind == PlayerKind.Stream) LucideIcons.CircleDot else LucideIcons.Download,
            enabled = state.loaded, trailing = SettingTrailing.Chevron, onClick = { run(entry) },
        )
        MenuEntry.AdBlock -> SettingRow(
            stringResource(R.string.ad_block_settings), m, icon = LucideIcons.ShieldCheck, highlight = state.adStatus != null,
            description = state.adStatus?.toString() ?: source.adBlockSummary(),
            trailing = SettingTrailing.Value(source.adBlockPreset()), onClick = { state.sheet = PlayerSheet.AdBlock },
        )
        MenuEntry.FullVideo -> SettingRow(stringResource(R.string.watch_video), m, icon = LucideIcons.Film, trailing = SettingTrailing.None, onClick = { run(entry) })
        MenuEntry.Subtitles -> SettingRow(
            stringResource(R.string.pl_subtitles), m, icon = LucideIcons.Captions,
            trailing = SettingTrailing.Toggle(state.subtitles == SubtitleState.On) { actions.menu(MenuEntry.Subtitles) },
        )
        MenuEntry.Compressor -> SettingRow(
            stringResource(R.string.pl_compressor), m, icon = LucideIcons.AudioLines,
            trailing = SettingTrailing.Toggle(state.compressorOn) { actions.menu(MenuEntry.Compressor) },
        )
        MenuEntry.Volume -> SettingRow(
            stringResource(R.string.pl_volume), m, icon = LucideIcons.Volume2,
            trailing = SettingTrailing.Value("${(source.playerVolume() * 100).roundToInt()}%"), onClick = { state.sheet = PlayerSheet.Volume },
        )
        MenuEntry.Chapters -> SettingRow(stringResource(R.string.pl_chapters), m, icon = LucideIcons.ListOrdered, onClick = { state.sheet = PlayerSheet.Chapters })
        MenuEntry.ViewerList -> SettingRow(stringResource(R.string.pl_viewer_list), m, icon = LucideIcons.Users, trailing = SettingTrailing.None, onClick = { run(entry) })
        MenuEntry.Bookmark -> SettingRow(
            stringResource(if (state.bookmarked == true) R.string.remove_bookmark else R.string.add_bookmark), m,
            icon = if (state.bookmarked == true) LucideIcons.BookmarkFilled else LucideIcons.Bookmark,
            trailing = SettingTrailing.None, onClick = { run(entry) },
        )
        MenuEntry.Share -> SettingRow(stringResource(R.string.share), m, icon = LucideIcons.Share2, trailing = SettingTrailing.None, onClick = { run(entry) })
        MenuEntry.FindVideo -> SettingRow(stringResource(R.string.pl_find_video), m, icon = LucideIcons.Search, trailing = SettingTrailing.None, onClick = { run(entry) })
        MenuEntry.SeekLive -> SettingRow(stringResource(R.string.pl_seek_live), m, icon = LucideIcons.Radio, trailing = SettingTrailing.None, onClick = { run(entry) })
        MenuEntry.Restart -> SettingRow(stringResource(R.string.pl_restart), m, icon = LucideIcons.RotateCw, trailing = SettingTrailing.None, onClick = { run(entry) })
        MenuEntry.Aspect -> SettingRow(
            stringResource(R.string.pl_aspect), m, icon = LucideIcons.Maximize,
            trailing = SettingTrailing.Value(stringResource(aspectLabel(state.resizeMode))), onClick = { state.sheet = PlayerSheet.Aspect },
        )
        MenuEntry.ChatLayout -> SettingRow(
            stringResource(R.string.pl_chat_layout), m, icon = LucideIcons.Columns2,
            trailing = SettingTrailing.Value(stringResource(if (state.chatOverlay) R.string.pl_chat_overlay else R.string.pl_chat_side), chevron = false),
            onClick = { actions.setChatOverlay(!state.chatOverlay) },
        )
        MenuEntry.ChatToggle -> SettingRow(
            stringResource(if (state.chatOpen) R.string.pl_hide_chat else R.string.pl_show_chat), m, icon = LucideIcons.MessageSquare,
            trailing = SettingTrailing.None, onClick = { state.sheet = null; actions.setChatOpen(!state.chatOpen) },
        )
        MenuEntry.ChatBar -> SettingRow(
            stringResource(if (state.chatBarVisible) R.string.pl_hide_chat_bar else R.string.pl_show_chat_bar), m, icon = LucideIcons.Keyboard,
            trailing = SettingTrailing.None, onClick = { run(entry) },
        )
        MenuEntry.ChatConnection -> SettingRow(
            stringResource(if (state.chatConnected) R.string.pl_chat_disconnect else R.string.pl_chat_connect), m,
            icon = if (state.chatConnected) LucideIcons.WifiOff else LucideIcons.Network,
            trailing = SettingTrailing.None, onClick = { run(entry) },
        )
        MenuEntry.ReloadEmotes -> SettingRow(stringResource(R.string.pl_reload_emotes), m, icon = LucideIcons.Smile, trailing = SettingTrailing.None, onClick = { run(entry) })
        MenuEntry.MediaPlaylistTags -> SettingRow(stringResource(R.string.show_media_playlist_tags), m, icon = LucideIcons.Code, trailing = SettingTrailing.None, onClick = { run(entry) })
        MenuEntry.MultivariantPlaylistTags -> SettingRow(stringResource(R.string.show_multivariant_playlist_tags), m, icon = LucideIcons.Code, trailing = SettingTrailing.None, onClick = { run(entry) })
    }
}

private fun aspectLabel(mode: Int) = when (mode) {
    1 -> R.string.pl_aspect_width
    2 -> R.string.pl_aspect_height
    3 -> R.string.pl_aspect_fill
    4 -> R.string.pl_aspect_zoom
    else -> R.string.pl_aspect_fit
}

@Composable
private fun QualityPage(state: PlayerUiState, source: PlayerSheetSource, actions: PlayerActions) {
    val qualities = remember { source.qualities() }
    val current = source.currentQuality()
    val selected = qualities.indexOfFirst { it.second.name == current?.name && it.second.url == current?.url }
    SingleChoiceList(
        qualities.map { ChoiceOption(it.first) },
        selected,
        onSelect = { index ->
            state.sheet = null
            actions.selectQuality(qualities[index].second)
        },
        modifier = Modifier.testTag("qualityList"),
    )
}

@Composable
private fun SpeedPage(state: PlayerUiState, source: PlayerSheetSource, actions: PlayerActions) {
    val speeds = remember { source.speeds() }
    // v0.1 matched the current speed by its text in the list
    val selected = speeds.indexOf(state.speed.toString())
    SingleChoiceList(
        speeds.map { ChoiceOption((it.toFloatOrNull()?.let(PlayerLogic::speedLabel)) ?: it) },
        selected,
        onSelect = { index ->
            speeds[index].toFloatOrNull()?.let { actions.selectSpeed(it) }
            state.sheet = null
        },
    )
}

@Composable
private fun VolumePage(source: PlayerSheetSource, actions: PlayerActions) {
    var volume by remember { mutableFloatStateOf(source.playerVolume() * 100f) }
    LagoonSlider(
        title = stringResource(R.string.pl_volume),
        value = volume,
        onValueChange = { volume = it; actions.setPlayerVolume(it / 100f, false) },
        onValueChangeFinished = { actions.setPlayerVolume(volume / 100f, true) },
        valueLabel = "${volume.roundToInt()}%",
        valueRange = 0f..100f,
        modifier = Modifier.testTag("volumeSlider"),
    )
    Spacer(Modifier.height(8.dp))
}

/** The sleep timer: a few common lengths, a slider for any other, and locking the screen when it ends (v0.1). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepPage(state: PlayerUiState, source: PlayerSheetSource, actions: PlayerActions) {
    var minutes by remember { mutableIntStateOf(source.sleepMinutes().coerceIn(1, 600)) }
    var lock by remember { mutableStateOf(source.sleepLock() && source.sleepLockAllowed()) }
    val running = state.sleepTimerEnd > System.currentTimeMillis()
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60, 90, 120).forEach { preset ->
                FilterChip(FilterChipItem(stringResource(R.string.pl_sleep_minutes, preset), selected = minutes == preset, onClick = { minutes = preset }))
            }
        }
        LagoonSlider(
            title = stringResource(R.string.pl_sleep_title),
            value = minutes.toFloat(),
            onValueChange = { minutes = it.roundToInt().coerceAtLeast(1) },
            valueLabel = PlayerLogic.time(minutes * 60_000L).removeSuffix(":00").let { if (minutes < 60) stringResource(R.string.pl_sleep_minutes, minutes) else it },
            valueRange = 1f..240f,
            modifier = Modifier.testTag("sleepSlider"),
        )
        SettingsGroup {
            SettingRow(
                stringResource(if (source.sleepLockAllowed()) R.string.sleep_timer_lock else R.string.sleep_timer_lock_permissions),
                icon = LucideIcons.Lock,
                trailing = SettingTrailing.Toggle(lock) {
                    if (source.sleepLockAllowed()) lock = it else { state.sheet = null; actions.requestSleepLockPermission() }
                },
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (running) {
                LagoonButton(
                    stringResource(R.string.pl_sleep_stop), onClick = { state.sheet = null; actions.setSleepTimer(0, lock) },
                    style = ButtonStyle.Secondary, modifier = Modifier.weight(1f),
                )
            }
            LagoonButton(
                stringResource(R.string.pl_sleep_set), onClick = { state.sheet = null; actions.setSleepTimer(minutes, lock) },
                modifier = Modifier.weight(1f).testTag("sleepStart"),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            LagoonButton(
                stringResource(R.string.pl_sleep_at), onClick = { state.sheet = PlayerSheet.SleepAt },
                style = ButtonStyle.Secondary, icon = LucideIcons.Clock,
            )
        }
    }
}

/**
 * "At a set time" (U9 A1, replaces Material's clock picker): two wheels pick the time of day, the line under them says
 * how long until then, and the timer starts on the button. The current minute would give 0, which the timer reads as
 * "cancel", so the button waits for a later time.
 */
@Composable
private fun SleepAtPage(state: PlayerUiState, source: PlayerSheetSource, actions: PlayerActions) {
    var minuteOfDay by remember { mutableIntStateOf(source.sleepAtTime().coerceIn(0, 1439)) }
    val now by produceState(source.nowMinuteOfDay()) {
        while (true) {
            delay(5_000)
            value = source.nowMinuteOfDay()
        }
    }
    val left = TimeWheelLogic.minutesUntil(now, minuteOfDay)
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val amPm = remember { java.text.DateFormatSymbols.getInstance().amPmStrings.let { it[0] to it[1] } }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LagoonTimeWheel(
            minuteOfDay = minuteOfDay,
            onChange = { minuteOfDay = it },
            is24Hour = source.is24HourClock(),
            amPm = amPm,
            visibleRows = if (windowHeight < 480.dp) 3 else 5,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            if (left == 0) stringResource(R.string.pl_sleep_at_later)
            else stringResource(R.string.pl_sleep_at_in, PlayerLogic.time(left * 60_000L).removeSuffix(":00").let {
                if (left < 60) stringResource(R.string.pl_sleep_minutes, left) else it
            }),
            style = Lagoon.type.body,
            color = if (left == 0) Lagoon.colors.textHint else Lagoon.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("sleepAtSummary"),
        )
        LagoonButton(
            stringResource(R.string.pl_sleep_set),
            onClick = { state.sheet = null; actions.setSleepTimerAt(minuteOfDay) },
            enabled = left > 0,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).testTag("sleepAtStart"),
        )
    }
}

/** The debug playlist text (U9 A2, was a Material dialog): monospace, selectable, scrolls sideways; copy below. */
@Composable
private fun PlaylistTagsPage(state: PlayerUiState, source: PlayerSheetSource, actions: PlayerActions) {
    val text = remember { source.playlistTags() }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SelectionContainer(
            Modifier
                .fillMaxWidth()
                .background(Lagoon.colors.card, LagoonShapes.row)
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
                .testTag("playlistTags"),
        ) {
            Text(text, style = Lagoon.type.caption.copy(fontFamily = FontFamily.Monospace), color = Lagoon.colors.textPrimary, softWrap = false)
        }
        LagoonButton(
            stringResource(R.string.copy_clip),
            onClick = { state.sheet = null; actions.copyText(text) },
            style = ButtonStyle.Secondary,
            icon = LucideIcons.Copy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ChaptersPage(chapters: List<Game>, state: PlayerUiState, actions: PlayerActions) {
    val colors = Lagoon.colors
    Column(Modifier.padding(horizontal = 12.dp)) {
        chapters.forEach { game ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable(onClick = {
                        state.sheet = null
                        game.vodPosition?.let { actions.seekToChapter(it.toLong()) }
                    }, shape = LagoonShapes.row, scaleOnPress = false)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LagoonImage(game.boxArt, null, Modifier.size(width = 40.dp, height = 53.dp).padding(0.dp))
                Column(Modifier.weight(1f)) {
                    Text(game.name ?: "", style = Lagoon.type.bodyStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val at = game.vodPosition?.let { PlayerLogic.time(it.toLong()) }
                    val length = game.vodDuration?.let { PlayerLogic.time(it.toLong()) }
                    Text(listOfNotNull(at, length?.let { "($it)" }).joinToString(" "), style = Lagoon.type.label, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun AspectPage(state: PlayerUiState, actions: PlayerActions) {
    val modes = (0..4).map { ChoiceOption(stringResource(aspectLabel(it))) }
    SingleChoiceList(modes, state.resizeMode, onSelect = { actions.setResizeMode(it) })
}

@Composable
private fun AdBlockPage(state: PlayerUiState, source: PlayerSheetSource, actions: PlayerActions) {
    val colors = Lagoon.colors
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroup {
            SettingRow(
                state.adStatus?.toString() ?: stringResource(R.string.pl_ad_block_active), icon = LucideIcons.ShieldCheck, highlight = true,
                description = source.adBlockSummary(), trailing = SettingTrailing.Value(source.adBlockPreset(), chevron = false),
            )
        }
        val log = remember { source.adLog().takeLast(8).reversed() }
        if (log.isNotEmpty()) {
            SettingsGroup(title = stringResource(R.string.pl_ad_block_recent)) {
                log.forEach {
                    Text(it, style = Lagoon.type.caption, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                }
            }
        }
        LagoonButton(
            stringResource(R.string.pl_ad_block_open_settings), onClick = { state.sheet = null; actions.openAdBlockSettings() },
            style = ButtonStyle.Secondary, icon = LucideIcons.Settings, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** Where back goes inside the player's sheet (motion plan M2b item 5). Pure, see PlayerGesturesTest. */
object PlayerSheetNav {
    /**
     * The page back returns to from [page], or null when back closes the sheet: the menu itself, and pages opened
     * straight from a quick button ([fromMenu] false). "At a set time" opened from the sleep timer goes back to it.
     */
    fun backTarget(page: PlayerSheet, fromMenu: Boolean, sleepAtFromSleep: Boolean): PlayerSheet? = when {
        page == PlayerSheet.SleepAt && sleepAtFromSleep -> PlayerSheet.Sleep
        page == PlayerSheet.Settings -> null
        fromMenu -> PlayerSheet.Settings
        else -> null
    }

    /** A move back up: to the menu, or from "at a set time" to the sleep timer. */
    fun isBack(from: PlayerSheet, to: PlayerSheet): Boolean =
        to == PlayerSheet.Settings || (from == PlayerSheet.SleepAt && to == PlayerSheet.Sleep)
}
