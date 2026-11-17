package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.github.andreyasadchy.xtra.model.chat.Raid
import com.github.andreyasadchy.xtra.model.ui.ChannelViewerList
import com.github.andreyasadchy.xtra.testing.FormFactor
import com.github.andreyasadchy.xtra.testing.ScreenshotRules
import com.github.andreyasadchy.xtra.ui.design.components.SheetSurface
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines of the chat (plan 10.2, U7): the live chat logged in (room-mode chips, points in the input),
 * logged out, with raid, poll and prediction bars, the emote panel, typing with suggestions and a reply, and the user
 * card, poll, prediction, points and viewer-list sheets drawn in place; phone portrait and at 1.3x font size. Pictures
 * do not load in tests and stay empty.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class U7ScreenshotTest(private val case: Case, private val variant: Variant) {

    enum class Case { Live, LoggedOut, Events, Emotes, Typing, UserCard, Poll, Prediction, Points, Viewers }

    enum class Variant(val formFactor: FormFactor, val fontScale: Float) {
        PhonePortrait(FormFactor.PhonePortrait, 1f),
        PhonePortraitFont130(FormFactor.PhonePortrait, 1.3f),
    }

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val rules = ScreenshotRules()

    @org.junit.After
    fun resetFont() = RuntimeEnvironment.setFontScale(1f)

    @Test
    fun capture() {
        variant.formFactor.apply()
        RuntimeEnvironment.setFontScale(variant.fontScale)
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                Box(Modifier.fillMaxSize().background(Lagoon.colors.page).testTag("page")) { Page(case) }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("page").captureRoboImage(ScreenshotRules.path("U7-${case.name}", variant.formFactor).let {
            if (variant.fontScale != 1f) it.replace(".png", "-Font130.png") else it
        })
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun params(): List<Array<Any>> = Case.entries.flatMap { c -> Variant.entries.map { v -> arrayOf<Any>(c, v) } }
    }
}

private fun setUp(case: U7ScreenshotTest.Case): Pair<ChatState, RecordingChatActions> {
    val state = if (case == U7ScreenshotTest.Case.LoggedOut) U7Fixtures.state(loggedIn = false, modes = RoomModes()) else U7Fixtures.state()
    val actions = RecordingChatActions(state)
    when (case) {
        U7ScreenshotTest.Case.Events -> {
            state.raid = Raid("r", "2", "seafloor", "Seafloor", null, 420, false)
            state.poll = U7Fixtures.poll
            state.pollSecondsLeft = 75
            state.prediction = U7Fixtures.prediction
        }
        U7ScreenshotTest.Case.Emotes -> state.emotePanel = true
        U7ScreenshotTest.Case.Typing -> {
            state.reply = ReplyTarget("1", "riverbank", "Riverbank", "that was close KEKW")
            state.input = TextFieldValue("nice :ke", TextRange(8))
            state.suggestions = listOf(Suggestion("KEKW", U7Fixtures.emotes[1]), Suggestion("KEKWait", null))
        }
        U7ScreenshotTest.Case.UserCard -> actions.sheetData.user = ChatUserInfo("11", "riverbank", "Riverbank", null, null, "October 14, 2015", "March 2, 2024")
        U7ScreenshotTest.Case.Poll -> {
            state.poll = U7Fixtures.poll
            state.pollSecondsLeft = 75
        }
        U7ScreenshotTest.Case.Prediction -> state.prediction = U7Fixtures.prediction
        U7ScreenshotTest.Case.Points -> state.points = ChannelPoints(balance = 12480, claimAvailable = true, autoClaim = false, name = "Pearls", lastEarned = 50)
        else -> Unit
    }
    return state to actions
}

@Composable
private fun Page(case: U7ScreenshotTest.Case) {
    val (state, actions) = remember(case) { setUp(case) }
    val sheet = when (case) {
        U7ScreenshotTest.Case.UserCard -> ChatSheet.UserCard(state.lines.first().message)
        U7ScreenshotTest.Case.Poll -> ChatSheet.Poll
        U7ScreenshotTest.Case.Prediction -> ChatSheet.Prediction
        U7ScreenshotTest.Case.Points -> ChatSheet.Points
        else -> null
    }
    when {
        sheet != null -> Box(Modifier.fillMaxSize()) {
            SheetSurface(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) { ChatSheetContent(state, sheet, actions) }
        }
        case == U7ScreenshotTest.Case.Viewers -> Box(Modifier.fillMaxSize()) {
            SheetSurface(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                Column { ViewerListContent(ChannelViewerList(listOf("lagoonfm"), listOf("modfish", "kelp"), listOf("vipfish"), listOf("fishfan", "other", "tidepool"), 8), true) {} }
            }
        }
        else -> ChatScreenInPlace(state, actions)
    }
}
