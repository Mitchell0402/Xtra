package com.github.andreyasadchy.xtra.ui.following

import androidx.compose.runtime.Composable
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import kotlinx.coroutines.flow.flowOf

/** Fixed data for the Following page tests: a fixed clock, no image URLs, very long Chinese and English titles. */
object FollowingFixtures {
    /** 2026-11-13 20:00 UTC */
    const val NOW = 1794600000000L
    private const val LONG_ZH = "超长标题测试：今晚通宵冲击王者段位，不上分不下播，顺便和弹幕一起聊聊新赛季的版本改动和英雄强度排行"
    private const val LONG_EN = "Extremely long title test: all-night ranked grind to Grandmaster with viewer games, patch notes review and giveaways"

    val options = FollowingListOptions(nowMillis = { NOW })

    val streams = listOf(
        Stream(id = "1", thumbnailURL = "https://example.test/live1-{width}x{height}.jpg", channelId = "c1", channelLogin = "nightowl", channelName = "夜航Nightowl", gameName = "英雄联盟", title = "排位冲大师，今天不上分不下播", viewerCount = 12408, createdAt = "2026-11-13T16:17:50Z", tags = listOf("中文", "排位")),
        Stream(id = "2", thumbnailURL = "https://example.test/live2-{width}x{height}.jpg", channelId = "c2", channelLogin = "pixelmoth", channelName = "pixelmoth", gameName = "Minecraft", title = "Night build day 42: new map, new base", viewerCount = 3216, createdAt = "2026-11-13T18:54:27Z", tags = listOf("English")),
        Stream(id = "3", thumbnailURL = "https://example.test/live3-{width}x{height}.jpg", channelId = "c3", channelLogin = "mika_cn", channelName = "Mika_cn", gameName = "无畏契约", title = LONG_ZH, viewerCount = 2904, createdAt = "2026-11-13T17:48:13Z"),
        Stream(id = "4", thumbnailURL = "https://example.test/live4-{width}x{height}.jpg", channelId = "c4", channelLogin = "longtitle_en", channelName = "longtitle_en", gameName = "Just Chatting With A Very Long Category Name", title = LONG_EN, viewerCount = 45, createdAt = "2026-11-13T19:56:01Z"),
    )

    val videos = listOf(
        Video(id = "101", thumbnailURL = "https://example.test/video101.jpg", channelId = "c1", channelLogin = "nightowl", channelName = "夜航Nightowl", title = "昨晚冲分全程，最后一把翻盘", createdAt = "2024-11-12T12:00:00Z", viewCount = 31000, durationSeconds = 15150, type = "archive"),
        Video(id = "102", thumbnailURL = "https://example.test/video102.jpg", channelId = "c2", channelLogin = "pixelmoth", channelName = "pixelmoth", title = "New patch, every champion tested", createdAt = "2024-11-10T12:00:00Z", viewCount = 24000, durationSeconds = 11112, type = "highlight"),
        Video(id = "103", thumbnailURL = "https://example.test/video103.jpg", channelId = "c3", channelLogin = "mika_cn", channelName = "Mika_cn", title = LONG_EN, createdAt = "2024-11-06T12:00:00Z", viewCount = 3960, durationSeconds = 15, type = "upload"),
    )

    /** Watched 62 % of the first video. */
    val positions = mapOf(101L to 15150L * 620)

    val channels = listOf(
        User(id = "c1", login = "nightowl", name = "夜航Nightowl", lastBroadcast = "2024-11-12T12:00:00Z", isLive = true, accountFollow = true),
        User(id = "c5", login = "sora", name = "Sora", lastBroadcast = "2024-11-10T12:00:00Z", localFollow = true),
        User(id = "c6", login = "xiaoyu", name = "小鱼", followedAt = "2023-02-01T12:00:00Z", accountFollow = true, localFollow = true),
        User(id = "c7", login = "a_very_long_login_name_for_testing", name = "A Very Long Channel Name For Testing The Row", lastBroadcast = "2024-10-01T12:00:00Z", accountFollow = true),
    )

    val games = listOf(
        Game(id = "g1", boxArtURL = "https://example.test/g1-{width}x{height}.jpg", name = "只是聊天", viewerCount = 312000, broadcasterCount = 5120, tags = listOf(Tag("t1", "IRL"))),
        Game(id = "g2", boxArtURL = "https://example.test/g2-{width}x{height}.jpg", name = "League of Legends", viewerCount = 184000, broadcasterCount = 2300),
        Game(id = "g3", boxArtURL = "https://example.test/g3-{width}x{height}.jpg", name = "无畏契约", viewerCount = 96000, broadcasterCount = 1800),
        Game(id = "g4", boxArtURL = "https://example.test/g4-{width}x{height}.jpg", name = "A Game With An Extremely Long Name That Wraps", viewerCount = 45, broadcasterCount = 3),
    )

    private val idle = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
    val loading = LoadStates(LoadState.Loading, LoadState.NotLoading(false), LoadState.NotLoading(false))
    val error = LoadStates(LoadState.Error(Exception("Unable to resolve host \"gql.twitch.tv\"")), LoadState.NotLoading(false), LoadState.NotLoading(false))

    @Composable
    fun <T : Any> paged(items: List<T>, states: LoadStates = idle): LazyPagingItems<T> =
        flowOf(if (items.isEmpty()) PagingData.empty(states) else PagingData.from(items, states)).collectAsLazyPagingItems()

    @Composable
    fun lists(
        streams: List<Stream> = this.streams,
        state: LoadStates = idle,
        recentlyLive: List<User> = channels,
    ) = FollowingLists(
        streams = paged(streams, state),
        videos = paged(videos),
        channels = paged(channels),
        games = paged(games),
        recentlyLive = paged(recentlyLive),
    )
}

/** Records what the page asked for. */
class RecordingActions : FollowingActions {
    val calls = mutableListOf<String>()
    override fun search() { calls += "search" }
    override fun settings() { calls += "settings" }
    override fun logIn() { calls += "logIn" }
    override fun logOut() { calls += "logOut" }
    override fun openStream(stream: Stream) { calls += "openStream:${stream.id}" }
    override fun openChannel(id: String?, login: String?, name: String?, image: String?, streamId: String?) { calls += "openChannel:$id" }
    override fun openGame(id: String?, slug: String?, name: String?, updateLocal: Boolean) { calls += "openGame:$name" }
    override fun openStreamTag(tag: String) { calls += "streamTag:$tag" }
    override fun openGameTag(tag: Tag) { calls += "gameTag:${tag.name}" }
    override fun record(stream: Stream) { calls += "record:${stream.id}" }
    override fun share(url: String, title: String?) { calls += "share:$url" }
    override fun unfollow(channelId: String?, name: String) { calls += "unfollow:$channelId" }
    override fun openVideo(video: Video, position: Long?) { calls += "openVideo:${video.id}:$position" }
    override fun download(video: Video) { calls += "download:${video.id}" }
    override fun toggleBookmark(video: Video) { calls += "bookmark:${video.id}" }
    override fun applyVideoSort(choice: VideoSortChoice, saveDefault: Boolean) { calls += "videoSort:${choice.sort}:${choice.type}:$saveDefault" }
    override fun applyChannelSort(choice: ChannelSortChoice, saveDefault: Boolean) { calls += "channelSort:${choice.sort}:${choice.order}:$saveDefault" }
    override fun integrityCheckFailed() { calls += "integrity" }
}
