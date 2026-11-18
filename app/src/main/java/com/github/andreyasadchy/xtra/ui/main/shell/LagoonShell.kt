package com.github.andreyasadchy.xtra.ui.main.shell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.components.FloatingNavBar
import com.github.andreyasadchy.xtra.ui.design.components.GlassContext
import com.github.andreyasadchy.xtra.ui.design.components.LocalGlass
import com.github.andreyasadchy.xtra.ui.design.components.glassWindowAllowsBlur
import com.github.andreyasadchy.xtra.ui.design.components.lagoonHazeState
import com.github.andreyasadchy.xtra.ui.design.components.FloatingNavRail
import com.github.andreyasadchy.xtra.ui.design.components.HideOnScrollState
import com.github.andreyasadchy.xtra.ui.design.components.NavItem
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons

/**
 * State of the app shell (plan 7.1 and 8): the floating navigation around the pages.
 *
 * Since U8 the shell, the navigation and every page are one composition (MainShell); the player container stays a
 * view above it. Pages read this state to draw under the floating pill and to hide it while scrolling; the player
 * reads the height of the navigation to place the mini player above it.
 */
@Stable
class ShellState(val tabs: List<ShellTab>) {
    var selectedIndex by mutableIntStateOf(0)

    /** Height of the bottom navigation area in px: pill, its margins and the gesture bar; only the gesture bar with the rail. */
    var bottomBarHeightPx by mutableIntStateOf(0)

    /** The rail on the left replaces the bottom pill (wider than 840 dp, plan 4). */
    var useRail by mutableStateOf(false)

    /** The mini player is on screen: the pill stays put so the two never move against each other (plan 7.4). */
    var miniPlayer by mutableStateOf(false)

    /** The player covers the window: pages leave the back gesture to it (U4, Browse's game page). */
    var playerMaximized by mutableStateOf(false)

    /** v0.1 hid the whole navigation when every tab was switched off. */
    var hasNavigation by mutableStateOf(true)

    /** The player fills a picture-in-picture window: the navigation stays out of it (keeping its place). */
    var inPictureInPicture by mutableStateOf(false)

    /**
     * Bumped when settings close after a change the pages can take without a rebuild (motion plan M5 item 3): pages
     * that read those settings read them again ([LocalSettingsVersion]).
     */
    var settingsVersion by mutableIntStateOf(0)

    val hideOnScroll = HideOnScrollState()

    val navLocked: Boolean get() = miniPlayer

    /** The backdrop Compose pages register and the pill blurs (glass plan section 6): one per activity. */
    val hazeState = lagoonHazeState()

    /** Set by the activity; null (tests, no activity) means glass stays solid. */
    var glassPolicy: GlassPolicy? by mutableStateOf(null)

    /**
     * Glass starts solid and blurs from the third frame on, so the first frame (cold start) does not wait for the blur
     * pipeline to be set up (stage G, real-device cold start gate).
     */
    var warmedUp by mutableStateOf(false)

    /** Whether the system and the "透明效果" setting allow blur right now. */
    val blurAllowed: Boolean get() = warmedUp && glassPolicy?.conditions?.blurAllowed == true

    /** What a Compose page provides as [LocalGlass] around its content. */
    @Composable
    fun pageGlass(): GlassContext = GlassContext(hazeState, blurAllowed && glassWindowAllowsBlur())
}

/** Implemented by the activity that hosts the shell, so the player can reach it from its fragment. */
interface ShellHost {
    val shell: ShellState?
}

@Composable
private fun navItems(tabs: List<ShellTab>): List<NavItem> {
    val following = NavItem(stringResource(R.string.following), LucideIcons.Heart, LucideIcons.HeartFilled)
    val browse = NavItem(stringResource(R.string.shell_nav_browse), LucideIcons.LayoutGrid, LucideIcons.LayoutGridFilled)
    val mine = NavItem(stringResource(R.string.shell_nav_mine), LucideIcons.User, LucideIcons.UserFilled)
    return tabs.map {
        when (it.key) {
            ShellTabs.KEY_BROWSE -> browse
            ShellTabs.KEY_MINE -> mine
            else -> following
        }
    }
}

/** Keeps the hide-on-scroll lock in step with the mini player and the kind of page shown, and shows the pill again when the lock changes. */
@Composable
private fun SyncLock(state: ShellState) {
    LaunchedEffect(state) {
        if (!state.warmedUp) {
            withFrameNanos { }
            withFrameNanos { }
            state.warmedUp = true
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.navLocked }.collect { locked ->
            state.hideOnScroll.locked = locked
            state.hideOnScroll.show()
        }
    }
}

/**
 * The phone navigation: the floating pill at the bottom. While the rail shows, only the gesture bar's height is left
 * here: pages and the mini player read this area's height as "what covers the bottom of the window".
 */
@Composable
fun ShellBottomBar(state: ShellState, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SyncLock(state)
    if (state.useRail) {
        Spacer(modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
        return
    }
    val glass = GlassContext(state.hazeState, state.blurAllowed && glassWindowAllowsBlur())
    CompositionLocalProvider(LocalGlass provides glass) {
        FloatingNavBar(
            items = navItems(state.tabs),
            selectedIndex = state.selectedIndex,
            onSelect = onSelect,
            modifier = modifier,
            visible = state.hideOnScroll.visible || state.navLocked,
        )
    }
}

/** The tablet navigation: the floating rail on the left. */
@Composable
fun ShellRail(state: ShellState, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FloatingNavRail(
        items = navItems(state.tabs),
        selectedIndex = state.selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * Changes when settings close after a "live" change (motion plan M5 item 3). A page that reads such a setting reads
 * this first (or keys its `remember` on it), so it redraws with the new value instead of the screen rebuilding.
 */
val LocalSettingsVersion = androidx.compose.runtime.compositionLocalOf { 0 }
