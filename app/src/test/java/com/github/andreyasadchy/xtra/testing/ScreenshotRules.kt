package com.github.andreyasadchy.xtra.testing

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.Locale
import java.util.TimeZone

/** The three layouts every screen is captured in (plan 10.2). */
enum class FormFactor(val qualifiers: String) {
    PhonePortrait(RobolectricDeviceQualifiers.Pixel7),
    PhoneLandscape("${RobolectricDeviceQualifiers.Pixel7}-land"),
    TabletLandscape(RobolectricDeviceQualifiers.MediumTablet),
}

/**
 * Makes screenshots deterministic: animations off, English locale, fixed time zone. Stages that show times or
 * images also inject a fixed clock and fake image data through their own test fakes.
 */
class ScreenshotRules : TestWatcher() {
    override fun starting(description: Description) {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
        Settings.Global.putFloat(resolver, Settings.Global.WINDOW_ANIMATION_SCALE, 0f)
    }

    companion object {
        /** Baselines live in app/src/testDebug/screenshots (see the roborazzi block in app/build.gradle.kts). */
        fun path(name: String, formFactor: FormFactor) = "src/testDebug/screenshots/$name-${formFactor.name}.png"
    }
}
