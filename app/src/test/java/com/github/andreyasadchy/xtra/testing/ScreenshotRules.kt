package com.github.andreyasadchy.xtra.testing

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.rules.TestWatcher
import org.robolectric.RuntimeEnvironment
import org.junit.runner.Description
import java.util.Locale
import java.util.TimeZone

/** The three layouts every screen is captured in (plan 10.2). */
enum class FormFactor(private val device: String, private val orientation: String) {
    PhonePortrait(RobolectricDeviceQualifiers.Pixel7, "+port"),
    PhoneLandscape(RobolectricDeviceQualifiers.Pixel7, "+land"),
    TabletLandscape(RobolectricDeviceQualifiers.MediumTablet, "+land");

    /**
     * Device first, then the orientation on top ("+" overrides one qualifier, Robolectric rejects "-land" appended).
     * [heightDp] makes the window taller than the device so a long component sheet fits in one image; the width, and
     * with it the layout class, stays the device's.
     */
    fun apply(heightDp: Int? = null) {
        RuntimeEnvironment.setQualifiers(device)
        RuntimeEnvironment.setQualifiers(orientation)
        if (heightDp != null) RuntimeEnvironment.setQualifiers("+h${heightDp}dp")
    }
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
