package com.github.andreyasadchy.xtra.lab.showcase

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/**
 * Offline stand-in for thumbnails and box art: the abstract shapes of the finalized mock-ups (soft circle on a tinted
 * ground). Deterministic, so screenshot tests always draw the same picture.
 */
class CoverPainter(private val base: Color, private val shape: Color, private val floor: Color) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRect(base)
        drawCircle(shape, radius = size.minDimension * 0.55f, center = Offset(size.width * 0.68f, size.height * 0.12f))
        drawRect(floor, topLeft = Offset(0f, size.height * 0.64f), size = Size(size.width, size.height * 0.36f))
    }
}

private val covers = listOf(
    CoverPainter(Color(0xFF1E1B2E), Color(0xFF3B3366), Color(0xFF2A2545)),
    CoverPainter(Color(0xFF13262A), Color(0xFF1F4A4F), Color(0xFF183338)),
    CoverPainter(Color(0xFF1A2033), Color(0xFF34406A), Color(0xFF232B45)),
    CoverPainter(Color(0xFF2B1A2A), Color(0xFF5C2E5A), Color(0xFF3A2238)),
    CoverPainter(Color(0xFF341A1C), Color(0xFF6B2F33), Color(0xFF45222A)),
    CoverPainter(Color(0xFF2E2716), Color(0xFF5E4F2A), Color(0xFF3B321D)),
)

fun cover(i: Int): Painter = covers[Math.floorMod(i, covers.size)]

data class SampleStream(val channel: String, val title: String, val game: String, val viewers: String, val uptime: String, val tags: List<String>)
data class SampleVideo(val title: String, val meta: String, val duration: String, val progress: Float?)
data class SampleGame(val name: String, val viewers: String)
data class SampleChannel(val name: String, val subtitle: String, val live: Boolean)

/** Sample content in the user's language mix: Chinese and English titles, plus very long ones (plan 10.2). */
object Samples {
    const val LONG_ZH = "超长标题测试：今晚通宵冲击王者段位，不上分不下播，顺便和弹幕一起聊聊新赛季的版本改动和英雄强度排行"
    const val LONG_EN = "Extremely long title test: all-night ranked grind to Grandmaster with viewer games, patch notes review and giveaways"

    val streams = listOf(
        SampleStream("夜航Nightowl", "排位冲大师，今天不上分不下播", "英雄联盟", "12,408", "3:42:10", listOf("中文", "排位")),
        SampleStream("pixelmoth", "Night build day 42: new map, new base", "Minecraft", "3,216", "1:05:33", listOf("English", "Chill")),
        SampleStream("Mika_cn", "五排上分，今晚开抽奖", "无畏契约", "2,904", "2:11:47", emptyList()),
        SampleStream("LongTitle", LONG_ZH, "只是聊天", "988", "0:12:05", listOf("中文", "聊天", "通宵")),
        SampleStream("longtitle_en", LONG_EN, "Just Chatting With A Very Long Category Name", "45", "0:03:59", listOf("English")),
    )

    val videos = listOf(
        SampleVideo("昨晚冲分全程，最后一把翻盘", "昨天 · 3.1 万次观看", "4:12:30", 0.62f),
        SampleVideo("New patch, every champion tested", "3 days ago · 24K views", "3:05:12", null),
        SampleVideo("周末水友赛", "5 天前 · 1.8 万次观看", "5:40:02", 1f),
        SampleVideo(LONG_EN, "1 week ago · 3,960 views", "0:15", null),
    )

    val games = listOf(
        SampleGame("只是聊天", "31.2 万"), SampleGame("League of Legends", "184K"), SampleGame("无畏契约", "9.6 万"),
        SampleGame("Minecraft", "71K"), SampleGame("反恐精英 2", "6.8 万"), SampleGame("Grand Theft Auto V with a long name", "62K"),
    )

    val channels = listOf(
        SampleChannel("夜航Nightowl", "正在播 英雄联盟", true),
        SampleChannel("Sora", "上次直播 昨天", false),
        SampleChannel("一个名字特别特别长的频道名称用来测试截断效果", "Last live 1 week ago, with a very long subtitle line", false),
    )

    /** Real Twitch box art, for showing the image fade-in on a device. */
    val boxArtUrls = listOf(
        "https://static-cdn.jtvnw.net/ttv-boxart/509658-285x380.jpg",
        "https://static-cdn.jtvnw.net/ttv-boxart/21779-285x380.jpg",
        "https://static-cdn.jtvnw.net/ttv-boxart/27471_IGDB-285x380.jpg",
    )
}
