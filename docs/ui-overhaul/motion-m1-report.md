# 动效 M0 + M1 报告

日期：2026-11-18。计划：`docs/ui-overhaul/motion-audit.md`（修改版 2，Mitchell 2026-11-18 批准，五个决定按建议默认）。M0 是 PR #41（已合并），M1 是本 PR（分支 `motion/m1`）。手机测试按计划放在“手机 1”（M1 + M2 合完之后），本报告只有模拟器和 JVM 的结果。

## 结论

| 验收项 | 结果 |
| --- | --- |
| JVM 测试 | 672 项全过（截图 327、交互 105、播放器契约 20、单元 220）；截图基线零变化 |
| 新增测试 | M0：`DesignTokensTest` 2 项（`playerSpring` 不过冲、“移除动画”下哪些跳到终点、`playerRiseStart`）；M1：`NavigationTest` 4 项（一次性切页标记）、`StaggerGateInteractionTest` 3 项（列表淡入只放一次） |
| 动效守护 | `tools/ci/motion_guard.py` 0 问题，自测 15 例；允许名单只剩 1 条永久项（`BrowseDetail` 的 `SeekableTransitionState.animateTo`），M0 时标给 M1 的 10 处全部改掉 |
| 模拟器录屏 | 切页、列表淡入、频道页前进/返回、打开播放器、最小化、迷你 × 关闭，见第 3 节 |

## 1. M0（PR #41）

- `LagoonMotion` 新增 `playerSpring`（阻尼 1.0）、`layout`、`contentSwap`、`sharedBounds`、`listItem*`、`sheetForward/sheetBack`（`SheetShift` = 1/4 宽）、`loop()`、`playerRiseStart`；`back()` 加上从 96 % 放大回来；`LONG` 只留给共享元素。
- 持续动画统一走 `continuousTransition(label, Ambient | Progress)`（`ui/design/theme/LagoonLoops.kt`）。`LocalLiveDotBreathing` 推广成 `LocalAmbientMotion`：直播圆点是 `Ambient`，玻璃背景上照旧停；转圈和骨架闪光是 `Progress`，照旧转（只在加载时出现，不是静止页面上的常驻动画）。行为没有变化。
- 计划里说的“N7 小 bug”（“移除动画”时共享元素边界还在动）实际不存在：`sharedBoxArt` 在“移除动画”时直接返回，不挂共享元素。`BrowseDetail` 三处照计划改成走 `LagoonMotion`。
- 筛选弹层子页面改用 `sheetForward/sheetBack`（计划 D3 的有意统一）。
- `TransitionBenchmark`：切标签、进出频道页、播放器最大化/最小化，选择器只用 v0.2 也有的（底部胶囊的中英文标签、twitch.tv 链接、返回键、播放器的 SurfaceView）。v0.2 对照包沿用启动线程的 `-Plagoon.perfId=v02`，等它合并后在手机 1 之前打包。没有在手机上跑过。

## 2. M1（本 PR）

1. **逐项淡入只放一次**（L2）：新的 `StaggerGate(key, hasItems)` 在列表第一次有数据的那一帧打开（在组合里就决定，所以第一帧已经是透明的，不会闪一下），放完一轮（约 0.57 s）关上，之后不再打开。哪些列表放过记在 `StaggerMemory` 里，存在页面的 saved state（分段和 `AnimatedContent` 之外），所以返回、切标签、切分段、滚走再滚回来、下拉刷新、搜索逐字出结果都不重放。9 处列表都接上了：关注（4 个分段）、我的（3 个分段）、浏览游戏、热门直播、团队、游戏页（每个分段一次）、频道页（每个分段一次）、搜索。没有接 `StaggerGate` 的列表不放淡入（默认关）。
2. **“我的”页红色删除底色**（L3）：淡入挪到整个滑动删除框上（`SwipeToDelete` 加了 `modifier`），红色底色和卡片一起淡入。
3. **切回第一个标签用切页动画**（N2）+ **标记一次性**（审阅 2 N1）：转场 lambda 只读不清；`AppNavigator` 在 controller 上挂同步的 `OnDestinationChangedListener`，当前条目不是标记的条目就清掉。`MainShell` 的两个 pop 转场也查标记。
4. **数值收口**：播放器 `progress` 用 `playerSpring`，`Animatable` 加 `updateBounds(0f, 1f)`；下拉松手把手指速度（`VelocityTracker`，换算成每秒 `progress`）交给弹簧，最小化和弹回两条路都带；控制层淡入淡出用 `fade/fadeExit`；迷你横滑关闭、弹回、`onDragCancel` 都用 `playerSpring`；聊天 `animateItem` 用 `listItemFadeIn/listItemPlacement`。从播放器点频道名/游戏名本来就是同时开始，现在两边都用规范的规格（P25/X5）。
5. **迷你 × 关闭**（P7）：和横滑一样先滑出淡出再关；`closePlayer()` 去掉了 `TRANSIT_FRAGMENT_FADE`。
6. 小项：快进/亮度提示淡入淡出（P12，提示还在时再次点击只换文字）；广告状态标签随控制层平移（P13）；提示条底边距跟着迷你播放器平移（P8）；登录底部栏 `expand/collapse`（T5）。

## 3. 模拟器录屏（emulator-5554，swiftshader，约 11–16 帧/秒）

录屏和联系表在 `E:/Projects/Lagoon-audit/motion/`：

| 文件 | 看到的 |
| --- | --- |
| `m1-tabs` | 关注 → 浏览：浏览游戏网格第一次加载逐个淡入；浏览 → 关注：两页交叉淡入（以前是浏览页整页向右滑出）；再回浏览：网格直接在，不重放 |
| `m0-back` | 频道页前进、返回正常（返回时下面的页从暗处淡回） |
| `m1-player` | 打开直播（仍是硬切，M2a 的事），返回键最小化：视频缩到迷你卡片；聊天第一帧就消失（P2，M2a 的事） |
| `m1-close` | 点迷你 × ：卡片向右滑出并淡出，再关闭 |

模拟器帧率太低，看不出弹簧和 tween 的手感差别，也看不出速度是否接上；这些在手机 1 用转场基准测试量。

## 4. 留给后面的

- **聊天新消息的位移从 200 ms tween 换成了 steady 弹簧**，安定时间更长一些。30 条/秒压测时这段动画本来就连续不断，预计成本相同，但要在手机上用 `ChatStressBenchmark` 复跑确认（手机 1）。
- 迷你卡片横滑、× 关闭时，视频画面比卡片晚一帧跟上（视频位置通过 `SideEffect` 交给 View，晚一帧），以前横滑就是这样，不是本次引入的；M2a 改视频槽时一起看。
- 提示条底边距的改动和启动计划 S3 的 D1（吐司层延后建立）在 `MainActivity` 同一段，后合的一方需要合并一下。
