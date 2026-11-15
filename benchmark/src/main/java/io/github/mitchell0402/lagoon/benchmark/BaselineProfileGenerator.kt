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
 * Following page, the navigation, and the settings home. The rule writes `...-baseline-prof.txt` to the connected
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
        startActivityAndWait()
        device.waitForIdle()
        // the shell's navigation: Browse, Me, back to Following
        listOf("Browse", "浏览", "Me", "我的", "Following", "关注").forEach { label ->
            device.findObject(By.text(label))?.let {
                it.click()
                device.waitForIdle()
            }
        }
        // the account sheet and the settings home
        device.findObject(By.desc("Account and settings"))?.click() ?: device.findObject(By.desc("账号与设置"))?.click()
        device.wait(Until.hasObject(By.text("Settings")), 2_000)
        (device.findObject(By.text("Settings")) ?: device.findObject(By.text("设置")))?.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }
}
