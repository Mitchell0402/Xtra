package com.github.andreyasadchy.xtra.player.ads

/**
 * A stream that can replace the primary one while it plays an ad.
 */
sealed class AdSource {
    /** The primary's media playlist, requested through an HTTP proxy in an ad free region. */
    data class StreamProxy(
        val host: String,
        val port: Int,
        val username: String?,
        val password: String?,
    ) : AdSource() {
        override val label get() = "proxy $host:$port"
    }

    /** A new playback access token with another platform / player type. */
    data class VideoSwap(
        val platform: String,
        val playerType: String,
    ) : AdSource() {
        override val label get() = "swap $platform/$playerType"
    }

    abstract val label: String
}

/**
 * The order in which ad sources are tried during an ad break: stream proxies first, then video
 * swap items. A stream proxy whose stream had ads is skipped for the rest of the stream; video
 * swap items get a fresh try at every ad break.
 */
class AdSourceQueue(
    /** Read at every call of [next], so settings that change during the stream are followed. */
    private val sources: () -> List<AdSource>,
) {
    private var index = 0
    private val badForStream = mutableSetOf<AdSource>()

    /** The next source to try in this ad break, or null when none is left. */
    fun next(): AdSource? {
        val sources = sources()
        while (index < sources.size) {
            val source = sources[index++]
            if (source !in badForStream) {
                return source
            }
        }
        return null
    }

    /** The source played ads too or failed to load. */
    fun markBad(source: AdSource) {
        if (source is AdSource.StreamProxy) {
            badForStream.add(source)
        }
    }

    fun startNextBreak() {
        index = 0
    }

    fun reset() {
        index = 0
        badForStream.clear()
    }
}
