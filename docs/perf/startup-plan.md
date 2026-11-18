# Lagoon 冷启动优化计划（修改版 1）

状态：修改版 1，回应 `docs/perf/startup-plan-review-1.md`（PR #32）。下一步是审阅 2，再修订，Mitchell 批准后才动代码。各审阅意见怎么处理的见第 9 节，需要 Mitchell 拍板的事见第 10 节。
起点：master `e028d7e9`（tag `ui-u9` 之后，含 PR #29 和计划、审阅文档）。

## 1. 目标和门槛

### 1.1 主门槛

OnePlus 15（CPH2747，Android 17）上，**同一次会话里交替测量**，新版冷启动中位数 / v0.1 中位数 **≤ 1.10**。具体规定：

| 项 | 规定 |
| --- | --- |
| 包 | **qa 包对 qa 包**（R8、无 lab 代码）。bench 包只用来录 trace，因为它带 `src/lab`，合并清单里多一个进程启动时就实例化的 `LabGlassInit` provider 和几个 lab Activity。两边的 qa 包都要用**专门的测量包名**重新打（见 1.3），不用现有的 `.qa`。 |
| 编译状态 | 用户实际的状态：安装 → 首启 → 发 ProfileInstaller 广播装上 APK 自带的配置文件 → `speed-profile` 编译（u8-report 第 3 节的第二种方法）。v0.1 走同一流程。每次准备后从 `dumpsys package` 读 dexopt 状态写进日志，不是 `speed-profile` 的那组作废。 |
| 启动方式 | 每次启动前 `force-stop` **两个**包，至少等 5 秒；两个包一次一次交替，每组内先后顺序轮换（ABAB / BABA），消除顺序影响。 |
| 指标 | `am start -W` 的 TotalTime（到第一帧，TTID）。 |
| 样本 | 一次会话 5 组 × 6 次，每个包 30 个启动。 |
| 过线 | **两次独立会话**（中间重启手机）的比值**都 ≤ 1.10**。每次会话同时报告配对比值的 bootstrap 95 % 区间；区间跨过 1.10 时写「勉强过」，不算不过，但要写出来。 |
| 目标 | 留余量，按 **≤ 1.06** 去做。 |

绝对毫秒数只作说明，不作门槛：v0.1 在不同会话里测出过 122、124–130 ms，换测量方法时自己会变（模拟器上约 390 → 470 ms）。

另外记一份「第一种状态」（安装后直接 `speed-profile`、没装 APK 的配置文件）作参考，不作门槛。

### 1.2 不许变差（数字）

和 U9 比（U9 = tag `ui-u9` 的同类包，同一次会话测）：

| 项 | 限值 | 怎么测 |
| --- | --- | --- |
| 玻璃滚动（`GlassBenchmark`） | jank 不高于 U9 + 0.3 个百分点；静止 0 帧 | 真机，照 G 阶段 |
| 聊天压力（`ChatStressBenchmark`） | jank < 5 %，P95 超时不比 U9 差 | 真机，照 U7 |
| 温启动、热启动 | 不慢于 U9 的 103 % | 同会话交替，`am start -W`（温：返回键退出后再开；热：Home 后再开） |
| 第一帧之后 1 秒内主线程忙碌时间 | 不多于 U9 的 105 % | 真机 perfetto，主线程 Running 时间，从第一次 `Choreographer#doFrame` 结束算起 1 秒 |
| 第一帧之后的前 10 帧 | 超过 2 个 vsync（120 Hz 下 16.7 ms）的帧数中位数不多于 U9 | 同一批 trace 里的 `DrawFrames` / `doFrame` 时长 |
| qa 包大小 | 增加不超过 0.2 MB | `apkanalyzer` |
| 视觉、功能 | 截图基线零变化；单元测试、lint 全过 | CI |

不许丢的东西照旧：所有功能、玻璃效果（模糊、噪点、高光、玻璃上的直播点规则）、页面动效、播放状态恢复、深链。

「关注页列表出现」不再作指标：门槛场景是全新安装、未登录，没有列表；登录后的数据来自 Twitch 网络，抖动远大于 5 %；U9 也没有 `reportFullyDrawn()` 可比。它换成上表的「第一帧之后 1 秒内主线程忙碌时间」，再加一次 Mitchell 手机上**登录状态的人工冒烟**（关注页、图片出现，没有明显变慢），在最后一次真机会话做。

### 1.3 测量用的包名

v0.1 的 qa 包和现在的 qa 包都是 `io.github.mitchell0402.lagoon.qa`，装不了两个，没法逐次交替；Mitchell 的手机上也装过 `.qa`（U3、U4 时），测量脚本的 `uninstall` 会清掉他的数据。所以：

- 现在的代码：构建脚本加一个只改包名后缀的属性（例如 `-Plagoon.perfId`，qa 构建类型的后缀变成 `.qa.perf`），不改任何行为。
- v0.1：在 `E:\Projects\Lagoon-baseline` 的 v0.1 源码上做同样的改动，打 `.qa.v01`（和以前打 `.bench.v01` 的办法一样）。
- U9 对照包：从 tag `ui-u9` 用 `git archive` 拷一份源码打（不占共用的 checkout），包名同样是 `.qa.perf`，和新版轮流全新安装，各自和 v0.1 交替。
- 脚本只允许卸载这几个测量包名和 `.bench`、`.bench.v01`，别的包名直接报错退出。

## 2. 差距从哪来（已知事实和待证实的部分）

按阶段的历史数据（详见各阶段报告）：

| 阶段 | 发生了什么 | 冷启动影响 |
| --- | --- | --- |
| U0/U1 | 只加了 Compose 依赖，页面没动 | 104 %（模拟器），可忽略 |
| U2 | 外壳和关注页换成 Compose | 第一帧开始要初始化 Compose 运行时 + 组合外壳和关注页 |
| G | 玻璃（Haze） | 首帧着色器编译 6 → 12；已用「第 3 帧才开装饰和模糊」修掉大部分（g-report 第 6 节、PR #16） |
| U3 | 基线配置文件 | 但 U3–U7 录的是 lab 组件展示页，而且是 R8 之后录的，等于没用（u8-report 第 3 节） |
| U8 | 重新录基线配置文件；一个 ComposeView 承载全部页面 | 模拟器 115 % → 110 %（第二种方法） |
| U9 | 多了一个 ComposeView（吐司层） | 真机 117 %（v0.2 120 %），**但这是第一种方法、没装配置文件的数字** |

U9 真机的 117 % 不能当起点：`coldstart7.py` 是先编译后首启，没有发 ProfileInstaller 广播，两边都没用上配置文件；而且只停要启动的那个包，另一个包还活着。U8 在模拟器上从第一种换到第二种，比值从 133 % 降到 110 %。所以真机上真正的差距要 S0 重测才知道，可能已经接近 110 %，也可能不是。

已核对的事实：

- U9 和 v0.1 的 APK 都**只有一个 `classes.dex`**（U9 bench 9.41 MB、qa 9.29 MB，v0.1 8.52 MB）。
- U9 APK 里的 `baseline.prof` 是好的：bench 4,077 个类，48,547 个方法里约 12.9k 标了启动、约 8.9k 热方法；qa 4,065 个类。「无 R8 构建上录、R8 改写」这条路是通的。
- v0.1 的 qa 包也带 5 个一样的初始化器（WorkManager、emoji2、ProcessLifecycle、ProfileInstaller、OkHttp `PlatformInitializer`），另外还有 ML Kit 的 `MlKitInitProvider`。所以初始化器不是 v0.1 和 U9 的差别所在；但门槛是比值，从 U9 身上省下的任何一毫秒都算数，不管它是哪个阶段带进来的。
- `BaselineProfileGenerator` 已经在用 `rule.collect(..., includeInStartupProfile = true)`，录制会同时产出启动配置文件；只是没有放进 app，构建也没打开 dex 启动布局。

推测（S0 证实或推翻）：剩下的差距主要是 **Compose 第一帧本身**（运行时类加载和验证、外壳 + 关注页的组合 / 测量 / 绘制）加上 **第一帧之前主线程上的杂项**（建 `MainViewModel` 的依赖、打开数据库、字体、第二个 ComposeView）。

## 3. 候选点

每项写了读代码的粗估（真机、第二种方法）和把握。数字在 S0 之后换成实测，记在 `startup-s0.md` 的账本里（第 4 节 S0 第 4 步）。

### A. 构建层（不改行为）

1. **当前代码上重录基线配置文件**（0–5 ms，中）。要回答的是 U9 新加的代码（吐司层 `GlobalToasts.kt`、弹层、主题包装）有没有被覆盖：在当前代码上用 `-Plagoon.profiling` 重录一次，和现在的 `app/src/main/baseline-prof.txt` 做 diff，看新增了哪些规则。录制只做未登录（见第 10 节第 3 条）。
2. **启动配置文件 + dex 启动布局**（0–3 ms，低）。单 dex 下 R8 做的是把启动类排到 dex 前面，省读 dex 时的缺页；门槛测量不清页缓存，所以收益小。手机重启后第一次启动受益多一些，不进门槛。做法：
   - 生成器已经产出启动配置文件，先查 AGP 9.5 的文档：**不用 `androidx.baselineprofile` 插件**时，启动配置文件放在哪里、用哪个开关打开 dex 启动布局（AGP 8.x 时是 `android.experimental.art-profile-r8-rewriting` 和 `android.experimental.r8.dex-startup-optimization`，名字要按 9.5 的文档核实，不凭记忆）。
   - 能不靠插件就用这条路；插件路线只作后备（它会生成 `benchmarkQa`、`nonMinifiedQa` 等构建类型，和 `:benchmark` 模块自己的 `benchmark` 构建类型、`.bench` 包名、所有脚本冲突），要走插件路线先回来改计划。
   - 检查：`apkanalyzer` 看启动类是否排在 dex 前面、`baseline.prof` 的规则数、qa 包大小。
3. **dex 压缩存放**（`build.gradle.kts` 第 126 行 `useLegacyPackaging = true`，为了 APK 小 4.7 MB）。`speed-profile` 之后理论上不影响启动；S0 用一次「不压缩」的 qa 包对照确认，没差别就保持现状。

### B. 进程启动（Application 和初始化器）

1. **WorkManager 按需初始化**（1–3 ms，中）。移除清单里的 `WorkManagerInitializer`（`tools:node="remove"`），`XtraApp` 实现 `Configuration.Provider`，第一次 `WorkManager.getInstance` 时才建。`MainActivity` 里开着直播通知时的周期任务排期（第 474 行）要挪到第一帧之后的后台，否则会在主线程触发初始化。另外两处用到它的地方（`ChannelPage.kt` 第 158 行，`SettingsViewModel.kt` 第 507、520 行）按需初始化没问题，但进回归清单。
2. **数据库和 `MainViewModel` 的仓库提前在后台建**（0.5–3 ms，低）。`MainActivity` 第一次用 `viewModel`（第 122 行 `by viewModels`，最晚在第 397 行 `restorePlayerFragment()`）就在主线程走 `MainViewModelFactory`：建 graphQL、helix、player、offlineVideos、localChannelFollows、auth 几个仓库；`PlayerRepository` 要 DAO，于是主线程上 `Room.databaseBuilder(...).build()`，带 32 个迁移 lambda 和 `_Impl` 的反射。做法：`XtraApp.onCreate` 在后台线程先触发这几个 lazy（`by lazy` 默认同步，主线程最多等它做完，不会重复建）。**先看真机 trace 里这段占多少再做。**
3. **字体预热**（0.5–2 ms，中）。4 个 Plus Jakarta Sans 是 `Font(resId)` 阻塞加载。预热要走 **Compose 自己的解析器**：后台线程 `createFontFamilyResolver(context).resolve(PlusJakartaSans, weight)`，只解析第一帧用到的字重。API 26 以上 Compose 的资源字体结果缓存在它自己的全局 `TypefaceRequestCache`，`ResourcesCompat.getFont` 走的是 `TypefaceCompat` 的另一套缓存，用它预热可能只是把文件读进页缓存。验证：trace 里第一次 `doFrame` 没有字体加载切片。中文字形照旧走系统字体。
4. **SharedPreferences 提前加载**（< 0.5 ms，低）。`XtraApp.onCreate` 先调一次 `getSharedPreferences`（不读值），让 XML 加载和别的事并行。trace 里看得到等待才做。
5. **不做**：OkHttp 的 `PlatformInitializer`（只是把 context 交给 OkHttp 的 Android 平台层，几乎不花时间，去掉可能影响 OkHttp 的行为）；`EmojiCompatInitializer`、`ProcessLifecycleInitializer`、`ProfileInstallerInitializer` 保留。
6. **顺手清理**：`XtraApp` 里 `< Q` 的 Conscrypt 那段是死代码（最低 API 29），删掉，对启动没影响。Conscrypt 依赖本身带 4 个 ABI 的 `libconscrypt_jni.so`（qa 包里合计约 9.4 MB），删依赖能明显减小 APK，但和启动无关，是否顺带做见第 10 节第 5 条。

### C. MainActivity.onCreate（第一帧之前的主线程）

1. **关注页组合里的网络查询**（0.2–0.5 ms，中）。`FollowingDestination.Content()` 第 162 行在 `isNetworkAvailable` 还是 null 时，每次组合都调 `hasValidatedNetwork()`（`getNetworkCapabilities`，binder），第一帧就会调；第 128 行的 effect 里又调一次。`MainActivity` 在 `onCreate`（第 183 行附近）已经查过同一件事。做法：`MainActivity` 把那次结果存成单独的字段给关注页用。**不要**写进 `isNetworkAvailable`：第 201 行的比较会因此认为状态没变，跳过第一次令牌校验和更新检查。
2. **保留在原位**：网络回调注册（第 345 行）、画中画广播接收器。各是一次 binder，零点几毫秒，网络回调还牵着断网提示、第一次令牌校验和更新检查（第 194–231 行）的时序。只有真机 trace 显示超过 1 ms 才重新考虑。
3. **清理项，不算收益**：`GlassPolicy` 最多读三次（构造第 72 行、`refresh()` 第 121 行、注册 `addCrossWindowBlurEnabledListener` 第 113 行时的立即回调），贵的只有第一次 binder，之后都有缓存；`restorePlayerFragment()` 在 `onCreate`（第 397 行）和 `onResume`（第 504 行）可能查两次播放状态（查询在 `Dispatchers.IO`，不影响首帧）。顺手去重，不进账。
4. 完整性检查（第 470 行）只在开了 `ENABLE_INTEGRITY` 且令牌过期时做事，看 trace 再定。`migrateSettings()` 平时只读几个 key，保留。

### D. 第一帧的 Compose 工作

1. **吐司层延后建立**（0.5–2 ms，中）。`activity_main.xml` 里 U9 加的 `toastOverlay` 在 `onCreate`（第 1022 行）就 `setContent`，第二个组合、第二个 `LagoonTheme`。改成**第一帧之后、主线程空闲时**建立（不是第一次要显示时，那样会把组合和类加载放到那一刻，可能正好在播放器上方卡一下）。要处理：
   - 启动时就可能有吐司：断网冷启动时第 189 行就 `lagoonToast(R.string.no_connection)`。吐司层建立之前的吐司先排队，建好后照常显示。
   - 玻璃装饰的初始值：吐司是 `GlassSurface(GlassLevel.Float)`（`Toast.kt` 第 112 行），读 `LocalGlassDecor`；新组合里的 `LagoonTheme` 前两帧装饰是关的，第一次吐司滑入时噪点和高光会在两帧后突然出现。给 `LagoonTheme` 加一个「装饰初始已打开」的参数，吐司层在外壳预热完之后建立并用它。用帧截图确认第一次吐司看不出跳变。
   - 无障碍：第 1030–1035 行按吐司显示与否切换 `importantForAccessibility`，延后建立时保留。
2. **首帧里的第二次组合**（看 trace）。首帧在 `LagoonTheme`、`MainShell`、`FollowingScreen` 里有没有一上来就写状态导致重组（如 `bottomBarHeightPx`、`selectedIndex`），能在首帧前算出来的直接算出来。
3. **类型安全导航**。9 个路由的 NavHost 建图会生成路由模式。只要配置文件覆盖了就够，不改结构；trace 里占得多再定。
4. **玻璃**。首帧已经不模糊、不画装饰（`LocalGlassDecor`、`ShellState.warmedUp`），视觉不动。只在真机 trace 里数第一帧的 `shader_compile`，确认没有额外的离屏层。
5. **Coil 的 ImageLoader 提前在后台建**（TTID 约 0；登录用户第一屏有图的那一帧可能省几十毫秒）。`XtraApp.newImageLoader` 在第一次请求图片时被调用，Compose 的 `AsyncImage` 在组合里取单例，所以在主线程；它会读设置，并按网络库设置取 `httpEngine` / `cronetEngine` / `okHttpClient`，用 HttpEngine 或 Cronet 的用户要在主线程建网络引擎。做法：第一帧之后在后台线程先取一次单例。不进 TTID 的账，进「第一帧之后」的指标。
6. **关注页分页流**（TTID 约 0）。更正草案：关注页用 `AnimatedContent` 只组合当前分段（`FollowingScreen.kt` 第 269 行起）；`collectAsLazyPagingItems()` 在组合里建对象，收集在 effect 里，组合提交后才开始；门槛场景未登录，几乎没东西可加载。所以这项不算 TTID 收益。可能改善的是登录用户第一帧之后的 CPU 竞争。**只有 S0 / S2 的「第一帧之后 1 秒主线程忙碌时间」显示分页流占得明显时才做**；做的话，第 108–133 行的刷新和断网恢复逻辑引用了全部 5 个 `LazyPagingItems`（直播分段本身就两个流：`streams` + `recentlyLive`），延后开始时这些引用必须仍然有效。

### E. 不做的事

- 不做「先画一个假骨架首帧、真内容晚一帧」这类只改数字的办法。
- 不删玻璃、不关动效、不把关注页换回 View。
- 不针对 Android 10–11 做额外工作（Mitchell 不考虑老版本），但最低版本 29 上不能崩。

### F. 收益账（S0 前的估计）

| 项 | 估计 | 把握 | 阶段 |
| --- | --- | --- | --- |
| A1 当前代码重录基线配置文件 | 0–5 ms | 中 | S1 |
| A2 启动配置文件 + dex 启动布局 | 0–3 ms | 低 | S1 |
| B1 WorkManager 按需初始化 | 1–3 ms | 中 | S2 |
| B2 数据库和仓库提前在后台建 | 0.5–3 ms | 低 | S2 |
| B3 字体预热（Compose 解析器） | 0.5–2 ms | 中 | S2 |
| B4 SharedPreferences 提前加载 | < 0.5 ms | 低 | S2 |
| C1 关注页组合里的网络查询 | 0.2–0.5 ms | 中 | S2 |
| D1 吐司层延后建立 | 0.5–2 ms | 中 | S3 |
| D2 首帧第二次组合 | 未知 | 低 | S3 |
| C3 GlassPolicy、播放状态去重 | ≈ 0 | 高 | 顺手 |
| D5 Coil、D6 分页流 | TTID ≈ 0 | 高 | S3（第一帧之后的指标） |

合计大约 3–16 ms。要省多少要等 S0 的真机重测；S3 之后仍 > 1.10 怎么办见 S4。

## 4. 分阶段实施

每个阶段一个分支、一个 PR，自己合并（项目常规权限）。**单项改动用 trace 切片验证**（第一帧之前主线程时间、对应切片的时长）；TTID 只在阶段结束时看。模拟器只做每阶段的回归 A/B（CPU 切片），而且要等别的线程不用模拟器时。

真机会话（每次都要 Mitchell 点头、手机解锁、充电、屏幕常亮）：

| 会话 | 时机 | 内容 |
| --- | --- | --- |
| P0 | S0 | 重测基线（第一种、第二种方法）+ 真机 trace |
| P1 | S1 结束 | 一次 TTID 会话 + 少量 trace，决定 S2 / S3 的规模 |
| P2、P3 | 最后 | 两次独立会话（中间重启）走门槛；P2 结束后做「不许变差」各项和登录状态的人工冒烟 |

### S0 测量和归因（不改产品代码）

1. **修脚本**。新脚本放进仓库 `tools/perf/coldstart.py`（让审阅能看到），替代 `Lagoon-baseline/u9/coldstart7.py`：
   - 准备：卸载（只限第 1.3 节的测量包名）→ 安装 → 首启 `am start -W` → 等 5 秒 → `force-stop` → `am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE -n <pkg>/androidx.profileinstaller.ProfileInstallReceiver`，记下结果码 → 等 3 秒 → `cmd package compile -m speed-profile -f <pkg>` → 从 `dumpsys package <pkg>` 读 dexopt 状态，不是 `speed-profile` 就作废这组。
   - `--method 1` 走旧流程（先编译、不发广播），只作参考。
   - 每次启动：`force-stop` 两个包，等 ≥ 5 秒；唤醒，确认 `mWakefulness=Awake`、`isKeyguardShowing=false`，不满足就作废这次；记 TotalTime、WaitTime。
   - 每组先后顺序轮换；每组开始记电量、电池温度、`dumpsys thermalservice` 的状态，热状态不是 0 的组标出来。
   - 输出 JSON：所有原始数、每包中位数、比值、配对比值的 bootstrap 95 % 区间、设备指纹。
   - 加构建属性 `-Plagoon.perfId`，打 `.qa.perf`；v0.1 源码上打 `.qa.v01`；U9 对照包从 tag `ui-u9` 打（第 1.3 节）。
2. **P0 真机重测基线**（要 Mitchell 同意）。第二种方法，qa 包，v0.1 对 U9，5 × 6；再用第一种方法跑一遍作参考；bench 包对 qa 包（同为 U9）跑一组 TTID，记下 bench 包多出来的固定开销，以后看 bench trace 时扣掉。
   - **决策点**：如果 U9 在第二种方法下已经 ≤ 1.10，先告诉 Mitchell，建议改成「保住并加固」：只做 S1 和 S2 里低风险的项，然后直接走 P2 / P3。是否这样缩小由 Mitchell 定。
3. **P0 真机 trace**。第二种方法下，v0.1 bench 和 U9 bench 各录 10 次冷启动 perfetto（`am start` + `perfetto`，bench 包是 profileable；sched、binder、`am`/`wm`/`view`/`gfx`/`dalvik` 类别）。用 TraceProcessor 拆成固定几段：`bindApplication`（含各初始化器、`LabGlassInit` 单独列出）、`activityStart` / `onCreate`（其中 `MainViewModel` 建立、`Room` 打开、`setContent` 两次各占多少）、第一次 `Choreographer#doFrame`（组合 / 测量 / 绘制、字体加载切片）、`RenderThread` 的 `DrawFrames`（`shader_compile` 次数）、主线程上的 `OpenDexFilesFromOat`、`VerifyClass`、JIT 编译数；以及第 1.2 节的「第一帧之后 1 秒主线程忙碌」和「前 10 帧」。
4. **模拟器 trace**（可选，模拟器空闲时）：同样拆段，只看 CPU 切片，作以后每阶段回归 A/B 的基准。
5. **dex 不压缩对照**（第 3 节 A3）：P0 里加一组「U9 qa 不压缩 dex」对 U9 qa，确认没差别。
6. **产出** `docs/perf/startup-s0.md`：新的基线数字（两种方法）、每段多花多少毫秒的表、第 3 节 F 的账本（估计 → 实测），之后每阶段更新账本。**S0 结果和本计划的估计不符时，先回来改计划（并审阅）再动代码。**

### S1 构建层：重录基线配置文件 + 启动配置文件

- 当前代码上重录（无 R8，`-Plagoon.profiling`），和现有 `baseline-prof.txt` 做 diff，写进报告：新增的规则主要来自哪些文件。
- 按 AGP 9.5 文档放入启动配置文件、打开 dex 启动布局（不用插件的路线，第 3 节 A2）；走不通就在报告里写明，A2 这项放弃或回来改计划走插件。
- 录制场景照旧：冷启动到关注页、浏览页开一个游戏再返回、我的、回到关注、账号弹层和设置首页；未登录。
- 检查：`baseline.prof` 规则数、启动类在 dex 里的位置、qa 包大小。
- 验证：模拟器 CPU 切片（`VerifyClass`、JIT 数、第一帧前主线程时间）；**P1** 一次真机 TTID 会话，按结果决定 S2 / S3 做多少（已经 ≤ 1.06 的话，S2 / S3 只做把握「中」以上、风险低的项）。

### S2 进程和 Activity

- B1 WorkManager 按需初始化，周期任务排期挪到第一帧之后的后台。
- B3 字体用 Compose 解析器在后台预热。
- C1 关注页复用 MainActivity 的第一次网络查询结果。
- 视 P0 trace：B2 数据库和仓库提前在后台建、B4 SharedPreferences 提前加载。
- 顺手：删 Conscrypt 死代码，`GlassPolicy`、`restorePlayerFragment()` 去重。
- 回归：
  - 断网冷启动：断网吐司出现（u2-report 第 98 行那条）；恢复联网后列表重载。
  - 第一次令牌校验和更新检查仍然只在第一次拿到网络状态时各做一次。
  - 深链冷启动、播放状态恢复、画中画按钮。
  - 直播通知开着时冷启动，周期任务仍被排上（`dumpsys jobscheduler`）；开通知 → 强行停止 → 重新打开 → 任务还在（`ForceStopRunnable`）。
  - 从设置页开、关直播通知；从频道页开通知。
  - 温启动、热启动（挪动的工作可能落到 resume 路径）。

### S3 第一帧的 Compose 工作

- D1 吐司层第一帧之后空闲时建立，带「装饰初始已打开」，启动时的吐司排队。
- D2 首帧内的状态写入导致的重组（视 trace）。
- D5 Coil 单例第一帧之后在后台先建。
- D6 关注页分页流延后：只在「第一帧之后 1 秒主线程忙碌」显示它占得明显时做。
- 用 Compose 组合 trace（`runtime-tracing`，只加在 profiling 构建里）确认每项的首帧耗时下降。
- 回归：截图基线零变化；吐司在播放器上方的位置（U9 B1）；第一次吐司的帧截图没有装饰跳变；断网冷启动的吐司照常出现；吐司显示时的无障碍；关注页 5 个分段切换和刷新、断网恢复后全部重载；第一帧之后前 10 帧（第 1.2 节）。

### S4（可选）S1–S3 后真机仍 > 1.10 才做

候选（要先在 trace 里看到对应的大块开销）：首帧之前在后台线程预先加载 Compose 关键类（`Class.forName` 预热）、减少首帧的离屏图层、`NavHost` 首帧只建起始路由。每项单独评估，任何会改视觉或交互的都先问 Mitchell。S4 之后仍 > 1.10：把账本和剩下的选项交给 Mitchell，由他决定接受现状还是继续（第 10 节第 4 条）。

## 5. 测量规矩

- **门槛只用同会话比值**，方法见第 1.1 节，脚本见 S0 第 1 步。
- **真机**：每次会话开始前确认屏幕常亮、未锁屏、充电；跑完把旋转等设置还原（手机原来是自动旋转）。真机测试一次只跑一个，不和其他线程的真机测试并行。按 Mitchell 2026-11-17 的话，**真机测试要他点头才开始**。两个 adb 设备时一律带 `-s`。
- **模拟器**：每阶段同一次会话 A/B，第二种方法；模拟器运行几小时后会退化，测量前从快照 `lagoon-v01-fulldata`（`-gpu swiftshader_indirect`）重启。绝对数不可信，只看 CPU 切片的相对差。不和别的线程抢模拟器。
- **每次报告写**：TTID 中位数、比值和 95 % 区间、dexopt 状态、热状态；对应阶段的 trace 切片数；第 1.2 节各项。

## 6. 风险

| 风险 | 对策 |
| --- | --- |
| 挪到第一帧之后的工作改变时序（播放恢复、深链、断网提示、令牌校验） | 网络回调、画中画接收器不挪；挪动的每项都列回归项 |
| 挪到第一帧之后的工作让第 2、3 帧变长（玻璃预热本来就在这两帧打开） | 第 1.2 节的「前 10 帧」限值；挪过去的工作尽量放后台线程而不是下一帧的主线程 |
| 挪到第一帧之后的工作拖慢紧接着测的 v0.1，比值被美化 | 每次启动前停两个包、等 ≥ 5 秒 |
| WorkManager 按需初始化后，进程被系统拉起时取不到配置；强行停止后的重新排期 | `Configuration.Provider` 是官方方式；单元测试 + 第 S2 节的 `ForceStopRunnable` 回归 |
| 吐司层延后：启动时的吐司丢失、第一次吐司装饰跳变或卡顿 | 吐司排队、装饰初始值、空闲时建立、帧截图确认 |
| 测量脚本卸载了 Mitchell 自己装的包 | 专用测量包名，脚本只允许卸载白名单里的包名 |
| 启动配置文件不靠插件在 AGP 9.5 上用不上 | 先查文档；走不通就放弃 A2 或回来改计划，不在 S1 里硬上插件 |
| 真机噪声大于单项收益（单次 ±5 ms） | 单项用 trace 切片验证；门槛用 30 次 × 两次会话 + 区间 |
| 手机发热降频 | 记热状态，非 0 的组标出来，必要时整组重测 |

## 7. 交付

- 每阶段：PR + `docs/perf/startup-sN.md`（测量表、trace 切片数字、账本更新、回归清单结果）。
- S0 额外交付 `tools/perf/coldstart.py` 和 trace 拆分脚本。
- 最后：`docs/perf/startup-report.md`，P2 / P3 的最终数字，对照第 1 节逐项写「过 / 不过」。
- 不发新版本；是否出 v0.2.1 由 Mitchell 决定。

## 8. 已核对、保留的部分

- 合并清单里的 5 个初始化器；`XtraApp.onCreate` 很轻、`XtraModule` 全是 lazy；Coil 单例第一次用时才建。
- 播放状态查询在 `Dispatchers.IO`（`PlayerRepository.getPlaybackStates`）。
- `migrateSettings()` 只有设置版本变化时才做重活。
- 吐司层在 `onCreate` 里 `setContent`（第 1022 行），里面是第二个 `LagoonTheme`。
- 第 3 节 E「不做的事」和第 6 节风险表的方向。

## 9. 修改记录（修改版 1 对审阅 1 的回应）

| 审阅意见 | 处理 |
| --- | --- |
| **B1** 脚本先编译后首启、不发广播，117 % 是无配置文件的数字 | 接受。第 1.1 节写明第二种方法和 dexopt 状态检查；S0 第 1 步重写脚本（进仓库），第 2 步真机重测基线，并加「已过线就缩小规模」的决策点；第一种方法只作参考。第 2 节把 117 % 标成无配置文件的数字，不再用「要省 9–10 ms」。 |
| **B2** 配对启动时另一个 app 还活着 | 接受。每次启动前停两个包、等 ≥ 5 秒，另外每组轮换先后顺序（第 1.1 节、S0 第 1 步、第 6 节）。 |
| **B3** 门槛：同会话比值 | 接受。门槛写成比值，绝对数只作说明（第 1.1 节）。 |
| **B3** 两次会话 + 区间 | 接受。两次独立会话都 ≤ 1.10，每次报告 bootstrap 95 % 区间，样本默认 5 × 6（第 1.1 节）。 |
| **B3** 最终用 qa 包 | 接受，并补了审阅没提到的问题：v0.1 和现在的 qa 包包名相同，Mitchell 手机上也装过 `.qa`，所以改用专门的测量包名（第 1.3 节）。bench 包只录 trace，并在 P0 量一次 bench 和 qa 的差。 |
| **B3** 「不许变差」要有数 | 接受。第 1.2 节逐项写了限值，包括温启动、热启动。 |
| **B3** 「列表出现」测不出来 | 接受。换成「第一帧之后 1 秒内主线程忙碌时间」+ 登录状态人工冒烟，删掉 `reportFullyDrawn()`（第 1.2 节）。 |
| **B4** 归因要有真机 trace；单项用 trace 验证 | 接受。S0 第 3 步真机各 10 次 trace；模拟器只作回归；第 4 节开头写明单项用 trace 切片验证、TTID 只在阶段结束看。真机会话列成 P0–P3。 |
| **F1** 只有一个 dex | 接受。A2 收益改成 0–3 ms、把握低，不再是「收益最大」的一项（第 3 节 A2）。 |
| **F2** 基线配置文件是好的，要查的是覆盖率 | 接受。A1 改成「当前代码上重录并 diff」（第 3 节 A1）。另外核对到生成器已经用 `includeInStartupProfile = true`，启动配置文件不用改录制。 |
| **F3** 分页流不在第一帧之前 | 接受。D6 改成 TTID ≈ 0，只在「第一帧之后」的指标显示有必要时做，并写明 5 个 `LazyPagingItems` 引用和直播分段两个流的约束。 |
| **F4** 字体缓存不共用 | 接受。B3 改用 Compose 的 `createFontFamilyResolver(...).resolve`，只解析首帧字重，trace 确认。 |
| **F5** GlassPolicy 去重几乎不花时间 | 接受。改成「顺手清理」，不进账（第 3 节 C3）。 |
| **F6** 网络回调、画中画接收器收益小风险大 | 接受。从 S2 删掉，保留在原位，除非真机 trace 显示超过 1 ms（第 3 节 C2）。 |
| **F7** 13 个 `repeatOnLifecycle` | 接受，删掉了这个计数（它本来就不是候选项）。 |
| **F7** WorkManager 的其他用处 | 接受。频道页、设置页加进 S2 回归。 |
| **F7** OkHttp `PlatformInitializer` | 接受。从计划删掉（第 3 节 B5）。 |
| **F7** Conscrypt 依赖可减 APK 约 9.4 MB | 死代码照删；删依赖和启动无关，列为第 10 节第 5 条请 Mitchell 定。 |
| **F7** `restorePlayerFragment()` 调两次 | 接受，顺手去重，不进账（第 3 节 C3）。 |
| **M1** 主线程建 MainViewModel 依赖和数据库 | 接受。新增 B2，先看真机 trace 再做。 |
| **M2** Coil ImageLoader 在主线程建网络引擎 | 接受。新增 D5，第一帧之后后台先建，算「第一帧之后」的指标。 |
| **M3** 关注页组合里的 binder 调用 | 接受。新增 C1，单独字段，不写进 `isNetworkAvailable`。 |
| **M4** SharedPreferences 提前加载 | 接受。新增 B4，trace 里看得到才做。 |
| **M5** 第一帧之后几帧的成本 | 接受。第 1.2 节加「前 10 帧」限值，第 6 节加对应风险。 |
| **R1** 吐司层按需 `setContent` 的风险 | 接受。D1 改成第一帧之后空闲时建立，启动吐司排队、装饰初始值、无障碍，回归加帧截图。 |
| **R2** baselineprofile 插件和构建类型冲突 | 接受。A2 先走不用插件的路线，插件只作后备，要用先改计划。 |
| **R3** 登录录制需要 Mitchell | 接受。默认只录未登录（第 3 节 A1），是否登录录制见第 10 节第 3 条。 |
| **R4** WorkManager 按需初始化后 `ForceStopRunnable` | 接受。加进 S2 回归。 |
| **第 5 节** 收益的现实估计 | 接受。第 3 节 F 列账本，S0 后换实测；S4 写明仍不过线时交给 Mitchell 决定。 |
| **第 6 节** 结构建议 | 接受。S0 顺序按建议重排（改脚本 → 真机基线 → 真机 trace → 模拟器 → 报告和账本）。 |

审阅之外，这次修订新核对到的：

- v0.1 和 U9 的 qa 包包名相同（都是 `io.github.mitchell0402.lagoon.qa`），见第 1.3 节。
- v0.1 的 qa 包也有同样 5 个初始化器（另加 ML Kit 的），所以初始化器不是两版的差别来源；但门槛是比值，省下的仍然算数。
- `BaselineProfileGenerator` 已经 `includeInStartupProfile = true`，S1 只缺「放进 app + 打开 dex 启动布局」。
- `Lagoon-baseline` 里没有 v0.2 的 qa 包（只有 bench 包；发布的 APK 在 `Lagoon-signing`，不碰）。v0.2 不进门槛，只作参考，用 bench 包看 trace 即可。

## 10. 需要 Mitchell 决定的事

1. **真机会话 P0**（S0 重测基线 + trace，约 1 小时，手机解锁、充电、常亮）什么时候可以开始。不做 P0，后面的阶段都没有可靠起点。
2. **P0 之后如果 U9 已经 ≤ 1.10**（第二种方法），是缩小成「保住并加固」（只做 S1 和 S2 里低风险的项，然后走 P2 / P3），还是照全计划做。我的建议是缩小。
3. **配置文件录制要不要登录状态**。登录要 Mitchell 在 bench 包上亲手登录，录完不退出（退出会在服务端吊销令牌）。默认只录未登录。
4. **S4 之后仍 > 1.10 时**：接受现状，还是继续找更大的改动（可能涉及首帧结构）。
5. **Conscrypt 依赖**：要不要顺带删掉（qa 包约小 9.4 MB，和启动无关，需要确认 OkHttp 不依赖它）。默认不在本计划里做，单独开一个小 PR。
