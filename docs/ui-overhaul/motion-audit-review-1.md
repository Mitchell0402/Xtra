# 交互动效审计 · 第 1 轮审阅

日期：2026-11-18。审阅对象：`docs/ui-overhaul/motion-audit.md`（PR #31 合入，master `dfeded43`）。流程位置：初稿 → **审阅 1（本文）** → 修改 → 审阅 2 → 修改 → Mitchell 批准。

做法：只读代码（master `dfeded43` 的 `app/src/main`），逐条核对初稿第 3 节的诊断和第 5 节方案依赖的代码事实；没有跑模拟器、没有碰手机（同一台电脑上有启动优化的审阅在用模拟器）。文中 `文件:行` 都是 master `dfeded43` 的行号。

## 0. 结论

**方向对，可以按这个骨架改第二稿；有 4 处必须在第二稿里改掉才能批准。**

初稿的盘点大体准确：我抽查的 30 多条诊断都和代码对得上（见第 1 节），问题排序（播放器、聊天硬切、翻页退步排前面）也合理。但有几处方案本身会引入新问题，或者漏了会撞车的地方：

| # | 必须改 | 为什么 |
| --- | --- | --- |
| A | M1 第 3 条（切回关注页走切页动画）的做法会把**普通返回**也变成切页动画 | `siblingTargetId` 从不清空（`AppNavigator.kt:18/70`）。让 `popEnterTransition` 也查它以后，“浏览 → 游戏页 → 返回浏览”的返回目标正是当时记下的那个条目 id，会错用 `sibling`。标记要做成用一次就清空的 |
| B | M2 第 1 条把 `alpha` 加进 `ChatPlacement` 会让聊天**每帧重新布局** | `placeChat` 只要 `placement != lastChat` 就重设 `layoutParams`（`PlayerFragment.kt:325–338`）。`alpha` 每帧变，整个 U7 聊天列表每帧 `requestLayout` + 重新测量。透明度和位移要走 `View.alpha/translationY`，不进比较 |
| C | M3 翻页和启动优化 S3 改的是**同一段代码** | 启动计划 S3 要把关注页的 5 个分页流改成“先开当前分段”（`docs/perf/startup-plan.md` 第 71、112 行）；M3 要把同一个 `AnimatedContent` 换成 `HorizontalPager`。两边各改各的必然冲突，数字也互相污染。第二稿要写明先后（建议 S3 先合，M3 基于它） |
| D | 漏了“播放器里换内容”这一整类转场 | 突袭自动跳转、点突袭、片段“看完整视频”、未公开回放都走 `startStream/startVideo`，旧 Fragment 被直接 `replace`（`ChatFragment.kt:291/309`、`PlayerFragment.kt:534/750`、`MainActivity.kt:857`）。M2 的“从卡片展开”不能用在这里，要单独定义（见 2.1） |

其余是“建议改”：漏项补进盘点、几处诊断补根因、性能门槛写成数字、工作量上调。第 6 节是给第二稿的逐条清单。

## 1. 诊断核对

### 1.1 和代码对得上的（抽查）

| 条目 | 核对结果 |
| --- | --- |
| N2 | 对。`selectTab` 是 `navigate(...) { popUpTo(start){saveState}; launchSingleTop; restoreState }`，回到起点是一次 pop，`MainShell.kt:133–134` 的 pop 转场不看 `siblingTargetId` |
| N5 | 对。`back()` 进入只有 `fadeIn(initialAlpha = 0.4f)`，没有 `scaleIn`（`LagoonMotion.kt:79–83`） |
| N10 | 对，而且比写的更频繁：`SettingsStore` 任何一项改动都会 `publishResult()` → `RESULT_OK`（`SettingsActivity.kt:119–122`）→ 主界面 `recreate()`（`MainActivity.kt:166`）。也就是**改任何设置**回来都整屏重建 |
| N11 | 对，`FLAG_ACTIVITY_NO_ANIMATION` + `makeCustomAnimation(0,0)`（`MainActivity.kt:524–531`） |
| S3/S4 | 对。频道页、游戏页是一个网格换内容（`ChannelScreen.kt:434–`），切分类时 `scrollToItem(1)`（`ChannelScreen.kt:269–272`、`GameScreen.kt:251–254`） |
| S7 | 对。`pressable` 用的是 `combinedClickable`（`Press.kt:62`），手指在卡片范围内横移不会取消点击，外面也没有横向滚动容器来抢手势 |
| P1 | 对。`progress` 初值按 `mode` 取（`PlayerScreen.kt:370`），新 Fragment 默认最大化，所以第一帧就是 0 |
| P2/P4/P17 | 对。`maximized = p == 0f && …`（`PlayerScreen.kt:383`），`p` 一离开 0 聊天就 `visible = false`。补一句：露出的“黑区”是 `Canvas` 画的页面底色（深色主题接近黑），不是窟窿；修法不变 |
| P3/P6/P11/C1 | 对。播放器里 6 个写死的 `tween`（`PlayerScreen.kt:380/577/628×2/1159/1165`），聊天 2 个（`ChatScreen.kt:283`）。另外 `PlayerScreen.kt:1169` 的 `dismiss.animateTo(0f)` 没传规格，用的是 Compose 默认弹簧，也要收 |
| P7 | 对，`TRANSIT_FRAGMENT_FADE`（`MainActivity.kt:875`） |
| P9 | 对，`OnBackPressedCallback`（`PlayerFragment.kt:150`），只在最大化时挂上 |
| P10 | 对。4 处 `setPictureInPictureParams` 都没有 `sourceRectHint`/宽高比（`MainActivity.kt:867/883`、`PlayerFragment.kt:1041/1047/1315`） |
| P18 | 对。`PlayerSheetHost` 只有淡入淡出，没有 `BackHandler`（`PlayerSheets.kt:100–122`）；筛选弹层有（`FilterSheet.kt:191`） |
| L2 | 对，`staggeredAppear` 的 `enabled` 没有任何调用方传（17 处调用都只传 `index`）。补充：因为动画状态是 `remember` 在每一项里的，**列表滚走再滚回来**也会重放；搜索页边打字边出结果时，**每敲一个字**前 12 项都重放一遍 |
| L3 | 对，`SwipeToDelete` 的背景常驻（`Progress.kt:83–85`），内容半透明就透出来 |
| T2/T3 | 对，`key(route)` 硬换（`SettingsScreen.kt:232`），普通 `BackHandler`（`SettingsScreen.kt:195`） |
| L9 | 初稿标 ✓ 是对的：两处 `animateContentSize` 都传了 `Lagoon.motion.move()` |

### 1.2 诊断要补根因的

- **P5 / N10 迷你缩略图黑一阵**：“从设置回来黑 0.5–1 s”在代码里有明确原因，不是渲染问题。主界面 `onStop` 时 `ExoPlayerFragment.onStop` → `backend.stop(isInPIPMode)`，后台音频开着（默认开）就切到“只听声音”并 `setTrackTypeDisabled(VIDEO, true)`（`ExoPlayerService.kt:1725–1742`）；回来再打开视频轨，要等下一个关键帧才有画面。所以：
  - 这一类黑屏是 v0.1 就有的播放逻辑，不是 U 阶段引入的；
  - M2 第 3 条的 `PixelCopy` 截帧能盖住它，但更省事的是复用 M2 第 2 条的“封面图垫底，`onRenderedFirstFrame` 后淡掉”：同一个机制同时解决打开播放器和回到前台两种黑屏，不用在 Surface 销毁前抢时间截图（`surfaceDestroyed` 回调里做异步 `PixelCopy` 本身就有竞争）；
  - “最小化之后黑一阵”在代码里找不到对应的轨道切换（`updateQuality` 只更新标签，`PlayerConnection.kt:268`），更像模拟器 swiftshader 下 SurfaceView 跟随父 View 缩放时的合成延迟。**先在手机上确认再排工作量**，初稿已经这么写了，保留。
- **C7 进入直播时聊天历史跳动**：同意先确认是不是只在模拟器上。
- **N6**：Navigation 2.10 的预测式返回依赖清单里的 `enableOnBackInvokedCallback="true"`，已经开了（`AndroidManifest.xml:19`），`targetSdk = 37`。没问题，第二稿可以写一句，免得后面怀疑。

## 2. 盘点漏掉的

### 2.1 播放器里换内容（必须补，见结论 D）

| 新条目 | 现在的样子 | 代码 |
| --- | --- | --- |
| 最大化时突袭自动跳转（`CHAT_RAIDS_AUTO_SWITCH`，默认开） | 整个播放器 Fragment 被替换：黑屏，信息栏和聊天重新冒出来，聊天重连 | `ChatFragment.kt:291`、`MainActivity.startStream` |
| 点突袭条跳转 | 同上 | `ChatFragment.kt:309` |
| 片段 → 看完整视频；未公开回放 | 同上 | `PlayerFragment.kt:750/534` |
| 迷你播放器开着时点另一张卡片 | 旧迷你卡片瞬间消失，新播放器从最大化状态冒出 | `startStream` 先 `close` 再 `replace` |
| 冷启动恢复上次播放 | 迷你卡片直接出现 | `restorePlayerFragment` |

建议规则：**播放器已经最大化时换内容 = 在原位交叉淡入**（旧画面不动，新的封面图在视频槽里淡入，信息栏用 `sibling` 换，聊天区淡出淡入），不走展开；**迷你开着时点新卡片 = 旧迷你卡片淡出 + 新的从卡片展开**；**冷启动恢复 = 不加动画**（不在启动路径上加东西，和启动优化不冲突）。这三条要写进第 7 节的规则表。

### 2.2 交互逻辑不一致（用户目标的第二半，初稿只盘了动画）

| 新条目 | 不一致在哪 | 代码 |
| --- | --- | --- |
| 迷你播放器不能上滑展开 | 最大化可以下拉缩小，反过来只能点；不开“自由移动”时竖向拖动什么都不做 | `PlayerScreen.kt:1150–1178` 只认横向 |
| 频道页/游戏页切分类丢滚动位置 | 关注页每个分段各记各的位置（`FollowingScreen.kt:238`），频道/游戏页共用一个网格，切过去就回到第 1 项 | `ChannelScreen.kt:247/272`、`GameScreen.kt:227/254` |
| 搜索“查看全部”之后按返回直接离开搜索页 | “查看全部”是往下钻一层，返回却不回“全部” | `SearchScreen.kt:188/289`，页面里没有 `BackHandler` |
| 横屏打字时聊天宽度一帧跳到屏幕的 1/1.8 | 键盘出现时 `chatWidth` 直接换值 | `PlayerScreen.kt:248` |
| 从播放器点频道名/游戏名 | 页面 `forward` 和播放器最小化同时开始，两套时长（300 ms tween / 250 ms tween） | `PlayerFragment.kt:690–700` |

前三条建议进 M3/M2，后两条进 M2 第 6 条和 M1 的数值收口。

### 2.3 平板和横屏

- 平板可以录：U9 已经验证过 `wm size 2560x1600` + `wm density 320` 在模拟器上能出平板形态，横屏用 `wm size 2992x1344`。第二稿建议把平板也录一遍（设置两栏、播放器全屏切换、频道页两栏），不要只看代码。
- **M3 没写平板/两栏形态**：频道页在宽屏是“左边信息栏 + 右边网格”（`ChannelScreen.kt:207/319`），翻页只能放在右栏；关注页在侧栏形态下翻页区域也要从侧栏右边开始，别让侧栏跟着翻。
- 分屏/折叠屏改窗口宽度时胶囊 ↔ 侧栏瞬间互换（`MainActivity` 自己处理 `screenSize|orientation`，`AndroidManifest.xml:32`）。系统的窗口缩放动画会盖住大半，低优先级，列进盘点即可。

## 3. 方案逐阶段意见

### M0 规范和守护

- **`playerSpring` 不能直接等于 steady**。steady 阻尼 0.85 有约 0.6 % 的过冲：`progress` 冲到 0 以下时视频比槽位大一圈、冲到 1 以上时迷你卡片的 `alpha` 和位置会越界。要么播放器的进度弹簧用阻尼 1（不过冲），要么 `Animatable.updateBounds(0f, 1f)`。卡片展开到全屏这种大位移，不过冲更稳。
- **和 U1 的 `LONG` 对齐**：U1 写的是“缩略图展开成播放器 = `LONG`”，初稿第 7 节改成了弹簧。我同意用弹簧（可打断、能接手指速度），但要在 M0 里把 `LONG` 的注释改掉，免得规范自相矛盾。`LONG` 留给共享元素（Browse 海报，以后的 M6）。
- `sheetForward/sheetBack` 的“1/4 宽”要做成 `LagoonMotion` 的常量（像 `SiblingShift`），不是写在调用处。注意它也会改到筛选弹层（现在用的是整页 `forward/back`），这是有意的统一，第二稿写明。
- **守护检查的范围要写全**：除了 `tween(`/`spring(`/`keyframes(`，还要抓：不传规格的 `animateTo(x)`（`PlayerScreen.kt:1169` 就是）、不传 `enter/exit` 的 `AnimatedVisibility(`（`LoginScreen.kt:184`）、不传规格的 `animateItem()`/`animateContentSize()`。白名单要列：`Media.kt:151/234`、`States.kt:70/259` 四个无限循环，以及 `BrowseDetail.kt:112/156/160`（数值来自常量，但应该改成走 `LagoonMotion` 的函数；`:112` 的共享元素边界在“移除动画”时也还在动，是个小 bug）。
- **再加一条守护**：毛玻璃（`GlassBackdrop`）下面不许放持续运行的动画。G 阶段真机门槛第一次没过就是因为直播圆点呼吸让毛玻璃每帧重模糊；M4 要加的投票/竞猜条如果带倒计时进度条，就是同一种坑。
- **转场 Macrobenchmark 从“可以加”改成 M0 的必做项**。后面每个阶段的验收都要手机数据，而 Mitchell 2026-11-17 说过暂时不测手机、无线调试也经常掉线。有一个固定的 `:benchmark` 用例（切标签、进出频道页、播放器最大化/最小化、M3 后加翻页，`FrameTimingMetric`），每次连上手机一条命令跑完，比每阶段手工 `gfxinfo` 省人力、也可比。

### M1 修错

- **第 1 条（逐项淡入只放一次）**：“每个分类在 ViewModel/`rememberSaveable` 记一个已展示过”方向对，但要做成**列表级**的开关，而不是每项的 `remember`：一个 `rememberStaggerGate(key)`，在“这个列表第一次从 0 项变成有数据”的那一帧打开，`StaggerLimit × StaggerMs + SHORT` 之后关上，整个列表共用。这样一并解决滚回顶部和搜索逐字重放。还要考虑 M3：翻页时离屏的页会被销毁，开关要放在页外面（ViewModel 或页面级 `rememberSaveable`），不然每翻一次就重放一次。
- **第 2 条（红色删除背景）**：更简单的修法是把 `staggeredAppear` 挪到 `SwipeToDelete` 外层，让背景和内容一起淡入；不用读 `dismissDirection`。
- **第 3 条（切回关注页）**：见结论 A。标记改成一次性：`selectTab` 记下目标条目 id，`MainShell` 的四个转场 lambda 读到匹配后清空（或在 `controller.currentBackStackEntryFlow` 下一次变化时清空）。补一个 JVM 测试：选标签 → 进二级页 → 返回，返回必须用 `back()`。
- **第 5 条（× 关闭）**：同意。顺带把 `onDragCancel` 的默认弹簧收掉。
- 第 4 条“下拉松手带速度”：`progressAnim.animateTo(target, spec, initialVelocity = v / dragRange)`；注意 `PlayerScreen.kt:567` 的注释说快速甩动时 `snapTo` 还排在队列里，改的时候要保证速度取自最后一次 `snapTo` 之后。

### M2 播放器

- **第 1 条（聊天跟着进度淡入淡出）**：见结论 B。实现建议：`ChatPlacement` 保持“布局”含义不变；另开一个只在 `SideEffect` 里写 `chat.alpha`/`chat.translationY` 的通道。聊天是含整列 Compose 列表的 View，`alpha < 1` 时每帧要离屏合成一次；淡入淡出那 300 ms 里给它 `LAYER_TYPE_HARDWARE`，结束时还原，这是 View 系统做淡入的标准做法。还有一点：现在 `visible = false` 时会收起键盘、清焦点（`PlayerFragment.kt:333–335`），改成渐隐后这个动作要留在“开始最小化”那一刻做，不能等透明度到 0。
- **第 2 条（从卡片展开）**：
  - **起点矩形不要每个入口传**（这就是第 8 节第 4 问，见第 5 节）：卡片组件在 `onPlaced` 里只存 `LayoutCoordinates` 的引用（不写状态、滚动时不重组），点击那一刻才算 `boundsInWindow()`，放进一个活动级的“最近一次点击的卡片”槽（带时间戳，超过 500 ms 作废）；`startPlayer` 取得到就用，取不到就走兜底。入口一共 18 处调用，其中 `PageBrowseActions` 已经把大部分收在一起，但这个做法连它都不用改。
  - **动画要等 Fragment 第一帧**：新 Fragment 建 View、ComposeView、SurfaceView、绑服务都压在点击后的头几帧上。如果展开动画从点击那一刻开始计时，头 100 ms 会被吃掉，看起来还是跳。建议：点击后立刻在卡片位置显示封面图（Compose 层，在 SurfaceView 之上），Fragment 第一帧画出来后（`withFrameNanos`）再开始弹簧。这一步要单独量“点卡片到展开结束”的帧。
  - **兜底**：初稿的兜底是“从迷你卡片位置展开”。没有迷你播放器的时候（链接、通知）从右下角一个并不存在的卡片展开有点怪；建议兜底改成从底部升起（同一套 `progress` 机制，起点设成屏幕底部中间的虚拟矩形），迷你播放器开着的时候才从迷你位置展开。
  - **广告换 Surface 时不能触发封面**：highlighter/备用播放器切换 Surface（`PlayerConnection.kt:169–177`）也会出新的第一帧。“首帧到了淡掉封面”的逻辑只能在打开和回到前台时挂，不能挂在每次 `onRenderedFirstFrame` 上，否则广告切换时封面会闪一下。这条要写进 M2 的受保护路径声明。
- **第 4 条（预测式返回）**：不用换成 Compose 的 `PredictiveBackHandler`。现有的 `OnBackPressedCallback` 可以直接覆写 `handleOnBackStarted/Progressed/Cancelled`（activity 1.13），保持现在“最大化时挂、最小化时摘”的生命周期，也保留和聊天表情面板回调（`toggleBackPressedCallback`）的先后关系：表情面板开着时返回应该先关面板，不能被播放器的拖动抢走。
- **第 5 条（画中画）**：4 处 `setPictureInPictureParams` 收成一个 `pipParams()` 构造函数，统一带 `sourceRectHint`、`setAspectRatio`、`setSeamlessResizeEnabled(false)`（视频内容官方建议关掉无缝缩放）；`sourceRectHint` 用**画面实际区域**（去掉黑边后的视频矩形），宽高比要和 `setAspectRatio` 一致，否则系统会裁。迷你播放器开着时回桌面也会自动进画中画，这时提示矩形是迷你缩略图的位置。
- **第 6 条（横屏聊天开关）**：思路对（动画期间缩放 + 位移，结束再定尺寸）。注意横屏有“缩放模式”设置（`state.resizeMode`，裁切/拉伸），等比缩放只在 FIT 模式下和终态一致，其他模式结束时会有一下跳，要接受或单独处理。
- 补 2.1 的“换内容”规则和 2.2 的“迷你上滑展开”。

### M3 左右滑

- 见结论 C：**排在启动优化 S3 之后**，并沿用 S3 的“只先开当前分段的分页流”。`beyondViewportPageCount = 0` 和这个方向一致，可能还帮启动。
- **翻页开始那一帧的成本被低估了**：`beyondViewportPageCount = 0` 意味着相邻页在手指一动的那一帧才开始组合，一个带图片的分页网格在这一帧里建出来，正好是用户最容易感觉到的“起手卡一下”。建议默认 0，但在页面静止后空闲时预组合相邻一页（`Pager` 的 prefetch 或 `beyondViewportPageCount = 1` 只对轻量页开），并把“翻页第一帧”单独作为测量项。
- **频道页“聊天”分段**：它在第 4 位（推荐/视频/剪辑/**聊天**/关于，`ChannelLogic.kt:11`），里面是 `AndroidFragment<ChatFragment>`（`ChannelDestination.kt:98`）。放进 Pager 后，划过去的过程中要建 Fragment 并连聊天，划走又销毁断开。建议：翻页过程中聊天页只画一个轻量占位（标题 + 骨架），停稳在聊天页才挂 Fragment；离开后保留 Fragment 到页面退出为止（不要每次划走都断开）。聊天页里输入框、表情面板的横向手势不触发翻页，初稿已写。
- **头部折叠的规则要写清**：频道页头部很高，各页滚动位置不同。头部偏移是全页共用的，切到一个还没滚过的页时，这一页的列表顶部会露在半折叠的头部下面。v0.1 的 AppBarLayout + ViewPager 也是这样，可以接受，但第二稿要写明选哪种（保持头部偏移 / 切页时头部展开），并说明胶囊“滚动隐藏”在切页后跟哪一页的滚动。关注页的大标题栏比这简单得多，“照搬”只能照搬嵌套滚动的接法。
- **S7 误触的修法**：不建议改 `pressable` 去自己判断移动距离（要换掉 `combinedClickable`，丢无障碍语义、要重写长按）。翻页容器接管横向手势后点击自然会被取消；没有翻页的页面（搜索、热门直播、浏览网格）在列表外层挂一个空的横向 `draggable`，越过触摸阈值就消费掉事件，子项的点击随之取消。效果一样，改动集中。
- 搜索分组：同意不做翻页、只加 `sibling`。再加上 2.2 的返回逻辑（从“查看全部”进来的，返回先回“全部”）。
- 工作量：频道页改结构 + 聊天页规则 + 平板两栏 + 截图基线重录（结构变了，基线会动）+ 新的交互测试，3–4 天偏紧，建议写 4–5 天，分 M3a（关注、我的）/ M3b（游戏）/ M3c（频道）三个 PR，M3c 超时就按退路走。

### M4 聊天与列表

- C6 表情面板 ↔ 键盘：`imeAnimationSource/Target` 还是实验 API。更稳的做法是记住上一次键盘高度，面板直接用这个高度，切换时面板和键盘高度相同，输入框就不跳；键盘动画本身已经通过 `WindowInsets.ime` 每帧跟着走（`PlayerScreen.kt:345`）。
- 第 4 条只给用户操作的列表加 `animateItem`，同意。关注页直播列表刷新后重排（按观众数）也会跳位，这属于分页列表，维持不加，第二稿写一句是有意的。
- 新增的投票/竞猜条不要带持续动画（见 M0 的守护）。

### M5 Activity、返回、弹层

- `overrideActivityTransition` 和跨 Activity 预测式返回的关系要先在手机上验证（只有 Android 15+ 的预测式返回才会用自定义的关闭动画），初稿已写“要确认”，保留。
- **弹层用 `motionScheme` 这条路更好**：还在用的 Material 组件只剩 `ModalBottomSheet`、`Switch`、`Slider/RangeSlider`、`SwipeToDismissBox`、下拉刷新、进度条，在 `LagoonTheme` 的 `MaterialTheme(motionScheme = …)` 里配一次（空间类 = steady，效果类 = `fade`），一处改全部生效。要确认 BOM 2026.09 里的 `ModalBottomSheet` 显示/隐藏确实读 `motionScheme`；不读的话再退回给 `SheetState` 传规格。
- 第 3 条“不整屏重建”见第 5 节第 2 问。

### M6 共享元素

见第 5 节第 3 问。

## 4. 规范一致性和性能

### 4.1 和 U1 动效规范、毛玻璃设计

- 数值体系：初稿第 7 节的规则表和 `LagoonMotion` 一致，除了 M0 里说的 `LONG` 注释和 `playerSpring` 过冲两处。
- 毛玻璃：胶囊、分段按钮是玻璃，弹层不透明（G 阶段定的）。M2 不会让玻璃下面的内容动（播放器是盖在外壳上的独立 View），不增加模糊成本；M3 翻页时玻璃下面的页面在动，每帧重模糊，和滚动同级，G 阶段量过滚动可接受（真机卡顿 1.78 % vs 1.45 %），翻页要用同样的方法量。分段按钮高亮块跟着翻页偏移走，块本身在玻璃里，不额外增加模糊。
- “移除动画”：`LagoonTheme` 只在创建时读一次系统开关（`LagoonTheme.kt:52`），应用开着时切开关要等界面重建才生效。影响很小，记一笔即可，不用改。

### 4.2 性能门槛写成数字

初稿说“不高于 v0.2”“不高过 1 个百分点”，建议第二稿统一成：

| 项目 | 方法 | 门槛 |
| --- | --- | --- |
| 转场卡顿 | M0 的转场 Macrobenchmark，`FrameTimingMetric`，每项 ≥ 10 次，和 v0.2 交替装、交替跑（沿用冷启动的公平做法） | 卡顿帧比例 ≤ v0.2 + 0.5 个百分点，`frameOverrunMs` P90 ≤ v0.2 + 2 ms |
| 翻页 | 同上，加“翻页第一帧”单独统计 | 同上；**对照组用 v0.1 的 ViewPager 翻页**（v0.2 没有翻页，拿点按钮切换比不公平） |
| 静止 | `dumpsys gfxinfo` 静止 10 s | 0 帧（G 阶段的门槛） |
| 播放丢帧 | `u7/phoneframes.py`，gaules 1080p60 5 分钟 | 在 v0.2 的波动范围内（v0.2 实测 11.7 %，v0.1 10.5 %，区间重叠） |
| 聊天压测 | `chatstress9.sh` 30 条/秒 | 卡顿帧 < 5 %，和 v0.2 持平 |
| 冷启动 | `coldstart7.py`，M3 和 M2（播放器首帧路径）必跑 | 和当时的 master 相比在噪声内（±3 %），不能吃掉启动优化的成果 |

手机门槛按阶段打包：M1+M2 一次、M3 一次、M4+M5 一次，每次提前和 Mitchell 约时间。模拟器只验“有没有、方向对不对”，初稿这点说得对。

### 4.3 测试

初稿只写了录屏和截图基线。建议每阶段补对应的 JVM/Robolectric 测试：M1 的一次性切页标记、逐项淡入只放一次；M2 的 `PlayerGeometryRules`（纯函数，聊天透明度随 `progress` 的曲线、起点矩形插值）；M3 的“横滑换分段、点按钮仍然能切、横滑卡片不触发点击”（`InteractionTest` 已有框架）。

## 5. 第 8 节四个问题的建议

1. **频道/游戏页：改结构做真翻页。** 理由：只加甩动手势和淡入，会造成“关注页内容跟手、频道页不跟手”，正是这次要消掉的不一致；改结构顺带修好 2.2 的“切分类丢滚动位置”。按 M3a/b/c 分三步做，游戏页（3 个列表分段、没有聊天）先行验证做法，频道页最后、按 3 节的聊天页规则做；频道页超出预算再退回初稿的退路。工作量按 4–5 天算。
2. **改设置回来不整屏重建：这次做，但做保守版。** 设置已经是声明式目录（`SettingsCatalog`），给每一项加一个“需要重建”标记，**默认是需要**，只把验证过能即时生效的常用项（列表显示选项、聊天显示选项这类 Compose 读 prefs 的）改成不需要；回来时只有带标记的项变了才 `recreate()`，并在重建前盖一层截图淡掉。好处：改设置是高频操作，现在每次都重建主界面、重建播放器（再加 1.2 的黑屏），观感和性能都差；默认“需要重建”保证不会出现改了没生效。工作量约 1–1.5 天。更彻底的办法是把设置搬进主界面的 `NavHost`（N9、N10、T3 一起消失），但牵涉登录流程和 WebView，留给以后。
3. **M6 共享元素：这次不做，维持 U8 的决定。** 理由：要在 `NavHost` 外包 `SharedTransitionLayout`，和 M3 的翻页、预测式返回会互相影响，最好等 M3、M5 稳定、有了转场基准数据再评估。第 5 节第 4 问的“卡片坐标槽”已经覆盖了最常用的“卡片 → 播放器”，其余页面间的共享元素收益小。
4. **打开播放器：做“从卡片展开”，但不按入口一个个传矩形。** 用第 3 节 M2 里说的“最近一次点击的卡片坐标槽”：卡片组件统一挂一个修饰符，点击时记坐标，`startPlayer` 取用，取不到走兜底。这样不改 18 处调用，改动集中在卡片组件和 `PlayerScreen`，约 1–1.5 天，比初稿的“入口多、改动散”省，比只做兜底效果好得多。兜底按第 3 节建议从底部升起。

## 6. 给第二稿的清单

必须：

- [ ] A：M1 第 3 条的标记改成一次性，并写测试（1.1 N2、3 节 M1）
- [ ] B：M2 第 1 条的透明度不进 `ChatPlacement` 比较，淡入期间开硬件层，收键盘提前到开始最小化时（3 节 M2）
- [ ] C：写明 M3 排在启动优化 S3 之后，关注页沿用 S3 的分页流延后（3 节 M3）
- [ ] D：补“播放器里换内容”五种情况和规则（2.1）

建议：

- [ ] 2.2 的交互不一致五条进盘点和对应阶段（迷你上滑展开、频道/游戏页记滚动位置、搜索返回、横屏打字聊天宽度、点频道名时两套时长）
- [ ] P5/N10 的根因（后台关视频轨）写进诊断，M2 第 3 条改成“封面垫底”优先，`PixelCopy` 作备选
- [ ] M0：`playerSpring` 不过冲；改 `LONG` 注释；`sheetForward` 位移做成常量；守护覆盖不传规格的 `animateTo/AnimatedVisibility/animateItem/animateContentSize`，白名单写全；新增“毛玻璃下不放持续动画”；转场 Macrobenchmark 改为必做
- [ ] M1：逐项淡入做成列表级开关，放在页外；红色背景改为外层淡入
- [ ] M2：展开等 Fragment 第一帧再开始；兜底从底部升起；广告换 Surface 不触发封面；预测式返回用现有回调的 progress 接口；画中画参数收成一个函数并带 `sourceRectHint`/宽高比/关无缝缩放
- [ ] M3：分 M3a/b/c；翻页第一帧单独测、相邻页空闲时预组合；频道聊天页占位规则；头部折叠和胶囊规则；平板两栏只翻右栏；S7 用外层横向 `draggable` 而不是改 `pressable`；工作量改 4–5 天
- [ ] M4：表情面板用记住的键盘高度；写明分页列表刷新重排不加动画是有意的
- [ ] M5：优先 `motionScheme`，确认 `ModalBottomSheet` 读它
- [ ] 第 6 节性能门槛换成 4.2 的数字表；手机测试按阶段打包
- [ ] 每阶段补 4.3 的测试
- [ ] 平板用 `wm size 2560x1600` + `density 320` 实录一遍
- [ ] 工作量合计上调到约 15–18 天（不含 M6），并单列手机测试的时间
