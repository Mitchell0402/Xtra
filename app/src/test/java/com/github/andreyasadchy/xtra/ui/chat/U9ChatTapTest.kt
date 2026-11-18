package com.github.andreyasadchy.xtra.ui.chat

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * U9 C1 (Mitchell's D2): in the chat a tap on an emote opens the emote card, only a tap on the sender's name opens
 * the user card, and the rest of a line does nothing (plan gate 4).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h900dp")
// real text measurement: the taps are placed on characters
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class U9ChatTapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    @After
    fun normalFont() = RuntimeEnvironment.setFontScale(1f)

    private fun show(state: ChatState = U7Fixtures.state()): RecordingChatActions {
        val actions = RecordingChatActions(state)
        compose.setContent { LagoonTheme(reducedMotion = true) { ChatScreenInPlace(state, actions) } }
        compose.waitForIdle()
        return actions
    }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.first()
    }

    /** Taps the middle of [word] (its [occurrence]-th match) inside the text node that contains [lineText]. */
    private fun SemanticsNodeInteraction.tapWord(word: String, occurrence: Int = 0) {
        val node = fetchSemanticsNode()
        val layout = layoutOf(node)
        val text = layout.layoutInput.text.text
        var index = -1
        repeat(occurrence + 1) { index = text.indexOf(word, index + 1) }
        assertTrue("'$word' in '$text'", index >= 0)
        val box = layout.getBoundingBox(index + word.length / 2)
        performTouchInput { click(Offset(box.center.x, box.center.y)) }
        compose.waitForIdle()
    }

    private fun line(text: String) = compose.onNodeWithText(text, substring = true, useUnmergedTree = true)

    @Test
    fun tappingTheNameOpensTheUserCard() {
        val actions = show()
        line("Subscriber Riverbank: that was close").tapWord("Riverbank")
        assertEquals(listOf("card:riverbank"), actions.calls)
    }

    @Test
    fun tappingTheMessageDoesNothing() {
        val actions = show()
        line("Subscriber Riverbank: that was close").tapWord("close")
        line("first time here").tapWord("everyone")
        line("did you see that").tapWord("see")
        line("Connected to").tapWord("Connected")
        assertEquals(emptyList<String>(), actions.calls)
    }

    @Test
    fun tappingAnEmoteOpensTheEmoteCard() {
        val actions = show()
        val node = line("Subscriber Riverbank: that was close").fetchSemanticsNode()
        val layout = layoutOf(node)
        val text = layout.layoutInput.text.text
        // the emote is an inline picture after "close "
        val at = text.indexOf("close") + "close ".length
        val box = layout.getBoundingBox(at)
        line("Subscriber Riverbank: that was close").performTouchInput { click(Offset(box.center.x, box.center.y)) }
        compose.waitForIdle()
        assertEquals(listOf("sheet:EmoteCard"), actions.calls)
    }

    @Test
    fun aReplyHeaderOpensTheRepliedToUserFromItsName() {
        val actions = show()
        line("Replying to").tapWord("Replying")
        assertEquals(emptyList<String>(), actions.calls)
        line("Replying to").tapWord("Riverbank")
        assertEquals(listOf("card:riverbank"), actions.calls)
    }

    @Test
    fun screenReaderActionsOpenTheCards() {
        val actions = show()
        val row = compose.onNodeWithText("Subscriber Riverbank: that was close", substring = true, useUnmergedTree = false).fetchSemanticsNode()
        val custom = generateSequence(row) { it.parent }.mapNotNull { it.config.getOrNull(SemanticsActions.CustomActions) }.first()
        val labels = custom.map { it.label }
        assertTrue(labels.toString(), "Open Riverbank's user card" in labels)
        assertTrue(labels.toString(), labels.any { it.startsWith("View emote") })
        custom.first { it.label == "Open Riverbank's user card" }.action()
        custom.first { it.label.startsWith("View emote") }.action()
        assertEquals(listOf("card:riverbank", "sheet:EmoteCard"), actions.calls)
    }

    @Test
    fun theReplyThreadFollowsTheSameRule() {
        val state = U7Fixtures.state()
        val tidal = U7Fixtures.messages().first { it.userLogin == "tidal" }
        val actions = RecordingChatActions(state)
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                androidx.compose.foundation.layout.Column { ChatSheetContent(state, ChatSheet.Thread(tidal), actions) }
            }
        }
        compose.waitForIdle()
        line("it really was").tapWord("really")
        assertEquals(emptyList<String>(), actions.calls)
        line("it really was").tapWord("Tidal")
        assertEquals(listOf("sheet:UserCard"), actions.calls)
    }

    @Test
    fun replyHeaderTargetFallsBackToTheReplyData() {
        val header = U7Fixtures.messages().first { it.reply != null && it.replyParent != null }
        val target = repliedTo(header, emptyList())
        assertEquals("riverbank", target.userLogin)
        assertEquals("1", target.id)
    }
}
