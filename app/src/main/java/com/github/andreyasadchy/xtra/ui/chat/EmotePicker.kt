package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import kotlinx.coroutines.launch

/** One group in the emote panel: recent, this channel, Twitch, 7TV, BTTV, FFZ, personal (plan 7.5). */
@Immutable
class EmoteSection(val id: String, val title: Int, val emotes: List<Emote>)

/**
 * Groups the emotes the chat knows the way the panel shows them. [channelTwitch] are the names of the viewer's Twitch
 * emotes from this channel (the v0.1 list sorted them first); [recent] are names, newest first.
 */
object EmoteSections {
    fun build(recent: List<String>, twitch: List<Emote>, channelTwitch: Set<String>, personal: List<Emote>, thirdParty: List<Emote>): List<EmoteSection> {
        val all = (twitch + personal + thirdParty)
        val byName = HashMap<String, Emote>()
        all.forEach { e -> e.name?.let { byName.putIfAbsent(it, e) } }
        val recentEmotes = recent.mapNotNull { byName[it] }
        val channel = twitch.filter { it.name in channelTwitch } + thirdParty.filter {
            it.source == Emote.CHANNEL_STV || it.source == Emote.CHANNEL_BTTV || it.source == Emote.CHANNEL_FFZ
        }
        val sections = listOf(
            EmoteSection("recent", R.string.cht_emotes_recent, recentEmotes),
            EmoteSection("personal", R.string.cht_emotes_personal, personal),
            EmoteSection("channel", R.string.cht_emotes_channel, channel),
            EmoteSection("twitch", R.string.cht_emotes_twitch, twitch.filter { it.name !in channelTwitch }),
            EmoteSection("stv", R.string.cht_emotes_stv, thirdParty.filter { it.source == Emote.GLOBAL_STV }),
            EmoteSection("bttv", R.string.cht_emotes_bttv, thirdParty.filter { it.source == Emote.GLOBAL_BTTV }),
            EmoteSection("ffz", R.string.cht_emotes_ffz, thirdParty.filter { it.source == Emote.GLOBAL_FFZ }),
        )
        // the same name can come from two sources (7TV and BTTV channel sets); the chat uses the first one, so does the panel
        return sections.map { EmoteSection(it.id, it.title, it.emotes.filter { e -> e.name != null }.distinctBy { e -> e.name }) }.filter { it.emotes.isNotEmpty() }
    }

    fun search(sections: List<EmoteSection>, query: String): List<Emote> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        return sections.flatMap { it.emotes }.filter { e -> e.name != null && e.name.contains(q, true) && seen.add(e.name) }
            .sortedBy { e -> if (e.name!!.startsWith(q, true)) 0 else 1 }
    }
}

/**
 * The emote panel. It takes the keyboard's place under the input (the two switch, plan 10.3 input-method test):
 * search on top, source tabs, a grid; tap inserts, long press shows the emote large.
 */
@Composable
fun EmotePicker(state: ChatState, actions: ChatActions, modifier: Modifier = Modifier) {
    val colors = Lagoon.colors
    var query by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<Emote?>(null) }
    val sections = state.emoteSections
    val results = remember(query, sections) { EmoteSections.search(sections, query) }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // index of each section header in the grid, for the tabs
    val headerIndex = remember(sections) {
        var i = 0
        sections.map { s -> (s.id to i).also { i += 1 + s.emotes.size } }.toMap()
    }
    Box(modifier.background(colors.card).testTag("emotePicker")) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)
                    .height(38.dp).background(colors.control, LagoonShapes.pill).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(LucideIcons.Search, null, tint = colors.textHint, modifier = Modifier.size(16.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.cht_emotes_search), style = Lagoon.type.label, color = colors.textHint, maxLines = 1)
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = Lagoon.type.label.copy(color = colors.textPrimary), cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth().testTag("emoteSearch"),
                    )
                }
            }
            if (query.isBlank() && sections.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sections.forEach { s ->
                        Text(
                            stringResource(s.title),
                            style = Lagoon.type.labelStrong, color = colors.textSecondary,
                            modifier = Modifier
                                .pressable(onClick = { scope.launch { grid.scrollToItem(headerIndex[s.id] ?: 0) } }, shape = LagoonShapes.pill)
                                .background(colors.control, LagoonShapes.pill)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            if (sections.isEmpty() && query.isBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.cht_emotes_loading), style = Lagoon.type.label, color = colors.textSecondary)
                }
            } else {
                LazyVerticalGrid(
                    GridCells.Adaptive(48.dp),
                    state = grid,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                ) {
                    if (query.isNotBlank()) {
                        if (results.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(stringResource(R.string.cht_emotes_none), style = Lagoon.type.label, color = colors.textSecondary, modifier = Modifier.padding(16.dp))
                            }
                        }
                        results.forEach { e -> item(key = "r:" + e.name) { EmoteCell(e, state.style.animated, actions) { preview = it } } }
                    } else {
                        sections.forEach { s ->
                            item(key = "h:" + s.id, span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    stringResource(s.title), style = Lagoon.type.labelStrong, color = colors.textSecondary,
                                    modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                                )
                            }
                            s.emotes.forEach { e -> item(key = s.id + ":" + e.name) { EmoteCell(e, state.style.animated, actions) { preview = it } } }
                        }
                    }
                }
            }
        }
        preview?.let { EmotePreview(it, state.style.animated, Modifier.align(Alignment.TopCenter).padding(top = 12.dp)) }
    }
}

@Composable
private fun EmoteCell(emote: Emote, animate: Boolean, actions: ChatActions, onPreview: (Emote?) -> Unit) {
    val name = emote.name ?: return
    val url = emote.url2x ?: emote.url3x ?: emote.url1x ?: emote.url4x ?: return
    val image = remember(url) { ChatImage("e", url, ChatImageKind.Emote, emote.isAnimated, emote.thirdParty) }
    Box(
        Modifier
            .size(48.dp)
            .pointerInputEmote(onTap = { actions.insert(name) }, onPreview = { onPreview(if (it) emote else null) })
            .semantics { contentDescription = name; onClick { actions.insert(name); true } },
        contentAlignment = Alignment.Center,
    ) {
        ChatPicture(image, animate, onClick = null, modifier = Modifier.size(32.dp))
    }
}

private fun Modifier.pointerInputEmote(onTap: () -> Unit, onPreview: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        detectTapGestures(
            onTap = { onTap() },
            onLongPress = { onPreview(true) },
            onPress = {
                tryAwaitRelease()
                onPreview(false)
            },
        )
    }

@Composable
private fun EmotePreview(emote: Emote, animate: Boolean, modifier: Modifier) {
    val colors = Lagoon.colors
    val url = emote.url4x ?: emote.url3x ?: emote.url2x ?: emote.url1x ?: return
    Column(
        modifier.background(colors.control, LagoonShapes.row).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChatPicture(ChatImage("pv", url, ChatImageKind.Emote, emote.isAnimated, emote.thirdParty), animate, onClick = null, modifier = Modifier.size(96.dp))
        Text(emote.name ?: "", style = Lagoon.type.labelStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
    }
}
