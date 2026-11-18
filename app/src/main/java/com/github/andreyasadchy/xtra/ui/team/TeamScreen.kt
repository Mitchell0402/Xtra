package com.github.andreyasadchy.xtra.ui.team

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.browse.BrowseActions
import com.github.andreyasadchy.xtra.ui.browse.DetailTopBar
import com.github.andreyasadchy.xtra.ui.browse.DetailTopBarHeight
import com.github.andreyasadchy.xtra.ui.browse.IntegrityWatch
import com.github.andreyasadchy.xtra.ui.browse.LocalBrowseOffline
import com.github.andreyasadchy.xtra.ui.browse.StreamItem
import com.github.andreyasadchy.xtra.ui.browse.actionsForAccessibility
import com.github.andreyasadchy.xtra.ui.browse.cardColumns
import com.github.andreyasadchy.xtra.ui.browse.pagedItems
import com.github.andreyasadchy.xtra.ui.browse.rememberCoverColor
import com.github.andreyasadchy.xtra.ui.browse.streamMenu
import com.github.andreyasadchy.xtra.ui.channel.MarkdownText
import com.github.andreyasadchy.xtra.ui.design.components.StaggerGate
import com.github.andreyasadchy.xtra.ui.design.components.ActionItem
import com.github.andreyasadchy.xtra.ui.design.components.ActionSheet
import com.github.andreyasadchy.xtra.ui.design.components.Avatar
import com.github.andreyasadchy.xtra.ui.design.components.ChannelRow
import com.github.andreyasadchy.xtra.ui.design.components.EmptyState
import com.github.andreyasadchy.xtra.ui.design.components.GlassBackdrop
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonImage
import com.github.andreyasadchy.xtra.ui.design.components.LagoonPullToRefresh
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonRow
import com.github.andreyasadchy.xtra.ui.design.components.ToastHost
import com.github.andreyasadchy.xtra.ui.design.components.ToastHostState
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.ui.following.displayName
import com.github.andreyasadchy.xtra.util.TwitchApiHelper

/** What the team page asks its host for besides the list actions. */
interface TeamActions : BrowseActions {
    fun shareTeam()
}

/** The team's header; null fields are not shown. */
@Immutable
data class TeamHeader(
    val name: String?,
    val displayName: String? = null,
    val description: String? = null,
    val logo: String? = null,
    val banner: String? = null,
    val members: Int? = null,
    val ownerLogin: String? = null,
    val ownerName: String? = null,
)

const val TeamListTag = "teamList"

/**
 * A team's page (plan 7.8: "styled like the channel page", U5): banner and logo, name, member count, owner and the
 * description (three lines, the rest on tap), then the members: live members as stream cards that open the stream,
 * the others as channel rows that open the channel (v0.1 TeamMembersAdapter).
 */
@Composable
fun TeamScreen(
    header: TeamHeader,
    members: LazyPagingItems<Stream>?,
    options: FollowingListOptions,
    actions: TeamActions,
    modifier: Modifier = Modifier,
    compactStreams: Boolean = false,
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    navScroll: NestedScrollConnection? = null,
    scrollToTopRequests: Int = 0,
    toast: ToastHostState? = null,
    offline: Boolean = false,
) {
    val configuration = LocalConfiguration.current
    val columns = cardColumns(configuration.screenWidthDp)
    val grid = rememberLazyGridState()
    var headerHeight by remember { mutableIntStateOf(0) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barTop = statusTop + DetailTopBarHeight
    IntegrityWatch(members, actions)
    LaunchedEffect(scrollToTopRequests) {
        if (scrollToTopRequests > 0) grid.animateScrollToItem(0)
    }
    val coverColor = rememberCoverColor(options.image(header.banner ?: header.logo)) ?: Lagoon.colors.card
    val collapsed: () -> Float = {
        if (grid.firstVisibleItemIndex > 0) 1f else grid.firstVisibleItemScrollOffset / (headerHeight * 0.6f).coerceAtLeast(1f)
    }
    val title = header.displayName ?: header.name.orEmpty()
    Box(modifier.fillMaxSize()) {
        GlassBackdrop(Modifier.fillMaxSize().then(if (navScroll != null) Modifier.nestedScroll(navScroll) else Modifier)) {
            CompositionLocalProvider(LocalBrowseOffline provides offline) {
                LagoonPullToRefresh(
                    isRefreshing = members?.loadState?.refresh is LoadState.Loading && (members?.itemCount ?: 0) > 0,
                    onRefresh = { members?.refresh() },
                    modifier = Modifier.fillMaxSize(),
                    indicatorTop = barTop,
                ) {
                    StaggerGate("team", hasItems = (members?.itemCount ?: 0) > 0) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = grid,
                            modifier = Modifier.fillMaxSize().testTag(TeamListTag),
                            contentPadding = PaddingValues(
                                start = LagoonSpacing.pageSide, end = LagoonSpacing.pageSide, top = barTop,
                                bottom = bottomPadding + LagoonSpacing.m,
                            ),
                            verticalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap),
                            horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap),
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "header", contentType = "header") {
                                TeamHeaderBlock(header, coverColor, options, barTop, Modifier.onSizeChanged { headerHeight = it.height })
                            }
                            if (members != null) {
                                pagedItems(
                                    members, offline, "member",
                                    skeleton = { SkeletonRow() },
                                    empty = { EmptyState(stringResource(R.string.team_empty), icon = LucideIcons.Users) },
                                ) { index, member ->
                                    MemberCell(member, index, options, compactStreams, actions)
                                }
                            }
                        }
                    }
                }
            }
        }
        DetailTopBar(
            title = title,
            collapsed = collapsed,
            onBack = actions::back,
            modifier = Modifier.semantics { isTraversalGroup = true; traversalIndex = -1f },
            actions = {
                LagoonIconButton(LucideIcons.Search, stringResource(R.string.search), actions::search)
                LagoonIconButton(LucideIcons.Share2, stringResource(R.string.share), actions::shareTeam)
            },
        )
        if (toast != null) {
            ToastHost(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding))
        }
    }
}

@Composable
private fun MemberCell(member: Stream, index: Int, options: FollowingListOptions, compact: Boolean, actions: TeamActions) {
    var menuOpen by remember { mutableStateOf(false) }
    val openChannel = { actions.openChannel(member.channelId, member.channelLogin, member.channelName, member.channelImage, member.id) }
    if (member.viewerCount != null) {
        val menu = streamMenu(member, options, actions, showGame = true, onTag = actions::openStreamTag)
        StreamItem(member, index, options, compact, showGame = true, menu = menu, onClick = { actions.openStream(member) }, onLongClick = { menuOpen = true })
        if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = member.channelName ?: member.channelLogin)
    } else {
        val name = displayName(member.channelName, member.channelLogin, options.nameDisplay)
        val menu = listOf(
            ActionItem(stringResource(R.string.following_open_channel), LucideIcons.User) { openChannel() },
            ActionItem(stringResource(R.string.share), LucideIcons.Share2) { actions.share("https://twitch.tv/${member.channelLogin}", member.channelName) },
        )
        ChannelRow(
            name = name,
            subtitle = stringResource(R.string.team_member_offline),
            onClick = openChannel,
            avatar = options.image(member.channelImage),
            onLongClick = { menuOpen = true },
            modifier = Modifier.background(Lagoon.colors.card, LagoonShapes.row).actionsForAccessibility(menu),
        )
        if (menuOpen) ActionSheet(menu, onDismiss = { menuOpen = false }, title = name)
    }
}

private val BannerMaxHeight = 200.dp
private val LogoSize = 80.dp

@Composable
private fun TeamHeaderBlock(header: TeamHeader, coverColor: Color, options: FollowingListOptions, gradientTop: Dp, modifier: Modifier = Modifier) {
    val colors = Lagoon.colors
    val page = colors.page
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val bannerHeight = minOf(maxWidth * 0.4f, BannerMaxHeight)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(bannerHeight)
                    .drawBehind {
                        val side = LagoonSpacing.pageSide.toPx()
                        val top = gradientTop.toPx()
                        drawRect(
                            Brush.verticalGradient(
                                0f to coverColor.copy(alpha = 0.9f),
                                0.7f to coverColor.copy(alpha = 0.45f),
                                1f to page.copy(alpha = 0f),
                                startY = -top, endY = size.height,
                            ),
                            topLeft = Offset(-side, -top),
                            size = Size(size.width + side * 2, size.height + top),
                        )
                    },
            ) {
                val banner = options.image(header.banner)
                if (banner != null) {
                    Box(Modifier.fillMaxSize().clip(LagoonShapes.card)) {
                        LagoonImage(banner, contentDescription = null, modifier = Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to page.copy(alpha = 0.85f))))
                    }
                }
            }
            Avatar(
                options.image(header.logo), header.displayName ?: header.name.orEmpty(), size = LogoSize,
                modifier = Modifier.padding(start = LagoonSpacing.xs).offset(y = bannerHeight - LogoSize * 0.6f),
            )
        }
        Spacer(Modifier.height(LogoSize * 0.6f + LagoonSpacing.s))
        Text(
            header.displayName ?: header.name.orEmpty(), style = Lagoon.type.title, color = colors.textPrimary,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
        )
        val line = listOfNotNull(
            header.members?.let { pluralStringResource(R.plurals.members, it, TwitchApiHelper.formatCount(it, options.truncateCounts)) },
            if (!header.ownerName.isNullOrBlank() || !header.ownerLogin.isNullOrBlank()) {
                stringResource(R.string.owner, displayName(header.ownerName, header.ownerLogin, options.nameDisplay))
            } else null,
        )
        if (line.isNotEmpty()) {
            Text(line.joinToString(" · "), style = Lagoon.type.label, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val description = header.description?.trim()
        if (!description.isNullOrEmpty()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            Column(Modifier.padding(top = LagoonSpacing.xs).animateContentSize(Lagoon.motion.move())) {
                MarkdownText(description, maxLines = if (expanded) Int.MAX_VALUE else 3)
                // the text's links take the taps, so the description opens with its own button (v0.1: tap the text)
                if (description.length > 120 || description.count { it == '\n' } >= 3) {
                    Text(
                        stringResource(if (expanded) R.string.team_show_less else R.string.team_show_more),
                        style = Lagoon.type.labelStrong,
                        color = colors.accent,
                        modifier = Modifier
                            .pressable(onClick = { expanded = !expanded }, shape = LagoonShapes.pill, scaleOnPress = false)
                            .padding(vertical = 10.dp, horizontal = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(LagoonSpacing.xs))
    }
}
