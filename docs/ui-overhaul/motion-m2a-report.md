# 动效 M2a 报告

日期：2026-11-18。计划：`docs/ui-overhaul/motion-audit.md`（修改版 2，已批准）第 5 节 M2a。分支 `motion/m2a`。手机测试按计划放在“手机 1”（M2a + M2b 合完之后），本报告只有模拟器和 JVM 的结果。

## 结论

| 验收项 | 结果 |
| --- | --- |
| JVM 测试 | 693 项全过（M1 时 672；新增 `PlayerArrivalTest` 21 项）；截图基线零变化 |
| 动效守护 | `tools/ci/motion_guard.py` 0 问题；允许名单没改（View 的淡出走新的 `LagoonViewMotion`，数值取自 `LagoonMotion`） |
| lint | 通过 |
| 模拟器录屏 | 从卡片打开、迷你开着点另一张卡片、最大化时换内容（片段 → 完整视频）、去设置再回来、画中画来回，见第 2 节 |
| 顺带修的 bug | M1 的吐司层底边距在进画中画时 overshoot 成负数，迷你播放器开着按 Home 会崩溃（已在 master 上），见第 3 节 |

## 1. 做了什么

1. **聊天跟着进度淡入淡出**（P2、P4、P17）。`ChatPlacement` 只管布局，`visible` 现在是“透明度大于 0”；透明度和下移 16 dp 走单独的 `onChatFade` 通道（Compose 侧 `SideEffect`，Fragment 侧只写 `View.alpha/translationY`），淡入淡出中给聊天容器开硬件层，到 0 或 1 还原。曲线 `PlayerGeometryRules.chatAlpha`：进度 0 → 0.3 从 1 淡到 0，最大化反过来；到 0 立即 `GONE`，不挡迷你卡片的触摸。收键盘、清焦点挪到 `minimize()` 开头。页面底色、信息栏、聊天都按同一个“离开程度”（迷你进度和打开进度取大）走，中间那段底色没有了。
2. **封面垫底**（P1、P5 情况①）。视频槽的 `AspectRatioFrameLayout` 里，SurfaceView 上面加了两层 `ImageView`：封面（新内容的缩略图）和旧画面截图，跟着视频一起缩放。规则在纯类 `CoverGate` 里：
   - 只在三个时机挂：打开播放器、换内容、回到前台。回到前台只在 `onStop` 时（服务 `stop` 之前）“视频开着且在播放、不在画中画”才挂，用户自己选的只听声音、暂停的都不挂。广告换 Surface 不挂（只会让已经挂着的封面提前淡掉）。
   - 退出：`onRenderedFirstFrame` 淡掉（`SHORT`）；3 s 超时淡掉；暂停、出错、选只听声音/只看聊天时立即摘掉；图还没到首帧先来了就不挂。
   - 图只从 Coil 缓存取（内存只读、磁盘只读、不走网络），打开时用卡片缩略图的像素尺寸，命中卡片自己那条内存缓存。Twitch 的“无图”占位图（`_404`、`404_preview`）不当封面。
   - 封面挂着时，只听声音的背景先不画（`PlayerUiState.coverUp`），回到前台时就不会先闪一下只听声音背景。
   - `PlayerConnection` 只多转发三个事件（首帧、出错、`playWhenReady`），Surface 移动逻辑没动。受保护清单里没有这个文件，CI 不强制；这里自愿声明。
3. **打开播放器从被点的卡片展开**（P1）。`pressable` 在点击时记下自己的窗口坐标（`PressOrigin`，500 ms 有效，取一次就清）；卡片的缩略图用 `pressOriginMedia(model)` 标记，点卡片时记的是缩略图的矩形和图片地址，不是整张卡片。用的是 `Modifier.Node` 遍历（`traverseDescendants` + `requireLayoutCoordinates`），没有给每张卡片挂 `onPlaced`，18 处 `start*` 调用都没改。
   - `MainActivity.openPlayer`：没有播放器时，有坐标就在卡片位置放一张缩略图（活动级、延后建立的 `ImageView`，在播放器之上、吐司层之下），新 Fragment 第一帧画出来时交给视频槽里的封面层、覆盖层同一帧藏起来，然后 `playerSpring` 把视频从卡片矩形（按视频比例、同宽同中心）展开到视频位置；没有坐标就从屏幕底部升起（`LagoonMotion.playerRiseStart`）。覆盖层有 1 s 保险，第一帧没来也会收掉。
   - 冷启动恢复上次播放（P24）不走这条路，照旧直接出现。
4. **播放器里换内容**（P20–P23）。四个入口（直播、视频、片段、离线视频）都改成 `openPlayer`：已有播放器时先截图——视频用 `PixelCopy.request(surfaceView)`，最大化时再用窗口截图（视频位置擦成透明）——两个回调都回来或 100 ms 到了，再 `close` + 替换。新 Fragment 继承旧的模式和视频比例，第一帧出来后窗口截图和旧画面一起淡掉（`contentSwap`）。规则在 `PlayerArrivalRules.swap`：最大化时原位换、忽略坐标槽；迷你开着时点卡片（500 ms 内有点击）就原位换并同时展开；突袭等没有点击的换内容留在迷你状态。截图期间又来了新内容，用最后一个。视频截图失败时退回旧内容的封面（只从缓存取）。

## 2. 模拟器录屏（emulator-5554，swiftshader，约 11–16 帧/秒）

录屏和联系表在 `E:/Projects/Lagoon-audit/motion/`：

| 文件 | 看到的 |
| --- | --- |
| `m2a-open` | Just Chatting 直播列表点第一张卡：封面图从卡片位置往上展开到视频位置，底色同时淡入；返回键最小化时聊天先变暗再消失（以前第一帧就没了） |
| `m2a-swapmini` | 迷你开着点另一张卡：迷你缩略图里旧画面淡成新封面，同时从迷你位置展开 |
| `m2a-swapmax` | 片段播完点“观看完整视频”：旧页面截图盖着新页面淡掉，没有黑屏和信息栏重新冒出来 |
| `m2a-settings` | 迷你开着去设置再返回：迷你缩略图回来第一帧就有画面（以前黑 0.5–1 s） |

看到的限制：模拟器软解码出首帧要 5 s 左右，超过 3 s 超时，所以 `m2a-open` 里封面 3 s 后淡成黑底加转圈，视频再出来——这正是计划要的“超时回到今天的样子”，手机上首帧一般在 1 s 内。模拟器帧率太低，弹簧手感、聊天淡入是否顺都看不出，留给手机 1 的转场基准。

没录的：没有卡片的入口（链接、通知）从底部升起、只听声音/暂停时回到前台不挂封面——这两条由 JVM 测试覆盖（`entranceWithoutACardRisesFromTheBottom`、`coverOnReturnOnlyWithVideoOnAndPlaying` 等），没有在模拟器上录。**广告替换回归没跑**：模拟器上的 debug 包没登录，等一次真实广告要 20–30 分钟；代码上封面只在三个时机挂，广告换 Surface 只会让挂着的封面提前淡掉，挪到手机 1 和 M2b 一起跑。

## 3. 顺带修的

- **画中画崩溃（M1 引入，已在 master）**：吐司层底边距用 steady 弹簧从“导航栏 + 92 dp”回到底部安全区；进画中画时目标是 0 dp，弹簧略微 overshoot 成负数，`padding` 抛 `IllegalArgumentException`。复现：迷你播放器开着按 Home。改成 `coerceAtLeast(0.dp)`。这行和启动计划 S3 的 D1 在同一段，已告诉启动线程。
- `startPlayer` 在状态已保存时用 `commitAllowingStateLoss`：换内容要等最多 100 ms 才提交，期间 Activity 可能已经停了。

## 4. 留给后面的

- 手机 1：转场基准（`TransitionBenchmark` 加“打开播放器”“换内容”两项）、冷启动（M2a 改了打开播放器的路径，但不在冷启动首帧路径上；计划 6.2 要求跑）、广告替换回归、聊天淡入淡出期间硬件层的帧耗时。
- 直播缩略图是列表时刻的预览，和当前画面可能差很多（计划已接受）。
- 换内容时窗口截图是整屏 ARGB_8888（这台模拟器约 16 MB），淡完立即回收。
- 底部弹层里的“观看”等行也是 `pressable`，从弹层打开会从那一行展开（行没有缩略图，按行宽取 16:9）。弹层是 Compose 自己的窗口时坐标可能有偏差，手机 1 顺便看一眼。
