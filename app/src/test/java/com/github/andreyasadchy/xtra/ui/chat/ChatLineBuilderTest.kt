package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.ui.text.LinkAnnotation
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.model.chat.Badge
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.NamePaint
import com.github.andreyasadchy.xtra.model.chat.Reply
import com.github.andreyasadchy.xtra.model.chat.STVUser
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random

/**
 * The line builder is a port of v0.1's ChatAdapterUtils.prepareChatMessage; these cases pin the v0.1 rules it keeps
 * (U7): order of the pieces, names, badges, Twitch and third-party emotes, zero-width emotes, bits, links, mentions,
 * highlights, reply lines, timestamps and readable colours.
 */
@RunWith(AndroidJUnit4::class)
class ChatLineBuilderTest {

    /** Robolectric keeps a font scale set by an earlier screenshot class; these tests and the ones after want 1x. */
    @org.junit.Before
    @org.junit.After
    fun normalFont() = org.robolectric.RuntimeEnvironment.setFontScale(1f)

    private fun msg(text: String, vararg extra: Pair<String, Any?>): ChatMessage {
        val m = extra.toMap()
        @Suppress("UNCHECKED_CAST")
        return ChatMessage(
            type = ChatMessage.USER_MESSAGE, id = "id", userId = m["userId"] as String? ?: "1", userLogin = m["login"] as String? ?: "fish",
            userName = m["name"] as String? ?: "Fish", message = text, color = m["color"] as String?, emotes = m["emotes"] as List<TwitchEmote>?,
            badges = m["badges"] as List<Badge>?, isAction = m["action"] == true, isFirst = m["first"] == true, bits = m["bits"] as Int?,
            timestamp = m["ts"] as Long?,
        )
    }

    /** The text with every picture shown as [id]. */
    private fun ChatLine.plain(): String {
        val inline = text.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, text.length)
        var out = text.text
        inline.sortedByDescending { it.start }.forEach { out = out.substring(0, it.start) + "[" + it.item + "]" + out.substring(it.end) }
        return out
    }

    @Test
    fun aPlainMessageIsNameColonText() {
        val line = U7Fixtures.builder().build(msg("hello there"))
        assertEquals("Fish: hello there", line.plain())
        assertEquals(ChatHighlight.None, line.highlight)
        assertEquals(0, line.nameStart)
        assertEquals(4, line.nameEnd)
    }

    @Test
    fun nameDisplayFollowsTheSetting() {
        val m = msg("hi", "name" to "鱼", "login" to "fish")
        assertEquals("鱼(fish): hi", U7Fixtures.builder(ChatStyle(nameDisplay = "0")).build(m).plain())
        assertEquals("鱼: hi", U7Fixtures.builder(ChatStyle(nameDisplay = "1")).build(m).plain())
        assertEquals("fish: hi", U7Fixtures.builder(ChatStyle(nameDisplay = "2")).build(m).plain())
    }

    @Test
    fun badgesComeBeforeTheNameAndUnknownOnesAreSkipped() {
        val line = U7Fixtures.builder().build(msg("hi", "badges" to listOf(Badge("subscriber", "12"), Badge("unknown", "1"))))
        assertEquals("[i0] Fish: hi", line.plain())
        assertEquals(ChatImageKind.Badge, line.images.single().kind)
        assertEquals("1-Year Subscriber", line.images.single().info?.name)
    }

    @Test
    fun thirdPartyEmotesBecomePictures() {
        val line = U7Fixtures.builder().build(msg("lol KEKW ok"))
        assertEquals("Fish: lol [i0] ok", line.plain())
        assertEquals("https://example.com/kekw", line.images.single().model)
        assertEquals(Emote.GLOBAL_BTTV, line.images.single().info?.source)
    }

    @Test
    fun zeroWidthEmotesStackOnThePreviousOne() {
        val line = U7Fixtures.builder().build(msg("PepeHands RainTime done"))
        assertEquals("Fish: [i0] done", line.plain())
        assertEquals(1, line.images.size)
        assertEquals("https://example.com/raintime", line.images.single().overlays.single().model)
        // off: the zero-width emote is an emote of its own
        val off = U7Fixtures.builder(ChatStyle(overlayEmotes = false)).build(msg("PepeHands RainTime done"))
        assertEquals("Fish: [i0] [i1] done", off.plain())
    }

    @Test
    fun aZeroWidthEmoteWithNothingBeforeItStandsAlone() {
        assertEquals("Fish: hi [i0]", U7Fixtures.builder().build(msg("hi RainTime")).plain())
    }

    @Test
    fun twitchEmotesUseTheirRangesCountedInCodePoints() {
        // "😀 Kappa": the emoji is two UTF-16 units, one code point; Twitch sends 2-6
        val line = U7Fixtures.builder().build(msg("😀 Kappa hi", "emotes" to listOf(TwitchEmote(id = "25", begin = 2, end = 6))))
        assertEquals("Fish: 😀 [i0] hi", line.plain())
        assertTrue((line.images.single().model as String).contains("/25/"))
        assertEquals("25", line.images.single().info?.emoteId)
    }

    @Test
    fun emoteQualityPicksTheUrl() {
        val e = Emote("Wide", url1x = "u1", url2x = "u2", url3x = "u3", url4x = "u4")
        val assets = ChatAssets.of(emptyList(), listOf(e), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())
        fun model(q: String) = ChatLineBuilder(ChatStyle(emoteQuality = q), ChatStrings.Plain, assets, null).build(msg("Wide")).images.single().model
        assertEquals("u4", model("4"))
        assertEquals("u3", model("3"))
        assertEquals("u2", model("2"))
        assertEquals("u1", model("1"))
    }

    @Test
    fun bitsShowTheCheerPictureAndTheCount() {
        val cheer = CheerEmote("Cheer", url1x = "c1", minBits = 1, color = "#9C3EE8")
        val big = CheerEmote("Cheer", url1x = "c100", minBits = 100, color = "#1DB2A5")
        val assets = ChatAssets.of(emptyList(), emptyList(), emptyList(), emptyList(), listOf(cheer, big), emptyList(), emptyList(), emptyList(), emptyMap())
        val line = ChatLineBuilder(ChatStyle(emoteQuality = "1"), ChatStrings.Plain, assets, null).build(msg("Cheer150 gg", "bits" to 150))
        assertEquals("Fish: [i0]150 gg", line.plain())
        assertEquals("c100", line.images.single().model)
    }

    @Test
    fun linksAreClickable() {
        val line = U7Fixtures.builder().build(msg("see example.com/a now"))
        val links = line.text.getLinkAnnotations(0, line.text.length)
        assertEquals("https://example.com/a", (links.single().item as LinkAnnotation.Url).url)
    }

    @Test
    fun aMentionOfTheViewerHighlightsTheLine() {
        assertEquals(ChatHighlight.Mention, U7Fixtures.builder(loggedIn = "viewer").build(msg("hey @Viewer")).highlight)
        // not the viewer's own message, and nothing when logged out
        assertEquals(ChatHighlight.None, U7Fixtures.builder(loggedIn = "viewer").build(msg("hey @viewer", "login" to "viewer")).highlight)
        assertEquals(ChatHighlight.None, U7Fixtures.builder(loggedIn = null).build(msg("hey @viewer")).highlight)
    }

    @Test
    fun firstMessagesFollowTheVisibilitySetting() {
        val m = msg("hi all", "first" to true)
        val shown = U7Fixtures.builder(ChatStyle(firstMsgVisibility = 0)).build(m)
        assertEquals("First message\nFish: hi all", shown.plain())
        assertEquals(ChatHighlight.First, shown.highlight)
        val tintOnly = U7Fixtures.builder(ChatStyle(firstMsgVisibility = 1)).build(m)
        assertEquals("Fish: hi all", tintOnly.plain())
        assertEquals(ChatHighlight.First, tintOnly.highlight)
        assertEquals(ChatHighlight.None, U7Fixtures.builder(ChatStyle(firstMsgVisibility = 2)).build(m).highlight)
    }

    @Test
    fun noticesAndSubscriptionsAreTinted() {
        val sub = ChatMessage(type = ChatMessage.USER_MESSAGE, systemMsg = "Fish subscribed", userName = "Fish", userLogin = "fish", message = "yay")
        val line = U7Fixtures.builder().build(sub)
        assertEquals("Fish subscribed\nFish: yay", line.plain())
        assertEquals(ChatHighlight.Notice, line.highlight)
        val system = U7Fixtures.builder().build(ChatMessage(systemMsg = "Connected to lagoonfm"))
        assertEquals("Connected to lagoonfm", system.plain())
        assertEquals(ChatHighlight.None, system.highlight)
    }

    @Test
    fun replyLinesQuoteTheParent() {
        val line = U7Fixtures.builder().build(ChatMessage(type = ChatMessage.REPLY_MESSAGE, reply = Reply("p", "fish", "Fish", "the KEKW one")))
        assertTrue(line.isReplyHeader)
        assertEquals("Replying to @Fish: the [i0] one", line.plain())
    }

    @Test
    fun actionsColourTheMessageAndDropTheColon() {
        val line = U7Fixtures.builder().build(msg("waves", "action" to true, "color" to "#FF0000"))
        assertEquals("Fish waves", line.plain())
        assertTrue(line.text.spanStyles.any { it.start == 5 && it.end == 10 })
    }

    @Test
    fun timestampsComeFirstWhenOn() {
        val line = U7Fixtures.builder(ChatStyle(timestamps = true, timestampFormat = "0")).build(msg("hi", "ts" to 0L))
        assertTrue(line.plain().matches(Regex("\\d{1,2}:\\d{2}.* Fish: hi")))
        assertEquals(U7Fixtures.builder().build(msg("hi", "ts" to 0L)).plain(), "Fish: hi")
    }

    @Test
    fun nameColoursReachFourAndAHalfToOne() {
        listOf("#0000FF", "#000080", "#8B0000", "#2E2E2E", "#00FF7F").forEach {
            val c = ChatColors.readable(android.graphics.Color.parseColor(it))
            assertTrue("$it -> contrast ${ColorUtils.calculateContrast(c, ChatColors.Background)}", ColorUtils.calculateContrast(c, ChatColors.Background) >= 4.5)
        }
    }

    @Test
    fun usersWithoutAColourKeepTheirRandomOne() {
        val b = ChatLineBuilder(ChatStyle(), ChatStrings.Plain, ChatAssets(), null, random = Random(3))
        val first = b.build(msg("a")).text.spanStyles.first().item.color
        val second = b.build(msg("b")).text.spanStyles.first().item.color
        assertEquals(first, second)
    }

    @Test
    fun paintsAreKeptForDrawTime() {
        val paint = NamePaint("p1", "LINEAR_GRADIENT", intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()), floatArrayOf(0f, 1f), angle = 90)
        val assets = ChatAssets.of(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), listOf(STVUser("1", paintId = "p1")), emptyList(), listOf(paint), emptyMap())
        val line = ChatLineBuilder(ChatStyle(), ChatStrings.Plain, assets, null).build(msg("hi"))
        assertNotNull(line.namePaint)
        assertNotNull(NamePaints.gradient(paint, 100f))
        val off = ChatLineBuilder(ChatStyle(namePaints = false), ChatStrings.Plain, assets, null).build(msg("hi"))
        assertNull(off.namePaint)
    }

    @Test
    fun gifMessagesShowThePicture() {
        val m = ChatMessage(type = ChatMessage.USER_MESSAGE, userName = "Fish", userLogin = "fish", message = "[party]", gif = "https://example.com/a.gif")
        val line = U7Fixtures.builder().build(m)
        assertEquals("Fish: \n[i0]", line.plain())
        assertEquals(ChatImageKind.Gif, line.images.single().kind)
        assertFalse(U7Fixtures.builder(ChatStyle(gifMessages = false)).build(m).images.isNotEmpty())
    }
}
