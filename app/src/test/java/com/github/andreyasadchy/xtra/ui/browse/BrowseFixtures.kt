package com.github.andreyasadchy.xtra.ui.browse

import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.following.FollowingFixtures
import com.github.andreyasadchy.xtra.ui.game.GameActions
import com.github.andreyasadchy.xtra.ui.game.GameHeader
import com.github.andreyasadchy.xtra.ui.games.GamesGridActions
import com.github.andreyasadchy.xtra.ui.search.SearchActions

/** Fixed data for the U4 page tests: the Following fixtures plus a game, clips and search users. */
object BrowseFixtures {
    val games = FollowingFixtures.games
    val streams = FollowingFixtures.streams
    val videos = FollowingFixtures.videos

    val clips = listOf(
        Clip(id = "c101", channelId = "c1", channelLogin = "nightowl", channelName = "夜航Nightowl", title = "最后一秒翻盘，全场沸腾", thumbnailURL = "https://example.test/clip101.jpg", createdAt = "2026-11-12T12:00:00Z", viewCount = 52000, durationSeconds = 28),
        Clip(id = "c102", channelId = "c2", channelLogin = "pixelmoth", channelName = "pixelmoth", title = "The creeper that ended a 40 hour world", thumbnailURL = "https://example.test/clip102.jpg", createdAt = "2026-11-10T12:00:00Z", viewCount = 8100, durationSeconds = 60),
    )

    val users = listOf(
        User(id = "c1", login = "nightowl", name = "夜航Nightowl", isLive = true, followerCount = 182000),
        User(id = "c8", login = "nightowl_clips", name = "nightowl_clips", followerCount = 1200),
    )

    val header = GameHeader(
        id = "g2",
        slug = "league-of-legends",
        name = "League of Legends",
        boxArt = "https://example.test/g2-285x380.jpg",
        viewers = 184000,
        broadcasters = 2300,
        followers = 11_000_000,
        tags = listOf(Tag("t2", "MOBA"), Tag("t3", "Strategy"), Tag("t4", "Multiplayer")),
    )
}

/** Records what the U4 pages asked for. */
class RecordingBrowseActions : GameActions, SearchActions, GamesGridActions {
    val calls = mutableListOf<String>()

    override fun back() { calls += "back" }
    override fun search() { calls += "search" }
    override fun openStream(stream: Stream) { calls += "openStream:${stream.id}" }
    override fun openChannel(id: String?, login: String?, name: String?, image: String?, streamId: String?) { calls += "openChannel:$id" }
    override fun openGame(id: String?, slug: String?, name: String?, boxArt: String?, updateLocal: Boolean) { calls += "openGame:$name" }
    override fun openStreamTag(tag: String) { calls += "streamTag:$tag" }
    override fun record(stream: Stream) { calls += "record:${stream.id}" }
    override fun share(url: String, title: String?) { calls += "share:$url" }
    override fun openVideo(video: Video, position: Long?) { calls += "openVideo:${video.id}" }
    override fun downloadVideo(video: Video) { calls += "downloadVideo:${video.id}" }
    override fun toggleBookmark(video: Video) { calls += "bookmark:${video.id}" }
    override fun openClip(clip: Clip) { calls += "openClip:${clip.id}" }
    override fun downloadClip(clip: Clip) { calls += "downloadClip:${clip.id}" }
    override fun integrityCheckFailed() { calls += "integrity" }

    // game page
    override fun applyFilter(kind: FilterKind, filter: BrowseFilter, saves: FilterSaves) {
        calls += "filter:$kind:${filter.sort}:${filter.period}:${filter.type}:${filter.languages.joinToString("+")}:${filter.tagNames.joinToString("+")}:" +
                "remember=${saves.remember},forget=${saves.forget},default=${saves.saveDefault},filters=${saves.saveFilters}"
    }
    override fun setFollowing(follow: Boolean) { calls += "follow:$follow" }
    override fun openGameTag(tag: Tag) { calls += "gameTag:${tag.name}" }
    override fun tagQuery(query: String) { calls += "tagQuery:$query" }

    // games grid
    override fun openGame(game: Game) { calls += "openGame:${game.name}" }
    override fun account() { calls += "account" }
    override fun applyFilter(filter: BrowseFilter) { calls += "gamesFilter:${filter.tags.joinToString("+") { "${it.id}/${it.name}" }}" }

    // search
    override fun query(text: String) { calls += "query:$text" }
    override fun remember(text: String) { calls += "remember:$text" }
    override fun forget(text: String) { calls += "forget:$text" }
    override fun forgetAll() { calls += "forgetAll" }
    override fun lookUpUser(byId: Boolean, value: String) { calls += "lookUp:$byId:$value" }
    override fun openUser(byId: Boolean, value: String) { calls += "openUser:$byId:$value" }
}
