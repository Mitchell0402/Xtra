package com.github.andreyasadchy.xtra.ui.design.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
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
    val scheme = remember(colors) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.accent,
            secondary = colors.accent,
            onSecondary = colors.onAccent,
            background = colors.page,
            onBackground = colors.textPrimary,
            surface = colors.card,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.control,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = colors.page,
            surfaceContainerLow = colors.card,
            surfaceContainer = colors.card,
            surfaceContainerHigh = colors.control,
            surfaceContainerHighest = colors.pressed,
            outline = colors.pressed,
            outlineVariant = colors.control,
            error = colors.danger,
            onError = colors.onAccent,
            scrim = colors.scrim,
        )
    }
    val typography = remember(type) {
        Typography(
            displaySmall = type.display, headlineSmall = type.title, titleLarge = type.title,
            titleMedium = type.headline, titleSmall = type.bodyStrong, bodyLarge = type.body, bodyMedium = type.body,
            bodySmall = type.label, labelLarge = type.labelStrong, labelMedium = type.label, labelSmall = type.caption,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(
            LocalLagoonColors provides colors,
            LocalLagoonType provides type,
            LocalLagoonMotion provides motion,
            LocalContentColor provides colors.textPrimary,
            LocalIndication provides ripple(color = colors.textPrimary),
            content = content,
        )
    }
}

/** Convenience for text that should take the current content colour. */
@Composable
@ReadOnlyComposable
fun TextStyle.withContent(): TextStyle = copy(color = LocalContentColor.current)
