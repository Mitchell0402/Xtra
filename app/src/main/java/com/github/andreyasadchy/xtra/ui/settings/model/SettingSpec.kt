package com.github.andreyasadchy.xtra.ui.settings.model

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The declarative settings list (plan 8, "设置：丢弃系统 Preference，改成一份声明式的设置清单驱动界面").
 *
 * Every stored setting keeps the storage key, storage type and default it had in v0.1: the default is the one the
 * code that reads the key uses (plan 10.2), which [SettingsCatalogTest] checks against every read in the app and
 * against the v0.1 preference XML kept in the test resources.
 */

/** Which SharedPreferences file holds a key: the app's default preferences, or the token file (account data). */
enum class PrefStore { App, Token }

/** How a text setting is typed and checked. */
enum class TextKind {
    Text,
    Url,
    /** Whole numbers only (buffer sizes, seconds, minutes). */
    Integer,
    /** Numbers with a decimal point (sizes, speeds). */
    Decimal,
    /** Several lines (the playback speed list). */
    Multiline,
    /** Tokens and headers: shown as "set" or "not set", never in full on the row. */
    Secret,
}

/** When a row shows at all; resolved by [SettingsEnvironment] (the device and Android version). */
enum class SettingCondition { Always, PictureInPicture, TranslateSupported, AndroidS, NetworkChoice }

/** One-shot actions a row runs instead of storing a value. */
enum class SettingAction {
    DeleteRecentSearches,
    DeleteVideoPositions,
    ImportAppDownloads,
    Backup,
    Restore,
    CheckUpdates,
    AdLog,
    ResetAdStats,
    DeviceAdminSettings,
    IntegrityToken,
    DownloadedLanguages,
    OpenProjectPage,
}

/** The pages under the settings home (plan 7.7: nine categories) and their sub-pages. */
enum class PageId {
    Appearance, Playback, PlayerButtons, PlayerMenu, Chat, AdBlock, VideoSwap, CustomProxy, StreamProxy,
    Downloads, Notifications, Backup, Advanced, Buffer, ApiTokens, Debug, About,
}

/** Where a choice row takes its options from. */
@Immutable
sealed interface ChoiceSource {
    /** Labels and values from string arrays (the v0.1 ListPreference arrays). */
    data class Arrays(@param:ArrayRes val entries: Int, @param:ArrayRes val values: Int) : ChoiceSource
    /** Options known only on the device (network libraries, translation languages). */
    data class Dynamic(val id: String) : ChoiceSource
}

@Immutable
sealed interface SettingItem {
    /** The storage key for stored settings, a stable id for actions and links (also the search target). */
    val id: String
    @get:StringRes val title: Int
    @get:StringRes val summary: Int?
    val icon: ImageVector?
    val condition: SettingCondition
    /** Key of the switch on the same page this row depends on: hidden (collapsed) while that switch is off. */
    val dependsOn: String?
}

/** A stored setting (switch, choice, slider or text). */
@Immutable
sealed interface StoredSetting : SettingItem {
    val key: String get() = id
    val store: PrefStore
    /** Changing it needs the main screen to rebuild when settings closes (the old pages read it once). */
    val refresh: Boolean
}

data class ToggleSetting(
    override val id: String,
    val default: Boolean,
    @param:StringRes override val title: Int,
    @param:StringRes override val summary: Int? = null,
    override val icon: ImageVector? = null,
    override val condition: SettingCondition = SettingCondition.Always,
    override val dependsOn: String? = null,
    override val store: PrefStore = PrefStore.App,
    override val refresh: Boolean = false,
) : StoredSetting

data class ChoiceSetting(
    override val id: String,
    val default: String,
    @param:StringRes override val title: Int,
    val source: ChoiceSource,
    @param:StringRes override val summary: Int? = null,
    override val icon: ImageVector? = null,
    override val condition: SettingCondition = SettingCondition.Always,
    override val dependsOn: String? = null,
    override val store: PrefStore = PrefStore.App,
    override val refresh: Boolean = false,
) : StoredSetting

/** A whole number on a slider, stored as an Int (the v0.1 SeekBarPreference keys). */
data class SliderSetting(
    override val id: String,
    val default: Int,
    val min: Int,
    val max: Int,
    @param:StringRes override val title: Int,
    @param:StringRes override val summary: Int? = null,
    /** Format for the value, with one %d (or %s) placeholder; null shows the bare number. */
    @param:StringRes val unit: Int? = null,
    val step: Int = 1,
    override val icon: ImageVector? = null,
    override val condition: SettingCondition = SettingCondition.Always,
    override val dependsOn: String? = null,
    override val store: PrefStore = PrefStore.App,
    override val refresh: Boolean = false,
) : StoredSetting

/** Free text, stored as a String (the v0.1 EditTextPreference keys). [default] null means "not set". */
data class TextSetting(
    override val id: String,
    val default: String?,
    @param:StringRes override val title: Int,
    val kind: TextKind = TextKind.Text,
    @param:StringRes override val summary: Int? = null,
    /** Format for the value on the row, with one %s placeholder (units such as "10 s"). */
    @param:StringRes val unit: Int? = null,
    override val icon: ImageVector? = null,
    override val condition: SettingCondition = SettingCondition.Always,
    override val dependsOn: String? = null,
    override val store: PrefStore = PrefStore.App,
    override val refresh: Boolean = false,
) : StoredSetting

/**
 * The order, visibility and default tab of a set of tabs (the v0.1 drag-list dialogs): stored as
 * "key:default:enabled" items joined by commas, exactly as before.
 */
data class TabOrderSetting(
    override val id: String,
    val default: String,
    @param:StringRes override val title: Int,
    /** Tab key to its label. */
    val tabs: List<Pair<String, Int>>,
    @param:StringRes override val summary: Int? = null,
    override val icon: ImageVector? = null,
    override val condition: SettingCondition = SettingCondition.Always,
    override val dependsOn: String? = null,
    override val store: PrefStore = PrefStore.App,
    override val refresh: Boolean = true,
) : StoredSetting

data class ActionRow(
    override val id: String,
    val action: SettingAction,
    @param:StringRes override val title: Int,
    @param:StringRes override val summary: Int? = null,
    override val icon: ImageVector? = null,
    val destructive: Boolean = false,
    override val condition: SettingCondition = SettingCondition.Always,
    override val dependsOn: String? = null,
) : SettingItem

data class PageLink(
    override val id: String,
    val page: PageId,
    @param:StringRes override val title: Int,
    @param:StringRes override val summary: Int? = null,
    override val icon: ImageVector? = null,
    override val condition: SettingCondition = SettingCondition.Always,
    override val dependsOn: String? = null,
) : SettingItem

@Immutable
data class SettingsGroupSpec(
    @param:StringRes val title: Int?,
    val items: List<SettingItem>,
    @param:StringRes val description: Int? = null,
)

@Immutable
data class SettingsPageSpec(
    val id: PageId,
    @param:StringRes val title: Int,
    val icon: ImageVector,
    val groups: List<SettingsGroupSpec>,
    /** One line under the page's row on the home screen (plan 7.7: an icon and one line per category). */
    @param:StringRes val summary: Int? = null,
    /** The page this one opens from (search shows it as a breadcrumb). */
    val parent: PageId? = null,
    /** Teal icon tile: the one row the home screen wants to stand out (ad blocking, as in the design). */
    val highlight: Boolean = false,
) {
    val items: List<SettingItem> get() = groups.flatMap { it.items }
}
