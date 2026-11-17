package com.github.andreyasadchy.xtra.ui.download

import com.github.andreyasadchy.xtra.ui.design.components.currentSheetMaxHeight
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle
import com.github.andreyasadchy.xtra.ui.design.components.ChoiceOption
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonImage
import com.github.andreyasadchy.xtra.ui.design.components.LagoonTextField
import com.github.andreyasadchy.xtra.ui.design.components.SettingRow
import com.github.andreyasadchy.xtra.ui.design.components.SettingTrailing
import com.github.andreyasadchy.xtra.ui.design.components.SingleChoiceList
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonLine
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What is being saved: a live stream (a recording), a video or a clip. */
enum class DownloadKind { Stream, Video, Clip }

/** One place downloads can go to inside the app's storage, with its free space when known. */
@Immutable
data class StorageOption(val name: String, val path: String, val freeBytes: Long?)

/** What the sheet shows about the item. */
@Immutable
data class DownloadItem(val kind: DownloadKind, val title: String?, val subtitle: String?, val thumbnail: Any?)

/** The choices in the sheet; the host keeps them across recompositions and writes them back to the settings. */
@Immutable
data class DownloadForm(
    val quality: String? = null,
    /** 0 = shared storage (a folder picked through the system), 1 = the app's storage (R.array.spinnerStorage). */
    val location: Int = 0,
    val sharedPath: String? = null,
    val storageIndex: Int = 0,
    val downloadChat: Boolean = false,
    val downloadChatEmotes: Boolean = false,
    val fromText: String = "",
    val toText: String = "",
)

/** Test tags. */
const val DownloadSheetTag = "downloadSheet"
const val DownloadButtonTag = "downloadButton"

/**
 * The download and record sheet (plan 7.6, U5) that replaces the v0.1 download dialog: the item on top, the qualities
 * as segmented buttons, for videos the start and end as a range slider with the frames at both ends (and the time
 * fields of v0.1 for exact times), the storage on one line with its free space, the chat options, and one button.
 *
 * [qualities] is null while they load. [totalMillis] and [defaultFromMillis] are for videos only.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheetContent(
    item: DownloadItem,
    qualities: List<String>?,
    form: DownloadForm,
    onForm: (DownloadForm) -> Unit,
    storages: List<StorageOption>,
    storageMounted: Boolean,
    onPickFolder: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    totalMillis: Long = 0,
    defaultFromMillis: Long = 0,
    rangeError: String? = null,
    storyboard: Storyboard? = null,
    folderLabel: String? = null,
) {
    val colors = Lagoon.colors
    val maxHeight = currentSheetMaxHeight() * 0.75f
    Column(modifier.testTag(DownloadSheetTag)) {
        Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
            ItemHeader(item)
            if (qualities == null) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonLine(0.3f)
                    SkeletonLine(0.9f, height = 40.dp)
                    SkeletonLine(0.6f, height = 40.dp)
                }
                return@Column
            }
            Section(stringResource(R.string.select_quality))
            FlowRow(
                Modifier.padding(horizontal = 20.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(LagoonSpacing.xs),
            ) {
                val selected = form.quality ?: qualities.firstOrNull()
                qualities.forEach { label ->
                    QualityButton(label, label == selected) { onForm(form.copy(quality = label)) }
                }
            }
            if (item.kind == DownloadKind.Video && totalMillis > 0) {
                Section(stringResource(R.string.download_sheet_range))
                RangePicker(form, onForm, totalMillis, defaultFromMillis, storyboard, item.thumbnail)
                if (rangeError != null) {
                    Text(rangeError, style = Lagoon.type.label, color = colors.danger, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }
            Section(stringResource(R.string.save_to))
            if (!storageMounted) {
                Text(
                    stringResource(R.string.no_storage_detected), style = Lagoon.type.body, color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else {
                val locations = listOf(stringResource(R.string.shared_storage), stringResource(R.string.app_storage))
                SingleChoiceList(
                    locations.map { ChoiceOption(it) },
                    selected = form.location.coerceIn(0, 1),
                    onSelect = { onForm(form.copy(location = it)) },
                )
                Column(Modifier.padding(horizontal = 6.dp)) {
                    if (form.location == 0) {
                        SettingRow(
                            stringResource(R.string.select_directory),
                            icon = LucideIcons.Folder,
                            trailing = SettingTrailing.Value(folderLabel ?: stringResource(R.string.download_sheet_no_folder)),
                            onClick = onPickFolder,
                        )
                    } else if (storages.size > 1) {
                        SingleChoiceList(
                            storages.map { ChoiceOption(it.name, it.freeBytes?.let { free -> stringResource(R.string.download_sheet_free, formatSize(free)) }) },
                            selected = form.storageIndex.coerceIn(0, storages.lastIndex),
                            onSelect = { onForm(form.copy(storageIndex = it)) },
                        )
                    } else {
                        storages.firstOrNull()?.let { storage ->
                            SettingRow(
                                storage.name,
                                icon = LucideIcons.HardDrive,
                                trailing = SettingTrailing.Value(storage.freeBytes?.let { stringResource(R.string.download_sheet_free, formatSize(it)) }.orEmpty(), chevron = false),
                            )
                        }
                    }
                }
            }
            run {
                Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                    SettingRow(
                        stringResource(R.string.download_chat),
                        icon = LucideIcons.MessageSquare,
                        trailing = SettingTrailing.Toggle(form.downloadChat) { onForm(form.copy(downloadChat = it)) },
                    )
                    SettingRow(
                        stringResource(R.string.download_chat_emotes),
                        icon = LucideIcons.Smile,
                        enabled = form.downloadChat,
                        trailing = SettingTrailing.Toggle(form.downloadChatEmotes && form.downloadChat) { onForm(form.copy(downloadChatEmotes = it)) },
                    )
                }
            }
        }
        if (qualities != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
            ) {
                LagoonButton(stringResource(android.R.string.cancel), onCancel, Modifier.weight(1f), style = ButtonStyle.Secondary)
                if (storageMounted) {
                    LagoonButton(
                        stringResource(if (item.kind == DownloadKind.Stream) R.string.download_sheet_record else R.string.download),
                        onDownload,
                        Modifier.weight(1f).testTag(DownloadButtonTag),
                        icon = if (item.kind == DownloadKind.Stream) LucideIcons.CircleDot else LucideIcons.Download,
                        enabled = form.location != 0 || form.sharedPath != null,
                    )
                }
            }
        }
    }
}

/** The second page: v0.1's notification permission explanation, shown once after the first download. */
@Composable
fun NotificationPermissionContent(onOk: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.notification_permission_title), style = Lagoon.type.headline, color = Lagoon.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.notification_permission_message), style = Lagoon.type.body, color = Lagoon.colors.textSecondary)
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s)) {
            LagoonButton(stringResource(android.R.string.cancel), onCancel, Modifier.weight(1f), style = ButtonStyle.Secondary)
            LagoonButton(stringResource(android.R.string.ok), onOk, Modifier.weight(1f))
        }
    }
}

fun formatSize(bytes: Long): String = com.github.andreyasadchy.xtra.ui.saved.DownloadLogic.formatBytes(bytes.toDouble())

@Composable
private fun ItemHeader(item: DownloadItem) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
    ) {
        Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(LagoonShapes.thumbnail).background(Lagoon.colors.control)) {
            if (item.thumbnail != null) LagoonImage(item.thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    when (item.kind) {
                        DownloadKind.Stream -> R.string.download_sheet_title_stream
                        DownloadKind.Video -> R.string.download_sheet_title_video
                        DownloadKind.Clip -> R.string.download_sheet_title_clip
                    }
                ),
                style = Lagoon.type.labelStrong, color = Lagoon.colors.accent,
            )
            Text(
                item.title?.trim().orEmpty(), style = Lagoon.type.bodyStrong, color = Lagoon.colors.textPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(item.subtitle, style = Lagoon.type.label, color = Lagoon.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text, style = Lagoon.type.labelStrong, color = Lagoon.colors.textSecondary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** A segmented-button style choice: teal tint when chosen. */
@Composable
private fun QualityButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Lagoon.colors
    Box(
        Modifier
            .heightIn(min = LagoonSpacing.touchTarget)
            .clip(LagoonShapes.pill)
            .pressable(onClick = onClick, shape = LagoonShapes.pill, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .background(if (selected) colors.accentSoft else colors.control)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Lagoon.type.labelStrong, color = if (selected) colors.accent else colors.textPrimary, maxLines = 1)
    }
}

/**
 * Start and end of a video download: the frames at both ends over a range slider, and the two time fields of v0.1
 * under it. The fields stay the source of truth (empty means "from where it was left" and "to the end", as before);
 * dragging writes them, typing a valid time moves the slider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePicker(
    form: DownloadForm,
    onForm: (DownloadForm) -> Unit,
    total: Long,
    defaultFrom: Long,
    storyboard: Storyboard?,
    thumbnail: Any?,
) {
    val colors = Lagoon.colors
    val from = (if (form.fromText.isEmpty()) defaultFrom else DownloadSheetLogic.parseTime(form.fromText) ?: defaultFrom).coerceIn(0, total)
    val to = (if (form.toText.isEmpty()) total else DownloadSheetLogic.parseTime(form.toText) ?: total).coerceIn(0, total)
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s)) {
            FramePreview(storyboard, from, thumbnail, DownloadSheetLogic.formatTime(from), Modifier.weight(1f))
            FramePreview(storyboard, to, thumbnail, DownloadSheetLogic.formatTime(to), Modifier.weight(1f))
        }
        RangeSlider(
            value = from.toFloat()..maxOf(from, to).toFloat(),
            onValueChange = { range ->
                onForm(
                    form.copy(
                        fromText = DownloadSheetLogic.formatTime(range.start.toLong()),
                        toText = DownloadSheetLogic.formatTime(range.endInclusive.toLong()),
                    )
                )
            },
            valueRange = 0f..total.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.control,
            ),
            modifier = Modifier.fillMaxWidth().testTag("downloadRange"),
        )
        Text(
            stringResource(R.string.download_sheet_selected, DownloadSheetLogic.formatTime((to - from).coerceAtLeast(0)), DownloadSheetLogic.formatTime(total)),
            style = Lagoon.type.label, color = colors.textSecondary,
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.from), style = Lagoon.type.label, color = colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                LagoonTextField(
                    value = form.fromText,
                    onValueChange = { onForm(form.copy(fromText = it.filter { c -> c.isDigit() || c == ':' }.take(9))) },
                    placeholder = DownloadSheetLogic.formatTime(defaultFrom),
                    leadingIcon = LucideIcons.Clock,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("downloadFrom"),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.to), style = Lagoon.type.label, color = colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                LagoonTextField(
                    value = form.toText,
                    onValueChange = { onForm(form.copy(toText = it.filter { c -> c.isDigit() || c == ':' }.take(9))) },
                    placeholder = DownloadSheetLogic.formatTime(total),
                    leadingIcon = LucideIcons.Clock,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("downloadTo"),
                )
            }
        }
    }
}

/** One frame of the storyboard (the video's thumbnail when there is none) with its time under it. */
@Composable
private fun FramePreview(storyboard: Storyboard?, millis: Long, thumbnail: Any?, time: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val frame = storyboard?.frameAt(millis)
    var sheet by remember(frame?.first) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(frame?.first) {
        val url = frame?.first ?: return@LaunchedEffect
        val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).build())
        sheet = (result as? SuccessResult)?.image?.toBitmap()?.let { withContext(Dispatchers.Default) { it.asImageBitmap() } }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(LagoonShapes.thumbnail).background(Lagoon.colors.control)) {
            val image = sheet
            if (frame != null && image != null && storyboard != null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawImage(
                        image,
                        srcOffset = IntOffset(frame.second, frame.third),
                        srcSize = IntSize(storyboard.width, storyboard.height),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    )
                }
            } else if (thumbnail != null) {
                LagoonImage(thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Text(time, style = Lagoon.type.labelStrong, color = Lagoon.colors.textPrimary, modifier = Modifier.padding(top = 4.dp))
    }
}
