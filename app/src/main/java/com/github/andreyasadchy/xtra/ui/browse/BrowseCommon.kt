package com.github.andreyasadchy.xtra.ui.browse

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.design.components.ActionItem
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle
import com.github.andreyasadchy.xtra.ui.design.components.ErrorState
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LiveCard
import com.github.andreyasadchy.xtra.ui.design.components.LiveCardCompact
import com.github.andreyasadchy.xtra.ui.design.components.VideoCard
import com.github.andreyasadchy.xtra.ui.design.components.shimmer
import com.github.andreyasadchy.xtra.ui.design.components.staggeredAppear
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.WidthClass
import com.github.andreyasadchy.xtra.ui.following.FollowingListOptions
import com.github.andreyasadchy.xtra.ui.following.displayName
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlin.time.Instant

/**
 * Shared pieces of the U4 browse pages (games grid, game page, streams by tag, search): what the list items show,
 * the long-press menus, and the loading, empty and error states of a paged list.
 */

/** Whether the device has no connection: the lists then name the connection in their error state. */
val LocalBrowseOffline = staticCompositionLocalOf { false }

/** What the browse pages can ask their host fragment to do (the v0.1 adapters' and menus' calls). */
interface BrowseActions {
    fun back()
    fun search()
    fun openStream(stream: Stream)
    fun openChannel(id: String?, login: String?, name: String?, image: String?, streamId: String? = null)
    fun openGame(id: String?, slug: String?, name: String?, boxArt: String? = null, updateLocal: Boolean = false)
    /** Live streams with this tag (v0.1: the popular streams page with the tag). */
    fun openStreamTag(tag: String)
    fun record(stream: Stream)
    fun share(url: String, title: String?)
    fun openVideo(video: Video, position: Long?)
    fun downloadVideo(video: Video)
    fun toggleBookmark(video: Video)
    fun openClip(clip: Clip)
    fun downloadClip(clip: Clip)
    fun integrityCheckFailed()
}

/** Columns of the card grids: one on phones, two at medium width, three on tablets (four from 1440 dp). */
fun cardColumns(widthDp: Int) = when (val widthClass = WidthClass.of(widthDp)) {
    WidthClass.Expanded -> if (widthDp >= 1440) 4 else 3
    else -> widthClass.cardColumns
}

/** Columns of the poster grids (plan 7.2): three on phones, four at medium width, six on tablets. */
fun posterColumns(widthDp: Int) = when (WidthClass.of(widthDp)) {
    WidthClass.Compact -> 3
    WidthClass.Medium -> 4
    WidthClass.Expanded -> 6
}

private val LoadState.integrityFailed get() = (this as? LoadState.Error)?.error?.message == C.FAILED_INTEGRITY_CHECK

/** v0.1 asked for a new integrity token when a page failed the integrity check. */
@Composable
fun IntegrityWatch(items: LazyPagingItems<*>?, actions: BrowseActions) {
    if (items == null) return
    val failed = items.loadState.refresh.integrityFailed || items.loadState.append.integrityFailed
    LaunchedEffect(items, failed) { if (failed) actions.integrityCheckFailed() }
}

/**
 * The items of a paged list inside a grid: the first-load skeleton, the error state with retry, the empty state,
 * the items, and the footer while the next page loads or fails to.
 */
fun <T : Any> LazyGridScope.pagedItems(
    items: LazyPagingItems<T>,
    offline: Boolean,
    contentType: String,
    skeleton: @Composable LazyGridItemScope.(Int) -> Unit,
    empty: @Composable () -> Unit,
    skeletonCount: Int = 6,
    item: @Composable LazyGridItemScope.(index: Int, item: T) -> Unit,
) {
    val refresh = items.loadState.refresh
    val first = items.itemCount == 0
    when {
        first && refresh is LoadState.Loading -> items(skeletonCount, contentType = { "skeleton" }) { skeleton(it) }
        first && refresh is LoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }, contentType = "error") {
            ErrorState(
                message = stringResource(if (offline) R.string.no_connection else R.string.following_load_error),
                onRetry = { items.refresh() },
                modifier = Modifier.padding(top = 32.dp),
            )
        }
        first -> item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") { empty() }
        else -> {
            items(items.itemCount, contentType = { contentType }) { index ->
                val value = items[index] ?: return@items
                item(index, value)
            }
            when (items.loadState.append) {
                is LoadState.Loading -> item(span = { GridItemSpan(maxLineSpan) }, contentType = "more") {
                    Box(Modifier.fillMaxWidth().height(4.dp).shimmer(LagoonShapes.pill))
                }
                is LoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }, contentType = "more") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LagoonButton(
                            stringResource(R.string.following_retry_more), { items.retry() },
                            style = ButtonStyle.Secondary, icon = LucideIcons.RotateCcw,
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

/** Long-press actions also reachable by screen readers (plan 10.5). */
fun Modifier.actionsForAccessibility(actions: List<ActionItem>) = semantics {
    customActions = actions.map { CustomAccessibilityAction(it.label) { it.onClick(); true } }
}

fun uptime(createdAt: String?, now: Long): String? =
    createdAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds()?.takeIf { it > 0 }?.let { start ->
        val seconds = (now - start) / 1000
        if (seconds > 0) DateUtils.formatElapsedTime(seconds) else null
    }

/** A live stream as a card, or as a compact row when the compact setting asks for it. */
@Composable
fun StreamItem(
    stream: Stream,
    index: Int,
    options: FollowingListOptions,
    compact: Boolean,
    showGame: Boolean,
    menu: List<ActionItem>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val viewers = TwitchApiHelper.formatCount(stream.viewerCount ?: 0, options.truncateCounts)
    val uptime = if (options.showUptime) uptime(stream.createdAt, options.nowMillis()) else null
    val name = displayName(stream.channelName, stream.channelLogin, options.nameDisplay)
    val modifier = Modifier.staggeredAppear(index).actionsForAccessibility(menu)
    if (compact) {
        LiveCardCompact(
            title = stream.title?.trim().orEmpty(), channelName = name, viewers = viewers, onClick = onClick, modifier = modifier,
            thumbnail = options.image(stream.thumbnail), gameName = if (showGame) stream.gameName else null, uptime = uptime,
            onLongClick = onLongClick,
        )
    } else {
        LiveCard(
            title = stream.title?.trim().orEmpty(), channelName = name, viewers = viewers, onClick = onClick, modifier = modifier,
            thumbnail = options.image(stream.thumbnail), avatar = options.image(stream.channelImage),
            gameName = if (showGame) stream.gameName else null, uptime = uptime,
            tags = if (options.showTags) stream.tags.orEmpty() else emptyList(), onLongClick = onLongClick,
        )
    }
}

/**
 * A stream's long-press menu: open channel, open game, its tags, record, share. [onTag] is what a tag does here: on
 * the game page and the streams-by-tag page it narrows the list (as the v0.1 tag chips did), elsewhere it opens the
 * streams with that tag.
 */
@Composable
fun streamMenu(
    stream: Stream,
    options: FollowingListOptions,
    actions: BrowseActions,
    showGame: Boolean,
    onTag: (String) -> Unit,
): List<ActionItem> {
    val list = mutableListOf(
        ActionItem(stringResource(R.string.following_open_channel), LucideIcons.User) {
            actions.openChannel(stream.channelId, stream.channelLogin, stream.channelName, stream.channelImage, stream.id)
        },
    )
    if (showGame) {
        stream.gameName?.let { game ->
            list += ActionItem(stringResource(R.string.following_open_game, game), LucideIcons.Gamepad2) {
                actions.openGame(stream.gameId, stream.gameSlug, stream.gameName)
            }
        }
    }
    if (options.showTags) {
        stream.tags.orEmpty().take(3).forEach { tag ->
            list += ActionItem(stringResource(R.string.following_browse_tag, tag), LucideIcons.ListFilter) { onTag(tag) }
        }
    }
    list += ActionItem(stringResource(R.string.following_record), LucideIcons.CircleDot) { actions.record(stream) }
    list += ActionItem(stringResource(R.string.share), LucideIcons.Share2) {
        actions.share("https://twitch.tv/${stream.channelLogin}", stream.title)
    }
    return list
}

/** A video card: channel, type, date and views under the title, how much was watched on the thumbnail. */
@Composable
fun VideoItem(
    video: Video,
    index: Int,
    options: FollowingListOptions,
    position: Long?,
    showChannel: Boolean,
    menu: List<ActionItem>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val duration = video.durationSeconds
    val meta = listOfNotNull(
        if (showChannel) displayName(video.channelName, video.channelLogin, options.nameDisplay).takeIf { it.isNotEmpty() } else null,
        TwitchApiHelper.getType(context, video.type),
        video.createdAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds()?.takeIf { it > 0 }?.let { TwitchApiHelper.formatDate(context, it) },
        video.viewCount?.let { pluralStringResource(R.plurals.views, it, TwitchApiHelper.formatCount(it, options.truncateCounts)) },
    ).joinToString(" · ")
    VideoCard(
        title = video.title?.trim().orEmpty(),
        meta = meta,
        onClick = onClick,
        modifier = Modifier.staggeredAppear(index).actionsForAccessibility(menu),
        thumbnail = options.image(video.thumbnail),
        duration = duration?.let { DateUtils.formatElapsedTime(it.toLong()) },
        progress = if (position != null && duration != null && duration > 0) (position / (duration * 1000f)).coerceIn(0f, 1f) else null,
        onLongClick = onLongClick,
    )
}

/** v0.1's video menu (download, bookmark, share) plus the jumps it had as taps on the channel and game. */
@Composable
fun videoMenu(video: Video, bookmarked: Boolean, actions: BrowseActions, showGame: Boolean): List<ActionItem> {
    val list = mutableListOf(
        ActionItem(stringResource(R.string.download), LucideIcons.Download) { actions.downloadVideo(video) },
    )
    if (!video.id.isNullOrBlank()) {
        list += ActionItem(
            stringResource(if (bookmarked) R.string.remove_bookmark else R.string.add_bookmark),
            if (bookmarked) LucideIcons.BookmarkFilled else LucideIcons.Bookmark,
        ) { actions.toggleBookmark(video) }
    }
    list += ActionItem(stringResource(R.string.share), LucideIcons.Share2) {
        actions.share("https://twitch.tv/videos/${video.id}", video.title)
    }
    if (video.channelId != null || video.channelLogin != null) {
        list += ActionItem(stringResource(R.string.following_open_channel), LucideIcons.User) {
            actions.openChannel(video.channelId, video.channelLogin, video.channelName, video.channelImage)
        }
    }
    if (showGame) {
        video.gameName?.let { game ->
            list += ActionItem(stringResource(R.string.following_open_game, game), LucideIcons.Gamepad2) {
                actions.openGame(video.gameId, video.gameSlug, video.gameName)
            }
        }
    }
    return list
}

/** A clip card: channel, date and views under the title, the clip's length on the thumbnail. */
@Composable
fun ClipItem(
    clip: Clip,
    index: Int,
    options: FollowingListOptions,
    showChannel: Boolean,
    menu: List<ActionItem>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val meta = listOfNotNull(
        if (showChannel) displayName(clip.channelName, clip.channelLogin, options.nameDisplay).takeIf { it.isNotEmpty() } else null,
        clip.createdAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds()?.takeIf { it > 0 }?.let { TwitchApiHelper.formatDate(context, it) },
        clip.viewCount?.let { pluralStringResource(R.plurals.views, it, TwitchApiHelper.formatCount(it, options.truncateCounts)) },
    ).joinToString(" · ")
    VideoCard(
        title = clip.title?.trim().orEmpty(),
        meta = meta,
        onClick = onClick,
        modifier = Modifier.staggeredAppear(index).actionsForAccessibility(menu),
        thumbnail = options.image(clip.thumbnail),
        duration = clip.durationSeconds?.let { DateUtils.formatElapsedTime(it.toLong()) },
        onLongClick = onLongClick,
    )
}

/** v0.1's clip menu (download, share) plus the channel and game it had as taps. */
@Composable
fun clipMenu(clip: Clip, actions: BrowseActions, showGame: Boolean): List<ActionItem> {
    val list = mutableListOf(
        ActionItem(stringResource(R.string.download), LucideIcons.Download) { actions.downloadClip(clip) },
        ActionItem(stringResource(R.string.share), LucideIcons.Share2) {
            actions.share("https://twitch.tv/${clip.channelLogin}/clip/${clip.id}", clip.title)
        },
    )
    if (clip.channelId != null || clip.channelLogin != null) {
        list += ActionItem(stringResource(R.string.following_open_channel), LucideIcons.User) {
            actions.openChannel(clip.channelId, clip.channelLogin, clip.channelName, clip.channelImage)
        }
    }
    if (showGame) {
        clip.gameName?.let { game ->
            list += ActionItem(stringResource(R.string.following_open_game, game), LucideIcons.Gamepad2) {
                actions.openGame(clip.gameId, clip.gameSlug, clip.gameName)
            }
        }
    }
    return list
}

/** Heading over a group of results or a section of a page. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, style = Lagoon.type.headline, color = Lagoon.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** Height of [DetailTopBar] under the status bar. */
val DetailTopBarHeight = 64.dp

/**
 * The bar of a page one level down (game page, search): back on the left, actions on the right, and a small title
 * that fades in, with the page colour behind it, once the page's own header has scrolled away ([collapsed] 0 to 1).
 */
@Composable
fun DetailTopBar(
    title: String,
    collapsed: () -> Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val page = Lagoon.colors.page
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind { drawRect(page.copy(alpha = collapsed().coerceIn(0f, 1f))) }
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(DetailTopBarHeight)
            .padding(start = 8.dp, end = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            LagoonIconButton(LucideIcons.ArrowLeft, stringResource(R.string.browse_back), onBack)
            Spacer(Modifier.width(4.dp))
            Text(
                title, style = Lagoon.type.headline, color = Lagoon.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = collapsed().coerceIn(0f, 1f) }
                    .semantics { heading() },
            )
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
    }
}
