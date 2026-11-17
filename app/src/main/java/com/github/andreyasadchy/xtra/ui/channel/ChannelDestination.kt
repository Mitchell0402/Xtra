package com.github.andreyasadchy.xtra.ui.channel

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.browse.FilterKind
import com.github.andreyasadchy.xtra.ui.browse.FilterSheetConfig
import com.github.andreyasadchy.xtra.ui.browse.hasValidatedNetwork
import com.github.andreyasadchy.xtra.ui.channel.about.ChannelAboutViewModel
import com.github.andreyasadchy.xtra.ui.channel.clips.ChannelClipsViewModel
import com.github.andreyasadchy.xtra.ui.channel.suggestions.ChannelSuggestionsViewModel
import com.github.andreyasadchy.xtra.ui.channel.videos.ChannelVideosViewModel
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.ui.design.components.GlassContext
import com.github.andreyasadchy.xtra.ui.design.components.LocalGlass
import com.github.andreyasadchy.xtra.ui.design.components.rememberToastHostState
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.ui.following.displayName
import com.github.andreyasadchy.xtra.ui.main.MainViewModel
import com.github.andreyasadchy.xtra.ui.main.nav.ChannelArgs
import com.github.andreyasadchy.xtra.ui.main.nav.ComposePage
import com.github.andreyasadchy.xtra.ui.main.nav.PageHost
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Instant

/**
 * A channel's page in Compose (U5), in place of the v0.1 ChannelPagerFragment, its view pager and its four list
 * fragments. The chat segment is the chat fragment (U7), hosted over the page under the pinned tabs.
 */
class ChannelDestination(host: PageHost) : ComposePage(host) {

    private val args = ChannelArgs.fromBundle(arguments)
    private val pagerViewModel: ChannelPagerViewModel by viewModels { ChannelPagerViewModel.Companion.ChannelPagerViewModelFactory }
    private val videosViewModel: ChannelVideosViewModel by viewModels { ChannelVideosViewModel.Companion.ChannelVideosViewModelFactory }
    private val clipsViewModel: ChannelClipsViewModel by viewModels { ChannelClipsViewModel.Companion.ChannelClipsViewModelFactory }
    private val aboutViewModel: ChannelAboutViewModel by viewModels { ChannelAboutViewModel.Companion.ChannelAboutViewModelFactory }
    private val suggestionsViewModel: ChannelSuggestionsViewModel by viewModels { ChannelSuggestionsViewModel.Companion.ChannelSuggestionsViewModelFactory }
    private val mainViewModel: MainViewModel by activityViewModels { MainViewModel.Companion.MainViewModelFactory }
    private val scrollToTopRequests = mutableIntStateOf(0)
    private val page by lazy { ChannelPage(this, args, pagerViewModel, videosViewModel, clipsViewModel, aboutViewModel) }

    override fun onCreate() {
        page.start()
    }

    @Composable
    override fun Content() {
        val prefs = remember { requireContext().prefs() }
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottom = shell?.let { with(LocalDensity.current) { it.bottomBarHeightPx.toDp() } }?.takeIf { it.value > 0f } ?: navBottom
        // the chat area the page leaves under its pinned tabs; the chat stays alive once opened, hidden while another segment shows
        var chatArea by remember { mutableStateOf<ChatArea?>(null) }
        var chatOpened by rememberSaveable { mutableStateOf(false) }
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalGlass provides (shell?.pageGlass() ?: GlassContext.Solid)) {
                Page(prefs, bottom, shell?.hideOnScroll?.nestedScrollConnection) { area ->
                    chatArea = area
                    if (area.visible) chatOpened = true
                }
            }
            val area = chatArea
            if (chatOpened) {
                val density = LocalDensity.current
                val chatArgs = remember {
                    ChatFragment.newInstance(
                        channelId = page.channelId,
                        channelLogin = page.channelLogin,
                        channelName = page.channelName,
                        streamId = pagerViewModel.stream.value?.id ?: args.streamId,
                    ).arguments ?: Bundle()
                }
                AndroidFragment<ChatFragment>(
                    modifier = if (area != null && area.visible) {
                        with(density) { Modifier.fillMaxSize().padding(start = area.left.toDp(), top = area.top.toDp(), bottom = area.bottom.toDp()) }
                    } else {
                        Modifier.size(0.dp)
                    },
                    arguments = chatArgs,
                )
            }
        }
    }

    @Composable
    private fun Page(
        prefs: android.content.SharedPreferences,
        bottomPadding: androidx.compose.ui.unit.Dp,
        navScroll: androidx.compose.ui.input.nestedscroll.NestedScrollConnection?,
        onChatArea: (ChatArea) -> Unit,
    ) {
        val context = requireContext()
        val segmentsPref = remember { prefs.getString(C.UI_CHANNEL_TABS, null) }
        val segments = remember { ChannelSegment.list(segmentsPref) }
        var selectedKey by rememberSaveable { mutableStateOf(ChannelSegment.default(segmentsPref, segments).key) }
        val selected = ChannelSegment.of(selectedKey)?.takeIf { it in segments } ?: segments.first()
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
        val followSetting = remember { prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0 }
        val loggedIn = remember {
            !TwitchApiHelper.getGQLHeaders(context, true)[C.HEADER_TOKEN].isNullOrBlank() ||
                    !TwitchApiHelper.getHelixHeaders(context)[C.HEADER_TOKEN].isNullOrBlank()
        }
        val videosFilterValue by videosViewModel.filter.collectAsState()
        val clipsFilterValue by clipsViewModel.filter.collectAsState()
        val suggestions = (if (ChannelSegment.Suggestions in segments) suggestionsViewModel.flow else emptyFlow()).collectAsLazyPagingItems()
        val videos = (if (ChannelSegment.Videos in segments && videosFilterValue != null) videosViewModel.flow else emptyFlow()).collectAsLazyPagingItems()
        val clips = (if (ChannelSegment.Clips in segments && clipsFilterValue != null) clipsViewModel.flow else emptyFlow()).collectAsLazyPagingItems()
        val stream by pagerViewModel.stream.collectAsState()
        val user by pagerViewModel.user.collectAsState()
        val following by pagerViewModel.isFollowing.collectAsState()
        val notifications by pagerViewModel.notificationsEnabled.collectAsState()
        val description by aboutViewModel.description.collectAsState()
        val socialMedias by aboutViewModel.socialMedias.collectAsState()
        val team by aboutViewModel.team.collectAsState()
        val originalName by aboutViewModel.originalName.collectAsState()
        val panels by aboutViewModel.panels.collectAsState()
        val isRemembered by page.remembered.collectAsState()
        val positions by remember { videosViewModel.positions }.collectAsState(emptyList())
        val bookmarks by remember { videosViewModel.bookmarks }.collectAsState(emptyList())
        val online by mainViewModel.isNetworkAvailable.collectAsState()
        val toast = rememberToastHostState()
        page.refreshLists = { suggestions.refresh(); videos.refresh(); clips.refresh() }

        LaunchedEffect(Unit) {
            pagerViewModel.integrity.collect { activity.getNewIntegrityToken(it, childFragmentManager) }
        }
        LaunchedEffect(Unit) {
            aboutViewModel.integrity.collect { activity.getNewIntegrityToken(it, childFragmentManager) }
        }
        LaunchedEffect(user) { if (user != null) page.onUserLoaded() }
        LaunchedEffect(Unit) {
            pagerViewModel.follow.collect { pair ->
                if (pair != null) {
                    val (followed, error) = pair
                    val name = displayName(page.channelName, page.channelLogin, options.nameDisplay)
                    toast.show(
                        when {
                            !error.isNullOrBlank() -> error
                            followed -> getString(R.string.now_following, name)
                            else -> getString(R.string.unfollowed, name)
                        }
                    )
                    pagerViewModel.follow.value = null
                }
            }
        }
        LaunchedEffect(Unit) {
            pagerViewModel.notifications.collect { pair ->
                if (pair != null) {
                    val (enabled, error) = pair
                    toast.show(
                        when {
                            !error.isNullOrBlank() -> error
                            enabled -> getString(R.string.enabled_notifications)
                            else -> getString(R.string.disabled_notifications)
                        }
                    )
                    pagerViewModel.notifications.value = null
                }
            }
        }
        // v0.1 (BaseNetworkFragment.onNetworkRestored): reload the channel and retry the lists when the connection returns
        LaunchedEffect(Unit) {
            var wasOnline = mainViewModel.isNetworkAvailable.value ?: context.hasValidatedNetwork()
            mainViewModel.isNetworkAvailable.collectLatest { now ->
                if (now == true && !wasOnline) {
                    page.loadChannel()
                    suggestions.retry(); videos.retry(); clips.retry()
                }
                if (now != null) wasOnline = now
            }
        }

        val live = stream?.takeIf { it.viewerCount != null }
        val roles = listOfNotNull(
            when (user?.broadcasterType?.lowercase()) {
                "partner" -> getString(R.string.user_partner)
                "affiliate" -> getString(R.string.user_affiliate)
                else -> null
            },
            if (user?.type?.lowercase() == "staff") getString(R.string.user_staff) else null,
        ).joinToString(", ").ifEmpty { null }
        val header = ChannelHeader(
            id = page.channelId,
            login = page.channelLogin,
            name = page.channelName,
            avatar = stream?.channelImage ?: user?.profileImage ?: args.channelImage,
            banner = user?.bannerImageURL,
            followers = user?.followerCount,
            roles = roles,
            createdAt = user?.createdAt?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } },
            lastBroadcast = user?.lastBroadcast?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } },
            description = description,
            stream = live,
        )
        val canRemember = !args.channelId.isNullOrBlank()
        ChannelScreen(
            header = header,
            segments = segments,
            selected = selected,
            onSelect = { selectedKey = it.key },
            lists = ChannelLists(
                suggestions = suggestions.takeIf { ChannelSegment.Suggestions in segments },
                videos = videos.takeIf { videosFilterValue != null },
                clips = clips.takeIf { clipsFilterValue != null },
            ),
            filters = ChannelFilters(
                videos = page.videosFilter(),
                clips = page.clipsFilter(),
                videosConfig = FilterSheetConfig(FilterKind.Videos, showPeriod = false, canRemember = canRemember, remembered = isRemembered, showLanguages = false, rememberForChannel = true, defaultVideoSort = com.github.andreyasadchy.xtra.ui.common.VideoFilters.SORT_TIME),
                clipsConfig = FilterSheetConfig(FilterKind.Clips, canRemember = canRemember, remembered = isRemembered, showLanguages = false, rememberForChannel = true),
            ),
            about = ChannelAbout(
                description = description,
                socialMedias = socialMedias.orEmpty().mapNotNull { (title, url) -> title?.takeIf { it.isNotBlank() }?.let { it to url } },
                team = team?.let { (teamName, display) -> display?.takeIf { it.isNotBlank() }?.let { teamName to it } },
                originalName = originalName,
                panels = panels.orEmpty(),
                loaded = panels != null || socialMedias != null,
            ),
            options = options,
            actions = page,
            loggedIn = loggedIn,
            accountName = remember { context.tokenPrefs().getString(C.USERNAME, null) },
            showFollow = followSetting < 2,
            following = following,
            notifications = notifications,
            videoPositions = positions.associate { it.id to it.position },
            bookmarkedVideoIds = bookmarks.mapNotNull { it.videoId }.toSet(),
            bottomPadding = bottomPadding,
            navScroll = navScroll,
            scrollToTopRequests = scrollToTopRequests.intValue,
            toast = toast,
            offline = online == false || (online == null && !context.hasValidatedNetwork()),
            onChatArea = onChatArea,
        )
    }

    override fun scrollToTop() {
        scrollToTopRequests.intValue++
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        page.onIntegrityTokenLoaded(callback)
    }
}
