package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.runtime.Immutable
import com.github.andreyasadchy.xtra.ui.settings.model.ChoiceSetting
import com.github.andreyasadchy.xtra.ui.settings.model.SettingAction
import com.github.andreyasadchy.xtra.ui.settings.model.SettingCondition
import com.github.andreyasadchy.xtra.ui.settings.model.StoredSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** What the device offers: features that show or hide rows, and options known only at run time. */
@Immutable
data class SettingsEnvironment(
    val pictureInPicture: Boolean = true,
    val translate: Boolean = false,
    val androidS: Boolean = true,
    /** Network libraries this device can use; fewer than two hides the choice. */
    val networkLibraries: List<String> = listOf("OkHttp"),
    /** Translation target languages: code to display name. */
    val translateLanguages: List<Pair<String, String>> = emptyList(),
    val versionName: String = "",
) {
    fun allows(condition: SettingCondition): Boolean = when (condition) {
        SettingCondition.Always -> true
        SettingCondition.PictureInPicture -> pictureInPicture
        SettingCondition.TranslateSupported -> translate
        SettingCondition.AndroidS -> androidS
        SettingCondition.NetworkChoice -> networkLibraries.size > 1
    }
}

@Immutable
data class AccountInfo(val loggedIn: Boolean, val name: String?)

/** What a settings page asks of its activity: actions with system UI, side effects of a change, the account. */
interface SettingsHost {
    val environment: SettingsEnvironment
    val account: AccountInfo

    /** Short confirmations and errors the activity reports (backup written, no update found, ...). */
    val messages: Flow<String> get() = emptyFlow()

    /** Runs an action row. Destructive ones arrive here after the page confirmed them. */
    fun runAction(action: SettingAction) {}

    /** Called after a stored setting was written, for its side effects (permissions, workers, locales). */
    fun afterChange(setting: StoredSetting, value: Any?) {}

    /** The value a choice shows when it is not simply read from storage (the app language comes from the system). */
    fun currentChoice(setting: ChoiceSetting): String? = null

    /** Translation models on the device, for the downloaded-languages sheet. */
    suspend fun downloadedLanguages(): List<String> = emptyList()
    fun setDownloadedLanguages(keep: List<String>) {}

    fun login() {}
    fun logout() {}
    fun close() {}

    /** The proxy and video swap lists; null in tests that do not open them. */
    val lists: SettingsLists? get() = null
}
