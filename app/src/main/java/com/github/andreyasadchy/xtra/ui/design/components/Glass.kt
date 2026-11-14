package com.github.andreyasadchy.xtra.ui.design.components

import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.theme.GlassLevel
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonGlass
import com.github.andreyasadchy.xtra.ui.design.theme.LocalLagoonColors
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

/**
 * Where glass in this composition takes its backdrop from, and whether it may blur right now (glass plan section 6).
 *
 * [blur] is decided outside the components: the shell works it out from the system (API 32+, window blur on, no
 * battery saver, ...) and the "透明效果" setting; tests pass false. Production code never asks whether it runs in a test.
 */
@Immutable
class GlassContext(val state: HazeState?, val blur: Boolean) {
    val canBlur: Boolean get() = blur && state != null

    companion object {
        val Solid = GlassContext(null, blur = false)
    }
}

val LocalGlass = staticCompositionLocalOf { GlassContext.Solid }

/** Marks a surface that draws real blur, for the blur-area test (glass plan section 7). */
val GlassBlurKey = SemanticsPropertyKey<Boolean>("GlassBlur")
private var SemanticsPropertyReceiver.glassBlur by GlassBlurKey

/** Whether glass in this window may blur its floating layers: never in windows lower than 480 dp. */
@Composable
fun glassWindowAllowsBlur(): Boolean = LocalConfiguration.current.screenHeightDp >= LagoonGlass.SHORT_WINDOW_DP

/** Saturation up, brightness down: what the blurred backdrop goes through before the tint. */
private fun backdropMatrix() = ColorMatrix().apply {
    setToSaturation(LagoonGlass.saturation)
    timesAssign(ColorMatrix().apply { setToScale(LagoonGlass.backdropDim, LagoonGlass.backdropDim, LagoonGlass.backdropDim, 1f) })
}

/**
 * A glass surface: the navigation, segment tabs, floating buttons, toasts, sheets (glass plan section 9).
 *
 * With [LocalGlass] allowing blur and [blur] true, a grade that blurs samples the page behind through Haze. Otherwise
 * no Haze node is attached at all and the surface draws the same material itself: the tint at the solid opacity, the
 * bundled grain and the top highlight. On the floating grades the secondary and hint text colours inside become the
 * glass secondary colour, so content cannot pick a level that fails contrast over a white backdrop.
 */
@Composable
fun GlassSurface(
    level: GlassLevel,
    shape: Shape,
    modifier: Modifier = Modifier,
    blur: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Lagoon.colors
    val glass = LocalGlass.current
    val base = level.base(colors)
    val blurring = blur && level.blurs && glass.canBlur
    val surface = if (blurring) {
        val tint = base.copy(alpha = level.blurAlpha)
        val style = remember(level, tint) {
            HazeBlurStyle {
                blurRadius(LagoonGlass.blurRadius)
                noiseFactor(LagoonGlass.hazeNoise)
                backgroundColor(base)
                colorEffects(
                    listOf(
                        HazeColorEffect.colorFilter(ColorFilter.colorMatrix(backdropMatrix())),
                        HazeColorEffect.tint(tint),
                    )
                )
            }
        }
        Modifier
            .clip(shape)
            .hazeBlur(HazeInput.Sources(glass.state!!), style, HazePerformanceMode.Balanced)
            .semantics { glassBlur = true }
    } else {
        Modifier.glassSolid(level, shape, highlight = false)
    }
    Box(modifier.then(surface).glassHighlight(shape)) {
        if (level.glassText) {
            val glassColors = remember(colors) { colors.copy(textSecondary = LagoonGlass.textSecondary, textHint = LagoonGlass.textSecondary) }
            CompositionLocalProvider(LocalLagoonColors provides glassColors) { content() }
        } else {
            content()
        }
    }
}

/**
 * The backdrop glass samples: wraps a Compose page's own content, once per page root (glass plan section 6), and
 * draws the page background with its faint teal ambient light. The floating layers (top bar, segment tabs, toasts)
 * go next to it in the same root, never inside, or the glass would sample itself. Never put an old page, a
 * SurfaceView, a PlayerSurface or a player's AndroidView inside (rule from glass plan section 6).
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = Lagoon.colors
    val glass = LocalGlass.current
    Box(
        modifier
            .then(if (glass.canBlur) Modifier.hazeSource(glass.state!!) else Modifier)
            .drawWithCache {
                val glow = Brush.radialGradient(
                    listOf(LagoonGlass.ambient, LagoonGlass.ambient.copy(alpha = 0f)),
                    center = Offset(size.width * 0.3f, 0f),
                    radius = maxOf(size.width, 1f) * 0.9f,
                )
                onDrawBehind {
                    drawRect(colors.page)
                    drawRect(glow)
                }
            },
    ) {
        // decision 4a: nothing under real glass moves on its own (see LocalLiveDotBreathing)
        CompositionLocalProvider(LocalLiveDotBreathing provides !glass.canBlur) { content() }
    }
}

/**
 * The glass material without blur, as a modifier for things that are not a [GlassSurface] (buttons): the tint at the
 * solid opacity, the grain and the top highlight. Text colours inside are left alone.
 */
@Composable
fun Modifier.glassSolid(level: GlassLevel, shape: Shape, highlight: Boolean = true): Modifier {
    val tint = level.base(Lagoon.colors).copy(alpha = level.solidAlpha)
    return clip(shape)
        .drawBehind { drawRect(tint) }
        .glassNoise()
        .then(if (highlight) Modifier.glassHighlight(shape) else Modifier)
}

/** The fixed grain, tiled at [LagoonGlass.noisePixel] per grain pixel. */
@Composable
fun Modifier.glassNoise(alpha: Float = LagoonGlass.noiseAlpha): Modifier {
    val brush = rememberNoiseBrush(LocalDensity.current)
    return drawBehind { drawRect(brush, alpha = alpha) }
}

@Composable
private fun rememberNoiseBrush(density: Density): ShaderBrush {
    val resources = LocalResources.current
    return remember(resources, density) {
        val bitmap = noiseBitmap ?: BitmapFactory.decodeResource(resources, R.drawable.glass_noise, BitmapFactory.Options().apply { inScaled = false })
            .also { noiseBitmap = it }
        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val scale = with(density) { LagoonGlass.noisePixel.toPx() }
        shader.setLocalMatrix(android.graphics.Matrix().apply { setScale(scale, scale) })
        ShaderBrush(shader)
    }
}

private var noiseBitmap: android.graphics.Bitmap? = null

/** The thin highlight along the top edge, following the shape's corners and fading out by half the height. */
fun Modifier.glassHighlight(shape: Shape): Modifier = drawWithCache {
    val width = LagoonGlass.highlightWidth.toPx()
    val inset = width / 2f
    val outline = shape.createOutline(Size(size.width - width, size.height - width), layoutDirection, this)
    val brush = Brush.verticalGradient(
        0f to LagoonGlass.highlight,
        0.5f to LagoonGlass.highlight.copy(alpha = 0f),
        startY = 0f,
        endY = size.height,
    )
    onDrawWithContent {
        drawContent()
        translate(inset, inset) { drawOutline(outline, brush, style = Stroke(width)) }
    }
}
