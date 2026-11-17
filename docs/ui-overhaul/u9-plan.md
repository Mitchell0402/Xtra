# U9 计划：遗留旧风格界面排查与整改（草案，待审阅）

日期：2026-11-17。只读排查，基于 master `d8ac2a4`（v0.2 发布后）。本文是计划初稿，按项目流程走 审阅 → 修改 → 审阅 → 修改 → 批准，批准前不改代码。

## 0. 结论速览

1. **你在手机上看到的紫色界面，主要是因为手机上平时用的 Lagoon 还是 v0.1。** 无线调试读到的手机安装情况（2026-11-17）：

   | 包名 | 版本 | 更新时间 | 说明 |
   | --- | --- | --- | --- |
   | `io.github.mitchell0402.lagoon` | 0.1（versionCode 1） | 2026-11-11 22:54 | 日常用的那个，还是 v0.1 |
   | `io.github.mitchell0402.lagoon.qa` | 0.1-QA（versionCode 6） | 2026-11-15 16:02 | U4 时期的测试包，没有 U5–U8 |
   | `io.github.mitchell0402.lagoon.bench` | 0.2-BENCH | 2026-11-17 12:04 | U8 性能测试留下的 |
   | `io.github.mitchell0402.lagoon.bench.v01` 等 | 0.1-BENCH | 2026-11-17 11:58 | 性能对照包 |

   v0.1 里聊天表情弹窗、播放菜单、画质菜单都是旧的 Material 对话框；这三处在 v0.2 里已经是新样式（U6、U7 做的）。把 GitHub 上的 `Lagoon-v0.2.apk` 覆盖安装到日常那个 Lagoon 上即可（或在 v0.1 里用应用内更新）。测试用的 `.qa`、`.bench` 包可以卸掉，免得混淆。

2. **v0.2 本身还剩 1 处真正的旧紫色界面**，另有 1 处只在调试开关下出现：
   - 睡眠定时 ›「在指定时间」的时钟选择器（Material 系统时间选择器，紫色小时框、酒红色上午/下午按钮），竖屏横屏都是。
   - 调试 ›「显示播放清单标签」弹出的 Material 对话框（只有打开调试开关才看得到）。

3. 排查中还发现几处**不是紫色但不是 Lagoon 样式**的地方，和几处**新样式里的毛病**（实测发现），一起列进 U9。

## 1. 怎么查的

- **代码扫描（全仓库）**：`res/layout` 只剩 `activity_main.xml`（一个 ComposeView 加播放器容器）；没有 `res/menu`；所有 Fragment/Activity/DialogFragment 的 Compose 根节点都包了 `LagoonTheme`（`MainActivity`、`SettingsActivity`、`LoginActivity`、`PlayerFragment`、`ChatFragment`、`ComposeSheetDialog`、`IntegrityDialog`）。
  逐一搜索了 `MaterialAlertDialogBuilder`、`AlertDialog.Builder`、`MaterialTimePicker`、`DatePicker`、`PopupMenu`、`ListPopupWindow`、`PopupWindow`、`BottomSheetDialog(Fragment)`、`DialogFragment`、`Snackbar`、`android.widget.Toast`、`PreferenceFragment`、`ViewBinding/inflate`、`com.google.android.material.*`、`android.widget.*`、`WebView`、通知构建、硬编码颜色（含 `#9146FF`、`#6200EE`、`#BB86FC`、`#D0BCFF` 等紫色）、资源引用。
- **主题检查**：窗口主题 `BaseDarkTheme`（`Theme.Material3.Dark.NoActionBar`）只设置了 `colorPrimary`/`colorOnPrimary`/背景，其余 Material 颜色槽（容器色、secondary、tertiary、surfaceContainer 系列）都是 Material 默认的紫色系；Compose 侧 `LagoonTheme` 的 `darkColorScheme` 映射了主要槽位，但 `secondaryContainer`、`tertiary*`、`surfaceBright/Dim`、`inverse*`、`errorContainer` 仍是默认紫色系。现在没有可见的组件用到它们，但任何新加的 Material 组件都会“漏紫”。
- **模拟器实拍**：电脑上的模拟器（API 37）安装 v0.2 正式包（哈希和发布一致），在 papaplatte 直播间逐个打开播放器、播放菜单每一项、聊天、频道页、长按菜单，竖屏和横屏各一轮。截图在电脑 `E:\Projects\Lagoon-audit\shots\`，说明在 `E:\Projects\Lagoon-audit\notes.md`。

## 2. 实拍结果（v0.2）

| 截图 | 界面 | 结果 |
| --- | --- | --- |
| a | 播放器和控制层 | 新 |
| b | 播放设置菜单 | 新 |
| c | 画质 | 新 |
| d1 | 睡眠定时页 | 新 |
| d2 / d3 / g5 | 「在指定时间」时钟选择器（表盘、键盘模式、横屏） | **旧（紫色）** |
| e1 | 录制弹层 | 新 |
| e2 / e3 | 去广告页；其中「广告拦截设置」按钮 | 新；但按钮落到设置首页，而不是去广告子页 |
| e4 / e5 / e6 | 音量、观众列表、点观众进频道 | 新 |
| e7 | 「查找未列出的回放」打开的回放页 | 大体新，但顶栏没有标题、头像是**紫色占位方块** |
| e7d / e8 / e12 | 回放菜单、倍速、下载弹层 | 新（e12 里紫色“?”缩略图是 Twitch 的 404 预览图，不是应用样式）；e13：键盘弹出时弹层顶到状态栏下面 |
| e9 / e11 / x1 | 频道“…”弹层、频道头展开、直播长按弹层 | 新 |
| e10 | 分享 | 系统分享面板（系统界面，不归应用管） |
| f1 | 表情卡片 | 新；但**在聊天里直接点表情，打开的是发言人的用户卡片**，表情卡片要从用户卡片里再点一次才出来 |
| f3 / f4 | 长按用户名（未登录无反应）、点用户名 | 用户卡片新 |
| g1–g4 | 横屏播放器、菜单、画质、睡眠定时 | 新；但横屏时弹层顶部伸到状态栏下面（时间和图标压在弹层上） |

## 3. 完整清单

### A. 还在用旧 Material 样式的界面（必须改）

| # | 界面 | 怎么到达 | 代码位置 | 现状 |
| --- | --- | --- | --- | --- |
| A1 | 睡眠定时时钟选择器 | 播放菜单 › 睡眠定时 ›「在指定时间」；或设置里开了「用时间选择器设睡眠定时」后点睡眠定时 | `ui/player/PlayerFragment.kt` `pickSleepTime()`（`MaterialTimePicker`） | 实拍确认紫色 |
| A2 | 播放清单标签对话框 | 设置 › 调试 › 打开「播放清单标签」后，播放菜单 › 显示媒体/多变体播放清单标签 | `PlayerFragment.kt` `showPlaylistTags()`，`util/ContextExtensions.kt` `getAlertDialogBuilder()` | Material 对话框（U6 时“原样保留”） |
| A3 | 主题安全网缺口 | 不是单个界面：窗口主题和 Compose 配色里没映射的颜色槽 | `res/values/styles.xml`、`ui/design/theme/LagoonTheme.kt` | 默认紫色系，现在无可见组件用到，但会让以后的改动或系统控件（如文字选择工具栏）漏紫 |

### B. 不是紫色，但不是 Lagoon 样式（建议改）

| # | 内容 | 位置 | 说明 |
| --- | --- | --- | --- |
| B1 | 系统 Toast（13 处调用） | `MainActivity`（断网、恢复连接、找不到浏览器）、`MainViewModel`（登录过期）、`ChannelPage`（找不到浏览器）、`DownloadDialog`（目录无效、仅订阅者）、`PlayerFragment`（找不到回放、睡眠定时已设/已取消，及服务转来的“直播结束”“播放出错”“仅订阅者”） | 应用里已有 Lagoon 提示条（`ui/design/components/Toast.kt`，计划第 6 节“替代系统 Toast”），这几处漏了 |
| B2 | 通知小图标还是 Xtra 旧标志 | `res/drawable-*/notification_icon.png`（播放通知、开播通知用） | 圆圈加斜杠的 Xtra 标志，改名为 Lagoon 时没换 |
| B3 | 画中画和下载通知的按钮图标是旧 Material PNG | `baseline_play_arrow_black_48`、`baseline_pause_black_48`、`baseline_audiotrack_black_24`（`PlayerFragment` 画中画动作、`VideoDownloadService` 通知） | 换成和应用一致的 Lucide 矢量图标 |
| B4 | 38 个没有任何引用的旧 Material 图标（5 种密度共 190 个文件） | `res/drawable-{m,h,xh,xxh,xxxh}dpi/baseline_*`、`ic_*_black_24dp`、`outline_*` | 死资源，删掉 |
| B5 | 头像占位色里有紫色和品红 | `ui/design/components/Media.kt` `AvatarTiles`（`#5B4BB7`、`#7A3D78`） | e7 里那个紫色方块就是它；名字为空时总落在第一个（紫色） |
| B6 | 聊天“奖励兑换”高亮是硬编码的淡紫 | `ui/chat/ChatScreen.kt:207` `RewardTint = #B18CFF` | 没走设计令牌；需要你定颜色（见第 6 节 D1） |
| B7 | Twitch 网页（登录、完整性校验）是 Twitch 自己的浅色网页 | `LoginActivity`、`IntegrityDialog` 的 WebView | 外框已是新样式；网页内容可以用 WebView 的“算法深色”让它变暗。可选，见 4.4 |

### C. 新样式里的毛病（实测发现，建议一起修）

| # | 问题 | 位置 | 修法 |
| --- | --- | --- | --- |
| C1 | 聊天里直接点表情不出表情卡片，出的是用户卡片 | `ui/chat/ChatScreen.kt` 主列表 `ChatRow` 没传 `onImageClick`（U7 的设计：点整行开用户卡片，卡片里的表情才能点） | 恢复 v0.1 的习惯：点表情开表情卡片，点行里其他地方开用户卡片（见 D2） |
| C2 | 底部弹层在横屏和键盘弹出时伸到状态栏下面 | `ui/design/components/Sheets.kt` `LagoonBottomSheet`（`contentWindowInsets` 全为 0）；`PlayerSheets.kt` 用 `screenHeightDp × 0.85` 限高 | 弹层顶部至少留出状态栏/刘海安全区，限高按“窗口高度 − 顶部安全区 − 键盘”算；所有弹层共用，一处修好 |
| C3 | 「查找未列出的回放」打开的回放页顶栏空白、头像紫色占位 | `PlayerFragment.kt:532` `startVideo(Video(), 0, qualities = …)` 传了空的 Video | 把当前直播的频道 id/登录名/显示名/头像和直播标题带进去；名字为空时占位用中性灰（B5） |
| C4 | 去广告页的「广告拦截设置」按钮落到设置首页 | `PlayerFragment.kt:931` `openAdBlockSettings()` 只启动 `SettingsActivity` | 带上目标页参数，直接打开 设置 › 去广告 |

### D. 系统界面（不归应用管，只列出说明）

分享面板、文件/文件夹选择器（下载目录、备份恢复、导入）、设备管理员授权页（睡眠定时锁屏）、“安装未知应用”权限页、画中画窗口控件、媒体通知的整体样式（由系统画，只有小图标和按钮图标是我们的，见 B2/B3）、系统启动画面（已经是 Lagoon 底色加新图标）。

## 4. 整改方案

### 4.1 A 组

- **A1 时间选择弹层**：新组件 `LagoonTimePicker`（`ui/design/components/Pickers.kt`），放在 `LagoonBottomSheet` 里：两列滚轮（小时、分钟），按系统 12/24 小时制显示，12 小时制多一列上午/下午分段控件；下方一行实时显示“将在 X 小时 Y 分后停止”，确认按钮是主按钮。行为和存储完全照旧：读写 `sleep_timer_time`，用 `TwitchApiHelper.getMinutesLeft()` 算分钟，`sleep_timer_lock` 照旧。设置项「用时间选择器设睡眠定时」保留。
- **A2 播放清单标签**：改成 Lagoon 弹层：等宽字体、可选中、可横向滚动的文本块，底部“复制”按钮（复制后出 Lagoon 提示条）。之后删掉 `getAlertDialogBuilder()`、`USE_MATERIAL3` 常量和两个 Material import。
- **A3 主题安全网**：
  - `LagoonTheme` 的 `darkColorScheme` 把**所有**颜色槽都映射到 Lagoon 令牌（`secondaryContainer`、`tertiary` 一组、`surfaceBright/Dim`、`surfaceTint`、`inverseSurface/OnSurface/Primary`、`errorContainer` 等）。
  - 窗口主题补全 Material 颜色属性（同样映射到 `colors.xml` 里的 Lagoon 值）。
  - A1、A2 做完后应用里再没有 Material Components 的代码用法，只剩主题父类。建议把父类换成 `Theme.AppCompat.NoActionBar`（深色）并去掉 `com.google.android.material` 依赖（见 D3），这样以后不可能再有 Material 对话框进来，安装包也会小一点。

### 4.2 B 组

- **B1**：13 处系统 Toast 改走 Lagoon 提示条。Activity 里的直接调用 `ShellState` 的提示队列；播放器服务转来的提示经 `PlayerConnection` 到 `PlayerFragment` 再进队列（服务本身不改）。只有应用不在前台、没有界面可显示时才退回系统 Toast。
- **B2**：用现有的 `ic_launcher_monochrome.xml` 做新的通知小图标（矢量，单色），**保持资源名 `notification_icon` 不变**，这样 `ExoPlayerService` 和 `LiveNotificationWorker` 的代码都不用动（`ExoPlayerService` 是受保护代码）。
- **B3**：三个旧 PNG 换成同名的 Lucide 矢量 drawable，同样不改调用代码。
- **B4**：删 38 个无引用图标（共 190 个文件），Lint 的未使用资源检查确认。
- **B5**：头像占位色去掉紫色和品红，换成青绿、蓝灰、暖灰一类和主题协调的颜色；名字为空用中性灰。
- **B6**：把奖励高亮做成令牌 `Lagoon.colors.reward`，颜色按 D1 定。

### 4.3 C 组

- **C1**：主列表的 `ChatRow` 传 `onImageClick` 打开表情卡片（徽章同理），行的其他区域仍开用户卡片。主列表每个内联图片多一个点击处理，要用 `ChatStressBenchmark` 量，见门槛。
- **C2**：`LagoonBottomSheet` 内容外层加 `WindowInsets.safeDrawing` 的顶部内边距（展开到顶时生效），内容最大高度改成按当前窗口可用高度（减去状态栏、刘海、键盘）计算；`PlayerSheetPage` 去掉自己的 `screenHeightDp × 0.85`，改用弹层给的限高。
- **C3**：`unlistedVideoQualities` 回调里用当前直播的 `channelId/channelLogin/channelName/profileImageUrl` 和标题构造 `Video`。
- **C4**：`SettingsActivity` 支持启动参数直接打开某个子页（去广告），`openAdBlockSettings()` 带上它；返回键回到播放器。

### 4.4 可选

- **B7**：登录和完整性校验的 WebView 打开算法深色（`WebSettingsCompat.setAlgorithmicDarkeningAllowed`，Android 13+）。**这两条流程关系到登录和绕过完整性校验，改动后必须完整走一遍登录（含电视设备码登录）和完整性校验**，任何异常就撤回这一项。

## 5. 顺序与分组

一个阶段 U9、一个线程、一个 PR，按下面顺序分提交，便于审阅和回滚：

1. A3 配色全映射 + 守护测试（先有护栏，后面的改动都在护栏下做）
2. A1 时间选择弹层
3. A2 播放清单标签弹层，删 `getAlertDialogBuilder`
4. C2 弹层安全区（影响所有弹层，尽早做，后面的截图基线一次录好）
5. C1 点表情开表情卡片
6. C3、C4
7. B1 提示条
8. B2、B3 图标；B4 删死资源；B5、B6 颜色
9. D3 通过的话：换主题父类、去掉 Material 依赖
10. （可选）B7

## 6. 需要你定的事

- **D1 奖励兑换高亮的颜色**：(a) 换成暖金色，做成令牌（推荐：和“去掉紫色”的方向一致，金色也符合“点数/奖励”的含义）；(b) 保留现在的淡紫，只做成令牌。
- **D2 聊天里点表情**：(a) 点表情直接开表情卡片，点其他地方开用户卡片（推荐，和 v0.1 一样，也是你这次期待的行为）；(b) 维持 U7：点整行都开用户卡片。
- **D3 去掉 Material Components 库**：(a) 去掉，主题父类换成 AppCompat 深色（推荐：杜绝旧对话框再出现，包更小）；(b) 保留库，只补全主题颜色。

## 7. 验收门槛

沿用基础计划第 10 节的全部护栏（红线、CI 7 项、冒烟、去广告回归、性能门槛、发布前检查），另加：

1. **守护检查（新，进 CI）**：`tools/ci/` 加一个脚本，`app/src/main` 里出现 `MaterialAlertDialogBuilder`、`AlertDialog.Builder`、`MaterialTimePicker`、`DatePickerDialog`、`TimePickerDialog`、`PopupMenu`、`ListPopupWindow`、`BottomSheetDialogFragment`、`android.widget.Toast`（允许名单：应用不在前台时的那一处退回）、`com.google.android.material.` 就失败。
2. **配色契约测试（新）**：`LagoonThemeContractTest` 逐个检查 `MaterialTheme.colorScheme` 的每个槽位都等于某个 Lagoon 令牌，没有一个是 Material 默认值。
3. **截图测试**：时间选择弹层（24 小时制、12 小时制）、播放清单标签弹层、横屏播放菜单、键盘弹出时的下载弹层、回放页顶栏（从查找未列出回放进入）、各一张手机竖屏和 1.3 倍字号；CI 录基线。
4. **交互测试**：时间选择确认后的分钟数和 v0.1 的 `getMinutesLeft` 一致（跨午夜）；主列表点表情开表情卡片、点文字开用户卡片；去广告按钮落在去广告子页；查找未列出的回放后顶栏显示频道名和标题；提示条替代 Toast 的每个入口都出提示条。
5. **性能**：C1 后 `ChatStressBenchmark`（每秒 30 条、保留 600 条）丢帧和 U7 报告的数字比不差于 5%；冷启动不变（误差内）。
6. **受保护代码**：预计不碰。B2、B3 用同名资源替换就是为了不改 `ExoPlayerService`；如果实际必须改，PR 写受保护代码声明。去广告回归照常跑（广告日志计数）。
7. **巡检**：模拟器按第 2 节表格把每个子界面重新截一遍（竖屏、横屏、键盘弹出），全部判为新样式，截图和对照表放进 `docs/ui-overhaul/u9/`。这张表留作以后每个阶段收尾都要过的“子界面巡检表”。
8. **真机**：在 OnePlus 15 上装 U9 的 qa 包，走一遍第 2 节表格；补上 U7 线程欠的真机冷启动和丢帧测试（需要手机解锁并充电）。
9. **安装包大小**：报告变化；D3 选 (a) 时应变小。
10. 门槛达不到时停下来问你，不自行放宽。

## 8. 不在 U9 范围

- 系统界面（第 3 节 D 组）。
- U8 盘点里留下的架构清理（nav_graph 换纯 Compose 导航、残留 Fragment、旧数据库表等），留给后面的“架构清理”。
- 共享元素转场、返回手势预览等新功能。
