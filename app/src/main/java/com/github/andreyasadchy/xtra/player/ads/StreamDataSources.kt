package com.github.andreyasadchy.xtra.player.ads

import android.annotation.SuppressLint
import android.app.Application
import android.net.http.HttpEngine
import android.net.http.ProxyOptions
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.github.andreyasadchy.xtra.XtraModule
import com.github.andreyasadchy.xtra.player.lowlatency.CronetDataSource
import com.github.andreyasadchy.xtra.player.lowlatency.HttpEngineDataSource
import com.github.andreyasadchy.xtra.player.lowlatency.OkHttpDataSource
import com.github.andreyasadchy.xtra.ui.player.ExoPlayerService
import com.github.andreyasadchy.xtra.util.C
import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.chromium.net.CronetEngine
import org.chromium.net.CronetProvider
import org.chromium.net.QuicOptions
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * HTTP data sources for live streams, for the selected network library, optionally sending one
 * kind of playlist request through an HTTP proxy.
 */
@OptIn(UnstableApi::class)
object StreamDataSources {

    data class HttpProxy(
        val host: String,
        val port: Int,
        val username: String?,
        val password: String?,
    )

    enum class ProxyTarget(val hostRegex: String) {
        MULTIVARIANT_PLAYLIST(ExoPlayerService.MULTIVARIANT_PLAYLIST_REGEX),
        MEDIA_PLAYLIST(ExoPlayerService.MEDIA_PLAYLIST_REGEX),
    }

    /**
     * @param customProxyUrl the custom proxy playlist url, requested with [proxyTimeoutMs]
     * @param proxy HTTP proxy for the requests chosen by [proxyTarget]
     * @param useMediaPlaylistProxy checked on every media playlist request
     */
    fun factory(
        application: Application,
        xtraModule: XtraModule,
        networkLibrary: String?,
        proxyTimeoutMs: Int,
        customProxyUrl: String? = null,
        proxy: HttpProxy? = null,
        proxyTarget: ProxyTarget = ProxyTarget.MULTIVARIANT_PLAYLIST,
        useMediaPlaylistProxy: () -> Boolean = { false },
    ): DataSource.Factory {
        val proxyMultivariant = proxy != null && proxyTarget == ProxyTarget.MULTIVARIANT_PLAYLIST
        val proxyMedia = proxy != null && proxyTarget == ProxyTarget.MEDIA_PLAYLIST
        return when {
            networkLibrary == C.HTTP_ENGINE && xtraModule.httpEngine.value != null -> @SuppressLint("NewApi") {
                val proxyEngine = proxy?.let { httpEngineWithProxy(application, xtraModule, it) }
                val fallbackClient = if (proxy != null && proxyEngine == null) okHttpWithProxy(xtraModule, proxy, proxyTarget, proxyTimeoutMs) else null
                HttpEngineDataSource.Factory(
                    xtraModule.httpEngine.value,
                    xtraModule.cronetExecutor.value,
                    customProxyUrl,
                    proxyTimeoutMs,
                    proxyMultivariant,
                    proxyMedia,
                    proxyEngine,
                    fallbackClient.takeIf { proxyMultivariant },
                    fallbackClient.takeIf { proxyMedia },
                    useMediaPlaylistProxy
                )
            }
            networkLibrary == C.CRONET && xtraModule.cronetEngine.value != null -> {
                val proxyEngine = proxy?.let { cronetWithProxy(application, xtraModule, it) }
                val fallbackClient = if (proxy != null && proxyEngine == null) okHttpWithProxy(xtraModule, proxy, proxyTarget, proxyTimeoutMs) else null
                CronetDataSource.Factory(
                    xtraModule.cronetEngine.value,
                    xtraModule.cronetExecutor.value,
                    customProxyUrl,
                    proxyTimeoutMs,
                    proxyMultivariant,
                    proxyMedia,
                    proxyEngine,
                    fallbackClient.takeIf { proxyMultivariant },
                    fallbackClient.takeIf { proxyMedia },
                    useMediaPlaylistProxy
                )
            }
            else -> {
                val customProxyClient = if (customProxyUrl != null) {
                    xtraModule.okHttpClient.value.newBuilder().apply {
                        timeouts(proxyTimeoutMs)
                    }.build()
                } else null
                val proxyClient = proxy?.let { okHttpWithProxy(xtraModule, it, proxyTarget, proxyTimeoutMs) }
                OkHttpDataSource.Factory(
                    xtraModule.okHttpClient.value,
                    customProxyClient,
                    customProxyUrl,
                    proxyClient.takeIf { proxyMultivariant },
                    proxyClient.takeIf { proxyMedia },
                    useMediaPlaylistProxy
                )
            }
        }
    }

    private fun OkHttpClient.Builder.timeouts(timeoutMs: Int) {
        val timeout = timeoutMs.toLong()
        connectTimeout(timeout, TimeUnit.MILLISECONDS)
        writeTimeout(timeout, TimeUnit.MILLISECONDS)
        readTimeout(timeout, TimeUnit.MILLISECONDS)
    }

    private fun okHttpWithProxy(xtraModule: XtraModule, proxy: HttpProxy, target: ProxyTarget, timeoutMs: Int): OkHttpClient {
        val hostRegex = Regex(target.hostRegex)
        return xtraModule.okHttpClient.value.newBuilder().apply {
            timeouts(timeoutMs)
            proxySelector(
                object : ProxySelector() {
                    override fun select(u: URI): List<Proxy> {
                        return if (hostRegex.matches(u.host)) {
                            listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxy.host, proxy.port)), Proxy.NO_PROXY)
                        } else {
                            listOf(Proxy.NO_PROXY)
                        }
                    }

                    override fun connectFailed(u: URI, sa: SocketAddress, e: IOException) {}
                }
            )
            if (!proxy.username.isNullOrBlank() && !proxy.password.isNullOrBlank()) {
                proxyAuthenticator { _, response ->
                    response.request.newBuilder().header(
                        "Proxy-Authorization", Credentials.basic(proxy.username, proxy.password)
                    ).build()
                }
            }
        }.build()
    }

    @SuppressLint("NewApi")
    private fun httpEngineWithProxy(application: Application, xtraModule: XtraModule, proxy: HttpProxy): HttpEngine? {
        val proxyHeaders = if (!proxy.username.isNullOrBlank() && !proxy.password.isNullOrBlank()) {
            listOf(android.util.Pair("Proxy-Authorization", Base64.encodeToString("${proxy.username}:${proxy.password}".toByteArray(), Base64.NO_WRAP)))
        } else emptyList()
        val builder = HttpEngine.Builder(application)
        return try {
            builder.setProxyOptions(ProxyOptions.fromProxyList(
                listOf(
                    android.net.http.Proxy.createHttpProxy(
                        android.net.http.Proxy.SCHEME_HTTP,
                        proxy.host,
                        proxy.port,
                        xtraModule.cronetExecutor.value,
                        object : android.net.http.Proxy.HttpConnectCallback {
                            override fun onBeforeRequest(request: android.net.http.Proxy.HttpConnectCallback.Request) {
                                request.proceed(proxyHeaders)
                            }

                            override fun onResponseReceived(responseHeaders: List<android.util.Pair<String?, String?>?>, statusCode: Int): Int {
                                return android.net.http.Proxy.HttpConnectCallback.RESPONSE_ACTION_PROCEED
                            }
                        }
                    )
                ),
                ProxyOptions.ALL_PROXIES_FAILED_BEHAVIOR_DISALLOW_DIRECT
            ))
        } catch (e: NoClassDefFoundError) {
            null
        }?.build()
    }

    private fun cronetWithProxy(application: Application, xtraModule: XtraModule, proxy: HttpProxy): CronetEngine? {
        if (CronetProvider.getAllProviders(application).none { it.isEnabled }) {
            return null
        }
        val proxyHeaders = if (!proxy.username.isNullOrBlank() && !proxy.password.isNullOrBlank()) {
            mapOf("Proxy-Authorization" to Base64.encodeToString("${proxy.username}:${proxy.password}".toByteArray(), Base64.NO_WRAP)).entries.toList()
        } else emptyList()
        val builder = CronetEngine.Builder(application).apply {
            val userAgent = "Cronet/" + defaultUserAgent.substringAfter("Cronet/", "").substringBefore(')')
            setUserAgent(userAgent)
            @QuicOptions.Experimental
            setQuicOptions(QuicOptions.builder().setHandshakeUserAgent(userAgent).build())
        }
        return try {
            @org.chromium.net.ProxyOptions.Experimental
            builder.setProxyOptions(org.chromium.net.ProxyOptions(
                listOf(
                    org.chromium.net.Proxy(
                        org.chromium.net.Proxy.HTTP,
                        proxy.host,
                        proxy.port,
                        xtraModule.cronetExecutor.value,
                        object : org.chromium.net.Proxy.Callback() {
                            override fun onBeforeTunnelRequest(request: org.chromium.net.Proxy.Callback.Request) {
                                request.proceed(proxyHeaders)
                            }

                            override fun onTunnelHeadersReceived(responseHeaders: List<Map.Entry<String?, String?>?>, statusCode: Int): Boolean {
                                return true
                            }
                        }
                    )
                )
            ))
        } catch (e: UnsupportedOperationException) {
            null
        }?.build()
    }
}
