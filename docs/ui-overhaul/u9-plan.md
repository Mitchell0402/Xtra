# U9 计划：遗留旧风格界面排查与整改（修改版 1，待二审）

日期：2026-11-17。基于 master `d8ac2a4`（v0.2 发布后）。流程：初稿 ✓ → 一审 ✓（`/mnt/project-files/reviews/u9-plan-review-1.md`）→ **修改版 1（本文）** → 二审 → 修改版 2 → Mitchell 批准。批准前不改代码。

## 修改记录（修改版 1，对照一审）

一审的 2 个严重、8 个重要、9 个“漏掉或说错”（M1–M9）、5 个一般问题（G1–G5）全部处理。逐条对应：

| 审阅条目 | 处理 | 在本文哪里 |
| --- | --- | --- |
| S1 提示条没有全局队列、会被弹层和播放器盖住 | 新设计“全局提示条”：Activity 最上层一个覆盖层，弹层开着时画在弹层里，画中画和后台退回系统 Toast；8 个页面各自的 `ToastHost` 并进来；单独成组，写明拆 PR 的条件 | 4.2 B1、第 6 节第 8 步 |
| S2 巡检只覆盖播放器/聊天/频道且未登录 | 从代码生成界面清单（附录 A，共 51 个弹层调用点 + 8 个播放菜单页 + 6 种聊天弹层 + 16 个设置子页 + 9 个路由 + 3 个 Activity + 2 个对话框 + 页内部件 + 通知）；U9 第一步先在 v0.2 上全量巡检（竖屏、横屏、平板横屏，已登录和未登录），再动代码；CI 检查新弹层必须登记 | 第 3 节、附录 A、第 6 节第 0 步、门槛 1 和 7 |
| I1 C2 只改了播放菜单，实际 8 处限高 | 8 处全改为弹层下发的可用高度；补键盘内边距；纯函数单测 + 模拟器实拍 | 4.3 C2、门槛 3 |
| I2 通知图标不能直接用 108dp 单色层；PNG 会覆盖矢量 | 按状态栏图标规格单独画 24dp 矢量，删掉 5 个密度的 PNG | 4.2 B2 |
| I3 时间选择应做成播放菜单子页 | 新增 `PlayerSheet.SleepAt` 子页；写明画中画、测试重点、读屏、矮窗口、不做键盘输入模式 | 4.1 A1 |
| I4 A3 和 D3 顺序冲突 | D3 先定；按 (a) 排顺序，(b) 的走法也写了 | 4.1 A3、第 6 节 |
| I5 守护要拦 Compose Material3 组件 | 守护规则扩到 Compose M3 和更多 View 组件，带允许名单 | 门槛 1 |
| I6 性能要同次会话 A/B；C1 要稳定 lambda | 改成同一台设备同一次会话轮流跑；C1 写明在列表外 `remember` 回调 | 4.3 C1、门槛 5 |
| I7 截图设备组合不对 | C2 相关界面加手机横屏、平板横屏，且在模拟器实拍 | 门槛 3 |
| I8 先请 Mitchell 升级到 v0.2 确认 | 列为第 0 节的请求，不等 U9 | 第 0 节 |
| M1 关注/取关提示是系统 Toast | 列入 B1 | 3.B B1 |
| M2 死图标数字 | 改为 52 个名字、260 个文件，另加 2 个死 XML（已用脚本复核） | 3.B B4 |
| M3 `exo_styled_controls_*` 死覆盖 | 和 B4 一起删 | 3.B B4 |
| M4 频道简介 Markdown 是系统字体 | 新增 B8 | 3.B B8 |
| M5 播放器按钮预览渐变有紫色 | 并入 B5 | 3.B B5 |
| M6 其他聊天高亮也是硬编码 | B6 扩成 6 个聊天颜色令牌 | 3.B B6 |
| M7 两个对话框用设备默认主题 | 新增 A4 | 3.A A4 |
| M8 通知没有强调色 | 并入 B2（不碰 `ExoPlayerService`） | 4.2 B2 |
| M9 下载通知按钮看不到图标；媒体通知按钮不动 | 写进 B3，下载通知不作为验收截图项 | 4.2 B3 |
| G1 B7 现状写错 | 改写：现有 `setForceDark` 在 targetSdk 37 下无效；改用 `setAlgorithmicDarkening`，按 WebView 特性判断 | 4.4 B7 |
| G2 徽章点了出什么 | 写明：有详情的徽章开表情卡片式的徽章卡片，没有的落到整行（用户卡片） | 4.3 C1 |
| G3 D3 “包更小”要实测 | 收益改为“实测后报告”；补启动画面颜色和对话框窗口两项验收 | 第 7 节 D3、门槛 9 |
| G4 U7 欠的真机测试不阻塞 | 单列，不阻塞合并 | 门槛 8 |
| G5 C3 顶栏要标明是回放 | 写明 | 4.3 C3 |

修改时在代码里另外核实到、一审没有提到的 5 点（都已写进对应条目）：

1. `ToastHost` 实际放在 **8 个**页面（一审写的 6 个，漏了“我的”和浏览页 `SavedScreen`、`GamesScreen`）。`ToastHostState` 本身已有排队（互斥锁，一次一条），缺的是“全局一个、放在最上层”。
2. 受保护的 `ExoPlayerService` import 了 `android.widget.Toast`，但只用它的 `LENGTH_*` 常量传给 `PlayerFragment`，自己不弹 Toast。守护检查要把这一行列进允许名单，不改受保护代码。
3. 时间选择的现有算法 `getMinutesLeft()` 在选中“当前这一分钟”时返回 0，而 0 会被 `onSleepTimerChanged` 当作“取消定时”。新子页在结果为 0 时禁用确认按钮（见 A1）。
4. 播放菜单在画中画时只是不画（`PlayerScreen.kt:457`），`state.sheet` 还在，退出画中画后会重新出现。旧的时间选择器进画中画是直接关掉的。A1 要在进画中画时把 `state.sheet` 清空。
5. `LagoonBottomSheet` 内容只加了导航栏内边距，没有键盘内边距，这是 e13（键盘弹出时下载弹层顶到状态栏下）的另一半原因。C2 一起修。另外 C4 要用的 `rememberSettingsNav(initialPage)` 已经存在，只是 `SettingsActivity` 没传参数，C4 比初稿估计的小。

---

## 0. 结论速览

1. **你在手机上看到的紫色界面，主要是因为手机上平时用的 Lagoon 还是 v0.1。** 无线调试读到的手机安装情况（2026-11-17）：

   | 包名 | 版本 | 更新时间 | 说明 |
   | --- | --- | --- | --- |
   | `io.github.mitchell0402.lagoon` | 0.1（versionCode 1） | 2026-11-11 22:54 | 日常用的那个，还是 v0.1 |
   | `io.github.mitchell0402.lagoon.qa` | 0.1-QA（versionCode 6） | 2026-11-15 16:02 | U4 时期的测试包，没有 U5–U8 |
   | `io.github.mitchell0402.lagoon.bench` | 0.2-BENCH | 2026-11-17 12:04 | U8 性能测试留下的 |
   | `io.github.mitchell0402.lagoon.bench.v01` 等 | 0.1-BENCH | 2026-11-17 11:58 | 性能对照包 |

   v0.1 里聊天表情弹窗、播放菜单、画质菜单都是旧的 Material 对话框；这三处在 v0.2 里已经是新样式（U6、U7 做的，模拟器实拍和代码都确认过）。

   **请你现在就做一件事（不用等 U9）**：在手机上用应用内更新（或覆盖安装 GitHub 上的 `Lagoon-v0.2.apk`）把日常那个 Lagoon 升到 v0.2，看一眼播放菜单、画质菜单、聊天里的表情。如果 v0.2 里这几处还是紫色，说明排查漏了东西，U9 范围要重新定。另外提前说明：v0.2 里在聊天中直接点表情，出来的是发言人的**用户卡片**（新样式），表情卡片要从用户卡片里再点一次；第 7 节 D2 就是把它改回“点表情直接出表情卡片”。测试用的 `.qa`、`.bench` 包可以卸掉，免得混淆。

2. **代码里，v0.2 本身只剩 2 处真正的旧 Material 界面**（一审逐个读代码也确认了）：
   - 睡眠定时 ›「在指定时间」的时钟选择器（`MaterialTimePicker`，紫色小时框、酒红色上午/下午按钮）。
   - 调试 ›「显示播放清单标签」的 Material 对话框（只有打开调试开关才看得到）。

   另有 1 处“主题没管到”的：下载弹层和完整性校验两个对话框窗口用的是**设备默认主题**（OnePlus 上就是 OxygenOS 的样式），里面的文字选择工具栏、网页表单控件跟着系统走（A4）。

3. 按 Mitchell 这次“深入重新检查”的要求，修改版**从代码生成了完整的界面清单**（附录 A）。代码层面每个界面都已经是 Lagoon 组件，没有发现第 4 处旧组件；但弹层位置、系统栏、键盘这类问题只有实拍才看得出来，所以 **U9 的第一步是按清单在 v0.2 上全量巡检**（竖屏、横屏、平板横屏，已登录和未登录），发现新问题先补进计划再开工。

4. 另外还有一批**不是紫色但不是 Lagoon 样式**的地方（第 3 节 B 组）和**新样式里的毛病**（C 组），一起列进 U9。

## 1. 怎么查的

- **代码扫描（全仓库）**：`res/layout` 只剩 `activity_main.xml`（主界面 ComposeView 加播放器容器）；没有 `res/menu`；所有 Activity/Fragment/DialogFragment 的 Compose 根节点都包了 `LagoonTheme`。逐一搜索了 `MaterialAlertDialogBuilder`、`AlertDialog.Builder`、`MaterialTimePicker`、`DatePicker`、`PopupMenu`、`ListPopupWindow`、`PopupWindow`、`BottomSheetDialog(Fragment)`、`DialogFragment`、`Snackbar`、`android.widget.*`、`com.google.android.material.*`、`WebView`、通知构建、硬编码颜色、`Theme_DeviceDefault`、Compose Material3 组件 import。
- **界面清单（修改版 1 新增）**：用脚本从代码列出所有 `LagoonBottomSheet(` 和 `ActionSheet(` 调用点（按所在函数归类）、`PlayerSheet` 枚举、`ChatSheet` 类型、设置 `PageId` 和 `SheetRequest`、导航路由、清单文件里的 Activity、`DialogFragment` 子类，再加上页内部件、空/错/断网状态和通知。结果见附录 A。
- **主题检查**：窗口主题 `BaseDarkTheme`（`Theme.Material3.Dark.NoActionBar`）只设置了 `colorPrimary`/`colorOnPrimary`/背景，其余 Material 颜色槽是 Material 默认紫色系；Compose 侧 `LagoonTheme` 的 `darkColorScheme` 映射了主要槽位，`secondaryContainer`、`tertiary*`、`surfaceBright/Dim`、`inverse*`、`errorContainer` 等仍是默认值。现在没有可见组件用到，但新加的 Material 组件会“漏紫”。
- **模拟器实拍（初稿时做的，范围有限）**：电脑上的模拟器（API 37）装 v0.2 正式包，在 papaplatte 直播间打开播放器、播放菜单每一项、聊天、频道页、长按菜单，竖屏和横屏各一轮，**未登录**。截图在电脑 `E:\Projects\Lagoon-audit\shots\`，说明在 `notes.md`。设置、登录、搜索、我的、关注、浏览，以及登录后的聊天没有拍，留到 U9 第 0 步。

## 2. 已有的实拍结果（v0.2，未登录）

| 截图 | 界面 | 结果 |
| --- | --- | --- |
| a | 播放器和控制层 | 新 |
| b | 播放设置菜单 | 新 |
| c | 画质 | 新 |
| d1 | 睡眠定时页 | 新 |
| d2 / d3 / g5 | 「在指定时间」时钟选择器（表盘、键盘模式、横屏） | **旧（紫色）** |
| e1 | 录制弹层 | 新 |
| e2 / e3 | 去广告页；其中「广告拦截设置」按钮 | 新；但按钮落到设置首页（C4） |
| e4 / e5 / e6 | 音量、观众列表、点观众进频道 | 新 |
| e7 | 「查找未列出的回放」打开的回放页 | 大体新，但顶栏没有标题、头像是紫色占位方块（C3、B5） |
| e7d / e8 / e12 | 回放菜单、倍速、下载弹层 | 新（e12 里紫色“?”缩略图是 Twitch 的 404 预览图）；e13：键盘弹出时弹层顶到状态栏下面（C2） |
| e9 / e11 / x1 | 频道“…”弹层、频道头展开、直播长按弹层 | 新 |
| e10 | 分享 | 系统分享面板（系统界面） |
| f1 | 表情卡片 | 新；但在聊天里直接点表情，打开的是用户卡片（C1） |
| f3 / f4 | 长按用户名（未登录无反应）、点用户名 | 用户卡片新 |
| g1–g4 | 横屏播放器、菜单、画质、睡眠定时 | 新；但横屏时弹层顶部伸到状态栏下面（C2） |

## 3. 完整问题清单

### A. 还在用旧 Material 样式或系统主题的界面（必须改）

| # | 界面 | 怎么到达 | 代码位置 | 现状 |
| --- | --- | --- | --- | --- |
| A1 | 睡眠定时时钟选择器 | 播放菜单 › 睡眠定时 ›「在指定时间」；或设置里开了「用时间选择器设睡眠定时」后点睡眠定时 | `ui/player/PlayerFragment.kt` `pickSleepTime()`（`MaterialTimePicker`） | 实拍确认紫色 |
| A2 | 播放清单标签对话框 | 设置 › 调试 › 打开「播放清单标签」后，播放菜单 › 显示媒体/多变体播放清单标签 | `PlayerFragment.kt` `showPlaylistTags()`，`util/ContextExtensions.kt` `getAlertDialogBuilder()` | Material 对话框 |
| A3 | 主题安全网缺口 | 不是单个界面：窗口主题和 Compose 配色里没映射的颜色槽 | `res/values/styles.xml`、`ui/design/theme/LagoonTheme.kt` | 默认紫色系，会让以后的改动或系统控件漏紫 |
| A4 | 两个对话框窗口用设备默认主题 | 下载弹层（`DownloadDialog`）、完整性校验 | `ui/common/ComposeSheetDialog.kt:34`、`ui/common/IntegrityDialog.kt:80`：`Dialog(…, android.R.style.Theme_DeviceDefault_NoActionBar)` | 窗口主题绕开了应用主题；A3 改不到这两个窗口，文字选择工具栏、网页表单控件跟设备主题走 |

### B. 不是紫色，但不是 Lagoon 样式（建议改）

| # | 内容 | 位置 | 说明 |
| --- | --- | --- | --- |
| B1 | 系统 Toast（13 处调用） | `MainActivity`（断网、恢复连接、找不到浏览器）、`MainViewModel`（登录过期）、`ChannelPage`（找不到浏览器）、`DownloadDialog`（目录无效、仅订阅者）、`PlayerFragment`（**关注/取消关注**、找不到回放、睡眠定时已设/已取消，以及服务转来的“直播结束”“播放出错”“仅订阅者”） | 关注/取关是用户最常看到的一条。应用已有 Lagoon 提示条，但只在 8 个页面各放了一个，没有全局的 |
| B2 | 通知小图标还是 Xtra 旧标志；通知没有强调色 | `res/drawable-*/notification_icon.png`（播放通知、开播通知、下载通知都用）；`LiveNotificationWorker`、两个下载服务没有 `setColor` | 改名为 Lagoon 时没换 |
| B3 | 画中画和下载通知的按钮图标是旧 Material PNG | `baseline_play_arrow_black_48`、`baseline_pause_black_48`（画中画、`VideoDownloadService` 通知）、`baseline_audiotrack_black_24`（画中画） | 真正看得到的只有画中画的按钮；下载通知是普通通知，系统只显示按钮文字（M9） |
| B4 | 死资源 | `res/drawable-{m,h,xh,xxh,xxxh}dpi/`：每种密度 56 个 PNG，其中 **52 个名字**没有任何引用（共 **260 个文件**）；`drawable/ad_status_background.xml`、`drawable/baseline_circle_24.xml` 无引用；`values/drawables.xml` 里 `exo_styled_controls_play/pause` 两条覆盖已无用（应用不再用 media3 的 `PlayerView`） | 2026-11-17 用脚本复核过数字 |
| B5 | 头像占位色和设置预览里有紫色/品红 | `ui/design/components/Media.kt:93` `AvatarTiles`（`#5B4BB7`、`#7A3D78`）；`ui/player/PlayerScreen.kt:1274` 播放器按钮预览背景渐变（`#2B1E3D`） | e7 的紫色方块就是 `AvatarTiles`；名字为空时总落在第一个（紫色） |
| B6 | 聊天的 6 个颜色都是硬编码 | `ui/chat/ChatScreen.kt:206-209`（First、Reward `#B18CFF` 淡紫、Notice、Mention），`ui/chat/ChatLines.kt:545-546`（Muted、Link） | 没走设计令牌；奖励色要你定（D1） |
| B7 | Twitch 网页（登录、完整性校验）是 Twitch 自己的浅色网页 | `LoginActivity`、`IntegrityDialog` 的 WebView | 外框已是新样式；可选，见 4.4 |
| B8 | 频道简介/面板的 Markdown 文字用系统字体 | `ui/channel/ChannelScreen.kt:853` `MarkdownText`（Markwon + 平台 `TextView`） | 颜色已按令牌，但字体是 Roboto 不是 Plus Jakarta Sans；链接按下的高亮色跟窗口主题走 |

### C. 新样式里的毛病（实测发现，建议一起修）

| # | 问题 | 位置 | 修法概要 |
| --- | --- | --- | --- |
| C1 | 聊天里直接点表情不出表情卡片，出的是用户卡片 | `ui/chat/ChatScreen.kt` 主列表 `ChatRow` 没传 `onImageClick` | 点表情开表情卡片，点行里其他地方开用户卡片（D2） |
| C2 | 底部弹层在横屏和键盘弹出时伸到状态栏下面 | `ui/design/components/Sheets.kt` `LagoonBottomSheet`（`contentWindowInsets` 全为 0、内容没有键盘内边距）；8 处按 `screenHeightDp` 比例限高 | 弹层统一算可用高度往下传，8 处都改 |
| C3 | 「查找未列出的回放」打开的回放页顶栏空白、头像紫色占位 | `PlayerFragment.kt:532` `startVideo(Video(), 0, qualities = …)` 传了空的 Video | 带上当前直播的频道信息和标题，并标明是回放 |
| C4 | 去广告页的「广告拦截设置」按钮落到设置首页 | `PlayerFragment.kt:931` `openAdBlockSettings()` 只启动 `SettingsActivity` | 带启动参数直接打开 设置 › 去广告 |

### D. 系统界面（不归应用管，只列出）

分享面板、文件/文件夹选择器（下载目录、备份恢复、导入）、设备管理员授权页（睡眠定时锁屏）、“安装未知应用”权限页、画中画窗口自带控件、媒体通知的整体样式（系统画，只有小图标和强调色是我们的，见 B2；媒体通知的按钮图标是 media3 自带，在受保护的 `ExoPlayerService` 里，**不动**）、系统启动画面（已是 Lagoon 底色加新图标）。

## 4. 整改方案

### 4.1 A 组

- **A1 时间选择改成播放菜单子页**
  - `PlayerSheet` 枚举新增 `SleepAt`，用现有的 `SubPage`（带返回键）。从睡眠定时页点「在指定时间」进入，返回键回到睡眠定时页；设置里开了「用时间选择器设睡眠定时」时，点睡眠定时直接打开这个子页（返回键关闭弹层）。
  - 新组件 `LagoonTimeWheel`（`ui/design/components/Pickers.kt`）：两列滚轮（小时、分钟），按系统 12/24 小时制显示；12 小时制多一个上午/下午分段控件。下方一行实时显示“将在 X 小时 Y 分后停止”，确认是主按钮。
  - 行为照旧：读写 `sleep_timer_time`，用 `TwitchApiHelper.getMinutesLeft()` 算分钟，`sleep_timer_lock` 照旧，设置项保留。**唯一改动**：结果为 0（选中当前这一分钟）时确认按钮禁用并提示“选一个之后的时间”，避免像现在这样被当成“取消定时”。
  - 进画中画：在 `onPictureInPictureModeChanged(true)` 里把 `state.sheet` 清空（现在播放菜单在画中画时只是不画，退出后会重新出现），和旧选择器“进画中画就关掉”的行为一致。
  - 读屏：每列滚轮是一个可调节控件，提供“增加/减少”调节动作和当前值朗读（如“小时，23”）。
  - 矮窗口：手机横屏（窗口高度 < 480dp）时滚轮每列只显示 3 行，保证整页放得下、不需要滚动。
  - 不提供旧选择器的键盘输入模式（滚轮已足够快），计划明确写下这一点。
  - 删掉 `MaterialTimePicker` 及其 import、`"closeOnPip"` 标签的处理（再无用处时）。
- **A2 播放清单标签**：改成 Lagoon 弹层：等宽字体、可选中、可横向滚动的文本块，底部“复制”按钮（复制后出 Lagoon 提示条）。之后删掉 `getAlertDialogBuilder()`、`USE_MATERIAL3` 常量和相关 import，连同 `PlayerFragment` 里为它建的 `HorizontalScrollView`/`TextView`。
- **A3 主题安全网**（按 D3 选 (a) 写；选 (b) 见括号）
  - `LagoonTheme` 的 `darkColorScheme` 把**所有**颜色槽映射到 Lagoon 令牌（`secondaryContainer`、`tertiary` 一组、`surfaceBright/Dim`、`surfaceContainer*`、`surfaceTint`、`inverseSurface/OnSurface/Primary`、`error*`、`outline*`、`scrim`）。这一步两种选法都做，最先做。
  - A1、A2 删完 Material Components 用法后，紧接着换窗口主题：父类改为 `Theme.AppCompat.NoActionBar`，设 `colorPrimary`、`colorPrimaryDark`、`colorAccent`、`colorControlActivated`、`colorControlNormal`、`colorControlHighlight`、`android:textColorHighlight`、背景色，全部指向 `colors.xml` 里的 Lagoon 值（AppCompat 默认 `colorAccent` 是它自己的青色，不是 Lagoon 的）；去掉 `com.google.android.material` 依赖。`values-v28`、`values-v30` 里的 `DarkTheme` 只设刘海模式、继承 `BaseDarkTheme`，不用改。（选 (b)：保留 Material3 父类，把窗口主题的 Material 颜色属性全部补齐。）
- **A4 对话框窗口主题**：新建 `Theme.Lagoon.Dialog`（继承 `DarkTheme`，透明背景、无标题、不变暗——变暗由我们的弹层自己画），`ComposeSheetDialog` 和 `IntegrityDialog` 改用它。两种 D3 选法都做。

### 4.2 B 组

- **B1 全局提示条**（最大的一项，单独成组，见第 6 节第 8 步）
  1. **一个全局队列**：`LagoonToasts`（Activity 级，放在 `MainActivity`/`SettingsActivity`/`LoginActivity` 各一个），内部就是现有的 `ToastHostState`（已有排队，一次一条）。通过 `CompositionLocal` 给 Compose 页面用，Activity/Fragment 代码直接调用。8 个页面各自的 `ToastHost` 改为调用它，保证同一时间屏幕上只有一条。
  2. **画在哪**：`activity_main.xml` 在播放器容器**之上**加第三个 `ComposeView`（`toastOverlay`），只画提示条，不拦截触摸（没有命中可交互元素时触摸穿透给下面；要有交互测试覆盖“提示条显示时点下面的按钮仍有效”）。位置：外壳底部导航和迷你播放器之上（读 `ShellState.bottomBarHeightPx`），播放器最大化或全屏时贴底部安全区。`SettingsActivity`、`LoginActivity` 用各自根节点里的同一个组件。
  3. **弹层开着时**：提示条画在**弹层里**。`LagoonBottomSheet` 打开时向 `LagoonToasts` 登记，最上面一个弹层内部放一个 `ToastHost`，覆盖层这时不画。`DownloadDialog` 的“目录无效”“仅订阅者”就是这种情况。
  4. **退回系统 Toast**：只有在应用不在前台**或在画中画**时（画中画小窗里的提示条看不清）。这是代码里唯一允许的 `Toast.makeText`，放在 `LagoonToasts.show()` 里，守护检查的允许名单只写这一处。
  5. **13 处迁移**：`MainActivity`、`MainViewModel`、`ChannelPage`、`DownloadDialog` 直接调用；`PlayerFragment` 的 `toast()` 实现（服务通过 `PlayerConnection` 回调来的）改为调用 `LagoonToasts`，服务本身不改。
- **B2 通知图标和强调色**
  - 按状态栏图标规格单独画矢量 `drawable/notification_icon.xml`：24dp 画布、约 2dp 留边、纯白单色、无渐变，图形取 Lagoon 标志的简化版（不直接用 `ic_launcher_monochrome.xml`，那是 108dp 自适应图标层，图形只占中间约 66dp，当作 24dp 用会缩成一小块）。
  - **同时删掉 5 个密度目录里的 `notification_icon.png`**，否则密度目录里的 PNG 优先于矢量，等于没换。资源名不变，`values/drawables.xml` 里 `media3_notification_small_icon` 指向它的那条保留，所以媒体通知的小图标也会换掉，而 `ExoPlayerService` 一行不动。
  - `LiveNotificationWorker`、`VideoDownloadService`、`StreamDownloadService` 的通知加 `setColor(Lagoon 青绿)`；`ExoPlayerService`（受保护）不加。
- **B3 按钮图标**：三个旧 PNG 换成同名的 Lucide 矢量 drawable（`play`、`pause`、`music`），删掉对应 PNG，不改调用代码。验收只看画中画的 3 个按钮；下载通知按钮系统不显示图标，不作为截图项。
- **B4 死资源**：删 52 个无引用图标（260 个文件）、2 个无引用 XML、`exo_styled_controls_play/pause` 两条覆盖。Lint `UnusedResources` 确认，并确认 media3 的会话/通知没有引用这两个名字（media3 通知用的是 `media3_icon_*`）。
- **B5 头像占位色**：`AvatarTiles` 去掉 `#5B4BB7`、`#7A3D78`，换成青绿、蓝灰、暖灰一类和主题协调的颜色；名字为空用中性灰。播放器按钮预览渐变的 `#2B1E3D` 换成同色系的深蓝灰。
- **B6 聊天颜色令牌**：`LagoonColors` 新增 `chatFirst`、`chatReward`、`chatNotice`、`chatMention`、`chatMuted`、`chatLink`。`chatFirst`、`chatMention` 直接引用 `accent`、`danger`（现在只是复制了数值），其余按现值落令牌，`chatReward` 按 D1 定。要求：左侧色条和卡片底色对比度 ≥ 3:1；`chatReward` 和 `chatMention` 的亮度差要能区分（不只靠色相）。
- **B8 Markdown 字体**：`MarkdownText` 的 `TextView` 设 `typeface = ResourcesCompat.getFont(context, Lagoon 正文字体)`，`highlightColor` 设成 `accentSoft`，链接色已是令牌，保持。

### 4.3 C 组

- **C1 点表情开表情卡片**
  - 主列表的 `ChatRow` 传 `onImageClick`：点表情开表情卡片，点行里其他地方仍开用户卡片。
  - **性能要点**：`ChatText` 用 `remember(line, style, density, ratios, onImageClick)` 生成内联图片表，`onImageClick` 是 key 之一。回调必须在 `ChatList` 外层 `remember` 成一个稳定的 `(ChatImage) -> Unit`，不能在 `items {}` 里每次新建 lambda，否则每次重组都会重建每一行的图片表。
  - **徽章**：`ChatPicture` 只在 `image.info != null` 时可点。有详情的徽章（订阅、比特等有 `info` 的）点了开同一个卡片弹层（显示徽章名和来源）；没有 `info` 的徽章不可点，点到会落到整行，打开用户卡片。不额外给徽章补数据。
  - 表情只有约 24–28dp 宽，点偏了会开用户卡片，接受（v0.1 也是如此），不扩大点击区域以免影响长按和整行点击。
- **C2 弹层安全区和可用高度**
  - `LagoonBottomSheet` 算出可用高度：`可用高度 = 窗口高度 − 顶部安全区（状态栏 ∪ 刘海）− 键盘高度 − 8dp`，写成纯函数 `sheetMaxHeight(windowHeight, topInset, imeInset)`，通过 `LocalSheetMaxHeight` 往下传。
  - 弹层整体 `heightIn(max = 可用高度)`；内容外层的内边距从“导航栏”改成“导航栏 ∪ 键盘”，键盘弹出时弹层整体上移而不是被顶到状态栏下。
  - 8 处自行限高全部改为按 `LocalSheetMaxHeight` 取比例：`PlayerSheets.kt:106`（0.85）、`FilterSheet.kt:253/395/432`（0.62/0.7/0.5）、`ViewerList.kt:66`（0.7）、`ChatSheets.kt:202/251`（0.4/0.6）、`DownloadSheet.kt:131`（0.75）。以后不许在弹层内容里直接读 `screenHeightDp`（守护检查，见门槛 1；`Glass.kt`、`GameScreen.kt` 里判断矮窗口的两处不是弹层限高，列入允许名单）。
  - 纯函数单元测试：手机竖屏、手机横屏（高 < 480dp）、平板横屏、键盘弹出四组数值。Robolectric 截图的系统栏/刘海/键盘高度都是 0，验证不了这一项，所以另在模拟器实拍（门槛 3）。
- **C3 未列出回放的顶栏**：`unlistedVideoQualities` 回调里用当前直播的 `channelId/channelLogin/channelName/profileImageUrl` 和直播标题构造 `Video`；顶栏标题前加“回放 ·”（中文）/“VOD ·”（英文），免得和直播混淆；名字为空时头像占位用中性灰（B5）。
- **C4 去广告设置直达**：`SettingsActivity` 读启动参数 `EXTRA_PAGE`（`PageId` 名称），传给已有的 `rememberSettingsNav(initialPage)`；`openAdBlockSettings()` 带上 `PageId.AdBlock`。在这一页按返回键回到设置首页还是直接回播放器：直接回播放器（从播放器来，只看这一页）。

### 4.4 可选

- **B7 网页变暗**：现状是 `LoginActivity.kt:178-183`、`:469-473` 调用了 `setForceDark`/`setForceDarkStrategy`，但应用 `targetSdk = 37`，这两个接口从 targetSdk 33 起不再生效，所以现在实际没有变暗；`IntegrityDialog` 完全没设置。做法：删掉旧调用，改为在 `WebViewFeature.isFeatureSupported(ALGORITHMIC_DARKENING)` 时调用 `WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)`（能否生效取决于 WebView 版本，配合 A4 的深色窗口主题）。**这两条流程关系到登录和绕过完整性校验，改动后必须完整走一遍网页登录、电视设备码登录和完整性校验**，任何异常就撤回这一项。

## 5. 界面清单和巡检（修改版 1 新增）

- **清单文件**：U9 第 0 步把附录 A 落成 `docs/ui-overhaul/u9/surfaces.md`，每行一个界面：稳定编号（`文件#函数[#序号]`，不用行号，代码移动不会失效）、名称、到达路径、是否依赖登录、是否有输入框、截图编号、结论（新/旧/问题号）。
- **截图组合**：每行都拍手机竖屏、手机横屏、平板横屏；有输入框的再拍一张键盘弹出；内容随登录变化的行（附录 A 里标“登”的）已登录、未登录各一轮，其余拍一次。
- **登录方式**：模拟器上用电视设备码登录：线程在模拟器里打开登录页拿到代码，请 Mitchell 在自己手机上打开 twitch.tv/activate 输入，一次即可，令牌留在模拟器里。模拟器不输入密码。
- **巡检不做对外动作**：不在真实聊天发消息、不兑换点数、不投票/预测、不关注或取关真实频道。这些界面的“发出后”状态（关注提示、发送失败等）用截图测试里的假数据拍。
- **平板横屏**：在电脑上新建一个平板模拟器（或在现有模拟器上 `wm size 2560x1600` + `wm density 320`），和 U4/U6 的平板验证一致。
- **截图存放**：原图放电脑 `E:\Projects\Lagoon-audit\u9\`；仓库里只放每个区域一张拼图（WebP）和 `surfaces.md`，避免仓库膨胀。
- **新发现怎么处理**：第 0 步发现的新问题按 A/B/C 归类追加到本计划（PR 里另起提交，写明“巡检新增”）。新增的 A 类（旧组件）超过 3 处，或任何一项需要改受保护代码，就停下来问 Mitchell 再继续。
- **以后**：`surfaces.md` 留作每个阶段收尾都要过的“子界面巡检表”；CI 检查保证新加的弹层必须登记（门槛 1）。

## 6. 顺序与分组

一个阶段 U9、一个线程、一个 PR，按下面顺序分提交，便于审阅和回滚：

0. **全量巡检（v0.2，改代码前）**：生成 `surfaces.md`，按第 5 节拍完，补充新发现。需要 Mitchell 配合一次电视设备码登录。
1. 先请 Mitchell 定 D1–D3（下面按推荐项排）。
2. Compose 配色全映射、配色契约测试、守护检查（含界面登记检查）进 CI。
3. A1（播放菜单子页）、A2。
4. 窗口主题：D3 选 (a) 就换 AppCompat 父类、去掉 Material 依赖；A4 两个对话框主题。（选 (b)：只补全窗口主题颜色 + A4。）放在 C2 之前，截图基线只录一次。
5. C2（弹层可用高度，8 处）。
6. C1、C3、C4。
7. B2、B3、B4（图标和死资源）；B5、B6、B8（颜色和字体）。
8. B1（全局提示条和 13 处迁移）。最大、风险最高，放最后单独成组。**拆出单独 PR 的条件**：这一组新增和修改代码超过约 600 行；或弹层内提示条的做法在实现中发现行不通、需要换方案（这时停下来请 Mitchell 定）。拆出时 U9 主 PR 先合并，守护检查里 `Toast` 的规则暂时放宽到现有 13 处，B1 的 PR 合并时收紧。
9. （可选）B7。
10. 按 `surfaces.md` 全量复拍，逐行判定。

## 7. 需要你定的事（一审同意全部推荐项，待你确认）

- **D1 奖励兑换高亮的颜色**：(a) 换成暖金色，做成令牌（**推荐**：去掉紫色，金色也符合“点数/奖励”的含义；对比度和区分度要求见 B6）；(b) 保留现在的淡紫，只做成令牌。
- **D2 聊天里点表情**：(a) 点表情直接开表情卡片，点其他地方开用户卡片（**推荐**：和 v0.1 一样，也是你这次期待的行为；条件是稳定回调和丢帧门槛，徽章行为见 C1）；(b) 维持 U7：点整行都开用户卡片。
- **D3 去掉 Material Components 库**：(a) 去掉，主题父类换成 AppCompat 深色（**推荐**：杜绝旧组件再进来；安装包大小变化实测后报告，R8 已经裁掉大部分，预计收益很小）；(b) 保留库，只补全主题颜色。主要的防护来自守护检查，所以 D3 选哪个都不影响其他条目。

## 8. 验收门槛

沿用基础计划第 10 节的全部护栏（红线、CI 7 项、冒烟、去广告回归、性能门槛、发布前检查），另加：

1. **守护检查（新，进 CI，`tools/ci/legacy_ui_guard.py`）**，允许名单放 `tools/ci/legacy_ui_allowlist.txt`，每条写原因：
   - 任何目录都禁止：`MaterialAlertDialogBuilder`、`AlertDialog.Builder`、`android.app.AlertDialog`、`ProgressDialog`、`MaterialTimePicker`、`DatePickerDialog`、`TimePickerDialog`、`PopupMenu`、`ListPopupWindow`、`PopupWindow`、`Spinner`、`NumberPicker`、`registerForContextMenu`、`BottomSheetDialogFragment`、`Snackbar`、`com.google.android.material.`（D3 选 (a) 时）、`Theme_DeviceDefault`、`Toast.makeText`（允许名单：`LagoonToasts` 里的退回一处；B1 拆出时临时放宽见第 6 节）。`import android.widget.Toast` 在受保护的 `ExoPlayerService` 里只用常量，列入允许名单。
   - Compose：任何目录禁止 `AlertDialog`、`BasicAlertDialog`、`DropdownMenu`、`ExposedDropdownMenuBox`、`Snackbar`、`SnackbarHost`、`DatePicker`、`TimePicker`、`TooltipBox`；`ui/design/` 以外不许 import `androidx.compose.material3` 的组件，允许 `Text`、`Icon`、`LocalContentColor`、`ripple`、`MaterialTheme`、`minimumInteractiveComponentSize`、`ExperimentalMaterial3Api`。现有 5 处（`DownloadSheet` 的 `RangeSlider`、`LoginScreen` 两处和 `PlayerScreen` 的 `CircularProgressIndicator`、`UpdatePrompt` 的 `LinearProgressIndicator`、`SavedScreen` 的 `SwipeToDismissBox`，以及 `ComposeSheetDialog` 的 `rememberModalBottomSheetState`）都已传 Lagoon 颜色，U9 里包成 `ui/design` 组件（`LagoonRangeSlider`、`LagoonSpinner`、`LagoonProgressBar`、`SwipeToDelete`），包不了的逐条进允许名单。
   - 弹层内容里禁止直接读 `screenHeightDp`（C2；允许名单：`Glass.kt`、`GameScreen.kt` 的矮窗口判断）。
   - 颜色：`ui/design/theme` 以外禁止 `Color(0x…)`（B5、B6 做完后为 0 处）。
   - **界面登记**：脚本列出所有 `LagoonBottomSheet(`、`ActionSheet(` 调用点（按 `文件#函数[#序号]`）、`PlayerSheet` 和 `ChatSheet`、`SheetRequest`、`PageId`、路由、清单里的 Activity、`DialogFragment` 子类；任何一个不在 `surfaces.md` 里就失败，`surfaces.md` 里有而代码里已没有的也失败（防止清单过期）。
   - 脚本自己要有测试：每条规则放一个违规样例，确认会失败。
2. **配色契约测试（新）**：`LagoonThemeContractTest` 逐个检查 `MaterialTheme.colorScheme` 的每个槽位都等于某个 Lagoon 令牌，没有一个是 Material 默认值；用反射遍历槽位，以后 Material 新增槽位会自动被检查到。
3. **截图测试（CI，Robolectric）**：时间选择子页（24 小时制、12 小时制、结果为 0 时的禁用状态）、播放清单标签弹层、回放页顶栏（从查找未列出回放进入）、聊天 6 种高亮行、头像占位，手机竖屏和 1.3 倍字号；CI 录基线。
   **模拟器实拍（不能用 Robolectric，原因见 C2）**：手机横屏的播放菜单和时间选择子页、键盘弹出时的下载弹层和搜索用户弹层、平板横屏的播放菜单和筛选弹层，以及 8 处限高弹层各一张手机横屏。
4. **交互和单元测试**：
   - 时间选择：12 小时制换算（上午 12 点 = 0 点，下午 12 点 = 12 点）、跨午夜（23:50 选 00:10 = 20 分钟）、选当前这一分钟时确认禁用、12/24 小时制切换后显示正确。
   - 进画中画时播放菜单（含时间选择子页）关闭，退出后不再出现。
   - `sheetMaxHeight` 四组数值（C2）。
   - 主列表点表情开表情卡片、点文字开用户卡片、点无详情徽章开用户卡片。
   - 去广告按钮落在去广告子页，返回回到播放器。
   - 查找未列出的回放后顶栏显示“回放 · 频道名”和标题。
   - 提示条：13 个入口各出一条 Lagoon 提示条；弹层开着时提示条在弹层里可见；画中画时走系统 Toast；提示条显示时点下面的按钮仍有效。
5. **性能**：C1 后跑 `ChatStressBenchmark`（每秒 30 条、保留 600 条）。**同一台设备、同一次会话里，v0.2 的 bench 包和 U9 的 bench 包轮流跑（各 5 轮，交替）**，U9 的丢帧中位数不差于 v0.2 的 5%。冷启动同样做同会话 A/B，误差内不变。测试设备用 U7 用过的那台；如果是真机，按项目规矩真机性能测试顺序进行、不和别的真机测试并行。
6. **受保护代码**：预计不碰。B2、B3 用同名资源替换、`setColor` 不加在 `ExoPlayerService` 里，就是为了不改受保护代码；守护检查对 `ExoPlayerService` 只加允许名单不改代码。如果实际必须改，PR 写受保护代码声明。去广告回归照常跑（广告日志计数）。
7. **巡检**：U9 收尾按 `surfaces.md` 全量复拍（第 5 节的组合），每一行判为“新”，或者有明确记录的例外（系统界面）。拼图和对照表放进 `docs/ui-overhaul/u9/`。
8. **真机**：在 OnePlus 15 上装 U9 的 qa 包，走一遍 `surfaces.md` 里标为“常用”的行（播放器、播放菜单、聊天、关注、设置）。**U7 线程欠的真机冷启动和丢帧测试单独记录，不阻塞 U9 合并**（需要手机保持解锁并充电，等 Mitchell 方便时做）。
9. **D3 选 (a) 的附加验收**：冷启动时系统启动画面的底色和图标不变；两个对话框窗口（下载、完整性校验）的背景、变暗和动画不变；安装包大小变化写进报告。
10. 门槛达不到时停下来问 Mitchell，不自行放宽。

## 9. 不在 U9 范围

- 系统界面（第 3 节 D 组），包括媒体通知的按钮图标（受保护代码）。
- U8 盘点里留下的架构清理（nav_graph 残留、残留 Fragment、旧数据库表等），留给后面的“架构清理”。
- 共享元素转场、返回手势预览等新功能。

---

## 附录 A：从代码生成的界面清单（master `d8ac2a4`）

“代码”一栏是读代码的结论：**L** = 用 Lagoon 组件画的；**旧** = 旧 Material 组件；**主题** = 窗口主题问题。“登” = 内容随登录变化，两种状态都要拍；“输” = 有输入框，要拍键盘弹出。U9 第 0 步在这张表上加截图编号和实拍结论。

### A.1 Activity 和对话框窗口

| 编号 | 界面 | 代码 | 备注 |
| --- | --- | --- | --- |
| `MainActivity` | 主窗口：外壳、底部胶囊/侧栏、所有页面、播放器 | L | 登 |
| `SettingsActivity` | 设置（首页、16 个子页、搜索、平板双栏） | L | 登（账号行） |
| `LoginActivity` | 登录：网页登录、电视设备码 | L + Twitch 网页 | B7 |
| `DownloadDialog` | 下载弹层（`ComposeSheetDialog` 子类） | L / 主题 | A4，输（文件名、范围） |
| `IntegrityDialog` | 完整性校验 | L + Twitch 网页 / 主题 | A4、B7 |

### A.2 页面（导航路由）

| 编号 | 界面 | 代码 | 备注 |
| --- | --- | --- | --- |
| `FollowingRoute` | 关注（直播、回放、频道、游戏四段） | L | 登 |
| `BrowseRoute` | 浏览（游戏网格） | L | |
| `SavedRoute` | 我的（已保存、下载、书签、筛选） | L | |
| `GamesRoute` | 按标签的游戏 | L | |
| `TopStreamsRoute` | 热门直播 | L | |
| `SearchRoute` | 搜索（直播、回放、频道、游戏） | L | 输 |
| `GameRoute` | 游戏页 | L | |
| `ChannelRoute` | 频道页（含简介 Markdown） | L（B8 字体） | 登（关注按钮） |
| `TeamRoute` | 团队页 | L | |

### A.3 `LagoonBottomSheet` 调用点（27 处）

| 编号 | 界面 | 代码 | 备注 |
| --- | --- | --- | --- |
| `SearchScreen.kt#UserLookupSheet` | 搜索用户 | L | 输 |
| `FollowingScreen.kt#VideoSortSheet` | 关注 › 回放排序 | L | 登 |
| `FollowingScreen.kt#ChannelSortSheet` | 关注 › 频道排序 | L | 登 |
| `FollowingScreen.kt#ConfirmSheet` | 关注 › 确认（取关等） | L | 登 |
| `ComposeSheetDialog.kt#onCreateView` | 对话框弹层宿主（`DownloadDialog` 用） | L | 按子类登记 |
| `FilterSheet.kt#FilterSheet` | 浏览/游戏/热门的筛选（含标签、语言两层） | L | C2 三处限高，输 |
| `ViewerList.kt#ViewerListSheet` | 观众列表 | L | C2 |
| `ChatSheets.kt#ChatSheets` | 聊天弹层（6 种，见 A.6） | L | C2 两处 |
| `UpdatePrompt.kt#UpdatePrompt#1` | 有新版本 | L | |
| `UpdatePrompt.kt#UpdatePrompt#2` | 正在下载更新 | L | |
| `SettingsSheets.kt#ChoiceSheet` | 设置 › 单选 | L | |
| `SettingsSheets.kt#SliderSheet` | 设置 › 滑块 | L | |
| `SettingsSheets.kt#TextSheet` | 设置 › 文字输入 | L | 输 |
| `SettingsSheets.kt#TabsSheet` | 设置 › 标签页顺序 | L | |
| `SettingsSheets.kt#ConfirmSheet` | 设置 › 确认操作 | L | |
| `SettingsSheets.kt#AdLogSheet` | 去广告日志 | L | |
| `SourceListsPage.kt#CustomProxySheet` | 自定义代理 增/改 | L | 输 |
| `SourceListsPage.kt#StreamProxySheet` | 直播代理 增/改 | L | 输 |
| `SourceListsPage.kt#VideoSwapSheet` | 回放替换 增/改 | L | 输 |
| `LoginScreen.kt#LoginPromptSheet#1` | 以电视设备登录？ | L | |
| `LoginScreen.kt#LoginPromptSheet#2` | 登录遇到问题 | L | |
| `LoginScreen.kt#LoginPromptSheet#3` | 网页文字大小 | L | |
| `PlayerSheets.kt#PlayerSheetHost` | 播放菜单（见 A.5） | L | C2 |
| `SavedScreen.kt#DeleteDownloadSheet` | 删除下载 | L | |
| `SavedScreen.kt#MoveSheet` | 移到应用存储 | L | |
| `SavedScreen.kt#QuestionSheet` | 我的 › 确认 | L | |
| `SavedScreen.kt#BookmarkSortSheet` | 书签排序 | L | |

### A.4 `ActionSheet`（长按/“…”菜单）调用点（24 处）

| 编号 | 界面 | 代码 |
| --- | --- | --- |
| `TopStreamsScreen.kt#TopStreamsScreen` | 热门直播长按 | L |
| `ChannelScreen.kt#ChannelScreen` | 频道“…” | L（登） |
| `ChannelScreen.kt#ChannelVideoCell` | 频道回放长按 | L |
| `ChannelScreen.kt#ChannelClipCell` | 频道剪辑长按 | L |
| `ChannelScreen.kt#SuggestionCell` | 频道推荐长按 | L |
| `ChannelScreen.kt#ChannelHeaderBlock` | 频道头直播长按 | L |
| `TeamScreen.kt#MemberCell#1` / `#2` | 团队成员（直播中/离线）长按 | L |
| `GameScreen.kt#StreamCell` / `#VideoCell` / `#ClipCell` | 游戏页三类长按 | L |
| `SearchScreen.kt#ResultItem#1` / `#2` | 搜索结果（直播/回放）长按 | L |
| `FollowingScreen.kt#LiveSegment` / `#VideosSegment` / `#ChannelsSegment` / `#GamesSegment` | 关注四段长按 | L（登） |
| `FollowingScreen.kt#AccountSheet` | 关注页账号菜单 | L（登） |
| `SettingsScreen.kt#AccountSheet` | 设置账号菜单 | L（登） |
| `SavedScreen.kt#SavedScreen` | 我的 › “…” | L |
| `SavedScreen.kt#DownloadCell` / `#BookmarkCell` / `#FilterCell` | 下载、书签、筛选长按 | L |
| `GamesScreen.kt#GamesScreen` | 游戏网格长按 | L |

### A.5 播放菜单页（`PlayerSheet`）

| 编号 | 界面 | 代码 | 备注 |
| --- | --- | --- | --- |
| `PlayerSheet.Settings` | 播放设置首页（直播/回放/剪辑/离线各不同） | L | |
| `PlayerSheet.Quality` | 画质 | L | |
| `PlayerSheet.Speed` | 倍速 | L | 回放 |
| `PlayerSheet.Sleep` | 睡眠定时 | L | |
| `PlayerSheet.SleepAt` | 在指定时间（**U9 新增**，替代 A1） | 旧 → L | |
| `PlayerSheet.Volume` | 音量 | L | |
| `PlayerSheet.Chapters` | 章节 | L | 回放 |
| `PlayerSheet.AdBlock` | 去广告 | L | C4 |
| `PlayerSheet.Aspect` | 画面比例 | L | |
| `PlayerFragment#showPlaylistTags` | 播放清单标签（调试） | **旧** | A2 |
| `PlayerFragment#pickSleepTime` | 时钟选择器 | **旧** | A1 |
| 录制弹层、回放菜单、观众列表 | 见 A.3 和下载弹层 | L | |

### A.6 聊天弹层（`ChatSheet`）和页内部件

| 编号 | 界面 | 代码 | 备注 |
| --- | --- | --- | --- |
| `ChatSheet.UserCard` | 用户卡片 | L | 登（操作按钮） |
| `ChatSheet.Thread` | 回复串 | L | |
| `ChatSheet.EmoteCard` | 表情卡片 | L | C1 |
| `ChatSheet.Points` | 频道点数 | L | 登 |
| `ChatSheet.Poll` | 投票 | L | 登 |
| `ChatSheet.Prediction` | 预测 | L | 登 |
| `ChatScreen#InputBar` / `#SuggestionRow` / `#ReplyBar` | 输入栏、补全、回复条 | L | 登，输 |
| `EmotePicker#EmotePicker` | 表情面板（含长按预览） | L | 登 |
| `ChatScreen#RoomModesRow` / `#ChatEventStrip` / `#PointsChip` / `#JumpToLatest` | 房间模式、事件条（突袭/投票/预测）、点数、回到最新 | L | 登 |
| `ChatScreen#ChatRow` | 6 种高亮行 | L（B6 硬编码色） | |

### A.7 设置子页（`PageId`，16 个）和设置弹层（`SheetRequest`，8 种）

子页：外观、播放、播放器按钮、聊天、去广告、回放替换、自定义代理、直播代理、下载、通知、备份、高级、缓冲、API 令牌、调试、关于，加上设置首页和设置搜索。弹层：单选、滑块、文字、标签顺序、确认、去广告日志、账号、确认退出（前 6 种对应 A.3 的 `SettingsSheets.kt`，后 2 种是 A.4 的 `AccountSheet` 和确认弹层）。代码全部是 L。播放器按钮预览渐变见 B5。

### A.8 状态、提示和通知

| 编号 | 界面 | 代码 | 备注 |
| --- | --- | --- | --- |
| 空状态、错误、加载、下拉刷新 | `ui/design/components/States.kt` | L | 每个列表页拍一次空状态和一次断网 |
| 播放器状态 | 加载、出错、离线、仅音频、迷你播放器、画中画 | L | 画中画按钮 B3 |
| 提示条 | 8 个页面的 `ToastHost` + 13 处系统 Toast | L / 系统 Toast | B1 |
| 通知 | 媒体播放、开播提醒、回放下载、直播录制 | 系统样式 + 我们的小图标 | B2、B3 |
| 系统启动画面 | 冷启动 | L 底色 + 新图标 | D3 验收 |
