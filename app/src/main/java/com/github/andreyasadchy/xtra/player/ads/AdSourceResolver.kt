package com.github.andreyasadchy.xtra.player.ads

import android.annotation.SuppressLint
import android.content.Context
import com.github.andreyasadchy.xtra.XtraModule
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.NetworkUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import com.github.andreyasadchy.xtra.util.NetworkUtils.executeAsync

/**
 * Turns a [AdSource.VideoSwap] into a media playlist url: requests a playback access token with
 * the source's platform and player type and picks the best variant of the multivariant playlist.
 */
class AdSourceResolver(
    private val context: Context,
    private val xtraModule: XtraModule,
    private val networkLibrary: String?,
) {
    /** @param maxHeight the primary's selected quality, null for the best one */
    suspend fun resolveVideoSwap(channelLogin: String, source: AdSource.VideoSwap, gqlHeaders: Map<String, String>, supportedCodecs: String?, maxHeight: Int? = null): String? {
        val playlistUrl = try {
            xtraModule.playerRepository.loadStreamPlaylistUrl(
                context = context,
                networkLibrary = networkLibrary,
                gqlHeaders = gqlHeaders,
                channelLogin = channelLogin,
                platform = source.platform,
                playerType = source.playerType,
                supportedCodecs = supportedCodecs,
                proxyPlaybackAccessToken = false,
                proxyHost = null,
                proxyPort = null,
                proxyUser = null,
                proxyPassword = null,
                proxyTimeout = null,
                enableIntegrity = false
            )
        } catch (e: Exception) {
            AdLog.w("${source.label}: token request failed", e)
            null
        } ?: return null
        val playlist = fetch(playlistUrl)
        if (playlist.isNullOrBlank()) {
            AdLog.w("${source.label}: multivariant playlist request failed")
            return null
        }
        return MultivariantPlaylist.bestVariantUrl(playlist, maxHeight)?.let { java.net.URI(playlistUrl).resolve(it).toString() }
    }

    suspend fun fetch(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                when {
                    networkLibrary == C.HTTP_ENGINE && xtraModule.httpEngine.value != null -> @SuppressLint("NewApi") {
                        val response = suspendCancellableCoroutine { continuation ->
                            val timeout = NetworkUtils.HttpEngineTimeout()
                            val request = xtraModule.httpEngine.value!!.newUrlRequestBuilder(
                                url,
                                xtraModule.cronetExecutor.value,
                                NetworkUtils.ByteArrayUrlCallback(continuation, timeout)
                            ).build()
                            timeout.start(request, continuation)
                            request.start()
                            continuation.invokeOnCancellation {
                                request.cancel()
                                timeout.stop()
                            }
                        }
                        if (response.info.httpStatusCode in 200..299) {
                            response.body.decodeToString()
                        } else null
                    }
                    networkLibrary == C.CRONET && xtraModule.cronetEngine.value != null -> {
                        val response = suspendCancellableCoroutine { continuation ->
                            val timeout = NetworkUtils.CronetTimeout()
                            val request = xtraModule.cronetEngine.value!!.newUrlRequestBuilder(
                                url,
                                NetworkUtils.ByteArrayCronetCallback(continuation, timeout),
                                xtraModule.cronetExecutor.value
                            ).build()
                            timeout.start(request, continuation)
                            request.start()
                            continuation.invokeOnCancellation {
                                request.cancel()
                                timeout.stop()
                            }
                        }
                        if (response.info.httpStatusCode in 200..299) {
                            response.body.decodeToString()
                        } else null
                    }
                    else -> {
                        xtraModule.okHttpClient.value.newCall(Request.Builder().url(url).build()).executeAsync().use { response ->
                            if (response.isSuccessful) {
                                response.body.string()
                            } else null
                        }
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    companion object {
        fun gqlHeaders(context: Context, prefs: android.content.SharedPreferences) =
            TwitchApiHelper.getGQLHeaders(context, prefs.getBoolean(C.TOKEN_INCLUDE_TOKEN_STREAM, true))
    }
}

/** Reads the variants of a Twitch multivariant playlist. */
object MultivariantPlaylist {

    data class Variant(
        val name: String,
        val height: Int?,
        val frameRate: Float?,
        val bitrate: Int?,
        val codecs: String?,
        val url: String,
    )

    fun variants(playlist: String): List<Variant> {
        val variants = mutableListOf<Variant>()
        var streamInf: String? = null
        var media = emptyMap<String, String>()
        val names = mutableMapOf<String, String>()
        playlist.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("#EXT-X-MEDIA:") -> {
                    media = attributes(line.substringAfter(':'))
                    val group = media["GROUP-ID"]
                    val name = media["NAME"]
                    if (group != null && name != null) {
                        names[group] = name
                    }
                }
                line.startsWith("#EXT-X-STREAM-INF:") -> streamInf = line.substringAfter(':')
                !line.startsWith("#") && streamInf != null -> {
                    val attributes = attributes(streamInf)
                    val name = attributes["STABLE-VARIANT-ID"] ?: attributes["VIDEO"]?.let { names[it] ?: it }
                    if (name != null) {
                        variants.add(
                            Variant(
                                name = name,
                                height = attributes["RESOLUTION"]?.substringAfter('x')?.toIntOrNull(),
                                frameRate = attributes["FRAME-RATE"]?.toFloatOrNull(),
                                bitrate = attributes["BANDWIDTH"]?.toIntOrNull(),
                                codecs = attributes["CODECS"],
                                url = line,
                            )
                        )
                    }
                    streamInf = null
                }
            }
        }
        return variants
    }

    /**
     * The highest quality video variant: source first, then by resolution, frame rate and
     * bitrate. With [maxHeight], the best one up to that height (or the lowest when all are
     * higher).
     */
    fun bestVariantUrl(playlist: String, maxHeight: Int? = null): String? {
        val video = variants(playlist).filter {
            !it.name.endsWith("-portrait") && !it.name.startsWith("audio", true)
        }
        val sorted = video.sortedWith(compareByDescending<Variant> { it.height ?: 0 }.thenByDescending { it.frameRate ?: 0f }.thenByDescending { it.bitrate ?: 0 })
        if (maxHeight != null) {
            return (sorted.firstOrNull { (it.height ?: 0) <= maxHeight } ?: sorted.lastOrNull())?.url
        }
        return (video.find { it.name.equals("source", true) || it.name.equals("chunked", true) } ?: sorted.firstOrNull())?.url
    }

    private val attributeRegex = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""")

    private fun attributes(value: String): Map<String, String> {
        return attributeRegex.findAll(value).associate { match ->
            match.groupValues[1] to match.groupValues[2].removeSurrounding("\"")
        }
    }
}
