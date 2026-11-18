package com.github.andreyasadchy.xtra.ui.main.nav

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.github.andreyasadchy.xtra.model.ui.Tag

/**
 * The app's navigation calls (U8), in place of the v0.1 graph's global actions. Calls made before the navigation
 * host exists (a twitch.tv link handled in onCreate) wait until it does.
 */
class AppNavigator {

    var controller: NavHostController? = null
        private set
    private val pending = mutableListOf<(NavHostController) -> Unit>()

    /**
     * The entry a tab switch just opened; the host animates the switch as a sibling, not as a page one level deeper or
     * a back (motion plan M1 item 3). One-shot: the transition lambdas only read it, and the next navigation of any
     * kind (a page deeper, back, the system back gesture) clears it in [clearStaleSibling].
     */
    var siblingTargetId: String? = null
        private set
    var siblingForward = true
        private set

    /** Whether the transition to or from [entry] is the tab switch that just happened. */
    fun isSibling(entry: NavBackStackEntry) = entry.id == siblingTargetId

    /**
     * Runs synchronously inside every navigate() and pop, before [selectTab] writes the new marker (it writes after
     * navigate() returns), so a tab switch only clears the previous one. Not a back-stack flow: that is collected
     * asynchronously and could clear the new marker before the transition reads it.
     */
    private val clearStaleSibling = NavController.OnDestinationChangedListener { controller, _, _ ->
        if (controller.currentBackStackEntry?.id != siblingTargetId) siblingTargetId = null
    }

    fun attach(controller: NavHostController) {
        this.controller?.removeOnDestinationChangedListener(clearStaleSibling)
        this.controller = controller
        controller.addOnDestinationChangedListener(clearStaleSibling)
        val calls = pending.toList()
        pending.clear()
        calls.forEach { it(controller) }
    }

    fun detach(controller: NavHostController) {
        controller.removeOnDestinationChangedListener(clearStaleSibling)
        if (this.controller === controller) this.controller = null
    }

    private fun run(call: (NavHostController) -> Unit) {
        val current = controller
        if (current != null) call(current) else pending += call
    }

    fun navigate(route: Any) = run { it.navigate(route) }

    fun back() = run { it.navigateUp() }

    fun openChannel(id: String? = null, login: String? = null, name: String? = null, image: String? = null, streamId: String? = null, updateLocal: Boolean = false) =
        navigate(ChannelRoute(channelId = id, channelLogin = login, channelName = name, channelImage = image, streamId = streamId, updateLocal = updateLocal))

    fun openGame(id: String? = null, slug: String? = null, name: String? = null, boxArt: String? = null, tags: List<String>? = null, languages: List<String>? = null, updateLocal: Boolean = false) =
        navigate(GameRoute(gameId = id, gameSlug = slug, gameName = name, boxArt = boxArt, tags = tags.orEmpty(), languages = languages.orEmpty(), updateLocal = updateLocal))

    fun openGames(tags: List<Tag>? = null) =
        navigate(GamesRoute(tagIds = tags.orEmpty().map { it.id.orEmpty() }, tagNames = tags.orEmpty().map { it.name.orEmpty() }))

    fun openTopStreams(tags: List<String>? = null, languages: List<String>? = null) =
        navigate(TopStreamsRoute(tags = tags.orEmpty(), languages = languages.orEmpty()))

    fun openSearch() = navigate(SearchRoute)

    fun openTeam(name: String?) = navigate(TeamRoute(name))

    /**
     * Switches to a navigation tab. Like v0.1 (NavigationUI with a bottom bar), every tab keeps its own back stack.
     * Returns false when [route] is already the current page, so the caller can scroll it to the top instead.
     */
    fun selectTab(route: Any, forward: Boolean) = run { controller ->
        controller.navigate(route) {
            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        siblingForward = forward
        siblingTargetId = controller.currentBackStackEntry?.id
    }

    /** A tap on the current tab: back to the tab's first page; false when it is already there. */
    fun popToTabRoot(route: Any): Boolean {
        val controller = controller ?: return false
        return controller.popBackStack(route, inclusive = false)
    }
}
