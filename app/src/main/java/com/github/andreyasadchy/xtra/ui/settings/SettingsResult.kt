package com.github.andreyasadchy.xtra.ui.settings

import com.github.andreyasadchy.xtra.ui.settings.model.Refresh

/** What the main screen does with settings' result (pure, SettingsRefreshTest). */
object SettingsResult {
    /**
     * [ok] is RESULT_OK (something changed), [extra] the [Refresh] name settings put in. A result from before the
     * levels existed (no extra) or with an unknown name rebuilds, as every change did before.
     */
    fun refresh(ok: Boolean, extra: String?): Refresh {
        if (!ok) return Refresh.None
        return extra?.let { name -> Refresh.entries.firstOrNull { it.name == name } } ?: Refresh.Recreate
    }
}
