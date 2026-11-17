package com.github.andreyasadchy.xtra.ui.chat

import com.github.andreyasadchy.xtra.ui.design.components.currentSheetMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.Poll
import com.github.andreyasadchy.xtra.model.chat.Prediction
import com.github.andreyasadchy.xtra.ui.design.components.Avatar
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonImage
import com.github.andreyasadchy.xtra.ui.design.components.SettingRow
import com.github.andreyasadchy.xtra.ui.design.components.SettingTrailing
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import java.text.NumberFormat

/** What the user card shows about the person, loaded when it opens (v0.1 MessageClickedViewModel). */
@Immutable
class ChatUserInfo(
    val id: String?,
    val login: String?,
    val name: String?,
    val image: String?,
    val banner: String?,
    val created: String?,
    val followed: String?,
)

/** The extra state the sheets read, filled by [ChatFragment] when a sheet opens. */
class ChatSheetData {
    var user by mutableStateOf<ChatUserInfo?>(null)
    var userFailed by mutableStateOf(false)
    var emoteSource by mutableStateOf<String?>(null)
    var notifyPoints by mutableStateOf(false)
}

interface ChatSheetActions {
    val sheetData: ChatSheetData
    fun reply(message: ChatMessage)
    fun mention(message: ChatMessage)
    fun copy(message: ChatMessage)
    fun copyToInput(message: ChatMessage)
    fun copyFull(message: ChatMessage)
    fun viewChannel(id: String?, login: String?, name: String?, image: String?)
    fun setAutoClaim(on: Boolean)
    fun setNotifyPoints(on: Boolean)
    val showFullMessage: Boolean
}

@Composable
fun ChatSheets(state: ChatState, actions: ChatActions) {
    val sheet = state.sheet ?: return
    if (actions !is ChatSheetActions) return
    // the poll or prediction can end while its sheet is open
    val gone = (sheet == ChatSheet.Poll && state.poll == null) || (sheet == ChatSheet.Prediction && state.prediction == null) ||
        (sheet == ChatSheet.Points && state.points == null)
    if (gone) {
        LaunchedEffect(sheet) { actions.closeSheet() }
        return
    }
    LagoonBottomSheet(actions::closeSheet, title = sheetTitle(sheet)) { ChatSheetContent(state, sheet, actions) }
}

@Composable
private fun sheetTitle(sheet: ChatSheet): String? = when (sheet) {
    is ChatSheet.Thread -> stringResource(R.string.cht_thread)
    ChatSheet.Poll -> stringResource(R.string.cht_poll)
    ChatSheet.Prediction -> stringResource(R.string.cht_prediction)
    else -> null
}

/** What a chat sheet shows, drawn in the sheet window or in place (tests). [actions] must also be [ChatSheetActions]. */
@Composable
fun ColumnScope.ChatSheetContent(state: ChatState, sheet: ChatSheet, actions: ChatActions) {
    val sheetActions = actions as ChatSheetActions
    when (sheet) {
        is ChatSheet.UserCard -> UserCard(state, sheet.message, actions, sheetActions)
        is ChatSheet.Thread -> ThreadSheet(state, sheet.message, actions)
        is ChatSheet.EmoteCard -> EmoteCardSheet(sheet.info, state.style.animated, sheetActions.sheetData)
        ChatSheet.Points -> PointsSheet(state, actions, sheetActions)
        ChatSheet.Poll -> state.poll?.let { PollSheet(it, state.pollSecondsLeft) }
        ChatSheet.Prediction -> state.prediction?.let { PredictionSheet(it, state.predictionSecondsLeft) }
    }
}

/** Lines from the person (or replies to them), like v0.1's message dialog list. */
fun ChatState.linesOf(message: ChatMessage): List<ChatLine> {
    val id = message.userId
    val login = message.userLogin
    return lines.filter { l ->
        val m = l.message
        // a reply's "replying to" line carries the reply in replyParent (v0.1), so it comes along with the reply
        (!id.isNullOrBlank() && (m.userId == id || m.replyParent?.userId == id)) ||
            (!login.isNullOrBlank() && (m.userLogin == login || m.replyParent?.userLogin == login))
    }
}

@Composable
private fun ColumnScope.UserCard(state: ChatState, opened: ChatMessage, actions: ChatActions, sheet: ChatSheetActions) {
    val colors = Lagoon.colors
    val data = sheet.sheetData
    val user = data.user
    var selected by remember(opened) { mutableStateOf(opened) }
    val lines = remember(opened, state.lines.size) { state.linesOf(opened).takeLast(100) }
    val name = ChatEvents.displayName(user?.name ?: opened.userName, user?.login ?: opened.userLogin, state.nameDisplay) ?: ""
    Box(Modifier.fillMaxWidth()) {
        if (user?.banner != null) {
            LagoonImage(user.banner, null, Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 12.dp).clip(LagoonShapes.row), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 12.dp).clip(LagoonShapes.row).background(Brush.verticalGradient(listOf(Color.Transparent, colors.page.copy(alpha = 0.85f)))))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = if (user?.banner != null) 52.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Avatar(
                user?.image, name, size = 56.dp,
                modifier = Modifier.pressable(onClick = { sheet.viewChannel(user?.id ?: opened.userId, user?.login ?: opened.userLogin, user?.name ?: opened.userName, user?.image) }, shape = LagoonShapes.avatar(56.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(name, style = Lagoon.type.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("userCardName"))
                user?.created?.let { Text(stringResource(R.string.cht_user_created, it), style = Lagoon.type.label, color = colors.textSecondary) }
                user?.followed?.let { Text(stringResource(R.string.cht_user_followed, it), style = Lagoon.type.label, color = colors.textSecondary) }
            }
        }
    }
    val badges = remember(opened) { state.lines.firstOrNull { it.message === opened }?.images?.filter { it.kind == ChatImageKind.Badge } ?: emptyList() }
    if (badges.isNotEmpty()) {
        Row(Modifier.padding(start = 20.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            badges.forEach { b -> ChatPicture(b, state.style.animated, onClick = { img -> img.info?.let { actions.openSheet(ChatSheet.EmoteCard(it)) } }, modifier = Modifier.size(22.dp)) }
        }
    }
    Spacer(Modifier.height(12.dp))
    val canWrite = state.messagingEnabled
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        if (canWrite && !selected.id.isNullOrBlank()) CardAction(LucideIcons.Reply, stringResource(R.string.reply)) { sheet.reply(selected) }
        if (canWrite && !(selected.userName ?: selected.userLogin).isNullOrBlank()) CardAction(LucideIcons.AtSign, stringResource(R.string.cht_mention)) { sheet.mention(selected) }
        if (!selected.message.isNullOrBlank()) CardAction(LucideIcons.Copy, stringResource(R.string.cht_copy)) { sheet.copy(selected) }
        if (canWrite && !selected.message.isNullOrBlank()) CardAction(LucideIcons.TextQuote, stringResource(R.string.cht_copy_to_input)) { sheet.copyToInput(selected) }
        CardAction(LucideIcons.User, stringResource(R.string.cht_view_channel)) {
            sheet.viewChannel(user?.id ?: selected.userId, user?.login ?: selected.userLogin, user?.name ?: selected.userName, user?.image)
        }
    }
    if (selected.reply?.threadParentId != null || sheet.showFullMessage) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selected.reply?.threadParentId != null) {
                SmallAction(LucideIcons.MessageCircle, stringResource(R.string.cht_view_thread)) { actions.openSheet(ChatSheet.Thread(selected)) }
            }
            if (sheet.showFullMessage && selected.fullMsg != null) {
                SmallAction(LucideIcons.Code, stringResource(R.string.copy_fullmsg)) { sheet.copyFull(selected) }
            }
        }
    }
    if (lines.isNotEmpty()) {
        Text(
            stringResource(R.string.cht_user_messages), style = Lagoon.type.labelStrong, color = colors.textSecondary,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
        )
        val maxHeight = currentSheetMaxHeight() * 0.4f
        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxHeight).testTag("userMessages"), state = listState, reverseLayout = true) {
            items(lines.size, key = { lines[lines.size - 1 - it].key }) { i ->
                val line = lines[lines.size - 1 - i]
                ChatRow(
                    line, state.style,
                    onClick = { selected = line.message },
                    onImageClick = { img -> img.info?.let { actions.openSheet(ChatSheet.EmoteCard(it)) } },
                    selected = line.message === selected,
                )
            }
        }
    }
}

@Composable
private fun CardAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = Lagoon.colors
    Column(
        Modifier.width(68.dp).pressable(onClick = onClick, shape = LagoonShapes.row).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp).background(colors.control, LagoonShapes.pill), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
        }
        Text(label, style = Lagoon.type.caption.copy(fontWeight = null), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = Lagoon.colors
    Row(
        Modifier.heightIn(min = 40.dp).pressable(onClick = onClick, shape = LagoonShapes.pill).background(colors.control, LagoonShapes.pill).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
        Text(label, style = Lagoon.type.label, color = colors.textPrimary)
    }
}

@Composable
private fun ThreadSheet(state: ChatState, message: ChatMessage, actions: ChatActions) {
    val parent = message.reply?.threadParentId
    val lines = remember(message, state.lines.size) {
        state.lines.filter { (it.message.reply?.threadParentId == parent || it.message.id == parent) && !it.isReplyHeader }
    }
    val maxHeight = currentSheetMaxHeight() * 0.6f
    val onNameClick = remember(actions) { { line: ChatLine -> actions.openSheet(ChatSheet.UserCard(line.message)) } }
    val onImageClick = remember(actions) { { img: ChatImage -> img.info?.let { actions.openSheet(ChatSheet.EmoteCard(it)) }; Unit } }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxHeight), reverseLayout = true) {
        items(lines.size, key = { lines[lines.size - 1 - it].key }) { i ->
            val line = lines[lines.size - 1 - i]
            // U9 C1: as in the chat, the name opens the user card, an emote its card, the rest nothing
            ChatRow(line, state.style, onClick = null, onNameClick = onNameClick, onImageClick = onImageClick, selected = line.message === message)
        }
    }
}

@Composable
private fun EmoteCardSheet(info: EmoteInfo, animate: Boolean, data: ChatSheetData) {
    val colors = Lagoon.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        info.url?.let {
            ChatPicture(ChatImage("card", it, ChatImageKind.Emote, info.animated != false, info.thirdParty == true), animate, onClick = null, modifier = Modifier.size(112.dp))
        }
        Spacer(Modifier.height(12.dp))
        info.name?.let { Text(it, style = Lagoon.type.headline, color = colors.textPrimary, textAlign = TextAlign.Center) }
        val source = data.emoteSource ?: info.source?.let { stringResource(emoteSourceText(it)) }
        source?.let { Text(it, style = Lagoon.type.label, color = colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(8.dp))
    }
}

fun emoteSourceText(source: Int): Int = when (source) {
    Emote.PERSONAL_STV -> R.string.personal_stv_emote
    Emote.CHANNEL_STV -> R.string.channel_stv_emote
    Emote.CHANNEL_BTTV -> R.string.channel_bttv_emote
    Emote.CHANNEL_FFZ -> R.string.channel_ffz_emote
    Emote.GLOBAL_STV -> R.string.global_stv_emote
    Emote.GLOBAL_BTTV -> R.string.global_bttv_emote
    else -> R.string.global_ffz_emote
}

@Composable
private fun PointsSheet(state: ChatState, actions: ChatActions, sheet: ChatSheetActions) {
    val colors = Lagoon.colors
    val points = state.points ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).background(colors.accentSoft, LagoonShapes.pill), contentAlignment = Alignment.Center) {
            if (points.iconUrl != null) {
                ChatPicture(ChatImage("pi", points.iconUrl, ChatImageKind.Badge, false, false), false, null, Modifier.size(28.dp))
            } else {
                Icon(LucideIcons.Gem, null, tint = colors.accent, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(NumberFormat.getInstance().format(points.balance), style = Lagoon.type.display, color = colors.textPrimary, modifier = Modifier.testTag("pointsBalance"))
        Text(points.name ?: stringResource(R.string.cht_points), style = Lagoon.type.label, color = colors.textSecondary)
        points.lastEarned?.let {
            Text(stringResource(R.string.cht_points_last, it), style = Lagoon.type.caption.copy(fontWeight = null), color = colors.textHint, modifier = Modifier.padding(top = 2.dp))
        }
        if (points.claimAvailable) {
            Spacer(Modifier.height(12.dp))
            LagoonButton(stringResource(R.string.cht_points_claim_bonus), actions::claimPoints, icon = LucideIcons.Gift)
        }
    }
    Spacer(Modifier.height(8.dp))
    SettingRow(
        stringResource(R.string.cht_points_auto), description = stringResource(R.string.cht_points_auto_desc), icon = LucideIcons.Gift,
        trailing = SettingTrailing.Toggle(points.autoClaim) { sheet.setAutoClaim(it) }, onClick = { sheet.setAutoClaim(!points.autoClaim) },
    )
    SettingRow(
        stringResource(R.string.points_notify), icon = LucideIcons.Bell,
        trailing = SettingTrailing.Toggle(sheet.sheetData.notifyPoints) { sheet.setNotifyPoints(it) }, onClick = { sheet.setNotifyPoints(!sheet.sheetData.notifyPoints) },
    )
}

/** A choice with its share as a bar behind the text; the leader in teal, the winner with a trophy. */
@Composable
private fun ShareRow(title: String, percent: Int, detail: String, lead: Boolean, winner: Boolean) {
    val colors = Lagoon.colors
    val bar = if (lead || winner) colors.accentSoft else colors.control
    val fraction = (percent / 100f).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 52.dp).clip(LagoonShapes.row).background(colors.card)
            .drawBehind { drawRect(bar, size = Size(size.width * fraction, size.height)) },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (winner) {
                Icon(LucideIcons.Trophy, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = Lagoon.type.bodyStrong, color = colors.textPrimary)
                Text(detail, style = Lagoon.type.caption.copy(fontWeight = null), color = colors.textSecondary)
            }
            Text("$percent%", style = Lagoon.type.bodyStrong, color = if (lead || winner) colors.accent else colors.textSecondary)
        }
    }
}

@Composable
private fun SheetHeader(title: String?, status: String) {
    val colors = Lagoon.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
        title?.let { Text(it, style = Lagoon.type.bodyStrong, color = colors.textPrimary) }
        Text(status, style = Lagoon.type.label, color = colors.textSecondary)
    }
}

@Composable
private fun PollSheet(poll: Poll, secondsLeft: Int?) {
    SheetHeader(poll.title, ChatEvents.pollStatus(poll, secondsLeft))
    val total = (poll.totalVotes ?: 0).toLong()
    val max = poll.choices?.maxOfOrNull { it.totalVotes ?: 0 } ?: 0
    val ended = poll.status == "COMPLETED" || poll.status == "TERMINATED"
    poll.choices?.forEach { c ->
        val votes = c.totalVotes ?: 0
        ShareRow(
            c.title ?: "", ChatEvents.percent(votes.toLong(), total),
            pluralStringResource(R.plurals.cht_votes, votes, NumberFormat.getInstance().format(votes)),
            lead = !ended && votes == max && max > 0, winner = ended && votes == max && max > 0,
        )
    }
}

@Composable
private fun PredictionSheet(p: Prediction, secondsLeft: Int?) {
    SheetHeader(p.title, ChatEvents.predictionStatus(p, secondsLeft))
    val total = p.outcomes?.sumOf { (it.totalPoints ?: 0).toLong() } ?: 0L
    val resolved = p.status == "RESOLVED" || p.status == "RESOLVE_PENDING"
    val max = p.outcomes?.maxOfOrNull { it.totalPoints ?: 0 } ?: 0
    p.outcomes?.forEach { o ->
        val pts = o.totalPoints ?: 0
        ShareRow(
            o.title ?: "", ChatEvents.percent(pts.toLong(), total),
            stringResource(R.string.cht_prediction_detail, NumberFormat.getInstance().format(pts), NumberFormat.getInstance().format(o.totalUsers ?: 0)),
            lead = !resolved && pts == max && max > 0, winner = resolved && o.id != null && o.id == p.winningOutcomeId,
        )
    }
}
