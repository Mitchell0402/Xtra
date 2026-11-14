package com.github.andreyasadchy.xtra.ui.main.shell

import android.view.animation.AnimationUtils
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shell reads the stored navigation settings exactly as v0.1 did (plan 10.1: keys, format and defaults unchanged),
 * and its page transitions use the motion tokens (plan 5).
 */
@RunWith(AndroidJUnit4::class)
class ShellTabsTest {

    @Test
    fun defaultTabsAreFollowingBrowseMe() {
        assertEquals(listOf("2", "0", "3"), ShellTabs.tabs(null, savedPager = true).map { it.key })
        assertEquals(R.id.followPagerFragment, ShellTabs.startDestination(null, savedPager = true))
        assertTrue(ShellTabs.showNavigation(null))
    }

    @Test
    fun storedOrderAndSwitchedOffTabsAreKept() {
        val pref = "0:0:1,3:0:0,2:0:1"
        assertEquals(listOf("0", "2"), ShellTabs.tabs(pref, savedPager = true).map { it.key })
    }

    @Test
    fun popularTabNeverShowsAndItsDefaultFallsBackToFollowing() {
        // a v0.1 list from before Popular was removed, with Popular as the default page
        val pref = "0:0:1,1:1:1,2:0:1,3:0:1"
        assertEquals(listOf("0", "2", "3"), ShellTabs.tabs(pref, savedPager = true).map { it.key })
        assertEquals(R.id.followPagerFragment, ShellTabs.startDestination(pref, savedPager = true))
    }

    @Test
    fun defaultPageFromSettings() {
        assertEquals(R.id.rootGamesFragment, ShellTabs.startDestination("2:0:1,0:1:1,3:0:1", savedPager = true))
        assertEquals(R.id.savedPagerFragment, ShellTabs.startDestination("2:0:1,0:0:1,3:1:1", savedPager = true))
        assertEquals(R.id.savedMediaFragment, ShellTabs.startDestination("2:0:1,0:0:1,3:1:1", savedPager = false))
    }

    @Test
    fun missingTabsAreAddedBackAndUnknownOnesDropped() {
        assertEquals(listOf("2:1:1", "0:0:1", "3:0:1"), ShellTabs.normalize("2:1:1,9:0:1", "2:1:1,0:0:1,3:0:1"))
    }

    @Test
    fun everyTabOffHidesTheNavigation() {
        assertFalse(ShellTabs.showNavigation("2:1:0,0:0:0,3:0:0"))
        assertTrue(ShellTabs.tabs("2:1:0,0:0:0,3:0:0", savedPager = true).isEmpty())
    }

    @Test
    fun followMediaDestinationCountsAsTheFollowingTab() {
        val tabs = ShellTabs.tabs(null, savedPager = true)
        assertEquals(0, ShellTabs.indexOf(tabs, R.id.followMediaFragment))
        assertEquals(1, ShellTabs.indexOf(tabs, R.id.rootGamesFragment))
        assertEquals(-1, ShellTabs.indexOf(tabs, R.id.channelPagerFragment))
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

    @Test
    fun transitionsUseTheMotionTokens() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val medium = listOf(R.anim.shell_forward_enter, R.anim.shell_forward_exit, R.anim.shell_back_enter, R.anim.shell_back_exit)
        val short = listOf(R.anim.shell_sibling_enter_next, R.anim.shell_sibling_exit_next, R.anim.shell_sibling_enter_prev, R.anim.shell_sibling_exit_prev)
        medium.forEach { assertEquals(LagoonMotion.MEDIUM.toLong(), AnimationUtils.loadAnimation(context, it).duration) }
        short.forEach { assertEquals(LagoonMotion.SHORT.toLong(), AnimationUtils.loadAnimation(context, it).duration) }
    }
}
