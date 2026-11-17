package com.github.andreyasadchy.xtra.ui.team

import com.github.andreyasadchy.xtra.ui.design.components.rememberLagoonToastState
import androidx.compose.runtime.Composable
import com.github.andreyasadchy.xtra.ui.main.nav.ComposePage
import com.github.andreyasadchy.xtra.ui.main.nav.PageHost
import com.github.andreyasadchy.xtra.ui.main.nav.TeamArgs
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.browse.PageBrowseActions
import com.github.andreyasadchy.xtra.ui.browse.hasValidatedNetwork
import com.github.andreyasadchy.xtra.ui.design.components.GlassContext
import com.github.andreyasadchy.xtra.ui.design.components.LocalGlass
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.ui.main.MainViewModel
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.collectLatest

/**
 * A team's page in Compose (U5, plan 7.8), in place of the v0.1 TeamFragment and TeamMembersAdapter.
 */
class TeamDestination(host: PageHost) : ComposePage(host) {

    private val args = TeamArgs.fromBundle(arguments)
    private val viewModel: TeamViewModel by viewModels { TeamViewModel.Companion.TeamViewModelFactory }
    private val mainViewModel: MainViewModel by activityViewModels { MainViewModel.Companion.MainViewModelFactory }
    private val scrollToTopRequests = mutableIntStateOf(0)
    private var refreshMembers: () -> Unit = {}

    private val actions by lazy {
        object : PageBrowseActions(this@TeamDestination), TeamActions {
            override fun shareTeam() = share("https://twitch.tv/team/${args.teamName}", args.teamName)
            override fun toggleBookmark(video: Video) = Unit
        }
    }

    override fun onCreate() {
        loadTeam()
    }

    private fun loadTeam() {
        val context = requireContext()
        viewModel.loadTeamInfo(
            teamName = args.teamName,
            networkLibrary = context.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            gqlHeaders = TwitchApiHelper.getGQLHeaders(context),
            enableIntegrity = context.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
    }

    @Composable
    override fun Content() {
        val context = requireContext()
        val prefs = context.prefs()
        val options = FollowingListOptions(
            nameDisplay = prefs.getString(C.UI_NAME_DISPLAY, "0") ?: "0",
            showUptime = prefs.getBoolean(C.UI_UPTIME, true),
            showTags = prefs.getBoolean(C.UI_TAGS, true),
            truncateCounts = prefs.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true),
        )
        val compact = prefs.getString(C.COMPACT_STREAMS, "disabled") == "all"
        val team by viewModel.team.collectAsState()
        val members = viewModel.flow.collectAsLazyPagingItems()
        val online by mainViewModel.isNetworkAvailable.collectAsState()
        val toast = rememberLagoonToastState()
        refreshMembers = { members.refresh() }
        LaunchedEffect(Unit) {
            viewModel.integrity.collect { activity.getNewIntegrityToken(it, childFragmentManager) }
        }
        // v0.1 (onNetworkRestored): reload the team and retry the members when the connection returns
        LaunchedEffect(Unit) {
            var wasOnline = mainViewModel.isNetworkAvailable.value ?: context.hasValidatedNetwork()
            mainViewModel.isNetworkAvailable.collectLatest { now ->
                if (now == true && !wasOnline) {
                    loadTeam()
                    members.retry()
                }
                if (now != null) wasOnline = now
            }
        }
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottom = shell?.let { with(LocalDensity.current) { it.bottomBarHeightPx.toDp() } }?.takeIf { it.value > 0f } ?: navBottom
        val loaded = team
        CompositionLocalProvider(LocalGlass provides (shell?.pageGlass() ?: GlassContext.Solid)) {
            TeamScreen(
                header = remember(loaded) {
                    TeamHeader(
                        name = args.teamName,
                        displayName = loaded?.displayName,
                        description = loaded?.description,
                        logo = loaded?.logoUrl,
                        banner = loaded?.bannerUrl,
                        members = loaded?.memberCount,
                        ownerLogin = loaded?.ownerLogin,
                        ownerName = loaded?.ownerName,
                    )
                },
                members = members,
                options = options,
                actions = actions,
                compactStreams = compact,
                bottomPadding = bottom,
                navScroll = shell?.hideOnScroll?.nestedScrollConnection,
                scrollToTopRequests = scrollToTopRequests.intValue,
                toast = toast,
                offline = online == false || (online == null && !context.hasValidatedNetwork()),
            )
        }
    }

    override fun scrollToTop() {
        scrollToTopRequests.intValue++
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        if (callback == PageBrowseActions.CALLBACK_REFRESH) {
            loadTeam()
            refreshMembers()
        }
    }
}
