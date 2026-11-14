package com.github.andreyasadchy.xtra.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.player.ads.AdBlockPreset
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.settings.model.ActionRow
import com.github.andreyasadchy.xtra.ui.settings.model.PageId
import com.github.andreyasadchy.xtra.ui.settings.model.SettingAction
import com.github.andreyasadchy.xtra.ui.settings.model.SettingsCatalog
import com.github.andreyasadchy.xtra.ui.settings.model.StoredSetting
import com.github.andreyasadchy.xtra.ui.settings.model.TextKind
import com.github.andreyasadchy.xtra.util.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/** Settings behaviour (plan 10.2 interaction tests, U3): rows write the right keys, sheets, presets, search. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class SettingsBehaviourTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var app: SharedPreferences
    private lateinit var token: SharedPreferences
    private lateinit var store: SettingsStore
    private val host = RecordingHost()

    class RecordingHost : SettingsHost {
        override val environment = SettingsEnvironment(translate = true, translateLanguages = listOf("en" to "English", "de" to "German"), networkLibraries = listOf("Cronet", "OkHttp"), versionName = "0.2")
        override val account = AccountInfo(true, "mitchell")
        val actions = mutableListOf<SettingAction>()
        val changes = mutableListOf<Pair<String, Any?>>()
        override fun runAction(action: SettingAction) { actions += action }
        override fun afterChange(setting: StoredSetting, value: Any?) { changes += setting.key to value }
    }

    @Before
    fun setUp() {
        app = context.getSharedPreferences("test_app", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        token = context.getSharedPreferences("test_token", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        store = SettingsStore(app, token)
    }

    private fun show(page: PageId? = null) {
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                SettingsApp(store, host, rememberSettingsNav(page))
            }
        }
    }

    @Test
    fun homeShowsNineCategoriesAndTheAccount() {
        show()
        compose.onNodeWithText("mitchell").assertIsDisplayed()
        listOf("Ad blocking", "Playback", "Chat", "Downloads and recording", "Appearance", "Notifications", "Backup and restore", "Advanced", "About and updates").forEach {
            compose.onNodeWithTag(SettingsListTag).performScrollToNode(hasText(it))
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun switchWritesItsKey() {
        show(PageId.Playback)
        assertTrue(app.getBoolean(C.PLAYER_BACKGROUND_AUDIO, true))
        compose.onNodeWithText("Play audio in the background").performClick()
        assertFalse(app.getBoolean(C.PLAYER_BACKGROUND_AUDIO, true))
        assertEquals(C.PLAYER_BACKGROUND_AUDIO to false, host.changes.last())
    }

    @Test
    fun choiceSheetWritesTheValue() {
        show(PageId.Playback)
        compose.onNodeWithText("Default player quality").performClick()
        compose.onNodeWithText("720p60").performClick()
        compose.waitForIdle()
        assertEquals("720p60", app.getString(C.PLAYER_DEFAULT_QUALITY, null))
        compose.onAllNodesWithText("720p60").onFirst().assertIsDisplayed()
    }

    @Test
    fun dependentRowsFollowTheirSwitch() {
        show(PageId.Chat)
        compose.onAllNodesWithText("Timestamp format").assertCountEquals(0)
        compose.onNodeWithText("Show timestamps").performClick()
        compose.onNodeWithText("Timestamp format").assertIsDisplayed()
    }

    @Test
    fun presetSetsTheSwitches() {
        show(PageId.AdBlock)
        compose.onNodeWithText("Balanced").performClick()
        AdBlockPreset.BALANCED.values.forEach { (key, value) -> assertEquals(key, value, app.getBoolean(key, !value)) }
        // changing one switch away from the preset shows the custom note
        compose.onNodeWithTag(SettingsListTag).performScrollToNode(hasText("Filter ad segments"))
        compose.onNodeWithText("Filter ad segments").performClick()
        assertEquals(AdBlockPreset.CUSTOM, AdBlockPreset.match { k, d -> app.getBoolean(k, d) })
    }

    @Test
    fun textSheetValidatesNumbersAndEmptyRestoresTheDefault() {
        assertFalse(isValidSettingText(TextKind.Integer, "12a"))
        assertTrue(isValidSettingText(TextKind.Integer, ""))
        assertFalse(isValidSettingText(TextKind.Decimal, "1..5"))
        show(PageId.Buffer)
        compose.onNodeWithText("Minimum buffer").performClick()
        compose.onNodeWithTag(SettingsTextFieldTag).performTextReplacement("20000")
        compose.onNodeWithTag(SettingsSheetSaveTag).performClick()
        assertEquals("20000", app.getString(C.PLAYER_BUFFER_MIN, null))
        compose.onNodeWithText("Minimum buffer").performClick()
        compose.onNodeWithTag(SettingsTextFieldTag).performTextReplacement("")
        compose.onNodeWithTag(SettingsSheetSaveTag).performClick()
        assertFalse(app.contains(C.PLAYER_BUFFER_MIN))
    }

    @Test
    fun tokensAreWrittenToTheTokenFile() {
        show(PageId.ApiTokens)
        compose.onNodeWithText("Account login").performClick()
        compose.onNodeWithTag(SettingsTextFieldTag).performTextInput("someone")
        compose.onNodeWithTag(SettingsSheetSaveTag).performClick()
        assertEquals("someone", token.getString(C.USERNAME, null))
        assertFalse(app.contains(C.USERNAME))
    }

    @Test
    fun destructiveActionsAskFirst() {
        show(PageId.Appearance)
        compose.onNodeWithTag(SettingsListTag).performScrollToNode(hasText("Delete recent searches"))
        compose.onNodeWithText("Delete recent searches").performClick()
        assertTrue(host.actions.isEmpty())
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf(SettingAction.DeleteRecentSearches), host.actions)
    }

    @Test
    fun tabOrderSheetKeepsTheV01Format() {
        show(PageId.Appearance)
        compose.onNodeWithTag(SettingsListTag).performScrollToNode(hasText("Following tabs"))
        compose.onNodeWithText("Following tabs").performClick()
        compose.onNodeWithContentDescription("Open Videos first").performClick()
        compose.onNodeWithTag(SettingsSheetSaveTag).performClick()
        assertEquals("0:0:1,1:0:1,2:1:1,3:0:1", app.getString(C.UI_FOLLOWING_TABS, null))
    }

    @Test
    fun searchFindsARowAndOpensItsPage() {
        show()
        compose.onNodeWithTag(SettingsSearchFieldTag).performTextInput("rebuffer")
        compose.onNodeWithText("Rebuffer").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Buffer settings").assertIsDisplayed()
        compose.onAllNodesWithText("Rebuffer").onFirst().assertIsDisplayed()
    }

    @Test
    fun aBadStoredTypeFallsBackToTheDefault() {
        // a hand-edited or very old backup: a string where a switch is expected, an int where text is
        app.edit().putString(C.PLAYER_PICTURE_IN_PICTURE, "yes").putInt(C.PLAYER_BUFFER_MIN, 5).commit()
        show(PageId.Playback)
        compose.onNodeWithText("Picture-in-Picture").assertIsDisplayed()
        compose.onNodeWithText("Play audio in the background").assertIsDisplayed()
    }

    @Test
    fun everyRowCanBeFoundBySearch() {
        listOf(Locale.US, Locale.SIMPLIFIED_CHINESE).forEach { locale ->
            val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
            val localized = context.createConfigurationContext(config)
            val index = SettingsSearch.index(localized, host.environment)
            val missing = SettingsCatalog.pages.flatMap { page -> page.items.map { page to it } }.filter { (page, item) ->
                host.environment.allows(item.condition) &&
                        SettingsSearch.search(localized.getString(item.title), index).none { it.id == item.id && it.page == page.id }
            }
            assertTrue("$locale: not found by title: ${missing.map { it.second.id }}", missing.isEmpty())
            // a page by its name, a row by words from its explanation
            assertTrue(SettingsSearch.search(localized.getString(SettingsCatalog.buffer.title), index).first().isPage)
        }
    }

    @Test
    fun tabOrderRepairsStoredLists() {
        // an unknown tab is dropped, a missing one goes back to its default place
        val tabs = TabOrder.parse("9:0:1,1:1:1,0:0:0", "0:0:1,1:1:1,2:0:1")
        assertEquals(listOf("1", "0", "2"), tabs.map { it.key })
        assertFalse(tabs[1].enabled)
        assertEquals("1:1:1,0:0:0,2:0:1", TabOrder.format(tabs))
        // hiding the default tab hands the default on
        val hidden = TabOrder.setEnabled(tabs, "1", false)
        assertEquals(listOf(false, false, true), hidden.map { it.isDefault })
        assertEquals(listOf("0", "1", "2"), TabOrder.move(tabs, 0, 1).map { it.key })
    }

    @Test
    fun actionRowsAllHaveAHandler() {
        val rows = SettingsCatalog.pages.flatMap { it.items }.filterIsInstance<ActionRow>()
        assertTrue(rows.isNotEmpty())
        rows.filter { it.destructive }.forEach { assertTrue(it.id, it.action in setOf(SettingAction.DeleteRecentSearches, SettingAction.DeleteVideoPositions, SettingAction.ResetAdStats)) }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals(n: Int) {
    assertEquals(n, fetchSemanticsNodes().size)
}
