package com.github.andreyasadchy.xtra.ui.main.nav

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.main.shell.ShellState

/** What a page gets from the activity and from its navigation entry. */
class PageHost(val activity: MainActivity, val entry: NavBackStackEntry, val nav: AppNavigator)

/**
 * A page of the app (U8, plan 8): what each v0.1 Fragment destination did, without the Fragment. The pages kept
 * their Fragment-shaped code through U2 to U7, so this class offers the same names (requireContext, lifecycleScope,
 * viewModels, childFragmentManager, onCreate, onStart, onStop) on top of the navigation entry:
 *
 * - view models live in the entry's store, so they survive configuration changes and trips to other pages, and are
 *   cleared when the page is popped, as a Fragment's were;
 * - [onCreate] runs once per entry (a Fragment's onCreate ran once per instance; the instance stayed alive in the back
 *   stack), [onStart] and [onStop] follow the entry's lifecycle;
 * - dialogs go to the activity's fragment manager.
 */
abstract class ComposePage(val host: PageHost) {

    val activity: MainActivity get() = host.activity
    val nav: AppNavigator get() = host.nav
    val shell: ShellState? get() = host.activity.shell
    val arguments: Bundle? get() = host.entry.arguments
    val lifecycleScope: LifecycleCoroutineScope get() = host.entry.lifecycleScope
    val childFragmentManager: FragmentManager get() = host.activity.supportFragmentManager

    fun requireActivity(): MainActivity = host.activity
    fun requireContext(): Context = host.activity
    fun getString(@StringRes id: Int): String = host.activity.getString(id)
    fun getString(@StringRes id: Int, vararg args: Any?): String = host.activity.getString(id, *args)
    fun startActivity(intent: Intent) = host.activity.startActivity(intent)

    /** A view model in this page's navigation entry; the same key as a Fragment's `by viewModels()`. */
    protected inline fun <reified VM : ViewModel> viewModels(noinline factory: () -> ViewModelProvider.Factory): Lazy<VM> = lazy {
        ViewModelProvider.create(host.entry, factory(), host.entry.defaultViewModelCreationExtras)[VM::class]
    }

    /** A view model of the activity; the same instance as the activity's `by viewModels()`. */
    protected inline fun <reified VM : ViewModel> activityViewModels(noinline factory: () -> ViewModelProvider.Factory): Lazy<VM> = lazy {
        ViewModelProvider.create(host.activity, factory(), host.activity.defaultViewModelCreationExtras)[VM::class]
    }

    open fun onCreate() {}
    open fun onStart() {}
    open fun onStop() {}

    @Composable
    abstract fun Content()

    /** A tap on the current navigation item while this page is the tab's first page. */
    open fun scrollToTop() {}

    /** The integrity check this page asked for finished ([callback] is what the page passed when asking). */
    open fun onIntegrityTokenLoaded(callback: String?) {}
}

/**
 * Remembers that something ran once in a view model store: a page's [ComposePage.onCreate] in its entry, a game
 * page's start inside Browse.
 */
class OnceFlag : ViewModel() {
    var done = false

    companion object {
        val Factory = viewModelFactory { initializer { OnceFlag() } }
    }
}

/**
 * Shows one page in its navigation entry: creates the page object, runs its onCreate once, forwards the entry's
 * start and stop, and registers it with the activity, which sends it scroll-to-top taps and integrity results.
 */
@Composable
fun PageEntry(host: PageHost, create: (PageHost) -> ComposePage) {
    val entry = host.entry
    val page = remember(entry) {
        create(host).also { page ->
            val flag = ViewModelProvider.create(entry, OnceFlag.Factory, entry.defaultViewModelCreationExtras)[OnceFlag::class]
            if (!flag.done) {
                flag.done = true
                page.onCreate()
            }
        }
    }
    DisposableEffect(page) {
        var started = false
        fun start() { if (!started) { started = true; page.onStart() } }
        fun stop() { if (started) { started = false; page.onStop() } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        entry.lifecycle.addObserver(observer)
        host.activity.registerPage(entry.id, page)
        onDispose {
            entry.lifecycle.removeObserver(observer)
            stop()
            host.activity.unregisterPage(entry.id, page)
        }
    }
    page.Content()
}
