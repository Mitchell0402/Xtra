package com.github.andreyasadchy.xtra.ui.chat

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.decode.BitmapFactoryDecoder
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.model.chat.NamePaint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Width-to-height ratios of chat pictures, learned when each one first loads. Placeholders start square and widen once
 * (7TV has many wide emotes); every line that shows the same picture reads the ratio from here.
 */
object ChatImageRatios {
    private val ratios = mutableStateMapOf<String, Float>()

    fun key(model: Any): String = if (model is ByteArray) "b" + System.identityHashCode(model) else model.toString()

    fun of(model: Any): Float = ratios[key(model)] ?: 1f

    fun learn(model: Any, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val ratio = width.toFloat() / height
        val key = key(model)
        if (ratios[key] != ratio) ratios[key] = ratio
    }
}

/** Sizes in dp for the three kinds of chat pictures. */
fun ChatStyle.heightOf(kind: ChatImageKind): Float = when (kind) {
    ChatImageKind.Badge -> badgeSize
    ChatImageKind.Emote -> emoteSize
    ChatImageKind.Gif -> gifSize
}

/**
 * The placeholders for one line. The width follows the learned ratio of the picture (the widest layer for stacked
 * zero-width emotes, like v0.1's LayerDrawable).
 */
fun inlineContentFor(images: List<ChatImage>, style: ChatStyle, density: Density, onClick: ((ChatImage) -> Unit)?): Map<String, InlineTextContent> {
    if (images.isEmpty()) return emptyMap()
    return images.associate { image ->
        val height = style.heightOf(image.kind)
        val ratio = maxOf(ChatImageRatios.of(image.model), image.overlays.maxOfOrNull { ChatImageRatios.of(it.model) } ?: 0f)
        val placeholder = with(density) { Placeholder((height * ratio).dp.toSp(), height.dp.toSp(), PlaceholderVerticalAlign.TextCenter) }
        image.id to InlineTextContent(placeholder) { ChatPicture(image, style.animated, onClick) }
    }
}

@Composable
fun ChatPicture(image: ChatImage, animate: Boolean, onClick: ((ChatImage) -> Unit)?, modifier: Modifier = Modifier.fillMaxSize()) {
    val clickModifier = if (onClick != null && image.info != null) Modifier.chatPictureClick { onClick(image) } else Modifier
    Box(modifier.then(clickModifier)) {
        ChatPictureLayer(image, animate, Modifier.fillMaxSize())
        image.overlays.forEach { ChatPictureLayer(it, animate, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun ChatPictureLayer(image: ChatImage, animate: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(image.model, animate) { chatImageRequest(context, image.model, image.thirdParty, animate && image.animated) }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
        onSuccess = { ChatImageRatios.learn(image.model, it.painter.intrinsicSize.width.toInt(), it.painter.intrinsicSize.height.toInt()) },
    )
}

/** The request v0.1 made: the chosen URL or saved bytes, a User-Agent for third-party hosts, only the first frame when animations are off. */
fun chatImageRequest(context: android.content.Context, model: Any, thirdParty: Boolean, animate: Boolean): ImageRequest =
    ImageRequest.Builder(context).apply {
        data(model)
        if (thirdParty) httpHeaders(NetworkHeaders.Builder().add("User-Agent", "Xtra/" + BuildConfig.VERSION_NAME).build())
        if (!animate) {
            decoderFactory(BitmapFactoryDecoder.Factory())
            memoryCacheKey(ChatImageRatios.key(model) + "#still")
        }
    }.build()

private fun Modifier.chatPictureClick(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(interactionSource = null, indication = null, onClick = onClick))

/**
 * 7TV name paints (v0.1 NamePaintSpan and NamePaintImageSpan): gradients repeat across the name, picture paints fill
 * the letters with the first frame of the picture, the first shadow is kept.
 */
object NamePaints {
    fun drawable(paint: NamePaint): Boolean = when (paint.type) {
        "LINEAR_GRADIENT", "RADIAL_GRADIENT" -> paint.colors != null && paint.colorPositions != null && paint.colors.size >= 2
        "URL" -> !paint.imageUrl.isNullOrBlank()
        else -> false
    }

    fun shadow(paint: NamePaint): Shadow? = paint.shadows?.firstOrNull()?.let { Shadow(Color(it.color), Offset(it.xOffset, it.yOffset), it.radius) }

    /** [nameWidthPx] is an estimate of the drawn name, the length the gradient spans before it repeats. */
    fun gradient(paint: NamePaint, nameWidthPx: Float): Brush? {
        val colors = paint.colors ?: return null
        val positions = paint.colorPositions ?: return null
        if (colors.size < 2 || positions.size != colors.size) return null
        return GradientBrush(colors.map { Color(it) }, positions.toList(), paint.angle ?: 90, nameWidthPx.coerceAtLeast(1f), paint.repeat == true)
    }

    private class GradientBrush(val colors: List<Color>, val stops: List<Float>, val angle: Int, val length: Float, val repeat: Boolean) : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            // CSS angles: 0 points up, 90 to the right
            val rad = (angle - 90) * PI / 180.0
            val dx = (cos(rad) * length).toFloat()
            val dy = (sin(rad) * length).toFloat()
            return LinearGradientShader(Offset.Zero, Offset(dx, dy), colors, stops, if (repeat) TileMode.Repeated else TileMode.Mirror)
        }

        override fun equals(other: Any?) = other is GradientBrush && other.colors == colors && other.stops == stops && other.angle == angle && other.length == length && other.repeat == repeat
        override fun hashCode() = colors.hashCode() * 31 + angle
    }

    private val imageShaders = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 32
    }

    /** The paint of a line, applied over the name's colour. Picture paints load once and then repaint the line. */
    @Composable
    fun apply(line: ChatLine, style: ChatStyle): AnnotatedString {
        val paint = line.namePaint ?: return line.text
        if (line.nameStart < 0 || line.nameEnd > line.text.length) return line.text
        val density = LocalDensity.current
        val textPx = with(density) { style.textSize.sp.toPx() }
        val nameWidth = (line.nameEnd - line.nameStart) * textPx * 0.56f
        val shadow = shadow(paint)
        if (paint.type == "URL") {
            val url = paint.imageUrl ?: return line.text
            val context = LocalContext.current
            var bitmap by remember(url) { mutableStateOf(synchronized(imageShaders) { imageShaders[url] }) }
            LaunchedEffect(url) {
                if (bitmap == null) {
                    val result = context.imageLoader.execute(
                        ImageRequest.Builder(context).data(url).allowHardware(false).decoderFactory(BitmapFactoryDecoder.Factory())
                            .httpHeaders(NetworkHeaders.Builder().add("User-Agent", "Xtra/" + BuildConfig.VERSION_NAME).build()).build()
                    )
                    (result as? SuccessResult)?.image?.toBitmap()?.let { b ->
                        synchronized(imageShaders) { imageShaders[url] = b }
                        bitmap = b
                    }
                }
            }
            val b = bitmap ?: return line.text
            return remember(line, b) {
                val brush = ShaderBrush(ImageShader(b.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
                AnnotatedString.Builder(line.text).apply { addStyle(SpanStyle(brush = brush, shadow = shadow), line.nameStart, line.nameEnd) }.toAnnotatedString()
            }
        }
        return remember(line, nameWidth) {
            val brush = gradient(paint, nameWidth) ?: return@remember line.text
            AnnotatedString.Builder(line.text).apply { addStyle(SpanStyle(brush = brush, shadow = shadow), line.nameStart, line.nameEnd) }.toAnnotatedString()
        }
    }
}
