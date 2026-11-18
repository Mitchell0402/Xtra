package com.github.andreyasadchy.xtra.ui.game

import com.github.andreyasadchy.xtra.ui.design.components.rememberLagoonToastState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import com.github.andreyasadchy.xtra.ui.design.components.GlassContext
import com.github.andreyasadchy.xtra.ui.design.components.LocalGlass
import com.github.andreyasadchy.xtra.ui.main.MainViewModel
import com.github.andreyasadchy.xtra.ui.main.nav.ComposePage
import com.github.andreyasadchy.xtra.ui.main.nav.GameArgs
import com.github.andreyasadchy.xtra.ui.main.nav.PageHost

/**
 * A game's page (U4), in place of the v0.1 GamePagerFragment and GameMediaFragment and their three list fragments;
 * a navigation destination of its own since U8. The view models live in the page's navigation entry.
 */
class GameDestination(host: PageHost) : ComposePage(host) {

    private val mainViewModel: MainViewModel by activityViewModels { MainViewModel.Companion.MainViewModelFactory }
    private val scrollToTopRequests = mutableIntStateOf(0)
    private val page by lazy {
        GamePage(this, host.entry, GameArgs.fromBundle(arguments), mainViewModel) { nav.back() }
    }

    override fun onCreate() {
        page.start()
    }

    @Composable
    override fun Content() {
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottom = shell?.let { with(LocalDensity.current) { it.bottomBarHeightPx.toDp() } }?.takeIf { it.value > 0f } ?: navBottom
        CompositionLocalProvider(LocalGlass provides (shell?.pageGlass() ?: GlassContext.Solid)) {
            page.Content(
                bottomPadding = bottom,
                navScroll = shell?.hideOnScroll?.nestedScrollConnection,
                scrollToTopRequests = scrollToTopRequests.intValue,
                toast = rememberLagoonToastState(),
            )
        }
    }

    override fun scrollToTop() {
        scrollToTopRequests.intValue++
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        page.onIntegrityTokenLoaded(callback)
    }
}
