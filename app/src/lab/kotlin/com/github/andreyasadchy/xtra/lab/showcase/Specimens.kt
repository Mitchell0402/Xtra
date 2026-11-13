package com.github.andreyasadchy.xtra.lab.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.ui.design.components.ActionItem
import com.github.andreyasadchy.xtra.ui.design.components.ActionMenu
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle
import com.github.andreyasadchy.xtra.ui.design.components.ChannelRow
import com.github.andreyasadchy.xtra.ui.design.components.ChoiceOption
import com.github.andreyasadchy.xtra.ui.design.components.EmptyState
import com.github.andreyasadchy.xtra.ui.design.components.ErrorState
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipItem
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipRow
import com.github.andreyasadchy.xtra.ui.design.components.FloatingNavBar
import com.github.andreyasadchy.xtra.ui.design.components.FloatingNavRail
import com.github.andreyasadchy.xtra.ui.design.components.FollowButton
import com.github.andreyasadchy.xtra.ui.design.components.GamePoster
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonSlider
import com.github.andreyasadchy.xtra.ui.design.components.LagoonSwitch
import com.github.andreyasadchy.xtra.ui.design.components.LagoonTextField
import com.github.andreyasadchy.xtra.ui.design.components.LargeTopBar
import com.github.andreyasadchy.xtra.ui.design.components.LiveCard
import com.github.andreyasadchy.xtra.ui.design.components.LiveCardCompact
import com.github.andreyasadchy.xtra.ui.design.components.MultiChoiceList
import com.github.andreyasadchy.xtra.ui.design.components.NavItem
import com.github.andreyasadchy.xtra.ui.design.components.SegmentedControl
import com.github.andreyasadchy.xtra.ui.design.components.SettingDependents
import com.github.andreyasadchy.xtra.ui.design.components.SettingRow
import com.github.andreyasadchy.xtra.ui.design.components.SettingTrailing
import com.github.andreyasadchy.xtra.ui.design.components.SettingsGroup
import com.github.andreyasadchy.xtra.ui.design.components.SheetSurface
import com.github.andreyasadchy.xtra.ui.design.components.SingleChoiceList
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonLiveCard
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonRow
import com.github.andreyasadchy.xtra.ui.design.components.Toast
import com.github.andreyasadchy.xtra.ui.design.components.ToastData
import com.github.andreyasadchy.xtra.ui.design.components.VideoCard
import com.github.andreyasadchy.xtra.ui.design.components.rememberTopBarState
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.design.theme.currentWidthClass

/*
 * One "specimen sheet" per component (plan section 6): every state side by side. The showcase page shows them and the
 * screenshot tests capture them in the three form factors (plan 10.2).
 */

/** The 14 components of plan section 6, in its order. */
enum class Specimen(val title: String) {
    LiveCards("直播卡片 Live card"),
    VideoCards("视频卡片 Video card"),
    GamePosters("游戏海报 Game poster"),
    ChannelRows("频道行 Channel row"),
    SettingRows("设置行 Setting row"),
    SettingsGroups("分组卡片 Settings group"),
    BottomSheets("底部弹层 Bottom sheet"),
    ActionMenus("操作菜单 Action menu"),
    Pickers("选择器 Pickers"),
    FilterChips("筛选标签条 Filter chips"),
    Navigation("导航 Navigation"),
    States("状态页 States"),
    Toasts("提示条 Toast"),
    Controls("按钮与开关 Buttons and switches"),
}

@Composable
fun SpecimenContent(specimen: Specimen) {
    when (specimen) {
        Specimen.LiveCards -> LiveCardSpecimen()
        Specimen.VideoCards -> VideoCardSpecimen()
        Specimen.GamePosters -> GamePosterSpecimen()
        Specimen.ChannelRows -> ChannelRowSpecimen()
        Specimen.SettingRows -> SettingRowSpecimen()
        Specimen.SettingsGroups -> SettingsGroupSpecimen()
        Specimen.BottomSheets -> BottomSheetSpecimen()
        Specimen.ActionMenus -> ActionMenuSpecimen()
        Specimen.Pickers -> PickerSpecimen()
        Specimen.FilterChips -> FilterChipSpecimen()
        Specimen.Navigation -> NavigationSpecimen()
        Specimen.States -> StatesSpecimen()
        Specimen.Toasts -> ToastSpecimen()
        Specimen.Controls -> ControlsSpecimen()
    }
}

/** A specimen page: page background, title, and its samples in one to three columns depending on width. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecimenPage(specimen: Specimen, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Lagoon.colors.page)
            .padding(LagoonSpacing.pageSide)
    ) {
        Text(specimen.title, style = Lagoon.type.title, color = Lagoon.colors.textPrimary)
        Box(Modifier.height(12.dp))
        SpecimenContent(specimen)
    }
}

/** Lays samples out in as many columns as the width class allows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecimenGrid(columns: Int = currentWidthClass().cardColumns, content: @Composable SpecimenScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = LagoonSpacing.cardGap
        val itemWidth = (maxWidth - gap * (columns - 1)) / columns
        FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SpecimenScope(Modifier.width(itemWidth)).content()
        }
    }
}

class SpecimenScope(private val itemModifier: Modifier) {
    @Composable
    fun Sample(label: String, content: @Composable () -> Unit) {
        Column(itemModifier) {
            Text(label, style = Lagoon.type.caption, color = Lagoon.colors.textHint, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            content()
        }
    }
}

@Composable
fun LiveCardSpecimen() = SpecimenGrid {
    val s = Samples.streams
    Sample("Default · 默认") { LiveCard(s[0].title, s[0].channel, s[0].viewers, {}, thumbnail = cover(0), gameName = s[0].game, uptime = s[0].uptime, tags = s[0].tags) }
    Sample("English, no tags") { LiveCard(s[1].title, s[1].channel, s[1].viewers, {}, thumbnail = cover(1), gameName = s[1].game, uptime = s[1].uptime) }
    Sample("Long Chinese title · 超长中文") { LiveCard(s[3].title, s[3].channel, s[3].viewers, {}, thumbnail = cover(3), gameName = s[3].game, uptime = s[3].uptime, tags = s[3].tags) }
    Sample("Long English title") { LiveCard(s[4].title, s[4].channel, s[4].viewers, {}, thumbnail = cover(4), gameName = s[4].game, uptime = s[4].uptime, tags = s[4].tags) }
    Sample("Image not loaded yet") { LiveCard(s[2].title, s[2].channel, s[2].viewers, {}, gameName = s[2].game) }
    Sample("Compact · 紧凑版") {
        Column(verticalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap)) {
            LiveCardCompact(s[0].title, s[0].channel, s[0].viewers, {}, thumbnail = cover(0), gameName = s[0].game, uptime = s[0].uptime)
            LiveCardCompact(s[4].title, s[4].channel, s[4].viewers, {}, thumbnail = cover(4), gameName = s[4].game)
        }
    }
}

@Composable
fun VideoCardSpecimen() = SpecimenGrid {
    val v = Samples.videos
    val download: @Composable () -> Unit = {
        LagoonIconButton(LucideIcons.Download, "Download", {}, filled = true, size = 40.dp)
    }
    Sample("Watched 62 % · 看到一半") { VideoCard(v[0].title, v[0].meta, {}, thumbnail = cover(2), duration = v[0].duration, progress = v[0].progress, trailing = download) }
    Sample("Not watched") { VideoCard(v[1].title, v[1].meta, {}, thumbnail = cover(3), duration = v[1].duration) }
    Sample("Finished · 已看完") { VideoCard(v[2].title, v[2].meta, {}, thumbnail = cover(1), duration = v[2].duration, progress = v[2].progress, trailing = download) }
    Sample("Long title, clip") { VideoCard(v[3].title, v[3].meta, {}, thumbnail = cover(4), duration = v[3].duration) }
}

@Composable
fun GamePosterSpecimen() = SpecimenGrid(columns = currentWidthClass().cardColumns * 3) {
    Samples.games.forEachIndexed { i, g ->
        Sample(if (i == 5) "Long name" else "Poster ${i + 1}") { GamePoster(g.name, {}, boxArt = cover(i), viewers = g.viewers) }
    }
}

@Composable
fun ChannelRowSpecimen() = SpecimenGrid {
    Sample("Live, offline, long · 直播中、未开播、超长") {
        Column(Modifier.background(Lagoon.colors.card, LagoonShapes.card).padding(vertical = 4.dp)) {
            Samples.channels.forEach { ChannelRow(it.name, it.subtitle, {}, live = it.live) }
        }
    }
    Sample("With action") {
        Column(Modifier.background(Lagoon.colors.card, LagoonShapes.card).padding(vertical = 4.dp)) {
            ChannelRow("Sora", "上次直播 昨天", {}, trailing = { LagoonIconButton(LucideIcons.Bell, "Notifications", {}, filled = false) })
        }
    }
}

@Composable
fun SettingRowSpecimen() = SpecimenGrid {
    Sample("Kinds · 类型") {
        Column(Modifier.background(Lagoon.colors.card, LagoonShapes.card).padding(vertical = 4.dp)) {
            SettingRow("广告屏蔽", icon = LucideIcons.ShieldCheck, highlight = true, trailing = SettingTrailing.Value("均衡"), onClick = {})
            SettingRow("Playback", description = "Auto quality, low latency", icon = LucideIcons.Play, onClick = {})
            SettingRow("后台播放", description = "锁屏后继续播放声音", icon = LucideIcons.Headphones, trailing = SettingTrailing.Toggle(true) {})
            SettingRow("Picture-in-picture", icon = LucideIcons.PictureInPicture2, trailing = SettingTrailing.Toggle(false) {})
            SettingRow("通知", icon = LucideIcons.Bell, trailing = SettingTrailing.Value("特别关注开播时", chevron = false))
            SettingRow("Disabled row", description = "Needs login", icon = LucideIcons.Lock, enabled = false, onClick = {})
        }
    }
    Sample("Long text · 超长文字") {
        Column(Modifier.background(Lagoon.colors.card, LagoonShapes.card).padding(vertical = 4.dp)) {
            SettingRow(
                "用一个非常非常长的设置标题来检查换行是否正常显示",
                description = "这是一段很长的说明文字，用来检查两行以后是否会被截断并显示省略号，而不是把右侧的当前值挤出去。",
                icon = LucideIcons.Info, trailing = SettingTrailing.Value("一个很长的当前值"), onClick = {},
            )
            SettingRow(
                "An extremely long setting title that has to wrap onto a second line",
                description = "A long explanation that is cut after two lines so the value on the right keeps its room on screen.",
                icon = LucideIcons.Globe, trailing = SettingTrailing.Toggle(true) {},
            )
        }
    }
}

@Composable
fun SettingsGroupSpecimen() {
    var background by remember { mutableStateOf(true) }
    SpecimenGrid {
        Sample("Group with title, rows, note · 分组") {
            SettingsGroup(title = "观看 Watching", description = "关闭后台播放时，锁屏和画中画相关的选项会收起。") {
                SettingRow("Ad blocking", icon = LucideIcons.ShieldCheck, highlight = true, trailing = SettingTrailing.Value("Balanced"), onClick = {})
                SettingRow("后台播放", icon = LucideIcons.Headphones, trailing = SettingTrailing.Toggle(background) { background = it })
                SettingDependents(background) {
                    SettingRow("锁屏时继续播放", trailing = SettingTrailing.Toggle(true) {})
                    SettingRow("画中画时锁屏继续播放", trailing = SettingTrailing.Toggle(false) {})
                }
            }
        }
        Sample("No title") {
            SettingsGroup {
                SettingRow("About Lagoon", icon = LucideIcons.Info, trailing = SettingTrailing.Value("0.1"), onClick = {})
                SettingRow("检查更新", icon = LucideIcons.RefreshCw, onClick = {})
            }
        }
    }
}

@Composable
fun BottomSheetSpecimen() = SpecimenGrid {
    Sample("Half height · 半高") {
        SheetSurface(title = "画质 Quality") {
            SingleChoiceList(
                listOf(ChoiceOption("自动 Auto", "根据网速切换"), ChoiceOption("1080p60 源画质"), ChoiceOption("720p60"), ChoiceOption("仅音频 Audio only")),
                selected = 1, onSelect = {},
            )
        }
    }
    Sample("Settings sheet · 播放设置") {
        SheetSurface {
            SettingRow("画质", icon = LucideIcons.Gauge, trailing = SettingTrailing.Value("1080p60"), onClick = {})
            SettingRow("Speed", icon = LucideIcons.Timer, trailing = SettingTrailing.Value("1.0×"), onClick = {})
            SettingRow("仅音频", icon = LucideIcons.Headphones, trailing = SettingTrailing.Toggle(false) {})
            SettingRow("Ad blocking", icon = LucideIcons.ShieldCheck, highlight = true, trailing = SettingTrailing.Value("2 skipped", chevron = false))
        }
    }
}

@Composable
fun ActionMenuSpecimen() = SpecimenGrid {
    Sample("Long-press menu · 长按菜单") {
        SheetSurface(title = "夜航Nightowl") {
            ActionMenu(sampleActions())
        }
    }
}

fun sampleActions(onAny: (String) -> Unit = {}) = listOf(
    ActionItem("打开频道", LucideIcons.User) { onAny("open") },
    ActionItem("仅听音频", LucideIcons.Headphones) { onAny("audio") },
    ActionItem("Record", LucideIcons.CircleDot) { onAny("record") },
    ActionItem("分享", LucideIcons.Share2) { onAny("share") },
    ActionItem("取消关注", LucideIcons.Trash2, destructive = true) { onAny("unfollow") },
)

@Composable
fun PickerSpecimen() {
    var single by remember { mutableStateOf(0) }
    var multi by remember { mutableStateOf(setOf(0, 2)) }
    var slider by remember { mutableStateOf(0.6f) }
    var segment by remember { mutableStateOf(0) }
    SpecimenGrid {
        Sample("Single choice · 单选") {
            Box(Modifier.background(Lagoon.colors.card, LagoonShapes.card).padding(vertical = 4.dp)) {
                SingleChoiceList(listOf(ChoiceOption("观看人数 Viewers"), ChoiceOption("最新 Newest"), ChoiceOption("Relevance", "Best match first")), single, { single = it })
            }
        }
        Sample("Multiple choice · 多选") {
            Box(Modifier.background(Lagoon.colors.card, LagoonShapes.card).padding(vertical = 4.dp)) {
                MultiChoiceList(listOf(ChoiceOption("中文"), ChoiceOption("English"), ChoiceOption("日本語")), multi, { i, on -> multi = if (on) multi + i else multi - i })
            }
        }
        Sample("Slider · 滑块") {
            Box(Modifier.background(Lagoon.colors.card, LagoonShapes.card).padding(vertical = 4.dp)) {
                LagoonSlider("聊天文字大小", slider, { slider = it }, "${(11 + slider * 10).toInt()} sp", steps = 9)
            }
        }
        Sample("Segmented · 分段") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedControl(listOf("直播中 · 6", "全部 29"), segment, { segment = it })
                SegmentedControl(listOf("Live", "Videos", "Clips", "About"), 2, {})
            }
        }
    }
}

@Composable
fun FilterChipSpecimen() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChipRow(
            listOf(
                FilterChipItem("排序：观看人数", dropdown = true, icon = LucideIcons.ArrowUpDown) {},
                FilterChipItem("全部", selected = true) {},
                FilterChipItem("中文") {},
                FilterChipItem("English") {},
                FilterChipItem("Language", dropdown = true, icon = LucideIcons.Languages) {},
                FilterChipItem("Drops enabled") {},
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        )
        FilterChipRow(
            listOf(FilterChipItem("A very long chip label that keeps going", selected = true) {}, FilterChipItem("短") {}),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        )
    }
}

val sampleNavItems = listOf(
    NavItem("关注", LucideIcons.Heart, LucideIcons.HeartFilled),
    NavItem("浏览", LucideIcons.LayoutGrid, LucideIcons.LayoutGridFilled),
    NavItem("我的", LucideIcons.Download, LucideIcons.DownloadFilled),
)

@Composable
fun NavigationSpecimen() {
    var selected by remember { mutableStateOf(0) }
    SpecimenGrid {
        Sample("Floating pill · 悬浮胶囊 (phone)") {
            Column {
                FloatingNavBar(sampleNavItems, selected, { selected = it })
                FloatingNavBar(sampleNavItems, 2, {})
            }
        }
        Sample("Rail · 竖栏 (tablet)") {
            Row(Modifier.height(320.dp)) {
                FloatingNavRail(sampleNavItems, 1, {})
            }
        }
        Sample("Top bar, expanded and collapsed · 顶栏") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LargeTopBar("关注", rememberTopBarState(), actions = {
                    LagoonIconButton(LucideIcons.Search, "Search", {})
                    LagoonIconButton(LucideIcons.Settings, "Settings", {})
                })
                val collapsed = rememberTopBarState().also { it.snapTo(1f) }
                LargeTopBar("广告屏蔽 Ad blocking", collapsed, navigationIcon = {
                    LagoonIconButton(LucideIcons.ChevronLeft, "Back", {}, filled = false)
                })
            }
        }
    }
}

@Composable
fun StatesSpecimen() = SpecimenGrid {
    Sample("Skeleton · 骨架屏") {
        Column(verticalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap)) {
            SkeletonLiveCard()
            Column(Modifier.background(Lagoon.colors.card, LagoonShapes.card)) {
                SkeletonRow()
                SkeletonRow()
            }
        }
    }
    Sample("Empty · 空状态") {
        EmptyState("关注的频道现在都没有在直播。最近开播过的频道在下面。", actionLabel = "去浏览", onAction = {})
    }
    Sample("Error · 错误") {
        ErrorState("Check your connection and try again.", onRetry = {})
    }
}

@Composable
fun ToastSpecimen() = SpecimenGrid {
    Sample("Message · 普通") { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Toast(ToastData("已复制链接")) } }
    Sample("With undo · 带撤销") { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Toast(ToastData("Deleted 1 download", actionLabel = "撤销 Undo")) } }
    Sample("Long message") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Toast(ToastData("下载已加入队列，完成后会在“我的”页面里看到，并发送一条通知提醒你。", actionLabel = "查看"))
        }
    }
}

@Composable
fun ControlsSpecimen() {
    var on by remember { mutableStateOf(true) }
    var following by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    SpecimenGrid {
        Sample("Buttons · 按钮") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LagoonButton("登录 Log in", {})
                LagoonButton("Secondary", {}, style = ButtonStyle.Secondary, icon = LucideIcons.Share2)
                LagoonButton("删除下载", {}, style = ButtonStyle.Danger, icon = LucideIcons.Trash2)
                LagoonButton("Disabled", {}, enabled = false)
            }
        }
        Sample("Icon buttons, switches, follow · 图标按钮、开关、关注") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    LagoonIconButton(LucideIcons.Search, "Search", {})
                    LagoonIconButton(LucideIcons.Settings, "Settings", {})
                    LagoonIconButton(LucideIcons.Ellipsis, "More", {}, filled = false)
                    LagoonIconButton(LucideIcons.Bell, "Disabled", {}, enabled = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LagoonSwitch(on, { on = it })
                    LagoonSwitch(!on, { on = !it })
                    LagoonSwitch(true, null, enabled = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FollowButton(following, { following = it })
                    FollowButton(true, {})
                }
            }
        }
        Sample("Inputs · 输入框") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LagoonTextField(text, { text = it }, "搜索游戏或频道", Modifier.fillMaxWidth())
                LagoonTextField("Nightowl", {}, "Search", Modifier.fillMaxWidth())
            }
        }
    }
}
