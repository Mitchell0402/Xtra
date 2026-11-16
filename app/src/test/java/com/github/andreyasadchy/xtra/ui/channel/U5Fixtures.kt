package com.github.andreyasadchy.xtra.ui.channel

import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.ChannelPanel
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.SavedFilter
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.browse.BrowseFilter
import com.github.andreyasadchy.xtra.ui.browse.BrowseFixtures
import com.github.andreyasadchy.xtra.ui.browse.FilterKind
import com.github.andreyasadchy.xtra.ui.browse.FilterSaves
import com.github.andreyasadchy.xtra.ui.browse.FilterSheetConfig
import com.github.andreyasadchy.xtra.ui.following.FollowingFixtures
import com.github.andreyasadchy.xtra.ui.saved.DownloadState
import com.github.andreyasadchy.xtra.ui.saved.SavedActions
import com.github.andreyasadchy.xtra.ui.team.TeamActions
import com.github.andreyasadchy.xtra.ui.team.TeamHeader

/** Fixed data for the U5 page tests: a channel, its about data, a team, bookmarks, downloads and filters. */
object U5Fixtures {
    val stream = FollowingFixtures.streams.first()

    val header = ChannelHeader(
        id = "c1", login = "nightowl", name = "夜航Nightowl",
        avatar = "https://example.test/avatar-c1.png",
        banner = "https://example.test/banner-c1.png",
        followers = 182000, roles = "Partner",
        createdAt = 1500000000000L,
        description = "每天晚上八点开播，主玩英雄联盟和各种新游戏。Streaming every night at 8 pm: League of Legends and whatever new game came out this week.",
        stream = stream,
    )

    val offlineHeader = header.copy(stream = null, lastBroadcast = FollowingFixtures.NOW - 86_400_000L * 2)

    // what ChannelPage hands over: the view models' effective values (newest first, all types, a week)
    val filters = ChannelFilters(
        videos = BrowseFilter(sort = com.github.andreyasadchy.xtra.ui.common.VideoFilters.SORT_TIME, type = com.github.andreyasadchy.xtra.ui.common.VideoFilters.VIDEO_TYPE_ALL),
        clips = BrowseFilter(period = com.github.andreyasadchy.xtra.ui.common.VideoFilters.PERIOD_WEEK),
        videosConfig = FilterSheetConfig(FilterKind.Videos, showPeriod = false, canRemember = true, showLanguages = false, rememberForChannel = true, defaultVideoSort = com.github.andreyasadchy.xtra.ui.common.VideoFilters.SORT_TIME),
        clipsConfig = FilterSheetConfig(FilterKind.Clips, canRemember = true, showLanguages = false, rememberForChannel = true),
    )

    val about = ChannelAbout(
        description = header.description,
        socialMedias = listOf("YouTube" to "https://www.youtube.com/@nightowl", "Discord" to "https://discord.gg/nightowl"),
        team = "owls" to "Night Owls",
        originalName = "nightowl_old",
        panels = listOf(
            ChannelPanel(title = "设备", imageUrl = "https://example.test/panel1.png", linkUrl = null, description = "**PC** 和 *外设* 清单"),
            ChannelPanel(title = "Schedule", imageUrl = null, linkUrl = null, description = "Mon–Fri 8 pm CST"),
        ),
        loaded = true,
    )

    val team = TeamHeader(
        name = "owls", displayName = "Night Owls", description = "A team of night-time streamers.",
        logo = "https://example.test/team-logo.png", banner = "https://example.test/team-banner.png",
        members = 24, ownerLogin = "nightowl", ownerName = "夜航Nightowl",
    )

    val members = FollowingFixtures.streams.take(2) + listOf(
        Stream(channelId = "c9", channelLogin = "sleepy", channelName = "Sleepy", channelImageURL = "https://example.test/avatar-c9.png"),
        Stream(channelId = "c10", channelLogin = "dawn_cn", channelName = "黎明Dawn"),
    )

    val bookmarks = FollowingFixtures.videos.mapIndexed { index, video ->
        Bookmark(
            videoId = video.id, userId = video.channelId, userLogin = video.channelLogin, userName = video.channelName,
            title = video.title, createdAt = if (index == 0) "2026-11-12T12:00:00Z" else video.createdAt,
            thumbnail = video.thumbnail, type = video.type, duration = video.durationSeconds.toString(),
            userType = if (index == 0) "affiliate" else null,
        ).apply { id = 10 - index }
    }

    val downloads = listOf(
        OfflineVideo(name = "夜航Nightowl 正在录制", channelId = "c1", channelLogin = "nightowl", channelName = "夜航Nightowl", thumbnail = "https://example.test/live1-640x360.jpg", status = OfflineVideo.STATUS_DOWNLOADING, live = true, downloadDate = FollowingFixtures.NOW).apply { id = 4 },
        OfflineVideo(name = "New patch, every champion tested", channelId = "c2", channelLogin = "pixelmoth", channelName = "pixelmoth", thumbnail = "https://example.test/video102.jpg", status = OfflineVideo.STATUS_DOWNLOADING, duration = 1_800_000, type = "highlight").apply { id = 3 },
        OfflineVideo(name = "Queued clip", channelLogin = "mika_cn", channelName = "Mika_cn", thumbnail = "https://example.test/clip101.jpg", status = OfflineVideo.STATUS_QUEUED, duration = 28_000).apply { id = 2 },
        OfflineVideo(name = "昨晚冲分全程，最后一把翻盘", channelId = "c1", channelLogin = "nightowl", channelName = "夜航Nightowl", thumbnail = "https://example.test/video101.jpg", status = OfflineVideo.STATUS_DOWNLOADED, duration = 600_000, lastWatchPosition = 240_000, type = "archive", downloadDate = FollowingFixtures.NOW - 86_400_000L, sourceStartPosition = 3_600_000, url = "/storage/emulated/0/Android/data/x/files/.downloads/a/playlist.m3u8").apply { id = 1 },
    )

    val downloadStates = mapOf(
        4 to DownloadState(active = true, bytes = 512_000_000, recordedSeconds = 5025),
        3 to DownloadState(active = true, progress = 42, max = 100, bytes = 80_000_000, bytesPerSecond = 2_400_000.0, secondsLeft = 192),
    )

    val savedFilters = listOf(
        SavedFilter(gameId = "g2", gameName = "League of Legends", tags = "中文,排位", languages = "ZH").apply { id = 2 },
        SavedFilter(tags = "English", languages = "EN,JA").apply { id = 1 },
    )
}

/** Records what the U5 pages asked for. */
class RecordingU5Actions : ChannelActions, TeamActions, SavedActions {
    val calls = mutableListOf<String>()

    override fun back() { calls += "back" }
    override fun search() { calls += "search" }
    override fun openStream(stream: Stream) { calls += "openStream:${stream.id}" }
    override fun openChannel(id: String?, login: String?, name: String?, image: String?, streamId: String?) { calls += "openChannel:$id" }
    override fun openChannel(id: String?, login: String?, name: String?, image: String?) { calls += "openChannel:$id" }
    override fun openGame(id: String?, slug: String?, name: String?, boxArt: String?, updateLocal: Boolean) { calls += "openGame:$name" }
    override fun openGame(id: String?, slug: String?, name: String?) { calls += "openGame:$name" }
    override fun openStreamTag(tag: String) { calls += "streamTag:$tag" }
    override fun record(stream: Stream) { calls += "record:${stream.id}" }
    override fun listen(stream: Stream) { calls += "listen:${stream.id}" }
    override fun share(url: String, title: String?) { calls += "share:$url" }
    override fun openVideo(video: Video, position: Long?) { calls += "openVideo:${video.id}" }
    override fun downloadVideo(video: Video) { calls += "downloadVideo:${video.id}" }
    override fun toggleBookmark(video: Video) { calls += "bookmark:${video.id}" }
    override fun openClip(clip: Clip) { calls += "openClip:${clip.id}" }
    override fun downloadClip(clip: Clip) { calls += "downloadClip:${clip.id}" }
    override fun integrityCheckFailed() { calls += "integrity" }

    // channel
    override fun applyFilter(kind: FilterKind, filter: BrowseFilter, saves: FilterSaves) {
        calls += "filter:$kind:${filter.sort}:${filter.period}:${filter.type}:remember=${saves.remember},default=${saves.saveDefault}"
    }
    override fun setFollowing(follow: Boolean) { calls += "follow:$follow" }
    override fun toggleNotifications() { calls += "bell" }
    override fun watchLive() { calls += "watchLive" }
    override fun shareChannel() { calls += "shareChannel" }
    override fun openTeam(name: String) { calls += "team:$name" }
    override fun openUrl(url: String) { calls += "url:$url" }
    override fun openSettings() { calls += "settings" }
    override fun openLogIn() { calls += "logIn" }
    override fun openLogOut() { calls += "logOut" }

    // team
    override fun shareTeam() { calls += "shareTeam" }

    // Me page
    override fun settings() { calls += "settings" }
    override fun logIn() { calls += "logIn" }
    override fun logOut() { calls += "logOut" }
    override fun importFolders() { calls += "importFolders" }
    override fun importFiles() { calls += "importFiles" }
    override fun playOffline(video: OfflineVideo) { calls += "play:${video.id}" }
    override fun stopDownload(video: OfflineVideo) { calls += "stop:${video.id}" }
    override fun resumeDownload(video: OfflineVideo) { calls += "resume:${video.id}" }
    override fun convert(video: OfflineVideo) { calls += "convert:${video.id}" }
    override fun moveToShared(video: OfflineVideo) { calls += "moveShared:${video.id}" }
    override fun moveToApp(video: OfflineVideo, index: Int) { calls += "moveApp:${video.id}:$index" }
    override fun changeChatFile(video: OfflineVideo) { calls += "chatFile:${video.id}" }
    override fun shareDownload(video: OfflineVideo) { calls += "shareDownload:${video.id}" }
    override fun deleteDownload(video: OfflineVideo, keepFiles: Boolean) { calls += "deleteDownload:${video.id}:$keepFiles" }
    override fun openBookmark(bookmark: Bookmark, position: Long?) { calls += "openBookmark:${bookmark.videoId}" }
    override fun refreshBookmark(bookmark: Bookmark) { calls += "refreshBookmark:${bookmark.videoId}" }
    override fun downloadBookmark(bookmark: Bookmark) { calls += "downloadBookmark:${bookmark.videoId}" }
    override fun toggleIgnore(userId: String) { calls += "ignore:$userId" }
    override fun deleteBookmark(bookmark: Bookmark) { calls += "deleteBookmark:${bookmark.videoId}" }
    override fun applyBookmarkSort(sort: String, order: String, saveDefault: Boolean) { calls += "bookmarkSort:$sort:$order:$saveDefault" }
    override fun openFilter(filter: SavedFilter) { calls += "openFilter:${filter.id}" }
    override fun deleteFilter(filter: SavedFilter) { calls += "deleteFilter:${filter.id}" }
}

internal val U5Clips = BrowseFixtures.clips
