package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing

/** A media card: card background, 8 dp inset around the thumbnail, press feedback, descendants read as one item. */
@Composable
private fun MediaCardFrame(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .pressable(onClick = onClick, onLongClick = onLongClick, shape = LagoonShapes.card)
            .background(Lagoon.colors.card)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) { content() }
}

@Composable
private fun Thumbnail(model: Any?, modifier: Modifier, overlay: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .clip(LagoonShapes.cardMedia)
            .background(Lagoon.colors.control),
    ) {
        if (model != null) LagoonImage(model, contentDescription = null, modifier = Modifier.matchParentSize())
        overlay()
    }
}

/**
 * A live stream (plan 6): big thumbnail with the live dot and viewer count, uptime, avatar, title, channel, game and
 * tags. [viewers] and [uptime] arrive formatted. Long press opens the card's action menu. [thumbnailModifier] lets a
 * screen mark the thumbnail as a shared element for the card-to-detail transition (plan 5).
 */
@Composable
fun LiveCard(
    title: String,
    channelName: String,
    viewers: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: Any? = null,
    avatar: Any? = null,
    gameName: String? = null,
    uptime: String? = null,
    tags: List<String> = emptyList(),
    onLongClick: (() -> Unit)? = null,
    thumbnailModifier: Modifier = Modifier,
) {
    val colors = Lagoon.colors
    val type = Lagoon.type
    MediaCardFrame(onClick, onLongClick, modifier) {
        Thumbnail(thumbnail, thumbnailModifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            OverlayChip(viewers, Modifier.align(Alignment.TopStart).padding(10.dp), live = true, rolling = true)
            if (uptime != null) OverlayChip(uptime, Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
        ) {
            Avatar(avatar, channelName, size = 42.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = type.bodyStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(channelName, style = type.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (gameName != null) Tag(gameName, Modifier.widthIn(max = 140.dp))
        }
        if (tags.isNotEmpty()) {
            Row(
                Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.take(3).forEach { Tag(it, Modifier.widthIn(max = 120.dp), quiet = true) }
            }
        }
    }
}

/** The compact live item: thumbnail on the left, two lines of title, channel and game on the right. */
@Composable
fun LiveCardCompact(
    title: String,
    channelName: String,
    viewers: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: Any? = null,
    gameName: String? = null,
    uptime: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = Lagoon.colors
    val type = Lagoon.type
    Row(
        modifier
            .pressable(onClick = onClick, onLongClick = onLongClick, shape = LagoonShapes.card)
            .background(colors.card)
            .padding(8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
    ) {
        Thumbnail(thumbnail, Modifier.width(148.dp).aspectRatio(16f / 9f)) {
            OverlayChip(viewers, Modifier.align(Alignment.BottomStart).padding(6.dp), live = true, rolling = true)
        }
        Column(Modifier.weight(1f).padding(end = 4.dp)) {
            Text(title, style = type.bodyStrong, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(channelName, gameName, uptime).joinToString(" · "),
                style = type.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A video or clip (plan 6): thumbnail with duration and watch progress, title and a meta line (date, views).
 * [progress] is 0..1, null hides the bar. [trailing] holds an optional action such as the download button.
 */
@Composable
fun VideoCard(
    title: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: Any? = null,
    duration: String? = null,
    progress: Float? = null,
    onLongClick: (() -> Unit)? = null,
    thumbnailModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Lagoon.colors
    val type = Lagoon.type
    MediaCardFrame(onClick, onLongClick, modifier) {
        Thumbnail(thumbnail, thumbnailModifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            if (duration != null) OverlayChip(duration, Modifier.align(Alignment.BottomEnd).padding(start = 8.dp, end = 8.dp, bottom = 10.dp))
            if (progress != null && progress > 0f) WatchProgress(progress, Modifier.align(Alignment.BottomStart))
        }
        Row(
            Modifier.padding(start = 6.dp, end = 0.dp, top = 12.dp).heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.xs),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = type.bodyStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, style = type.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke()
        }
    }
}

/** Thin teal bar along the bottom of a thumbnail showing how much has been watched. */
@Composable
fun WatchProgress(progress: Float, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.design_watched, (progress.coerceIn(0f, 1f) * 100).toInt())
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.White.copy(alpha = 0.2f))
            .semantics { this.contentDescription = label }
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(Lagoon.colors.accent))
    }
}

/** A game (plan 6): portrait box art, name and viewer count; [subtitle] is an optional second line (broadcaster count). */
@Composable
fun GamePoster(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boxArt: Any? = null,
    viewers: String? = null,
    onLongClick: (() -> Unit)? = null,
    boxArtModifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    MediaCardFrame(onClick, onLongClick, modifier) {
        Thumbnail(boxArt, boxArtModifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            if (viewers != null) OverlayChip(viewers, Modifier.align(Alignment.BottomStart).padding(8.dp), live = true)
        }
        Text(
            name,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp),
            style = Lagoon.type.bodyStrong,
            color = Lagoon.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp),
                style = Lagoon.type.label,
                color = Lagoon.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A channel in a list (plan 6): avatar with the teal ring while live, name, and what it is streaming or when it was
 * last live. Sits inside a card or group; [trailing] holds an optional action.
 */
@Composable
fun ChannelRow(
    name: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: Any? = null,
    live: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, onLongClick = onLongClick, shape = LagoonShapes.row, scaleOnPress = false)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
    ) {
        Avatar(avatar, name, size = 40.dp, live = live)
        Column(Modifier.weight(1f)) {
            Text(name, style = Lagoon.type.bodyStrong, color = Lagoon.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = Lagoon.type.label, color = Lagoon.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

/** Small grey pill for a game or tag on a card. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, quiet: Boolean = false) {
    Text(
        text,
        modifier = modifier
            .background(if (quiet) Lagoon.colors.page else Lagoon.colors.control, LagoonShapes.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = Lagoon.type.labelStrong,
        color = Lagoon.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

