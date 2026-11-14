package com.github.andreyasadchy.xtra.ui.main.shell

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import java.util.function.Consumer

/** Why glass is drawn solid right now; empty means it may blur. Shown in the showcase and the G report. */
@Immutable
data class GlassConditions(
    val oldSystem: Boolean = false,
    val windowBlurOff: Boolean = false,
    val powerSave: Boolean = false,
    val lowRam: Boolean = false,
    val highContrastText: Boolean = false,
    val settingOff: Boolean = false,
) {
    val blurAllowed: Boolean get() = !(oldSystem || windowBlurOff || powerSave || lowRam || highContrastText || settingOff)

    fun reasons(): List<String> = buildList {
        if (oldSystem) add("API < 32")
        if (windowBlurOff) add("window blur off")
        if (powerSave) add("battery saver")
        if (lowRam) add("low RAM device")
        if (highContrastText) add("high contrast text")
        if (settingOff) add("setting off")
    }
}

/**
 * A switch that only debug and benchmark builds fill in (src/lab): forces glass on or off, so the benchmark module
 * can compare both with one APK (glass plan section 7). Release and qa builds keep [None].
 */
fun interface GlassOverride {
    /** true or false forces the "透明效果" setting; null leaves it to the user. [launch] is the activity's intent. */
    fun forcedSetting(launch: Intent?): Boolean?

    companion object {
        val None = GlassOverride { null }

        @Volatile
        var current: GlassOverride = None
    }
}

/**
 * Works out whether glass may blur (glass plan section 6, "什么时候走回退版"): API 32 and newer, the system's window
 * blur on (manufacturers, battery saver and the developer option turn it off), no battery saver, not a low-RAM
 * device, no high contrast text (API 36+), and the "透明效果" setting on. Every condition is watched while the
 * activity lives, so glass switches live, without recreating anything.
 */
class GlassPolicy(private val context: Context) : DefaultLifecycleObserver {

    var conditions by mutableStateOf(read())
        private set

    private val prefs = context.prefs()
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == C.UI_GLASS) refresh() }
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }
    private var blurListener: Consumer<Boolean>? = null
    private var contrastListener: Any? = null

    fun refresh() {
        conditions = read()
    }

    private fun read(): GlassConditions {
        val power = context.getSystemService(PowerManager::class.java)
        val forced = GlassOverride.current.forcedSetting((context as? Activity)?.intent)
        return GlassConditions(
            oldSystem = Build.VERSION.SDK_INT < 32,
            windowBlurOff = Build.VERSION.SDK_INT >= 31 && !windowManager().isCrossWindowBlurEnabled,
            powerSave = power?.isPowerSaveMode == true,
            lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true,
            highContrastText = Build.VERSION.SDK_INT >= 36 && highContrastText(),
            settingOff = !(forced ?: context.prefs().getBoolean(C.UI_GLASS, true)),
        )
    }

    private fun windowManager() = context.getSystemService(WindowManager::class.java)

    @RequiresApi(36)
    private fun highContrastText() = context.getSystemService(AccessibilityManager::class.java)?.isHighContrastTextEnabled == true

    override fun onCreate(owner: LifecycleOwner) {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        ContextCompat.registerReceiver(
            context, powerReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val executor = ContextCompat.getMainExecutor(context)
        if (Build.VERSION.SDK_INT >= 31) {
            val listener = Consumer<Boolean> { refresh() }
            windowManager().addCrossWindowBlurEnabledListener(executor, listener)
            blurListener = listener
        }
        if (Build.VERSION.SDK_INT >= 36) {
            val listener = AccessibilityManager.HighContrastTextStateChangeListener { refresh() }
            context.getSystemService(AccessibilityManager::class.java)?.addHighContrastTextStateChangeListener(executor, listener)
            contrastListener = listener
        }
        refresh()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        context.unregisterReceiver(powerReceiver)
        if (Build.VERSION.SDK_INT >= 31) blurListener?.let { windowManager().removeCrossWindowBlurEnabledListener(it) }
        if (Build.VERSION.SDK_INT >= 36) {
            (contrastListener as? AccessibilityManager.HighContrastTextStateChangeListener)?.let {
                context.getSystemService(AccessibilityManager::class.java)?.removeHighContrastTextStateChangeListener(it)
            }
        }
        blurListener = null
        contrastListener = null
    }
}
