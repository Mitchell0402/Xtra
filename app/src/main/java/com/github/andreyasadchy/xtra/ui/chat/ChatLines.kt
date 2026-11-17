package com.github.andreyasadchy.xtra.ui.chat

import com.github.andreyasadchy.xtra.ui.design.theme.DarkLagoonColors
import android.content.Context
import android.content.SharedPreferences
import android.util.Patterns
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.core.graphics.ColorUtils
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.NamePaint
import com.github.andreyasadchy.xtra.model.chat.STVBadge
import com.github.andreyasadchy.xtra.model.chat.STVUser
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import java.text.NumberFormat
import java.util.Random
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.floor
import kotlin.math.pow

/**
 * Chat settings read once per chat (the v0.1 ChatAdapter read the same keys with the same defaults). Sizes are in dp,
 * already multiplied by the size modifier.
 */
@Immutable
data class ChatStyle(
    val textSize: Float = 14f,
    val emoteSize: Float = 29.5f,
    val badgeSize: Float = 18.5f,
    val gifSize: Float = 79.5f,
    val timestamps: Boolean = false,
    val timestampFormat: String? = "0",
    val firstMsgVisibility: Int = 0,
    val randomColors: Boolean = true,
    val readableColors: Boolean = true,
    val nameDisplay: String? = "0",
    val boldNames: Boolean = false,
    val namePaints: Boolean = true,
    val stvBadges: Boolean = true,
    val personalEmotes: Boolean = true,
    val systemMessageEmotes: Boolean = true,
    val gifMessages: Boolean = true,
    val emoteQuality: String = "4",
    val animated: Boolean = true,
    val overlayEmotes: Boolean = true,
    val messageLimit: Int = 600,
) {
    companion object {
        fun from(prefs: SharedPreferences): ChatStyle {
            val modifier = prefs.getInt(C.CHAT_SIZE_MODIFIER, 100) / 100f
            fun size(key: String, default: Float) = (prefs.getString(key, null)?.toFloatOrNull() ?: default) * modifier
            return ChatStyle(
                textSize = size(C.CHAT_TEXT_SIZE, 14f),
                emoteSize = size(C.CHAT_EMOTE_SIZE, 29.5f),
                badgeSize = size(C.CHAT_BADGE_SIZE, 18.5f),
                gifSize = size(C.CHAT_GIF_SIZE, 79.5f),
                timestamps = prefs.getBoolean(C.CHAT_TIMESTAMPS, false),
                timestampFormat = prefs.getString(C.CHAT_TIMESTAMP_FORMAT, "0"),
                firstMsgVisibility = prefs.getString(C.CHAT_FIRST_MSG_VISIBILITY, "0")?.toIntOrNull() ?: 0,
                randomColors = prefs.getBoolean(C.CHAT_RANDOM_COLOR, true),
                readableColors = prefs.getBoolean(C.CHAT_THEME_ADAPTED_USERNAME_COLOR, true),
                nameDisplay = prefs.getString(C.UI_NAME_DISPLAY, "0"),
                boldNames = prefs.getBoolean(C.CHAT_BOLD_NAMES, false),
                namePaints = prefs.getBoolean(C.CHAT_SHOW_PAINTS, true),
                stvBadges = prefs.getBoolean(C.CHAT_SHOW_STV_BADGES, true),
                personalEmotes = prefs.getBoolean(C.CHAT_SHOW_PERSONAL_EMOTES, true),
                systemMessageEmotes = prefs.getBoolean(C.CHAT_SYSTEM_MESSAGE_EMOTES, true),
                gifMessages = prefs.getBoolean(C.CHAT_SHOW_GIF_MESSAGES, true),
                emoteQuality = prefs.getString(C.CHAT_IMAGE_QUALITY, "4") ?: "4",
                animated = prefs.getBoolean(C.ANIMATED_EMOTES, true),
                overlayEmotes = prefs.getBoolean(C.CHAT_ZERO_WIDTH, true),
                messageLimit = prefs.getInt(C.CHAT_LIMIT, 600).coerceAtLeast(1),
            )
        }
    }
}

/** The fixed texts the builder needs, read from resources once so the builder itself stays free of Context. */
class ChatStrings(
    val firstChat: String,
    val redeemed: String,
    val redeemedNoMessage: String,
    val reward: String,
    val replyingTo: String,
    val msgIdLabel: (String) -> String?,
) {
    companion object {
        fun from(context: Context) = ChatStrings(
            firstChat = context.getString(R.string.chat_first),
            redeemed = context.getString(R.string.redeemed),
            redeemedNoMessage = context.getString(R.string.user_redeemed),
            reward = context.getString(R.string.chat_reward),
            replyingTo = context.getString(R.string.replying_to_message),
            msgIdLabel = { TwitchApiHelper.getMessageIdString(context, it) },
        )

        /** For tests and previews. */
        val Plain = ChatStrings("First message", "Redeemed %s", "%1\$s redeemed %2\$s", "Reward message", "Replying to @%1\$s: %2\$s", { null })
    }
}

enum class ChatHighlight { None, First, Reward, Notice, Mention }

enum class ChatImageKind { Badge, Emote, Gif }

/** One picture inside a line: a badge, an emote (with the zero-width emotes stacked on it) or a GIF message. */
@Immutable
class ChatImage(
    val id: String,
    /** A URL, or the bytes of an emote saved with a downloaded video. */
    val model: Any,
    val kind: ChatImageKind,
    val animated: Boolean,
    val thirdParty: Boolean,
    val overlays: List<ChatImage> = emptyList(),
    /** What the emote card shows when the picture is tapped (in the user card; the main list opens the user card). */
    val info: EmoteInfo? = null,
)

@Immutable
class EmoteInfo(
    val url: String?,
    val name: String?,
    val format: String?,
    val animated: Boolean?,
    val source: Int?,
    val thirdParty: Boolean?,
    val emoteId: String?,
)

/** A message ready to draw: text with placeholders, the pictures behind them, and its background. */
@Immutable
class ChatLine(
    val key: Long,
    val message: ChatMessage,
    val text: AnnotatedString,
    val images: List<ChatImage>,
    val highlight: ChatHighlight,
    /** The small "replying to" line the chat puts above a reply (v0.1 REPLY_MESSAGE). */
    val isReplyHeader: Boolean,
    /** The 7TV paint of the name ([nameStart] to [nameEnd]); applied when the line is drawn, which knows the text size in pixels. */
    val namePaint: NamePaint? = null,
    val nameStart: Int = -1,
    val nameEnd: Int = -1,
)

/**
 * The emotes, badges and 7TV data a line is built from. The big lists are copied into maps (v0.1 searched every list
 * for every word); the 7TV lists change while the chat runs and are read under their locks like v0.1 did.
 */
class ChatAssets(
    val localTwitchEmotes: Map<String, TwitchEmote> = emptyMap(),
    val thirdPartyEmotes: Map<String, Emote> = emptyMap(),
    val badges: Map<String, TwitchBadge> = emptyMap(),
    val cheerEmotes: List<CheerEmote> = emptyList(),
    val stvUsers: List<STVUser> = emptyList(),
    val stvBadges: List<STVBadge> = emptyList(),
    val namePaints: List<NamePaint> = emptyList(),
    val personalEmoteSets: Map<String, List<Emote>> = emptyMap(),
) {
    companion object {
        /** Channel badges win over global ones with the same set and version (v0.1 searched the channel list first). */
        fun of(
            localTwitchEmotes: List<TwitchEmote>, thirdPartyEmotes: List<Emote>, globalBadges: List<TwitchBadge>, channelBadges: List<TwitchBadge>,
            cheerEmotes: List<CheerEmote>, stvUsers: List<STVUser>, stvBadges: List<STVBadge>, namePaints: List<NamePaint>,
            personalEmoteSets: Map<String, List<Emote>>,
        ): ChatAssets {
            val badges = HashMap<String, TwitchBadge>()
            synchronized(globalBadges) { globalBadges.forEach { badges[it.setId + "/" + it.version] = it } }
            synchronized(channelBadges) { channelBadges.forEach { badges[it.setId + "/" + it.version] = it } }
            val emotes = HashMap<String, Emote>()
            // v0.1 took the first match in list order
            synchronized(thirdPartyEmotes) { thirdPartyEmotes.forEach { e -> e.name?.let { emotes.putIfAbsent(it, e) } } }
            return ChatAssets(
                localTwitchEmotes = synchronized(localTwitchEmotes) { localTwitchEmotes.mapNotNull { e -> e.id?.let { it to e } }.toMap() },
                thirdPartyEmotes = emotes,
                badges = badges,
                cheerEmotes = synchronized(cheerEmotes) { cheerEmotes.toList() },
                stvUsers = stvUsers,
                stvBadges = stvBadges,
                namePaints = namePaints,
                personalEmoteSets = personalEmoteSets,
            )
        }
    }
}

/** Twitch-style name colours, readable on the dark chat background. */
object ChatColors {
    val Background = 0xFF111113.toInt()
    private val twitchColors = intArrayOf(-65536, -16776961, -16744448, -5103070, -32944, -6632142, -47872, -13726889, -2448096, -2987746, -10510688, -14774017, -38476, -7722014, -16711809)
    const val GREY = -10066329

    fun random(random: Random) = twitchColors[random.nextInt(twitchColors.size)]

    /**
     * v0.1's hue-aware lightening for dark themes, then lightened further until the colour reaches 4.5:1 against the
     * chat background (plan 10.5: user names must reach it after the adjustment).
     */
    fun readable(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        val distToRed = 0f - hsl[0]
        val distToBlue = 240f - hsl[0]
        val normRed = distToRed - 360f * floor((distToRed + 180f) / 360f)
        val normBlue = distToBlue - 360f * floor((distToBlue + 180f) / 360f)
        val min = 0.3f + maxOf((1f - (normBlue / 40f).pow(2f)) * 0.35f, 0f) + maxOf((1f - (normRed / 40f).pow(2f)) * 0.1f, 0f)
        hsl[2] = maxOf(hsl[2], min)
        var c = ColorUtils.HSLToColor(hsl) or (0xFF shl 24)
        while (ColorUtils.calculateContrast(c, Background) < 4.5 && hsl[2] < 1f) {
            hsl[2] = minOf(1f, hsl[2] + 0.04f)
            c = ColorUtils.HSLToColor(hsl) or (0xFF shl 24)
        }
        return c
    }

    fun parse(color: String): Int? = runCatching { android.graphics.Color.parseColor(color) }.getOrNull()
}

/**
 * Turns a [ChatMessage] into a [ChatLine]. A port of v0.1's ChatAdapterUtils.prepareChatMessage (same order of
 * pieces, same rules for names, badges, emotes, bits, zero-width emotes, links and mentions), building a Compose
 * AnnotatedString instead of a Spannable. Translation is gone (plan 12). Not thread-safe: one builder per thread.
 */
class ChatLineBuilder(
    private val style: ChatStyle,
    private val strings: ChatStrings,
    @Volatile var assets: ChatAssets,
    private val loggedInUser: String?,
    private val chatUrl: String? = null,
    private val getEmoteBytes: ((String, Pair<Long, Int>) -> ByteArray?)? = null,
    private val random: Random = Random(),
    private val keys: AtomicLong = AtomicLong(),
) {
    private val userColors = HashMap<String, Int>()
    private val savedColors = HashMap<String, Int>()
    private val savedLocal = LinkedHashMap<String, ByteArray>()
    private var imageCount = 0

    fun nextKey() = keys.incrementAndGet()

    fun build(message: ChatMessage, key: Long = nextKey()): ChatLine {
        imageCount = 0
        val images = ArrayList<ChatImage>()
        var highlight = ChatHighlight.None
        var namePaint: NamePaint? = null
        var nameStart = -1
        var nameEnd = -1
        val b = AnnotatedString.Builder()
        when {
            message.type == ChatMessage.REPLY_MESSAGE -> {
                val name = displayName(message.reply?.userName, message.reply?.userLogin)
                b.withStyle(SpanStyle(color = Muted)) {
                    val header = strings.replyingTo.format(name, "")
                    // U9 C1: only the "@name" opens the replied-to user's card
                    nameRange(header, name)?.let { (s, e) -> nameStart = b.length + s; nameEnd = b.length + e }
                    append(header)
                    message.reply?.message?.let { appendBody(this, it, message, null, images) }
                    Unit
                }
            }
            message.message.isNullOrBlank() && (message.systemMsg != null || message.reward?.title != null) -> {
                appendTimestamp(b, message)
                if (message.systemMsg != null) {
                    b.withStyle(SpanStyle(color = Muted)) {
                        if (style.systemMessageEmotes) appendBody(this, message.systemMsg, message, null, images) else append(message.systemMsg)
                        Unit
                    }
                } else if (message.reward?.title != null) {
                    val who = displayName(message.userName, message.userLogin)
                    val text = strings.redeemedNoMessage.format(who, message.reward.title)
                    nameRange(text, who)?.let { (s, e) -> nameStart = b.length + s; nameEnd = b.length + e }
                    b.withStyle(SpanStyle(color = Muted)) {
                        if (style.systemMessageEmotes) appendBody(this, text, message, null, images) else append(text)
                        append(" ")
                    }
                    appendReward(b, message, images, Muted)
                }
            }
            else -> {
                if (message.systemMsg != null) {
                    b.append(message.systemMsg + "\n")
                } else if (message.msgId != null) {
                    b.append((strings.msgIdLabel(message.msgId) ?: message.msgId) + "\n")
                }
                if (message.isFirst && style.firstMsgVisibility == 0) b.append(strings.firstChat + "\n")
                if (message.reward?.title != null) {
                    b.append(strings.redeemed.format(message.reward.title) + " ")
                    appendReward(b, message, images, null)
                    b.append("\n")
                } else if (message.reward?.id != null && style.firstMsgVisibility == 0) {
                    b.append(strings.reward + "\n")
                }
                appendTimestamp(b, message)
                message.badges?.forEach { chatBadge ->
                    val badge = assets.badges[chatBadge.setId + "/" + chatBadge.version] ?: return@forEach
                    val model = badge.localData?.let { local(badge.setId + badge.version, it) }
                        ?: (badge.url4x ?: badge.url3x ?: badge.url2x ?: badge.url1x)?.let { pick(badge.url1x, badge.url2x, badge.url3x, badge.url4x) }
                    if (model != null) {
                        addImage(b, images, ChatImage(newId(), model, ChatImageKind.Badge, animated = false, thirdParty = false,
                            info = EmoteInfo(badge.url4x ?: badge.url3x ?: badge.url2x ?: badge.url1x, badge.title, null, null, null, null, null)), badge.title)
                        b.append(" ")
                    }
                }
                val stvUser = if ((style.stvBadges || style.namePaints || style.personalEmotes) && !message.userId.isNullOrBlank()) {
                    synchronized(assets.stvUsers) { assets.stvUsers.find { it.userId == message.userId } }
                } else null
                if (style.stvBadges && stvUser?.badgeId != null) {
                    val badge = synchronized(assets.stvBadges) { assets.stvBadges.find { it.id == stvUser.badgeId } }
                    if (badge != null) {
                        pick(badge.url1x, badge.url2x, badge.url3x, badge.url4x)?.let { url ->
                            addImage(b, images, ChatImage(newId(), url, ChatImageKind.Badge, animated = true, thirdParty = true,
                                info = EmoteInfo(badge.url4x ?: badge.url3x ?: badge.url2x ?: badge.url1x, badge.name, badge.format, true, null, true, null)), badge.name)
                            b.append(" ")
                        }
                    }
                }
                val color = nameColor(message)
                if (!message.userName.isNullOrBlank()) {
                    val name = displayName(message.userName, message.userLogin) ?: message.userName
                    nameStart = b.length
                    val paint = if (style.namePaints && stvUser?.paintId != null) {
                        synchronized(assets.namePaints) { assets.namePaints.find { it.id == stvUser.paintId } }
                    } else null
                    b.withStyle(SpanStyle(color = Color(color), fontWeight = if (style.boldNames) FontWeight.Bold else FontWeight.SemiBold)) { append(name) }
                    nameEnd = b.length
                    if (paint != null && NamePaints.drawable(paint)) namePaint = paint
                    b.append(if (message.isAction) " " else ": ")
                }
                if (style.gifMessages && !message.gif.isNullOrBlank()) {
                    b.append("\n")
                    addImage(b, images, ChatImage(newId(), message.gif, ChatImageKind.Gif, animated = true, thirdParty = false,
                        info = EmoteInfo(message.gif, message.message?.removeSurrounding("[", "]"), "gif", true, null, false, null)), message.message)
                } else if (message.message != null) {
                    val mentioned = if (message.isAction) {
                        var m = false
                        b.withStyle(SpanStyle(color = Color(color))) { m = appendBody(this, message.message, message, stvUser, images) }
                        m
                    } else {
                        appendBody(b, message.message, message, stvUser, images)
                    }
                    if (mentioned) highlight = ChatHighlight.Mention
                }
                highlight = when {
                    message.isFirst && style.firstMsgVisibility < 2 -> ChatHighlight.First
                    message.reward?.id != null && style.firstMsgVisibility < 2 -> ChatHighlight.Reward
                    message.systemMsg != null || message.msgId != null -> ChatHighlight.Notice
                    else -> highlight
                }
            }
        }
        return ChatLine(key, message, b.toAnnotatedString(), images, highlight, message.type == ChatMessage.REPLY_MESSAGE, namePaint, nameStart, nameEnd)
    }

    /** "name(login)", "name" or "login" by the name display setting, like everywhere in v0.1. */
    fun displayName(name: String?, login: String?): String? {
        return if (name != null && login != null && !login.equals(name, true)) {
            when (style.nameDisplay) {
                "0" -> "$name($login)"
                "1" -> name
                else -> login
            }
        } else name ?: login
    }

    private fun nameColor(message: ChatMessage): Int {
        val raw = message.color
        if (raw != null) {
            return savedColors.getOrPut(raw) {
                val parsed = ChatColors.parse(raw) ?: ChatColors.GREY
                if (style.readableColors) ChatColors.readable(parsed) else parsed
            }
        }
        val name = message.userName
        return (name?.let { userColors[it] }) ?: run {
            val c = if (style.randomColors) ChatColors.random(random) else ChatColors.GREY
            val adapted = if (style.readableColors) ChatColors.readable(c) else c
            if (name != null) userColors[name] = adapted
            adapted
        }
    }

    private fun appendTimestamp(b: AnnotatedString.Builder, message: ChatMessage) {
        if (message.timestamp != null && style.timestamps) {
            TwitchApiHelper.getTimestamp(message.timestamp, style.timestampFormat)?.let {
                b.withStyle(SpanStyle(color = Muted)) { append(it) }
                b.append(" ")
            }
        }
    }

    /** Where [name] (with an "@" in front, if there is one) sits in [text]: the tappable part of a line (U9 C1). */
    private fun nameRange(text: String, name: String?): Pair<Int, Int>? {
        if (name.isNullOrEmpty()) return null
        val i = text.indexOf(name)
        if (i < 0) return null
        val start = if (i > 0 && text[i - 1] == '@') i - 1 else i
        return start to i + name.length
    }

    private fun appendReward(b: AnnotatedString.Builder, message: ChatMessage, images: ArrayList<ChatImage>, color: Color?) {
        val reward = message.reward ?: return
        pick(reward.url1x, reward.url2x, reward.url4x, reward.url4x)?.let {
            addImage(b, images, ChatImage(newId(), it, ChatImageKind.Badge, animated = false, thirdParty = false), reward.title)
            b.append(" ")
        }
        reward.cost?.let { cost ->
            val text = NumberFormat.getInstance().format(cost)
            if (color != null) b.withStyle(SpanStyle(color = color)) { append(text) } else b.append(text)
        }
    }

    /**
     * The words of a message: Twitch emotes by their ranges, 7TV personal emotes, third-party emotes by name, bits,
     * zero-width emotes stacked on the emote before them, links, @mentions. Returns whether the logged-in user is
     * mentioned.
     */
    private fun appendBody(b: AnnotatedString.Builder, text: String, message: ChatMessage, stvUser: STVUser?, images: ArrayList<ChatImage>): Boolean {
        var mentioned = false
        val twitch = HashMap<Int, TwitchEmote>()
        try {
            message.emotes?.forEach {
                val realBegin = text.offsetByCodePoints(0, it.begin)
                val realEnd = if (it.begin == realBegin) it.end else it.end + realBegin - it.begin
                twitch[realBegin] = TwitchEmote(id = it.id, begin = realBegin, end = realEnd)
            }
        } catch (e: Exception) {
            // ranges from another text (the reply preview of a parent) do not apply
        }
        val personal = if (style.personalEmotes) stvUser?.emoteSetId?.let { id -> synchronized(assets.personalEmoteSets) { assets.personalEmoteSets[id] } } else null
        var previous: MutableImage? = null
        var index = 0
        var first = true
        for (word in text.split(" ")) {
            val wordStart = index
            index += word.length + 1
            if (message.bits != null) {
                val count = word.takeLastWhile { it.isDigit() }
                val name = word.substringBeforeLast(count)
                if (count.isNotEmpty()) {
                    val bits = count.toIntOrNull()
                    val cheer = if (bits != null) assets.cheerEmotes.findLast { it.name.equals(name, true) && it.minBits <= bits } else null
                    if (cheer != null) {
                        if (!first) b.append(" ")
                        first = false
                        val model = cheer.localData?.let { local(cheer.name + cheer.minBits, it) } ?: pick(cheer.url1x, cheer.url2x, cheer.url3x, cheer.url4x)
                        if (model != null) {
                            addImage(b, images, ChatImage(newId(), model, ChatImageKind.Emote, cheer.isAnimated, false,
                                info = EmoteInfo(cheer.url4x ?: cheer.url3x ?: cheer.url2x ?: cheer.url1x, word, cheer.format, cheer.isAnimated, null, null, null)), name)
                        }
                        val bitsColor = cheer.color?.takeIf { it.isNotBlank() }?.let { c -> savedColors.getOrPut(c) { ChatColors.parse(c)?.let { if (style.readableColors) ChatColors.readable(it) else it } ?: ChatColors.GREY } }
                        if (bitsColor != null) b.withStyle(SpanStyle(color = Color(bitsColor))) { append(count) } else b.append(count)
                        previous = null
                        continue
                    }
                }
            }
            val emote = personal?.find { it.name == word } ?: assets.thirdPartyEmotes[word]
            if (emote != null) {
                val model = emote.localData?.let { local(emote.name!!, it) } ?: pick(emote.url1x, emote.url2x, emote.url3x, emote.url4x)
                if (model != null) {
                    val info = EmoteInfo(emote.url4x ?: emote.url3x ?: emote.url2x ?: emote.url1x, emote.name, emote.format, emote.isAnimated, emote.source, emote.thirdParty, null)
                    val prev = previous
                    if (emote.isOverlayEmote && style.overlayEmotes && prev != null) {
                        prev.overlays += ChatImage(newId(), model, ChatImageKind.Emote, emote.isAnimated, emote.thirdParty, info = info)
                        images[prev.index] = prev.toImage()
                        continue
                    }
                    if (!first) b.append(" ")
                    first = false
                    val image = ChatImage(newId(), model, ChatImageKind.Emote, emote.isAnimated, emote.thirdParty, info = info)
                    addImage(b, images, image, word)
                    previous = MutableImage(images.lastIndex, image)
                    continue
                }
            }
            val te = twitch[wordStart]
            if (te != null) {
                val local = te.id?.let { assets.localTwitchEmotes[it] }
                val e = local?.let {
                    TwitchEmote(id = it.id, name = it.name, localData = it.localData, format = it.format, isAnimated = it.isAnimated, setId = it.setId, ownerId = it.ownerId)
                } ?: TwitchEmote(id = te.id)
                val model = e.localData?.let { local(e.id!!, it) } ?: pick(e.url1x, e.url2x, e.url3x, e.url4x)
                if (model != null) {
                    if (!first) b.append(" ")
                    first = false
                    val image = ChatImage(newId(), model, ChatImageKind.Emote, e.isAnimated, false,
                        info = EmoteInfo(e.url4x ?: e.url3x ?: e.url2x ?: e.url1x, word, e.format, e.isAnimated, null, null, e.id))
                    addImage(b, images, image, word)
                    previous = MutableImage(images.lastIndex, image)
                    continue
                }
            }
            if (!first) b.append(" ")
            first = false
            previous = null
            if (word.isNotEmpty() && Patterns.WEB_URL.matcher(word).matches()) {
                val url = if (word.startsWith("http")) word else "https://$word"
                b.withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = Link, textDecoration = TextDecoration.Underline)))) { append(word) }
                continue
            }
            if (word.startsWith('@') && style.boldNames) {
                b.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(word) }
            } else {
                b.append(word)
            }
            if (!mentioned && !loggedInUser.isNullOrBlank() && word.contains(loggedInUser, true) && message.userId != null && message.userLogin != loggedInUser) {
                mentioned = true
            }
        }
        return mentioned
    }

    private class MutableImage(val index: Int, val base: ChatImage) {
        val overlays = ArrayList<ChatImage>()
        fun toImage() = ChatImage(base.id, base.model, base.kind, base.animated, base.thirdParty, overlays.toList(), base.info)
    }

    private fun newId() = "i${imageCount++}"

    private fun addImage(b: AnnotatedString.Builder, images: ArrayList<ChatImage>, image: ChatImage, alternate: String?) {
        images.add(image)
        // the alternate text is what copying and screen readers get for the picture
        b.appendInlineContent(image.id, alternate?.takeIf { it.isNotEmpty() } ?: "�")
    }

    /** The URL for the chosen quality (v0.1 rules). */
    private fun pick(url1x: String?, url2x: String?, url3x: String?, url4x: String?): String? = when (style.emoteQuality) {
        "4" -> url4x ?: url3x ?: url2x ?: url1x
        "3" -> url3x ?: url2x ?: url1x
        "2" -> url2x ?: url1x
        else -> url1x
    }

    private fun local(name: String, data: Pair<Long, Int>): ByteArray? {
        savedLocal[name]?.let { return it }
        val url = chatUrl ?: return null
        val bytes = getEmoteBytes?.invoke(url, data) ?: return null
        if (savedLocal.size >= 100) savedLocal.remove(savedLocal.keys.first())
        savedLocal[name] = bytes
        return bytes
    }

    companion object {
        // the builder runs outside composition; Lagoon is dark only, so it reads the dark tokens (U9 B6)
        val Muted = DarkLagoonColors.chatMuted
        val Link = DarkLagoonColors.chatLink
    }
}
