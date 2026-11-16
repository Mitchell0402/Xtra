package com.github.andreyasadchy.xtra.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.SavedFilter
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.testing.FormFactor
import com.github.andreyasadchy.xtra.testing.ScreenshotRules
import com.github.andreyasadchy.xtra.ui.browse.BrowseFixtures
import com.github.andreyasadchy.xtra.ui.design.components.SheetSurface
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.download.DownloadForm
import com.github.andreyasadchy.xtra.ui.download.DownloadItem
import com.github.andreyasadchy.xtra.ui.download.DownloadKind
import com.github.andreyasadchy.xtra.ui.download.DownloadSheetContent
import com.github.andreyasadchy.xtra.ui.download.StorageOption
import com.github.andreyasadchy.xtra.ui.following.FollowingAccount
import com.github.andreyasadchy.xtra.ui.following.FollowingFixtures
import com.github.andreyasadchy.xtra.ui.following.ShellFrame
import com.github.andreyasadchy.xtra.ui.following.testOptions
import com.github.andreyasadchy.xtra.ui.saved.BookmarkSortChoice
import com.github.andreyasadchy.xtra.ui.saved.SavedOptions
import com.github.andreyasadchy.xtra.ui.saved.SavedScreen
import com.github.andreyasadchy.xtra.ui.saved.SavedSegment
import com.github.andreyasadchy.xtra.ui.team.TeamHeader
import com.github.andreyasadchy.xtra.ui.team.TeamScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines of the U5 pages inside the shell (plan 10.2): the channel page (loading, live with videos,
 * offline with clips, about, failed, empty), the team page, the Me page's three segments and its empty state, and the
 * download and record sheet, in phone portrait, phone landscape and tablet landscape, plus phone portrait at 1.3x font
 * size. Fixed clock, fixed pictures, reduced motion.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class U5ScreenshotTest(private val case: Case, private val variant: Variant) {

    enum class Case {
        ChannelLoading, ChannelLive, ChannelClips, ChannelAbout, ChannelError, ChannelEmpty,
        TeamLoading, Team,
        SavedBookmarks, SavedDownloads, SavedFilters, SavedEmpty,
        DownloadVideo, DownloadStream, DownloadLoading,
    }

    enum class Variant(val formFactor: FormFactor, val fontScale: Float) {
        PhonePortrait(FormFactor.PhonePortrait, 1f),
        PhoneLandscape(FormFactor.PhoneLandscape, 1f),
        TabletLandscape(FormFactor.TabletLandscape, 1f),
        PhonePortraitFont130(FormFactor.PhonePortrait, 1.3f),
    }

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val rules = ScreenshotRules()

    @Test
    fun capture() {
        variant.formFactor.apply()
        RuntimeEnvironment.setFontScale(variant.fontScale)
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                ShellFrame(Modifier.testTag("page")) { bottom -> Page(case, bottom) }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("page").captureRoboImage(ScreenshotRules.path("U5-${case.name}", variant.formFactor).let {
            if (variant.fontScale != 1f) it.replace(".png", "-Font130.png") else it
        })
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun params(): List<Array<Any>> = Case.entries.flatMap { c -> Variant.entries.map { v -> arrayOf<Any>(c, v) } }
    }
}

private val allSegments = listOf(ChannelSegment.About, ChannelSegment.Suggestions, ChannelSegment.Videos, ChannelSegment.Clips, ChannelSegment.Chat)

@Composable
private fun Page(case: U5ScreenshotTest.Case, bottom: Dp) {
    val actions = RecordingU5Actions()
    when (case) {
        U5ScreenshotTest.Case.ChannelLoading, U5ScreenshotTest.Case.ChannelLive, U5ScreenshotTest.Case.ChannelClips,
        U5ScreenshotTest.Case.ChannelAbout, U5ScreenshotTest.Case.ChannelError, U5ScreenshotTest.Case.ChannelEmpty -> {
            val header = when (case) {
                U5ScreenshotTest.Case.ChannelLoading -> ChannelHeader(id = "c1", login = "nightowl", name = "夜航Nightowl", avatar = U5Fixtures.header.avatar)
                U5ScreenshotTest.Case.ChannelLive -> U5Fixtures.header
                else -> U5Fixtures.offlineHeader
            }
            val videos = when (case) {
                U5ScreenshotTest.Case.ChannelLoading -> FollowingFixtures.paged(emptyList<Video>(), FollowingFixtures.loading)
                U5ScreenshotTest.Case.ChannelError -> FollowingFixtures.paged(emptyList<Video>(), FollowingFixtures.error)
                U5ScreenshotTest.Case.ChannelEmpty -> FollowingFixtures.paged(emptyList<Video>())
                else -> FollowingFixtures.paged(FollowingFixtures.videos)
            }
            val selected = when (case) {
                U5ScreenshotTest.Case.ChannelClips -> ChannelSegment.Clips
                U5ScreenshotTest.Case.ChannelAbout -> ChannelSegment.About
                else -> ChannelSegment.Videos
            }
            ChannelScreen(
                header = header,
                segments = allSegments,
                selected = selected,
                onSelect = {},
                lists = ChannelLists(
                    suggestions = FollowingFixtures.paged(BrowseFixtures.streams),
                    videos = videos,
                    clips = FollowingFixtures.paged<Clip>(BrowseFixtures.clips),
                ),
                filters = U5Fixtures.filters,
                about = if (case == U5ScreenshotTest.Case.ChannelAbout) U5Fixtures.about else ChannelAbout(),
                options = testOptions,
                actions = actions,
                loggedIn = true,
                following = if (case == U5ScreenshotTest.Case.ChannelLoading) null else case == U5ScreenshotTest.Case.ChannelLive,
                notifications = if (case == U5ScreenshotTest.Case.ChannelLoading) null else case == U5ScreenshotTest.Case.ChannelLive,
                videoPositions = FollowingFixtures.positions,
                bottomPadding = bottom,
            )
        }
        U5ScreenshotTest.Case.TeamLoading, U5ScreenshotTest.Case.Team -> TeamScreen(
            header = if (case == U5ScreenshotTest.Case.TeamLoading) TeamHeader(name = "owls") else U5Fixtures.team,
            members = if (case == U5ScreenshotTest.Case.TeamLoading) FollowingFixtures.paged(emptyList<Stream>(), FollowingFixtures.loading)
            else FollowingFixtures.paged(U5Fixtures.members),
            options = testOptions,
            actions = actions,
            bottomPadding = bottom,
        )
        U5ScreenshotTest.Case.SavedBookmarks, U5ScreenshotTest.Case.SavedDownloads, U5ScreenshotTest.Case.SavedFilters, U5ScreenshotTest.Case.SavedEmpty -> {
            val empty = case == U5ScreenshotTest.Case.SavedEmpty
            SavedScreen(
                segments = SavedSegment.entries,
                selected = when (case) {
                    U5ScreenshotTest.Case.SavedBookmarks -> SavedSegment.Bookmarks
                    U5ScreenshotTest.Case.SavedFilters -> SavedSegment.Filters
                    else -> SavedSegment.Downloads
                },
                onSelect = {},
                account = FollowingAccount(true, "mitchell"),
                bookmarks = if (empty) emptyList<Bookmark>() else U5Fixtures.bookmarks,
                bookmarkSort = BookmarkSortChoice(),
                downloads = FollowingFixtures.paged(if (empty) emptyList<OfflineVideo>() else U5Fixtures.downloads),
                downloadStates = U5Fixtures.downloadStates,
                filters = FollowingFixtures.paged(if (empty) emptyList<SavedFilter>() else U5Fixtures.savedFilters),
                options = testOptions,
                savedOptions = SavedOptions(),
                actions = actions,
                videoPositions = FollowingFixtures.positions,
                bottomPadding = bottom,
            )
        }
        U5ScreenshotTest.Case.DownloadVideo, U5ScreenshotTest.Case.DownloadStream, U5ScreenshotTest.Case.DownloadLoading -> Box(Modifier.fillMaxSize().background(Lagoon.colors.page)) {
            SheetSurface(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                val video = case != U5ScreenshotTest.Case.DownloadStream
                DownloadSheetContent(
                    item = DownloadItem(
                        if (video) DownloadKind.Video else DownloadKind.Stream,
                        title = if (video) FollowingFixtures.videos.first().title else FollowingFixtures.streams.first().title,
                        subtitle = "夜航Nightowl",
                        thumbnail = testOptions.image("https://example.test/video101.jpg"),
                    ),
                    qualities = if (case == U5ScreenshotTest.Case.DownloadLoading) null
                    else listOf("Source", "1080p60", "720p60", "720p", "480p", "360p", "160p", "Audio only"),
                    form = DownloadForm(quality = "720p60", location = if (video) 1 else 0, sharedPath = if (video) null else "content://x/tree/primary%3ADownload", fromText = if (video) "00:10:00" else "", toText = if (video) "00:42:30" else ""),
                    onForm = {},
                    storages = listOf(StorageOption("Internal storage", "/storage/emulated/0", 23_400_000_000), StorageOption("SD card", "/storage/1234", 61_000_000_000)),
                    storageMounted = true,
                    onPickFolder = {},
                    onDownload = {},
                    onCancel = {},
                    totalMillis = if (video) 15_150_000 else 0,
                    folderLabel = if (video) null else "primary:Download",
                )
            }
        }
    }
}
