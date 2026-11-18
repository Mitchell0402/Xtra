# 冷启动优化计划 · 审阅 1

审阅对象：`docs/perf/startup-plan.md`（草案 v1，PR #30，master `ac47d059`）。
做法：只读。逐条对照 master 上的代码、合并后的清单（benchmark 构建）、`Lagoon-baseline` 里 U9 和 v0.1 的 APK，以及测量脚本 `Lagoon-baseline/u9/coldstart7.py`。没有跑手机或模拟器（另一个线程在用模拟器做动效审计）。下面的「预计收益」和计划一样是读代码估的，要靠 S0 的 trace 证实。

## 结论

**改完再进 S1，不用推倒重来。** 方向是对的：先用 trace 归因（S0），不做「假骨架首帧」这类只改数字的办法，玻璃和功能列成不许丢的东西，每阶段一个 PR。但有四个问题会让后面的结论站不住，必须先改：

1. **计划写的测量方法和脚本实际做的不一样**：U9 真机的 117 % 是在「配置文件没装上」的状态下测的（B1）。
2. **配对启动时另一个 app 还活着**：「挪到第一帧之后」的工作会算到对方头上，比值会被美化（B2）。
3. **门槛定义不够硬**：绝对毫秒数、bench 包和发布包不同、「列表出现」按现在的定义测不出来（B3）。
4. **归因只在模拟器上做**：模拟器的差距（131–133 %）和真机（117 %）成分不同，10 ms 级的决定要用真机 trace（B4）。

另外几条事实需要更正（单 dex、分页流不算首帧、字体缓存不共用等，第 2 节），还有几个漏掉的候选点（第 3 节）。按我的估计，计划第 3 节各项加起来大约省 3–15 ms，只有 S0 证实了大项才够 9–10 ms；B1 修正后差距本身可能就变了，所以 S0 第一步应该是重新定基线。

## 1. 必须先改的（阻塞 S1）

### B1 测量方法和计划第 1 节写的不一致

计划第 1 节说方法是「新装、首启后装上 APK 自带的配置文件、`speed-profile` 编译」。但 `coldstart7.py` 的 `prepare()` 是：

```
uninstall → install → cmd package compile -m speed-profile -f → 第一次 am start -W → sleep 3
```

**先编译、后首启，也没有发 ProfileInstaller 的广播。** 这正是 u8-report 第 3 节表里的第一种做法（「APK 里的配置文件还没装上」）。u8-report 自己写了：第一种两边都没用到配置文件，第二种（首启后装上配置文件再编译）才是用户实际的状态。U8 在模拟器上从第一种换到第二种，比值从 133 % 降到 110 %。`coldstart6-u8b-profiles.log` 里有 `Broadcast completed` 行，说明 U8 用过第二种；`coldstart7.py` 又回到了第一种。

所以 U9 真机的 143 / 122 ms（117 %）大概率是「无配置文件」的数字。后果：

- 计划的起点（要省 9–10 ms）不可靠，真实差距可能更小，也可能不同。
- **S1 的收益（启动配置文件、重录基线配置文件）在第一种方法下根本测不出来**，因为测的 APK 没用上配置文件。

要改的：

1. 脚本改成：安装 → 首启 → `am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE -n <pkg>/androidx.profileinstaller.ProfileInstallReceiver` → 等几秒 → `cmd package compile -m speed-profile -f <pkg>` → 测。v0.1 也走同一流程（v0.1 的 APK 也带 3 KB 的库配置文件，见 F2）。
2. 每次 `prepare` 后把 `dumpsys package <pkg>` 里的 dexopt 状态（`status=speed-profile`，不是 `verify`）写进日志，状态不对的那组作废。
3. S0 第一项：用新脚本在真机上重测 v0.1 / v0.2 / U9，计划第 1 节的现状和「要省多少」按新数字重写。如果 U9 在第二种方法下已经 ≤ 110 %，计划应该先告诉 Mitchell，改成「保住并加固」（做 S1 和 S2 里低风险的），而不是照原样全做。
4. 第一种状态（装完、系统后台优化之前）也值得记录，作为参考，不作门槛；计划里写明门槛用哪一种。

### B2 配对启动时另一个 app 没停

`launch()` 只 `force-stop` 要启动的那个包，两次启动之间只 `sleep 2`。v0.1 启动 2 秒后就去启动新版，这时 v0.1 还活着（列表请求、图片、数据库都可能还在跑），反过来也一样。于是一边的「首帧之后的工作」会和另一边的冷启动抢 CPU。

这对本计划尤其要紧：S2、S3 的主要手法就是把工作挪到第一帧之后，挪得越多，越会拖慢紧接着测的 v0.1，比值看起来就越好。

要改的：每次启动前 `force-stop` **两个**包，间隔加到 5 秒以上（或者等到 `dumpsys cpuinfo` 里两个包都没有活动），日志里记下来。

### B3 门槛定义

- **只用同一次会话里的比值。** v0.1 在不同会话里测出过 122、124–130 ms；在模拟器上换测量方法，v0.1 自己会从约 390 ms 变成约 470 ms（u8-report 第 3 节）。「约 134 ms」「≤ 130 ms」这样的绝对数只能作说明，门槛写成「同会话交替测量，新版中位数 / v0.1 中位数 ≤ 1.10」。
- **一次过线不算过。** 建议：两次独立会话（中间重启手机）都 ≤ 1.10 才算过，同时报告配对比值的 bootstrap 95 % 区间。单次 ±5 ms 的噪声对 9 ms 的目标太大，计划第 6 节「必要时加到 5×6」应改为默认就看区间。
- **最终门槛用 qa 包。** bench 包不等于发出去的包：它带 `src/lab` 的代码和清单，合并清单里多一个进程启动时就实例化的 `LabGlassInit` ContentProvider，还多几个 lab Activity。`am start -W` 不需要 profileable，所以最终数字应该用 qa 包对 v0.1 的 qa 包（`Lagoon-baseline/v0.1/lagoon-v0.1-qa.apk`）；bench 包只用来录 trace。
- **「不许变差」要有数。** 现在第 1 节只写了名字。建议照 G 阶段和 U7 的做法写成：玻璃滚动 jank 不高于 U9 + 0.3 个百分点、静止 0 帧，聊天压力 jank < 5 % 且 P95 超时不变差，温启动和热启动不慢于 U9 3 % 以上（S2、S3 挪动的工作可能落到 resume 路径上）。
- **「列表出现」按现在的定义测不出来。** 三个原因：U9 的 bench 包没有 `reportFullyDrawn()`，没有基线可比；门槛场景是全新安装、未登录，关注页没有列表可出现；就算登录了，列表数据来自 Twitch 网络，几百毫秒的抖动远大于 5 %。建议换成：(a) trace 里「第一帧之后 1 秒内主线程忙碌时间」，这是 CPU 指标，确定性高；(b) 在 Mitchell 手机上登录状态下做一次人工冒烟（关注页、图片出现，没有明显变慢）。真要用 `reportFullyDrawn`，就得先在 U9 代码上加同样的点重新打一个基线包。

### B4 S0 的归因要有真机 trace

S0 计划只在模拟器（swiftshader）上录 trace。模拟器上的差距是 131–133 %，真机是 117 %，两者成分不同：软件渲染把 RenderThread 放大很多，x86_64 和 arm64 的编译和类加载也不一样。真机要省的是 10 ms 左右，按模拟器的切片排优先级，很可能排错。

建议 S0 加一次真机会话（要 Mitchell 点头、解锁、充电）：B1 的新方法下，v0.1 和 U9 的 bench 包各录 10 次 perfetto（`am start` + `perfetto`，bench 包是 profileable），拆分方法照计划第 4 节 S0 第 2 步。模拟器只用来做每阶段的回归 A/B。

单项改动的验证也要靠 trace 切片（第一帧之前主线程的时间、对应切片的时长），不要靠 TTID A/B。S2 里很多项只有零点几毫秒，同一次会话的 TTID 分辨不出来。TTID 只在每阶段结束时看一次。

## 2. 事实更正

### F1 只有一个 dex，dex 布局优化的收益被高估

计划 A1 说「启动要用的类散落在多个 dex 里」。实际 U9 的 bench 包和 qa 包都只有一个 `classes.dex`（9.41 MB 和 9.29 MB），v0.1 也是一个（8.52 MB）。R8 的启动布局在单 dex 里做的是把启动类排到前面，省的是读 dex 时的缺页。现在的测量不清页缓存，两次启动之间 APK 和 vdex 的页都还在内存里，所以这项在门槛测量里大概只有 0–3 %。还是可以做（便宜、不改行为），但不能当「收益最大」那项来算账。手机重启后第一次启动会受益多一些，这是额外的好处，不进门槛。

### F2 基线配置文件是好的，A2 要查的是覆盖率

我解析了 APK 里的 `assets/dexopt/baseline.prof`（P 版格式）：U9 bench 包里有 4,077 个类，48,547 个方法里约 12.9k 个标了启动、约 8.9k 个热方法；qa 包是 4,065 个类，几乎一样。说明「在无 R8 构建上录、R8 改写」这条路是通的，规则没有丢。A2 要回答的只是 U9 新加的代码（吐司层 `GlobalToasts.kt`、弹层、主题包装）有没有被覆盖，做法是在当前代码上重录一次，和现在的 `baseline-prof.txt` 做 diff。v0.1 的 APK 也带一个 3 KB 的配置文件（库自带的规则），B1 的新方法下它也会被装上，这是对的。

### F3 关注页的 5 个分页流不在第一帧之前

- 关注页用 `AnimatedContent` 只组合当前分段（`FollowingScreen.kt` 第 269 行起），其他分段第一帧根本不组合。
- `collectAsLazyPagingItems()` 在组合里建对象，真正的收集在 effect 里，组合提交之后才开始；数据源是网络（`FollowedStreamsDataSource` 等）。
- 门槛场景是全新安装、未登录，几乎没有可加载的东西。

所以 D 里「第一帧前后就有 5 组请求在抢主线程」的前半句不成立，这项**不算 TTID 的收益**，不应该算进 9–10 ms 的账。它可能改善的是登录用户「列表出现」的时间和第一帧之后的 CPU 竞争，可以留在 S3，但目标要改写。另外两个细节：直播分段本身就要两个流（`streams` + `recentlyLive`）；`FollowingDestination` 第 108–133 行附近的刷新和断网恢复逻辑引用了全部 5 个 `LazyPagingItems`，延后开始时要保证这些引用仍然有效。

### F4 字体预热的缓存不一定共用

计划说「`ResourcesCompat.getFont` 有进程内缓存，Compose 的 `AndroidFontLoader` 走同一个缓存」。我认为这条很可能不对：API 26 以上，Compose 的资源字体通过框架的 `Resources.getFont` 加载，结果缓存在 Compose 自己的全局 `TypefaceRequestCache` 里；`ResourcesCompat.getFont` 走 `TypefaceCompat` 自己的 LruCache。用 `ResourcesCompat` 预热，可能只是把 TTF 读进了页缓存。建议改成在后台线程用 Compose 自己的解析器解析一次（`createFontFamilyResolver(context).resolve(PlusJakartaSans, weight)`，只解析第一帧用到的字重），并在 trace 里确认第一次 `doFrame` 里没有字体加载切片。预计 0.5–2 ms。

### F5 GlassPolicy 的重复读取几乎不花时间

实际不是两次而是最多三次：构造时一次（第 72 行），观察者 `onCreate` 里 `refresh()` 一次（第 121 行），`addCrossWindowBlurEnabledListener`（第 113 行）按 API 文档注册时还会立刻回调一次。但贵的是第一次 binder；之后 `isPowerSaveMode` 有 IpcDataCache，`isLowRamDevice` 是系统属性，模糊状态在注册监听后由本地缓存。去重是好的清理，收益接近 0，不要算进账。

### F6 网络回调和画中画接收器：收益小、风险不小

`registerNetworkCallback`（MainActivity 第 345 行）和 `registerReceiver` 各是一次 binder，零点几毫秒。网络回调还牵着 `initialized` 的提示逻辑和第一次的令牌校验、更新检查（第 194–231 行）。挪到第一帧之后会改变这些的时序，换来的不到 1 ms。建议从 S2 去掉，除非真机 trace 显示它超过 1 ms。

### F7 其他小处

- `onCreate` 里是 13 个 `repeatOnLifecycle`，不是 14 个。
- WorkManager 除了 MainActivity，还在 `ChannelPage.kt` 第 158 行和 `SettingsViewModel.kt` 第 507、520 行用到。按需初始化对它们没问题，但回归清单要加「从设置页打开或关闭直播通知」「从频道页开通知」。
- OkHttp 的 `PlatformInitializer` 只是把应用 context 交给 OkHttp 的 Android 平台层，几乎不花时间；去掉反而可能影响 OkHttp 在 Android 上的行为。建议直接从计划删掉，除非 trace 里看到它。
- Conscrypt：删掉 `XtraApp` 里 `< Q` 的那段对启动没有影响（那段从不执行）。但依赖本身带了 4 个 ABI 的 `libconscrypt_jni.so`（qa 包里每个 1.8–3.0 MB，合计约 9.4 MB，未压缩），而代码里只有这一处用它。删依赖能明显减小 APK（不影响启动），可以作为顺手的附带项，前提是确认 OkHttp 不依赖它（OkHttp 只在 Conscrypt 被装成第一个安全提供者时才用它）。
- MainActivity 的 `restorePlayerFragment()` 在 `onCreate`（第 397 行）和 `onResume`（第 504 行）各调一次，播放状态查询只在第一次还没返回时被挡住，所以可能查两次。查询在 `Dispatchers.IO` 上（已核对），不影响首帧，只是多余。

## 3. 漏掉的候选点

### M1 主线程上建 MainViewModel 的依赖（v0.1 继承的，但一样算数）

门槛是比值，第一帧前主线程省下的任何一毫秒都算，不管是哪个阶段引入的。`MainActivity` 第一次用 `viewModel`（第 122 行的 `by viewModels`，最晚在 `onCreate` 第 397 行 `restorePlayerFragment()` 里）就在主线程走 `MainViewModelFactory`：建 graphQL、helix、player、offlineVideos、localChannelFollows、auth 几个仓库；`PlayerRepository` 要 DAO，于是 `xtraModule.database` 在主线程 `Room.databaseBuilder(...).build()`，带 32 个迁移的 lambda（各是一个类）和 `_Impl` 的反射。可以在 `XtraApp.onCreate` 用后台线程先触发这几个 lazy（`by lazy` 默认同步，主线程最多等它完成）。先在 trace 里看 `onCreate` 里这段占多少再决定。

### M2 Coil 的 ImageLoader 在主线程创建，可能顺带建网络引擎

`XtraApp.newImageLoader` 在第一次请求图片时被调用，Compose 的 `AsyncImage` 在组合里取单例，所以是在主线程。它会读设置，并按网络库设置取 `xtraModule.httpEngine.value` / `cronetEngine.value` / `okHttpClient.value`：用 HttpEngine 或 Cronet 的用户（模拟器快照就设成了 HttpEngine），第一帧有图的那一帧要在主线程建网络引擎，这可能是几十毫秒。全新安装、未登录的门槛场景里第一帧没有图，所以不进 TTID；但它直接影响登录用户「列表和图片出现」的时间和那一帧的卡顿。建议第一帧之后在后台线程先取一次单例。

### M3 关注页组合里的 binder 调用

`FollowingDestination.Content()` 第 162 行的 `offline = ... (online == null && !hasValidatedNetwork())` 在组合里调 `getNetworkCapabilities`，而且只要 `isNetworkAvailable` 还是 null，每次重组都会调；第 128 行的 effect 里又调一次。MainActivity 在 `onCreate` 第 183 行已经查过同一件事。建议在 MainActivity 里把那次结果存下来给关注页用。**不要**直接把它写进 `isNetworkAvailable`：第 201 行的比较会因此认为没有变化，跳过第一次令牌校验和更新检查。

### M4 SharedPreferences 提前加载

`onCreate` 第一次 `prefs()` / `tokenPrefs()` 的读取会等 XML 文件加载完。在 `XtraApp.onCreate` 里先调一次 `getSharedPreferences`（不读值）就能让加载和别的事并行。收益小，trace 里看得到再做。

### M5 第一帧之后几帧的成本

S2、S3 把工作挪到第一帧之后，而玻璃的预热（`LocalGlassDecor`、`ShellState.warmedUp`）本来就在第 2、3 帧打开。挪过去的工作可能让第 2、3 帧变成长帧，用户看到的是首屏刚出来就顿一下。建议每阶段除了 TTID，再记前 10 帧的帧时长（`dumpsys gfxinfo` 或 trace）。

## 4. 风险补充

### R1 吐司层按需 `setContent`

- **启动时就可能有吐司。** 断网冷启动时 `onCreate` 第 189 行就会 `lagoonToast(R.string.no_connection)`，这时吐司层必须能显示。
- **玻璃装饰会闪一下。** 吐司是 `GlassSurface(GlassLevel.Float)`（`Toast.kt` 第 112 行），读 `LocalGlassDecor`。新建的组合里 `LagoonTheme` 前两帧装饰是关的，第 3 帧才开，所以第一次吐司滑入时，噪点和高光会在前两帧之后突然出现。这是玻璃效果的可见变化，截图基线抓不到。要么吐司层建好时直接用已打开的装饰（给 `LagoonTheme` 加一个初始值参数，外壳预热完再建吐司层），要么用帧截图确认看不出来。
- **第一次吐司的卡顿。** 「第一次要显示时才 `setContent`」会把组合和类加载放到那一刻，可能正好在播放器上方。建议改成「第一帧之后，在主线程空闲时 `setContent`」，第一次吐司照常。
- 无障碍：第 1030–1035 行按吐司显示与否切换 `importantForAccessibility`，延后建立时要保留。

### R2 baselineprofile 插件和现有构建类型冲突

插件会给每个开了混淆的构建类型生成 `benchmark<类型>` 和 `nonMinified<类型>`，也就是会有 `benchmarkQa`、`nonMinifiedQa`、`benchmarkBenchmark`、`nonMinifiedBenchmark`；`:benchmark` 测试模块里已经有自己写的 `benchmark` 构建类型；所有脚本和 v0.1 基线包都依赖 `.bench` / `.bench.v01` 的包名。还要先确认插件版本支持 AGP 9.5。建议 S1 先走风险小的路线：保留手写的生成器，让 `BaselineProfileRule.collect(..., includeInStartupProfile = true)` 同时产出启动配置文件，再查 AGP 9.5 能不能不靠插件就用上它（AGP 8.x 时插件是通过 `android.experimental.art-profile-r8-rewriting` 和 `android.experimental.r8.dex-startup-optimization` 打开的，名字要按 9.5 的文档核实）。插件路线放到后面，作为可选项。

### R3 「登录和未登录各录一次」需要 Mitchell

在 bench 包上登录要 Mitchell 亲手操作（Claude 不能输入凭据），而且退出登录会在服务端吊销令牌（U2 记录）。计划要写明谁来登录、录完不退出。做不到就只录未登录，加上能脚本化的页面切换。

### R4 WorkManager 按需初始化

`ForceStopRunnable`（被强行停止或更新后重新排期）以后只在第一次 `getInstance` 时运行。开着直播通知时 MainActivity 每次启动都会调用（挪到第一帧之后），所以没问题，但回归要测：开通知 → 强行停止 → 重新打开 → `dumpsys jobscheduler` 里任务还在。

## 5. 收益的现实估计

按 B1 第二种方法、真机，读代码的粗估（计划没有给逐项数字，这里补上，S0 后替换成实测）：

| 项 | 估计 | 把握 |
| --- | --- | --- |
| A1 dex 布局（单 dex，页缓存是热的） | 0–3 ms | 低 |
| A2 当前代码重录基线配置文件 | 0–5 ms，看 U9 代码覆盖了多少 | 中 |
| WorkManager 按需初始化 | 1–3 ms | 中 |
| 字体预热（改用 Compose 的解析器） | 0.5–2 ms | 中 |
| 吐司层延后 `setContent` | 0.5–2 ms | 中 |
| M1 数据库和仓库提前在后台建 | 0.5–3 ms | 低 |
| GlassPolicy、网络回调、画中画、完整性检查 | 合计 < 1 ms | 高 |
| 关注页分页流延后 | TTID 约 0 | 高 |

合计大约 3–15 ms。只有 S0 证实了几个大项，才够原定的 9–10 ms；B1 的重新测量可能比这些优化本身更能改变结论。所以计划要写明：S3 之后真机仍 > 110 % 时怎么办（做 S4 的哪几项，或请 Mitchell 决定是否接受）。

## 6. 对计划结构的建议

- **S0 的顺序**：(1) 改脚本（B1、B2），真机按第二种方法重测基线（要 Mitchell 同意）。这里是个决策点：已经过线就改计划规模。(2) 真机 trace（B4）。(3) 模拟器 trace，只看 CPU 切片。(4) 写 `startup-s0.md`，里面放一张逐项的「预计 / 实测」账本，后面每个阶段更新它。
- **逐项用 trace 验证，每阶段才看一次 TTID**（B4 最后一段）。
- **S2 去掉**网络回调和画中画接收器的挪动、OkHttp 初始化器（F6、F7）；**加上** M1、M3、M4（视 trace 结果）。
- **S3 的吐司层**改成「第一帧之后空闲时建立」，并处理装饰初始值（R1）；分页流一项的目标改成「列表出现时间、首帧后 CPU」，不计入 TTID（F3）。
- **门槛章节**按 B3 改写：同会话比值、两次会话、qa 包、数字化的「不许变差」、换掉「列表出现」的定义。

## 7. 核对过、没有问题的地方

- 合并清单里确实是 5 个初始化器：WorkManager、emoji2、ProcessLifecycle、ProfileInstaller、OkHttp `PlatformInitializer`。另有一个 lab 的 `LabGlassInit` provider，只在 bench 和 debug 包里（见 B3）。
- `XtraApp.onCreate` 很轻，`XtraModule` 全是 lazy；Coil 的单例也是第一次用时才建（但见 M2）。
- 播放状态查询在 `Dispatchers.IO`（`PlayerRepository.getPlaybackStates`）。
- `migrateSettings()` 只有设置版本变化时才做重活。
- 吐司层确实在 `onCreate` 里 `setContent`（MainActivity 第 1022 行），里面是第二个 `LagoonTheme`。
- 关注页确实在第一次组合时对 5 个流调用 `collectAsLazyPagingItems()`（第 97–103 行），只是它们不在第一帧之前生效（F3）。
- dex 确实压缩存放（`build.gradle.kts` 第 126 行 `useLegacyPackaging = true`），计划 A3 留到 S0 对照，这样处理是对的。
- 计划第 3 节 E 的「不做的事」和第 6 节的风险表方向都对，保留。
