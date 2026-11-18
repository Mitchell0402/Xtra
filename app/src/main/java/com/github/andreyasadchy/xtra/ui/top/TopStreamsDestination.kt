package com.github.andreyasadchy.xtra.ui.top

import com.github.andreyasadchy.xtra.ui.main.shell.LocalSettingsVersion
import androidx.compose.runtime.Composable
import com.github.andreyasadchy.xtra.ui.main.nav.ComposePage
import com.github.andreyasadchy.xtra.ui.main.nav.PageHost
import com.github.andreyasadchy.xtra.ui.main.nav.TopStreamsArgs
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.browse.BrowseFilter
import com.github.andreyasadchy.xtra.ui.browse.FilterLogic
import com.github.andreyasadchy.xtra.ui.browse.FilterSaves
import com.github.andreyasadchy.xtra.ui.browse.FilterTag
import com.github.andreyasadchy.xtra.ui.browse.PageBrowseActions
import com.github.andreyasadchy.xtra.ui.browse.hasValidatedNetwork
import com.github.andreyasadchy.xtra.ui.common.SearchTagsViewModel
import com.github.andreyasadchy.xtra.ui.design.components.GlassContext
import com.github.andreyasadchy.xtra.ui.design.components.LocalGlass
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.ui.main.MainViewModel
import com.github.andreyasadchy.xtra.ui.top.TopStreamsViewModel.Companion.TopStreamsViewModelFactory
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * Live streams by viewers or by tag in Compose (U4), in place of v0.1's TopStreamsFragment. The "Popular" tab is
 * gone (U2); this page stays for the stream tags that open it.
 */
class TopStreamsDestination(host: PageHost) : ComposePage(host) {

    private val args = TopStreamsArgs.fromBundle(arguments)
    private val viewModel: TopStreamsViewModel by viewModels { TopStreamsViewModelFactory }
    private val tagsViewModel: SearchTagsViewModel by viewModels { SearchTagsViewModel.SearchTagsViewModelFactory }
    private val mainViewModel: MainViewModel by activityViewModels { MainViewModel.Companion.MainViewModelFactory }
    private val scrollToTopRequests = mutableIntStateOf(0)
    private val refreshRequests = mutableIntStateOf(0)
    private val actions by lazy { object : PageBrowseActions(this@TopStreamsDestination) {
        override fun toggleBookmark(video: Video) {}
    } }

    override fun onCreate() {
        tagsViewModel.getGameTags = false
        // v0.1 initialize(): the saved choices of this page; tags and languages from the link win
        lifecycleScope.launch {
            if (viewModel.filter.value == null) {
                val filter = FilterLogic.initialTopStreams(viewModel.getGameSort(FilterLogic.TOP_STREAMS_ID), args.tags?.toList(), args.languages?.toList())
                viewModel.setFilter(filter.sort, filter.tagNames.toTypedArray(), filter.languages.toTypedArray())
            }
        }
    }

    private fun currentFilter() = BrowseFilter(
        sort = viewModel.sort,
        languages = viewModel.languages.toList(),
        tags = viewModel.tags.map { FilterTag(null, it) },
    )

    /** TopStreamsFragment.onChange in v0.1: reload on a change, keep the filters, save this page's default. */
    private fun apply(filter: BrowseFilter, saves: FilterSaves) {
        lifecycleScope.launch {
            val new = FilterLogic.normalizeStreams(filter)
            if (FilterLogic.changed(FilterLogic.normalizeStreams(currentFilter()), new)) {
                viewModel.setFilter(FilterLogic.effectiveStreamSort(new), new.tagNames.toTypedArray(), new.languages.toTypedArray())
            }
            if (saves.saveFilters) FilterLogic.savedFilter(null, null, null, new)?.let { viewModel.saveFilters(it) }
            if (saves.saveDefault) {
                viewModel.saveGameSort(FilterLogic.saveStreams(viewModel.getGameSort(FilterLogic.TOP_STREAMS_ID), FilterLogic.TOP_STREAMS_ID, new))
            }
        }
    }

    @Composable
    override fun Content() {
        val context = requireContext()
        val prefs = context.prefs()
        val root = args.tags.isNullOrEmpty() && args.languages.isNullOrEmpty()
        // settings that change without a rebuild are read again when they do (motion plan M5 item 3)
        LocalSettingsVersion.current
        val options = FollowingListOptions(
            nameDisplay = prefs.getString(C.UI_NAME_DISPLAY, "0") ?: "0",
            showUptime = prefs.getBoolean(C.UI_UPTIME, true),
            showTags = prefs.getBoolean(C.UI_TAGS, true),
            truncateCounts = prefs.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true),
        )
        val compact = prefs.getString(C.COMPACT_STREAMS, "disabled") == "all"
        val filterValue by viewModel.filter.collectAsState()
        val streams = (if (filterValue != null) viewModel.flow else emptyFlow()).collectAsLazyPagingItems()
        val tagResults = tagsViewModel.flow.collectAsLazyPagingItems()
        val online by mainViewModel.isNetworkAvailable.collectAsState()
        LaunchedEffect(refreshRequests.intValue) { if (refreshRequests.intValue > 0) streams.refresh() }
        LaunchedEffect(Unit) {
            var wasOnline = mainViewModel.isNetworkAvailable.value ?: context.hasValidatedNetwork()
            mainViewModel.isNetworkAvailable.collectLatest { now ->
                if (now == true && !wasOnline) streams.retry()
                if (now != null) wasOnline = now
            }
        }
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottom = shell?.let { with(LocalDensity.current) { it.bottomBarHeightPx.toDp() } }?.takeIf { it.value > 0f } ?: navBottom
        CompositionLocalProvider(LocalGlass provides (shell?.pageGlass() ?: GlassContext.Solid)) {
            TopStreamsScreen(
                root = root,
                streams = streams.takeIf { filterValue != null },
                filter = filterValue?.let { currentFilter() } ?: BrowseFilter(),
                options = options,
                compactStreams = compact,
                actions = actions,
                onApplyFilter = ::apply,
                tagResults = tagResults,
                onTagQuery = tagsViewModel::setQuery,
                bottomPadding = bottom,
                navScroll = shell?.hideOnScroll?.nestedScrollConnection,
                scrollToTopRequests = scrollToTopRequests.intValue,
                offline = online == false || (online == null && !context.hasValidatedNetwork()),
            )
        }
    }

    override fun scrollToTop() {
        scrollToTopRequests.intValue++
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        if (callback == PageBrowseActions.CALLBACK_REFRESH) refreshRequests.intValue++
    }
}
