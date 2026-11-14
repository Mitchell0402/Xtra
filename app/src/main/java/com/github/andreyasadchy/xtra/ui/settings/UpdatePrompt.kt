package com.github.andreyasadchy.xtra.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.components.SettingIconTile
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle

/**
 * The in-app update prompts (from MainActivity's startup check and the settings "check for updates" row), in place
 * of the two v0.1 alert dialogs: "update available" and the download progress. The check, download and install
 * code is unchanged (MainViewModel / SettingsViewModel).
 */
@Stable
class UpdatePromptState {
    /** Release URL of the update on offer; null when no prompt shows. */
    var offeredUrl by mutableStateOf<String?>(null)
    var downloading by mutableStateOf(false)
    var downloaded by mutableLongStateOf(0L)
    var size by mutableStateOf<Long?>(null)

    fun offer(url: String) {
        offeredUrl = url
        downloading = false
    }

    fun startDownload(totalSize: Long?) {
        offeredUrl = null
        downloading = true
        downloaded = 0
        size = totalSize
    }

    fun close() {
        offeredUrl = null
        downloading = false
    }
}

@Composable
fun UpdatePrompt(
    state: UpdatePromptState,
    onUpdate: (url: String) -> Unit,
    onLater: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val url = state.offeredUrl
    if (url != null) {
        LagoonBottomSheet(onDismiss = { state.close(); onLater() }, title = stringResource(R.string.update_available)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                SettingIconTile(LucideIcons.Package, highlight = true)
            }
            SheetExplanation(stringResource(R.string.st_update_message))
            SheetButtons(
                secondary = stringResource(R.string.st_later) to { state.close(); onLater() },
                primary = stringResource(R.string.st_update_now) to { onUpdate(url) },
            )
        }
    }
    if (state.downloading) {
        val context = LocalContext.current
        val size = state.size
        LagoonBottomSheet(onDismiss = { state.close(); onCancelDownload() }, title = stringResource(R.string.downloading_update)) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp)) {
                if (size != null && size > 0) {
                    LinearProgressIndicator(
                        progress = { (state.downloaded.toFloat() / size).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Lagoon.colors.accent,
                        trackColor = Lagoon.colors.control,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Text(
                        stringResource(R.string.downloading_update_progress, Formatter.formatFileSize(context, state.downloaded), Formatter.formatFileSize(context, size)),
                        style = Lagoon.type.label, color = Lagoon.colors.textSecondary, modifier = Modifier.padding(top = 10.dp),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp), color = Lagoon.colors.accent, trackColor = Lagoon.colors.control)
                }
            }
            SheetButtons(secondary = null, primary = stringResource(R.string.st_cancel) to { state.close(); onCancelDownload() }, primaryStyle = ButtonStyle.Secondary)
        }
    }
}
