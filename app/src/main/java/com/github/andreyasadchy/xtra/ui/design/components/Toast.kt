package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.ui.design.theme.GlassLevel
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ToastResult { Dismissed, ActionPerformed }

class ToastData(
    val message: String,
    val actionLabel: String? = null,
    val durationMs: Long = 3000L,
    internal val result: CompletableDeferred<ToastResult> = CompletableDeferred(),
) {
    fun performAction() = result.complete(ToastResult.ActionPerformed)
    fun dismiss() = result.complete(ToastResult.Dismissed)
}

/**
 * Queue of toasts (plan 6, replaces system Toasts and Snackbars). [show] suspends until the toast is gone and tells
 * whether its action (usually "Undo") was tapped. One toast at a time; later ones wait their turn.
 */
@Stable
class ToastHostState {
    private val mutex = Mutex()

    var current by mutableStateOf<ToastData?>(null)
        private set

    suspend fun show(message: String, actionLabel: String? = null, durationMs: Long = if (actionLabel != null) 5000L else 3000L): ToastResult =
        mutex.withLock {
            val data = ToastData(message, actionLabel, durationMs, CompletableDeferred())
            current = data
            try {
                data.result.await()
            } finally {
                current = null
            }
        }
}

@Composable
fun rememberToastHostState() = remember { ToastHostState() }

/** Shows the current toast as a floating pill at the bottom; place it above the navigation. */
@Composable
fun ToastHost(state: ToastHostState, modifier: Modifier = Modifier) {
    val motion = Lagoon.motion
    var shown by remember { mutableStateOf<ToastData?>(null) }
    val current = state.current
    if (current != null) shown = current
    LaunchedEffect(current) {
        if (current != null) {
            delay(current.durationMs)
            current.dismiss()
        }
    }
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(motion.steady()) { it } + fadeIn(motion.fade()),
            exit = slideOutVertically(motion.move()) { it / 2 } + fadeOut(motion.fadeExit()),
        ) {
            shown?.let { Toast(it) }
        }
    }
}

/** The toast pill itself. */
@Composable
fun Toast(data: ToastData, modifier: Modifier = Modifier) {
    val colors = Lagoon.colors
    GlassSurface(
        GlassLevel.Float,
        LagoonShapes.pill,
        modifier
            .widthIn(max = 560.dp)
            .heightIn(min = 52.dp)
            .shadow(16.dp, LagoonShapes.pill, ambientColor = Color.Black, spotColor = Color.Black),
    ) {
    Row(
        Modifier
            .heightIn(min = 52.dp)
            .padding(start = 20.dp, end = if (data.actionLabel != null) 6.dp else 20.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(data.message, style = Lagoon.type.body, color = colors.textPrimary, modifier = Modifier.weight(1f, fill = false).padding(vertical = 14.dp))
        if (data.actionLabel != null) {
            Text(
                data.actionLabel,
                style = Lagoon.type.labelStrong,
                color = colors.accent,
                modifier = Modifier
                    .pressable(onClick = { data.performAction() }, shape = LagoonShapes.pill)
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }
    }
}
}
