package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
class ChannelPointContextResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    class Data(
        val community: Community,
    )

    @Serializable
    class Community(
        val channel: Channel,
    )

    @Serializable
    class Channel(
        val self: Self? = null,
        val communityPointsSettings: Settings? = null,
    )

    @Serializable
    class Self(
        val communityPoints: Points? = null,
    )

    /** The channel's own name and picture for its points, when it set them. */
    @Serializable
    class Settings(
        val name: String? = null,
        val image: Image? = null,
    )

    @Serializable
    class Image(
        val url: String? = null,
        val url2x: String? = null,
        val url4x: String? = null,
    )

    @Serializable
    class Points(
        val balance: Int? = null,
        val availableClaim: Claim? = null,
    )

    @Serializable
    class Claim(
        val id: String? = null,
    )
}