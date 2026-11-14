package com.github.andreyasadchy.xtra.ui.following

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.ChannelSort
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.design.components.rememberToastHostState
import com.github.andreyasadchy.xtra.ui.design.components.GlassContext
import com.github.andreyasadchy.xtra.ui.design.components.LocalGlass
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.following.channels.FollowedChannelsViewModel
import com.github.andreyasadchy.xtra.ui.following.games.FollowedGamesViewModel
import com.github.andreyasadchy.xtra.ui.following.streams.FollowedStreamsViewModel
import com.github.andreyasadchy.xtra.ui.following.videos.FollowedVideosViewModel
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamesFragmentDirections
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.main.MainViewModel
import com.github.andreyasadchy.xtra.ui.main.shell.FollowingSegment
import com.github.andreyasadchy.xtra.ui.main.shell.ShellHost
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.ui.top.TopStreamsFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * The Following page in Compose (U2), in place of FollowPagerFragment / FollowMediaFragment and their four list
 * fragments. It stays a Fragment destination of the old navigation graph until U8 (plan 8), so every
 * findNavController call elsewhere keeps working and the page restores with the back stack.
 */
class FollowingFragment : Fragment(), Scrollable, IntegrityDialog.Listener, FollowingActions {

    private val viewModel: FollowingViewModel by viewModels { FollowingViewModel.Factory }
    private val streamsViewModel: FollowedStreamsViewModel by viewModels { FollowedStreamsViewModel.FollowedStreamsViewModelFactory }
    private val videosViewModel: FollowedVideosViewModel by viewModels { FollowedVideosViewModel.FollowedVideosViewModelFactory }
    private val channelsViewModel: FollowedChannelsViewModel by viewModels { FollowedChannelsViewModel.FollowedChannelsViewModelFactory }
    private val gamesViewModel: FollowedGamesViewModel by viewModels { FollowedGamesViewModel.FollowedGamesViewModelFactory }
    private val mainViewModel: MainViewModel by activityViewModels { MainViewModel.Companion.MainViewModelFactory }

    private val scrollToTopRequests = mutableIntStateOf(0)
    private val refreshRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the saved sorts, read the same way the v0.1 list fragments did before their first load
        lifecycleScope.launch {
            if (videosViewModel.filter.value == null) {
                val saved = videosViewModel.getChannelSort(SORT_ID_VIDEOS)
                videosViewModel.setFilter(sort = saved?.videoSort, type = saved?.videoType)
            }
            if (channelsViewModel.filter.value == null) {
                val saved = channelsViewModel.getChannelSort(SORT_ID_CHANNELS)
                channelsViewModel.setFilter(sort = saved?.videoSort, order = saved?.videoType)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val prefs = context.prefs()
        val gqlToken = TwitchApiHelper.getGQLHeaders(context, true)[C.HEADER_TOKEN]
        val loggedIn = !gqlToken.isNullOrBlank() || !TwitchApiHelper.getHelixHeaders(context)[C.HEADER_TOKEN].isNullOrBlank()
        val segmentsPref = prefs.getString(C.UI_FOLLOWING_TABS, null)
        val segments = FollowingSegment.list(segmentsPref, showVideos = !gqlToken.isNullOrBlank())
        val defaultSegment = FollowingSegment.default(segmentsPref, segments)
        val account = FollowingAccount(loggedIn, context.tokenPrefs().getString(C.USERNAME, null))
        val options = FollowingListOptions(
            nameDisplay = prefs.getString(C.UI_NAME_DISPLAY, "0") ?: "0",
            showUptime = prefs.getBoolean(C.UI_UPTIME, true),
            showTags = prefs.getBoolean(C.UI_TAGS, true),
            truncateCounts = prefs.getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true),
            showBroadcasters = prefs.getBoolean(C.UI_BROADCASTERS_COUNT, true),
            compactStreams = prefs.getString(C.COMPACT_STREAMS, "disabled") != "disabled",
            usePositions = prefs.getBoolean(C.PLAYER_USE_VIDEO_POSITIONS, true),
        )
        val shell = (activity as? ShellHost)?.shell
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LagoonTheme {
                    val segmentKey by viewModel.segment.collectAsState()
                    val selected = segmentKey?.let { FollowingSegment.of(it) }?.takeIf { it in segments } ?: defaultSegment
                    val streams = streamsViewModel.flow.collectAsLazyPagingItems()
                    val videosFilter by videosViewModel.filter.collectAsState()
                    val videos = (if (FollowingSegment.Videos in segments && videosFilter != null) videosViewModel.flow else emptyFlow()).collectAsLazyPagingItems()
                    val channelsFilter by channelsViewModel.filter.collectAsState()
                    val channels = (if (channelsFilter != null) channelsViewModel.flow else emptyFlow()).collectAsLazyPagingItems()
                    val games = gamesViewModel.flow.collectAsLazyPagingItems()
                    val recentlyLive = viewModel.recentlyLive.collectAsLazyPagingItems()
                    val positions by remember { videosViewModel.positions }.collectAsState(emptyList())
                    val bookmarks by remember { videosViewModel.bookmarks }.collectAsState(emptyList())
                    val toast = rememberToastHostState()
                    val online by mainViewModel.isNetworkAvailable.collectAsState()
                    LaunchedEffect(refreshRequests.intValue) {
                        if (refreshRequests.intValue > 0) {
                            streams.refresh(); videos.refresh(); channels.refresh(); games.refresh(); recentlyLive.refresh()
                        }
                    }
                    LaunchedEffect(Unit) {
                        viewModel.events.collect { event ->
                            when (event) {
                                FollowingViewModel.Event.Unfollowed -> {
                                    refreshRequests.intValue++
                                    lastUnfollowedName?.let { toast.show(getString(R.string.following_unfollowed, it)) }
                                }
                                is FollowingViewModel.Event.UnfollowFailed -> toast.show(getString(R.string.following_unfollow_failed, event.message ?: ""))
                                FollowingViewModel.Event.NeedsIntegrity -> (activity as? MainActivity)?.getNewIntegrityToken(CALLBACK_UNFOLLOW, childFragmentManager)
                            }
                        }
                    }
                    // v0.1 retried the lists when the connection came back (BaseNetworkFragment.onNetworkRestored)
                    LaunchedEffect(Unit) {
                        // the followed lists turn failed requests into empty pages, so reload them rather than retry
                        var wasOnline = mainViewModel.isNetworkAvailable.value ?: hasValidatedNetwork()
                        mainViewModel.isNetworkAvailable.collectLatest { online ->
                            if (online == true && !wasOnline) {
                                streams.refresh(); videos.refresh(); channels.refresh(); games.refresh(); recentlyLive.refresh()
                            }
                            if (online != null) wasOnline = online
                        }
                    }
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val bottomPadding = shell?.let { with(LocalDensity.current) { it.bottomBarHeightPx.toDp() } }?.takeIf { it.value > 0f } ?: navBottom
                    CompositionLocalProvider(LocalGlass provides (shell?.pageGlass() ?: GlassContext.Solid)) {
                    FollowingScreen(
                        segments = segments,
                        selected = selected,
                        onSelectSegment = { viewModel.selectSegment(it.key) },
                        account = account,
                        lists = FollowingLists(
                            streams = streams,
                            videos = if (FollowingSegment.Videos in segments) videos else null,
                            channels = channels,
                            games = games,
                            recentlyLive = recentlyLive,
                        ),
                        options = options,
                        videoSort = VideoSortChoice(videosViewModel.sort, videosViewModel.type),
                        channelSort = ChannelSortChoice(channelsViewModel.sort, channelsViewModel.order),
                        videoPositions = positions.associate { it.id to it.position },
                        bookmarkedVideoIds = bookmarks.mapNotNull { it.videoId }.toSet(),
                        actions = this@FollowingFragment,
                        bottomPadding = bottomPadding,
                        navScroll = shell?.hideOnScroll?.nestedScrollConnection,
                        scrollToTopRequests = scrollToTopRequests.intValue,
                        toast = toast,
                        // MainActivity only fills isNetworkAvailable once the network callback fires
                        offline = online == false || (online == null && !hasValidatedNetwork()),
                    )
                    }
                }
            }
        }
    }

    private var lastUnfollowedName: String? = null

    private fun hasValidatedNetwork(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun scrollToTop() {
        scrollToTopRequests.intValue++
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        when (callback) {
            CALLBACK_REFRESH -> refreshRequests.intValue++
            CALLBACK_UNFOLLOW -> viewModel.retryPendingUnfollow()
        }
    }

    // FollowingActions: what the v0.1 fragments, adapters and toolbar menu did, call for call

    override fun search() {
        findNavController().navigate(SearchPagerFragmentDirections.actionGlobalSearchPagerFragment())
    }

    override fun settings() {
        val activity = requireActivity() as MainActivity
        activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java))
    }

    override fun logIn() {
        val activity = requireActivity() as MainActivity
        activity.loginResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
    }

    override fun logOut() {
        val activity = requireActivity() as MainActivity
        activity.logoutResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
    }

    override fun openStream(stream: Stream) {
        (requireActivity() as MainActivity).startStream(stream)
    }

    override fun openChannel(id: String?, login: String?, name: String?, image: String?, streamId: String?) {
        findNavController().navigate(
            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                channelId = id,
                channelLogin = login,
                channelName = name,
                channelImage = image,
                streamId = streamId,
            )
        )
    }

    override fun openGame(id: String?, slug: String?, name: String?, updateLocal: Boolean) {
        findNavController().navigate(
            if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(gameId = id, gameSlug = slug, gameName = name, updateLocal = updateLocal)
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(gameId = id, gameSlug = slug, gameName = name, updateLocal = updateLocal)
            }
        )
    }

    override fun openStreamTag(tag: String) {
        findNavController().navigate(TopStreamsFragmentDirections.actionGlobalTopFragment(tags = arrayOf(tag)))
    }

    override fun openGameTag(tag: Tag) {
        findNavController().navigate(GamesFragmentDirections.actionGlobalGamesFragment(tags = arrayOf(tag)))
    }

    /** Same as "Download" on a channel page that is live: the download dialog loads the stream's qualities itself. */
    override fun record(stream: Stream) {
        DownloadDialog.newStreamInstance(
            id = stream.id,
            channelId = stream.channelId,
            channelLogin = stream.channelLogin,
            channelName = stream.channelName,
            channelImage = stream.channelImage,
            gameId = stream.gameId,
            gameSlug = stream.gameSlug,
            gameName = stream.gameName,
            title = stream.title,
            thumbnail = stream.thumbnail,
            createdAt = stream.createdAt,
        ).show(childFragmentManager, null)
    }

    override fun share(url: String, title: String?) {
        startActivity(Intent.createChooser(Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            title?.let { putExtra(Intent.EXTRA_TITLE, it) }
            type = "text/plain"
        }, null))
    }

    override fun unfollow(channelId: String?, name: String) {
        lastUnfollowedName = name
        viewModel.unfollow(channelId)
    }

    /** VideosAdapter's click: resume where the video was left, from the start when it was watched to the end. */
    override fun openVideo(video: Video, position: Long?) {
        val duration = video.durationSeconds
        val startFromBeginning = position != null && duration != null && duration > 0 && position >= duration * 1000L &&
                video.thumbnail.let { it != null && !it.startsWith("https://vod-secure.twitch.tv/_404/404_processing") }
        (requireActivity() as MainActivity).startVideo(video, if (startFromBeginning) 0 else position, startFromBeginning)
    }

    override fun download(video: Video) {
        DownloadDialog.newVideoInstance(
            id = video.id,
            channelId = video.channelId,
            channelLogin = video.channelLogin,
            channelName = video.channelName,
            channelImage = video.channelImage,
            gameId = video.gameId,
            gameSlug = video.gameSlug,
            gameName = video.gameName,
            title = video.title,
            thumbnail = video.thumbnail,
            createdAt = video.createdAt,
            durationSeconds = video.durationSeconds,
            type = video.type,
            animatedPreviewUrl = video.animatedPreviewURL,
        ).show(childFragmentManager, null)
    }

    override fun toggleBookmark(video: Video) {
        val context = requireContext()
        videosViewModel.saveBookmark(
            context.filesDir.path,
            video,
            context.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            TwitchApiHelper.getGQLHeaders(context),
            TwitchApiHelper.getHelixHeaders(context),
        )
    }

    override fun applyVideoSort(choice: VideoSortChoice, saveDefault: Boolean) {
        lifecycleScope.launch {
            if (choice.sort != videosViewModel.sort || choice.type != videosViewModel.type) {
                videosViewModel.setFilter(choice.sort, choice.type)
            }
            if (saveDefault) {
                val item = videosViewModel.getChannelSort(SORT_ID_VIDEOS)?.apply {
                    videoSort = choice.sort
                    videoType = choice.type
                } ?: ChannelSort(id = SORT_ID_VIDEOS, videoSort = choice.sort, videoType = choice.type)
                videosViewModel.saveChannelSort(item)
            }
        }
    }

    override fun applyChannelSort(choice: ChannelSortChoice, saveDefault: Boolean) {
        lifecycleScope.launch {
            if (choice.sort != channelsViewModel.sort || choice.order != channelsViewModel.order) {
                channelsViewModel.setFilter(choice.sort, choice.order)
            }
            if (saveDefault) {
                val item = channelsViewModel.getChannelSort(SORT_ID_CHANNELS)?.apply {
                    videoSort = choice.sort
                    videoType = choice.order
                } ?: ChannelSort(id = SORT_ID_CHANNELS, videoSort = choice.sort, videoType = choice.order)
                channelsViewModel.saveChannelSort(item)
            }
        }
    }

    override fun integrityCheckFailed() {
        (activity as? MainActivity)?.getNewIntegrityToken(CALLBACK_REFRESH, childFragmentManager)
    }

    private companion object {
        const val SORT_ID_VIDEOS = "followed_videos"
        const val SORT_ID_CHANNELS = "followed_channels"
        const val CALLBACK_REFRESH = "refresh"
        const val CALLBACK_UNFOLLOW = "unfollow"
    }
}
