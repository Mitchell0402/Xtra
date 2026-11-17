package com.github.andreyasadchy.xtra.ui.design.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * One toast queue per activity (U9 B1): pages, sheets, fragments and the player all post here, so only one toast is
 * on screen at a time and it is drawn on top of everything. Where it is drawn:
 *  - with a sheet open, inside the top sheet (sheets are their own windows and would hide it);
 *  - otherwise by the activity: MainActivity's overlay above the player ([hasOverlay]), or the page's own host in the
 *    activities without one (settings, login);
 *  - outside the app's window, the system toast: when the activity is not started or is in picture-in-picture (a
 *    toast in the small window cannot be read). That is the only system toast left in the app.
 */
@Stable
class LagoonToasts(private val activity: ComponentActivity, val hasOverlay: Boolean = false) {
    val state = ToastHostState()

    private val sheets = mutableStateListOf<Long>()
    private var nextSheet = 0L

    /** The sheet that draws the toast now: the last one opened, or null when none is open. */
    val drawingSheet: Long? get() = sheets.lastOrNull()

    internal fun sheetOpened(): Long = (nextSheet++).also { sheets.add(it) }

    internal fun sheetClosed(id: Long) {
        sheets.remove(id)
    }

    fun show(message: CharSequence, long: Boolean = false) {
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || activity.isInPictureInPictureMode) {
            systemToast(activity, message, long)
            return
        }
        activity.lifecycleScope.launch { state.show(message.toString(), durationMs = if (long) 4500L else 3000L) }
    }

    fun show(@StringRes message: Int, long: Boolean = false) = show(activity.getString(message), long)
}

/** An activity with a [LagoonToasts] queue: MainActivity, SettingsActivity, LoginActivity. */
interface LagoonToastsOwner {
    val toasts: LagoonToasts
}

val LocalLagoonToasts = staticCompositionLocalOf<LagoonToasts?> { null }

/** The activity's queue, or a page's own when there is none (tests, showcase). */
@Composable
fun rememberLagoonToastState(): ToastHostState = LocalLagoonToasts.current?.state ?: rememberToastHostState()

/** A Lagoon toast from anywhere that has a context; the system toast when no Lagoon activity is behind it. */
fun Context.lagoonToast(message: CharSequence, long: Boolean = false) {
    val owner = findActivity() as? LagoonToastsOwner
    if (owner != null) owner.toasts.show(message, long) else systemToast(this, message, long)
}

fun Context.lagoonToast(@StringRes message: Int, long: Boolean = false) = lagoonToast(getString(message), long)

internal fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun systemToast(context: Context, message: CharSequence, long: Boolean) {
    android.widget.Toast.makeText(context, message, if (long) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
}

/** MainActivity's toast layer above the player: draws the queue unless a sheet does; touches pass through it. */
@Composable
fun LagoonToastOverlay(toasts: LagoonToasts, bottomPadding: Dp) {
    if (toasts.drawingSheet != null) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        ToastHost(toasts.state, Modifier.padding(bottom = bottomPadding), owner = true)
    }
}
