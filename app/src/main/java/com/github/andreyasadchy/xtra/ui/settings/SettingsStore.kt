package com.github.andreyasadchy.xtra.ui.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.ui.settings.model.ChoiceSetting
import com.github.andreyasadchy.xtra.ui.settings.model.PrefStore
import com.github.andreyasadchy.xtra.ui.settings.model.SliderSetting
import com.github.andreyasadchy.xtra.ui.settings.model.StoredSetting
import com.github.andreyasadchy.xtra.ui.settings.model.TabOrderSetting
import com.github.andreyasadchy.xtra.ui.settings.model.TextSetting
import com.github.andreyasadchy.xtra.ui.settings.model.ToggleSetting

/**
 * Reads and writes the settings for the Compose pages. Reads go through [version], so a page recomposes when any
 * value changes, including changes made elsewhere (a preset setting five switches, a restore).
 *
 * Every read falls back to the setting's default when the stored value has another type (a hand-edited or very old
 * backup), so a bad value never crashes the page.
 */
@Stable
class SettingsStore(
    private val app: SharedPreferences,
    private val token: SharedPreferences,
    /** Called after a write of a setting that needs the main screen to rebuild ([StoredSetting.refresh]). */
    private val onRefreshNeeded: () -> Unit = {},
) {
    private var version by mutableIntStateOf(0)

    // SharedPreferences keeps listeners weakly, so the store holds the reference
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }

    init {
        app.registerOnSharedPreferenceChangeListener(listener)
        token.registerOnSharedPreferenceChangeListener(listener)
    }

    fun close() {
        app.unregisterOnSharedPreferenceChangeListener(listener)
        token.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun prefs(store: PrefStore): SharedPreferences = if (store == PrefStore.Token) token else app

    /** Read inside a composition to recompose on any change. */
    fun observe(): Int = version

    fun bool(setting: ToggleSetting): Boolean = bool(setting.key, setting.default, setting.store)

    fun bool(key: String, default: Boolean, store: PrefStore = PrefStore.App): Boolean {
        version
        return try {
            prefs(store).getBoolean(key, default)
        } catch (e: ClassCastException) {
            default
        }
    }

    fun string(setting: ChoiceSetting): String = string(setting.key, setting.default, setting.store) ?: setting.default

    fun string(setting: TextSetting): String? = string(setting.key, setting.default, setting.store)

    fun string(setting: TabOrderSetting): String = string(setting.key, null, setting.store) ?: setting.default

    fun string(key: String, default: String?, store: PrefStore = PrefStore.App): String? {
        version
        return try {
            prefs(store).getString(key, default)
        } catch (e: ClassCastException) {
            default
        }
    }

    fun int(setting: SliderSetting): Int {
        version
        return try {
            prefs(setting.store).getInt(setting.key, setting.default)
        } catch (e: ClassCastException) {
            setting.default
        }
    }

    fun isSet(key: String, store: PrefStore = PrefStore.App): Boolean {
        version
        return prefs(store).contains(key)
    }

    fun put(setting: ToggleSetting, value: Boolean) = write(setting) { putBoolean(setting.key, value) }

    fun put(setting: ChoiceSetting, value: String) = write(setting) { putString(setting.key, value) }

    fun put(setting: SliderSetting, value: Int) = write(setting) { putInt(setting.key, value) }

    fun put(setting: TabOrderSetting, value: String) = write(setting) { putString(setting.key, value) }

    /** [value] null removes the key (the v0.1 "not set" state of tokens and the update URL). */
    fun put(setting: TextSetting, value: String?) = write(setting) {
        if (value == null) remove(setting.key) else putString(setting.key, value)
    }

    fun putBooleans(values: Map<String, Boolean>) {
        app.edit { values.forEach { (key, value) -> putBoolean(key, value) } }
    }

    private inline fun write(setting: StoredSetting, crossinline block: SharedPreferences.Editor.() -> Unit) {
        prefs(setting.store).edit { block() }
        if (setting.refresh) onRefreshNeeded()
    }
}
