package com.github.andreyasadchy.xtra.ui.main.nav

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.github.andreyasadchy.xtra.model.ui.Tag
import kotlinx.serialization.Serializable

/*
 * The app's pages as Compose navigation routes (U8, plan 8), in place of the v0.1 navigation graph (nav_graph.xml)
 * and its generated Directions and Args classes. Argument names are the graph's, so the view models read the same
 * keys from their SavedStateHandle as before.
 */

/** The Following tab (v0.1 followPagerFragment / followMediaFragment). */
@Serializable
data object FollowingRoute

/** The Browse tab: the games grid (v0.1 rootGamesFragment). */
@Serializable
data object BrowseRoute

/** The "Me" tab: saved, downloads and bookmarks (v0.1 savedPagerFragment / savedMediaFragment). */
@Serializable
data object SavedRoute

/** Games with these tags (v0.1 gamesFragment). Tags are id and name pairs, kept as two lists of the same length. */
@Serializable
data class GamesRoute(
    val tagIds: List<String> = emptyList(),
    val tagNames: List<String> = emptyList(),
)

/** Live streams by viewers, or with these tags or languages (v0.1 topFragment). */
@Serializable
data class TopStreamsRoute(
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
)

/** The search page (v0.1 searchPagerFragment). */
@Serializable
data object SearchRoute

/** A game's page (v0.1 gamePagerFragment / gameMediaFragment). */
@Serializable
data class GameRoute(
    val gameId: String? = null,
    val gameSlug: String? = null,
    val gameName: String? = null,
    val boxArt: String? = null,
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val updateLocal: Boolean = false,
)

/** A channel's page (v0.1 channelPagerFragment). */
@Serializable
data class ChannelRoute(
    val channelId: String? = null,
    val channelLogin: String? = null,
    val channelName: String? = null,
    val channelImage: String? = null,
    val updateLocal: Boolean = false,
    val streamId: String? = null,
)

/** A team's page (v0.1 teamFragment). */
@Serializable
data class TeamRoute(val teamName: String? = null)

/**
 * Reads a list argument. Navigation stores a route's `List<String>` as a string array in the entry's arguments; a
 * SavedStateHandle built by hand (Browse's inner game page) may hold a list or an array.
 */
private fun Any?.stringList(): List<String>? = when (this) {
    is Array<*> -> filterIsInstance<String>()
    is List<*> -> filterIsInstance<String>()
    else -> null
}

private fun Map<String, Any?>.toHandle() = SavedStateHandle(this)

/** The arguments of [GamesRoute] as the games view model reads them. */
data class GamesArgs(val tags: Array<Tag>?) {
    companion object {
        fun from(get: (String) -> Any?): GamesArgs {
            val ids = get("tagIds").stringList().orEmpty()
            val names = get("tagNames").stringList().orEmpty()
            val tags = ids.mapIndexed { index, id -> Tag(id = id.ifEmpty { null }, name = names.getOrNull(index)?.ifEmpty { null }) }
            return GamesArgs(tags.takeIf { it.isNotEmpty() }?.toTypedArray())
        }

        fun fromSavedStateHandle(handle: SavedStateHandle) = from { handle.get<Any?>(it) }
        fun fromBundle(bundle: Bundle?) = from { @Suppress("DEPRECATION") bundle?.get(it) }
    }

    override fun equals(other: Any?) = other is GamesArgs && tags.contentEquals(other.tags)
    override fun hashCode() = tags.contentHashCode()
}

/** The arguments of [TopStreamsRoute]. */
data class TopStreamsArgs(val tags: Array<String>?, val languages: Array<String>?) {
    companion object {
        fun from(get: (String) -> Any?) = TopStreamsArgs(
            tags = get("tags").stringList()?.takeIf { it.isNotEmpty() }?.toTypedArray(),
            languages = get("languages").stringList()?.takeIf { it.isNotEmpty() }?.toTypedArray(),
        )

        fun fromSavedStateHandle(handle: SavedStateHandle) = from { handle.get<Any?>(it) }
        fun fromBundle(bundle: Bundle?) = from { @Suppress("DEPRECATION") bundle?.get(it) }
    }

    override fun equals(other: Any?) = other is TopStreamsArgs && tags.contentEquals(other.tags) && languages.contentEquals(other.languages)
    override fun hashCode() = 31 * tags.contentHashCode() + languages.contentHashCode()
}

/** The arguments of [GameRoute] as the game view models read them (v0.1 GamePagerFragmentArgs). */
data class GameArgs(
    val gameId: String? = null,
    val gameSlug: String? = null,
    val gameName: String? = null,
    val boxArt: String? = null,
    val tags: Array<String>? = null,
    val languages: Array<String>? = null,
    val updateLocal: Boolean = false,
) {
    fun toRoute() = GameRoute(gameId, gameSlug, gameName, boxArt, tags?.toList().orEmpty(), languages?.toList().orEmpty(), updateLocal)

    /** For view models that do not live in a navigation entry (a game opened inside Browse, U4). */
    fun toSavedStateHandle() = mapOf(
        "gameId" to gameId, "gameSlug" to gameSlug, "gameName" to gameName, "boxArt" to boxArt,
        "tags" to tags, "languages" to languages, "updateLocal" to updateLocal,
    ).toHandle()

    companion object {
        fun from(get: (String) -> Any?) = GameArgs(
            gameId = get("gameId") as? String,
            gameSlug = get("gameSlug") as? String,
            gameName = get("gameName") as? String,
            boxArt = get("boxArt") as? String,
            tags = get("tags").stringList()?.takeIf { it.isNotEmpty() }?.toTypedArray(),
            languages = get("languages").stringList()?.takeIf { it.isNotEmpty() }?.toTypedArray(),
            updateLocal = get("updateLocal") as? Boolean ?: false,
        )

        fun fromSavedStateHandle(handle: SavedStateHandle) = from { handle.get<Any?>(it) }
        fun fromBundle(bundle: Bundle?) = from { @Suppress("DEPRECATION") bundle?.get(it) }
    }

    override fun equals(other: Any?) = other is GameArgs && gameId == other.gameId && gameSlug == other.gameSlug &&
            gameName == other.gameName && boxArt == other.boxArt && tags.contentEquals(other.tags) &&
            languages.contentEquals(other.languages) && updateLocal == other.updateLocal

    override fun hashCode() = listOf(gameId, gameSlug, gameName, boxArt, tags?.toList(), languages?.toList(), updateLocal).hashCode()
}

/** The arguments of [ChannelRoute] (v0.1 ChannelPagerFragmentArgs). */
data class ChannelArgs(
    val channelId: String? = null,
    val channelLogin: String? = null,
    val channelName: String? = null,
    val channelImage: String? = null,
    val updateLocal: Boolean = false,
    val streamId: String? = null,
) {
    companion object {
        fun from(get: (String) -> Any?) = ChannelArgs(
            channelId = get("channelId") as? String,
            channelLogin = get("channelLogin") as? String,
            channelName = get("channelName") as? String,
            channelImage = get("channelImage") as? String,
            updateLocal = get("updateLocal") as? Boolean ?: false,
            streamId = get("streamId") as? String,
        )

        fun fromSavedStateHandle(handle: SavedStateHandle) = from { handle.get<Any?>(it) }
        fun fromBundle(bundle: Bundle?) = from { @Suppress("DEPRECATION") bundle?.get(it) }
    }
}

/** The arguments of [TeamRoute]. */
data class TeamArgs(val teamName: String? = null) {
    companion object {
        fun fromSavedStateHandle(handle: SavedStateHandle) = TeamArgs(handle.get<String>("teamName"))
        fun fromBundle(bundle: Bundle?) = TeamArgs(bundle?.getString("teamName"))
    }
}
