package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.Chatter
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.RoomState
import com.github.andreyasadchy.xtra.model.ui.ChannelViewerList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Logic around the chat page (U7): room modes, suggestions, emote groups, viewer search, percentages, names. */
@RunWith(AndroidJUnit4::class)
class ChatLogicTest {

    /** Robolectric keeps a font scale set by an earlier screenshot class; these tests and the ones after want 1x. */
    @org.junit.Before
    @org.junit.After
    fun normalFont() = org.robolectric.RuntimeEnvironment.setFontScale(1f)

    @Test
    fun roomModesMergePartialUpdates() {
        var modes = RoomModes()
        assertFalse(modes.any)
        // the first ROOMSTATE has every field, later ones only what changed (v0.1 kept the others)
        modes = modes.merge(RoomState(emote = "0", followers = "10", unique = "0", slow = "30", subs = "0"))
        assertEquals(RoomModes(followersMinutes = 10, slowSeconds = 30), modes)
        modes = modes.merge(RoomState(emote = null, followers = null, unique = null, slow = "0", subs = "1"))
        assertEquals(RoomModes(followersMinutes = 10, slowSeconds = 0, subsOnly = true), modes)
        modes = modes.merge(RoomState(emote = null, followers = "-1", unique = null, slow = null, subs = "0"))
        assertFalse(modes.any)
        assertTrue(RoomModes(followersMinutes = 0).any)
    }

    @Test
    fun suggestionsNeedAColonOrAnAt() {
        assertNull(ChatSuggestions.tokenAt(TextFieldValue("kek", TextRange(3))))
        assertNull(ChatSuggestions.tokenAt(TextFieldValue(":", TextRange(1))))
        assertEquals(4 to ":kek", ChatSuggestions.tokenAt(TextFieldValue("lol :kek", TextRange(8))))
        // the word at the cursor, not the last one
        assertEquals(0 to "@ri", ChatSuggestions.tokenAt(TextFieldValue("@ri hello", TextRange(3))))
    }

    @Test
    fun suggestionsMatchLettersInOrderLikeV01() {
        val candidates = listOf<Any?>(Emote("KEKW"), Emote("KEKWait"), Emote("Kappa"), Chatter("Riverbank"), Chatter("Kelp"))
        assertEquals(listOf("KEKW", "KEKWait"), ChatSuggestions.find(":kekw", candidates).map { it.text })
        // letters in order with others between them
        assertEquals(listOf("KEKWait"), ChatSuggestions.find(":kwt", candidates).map { it.text })
        assertEquals(listOf("@Riverbank"), ChatSuggestions.find("@rvb", candidates).map { it.text })
        // ":" only looks at emotes, "@" only at chatters
        assertTrue(ChatSuggestions.find(":riv", candidates).isEmpty())
        assertTrue(ChatSuggestions.find("@kap", candidates).isEmpty())
    }

    @Test
    fun applyingASuggestionReplacesTheWordAndAddsASpace() {
        val v = ChatSuggestions.apply(TextFieldValue("gg :kek later", TextRange(7)), "KEKW")
        assertEquals("gg KEKW  later", v.text)
        assertEquals(8, v.selection.end)
    }

    @Test
    fun deleteWordDropsTheLastWordLikeV01() {
        assertEquals("hello", ChatSuggestions.deleteWord("hello there "))
        assertEquals("", ChatSuggestions.deleteWord("hello"))
        assertEquals("a b", ChatSuggestions.deleteWord("a b c"))
    }

    @Test
    fun emoteSectionsGroupBySourceAndDropDuplicates() {
        val twitch = listOf(Emote("Kappa"), Emote("ChannelSub"))
        val third = listOf(
            Emote("PepeHands", source = Emote.CHANNEL_STV), Emote("PepeHands", source = Emote.CHANNEL_BTTV),
            Emote("KEKW", source = Emote.GLOBAL_BTTV), Emote("RainTime", source = Emote.GLOBAL_STV), Emote("monkaS", source = Emote.GLOBAL_FFZ),
        )
        val personal = listOf(Emote("MyOwn", source = Emote.PERSONAL_STV))
        val sections = EmoteSections.build(listOf("KEKW", "Gone", "Kappa"), twitch, setOf("ChannelSub"), personal, third)
        assertEquals(listOf("recent", "personal", "channel", "twitch", "stv", "bttv", "ffz"), sections.map { it.id })
        assertEquals(listOf("KEKW", "Kappa"), sections.first { it.id == "recent" }.emotes.map { it.name })
        assertEquals(listOf("ChannelSub", "PepeHands"), sections.first { it.id == "channel" }.emotes.map { it.name })
        assertEquals(listOf("Kappa"), sections.first { it.id == "twitch" }.emotes.map { it.name })
        assertEquals(R.string.cht_emotes_channel, sections.first { it.id == "channel" }.title)
        // empty groups disappear
        assertEquals(listOf("twitch"), EmoteSections.build(emptyList(), twitch, emptySet(), emptyList(), emptyList()).map { it.id })
    }

    @Test
    fun emoteSearchPutsPrefixMatchesFirst() {
        val sections = EmoteSections.build(emptyList(), listOf(Emote("peepoHappy"), Emote("Pepega")), emptySet(), emptyList(), listOf(Emote("HappyPepe", source = Emote.GLOBAL_STV)))
        assertEquals(listOf("Pepega", "HappyPepe"), EmoteSections.search(sections, "pepe").map { it.name })
        assertTrue(EmoteSections.search(sections, " ").isEmpty())
    }

    @Test
    fun viewerSearchFiltersEveryGroup() {
        val list = ChannelViewerList(listOf("lagoonfm"), listOf("modfish", "kelp"), listOf("vipfish"), listOf("fishfan", "other"), 6)
        assertEquals(4, ViewerListLogic.groups(list, "").size)
        val fish = ViewerListLogic.groups(list, "FISH")
        assertEquals(listOf(R.string.moderators to listOf("modfish"), R.string.vips to listOf("vipfish"), R.string.viewers to listOf("fishfan")), fish)
        assertTrue(ViewerListLogic.groups(list, "nobody").isEmpty())
    }

    @Test
    fun percentagesRoundAndSurviveZero() {
        assertEquals(64, ChatEvents.percent(640, 1000))
        assertEquals(33, ChatEvents.percent(1, 3))
        assertEquals(0, ChatEvents.percent(5, 0))
    }

    @Test
    fun displayNamesFollowTheSetting() {
        assertEquals("鱼(fish)", ChatEvents.displayName("鱼", "fish", "0"))
        assertEquals("鱼", ChatEvents.displayName("鱼", "fish", "1"))
        assertEquals("fish", ChatEvents.displayName("鱼", "fish", "2"))
        assertEquals("Fish", ChatEvents.displayName("Fish", "fish", "0"))
    }
}
