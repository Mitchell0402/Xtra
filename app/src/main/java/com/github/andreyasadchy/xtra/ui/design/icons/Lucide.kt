package com.github.andreyasadchy.xtra.ui.design.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Stroke width of every icon in the app (plan section 4: rounded line icons at 1.75). */
const val LucideStrokeWidth = 1.75f

/**
 * Builds a 24 x 24 Lucide icon. Paths are black and tinted by `Icon`; [fillMask] has one '1' per path that is
 * filled in the selected-state variant (null = outline only).
 */
internal fun lucide(name: String, fillMask: String?, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            paths.forEachIndexed { i, d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = if (fillMask?.getOrNull(i) == '1') SolidColor(Color.Black) else null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = LucideStrokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()
