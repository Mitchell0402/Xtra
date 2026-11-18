package com.github.andreyasadchy.xtra.ui.games

import com.github.andreyasadchy.xtra.ui.main.shell.LocalSettingsVersion
import com.github.andreyasadchy.xtra.ui.design.components.rememberLagoonToastState
import androidx.compose.runtime.Composable
import com.github.andreyasadchy.xtra.ui.main.nav.ComposePage
import com.github.andreyasadchy.xtra.ui.main.nav.GamesArgs
import com.github.andreyasadchy.xtra.ui.main.nav.PageHost
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.browse.BrowseFilter
import com.github.andreyasadchy.xtra.ui.browse.FilterLogic
import com.github.andreyasadchy.xtra.ui.browse.FilterTag
import com.github.andreyasadchy.xtra.ui.browse.PageBrowseActions
import com.github.andreyasadchy.xtra.ui.browse.hasValidatedNetwork
import com.github.andreyasadchy.xtra.ui.common.SearchTagsViewModel
import com.github.andreyasadchy.xtra.ui.design.components.GlassContext
import com.github.andreyasadchy.xtra.ui.design.components.LocalGlass
import com.github.andreyasadchy.xtra.ui.following.AccountSheet
import com.github.andreyasadchy.xtra.ui.following.ConfirmSheet
import com.github.andreyasadchy.xtra.ui.following.FollowingAccount
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.ui.game.GamePage
import com.github.andreyasadchy.xtra.ui.games.GamesViewModel.Companion.GamesViewModelFactory
import com.github.andreyasadchy.xtra.ui.main.MainViewModel
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * The Browse page in Compose (U4): the games grid, and a game opened from it as a page over the grid in the same
 * composition, for the box-art shared transition and gesture-following back (plan 5 and 10.6 U4). Also the "games
 * with this tag" destination (U8: GamesRoute).
 */
class BrowseDestination(host: PageHost) : ComposePage(host), GamesGridActions {

    private val args = GamesArgs.fromBundle(arguments)
    private val viewModel: GamesViewModel by viewModels { GamesViewModelFactory }
    private val tagsViewModel: SearchTagsViewModel by viewModels { SearchTagsViewModel.SearchTagsViewModelFactory }
    private val detail: BrowseDetailViewModel by viewModels { BrowseDetailViewModel.Factory }
    private val mainViewModel: MainViewModel by activityViewModels { MainViewModel.Companion.MainViewModelFactory }
    private val pages = mutableMapOf<String, GamePage>()
    private val scrollToTopRequests = mutableIntStateOf(0)
    private val refreshRequests = mutableIntStateOf(0)
    private val accountRequests = mutableIntStateOf(0)
    private val actions by lazy { object : PageBrowseActions(this@BrowseDestination) {
        override fun toggleBookmark(video: com.github.andreyasadchy.xtra.model.ui.Video) {}
    } }

    override fun onCreate() {
        tagsViewModel.getGameTags = true
        // v0.1 initialize(): the tags from the link, kept when the page comes back
        if (viewModel.filter.value == null) {
            viewModel.setFilter(viewModel.tags.ifEmpty { args.tags })
        }
    }

    private fun page(ref: GameRef): GamePage = pages.getOrPut(ref.key) {
        GamePage(this, detail.owner(ref.key), ref.args(), mainViewModel) { detail.close() }.also { it.start() }
    }

    @Composable
    override fun Content() {
        val context = requireContext()
        val prefs = context.prefs()
        val root = args.tags.isNullOrEmpty()
        // settings that change without a rebuild are read again when they do (motion plan M5 item 3)
        LocalSettingsVersion.current
        val options = FollowingListOptions(
            nameDisplay = prefs.getString(C.UI_NAME_DISPLAY, "0") ?: "0",
            showTags = prefs.getBoolean(C.UI_TAGS, true),
            truncateCounts = prefs.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true),
            showBroadcasters = prefs.getBoolean(C.UI_BROADCASTERS_COUNT, true),
        )
        val gqlToken = TwitchApiHelper.getGQLHeaders(context, true)[C.HEADER_TOKEN]
        val loggedIn = !gqlToken.isNullOrBlank() || !TwitchApiHelper.getHelixHeaders(context)[C.HEADER_TOKEN].isNullOrBlank()
        val account = FollowingAccount(loggedIn, context.tokenPrefs().getString(C.USERNAME, null))
        val filterValue by viewModel.filter.collectAsState()
        val games = (if (filterValue != null) viewModel.flow else emptyFlow()).collectAsLazyPagingItems()
        val tagResults = tagsViewModel.flow.collectAsLazyPagingItems()
        val open by detail.open.collectAsState()
        val online by mainViewModel.isNetworkAvailable.collectAsState()
        val grid = rememberLazyGridState()
        val toast = rememberLagoonToastState()
        var accountSheet by rememberSaveable { mutableStateOf(false) }
        var logOutSheet by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(accountRequests.intValue) { if (accountRequests.intValue > 0) accountSheet = true }
        LaunchedEffect(refreshRequests.intValue) { if (refreshRequests.intValue > 0) games.refresh() }
        LaunchedEffect(Unit) {
            var wasOnline = mainViewModel.isNetworkAvailable.value ?: context.hasValidatedNetwork()
            mainViewModel.isNetworkAvailable.collectLatest { now ->
                if (now == true && !wasOnline) games.retry()
                if (now != null) wasOnline = now
            }
        }
        val offline = online == false || (online == null && !context.hasValidatedNetwork())
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottom = shell?.let { with(LocalDensity.current) { it.bottomBarHeightPx.toDp() } }?.takeIf { it.value > 0f } ?: navBottom
        val navScroll = shell?.hideOnScroll?.nestedScrollConnection
        CompositionLocalProvider(LocalGlass provides (shell?.pageGlass() ?: GlassContext.Solid)) {
            BrowseDetailHost(
                open = open,
                onClose = detail::close,
                onSettled = { settled ->
                    detail.release(settled?.key)
                    pages.keys.retainAll(setOfNotNull(settled?.key))
                },
                backEnabled = shell?.playerMaximized != true,
                grid = { share ->
                    GamesScreen(
                        root = root,
                        games = games.takeIf { filterValue != null },
                        filter = BrowseFilter(tags = viewModel.tags.map { FilterTag(it.id, it.name.orEmpty()) }),
                        options = options,
                        actions = this@BrowseDestination,
                        grid = grid,
                        tagResults = tagResults,
                        posterModifier = { game -> Modifier.sharedBoxArt(share, GameRef(game.id, game.slug, game.name, game.boxArt).key) },
                        bottomPadding = bottom,
                        navScroll = navScroll,
                        scrollToTopRequests = scrollToTopRequests.intValue,
                        toast = toast,
                        offline = offline,
                    )
                },
                detail = { ref, share ->
                    val page = remember(ref.key) { page(ref) }
                    page.Content(
                        bottomPadding = bottom,
                        navScroll = navScroll,
                        scrollToTopRequests = 0,
                        toast = toast,
                        boxArtModifier = Modifier.sharedBoxArt(share, ref.key),
                    )
                },
            )
        }
        if (accountSheet) {
            AccountSheet(
                account,
                onSettings = actions::settings,
                onLogIn = actions::logIn,
                onDismiss = { accountSheet = false },
                onLogOut = { logOutSheet = true },
            )
        }
        if (logOutSheet) {
            ConfirmSheet(
                title = stringResource(R.string.following_log_out_confirm, account.name ?: ""),
                message = stringResource(R.string.following_log_out_message),
                confirm = stringResource(R.string.following_log_out),
                onConfirm = actions::logOut,
                onDismiss = { logOutSheet = false },
            )
        }
    }

    /** A tap on the current tab: closes an open game first (v0.1 popped back to the tab's first page), else scrolls up. */
    override fun scrollToTop() {
        if (detail.open.value != null) detail.close() else scrollToTopRequests.intValue++
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        val open = detail.open.value
        if (open != null) {
            page(open).onIntegrityTokenLoaded(callback)
        } else if (callback == PageBrowseActions.CALLBACK_REFRESH) {
            refreshRequests.intValue++
        }
    }

    // GamesGridActions

    override fun openGame(game: Game) {
        detail.open(GameRef(game.id, game.slug, game.name, game.boxArt))
    }

    override fun search() = actions.search()

    override fun account() {
        accountRequests.intValue++
    }

    override fun back() = actions.back()

    /** GamesFragment.onChange in v0.1: the tags sorted by id, the list reloaded only when they changed. */
    override fun applyFilter(filter: BrowseFilter) {
        val new = FilterLogic.normalizeGames(filter)
        val old = BrowseFilter(tags = viewModel.tags.map { FilterTag(it.id, it.name.orEmpty()) })
        if (FilterLogic.changed(old, new)) {
            lifecycleScope.launch {
                viewModel.setFilter(new.tags.map { Tag(id = it.id, name = it.name) }.toTypedArray())
            }
        }
    }

    override fun tagQuery(query: String) {
        tagsViewModel.setQuery(query)
    }

    override fun integrityCheckFailed() = actions.integrityCheckFailed()
}
