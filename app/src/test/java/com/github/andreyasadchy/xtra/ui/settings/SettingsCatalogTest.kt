package com.github.andreyasadchy.xtra.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.ui.settings.model.ActionRow
import com.github.andreyasadchy.xtra.ui.settings.model.ChoiceSetting
import com.github.andreyasadchy.xtra.ui.settings.model.PageLink
import com.github.andreyasadchy.xtra.ui.settings.model.PrefStore
import com.github.andreyasadchy.xtra.ui.settings.model.SettingsCatalog
import com.github.andreyasadchy.xtra.ui.settings.model.SliderSetting
import com.github.andreyasadchy.xtra.ui.settings.model.StoredSetting
import com.github.andreyasadchy.xtra.ui.settings.model.TabOrderSetting
import com.github.andreyasadchy.xtra.ui.settings.model.TextSetting
import com.github.andreyasadchy.xtra.ui.settings.model.ToggleSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The settings list against v0.1 (plan 10.1 and 10.2, U3 gate): every key keeps its storage type and default, every
 * v0.1 entry is kept, moved or removed on purpose, and every key the player service and the ad code read is there
 * with the default they use.
 *
 * The v0.1 preference XML is kept in src/test/resources/legacy-preferences. "The default" is the one the code that
 * reads the key passes, which wins where the XML differs (plan 10.2); [xmlDiffers] lists those cases.
 */
@RunWith(AndroidJUnit4::class)
class SettingsCatalogTest {

    private val legacyDir = File("src/test/resources/legacy-preferences")
    private val src = File("src/main/java/com/github/andreyasadchy/xtra")

    data class Legacy(val file: String, val tag: String, val key: String, val default: String?)

    private val legacy: List<Legacy> by lazy {
        legacyDir.listFiles()!!.sortedBy { it.name }.flatMap { file ->
            Regex("""<([\w.]+)\s([^>]*?)/?>""", RegexOption.DOT_MATCHES_ALL).findAll(file.readText()).mapNotNull { m ->
                val attrs = Regex("""(?:android|app):(\w+)="([^"]*)"""").findAll(m.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
                attrs["key"]?.let { Legacy(file.name, m.groupValues[1].substringAfterLast('.'), it, attrs["defaultValue"]) }
            }.toList()
        }
    }

    private val constants: Map<String, String> by lazy {
        Regex("""const val (\w+)\s*=\s*"([^"]*)"""").findAll(src.resolve("util/C.kt").readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    data class Read(val file: String, val type: String, val key: String, val default: String)

    /** Every `prefs.getX(C.KEY or "key", default)` in the app. */
    private val reads: List<Read> by lazy {
        val pattern = Regex("""\.get(Boolean|String|Int|Long|Float)\(\s*(?:C\.(\w+)|"([^"]+)")\s*,\s*((?:[^()]|\([^()]*\))*)\)""")
        src.walkTopDown().filter { it.extension == "kt" && "ui/settings/" !in it.invariantSeparatorsPath }.flatMap { file ->
            pattern.findAll(file.readText()).mapNotNull { m ->
                val key = if (m.groupValues[2].isNotEmpty()) constants[m.groupValues[2]] else m.groupValues[3]
                key?.let { Read(file.invariantSeparatorsPath.substringAfter("xtra/"), m.groupValues[1], it, m.groupValues[4].trim()) }
            }.toList()
        }.toList()
    }

    private val stored: Map<String, StoredSetting> by lazy { SettingsCatalog.stored.associate { it.second.key to it.second } }

    /** XML defaults that never applied: the code that reads the key passes another one, and that one stays. */
    private val xmlDiffers = mapOf(
        // ListPreference persisted the chosen language, but the value shown comes from the app locales; "auto" = none
        "ui_language" to "auto",
        // the XML pointed at upstream Xtra's releases; v0.1 reads null and then uses the Lagoon releases
        "update_url" to null,
        // the XML default was a resource (1 or 2 on phones, 2 or 3 on tablets); the old pages read "1" and "2"
        "columnsPortrait" to "1",
        "columnsLandscape" to "2",
    )

    @Test
    fun idsAreUnique() {
        val ids = SettingsCatalog.pages.flatMap { page -> page.items.map { it.id } }
        assertEquals(ids.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.toString(), ids.size, ids.toSet().size)
    }

    @Test
    fun legacyPreferencesAreAllHere() {
        // the U0 inventory's 225 entries plus "ui_glass_effects", added in stage G
        assertEquals(226, legacy.size)
        // v0.1 Preference rows that only opened a page or dialog: their keys were never stored
        val links = SettingsCatalog.pages.flatMap { it.items }.filter { it is PageLink || it is ActionRow }.map { it.id }.toSet()
        val categoryRows = setOf(
            "ui_settings", "chat_settings", "player_settings", "player_button_settings", "buffer_settings", "ad_block_settings",
            "playback_settings", "api_token_settings", "download_settings", "update_settings", "debug_settings",
            "player_menu_settings", "ad_block_advanced", "ad_block_stats",
        )
        val tabDialogs = mapOf(
            "ui_navigation_tab_list_dialog" to "ui_navigation_tab_list", "ui_following_tabs_dialog" to "ui_following_tabs",
            "ui_saved_tabs_dialog" to "ui_saved_tabs", "ui_channel_tabs_dialog" to "ui_channel_tabs",
            "ui_game_tabs_dialog" to "ui_game_tabs", "ui_search_tabs_dialog" to "ui_search_tabs",
        )
        val unaccounted = legacy.filter { entry ->
            val key = entry.key
            when {
                key in SettingsCatalog.removed -> false
                entry.tag == "Preference" && (key in links || key in categoryRows || key == "check_updates") -> false
                entry.tag == "Preference" && tabDialogs[key]?.let { stored[it] is TabOrderSetting } == true -> false
                entry.tag == "PreferenceCategory" -> false
                else -> key !in stored
            }
        }
        assertTrue("v0.1 entries neither kept nor removed on purpose: $unaccounted", unaccounted.isEmpty())
    }

    @Test
    fun storageTypesMatchV01() {
        val wrong = legacy.mapNotNull { entry ->
            val setting = stored[entry.key] ?: return@mapNotNull null
            val ok = when (entry.tag) {
                "SwitchPreferenceCompat" -> setting is ToggleSetting
                "ListPreference" -> setting is ChoiceSetting
                "SeekBarPreference" -> setting is SliderSetting
                "EditTextPreference" -> setting is TextSetting
                // v0.1 had a Preference "player_settings" opening the player page and a switch with the same key
                "Preference" -> true
                else -> false
            }
            if (ok) null else "${entry.key}: ${entry.tag} became ${setting::class.simpleName}"
        }
        assertTrue(wrong.toString(), wrong.isEmpty())
    }

    @Test
    fun defaultsMatchV01Xml() {
        val wrong = legacy.mapNotNull { entry ->
            val setting = stored[entry.key] ?: return@mapNotNull null
            if (entry.tag == "Preference") return@mapNotNull null
            val expected = if (entry.key in xmlDiffers) xmlDiffers[entry.key] else entry.default?.replace("\\n", "\n")
            val actual = defaultOf(setting)
            if (expected == actual || (expected == null && actual == "")) null else "${entry.key}: xml $expected, catalogue $actual"
        }
        assertTrue(wrong.toString(), wrong.isEmpty())
    }

    /** Every literal default the app passes when it reads a settings key equals the catalogue's (plan 10.2). */
    @Test
    fun defaultsMatchEveryRead() {
        val checked = mutableSetOf<String>()
        val wrong = reads.mapNotNull { read ->
            val setting = stored[read.key] ?: return@mapNotNull null
            val literal = literal(read.default) ?: return@mapNotNull null
            // MainActivity's one-time migration of the language reads "" for "not set"; "" and "auto" mean the same
            if (read.key == "ui_language" && literal == "") return@mapNotNull null
            checked += read.key
            val actual = defaultOf(setting)
            if (literal == actual) null else "${read.file}: ${read.key} read with $literal, catalogue $actual"
        }
        assertTrue(wrong.toString(), wrong.isEmpty())
        assertTrue("only ${checked.size} keys checked", checked.size > 150)
    }

    /**
     * The keys the player service and the ad code read (plan 10.1: "约 47 个设置键列入白名单"), found by scanning
     * those files: each one that is a setting is on a page, with the same type and default; the rest are player
     * state the service keeps (speed, volume, ...) and never were settings.
     */
    @Test
    fun playerAndAdKeysAreKept() {
        val protected = listOf("player/ads/", "ui/player/ExoPlayerService.kt", "player/lowlatency/", "util/m3u8/PlaylistUtils.kt")
        val protectedReads = reads.filter { read -> protected.any { read.file.startsWith(it) } }
        val keys = protectedReads.map { it.key }.toSet()
        val legacyKeys = legacy.map { it.key }.toSet()
        val settings = keys.filter { it in legacyKeys }
        val missing = settings.filter { it !in stored }
        assertTrue("player and ad keys without a page: $missing", missing.isEmpty())
        val wrong = protectedReads.filter { it.key in stored }.mapNotNull { read ->
            val literal = literal(read.default) ?: return@mapNotNull null
            val actual = defaultOf(stored.getValue(read.key))
            if (literal == actual) null else "${read.file}: ${read.key} $literal vs $actual"
        }
        assertTrue(wrong.toString(), wrong.isEmpty())
        // the scan must keep finding them; a refactor that hides the reads should fail here, not pass silently
        assertTrue("found ${settings.size} settings keys in the player and ad code", settings.size >= 30)
        // the rest is player state written by the player itself
        val state = keys - settings.toSet()
        assertEquals(
            setOf("player_audio_compressor", "player_speed", "player_subtitles_enabled", "player_volume", "token_platform", "token_playertype"),
            state.filterNot { it.startsWith("ad_stats") }.toSet(),
        )
    }

    @Test
    fun tokenKeysStayInTheTokenFile() {
        val token = setOf("user_id", "username", "token", "gql_token2", "gql_token_web", "gql_headers")
        SettingsCatalog.stored.map { it.second }.forEach { setting ->
            assertEquals(setting.key, setting.key in token, setting.store == PrefStore.Token)
        }
    }

    @Test
    fun everyCategoryIsOnTheHomeScreen() {
        assertEquals(9, SettingsCatalog.categories.size)
        SettingsCatalog.pages.filter { it.parent == null }.forEach { assertTrue(it.id.name, it in SettingsCatalog.categories) }
        SettingsCatalog.pages.mapNotNull { it.parent }.forEach { parent ->
            assertTrue("$parent links its sub-pages", SettingsCatalog.page(parent).items.any { it is PageLink })
        }
    }

    private fun defaultOf(setting: StoredSetting): String? = when (setting) {
        is ToggleSetting -> setting.default.toString()
        is ChoiceSetting -> setting.default
        is SliderSetting -> setting.default.toString()
        is TextSetting -> setting.default
        is TabOrderSetting -> setting.default
    }

    /** The default argument as a value, or null when it is an expression (another setting, a variable). */
    private fun literal(code: String): String? = when {
        code == "null" -> null
        code == "true" || code == "false" -> code
        code.matches(Regex("-?\\d+")) -> code
        code.matches(Regex("-?\\d+(\\.\\d+)?f")) -> code.removeSuffix("f")
        code.startsWith("\"") && code.endsWith("\"") -> code.removeSurrounding("\"").replace("\\n", "\n").replace("\\$", "$")
        code.startsWith("C.") -> constants[code.removePrefix("C.")]
        else -> null
    }
}
