package com.github.andreyasadchy.xtra.ui.chat

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.Raid
import com.github.andreyasadchy.xtra.model.ui.ChannelViewerList
import com.github.andreyasadchy.xtra.ui.design.components.SheetSurface
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The chat page's behaviour (plan 10.2 interaction tests, U7): input, points, emotes, room modes, events and sheets. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h900dp")
class U7InteractionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(state: ChatState): RecordingChatActions {
        val actions = RecordingChatActions(state)
        compose.setContent { LagoonTheme(reducedMotion = true) { ChatScreenInPlace(state, actions) } }
        compose.waitForIdle()
        return actions
    }

    /** Robolectric keeps a font scale set by an earlier screenshot class; these tests and the ones after want 1x. */
    @org.junit.Before
    @org.junit.After
    fun normalFont() = org.robolectric.RuntimeEnvironment.setFontScale(1f)

    @Test
    fun thePointsBalanceSitsInTheInputAndOpensItsSheet() {
        val state = U7Fixtures.state()
        val actions = show(state)
        compose.onNodeWithTag("pointsChip").assertIsDisplayed()
        compose.onNodeWithText("12.4K", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("pointsChip").performClick()
        assertEquals(listOf("points"), actions.calls)
        assertEquals(ChatSheet.Points, state.sheet)
    }

    @Test
    fun aPendingBonusCanBeClaimedWhenAutomaticClaimingIsOff() {
        val state = U7Fixtures.state(points = ChannelPoints(balance = 100, claimAvailable = true, autoClaim = false))
        val actions = show(state)
        compose.onNodeWithText("Claim").performClick()
        assertEquals(listOf("claim"), actions.calls)
    }

    @Test
    fun withAutomaticClaimingTheChipShowsTheBalance() {
        show(U7Fixtures.state(points = ChannelPoints(balance = 100, claimAvailable = true, autoClaim = true)))
        assertEquals(0, compose.onAllNodesWithText("Claim").fetchSemanticsNodes().size)
        compose.onNodeWithText("100").assertIsDisplayed()
    }

    @Test
    fun roomModesStayAsChips() {
        show(U7Fixtures.state(modes = RoomModes(followersMinutes = 0, subsOnly = true, emoteOnly = true, unique = true)))
        compose.onNodeWithTag("roomModes").assertIsDisplayed()
        compose.onNodeWithText("Followers only").assertIsDisplayed()
        compose.onNodeWithText("Subscribers only").assertIsDisplayed()
        compose.onNodeWithText("Emotes only").assertIsDisplayed()
    }

    @Test
    fun noModesNoRow() {
        show(U7Fixtures.state(modes = RoomModes()))
        assertEquals(0, compose.onAllNodesWithText("Followers only").fetchSemanticsNodes().size)
    }

    @Test
    fun typingShowsSendAndSendingUsesTheText() {
        val state = U7Fixtures.state()
        val actions = show(state)
        assertEquals(0, compose.onAllNodesWithText("Send", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithTag("chatInput").performTextInput("hello lagoon")
        compose.onNodeWithContentDescription("Send").performClick()
        assertEquals(listOf("send:hello lagoon"), actions.calls)
    }

    @Test
    fun colonShowsEmoteSuggestionsAndATapCompletesTheWord() {
        val state = U7Fixtures.state()
        show(state)
        compose.onNodeWithTag("chatInput").performTextInput("lol :kek")
        compose.onNodeWithTag("chatSuggestions").assertIsDisplayed()
        compose.onNodeWithText("KEKW").performClick()
        assertEquals("lol KEKW ", state.input.text)
        assertTrue(state.suggestions.isEmpty())
    }

    @Test
    fun atShowsChatters() {
        val state = U7Fixtures.state()
        show(state)
        compose.onNodeWithTag("chatInput").performTextInput("@riv")
        compose.onNodeWithText("@Riverbank").performClick()
        assertEquals("@Riverbank ", state.input.text)
    }

    @Test
    fun theUndoButtonDropsTheLastWord() {
        val state = U7Fixtures.state()
        show(state)
        compose.onNodeWithTag("chatInput").performTextInput("one two three")
        compose.onNodeWithContentDescription("Delete last word").performClick()
        assertEquals("one two", state.input.text)
    }

    @Test
    fun theEmoteButtonOpensThePanelAndATapInserts() {
        val state = U7Fixtures.state()
        val actions = show(state)
        compose.onNodeWithContentDescription("Emotes").performClick()
        assertTrue(state.emotePanel)
        compose.onNodeWithTag("emotePicker").assertIsDisplayed()
        compose.onAllNodesWithText("This channel")[0].assertIsDisplayed()
        compose.onNodeWithContentDescription("KEKW").performClick()
        assertTrue("insert:KEKW" in actions.calls)
        // the button now brings the keyboard back
        compose.onNodeWithContentDescription("Keyboard").performClick()
        assertFalse(state.emotePanel)
    }

    @Test
    fun emoteSearchFindsAcrossGroups() {
        val state = U7Fixtures.state().apply { emotePanel = true }
        val actions = show(state)
        compose.onNodeWithTag("emoteSearch").performTextReplacement("monk")
        compose.onNodeWithContentDescription("monkaS").performClick()
        assertTrue("insert:monkaS" in actions.calls)
        compose.onNodeWithTag("emoteSearch").performTextReplacement("zzz")
        compose.onNodeWithText("No emotes match").assertIsDisplayed()
    }

    // tapping a line: see U9ChatTapTest (U9 C1 replaced the whole-row tap)

    @Test
    fun loggedOutHasNoInput() {
        show(U7Fixtures.state(loggedIn = false))
        assertEquals(0, compose.onAllNodesWithText("Send a message").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("12.4K", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun theInputCanBeHiddenFromThePlayer() {
        show(U7Fixtures.state().apply { chatBarVisible = false })
        assertEquals(0, compose.onAllNodesWithText("Send a message").fetchSemanticsNodes().size)
    }

    @Test
    fun pollAndPredictionBarsOpenTheirSheetsAndClose() {
        val state = U7Fixtures.state().apply {
            poll = U7Fixtures.poll
            pollSecondsLeft = 75
            prediction = U7Fixtures.prediction
        }
        val actions = show(state)
        compose.onNodeWithText("Next map?").performClick()
        assertEquals(ChatSheet.Poll, state.sheet)
        compose.onNodeWithText("Will we win this round?").performClick()
        assertEquals(ChatSheet.Prediction, state.sheet)
        compose.onAllNodesWithText("Locked").fetchSemanticsNodes().isNotEmpty().let { assertTrue(it) }
        assertTrue(actions.calls.containsAll(listOf("sheet:Poll", "sheet:Prediction")))
    }

    @Test
    fun theRaidBarGoesAndCloses() {
        val state = U7Fixtures.state().apply { raid = Raid("r", "2", "seafloor", "Seafloor", null, 420, false) }
        val actions = show(state)
        compose.onNodeWithText("Raiding Seafloor").performClick()
        assertEquals(listOf("raid"), actions.calls)
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(null, state.raid)
    }

    @Test
    fun replayWithoutChatSaysSo() {
        show(ChatState().apply { replayUnavailable = true })
        compose.onNodeWithText("Chat replay is unavailable").assertIsDisplayed()
    }

    @Test
    fun theUserCardOffersReplyMentionCopyRepeatAndChannel() {
        val state = U7Fixtures.state()
        val actions = RecordingChatActions(state)
        val message = state.lines.first().message
        actions.sheetData.user = ChatUserInfo("11", "riverbank", "Riverbank", null, null, "October 14, 2015", null)
        compose.setContent { LagoonTheme(reducedMotion = true) { SheetSurface { ChatSheetContent(state, ChatSheet.UserCard(message), actions) } } }
        compose.onNodeWithText("Joined October 14, 2015").assertIsDisplayed()
        listOf("Mention", "Copy", "Repeat", "Channel", "Reply").forEach { compose.onNodeWithText(it).performClick() }
        assertEquals(listOf("mention:riverbank", "copy:1", "repeat:1", "channel:riverbank", "reply:1"), actions.calls)
        assertEquals("1", state.reply?.id)
    }

    @Test
    fun loggedOutTheUserCardOnlyCopiesAndOpensTheChannel() {
        val state = U7Fixtures.state(loggedIn = false)
        val actions = RecordingChatActions(state)
        compose.setContent { LagoonTheme(reducedMotion = true) { SheetSurface { ChatSheetContent(state, ChatSheet.UserCard(state.lines.first().message), actions) } } }
        assertEquals(0, compose.onAllNodesWithText("Reply").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Mention").fetchSemanticsNodes().size)
        compose.onNodeWithText("Copy").assertIsDisplayed()
    }

    @Test
    fun thePointsSheetTogglesAutomaticClaiming() {
        val state = U7Fixtures.state()
        val actions = RecordingChatActions(state)
        compose.setContent { LagoonTheme(reducedMotion = true) { SheetSurface { ChatSheetContent(state, ChatSheet.Points, actions) } } }
        compose.onNodeWithTag("pointsBalance").assertIsDisplayed()
        compose.onNodeWithText("Pearls").assertIsDisplayed()
        compose.onNodeWithText("Claim bonuses automatically").performClick()
        assertEquals(listOf("auto:false"), actions.calls)
    }

    @Test
    fun theViewerListSearches() {
        val list = ChannelViewerList(listOf("lagoonfm"), listOf("modfish"), emptyList(), listOf("fishfan", "other"), 4)
        var opened: String? = null
        compose.setContent { LagoonTheme(reducedMotion = true) { SheetSurface { Column { ViewerListContent(list, true) { opened = it } } } } }
        compose.onNodeWithText("other").assertIsDisplayed()
        compose.onNodeWithTag("viewerSearch").performTextInput("fish")
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithText("other").fetchSemanticsNodes().size)
        compose.onNodeWithText("fishfan").performClick()
        assertEquals("fishfan", opened)
    }

    // here rather than in ChatLogicTest: Compose state made outside a compose test broke the next classes' tests
    @Test
    fun aPersonsLinesIncludeRepliesToThem() {
        val state = U7Fixtures.state()
        val riverbank = state.lines.first().message
        // own message and the resub notice (matched by login)
        assertEquals(listOf("1", "resub"), state.linesOf(riverbank).map { it.message.id ?: it.message.msgId })
        // a reply comes with its "replying to" line (v0.1)
        val tidal = state.lines.first { it.message.userLogin == "tidal" }.message
        assertEquals(listOf(true, false), state.linesOf(tidal).map { it.isReplyHeader })
        assertTrue(state.linesOf(ChatMessage(userLogin = "nobody")).isEmpty())
    }
}
