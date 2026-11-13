package com.github.andreyasadchy.xtra.ui.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.github.andreyasadchy.xtra.R

/**
 * Plus Jakarta Sans (bundled, Latin subset, OFL) for Latin text and digits. Chinese has no glyphs in it, so Android
 * falls back to the system CJK font (Noto Sans CJK on most phones) per character, which is what plan section 12
 * decided: no bundled Chinese font.
 */
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

/** The six sizes the app uses (plan section 4: 28, 22, 17, 15, 13, 11). Nothing else is allowed. */
@Immutable
data class LagoonType(
    /** 28: large page titles. */
    val display: TextStyle,
    /** 22: channel names, sheet titles. */
    val title: TextStyle,
    /** 17: collapsed top bar title, section headers. */
    val headline: TextStyle,
    /** 15: list titles and body text. */
    val body: TextStyle,
    /** 15 at medium weight: row titles, buttons. */
    val bodyStrong: TextStyle,
    /** 13: secondary lines, chips, group labels. */
    val label: TextStyle,
    /** 13 at semibold: chip and button labels. */
    val labelStrong: TextStyle,
    /** 11: badges on thumbnails, captions. */
    val caption: TextStyle,
)

private val base = TextStyle(
    fontFamily = PlusJakartaSans,
    letterSpacing = 0.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

// line heights leave room for Chinese, whose glyphs sit taller than Latin ones
val DefaultLagoonType = LagoonType(
    display = base.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    title = base.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    headline = base.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    body = base.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyStrong = base.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    label = base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelStrong = base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    caption = base.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
)
