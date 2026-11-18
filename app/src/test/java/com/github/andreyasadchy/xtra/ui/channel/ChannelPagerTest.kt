package com.github.andreyasadchy.xtra.ui.channel

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.ui.browse.BrowseFixtures
import com.github.andreyasadchy.xtra.ui.design.components.rememberToastHostState
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.following.FollowingFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Motion plan M3c: the channel page's segments are pages. A swipe moves between them and tells the page which one
 * shows, a swipe opens the next segment's list as soon as it heads there, a swipe over a card does not open it, and
 * the old chat only shows once the pages have settled on its segment (a stand-in takes its place while they move).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h1600dp")
class ChannelPagerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val actions = RecordingU5Actions()
    private val options = FollowingFixtures.options.copy(image = { null })
    private val visited = mutableListOf<ChannelSegment>()
    private val chatAreas = mutableListOf<ChatArea>()
    private var shown = ChannelSegment.Videos

    private fun showChannel(start: ChannelSegment = ChannelSegment.Videos) {
        shown = start
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                var selected by remember { mutableStateOf(start) }
                ChannelScreen(
                    header = U5Fixtures.header,
                    segments = listOf(ChannelSegment.Suggestions, ChannelSegment.Videos, ChannelSegment.Clips, ChannelSegment.Chat, ChannelSegment.About),
                    selected = selected,
                    onSelect = { selected = it; shown = it },
                    onVisit = { visited += it },
                    onChatArea = { chatAreas += it },
                    lists = ChannelLists(
                        FollowingFixtures.paged(BrowseFixtures.streams),
                        FollowingFixtures.paged(FollowingFixtures.videos),
                        FollowingFixtures.paged<Clip>(BrowseFixtures.clips),
                    ),
                    filters = U5Fixtures.filters,
                    about = U5Fixtures.about,
                    options = options,
                    actions = actions,
                    loggedIn = true,
                    following = false,
                    notifications = false,
                    toast = rememberToastHostState(),
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aSwipeMovesBetweenSegments() {
        showChannel()
        compose.onNodeWithTag(ChannelPagerTag).performTouchInput { swipeLeft(startX = width * 0.85f, endX = width * 0.15f) }
        compose.waitForIdle()
        assertEquals(ChannelSegment.Clips, shown)
        compose.onNodeWithText("Clips").assertIsSelected()
        compose.onNodeWithTag(ChannelPagerTag).performTouchInput { swipeRight(startX = width * 0.15f, endX = width * 0.85f) }
        compose.waitForIdle()
        assertEquals(ChannelSegment.Videos, shown)
    }

    @Test
    fun aSwipeOpensTheNextSegmentsList() {
        showChannel()
        compose.onNodeWithTag(ChannelPagerTag).performTouchInput { swipeRight(startX = width * 0.15f, endX = width * 0.85f) }
        compose.waitForIdle()
        assertTrue(visited.toString(), ChannelSegment.Suggestions in visited)
    }

    @Test
    fun aSwipeOverACardDoesNotOpenIt() {
        showChannel()
        compose.onNodeWithText(FollowingFixtures.videos.first().title!!).performTouchInput { swipeLeft(startX = width * 0.85f, endX = width * 0.15f) }
        compose.waitForIdle()
        assertFalse(actions.calls.toString(), actions.calls.any { it.startsWith("openVideo") })
    }

    @Test
    fun theChatShowsOnlyOnceTheChatPageHasSettled() {
        showChannel(start = ChannelSegment.Clips)
        assertFalse(chatAreas.last().visible)
        compose.onNodeWithTag(ChannelPagerTag).performTouchInput { swipeLeft(startX = width * 0.85f, endX = width * 0.15f) }
        compose.waitForIdle()
        assertEquals(ChannelSegment.Chat, shown)
        assertTrue(chatAreas.toString(), chatAreas.last().visible)
        // leaving the chat hides it again
        compose.onNodeWithText("About").performClick()
        compose.waitForIdle()
        assertFalse(chatAreas.last().visible)
    }
}
