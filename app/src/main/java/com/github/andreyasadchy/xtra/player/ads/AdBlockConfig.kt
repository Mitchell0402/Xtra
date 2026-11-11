package com.github.andreyasadchy.xtra.player.ads

import android.content.SharedPreferences
import com.github.andreyasadchy.xtra.model.ui.CustomProxy
import com.github.andreyasadchy.xtra.model.ui.StreamProxy
import com.github.andreyasadchy.xtra.model.ui.VideoSwap
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.util.C

/**
 * Every ad blocking setting for live streams, read in one place with its default.
 */
data class AdBlockConfig(
    val useCustomProxy: Boolean,
    val useStreamProxy: Boolean,
    val useVideoSwap: Boolean,
    val hideAds: Boolean,
    /** Layer 3: ad segments of live playlists become gaps that are never loaded. */
    val filterAdSegments: Boolean,
    val proxyTimeoutMs: Int,
    val customProxies: List<CustomProxy>,
    /** Enabled stream proxies with a host, a port and at least one thing to proxy. */
    val streamProxies: List<StreamProxy>,
    val videoSwaps: List<VideoSwap>,
) {
    /**
     * Ad sources for the secondary player, in the order they are tried.
     * @param useStreamProxy whether the stream proxy is in use for this stream (it is turned off
     * when the custom proxy is used or every stream proxy failed)
     */
    fun adSources(useStreamProxy: Boolean): List<AdSource> {
        val proxies = if (useStreamProxy) {
            streamProxies.filter { it.proxyMediaPlaylist }.mapNotNull { proxy ->
                val host = proxy.host
                val port = proxy.port
                if (!host.isNullOrBlank() && port != null) {
                    AdSource.StreamProxy(host, port, proxy.username, proxy.password)
                } else null
            }
        } else emptyList()
        val swaps = if (useVideoSwap) {
            videoSwaps.mapNotNull { item ->
                val platform = item.platform
                val playerType = item.playerType
                if (!platform.isNullOrBlank() && !playerType.isNullOrBlank()) {
                    AdSource.VideoSwap(platform, playerType)
                } else null
            }
        } else emptyList()
        return proxies + swaps
    }

    companion object {
        suspend fun load(prefs: SharedPreferences, repository: PlayerRepository): AdBlockConfig {
            val useCustomProxy = prefs.getBoolean(C.PLAYER_USE_CUSTOM_PROXY, true)
            return AdBlockConfig(
                useCustomProxy = useCustomProxy,
                // the custom proxy replaces the whole stream, the stream proxy only works without it
                useStreamProxy = !useCustomProxy && prefs.getBoolean(C.PLAYER_USE_STREAM_PROXY, false),
                useVideoSwap = prefs.getBoolean(C.PLAYER_USE_VIDEO_SWAP, true),
                hideAds = prefs.getBoolean(C.PLAYER_HIDE_ADS, true),
                filterAdSegments = prefs.getBoolean(C.PLAYER_FILTER_AD_SEGMENTS, true),
                proxyTimeoutMs = prefs.getString(C.PROXY_TIMEOUT, "3000")?.toIntOrNull() ?: 3000,
                customProxies = repository.getCustomProxies().filter {
                    it.enabled && !it.url.isNullOrBlank()
                }.sortedBy { it.position },
                streamProxies = repository.getStreamProxies().filter {
                    it.enabled && !it.host.isNullOrBlank() && it.port != null
                            && (it.proxyPlaybackAccessToken || it.proxyMultivariantPlaylist || it.proxyMediaPlaylist)
                }.sortedBy { it.position },
                videoSwaps = repository.getVideoSwapItems().filter {
                    it.enabled && !it.platform.isNullOrBlank() && !it.playerType.isNullOrBlank()
                }.sortedBy { it.position },
            )
        }
    }
}
