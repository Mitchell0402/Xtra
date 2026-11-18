package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.IntOffset
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonColors
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.Chatter
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.Poll
import com.github.andreyasadchy.xtra.model.chat.Prediction
import com.github.andreyasadchy.xtra.model.chat.Raid
import com.github.andreyasadchy.xtra.model.chat.RoomState
import com.github.andreyasadchy.xtra.ui.design.components.Avatar
import com.github.andreyasadchy.xtra.ui.design.components.EmptyState
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlinx.coroutines.flow.distinctUntilChanged

/** Chat modes of the room (IRC ROOMSTATE), merged as partial updates arrive. */
@Immutable
data class RoomModes(
    val emoteOnly: Boolean = false,
    /** -1 off, 0 any follower, otherwise minutes followed. */
    val followersMinutes: Int = -1,
    val unique: Boolean = false,
    val slowSeconds: Int = 0,
    val subsOnly: Boolean = false,
) {
    val any get() = emoteOnly || followersMinutes >= 0 || unique || slowSeconds > 0 || subsOnly

    fun merge(r: RoomState) = RoomModes(
        emoteOnly = r.emote?.let { it == "1" } ?: emoteOnly,
        followersMinutes = r.followers?.toIntOrNull() ?: followersMinutes,
        unique = r.unique?.let { it == "1" } ?: unique,
        slowSeconds = r.slow?.toIntOrNull() ?: slowSeconds,
        subsOnly = r.subs?.let { it == "1" } ?: subsOnly,
    )
}

/** The viewer's channel points in this channel. */
@Immutable
data class ChannelPoints(
    val balance: Int,
    val claimAvailable: Boolean = false,
    val autoClaim: Boolean = true,
    /** The channel's own name and picture for its points, when it set them. */
    val name: String? = null,
    val iconUrl: String? = null,
    val lastEarned: Int? = null,
)

@Immutable
data class ReplyTarget(val id: String, val userLogin: String?, val userName: String?, val message: String?)

sealed interface ChatSheet {
    data class UserCard(val message: ChatMessage) : ChatSheet
    data class Thread(val message: ChatMessage) : ChatSheet
    data class EmoteCard(val info: EmoteInfo) : ChatSheet
    data object Points : ChatSheet
    data object Poll : ChatSheet
    data object Prediction : ChatSheet
}

/** A suggestion while typing: an emote after ":", a chatter after "@" (v0.1's auto-complete list). */
@Immutable
class Suggestion(val text: String, val emote: Emote?)

/** Everything the chat page shows; written by [ChatFragment], read by the composables. */
@Stable
class ChatState {
    /** Oldest first. */
    val lines = mutableStateListOf<ChatLine>()
    var style by mutableStateOf(ChatStyle())
    var disabled by mutableStateOf(false)
    var replayUnavailable by mutableStateOf(false)
    var isLive by mutableStateOf(false)
    var messagingEnabled by mutableStateOf(false)
    var chatBarVisible by mutableStateOf(true)
    var modes by mutableStateOf(RoomModes())
    var raid by mutableStateOf<Raid?>(null)
    var raidImage by mutableStateOf<String?>(null)
    var poll by mutableStateOf<Poll?>(null)
    var pollSecondsLeft by mutableStateOf<Int?>(null)
    var prediction by mutableStateOf<Prediction?>(null)
    var predictionSecondsLeft by mutableStateOf<Int?>(null)
    var points by mutableStateOf<ChannelPoints?>(null)
    var input by mutableStateOf(TextFieldValue(""))
    var reply by mutableStateOf<ReplyTarget?>(null)
    var emotePanel by mutableStateOf(false)
    var sheet by mutableStateOf<ChatSheet?>(null)
    var keyboardHeight by mutableIntStateOf(0)
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
    var emoteSections by mutableStateOf<List<EmoteSection>>(emptyList())
    /** Many messages a second: new lines appear without the slide-in (plan 5). */
    var fast by mutableStateOf(false)
    /** Bumped to ask the list to jump to the newest line (after sending). */
    var scrollToLatest by mutableIntStateOf(0)
    /** Bumped to put the cursor in the input and show the keyboard (reply, mention). */
    var focusRequests by mutableIntStateOf(0)
    var nameDisplay: String? = "0"
}

interface ChatActions {
    fun openUserCard(message: ChatMessage)
    fun send()
    fun onInputChange(value: TextFieldValue)
    fun deleteWord()
    fun clearInput()
    fun cancelReply()
    fun toggleEmotePanel()
    fun onInputFocused()
    fun insert(text: String)
    fun applySuggestion(s: Suggestion)
    fun openPoints()
    fun claimPoints()
    fun openRaid()
    fun closeRaid()
    fun closePoll()
    fun closePrediction()
    fun closeSheet()
    fun openSheet(sheet: ChatSheet)
}

fun ChatHighlight.tint(colors: LagoonColors): Color? = when (this) {
    ChatHighlight.None -> null
    ChatHighlight.First -> colors.chatFirst
    ChatHighlight.Reward -> colors.chatReward
    ChatHighlight.Notice -> colors.chatNotice
    ChatHighlight.Mention -> colors.chatMention
}

/** The chat page. [sheets] off draws no sheet windows (tests draw the sheets in place). */
@Composable
fun ChatScreen(state: ChatState, actions: ChatActions, modifier: Modifier = Modifier, sheets: Boolean = true) {
    // no background of its own: the player's container is the page colour, or see-through when the chat lies over the video
    Column(modifier.fillMaxSize()) {
        if (state.disabled) return@Column
        ChatEventStrip(state, actions)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.replayUnavailable) {
                EmptyState(stringResource(R.string.chat_replay_unavailable), Modifier.align(Alignment.Center), icon = LucideIcons.MessageSquare)
            } else {
                ChatList(state, actions)
            }
        }
        ChatBottom(state, actions)
    }
    if (sheets) ChatSheets(state, actions)
}

// ---- list -----------------------------------------------------------------------------------------------------------

@Composable
fun ChatList(state: ChatState, actions: ChatActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 8 } }
    var unseen by remember { mutableIntStateOf(0) }
    val lines = state.lines
    // newest key: when it changes while the list rests at the bottom, keep showing the newest line
    LaunchedEffect(listState) {
        snapshotFlow { lines.lastOrNull()?.key }.distinctUntilChanged().collect {
            if (atBottom || (!listState.isScrollInProgress && listState.firstVisibleItemIndex <= 1)) {
                listState.requestScrollToItem(0)
                unseen = 0
            } else {
                unseen++
            }
        }
    }
    LaunchedEffect(state.scrollToLatest) {
        if (state.scrollToLatest > 0) listState.scrollToItem(0)
    }
    LaunchedEffect(atBottom) { if (atBottom) unseen = 0 }
    val reduced = Lagoon.motion.reduced
    val listFade = Lagoon.motion.listItemFadeIn<Float>()
    val listPlacement = Lagoon.motion.listItemPlacement(IntOffset.VisibilityThreshold)
    // U9 C1 (D2): the name opens the user card (a reply header's "@name": the replied-to user), an emote or a badge
    // with details opens its card, the rest of a line does nothing. One remembered callback each, taking the line,
    // so rows do not rebuild their picture maps on every recomposition.
    val onNameClick = remember(actions, lines) { { line: ChatLine -> actions.openUserCard(if (line.isReplyHeader) repliedTo(line.message, lines) else line.message) } }
    val onImageClick = remember(actions) { { image: ChatImage -> image.info?.let { actions.openSheet(ChatSheet.EmoteCard(it)) }; Unit } }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize().testTag("chatList").semantics { contentDescription = "chat" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
        ) {
            items(count = lines.size, key = { lines[lines.size - 1 - it].key }, contentType = { if (lines[lines.size - 1 - it].isReplyHeader) 1 else 0 }) { index ->
                val line = lines[lines.size - 1 - index]
                val animate = !state.fast && !reduced
                ChatRow(
                    line, state.style,
                    onClick = null,
                    onNameClick = onNameClick,
                    onImageClick = onImageClick,
                    modifier = if (animate) Modifier.animateItem(fadeInSpec = listFade, placementSpec = listPlacement, fadeOutSpec = null) else Modifier,
                )
            }
        }
        AnimatedVisibility(
            visible = !atBottom && lines.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            enter = fadeIn(Lagoon.motion.fade()) + slideInVertically(Lagoon.motion.move(200)) { it },
            exit = fadeOut(Lagoon.motion.fadeExit()) + slideOutVertically(Lagoon.motion.move(200)) { it },
        ) {
            JumpToLatest(unseen) { state.scrollToLatest++ }
        }
    }
}

@Composable
private fun JumpToLatest(unseen: Int, onClick: () -> Unit) {
    val colors = Lagoon.colors
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .pressable(onClick = onClick, shape = LagoonShapes.pill)
            .background(colors.control, LagoonShapes.pill)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(LucideIcons.ArrowDown, null, tint = colors.accent, modifier = Modifier.size(16.dp))
        Text(
            if (unseen > 0) pluralStringResource(R.plurals.cht_new_messages, unseen, unseen) else stringResource(R.string.cht_latest),
            style = Lagoon.type.labelStrong, color = colors.textPrimary,
        )
    }
}

/**
 * The message a reply header's "@name" stands for: that user's latest line still in the chat, or one made from the
 * reply data when it scrolled away (the user card only needs the login).
 */
internal fun repliedTo(header: ChatMessage, lines: List<ChatLine>): ChatMessage {
    val reply = header.reply ?: return header.replyParent ?: header
    return lines.lastOrNull { !it.isReplyHeader && it.message.userLogin != null && it.message.userLogin.equals(reply.userLogin, ignoreCase = true) }?.message
        ?: ChatMessage(id = reply.threadParentId, userLogin = reply.userLogin, userName = reply.userName, message = reply.message, type = ChatMessage.USER_MESSAGE)
}

/** One chat line: no avatar, even spacing, a soft tint and a bar on the left for mentions, first messages, rewards and notices (plan 7.5). */
@Composable
fun ChatRow(
    line: ChatLine,
    style: ChatStyle,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onImageClick: ((ChatImage) -> Unit)? = null,
    selected: Boolean = false,
    onNameClick: ((ChatLine) -> Unit)? = null,
) {
    val colors = Lagoon.colors
    val tint = line.highlight.tint(colors)
    val rowModifier = modifier
        .fillMaxWidth()
        .then(
            when {
                selected -> Modifier.background(colors.accentSoft)
                tint != null -> Modifier.drawBehind {
                    drawRect(tint.copy(alpha = 0.10f))
                    drawRect(tint.copy(alpha = 0.85f), size = Size(3.dp.toPx(), size.height))
                }
                else -> Modifier
            }
        )
        .then(if (onClick != null) Modifier.clickable(interactionSource = null, indication = ripple(), onClick = onClick) else Modifier)
        .then(if (onNameClick != null || onImageClick != null) Modifier.chatRowActions(line, onNameClick, onImageClick) else Modifier)
        .padding(start = 12.dp, end = 12.dp, top = 3.dp, bottom = 3.dp)
    if (line.isReplyHeader) {
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(LucideIcons.Reply, null, tint = ChatLineBuilder.Muted, modifier = Modifier.size(13.dp))
            ChatText(line, style, onImageClick, maxLines = 1, scale = 0.86f, onNameClick = onNameClick)
        }
    } else {
        Box(rowModifier) { ChatText(line, style, onImageClick, onNameClick = onNameClick) }
    }
}

/**
 * Screen-reader way in now that a line has no row tap (U9 C1): "Open X's user card" for a line with a name, "View
 * emote Y" for its emotes and detailed badges (five at most, so the actions menu stays short).
 */
@Composable
private fun Modifier.chatRowActions(line: ChatLine, onNameClick: ((ChatLine) -> Unit)?, onImageClick: ((ChatImage) -> Unit)?): Modifier {
    val name = if (onNameClick != null && line.nameStart >= 0 && line.nameEnd <= line.text.length) line.text.text.substring(line.nameStart, line.nameEnd).removePrefix("@") else null
    val userLabel = name?.let { stringResource(R.string.cht_action_user_card, it) }
    val images = if (onImageClick != null) line.images.filter { it.info != null }.take(5) else emptyList()
    val emoteLabels = images.map { stringResource(R.string.cht_action_emote, it.info?.name ?: "") }
    if (userLabel == null && images.isEmpty()) return this
    return semantics {
        customActions = buildList {
            if (userLabel != null) add(CustomAccessibilityAction(userLabel) { onNameClick?.invoke(line); true })
            images.forEachIndexed { i, image -> add(CustomAccessibilityAction(emoteLabels[i]) { onImageClick?.invoke(image); true }) }
        }
    }
}

@Composable
fun ChatText(
    line: ChatLine,
    style: ChatStyle,
    onImageClick: ((ChatImage) -> Unit)?,
    maxLines: Int = Int.MAX_VALUE,
    scale: Float = 1f,
    onNameClick: ((ChatLine) -> Unit)? = null,
) {
    val density = LocalDensity.current
    // the learned picture ratios are read here, so a line only rebuilds its placeholders when one of its own pictures widens
    val ratios = line.images.map { ChatImageRatios.of(it.model) + (it.overlays.maxOfOrNull { o -> ChatImageRatios.of(o.model) } ?: 0f) }
    val inline = remember(line, style, density, ratios, onImageClick) { inlineContentFor(line.images, style, density, onImageClick) }
    val text = NamePaints.apply(line, style)
    val primary = Lagoon.colors.textPrimary
    val textStyle = remember(style.textSize, scale, primary) {
        val size = style.textSize * scale
        TextStyle(fontSize = size.sp, lineHeight = (size * 1.45f).sp, color = primary)
    }
    val nameClickable = onNameClick != null && line.nameStart >= 0 && line.nameEnd > line.nameStart && line.nameEnd <= line.text.length
    // the last layout, for turning a tap position into a character (kept out of state: it only feeds the tap)
    val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
    var namePressed by remember { mutableStateOf(false) }
    val pressColor = Lagoon.colors.accentSoft
    Text(
        text = text,
        inlineContent = inline,
        style = textStyle,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        onTextLayout = { layout[0] = it },
        modifier = if (!nameClickable) Modifier else Modifier
            .drawBehind {
                if (namePressed) layout[0]?.let { drawPath(it.getPathForRange(line.nameStart, line.nameEnd), pressColor) }
            }
            .pointerInput(line.key, onNameClick) {
                fun onName(p: Offset): Boolean {
                    val l = layout[0] ?: return false
                    val box = l.getBoundingBox(l.getOffsetForPosition(p).coerceIn(line.nameStart, line.nameEnd - 1))
                    return l.getOffsetForPosition(p) in line.nameStart until line.nameEnd && box.inflate(4f).contains(p)
                }
                detectTapGestures(
                    onPress = { p ->
                        if (onName(p)) {
                            namePressed = true
                            tryAwaitRelease()
                            namePressed = false
                        }
                    },
                    onTap = { p -> if (onName(p)) onNameClick?.invoke(line) },
                )
            },
    )
}

// ---- events: raid, poll, prediction --------------------------------------------------------------------------------

/** The slim bars under the video (plan 7.4): raid, poll and prediction, each one line; polls and predictions open a sheet. */
@Composable
fun ChatEventStrip(state: ChatState, actions: ChatActions) {
    val raid = state.raid
    val poll = state.poll
    val prediction = state.prediction
    if (raid == null && poll == null && prediction == null) return
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (raid != null) {
            val name = remember(raid, state.nameDisplay) { ChatEvents.displayName(raid.targetName, raid.targetLogin, state.nameDisplay) }
            EventBar(
                leading = { Avatar(state.raidImage, raid.targetName ?: raid.targetLogin ?: "", size = 26.dp) },
                title = stringResource(R.string.cht_raid_title, name ?: ""),
                detail = pluralStringResource(R.plurals.cht_raid_viewers, raid.viewerCount ?: 0, raid.viewerCount ?: 0),
                onClick = actions::openRaid,
                onClose = actions::closeRaid,
                trailingIcon = LucideIcons.ArrowRight,
            )
        }
        if (poll != null) {
            EventBar(
                leading = { EventIcon(LucideIcons.ChartColumn) },
                title = poll.title ?: stringResource(R.string.cht_poll),
                detail = ChatEvents.pollStatus(poll, state.pollSecondsLeft),
                onClick = { actions.openSheet(ChatSheet.Poll) },
                onClose = actions::closePoll,
            )
        }
        if (prediction != null) {
            EventBar(
                leading = { EventIcon(LucideIcons.Trophy) },
                title = prediction.title ?: stringResource(R.string.cht_prediction),
                detail = ChatEvents.predictionStatus(prediction, state.predictionSecondsLeft),
                onClick = { actions.openSheet(ChatSheet.Prediction) },
                onClose = actions::closePrediction,
            )
        }
    }
}

@Composable
private fun EventIcon(icon: ImageVector) {
    val colors = Lagoon.colors
    Box(Modifier.size(26.dp).background(colors.accentSoft, LagoonShapes.pill), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun EventBar(
    leading: @Composable () -> Unit,
    title: String,
    detail: String?,
    onClick: () -> Unit,
    onClose: () -> Unit,
    trailingIcon: ImageVector = LucideIcons.ChevronRight,
) {
    val colors = Lagoon.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(LagoonShapes.row)
            .background(colors.card)
            .pressable(onClick = onClick, shape = LagoonShapes.row, scaleOnPress = false)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading()
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, style = Lagoon.type.labelStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = Lagoon.type.caption.copy(fontWeight = null), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(trailingIcon, null, tint = colors.textHint, modifier = Modifier.size(16.dp))
        LagoonIconButton(LucideIcons.X, stringResource(R.string.cht_close), onClose, filled = false, tint = colors.textSecondary, size = 36.dp)
    }
}

// ---- bottom: modes, reply, suggestions, input, emote panel -----------------------------------------------------------

@Composable
fun ChatBottom(state: ChatState, actions: ChatActions) {
    val showInput = state.messagingEnabled && state.chatBarVisible
    if (state.modes.any) RoomModesRow(state.modes)
    if (!showInput) return
    AnimatedVisibility(state.reply != null, enter = Lagoon.motion.expand(), exit = Lagoon.motion.collapse()) {
        state.reply?.let { ReplyBar(it, state.nameDisplay, actions) }
    }
    if (state.suggestions.isNotEmpty()) SuggestionRow(state, actions)
    InputBar(state, actions)
    if (state.emotePanel) {
        val minHeight = 260.dp
        val keyboard = with(LocalDensity.current) { state.keyboardHeight.toDp() }
        EmotePicker(state, actions, Modifier.fillMaxWidth().height(maxOf(minHeight, keyboard).coerceAtMost(360.dp)))
    }
}

/** Slow mode, followers-only and the other room modes as small chips that stay while they apply. */
@Composable
fun RoomModesRow(modes: RoomModes) {
    val context = LocalContext.current
    val chips = buildList {
        if (modes.slowSeconds > 0) add(LucideIcons.Hourglass to stringResource(R.string.cht_mode_slow, TwitchApiHelper.getDurationFromSeconds(context, modes.slowSeconds.toString()) ?: "${modes.slowSeconds}s"))
        if (modes.followersMinutes == 0) add(LucideIcons.UserCheck to stringResource(R.string.cht_mode_followers))
        if (modes.followersMinutes > 0) add(LucideIcons.UserCheck to stringResource(R.string.cht_mode_followers_min, TwitchApiHelper.getDurationFromSeconds(context, (modes.followersMinutes * 60).toString()) ?: "${modes.followersMinutes}m"))
        if (modes.subsOnly) add(LucideIcons.Crown to stringResource(R.string.cht_mode_subs))
        if (modes.emoteOnly) add(LucideIcons.Smile to stringResource(R.string.cht_mode_emotes))
        if (modes.unique) add(LucideIcons.Fingerprint to stringResource(R.string.cht_mode_unique))
    }
    val colors = Lagoon.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp).testTag("roomModes"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { (icon, label) ->
            Row(
                Modifier.background(colors.card, LagoonShapes.pill).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                Text(label, style = Lagoon.type.caption.copy(fontWeight = null), color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ReplyBar(reply: ReplyTarget, nameDisplay: String?, actions: ChatActions) {
    val colors = Lagoon.colors
    val name = ChatEvents.displayName(reply.userName, reply.userLogin, nameDisplay) ?: ""
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(LucideIcons.Reply, null, tint = colors.accent, modifier = Modifier.size(15.dp))
        Text(
            stringResource(R.string.cht_replying, name, reply.message ?: ""),
            style = Lagoon.type.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        LagoonIconButton(LucideIcons.X, stringResource(R.string.cht_cancel_reply), actions::cancelReply, filled = false, tint = colors.textSecondary, size = 36.dp)
    }
}

@Composable
private fun SuggestionRow(state: ChatState, actions: ChatActions) {
    val colors = Lagoon.colors
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("chatSuggestions"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(state.suggestions, key = { it.text }) { s ->
            Row(
                Modifier
                    .minimumInteractiveComponentSize()
                    .heightIn(min = 36.dp)
                    .pressable(onClick = { actions.applySuggestion(s) }, shape = LagoonShapes.pill)
                    .background(colors.card, LagoonShapes.pill)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val emote = s.emote
                if (emote != null) {
                    val url = emote.url2x ?: emote.url1x ?: emote.url3x ?: emote.url4x
                    if (url != null) ChatPicture(ChatImage("s", url, ChatImageKind.Emote, false, emote.thirdParty), animate = false, onClick = null, modifier = Modifier.size(24.dp))
                }
                Text(s.text, style = Lagoon.type.label, color = colors.textPrimary, maxLines = 1)
            }
        }
    }
}

/** The input pill (plan 7.5): emotes on the left, the channel points and send on the right. */
@Composable
fun InputBar(state: ChatState, actions: ChatActions) {
    val colors = Lagoon.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val hasText = state.input.text.isNotBlank()
    LaunchedEffect(state.focusRequests) {
        if (state.focusRequests > 0) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .background(colors.card, RoundedPill)
                .padding(start = 2.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LagoonIconButton(
                if (state.emotePanel) LucideIcons.Keyboard else LucideIcons.Smile,
                stringResource(if (state.emotePanel) R.string.cht_keyboard else R.string.cht_emotes),
                onClick = {
                    if (state.emotePanel) {
                        actions.onInputFocused()
                        focus.requestFocus()
                        keyboard?.show()
                    } else {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        actions.toggleEmotePanel()
                    }
                },
                filled = false, tint = if (state.emotePanel) colors.accent else colors.textSecondary, size = 40.dp,
            )
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (state.input.text.isEmpty()) {
                    Text(stringResource(R.string.cht_input_hint), style = Lagoon.type.body, color = colors.textHint, maxLines = 1)
                }
                BasicTextField(
                    value = state.input,
                    onValueChange = actions::onInputChange,
                    textStyle = Lagoon.type.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { actions.send() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onFocusChanged { if (it.isFocused) actions.onInputFocused() }
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                                actions.send(); true
                            } else false
                        }
                        .testTag("chatInput"),
                )
            }
            if (hasText) {
                Box(
                    Modifier
                        .size(40.dp)
                        .pressable(onClick = actions::deleteWord, onLongClick = actions::clearInput, shape = LagoonShapes.pill, onLongClickLabel = stringResource(R.string.cht_clear_all)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LucideIcons.Undo2, stringResource(R.string.cht_delete_word), tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
            state.points?.let { PointsChip(it, actions) }
        }
        AnimatedVisibility(hasText, enter = fadeIn(Lagoon.motion.fade()), exit = fadeOut(Lagoon.motion.fadeExit())) {
            Box(
                Modifier
                    .size(44.dp)
                    .pressable(onClick = actions::send, shape = LagoonShapes.pill)
                    .background(colors.accent, LagoonShapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(LucideIcons.SendHorizontal, stringResource(R.string.cht_send), tint = colors.onAccent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private val RoundedPill = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)

@Composable
fun PointsChip(points: ChannelPoints, actions: ChatActions) {
    val colors = Lagoon.colors
    val claim = points.claimAvailable && !points.autoClaim
    val label = if (claim) stringResource(R.string.cht_points_claim) else TwitchApiHelper.formatCount(points.balance, true)
    val description = stringResource(R.string.cht_points_desc, points.balance)
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 32.dp)
            .pressable(onClick = { if (claim) actions.claimPoints() else actions.openPoints() }, shape = LagoonShapes.pill)
            .background(if (claim) colors.accent else colors.control, LagoonShapes.pill)
            .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("pointsChip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (points.iconUrl != null && !claim) {
            ChatPicture(ChatImage("p", points.iconUrl, ChatImageKind.Badge, false, false), animate = false, onClick = null, modifier = Modifier.size(15.dp))
        } else {
            Icon(LucideIcons.Gem, null, tint = if (claim) colors.onAccent else colors.accent, modifier = Modifier.size(15.dp))
        }
        Text(label, style = Lagoon.type.labelStrong, color = if (claim) colors.onAccent else colors.textPrimary, maxLines = 1)
    }
}

/** Text helpers shared by the bars and sheets. */
object ChatEvents {
    fun displayName(name: String?, login: String?, nameDisplay: String?): String? =
        if (name != null && login != null && !login.equals(name, true)) {
            when (nameDisplay) {
                "0" -> "$name($login)"
                "1" -> name
                else -> login
            }
        } else name ?: login

    @Composable
    fun pollStatus(poll: Poll, secondsLeft: Int?): String {
        val votes = pluralStringResource(R.plurals.cht_votes, poll.totalVotes ?: 0, poll.totalVotes ?: 0)
        return when (poll.status) {
            "ACTIVE" -> if (secondsLeft != null && secondsLeft > 0) stringResource(R.string.cht_time_left, android.text.format.DateUtils.formatElapsedTime(secondsLeft.toLong())) + " · " + votes else votes
            else -> {
                val winner = poll.choices?.maxByOrNull { it.totalVotes ?: 0 }?.title
                if (winner != null) stringResource(R.string.cht_poll_winner, winner) else stringResource(R.string.cht_ended)
            }
        }
    }

    @Composable
    fun predictionStatus(p: Prediction, secondsLeft: Int?): String = when (p.status) {
        "ACTIVE" -> if (secondsLeft != null && secondsLeft > 0) stringResource(R.string.cht_time_left, android.text.format.DateUtils.formatElapsedTime(secondsLeft.toLong())) else stringResource(R.string.cht_prediction_open)
        "LOCKED" -> stringResource(R.string.prediction_locked)
        "RESOLVED", "RESOLVE_PENDING" -> p.outcomes?.find { it.id == p.winningOutcomeId }?.title?.let { stringResource(R.string.cht_prediction_winner, it) } ?: stringResource(R.string.cht_ended)
        else -> stringResource(R.string.prediction_refunded)
    }

    fun percent(part: Long, total: Long): Int = if (total <= 0) 0 else ((part * 100.0) / total).let { kotlin.math.round(it).toInt() }
}

/** v0.1's auto-complete rule: a word starting with ":" or "@" matches names that contain its letters in order. */
object ChatSuggestions {
    fun tokenAt(value: TextFieldValue): Pair<Int, String>? {
        val cursor = value.selection.end.coerceIn(0, value.text.length)
        var start = cursor
        while (start > 0 && value.text[start - 1] != ' ') start--
        val token = value.text.substring(start, cursor)
        return if (token.length >= 2 && (token.startsWith(':') || token.startsWith('@'))) start to token else null
    }

    fun find(token: String, candidates: List<Any?>, limit: Int = 30): List<Suggestion> {
        val regex = token.map { "${Regex.escape(it.lowercase())}\\S*?" }.joinToString("").toRegex()
        val out = ArrayList<Suggestion>()
        for (c in candidates) {
            when (c) {
                is Emote -> if (token.startsWith(':') && regex.matches(c.toString().lowercase())) c.name?.let { out.add(Suggestion(it, c)) }
                is Chatter -> if (token.startsWith('@') && regex.matches(c.toString().lowercase())) c.name?.let { out.add(Suggestion("@$it", null)) }
            }
            if (out.size >= limit) break
        }
        return out.distinctBy { it.text }
    }

    /** Replaces the word at the cursor with [text] and a space (v0.1 SpaceTokenizer.terminateToken). */
    fun apply(value: TextFieldValue, text: String): TextFieldValue {
        val (start, _) = tokenAt(value) ?: (value.selection.end to "")
        val end = value.selection.end
        val newText = value.text.substring(0, start) + text + " " + value.text.substring(end)
        val cursor = start + text.length + 1
        return TextFieldValue(newText, TextRange(cursor))
    }

    /** v0.1's clear button: drops the last word. */
    fun deleteWord(text: String): String {
        val trimmed = text.trimEnd()
        return trimmed.substring(0, maxOf(trimmed.lastIndexOf(' '), 0))
    }
}
