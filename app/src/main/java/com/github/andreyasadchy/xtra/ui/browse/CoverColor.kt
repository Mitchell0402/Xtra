package com.github.andreyasadchy.xtra.ui.browse

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The main colour of a cover (plan 4, "封面取色"): the saturation-weighted mean of a 24 px copy of the image, moved
 * to a dark, muted tone so the header gradient stays quiet behind white text. Null until the image is loaded, and
 * for anything that is not a URL (tests draw fixed pictures).
 */
@Composable
fun rememberCoverColor(model: Any?): Color? {
    val context = LocalContext.current
    var color by remember(model) { mutableStateOf<Color?>(null) }
    LaunchedEffect(model) {
        val url = model as? String ?: return@LaunchedEffect
        val result = context.imageLoader.execute(
            // never through the memory cache: a 24 px copy stored under the same key would replace the full
            // picture there, and the poster would reload (and blink) on the way back from the game page
            ImageRequest.Builder(context).data(url).size(Size(24, 24)).allowHardware(false)
                .memoryCachePolicy(CachePolicy.DISABLED).build()
        )
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect
        color = withContext(Dispatchers.Default) { coverTone(bitmap) }
    }
    return color
}

/** The mean colour of [bitmap], weighted towards saturated, mid-light pixels, as a dark muted tone. */
fun coverTone(bitmap: Bitmap): Color? {
    val small = if (bitmap.width > 24 || bitmap.height > 24) Bitmap.createScaledBitmap(bitmap, 24, 24, true) else bitmap
    val pixels = IntArray(small.width * small.height)
    small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var total = 0.0
    val hsl = FloatArray(3)
    for (pixel in pixels) {
        if (android.graphics.Color.alpha(pixel) < 128) continue
        ColorUtils.colorToHSL(pixel, hsl)
        // grey and near-black/near-white pixels say little about the cover's colour
        val weight = 0.05 + hsl[1] * (1 - kotlin.math.abs(hsl[2] - 0.5) * 2)
        r += android.graphics.Color.red(pixel) * weight
        g += android.graphics.Color.green(pixel) * weight
        b += android.graphics.Color.blue(pixel) * weight
        total += weight
    }
    if (total == 0.0) return null
    val mean = android.graphics.Color.rgb((r / total).toInt(), (g / total).toInt(), (b / total).toInt())
    ColorUtils.colorToHSL(mean, hsl)
    hsl[1] = hsl[1].coerceAtMost(0.55f)
    hsl[2] = 0.30f
    return Color(ColorUtils.HSLToColor(hsl))
}
