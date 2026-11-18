package com.github.andreyasadchy.xtra.ui.main.nav

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.model.ui.Tag
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Compose navigation that replaced the v0.1 graph in U8: route arguments reach the view models under the graph's
 * old names and types, calls made before the host exists wait for it, and every tab keeps its own back stack.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val rule = createComposeRule()

    private class ArgsProbe(val handle: SavedStateHandle) : ViewModel()

    private val probeFactory = viewModelFactory { initializer { ArgsProbe(createSavedStateHandle()) } }

    /** The handle a page's view model gets, made the way the view model factories make it. */
    private fun NavBackStackEntry.handle() = ViewModelProvider.create(this, probeFactory, defaultViewModelCreationExtras)[ArgsProbe::class].handle

    private fun host(nav: AppNavigator): () -> NavHostController {
        lateinit var controller: NavHostController
        rule.setContent {
            controller = rememberNavController()
            NavHost(controller, startDestination = FollowingRoute) {
                composable<FollowingRoute> {}
                composable<BrowseRoute> {}
                composable<SavedRoute> {}
                composable<GamesRoute> {}
                composable<TopStreamsRoute> {}
                composable<SearchRoute> {}
                composable<GameRoute> {}
                composable<ChannelRoute> {}
                composable<TeamRoute> {}
            }
            DisposableEffect(controller) {
                nav.attach(controller)
                onDispose { nav.detach(controller) }
            }
        }
        return { controller }
    }

    @Test
    fun routeArgumentsReachViewModelsUnderTheGraphNames() {
        val nav = AppNavigator()
        val controller = host(nav)
        rule.runOnIdle {
            nav.openGame(id = "29595", slug = "dota-2", name = "Dota 2", boxArt = "https://x/y.jpg", tags = listOf("English", "MOBA"), languages = listOf("en"), updateLocal = true)
        }
        rule.runOnIdle {
            val entry = controller().currentBackStackEntry!!
            val args = GameArgs.fromSavedStateHandle(entry.handle())
            assertEquals(GameArgs("29595", "dota-2", "Dota 2", "https://x/y.jpg", arrayOf("English", "MOBA"), arrayOf("en"), true), args)
            assertEquals(args, GameArgs.fromBundle(entry.arguments))
        }
        rule.runOnIdle { nav.openChannel(login = "gaules") }
        rule.runOnIdle {
            val args = ChannelArgs.fromSavedStateHandle(controller().currentBackStackEntry!!.handle())
            // a missing argument stays null, not the text "null"
            assertEquals(ChannelArgs(channelLogin = "gaules"), args)
        }
        rule.runOnIdle { nav.openGames(listOf(Tag(id = "6ea6bca4", name = "English"))) }
        rule.runOnIdle {
            val tags = GamesArgs.fromSavedStateHandle(controller().currentBackStackEntry!!.handle()).tags!!
            assertEquals(listOf("6ea6bca4" to "English"), tags.map { it.id to it.name })
        }
        rule.runOnIdle { nav.openTopStreams(tags = listOf("English")) }
        rule.runOnIdle {
            val args = TopStreamsArgs.fromSavedStateHandle(controller().currentBackStackEntry!!.handle())
            assertArrayEquals(arrayOf("English"), args.tags)
            assertNull(args.languages)
        }
        rule.runOnIdle { nav.openTeam("esl") }
        rule.runOnIdle {
            assertEquals(TeamArgs("esl"), TeamArgs.fromSavedStateHandle(controller().currentBackStackEntry!!.handle()))
        }
    }

    @Test
    fun aGameOpenedInsideBrowseReadsTheSameArguments() {
        val args = GameArgs(gameId = "1", gameName = "Chess", tags = arrayOf("a"))
        assertEquals(args, GameArgs.fromSavedStateHandle(args.toSavedStateHandle()))
    }

    @Test
    fun callsBeforeTheHostExistsWaitForIt() {
        val nav = AppNavigator()
        nav.openChannel(login = "summit1g")
        val controller = host(nav)
        rule.runOnIdle {
            val entry = controller().currentBackStackEntry!!
            assertTrue(entry.destination.hasRoute(ChannelRoute::class))
            controller().popBackStack()
            assertTrue(controller().currentBackStackEntry!!.destination.hasRoute(FollowingRoute::class))
        }
    }

    @Test
    fun everyTabKeepsItsOwnBackStack() {
        val nav = AppNavigator()
        val controller = host(nav)
        rule.runOnIdle { nav.openChannel(login = "a") }
        rule.runOnIdle { nav.selectTab(BrowseRoute, forward = true) }
        rule.runOnIdle {
            assertTrue(controller().currentBackStackEntry!!.destination.hasRoute(BrowseRoute::class))
            assertEquals(controller().currentBackStackEntry!!.id, nav.siblingTargetId)
        }
        rule.runOnIdle { nav.openGame(name = "Chess") }
        rule.runOnIdle { nav.selectTab(FollowingRoute, forward = false) }
        rule.runOnIdle {
            // back on Following, its channel page is restored
            assertTrue(controller().currentBackStackEntry!!.destination.hasRoute(ChannelRoute::class))
            assertTrue(nav.popToTabRoot(FollowingRoute))
            assertTrue(controller().currentBackStackEntry!!.destination.hasRoute(FollowingRoute::class))
            // already the tab's first page: the caller scrolls it to the top instead
            assertFalse(nav.popToTabRoot(FollowingRoute))
        }
        rule.runOnIdle { nav.selectTab(BrowseRoute, forward = true) }
        rule.runOnIdle {
            assertTrue(controller().currentBackStackEntry!!.destination.hasRoute(GameRoute::class))
        }
    }

    // --- motion plan M1 item 3: the tab-switch marker is one-shot

    @Test
    fun aPageDeeperAndBackAfterATabSwitchAreNotSiblings() {
        val nav = AppNavigator()
        val controller = host(nav)
        rule.runOnIdle { nav.selectTab(BrowseRoute, forward = true) }
        val browse = rule.runOnIdle { controller().currentBackStackEntry!! }
        rule.runOnIdle { assertTrue(nav.isSibling(browse)) }
        rule.runOnIdle { nav.openGame(name = "Chess") }
        rule.runOnIdle { assertNull(nav.siblingTargetId) }
        // back to Browse: the entry the tab switch marked, but this is a back, not a tab switch
        rule.runOnIdle { nav.back() }
        rule.runOnIdle {
            assertEquals(browse.id, controller().currentBackStackEntry!!.id)
            assertFalse(nav.isSibling(browse))
        }
    }

    @Test
    fun switchingBackToTheStartTabIsASiblingOnBothSides() {
        val nav = AppNavigator()
        val controller = host(nav)
        val following = rule.runOnIdle { controller().currentBackStackEntry!! }
        rule.runOnIdle { nav.selectTab(BrowseRoute, forward = true) }
        // a pop to the start destination: the pop transitions read the marker for the entry coming back
        rule.runOnIdle { nav.selectTab(FollowingRoute, forward = false) }
        rule.runOnIdle {
            assertEquals(following.id, controller().currentBackStackEntry!!.id)
            assertTrue(nav.isSibling(controller().currentBackStackEntry!!))
            assertFalse(nav.siblingForward)
        }
    }

    @Test
    fun twoTabSwitchesInARowMarkEachOnce() {
        val nav = AppNavigator()
        val controller = host(nav)
        rule.runOnIdle { nav.selectTab(BrowseRoute, forward = true) }
        val browse = rule.runOnIdle { controller().currentBackStackEntry!! }
        rule.runOnIdle { nav.selectTab(SavedRoute, forward = true) }
        rule.runOnIdle {
            assertFalse(nav.isSibling(browse))
            assertTrue(nav.isSibling(controller().currentBackStackEntry!!))
        }
    }

    @Test
    fun theSystemBackGestureRightAfterATabSwitchIsABack() {
        val nav = AppNavigator()
        val controller = host(nav)
        rule.runOnIdle { nav.selectTab(BrowseRoute, forward = true) }
        // the system back gesture pops the controller directly, without going through AppNavigator
        rule.runOnIdle { controller().popBackStack() }
        rule.runOnIdle {
            assertTrue(controller().currentBackStackEntry!!.destination.hasRoute(FollowingRoute::class))
            assertNull(nav.siblingTargetId)
        }
    }
}
