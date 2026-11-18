# U9 报告：遗留旧风格界面整改

日期：2026-11-17。分支 `ui/u9-legacy`，PR #28。计划：`docs/ui-overhaul/u9-plan.md`（修改版 2，Mitchell 2026-11-17 批准；D1 暖金、D2 点表情开表情卡片、D3 去掉 Material 组件库）。

## 结论

| 门槛（计划第 8 节） | 结果 |
| --- | --- |
| 1 守护检查进 CI（禁用名单、扫描范围、布局登记、界面登记、自测） | 通过：`tools/ci/legacy_ui_guard.py`，自测 17 例，允许名单只剩 6 条永久项（第 3 节） |
| 2 配色契约测试 | 通过：48 个颜色槽全部是 Lagoon 令牌（`LagoonThemeContractTest`） |
| 3 截图测试 / 模拟器实拍 | 通过：新增 12 张 U9 截图，140 张基线由 CI 重录并逐类看过（第 6 节）；手机横屏、平板横屏、键盘、刘海一侧在模拟器和真机实拍（第 5、8 节） |
| 4 交互和单元测试 | 通过：JVM 测试 663 项全过；新增 U9SleepAtTest、U9ChatTapTest、U9ToastTest、SheetHeightTest、U9SettingsNavTest、LagoonThemeContractTest、DarkThemeAttributesTest |
| 5 性能（同会话 A/B） | 通过：真机聊天压力 v0.2 和 U9 持平，冷启动 U9 143.0 ms vs v0.2 147.5 ms（第 7 节） |
| 6 受保护代码 | 未改。只改了 CI（PR 里有声明）；去广告相关代码没动 |
| 7 巡检 | 通过：v0.2、4b、U9 三个版本按同一脚本全量巡检，崩溃日志 0 条；逐行结论在 `u9/surfaces.md`（第 5 节） |
| 8 真机 | 做了（Mitchell 手机可用后补上）：性能 A/B、横屏弹层、刘海一侧、R3 文字选择、聊天点击（第 8 节） |
| 9 去掉 Material 库（8 条保障） | 通过，8 条全部做到（第 4 节） |

## 1. 做了什么（按计划第 6 节的提交顺序）

| 步骤 | 提交 | 内容 |
| --- | --- | --- |
| 0 | — | v0.2 全量巡检脚本 `tools/ui-inventory/u9_walkthrough.py`、界面清单 `docs/ui-overhaul/u9/surfaces.md`（103 个代码界面 + 12 行页内部件） |
| 2 | `35c64e97` | Compose 配色 48 槽全映射 + 契约测试；守护检查进 CI；窗口主题属性对照测试先录 v0.2 现值；Lint `MissingClass` 设为错误 |
| 3 | `e9221411` | A1「在指定时间」改成播放菜单子页（Lagoon 滚轮）；A2 播放清单标签改成弹层子页；源码里不再有 Material 组件库的 import |
| 4a | `2f14d10e` | 窗口主题父类换成 `Theme.AppCompat.NoActionBar`，平台部件读的颜色全部指向 Lagoon；A4 两个对话框窗口改用 `Theme.Lagoon.Dialog` |
| 4b | `b08e24a5` | 去掉 `com.google.android.material` 依赖；CI 加依赖检查和 qa 包扫描；锁住两个会被降级的传递依赖 |
| 5 | `2eaedcf4` | C2 弹层可用高度（状态栏/刘海以下、键盘以上），8 处限高改用它 |
| 6 | `24c426dc`、`89961cc8` | C1 聊天点击规则（D2）；C3 未列出回放的顶栏；C4 去广告设置直达 |
| 7 | `f5c5bf87` | B2 通知图标和强调色、B3 画中画按钮图标、B4 死资源、B5 头像占位色、B6 聊天颜色令牌（D1 暖金）、B8 简介字体；页面里的 5 个 Material 组件收进 `ui/design` |
| 8 | `ad3e2624`、`2ba25ee5` | B1 全局提示条，13 处系统 Toast 迁移；迷你播放器显示时提示条抬到它上方 |
| 10 | `dd116a8f`、`78236e7c` | CI 重录截图基线；横屏巡检发现弹层内容超高时按钮被截掉，弹层内容改为可滚动 |

### 和计划不同的地方

- `LagoonTimeWheel` 放在新文件 `ui/design/components/TimeWheel.kt`（计划写的是 `Pickers.kt`，那个文件已经很长）。
- 画中画「仅音频」按钮用 Lucide 的 headphones（耳机），不是 music：和播放器里「仅音频」的图标一致。
- 下载通知的小图标本来就是系统的下载动画图标（`stat_sys_download`），保留；只加了 Lagoon 强调色。改掉 `notification_icon` 的是开播提醒和媒体播放通知（后者通过 `media3_notification_small_icon` 指过去，`ExoPlayerService` 没动）。
- B1 只有约 130 行改动（计划的拆分条件是 600 行），所以留在主 PR 里，守护检查一次收紧到只剩 `GlobalToasts.kt` 一处系统 Toast。
- 提示条在迷你播放器显示时抬到迷你播放器卡片上方（计划只写了外壳底部导航之上）。
- 第 0 步的 v0.2 全量巡检没有发现新的旧组件（A 类）。巡检里新发现一处 C2 的另一半：手机横屏时弹层只有约 448 dp 高，排序这类弹层的按钮在 v0.2 里本来就被截掉一部分，C2 把弹层压到状态栏以下后截得更多。修法是让弹层内容超高时可滚动（`78236e7c`），三个排序弹层去掉各自的内层滚动（嵌套的无界滚动会崩溃，JVM 测试先抓到了）。
- 模拟器横屏：这台模拟器快照的方向传感器是关的，Android 14 以后 `settings user_rotation` 对应用也无效，`wm user-rotation lock` 同样不转。横屏改用横向的显示尺寸（`wm size 2992x1344`，和真机横屏一样是 997×448 dp），平板用 `wm size 2560x1600` + 密度 320。这种横屏的状态栏仍在长边、刘海不在侧边，所以刘海一侧放到真机上看（第 8 节）。

## 2. 三项决定的落实

- **D1 暖金**：`chatReward = #F2C14E`。左侧色条对卡片底色对比度约 10:1（要求 ≥ 3:1），和 `chatMention`（#FF6B6E）的对比度 1.7（只看亮度也能区分），`DesignTokensTest.chatHighlightTokens` 守着。
- **D2 聊天点击**：主列表的行不再整行可点。点表情或有详情的徽章开表情卡片；点用户名（按排版结果命中名字范围，按下有青绿底色）开用户卡片；点正文、空白、时间戳、无详情徽章无反应；链接照旧。回复提示行里只有「@名字」可点，开被回复的人；只有兑换没有消息的奖励行，点名字开这个人。回复串弹层同样规则；用户卡片里的消息列表不变。读屏：每行有「打开 X 的用户卡片」和最多 5 条「查看表情 Y」自定义操作。回调在列表外 `remember`、按行传参，行的图片表不会每次重建。
- **D3 去掉 Material 组件库**：见第 4 节。

## 3. 守护检查（门槛 1）

`tools/ci/legacy_ui_guard.py`，CI 的 Lint 任务里先跑自测 `tools/ci/test_legacy_ui_guard.py`（17 例，每条规则一个违规样例）再跑检查。

- 扫描范围：`app/src/*` 下的 `.kt`、`.java`、`res/**/*.xml`、各个清单、`baseline-prof.txt`，以及 `app/proguard-rules.pro`；同时匹配代码标识符和字符串字面量（包括 `Lcom/google/android/material/...` 这种 JVM 描述符），注释不算。
- 规则：旧 Material/平台组件禁用名单；Compose 弹窗、下拉菜单、Snackbar、日期时间选择器；`ui/design` 以外不许 import Material 3 组件（只允许 Text、Icon、ripple 等）；`screenHeightDp`；`ui/design/theme` 以外的 `Color(0x…)`；布局文件登记；界面登记（代码里的弹层调用点、播放菜单页、聊天弹层、设置子页和弹层、路由、Activity、对话框，和 `surfaces.md` 双向一致）；允许名单里不再匹配任何东西的条目也算失败。
- 允许名单 `tools/ci/legacy_ui_allowlist.txt` 最终只有 6 条，都是永久的：`activity_main.xml`（主窗口布局）、lab 源码集的示例颜色、`Glass.kt` 和 `GameScreen.kt` 的矮窗口判断、`DarkThemeAttributesTest` 里拿设备默认主题做对照、`GlobalToasts.kt` 里唯一的系统 Toast。

## 4. 去掉 Material 组件库（门槛 9，计划 4.5 的 8 条）

1. **两个提交**：4a `2f14d10e` 只换主题父类（库还在），4b `b08e24a5` 去依赖。两个提交各自在本地跑过 JVM 测试（643 项）、Lint 和 qa 构建（R8）并通过；推送时 CI 的这两次运行被后面的推送取消了，没有单独的 CI 结果。完整的 CI（4 项检查，含依赖检查和 qa 包扫描）在最终提交 `78236e7c` 上通过。单独为 4a/4b 补跑 CI 没有意义：第 3 步新增的截图那时还没有基线，截图任务在那两个提交上一定失败。
2. **依赖检查（CI）**：`qaRuntimeClasspath`、`releaseRuntimeClasspath`、`debugRuntimeClasspath` 上 `dependencyInsight` 都是「No dependencies matching」。
3. **依赖版本对照**（4b 前后 `releaseRuntimeClasspath`，原始列表在电脑 `E:\Projects\Lagoon-baseline\u9\deps`）：去掉了 material、cardview、constraintlayout(-core)、coordinatorlayout、dynamicanimation、graphics-shapes、transition、viewpager2、error_prone_annotations、kotlin-bom，共 12 个模块（244 → 232）。**有两个降级**：`customview` 1.2.0 → 1.0.0、`drawerlayout` 1.1.1 → 1.0.0（以前是 Material 把它们抬上去的，appcompat 自己只要 1.0.0）。已在 `app/build.gradle.kts` 用 `constraints` 锁回原版本，之后没有降级。
4. **qa 包扫描（CI）**：`tools/ci/qa_apk_scan.py`：mapping 里的 Material 类、dex 和打包 XML 里的包名字符串（UTF-8 和 UTF-16）、资源表里 `mtrl_`/`m3_`/`design_` 前缀，4b 后全部为 0；允许名单精确到 Compose Material 3 自己的 `m3c_` 和项目 `strings_design.xml` 里的 18 个 `design_` 字符串。同一个脚本对 4a 的包报 97 处，说明它真的能抓到。
5. **主题属性对照测试** `DarkThemeAttributesTest`：第 2 步先录 v0.2 现值，4a 后只有 5 项变化，都是有意的：

   | 属性 | v0.2（Material3） | U9 |
   | --- | --- | --- |
   | `colorAccent` | `#CCC2DC`（淡紫） | `#5EE2C4`（Lagoon 青绿） |
   | `colorControlActivated`（光标、选择手柄） | `#CCC2DC` | `#5EE2C4` |
   | `colorControlNormal` | `#CAC4D0` | 白 62 %（textSecondary） |
   | `textColorHighlight`（选中文字底色） | `#99D0BCFF`（紫） | 青绿 40 %（和 Compose 的选中色一致） |
   | `textColorPrimary` | `#E6E0E9` | 白 92 %（textPrimary） |

   窗口和启动画面背景、状态栏/导航栏颜色与对比度设置、`isLightTheme`、Activity 动画（`Animation.Material.Activity`）都没变。也就是说，v0.2 里平台输入框的光标、选择手柄、选中底色其实一直是淡紫的，4a 顺带修了。`Theme.Lagoon.Dialog` 的窗口形态、变暗、动画和原来的设备默认主题逐项相同（测试 `dialogWindowBehavesAsBefore`）。
6. **Lint**：`MissingClass` 设为错误，基线里没有它。
7. **4b 后 qa 包全量巡检**：4b 提交打出的 qa 包（`Lagoon-baseline/u9/lagoon-u9-4b-qa.apk`）在模拟器上按同一脚本走竖屏全部 17 段，和 v0.2 截图逐张对照：除了「在指定时间」从紫色时钟变成 Lagoon 滚轮，没有任何界面变样或空白。输入框的选择手柄、选中底色在页面和弹层里都是青绿。
8. **崩溃日志扫描**：每轮巡检全程 `adb logcat`，搜 `ClassNotFoundException`、`NoClassDefFoundError`、`NoSuchMethodError`、`NoSuchFieldError`、`Resources$NotFoundException`、`InflateException`、`Failed to instantiate custom view inflater`、`FATAL EXCEPTION`：v0.2（对照）竖屏/横屏/平板、4b 竖屏、U9 最终版竖屏/横屏/平板，全部 0 条。

**安装包大小**（同一提交线上 4b 前后的 qa 包，计划 N3）：4a 16,185,366 字节 → 4b 15,921,437 字节，小 263,929 字节（约 258 KB）。U9 最终 qa 包 15,927,241 字节；v0.2 qa 包 16,868,080 字节（中间还有 B4 删的 260 个 PNG）。

**启动画面和对话框窗口**：属性对照测试证明启动画面背景（`windowBackground`、`windowSplashScreenBackground`）和对话框窗口的背景、变暗、动画不变；实拍里冷启动画面、下载（录制）弹层和 v0.2 一样。

**R3（Compose 弹层窗口的主题）**：在 OnePlus 15（OxygenOS）上对比了搜索页普通输入框和「以 ID/登录取得用户」弹层输入框的长按选择：两边的选择手柄、选中底色都是 Lagoon 青绿，工具栏一致（系统的深色浮动工具栏）。结论「已核实，无需改」，A4 没有再给 Compose 弹层窗口包主题。

## 5. 巡检（门槛 7）

脚本 `tools/ui-inventory/u9_walkthrough.py`（17 段：关注四段和两种排序、长按、账号菜单、浏览和筛选、游戏页、我的、搜索和查找用户、文字选择、频道页各段和“…”、设置首页和全部 16 个子页、播放器、播放菜单各页、录制、聊天和聊天弹层），每段截图，全程 logcat。只读：不发消息、不关注、不兑换、不投票。

| 版本 | 竖屏 | 横屏（997×448 dp） | 平板（1280×800 dp） |
| --- | --- | --- | --- |
| v0.2 qa（对照） | ✓ 0 崩溃 | ✓ 0 崩溃 | ✓ 0 崩溃 |
| 4b qa | ✓ 0 崩溃 | — | — |
| U9 最终 qa | ✓ 0 崩溃 | ✓ 0 崩溃 | ✓ 0 崩溃 |

原图在电脑 `E:\Projects\Lagoon-audit\u9\`（`pre`、`pre2`、`post4b`、`final`、`final2`、`phone`），逐行结论在 `docs/ui-overhaul/u9/surfaces.md` 的「改前 / 改后」两列。脚本有几段在某些组合下跳过（未登录或横屏时找不到入口：热门直播入口、横屏下的播放器入口、录制/观众菜单项在小屏需要滚动），这些界面在其他组合或真机上拍到了。

对照里看到的变化都是计划内的：时钟选择器 → 滚轮子页；弹层（排序、筛选、查找用户、播放菜单、录制）在横屏时停在状态栏下面，超出部分可滚动；头像占位、播放器按钮预览没有紫色；聊天奖励行是暖金色；输入框选择手柄和选中底色从淡紫变成青绿。

## 6. 截图基线

CI 用 `record_screenshots` 重录（`dd116a8f`，140 个文件）。新增 12 张：「在指定时间」24 小时制、12 小时制、选中当前分钟（按钮禁用）、播放清单标签，各手机竖屏、手机横屏、1.3 倍字号。变化的旧基线逐类看过，都是预期的：

- 播放器按钮预览（`U6-ButtonsPreview-*`）：渐变去掉紫色一段（B5）。
- 播放菜单、睡眠定时、筛选、下载（`U6-Sheet*`、`U6-Sleep-PhoneLandscape`、`BrowseScreenshotTest` 的筛选、`U5-Download*`）：弹层内容按新的可用高度排版（C2）。
- 频道、团队、关注等带头像占位的页面：占位色换成青绿/蓝灰/暖灰（B5），很多只差几十字节。
- 聊天（`U7-*`）：奖励色暖金（B6）、每行的读屏操作不影响画面。

`78236e7c`（弹层可滚动）之后 CI 截图对比通过，没有再变。

## 7. 性能（门槛 5）

### 聊天压力（C1 之后，真机）

OnePlus 15（CPH2747，Android 17，120 Hz），无线调试，亮屏充电。`ChatStressBenchmark`（每秒 30 条、保留 600 条、每轮 3 次迭代），v0.2 的 bench 包和 U9 的 bench 包（`ad3e2624`）同一次会话里轮流各 5 轮（脚本 `Lagoon-baseline/u9/chatstress9.sh`，汇总 `stress_summary.py`，原始输出 `u9/phone-stress/`）。五轮中位数：

| | 帧数 | CPU 每帧 P50 / P90 / P95 / P99（ms） | 超出帧预算 P50 / P90 / P95 / P99（ms，负数=有余量） |
| --- | --- | --- | --- |
| v0.2 | 3571 | 5.72 / 7.51 / 8.67 / 11.97 | −9.88 / −8.04 / −6.78 / −3.36 |
| U9 | 3579 | 5.80 / 7.67 / 8.81 / 12.13 | −9.76 / −7.86 / −6.62 / −3.18 |

所有 10 轮的 P99 都是负的（掉帧不到 1 %），两边掉帧都约为 0，门槛「不差于 v0.2 的 5 %」通过。U9 的 CPU 时间高 0.1–0.2 ms，在轮间波动以内（v0.2 第 3 轮单独快了 1–2 ms）；C1 的名字命中检测和每行的读屏自定义操作没有带来可见的开销。

### 冷启动

同一台手机、同一次会话，`Lagoon-baseline/u9/coldstart7.py`：v0.1、v0.2、U9 的 bench 包（v0.2 和 U9 同包名，轮流全新安装），每个都 speed-profile 编译，和 v0.1 一次一次交替启动，3 组 × 6 次（`am start -W` 的 TotalTime 中位数）：

| | v0.1（同组） | 本版本 | 比 v0.1 |
| --- | --- | --- | --- |
| v0.2 | 122.5 ms | 147.5 ms | 120.4 % |
| U9 | 122.0 ms | 143.0 ms | 117.2 % |

U9 没有比 v0.2 慢（快 3 %，在误差内），门槛「误差内不变」通过。两者相对 v0.1 都高于基础计划的 110 %，这是 U2 以来就有的启动问题（U7 报告第 7 节），按计划在 U9 之后单独优化。

## 8. 真机（门槛 8）

Mitchell 的 OnePlus 15（CPH2747，Android 17，OxygenOS）在 2026-11-17 晚上重新可用（无线调试），所以原本推迟的真机项都补上了。装的是 U9 qa 包（`io.github.mitchell0402.lagoon.qa`，未登录；Mitchell 日常用的正式版没有动）。

- 性能 A/B：见第 7 节（聊天压力、冷启动）。
- R3 文字选择：见第 4 节，页面和弹层一致，都是 Lagoon 青绿。
- 手机横屏（`wm user-rotation lock 1`，刘海在左侧；测完恢复成原来的自动旋转）：播放设置、睡眠定时、「在指定时间」（横屏是 3 行滚轮，整页能放下，按钮需要轻滑一下）、录制弹层（下载窗口，`Theme.Lagoon.Dialog`）都停在状态栏下面，位置和主窗口一致，刘海一侧没有内容被挡。截图在电脑 `E:\Projects\Lagoon-audit\u9\phone\`。
- 聊天点击（D2）：点正文无反应；点 7TV 表情开表情卡片；点有详情的徽章（Pichu、Prime Gaming、3 个月订阅）开徽章卡片。点用户名这一项在真机上没能稳定点中（直播聊天一直在滚，脚本取坐标到点击之间行会移动），由带真实排版的 `U9ChatTapTest` 覆盖；你自己用的时候可以顺手确认一下。
- 键盘弹出时的弹层：真机上通过 adb 打字时输入法只显示候选条，没能拍到完整键盘弹出的样子；模拟器上带键盘的截图（搜索、聊天输入）正常，`sheetMaxHeight` 有单元测试。

## 9. 没做 / 以后

- 4.4 的 B7（登录和完整性校验网页变暗）：可选项，没做。它要求改完完整走一遍网页登录、电视设备码和完整性校验，需要你的账号操作。
- 冷启动相对 v0.1 的差距（117 %）：U9 之后的启动优化。
- 用户名点击在真机上的手动确认（见第 8 节）。
- 测试留在手机上的包：U9 qa 包（未登录）、bench 包和 benchmark 测试包；不需要可以卸掉，正式版不受影响。
