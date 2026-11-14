# G 报告：磨砂玻璃

日期：2026-11-14。分支 `ui/g-glass`。依据：《Lagoon 磨砂玻璃风格计划》修改版 2（已批准：1a、2b、3a、4a），以及 Mitchell 开工时的补充：“不需要所有地方都是玻璃，需要高亮的部分，比如可操作的、可以切换的页面标签，或者可操作的按钮，能有比较明显的磨砂玻璃效果；最终的视觉效果由你来定。”

## 结论

| 门槛（玻璃计划第 11 节） | 结果 |
| --- | --- |
| 技术验证第 1 项：依赖不带升、不带降 | 通过，但用的是 2.0.1 而不是 2.1.0（第 2 节）。Compose ui、foundation、material3、core、collection 版本都不变；唯一变化是 androidx.tracing 2.0.0 → 2.0.3（补丁版） |
| 技术验证第 2 项：胶囊跨根节点模糊、三种转场、两个来源、旧页面 | 通过，有一处与计划不同：Haze 的 Auto 策略只在跨窗口时切屏幕坐标，同一窗口的两个 ComposeView 会错位，改为强制屏幕坐标（第 3 节） |
| 第 7 节性能：滚动、静止、弹层、冷启动（带模糊的数字来自 API 32 以上真机） | **等真机**。模拟器上的 GlassBenchmark 结果见第 6 节，只说明用例能跑，不作门槛 |
| 面积上限 | 通过：窄 11.0%、中 9.8%、宽 2.6%、矮 0（上限 30%、30%、25%、0） |
| Android 10 和 11 | Android 10 模拟器冒烟通过，走回退版；U2 没有留下 Android 10 的卡顿数字可比，见第 6 节 |
| 安装包体积 ≤ +0.3 MB | 通过：qa 36,786,437 字节，比 U2 的 36,695,144 多 91,293 字节（+0.09 MB），含 Haze 三个模块和噪点图 |
| 对比度单元测试、面积单元测试 | 通过 |
| 不改 `ui/player/`、PlayerFragment、PlayerLayout；模糊来源里没有旧页面和播放器 | 通过。来源只有关注页自己的 `GlassBackdrop`（`FollowingScreen`）和展示页、实验页；`activity_main.xml` 没改 |
| 关注页覆盖层结构：收起展开、下拉刷新、切换分段、旋转后位置正确，首项不被盖住 | 通过：`FollowingGlassTest` 四种窗口；模拟器上横竖屏各看过 |
| 转场录屏 | 通过（第 4 节） |
| 截图基线：改动的组件、外壳和关注页按回退版重录 | CI 录制（只在 CI 录），PR 里附前后对比 |
| 真模糊外观的人工截图 | 模拟器上关注页、展示页已截；真机截图和性能一起补 |
| 回退切换：省电、“透明效果”开关、停用窗口模糊、竖屏转横屏 | 开关、横竖屏通过；省电模式通过，但系统省电会强制深色模式，Activity 会被系统重建（v0.1 起就如此，与玻璃无关）；“停用窗口模糊”在这个模拟器镜像上 adb 无权限改，代码路径有监听器，真机上补测 |
| U2 外壳相关门槛重跑：旧页面逐个点过、迷你播放器不被遮挡、画中画 | 通过（浏览、我的、搜索、设置；迷你播放器停在胶囊上方） |
| 去广告回归（协调方要求照跑） | 通过：假广告 145 秒，`web/highlighter` 换源挡住，结束后主播放器回到直播边缘 |

## 1. 最终视觉：玻璃只给能点、能切换的东西

按 Mitchell 的补充，我把玻璃收得比计划更窄，并按“要明显”调了参数：

| 表面 | 材质 | 真模糊 |
| --- | --- | --- |
| 底部悬浮胶囊 | 浮层玻璃，选中项实心青绿配深色字（1a） | 关注页上（API 32+，非矮窗口）；旧页面上是回退版 |
| 左侧悬浮竖栏（宽于 840dp，含大多数手机横屏） | 浮层玻璃，不模糊（后面永远没有内容），选中同上 | 否 |
| 关注页分段标签 | 浮层玻璃，浮在列表上方；选中段是一块更亮的“透镜”（白 14%），青绿只留给导航 | 是 |
| 顶栏右侧圆形按钮（搜索、账号） | 玻璃材质，不模糊（它们后面是顶栏的页面底） | 否 |
| 次要按钮（Secondary） | 玻璃材质，不模糊 | 否 |
| 提示条 | 浮层玻璃 | 是 |
| 弹层、操作菜单 | 厚档：卡片色**不透明**、噪点、顶边高光 | 否 |
| 顶栏本身 | **不是玻璃**：展开时透明，收起时页面底色淡入并向下渐隐 | — |
| 卡片 | **保持纯实心**（偏离 2b，见下） | — |

和计划不同的三处，理由：

1. **卡片不加噪点和高光（2b 改成 2a）。** 补充里说玻璃要留给需要高亮的部分；卡片也带上磨砂颗粒和顶边高光，玻璃控件就不再突出。改回很简单：在卡片底面加 `glassSolid`。
2. **顶栏不做“栏”玻璃。** 收起后的整条顶栏是装饰性的面，不能点；分段标签单独做成一块悬浮玻璃，周围仍能看到内容滑过，更接近你说的“悬浮在屏幕上、四周不跟屏幕连接”。`GlassLevel.Bar` 保留给 U6、U7 的栏。
3. **浮层更透，背后压暗。** 计划是 80% 底色、饱和度 1.2；真模糊时几乎看不出背后。现在是 72% 底色、饱和度 1.5，同时把模糊后的背景亮度压到 75%。这样背景的颜色明显透出来，而背后是纯白时文字仍然达标（第 5 节）。弹层改成不透明：不模糊的 94% 会让后面的青绿胶囊在弹层上透出一块污渍。

“透明效果”开关（3a）已经加在现有的 设置 > 界面 第一项（键 `ui_glass_effects`，默认开）；U3 把它移到新的外观页即可。调试版展示页的“玻璃”分页也有同一个开关和当前回退原因。

## 2. 依赖核对（技术验证第 1 项）

`:app:dependencies --configuration qaRuntimeClasspath` 加 Haze 前后对比：

- 2.1.0：`haze-blur-android` 的 POM 依赖 `ui-android` 1.12.2、`collection` 1.7.0。项目 BOM 2026.09.00 对应 ui 1.12.1、collection 1.6.0，所以 2.1.0 会把 ui 单独带升、foundation 和 material3 留在原版本。按计划，改核对 2.0.1。
- 2.0.1：ui 1.12.1、collection 1.6.0、core-ktx 1.19.1、activity 1.13.0、lifecycle 2.11.0，都和项目一致。实际解析后 androidx 的 compose、core、collection、lifecycle 版本全部不变；变化只有 `androidx.tracing` 2.0.0 → 2.0.3（Haze 的 haze-utils 依赖，补丁版，不涉及界面）。JetBrains 的 compose 和 lifecycle 条目在 Android 上只是转到 androidx 的壳，版本号变化不影响实际库。
- 2.0.1 与 2.1.0 的接口一样（`hazeSource`、`hazeBlur`、`HazeBlurStyle`、`HazePerformanceMode`），以后 BOM 升到 ui 1.12.2 时可以直接换 2.1.0。
- qa（R8）构建正常，模拟器上 qa 包的关注页真模糊正常。

## 3. 外壳结构和跨根节点模糊（技术验证第 2 项）

- `ShellState` 持有一个 `HazeState`；关注页在自己的 ComposeView 里用 `GlassBackdrop` 包住列表（来源），顶栏、分段标签、提示条画在同一根节点的覆盖层上；胶囊在 Activity 的 `navBar` ComposeView 里，通过同一个 `HazeState` 取样。
- **计划的假设不成立：** Haze 2.x 的 Auto 定位策略比较的是 `View.getWindowId()`，同一个 Activity 里的两个 ComposeView 窗口 id 相同，于是用各自根节点里的坐标（positionInRoot），胶囊会画出向下偏移一个“胶囊到屏幕顶”距离的内容。Haze 把策略设成 internal；`HazeAccess.kt` 一个文件里压制可见性检查，把我们的 `HazeState` 固定成屏幕坐标。模拟器上胶囊内的模糊色块与外面的封面边缘对齐（实验页截图），滚动时没有看到滞后一帧；Robolectric 测试也走这条路径。Haze 升级时要复查这一处。
- 玻璃模式由外部注入：`LocalGlass`（`GlassContext(state, blur)`）。页面读 `ShellState.pageGlass()`；胶囊额外要求当前不是旧页面。测试默认 `GlassContext.Solid`，不挂 Haze 节点。
- 回退条件（`GlassPolicy`，全部带监听，实时切换不重建）：API < 32、`isCrossWindowBlurEnabled()` 为假、省电模式、低内存设备、高对比度文字（API 36 起，`AccessibilityManager.isHighContrastTextEnabled()` 已核实在 SDK 37 里有）、“透明效果”关闭；矮于 480dp 的窗口覆盖层不模糊。
- 回退版完全不经过 Haze：`GlassSurface` 自己画底色、`res/drawable-nodpi/glass_noise.png`（96×96，固定种子，`tools/glass/make_noise.py` 生成）的 BitmapShader 平铺噪点和顶边高光。
- benchmark 开关：`main` 里只有 `GlassOverride` 接口（默认不覆盖）；`src/lab` 里的 `LabGlassInit` 读启动 extra `lagoon.glass=on|off`。CI 的 qa 构建新增一步，检查 R8 映射里没有 `com.github.andreyasadchy.xtra.lab.` 的类（本地 qa 映射为 0）。

## 4. 转场与切换（模拟器录屏）

- 同级切换（关注 → 浏览 → 关注）、前进（关注 → 搜索）、返回（搜索 → 关注），672×1496 录屏逐帧看胶囊：没有闪烁，没有错位的色块。
- 切到旧页面时，胶囊在转场开始那一帧从模糊换成回退版，颜色亮一档（底色 control 94%）；回到关注页时在转场开始换回模糊。是一次台阶式变化而不是闪烁，所以没有用“转场期间回退”或改 Animator，U2 的转场代码没动。
- 回到关注页的 300ms 里页面在做 View 动画，胶囊里的模糊取的是页面的最终位置；24dp 模糊下看不出来。
- “透明效果”在设置里关掉再回关注页：胶囊和分段标签变成回退版，列表滚动位置不变（没有重建）；再打开恢复模糊。
- 横屏：竖栏（回退材质、实心青绿选中块），分段标签浮在三列网格上；转回竖屏位置和滚动都在。

## 5. 对比度（DesignTokensTest.glassTextReachesContrastOverWhite）

最坏情况：玻璃正后方纯白，不计模糊带来的改善。要求 4.5:1 加 0.2 余量。

| 材质 | 主文字 | 玻璃次要 78% | 青绿字 | 选中透镜上的主文字 | 次要 62% | 提示 50% | 红色 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 浮层真模糊：72% 底色，背景压到 75% | 7.01 | 5.59 | 4.96 | 4.91 | 不许用 | 不许用 | 不许用 |
| 浮层回退：94% 底色 | 10.90 | 8.33 | 7.87 | 7.15 | 5.90 | 4.42，不许用 | 4.54 |
| 厚档：卡片色不透明 | 14.64 | — | 10.75 | — | 7.26 | 5.18 | 6.20 |

浮层和栏里 `GlassSurface` 把次要和提示色换成玻璃次要色，所以玻璃上画不出 62% 和 50% 的字；红色不在浮层上用。导航选中项是实心青绿配 onAccent（11.8:1），与玻璃无关。

## 6. 性能

- **真机：等 Mitchell 连手机。** 用例：`GlassBenchmark`（`:benchmark`），玻璃开和关交替各 10 次：`scrollFollowing`（实验页，关注页 60 张固定直播卡片，胶囊在另一个根节点）、`idleFollowing`（静止 10 秒，红点呼吸）、`accountSheet`（开关账号弹层）、`coldStartup`（真实首页）。运行：`gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.mitchell0402.lagoon.benchmark.GlassBenchmark`。静止门槛不过时按 4a 停掉红点呼吸。
- 模拟器（API 37，SwiftShader 软件渲染）：结果见下方“模拟器 benchmark”一节，只说明用例能跑通。
- Android 10：回退版每个玻璃面只多一次 BitmapShader 填充和一条描边；U2 没有记录 Android 10 的卡顿数字，所以这一项写成“冒烟通过，数字待真机或下一阶段补”。

## 7. 自动化测试

本地 JVM 测试全部通过（新增：`FollowingGlassTest` 8 个、`GlassPolicyTest` 3 个、`DesignTokensTest` 2 个、展示页“玻璃”截图 4 个），Lint 没有新增错误。

## 8. 截图基线

受影响：导航（胶囊、竖栏）、按钮与开关、选择器（分段）、弹层、操作菜单、提示条、关注页和外壳的全部截图；新增“玻璃”展示页四张。全部按回退版在 CI 上重录（Robolectric 不画 RenderEffect，计划第 6 节可选的硬件渲染截图这次没做）。

## 9. 给后续阶段

- U3：把“透明效果”从 设置 > 界面 移到外观页；新页面照第 9 节的四条约定（列表延伸到浮层下面、浮层画在 `GlassBackdrop` 外、只用 `GlassSurface`、来源里不放播放器）。
- Haze 升级时复查 `HazeAccess.kt`（屏幕坐标），以及 2.1.0 的 ui 版本要求。
- 卡片若想要 2b 的磨砂质感：卡片底面加 `Modifier.glassSolid(GlassLevel.Thick, shape)`，基线重录。
