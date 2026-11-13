package com.github.andreyasadchy.xtra.ui.design.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radii (plan section 4): cards 24, rows and inputs 16, thumbnails 12, buttons and chips fully round. */
object LagoonShapes {
    val card = RoundedCornerShape(24.dp)
    /** A thumbnail inside a card: card radius minus the card's 8 dp inset, so the corners stay concentric. */
    val cardMedia = RoundedCornerShape(16.dp)
    val row = RoundedCornerShape(16.dp)
    val input = RoundedCornerShape(16.dp)
    val thumbnail = RoundedCornerShape(12.dp)
    val pill = CircleShape
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    /** Icon tiles in settings rows. */
    val tile = RoundedCornerShape(11.dp)

    /** Avatars are soft squares, a third of their size. */
    fun avatar(size: Dp) = RoundedCornerShape(size / 3)
}

/** Spacing on a 4 dp grid (plan section 4): page sides 16, card padding 16, 12 between cards. */
object LagoonSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    val pageSide = 16.dp
    val cardPadding = 16.dp
    val cardGap = 12.dp
    /** Inset of a thumbnail inside a media card. */
    val cardMediaInset = 8.dp
    /** Smallest touch target (plan 10.5). */
    val touchTarget = 48.dp
}

/** Layout widths (plan section 4): below 600 dp, 600 to 840 dp, above 840 dp (tablet landscape). */
enum class WidthClass { Compact, Medium, Expanded;

    /** Columns for card grids: one on phones, two at medium width, three or four on tablets. */
    val cardColumns: Int get() = when (this) { Compact -> 1; Medium -> 2; Expanded -> 3 }

    companion object {
        fun of(widthDp: Int) = when {
            widthDp < 600 -> Compact
            widthDp < 840 -> Medium
            else -> Expanded
        }
    }
}

@Composable
@ReadOnlyComposable
fun currentWidthClass(): WidthClass = WidthClass.of(LocalConfiguration.current.screenWidthDp)
