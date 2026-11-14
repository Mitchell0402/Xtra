package com.github.andreyasadchy.xtra.lab.showcase

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipItem
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipRow
import com.github.andreyasadchy.xtra.ui.design.components.FloatingNavBar
import com.github.andreyasadchy.xtra.ui.design.components.GlassBackdrop
import com.github.andreyasadchy.xtra.ui.design.components.GlassContext
import com.github.andreyasadchy.xtra.ui.design.components.GlassSurface
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LocalGlass
import com.github.andreyasadchy.xtra.ui.design.components.lagoonHazeState
import com.github.andreyasadchy.xtra.ui.design.components.SegmentedControl
import com.github.andreyasadchy.xtra.ui.design.components.Toast
import com.github.andreyasadchy.xtra.ui.design.components.ToastData
import com.github.andreyasadchy.xtra.ui.design.components.LiveCard
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.GlassLevel
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes

/** Backdrops to judge the glass against: busy colour, the worst case (white) and the plain page. */
private enum class GlassBackground(val label: String) { Colour("彩色"), White("白色"), Page("页面") }

/**
 * Stage G's glass sheet: every glass grade over a switchable backdrop, real blur next to the solid fallback. In the
 * showcase the left box blurs when the system allows it; screenshot tests draw both solid (no blur in Robolectric).
 */
@Composable
fun GlassSpecimen() {
    var background by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterChipRow(
            GlassBackground.entries.mapIndexed { i, b -> FilterChipItem(b.label, selected = i == background, onClick = { background = i }) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        )
        SpecimenGrid {
            Sample("真模糊 Blur (when the system allows it)") { GlassStage(GlassBackground.entries[background]) }
            Sample("回退 Solid fallback") {
                CompositionLocalProvider(LocalGlass provides GlassContext(LocalGlass.current.state, blur = false)) {
                    GlassStage(GlassBackground.entries[background])
                }
            }
            Sample("卡片保持实心 Cards stay solid (glass is for what can be tapped)") {
                val s = Samples.streams
                LiveCard(s[0].title, s[0].channel, s[0].viewers, {}, thumbnail = cover(0), gameName = s[0].game, uptime = s[0].uptime)
            }
            Sample("厚档 Thick: sheets, menus") {
                GlassSurface(GlassLevel.Thick, LagoonShapes.card, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("画质 Quality", style = Lagoon.type.headline, color = Lagoon.colors.textPrimary)
                        Text("Sheets keep the card colour at 94 %, grain and the top highlight; never blurred.", style = Lagoon.type.label, color = Lagoon.colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassStage(background: GlassBackground) {
    var segment by remember { mutableIntStateOf(1) }
    var nav by remember { mutableIntStateOf(0) }
    // its own backdrop state: this stage sits inside the showcase page, which is itself a backdrop
    val parent = LocalGlass.current
    val state = remember { lagoonHazeState() }
    Box(Modifier.fillMaxWidth().height(440.dp).clip(LagoonShapes.card)) {
      CompositionLocalProvider(LocalGlass provides GlassContext(state, parent.blur)) {
        GlassBackdrop(Modifier.fillMaxSize()) { BackdropPattern(background) }
        Column(
            Modifier.fillMaxSize().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
                LagoonIconButton(LucideIcons.Search, "Search", {})
                LagoonIconButton(LucideIcons.User, "Account", {})
            }
            SegmentedControl(listOf("直播", "视频", "频道", "游戏"), segment, { segment = it }, Modifier.padding(horizontal = 12.dp), glass = true)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LagoonButton("分享 Share", {}, style = ButtonStyle.Secondary, icon = LucideIcons.Share2)
                LagoonButton("观看", {})
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Toast(ToastData("已取消关注 Nightowl", actionLabel = "撤销"))
            }
            FloatingNavBar(sampleNavItems, nav, { nav = it }, Modifier.padding(horizontal = 0.dp))
        }
      }
    }
}

/** A fixed, busy picture behind the glass: colour blocks, a white bar and lines of "text". */
@Composable
private fun BackdropPattern(background: GlassBackground) {
    Canvas(Modifier.fillMaxSize()) {
        when (background) {
            GlassBackground.White -> drawRect(Color.White)
            GlassBackground.Page -> Unit
            GlassBackground.Colour -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFF1E3A5F), Color(0xFF6B2D5C), Color(0xFFB8572A))))
                val blocks = listOf(Color(0xFF5EE2C4), Color(0xFFFFC857), Color(0xFFFF6B6E), Color.White, Color(0xFF7A5CFF))
                val w = size.width
                for (i in 0 until 14) {
                    val x = (i * 0.37f % 1f) * w
                    val y = i * size.height / 14f
                    drawRoundRect(blocks[i % blocks.size], Offset(x - w * 0.15f, y), Size(w * 0.45f, size.height / 18f), CornerRadius(24f))
                }
                for (i in 0 until 30) {
                    drawRect(Color.White.copy(alpha = 0.55f), Offset(w * 0.08f, i * size.height / 30f), Size(w * (0.3f + (i % 5) * 0.12f), 6f))
                }
            }
        }
    }
}
