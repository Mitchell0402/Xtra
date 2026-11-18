# Lagoon 冷启动优化计划（修改版 2）

状态：修改版 2，回应 `docs/perf/startup-plan-review-2.md`（PR #35）。审阅 2 的结论是「小改后交 Mitchell 批准」，所以这一版是**待批准版**：第 10 节五个决定按审阅 2 的建议写成了默认做法，标着「待批准」，Mitchell 批准（或改）之后才动代码。各审阅意见怎么处理的见第 9 节。
起点：master `1be5ad57`（tag `ui-u9` 之后只改了 `docs/`，`app/`、`benchmark/`、`gradle/` 和根构建脚本都没动）。

## 1. 目标和门槛

### 1.1 主门槛

OnePlus 15（CPH2747，Android 17）上，**同一次会话里交替测量**，新版冷启动中位数 / v0.1 中位数 **≤ 1.10**。具体规定：

| 项 | 规定 |
| --- | --- |
| 包 | **qa 包对 qa 包**（R8、无 lab 代码）。bench 包只用来录 trace，因为它带 `src/lab`，合并清单里多一个进程启动时就实例化的 `LabGlassInit` provider 和几个 lab Activity。所有被测的包都用**每个版本一个的测量包名**重新打（见 1.3），不用现有的 `.qa`。 |
| 编译状态 | 用户实际的状态（u8-report 第 3 节的第二种方法），v0.1 走同一流程，见下面的「准备流程」。准备不合格的整组作废。 |
| 启动方式 | 每次启动前 `force-stop` **两个**包，至少等 5 秒；两个包一次一次交替，每组内先后顺序轮换（ABAB / BABA），消除顺序影响。 |
| 启动类型检查 | 每次 `am start -W` 都解析输出里的 `LaunchState:`，冷启动必须是 `COLD`，否则这次作废（顺便抓住 `force-stop` 没生效的情况）。 |
| 指标 | `am start -W` 的 TotalTime（到第一帧，TTID）。 |
| 样本 | 一次会话 5 组 × 6 次，每个包 30 个启动。 |
| 过线 | **两次独立会话**（中间重启手机）的比值**都 ≤ 1.10**。每次会话同时报告 bootstrap 95 % 区间（算法见 S0 第 1 步）；区间跨过 1.10 时写「勉强过」，不算不过，但要写出来。 |
| 目标 | 留余量，按 **≤ 1.06** 去做。 |

**准备流程**（每个包每次会话做一次）：

1. 卸载（只限 1.3 的白名单）→ 安装 → 首启 `am start -W`，**进程不停**，等 5 秒。
2. 进程还活着时发广播：`am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE --include-stopped-packages -n <pkg>/androidx.profileinstaller.ProfileInstallReceiver`。放在 `force-stop` **之前**，因为 `force-stop` 会把包重新置成「已停止」，系统默认不把广播投给已停止的包；`--include-stopped-packages` 是双保险。
3. 结果码**只接受 `1`**（`RESULT_INSTALL_SUCCESS`）。1.4.1 的接收器是强制写入，正常就是 `1`；其他值（包括 `2`「已经装过」）整组作废，日志写出结果码，先查原因，不放宽规则。
4. 等 3 秒 → `force-stop` → `cmd package compile -m speed-profile -f <pkg>`。
5. 两项检查，**都过**才算准备好：
   - `dumpsys package <pkg>` 的 dexopt 状态是 `speed-profile`；
   - **直接数配置文件内容**：`cmd package dump-profiles --dump-classes-and-methods <pkg>`，读 `/data/misc/profman/<pkg>-primary.prof.txt`，数类和方法的行数。dexopt 状态证明不了配置文件来自 APK：首启那 5 秒 ART 的 JIT 也会记一份，编译时会合并进去，广播失败时照样是 `speed-profile`。行数的下限按包定：S0 第 1 步在模拟器空跑时记下每个包的行数（U9 预计几千到上万条，APK 里有 4,065 个类、约 1.29 万个启动方法；v0.1 预计几百条，只有 3 KB 的库规则加首启 JIT），真机上低于空跑数的 80 % 就作废。

绝对毫秒数只作说明，不作门槛：v0.1 在不同会话里测出过 122、124–130 ms，换测量方法时自己会变（模拟器上约 390 → 470 ms）。

另外记一份「第一种状态」（安装后直接 `speed-profile`、不发广播）作参考，不作门槛；它的配置文件行数也记下来，正好说明两种方法的差别。

### 1.2 不许变差（数字）

和 U9 比（U9 = 1.3 里的 U9 对照包，同一次会话测）：

| 项 | 限值 | 怎么测 |
| --- | --- | --- |
| 玻璃滚动（`GlassBenchmark`） | jank 不高于 U9 + 0.3 个百分点；静止 0 帧 | 真机，照 G 阶段；`-Pandroid.testInstrumentationRunnerArguments.targetPackage=` 指向各自的 bench 测量包 |
| 聊天压力（`ChatStressBenchmark`） | jank < 5 %，P95 超时不比 U9 差 | 真机，照 U7，目标包同上 |
| 温启动 | 不慢于 U9 的 103 % | 同会话交替，`am start -W`，`LaunchState` 必须是 `WARM`。做法：这一段临时把「不保留活动」打开（`settings put global always_finish_activities 1`，会话开头先读出原值写进日志），按 Home 让 Activity 被结束、进程还在，再打开。跑完立即还原原值（和旋转设置一起进还原清单，脚本在 `finally` 里还原，结尾再读一次核对）。 |
| 热启动 | 不慢于 U9 的 103 % | 同上，但「不保留活动」保持原值，按 Home 后再打开，`LaunchState` 必须是 `HOT`。 |
| 主线程总工作量 | 「进程启动到第一帧后 1 秒」主线程 Running 时间不多于 U9 的 103 % | 真机 perfetto，bench 测量包（新版对 U9），各 10 次取中位数。窗口两边用**同一个长度**：U9 第一帧结束时刻的中位数 + 1 秒，从进程启动算起，这样新版第一帧变早也不会让窗口变短、少算工作。 |
| 第一帧之后的前 10 帧 | 超过 2 个 vsync（120 Hz 下 16.7 ms）的帧数中位数不多于 U9 | 同一批 trace（**新版 bench 对 U9 bench**，不和 qa 比，所以不用扣 bench 多出来的开销）的 `DrawFrames` / `doFrame` 时长 |
| qa 包大小 | 增加不超过 0.2 MB | `apkanalyzer` |
| 视觉、功能 | 截图基线零变化；单元测试、lint 全过 | CI |

只报告、不设限的两项（用来看工作被挪到了哪里）：「第一帧之前」和「第一帧之后 1 秒」两段各自的主线程 Running 时间。S2、S3 本来就是把工作从前一段挪到后一段，后一段单独设限会越做越容易超，超了也不说明变差了；总量限值证明工作是「挪了」不是「多了」，「前 10 帧」盯住首屏刚出来时的卡顿。

如果「不保留活动」在这台手机上打不开，或者打开后 `LaunchState` 仍不是 `WARM`，就在报告里写明「只测了热启动，温启动没有覆盖」，不拿热启动的数顶替。

不许丢的东西照旧：所有功能、玻璃效果（模糊、噪点、高光、玻璃上的直播点规则）、页面动效、播放状态恢复、深链。

「关注页列表出现」不再作指标：门槛场景是全新安装、未登录，没有列表；登录后的数据来自 Twitch 网络，抖动远大于 5 %；U9 也没有 `reportFullyDrawn()` 可比。它换成上表的「主线程总工作量」和「前 10 帧」，再加一次 Mitchell 手机上**登录状态的人工冒烟**（写法见第 4 节 P3）。

### 1.3 测量用的包名

v0.1 的 qa 包和现在的 qa 包都是 `io.github.mitchell0402.lagoon.qa`，装不了两个；Mitchell 的手机上也装过 `.qa`（U3、U4 时）和正式版。所以：

- **每个被测的版本一个包名**，几个包能同时装、直接交替，不用来回卸载。构建脚本加属性 `-Plagoon.perfId=<名字>`：qa 构建类型的后缀变成 `.qa.perf.<名字>`，benchmark 构建类型变成 `.bench.perf.<名字>`，不改任何别的东西，CI 不设这个属性。会用到的：

  | 包 | 来源 | 用途 |
  | --- | --- | --- |
  | `.qa.perf.v01`、`.bench.v01` | `Lagoon-baseline\src-v0.1`，在那份拷贝上直接改后缀（`.bench.v01` 以前就这么打过） | 门槛的分母；trace 对照 |
  | `.qa.perf.u9`、`.bench.perf.u9` | S0 加完属性的那个提交（见下） | U9 基线；第 1.2 节的对照 |
  | `.qa.perf.nocompress` | 同一提交，另加 `-Plagoon.dexUncompressed`（只在这个对照包上把 `useLegacyPackaging` 设成 false） | 第 3 节 A3 的对照，只在 P0 用 |
  | `.qa.perf.new`、`.bench.perf.new` | 每阶段结束时的代码 | 新版 |

- **U9 对照包不从 tag 拷源码打。** `ui-u9` 之后 app 代码没变（`git log ui-u9..origin/master` 只动了 `docs/`），所以 S0 加完构建属性后，从那个提交打的 `.qa.perf.u9` 就是 U9。报告里记下提交号，并附 `git diff ui-u9 <提交> -- app benchmark gradle *.gradle.kts`，证明只多了包名属性。
- **核对重打的包只改了包名。** 重打的 v0.1、U9 qa 包，`classes.dex` 和 `baseline.prof` 的大小和 `Lagoon-baseline` 里的原包相差不超过 1 %（v0.1 qa 原包 8,517,812 / 3,006 字节，U9 qa 原包 9,287,580 / 9,101 字节）。差得多说明工具链或源码不一样，先查清再测。
- **卸载白名单是精确的完整包名集合**，就是上表这几个（带 `io.github.mitchell0402.lagoon` 前缀的全名），不用前缀、后缀或不锚定的正则匹配，否则正式版 `io.github.mitchell0402.lagoon` 和 `.qa` 都可能被误匹配。白名单之外的包名一律不卸载，直接报错退出。
- **会话前后核对手机上的包。** 脚本开头和结尾都把 `pm list packages | grep mitchell0402` 写进日志。开头发现 `.qa` 或正式版，照常跑、绝不碰；结尾核对它们还在，不在就报错。
- **脚本不带 `-s` 序列号直接退出**（模拟器和手机可能同时连着）。
- S1 录配置文件仍用 Gradle 和默认的 `.bench`（`-Plagoon.profiling`），只在模拟器上跑，不进门槛脚本的白名单。

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
- v0.1 和 U9 都用 profileinstaller 1.4.1，它对 API ≥ 31 一律写 `V015_S` 格式，Android 17 支持；U8 在模拟器上发广播返回过 `result=1`。
- v0.1 的 qa 包也带 5 个一样的初始化器（WorkManager、emoji2、ProcessLifecycle、ProfileInstaller、OkHttp `PlatformInitializer`），另外还有 ML Kit 的 `MlKitInitProvider`。所以初始化器不是 v0.1 和 U9 的差别所在；但门槛是比值，从 U9 身上省下的任何一毫秒都算数，不管它是哪个阶段带进来的。
- `BaselineProfileGenerator` 已经在用 `rule.collect(..., includeInStartupProfile = true)`，录制会同时产出启动配置文件；只是没有放进 app，构建也没打开 dex 启动布局。

推测（S0 证实或推翻）：剩下的差距主要是 **Compose 第一帧本身**（运行时类加载和验证、外壳 + 关注页的组合 / 测量 / 绘制）加上 **第一帧之前主线程上的杂项**（建 `MainViewModel` 的依赖、打开数据库、字体、第二个 ComposeView）。

## 3. 候选点

每项写了读代码的粗估（真机、第二种方法）和把握。数字在 S0 之后换成实测，记在 `startup-s0.md` 的账本里（第 4 节 S0 第 6 步）。

### A. 构建层（不改行为）

1. **当前代码上重录基线配置文件**（0–5 ms，中）。要回答的是 U9 新加的代码（吐司层 `GlobalToasts.kt`、弹层、主题包装）有没有被覆盖：在当前代码上用 `-Plagoon.profiling` 重录一次，和现在的 `app/src/main/baseline-prof.txt` 做 diff，看新增了哪些规则。录制只做未登录（第 10 节第 3 条）。
2. **启动配置文件 + dex 启动布局**（0–3 ms，低）。单 dex 下 R8 做的是把启动类排到 dex 前面，省读 dex 时的缺页；门槛测量不清页缓存，所以收益小。手机重启后第一次启动受益多一些，不进门槛。做法：
   - 生成器已经产出启动配置文件，先查 AGP 9.5 的文档：**不用 `androidx.baselineprofile` 插件**时，启动配置文件放在哪里、用哪个开关打开 dex 启动布局（AGP 8.x 时是 `android.experimental.art-profile-r8-rewriting` 和 `android.experimental.r8.dex-startup-optimization`，名字要按 9.5 的文档核实，不凭记忆）。
   - 能不靠插件就用这条路；插件路线只作后备（它会生成 `benchmarkQa`、`nonMinifiedQa` 等构建类型，和 `:benchmark` 模块自己的 `benchmark` 构建类型、`.bench` 包名、所有脚本冲突），要走插件路线先回来改计划。
   - 检查：`apkanalyzer` 看启动类是否排在 dex 前面、`baseline.prof` 的规则数、qa 包大小。
3. **dex 压缩存放**（`build.gradle.kts` 第 126 行 `useLegacyPackaging = true`，为了 APK 小 4.7 MB）。`speed-profile` 之后理论上不影响启动；S0 用 `.qa.perf.nocompress` 对 `.qa.perf.u9` 对照确认，没差别就保持现状。

### B. 进程启动（Application 和初始化器）

1. **WorkManager 按需初始化**（1–3 ms，中）。移除清单里的 `WorkManagerInitializer`（`tools:node="remove"`），`XtraApp` 实现 `Configuration.Provider`，第一次 `WorkManager.getInstance` 时才建。`MainActivity` 里开着直播通知时的周期任务排期（第 474 行）要挪到第一帧之后的后台，否则会在主线程触发初始化。另外两处用到它的地方（`ChannelPage.kt` 第 158 行，`SettingsViewModel.kt` 第 507、520 行）按需初始化没问题，但进回归清单。
2. **数据库和 `MainViewModel` 的仓库提前在后台建**（0.5–3 ms，低）。`MainActivity` 第一次用 `viewModel`（第 122 行 `by viewModels`，最晚在第 397 行 `restorePlayerFragment()`）就在主线程走 `MainViewModelFactory`：建 graphQL、helix、player、offlineVideos、localChannelFollows、auth 几个仓库；`PlayerRepository` 要 DAO，于是主线程上 `Room.databaseBuilder(...).build()`，带 32 个迁移 lambda 和 `_Impl` 的反射。做法：`XtraApp.onCreate` 在后台线程先触发这几个 lazy（`by lazy` 默认同步，主线程最多等它做完，不会重复建）。**先看真机 trace 里这段占多少再做。**
3. **字体预热**（0.5–2 ms，中）。4 个 Plus Jakarta Sans 是 `Font(resId)` 阻塞加载。预热要走 **Compose 自己的解析器**：后台线程 `createFontFamilyResolver(context).resolve(PlusJakartaSans, weight)`，只解析第一帧用到的字重。API 26 以上 Compose 的资源字体结果缓存在它自己的全局 `TypefaceRequestCache`，`ResourcesCompat.getFont` 走的是 `TypefaceCompat` 的另一套缓存，用它预热可能只是把文件读进页缓存。验证：trace 里第一次 `doFrame` 没有字体加载切片。中文字形照旧走系统字体。
4. **SharedPreferences 提前加载**（< 0.5 ms，低）。`XtraApp.onCreate` 先调一次 `getSharedPreferences`（不读值），让 XML 加载和别的事并行。trace 里看得到等待才做。
5. **不做**：OkHttp 的 `PlatformInitializer`（只是把 context 交给 OkHttp 的 Android 平台层，几乎不花时间，去掉可能影响 OkHttp 的行为）；`EmojiCompatInitializer`、`ProcessLifecycleInitializer`、`ProfileInstallerInitializer` 保留。
6. **顺手清理**：`XtraApp` 里 `< Q` 的 Conscrypt 那段是死代码（最低 API 29），删掉，对启动没影响。Conscrypt 依赖本身带 4 个 ABI 的 `libconscrypt_jni.so`（U9 qa 包 15.9 MB 里合计约 10.4 MB，不压缩存放），删依赖和启动无关，按第 10 节第 5 条单独开小 PR。

### C. MainActivity.onCreate（第一帧之前的主线程）

1. **关注页组合里的网络查询**（0.2–0.5 ms，中）。`FollowingDestination.Content()` 第 162 行在 `isNetworkAvailable` 还是 null 时，每次组合都调 `hasValidatedNetwork()`（`getNetworkCapabilities`，binder），第一帧就会调；第 128 行的 effect 里又调一次。`MainActivity` 在 `onCreate`（第 183 行附近）已经查过同一件事。做法：`MainActivity` 把那次结果存成单独的字段给关注页用。**不要**写进 `isNetworkAvailable`：第 201 行的比较会因此认为状态没变，跳过第一次令牌校验和更新检查。
2. **保留在原位**：网络回调注册（第 345 行）、画中画广播接收器。各是一次 binder，零点几毫秒，网络回调还牵着断网提示、第一次令牌校验和更新检查（第 194–231 行）的时序。只有真机 trace 显示超过 1 ms 才重新考虑。
3. **清理项，不算收益**：`GlassPolicy` 最多读三次（构造第 72 行、`refresh()` 第 121 行、注册 `addCrossWindowBlurEnabledListener` 第 113 行时的立即回调），贵的只有第一次 binder，之后都有缓存；`restorePlayerFragment()` 在 `onCreate`（第 397 行）和 `onResume`（第 504 行）可能查两次播放状态（查询在 `Dispatchers.IO`，不影响首帧）。顺手去重，不进账。
4. 完整性检查（第 470 行）只在开了 `ENABLE_INTEGRITY` 且令牌过期时做事，看 trace 再定。`migrateSettings()` 平时只读几个 key，保留。

### D. 第一帧的 Compose 工作

1. **吐司层延后建立**（0.5–2 ms，中）。`activity_main.xml` 里 U9 加的 `toastOverlay` 在 `onCreate`（第 1022 行）就 `setContent`，第二个组合、第二个 `LagoonTheme`。改成**第一帧之后、主线程空闲时**建立（不是第一次要显示时，那样会把组合和类加载放到那一刻，可能正好在播放器上方卡一下）。
   - **启动时的吐司不需要新代码。** `LagoonToasts.show()`（`GlobalToasts.kt` 第 47–53 行）在 Activity 还没到 STARTED 时直接用系统吐司，断网冷启动时第 189 行那条 `lagoonToast(R.string.no_connection)` 发生在 STARTED 之前，所以现在就是系统吐司，和吐司层什么时候建立无关。STARTED 之后的吐司走 `ToastHostState`，它自己有互斥锁排队，吐司层只是读 `current` 来画，晚点建立也不会丢。剩下的小影响：STARTED 之后、吐司层建立之前出现的吐司，计时从 `show()` 开始，显示时间会短几十到几百毫秒，可以接受。
   - 玻璃装饰的初始值：吐司是 `GlassSurface(GlassLevel.Float)`（`Toast.kt` 第 112 行），读 `LocalGlassDecor`；新组合里的 `LagoonTheme` 前两帧装饰是关的，第一次吐司滑入时噪点和高光会在两帧后突然出现。给 `LagoonTheme` 加一个「装饰初始已打开」的参数，吐司层在外壳预热完之后建立并用它。用帧截图确认第一次吐司看不出跳变。
   - 无障碍：第 1030–1035 行按吐司显示与否切换 `importantForAccessibility`，延后建立时保留。
2. **首帧里的第二次组合**（看 trace）。首帧在 `LagoonTheme`、`MainShell`、`FollowingScreen` 里有没有一上来就写状态导致重组（如 `bottomBarHeightPx`、`selectedIndex`），能在首帧前算出来的直接算出来。
3. **类型安全导航**。9 个路由的 NavHost 建图会生成路由模式。只要配置文件覆盖了就够，不改结构；trace 里占得多再定。
4. **玻璃**。首帧已经不模糊、不画装饰（`LocalGlassDecor`、`ShellState.warmedUp`），视觉不动。只在真机 trace 里数第一帧的 `shader_compile`，确认没有额外的离屏层。
5. **Coil 的 ImageLoader 提前在后台建**（TTID 约 0；登录用户第一屏有图的那一帧可能省几十毫秒）。`XtraApp.newImageLoader` 在第一次请求图片时被调用，Compose 的 `AsyncImage` 在组合里取单例，所以在主线程；它会读设置，并按网络库设置取 `httpEngine` / `cronetEngine` / `okHttpClient`，用 HttpEngine 或 Cronet 的用户要在主线程建网络引擎。做法：第一帧之后在后台线程先取一次单例。不进 TTID 的账，进「主线程总工作量」和「前 10 帧」。
6. **关注页分页流**（TTID 约 0）。关注页用 `AnimatedContent` 只组合当前分段（`FollowingScreen.kt` 第 269 行起）；`collectAsLazyPagingItems()` 在组合里建对象，收集在 effect 里，组合提交后才开始；门槛场景未登录，几乎没东西可加载。所以这项不算 TTID 收益。可能改善的是登录用户第一帧之后的 CPU 竞争。**只有「第一帧之后 1 秒」那段（只报告的一项）显示分页流占得明显时才做**；做的话，第 108–133 行的刷新和断网恢复逻辑引用了全部 5 个 `LazyPagingItems`（直播分段本身就两个流：`streams` + `recentlyLive`），延后开始时这些引用必须仍然有效。

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
| P0 | S0，脚本在模拟器空跑通过之后 | 重测基线（第一种、第二种方法）+ 真机 trace + dex 不压缩对照 |
| P1 | S1 结束 | 一次 TTID 会话 + 少量 trace，决定 S2 / S3 的规模 |
| P2 | 最后 | 门槛第一次；之后做第 1.2 节「不许变差」各项 |
| P3 | P2 之后，中间重启手机 | 门槛第二次；之后做登录状态的人工冒烟 |

**登录冒烟的写法**：用 P3 准备流程留下的 `.qa.perf.new`（最终代码），Mitchell 自己在手机上登录（Claude 不输入账号密码、不贴令牌），看关注页、图片出现，和他自己平时用的版本比没有明显变慢。放在最后一次会话的最后，这样之后没有准备流程会把它卸掉、登录丢掉。冒烟之后这个包留不留由 Mitchell 定；以后再跑测量脚本，它在白名单里，会被卸掉（不需要先退出登录，卸载即可）。

### S0 测量和归因（不改产品代码）

1. **写脚本和构建属性**（不需要手机，批准后马上做）。新脚本放进仓库 `tools/perf/coldstart.py`（让审阅能看到），替代 `Lagoon-baseline/u9/coldstart7.py`：
   - 必须带 `-s <序列号>`，没有就退出。
   - 开头、结尾记 `pm list packages | grep mitchell0402`，按第 1.3 节核对；卸载只限精确白名单。
   - 准备：第 1.1 节的「准备流程」五步（广播在 `force-stop` 之前、只认 `result=1`、dexopt 状态 + `dump-profiles` 行数两项检查）。`--method 1` 走旧流程（先编译、不发广播），只作参考，同样记行数。
   - 每次启动：`force-stop` 两个包，等 ≥ 5 秒；唤醒，确认屏幕醒着、没有锁屏，**查不到对应字段就当作不满足**，这次作废并报错，不能当作通过（`dumpsys power` 的 `mWakefulness`、`dumpsys window` 里表示锁屏的字段名在各 Android 版本和厂商系统上不一样）；记 TotalTime、WaitTime、`LaunchState`，和这次想测的模式不符就作废。
   - 温、热启动模式（第 1.2 节）：会话开头读出 `always_finish_activities` 原值写日志，温启动段临时设成 1，`finally` 里还原，结尾再读一次核对。
   - 每组先后顺序轮换；每组开始记电量、电池温度、`dumpsys thermalservice` 的状态，热状态不是 0 的组标出来。
   - bootstrap 区间：把同一轮里的一次 v0.1 和一次新版配成一「对」，共 30 对；有放回地重抽 30 对，算两边中位数之比，重复 10,000 次，取 2.5 % 和 97.5 % 分位。随机种子写进输出。
   - 输出 JSON：所有原始数（含作废的次数和原因）、每包中位数、比值、区间、配置文件行数、设备指纹。
   - 构建属性 `-Plagoon.perfId`、`-Plagoon.dexUncompressed`（第 1.3 节）；v0.1 源码拷贝上改后缀打 `.qa.perf.v01`。打完核对重打包的 dex 和 `baseline.prof` 大小（第 1.3 节）。
2. **模拟器空跑**（模拟器空闲时，不占手机）。把整个流程跑一遍：几个测量包能同时装上、准备流程（广播结果码、dexopt 状态、`dump-profiles` 行数，并记下每个包的行数作真机下限的依据）、`LaunchState` 解析（冷、温、热三种都要实际出现一次）、`always_finish_activities` 还原、白名单（故意放一个不在白名单的包名，确认脚本拒绝）、锁屏字段查不到时作废。错误在这里找出来，不占手机时间。
3. **P0 真机重测基线**（要 Mitchell 同意）。开跑前先手工在 Android 17 的 OnePlus 上核对一次唤醒和锁屏的字段名，写进脚本注释。第二种方法，qa 包，`.qa.perf.v01` 对 `.qa.perf.u9`，5 × 6；再用第一种方法跑一遍作参考；`.bench.perf.u9` 对 `.qa.perf.u9` 跑一组 TTID，记下 bench 包多出来的固定开销（只作说明，第 1.2 节的 trace 指标都是 bench 对 bench）；`.qa.perf.nocompress` 对 `.qa.perf.u9` 一组（第 3 节 A3）。
   - **决策点**：如果 U9 在第二种方法下已经 ≤ 1.10，按第 10 节第 2 条（待批准的默认做法）缩小成「保住并加固」。
4. **P0 真机 trace**。第二种方法下，`.bench.v01` 和 `.bench.perf.u9` 各录 10 次冷启动 perfetto（`am start` + `perfetto`，bench 包是 profileable；sched、binder、`am`/`wm`/`view`/`gfx`/`dalvik` 类别）。用 TraceProcessor 拆成固定几段：`bindApplication`（含各初始化器、`LabGlassInit` 单独列出）、`activityStart` / `onCreate`（其中 `MainViewModel` 建立、`Room` 打开、`setContent` 两次各占多少）、第一次 `Choreographer#doFrame`（组合 / 测量 / 绘制、字体加载切片）、`RenderThread` 的 `DrawFrames`（`shader_compile` 次数）、主线程上的 `OpenDexFilesFromOat`、`VerifyClass`、JIT 编译数；以及第 1.2 节的「主线程总工作量」（固定窗口）、「第一帧之前」「第一帧之后 1 秒」两段、「前 10 帧」。U9 这批 trace 的第一帧结束时刻中位数就是以后各阶段固定窗口的长度依据。
5. **模拟器 trace**（可选，模拟器空闲时）：同样拆段，只看 CPU 切片，作以后每阶段回归 A/B 的基准。
6. **产出** `docs/perf/startup-s0.md`：新的基线数字（两种方法，含配置文件行数）、每段多花多少毫秒的表、第 3 节 F 的账本（估计 → 实测），之后每阶段更新账本。**S0 结果和本计划的估计不符时，先回来改计划（并审阅）再动代码。**

### S1 构建层：重录基线配置文件 + 启动配置文件

- 当前代码上重录（无 R8，`-Plagoon.profiling`），和现有 `baseline-prof.txt` 做 diff，写进报告：新增的规则主要来自哪些文件。
- 按 AGP 9.5 文档放入启动配置文件、打开 dex 启动布局（不用插件的路线，第 3 节 A2）；走不通就在报告里写明，A2 这项放弃或回来改计划走插件。
- 录制场景照旧：冷启动到关注页、浏览页开一个游戏再返回、我的、回到关注、账号弹层和设置首页；未登录（第 10 节第 3 条）。
- 检查：`baseline.prof` 规则数、启动类在 dex 里的位置、qa 包大小。
- 验证：模拟器 CPU 切片（`VerifyClass`、JIT 数、第一帧前主线程时间）；**P1** 一次真机 TTID 会话，按结果决定 S2 / S3 做多少（已经 ≤ 1.06 的话，S2 / S3 只做把握「中」以上、风险低的项）。

### S2 进程和 Activity

- B1 WorkManager 按需初始化，周期任务排期挪到第一帧之后的后台。
- B3 字体用 Compose 解析器在后台预热。
- C1 关注页复用 MainActivity 的第一次网络查询结果。
- 视 P0 trace：B2 数据库和仓库提前在后台建、B4 SharedPreferences 提前加载。
- 顺手：删 Conscrypt 死代码，`GlassPolicy`、`restorePlayerFragment()` 去重。
- 回归：
  - 断网冷启动：断网吐司出现（u2-report 第 98 行那条，现在是系统吐司）；恢复联网后列表重载。
  - 第一次令牌校验和更新检查仍然只在第一次拿到网络状态时各做一次。
  - 深链冷启动、播放状态恢复、画中画按钮。
  - 直播通知开着时冷启动，周期任务仍被排上（`dumpsys jobscheduler`）；开通知 → 强行停止 → 重新打开 → 任务还在（`ForceStopRunnable`）。
  - 从设置页开、关直播通知；从频道页开通知。
  - 温启动（`LaunchState` 为 `WARM`）、热启动（挪动的工作可能落到 Activity 重建或 resume 路径）。

### S3 第一帧的 Compose 工作

- D1 吐司层第一帧之后空闲时建立，带「装饰初始已打开」；启动时的吐司不用改（第 3 节 D1）。
- D2 首帧内的状态写入导致的重组（视 trace）。
- D5 Coil 单例第一帧之后在后台先建。
- D6 关注页分页流延后：只在「第一帧之后 1 秒」那段显示它占得明显时做。
- 用 Compose 组合 trace（`runtime-tracing`，只加在 profiling 构建里）确认每项的首帧耗时下降。
- 回归：截图基线零变化；吐司在播放器上方的位置（U9 B1）；第一次吐司的帧截图没有装饰跳变；断网冷启动的吐司照常出现；吐司显示时的无障碍；关注页 5 个分段切换和刷新、断网恢复后全部重载；第 1.2 节的「主线程总工作量」和「前 10 帧」。

### S4（可选）S1–S3 后真机仍 > 1.10 才做

候选（要先在 trace 里看到对应的大块开销）：首帧之前在后台线程预先加载 Compose 关键类（`Class.forName` 预热）、减少首帧的离屏图层、`NavHost` 首帧只建起始路由。每项单独评估，任何会改视觉或交互的都先问 Mitchell。S4 之后仍 > 1.10：按第 10 节第 4 条（待批准的默认做法）处理。

## 5. 测量规矩

- **门槛只用同会话比值**，方法见第 1.1 节，脚本见 S0 第 1 步。
- **真机**：每次会话开始前确认屏幕常亮、未锁屏、充电；跑完把旋转、「不保留活动」等设置还原（手机原来是自动旋转），结尾核对还原结果和手机上原有的包都在。真机测试一次只跑一个，不和其他线程的真机测试并行。按 Mitchell 2026-11-17 的话，**真机测试要他点头才开始**。两个 adb 设备时一律带 `-s`，脚本不带 `-s` 不跑。
- **模拟器**：每阶段同一次会话 A/B，第二种方法；模拟器运行几小时后会退化，测量前从快照 `lagoon-v01-fulldata`（`-gpu swiftshader_indirect`）重启。绝对数不可信，只看 CPU 切片的相对差。不和别的线程抢模拟器。
- **每次报告写**：TTID 中位数、比值和 95 % 区间、准备流程结果（广播结果码、dexopt 状态、配置文件行数）、作废次数和原因、热状态；对应阶段的 trace 切片数；第 1.2 节各项。

## 6. 风险

| 风险 | 对策 |
| --- | --- |
| APK 的配置文件其实没装上，测的是「首启 JIT」状态 | 广播在 `force-stop` 之前、只认 `result=1`、`dump-profiles` 数行数，三条都过才算（第 1.1 节） |
| 「温启动」实际测成热启动 | 每次检查 `LaunchState`；温启动用「不保留活动」，做不到就写明没覆盖（第 1.2 节） |
| 挪到第一帧之后的工作改变时序（播放恢复、深链、断网提示、令牌校验） | 网络回调、画中画接收器不挪；挪动的每项都列回归项 |
| 挪到第一帧之后的工作让第 2、3 帧变长（玻璃预热本来就在这两帧打开） | 第 1.2 节的「前 10 帧」限值和「主线程总工作量」限值；挪过去的工作尽量放后台线程而不是下一帧的主线程 |
| 挪到第一帧之后的工作拖慢紧接着测的 v0.1，比值被美化 | 每次启动前停两个包、等 ≥ 5 秒 |
| WorkManager 按需初始化后，进程被系统拉起时取不到配置；强行停止后的重新排期 | `Configuration.Provider` 是官方方式；单元测试 + 第 S2 节的 `ForceStopRunnable` 回归 |
| 吐司层延后：第一次吐司装饰跳变或卡顿 | 装饰初始值、空闲时建立、帧截图确认；启动时的吐司本来就是系统吐司，之后的有 `ToastHostState` 排队，不会丢 |
| 测量脚本卸载了 Mitchell 自己装的包 | 每版本一个测量包名、精确白名单、会话前后核对包列表、必须带 `-s` |
| 「不保留活动」忘了还原，影响 Mitchell 日常使用 | 开头记原值、`finally` 还原、结尾核对，报告里写还原结果 |
| 重打的对照包和原包不是同一份东西 | 核对 dex 和 `baseline.prof` 大小（相差 ≤ 1 %），U9 附 diff |
| 启动配置文件不靠插件在 AGP 9.5 上用不上 | 先查文档；走不通就放弃 A2 或回来改计划，不在 S1 里硬上插件 |
| 真机噪声大于单项收益（单次 ±5 ms） | 单项用 trace 切片验证；门槛用 30 次 × 两次会话 + 区间 |
| 手机发热降频 | 记热状态，非 0 的组标出来，必要时整组重测 |

## 7. 交付

- 每阶段：PR + `docs/perf/startup-sN.md`（测量表、trace 切片数字、账本更新、回归清单结果）。
- S0 额外交付 `tools/perf/coldstart.py`、trace 拆分脚本和模拟器空跑的日志。
- 最后：`docs/perf/startup-report.md`，P2 / P3 的最终数字，对照第 1 节逐项写「过 / 不过」。
- 不发新版本；是否出 v0.2.1 由 Mitchell 决定。

## 8. 已核对、保留的部分

- 合并清单里的 5 个初始化器；`XtraApp.onCreate` 很轻、`XtraModule` 全是 lazy；Coil 单例第一次用时才建。
- 播放状态查询在 `Dispatchers.IO`（`PlayerRepository.getPlaybackStates`）。
- `migrateSettings()` 只有设置版本变化时才做重活。
- 吐司层在 `onCreate` 里 `setContent`（第 1022 行），里面是第二个 `LagoonTheme`。
- `MainActivity` 不自己处理根上的返回（只在画中画的音频按钮里 `moveTaskToBack`，第 359 行），Compose 的 NavHost 只剩起始页时也不拦，所以 Android 12 起按返回键是热启动路径。
- `:benchmark` 的目标包可以用 `targetPackage` 运行参数换（`Target.kt`），第 1.2 节的基准测试能指向测量包名。
- 第 3 节 E「不做的事」和第 6 节风险表的方向。

附带发现（不属于本计划）：断网冷启动时用户看到的是系统样式的吐司，不是 Lagoon 的玻璃吐司（U9 留下的不一致，原因见第 3 节 D1）。要不要改另外再说，不在本计划里做。

## 9. 修改记录

### 9.1 修改版 2 对审阅 2 的回应

| 审阅意见 | 处理 |
| --- | --- |
| **N1** 配置文件装上了没有，要直接验证 | 接受，三条都做。第 1.1 节新增「准备流程」：广播放在 `force-stop` 之前并加 `--include-stopped-packages`；结果码只认 `1`，其他值整组作废、写原因；编译后 `dump-profiles --dump-classes-and-methods` 数行数，下限取模拟器空跑数的 80 %。第一种方法也记行数。第 6 节加对应风险。 |
| **N2** 「温启动」测的是热启动 | 接受。每次 `am start -W` 都解析 `LaunchState`，冷、温、热各自必须对上（第 1.1、1.2 节）；温启动改用临时打开「不保留活动」+ Home，进还原清单；做不到就写明只测了热启动。第 8 节记下根 Activity 返回键的事实。 |
| **N3** 首帧后 1 秒忙碌时间和计划做法冲突 | 接受。改成「进程启动到第一帧后 1 秒」主线程 Running **总量** ≤ U9 的 103 %，窗口长度两边固定（U9 第一帧中位数 + 1 秒），避免第一帧变早少算工作；「第一帧之前」「第一帧之后 1 秒」两段只报告（第 1.2 节）。D5、D6、S3 的引用跟着改。 |
| **N4** 测量包名 | 接受。每个版本一个 `.qa.perf.<名字>` / `.bench.perf.<名字>`（表在第 1.3 节）；U9 从 S0 的提交打并附 diff；核对重打包 dex 和 `baseline.prof` 大小；精确完整包名白名单；会话前后记包列表，`.qa` 和正式版绝不碰；必须带 `-s`。v0.1 的 qa 也改成 `.qa.perf.v01`，和别的统一。 |
| **N4** `.bench` 上的登录会被卸掉 | 接受。门槛脚本的白名单不再含 `.bench`（录配置文件只在模拟器上用 Gradle 和 `.bench`）；登录录制按第 10 节第 3 条不做。 |
| **N5** 启动吐司本来就不走吐司层 | 接受。D1 删掉「启动吐司排队」，写明那条是系统吐司、之后的吐司有 `ToastHostState` 排队，不需要新代码；S3 和第 6 节跟着改。断网时是系统样式吐司这件事记在第 8 节「附带发现」，不进本计划。 |
| **N6** 唤醒、锁屏字段查不到要作废 | 接受。S0 第 1 步：查不到字段当作不满足、作废并报错；P0 开跑前在手机上手工核对字段名写进脚本注释（S0 第 3 步）。 |
| **N7** bootstrap 算法 | 接受，照审阅写进 S0 第 1 步（按「对」有放回重抽 30 对、中位数之比、10,000 次、2.5 / 97.5 % 分位），随机种子写进输出。 |
| **N8** 前 10 帧和谁比 | 接受。只和 U9 bench 比（第 1.2 节写明）。 |
| **N8** 脚本先在模拟器空跑 | 接受。新增 S0 第 2 步，列了要空跑到的每一项；P0 改在空跑通过之后（第 4 节表）。 |
| **N8** 登录冒烟的细节 | 接受。挪到 P3 最后：用 `.qa.perf.new`，Mitchell 亲手登录，之后没有准备流程会卸掉它（第 4 节）。 |
| 第 3 节 五个决定的建议 | 写进第 10 节，作为默认做法，标「待批准」。 |
| 第 1 节 profileinstaller 1.4.1、Android 17 支持 | 写进第 2 节已核对的事实。 |
| 第 3 节 Conscrypt 约 10.4 MB | 第 3 节 B6 的数字从 9.4 MB 改成约 10.4 MB（15.9 MB 的 U9 qa 包里）。 |

### 9.2 修改版 1 对审阅 1 的回应

| 审阅意见 | 处理 |
| --- | --- |
| **B1** 脚本先编译后首启、不发广播，117 % 是无配置文件的数字 | 接受。第 1.1 节写明第二种方法和 dexopt 状态检查；S0 第 1 步重写脚本（进仓库），第 2 步真机重测基线，并加「已过线就缩小规模」的决策点；第一种方法只作参考。第 2 节把 117 % 标成无配置文件的数字，不再用「要省 9–10 ms」。（修改版 2 又按 N1 加强了验证。） |
| **B2** 配对启动时另一个 app 还活着 | 接受。每次启动前停两个包、等 ≥ 5 秒，另外每组轮换先后顺序（第 1.1 节、S0 第 1 步、第 6 节）。 |
| **B3** 门槛：同会话比值 | 接受。门槛写成比值，绝对数只作说明（第 1.1 节）。 |
| **B3** 两次会话 + 区间 | 接受。两次独立会话都 ≤ 1.10，每次报告 bootstrap 95 % 区间，样本默认 5 × 6（第 1.1 节）。 |
| **B3** 最终用 qa 包 | 接受，并补了审阅没提到的问题：v0.1 和现在的 qa 包包名相同，Mitchell 手机上也装过 `.qa`，所以改用专门的测量包名（第 1.3 节，修改版 2 按 N4 细化）。bench 包只录 trace，并在 P0 量一次 bench 和 qa 的差。 |
| **B3** 「不许变差」要有数 | 接受。第 1.2 节逐项写了限值，包括温启动、热启动（修改版 2 按 N2、N3 改了测法和指标）。 |
| **B3** 「列表出现」测不出来 | 接受。换成主线程工作量 + 登录状态人工冒烟，删掉 `reportFullyDrawn()`（第 1.2 节）。 |
| **B4** 归因要有真机 trace；单项用 trace 验证 | 接受。S0 真机各 10 次 trace；模拟器只作回归；第 4 节开头写明单项用 trace 切片验证、TTID 只在阶段结束看。真机会话列成 P0–P3。 |
| **F1** 只有一个 dex | 接受。A2 收益改成 0–3 ms、把握低，不再是「收益最大」的一项（第 3 节 A2）。 |
| **F2** 基线配置文件是好的，要查的是覆盖率 | 接受。A1 改成「当前代码上重录并 diff」（第 3 节 A1）。另外核对到生成器已经用 `includeInStartupProfile = true`，启动配置文件不用改录制。 |
| **F3** 分页流不在第一帧之前 | 接受。D6 改成 TTID ≈ 0，只在「第一帧之后」的指标显示有必要时做，并写明 5 个 `LazyPagingItems` 引用和直播分段两个流的约束。 |
| **F4** 字体缓存不共用 | 接受。B3 改用 Compose 的 `createFontFamilyResolver(...).resolve`，只解析首帧字重，trace 确认。 |
| **F5** GlassPolicy 去重几乎不花时间 | 接受。改成「顺手清理」，不进账（第 3 节 C3）。 |
| **F6** 网络回调、画中画接收器收益小风险大 | 接受。从 S2 删掉，保留在原位，除非真机 trace 显示超过 1 ms（第 3 节 C2）。 |
| **F7** 13 个 `repeatOnLifecycle` | 接受，删掉了这个计数（它本来就不是候选项）。 |
| **F7** WorkManager 的其他用处 | 接受。频道页、设置页加进 S2 回归。 |
| **F7** OkHttp `PlatformInitializer` | 接受。从计划删掉（第 3 节 B5）。 |
| **F7** Conscrypt 依赖可减小 APK | 死代码照删；删依赖和启动无关，列为第 10 节第 5 条。 |
| **F7** `restorePlayerFragment()` 调两次 | 接受，顺手去重，不进账（第 3 节 C3）。 |
| **M1** 主线程建 MainViewModel 依赖和数据库 | 接受。新增 B2，先看真机 trace 再做。 |
| **M2** Coil ImageLoader 在主线程建网络引擎 | 接受。新增 D5，第一帧之后后台先建，算「第一帧之后」的指标。 |
| **M3** 关注页组合里的 binder 调用 | 接受。新增 C1，单独字段，不写进 `isNetworkAvailable`。 |
| **M4** SharedPreferences 提前加载 | 接受。新增 B4，trace 里看得到才做。 |
| **M5** 第一帧之后几帧的成本 | 接受。第 1.2 节加「前 10 帧」限值，第 6 节加对应风险。 |
| **R1** 吐司层按需 `setContent` 的风险 | 接受。D1 改成第一帧之后空闲时建立、装饰初始值、无障碍，回归加帧截图（修改版 2 按 N5 删了「排队」）。 |
| **R2** baselineprofile 插件和构建类型冲突 | 接受。A2 先走不用插件的路线，插件只作后备，要用先改计划。 |
| **R3** 登录录制需要 Mitchell | 接受。默认只录未登录（第 3 节 A1），见第 10 节第 3 条。 |
| **R4** WorkManager 按需初始化后 `ForceStopRunnable` | 接受。加进 S2 回归。 |
| **第 5 节** 收益的现实估计 | 接受。第 3 节 F 列账本，S0 后换实测；S4 写明仍不过线时的处理。 |
| **第 6 节** 结构建议 | 接受。S0 顺序按建议重排。 |

## 10. 需要 Mitchell 决定的事（默认做法，待批准）

下面五条按审阅 2 的建议写成了默认做法。**都还没生效**：Mitchell 一次回复批准（或逐条改）之后，计划才算定稿，才开始 S0 第 1 步。

1. **P0 什么时候开始。** 默认（待批准）：先做完 S0 第 1、2 步（脚本、构建属性、几个测量包、模拟器空跑），这几步不用手机，批准后马上做；空跑通过后再在线程里请 Mitchell 定一次约 1 小时的手机时间（解锁、充电、常亮）。脚本落地之前不占手机。
2. **P0 后 U9 如果已经 ≤ 1.10。** 默认（待批准）：缩小成「保住并加固」：做 S1（便宜、不改行为），加 S2 里把握「中」以上、风险低的三项（B1 WorkManager、B3 字体、C1 网络查询），然后走 P2、P3。P0 只是一次会话，过线仍以 P2、P3 两次都过为准。
3. **配置文件录制要不要登录状态。** 默认（待批准）：只录未登录。门槛场景本身是全新安装、未登录；登录状态多出来的工作（列表、图片）都在第一帧之后，不进 TTID；登录录制要 Mitchell 每次在被反复卸载的 bench 包上亲手重登。P3 的登录冒烟里看到明显变慢，再回来考虑。
4. **S4 之后仍 > 1.10。** 默认（待批准）：接受现状，不授权改首帧结构。前提是账本说明剩下的差距主要是 Compose 运行时本身，并且第 1.2 节各项都过；到时把账本交给 Mitchell 看。S4 里每项要先在 trace 里看到对应的大块开销才做，会改视觉或交互的一律先问。
5. **Conscrypt 依赖。** 默认（待批准）：不进本计划，另开一个小 PR 删掉，可以和本计划并行。U9 qa 包 15.9 MB 里它的 `.so` 约占 10.4 MB，删掉后包大约只剩三分之一多一点；代码里只有 `XtraApp` 里 `< Q` 那段用它（不会执行），OkHttp 5 在 Android 10 以上用系统 TLS。验证：API 29 模拟器和手机上 OkHttp、Cronet、HttpEngine 三种网络库各打开关注页和一个直播，看请求正常、走 HTTP/2。
