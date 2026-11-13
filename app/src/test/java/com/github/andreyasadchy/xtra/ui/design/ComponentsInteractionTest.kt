package com.github.andreyasadchy.xtra.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.ui.design.components.ActionItem
import com.github.andreyasadchy.xtra.ui.design.components.ActionMenu
import com.github.andreyasadchy.xtra.ui.design.components.ChoiceOption
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipItem
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipRow
import com.github.andreyasadchy.xtra.ui.design.components.FloatingNavBar
import com.github.andreyasadchy.xtra.ui.design.components.FollowButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonTextField
import com.github.andreyasadchy.xtra.ui.design.components.LiveCard
import com.github.andreyasadchy.xtra.ui.design.components.MultiChoiceList
import com.github.andreyasadchy.xtra.ui.design.components.NavItem
import com.github.andreyasadchy.xtra.ui.design.components.SegmentedControl
import com.github.andreyasadchy.xtra.ui.design.components.SettingDependents
import com.github.andreyasadchy.xtra.ui.design.components.SettingRow
import com.github.andreyasadchy.xtra.ui.design.components.SettingTrailing
import com.github.andreyasadchy.xtra.ui.design.components.SingleChoiceList
import com.github.andreyasadchy.xtra.ui.design.components.ToastHost
import com.github.andreyasadchy.xtra.ui.design.components.ToastResult
import com.github.andreyasadchy.xtra.ui.design.components.rememberToastHostState
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Behaviour of the design components: selection, toggles, sheets, toasts (plan 10.2, interaction tests). */
@RunWith(AndroidJUnit4::class)
class ComponentsInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun content(block: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent { LagoonTheme(reducedMotion = true) { block() } }

    @Test
    fun navBarSelectsTappedItem() {
        var selected by mutableIntStateOf(0)
        content {
            FloatingNavBar(
                listOf(NavItem("Following", LucideIcons.Heart), NavItem("Browse", LucideIcons.LayoutGrid), NavItem("Mine", LucideIcons.Download)),
                selected, { selected = it },
            )
        }
        compose.onNodeWithContentDescription("Following").assertIsSelected()
        compose.onNodeWithContentDescription("Browse").performClick()
        assertEquals(1, selected)
        compose.onNodeWithContentDescription("Browse").assertIsSelected()
        compose.onNodeWithContentDescription("Following").assertIsNotSelected()
    }

    @Test
    fun segmentedControlSelects() {
        var selected by mutableIntStateOf(0)
        content { SegmentedControl(listOf("Live", "All"), selected, { selected = it }) }
        compose.onNodeWithText("All").performClick()
        assertEquals(1, selected)
        compose.onNodeWithText("All").assertIsSelected()
    }

    @Test
    fun switchRowTogglesAndRevealsDependents() {
        content {
            var on by remember { mutableStateOf(false) }
            Column {
                SettingRow("Background audio", trailing = SettingTrailing.Toggle(on) { on = it })
                SettingDependents(on) { SettingRow("When locked", trailing = SettingTrailing.Toggle(true) {}) }
            }
        }
        compose.onNodeWithText("When locked").assertDoesNotExist()
        compose.onNodeWithText("Background audio").performClick()
        compose.onNodeWithText("When locked").assertIsDisplayed()
        compose.onNodeWithText("Background audio").assertIsOn()
        compose.onNodeWithText("Background audio").performClick()
        compose.onNodeWithText("Background audio").assertIsOff()
        compose.onNodeWithText("When locked").assertDoesNotExist()
    }

    @Test
    fun settingRowClick() {
        var clicks = 0
        content { SettingRow("Playback", trailing = SettingTrailing.Value("Auto"), onClick = { clicks++ }) }
        compose.onNodeWithText("Playback").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun singleChoiceSelects() {
        var selected by mutableIntStateOf(0)
        content { SingleChoiceList(listOf(ChoiceOption("Auto"), ChoiceOption("1080p60")), selected, { selected = it }) }
        compose.onNodeWithText("1080p60").performClick()
        assertEquals(1, selected)
        compose.onNodeWithText("1080p60").assertIsSelected()
        compose.onNodeWithText("Auto").assertIsNotSelected()
    }

    @Test
    fun multiChoiceToggles() {
        var selected by mutableStateOf(setOf(0))
        content {
            MultiChoiceList(listOf(ChoiceOption("中文"), ChoiceOption("English")), selected, { i, on -> selected = if (on) selected + i else selected - i })
        }
        compose.onNodeWithText("English").performClick()
        assertEquals(setOf(0, 1), selected)
        compose.onNodeWithText("中文").performClick()
        assertEquals(setOf(1), selected)
    }

    @Test
    fun actionMenuRunsAction() {
        val ran = mutableListOf<String>()
        content {
            ActionMenu(listOf(ActionItem("Share", LucideIcons.Share2) { ran += "share" }, ActionItem("Unfollow", LucideIcons.Trash2, destructive = true) { ran += "unfollow" }))
        }
        compose.onNodeWithText("Unfollow").performClick()
        assertEquals(listOf("unfollow"), ran)
    }

    @Test
    fun bottomSheetShowsContentAndDismisses() {
        content {
            var open by remember { mutableStateOf(false) }
            Column {
                LagoonButton("Open", { open = true })
                if (open) {
                    LagoonBottomSheet(onDismiss = { open = false }, title = "Quality") {
                        LagoonButton("Close sheet", { open = false })
                    }
                }
            }
        }
        compose.onNodeWithText("Open").performClick()
        compose.onNodeWithText("Quality").assertIsDisplayed()
        compose.onNodeWithText("Close sheet").performClick()
        compose.onNodeWithText("Quality").assertDoesNotExist()
    }

    @Test
    fun toastUndoReportsAction() {
        var result: ToastResult? = null
        content {
            val toasts = rememberToastHostState()
            val scope = rememberCoroutineScope()
            Column {
                LagoonButton("Delete", { scope.launch { result = toasts.show("Deleted", "Undo") } })
                ToastHost(toasts)
            }
        }
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Deleted").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertEquals(ToastResult.ActionPerformed, result)
    }

    @Test
    fun textFieldClearButtonEmptiesIt() {
        var text by mutableStateOf("")
        content { LagoonTextField(text, { text = it }, "Search") }
        compose.onNodeWithText("Search").performTextInput("lirik")
        assertEquals("lirik", text)
        compose.onNodeWithContentDescription("Clear").performClick()
        assertEquals("", text)
    }

    @Test
    fun followButtonToggles() {
        var following by mutableStateOf(false)
        content { FollowButton(following, { following = it }) }
        compose.onNodeWithText("Follow").performClick()
        assertEquals(true, following)
        compose.onNodeWithText("Following").assertIsDisplayed()
    }

    @Test
    fun filterChipClick() {
        var clicked = 0
        content { FilterChipRow(listOf(FilterChipItem("Sort", dropdown = true) { clicked++ }, FilterChipItem("中文") {})) }
        compose.onNodeWithText("Sort").performClick()
        assertEquals(1, clicked)
    }

    @Test
    fun liveCardClickAndLongClick() {
        val events = mutableListOf<String>()
        content { LiveCard("Ranked", "Nightowl", "12,408", onClick = { events += "click" }, onLongClick = { events += "long" }) }
        compose.onNodeWithText("Ranked").performClick()
        assertEquals(listOf("click"), events)
    }
}
