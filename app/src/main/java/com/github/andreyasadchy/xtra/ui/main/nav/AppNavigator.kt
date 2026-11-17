package com.github.andreyasadchy.xtra.ui.main.nav

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

    /** The entry a tab switch just opened; the host animates it as a sibling, not as a page one level deeper. */
    var siblingTargetId: String? = null
        private set
    var siblingForward = true
        private set

    fun attach(controller: NavHostController) {
        this.controller = controller
        val calls = pending.toList()
        pending.clear()
        calls.forEach { it(controller) }
    }

    fun detach(controller: NavHostController) {
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
