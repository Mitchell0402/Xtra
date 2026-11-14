package com.github.andreyasadchy.xtra.lab.showcase

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.ui.design.components.ActionSheet
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle
import com.github.andreyasadchy.xtra.ui.design.components.ChoiceOption
import com.github.andreyasadchy.xtra.ui.design.components.EmptyState
import com.github.andreyasadchy.xtra.ui.design.components.FloatingNavBar
import com.github.andreyasadchy.xtra.ui.design.components.FloatingNavRail
import com.github.andreyasadchy.xtra.ui.design.components.FollowButton
import com.github.andreyasadchy.xtra.ui.design.components.GlassBackdrop
import com.github.andreyasadchy.xtra.ui.design.components.GlassContext
import com.github.andreyasadchy.xtra.ui.design.components.LocalGlass
import com.github.andreyasadchy.xtra.ui.design.components.glassWindowAllowsBlur
import com.github.andreyasadchy.xtra.ui.design.components.lagoonHazeState
import com.github.andreyasadchy.xtra.ui.main.shell.GlassPolicy
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.ui.design.components.GamePoster
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonImage
import com.github.andreyasadchy.xtra.ui.design.components.LagoonPullToRefresh
import com.github.andreyasadchy.xtra.ui.design.components.LargeTopBar
import com.github.andreyasadchy.xtra.ui.design.components.LiveCard
import com.github.andreyasadchy.xtra.ui.design.components.SettingDependents
import com.github.andreyasadchy.xtra.ui.design.components.SettingRow
import com.github.andreyasadchy.xtra.ui.design.components.SettingTrailing
import com.github.andreyasadchy.xtra.ui.design.components.SettingsGroup
import com.github.andreyasadchy.xtra.ui.design.components.SingleChoiceList
import com.github.andreyasadchy.xtra.ui.design.components.SkeletonLiveCard
import com.github.andreyasadchy.xtra.ui.design.components.ToastHost
import com.github.andreyasadchy.xtra.ui.design.components.ToastResult
import com.github.andreyasadchy.xtra.ui.design.components.rememberHideOnScrollState
import com.github.andreyasadchy.xtra.ui.design.components.rememberToastHostState
import com.github.andreyasadchy.xtra.ui.design.components.rememberTopBarState
import com.github.andreyasadchy.xtra.ui.design.components.staggeredAppear
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonMotion
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.design.theme.WidthClass
import com.github.andreyasadchy.xtra.ui.design.theme.currentWidthClass
import com.github.andreyasadchy.xtra.ui.design.theme.siblingBy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The U1 component showcase (plan section 6): every component in every state, and every motion of plan section 5 to
 * try by hand. Debug builds only; it has its own launcher icon, "Lagoon 组件".
 */
class ComponentShowcaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // dark only: light status and navigation bar icons whatever the system mode
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val policy = GlassPolicy(this).also { lifecycle.addObserver(it) }
        val hazeState = lagoonHazeState()
        setContent {
            LagoonTheme {
                CompositionLocalProvider(LocalGlass provides GlassContext(hazeState, policy.conditions.blurAllowed && glassWindowAllowsBlur())) {
                    ShowcaseApp(policy)
                }
            }
        }
    }
}

private enum class Tab(val label: String) { Components("组件"), Glass("玻璃"), Motion("动效"), Tokens("参数") }

@Composable
fun ShowcaseApp(policy: GlassPolicy? = null) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val widthClass = currentWidthClass()
    val topBar = rememberTopBarState()
    val navScroll = rememberHideOnScrollState()
    val toasts = rememberToastHostState()
    val density = LocalDensity.current
    val motion = Lagoon.motion
    val items = listOf(
        com.github.andreyasadchy.xtra.ui.design.components.NavItem(Tab.Components.label, LucideIcons.LayoutGrid, LucideIcons.LayoutGridFilled),
        com.github.andreyasadchy.xtra.ui.design.components.NavItem(Tab.Glass.label, LucideIcons.Eye),
        com.github.andreyasadchy.xtra.ui.design.components.NavItem(Tab.Motion.label, LucideIcons.Sparkles),
        com.github.andreyasadchy.xtra.ui.design.components.NavItem(Tab.Tokens.label, LucideIcons.Palette),
    )
    Row(Modifier.fillMaxSize().background(Lagoon.colors.page)) {
        if (widthClass == WidthClass.Expanded) FloatingNavRail(items, tab, { tab = it })
        Box(Modifier.weight(1f).fillMaxSize()) {
            // the page is the glass backdrop; the toast and the pill float over it, outside (glass plan section 6)
            GlassBackdrop(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().nestedScroll(navScroll.nestedScrollConnection).nestedScroll(topBar.nestedScrollConnection)) {
                LargeTopBar(Tab.entries[tab].label + " · Lagoon", topBar)
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { siblingBy(motion, density) { it } },
                    label = "showcaseTab",
                ) { t ->
                    when (Tab.entries[t]) {
                        Tab.Components -> ComponentsTab()
                        Tab.Glass -> GlassTab(policy)
                        Tab.Motion -> MotionTab(onToast = { message, undo ->
                            if (toasts.show(message, if (undo) "撤销" else null) == ToastResult.ActionPerformed) toasts.show("已撤销")
                        })
                        Tab.Tokens -> TokensTab()
                    }
                }
            }
            }
            Column(Modifier.align(Alignment.BottomCenter)) {
                ToastHost(toasts)
                if (widthClass != WidthClass.Expanded) FloatingNavBar(items, tab, { tab = it }, visible = navScroll.visible)
            }
        }
    }
}

private val bottomRoom = 120.dp

/**
 * Stage G: the glass sheet with the temporary "透明效果" switch (the real one is in Settings > UI; U3 moves it to the
 * appearance page) and why glass is solid right now, then a long colourful list to scroll under the real pill.
 */
@Composable
private fun GlassTab(policy: GlassPolicy?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.prefs() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomRoom)) {
        item {
            Column(Modifier.padding(horizontal = LagoonSpacing.pageSide)) {
                val on = policy?.conditions?.settingOff != true
                SettingRow(
                    "透明效果 Transparency effects",
                    description = policy?.conditions?.reasons()?.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Solid now: ") ?: "Blur allowed",
                    trailing = SettingTrailing.Toggle(on) { prefs.edit().putBoolean(C.UI_GLASS, it).apply() },
                )
            }
        }
        item { SpecimenPage(Specimen.Glass) }
        itemsIndexed(List(12) { it }) { i, _ ->
            Box(
                Modifier
                    .padding(horizontal = LagoonSpacing.pageSide, vertical = 6.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(LagoonShapes.card)
            ) { LagoonImage(cover(i), contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun ComponentsTab() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomRoom)) {
        itemsIndexed(Specimen.entries) { _, specimen -> SpecimenPage(specimen) }
    }
}

@Composable
private fun DemoBlock(title: String, note: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = LagoonSpacing.pageSide, vertical = 10.dp)) {
        Text(title, style = Lagoon.type.headline, color = Lagoon.colors.textPrimary)
        Text(note, style = Lagoon.type.label, color = Lagoon.colors.textSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        content()
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MotionTab(onToast: suspend (String, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<String?>(null) }
    var actionSheet by remember { mutableStateOf(false) }
    var quality by remember { mutableIntStateOf(0) }
    LagoonPullToRefresh(isRefreshing = false, onRefresh = { scope.launch { onToast("已刷新（演示）", false) } }) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomRoom)
                .widthIn(max = 720.dp)
        ) {
            DemoBlock("按压 Press", "可点击的卡片和行按下时缩到 97% 并变亮一档，松手弹回；长按弹出操作菜单。") {
                val s = Samples.streams[0]
                LiveCard(s.title, s.channel, s.viewers, onClick = {}, onLongClick = { actionSheet = true }, thumbnail = cover(0), gameName = s.game, uptime = s.uptime)
            }
            DemoBlock("底部弹层 Sheet", "从底部用弹簧升起，背景变暗，可以下拉跟手关闭；半高和全高两种。") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LagoonButton("半高 Half", { sheet = "half" }, style = ButtonStyle.Secondary)
                    LagoonButton("全高 Full", { sheet = "full" }, style = ButtonStyle.Secondary)
                    LagoonButton("操作菜单", { actionSheet = true }, style = ButtonStyle.Secondary)
                }
            }
            DemoBlock("提示条 Toast", "底部浮起的胶囊，可带撤销。") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LagoonButton("普通", { scope.launch { onToast("已复制链接", false) } }, style = ButtonStyle.Secondary)
                    LagoonButton("带撤销", { scope.launch { onToast("已删除 1 个下载", true) } }, style = ButtonStyle.Secondary)
                }
            }
            DemoBlock("卡片到详情 Card to detail", "封面作为共享元素放大到新位置（450 毫秒，标准曲线）。点卡片打开，点详情返回。") {
                CardToDetailDemo()
            }
            DemoBlock("进入下一级 Forward", "新页从右侧滑入并淡入，旧页略微缩小变暗；返回键或返回手势回到上一级。") {
                ForwardDemo()
            }
            DemoBlock("首次加载 First load", "骨架屏加微光，内容到达后逐项淡入，间隔 30 毫秒。") {
                LoadingDemo()
            }
            DemoBlock("设置联动 Dependent rows", "开关控制的子项展开或收起，而不是变灰。") {
                var on by remember { mutableStateOf(true) }
                SettingsGroup {
                    SettingRow("后台播放", icon = LucideIcons.Headphones, trailing = SettingTrailing.Toggle(on) { on = it })
                    SettingDependents(on) {
                        SettingRow("锁屏时继续播放", trailing = SettingTrailing.Toggle(true) {})
                        SettingRow("画中画时继续播放", trailing = SettingTrailing.Toggle(false) {})
                    }
                }
            }
            DemoBlock("关注 Follow", "图标从线性变为填充，带一次弹跳和触感。") {
                var following by remember { mutableStateOf(false) }
                FollowButton(following, { following = it })
            }
            DemoBlock("直播状态 Live", "红点缓慢呼吸；观看人数变化时数字滚动。") {
                var viewers by remember { mutableIntStateOf(12408) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1600)
                        viewers += listOf(-37, 12, 58, -4, 103).random()
                    }
                }
                val s = Samples.streams[1]
                LiveCard(s.title, s.channel, "%,d".format(viewers), {}, thumbnail = cover(1), gameName = s.game, uptime = s.uptime)
            }
            DemoBlock("图片淡入 Image fade-in", "封面和头像加载完成时淡入（需要联网）。点“重新加载”再看一次。") {
                var reload by remember { mutableIntStateOf(0) }
                Row(horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap)) {
                    Samples.boxArtUrls.forEachIndexed { i, url ->
                        Box(Modifier.weight(1f)) {
                            GamePoster(Samples.games[i].name, {}, boxArt = "$url?r=$reload", viewers = Samples.games[i].viewers)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LagoonButton("重新加载 Reload", { reload++ }, style = ButtonStyle.Secondary, icon = LucideIcons.RefreshCw)
            }
            DemoBlock("下拉刷新 Pull to refresh", "在本页顶部下拉：指示器跟手，过了触发点会略微拉伸，松手时有一次轻触感。") {}
        }
    }
    when (sheet) {
        "half" -> LagoonBottomSheet(onDismiss = { sheet = null }, title = "画质 Quality") {
            SingleChoiceList(
                listOf(ChoiceOption("自动 Auto", "根据网速切换"), ChoiceOption("1080p60 源画质"), ChoiceOption("720p60"), ChoiceOption("480p"), ChoiceOption("仅音频 Audio only")),
                quality, { quality = it },
            )
        }
        "full" -> LagoonBottomSheet(onDismiss = { sheet = null }, title = "全高弹层 Full height", skipHalfHeight = true) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                repeat(14) { SettingRow("条目 ${it + 1}", description = "Row ${it + 1}", icon = LucideIcons.Info, onClick = {}) }
            }
        }
    }
    if (actionSheet) {
        ActionSheet(sampleActions { scope.launch { onToast("Action: $it", false) } }, onDismiss = { actionSheet = false }, title = "夜航Nightowl")
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CardToDetailDemo() {
    var open by remember { mutableStateOf<Int?>(null) }
    val motion = Lagoon.motion
    SharedTransitionLayout {
        AnimatedContent(targetState = open, label = "cardToDetail") { target ->
            if (target == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap)) {
                    listOf(0, 2).forEach { i ->
                        val s = Samples.streams[i]
                        Box(Modifier.weight(1f)) {
                            LiveCard(
                                s.title, s.channel, s.viewers, { open = i }, thumbnail = cover(i),
                                thumbnailModifier = Modifier.sharedElement(
                                    rememberSharedContentState("cover$i"), this@AnimatedContent,
                                    boundsTransform = { _, _ -> if (motion.reduced) tween(0) else tween(LagoonMotion.LONG, easing = LagoonMotion.Standard) },
                                ),
                            )
                        }
                    }
                }
            } else {
                val s = Samples.streams[target]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Lagoon.colors.card, LagoonShapes.card)
                        .padding(bottom = 16.dp)
                ) {
                    Box(
                        Modifier
                            .sharedElement(
                                rememberSharedContentState("cover$target"), this@AnimatedContent,
                                boundsTransform = { _, _ -> if (motion.reduced) tween(0) else tween(LagoonMotion.LONG, easing = LagoonMotion.Standard) },
                            )
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(LagoonShapes.card)
                    ) {
                        LagoonImage(cover(target), null, Modifier.fillMaxSize())
                    }
                    Text(s.title, style = Lagoon.type.title, color = Lagoon.colors.textPrimary, modifier = Modifier.padding(16.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${s.channel} · ${s.game}", style = Lagoon.type.label, color = Lagoon.colors.textSecondary, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(12.dp))
                    LagoonButton("返回 Back", { open = null }, Modifier.padding(horizontal = 16.dp), style = ButtonStyle.Secondary, icon = LucideIcons.ChevronLeft)
                }
                BackHandler { open = null }
            }
        }
    }
}

@Composable
private fun ForwardDemo() {
    var page by remember { mutableStateOf<String?>(null) }
    val motion = Lagoon.motion
    Box(Modifier.fillMaxWidth().height(280.dp).clip(LagoonShapes.card).background(Lagoon.colors.page)) {
        AnimatedContent(
            targetState = page,
            transitionSpec = { if (targetState != null) motion.forward() else motion.back() },
            label = "forward",
        ) { current ->
            if (current == null) {
                SettingsGroup(Modifier.fillMaxSize()) {
                    SettingRow("广告屏蔽", icon = LucideIcons.ShieldCheck, highlight = true, trailing = SettingTrailing.Value("均衡"), onClick = { page = "广告屏蔽" })
                    SettingRow("播放", icon = LucideIcons.Play, trailing = SettingTrailing.Value("自动画质"), onClick = { page = "播放" })
                    SettingRow("聊天", icon = LucideIcons.MessageCircle, onClick = { page = "聊天" })
                    SettingRow("外观", icon = LucideIcons.Palette, trailing = SettingTrailing.Value("深色"), onClick = { page = "外观" })
                }
            } else {
                Column(Modifier.fillMaxSize().background(Lagoon.colors.page)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LagoonIconButton(LucideIcons.ChevronLeft, "返回", { page = null }, filled = false)
                        Text(current, style = Lagoon.type.headline, color = Lagoon.colors.textPrimary)
                    }
                    EmptyState("这是“$current”子页（演示）。")
                }
                BackHandler { page = null }
            }
        }
    }
}

@Composable
private fun LoadingDemo() {
    var loaded by remember { mutableStateOf(false) }
    var run by remember { mutableIntStateOf(0) }
    LaunchedEffect(run) {
        loaded = false
        delay(1800)
        loaded = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(LagoonSpacing.cardGap)) {
        if (!loaded) {
            repeat(2) { SkeletonLiveCard() }
        } else {
            Samples.streams.take(2).forEachIndexed { i, s ->
                LiveCard(s.title, s.channel, s.viewers, {}, Modifier.staggeredAppear(i), thumbnail = cover(i), gameName = s.game, uptime = s.uptime)
            }
        }
        LagoonButton("再来一次 Replay", { run++ }, style = ButtonStyle.Secondary, icon = LucideIcons.RotateCcw)
    }
}

@Composable
private fun TokensTab() {
    val c = Lagoon.colors
    val t = Lagoon.type
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LagoonSpacing.pageSide)
            .padding(bottom = bottomRoom)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Text("颜色 Colours", style = t.headline, color = c.textPrimary, modifier = Modifier.padding(vertical = 8.dp))
        listOf(
            "page #111113" to c.page, "card #1B1B1F" to c.card, "control #26262B" to c.control, "pressed #303036" to c.pressed,
            "accent #5EE2C4" to c.accent, "accentSoft" to c.accentSoft, "danger" to c.danger,
            "text 92%" to c.textPrimary, "text 62%" to c.textSecondary, "text 50%" to c.textHint,
        ).forEach { (name, color) ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(color, LagoonShapes.thumbnail))
                Text(name, style = t.label, color = c.textSecondary, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Text("字号 Type", style = t.headline, color = c.textPrimary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        listOf("28 Display 关注" to t.display, "22 Title 夜航Nightowl" to t.title, "17 Headline 广告屏蔽" to t.headline,
            "15 Body 排位冲大师 Ranked 1080p60" to t.body, "13 Label 上次直播 昨天" to t.label, "11 Caption 12,408" to t.caption,
        ).forEach { (text, style) -> Text(text, style = style, color = c.textPrimary, modifier = Modifier.padding(vertical = 4.dp)) }
        Text("动效 Motion", style = t.headline, color = c.textPrimary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        listOf(
            "即时 100 ms · 短 200 ms · 中 300 ms · 长 450 ms",
            "标准曲线 cubic-bezier(0.2, 0, 0, 1) · 退出 (0.3, 0, 1, 1)",
            "稳 damping 0.85 stiffness 380 · 弹 damping 0.6 stiffness 300",
            if (Lagoon.motion.reduced) "系统“移除动画”已开启：位移和缩放改为淡入淡出" else "系统“移除动画”未开启",
        ).forEach { Text(it, style = t.label, color = c.textSecondary, modifier = Modifier.padding(vertical = 3.dp)) }
    }
}
