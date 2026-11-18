package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.IntOffset
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon

/**
 * For items of a list the viewer adds to or removes from (downloads, bookmarks, saved filters;
 * motion plan M4 item 4, L4): a new item fades in, the others slide to their new places on the steady spring, a removed
 * one fades out. Not for paged lists that only grow at the end, where moving every item would mean nothing (L13).
 * The items need stable keys.
 */
@Composable
fun LazyGridItemScope.userListItem(): Modifier {
    val motion = Lagoon.motion
    return Modifier.animateItem(
        fadeInSpec = motion.listItemFadeIn(),
        placementSpec = motion.listItemPlacement(IntOffset.VisibilityThreshold),
        fadeOutSpec = motion.listItemFadeOut(),
    )
}
