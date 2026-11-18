# 交互动效审计 · 第 2 轮审阅

日期：2026-11-18。审阅对象：`docs/ui-overhaul/motion-audit.md` 修改版 1（PR #37 合入，master `15b5560a`）。流程位置：初稿 → 审阅 1 → 修改版 1 → **审阅 2（本文）** → 修改版 2 → Mitchell 批准。

做法：只读代码（master `15b5560a` 的 `app/src/main`；`git diff ui-u9 15b5560a -- app` 为空，应用代码和 U9 一样），逐条核对修改版 1 第 9 节说“已处理”的地方，再看新写的方案会不会引入新问题。没有跑模拟器、没有碰手机。文中 `文件:行` 都是这个版本的行号。

## 0. 结论

**审阅 1 的四条必须改都接了，方向都对；但其中三条的具体做法和代码事实对不上，修改版 2 要改掉这 5 处（第 2 节 N1–N5）才能交批准。** 改的都是方案细节，不用重排阶段，工作量大约多 0.5 天（M2a）。

| 审阅 1 | 修改版 1 的处理 | 核对结果 |
| --- | --- | --- |
| A 一次性切页标记 | 转场 lambda 读到就清空 + `currentBackStackEntryFlow` 下一次变化时清空 | **方向对，清空的时机不对**（N1）：在 lambda 里清空会让同一次转场的进入/退出一半用切页一半用返回；用异步的 flow 清空可能在转场读之前就清掉 |
| B 聊天透明度不进 `ChatPlacement` | `View.alpha/translationY` 独立通道 + 淡入期间硬件层 + 收键盘提前 | **对**，和 `PlayerFragment.kt:324–343` 的结构吻合（第 1.2 节） |
| C M3 排在启动 S3 之后 | 关注 + 我的拆成 M3b，等 S3 | **顺序对，但“`beyondViewportPageCount = 0` 天然只收集组合了的页”这个前提不成立**（N3）：5 个分页流是在 `FollowingDestination` 里收集的，不在分段里 |
| D 播放器里换内容 | `replace` 前 `PixelCopy` 截窗口，新 Fragment 第一帧后淡掉 | **规则对，截图做法有两处技术问题**（N2）：窗口截图不含 SurfaceView 的视频；`startStream` 在 `replace` 之前已经 `close()` 了旧播放器 |
| 1.2 黑缩略图（封面垫底） | 打开、回到前台时挂封面，首帧淡掉 | **机制对，触发条件和退出条件不够**（N4）：会盖住用户自己选的“只听声音”、暂停的视频永远等不到首帧；封面要只从缓存取 |
| 1.2 设置重建的范围 | 更正为 14 项 + 横屏聊天宽度 + 语言/账号 | **更正是对的**，审阅 1 说错了（第 1.3 节）；但“回来重读”的候选名单里有两项播放器和聊天也在读（N5） |

第 8 节五个决定：**五个都建议按修改版 1 的默认走**，其中第 2、4、5 问带上本文的修正（第 4 节）。

## 1. 逐条核对修改版 1 的修正

### 1.1 A：一次性切页标记（M1 第 3 条）

代码事实都对：`siblingTargetId` 只在 `selectTab` 里写、从不清空（`AppNavigator.kt:18/70`），而且是在 `controller.navigate(...)` **之后**才写；`MainShell.kt:125–134` 的 `enterTransition`/`exitTransition` 都用 `targetState.id == nav.siblingTargetId` 判断，pop 两个不看它。

问题在修改版 1 写的两种清空方式，见 N1。

### 1.2 B：聊天淡入淡出（M2a 第 1 条）

核对通过：

- `placeChat` 只要 `placement != lastChat` 就重设 `layoutParams`、背景色和 `isVisible`（`PlayerFragment.kt:324–343`），`ChatPlacement` 是 data class（`PlayerScreen.kt:128`），`alpha` 不进它是对的。
- 聊天容器是 `FragmentContainerView`，排在 ComposeView **之上**（`PlayerFragment.kt:203–213`）；容器自己和 `ChatFragment` 都没有别的地方写 `alpha`，单独写 `View.alpha` 不会和谁打架。
- 收键盘、清焦点现在在 `placeChat` 的 `!visible` 分支（`:333–335`），修改版 1 说要提前到“开始最小化”，对。

补一句（不必改方案）：聊天容器在 ComposeView 之上，渐隐期间仍是 `VISIBLE`，会挡住它下面的触摸；竖屏时迷你卡片最后落在聊天原来的区域里。修改版 1 的曲线是 `progress` 0 → 0.3 聊天就淡完，之后设 `GONE`，所以只挡几十毫秒，可以接受。修改版 2 写一句“alpha 到 0 时立刻 `GONE`”即可。

### 1.3 设置重建范围的更正（N10）

核对通过，**审阅 1 写错了，修改版 1 的更正是对的**：

- `SettingsStore.write` 只在 `setting.refresh` 时回调（`SettingsStore.kt:108–111`）；`putBooleans`（广告屏蔽预设，`AdBlockPage.kt:175`）甚至不经过 `write`，不触发。
- 外观页 8 项显式 `refresh = true`（`SettingsCatalog.kt:27/32–38`），`TabOrderSetting` 默认 `true`（`SettingSpec.kt:161`），目录里 6 个，合计 14；横屏聊天宽度走 `markChanged()`（`SettingsActivity.kt:275`）；账号走 `EXTRA_ACCOUNT_CHANGED` → `restartActivity()`（`MainActivity.kt:162–164`）。

但 M5 第 3 条里两处小事实要改，见 N5 和第 3 节 S9。

### 1.4 C：M3b 排在启动 S3 之后

先后顺序、“两边 PR 不同日合并”、“手机会话错开”都对。问题见 N3（收集位置）和第 3 节 S6（引用的是启动计划修改版 1，现在已是修改版 2；S3 可能不做）。

### 1.5 D：播放器里换内容（M2a 第 4 条）

规则（播放器已在屏幕上就原位换、保持模式、冷启动恢复不加动画）和入口都核对过：`ChatFragment.kt:291/309`、`PlayerFragment.kt:534/750`、`MainActivity.kt:750–771`（`startStream` 第一行就是 `close(deleteStates = false)`）。做法有问题，见 N2。

### 1.6 封面垫底（M2a 第 2 条，修 P5 情况①）

根因核对通过：`ExoPlayerFragment.onStop` → `ExoPlayerService.stop(isInPIPMode)`，后台音频开着且在播放时切“只听声音”并禁用视频轨（`ExoPlayerService.kt:1725–1742`）；`PlayerConnection.kt:121` 把它同步成 `state.videoDisabled`，`PlayerFragment.kt:254` 再据此把 SurfaceView 设成不可见，所以回来时 Surface 也是新建的。另外 `PLAYER_USE_BACKGROUND_AUDIO_TRACK` 开着时还会换媒体项重新 `prepare`（同一段），回来要重新缓冲，黑得更久。

“只在打开和回到前台时挂、不挂在每次首帧上”也对：广告切换时 `setPlayerSurface` 会把首帧监听器在主/备播放器之间挪（`PlayerConnection.kt:166–180`）。触发和退出条件不够，见 N4。

### 1.7 其余“建议改”项

抽查都落实了：`playerSpring` 阻尼 1.0 + `updateBounds`；`LONG` 注释；`sheetForward` 常量和筛选弹层统一；守护范围（`PlayerScreen.kt:380/577/628×2/1159/1165` 六处 `tween`、`:1169` 不传规格的 `animateTo`、`ChatScreen.kt:283` 两处，和代码一致）；Macrobenchmark 必做；列表级淡入开关；红色背景外层淡入；预测式返回沿用现有回调（`PlayerFragment.kt:150/637–652` 的挂/摘和表情面板先后关系核对过）；`pipParams()`；M3 拆三步、聊天占位、头部规则、平板只翻右栏、外层 `draggable`；M4、M5 各条；数字门槛；三次手机会话；工作量。M3 的字母和审阅 1 不同，修改版 1 第 9.2 节已说明，不影响。

## 2. 修改版 2 必须改的（N1–N5）

### N1 一次性标记的清空时机（M1 第 3 条）

修改版 1 写了两种清空，各有一个问题：

1. **在转场 lambda 里读到就清空**：同一次切页，Navigation 先算进入、再算退出（`enterTransition` 和 `exitTransition` 两个 lambda，两个都用 `targetState.id` 比较）。进入的 lambda 一清空，退出的就读不到，结果是新页“切页淡入”、旧页“前进退出（缩小变暗）”，比现在还怪。预测式返回拖动时 lambda 还可能被重复求值。
2. **`currentBackStackEntryFlow` 下一次变化时清空**：这个 flow 是异步收集的（主线程 `post`），`selectTab` 自己那次导航也会发一次。收集者很可能在下一帧组合（读 lambda）之前就跑了，把刚设的标记清掉，N2 就修不好。

**改法**：不在 lambda 里清，改用同步的 `controller.addOnDestinationChangedListener`：回调里只要 `controller.currentBackStackEntry?.id != siblingTargetId` 就清空。这个回调在 `navigate()` 内部同步触发；`selectTab` 是在 `navigate()` 之后才写标记，所以它自己的那次导航只会清掉上一次的旧标记，新标记保留到下一次导航（进二级页、返回、系统返回手势都会触发回调）才清。系统返回手势不经过 `AppNavigator`，所以必须挂在 controller 上，而不是在 `AppNavigator` 的各个方法里清。JVM 测试照修改版 1 列的三个场景，再加一个“切标签后立刻按系统返回”。

### N2 换内容的截图做法（M2a 第 4 条）

两个问题：

1. **`PixelCopy.request(window, …)` 拷不到视频**。窗口版只拷窗口自己的 Surface；视频在 SurfaceView（`PlayerFragment.kt:185`）的独立图层里，不在窗口缓冲区中，截出来视频区是黑的（或透明）。要单独 `PixelCopy.request(surfaceView, …)` 拿视频画面，再和窗口截图拼起来（或者只截视频，信息栏、聊天在覆盖层下面直接淡入）。M2a 第一次模拟器自测就验证这一点。
2. **截图时机要在 `close()` 之前，不是 `replace` 之前**。`startStream/startVideo/startClip` 的第一行就是 `close(deleteStates = false)`（`MainActivity.kt:751/774/804`），它 `pause` + `stop` 播放器、解绑、`stopSelf`（`ExoPlayerFragment.kt:86–97`）；`PixelCopy` 又是异步的。所以顺序要改成：先发截图请求 → 回调里（或 100 ms 超时退回封面图）再 `close` + `replace`。这会把换内容推迟一两帧，可以接受。修改版 1 写的“`close(deleteStates = false)` 的顺序不变”要改成“`close` 挪到截图回调之后，三个入口和 `startOfflineVideo` 一起改”。

### N3 M3b 的分页流收集位置（M3 开头、M3b）

修改版 1 写“S3 判定 D6 不做时，M3b 自己保证‘只有组合了的页才收集分页流’（`beyondViewportPageCount = 0` 天然如此）”。**这个前提不成立**：关注页 5 个 `collectAsLazyPagingItems()` 都在 `FollowingDestination.Content()` 里（`FollowingDestination.kt:97–103`），不在分段里；把 `AnimatedContent` 换成 `HorizontalPager` 不会改变“5 个都在收集”。游戏页同理（`GamePage.kt:266–269`）。

反过来，如果为了“只收集组合了的页”把收集挪进 Pager 的页里，又会出新问题：`beyondViewportPageCount = 0` 时相邻页每次翻动才组合，`LazyPagingItems` 每次新建，第一帧是空的（`cachedIn` 只保证不重新请求网络，`collectAsLazyPagingItems` 的收集在 effect 里，第一帧照样没数据），翻过去先闪一下空页或骨架再出内容，正好在“翻页第一帧”上。

**改法**：收集留在页面外（`Destination` 层），`LazyPagingItems` 和每页的 `LazyGridState`（关注页已经在 `FollowingScreen.kt:238` 提到页外了）都不随 Pager 页销毁；“只先开当前分段”做成**分段级的“访问过”开关**（第一次停在或翻到某分段时打开，之后一直开），而不是“页正在组合”。这样翻页第一帧有数据、滚动位置不丢，也和启动 S3 的 D6 是同一个开关，两边不重复。刷新和断网恢复只刷新已打开的分段（修改版 1 这一点写对了）。

### N4 封面垫底的触发和退出条件（M2a 第 2 条）

1. **触发**：修改版 1 写“`onStart` 且视频轨之前被关掉过”。用户自己选了“只听声音”画质时视频轨也是关的（`ExoPlayerService.start` 里同样 `setTrackTypeDisabled`），这时挂封面会盖住 `AudioOnlyBackdrop`，而且永远等不到首帧。改成：在 Fragment 的 `onStop` 里记下“离开时视频是开着的”（`!state.videoDisabled`），回来时只有这个为真才挂。只在 Fragment 里记，不碰 `ExoPlayerService`（受保护）。
2. **退出**：除了 `onRenderedFirstFrame`，还要有超时（建议 3 s）和取消条件：播放出错、暂停（暂停的回放回来不一定渲染新帧）、切到只听声音、关闭播放器。超时就直接淡掉，回到今天的样子，不会更差。
3. **图从哪来**：直播的 `thumbnail` 是列表时刻的预览图，回到前台时内存缓存可能已经没了。封面只从 Coil 的内存/磁盘缓存取，用和卡片相同的尺寸键；缓存没有就不挂（不发网络请求，网络比关键帧还慢）。直播封面和当前画面可能差很多，这一点写进报告，接受。

### N5 “回来重读”的候选名单（M5 第 3 条）

修改版 1 说外观页 7 项卡片显示选项“都是 Compose 读 prefs”，可以先改成 `Live`。核对后：

- **名称显示（`UI_NAME_DISPLAY`）和观看数缩写（`UI_TRUNCATE_VIEW_COUNT`）播放器和聊天也在读**：`PlayerLogic.kt:71/72` 建 `PlayerOptions`（`PlayerFragment.kt:132` 只建一次），`PlayerFragment.kt:270`，`ChatFragment.kt:115`，`ChatViewModel.kt:1026/2767`。改成 `Live` 后不重建，开着的播放器和聊天还是旧值。这两项要么维持 `Recreate`，要么 `Live` 的信号同时让播放器重读选项、聊天重读名称显示（多半天）。建议先维持 `Recreate`。
- 直播预览样式（`COMPACT_STREAMS`）除了页面，还在 3 个 ViewModel 里决定 `PagingConfig` 的预取距离（`FollowedStreamsViewModel.kt:30`、`GameStreamsViewModel.kt:58`、`StreamSearchViewModel.kt:41`）。只是预取距离，不重建也不影响显示，可以 `Live`，写明即可。
- 收藏剩余时长（`UI_BOOKMARK_TIME_LEFT`）打开时要触发一次 `updateBookmarkUsers()`（`SavedDestination.kt:105`，只在页面建立时跑）。改 `Live` 时这个调用要跟着值变化重跑。

所以第一批 `Live` 建议是：开播时长、标签、联播人数、直播预览样式、收藏剩余时长（带上重跑）。名称显示、观看数缩写留在 `Recreate`。

## 3. 建议改的（S1–S12）

| # | 哪里 | 意见 |
| --- | --- | --- |
| S1 | M2a 第 3 条，活动级覆盖层 | 不要新加 ComposeView，也不要借 `toastOverlay`（启动计划 S3 的 D1 要把它延后建立）。覆盖层只显示一张静态图，用**第一次用到时才 `addView` 的 `ImageView`**，零启动成本，层级放在 `playerContainer` 之上、`toastOverlay` 之下 |
| S2 | M2a 第 3 条，覆盖层交接时机 | “展开到位时覆盖层交给第 2 条的封面层”有歧义。覆盖层只负责“点击到 Fragment 第一帧”这一段；弹簧**开始**时就交给播放器自己的封面层（它画在视频槽上，跟着 `videoPlacement` 一起缩放），否则覆盖层会停在卡片位置、播放器在它下面展开 |
| S3 | M2a 第 3 条，坐标槽 | 修饰符直接做进 `pressable`（`Press.kt`，43 处调用都会自动带上），在点击回调里取当前 `LayoutCoordinates`，不需要每张卡片 `onPlaced`（滚动时每帧每项都会回调一次，虽然便宜）。另外写明优先级：**已有播放器时一律走换内容**，忽略坐标槽（`P22` 的“看完整视频”按钮、突袭条本身也是 `pressable`，500 ms 内会被记进槽里） |
| S4 | M2a 第 4 条 / 第 8 节第 5 问 | 迷你开着时点新卡片：截图淡出和从迷你展开**同时开始**（新 Fragment 第一帧就开弹簧，截图覆盖层画在视频槽里跟着一起放大），不要“换好再展开”串起来，否则点完要先等约 200 ms 的淡入才开始放大，显得慢。做到这点需要截图覆盖层画在播放器自己的视频槽里（同 S2），而不是活动级的固定位置 |
| S5 | M5 第 3 条，`Recreate` 时的截图 | 重建会销毁 Activity，盖在旧窗口上的 View 活不过 `recreate()`。截图要放在 `MainViewModel`（重建时保留），新 Activity 在 `onCreate` 里先挂上、第一帧后淡掉；这只在“有待显示的截图”时发生，不在冷启动路径上。同 N2，窗口截图不含视频，视频区由 N4 的封面接手 |
| S6 | 第 5 节“和启动优化的先后” | 引用改成启动计划**修改版 2**；冷启动 A/B 的指标改用修改版 2 第 1.2 节的“进程启动到第一帧后 1 秒的主线程总工作量”和“前 10 帧”，门槛用它的同会话交替 + bootstrap 置信区间（“新 / master 中位数比的 95 % 上界 ≤ 1.03”），不用笼统的“±3 %”。测量包名沿用启动计划的 `-Plagoon.perfId=<名字>`（如 `m3b`），不另起 |
| S7 | M3b 的依赖 | 启动计划写明 P1 之后“已经 ≤ 1.06 的话 S2/S3 只做把握中以上的项”，S3 可能只做一部分或不做。M3b 的前置条件改成“启动 S3 合并，**或启动线程宣布 D6 不做 / S3 结束**”。另请在启动计划下次修改（或 S3 的 PR 描述）里加一句反向引用：M3b 会沿用 D6 的“分段访问过”开关（N3），免得启动那边不知道 |
| S8 | M0 第 3 条，对照包 | v0.2 对照包同样用 `-Plagoon.perfId=v02`（得到 `.bench.perf.v02`），不要再起第三套后缀；谁先落地谁实现这个开关（启动 S0 或 M0），另一边复用。转场用例里的选择器只能用 v0.2 里已经有的测试标签或文字（`BaselineComparisonBenchmark` 对 v0.1 就是这么分开处理的），M0 新加的标签 v0.2 没有 |
| S9 | M5 第 3 条，玻璃开关 | “例如玻璃开关 `UI_GLASS` 只在创建时读一次”不对：`GlassPolicy` 监听这个 key 的变化（`GlassPolicy.kt:76`），已经是即时生效的。换个例子或删掉 |
| S10 | M0 第 2 条，守护白名单 | `Media.kt:151` 不是“加载动画”，是**直播圆点呼吸**，正是 G 阶段那次毛玻璃门槛失败的来源（现在靠 `LocalLiveDotBreathing` 在玻璃背景上关掉，`Media.kt:138–157`）。白名单按 `label`（`liveDot`、`ringSpin`、`shimmer`、`refreshSpin`）而不是行号，行号一改就失效。“毛玻璃下不放持续动画”的守护建议直接推广这个现成机制：把 `LocalLiveDotBreathing` 改成通用的“允许持续动画”组合局部值，所有无限动画都通过一个读它的封装函数，守护脚本只禁止在 `design/` 以外直接写 `rememberInfiniteTransition`。这比按文件扫描“玻璃宿主”可靠 |
| S11 | M2b 第 1 条，预测式返回 | 只在 `state.sheet == null` 时推进 `progress`（现有回调有弹层分支，`PlayerFragment.kt:150–153`）；手机横屏全屏时最小化会在 500 ms 后解锁方向（`:657–662`），边拖边缩小再转屏不好看，建议横屏保留普通返回，只在竖屏和平板上预览 |
| S12 | 受保护路径 | CI 的受保护清单（`tools/ci/protected_paths.py`）里没有 `PlayerConnection.kt`，修改版 1 把它当受保护路径写声明是自愿从严，可以保留，但写明“CI 不强制”。另外 M0 的守护脚本放在 `tools/ci/`，属于 `ci` 区域，M0 的 PR 描述要带受保护代码声明 |

## 4. 第 8 节五个决定的建议

1. **频道页/游戏页左右滑：真翻页，分三步（同修改版 1 默认）。** 理由不变（只加甩动会造成关注页跟手、频道页不跟手的新不一致，真翻页顺带修好 X2）。前提是按 N3 把分页流收集留在页外、用“分段访问过”开关，否则翻页第一帧会闪空页。
2. **改设置回来不整屏重建：做保守版（同默认），但第一批“回来重读”的名单按 N5 收窄**（开播时长、标签、联播人数、直播预览样式、收藏剩余时长），名称显示和观看数缩写先留在“必须重建”；重建时的截图按 S5 放在 ViewModel 里跨过重建。工作量仍约 1 天。
3. **M6 共享元素：这次不做（同默认）。** 维持 U8 的决定；M3 改结构、M5 改返回之后再有基准数据，到时再评估。
4. **打开播放器：从最后一次点击的卡片展开（同默认），带上 S1–S3 三处修正**：覆盖层用延后建立的 `ImageView`，弹簧开始时就把封面交给播放器自己的视频槽，坐标取自 `pressable` 点击时刻，已有播放器时一律走换内容。取不到卡片从底部升起，迷你开着时从迷你位置展开。
5. **迷你开着时点另一张卡片：原位换内容再从迷你位置展开（同默认），但两步同时进行**（S4）：新播放器第一帧出来就开始放大，旧画面的截图在放大的过程中淡掉。这样仍然只有一个视觉焦点，又没有“先等淡入再放大”的停顿。

## 5. 给修改版 2 的清单

必须：

- [ ] N1：一次性标记改用 `addOnDestinationChangedListener` 同步清空，不在 lambda 里清，不用异步 flow；加“切标签后立刻系统返回”测试
- [ ] N2：换内容截视频要 `PixelCopy.request(surfaceView, …)`；截图请求放在 `close()` 之前，`close` + `replace` 挪到回调（带超时）之后，四个 `start*` 入口一起改
- [ ] N3：M3b（和 M3a）的分页流收集留在页外，“只先开当前分段”做成分段级“访问过”开关；删掉“`beyondViewportPageCount = 0` 天然如此”
- [ ] N4：封面只在“离开时视频开着”时挂；加 3 s 超时和出错/暂停/只听声音/关闭时的取消；只从图片缓存取
- [ ] N5：`Live` 第一批去掉名称显示和观看数缩写（或写明播放器、聊天也要重读）；直播预览样式写明 ViewModel 的预取距离；收藏剩余时长要重跑 `updateBookmarkUsers`

建议：

- [ ] S1–S5：覆盖层用延后建立的 `ImageView`；弹簧开始即交接封面；坐标槽做进 `pressable` 并写明“已有播放器优先走换内容”；迷你点新卡片时淡出和展开同时进行；`Recreate` 截图放 `MainViewModel`
- [ ] S6–S8：引用启动计划修改版 2 的指标和 bootstrap 门槛；M3b 前置条件加“或 S3 宣布结束”，并请启动计划加反向引用；对照包名沿用 `-Plagoon.perfId`，转场用例只用 v0.2 已有的选择器
- [ ] S9–S12：删掉 `UI_GLASS` 的例子；白名单按 `label`，持续动画守护推广 `LocalLiveDotBreathing`；预测式返回只在无弹层、竖屏/平板时预览；写明 `PlayerConnection` 的声明是自愿的、M0 的 PR 要带 `ci` 声明
- [ ] 第 1.2 节：写一句“聊天 alpha 到 0 时立刻 `GONE`”
- [ ] 工作量：M2a 改成 3–3.5 天（N2 的截图拼接、N4 的条件），合计仍在 15.5–18.5 天
