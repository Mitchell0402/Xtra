package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.ChannelViewerList
import com.github.andreyasadchy.xtra.ui.design.components.Avatar
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.components.LagoonTextField
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonRow
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes

/** Groups of the viewer list after a search: broadcaster, moderators, VIPs, viewers. */
object ViewerListLogic {
    fun groups(list: ChannelViewerList, query: String): List<Pair<Int, List<String>>> {
        val q = query.trim()
        fun f(names: List<String>) = if (q.isEmpty()) names else names.filter { it.contains(q, true) }
        return listOf(
            R.string.broadcaster to f(list.broadcasters),
            R.string.moderators to f(list.moderators),
            R.string.vips to f(list.vips),
            R.string.viewers to f(list.viewers),
        ).filter { it.second.isNotEmpty() }
    }
}

/** The viewer list as a sheet with a search field (plan 7.5); replaces PlayerViewerListDialog. */
@Composable
fun ViewerListSheet(list: ChannelViewerList?, compactCount: Boolean, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    LagoonBottomSheet(onDismiss, skipHalfHeight = true, title = stringResource(R.string.cht_viewer_list)) {
        ViewerListContent(list, compactCount, onOpen)
    }
}

@Composable
fun ViewerListContent(list: ChannelViewerList?, compactCount: Boolean, onOpen: (String) -> Unit) {
    val colors = Lagoon.colors
    var query by remember { mutableStateOf("") }
    list?.count?.let {
        Text(
            stringResource(R.string.user_count, com.github.andreyasadchy.xtra.util.TwitchApiHelper.formatCount(it, compactCount)),
            style = Lagoon.type.label, color = colors.textSecondary, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
    }
    LagoonTextField(query, { query = it }, stringResource(R.string.cht_viewer_search), Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("viewerSearch"))
    val height = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    if (list == null) {
        repeat(5) { SkeletonRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
        return
    }
    val groups = remember(list, query) { ViewerListLogic.groups(list, query) }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = height).testTag("viewerList")) {
        if (groups.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.cht_viewer_none), style = Lagoon.type.label, color = colors.textSecondary)
                }
            }
        }
        groups.forEach { (title, names) ->
            item(key = "h$title") {
                Text(
                    stringResource(title) + " · " + names.size, style = Lagoon.type.labelStrong, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
                )
            }
            items(names, key = { "$title/$it" }) { login ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).pressable(onClick = { onOpen(login) }, shape = LagoonShapes.row, scaleOnPress = false)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(null, login, size = 28.dp)
                    Text(login, style = Lagoon.type.body, color = colors.textPrimary, maxLines = 1)
                }
            }
        }
    }
}
