package com.github.andreyasadchy.xtra.ui.design.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import com.github.andreyasadchy.xtra.ui.design.components.LocalGlassDecor
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle

val LocalLagoonColors = staticCompositionLocalOf { DarkLagoonColors }
val LocalLagoonType = staticCompositionLocalOf { DefaultLagoonType }
val LocalLagoonMotion = staticCompositionLocalOf { LagoonMotion(reduced = false) }

/** Access to the design tokens inside a [LagoonTheme]: `Lagoon.colors.card`, `Lagoon.type.body`, `Lagoon.motion.steady()`. */
object Lagoon {
    val colors: LagoonColors
        @Composable @ReadOnlyComposable get() = LocalLagoonColors.current
    val type: LagoonType
        @Composable @ReadOnlyComposable get() = LocalLagoonType.current
    val motion: LagoonMotion
        @Composable @ReadOnlyComposable get() = LocalLagoonMotion.current
}

/**
 * Root of every Compose screen. Provides the Lagoon tokens and maps them onto Material 3 so the few Material pieces
 * we style instead of rebuilding (bottom sheet, slider, switch) pick up the same colours and type.
 *
 * @param reducedMotion null reads the system "Remove animations" switch.
 */
@Composable
fun LagoonTheme(
    colors: LagoonColors = DarkLagoonColors,
    reducedMotion: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val reduced = reducedMotion ?: remember(context) { LagoonMotion.isReduced(context) }
    val motion = remember(reduced) { LagoonMotion(reduced) }
    val type = DefaultLagoonType
    val scheme = remember(colors) { lagoonColorScheme(colors) }
    val typography = remember(type) {
        Typography(
            displaySmall = type.display, headlineSmall = type.title, titleLarge = type.title,
            titleMedium = type.headline, titleSmall = type.bodyStrong, bodyLarge = type.body, bodyMedium = type.body,
            bodySmall = type.label, labelLarge = type.labelStrong, labelMedium = type.label, labelSmall = type.caption,
        )
    }
    // glass decorations from the third frame on (see LocalGlassDecor)
    val glassDecor = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        glassDecor.value = true
    }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(
            LocalGlassDecor provides glassDecor,
            LocalLagoonColors provides colors,
            LocalLagoonType provides type,
            LocalLagoonMotion provides motion,
            LocalContentColor provides colors.textPrimary,
            LocalIndication provides ripple(color = colors.textPrimary),
            content = content,
        )
    }
}

/**
 * Every Material 3 colour slot mapped to a Lagoon token (U9 A3), so a Material piece we did not restyle can never
 * fall back to Material's default purple. LagoonThemeContractTest checks each slot by reflection, so a slot added
 * by a future Material version fails the build until it is mapped here.
 */
fun lagoonColorScheme(colors: LagoonColors): ColorScheme = darkColorScheme(
    primary = colors.accent,
    onPrimary = colors.onAccent,
    primaryContainer = colors.accentSoft,
    onPrimaryContainer = colors.accent,
    inversePrimary = colors.accent,
    secondary = colors.accent,
    onSecondary = colors.onAccent,
    secondaryContainer = colors.accentSoft,
    onSecondaryContainer = colors.accent,
    tertiary = colors.accent,
    onTertiary = colors.onAccent,
    tertiaryContainer = colors.accentSoft,
    onTertiaryContainer = colors.accent,
    background = colors.page,
    onBackground = colors.textPrimary,
    surface = colors.card,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.control,
    onSurfaceVariant = colors.textSecondary,
    // Material's default tint is the primary colour; kept so tonal elevation looks exactly as before
    surfaceTint = colors.accent,
    inverseSurface = colors.textPrimary,
    inverseOnSurface = colors.page,
    error = colors.danger,
    onError = colors.onAccent,
    errorContainer = colors.dangerSoft,
    onErrorContainer = colors.danger,
    outline = colors.pressed,
    outlineVariant = colors.control,
    scrim = colors.scrim,
    surfaceBright = colors.pressed,
    surfaceContainer = colors.card,
    surfaceContainerHigh = colors.control,
    surfaceContainerHighest = colors.pressed,
    surfaceContainerLow = colors.card,
    surfaceContainerLowest = colors.page,
    surfaceDim = colors.page,
    primaryFixed = colors.accent,
    primaryFixedDim = colors.accent,
    onPrimaryFixed = colors.onAccent,
    onPrimaryFixedVariant = colors.onAccent,
    secondaryFixed = colors.accent,
    secondaryFixedDim = colors.accent,
    onSecondaryFixed = colors.onAccent,
    onSecondaryFixedVariant = colors.onAccent,
    tertiaryFixed = colors.accent,
    tertiaryFixedDim = colors.accent,
    onTertiaryFixed = colors.onAccent,
    onTertiaryFixedVariant = colors.onAccent,
)

/** Convenience for text that should take the current content colour. */
@Composable
@ReadOnlyComposable
fun TextStyle.withContent(): TextStyle = copy(color = LocalContentColor.current)
