package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.CustomProxy
import com.github.andreyasadchy.xtra.model.ui.StreamProxy
import com.github.andreyasadchy.xtra.model.ui.VideoSwap
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle
import com.github.andreyasadchy.xtra.ui.design.components.EmptyState
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonSwitch
import com.github.andreyasadchy.xtra.ui.design.components.SettingRow
import com.github.andreyasadchy.xtra.ui.design.components.SettingTrailing
import com.github.andreyasadchy.xtra.ui.design.components.SettingsGroup
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.settings.model.SettingsPageSpec
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The three source lists of the ad blocking page, in place of CustomProxySettingsFragment,
 * StreamProxySettingsFragment and VideoSwapSettingsFragment. The view models and every database call are the
 * v0.1 ones; only the screens are new. The list objects are mutable rows of the Room tables, changed in place as
 * before, so [revision] tells Compose when to redraw.
 */
@Stable
class SettingsLists(
    private val customVm: CustomProxySettingsViewModel,
    private val streamVm: StreamProxySettingsViewModel,
    private val swapVm: VideoSwapSettingsViewModel,
    private val networkLibrary: () -> String?,
    /** The video swap row with position -1: the platform and player type every stream request uses. */
    private val defaultSwap: () -> VideoSwap,
    private val saveDefaultSwap: (platform: String?, playerType: String?) -> Unit,
    /** Shows the system's local network permission request (Android 17); null where it does not exist. */
    val requestLocalNetwork: (() -> Unit)?,
) {
    var revision by mutableIntStateOf(0)
        private set

    private fun changed() { revision++ }

    // custom proxies (playlist proxies)

    val customProxies: List<CustomProxy> get() { revision; return customVm.list.value.toList() }
    fun customStatus(url: String?): Boolean? { revision; return url?.let { customVm.statusMap[it] } }

    suspend fun watchCustom() {
        customVm.getProxies(networkLibrary())
        coroutineScope {
            launch { customVm.list.collectLatest { changed() } }
            launch { customVm.statusChanged.collect { changed() } }
        }
    }

    fun addCustom(url: String?, addQueryParams: Boolean) {
        val list = customVm.list.value
        val item = CustomProxy(url = url, addQueryParams = addQueryParams, position = list.lastIndex + 1)
        list.add(item)
        customVm.saveProxy(item)
        customVm.updateProxyStatus(networkLibrary(), url)
        changed()
    }

    fun editCustom(item: CustomProxy, url: String?, addQueryParams: Boolean) {
        item.url = url
        item.addQueryParams = addQueryParams
        customVm.updateProxy(item)
        customVm.updateProxyStatus(networkLibrary(), url)
        changed()
    }

    fun enableCustom(item: CustomProxy, enabled: Boolean) {
        item.enabled = enabled
        customVm.updateProxy(item)
        changed()
    }

    fun deleteCustom(item: CustomProxy) {
        customVm.list.value.remove(item)
        customVm.deleteProxy(item)
        changed()
    }

    fun moveCustom(from: Int, to: Int) {
        val list = customVm.list.value
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        customVm.updateProxies()
        changed()
    }

    // stream (HTTP) proxies

    val streamProxies: List<StreamProxy> get() { revision; return streamVm.list.value.toList() }

    suspend fun watchStream() {
        streamVm.getProxies()
        streamVm.list.collectLatest { changed() }
    }

    fun saveStream(item: StreamProxy?, edit: StreamProxyEdit) {
        val target = item ?: StreamProxy(position = streamVm.list.value.lastIndex + 1)
        target.host = edit.host
        target.port = edit.port.toIntOrNull()
        target.username = edit.user
        target.password = edit.password
        target.proxyPlaybackAccessToken = edit.playbackAccessToken
        target.proxyMultivariantPlaylist = edit.multivariantPlaylist
        target.proxyMediaPlaylist = edit.mediaPlaylist
        if (item == null) {
            streamVm.list.value.add(target)
            streamVm.saveProxy(target)
        } else {
            streamVm.updateProxy(target)
        }
        changed()
    }

    fun enableStream(item: StreamProxy, enabled: Boolean) {
        item.enabled = enabled
        streamVm.updateProxy(item)
        changed()
    }

    fun deleteStream(item: StreamProxy) {
        streamVm.list.value.remove(item)
        streamVm.deleteProxy(item)
        changed()
    }

    fun moveStream(from: Int, to: Int) {
        val list = streamVm.list.value
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        streamVm.updateProxies()
        changed()
    }

    // video swap (player types used during ads); the first row is the default pair, position -1

    val swaps: List<VideoSwap> get() { revision; return swapVm.list.value.toList() }

    suspend fun watchSwaps() {
        swapVm.getVideoSwapItems(defaultSwap())
        swapVm.list.collectLatest { changed() }
    }

    fun saveSwap(item: VideoSwap?, platform: String?, playerType: String?) {
        when {
            item == null -> {
                val list = swapVm.list.value
                val added = VideoSwap(platform = platform, playerType = playerType, position = list.lastIndex + 1)
                list.add(added)
                swapVm.saveVideoSwap(added)
            }
            item.position == -1 -> {
                item.platform = platform
                item.playerType = playerType
                saveDefaultSwap(platform, playerType)
            }
            else -> {
                item.platform = platform
                item.playerType = playerType
                swapVm.updateVideoSwap(item)
            }
        }
        changed()
    }

    fun enableSwap(item: VideoSwap, enabled: Boolean) {
        item.enabled = enabled
        swapVm.updateVideoSwap(item)
        changed()
    }

    fun deleteSwap(item: VideoSwap) {
        swapVm.list.value.remove(item)
        swapVm.deleteVideoSwap(item)
        changed()
    }

    /** Moves among the user's rows; the default row stays first. */
    fun moveSwap(from: Int, to: Int) {
        val list = swapVm.list.value
        if (from < 1 || to < 1 || from > list.lastIndex || to > list.lastIndex) return
        list.add(to, list.removeAt(from))
        swapVm.updateVideoSwapItems()
        changed()
    }
}

data class StreamProxyEdit(
    val host: String = "",
    val port: String = "",
    val user: String = "",
    val password: String = "",
    val playbackAccessToken: Boolean = false,
    val multivariantPlaylist: Boolean = false,
    val mediaPlaylist: Boolean = true,
)

// ---- pages --------------------------------------------------------------------------------------------------------

@Composable
private fun ListRowControls(
    label: String,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEnabled: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Lagoon.colors
    LagoonIconButton(LucideIcons.ArrowUp, stringResource(R.string.st_move_up, label), onUp, filled = false, tint = colors.textSecondary, size = 40.dp, enabled = canMoveUp)
    LagoonIconButton(LucideIcons.ArrowDown, stringResource(R.string.st_move_down, label), onDown, filled = false, tint = colors.textSecondary, size = 40.dp, enabled = canMoveDown)
    LagoonIconButton(LucideIcons.Trash2, stringResource(R.string.st_delete_named, label), onDelete, filled = false, tint = colors.danger, size = 40.dp)
    LagoonSwitch(enabled, onEnabled, Modifier.semantics { contentDescription = label })
}

@Composable
private fun SourceRow(
    title: String,
    subtitle: String?,
    subtitleColor: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
    controls: @Composable () -> Unit,
) {
    val colors = Lagoon.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .pressable(onClick = onClick, shape = LagoonShapes.row, scaleOnPress = false)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(title, style = Lagoon.type.bodyStrong, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Lagoon.type.label, color = subtitleColor ?: colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        controls()
    }
}

@Composable
fun CustomProxyPage(page: SettingsPageSpec, lists: SettingsLists?, onBack: (() -> Unit)?) {
    val colors = Lagoon.colors
    var editing by remember { mutableStateOf<CustomProxy?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CustomProxy?>(null) }
    if (lists != null) LaunchedEffect(lists) { lists.watchCustom() }
    val items = lists?.customProxies.orEmpty()
    SettingsPageFrame(stringResource(page.title), onBack) {
        item(key = "explain") { Text(stringResource(R.string.ad_block_custom_proxy_desc), style = Lagoon.type.label, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 6.dp)) }
        item(key = "list") {
            if (items.isEmpty()) {
                EmptyState(stringResource(R.string.st_list_empty), Modifier.fillMaxWidth(), icon = LucideIcons.Globe)
            } else {
                SettingsGroup {
                    items.forEachIndexed { index, proxy ->
                        val status = lists?.customStatus(proxy.url)
                        SourceRow(
                            title = proxy.url?.takeIf { it.isNotBlank() } ?: stringResource(R.string.st_not_set),
                            subtitle = stringResource(when (status) { true -> R.string.online; false -> R.string.offline; null -> R.string.loading }),
                            subtitleColor = when (status) { true -> colors.accent; false -> colors.danger; null -> null },
                            onClick = { editing = proxy },
                        ) {
                            val label = proxy.url ?: ""
                            ListRowControls(label, proxy.enabled, index > 0, index < items.lastIndex,
                                onEnabled = { lists?.enableCustom(proxy, it) },
                                onUp = { lists?.moveCustom(index, index - 1) },
                                onDown = { lists?.moveCustom(index, index + 1) },
                                onDelete = { deleting = proxy })
                        }
                    }
                }
            }
        }
        item(key = "add") { LagoonButton(stringResource(R.string.add_proxy), { adding = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, icon = LucideIcons.Plus) }
    }
    val target = editing
    if (adding || target != null) {
        CustomProxySheet(target, onDismiss = { adding = false; editing = null }) { url, params ->
            if (target == null) lists?.addCustom(url, params) else lists?.editCustom(target, url, params)
        }
    }
    deleting?.let { proxy ->
        ConfirmSheet(stringResource(R.string.delete), stringResource(R.string.delete_proxy_message), stringResource(R.string.delete), { lists?.deleteCustom(proxy) }, { deleting = null })
    }
}

@Composable
private fun CustomProxySheet(item: CustomProxy?, onDismiss: () -> Unit, onSave: (String?, Boolean) -> Unit) {
    var url by remember { mutableStateOf(item?.url ?: "") }
    var params by remember { mutableStateOf(item?.addQueryParams ?: true) }
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(if (item == null) R.string.add_proxy else R.string.st_edit_proxy), skipHalfHeight = true) {
        SheetField(stringResource(R.string.proxy_url), url, { url = it }, KeyboardType.Uri, singleLine = false)
        SheetExplanation(stringResource(R.string.stream_proxy_url_desc))
        SettingRow(stringResource(R.string.add_proxy_query_params), Modifier.padding(horizontal = 8.dp), trailing = SettingTrailing.Toggle(params) { params = it })
        SheetButtons(
            secondary = stringResource(R.string.st_cancel) to onDismiss,
            primary = stringResource(R.string.st_save) to { onSave(url, params); onDismiss() },
        )
    }
}

@Composable
fun StreamProxyPage(page: SettingsPageSpec, lists: SettingsLists?, onBack: (() -> Unit)?) {
    val colors = Lagoon.colors
    var editing by remember { mutableStateOf<StreamProxy?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<StreamProxy?>(null) }
    if (lists != null) LaunchedEffect(lists) { lists.watchStream() }
    val items = lists?.streamProxies.orEmpty()
    SettingsPageFrame(stringResource(page.title), onBack) {
        item(key = "explain") { Text(stringResource(R.string.ad_block_stream_proxy_desc), style = Lagoon.type.label, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 6.dp)) }
        item(key = "list") {
            if (items.isEmpty()) {
                EmptyState(stringResource(R.string.st_list_empty), Modifier.fillMaxWidth(), icon = LucideIcons.Network)
            } else {
                SettingsGroup {
                    items.forEachIndexed { index, proxy ->
                        val label = listOfNotNull(proxy.host, proxy.port?.toString()).joinToString(":")
                        SourceRow(
                            title = label.ifEmpty { stringResource(R.string.st_not_set) },
                            subtitle = streamProxyUses(proxy),
                            onClick = { editing = proxy },
                        ) {
                            ListRowControls(label, proxy.enabled, index > 0, index < items.lastIndex,
                                onEnabled = { lists?.enableStream(proxy, it) },
                                onUp = { lists?.moveStream(index, index - 1) },
                                onDown = { lists?.moveStream(index, index + 1) },
                                onDelete = { deleting = proxy })
                        }
                    }
                }
            }
        }
        item(key = "add") { LagoonButton(stringResource(R.string.add_proxy), { adding = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, icon = LucideIcons.Plus) }
    }
    val target = editing
    if (adding || target != null) {
        StreamProxySheet(target, lists?.requestLocalNetwork, onDismiss = { adding = false; editing = null }) { edit -> lists?.saveStream(target, edit) }
    }
    deleting?.let { proxy ->
        ConfirmSheet(stringResource(R.string.delete), stringResource(R.string.delete_proxy_message), stringResource(R.string.delete), { lists?.deleteStream(proxy) }, { deleting = null })
    }
}

@Composable
private fun streamProxyUses(proxy: StreamProxy): String = listOfNotNull(
    stringResource(R.string.proxy_playback_access_token).takeIf { proxy.proxyPlaybackAccessToken },
    stringResource(R.string.proxy_multivariant_playlist).takeIf { proxy.proxyMultivariantPlaylist },
    stringResource(R.string.proxy_media_playlist).takeIf { proxy.proxyMediaPlaylist },
).joinToString(stringResource(R.string.st_list_separator)).ifEmpty { stringResource(R.string.st_not_set) }

@Composable
private fun StreamProxySheet(item: StreamProxy?, requestLocalNetwork: (() -> Unit)?, onDismiss: () -> Unit, onSave: (StreamProxyEdit) -> Unit) {
    var edit by remember {
        mutableStateOf(
            item?.let {
                StreamProxyEdit(it.host ?: "", it.port?.toString() ?: "", it.username ?: "", it.password ?: "", it.proxyPlaybackAccessToken, it.proxyMultivariantPlaylist, it.proxyMediaPlaylist)
            } ?: StreamProxyEdit()
        )
    }
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(if (item == null) R.string.add_proxy else R.string.st_edit_proxy), skipHalfHeight = true) {
        SheetField(stringResource(R.string.proxy_host), edit.host, { edit = edit.copy(host = it) }, KeyboardType.Uri)
        SheetField(stringResource(R.string.proxy_port), edit.port, { v -> edit = edit.copy(port = v.filter(Char::isDigit)) }, KeyboardType.Number)
        SheetField(stringResource(R.string.proxy_user), edit.user, { edit = edit.copy(user = it) }, KeyboardType.Text)
        SheetField(stringResource(R.string.proxy_password), edit.password, { edit = edit.copy(password = it) }, KeyboardType.Password, secret = true)
        Column(Modifier.padding(horizontal = 8.dp)) {
            SettingRow(stringResource(R.string.proxy_playback_access_token), trailing = SettingTrailing.Toggle(edit.playbackAccessToken) { edit = edit.copy(playbackAccessToken = it) })
            SettingRow(stringResource(R.string.proxy_multivariant_playlist), trailing = SettingTrailing.Toggle(edit.multivariantPlaylist) { edit = edit.copy(multivariantPlaylist = it) })
            SettingRow(stringResource(R.string.proxy_media_playlist), trailing = SettingTrailing.Toggle(edit.mediaPlaylist) { edit = edit.copy(mediaPlaylist = it) })
            if (requestLocalNetwork != null) {
                SettingRow(
                    stringResource(R.string.access_local_network_title),
                    description = stringResource(R.string.access_local_network_desc),
                    icon = LucideIcons.Network,
                    onClick = requestLocalNetwork,
                )
            }
        }
        SheetButtons(
            secondary = stringResource(R.string.st_cancel) to onDismiss,
            primary = stringResource(R.string.st_save) to { onSave(edit); onDismiss() },
        )
    }
}

@Composable
fun VideoSwapPage(page: SettingsPageSpec, lists: SettingsLists?, onBack: (() -> Unit)?) {
    val colors = Lagoon.colors
    var editing by remember { mutableStateOf<VideoSwap?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<VideoSwap?>(null) }
    if (lists != null) LaunchedEffect(lists) { lists.watchSwaps() }
    val items = lists?.swaps.orEmpty()
    SettingsPageFrame(stringResource(page.title), onBack) {
        item(key = "explain") { Text(stringResource(R.string.ad_block_video_swap_desc), style = Lagoon.type.label, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 6.dp)) }
        item(key = "list") {
            SettingsGroup {
                items.forEachIndexed { index, swap ->
                    val label = listOfNotNull(swap.platform?.takeIf { it.isNotBlank() }, swap.playerType?.takeIf { it.isNotBlank() }).joinToString(", ")
                    if (swap.position == -1) {
                        SettingRow(
                            stringResource(R.string.default_values),
                            Modifier.padding(horizontal = 4.dp),
                            description = stringResource(R.string.st_swap_default_summary),
                            icon = LucideIcons.Star,
                            trailing = SettingTrailing.Value(label),
                            onClick = { editing = swap },
                        )
                    } else {
                        SourceRow(title = label.ifEmpty { stringResource(R.string.st_not_set) }, subtitle = null, onClick = { editing = swap }) {
                            ListRowControls(label, swap.enabled, index > 1, index < items.lastIndex,
                                onEnabled = { lists?.enableSwap(swap, it) },
                                onUp = { lists?.moveSwap(index, index - 1) },
                                onDown = { lists?.moveSwap(index, index + 1) },
                                onDelete = { deleting = swap })
                        }
                    }
                }
            }
        }
        item(key = "add") { LagoonButton(stringResource(R.string.st_add_swap), { adding = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, icon = LucideIcons.Plus) }
    }
    val target = editing
    if (adding || target != null) {
        VideoSwapSheet(target, onDismiss = { adding = false; editing = null }) { platform, type -> lists?.saveSwap(target, platform, type) }
    }
    deleting?.let { swap ->
        ConfirmSheet(stringResource(R.string.delete), stringResource(R.string.delete_item_message), stringResource(R.string.delete), { lists?.deleteSwap(swap) }, { deleting = null })
    }
}

@Composable
private fun VideoSwapSheet(item: VideoSwap?, onDismiss: () -> Unit, onSave: (String?, String?) -> Unit) {
    var platform by remember { mutableStateOf(item?.platform ?: "") }
    var playerType by remember { mutableStateOf(item?.playerType ?: "") }
    LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(if (item == null) R.string.st_add_swap else R.string.st_edit_swap), skipHalfHeight = true) {
        SheetField(stringResource(R.string.platform_param), platform, { platform = it }, KeyboardType.Text)
        SheetField(stringResource(R.string.player_type_param), playerType, { playerType = it }, KeyboardType.Text)
        SheetButtons(
            secondary = stringResource(R.string.st_cancel) to onDismiss,
            primary = stringResource(R.string.st_save) to { onSave(platform, playerType); onDismiss() },
        )
    }
}

/** A labelled text field inside a sheet. */
@Composable
fun SheetField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboard: KeyboardType,
    singleLine: Boolean = true,
    secret: Boolean = false,
) {
    val colors = Lagoon.colors
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(label, style = Lagoon.type.label, color = colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card, LagoonShapes.input)
                .padding(horizontal = 16.dp, vertical = 13.dp)
                .semantics { contentDescription = label },
            textStyle = Lagoon.type.body.copy(color = colors.textPrimary, fontFamily = if (keyboard == KeyboardType.Uri) FontFamily.Monospace else Lagoon.type.body.fontFamily),
            cursorBrush = SolidColor(colors.accent),
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 3,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
        )
    }
}
