package com.github.andreyasadchy.xtra.ui.main.shell

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.ui.main.nav.BrowseRoute
import com.github.andreyasadchy.xtra.ui.main.nav.ChannelRoute
import com.github.andreyasadchy.xtra.ui.main.nav.FollowingRoute
import com.github.andreyasadchy.xtra.ui.main.nav.GamesRoute
import com.github.andreyasadchy.xtra.ui.main.nav.SavedRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shell reads the stored navigation settings exactly as v0.1 did (plan 10.1: keys, format and defaults unchanged),
 * and only the tab roots belong to a tab.
 */
@RunWith(AndroidJUnit4::class)
class ShellTabsTest {

    @Test
    fun defaultTabsAreFollowingBrowseMe() {
        assertEquals(listOf("2", "0", "3"), ShellTabs.tabs(null).map { it.key })
        assertEquals(FollowingRoute, ShellTabs.startDestination(null))
        assertTrue(ShellTabs.showNavigation(null))
    }

    @Test
    fun storedOrderAndSwitchedOffTabsAreKept() {
        val pref = "0:0:1,3:0:0,2:0:1"
        assertEquals(listOf("0", "2"), ShellTabs.tabs(pref).map { it.key })
    }

    @Test
    fun popularTabNeverShowsAndItsDefaultFallsBackToFollowing() {
        // a v0.1 list from before Popular was removed, with Popular as the default page
        val pref = "0:0:1,1:1:1,2:0:1,3:0:1"
        assertEquals(listOf("0", "2", "3"), ShellTabs.tabs(pref).map { it.key })
        assertEquals(FollowingRoute, ShellTabs.startDestination(pref))
    }

    @Test
    fun defaultPageFromSettings() {
        assertEquals(BrowseRoute, ShellTabs.startDestination("2:0:1,0:1:1,3:0:1"))
        assertEquals(SavedRoute, ShellTabs.startDestination("2:0:1,0:0:1,3:1:1"))
    }

    @Test
    fun missingTabsAreAddedBackAndUnknownOnesDropped() {
        assertEquals(listOf("2:1:1", "0:0:1", "3:0:1"), ShellTabs.normalize("2:1:1,9:0:1", "2:1:1,0:0:1,3:0:1"))
    }

    @Test
    fun everyTabOffHidesTheNavigation() {
        assertFalse(ShellTabs.showNavigation("2:1:0,0:0:0,3:0:0"))
        assertTrue(ShellTabs.tabs("2:1:0,0:0:0,3:0:0").isEmpty())
    }

    @Test
    fun onlyTabRootsBelongToATab() {
        val tabs = ShellTabs.tabs(null)
        assertEquals(0, ShellTabs.indexOf(tabs, FollowingRoute))
        assertEquals(1, ShellTabs.indexOf(tabs, BrowseRoute))
        assertEquals(2, ShellTabs.indexOf(tabs, SavedRoute))
        assertEquals(-1, ShellTabs.indexOf(tabs, ChannelRoute(channelLogin = "a")))
        assertEquals(-1, ShellTabs.indexOf(tabs, GamesRoute(listOf("1"), listOf("tag"))))
    }

    @Test
    fun followingSegmentsUseTheStoredListAndHideVideosWithoutToken() {
        // v0.1 default: games, live, videos, channels; live is the default segment
        assertEquals(
            listOf(FollowingSegment.Games, FollowingSegment.Live, FollowingSegment.Videos, FollowingSegment.Channels),
            FollowingSegment.list(null, showVideos = true),
        )
        val noVideos = FollowingSegment.list(null, showVideos = false)
        assertEquals(listOf(FollowingSegment.Games, FollowingSegment.Live, FollowingSegment.Channels), noVideos)
        assertEquals(FollowingSegment.Live, FollowingSegment.default(null, noVideos))
        // videos marked as default but hidden: fall back to live
        assertEquals(FollowingSegment.Live, FollowingSegment.default("0:0:1,1:0:1,2:1:1,3:0:1", noVideos))
        // every segment off: live alone, like v0.1's single live tab
        assertEquals(listOf(FollowingSegment.Live), FollowingSegment.list("0:0:0,1:0:0,2:0:0,3:0:0", showVideos = true))
    }
}
