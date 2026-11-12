package com.github.andreyasadchy.xtra.lab

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import coil3.compose.AsyncImage
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/** One prepared chat line: text with placeholders, and the image behind each placeholder. */
@Immutable
class ComposeChatRow(val key: Long, val text: AnnotatedString, val images: Map<String, InlineImage>)

@Immutable
class InlineImage(val url: String, val width: TextUnit, val height: TextUnit)

/**
 * Builds rows off the main thread. Uses the same inputs as ChatAdapterUtils.prepareChatMessage (badges, Twitch emote
 * ranges, third-party emotes by word, user colour made readable on a dark background), without the click spans,
 * name paints and translation that the prototype does not need for a performance comparison.
 */
class ComposeChatRowBuilder(context: Context, data: ChatLabData) {
    private val keys = AtomicLong()
    private val emotes = data.thirdPartyEmotes.associateBy { it.name }
    private val badges = (data.globalBadges + data.channelBadges).associateBy { it.setId + "/" + it.version }
    private val colors = HashMap<String, Int>()
    private val random = Random(0)

    fun build(message: ChatMessage): ComposeChatRow {
        val images = HashMap<String, InlineImage>()
        val text = buildAnnotatedString {
            message.systemMsg?.let {
                withStyle(SpanStyle(color = Color(0xFF9E9EA6))) { append(it) }
                if (message.message != null) append(" ")
            }
            message.badges?.forEachIndexed { i, b ->
                val badge = badges[b.setId + "/" + b.version]
                val url = badge?.url2x ?: badge?.url1x
                if (url != null) {
                    val id = "b$i"
                    images[id] = InlineImage(url, 18.5.sp, 18.5.sp)
                    appendInlineContent(id, " ")
                    append(" ")
                }
            }
            val name = message.userName ?: message.userLogin
            if (name != null) {
                withStyle(SpanStyle(color = Color(nameColor(message)), fontWeight = FontWeight.SemiBold)) { append(name) }
                append(if (message.isAction) " " else ": ")
            }
            val body = message.message ?: return@buildAnnotatedString
            val twitch = message.emotes.orEmpty().associateBy { it.begin }
            var i = 0
            var n = 0
            while (i < body.length) {
                val te = twitch[i]
                if (te != null && te.end >= i) {
                    val id = "e${n++}"
                    images[id] = InlineImage(te.url4x ?: te.url2x!!, 29.5.sp, 29.5.sp)
                    appendInlineContent(id, body.substring(i, minOf(te.end + 1, body.length)))
                    i = te.end + 1
                    continue
                }
                val end = body.indexOf(' ', i).let { if (it == -1) body.length else it }
                val word = body.substring(i, end)
                val emote = emotes[word]
                if (emote != null) {
                    val id = "e${n++}"
                    images[id] = InlineImage(emote.url4x ?: emote.url3x ?: emote.url2x ?: emote.url1x!!, 29.5.sp, 29.5.sp)
                    appendInlineContent(id, word)
                } else {
                    append(word)
                }
                if (end < body.length) append(' ')
                i = end + 1
            }
        }
        return ComposeChatRow(keys.incrementAndGet(), text, images)
    }

    /** Random colour for users without one, lightened until it reaches 4.5:1 against the chat background. */
    private fun nameColor(m: ChatMessage): Int {
        val login = m.userLogin ?: ""
        return colors.getOrPut(login) {
            var c = m.color?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
                ?: android.graphics.Color.HSVToColor(floatArrayOf(random.nextFloat() * 360f, 0.7f, 0.9f))
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(c, hsl)
            while (ColorUtils.calculateContrast(c, BACKGROUND) < 4.5 && hsl[2] < 1f) {
                hsl[2] = minOf(1f, hsl[2] + 0.05f)
                c = ColorUtils.HSLToColor(hsl)
            }
            c
        }
    }

    companion object {
        const val BACKGROUND = 0xFF111113.toInt()
    }
}

@Composable
fun ComposeChatList(rows: List<ComposeChatRow>) {
    // newest row first with reverseLayout, so the list stays pinned to the bottom without scroll calls
    LazyColumn(Modifier.fillMaxSize().background(Color(ComposeChatRowBuilder.BACKGROUND)), reverseLayout = true) {
        items(count = rows.size, key = { rows[rows.size - 1 - it].key }, contentType = { 0 }) { index ->
            ChatLine(rows[rows.size - 1 - index])
        }
    }
}

@Composable
private fun ChatLine(row: ComposeChatRow) {
    val inline = row.images.mapValues { (_, img) ->
        InlineTextContent(Placeholder(img.width, img.height, PlaceholderVerticalAlign.TextCenter)) {
            AsyncImage(model = img.url, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
    Text(
        text = row.text,
        inlineContent = inline,
        color = Color(0xEBFFFFFF),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
}
