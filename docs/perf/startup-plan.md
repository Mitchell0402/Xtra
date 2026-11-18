# Lagoon 冷启动优化计划（草案 v1）

状态：草案，等审阅 → 修订 → 审阅 → 修订，Mitchell 批准后再动代码。
起点：master `0e334047`（tag `ui-u9` 之后，含 PR #29）。

## 1. 目标和门槛

| 项 | 要求 |
| --- | --- |
| 主门槛 | OnePlus 15（CPH2747，Android 17）上冷启动 **不慢于 v0.1 的 110 %** |
| 测量方法 | 和 U9 报告第 7 节相同：`Lagoon-baseline/u9/coldstart7.py`，bench 包，新装、首启后装上 APK 自带的配置文件、`speed-profile` 编译，和 v0.1 一次一次交替，3 组 × 6 次，取 `am start -W` 的 TotalTime 中位数 |
| 不许丢的东西 | 所有功能、玻璃效果（模糊、噪点、高光、玻璃上的直播点规则）、页面动效、播放状态恢复、深链 |
| 不许变差 | 聊天压力（`ChatStressBenchmark`）、玻璃滚动和静止（`GlassBenchmark`）、截图基线、单元测试、lint |

现状（真机，同一次会话）：v0.1 122.0–122.5 ms，v0.2 147.5 ms（120.4 %），U9 143.0 ms（117.2 %）。按 v0.1 ≈ 122 ms 算，110 % 是 **约 134 ms**，要从 U9 再省 **约 9–10 ms（7 %）**；留出噪声余量，目标定在 **≤ 130 ms（≈ 106 %）**。

注意：`am start -W` 的 TotalTime 是到 **第一帧**（TTID）为止。所以只有第一帧之前的主线程工作和渲染线程工作算数；第一帧之后的工作不影响这个数字，但会影响用户看到列表内容的时间，第 7 节另外记一个「列表出现」时间防止只是把工作往后挪。

## 2. 差距从哪来（已知事实）

按阶段的历史数据（详见各阶段报告）：

| 阶段 | 发生了什么 | 冷启动影响 |
| --- | --- | --- |
| U0/U1 | 只加了 Compose 依赖，页面没动 | 104 %（模拟器），可忽略 |
| U2 | 外壳和关注页换成 Compose | 第一帧开始要初始化 Compose 运行时 + 组合外壳和关注页 |
| G | 玻璃（Haze） | 首帧着色器编译 6 → 12；已用「第 3 帧才开装饰和模糊」修掉大部分（g-report 第 6 节、PR #16） |
| U3 | 基线配置文件 | 但 U3–U7 录的是 lab 组件展示页，而且是 R8 之后录的，等于没用（u8-report 第 3 节） |
| U8 | 重新录基线配置文件；一个 ComposeView 承载全部页面 | 模拟器 115 % → 110 % |
| U9 | 多了一个 ComposeView（吐司层） | 真机 117 %（v0.2 120 %） |

结论：剩下的差距是 **Compose 第一帧本身**（运行时类加载、外壳 + 关注页的组合 / 测量 / 绘制）加上 **启动期主线程上的杂项**。v0.1 的第一帧是 View 体系的关注页，类都在系统里已经预加载好了；Compose 的类全在我们的 dex 里，所以类加载和编译状态对我们的影响比 v0.1 大得多。

## 3. 读代码找到的候选点

按「预计收益 / 风险」排序。收益是估计，第 0 阶段要用 trace 证实或推翻。

### A. 构建层（不改行为）

1. **没有启动配置文件和 dex 布局优化。** 项目只有手写导入的 `app/src/main/baseline-prof.txt`（约 3.2 万行），没有用 `androidx.baselineprofile` Gradle 插件，所以没有 `startup-prof.txt`，也没有 `dexLayoutOptimization`：启动要用的类散落在多个 dex 里，冷启动要多读很多页。Google 给的典型收益是启动快几个百分点到 15 %。这是收益最大、风险最小的一项。
2. **基线配置文件是 U8 时录的。** U9 改了吐司层、弹层、主题包装等，首帧路径上的新代码可能没被覆盖（会走解释执行 + JIT）。需要在当前代码上重录，并检查 APK 里 `assets/dexopt/baseline.prof` 的规则数和 R8 改写后的命中率。
3. **dex 压缩存放**（`build.gradle.kts` 第 124 行附近的注释：为了 APK 小 4.7 MB 保持压缩）。`speed-profile` 编译后 dex 会被拷进 vdex，理论上不影响启动；第 0 阶段用一次「不压缩」对照确认，确认无影响就保持现状。

### B. 进程启动（Application 和初始化器）

合并后的清单里 `InitializationProvider` 在进程启动时同步跑 5 个初始化器：

- `WorkManagerInitializer`：每次启动都建 WorkManager（含数据库配置、调度器）。只有「直播通知」开着时才真用到。可改为按需初始化（移除清单里的初始化器，`XtraApp` 实现 `Configuration.Provider`，第一次 `WorkManager.getInstance` 时才建）。
- `okhttp3.internal.platform.PlatformInitializer`：OkHttp 5 新加的；第 0 阶段对照 v0.1 的合并清单，看 v0.1 有没有。
- `EmojiCompatInitializer`：Compose 文字依赖它显示新 emoji，会在后台线程加载，保留；只确认它没有阻塞主线程。
- `ProcessLifecycleInitializer`、`ProfileInstallerInitializer`：保留。

`XtraApp.onCreate` 本身很轻（`XtraModule` 全是 lazy）。最低 API 29 下 Conscrypt 那段已是死代码，顺手删掉（Mitchell 不考虑老版本）。

### C. MainActivity.onCreate（第一帧之前的主线程）

`onCreate` 里第一帧不需要、但现在同步做的事：

- 注册网络回调、画中画广播接收器；首次判断网络可用性（`getNetworkCapabilities`，binder 调用）。
- 14 个 `repeatOnLifecycle` 收集协程（本身便宜，但每个都会在 STARTED 时立刻跑一次）。
- `ENABLE_INTEGRITY` 检查、`LIVE_NOTIFICATIONS_ENABLED` 时 `WorkManager.getInstance(...).enqueueUniquePeriodicWork`（如果 B 改成按需初始化，这里会在主线程触发初始化，必须挪到第一帧之后或后台）。
- `restorePlayerFragment()` → `viewModel.getPlaybackStates()`：要确认 Room 查询在 IO 线程，且第一次打开数据库不在主线程。
- `GlassPolicy`：构造时 `read()` 一次，`onCreate` 里又 `read()` 一次，每次 3–4 个 binder 调用（窗口模糊、省电、低内存、高对比度文字）。去掉重复的一次。
- `migrateSettings()` 只在设置版本变化时做重活（`MediaCodecSelector` 查询），平时只读几个 key，保留。

做法：只把第一帧确实不需要的工作挪到第一帧之后（`decorView.post` 后一帧或 `Dispatchers.Default`），**播放状态恢复、深链处理、断网提示的时序不能变**（各自有回归测试或冒烟项）。

### D. 第一帧的 Compose 工作

- **两个 ComposeView。** `activity_main.xml` 里除了外壳还有 U9 加的 `toastOverlay`，它在 `onCreate` 就 `setContent { LagoonTheme { ... } }`：第二个组合、第二套主题 local、第二次 `LagoonTheme` 的 3 帧装饰计时。改成第一次要显示吐司时才 `setContent`（或者第一帧之后），吐司层的位置逻辑不变。真机 v0.2 → U9 没变慢说明它不大，但它是纯多余的首帧工作。
- **关注页一上来同时开 5 个分页流。** `FollowingDestination.Content()` 在第一次组合时对直播、视频、频道、游戏、最近开播 5 个 ViewModel 全部 `collectAsLazyPagingItems()`，于是第一帧前后就有 5 组 Room / 网络请求和分页器在抢主线程。v0.1 的 ViewPager 只建当前页（加相邻一页）。改为：只有当前分段的流在第一次组合时开始，其余分段在第一帧之后（或切过去时）再开始；刷新、断网恢复的「全部刷新」逻辑不变。
- **字体。** 4 个 Plus Jakarta Sans（各约 48 KB）是 `Font(resId)` 阻塞加载，第一次排字时在主线程读文件、建 Typeface。在 `XtraApp.onCreate` 用后台线程预热（`ResourcesCompat.getFont` 有进程内缓存，Compose 的 `AndroidFontLoader` 走同一个缓存），第一帧时就是命中缓存。中文字形照旧走系统字体。
- **类型安全导航。** 9 个路由的 NavHost 建图会用 kotlinx.serialization 生成路由模式，首帧要做一遍。只要基线 / 启动配置文件覆盖了就够，不打算改结构；第 0 阶段看 trace 里它占多少再定。
- **玻璃。** 首帧已经不模糊、不画装饰（`LocalGlassDecor`、`ShellState.warmedUp`），不再动它的视觉。只确认 Haze 的节点和 `GlassBackdrop` 在首帧没有触发额外的离屏层或着色器（第 0 阶段数 `shader_compile`）。

### E. 不做的事

- 不做「先画一个假骨架首帧、真内容晚一帧」这类只改数字的办法：TTID 会好看，但用户看到真内容的时间不变甚至更晚。
- 不删玻璃、不关动效、不把关注页换回 View。
- 不针对 Android 10–11 做额外工作（Mitchell 不考虑老版本），但最低版本 29 上不能崩。

## 4. 分阶段实施

每个阶段一个分支、一个 PR，自己合并（项目常规权限）；每阶段结束在模拟器做同会话 A/B（上一阶段 vs 本阶段，各和 v0.1 交替），真机只在 S1 结束和最后做（需要 Mitchell 同意并保持手机解锁、充电）。

### S0 测量和归因（只读，不改产品代码）

1. 在模拟器（API 37，从 `lagoon-v01-fulldata` 快照启动，`-gpu swiftshader_indirect`）上给 v0.1、v0.2、U9 的 bench 包各录 5 次冷启动 perfetto trace（`StartupBenchmark` 加 `PerfettoTraceRule`，或 `am start` + `perfetto` 手动）。
2. 用 TraceProcessor 拆成固定几段：`bindApplication`（含各初始化器）、`activityStart`/`onCreate`、`inflate`、第一次 `Choreographer#doFrame`（组合 / 测量 / 绘制）、`RenderThread` 的 `DrawFrames`（`shader_compile` 次数）、主线程上的 `OpenDexFilesFromOat`、`VerifyClass`、JIT 编译数。模拟器绝对数不可信（软件渲染），只看 **CPU 侧各段的相对差**；GPU 侧的数只作参考。
3. 对照 v0.1 和 U9 的合并清单（初始化器差异）、`dumpsys package dexopt`（编译状态和配置文件是否装上）。
4. 产出：`docs/perf/startup-s0.md`，一张「每段多花多少毫秒」的表，并据此调整 S1–S3 的顺序。**S0 结果和本计划第 3 节的估计不符时，先回来改计划再动代码。**

### S1 构建层：启动配置文件 + dex 布局 + 重录基线配置文件

- 引入 `androidx.baselineprofile` 插件，`:benchmark` 模块的 `BaselineProfileGenerator` 改成插件的生成任务（继续按类名启动 MainActivity、用 `-Plagoon.profiling` 无 R8 构建录）；同一次录制产出 `startup-prof.txt`，打开 `dexLayoutOptimization`。
- 录制场景：冷启动到关注页 + 切一遍底部各标签 + 打开设置首页（和 U8 的覆盖一致）；登录和未登录各录一次后合并。
- 检查：APK 里的 `baseline.prof` 规则数、主 dex 是否放了启动类（`apkanalyzer dex packages`）、qa 包大小变化（预计 < 0.1 MB）。
- 门槛：模拟器同会话 CPU 段有下降；真机测一次（要 Mitchell 配合）。如果真机已经 ≤ 110 %，S2/S3 仍做，但可以降为「做容易的、风险低的部分」。

### S2 进程和 Activity：初始化器与主线程杂项

- WorkManager 改按需初始化（`Configuration.Provider` + 清单 `tools:node="remove"`），`MainActivity` 里的周期任务排期挪到第一帧之后的后台。
- 视 S0 结果处理 OkHttp 的 `PlatformInitializer`（能改成后台或按需就改，不能就保留）。
- `XtraApp.onCreate` 后台预热 4 个字体；删 Conscrypt 死代码。
- `GlassPolicy` 去掉重复的 `read()`。
- `MainActivity.onCreate`：网络回调注册、画中画接收器、完整性检查挪到第一帧之后；确认播放状态查询不在主线程。
- 回归：断网冷启动（u2-report 第 98 行那条）、深链冷启动、直播通知开着的冷启动（周期任务仍被排上）、播放状态恢复、画中画按钮。

### S3 第一帧的 Compose 工作

- 吐司层按需 `setContent`。
- 关注页只先开当前分段的分页流，其余第一帧之后再开。
- 视 S0 结果：首帧在 `LagoonTheme`、`MainShell`、`FollowingScreen` 里有没有一上来就写状态导致的第二次组合（如 `bottomBarHeightPx`、`selectedIndex` 在首帧内被写），能在首帧前算出来的直接算出来。
- 用 Compose 组合 trace（`runtime-tracing`，只在 profiling 构建里加）确认每一项的首帧耗时真的下降。
- 回归：截图基线不变（视觉零变化）、吐司在播放器上方的位置（U9 B1）、关注页 5 个分段切换和刷新、断网恢复后全部重载。

### S4（可选）只有 S1–S3 后真机仍 > 110 % 才做

候选（需要先在 S0/S3 的 trace 里看到对应的大块开销）：首帧之前预先在后台线程加载 Compose 关键类（`Class.forName` 预热）、减少首帧的离屏图层、`NavHost` 改成首帧只建起始路由。每一项单独评估，任何会改视觉或交互的都先问 Mitchell。

## 5. 测量规矩

- **模拟器**：每阶段同一次会话 A/B，fresh install，按 u8-report 第 3 节第二种方法（首启装上配置文件再 `speed-profile`）；模拟器运行几小时后会退化（u8 记录），每次测量前从快照重启。
- **真机**：`coldstart7.py`；每次跑前确认屏幕常亮、未锁屏（`isKeyguardShowing`），跑完把旋转等设置还原；真机测试一次只跑一个，不和其他线程的真机测试并行。按 Mitchell 2026-11-17 的话，**真机测试要他点头才开始**。
- 每次报告同时写：TTID 中位数、比 v0.1 百分比、以及「关注页列表出现」时间（S1 起给 bench 包在关注页第一页数据显示时调 `reportFullyDrawn()`，只用于防止把工作挪到首帧后反而让内容更晚；v0.1 没有这个点，不作门槛，只和 U9 比，不许变慢超过 5 %）。

## 6. 风险

| 风险 | 对策 |
| --- | --- |
| 挪到第一帧之后的工作改变了时序（播放恢复、深链、断网提示） | 每项都列回归项；能留在原位的就不挪 |
| WorkManager 按需初始化后，直播通知的周期任务在进程被系统拉起时取不到配置 | `Configuration.Provider` 是官方方式，系统拉起 Worker 时会走它；加一条单元测试 + 模拟器上开通知等一个周期 |
| 分页流延后开始，切到其他分段时第一次多等一下 | 第一帧之后立即开始（不是等切换时），用户几乎不可能在一帧内切过去 |
| 真机噪声大于收益（单次 ±5 ms） | 坚持同会话交替 3×6；必要时加到 5×6 |
| 基线配置文件插件和 AGP 9.5 / 现有手写流程冲突 | S1 第一步先在分支上单独试插件；不兼容就保留现在的手写流程，查 AGP 9.5 文档看能否不用插件直接放 `startup-prof.txt`，在 S1 报告里写明结论 |

## 7. 交付

- 每阶段：PR + `docs/perf/startup-sN.md`（测量表、trace 截图或数字、回归清单结果）。
- 最后：`docs/perf/startup-report.md`，真机最终数字，对照第 1 节门槛逐项写「过 / 不过」。
- 不发新版本；是否出 v0.2.1 由 Mitchell 决定。
