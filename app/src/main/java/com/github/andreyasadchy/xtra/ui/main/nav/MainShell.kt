package com.github.andreyasadchy.xtra.ui.main.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.andreyasadchy.xtra.ui.channel.ChannelDestination
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.following.FollowingDestination
import com.github.andreyasadchy.xtra.ui.game.GameDestination
import com.github.andreyasadchy.xtra.ui.games.BrowseDestination
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.main.shell.ShellBottomBar
import com.github.andreyasadchy.xtra.ui.main.shell.ShellRail
import com.github.andreyasadchy.xtra.ui.main.shell.ShellState
import com.github.andreyasadchy.xtra.ui.main.shell.ShellTabs
import com.github.andreyasadchy.xtra.ui.saved.SavedDestination
import com.github.andreyasadchy.xtra.ui.search.SearchDestination
import com.github.andreyasadchy.xtra.ui.team.TeamDestination
import com.github.andreyasadchy.xtra.ui.top.TopStreamsDestination

/**
 * The whole app window except the player (U8, plan 7.1 and 8): the rail or the floating pill, and the pages in one
 * Compose navigation host. The player's container is a view above this one, as before.
 *
 * [ignoreCutouts] is the "draw behind cutouts" setting: the pages then only keep clear of the system bars.
 */
@Composable
fun MainShell(
    activity: MainActivity,
    state: ShellState,
    nav: AppNavigator,
    startRoute: Any,
    ignoreCutouts: Boolean,
    onSelectTab: (Int) -> Unit,
    overlay: @Composable () -> Unit,
) {
    val controller = rememberNavController()
    DisposableEffect(controller) {
        nav.attach(controller)
        onDispose { nav.detach(controller) }
    }
    val entry by controller.currentBackStackEntryAsState()
    LaunchedEffect(entry) {
        val destination = entry?.destination ?: return@LaunchedEffect
        ShellTabs.indexOf(state.tabs, destination).takeIf { it != -1 }?.let { state.selectedIndex = it }
    }
    val sides = (if (ignoreCutouts) WindowInsets.systemBars else WindowInsets.systemBars.union(WindowInsets.displayCutout))
    val hidden = if (state.inPictureInPicture) 0f else 1f
    Box(Modifier.fillMaxSize().background(Lagoon.colors.page)) {
        Row(Modifier.fillMaxSize()) {
            if (state.useRail && state.hasNavigation) {
                ShellRail(state, onSelectTab, Modifier.fillMaxHeight().windowInsetsPadding(sides.only(WindowInsetsSides.Start)).alpha(hidden))
            }
            AppNavHost(
                activity = activity,
                controller = controller,
                nav = nav,
                startRoute = startRoute,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // the rail already sits after the start inset
                    .windowInsetsPadding(sides.only(if (state.useRail && state.hasNavigation) WindowInsetsSides.End else WindowInsetsSides.Horizontal)),
            )
        }
        if (state.hasNavigation) {
            // invisible, not gone, in picture-in-picture: the mini player keeps its place above the pill
            ShellBottomBar(
                state,
                onSelectTab,
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { state.bottomBarHeightPx = it.height }
                    .windowInsetsPadding(sides.only(WindowInsetsSides.Horizontal))
                    .alpha(hidden),
            )
        } else {
            LaunchedEffect(Unit) { state.bottomBarHeightPx = 0 }
        }
        overlay()
    }
}

/** The pages, with the motion system's page transitions (plan 5): one level deeper, back, and tab switches. */
@Composable
private fun AppNavHost(
    activity: MainActivity,
    controller: NavHostController,
    nav: AppNavigator,
    startRoute: Any,
    modifier: Modifier,
) {
    val motion = Lagoon.motion
    val density = LocalDensity.current
    NavHost(
        navController = controller,
        startDestination = startRoute,
        modifier = modifier,
        enterTransition = {
            if (nav.isSibling(targetState)) motion.sibling(density, nav.siblingForward).targetContentEnter
            else motion.forward().targetContentEnter
        },
        exitTransition = {
            if (nav.isSibling(targetState)) motion.sibling(density, nav.siblingForward).initialContentExit
            else motion.forward().initialContentExit
        },
        // switching back to the start tab pops to it, so the pop transitions check the tab switch too (N2)
        popEnterTransition = {
            if (nav.isSibling(targetState)) motion.sibling(density, nav.siblingForward).targetContentEnter
            else motion.back().targetContentEnter
        },
        popExitTransition = {
            if (nav.isSibling(targetState)) motion.sibling(density, nav.siblingForward).initialContentExit
            else motion.back().initialContentExit
        },
    ) {
        page<FollowingRoute>(activity, nav) { FollowingDestination(it) }
        page<BrowseRoute>(activity, nav) { BrowseDestination(it) }
        page<GamesRoute>(activity, nav) { BrowseDestination(it) }
        page<SavedRoute>(activity, nav) { SavedDestination(it) }
        page<TopStreamsRoute>(activity, nav) { TopStreamsDestination(it) }
        page<SearchRoute>(activity, nav) { SearchDestination(it) }
        page<GameRoute>(activity, nav) { GameDestination(it) }
        page<ChannelRoute>(activity, nav) { ChannelDestination(it) }
        page<TeamRoute>(activity, nav) { TeamDestination(it) }
    }
}

private inline fun <reified T : Any> NavGraphBuilder.page(
    activity: MainActivity,
    nav: AppNavigator,
    noinline create: (PageHost) -> ComposePage,
) {
    composable<T> { entry -> PageEntry(PageHost(activity, entry, nav), create) }
}
