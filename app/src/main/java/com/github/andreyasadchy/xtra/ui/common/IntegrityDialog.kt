package com.github.andreyasadchy.xtra.ui.common

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import androidx.webkit.WebViewClientCompat
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.SettingIconTile
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import org.json.JSONObject

/**
 * The integrity check (plan 7.8: U3): Twitch's login page in a web view, which sends the Client-Integrity header the
 * app then stores. The header capture is the v0.1 code; the frame around the web view is new (full screen, Compose,
 * with a title, one line of explanation and a close button instead of a bare dialog).
 */
class IntegrityDialog : DialogFragment() {

    interface Listener {
        fun onIntegrityTokenLoaded(callback: String?)
    }

    private var listener: Listener? = null
    private var webView: WebView? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as? Listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), android.R.style.Theme_DeviceDefault_NoActionBar).apply {
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                it.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        CookieManager.getInstance().removeAllCookies(null)
        val token = TwitchApiHelper.getGQLHeaders(context, true)[C.HEADER_TOKEN]?.removePrefix("OAuth ")
        if (!token.isNullOrBlank()) {
            CookieManager.getInstance().setCookie("https://www.twitch.tv", "auth-token=$token")
        }
        val view = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClientCompat() {

                override fun shouldInterceptRequest(view: WebView, webViewRequest: WebResourceRequest): WebResourceResponse? {
                    if (!webViewRequest.requestHeaders.entries.find { it.key.equals("Client-Integrity", true) }?.value.isNullOrBlank()) {
                        context.tokenPrefs().edit {
                            putLong(C.INTEGRITY_EXPIRATION, System.currentTimeMillis() + 57600000)
                            putString(C.GQL_HEADERS, JSONObject(
                                if (context.prefs().getBoolean(C.GET_ALL_GQL_HEADERS, false)) {
                                    webViewRequest.requestHeaders
                                } else {
                                    webViewRequest.requestHeaders.filterKeys {
                                        it.equals(C.HEADER_TOKEN, true) ||
                                                it.equals(C.HEADER_CLIENT_ID, true) ||
                                                it.equals("Client-Integrity", true) ||
                                                it.equals("X-Device-Id", true)
                                    }
                                }
                            ).toString())
                        }
                        listener?.onIntegrityTokenLoaded(requireArguments().getString(KEY_CALLBACK))
                        dismiss()
                    }
                    return super.shouldInterceptRequest(view, webViewRequest)
                }
            }
            loadUrl("https://www.twitch.tv/login")
        }
        webView = view
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LagoonTheme {
                    val colors = Lagoon.colors
                    Column(Modifier.fillMaxSize().background(colors.page).windowInsetsPadding(WindowInsets.safeDrawing)) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SettingIconTile(LucideIcons.ShieldCheck, highlight = true)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(stringResource(R.string.integrity_title), style = Lagoon.type.headline, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
                                Text(stringResource(R.string.integrity_summary), style = Lagoon.type.label, color = colors.textSecondary)
                            }
                            LagoonIconButton(LucideIcons.X, stringResource(R.string.st_back), onClick = { dismiss() }, filled = false)
                        }
                        AndroidView(
                            factory = { view },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .padding(bottom = 8.dp)
                                .clip(LagoonShapes.card),
                        )
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        (activity as? MainActivity)?.integrityTokenLoaded()
        webView?.loadUrl("about:blank")
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView = null
    }

    companion object {
        private const val KEY_CALLBACK = "callback"

        fun newInstance(callback: String?): IntegrityDialog {
            return IntegrityDialog().apply {
                arguments = Bundle().apply {
                    putString(KEY_CALLBACK, callback)
                }
            }
        }
    }
}
