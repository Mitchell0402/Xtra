package com.github.andreyasadchy.xtra.ui.settings

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DEFAULT_UPDATE_URL
import com.github.andreyasadchy.xtra.util.updateUrl
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The in-app update check goes to Lagoon's releases (plan 10.7), whatever v0.1's settings page stored. */
@RunWith(AndroidJUnit4::class)
class UpdateUrlTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("update-url-test", Context.MODE_PRIVATE)

    @Test
    fun unsetGoesToLagoonReleases() {
        prefs.edit { clear() }
        assertEquals(DEFAULT_UPDATE_URL, prefs.updateUrl())
    }

    @Test
    fun theUpstreamDefaultV01StoredCountsAsUnset() {
        prefs.edit { putString(C.UPDATE_URL, "https://api.github.com/repos/crackededed/xtra/releases/tags/latest") }
        assertEquals(DEFAULT_UPDATE_URL, prefs.updateUrl())
        // the stored value stays as it was (plan 10.1)
        assertEquals("https://api.github.com/repos/crackededed/xtra/releases/tags/latest", prefs.getString(C.UPDATE_URL, null))
    }

    @Test
    fun anAddressTheUserTypedIsKept() {
        prefs.edit { putString(C.UPDATE_URL, "https://example.org/releases/latest") }
        assertEquals("https://example.org/releases/latest", prefs.updateUrl())
    }
}
