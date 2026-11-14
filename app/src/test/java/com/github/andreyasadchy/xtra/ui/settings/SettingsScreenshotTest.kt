package com.github.andreyasadchy.xtra.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.andreyasadchy.xtra.testing.FormFactor
import com.github.andreyasadchy.xtra.testing.ScreenshotRules
import com.github.andreyasadchy.xtra.ui.design.components.rememberToastHostState
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.login.LoginScreen
import com.github.andreyasadchy.xtra.ui.login.LoginStep
import com.github.andreyasadchy.xtra.ui.login.LoginUiState
import com.github.andreyasadchy.xtra.ui.settings.model.PageId
import com.github.andreyasadchy.xtra.util.C
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines of settings and login (plan 10.2, U3): the home screen, the ad blocking page with statistics,
 * the appearance page with its preview, a long restyled page (chat), a sub-page, search results, an empty source
 * list, and the login intro and device-code steps; phone portrait, phone landscape, tablet landscape (two panes),
 * and phone portrait at 1.3x font size.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest(private val case: Case, private val variant: Variant) {

    enum class Case { Home, AdBlock, Appearance, Chat, PlayerButtons, Search, CustomProxyEmpty, LoginIntro, LoginCode }

    enum class Variant(val formFactor: FormFactor, val fontScale: Float) {
        PhonePortrait(FormFactor.PhonePortrait, 1f),
        PhoneLandscape(FormFactor.PhoneLandscape, 1f),
        TabletLandscape(FormFactor.TabletLandscape, 1f),
        PhonePortraitFont130(FormFactor.PhonePortrait, 1.3f),
    }

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val rules = ScreenshotRules()

    private val host = object : SettingsHost {
        override val environment = SettingsEnvironment(versionName = "0.2", networkLibraries = listOf("Cronet", "OkHttp"))
        override val account = AccountInfo(true, "lagoon_viewer")
    }

    @Test
    fun capture() {
        variant.formFactor.apply()
        RuntimeEnvironment.setFontScale(variant.fontScale)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context.getSharedPreferences("shot_app", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        val token = context.getSharedPreferences("shot_token", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        if (case == Case.AdBlock) {
            // the counters AdStats keeps, as after a few evenings of watching
            app.edit().putInt("ad_stats_breaks", 46).putLong("ad_stats_seconds", 1385)
                .putInt("ad_stats_by_swap web/highlighter", 31).putInt("ad_stats_by_filtered", 9)
                .putInt("ad_stats_by_proxy eu.luminous.dev", 5).putInt("ad_stats_by_hidden", 1).commit()
        }
        if (case == Case.Appearance) app.edit().putString(C.UI_NAME_DISPLAY, "1").commit()
        val store = SettingsStore(app, token)
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                Box(Modifier.fillMaxSize().testTag("page")) {
                    when (case) {
                        Case.LoginIntro, Case.LoginCode -> {
                            val state = remember {
                                LoginUiState().apply {
                                    step = if (case == Case.LoginCode) LoginStep.Code else LoginStep.Intro
                                    deviceCode = "QXWHPJRT"
                                }
                            }
                            LoginScreen(state, null, null, rememberToastHostState(), {}, {}, {}, {}, {}, {}, {})
                        }
                        else -> {
                            val page = when (case) {
                                Case.AdBlock -> PageId.AdBlock
                                Case.Appearance -> PageId.Appearance
                                Case.Chat -> PageId.Chat
                                Case.PlayerButtons -> PageId.PlayerButtons
                                Case.CustomProxyEmpty -> PageId.CustomProxy
                                else -> null
                            }
                            SettingsApp(store, host, rememberSettingsNav(page))
                        }
                    }
                }
            }
        }
        if (case == Case.Search) compose.onNodeWithTag(SettingsSearchFieldTag).performTextInput("quality")
        compose.waitForIdle()
        compose.onNodeWithTag("page").captureRoboImage(ScreenshotRules.path("Settings-${case.name}", variant.formFactor).let {
            if (variant.fontScale != 1f) it.replace(".png", "-Font130.png") else it
        })
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun params(): List<Array<Any>> = Case.entries.flatMap { c -> Variant.entries.map { v -> arrayOf<Any>(c, v) } }
    }
}
