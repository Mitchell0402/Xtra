package io.github.mitchell0402.lagoon.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile (plan 8: "加入基线配置文件，避免 Compose 首次启动变慢"): cold start to the
 * Following page, the navigation (U8: Compose navigation, a game opened from Browse), and the settings home. The rule writes `...-baseline-prof.txt` to the connected
 * test output; copy it to app/src/main/baseline-prof.txt. Run on an emulator or device with API 33 or newer:
 *   gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.mitchell0402.lagoon.benchmark.BaselineProfileGenerator
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndNavigation() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startMainActivity()
        device.waitForIdle()
        // the shell's navigation: Browse, a game, back, Me, back to Following (unselected items show only their
        // icon, so look them up by text or by description)
        fun tab(vararg labels: String) {
            labels.firstNotNullOfOrNull { device.findObject(By.text(it)) ?: device.findObject(By.desc(it)) }?.let {
                it.click()
                device.waitForIdle()
            }
        }
        tab("Browse", "浏览")
        device.wait(Until.hasObject(By.res("gamesGrid")), 5_000)
        device.findObject(By.res("gamesGrid"))?.children?.firstOrNull()?.let {
            it.click()
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }
        tab("Me", "我的")
        tab("Following", "关注")
        // the account sheet and the settings home
        device.findObject(By.desc("Account and settings"))?.click() ?: device.findObject(By.desc("账号与设置"))?.click()
        device.wait(Until.hasObject(By.text("Settings")), 2_000)
        (device.findObject(By.text("Settings")) ?: device.findObject(By.text("设置")))?.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }
}
