package com.github.andreyasadchy.xtra.ui.channel

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.ui.browse.BrowseFixtures
import com.github.andreyasadchy.xtra.ui.common.VideoFilters
import com.github.andreyasadchy.xtra.ui.design.components.rememberToastHostState
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.download.DownloadButtonTag
import com.github.andreyasadchy.xtra.ui.download.DownloadForm
import com.github.andreyasadchy.xtra.ui.download.DownloadItem
import com.github.andreyasadchy.xtra.ui.download.DownloadKind
import com.github.andreyasadchy.xtra.ui.download.DownloadSheetContent
import com.github.andreyasadchy.xtra.ui.following.FollowingAccount
import com.github.andreyasadchy.xtra.ui.following.FollowingFixtures
import com.github.andreyasadchy.xtra.ui.saved.BookmarkSortChoice
import com.github.andreyasadchy.xtra.ui.saved.SavedListTag
import com.github.andreyasadchy.xtra.ui.saved.SavedOptions
import com.github.andreyasadchy.xtra.ui.saved.SavedScreen
import com.github.andreyasadchy.xtra.ui.saved.SavedSegment
import com.github.andreyasadchy.xtra.ui.saved.bookmarks.BookmarkSorts
import com.github.andreyasadchy.xtra.ui.team.TeamScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The U5 pages' behaviour (plan 10.2 interaction tests): channel page, team page, Me page and the download sheet. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h1600dp")
class U5InteractionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val actions = RecordingU5Actions()
    private val options = FollowingFixtures.options.copy(image = { null })

    private fun showChannel(header: ChannelHeader = U5Fixtures.header, start: ChannelSegment = ChannelSegment.Videos, following: Boolean? = false) {
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                var selected by remember { mutableStateOf(start) }
                ChannelScreen(
                    header = header,
                    segments = listOf(ChannelSegment.About, ChannelSegment.Suggestions, ChannelSegment.Videos, ChannelSegment.Clips),
                    selected = selected,
                    onSelect = { selected = it; actions.calls += "segment:$it" },
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
                    following = following,
                    notifications = false,
                    toast = rememberToastHostState(),
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theLiveCardWatchesTheStream() {
        showChannel()
        compose.onNodeWithTag(ChannelLiveCardTag).performClick()
        assertEquals("watchLive", actions.calls.last())
    }

    @Test
    fun followFollows() {
        showChannel()
        compose.onNodeWithText("Follow").performClick()
        assertEquals("follow:true", actions.calls.last())
    }

    @Test
    fun unfollowAsksFirst() {
        showChannel(following = true)
        compose.onNodeWithText("Following").performClick()
        compose.onNodeWithText("Unfollow").performClick()
        assertEquals("follow:false", actions.calls.last())
    }

    @Test
    fun theBellTogglesNotifications() {
        showChannel()
        compose.onNodeWithTag("channelBell").performClick()
        assertEquals("bell", actions.calls.last())
    }

    @Test
    fun videoSortIsAppliedAndRememberedForTheChannel() {
        showChannel()
        compose.onNodeWithTag(ChannelListTag).performScrollToNode(hasText("Upload date"))
        compose.onNodeWithText("Upload date").performClick()
        compose.onNodeWithText("Remember choices for this channel").assertExists()
        compose.onAllNodesWithText("Languages").fetchSemanticsNodes().let { assertTrue("no languages for channels", it.isEmpty()) }
        compose.onNodeWithText("View count").performClick()
        compose.onNodeWithText("Remember choices for this channel").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.waitForIdle()
        assertEquals("filter:Videos:${VideoFilters.SORT_VIEWS}:null:${VideoFilters.VIDEO_TYPE_ALL}:remember=true,default=false", actions.calls.last())
    }

    @Test
    fun segmentsSwitchAndAboutOpensTheTeamAndLinks() {
        showChannel(start = ChannelSegment.About)
        compose.onNodeWithTag(ChannelListTag).performScrollToNode(hasText("Team: Night Owls"))
        compose.onNodeWithText("Team: Night Owls").performClick()
        assertEquals("team:owls", actions.calls.last())
        compose.onNodeWithTag(ChannelListTag).performScrollToNode(hasText("YouTube"))
        compose.onNodeWithText("YouTube").performClick()
        assertEquals("url:https://www.youtube.com/@nightowl", actions.calls.last())
    }

    @Test
    fun theMoreSheetSharesAndRecords() {
        showChannel()
        compose.onNodeWithTag("channelMore").performClick()
        compose.onNodeWithText("Record").performClick()
        compose.waitForIdle()
        assertEquals("record:1", actions.calls.last())
        compose.onNodeWithTag("channelMore").performClick()
        compose.onNodeWithText("Share").performClick()
        compose.waitForIdle()
        assertEquals("shareChannel", actions.calls.last())
    }

    @Test
    fun aVideoLongPressOffersDownload() {
        showChannel()
        compose.onNodeWithTag(ChannelListTag).performScrollToNode(hasText(FollowingFixtures.videos.first().title!!))
        compose.onNodeWithText(FollowingFixtures.videos.first().title!!).performTouchInput { longClick() }
        compose.onNodeWithText("Download").performClick()
        compose.waitForIdle()
        assertEquals("downloadVideo:101", actions.calls.last())
    }

    @Test
    fun teamMembersOpenTheStreamOrTheChannel() {
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                TeamScreen(U5Fixtures.team, FollowingFixtures.paged(U5Fixtures.members), options, actions)
            }
        }
        compose.onNodeWithText("Sleepy").performClick()
        assertEquals("openChannel:c9", actions.calls.last())
        compose.onAllNodesWithText(FollowingFixtures.streams.first().title!!).onFirst().performClick()
        assertEquals("openStream:1", actions.calls.last())
    }

    private fun showSaved(segment: SavedSegment) {
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                var selected by remember { mutableStateOf(segment) }
                SavedScreen(
                    segments = SavedSegment.entries,
                    selected = selected,
                    onSelect = { selected = it },
                    account = FollowingAccount(true, "mitchell"),
                    bookmarks = U5Fixtures.bookmarks,
                    bookmarkSort = BookmarkSortChoice(),
                    downloads = FollowingFixtures.paged(U5Fixtures.downloads),
                    downloadStates = U5Fixtures.downloadStates,
                    filters = FollowingFixtures.paged(U5Fixtures.savedFilters),
                    options = options,
                    savedOptions = SavedOptions(),
                    actions = actions,
                    toast = rememberToastHostState(),
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aDownloadInProgressCanBeStoppedAndAFinishedOnePlays() {
        showSaved(SavedSegment.Downloads)
        compose.onNodeWithText("New patch, every champion tested").performTouchInput { longClick() }
        compose.onNodeWithText("Stop download").performClick()
        compose.waitForIdle()
        assertEquals("stop:3", actions.calls.last())
        compose.onNodeWithTag(SavedListTag).performScrollToNode(hasText("昨晚冲分全程，最后一把翻盘"))
        compose.onNodeWithText("昨晚冲分全程，最后一把翻盘").performClick()
        assertEquals("play:1", actions.calls.last())
    }

    @Test
    fun deletingFromTheMenuAsksAboutTheFiles() {
        showSaved(SavedSegment.Downloads)
        compose.onNodeWithTag(SavedListTag).performScrollToNode(hasText("昨晚冲分全程，最后一把翻盘"))
        compose.onNodeWithText("昨晚冲分全程，最后一把翻盘").performTouchInput { longClick() }
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Keep files").performClick()
        compose.onNodeWithTag("deleteDownloadConfirm").performClick()
        compose.waitForIdle()
        assertEquals("deleteDownload:1:false", actions.calls.last())
    }

    @Test
    fun aSwipedBookmarkComesBackWithUndoAndGoesWithout() {
        showSaved(SavedSegment.Bookmarks)
        val title = U5Fixtures.bookmarks.first().title!!
        compose.onNodeWithText(title).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(title).assertExists()
        assertFalse(actions.calls.any { it.startsWith("deleteBookmark") })
        compose.onNodeWithText(title).performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(6000)
        compose.waitForIdle()
        assertEquals("deleteBookmark:101", actions.calls.last())
    }

    @Test
    fun bookmarkSortIsAppliedOrSavedAsDefault() {
        showSaved(SavedSegment.Bookmarks)
        compose.onNodeWithText("Saved date · Descending").performClick()
        compose.onNodeWithText("Deletion date").performClick()
        compose.onNodeWithText("Ascending").performClick()
        compose.onNodeWithText("Save as default").performClick()
        compose.waitForIdle()
        assertEquals("bookmarkSort:${BookmarkSorts.SORT_EXPIRES_AT}:${BookmarkSorts.ORDER_ASC}:true", actions.calls.last())
    }

    @Test
    fun aSavedFilterOpens() {
        showSaved(SavedSegment.Filters)
        compose.onNodeWithText("League of Legends").performClick()
        assertEquals("openFilter:2", actions.calls.last())
    }

    @Test
    fun theImportSheetOffersFoldersAndFiles() {
        showSaved(SavedSegment.Downloads)
        compose.onNodeWithTag("savedImport").performClick()
        compose.onNodeWithText("Import files").performClick()
        compose.waitForIdle()
        assertEquals("importFiles", actions.calls.last())
    }

    @Test
    fun theDownloadSheetPicksAQualityAndNeedsAFolderForSharedStorage() {
        var form by mutableStateOf(DownloadForm(location = 0))
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                DownloadSheetContent(
                    item = DownloadItem(DownloadKind.Video, "A video", "nightowl", null),
                    qualities = listOf("Source", "720p60", "480p"),
                    form = form,
                    onForm = { form = it },
                    storages = emptyList(),
                    storageMounted = true,
                    onPickFolder = { actions.calls += "pickFolder" },
                    onDownload = { actions.calls += "download" },
                    onCancel = {},
                    totalMillis = 600_000,
                )
            }
        }
        compose.onNodeWithText("720p60").performClick()
        assertEquals("720p60", form.quality)
        compose.onNodeWithTag(DownloadButtonTag).assertIsNotEnabled()
        compose.onNodeWithText("Select directory").performClick()
        assertEquals("pickFolder", actions.calls.last())
    }
}
