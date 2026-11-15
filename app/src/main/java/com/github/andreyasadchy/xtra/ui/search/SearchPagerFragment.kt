package com.github.andreyasadchy.xtra.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.andreyasadchy.xtra.model.ui.RecentSearch
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.browse.FragmentBrowseActions
import com.github.andreyasadchy.xtra.ui.browse.hasValidatedNetwork
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.design.components.GlassContext
import com.github.andreyasadchy.xtra.ui.design.components.LocalGlass
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.main.MainViewModel
import com.github.andreyasadchy.xtra.ui.main.shell.ShellHost
import com.github.andreyasadchy.xtra.ui.search.SearchPagerViewModel.Companion.SearchPagerViewModelFactory
import com.github.andreyasadchy.xtra.ui.search.channels.ChannelSearchViewModel
import com.github.andreyasadchy.xtra.ui.search.games.GameSearchViewModel
import com.github.andreyasadchy.xtra.ui.search.streams.StreamSearchViewModel
import com.github.andreyasadchy.xtra.ui.search.videos.VideoSearchViewModel
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * The full-screen search page in Compose (U4), in place of v0.1's SearchPagerFragment and its four tab fragments.
 * The four search view models are unchanged; one query now goes to all of them, and the recent searches of all four
 * types show together.
 */
class SearchPagerFragment : Fragment(), IntegrityDialog.Listener, SearchActions {

    private val viewModel: SearchPagerViewModel by viewModels { SearchPagerViewModelFactory }
    private val channels: ChannelSearchViewModel by viewModels { ChannelSearchViewModel.ChannelSearchViewModelFactory }
    private val games: GameSearchViewModel by viewModels { GameSearchViewModel.GameSearchViewModelFactory }
    private val streams: StreamSearchViewModel by viewModels { StreamSearchViewModel.StreamSearchViewModelFactory }
    private val videos: VideoSearchViewModel by viewModels { VideoSearchViewModel.VideoSearchViewModelFactory }
    private val mainViewModel: MainViewModel by activityViewModels { MainViewModel.Companion.MainViewModelFactory }
    private val refreshRequests = mutableIntStateOf(0)
    private var groups: List<SearchGroup> = emptyList()
    private var recentItems: List<RecentSearch> = emptyList()
    private var lookup by mutableStateOf<UserLookup?>(null)
    private val storeRecent get() = requireContext().prefs().getBoolean(C.UI_STORE_RECENT_SEARCHES, true)

    private val delegate by lazy { object : FragmentBrowseActions(this@SearchPagerFragment) {
        override fun toggleBookmark(video: Video) {
            val context = requireContext()
            videos.saveBookmark(
                context.filesDir.path, video, context.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                TwitchApiHelper.getGQLHeaders(context), TwitchApiHelper.getHelixHeaders(context),
            )
        }
    } }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val prefs = context.prefs()
        val shell = (activity as? ShellHost)?.shell
        groups = SearchGroup.list(prefs.getString(C.UI_SEARCH_TABS, null))
        val options = FollowingListOptions(
            nameDisplay = prefs.getString(C.UI_NAME_DISPLAY, "0") ?: "0",
            showUptime = prefs.getBoolean(C.UI_UPTIME, true),
            showTags = prefs.getBoolean(C.UI_TAGS, true),
            truncateCounts = prefs.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true),
            showBroadcasters = prefs.getBoolean(C.UI_BROADCASTERS_COUNT, true),
            usePositions = prefs.getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true),
        )
        val compact = prefs.getString(C.COMPACT_STREAMS, "disabled") == "all"
        val showRecent = prefs.getBoolean(C.UI_STORE_RECENT_SEARCHES, true)
        // the query the view models still hold after a trip back from a result
        val initialQuery = channels.query.value
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LagoonTheme {
                    val channelItems = channels.flow.collectAsLazyPagingItems()
                    val streamItems = streams.flow.collectAsLazyPagingItems()
                    val gameItems = games.flow.collectAsLazyPagingItems()
                    val videoItems = videos.flow.collectAsLazyPagingItems()
                    val recentFlow = remember {
                        if (showRecent) combine(channels.recentSearches, streams.recentSearches, games.recentSearches, videos.recentSearches) { a, b, c, d -> a + b + c + d }
                        else flowOf(emptyList())
                    }
                    val recentAll by recentFlow.collectAsState(emptyList())
                    recentItems = recentAll
                    val recent = recentAll.sortedByDescending { it.lastSearched }.map { it.query }.distinct().take(20)
                    val positions by remember { videos.positions }.collectAsState(emptyList())
                    val bookmarks by remember { videos.bookmarks }.collectAsState(emptyList())
                    val online by mainViewModel.isNetworkAvailable.collectAsState()
                    LaunchedEffect(refreshRequests.intValue) {
                        if (refreshRequests.intValue > 0) { channelItems.refresh(); streamItems.refresh(); gameItems.refresh(); videoItems.refresh() }
                    }
                    LaunchedEffect(Unit) {
                        viewModel.integrity.collect { (activity as? MainActivity)?.getNewIntegrityToken(it, childFragmentManager) }
                    }
                    LaunchedEffect(Unit) {
                        viewModel.userResult.collect { result ->
                            val asked = pendingLookup
                            if (result != null && asked != null) {
                                lookup = UserLookup(asked.first, asked.second, result.first?.takeIf { it.isNotBlank() }, result.second)
                                viewModel.userResult.value = null
                            }
                        }
                    }
                    LaunchedEffect(Unit) {
                        var wasOnline = mainViewModel.isNetworkAvailable.value ?: context.hasValidatedNetwork()
                        mainViewModel.isNetworkAvailable.collectLatest { now ->
                            if (now == true && !wasOnline) { channelItems.retry(); streamItems.retry(); gameItems.retry(); videoItems.retry() }
                            if (now != null) wasOnline = now
                        }
                    }
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val bottom = shell?.let { with(LocalDensity.current) { it.bottomBarHeightPx.toDp() } }?.takeIf { it.value > 0f } ?: navBottom
                    CompositionLocalProvider(LocalGlass provides (shell?.pageGlass() ?: GlassContext.Solid)) {
                        SearchScreen(
                            groups = groups,
                            results = SearchResults(
                                channels = channelItems.takeIf { SearchGroup.Channels in groups },
                                streams = streamItems.takeIf { SearchGroup.Streams in groups },
                                games = gameItems.takeIf { SearchGroup.Games in groups },
                                videos = videoItems.takeIf { SearchGroup.Videos in groups },
                            ),
                            recent = recent,
                            options = options,
                            compactStreams = compact,
                            actions = this@SearchPagerFragment,
                            initialQuery = initialQuery,
                            showRecent = showRecent,
                            videoPositions = positions.associate { it.id to it.position },
                            bookmarkedVideoIds = bookmarks.mapNotNull { it.videoId }.toSet(),
                            lookup = lookup,
                            onLookupShown = { lookup = null },
                            bottomPadding = bottom,
                            navScroll = shell?.hideOnScroll?.nestedScrollConnection,
                            offline = online == false || (online == null && !context.hasValidatedNetwork()),
                        )
                    }
                }
            }
        }
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        if (callback == FragmentBrowseActions.CALLBACK_REFRESH) refreshRequests.intValue++
    }

    // SearchActions

    override fun query(text: String) {
        if (SearchGroup.Channels in groups) channels.setQuery(text)
        if (SearchGroup.Streams in groups) streams.setQuery(text)
        if (SearchGroup.Games in groups) games.setQuery(text)
        if (SearchGroup.Videos in groups) videos.setQuery(text)
    }

    /** Saved under every result type that is shown, so the query also comes back in the old per-type lists. */
    override fun remember(text: String) {
        if (!storeRecent || text.isBlank()) return
        if (SearchGroup.Channels in groups) channels.saveRecentSearch(text)
        if (SearchGroup.Streams in groups) streams.saveRecentSearch(text)
        if (SearchGroup.Games in groups) games.saveRecentSearch(text)
        if (SearchGroup.Videos in groups) videos.saveRecentSearch(text)
    }

    override fun forget(text: String) {
        recentItems.filter { it.query == text }.forEach(::delete)
    }

    override fun forgetAll() {
        recentItems.forEach(::delete)
    }

    private fun delete(item: RecentSearch) {
        when (item.type) {
            RecentSearch.TYPE_CHANNEL -> channels.deleteRecentSearch(item)
            RecentSearch.TYPE_STREAM -> streams.deleteRecentSearch(item)
            RecentSearch.TYPE_GAME -> games.deleteRecentSearch(item)
            RecentSearch.TYPE_VIDEO -> videos.deleteRecentSearch(item)
        }
    }

    private var pendingLookup: Pair<Boolean, String>? = null

    override fun lookUpUser(byId: Boolean, value: String) {
        val context = requireContext()
        pendingLookup = byId to value
        viewModel.loadUserResult(
            checkedId = if (byId) 0 else 1,
            result = value,
            networkLibrary = context.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            gqlHeaders = TwitchApiHelper.getGQLHeaders(context),
            enableIntegrity = context.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
    }

    override fun openUser(byId: Boolean, value: String) {
        lookup = null
        findNavController().navigate(
            if (byId) ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(channelId = value)
            else ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(channelLogin = value)
        )
    }

    // BrowseActions, as v0.1's adapters did

    override fun back() = delegate.back()
    override fun search() {}
    override fun openStream(stream: com.github.andreyasadchy.xtra.model.ui.Stream) = delegate.openStream(stream)
    override fun openChannel(id: String?, login: String?, name: String?, image: String?, streamId: String?) = delegate.openChannel(id, login, name, image, streamId)
    override fun openGame(id: String?, slug: String?, name: String?, boxArt: String?, updateLocal: Boolean) = delegate.openGame(id, slug, name, boxArt, updateLocal)
    override fun openStreamTag(tag: String) = delegate.openStreamTag(tag)
    override fun record(stream: com.github.andreyasadchy.xtra.model.ui.Stream) = delegate.record(stream)
    override fun share(url: String, title: String?) = delegate.share(url, title)
    override fun openVideo(video: Video, position: Long?) = delegate.openVideo(video, position)
    override fun downloadVideo(video: Video) = delegate.downloadVideo(video)
    override fun toggleBookmark(video: Video) = delegate.toggleBookmark(video)
    override fun openClip(clip: com.github.andreyasadchy.xtra.model.ui.Clip) = delegate.openClip(clip)
    override fun downloadClip(clip: com.github.andreyasadchy.xtra.model.ui.Clip) = delegate.downloadClip(clip)
    override fun integrityCheckFailed() = delegate.integrityCheckFailed()
}
