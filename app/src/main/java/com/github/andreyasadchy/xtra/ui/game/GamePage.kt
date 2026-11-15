package com.github.andreyasadchy.xtra.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.Dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.browse.BrowseFilter
import com.github.andreyasadchy.xtra.ui.browse.FilterKind
import com.github.andreyasadchy.xtra.ui.browse.FilterLogic
import com.github.andreyasadchy.xtra.ui.browse.FilterSaves
import com.github.andreyasadchy.xtra.ui.browse.FilterSheetConfig
import com.github.andreyasadchy.xtra.ui.browse.FilterTag
import com.github.andreyasadchy.xtra.ui.browse.FragmentBrowseActions
import com.github.andreyasadchy.xtra.ui.browse.hasValidatedNetwork
import com.github.andreyasadchy.xtra.ui.common.SearchTagsViewModel
import com.github.andreyasadchy.xtra.ui.design.components.ToastHostState
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.ui.game.clips.GameClipsViewModel
import com.github.andreyasadchy.xtra.ui.game.streams.GameStreamsViewModel
import com.github.andreyasadchy.xtra.ui.game.videos.GameVideosViewModel
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.main.MainViewModel
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/** Hands a game page's arguments to its view models when they do not live in the game fragment (Browse, U4). */
val GameArgsKey = object : CreationExtras.Key<GamePagerFragmentArgs> {}

/**
 * One game page: its view models, what v0.1's GamePagerFragment and its three list fragments did on start, on
 * filter changes and on follow, and the Compose content. The view models live in [owner]: the game fragment for the
 * game destination, a store of its own for a game opened inside Browse (the shared box-art transition, U4).
 */
class GamePage(
    private val fragment: Fragment,
    owner: ViewModelStoreOwner,
    val args: GamePagerFragmentArgs,
    private val mainViewModel: MainViewModel,
    private val onBack: () -> Unit,
) : FragmentBrowseActions(fragment), GameActions {

    private val context get() = fragment.requireContext()
    private val store = owner.viewModelStore
    private val extras = MutableCreationExtras().apply {
        set(APPLICATION_KEY, fragment.requireActivity().application)
        set(GameArgsKey, args)
    }

    private fun <T : ViewModel> vm(key: String, factory: ViewModelProvider.Factory, type: KClass<T>): T =
        ViewModelProvider.create(store, factory, extras)["game:${args.gameId}:${args.gameName}:$key", type]

    val pager by lazy { vm("pager", GamePagerViewModel.GamePagerViewModelFactory, GamePagerViewModel::class) }
    val streams by lazy { vm("streams", GameStreamsViewModel.GameStreamsViewModelFactory, GameStreamsViewModel::class) }
    val videos by lazy { vm("videos", GameVideosViewModel.GameVideosViewModelFactory, GameVideosViewModel::class) }
    val clips by lazy { vm("clips", GameClipsViewModel.GameClipsViewModelFactory, GameClipsViewModel::class) }
    private val tags by lazy {
        vm("streamTags", SearchTagsViewModel.SearchTagsViewModelFactory, SearchTagsViewModel::class).apply { getGameTags = false }
    }

    /** This game has remembered sort and filter choices (v0.1 showed "delete" in the sort dialogs then). */
    private val remembered = MutableStateFlow(false)
    private val followSetting get() = context.prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
    private val hasHelixToken get() = !TwitchApiHelper.getHelixHeaders(context)[C.HEADER_TOKEN].isNullOrBlank()
    private val networkLibrary get() = context.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP)
    private val enableIntegrity get() = context.prefs().getBoolean(C.ENABLE_INTEGRITY, false)
    private var refreshLists: () -> Unit = {}

    /** v0.1's initialize(): the lists' first filters from the remembered choices, the game, follow state, local update. */
    fun start() {
        fragment.lifecycleScope.launch {
            val id = args.gameId
            if (streams.filter.value == null) {
                val saved = id?.let { streams.getGameSort(it) } ?: streams.getGameSort(FilterLogic.DEFAULT_ID)
                val filter = FilterLogic.initialGameStreams(saved, args.tags?.toList(), args.languages?.toList())
                streams.setFilter(filter.sort, filter.tagNames.toTypedArray(), filter.languages.toTypedArray())
            }
            if (videos.filter.value == null) {
                val saved = id?.let { videos.getGameSort(it) } ?: videos.getGameSort(FilterLogic.DEFAULT_ID)
                val filter = FilterLogic.initialGameVideos(saved, hasHelixToken)
                videos.setFilter(filter.sort, filter.period, filter.type, filter.languages.toTypedArray())
            }
            if (clips.filter.value == null) {
                val saved = id?.let { clips.getGameSort(it) } ?: clips.getGameSort(FilterLogic.DEFAULT_ID)
                val filter = FilterLogic.initialGameClips(saved)
                clips.setFilter(filter.period, filter.languages.toTypedArray())
            }
            refreshRemembered()
        }
        loadGame()
        if (followSetting < 2) {
            pager.isFollowingGame(args.gameId, followSetting, networkLibrary, TwitchApiHelper.getGQLHeaders(context, true))
        }
        if (args.updateLocal) {
            pager.updateLocalGame(context.filesDir.path, args.gameId, args.gameName, networkLibrary, TwitchApiHelper.getGQLHeaders(context), TwitchApiHelper.getHelixHeaders(context))
        }
    }

    private fun loadGame() {
        pager.loadGame(networkLibrary, TwitchApiHelper.getGQLHeaders(context), TwitchApiHelper.getHelixHeaders(context), enableIntegrity)
    }

    private suspend fun refreshRemembered() {
        remembered.value = args.gameId?.let { streams.getGameSort(it) } != null
    }

    /** IntegrityDialog finished: what each v0.1 fragment did with the callback it had asked for. */
    fun onIntegrityTokenLoaded(callback: String?) {
        when (callback) {
            CALLBACK_REFRESH -> {
                loadGame()
                if (followSetting < 2) {
                    pager.isFollowingGame(args.gameId, followSetting, networkLibrary, TwitchApiHelper.getGQLHeaders(context, true))
                }
                refreshLists()
            }
            CALLBACK_FOLLOW -> setFollowing(true)
            CALLBACK_UNFOLLOW -> setFollowing(false)
        }
    }

    override fun back() = onBack()

    override fun setFollowing(follow: Boolean) {
        if (follow) {
            pager.saveFollowGame(
                args.gameId, args.gameSlug, args.gameName, followSetting, context.filesDir.path, networkLibrary,
                TwitchApiHelper.getGQLHeaders(context, true), TwitchApiHelper.getHelixHeaders(context), enableIntegrity,
            )
        } else {
            pager.deleteFollowGame(args.gameId, followSetting, networkLibrary, TwitchApiHelper.getGQLHeaders(context, true), enableIntegrity)
        }
    }

    override fun toggleBookmark(video: Video) {
        videos.saveBookmark(context.filesDir.path, video, networkLibrary, TwitchApiHelper.getGQLHeaders(context), TwitchApiHelper.getHelixHeaders(context))
    }

    override fun tagQuery(query: String) {
        tags.setQuery(query)
    }

    override fun openGameTag(tag: Tag) {
        super.openGameTag(tag)
    }

    fun streamsFilter() = BrowseFilter(
        sort = streams.sort,
        languages = streams.languages.toList(),
        tags = streams.tags.map { FilterTag(null, it) },
    )

    fun videosFilter() = BrowseFilter(sort = videos.sort, period = videos.period, type = videos.type, languages = videos.languages.toList())

    fun clipsFilter() = BrowseFilter(period = clips.period, languages = clips.languages.toList())

    /** The v0.1 lists' onChange: reload when something changed, then save what was asked for, in the same order. */
    override fun applyFilter(kind: FilterKind, filter: BrowseFilter, saves: FilterSaves) {
        val id = args.gameId
        val helix = hasHelixToken
        fragment.lifecycleScope.launch {
            if (saves.forget && id != null) {
                streams.getGameSort(id)?.let { streams.deleteGameSort(it) }
            }
            when (kind) {
                FilterKind.Streams -> {
                    val new = FilterLogic.normalizeStreams(filter)
                    if (FilterLogic.changed(FilterLogic.normalizeStreams(streamsFilter()), new)) {
                        streams.setFilter(FilterLogic.effectiveStreamSort(new), new.tagNames.toTypedArray(), new.languages.toTypedArray())
                    }
                    if (saves.saveFilters) {
                        FilterLogic.savedFilter(id, args.gameSlug, args.gameName, new)?.let { streams.saveFilters(it) }
                    }
                    if (saves.remember && id != null) streams.saveGameSort(FilterLogic.saveStreams(streams.getGameSort(id), id, new))
                    if (saves.saveDefault) streams.saveGameSort(FilterLogic.saveStreams(streams.getGameSort(FilterLogic.DEFAULT_ID), FilterLogic.DEFAULT_ID, new))
                }
                FilterKind.Videos -> {
                    val new = FilterLogic.normalizeVideos(filter)
                    val sort = FilterLogic.effectiveVideoSort(new)
                    val period = FilterLogic.effectivePeriod(new)
                    val type = FilterLogic.effectiveType(new)
                    if (FilterLogic.changed(videosFilter(), new.copy(sort = sort, period = period, type = type))) {
                        videos.setFilter(sort, period, type, new.languages.toTypedArray())
                    }
                    if (saves.remember && id != null) videos.saveGameSort(FilterLogic.saveVideos(videos.getGameSort(id), id, new, helix))
                    if (saves.saveDefault) videos.saveGameSort(FilterLogic.saveVideos(videos.getGameSort(FilterLogic.DEFAULT_ID), FilterLogic.DEFAULT_ID, new, helix))
                }
                FilterKind.Clips -> {
                    val new = FilterLogic.normalizeVideos(filter)
                    val period = FilterLogic.effectivePeriod(new)
                    if (FilterLogic.changed(clipsFilter(), new.copy(sort = null, type = null, period = period))) {
                        clips.setFilter(period, new.languages.toTypedArray())
                    }
                    if (saves.remember && id != null) clips.saveGameSort(FilterLogic.saveClips(clips.getGameSort(id), id, new))
                    if (saves.saveDefault) clips.saveGameSort(FilterLogic.saveClips(clips.getGameSort(FilterLogic.DEFAULT_ID), FilterLogic.DEFAULT_ID, new))
                }
                FilterKind.Games -> Unit
            }
            refreshRemembered()
        }
    }

    /**
     * The page. [boxArtModifier] marks the box art for the shared transition; [bottomPadding] leaves room for the
     * floating navigation.
     */
    @Composable
    fun Content(
        bottomPadding: Dp,
        navScroll: NestedScrollConnection?,
        scrollToTopRequests: Int,
        toast: ToastHostState,
        modifier: Modifier = Modifier,
        boxArtModifier: Modifier = Modifier,
    ) {
        val prefs = remember { context.prefs() }
        val segmentsPref = remember { prefs.getString(C.UI_GAME_TABS, null) }
        val segments = remember { GameSegment.list(segmentsPref) }
        var selectedKey by rememberSaveable { mutableStateOf(GameSegment.default(segmentsPref, segments).key) }
        val selected = GameSegment.of(selectedKey)?.takeIf { it in segments } ?: segments.first()
        val options = remember {
            FollowingListOptions(
                nameDisplay = prefs.getString(C.UI_NAME_DISPLAY, "0") ?: "0",
                showUptime = prefs.getBoolean(C.UI_UPTIME, true),
                showTags = prefs.getBoolean(C.UI_TAGS, true),
                truncateCounts = prefs.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true),
                showBroadcasters = prefs.getBoolean(C.UI_BROADCASTERS_COUNT, true),
                usePositions = prefs.getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true),
            )
        }
        val compact = remember { prefs.getString(C.COMPACT_STREAMS, "disabled") == "all" }
        val streamsFilterValue by streams.filter.collectAsState()
        val videosFilterValue by videos.filter.collectAsState()
        val clipsFilterValue by clips.filter.collectAsState()
        val streamItems = (if (GameSegment.Live in segments && streamsFilterValue != null) streams.flow else emptyFlow()).collectAsLazyPagingItems()
        val videoItems = (if (GameSegment.Videos in segments && videosFilterValue != null) videos.flow else emptyFlow()).collectAsLazyPagingItems()
        val clipItems = (if (GameSegment.Clips in segments && clipsFilterValue != null) clips.flow else emptyFlow()).collectAsLazyPagingItems()
        val tagItems = tags.flow.collectAsLazyPagingItems()
        val game by pager.game.collectAsState()
        val following by pager.isFollowing.collectAsState()
        val isRemembered by remembered.collectAsState()
        val positions by remember { videos.positions }.collectAsState(emptyList())
        val bookmarks by remember { videos.bookmarks }.collectAsState(emptyList())
        val online by mainViewModel.isNetworkAvailable.collectAsState()
        refreshLists = { streamItems.refresh(); videoItems.refresh(); clipItems.refresh() }

        LaunchedEffect(Unit) {
            pager.integrity.collect { (fragment.activity as? MainActivity)?.getNewIntegrityToken(it, fragment.childFragmentManager) }
        }
        LaunchedEffect(Unit) {
            pager.follow.collect { pair ->
                if (pair != null) {
                    val (followed, error) = pair
                    toast.show(
                        when {
                            !error.isNullOrBlank() -> error
                            followed -> context.getString(R.string.now_following, args.gameName)
                            else -> context.getString(R.string.unfollowed, args.gameName)
                        }
                    )
                    pager.follow.value = null
                }
            }
        }
        // v0.1 (BaseNetworkFragment.onNetworkRestored): reload the game and retry the lists when the connection returns
        LaunchedEffect(Unit) {
            var wasOnline = mainViewModel.isNetworkAvailable.value ?: context.hasValidatedNetwork()
            mainViewModel.isNetworkAvailable.collectLatest { now ->
                if (now == true && !wasOnline) {
                    loadGame()
                    streamItems.retry(); videoItems.retry(); clipItems.retry()
                }
                if (now != null) wasOnline = now
            }
        }

        val loaded = game
        val header = GameHeader(
            id = args.gameId,
            slug = args.gameSlug,
            name = loaded?.name ?: args.gameName,
            boxArt = args.boxArt ?: loaded?.boxArt,
            viewers = loaded?.viewerCount,
            broadcasters = loaded?.broadcasterCount,
            followers = loaded?.followerCount,
            tags = loaded?.tags.orEmpty(),
        )
        val canRemember = !args.gameId.isNullOrBlank()
        GameScreen(
            header = header,
            segments = segments,
            selected = selected,
            onSelect = { selectedKey = it.key },
            lists = GameLists(
                streams = streamItems.takeIf { streamsFilterValue != null },
                videos = videoItems.takeIf { videosFilterValue != null },
                clips = clipItems.takeIf { clipsFilterValue != null },
            ),
            filters = GameFilters(
                streams = streamsFilterValue?.let { streamsFilter() } ?: BrowseFilter(),
                videos = videosFilterValue?.let { videosFilter() } ?: BrowseFilter(),
                clips = clipsFilterValue?.let { clipsFilter() } ?: BrowseFilter(),
                streamsConfig = FilterSheetConfig(FilterKind.Streams, canRemember = canRemember, remembered = isRemembered, canSaveFilters = true),
                videosConfig = FilterSheetConfig(FilterKind.Videos, showPeriod = hasHelixToken, canRemember = canRemember, remembered = isRemembered),
                clipsConfig = FilterSheetConfig(FilterKind.Clips, canRemember = canRemember, remembered = isRemembered),
            ),
            options = options,
            compactStreams = compact,
            actions = this,
            modifier = modifier,
            showFollow = followSetting < 2,
            following = following,
            tagResults = tagItems,
            videoPositions = positions.associate { it.id to it.position },
            bookmarkedVideoIds = bookmarks.mapNotNull { it.videoId }.toSet(),
            boxArtModifier = boxArtModifier,
            bottomPadding = bottomPadding,
            navScroll = navScroll,
            scrollToTopRequests = scrollToTopRequests,
            toast = toast,
            offline = online == false || (online == null && !context.hasValidatedNetwork()),
        )
    }

    companion object {
        const val CALLBACK_FOLLOW = "follow"
        const val CALLBACK_UNFOLLOW = "unfollow"
    }
}
