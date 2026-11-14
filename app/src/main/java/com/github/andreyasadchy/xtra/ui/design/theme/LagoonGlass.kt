package com.github.andreyasadchy.xtra.ui.design.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The frosted glass material (glass plan sections 3 and 9). Glass is a dark tint over a heavily blurred backdrop, with
 * fine grain and a thin highlight along the top edge. It stays on the floating, interactive layers only: the
 * navigation, the segment tabs, buttons that float over content, toasts; sheets use the solid "thick" grade.
 */
enum class GlassLevel(
    /** The layer colour the surface had before it became glass, so the layers keep their order. */
    val base: (LagoonColors) -> Color,
    /** Tint over the blurred backdrop. */
    val blurAlpha: Float,
    /** Tint when nothing is blurred (fallback): the material without the blur. */
    val solidAlpha: Float,
    /** Whether this grade may blur at all. */
    val blurs: Boolean,
    /** Text on it uses the glass secondary colour instead of the 62 % and 50 % levels (plan 8). */
    val glassText: Boolean,
) {
    /** Floating controls: navigation pill and rail, segment tabs, floating buttons, toasts. */
    Float({ it.control }, blurAlpha = 0.72f, solidAlpha = 0.94f, blurs = true, glassText = true),

    /** A bar across the page (kept for U6/U7 bars; G uses it nowhere by default). */
    Bar({ it.page }, blurAlpha = 0.75f, solidAlpha = 0.94f, blurs = true, glassText = true),

    /** Sheets, menus and dialogs: their own window over a 60 % scrim, never blurred. */
    Thick({ it.card }, blurAlpha = 0.94f, solidAlpha = 0.94f, blurs = false, glassText = false),
}

object LagoonGlass {
    val blurRadius: Dp = 24.dp

    /**
     * Saturation of the blurred backdrop. Raised from the plan's 1.2 to 1.5 together with [backdropDim] and the float
     * tint at 72 % (stage G, after Mitchell asked for clearly visible glass on the interactive parts): more of the
     * colour behind shows, while white behind the glass still dims to a grey that keeps every text colour above 4.5:1
     * (DesignTokensTest.glassTextReachesContrastOverWhite).
     */
    const val saturation = 1.5f

    /** The blurred backdrop is dimmed to this brightness before the tint, so a white backdrop cannot wash text out. */
    const val backdropDim = 0.8f

    /** Haze's own grain on blurred glass; tuned in the showcase to match [noiseAlpha] on the fallback. */
    const val hazeNoise = 0.08f

    /** Opacity of the bundled grain (res/drawable-nodpi/glass_noise.png) on solid glass. */
    const val noiseAlpha = 0.045f

    /** Size of one grain pixel. */
    val noisePixel: Dp = 0.5.dp

    /** Top edge highlight: 1 dp of white fading to nothing by half the height. */
    val highlightWidth: Dp = 1.dp
    val highlight = Color.White.copy(alpha = 0.10f)

    /** Secondary text and icons on glass (plan 8): 78 % white, so nothing on glass falls below 4.5:1. */
    val textSecondary = Color.White.copy(alpha = 0.78f)

    /** Selected segment on glass: a lighter lens rather than a second teal block next to the navigation's. */
    val segmentSelected = Color.White.copy(alpha = 0.14f)

    /** Ambient light at the top of list pages: a faint teal glow. */
    val ambient = Color(0xFF5EE2C4).copy(alpha = 0.06f)

    /** Windows lower than this (most phones in landscape) never blur their floating layers (plan 7). */
    const val SHORT_WINDOW_DP = 480
}
