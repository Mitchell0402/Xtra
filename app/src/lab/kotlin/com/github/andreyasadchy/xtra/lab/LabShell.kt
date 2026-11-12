package com.github.andreyasadchy.xtra.lab

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.NavHostFragment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File

/**
 * U0 validation 3: the existing MainActivity layout (fragment navigation host, player container, bottom bar) running
 * inside a Compose shell, the way U2 plans to introduce the new shell without touching the 77 findNavController and
 * 56 MainActivity call sites.
 *
 * Nothing in the main source set changes. When the file `files/lab_compose_shell` exists in the app's data directory
 * (debug build: `adb shell run-as <pkg> touch files/lab_compose_shell`), this provider registers activity callbacks
 * that, right after MainActivity.onCreate, move MainActivity's content view into a ComposeView:
 *   - a Compose top bar that follows the legacy NavController's current destination,
 *   - the legacy layout in an AndroidView, unchanged, so its fragments, player and mini player keep working,
 *   - a floating Compose pill drawn over the legacy bottom bar (made transparent, still laid out so the mini player
 *     keeps its place) that selects items on the legacy BottomNavigationView, and hides when the legacy bar hides.
 */
class LabShellProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context!!.applicationContext as Application
        if (File(app.filesDir, "lab_compose_shell").exists()) {
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
                    if (activity is MainActivity) wrap(activity)
                }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })
        }
        return true
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(uri: Uri) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        fun wrap(activity: FragmentActivity) {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            val legacy = content.getChildAt(0) ?: return
            content.removeView(legacy)
            val navBar = legacy.findViewById<BottomNavigationView>(R.id.navBar)
            val navBarContainer = legacy.findViewById<View>(R.id.navBarContainer)
            val state = ShellState()
            navBarContainer.alpha = 0f
            legacy.viewTreeObserver.addOnGlobalLayoutListener(ViewTreeObserver.OnGlobalLayoutListener {
                state.navVisible.value = navBarContainer.isShown
                state.navHeight.intValue = navBarContainer.height
                state.items.value = (0 until navBar.menu.size()).map { navBar.menu.getItem(it).let { i -> i.itemId to i.title.toString() } }
                state.selected.intValue = navBar.selectedItemId
            })
            val navHost = activity.supportFragmentManager.findFragmentById(R.id.navHostFragment) as? NavHostFragment
            navHost?.navController?.addOnDestinationChangedListener { _, destination, _ ->
                state.title.value = destination.label?.toString() ?: activity.resources.getResourceEntryName(destination.id)
            }
            content.addView(ComposeView(activity).apply {
                setContent { Shell(state, legacy) { navBar.selectedItemId = it } }
            })
        }
    }
}

private class ShellState {
    val title: MutableState<String> = mutableStateOf("")
    val navVisible = mutableStateOf(true)
    val navHeight = mutableIntStateOf(0)
    val items = mutableStateOf(emptyList<Pair<Int, String>>())
    val selected = mutableIntStateOf(0)
}

@Composable
private fun Shell(state: ShellState, legacy: View, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF111113))) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().background(Color(0xFF1B1B1F)).statusBarsPadding().height(40.dp).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart) {
                Text("Compose shell · ${state.title.value}", color = Color(0xEBFFFFFF), fontSize = 15.sp)
            }
            AndroidView(factory = { legacy }, modifier = Modifier.fillMaxWidth().weight(1f))
        }
        if (state.navVisible.value) {
            val height = with(LocalDensity.current) { state.navHeight.intValue.toDp() }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).height((height - 16.dp).coerceAtLeast(48.dp))
                    .clip(RoundedCornerShape(50)).background(Color(0xFF26262B)).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.items.value.forEach { (id, title) ->
                    val selected = id == state.selected.intValue
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (selected) Color(0xFF5EE2C4) else Color.Transparent)
                        .clickable { onSelect(id) }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Text(title, color = if (selected) Color(0xFF111113) else Color(0xEBFFFFFF), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
