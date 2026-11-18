# 交互动效审计与改进计划（修改版 2）

状态：**修改版 2，已批准**（Mitchell，2026-11-18：五个决定全部按建议默认）。回应 `docs/ui-overhaul/motion-audit-review-2.md`（PR #38）。流程：初稿（PR #31）→ 审阅 1（PR #34）→ 修改版 1（PR #37）→ 审阅 2（PR #38）→ **修改版 2（本文）** → Mitchell 批准。审阅 2 的结论是“改掉 N1–N5 就交批准”，所以第 8 节五个决定按审阅 2 的建议写成了默认做法，标着“待批准”；Mitchell 批准（或改）之后才动代码。各条审阅意见怎么处理的见第 10 节（修改版 2）和第 9 节（修改版 1）。

起点：初稿基于 master `0e334047`（U9 合并后，tag `ui-u9`）；修改版 1 的代码核对基于 master `1be5ad57`，修改版 2 基于 master `6a7b73ed`（tag 之后都只合了文档，应用代码没变）。文中 `文件:行` 是这个版本的行号。

目标：每一个界面变化都有动画，同一类变化用同一种动画，同一类操作有同一种交互；数值全部来自 `LagoonMotion`（U1 定的四档时长、两条曲线、两种弹簧）；性能不能比 v0.2 差（数字门槛见第 6 节）。旧安卓版本的兼容不作要求（minSdk 29 不变，但新效果可以只在 Android 14+ 上做）。

## 0. 怎么查的

- **代码**：在 `app/src/main/java/.../ui` 里搜 `AnimatedContent`、`AnimatedVisibility`、`animate*AsState`、`Animatable`、`tween`/`spring`、`SharedTransitionLayout`、`BackHandler`/`PredictiveBackHandler`、Navigation 转场、Fragment 事务、Activity 启动，共 183 处，逐处看过；再按“每个会让界面变样的操作”反查有没有动画（没有动画的地方搜不到，只能反查）。修改版 1 又按审阅 1 补查了播放器换内容的入口、设置回传结果的路径和后台播放逻辑（第 3.3、3.1 节）。
- **录屏**：模拟器 emulator-5554（Pixel 10 Pro XL，API 37，swiftshader 软件渲染，已登录的数据快照），装 U9 qa 包，`screenrecord` 录了 11 段，每段抽出“和上一帧不同”的帧拼成联系表。录屏、联系表和脚本在 `E:/Projects/Lagoon-audit/motion/`（`rec.py` 录制，`sheetonly.py` 按时间段出表）。
- **局限**：软件渲染下模拟器只有约 11–16 帧/秒，300 ms 的转场只能拍到 2–4 帧，所以录屏只用来回答“有没有动画、方向对不对、有没有闪黑/跳动”，不能回答“顺不顺”。顺不顺、掉不掉帧要在手机上量（第 6 节）。
- **平板**：初稿和本稿都只看了代码。审阅 1 建议用 `wm size 2560x1600` + `wm density 320` 实录（U9 验证过这个形态能出来）。本稿写作时模拟器被其他线程占用、手机按要求不碰，所以**平板实录挪进 M0 的第一项**（第 5 节），审阅 2 之前做不了。
- 预测式返回的前提已核对：清单里 `enableOnBackInvokedCallback="true"`（`AndroidManifest.xml:19`），`targetSdk = 37`，Navigation 2.10 的跟手返回能工作，后面不用再怀疑这一点。

## 1. 结论

1. **页面级转场基本到位**：底部导航切页（横移 24 dp + 淡入淡出）、进入下一级页面（从右滑入，旧页缩到 96% 并变暗）、返回、Browse 里游戏海报到详情的共享元素、弹层滑入、迷你播放器横滑关闭，这些都有，而且大多用的是 `LagoonMotion`。
2. **最显眼的缺口在播放器**：打开播放器是硬切（页面直接变黑）；**播放器里换内容**（突袭自动跳转、片段看完整视频、迷你开着时点另一张卡片）也是整个播放器被替换、黑一下重来；最大化/最小化时聊天区瞬间出现/消失；从设置等别的界面回来时缩略图会黑一阵（根因是后台播放会关掉视频轨，见 P5）；返回键不能“边拖边看”；进画中画没有平滑放大。
3. **左右滑动切换分类整个没有**：关注页、我的页、频道页、游戏页只能点分段按钮，不能左右滑。v0.1 用 ViewPager，这些页面都能滑，这是相对 v0.1 的退步。顺带一个错误：在卡片上横滑会被当成点击。
4. **同一类变化做法不一致**：动画上（切分类有的有过渡有的硬切、切回关注页走“返回”动画、各种弹层子页面进出方式不同、返回手势有的能预览有的不能），交互上也有（迷你播放器不能上滑展开、频道/游戏页切分类丢滚动位置、搜索“查看全部”后返回直接离开搜索页，见 3.9）。
5. **几个“动画本身有问题”的地方**：列表逐项淡入在回到页面、滚回顶部、搜索每敲一个字时都重放；“我的”页卡片淡入时露出底下红色的“删除”背景；“返回”转场少了缩放，和“前进”不对称。
6. **数值没完全收口**：播放器里 6 处写死的 `tween` 加 1 处不传规格的 `animateTo`，聊天 2 处；Material 弹层用的是 Material 默认动画。
7. **和启动优化有一处撞车**：M3 的关注页翻页和启动计划 S3 改同一段代码（`FollowingScreen`/`FollowingDestination` 的分段和分页流），M3 的关注页部分排在 S3 之后，两边共用一个“分段访问过”开关（第 5 节）。

## 2. 动效规范现状（U1）

`ui/design/theme/LagoonMotion.kt`：

| 名称 | 值 | 用途（规范写的） |
| --- | --- | --- |
| `INSTANT` | 100 ms | 按压、开关、颜色 |
| `SHORT` | 200 ms | 淡入淡出、切页、小位移 |
| `MEDIUM` | 300 ms | 前进/返回、弹层 |
| `LONG` | 450 ms | 卡片展开成页面、**缩略图展开成播放器** |
| `Standard` / `Exit` | (0.2,0,0,1) / (0.3,0,1,1) | 进入和移动 / 离开 |
| `steady` 弹簧 | 阻尼 0.85，刚度 380 | 跟手拖动、弹层、**迷你播放器**、滑块 |
| `bouncy` 弹簧 | 阻尼 0.6，刚度 300 | 关注、收藏这类小庆祝 |
| `sibling` / `forward` / `back` | 见代码 | 切页 / 前进 / 返回 |
| `expand` / `collapse` | — | 开关控制的子项展开收起 |
| `reduced` | 系统“移除动画”开着 | 位移和缩放直接跳到终点，只保留淡入淡出 |

规范里写了但代码没做的：“缩略图展开成播放器”（`LONG`）、“迷你播放器用 steady 弹簧”（实际是 `tween(250)`）。本计划把“缩略图展开成播放器”改成弹簧（可打断、能接手指速度），M0 同步改 `LONG` 的注释，`LONG` 只留给共享元素（第 5 节 M0）。

另记一笔：`LagoonTheme` 只在创建时读一次系统“移除动画”开关（`LagoonTheme.kt:52`），应用开着时切开关要等界面重建才生效。影响很小，不改。

## 3. 全量盘点

状态：✓ 有且符合规范；⚠ 有，但和同类不一致或数值没收口；✗ 没有动画（硬切）；✗✗ 有明显的视觉错误。“证据”里 `录屏 xxx` 指 `E:/Projects/Lagoon-audit/motion/xxx.mp4` 和对应联系表。修改版 1 新增的条目标 **新**。

### 3.1 外壳与页面导航

| # | 操作 | 现在的样子 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| N1 | 底部胶囊/侧栏切到右边的标签 | `sibling`：淡入淡出 + 24 dp 横移；胶囊高亮块弹簧滑过去 | ✓ | `MainShell.kt` enterTransition；录屏 tabs 1.3 s |
| N2 | 切回第一个标签（关注） | `selectTab` 是 `navigate { popUpTo(start){saveState}; launchSingleTop; restoreState }`，回到起点是一次 pop，Navigation 用 pop 转场（`MainShell.kt:133–134` 不看 `siblingTargetId`）：上一页整页向右滑出 | ⚠ 和 N1 不一致 | `AppNavigator.selectTab`；录屏 tabs 6.3 s |
| N3 | 点当前标签回到标签首页 | `popBackStack` → `back()` | ✓ | `AppNavigator.popToTabRoot` |
| N4 | 进入频道页/游戏页/搜索/团队/热门直播 | `forward()`：新页从右滑入，旧页缩到 96%、变暗到 40% | ✓ | `LagoonMotion.forward` |
| N5 | 返回上一页 | `back()`：上一页从 40% 亮度淡入，**没有从 96% 放大回来** | ⚠ 和 N4 不对称，预测式返回时尤其明显 | `LagoonMotion.kt:79–83` |
| N6 | 预测式返回（边缘拖动） | Navigation 2.10 自带，跟手播放 `back()`；Browse 游戏详情自己用 `PredictiveBackHandler` | ✓（但继承 N5 的问题） | `BrowseDetail.kt:141`；清单已开 `enableOnBackInvokedCallback` |
| N7 | Browse 海报 → 游戏详情 | 海报共享元素 450 ms + 淡入淡出；返回时海报飞回网格 | ✓（小 bug：`BrowseDetail.kt:112` 的边界动画在“移除动画”时也还在动，M0 收口） | `BrowseDetail.kt`；录屏 nav 12.3 s |
| N8 | 其他地方的“卡片 → 详情”（关注页的游戏、搜索结果、频道头像、直播卡片里的游戏名） | 普通 `forward()`，没有共享元素 | ✗（U8 定的留到下个版本；本计划建议维持，第 8 节第 3 问） | — |
| N9 | 进入设置 / 登录（独立 Activity） | 系统默认的 Activity 转场，不是 `forward()`；返回同理 | ⚠ 和应用内页面不一致 | `PageBrowseActions.kt:35`，没有 `overrideActivityTransition` |
| N10 | 从设置回来且改过**会触发重建的**设置 | 主界面 `recreate()`，整屏重建，播放器也跟着重建 | ✗✗ 闪一下 | 根因见下；`MainActivity.kt:166`；录屏 sheet 9.2–10.2 s |
| N11 | 登录/退出后重启主界面 | `FLAG_ACTIVITY_NO_ANIMATION` + `makeCustomAnimation(0,0)`，硬切 | ✗ | `MainActivity.kt:524–531` |
| N12 | 往下滚动时胶囊隐藏、回顶时出现 | steady 弹簧下移 + 淡出 | ✓ | `Navigation.kt:99` |
| N13 | 进入画中画时胶囊/侧栏隐藏 | `alpha` 直接变 0 | ⚠（画中画窗口里看不到，影响小） | `MainShell.kt` `hidden` |
| N14 **新** | 分屏/折叠屏改窗口宽度时胶囊 ↔ 侧栏互换 | 瞬间互换（`MainActivity` 自己处理 `screenSize\|orientation`） | ✗（系统窗口缩放动画盖住大半，低优先级） | `AndroidManifest.xml:32` |

**N10 的根因（修改版 1 补）**：设置页的每一项在目录里有一个 `refresh` 标记，`SettingsStore.write` 写完后只有带标记的项才回调 `onRefreshNeeded`（`SettingsStore.kt:110`）→ 设置页 `publishResult()` 设 `RESULT_OK` → 主界面 `recreate()`（`SettingsActivity.kt:119–122`、`MainActivity.kt:161–167`）。现在会触发重建的是：

- 外观里 8 项显式带 `refresh = true`：刘海区域绘制、直播预览样式、名称显示、观看数缩写、开播时长、标签、联播人数、收藏剩余时长（`SettingsCatalog.kt` 外观页）；
- 6 个分段/标签顺序（`TabOrderSetting` 的 `refresh` 默认就是 `true`，`SettingSpec.kt:161`）：导航标签、关注、我的、频道、游戏、搜索的分段顺序；
- 横屏聊天宽度滑块（`SettingsActivity.kt:269–275` 直接 `markChanged()`）；
- 语言（`AppCompatDelegate.setApplicationLocales`，系统自己重建）和账号变化（走 `restartActivity()`，即 N11）。

审阅 1 写的“改任何设置都会重建”不准确：没有 `refresh` 标记的项（播放器、聊天、网络等大多数设置）改了不会重建。但上面这些是外观页里最常改的几项，所以 N10 照样常见。另外，**不管有没有重建，只要离开过主界面再回来，迷你缩略图都会黑一阵**，那是 P5 的另一个原因（后台播放关视频轨），两件事叠在一起才是录屏里看到的样子。

### 3.2 分类（分段按钮）切换与左右滑动

| # | 页面 | 点分段按钮 | 左右滑 | 证据 |
| --- | --- | --- | --- | --- |
| S1 | 关注（游戏/直播/视频/频道） | ✓ `sibling` | ✗ 不能滑 | `FollowingScreen.kt` AnimatedContent（第 269 行起）；录屏 tabs 8.8 s 横滑无反应 |
| S2 | 我的（收藏/下载/筛选） | ✓ `sibling` | ✗ | `SavedScreen.kt` |
| S3 | 频道（推荐/视频/剪辑/聊天/关于） | ✗ 列表内容硬切（同一个网格换内容，切分类时 `scrollToItem(1)`），只有按钮高亮块在滑 | ✗ | `ChannelScreen.kt:269–272/434–`；录屏 chseg 1.3 s |
| S4 | 游戏（直播/视频/剪辑） | ✗ 硬切，同上 | ✗ | `GameScreen.kt:251–254` |
| S5 | 搜索结果分组（全部/直播/视频/频道/游戏） | ✗ 硬切 | ✗（分组是筛选芯片，不做翻页） | `SearchScreen.kt:253` |
| S6 | 设置 → 外观 → 预览卡大小切换 | ✓ `sibling` | — | `AppearancePreview.kt` |
| S7 | 卡片上横滑 | ✗✗ `pressable` 用 `combinedClickable`（`Press.kt:62`），手指在卡片范围内横移不会取消点击，外面也没有横向滚动容器来抢手势 | — | 录屏 chseg 6.4 s：横滑频道页直播卡片打开了播放器 |

### 3.3 播放器

| # | 操作 | 现在的样子 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| P1 | 打开播放器（点直播卡片/头像/视频） | Fragment 直接 `replace`，`progress` 初值按 `mode` 取，新 Fragment 默认最大化，第一帧就是最大化：整屏变黑，然后信息栏、聊天依次冒出来 | ✗ 规范要求的“缩略图展开成播放器”没做 | `MainActivity.startPlayer`（`MainActivity.kt:857`）；`PlayerScreen.kt:370`；录屏 tabs 9.15 s、chopen 1.15 s |
| P2 | 最大化 → 迷你（返回键 / 下拉） | 视频缩到缩略图 250 ms `tween`（不是规范的 steady 弹簧）；`maximized = p == 0f && …`，`p` 一离开 0 聊天就 `visible = false`，**第一帧就消失**，露出约 200 ms 的页面底色（深色主题接近黑，是 `Canvas` 画的底色，不是窟窿），页面才淡回来 | ✗✗ 聊天硬切 | `PlayerScreen.kt:380/383`；录屏 minimize 2.13–2.47 s |
| P3 | 下拉松手 | 不管甩得多快，都从当前位置重新按 250 ms 走完，手指速度没带过去；没超过 15% 时 `tween(200)` 弹回 | ⚠ 不跟手 | `PlayerScreen.kt:567–585` |
| P4 | 迷你 → 最大化（点卡片） | 视频放大；**聊天区在动画结束才一下子出现** | ✗✗ 聊天硬切 | `PlayerScreen.kt:383`；录屏 maxdismiss 1.44–1.69 s |
| P5 | 迷你缩略图出黑块 | 两种情况：①从设置等别的 Activity 回来后黑 0.5–1 s；②最小化之后偶尔黑或部分黑 | ✗✗ | 根因见下；录屏 chseg2 8.74–9.31 s、sheet 9.2–10.2 s |
| P6 | 迷你播放器横滑关闭 | 跟手，松手 `tween(200)` 滑出并淡出；`onDragCancel` 的 `dismiss.animateTo(0f)` 没传规格，用的是 Compose 默认弹簧 | ⚠ 数值没收口 | `PlayerScreen.kt:1159/1165/1169`；录屏 maxdismiss 8.0 s |
| P7 | 迷你播放器点 × 关闭 | Fragment `TRANSIT_FRAGMENT_FADE`（View 系统的淡出，时长不受 `LagoonMotion` 管），和横滑关闭的样子不同 | ⚠ | `MainActivity.kt:875` |
| P8 | 迷你播放器出现/消失时，页面底部留白、提示条位置 | 提示条位置直接跳 92 dp | ⚠ | `MainActivity.kt:1026–1028`；录屏 xclose 3.16 s |
| P9 | 返回键最小化 | 普通 `OnBackPressedCallback`，只在最大化时挂上，不能预测式返回 | ✗ | `PlayerFragment.kt:150` |
| P10 | 进入画中画 | 4 处 `setPictureInPictureParams` 都没设 `sourceRectHint` 和宽高比，系统进画中画时不能从视频位置平滑缩小 | ✗ | `MainActivity.kt:867/883`、`PlayerFragment.kt:1041/1047/1315` |
| P11 | 控制层显示/隐藏 | 淡入淡出 `tween(250)`（不是 `SHORT`） | ⚠ | `PlayerScreen.kt:628` |
| P12 | 双击快进/快退提示、亮度/音量提示 | 直接出现、直接消失 | ✗ | `PlayerScreen.kt:648–649` |
| P13 | 广告状态标签 | 控制层出现时位置从 8 dp 跳到 56 dp | ✗ | `PlayerScreen.kt:641` |
| P14 | 竖屏 → 横屏全屏（按钮或旋转） | 系统旋转动画；转完后播放器布局一帧跳到位 | ⚠（系统旋转动画会盖住一部分） | `PlayerFragment.toggleFullscreen` |
| P15 | 横屏打开/收起聊天（按钮、双击、从右边缘拉出） | 视频区域宽度和聊天一帧跳变 | ✗ | `PlayerScreen.kt` `geometry` 直接算，无动画 |
| P16 | 平板全屏切换、聊天分隔条 | 全屏切换一帧跳；分隔条拖动跟手 ✓ | ✗ / ✓ | `PlayerFragment.toggleFullscreen`、`ChatDivider` |
| P17 | 横屏最大化时聊天出现 | 同 P4，聊天晚一拍冒出来 | ✗✗ | 录屏 land 1.37–1.53 s |
| P18 | 播放器菜单弹层里进子页面（画质、速度、睡眠定时…） | 只有淡入淡出；没有 `BackHandler`，返回键直接关掉整个弹层，不回到菜单 | ⚠ 和筛选弹层（前进/返回 + 返回键回上一级）不一致 | `PlayerSheets.kt:100–122` |
| P19 | 只听声音的背景 | 跟着 `progress` 淡出 | ✓ | `PlayerScreen.kt` `AudioOnlyBackdrop` |
| P20 **新** | 最大化时突袭自动跳转（`CHAT_RAIDS_AUTO_SWITCH`，默认开） | 整个播放器 Fragment 被 `replace`：黑屏，信息栏和聊天重新冒出来，聊天重连 | ✗✗ | `ChatFragment.kt:291` → `MainActivity.startStream` |
| P21 **新** | 点突袭条跳转 | 同 P20 | ✗✗ | `ChatFragment.kt:309` |
| P22 **新** | 片段 → 看完整视频；未公开回放 | 同 P20 | ✗✗ | `PlayerFragment.kt:750/534` |
| P23 **新** | 迷你播放器开着时点另一张卡片 | `startStream` 先 `close(deleteStates = false)` 再 `replace`：旧迷你卡片瞬间消失，新播放器从最大化状态冒出 | ✗✗ | `MainActivity.kt:750–771` |
| P24 **新** | 冷启动恢复上次播放 | 迷你卡片直接出现 | ✓（有意不加：不在启动路径上加东西） | `MainActivity.restorePlayerFragment` |
| P25 **新** | 从播放器点频道名/游戏名 | 页面 `forward` 和播放器最小化同时开始，两套时长（300 ms tween / 250 ms tween） | ⚠ | `PlayerFragment.kt:690–700` |

**P5 的根因（修改版 1 补）**：

- **情况①（从别的 Activity 回来）是播放逻辑，不是渲染问题，v0.1 就有。** 主界面 `onStop` → `ExoPlayerFragment.onStop` → `ExoPlayerService.stop(isInPIPMode)`：后台播放开着（`PLAYER_BACKGROUND_AUDIO` 默认开）且在播放时，切到“只听声音”画质，并且（`PLAYER_DISABLE_BACKGROUND_VIDEO` 默认开）`setTrackTypeDisabled(VIDEO, true)`（`ExoPlayerService.kt:1725–1742`）。回来时重新打开视频轨，要等下一个关键帧才有画面，直播一般 1–2 s 一个关键帧，所以黑 0.5–1 s。去设置、去登录、按 Home 再回来都会碰到。如果这次还触发了 N10 的 `recreate()`，播放器 Fragment 和 Surface 也一起重建，黑得更久。
- **情况②（最小化之后）在代码里找不到对应的轨道切换**（`updateQuality` 只更新标签，`PlayerConnection.kt:268`），更像模拟器 swiftshader 下 SurfaceView 跟随父 View 缩放时的合成延迟。先在手机上确认，确认是真的再排工作量。

另外 `PLAYER_USE_BACKGROUND_AUDIO_TRACK` 开着时，同一段还会换媒体项重新 `prepare`，回来要重新缓冲，黑得更久（审阅 2 第 1.6 节）。

修法（M2a 第 2 条）：**不动播放逻辑**（后台省流量是用户设置，属于播放路径），用“封面垫底”盖住等关键帧的那段：回到前台时视频槽先显示当前内容的封面图，`onRenderedFirstFrame` 到了再淡掉，和“打开播放器”用同一个机制。修改版 2 收紧了条件：只在“离开时视频开着且在播放”时挂（不盖住用户自己选的“只听声音”），3 s 超时和出错/暂停/切只听声音/关闭时取消，封面只从图片缓存取，不发网络请求。不在 `surfaceDestroyed` 里截最后一帧（异步截图有竞争）。

### 3.4 弹层、对话框、提示条

| # | 操作 | 现在的样子 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| D1 | 底部弹层弹出/收起/拖动关闭 | Material `ModalBottomSheet`：遮罩淡入 + 滑入；时长和曲线是 Material 默认的（主题没设 `motionScheme`），不是 `MEDIUM`/steady | ⚠ 能接受，没收口 | `Sheets.kt`；录屏 sheet 1.5 s |
| D2 | 弹层内容变高（半高 → 全高、内容加载完） | 高度跳 | ⚠ | `Sheets.kt` |
| D3 | 筛选弹层子页面 | `forward`/`back`（整页位移）+ 返回键回主页 | ✓（M0 后改用 `sheetForward/sheetBack`，有意统一） | `FilterSheet.kt:191` |
| D4 | 旧入口的弹层（下载） | 外层一个透明 Dialog 窗口 + 内层 Material 弹层；关闭时先滑下再关窗 | ✓ | `ComposeSheetDialog.kt` |
| D5 | 提示条（含撤销） | steady 弹簧从下滑入，淡出 | ✓ | `Toast.kt` |
| D6 | 更新提示、积分弹层、用户卡片、观众列表等 | 都走 D1 | 同 D1 | — |
| D7 | 完整性验证对话框（WebView） | 系统默认 Dialog 动画 | ⚠ 很少出现 | `IntegrityDialog.kt` |

### 3.5 列表与页面内容

| # | 操作 | 现在的样子 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| L1 | 首次加载时前 12 项逐个淡入上移 | 有 | ✓ | `States.kt:142` |
| L2 | **逐项淡入重放**：返回、切回标签、切分类、**列表滚走再滚回来**、**搜索边打字边出结果时每敲一个字** | `staggeredAppear` 的 `enabled` 参数 17 处调用都没传；动画状态 `remember` 在每一项里，项一重新进入组合就重放 | ✗✗ | 录屏 nav 3.39–3.51 s（关注页直播头像第 3 个晚出现）、tabs 6.19 s |
| L3 | “我的”页卡片淡入时 | 卡片半透明期间露出底下滑动删除的红色背景和垃圾桶图标 | ✗✗ | `SwipeToDelete` 背景常驻（`Progress.kt:83–85`）；录屏 tabs 3.80–3.85 s |
| L4 | 删除/撤销（下载、收藏、筛选）后剩下的项 | 直接跳位，没有 `animateItem` | ✗ | 全项目只有聊天用了 `animateItem` |
| L5 | 骨架屏 → 内容 | 骨架闪光；内容逐项淡入 | ✓ | `States.kt` |
| L6 | 图片加载完成 | 淡入；内存缓存命中直接显示 | ✓ | `Media.kt` `LagoonImage` |
| L7 | 下拉刷新 | Material 下拉框 + 自绘旋转圈 + 触感 | ✓ | `States.kt:230` |
| L8 | 回到顶部（点当前标签） | `animateScrollToItem` | ✓ | — |
| L9 | 频道“关于”展开、团队页展开 | `animateContentSize(Lagoon.motion.move())` | ✓ | `ChannelScreen.kt`、`TeamScreen.kt` |
| L10 | 关注按钮、收藏 | bouncy 弹跳 | ✓ | `Controls.kt` |
| L11 | 观众数变化 | 数字上下滚动 | ✓ | `Media.kt` |
| L12 | 开关控制的子项 | `expand`/`collapse` | ✓ | `Settings.kt` |
| L13 **新** | 关注页直播列表刷新后按观众数重排 | 跳位 | ✓（有意不加：分页列表，见 M4 第 4 条） | — |

### 3.6 聊天

| # | 操作 | 现在的样子 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| C1 | 新消息进来 | `animateItem` 淡入 + 位移 `tween(200)`（2 处写死）；快速模式和“移除动画”时关掉 | ⚠ 数值写死 | `ChatScreen.kt:283` |
| C2 | “回到最新”按钮 | 淡入 + 上滑 | ✓ | `ChatScreen.kt` |
| C3 | 回复条 | `expand`/`collapse` | ✓ | — |
| C4 | 突袭/投票/竞猜条出现和消失 | 直接出现，把聊天往下推 | ✗ | `ChatScreen.kt:449` |
| C5 | 房间模式小标签、@ 提示行 | 直接出现 | ✗ | `ChatScreen.kt:527/532` |
| C6 | 表情面板 ↔ 键盘 | 面板一帧出现，键盘按系统动画收起，中间输入框会跳 | ✗ | `ChatScreen.kt:534` |
| C7 | 进入直播时历史消息加载 | 列表多次跳动，有一帧整个列表是空的 | ✗✗（模拟器上看到，待确认是不是只在模拟器） | 录屏 chopen 2.09–2.27 s、3.93 s |
| C8 | 发送按钮 | 有文字时淡入 | ✓ | — |

### 3.7 设置与登录

| # | 操作 | 现在的样子 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| T1 | 窄屏进入/返回子页面 | `forward`/`back` | ✓ | `SettingsScreen.kt` |
| T2 | 宽屏（两栏）右栏换页面 | `key(route)` 直接换，硬切 | ✗ 和窄屏不一致 | `SettingsScreen.kt:232` |
| T3 | 设置里的返回 | 普通 `BackHandler`，不能预测式返回 | ⚠ 和导航页不一致 | `SettingsScreen.kt:195` |
| T4 | 左栏选中背景、搜索结果闪一下 | 颜色渐变 | ✓ | — |
| T5 | 登录页底部栏出现 | `AnimatedVisibility` 不传 `enter/exit`，默认动画，不走 `LagoonMotion` | ⚠ | `LoginScreen.kt:184` |

### 3.8 横屏与平板

横屏（`wm size 2992x1344`，侧栏形态）：页面转场、切页和竖屏一样（✓）。播放器最大化时聊天晚一拍冒出（P17），最小化时聊天第一帧消失（同 P2），收起/打开聊天一帧跳（P15），打字时聊天宽度一帧跳（X4）。平板只看了代码：设置两栏右栏硬切（T2），播放器全屏切换一帧跳（P16），侧栏高亮块有弹簧（✓），频道页宽屏是“左边信息栏 + 右边网格”（`ChannelScreen.kt:207/319`，影响 M3 的翻页范围）。平板实录放在 M0 第一项。系统的“全屏查看”提示每次进全屏都会出现，不是应用的问题。

### 3.9 交互逻辑不一致（修改版 1 新增）

初稿只盘了动画；用户目标的另一半是“同一类操作有同一种交互”。

| # | 不一致在哪 | 现在的样子 | 代码 | 进哪个阶段 |
| --- | --- | --- | --- | --- |
| X1 | 迷你播放器不能上滑展开 | 最大化可以下拉缩小，反过来只能点；不开“自由移动”时竖向拖动什么都不做 | `PlayerScreen.kt:1150–1178` 只认横向 | M2b |
| X2 | 频道页/游戏页切分类丢滚动位置 | 关注页每个分段各记各的位置（`FollowingScreen.kt:238`），频道/游戏页共用一个网格，切过去就回到第 1 项 | `ChannelScreen.kt:247/272`、`GameScreen.kt:227/254` | M3（翻页后每页一个网格，自然解决） |
| X3 | 搜索“查看全部”之后按返回直接离开搜索页 | “查看全部”是往下钻一层，返回却不回“全部” | `SearchScreen.kt:188/289`，页面里没有 `BackHandler` | M3 第 4 条 |
| X4 | 横屏打字时聊天宽度一帧跳到屏幕的 1/1.8 | 键盘出现时 `chatWidth` 直接换值 | `PlayerScreen.kt:248` | M2b 第 3 条（同 P15 的几何过渡） |
| X5 | 从播放器点频道名/游戏名 | 同 P25，两套时长 | `PlayerFragment.kt:690–700` | M1 第 4 条（数值收口后两边都走 steady/`MEDIUM`，同时开始） |

## 4. 问题排序

按“用户多常碰到 × 看起来多别扭”排。工作量是 Claude 的开发时间（含模拟器自测和 JVM 测试），不含审阅和手机测试（手机时间单列在第 5 节汇总）。性能风险：低 = 只在动画那几百毫秒里多做一点事；中 = 会动到视频画面、列表结构或常驻对象，必须手机量；高 = 可能影响平时滚动或播放。

| 排名 | 问题 | 涉及条目 | 严重度 | 工作量 | 性能风险 |
| --- | --- | --- | --- | --- | --- |
| 1 | 打开播放器硬切 | P1 | 高：每次看直播都会碰到 | 1.5–2 天（含封面垫底、卡片坐标槽） | 中（第一帧路径，见 M2a） |
| 2 | 播放器里换内容整块重来 | P20–P23 | 高：突袭自动跳转默认开 | 1–1.5 天 | 低–中（截两张图，换内容推迟一两帧） |
| 3 | 最大化/最小化时聊天硬切、中间露底色 | P2、P4、P17 | 高 | 0.5–1 天 | 低（只改 View 的透明度和位移，淡入期间硬件层） |
| 4 | 分类不能左右滑（相对 v0.1 退步）+ 横滑误触 + 切分类丢位置 | S1–S4、S7、X2 | 高 | 4–5 天（分三步） | 中（翻页时两页同时在画） |
| 5 | 逐项淡入重放 + “我的”页露出红色删除背景 | L2、L3 | 中：天天看到，像卡顿 | 0.5 天 | 低（反而少做动画） |
| 6 | 回到前台/改设置后缩略图黑一阵、整屏重建 | P5、N10 | 中 | 0.5 天（封面垫底，和第 1 项共用）+ 1 天（保守版不重建） | 低 |
| 7 | 切回第一个标签走“返回”动画；返回少缩放 | N2、N5 | 中 | 0.5 天 | 低 |
| 8 | 播放器返回不能预测式、画中画进入不平滑、迷你不能上滑 | P9、P10、X1 | 中 | 1.5 天 | 低 |
| 9 | 下拉松手不带速度、数值写死、两套时长 | P3、P6、P11、C1、P25 | 中低 | 0.5 天 | 低 |
| 10 | 聊天里各种条直接冒出、表情面板和键盘交接跳 | C4–C6 | 中低 | 1–1.5 天 | 低–中（聊天压测要复跑） |
| 11 | 横屏/平板聊天开关、全屏切换、打字时宽度一帧跳 | P15、P16、X4 | 中低 | 1 天 | 中（不能每帧改 SurfaceView 尺寸） |
| 12 | 设置/登录独立 Activity 转场和应用内不一致 | N9、N11 | 中低 | 0.5–1 天 | 低 |
| 13 | 弹层/子页面做法不统一，返回不能预测；搜索返回 | P18、T2、T3、D1、D2、X3 | 低 | 1 天 | 低 |
| 14 | 列表删除/撤销跳位 | L4 | 低 | 0.5 天 | 低（只给用户操作的列表加） |
| 15 | 其他小项（播放器提示、广告标签、提示条跳位、登录栏、胶囊进画中画、改窗口宽度） | P7、P8、P12、P13、N13、N14、T5 | 低 | 0.5–1 天 | 低 |
| 16 | 其余卡片 → 详情的共享元素 | N8 | 低（锦上添花） | 2–3 天 | 中（整个导航包一层 `SharedTransitionLayout`） |
| 17 | 进入直播时聊天历史跳动/空一帧 | C7 | 待确认 | 0.5 天排查 | — |

## 5. 分阶段方案

原则：先修错（便宜、风险低），再补播放器（最常用），再做翻页（工作量最大），最后统一和锦上添花。每个阶段一个或几个 PR（标出来了），CI 截图基线照旧（动效关闭拍图，大部分阶段不动基线；M3 结构变化会动基线，CI 上重录）。每个阶段补对应的 JVM/Robolectric 测试（第 6.3 节）。

### 和启动优化的先后（审阅 1 结论 C）

启动优化计划（`docs/perf/startup-plan.md`，**修改版 2**）的 S3 可能动关注页首帧和分页流（D6 “关注页分页流延后”，只在“第一帧之后 1 秒”那段显示它占得明显时做），M3 要把同一个 `AnimatedContent` 换成 `HorizontalPager`。规则：

- **分页流收集留在页外，用“分段访问过”开关决定先开哪些**（审阅 2 N3）。关注页 5 个 `collectAsLazyPagingItems()` 在 `FollowingDestination.Content()` 里（`FollowingDestination.kt:97–103`），游戏页 4 个在 `GamePage.kt:266–269`，都不在分段里；换成 `HorizontalPager` 不会改变“全部在收集”。也**不把收集挪进 Pager 的页里**：`beyondViewportPageCount = 0` 时相邻页每次翻动才组合，`LazyPagingItems` 每次新建，第一帧是空的（`cachedIn` 只保证不重新请求网络，收集在 effect 里，第一帧照样没数据），翻页第一帧会闪空页或骨架。做法：
  - `LazyPagingItems` 和每页的 `LazyGridState` 都在 `Destination` 层，不随 Pager 页销毁（关注页的网格状态已经在页外，`FollowingScreen.kt:238`）。
  - 每个分段一个“访问过”开关，存在 ViewModel 里：第一次停在或翻到这个分段时打开，之后一直开着；没打开的分段传 `emptyFlow()`（现有代码对视频、频道分段已经这么写）。冷启动时只有当前分段是开的。
  - 刷新、断网恢复（`FollowingDestination` 第 108–133 行）只刷新已打开的分段；没打开的分段第一次打开时自然从头加载。
  - **这个开关就是启动计划 D6 要做的东西**，两边只实现一次：谁先落地谁写，另一边复用。S3 做了 D6，M3b 直接用；S3 不做 D6，M3b 自己加这个开关。
- **M3b 的前置条件**（审阅 2 S7）：启动 S3 合并，**或者启动线程宣布 D6 不做 / S3 结束**（启动计划写明 P1 之后“已经 ≤ 1.06 的话 S2/S3 只做把握中以上的项”，S3 可能只做一部分或不做）。启动计划下次修改（或 S3 的 PR 描述）加一句反向引用：“M3b 沿用 D6 的分段访问过开关”，这一句由启动那边的线程加，本 PR 不改启动计划。
- M3b 合并前跑一次冷启动 A/B（审阅 2 S6），**指标和门槛用启动计划修改版 2 第 1.2 节的**：“进程启动到第一帧后 1 秒的主线程总工作量”和“第一帧之后的前 10 帧”，同会话交替 + bootstrap 95 % 区间，过线标准是“新 / 当时 master 中位数比的 95 % 上界 ≤ 1.03”。测量包名沿用 `-Plagoon.perfId=<名字>`（M3b 用 `m3b`，得到 `.bench.perf.m3b`），不另起后缀。
- 其他阶段不碰启动路径：M2 的“冷启动恢复播放不加动画”（P24）就是为了这个；M0–M2、M3a（游戏页）、M3c（频道页）、M4、M5 和启动计划可以并行，但同一天不合两边的 PR，避免数字互相污染。
- 两边都要手机，手机会话要错开排（每次都要 Mitchell 点头）。

### M0 规范补充、守护检查、测量工具（1 天，风险低，1 个 PR）

0. **平板实录**（审阅 1 2.3，本稿没能做）：等模拟器空出来，`wm size 2560x1600` + `wm density 320` 录设置两栏、播放器全屏切换、频道页两栏、关注页侧栏形态，结果补进第 3.8 节。不改代码，单独提交文档。
1. `LagoonMotion` 补充，后面各阶段只用这些：
   - `back()` 进入加“从 96% 放大回来”，和 `forward()` 对称（N5）。
   - `playerSpring`：播放器展开/收起、迷你卡片、卡片展开。**阻尼 1.0（不过冲）**、刚度沿用 steady 的 380。steady 的 0.85 有约 0.6 % 过冲，`progress` 冲到 0 以下视频会比槽位大一圈、冲到 1 以上迷你卡片的透明度和位置越界；大位移不过冲更稳。同时 `progress` 的 `Animatable` 设 `updateBounds(0f, 1f)` 兜底。
   - `playerRise`：没有起点矩形时的兜底（从屏幕底部升起，见 M2a），同样用 `playerSpring`。
   - `sheetForward()` / `sheetBack()`：弹层内的子页面，位移是常量 `SheetShift`（= 1/4 宽，和 `SiblingShift` 一样放在 `LagoonMotion` 里，不写在调用处），不缩放，避免弹层里整页横飞。**筛选弹层也改用它**（现在是整页 `forward/back`），这是有意的统一。
   - `contentSwap`：播放器里换内容的交叉淡入（`SHORT`，`Standard`），见 M2a 第 4 条。
   - `listItem`：`animateItem` 的三种规格（淡入 `SHORT`、位移 steady、淡出 `fadeExit`）。
   - `layout`：几何变化（宽度、位置）用 steady 弹簧。
   - 改 `LONG` 的注释：去掉“缩略图展开成播放器”（改为弹簧），`LONG` 只用于共享元素（Browse 海报，以后的 M6）。
   - `BrowseDetail.kt:112/156/160` 改成走 `LagoonMotion` 的函数；`:112` 共享元素边界在“移除动画”时跳到终点（N7 的小 bug）。
2. **守护检查**（仿 `tools/ci/legacy_ui_guard.py`），`ui/` 下除了 `design/theme` 以外报错：
   - `tween(`、`spring(`、`keyframes(`；
   - 不传规格的 `animateTo(x)` / `animateTo(x, initialVelocity = …)`（`PlayerScreen.kt:1169` 就是）；
   - 不传 `enter`/`exit` 的 `AnimatedVisibility(`（`LoginScreen.kt:184` 就是）；不传 `transitionSpec` 的 `AnimatedContent(`；
   - 不传规格的 `animateItem()`、`animateContentSize()`。
   - **持续动画一律走一个封装**（审阅 2 S10）：现在有四个无限动画，按 `label` 列出：`liveDot`（直播圆点呼吸，`Media.kt:151`）、`ringSpin`（`Media.kt:234`）、`shimmer`（`States.kt:70`）、`refreshSpin`（`States.kt:259`）。其中 `liveDot` 就是 G 阶段毛玻璃门槛第一次没过的原因，现在靠 `LocalLiveDotBreathing`（`Media.kt:138–157`）在玻璃背景上关掉。M0 把它推广成通用的“允许持续动画”组合局部值（玻璃宿主里设成 `false`），四个无限动画都改成通过一个读它的封装函数（放在 `design/`）。守护脚本只做一件事：`design/` 以外不许直接写 `rememberInfiniteTransition`。白名单按 `label` 写、不按行号（行号一改就失效）。这比按文件扫描“玻璃宿主”可靠，再加 6.2 的静止 0 帧检查兜底。
   - 守护脚本放在 `tools/ci/`，属于 `ci` 受保护区域，M0 的 PR 描述要带受保护代码声明（审阅 2 S12）。
3. **转场 Macrobenchmark（必做）**：`:benchmark` 加一个用例，用 `FrameTimingMetric` 量：切标签（关注 ↔ 浏览 ↔ 我的）、进出频道页、播放器最大化/最小化、打开播放器（M2a 之后加）、翻页（M3 之后加）。每次连上手机一条命令跑完，v0.2 和当前包交替装、交替跑（沿用冷启动的公平做法）。对照包（审阅 2 S8）：v0.2 从 tag `v0.2` 打，**同样用启动计划的 `-Plagoon.perfId=v02`**（得到 `.bench.perf.v02`），不另起第三套后缀；这个构建属性谁先落地谁实现（启动 S0 或本阶段），另一边复用。转场用例里的选择器**只用 v0.2 里已经有的测试标签或文字**（M0 新加的标签 v0.2 没有；`BaselineComparisonBenchmark` 对 v0.1 就是这么分开处理的）。翻页的对照组要 v0.1 包（第 6.2 节），用现有的 `.bench.v01`。
4. 文档：README 的“设计系统”一节补一张“哪类变化用哪个动画”的表（本文第 7 节）。

### M1 修错和收口（1.5 天，风险低，1 个 PR）

1. **逐项淡入只放一次**（L2）：做成**列表级**的开关，不是每项的 `remember`：`rememberStaggerGate(key)`，在“这个列表第一次从 0 项变成有数据”的那一帧打开，`StaggerLimit × StaggerMs + SHORT` 之后关上，整个列表的项共用这个开关传给 `staggeredAppear(enabled = …)`。开关状态存在**页外**（ViewModel 或页面级 `rememberSaveable`，以“页面 + 分段”为 key），这样返回、切回标签、滚走再滚回来、搜索逐字出结果都不重放；M3 翻页时离屏页被销毁也不会重放。下拉刷新后不重放（数据不是从 0 变成有）。
2. **红色删除背景**（L3）：把 `staggeredAppear` 挪到 `SwipeToDelete` 外层，背景和内容一起淡入，不用读 `dismissDirection`。
3. **切回第一个标签用切页动画**（N2），**标记一次性**（审阅 1 结论 A）：现在的 `siblingTargetId` 从不清空（`AppNavigator.kt:18/70`），如果直接让 `popEnterTransition`/`popExitTransition` 也查它，“浏览 → 游戏页 → 返回浏览”的返回目标正是当时记下的条目 id，会错用 `sibling`。改法（修改版 2 按审阅 2 N1 改了清空时机）：
   - **转场 lambda 只读、不清**。同一次切页 Navigation 要分别求 `enterTransition` 和 `exitTransition`，进入的 lambda 一清，退出的就读不到（新页切页淡入、旧页却按“前进”缩小变暗）；预测式返回拖动时 lambda 还会被重复求值。
   - **不用 `currentBackStackEntryFlow` 清**。它是异步收集的，`selectTab` 自己那次导航也会发一次，收集者可能在下一帧组合读 lambda 之前就把刚设的标记清掉。
   - **用同步的 `controller.addOnDestinationChangedListener` 清**：回调里只要 `controller.currentBackStackEntry?.id != siblingTargetId` 就清空。这个回调在 `navigate()` 内部同步触发，而 `selectTab` 是在 `navigate()` **之后**才写标记（`AppNavigator.kt:70`），所以它自己那次导航只清掉上一次的旧标记，新标记一直留到下一次导航（进二级页、返回、系统返回手势）才清。系统返回手势不经过 `AppNavigator`，所以监听器挂在 controller 上（`MainShell` 里 `DisposableEffect` 挂、摘），不在 `AppNavigator` 的各个方法里清。
   - `MainShell` 的 `popEnterTransition`/`popExitTransition` 也查这个标记（切回关注页是一次 pop，N2）。“浏览 → 游戏页 → 返回浏览”时，进游戏页那次导航已经把标记清掉，返回走 `back()`。
   - 测试（JVM）：选标签 → 进二级页 → 返回，返回必须用 `back()`；切回关注页用 `sibling`，进入和退出两边都是；连续两次切标签各用一次；**切标签后立刻按系统返回**，返回用 `back()`。
4. **数值收口**（P3、P6、P11、C1、P25/X5）：播放器里 6 处写死的 `tween` 和 1 处不传规格的 `animateTo` 换成 M0 的规格；聊天 2 处换 `listItem`。下拉松手把手指速度传给弹簧：`progressAnim.animateTo(target, playerSpring, initialVelocity = v / dragRange)`；`PlayerScreen.kt:567` 的注释说快速甩动时 `snapTo` 还排在队列里，速度要取自最后一次 `snapTo` 之后。从播放器点频道名/游戏名时页面 `forward` 和播放器最小化同时开始、各用规范的规格。
5. **迷你播放器点 × 和横滑关闭一样**：先滑出淡出再关（P7），去掉 Fragment 的 `TRANSIT_FRAGMENT_FADE`；`onDragCancel` 的默认弹簧收掉（P6）。
6. 小项：快进/亮度提示淡入淡出（P12）；广告标签位移动画（P13）；提示条底边距用 steady 动画（P8）；登录底部栏走 `expand`/`collapse`（T5）。

验收：模拟器录屏逐项对照第 3 节；JVM 测试；截图基线不变。手机数据和 M2 一起测（第 6.2 节）。

### M2 播放器转场（5–6 天，风险中，分 2 个 PR）

M2a 是“打开、换内容、最大化/最小化、回到前台”这条主线（3–3.5 天，修改版 2 加了 0.5 天：换内容的视频截图和交接、封面垫底的触发和取消条件）；M2b 是返回手势、画中画、横屏几何、弹层子页面（2–2.5 天）。M2a 先合。

#### M2a

1. **聊天跟着进度淡入淡出**（P2、P4、P17），**透明度不进 `ChatPlacement`**（审阅 1 结论 B）：`placeChat` 只要 `placement != lastChat` 就重设 `layoutParams`（`PlayerFragment.kt:325–338`），如果把每帧变化的 `alpha` 放进 `ChatPlacement`，整个 U7 聊天列表每帧 `requestLayout` + 重新测量。做法：
   - `ChatPlacement` 保持“布局”含义不变（位置、尺寸、是否参与布局）；`visible` 改成“最大化或正在过渡中”，过渡期间聊天留在布局里。
   - 另开一个只写 `View.alpha` / `View.translationY` 的通道（Compose 侧在 `SideEffect` 里写，不进比较、不触发布局），由 `progress` 算：最大化时 `progress` 从 0.3 到 0 聊天从 0 淡到 1，最小化反过来；可选下移 16 dp。
   - 淡入淡出期间（`0 < alpha < 1`）给聊天 View 设 `LAYER_TYPE_HARDWARE`，结束时还原 `LAYER_TYPE_NONE`。聊天是含整列 Compose 列表的 View，`alpha < 1` 时不开硬件层就每帧离屏合成一次；这是 View 系统做淡入的标准做法。
   - **alpha 到 0 时立刻 `GONE`**：聊天容器排在 ComposeView 之上（`PlayerFragment.kt:203–213`），渐隐期间仍是 `VISIBLE` 会挡住下面的触摸（竖屏时迷你卡片最后落在聊天原来的区域里）。曲线是 `progress` 0 → 0.3 就淡完，所以只挡几十毫秒。
   - 现在 `visible = false` 时收键盘、清焦点（`PlayerFragment.kt:333–335`），改成渐隐后这个动作提前到“开始最小化”那一刻（拖动越过阈值或返回键触发时），不能等透明度到 0。
   - 下面页面的露出同理跟着 `progress`，消掉中间那段底色。
   - 曲线放进 `PlayerGeometryRules`（纯函数），JVM 测试覆盖。
2. **封面垫底**（P1、P5 情况①的共同机制）：视频槽里在 SurfaceView 之上放一层封面图，跟着 `videoPlacement` 一起缩放，`onRenderedFirstFrame` 之后 `SHORT` 淡掉，然后摘掉。**只在下面几个时机挂**，不挂在每次 `onRenderedFirstFrame` 上：广告换 Surface（highlighter/备用播放器切换，`PlayerConnection.kt:166–180`）也会出新的第一帧，挂上去广告切换时封面会闪。
   - **打开播放器**：总是挂（见第 3 条）。
   - **回到前台**（审阅 2 N4）：在 Fragment 的 `onStop` 里、调用服务的 `stop` **之前**记下“离开时视频开着且在播放”（`!state.videoDisabled && isPlaying`），`onStart` 时只有这个为真才挂。用户自己选了“只听声音”画质时视频轨也是关的（`ExoPlayerService.start` 里同样 `setTrackTypeDisabled`），这时不挂，不会盖住 `AudioOnlyBackdrop`；离开时已暂停的也不挂（暂停的回放回来不一定渲染新帧）。只在 Fragment 里记，不碰 `ExoPlayerService`（受保护）。
   - **退出条件**：`onRenderedFirstFrame` 淡掉；另外 **3 s 超时**直接淡掉（回到今天的样子，不会更差），以及播放出错、暂停、切到只听声音、关闭播放器时立刻摘掉。
   - **图从哪来**：只从 Coil 的内存/磁盘缓存取，用和卡片相同的尺寸键；缓存没有就不挂，不发网络请求（网络比关键帧还慢）。直播的 `thumbnail` 是列表时刻的预览图，和当前画面可能差很多，这一点写进报告，接受。
   - 受保护路径：本条不改 `PlayerConnection` 的 Surface 移动逻辑，只读首帧事件。CI 的受保护清单（`tools/ci/protected_paths.py`）里没有 `PlayerConnection.kt`，M2a 的 PR 仍然自愿写声明，并注明“CI 不强制”（审阅 2 S12）。
3. **打开播放器从卡片展开**（P1，第 8 节第 4 问的建议默认）：
   - **起点矩形不按入口传，做进 `pressable`**（审阅 2 S3）：修饰符直接放进 `pressable`（`Press.kt`，43 处调用自动带上），在点击回调里取这个节点当前的 `LayoutCoordinates`，那一刻才算 `boundsInWindow()`，连同封面图地址放进一个活动级的“最近一次点击的卡片”槽（带时间戳，超过 500 ms 作废）。不需要每张卡片挂 `onPlaced`。`startPlayer` 取得到就用，取不到走兜底。18 处 `startStream/startVideo/startClip` 调用都不用改。
   - **优先级**：**已有播放器时一律走第 4 条的换内容**，忽略坐标槽。P22 的“看完整视频”按钮、突袭条本身也是 `pressable`，500 ms 内会被记进槽里，不能让它们触发“从卡片展开”。
   - **动画等 Fragment 第一帧**：新 Fragment 建 View、ComposeView、SurfaceView、绑服务都压在点击后的头几帧。如果展开从点击那一刻计时，头 100 ms 会被吃掉，看起来还是跳。做法：点击后立刻在卡片位置显示封面图，Fragment 第一帧画出来后（`withFrameNanos`）再用 `playerSpring` 把 `progress` 从“卡片矩形”展开到视频位置，页面背景、信息栏、聊天跟着进度淡入。“点卡片到展开结束”的帧单独作为一个测量项（第 6.2 节）。
   - **活动级覆盖层用延后建立的 `ImageView`**（审阅 2 S1）：只显示一张静态图，不新加 ComposeView，也不借 `toastOverlay`（启动计划 S3 的 D1 要把它延后建立）。第一次用到时才 `addView`，零启动成本；层级放在 `playerContainer` 之上、`toastOverlay` 之下。
   - **覆盖层只管“点击到 Fragment 第一帧”这一段**（审阅 2 S2）：弹簧**开始**的那一帧就把封面交给播放器自己的封面层（第 2 条，画在视频槽上，跟着 `videoPlacement` 一起缩放），覆盖层同一帧隐藏。否则覆盖层会停在卡片位置、播放器在它下面展开。
   - **兜底**：迷你播放器开着时从迷你位置展开（现成的 `progress` 1 → 0）；没有迷你播放器（链接、通知、没有卡片的入口）**从屏幕底部升起**（`playerRise`，起点是底部中间的虚拟矩形），不从一个并不存在的迷你卡片位置展开。
4. **播放器里换内容**（P20–P23，审阅 1 结论 D，新增的一类转场）：规则是**播放器已经在屏幕上就原位换，不展开**。
   - 时机：突袭自动跳转（P20）、点突袭条（P21）、片段看完整视频 / 未公开回放（P22）、迷你开着时点另一张卡片（P23）。
   - **截图拿什么**（审阅 2 N2 第 1 点）：`PixelCopy.request(window, …)` 只拷窗口自己的 Surface，视频在 SurfaceView（`PlayerFragment.kt:185`）的独立图层里，窗口截图里视频区是黑的或透明的。所以分两张：
     - **视频**：`PixelCopy.request(surfaceView, …)` 拿旧视频画面，交给新 Fragment 的封面层（第 2 条），画在视频槽里、跟着 `videoPlacement` 缩放。拿到的是缓冲区尺寸的画面，封面层按当前的缩放模式（`resizeMode`）摆放，和视频本身的摆法一致。
     - **视频以外**（只在最大化时需要；迷你卡片只有视频和一行标题，标题直接换）：`PixelCopy.request(window, 播放器矩形, …)` 截信息栏、聊天，放进第 3 条的活动级 `ImageView`，视频矩形那块用 `PorterDuff.Mode.CLEAR` 擦成透明，透出下面新 Fragment 视频槽里的旧视频截图。两张不用拼成一张。
     - M2a 第一次模拟器自测就验证“窗口截图里视频区确实是空的、SurfaceView 截图能拿到画面”；SurfaceView 截图失败就退回旧内容的封面图（只从缓存取），再拿不到就不放视频截图。
   - **截图时机在 `close()` 之前**（审阅 2 N2 第 2 点）：`startStream/startVideo/startClip/startOfflineVideo` 的第一行就是 `close(deleteStates = false)`（`MainActivity.kt:751/774/804/829`），它 `pause` + `stop` 播放器、解绑、`stopSelf`（`ExoPlayerFragment.kt:86–97`），而 `PixelCopy` 是异步的。四个入口一起改成：发现已有播放器时，先同时发两个截图请求 → 两个回调都回来（或 **100 ms 超时**，超时的那张退回封面图）→ 再 `close(deleteStates = false)` + `replace`。换内容因此推迟一两帧，可以接受。没有播放器时（第 3 条的打开）顺序不变。
   - 新 Fragment **继承旧的模式**（最大化还是迷你，现在默认最大化）；新 Fragment 第一帧画出来后，窗口截图和视频截图用 `contentSwap` 淡掉。效果是：旧画面淡成新封面（缓存里有的话），再淡成新视频；信息栏、聊天在截图下面已经换好，跟着一起显出来。截图 `Bitmap` 淡完就回收。
   - **迷你开着时点另一张卡片**（P23，审阅 2 S4）：原位换和从迷你展开**同时进行**，不串起来。新 Fragment 第一帧就开 `playerSpring`（`progress` 1 → 0），旧视频截图在新 Fragment 的视频槽里跟着一起放大、在放大过程中淡掉。这一条要求视频截图画在播放器自己的视频槽里（上面已经是），而不是活动级的固定位置。不从被点的卡片展开（两个动画抢同一个视觉焦点），这是第 8 节第 5 问的建议默认。
   - 突袭自动跳转时如果是迷你状态，就留在迷你状态原位换，不自动放大。
   - 聊天会重连（Fragment 换了），这是现有行为，截图只负责视觉。
   - 冷启动恢复上次播放（P24）不加动画。
   - 不在本阶段把“复用同一个 Fragment 只换媒体”作为做法：那要改 `PlayerConnection` 和服务的绑定（受保护的广告路径），收益只是少一次聊天重连；留作以后的选项。
5. 验收（M2a）：模拟器录屏覆盖打开（有卡片、无卡片、迷你开着）、换内容四种（最大化和迷你各一次）、最大化/最小化、去设置再回来（视频开着、只听声音、暂停三种）；JVM 测试覆盖 `PlayerGeometryRules` 的聊天透明度曲线、起点矩形插值、坐标槽过期和“已有播放器忽略坐标槽”、封面垫底的挂/不挂判断和取消条件；广告替换回归一次（highlighter 切换时封面不闪）。

#### M2b

1. **最大化播放器支持预测式返回**（P9）：不换成 Compose 的 `PredictiveBackHandler`，直接在现有的 `OnBackPressedCallback` 里覆写 `handleOnBackStarted/Progressed/Cancelled`（activity 1.13 有），拖动时把 `progress` 推到 0–0.25（播放器跟着缩小一点），松手提交就最小化，取消就 `playerSpring` 弹回。保持现在“最大化时挂、最小化时摘”的生命周期，也保留和聊天表情面板回调（`toggleBackPressedCallback`）的先后关系：表情面板开着时返回先关面板，不能被播放器的拖动抢走。另外（审阅 2 S11）：只在 `state.sheet == null` 时推进 `progress`（现有回调有弹层分支，`PlayerFragment.kt:150–153`，弹层开着时返回照旧关弹层）；**手机横屏全屏时不预览**，保留普通返回，因为最小化会在 500 ms 后解锁方向（`:657–662`），边拖边缩小再转屏不好看。预览只在竖屏和平板上做。
2. **画中画平滑进入**（P10）：4 处 `setPictureInPictureParams` 收成一个 `pipParams()` 构造函数，统一带 `setSourceRectHint`、`setAspectRatio`、`setSeamlessResizeEnabled(false)`（视频内容官方建议关掉无缝缩放），`setAutoEnterEnabled` 保持现状。`sourceRectHint` 用**画面实际区域**（去掉黑边后的视频矩形），宽高比和 `setAspectRatio` 一致，否则系统会裁；几何变化时更新。迷你播放器开着时回桌面也会自动进画中画，这时提示矩形是迷你缩略图的位置。
3. **横屏/平板布局变化有过渡**（P15、P16、X4）：视频区和聊天的矩形用 steady 弹簧过渡。**不能每帧改 SurfaceView 的尺寸**（每次改尺寸都会重新分配缓冲，掉帧）：动画期间只改 SurfaceView 的缩放和位移（迷你播放器现在就是这么做的），动画结束再一次性设到最终尺寸。横屏有“缩放模式”设置（`state.resizeMode`，裁切/拉伸）：等比缩放只在 FIT 模式下和终态一致，其他模式结束时会有一下跳，接受（在报告里写明），不单独处理。横屏打字时聊天宽度变化（X4）走同一套过渡。
4. **迷你播放器上滑展开**（X1）：迷你卡片在不开“自由移动”时识别竖向拖动，上滑跟手把 `progress` 从 1 往 0 拉，松手按速度和位置决定展开或弹回（和最大化下拉对称）。“自由移动”开着时竖向拖动仍是移动卡片，行为不变。
5. **播放器菜单弹层子页面**用 `sheetForward/sheetBack`，弹层里按返回先回菜单（P18）。
6. 验收（M2b）：模拟器录屏；JVM 测试覆盖 `pipParams()` 的矩形和宽高比计算、迷你上滑的阈值判断。

M2 的手机验收和 M1 一起做（第 6.2 节）。

### M3 左右滑动切分类（4–5 天，风险中，分 3 个 PR）

分三步，按执行顺序编号：

- **M3a 游戏页（1–1.5 天）**：3 个列表分段、没有聊天、不碰启动路径，先用它把“头部在外、Pager 在下、头部折叠用嵌套滚动驱动”的做法走通。
- **M3b 关注页 + 我的页（1–1.5 天）**：`AnimatedContent` 换 `HorizontalPager`，结构不大动。**排在启动 S3 合并（或启动线程宣布 D6 不做 / S3 结束）之后**，用“分段访问过”开关（见本节开头）。
- **M3c 频道页（2 天）**：最复杂（5 个分段，含聊天，宽屏两栏）。超出预算就按退路：频道页只加“切分类时列表淡入 + 横向甩动切分类”的手势，不改结构（1 天）。

共同规则：

1. **Pager**：`beyondViewportPageCount = 0`（只在翻页过程中组合相邻页）。它只决定**组合**，不决定**数据**：分页流的收集和每页的网格状态都在页外（`Destination` 层），不随页销毁；哪些分段的分页流先开，由“分段访问过”开关决定（本节开头，审阅 2 N3）。三个页面（游戏、关注 + 我的、频道）都这样做，M3a 先把它走通。分段按钮的高亮块跟着翻页偏移走（现在是点了才滑）；高亮块本身在玻璃里，不额外增加模糊。
2. **翻页起手那一帧**：`beyondViewportPageCount = 0` 意味着相邻页在手指一动的那一帧才开始组合，一个带图片的分页网格在这一帧里建出来，正是用户最容易感觉到的“起手卡一下”。因为数据在页外，这一帧有内容、不闪空页，滚动位置也不丢。翻向一个还没访问过的分段时，开关在**手指开始拖向它**的那一刻打开（不等停稳），加载和翻页同时进行，这一页先显示骨架。默认 0，把“翻页第一帧”单独作为测量项（第 6.2 节）；超过门槛再考虑在页面停稳后空闲时预组合相邻一页（只对轻量页开，而且要在冷启动第一帧之后的那段时间之外，不吃启动优化的成果）。
3. **头部折叠**（频道页、游戏页）：头部偏移是全页共用的，**保持偏移，切页不展开头部**（和 v0.1 的 AppBarLayout + ViewPager 一样）。切到一个还没滚过的页时，这一页列表顶部会在半折叠的头部下面，可以接受。胶囊的“滚动隐藏”跟**当前停稳的那一页**的滚动走；切页停稳时胶囊重新出现。每页一个网格，各记各的滚动位置（X2 自然解决）。关注页的大标题栏可以照搬的只是嵌套滚动的接法。
4. **宽屏/侧栏**：频道页宽屏是“左边信息栏 + 右边网格”（`ChannelScreen.kt:207/319`），翻页只放在右栏；关注页在侧栏形态下翻页区域从侧栏右边开始，侧栏不跟着翻。
5. **频道页“聊天”分段**（M3c）：聊天在第 4 位（推荐/视频/剪辑/**聊天**/关于，`ChannelLogic.kt:11`），里面是 `AndroidFragment<ChatFragment>`（`ChannelDestination.kt:98`）。翻页过程中聊天页只画一个轻量占位（标题 + 骨架），停稳在聊天页才挂 Fragment；挂上之后保留到频道页退出为止（不每次划走都断开）。聊天页里输入框、表情面板的横向手势不触发翻页。
6. **S7 横滑误触**：翻页容器接管横向手势后，卡片的点击自然被取消。没有翻页的页面（搜索、热门直播、浏览网格）在列表外层挂一个空的横向 `draggable`，越过触摸阈值就消费掉事件，子项的点击随之取消。**不改 `pressable`**（要换掉 `combinedClickable`，会丢无障碍语义、要重写长按）。
7. **搜索**（S5、X3）：分组是筛选芯片，不做翻页，只给切换加 `sibling` 过渡；从“查看全部”进来的，页面里加 `BackHandler`，返回先回“全部”。
8. 和系统返回手势的冲突：屏幕左边缘的系统返回手势优先，翻页不抢（Pager 默认如此，用测试验证）。
9. 截图基线：结构变了，M3a/M3c 的基线在 CI 上重录，PR 里列出变化的图。

验收：每个 PR 跑 `InteractionTest`（横滑换分段、点按钮仍然能切、横滑卡片不触发点击、左边缘返回不被翻页抢）；手机数据三个 PR 合完一起测（第 6.2 节）；M3b 另加冷启动 A/B。

### M4 聊天与列表（1.5–2 天，风险低–中，1 个 PR）

1. 突袭/投票/竞猜条、房间模式标签、@ 提示行用 `expand`/`collapse`（C4、C5）。新增的条**不带持续动画**（比如倒计时进度条用每秒一跳的文字，不用连续动画），见 M0 的毛玻璃守护。
2. 表情面板 ↔ 键盘（C6）：记住上一次键盘高度，面板直接用这个高度；切换时面板和键盘高度相同，输入框就不跳。键盘动画本身已经通过 `WindowInsets.ime` 每帧跟着走（`PlayerScreen.kt:345`）。不用实验 API `imeAnimationSource/Target`。
3. 进入直播时历史消息跳动（C7）：先在手机上看是不是只在模拟器；是真问题就查历史合并时的列表 key/滚动锚点。
4. 用户操作会增删的列表加 `animateItem(listItem…)`（L4）：下载、收藏、筛选、最近搜索、关注列表取关后。**分页加载的长列表不加**（追加时整列位移动画没有意义，还多算）；关注页直播列表刷新后按观众数重排也属于分页列表，**有意不加**（L13）。
5. 频道页如果 M3c 走了退路：切分类时新内容按 L1 的方式淡入一次（用 M1 的列表级开关）。

验收：JVM 测试（键盘高度记忆）；手机数据和 M5 一起测。

### M5 Activity、返回手势、弹层统一（2–2.5 天，风险低，1–2 个 PR）

1. 设置、登录用 `overrideActivityTransition`（Android 14+）配和 `forward`/`back` 一样的进出动画（300 ms、`Standard` 曲线、旧页 96%/40%），低于 14 的保持系统默认（N9）。跨 Activity 的预测式返回只有 Android 15+ 会用自定义的关闭动画，要在手机上确认还能预览。
2. 登录/退出后重启主界面用淡入淡出代替无动画（N11）。
3. **改设置回来不整屏重建，保守版**（N10，第 8 节第 2 问的建议默认，1 天）。现成的基础是目录里已有的 `refresh` 标记（第 3.1 节），但它现在的含义是“改了就整屏重建”。改法：
   - `refresh: Boolean` 换成三种取值：`None`（不需要主界面做什么）、`Live`（主界面回来时重读即可生效）、`Recreate`（必须重建）。**默认 `Recreate`**：现在带 `refresh` 的 14 项 + 横屏聊天宽度，全部先标 `Recreate`，行为和今天一样；不带 `refresh` 的项保持 `None`。
   - 逐项验证后才改成 `Live`。**第一批只有 5 项**（审阅 2 N5 核对过各项还有谁在读）：
     - 开播时长、标签、联播人数：只有页面的 Compose 在读，直接 `Live`。
     - 直播预览样式（`COMPACT_STREAMS`）：除了页面，还在 3 个 ViewModel 里决定 `PagingConfig` 的预取距离（`FollowedStreamsViewModel.kt:30`、`GameStreamsViewModel.kt:58`、`StreamSearchViewModel.kt:41`）。只影响预取距离，不重建也不影响显示，`Live`，写进报告。
     - 收藏剩余时长（`UI_BOOKMARK_TIME_LEFT`）：打开时要跑一次 `updateBookmarkUsers()`（`SavedDestination.kt:105`，现在只在页面建立时跑）。改 `Live` 时这个调用要跟着值变化重跑（`LaunchedEffect` 以设置值为 key）。
   - **名称显示（`UI_NAME_DISPLAY`）和观看数缩写（`UI_TRUNCATE_VIEW_COUNT`）留在 `Recreate`**：播放器和聊天也在读（`PlayerLogic.kt:71/72` 建 `PlayerOptions`，`PlayerFragment.kt:132` 只建一次；`PlayerFragment.kt:270`、`ChatFragment.kt:115`、`ChatViewModel.kt:1026/2767`），改成 `Live` 开着的播放器和聊天还是旧值。要让它们也即时生效得多半天（`Live` 信号同时让播放器重读选项、聊天重读名称显示），这次不做。
   - 分段顺序、刘海、聊天宽度、语言、名称显示、观看数缩写维持 `Recreate`。验证方法是改完回来、不重建，界面立刻是新值（JVM/Robolectric 测试 + 模拟器手点）。
   - 设置页回传“这次改过的项里最重的那一级”；主界面：`Live` → 主界面的设置版本号 +1，读设置的地方随之重组；`None` → 什么都不做；`Recreate` → 重建前截一张当前画面，重建后淡掉，做法见下一条。
   - **`Recreate` 时的截图放在 `MainViewModel`**（审阅 2 S5）：重建会销毁 Activity，盖在旧窗口上的 View 活不过 `recreate()`。重建前用 `PixelCopy.request(window, …)` 截图存进 `MainViewModel`（重建时保留），新 Activity 在 `onCreate` 里先挂上（用 M2a 的延后 `ImageView`），第一帧后 `SHORT` 淡掉并清空。这只在“有待显示的截图”时发生，不在冷启动路径上。窗口截图不含视频，视频区由 M2a 的封面垫底接手（同 N2）。
   - 顺带核对一遍不带 `refresh`、但主界面只在创建时读一次的项（例如 `LagoonTheme` 的“移除动画”；玻璃开关 `UI_GLASS` 不在此列，`GlassPolicy` 监听它的变化，`GlassPolicy.kt:76`，已经即时生效），把核对结果写进报告；发现“改了不生效直到重启”的，标 `Recreate`，不在本阶段改成即时生效。
   - 更彻底的办法是把设置搬进主界面的 `NavHost`（N9、N10、T3 一起消失），牵涉登录流程和 WebView，留给以后。
   - 注意：去设置再回来时缩略图黑一阵（P5 情况①）和重建无关，由 M2a 的封面垫底处理；本条只消掉“整屏重建”。
4. 设置宽屏右栏换页用 `sibling`（T2）；设置和筛选弹层的返回改 `PredictiveBackHandler`，跟手播放 `back`/`sheetBack`（T3）。
5. **弹层用 `motionScheme`**（D1）：还在用的 Material 组件只剩 `ModalBottomSheet`、`Switch`、`Slider/RangeSlider`、`SwipeToDismissBox`、下拉刷新、进度条，在 `LagoonTheme` 的 `MaterialTheme(motionScheme = …)` 里配一次（空间类 = steady，效果类 = `fade`），一处改全部生效。先确认 BOM 2026.09 里 `ModalBottomSheet` 的显示/隐藏确实读 `motionScheme`；不读的话退回给 `SheetState` 传规格。内容变高用 `animateContentSize(Lagoon.motion.move())`（D2）。
6. 其他小项：胶囊进画中画淡出（N13）、改窗口宽度时胶囊 ↔ 侧栏交叉淡入（N14）。

### M6（可选）其余卡片 → 详情共享元素（2–3 天，风险中）

在 `MainShell` 外面包 `SharedTransitionLayout`，`composable` 的 `AnimatedContentScope` 传给页面；先做三处：游戏海报（关注页游戏、搜索结果）→ 游戏页头部，频道头像 → 频道页头像，直播卡片缩略图 → 频道页直播卡片。**建议这次不做**（第 8 节第 3 问）：它和 M3 的翻页、预测式返回会互相影响，等 M3、M5 稳定、有了转场基准数据再评估；最常用的“卡片 → 播放器”已经由 M2a 的坐标槽覆盖。

### 汇总

| 阶段 | 内容 | 工作量 | 性能风险 | 依赖 / 顺序 |
| --- | --- | --- | --- | --- |
| M0 | 规范补充 + 守护检查 + 转场 Macrobenchmark（+ 平板实录） | 1 天 | 低 | — |
| M1 | 修错和收口 | 1.5 天 | 低 | M0 |
| M2a | 打开、换内容、聊天淡入淡出、封面垫底 | 3–3.5 天 | 中 | M0 |
| M2b | 预测式返回、画中画、横屏几何、迷你上滑、弹层子页面 | 2–2.5 天 | 中 | M2a |
| M3a | 游戏页翻页 | 1–1.5 天 | 中 | M0 |
| M3b | 关注 + 我的翻页 | 1–1.5 天 | 中 | M3a、**启动计划 S3 合并，或启动线程宣布 D6 不做 / S3 结束** |
| M3c | 频道页翻页（退路 1 天） | 2 天 | 中 | M3a |
| M4 | 聊天与列表 | 1.5–2 天 | 低–中 | M0 |
| M5 | Activity、返回手势、弹层统一、保守版不重建 | 2–2.5 天 | 低 | M0 |
| M6 | 其余共享元素（可选，建议不做） | 2–3 天 | 中 | M3、M5 之后 |

**开发合计约 15.5–18.5 天**（不含 M6；初稿 12–14 天，修改版 1 15–18 天，上调主要来自 M2 的换内容和封面机制、M3 拆三步加聊天占位和平板两栏、M5 的保守版不重建、M0 的 Macrobenchmark 和守护范围；修改版 2 再给 M2a 加 0.5 天，用于换内容的视频截图和交接、封面垫底的触发和取消条件）。

**手机测试另算**，按阶段打包成 3 次会话，每次约半天，提前和 Mitchell 约时间：

| 会话 | 时机 | 内容 |
| --- | --- | --- |
| 手机 1 | M1 + M2 合完 | 转场 Macrobenchmark（切标签、进出频道页、打开播放器、最大化/最小化）、播放丢帧、P5 情况②和 C7 是否只在模拟器、M5 前的 Activity 预测式返回预检 |
| 手机 2 | M3 三个 PR 合完 | 翻页（含翻页第一帧，对照 v0.1 ViewPager）、冷启动 A/B（M3b） |
| 手机 3 | M4 + M5 合完 | 聊天压测、Activity 转场和跨 Activity 预测式返回、静止 0 帧总复查 |

建议顺序：M0 → M1 → M2a → M2b → M3a → （启动 S3 合并后）M3b → M3c → M4 → M5。M3b 等启动 S3 的时候，M3c、M4、M5 可以先做。

## 6. 性能和测试怎么守

### 6.1 原则

- **不加常驻动画**：所有新动画只在状态变化时跑，静止时 0 帧；毛玻璃下不放持续动画（M0 守护）。
- **视频不跟着每帧改尺寸**：一律缩放 + 位移，结束再定尺寸（M2b 第 3 条）。
- **聊天淡入淡出不重新布局**：只改 `View.alpha/translationY`，过渡期间硬件层（M2a 第 1 条）。
- **列表**：分页长列表不加 `animateItem`；逐项淡入只在第一次（M1 第 1 条，反而减少了工作）；翻页只组合当前页和翻动中的相邻页。
- **冷启动**：本计划不在启动路径上加任何东西（冷启动恢复播放不加动画）；M3b 改关注页首屏结构，排在启动 S3 之后，并跑冷启动 A/B。
- **“移除动画”**：所有新动画都走 `LagoonMotion`，`reduced` 时位移和缩放直接到位，只留淡入淡出；每阶段在系统里关掉动画走一遍。

### 6.2 门槛（数字）

模拟器只验“有没有、方向对不对”（录屏联系表）；“顺不顺”以手机为准（OnePlus 15）。

| 项目 | 方法 | 门槛 |
| --- | --- | --- |
| 转场卡顿 | M0 的转场 Macrobenchmark，`FrameTimingMetric`，每项 ≥ 10 次，和 v0.2 交替装、交替跑 | 卡顿帧比例 ≤ v0.2 + 0.5 个百分点；`frameOverrunMs` P90 ≤ v0.2 + 2 ms |
| 打开播放器 | 同上，“点卡片到展开结束”单独统计 | 卡顿帧 ≤ 1 帧（中位数）；展开开始的那一帧不超过 `frameOverrunMs` 8 ms |
| 翻页 | 同上，加“翻页第一帧”单独统计 | 同转场；**对照组用 v0.1 的 ViewPager 翻页**（v0.2 没有翻页，拿点按钮切换比不公平） |
| 静止 | `dumpsys gfxinfo` 静止 10 s | 0 帧（G 阶段的门槛），每阶段复查 |
| 播放丢帧 | `u7/phoneframes.py`，gaules 1080p60 5 分钟 | 在 v0.2 的波动范围内（v0.2 实测 11.7 %，v0.1 10.5 %，区间重叠） |
| 聊天压测 | `chatstress9.sh` 30 条/秒 | 卡顿帧 < 5 %，和 v0.2 持平 |
| 冷启动 | 启动计划修改版 2 第 1.2 节的方法：真机 perfetto，bench 测量包（`-Plagoon.perfId=<阶段名>`）对当时的 master，同会话交替；M3b 和 M2a（播放器第一帧路径）必跑 | “进程启动到第一帧后 1 秒的主线程总工作量”和“前 10 帧”两项，新 / master 中位数比的 bootstrap 95 % 上界 ≤ 1.03（区间算法同启动计划 S0 第 1 步）；前 10 帧的超时帧数中位数不多于 master |

### 6.3 测试

- 截图基线照旧（动效关闭拍图）；M3 结构变化在 CI 上重录。
- JVM/Robolectric：M1 一次性切页标记（四个场景，含“切标签后立刻系统返回”）、逐项淡入列表级开关只放一次；M2 `PlayerGeometryRules`（聊天透明度随 `progress` 的曲线、起点矩形插值、坐标槽 500 ms 过期、已有播放器时忽略坐标槽）、封面垫底的挂/不挂和取消条件（视频开着、只听声音、暂停、出错、3 s 超时）、换内容截图回调和 100 ms 超时之后才 `close`、`pipParams()` 的矩形和宽高比、迷你上滑阈值；M3 `InteractionTest` 加横滑换分段、点按钮仍能切、横滑卡片不触发点击、左边缘返回不被抢，以及“分段访问过”开关（只开当前分段、翻过去才开、刷新只刷已开的）；M4 键盘高度记忆；M5 设置三级标记的回传、5 个 `Live` 项回来即时生效（收藏剩余时长重跑 `updateBookmarkUsers`）。
- LeakCanary：M2（覆盖层、坐标槽持有 `LayoutCoordinates`）和 M3c（聊天 Fragment 保留到页面退出）各跑一次。

## 7. 统一后的规则（给后面各阶段对照）

| 变化 | 用什么 |
| --- | --- |
| 同级切换（底部标签、分段、分组、设置宽屏右栏） | `sibling`；能左右滑的地方，滑动时内容跟手，松手 steady 弹簧 |
| 进入下一级（页面、弹层里的子页面） | `forward` / `sheetForward` |
| 返回 | `back` / `sheetBack`，全部支持预测式返回 |
| 卡片 → 详情（同一张图在两页都有） | 共享元素，`LONG` + `Standard` |
| 打开播放器（没有播放器时） | 从被点的卡片展开（`playerSpring`，等 Fragment 第一帧再开始，封面垫底）；没有卡片就从底部升起；迷你开着时见下一行 |
| **播放器里换内容**（突袭跳转、看完整视频、迷你开着时点新卡片） | **原位交叉淡入**（`contentSwap`），保持当前模式；先截图（视频用 SurfaceView 截图）再关旧播放器；迷你开着时点新卡片，换内容和从迷你位置展开同时进行 |
| 冷启动恢复播放 | 不加动画 |
| 播放器展开/收起、迷你卡片 | `playerSpring`（不过冲），跟手，松手带速度；最大化下拉、迷你上滑对称 |
| 视频画面还没出来（打开、离开时视频开着的回到前台） | 封面图垫底（只从图片缓存取），首帧到了 `SHORT` 淡掉，最多等 3 s |
| 临时层（弹层、提示条） | 弹层 steady 滑入 + 遮罩淡入（`motionScheme`）；提示条 steady 滑入 |
| 页面内某块出现/消失 | `expand` / `collapse` |
| 列表里用户操作的增删 | `listItem`；分页列表的追加和重排不加 |
| 数值、颜色变化 | `fade`（颜色）、数字滚动 |
| 按压 | `pressable`（97%，bouncy 回弹） |
| 第一次加载 | 前 12 项逐项淡入，每个列表只此一次 |
| 跨 Activity（设置、登录） | Android 14+ 用和 `forward`/`back` 一样的进出；重启主界面淡入淡出 |

## 8. 需要 Mitchell 决定的事

**2026-11-18 Mitchell 批准：五问全部按建议默认。** 下面五问的建议默认都是审阅 2 第 4 节的建议：五个都和修改版 1 的默认方向相同，第 2、4、5 问带上了审阅 2 的修正。批准时没有另外说明，就按建议默认做；任何一问想换，只要回一句哪一问换成什么。

1. **频道页/游戏页的左右滑**：改结构做真翻页，还是只加甩动手势和淡入（1 天）？
   **建议默认（待批准）：真翻页，分三步**（M3a 游戏页、M3b 关注 + 我的、M3c 频道页），4–5 天。只加甩动会造成“关注页内容跟手、频道页不跟手”，正是这次要消掉的不一致；真翻页顺带修好切分类丢滚动位置（X2）。前提是分页流收集留在页外、用“分段访问过”开关（第 5 节 M3 开头），否则翻页第一帧会闪空页。频道页超出预算再退回只加甩动。
2. **改设置回来不整屏重建这次做不做？**
   **建议默认（待批准）：做保守版**（M5 第 3 条，约 1 天）：每项设置标三级（不需要 / 回来重读 / 必须重建），默认“必须重建”。**第一批“回来重读”只有 5 项**：开播时长、标签、联播人数、直播预览样式、收藏剩余时长；名称显示和观看数缩写播放器和聊天也在读，先留在“必须重建”。需要重建时的截图放在 `MainViewModel` 里跨过重建，新界面第一帧后淡掉。不搬设置进 `NavHost`。
3. **M6 共享元素这次做不做？**
   **建议默认（待批准）：不做**，维持 U8 的决定，等 M3、M5 稳定、有了转场基准数据再评估。
4. **打开播放器的起点**：“从卡片展开”还是只做兜底？
   **建议默认（待批准）：从最后一次点击的卡片位置展开**，约 1–1.5 天。坐标取自 `pressable` 点击的那一刻（43 处调用自动带上，18 处打开播放器的调用都不改）；点击到播放器第一帧之间用一个延后建立的 `ImageView` 显示封面，弹簧一开始就把封面交给播放器自己的视频槽；**已有播放器时一律走换内容**，不从卡片展开。取不到卡片时**从底部升起**；迷你开着时从迷你位置展开。
5. **迷你开着时点另一张卡片**：原位换内容再从迷你位置展开，还是旧迷你淡出、新的从被点的卡片展开？
   **建议默认（待批准）：原位换内容并从迷你位置展开，两步同时进行**（M2a 第 4 条）：新播放器第一帧出来就开始放大，旧画面的截图在放大过程中淡掉。和“播放器已在屏幕上就原位换”的规则一致，只有一个视觉焦点，也没有“先等淡入再放大”的停顿。另一种做法工作量相近，你更喜欢就换。

## 9. 修改记录（修改版 1 对审阅 1 的回应）

### 9.1 必须改的四条

| 审阅意见 | 处理 | 在哪 |
| --- | --- | --- |
| **A** `siblingTargetId` 从不清空，让 pop 转场查它会把普通返回变成切页 | 接受。标记改成一次性：转场 lambda 读到匹配后清空，`currentBackStackEntryFlow` 下一次变化也清空；加 JVM 测试“选标签 → 进二级页 → 返回必须 `back()`” | 第 5 节 M1 第 3 条；6.3 |
| **B** `alpha` 进 `ChatPlacement` 会让聊天每帧重新布局 | 接受。`ChatPlacement` 只管布局；透明度和位移走 `View.alpha/translationY` 的独立通道；淡入淡出期间 `LAYER_TYPE_HARDWARE`，结束还原；收键盘、清焦点提前到开始最小化时 | 第 5 节 M2a 第 1 条；6.1 |
| **C** M3 翻页和启动 S3 改同一段代码 | 接受。关注 + 我的拆成 M3b，排在启动 S3 合并之后；写明 S3 做或不做 D6 两种情况下 M3b 怎么接；M3b 加冷启动 A/B；两边 PR 不同日合并、手机会话错开 | 第 1 节第 7 条；第 5 节开头“和启动优化的先后”、M3、汇总表 |
| **D** 漏了“播放器里换内容” | 接受。盘点补 P20–P24（加 P25 两套时长）；新增 `contentSwap`；规则：播放器已在屏幕上就原位交叉淡入、保持模式，迷你开着时点新卡片原位换完再展开，冷启动恢复不加动画；做法是 `replace` 前截一次窗口图做覆盖层，新 Fragment 第一帧后淡掉 | 第 3.3 节；第 5 节 M0、M2a 第 4 条；第 7 节；第 8 节第 5 问 |

### 9.2 建议改的

| 审阅意见 | 处理 | 在哪 |
| --- | --- | --- |
| 1.1 抽查补充（N2 的 `selectTab` 细节、P2 的“黑区”是页面底色、P6 不传规格的 `animateTo`、P10 四处位置、P18 没有 `BackHandler`、L2 滚回来和逐字搜索也重放） | 接受，写进对应条目 | 第 3.1、3.3、3.5 节 |
| 1.2 P5/N10 的根因 | 接受，并**更正审阅 1 的一处说法**：不是“改任何设置都重建”，而是带 `refresh` 标记的 14 项 + 横屏聊天宽度（外加语言、账号）才重建；黑屏的主因是 `onStop` 时后台播放关视频轨，和重建叠加。M2a 改成“封面垫底”优先，`PixelCopy` 作备选 | 第 3.1 节 N10、第 3.3 节 P5；M2a 第 2 条 |
| 1.2 C7 先确认 | 保留 | M4 第 3 条；手机 1 |
| 1.2 N6 的 `enableOnBackInvokedCallback` | 写了一句 | 第 0 节 |
| 2.1 换内容五种情况 | 同 D | — |
| 2.2 交互不一致五条 | 接受，新增第 3.9 节 X1–X5，分别进 M2b、M3、M1 | 第 3.9 节 |
| 2.3 平板实录 | 接受，但本稿写作时模拟器被占用、按要求不碰，挪成 M0 第 0 项（只录，不改代码） | 第 0 节；M0 第 0 项 |
| 2.3 M3 的平板/两栏 | 接受：宽屏只翻右栏，侧栏不跟着翻 | M3 第 4 条 |
| 2.3 改窗口宽度时胶囊 ↔ 侧栏互换 | 列进盘点（N14），低优先级，M5 小项 | 第 3.1 节；M5 第 6 条 |
| M0 `playerSpring` 过冲 | 接受：阻尼 1.0 + `updateBounds(0f, 1f)` | M0 第 1 条 |
| M0 `LONG` 注释 | 接受 | 第 2 节；M0 第 1 条 |
| M0 `sheetForward` 位移做常量、筛选弹层一并改 | 接受，写明是有意统一 | M0 第 1 条；第 3.4 节 D3 |
| M0 守护范围和白名单 | 接受，全部列出（含 `BrowseDetail` 三处和“移除动画”时还在动的小 bug） | M0 第 1、2 条 |
| M0 毛玻璃下不放持续动画 | 接受，新增守护 + 静止 0 帧兜底 | M0 第 2 条；M4 第 1 条 |
| M0 转场 Macrobenchmark 改必做 | 接受 | M0 第 3 条 |
| M1 逐项淡入做成列表级开关、放页外 | 接受 | M1 第 1 条 |
| M1 红色背景改外层淡入 | 接受 | M1 第 2 条 |
| M1 下拉速度取自最后一次 `snapTo` 之后；`onDragCancel` 默认弹簧 | 接受 | M1 第 4、5 条 |
| M2 展开等 Fragment 第一帧 | 接受，点击后立刻在卡片位置显示封面，第一帧后再开始弹簧；单独测量 | M2a 第 3 条；6.2 |
| M2 兜底从底部升起 | 接受（迷你开着时仍从迷你位置展开） | M2a 第 3 条 |
| M2 广告换 Surface 不触发封面 | 接受，封面只在打开和回到前台时挂，写进受保护路径声明 | M2a 第 2 条 |
| M2 预测式返回用现有回调的 progress 接口，保留表情面板先后 | 接受 | M2b 第 1 条 |
| M2 画中画参数收成一个函数 | 接受（`sourceRectHint` 用画面实际区域，宽高比一致，关无缝缩放） | M2b 第 2 条 |
| M2 横屏缩放模式下结束会跳 | 接受，非 FIT 模式结束的一下跳接受并写进报告 | M2b 第 3 条 |
| M3 拆 a/b/c | 接受。编号按执行顺序：**M3a 游戏页**（审阅 1 第 5 节说游戏页先行验证）、M3b 关注 + 我的（等启动 S3）、M3c 频道页。和审阅 1 第 3 节的字母分配不同，内容一致 | M3 |
| M3 翻页第一帧单独测、相邻页预组合 | 接受：默认 0，先测，超门槛再在空闲时预组合（避开启动后那一段） | M3 第 2 条；6.2 |
| M3 频道聊天页占位规则 | 接受 | M3 第 5 条 |
| M3 头部折叠和胶囊规则 | 选“保持头部偏移”（同 v0.1）；胶囊跟当前停稳的页 | M3 第 3 条 |
| M3 S7 用外层横向 `draggable`，不改 `pressable` | 接受 | M3 第 6 条 |
| M3 搜索返回先回“全部” | 接受 | M3 第 7 条；X3 |
| M3 工作量 4–5 天 | 接受 | 汇总表 |
| M4 表情面板用记住的键盘高度 | 接受，不用实验 API | M4 第 2 条 |
| M4 分页列表刷新重排不加动画是有意的 | 接受，写进盘点 L13 | 第 3.5 节；M4 第 4 条 |
| M4 新条不带持续动画 | 接受 | M4 第 1 条 |
| M5 优先 `motionScheme`，确认 `ModalBottomSheet` 读它 | 接受 | M5 第 5 条 |
| M5 跨 Activity 预测式返回先在手机上确认 | 保留 | M5 第 1 条；手机 1 |
| 4.1 `LagoonTheme` 只读一次“移除动画” | 记一笔，不改 | 第 2 节 |
| 4.2 性能门槛换成数字 | 接受，另加“打开播放器”一项 | 第 6.2 节 |
| 4.2 手机测试按阶段打包 | 接受，3 次会话 | 第 5 节汇总 |
| 4.3 每阶段补测试 | 接受 | 第 6.3 节和各阶段验收 |
| 工作量上调到 15–18 天，单列手机时间 | 接受：开发 15–18 天（不含 M6），手机 3 次各约半天 | 第 5 节汇总 |

### 9.3 第 8 节四问

审阅 1 第 5 节的四条建议全部采用为**建议默认**，但仍是 Mitchell 的决定（第 8 节第 1–4 问）。另加第 5 问（迷你开着时点新卡片的展开方式），因为审阅 1 建议“旧迷你淡出 + 新的从卡片展开”，本稿改成“原位换再展开”，两种都可以，交给 Mitchell。

## 10. 修改记录（修改版 2 对审阅 2 的回应）

审阅 2（PR #38）的结论：审阅 1 的四条方向都对，但三条的具体做法和代码事实对不上，要改掉 N1–N5 才能交批准。修改版 2 全部接受，没有重排阶段；工作量 M2a 加 0.5 天，合计 15.5–18.5 天。代码事实按 master `6a7b73ed` 抽查过（`AppNavigator.kt:18/70`、`MainShell.kt:126/130`、`MainActivity.kt:751/774/804/829`、`FollowingDestination.kt:97–103`、`GamePage.kt:266–269`、`Media.kt:143–157`、`tools/ci/protected_paths.py`），没有跑模拟器和手机。

### 10.1 必须改的五条

| 审阅意见 | 处理 | 在哪 |
| --- | --- | --- |
| **N1** 一次性标记的清空时机：在 lambda 里清会让同一次切页进入/退出各用一种动画；异步 flow 可能在 lambda 读之前就清掉 | 接受。lambda 只读不清；改用同步的 `controller.addOnDestinationChangedListener`，当前条目 id 不等于标记就清空（`selectTab` 在 `navigate()` 之后才写标记，所以自己那次导航不会清掉新标记）；监听器挂在 controller 上以覆盖系统返回手势；加“切标签后立刻系统返回”测试 | 第 5 节 M1 第 3 条；6.3 |
| **N2** 换内容的截图：窗口截图拷不到 SurfaceView 的视频；`start*` 第一行就 `close()`，截图要在它之前 | 接受。视频用 `PixelCopy.request(surfaceView, …)`，交给新 Fragment 视频槽里的封面层；视频以外（只在最大化时）用窗口截图放进延后建立的 `ImageView`，视频矩形擦成透明，不拼图；两个请求回调（或 100 ms 超时）之后才 `close` + `replace`，`startStream/startVideo/startClip/startOfflineVideo` 四个入口一起改；删掉“`close` 顺序不变” | 第 5 节 M2a 第 4 条；第 4 节第 2 行；第 7 节 |
| **N3** 分页流是在 `Destination` 里收集的，`beyondViewportPageCount = 0` 不会让“只有组合了的页才收集”成立；挪进页里又会让翻页第一帧闪空页 | 接受。收集和网格状态都留在页外；“只先开当前分段”做成 ViewModel 里的分段级“访问过”开关（第一次停在或拖向某分段时打开，之后一直开），和启动计划 D6 是同一个开关，谁先落地谁实现；刷新、断网恢复只刷已打开的分段；删掉“天然如此”。M3a、M3c 同样做 | 第 5 节“和启动优化的先后”、M3 共同规则第 1、2 条；6.3 |
| **N4** 封面垫底会盖住用户自己选的“只听声音”、暂停的视频等不到首帧、封面可能要走网络 | 接受。Fragment 的 `onStop`（调用服务 `stop` 之前）记“离开时视频开着且在播放”，回来只有这个为真才挂；3 s 超时淡掉，出错/暂停/切只听声音/关闭时摘掉；封面只从 Coil 缓存取，同卡片的尺寸键，缓存没有就不挂；不碰 `ExoPlayerService` | 第 3.3 节 P5 修法；第 5 节 M2a 第 2 条；第 7 节 |
| **N5** 名称显示、观看数缩写播放器和聊天也在读；直播预览样式还决定 ViewModel 的预取距离；收藏剩余时长要重跑 `updateBookmarkUsers` | 接受。第一批 `Live` 收窄为 5 项（开播时长、标签、联播人数、直播预览样式、收藏剩余时长）；名称显示、观看数缩写留在 `Recreate`；直播预览样式写明预取距离不重建也不影响显示；收藏剩余时长跟着值变化重跑 `updateBookmarkUsers()` | 第 5 节 M5 第 3 条；第 8 节第 2 问 |

### 10.2 建议改的

| 审阅意见 | 处理 | 在哪 |
| --- | --- | --- |
| 1.2 聊天 alpha 到 0 时立刻 `GONE` | 接受 | M2a 第 1 条 |
| S1 覆盖层用延后建立的 `ImageView`，不借 `toastOverlay` | 接受，层级在 `playerContainer` 之上、`toastOverlay` 之下 | M2a 第 3 条 |
| S2 弹簧开始时就把封面交给播放器的视频槽 | 接受 | M2a 第 3 条 |
| S3 坐标槽做进 `pressable`；已有播放器时一律走换内容 | 接受 | M2a 第 3 条；第 8 节第 4 问 |
| S4 迷你点新卡片时淡出和展开同时进行 | 接受，视频截图画在新 Fragment 的视频槽里跟着放大 | M2a 第 4 条；第 7 节；第 8 节第 5 问 |
| S5 `Recreate` 截图放 `MainViewModel` 跨过重建 | 接受 | M5 第 3 条 |
| S6 引用启动计划修改版 2 的指标和 bootstrap 门槛，测量包名沿用 `-Plagoon.perfId` | 接受 | 第 5 节“和启动优化的先后”；6.2 冷启动一行 |
| S7 M3b 前置条件加“或启动线程宣布 D6 不做 / S3 结束”；启动计划加反向引用 | 前一半接受。反向引用由启动那边的线程在下次修改或 S3 的 PR 描述里加，本 PR 不改 `docs/perf/startup-plan.md`（它在等批准，避免两边同时改一个文件） | 第 5 节“和启动优化的先后”；汇总表 |
| S8 v0.2 对照包用 `-Plagoon.perfId=v02`；转场用例只用 v0.2 已有的选择器 | 接受 | M0 第 3 条 |
| S9 `UI_GLASS` 已经即时生效，例子不对 | 接受，改成说明它不在此列 | M5 第 3 条 |
| S10 白名单按 `label`；持续动画守护推广 `LocalLiveDotBreathing` | 接受：通用“允许持续动画”组合局部值 + 一个封装函数，守护只禁止 `design/` 以外直接写 `rememberInfiniteTransition` | M0 第 2 条 |
| S11 预测式返回只在无弹层、竖屏/平板时预览 | 接受 | M2b 第 1 条 |
| S12 `PlayerConnection` 的声明是自愿的；M0 的 PR 要带 `ci` 声明 | 接受 | M0 第 2 条；M2a 第 2 条 |
| 工作量 M2a 3–3.5 天，合计 15.5–18.5 天 | 接受 | 第 4 节第 2 行；M2 标题；汇总 |

### 10.3 第 8 节五问

审阅 2 第 4 节的五条建议全部写成**建议默认（待批准）**，第 2、4、5 问带上了 N5、S1–S5 的修正。批准前不动代码。
