# 冷启动优化 S0：测量和归因

计划：`docs/perf/startup-plan.md`（修改版 2，Mitchell 2026-11-18 批准）。本文件按 S0 的步骤逐步补充。

| 步骤 | 状态 |
| --- | --- |
| 1 脚本和构建属性 | **完成**（本 PR） |
| 2 模拟器空跑 | **完成**（2026-11-18，见第 3 节） |
| 3 P0 真机重测基线 | 待做，要 Mitchell 定手机时间（第 5 节是执行单） |
| 4 P0 真机 trace | 待做，和第 3 步同一次会话 |
| 5 模拟器 trace（可选） | 待做 |
| 6 账本 | 待 P0 |

## 1. 工具

### 1.1 构建属性（`app/build.gradle.kts`）

- `-Plagoon.perfId=<名字>`：qa 构建类型的包名后缀变成 `.qa.perf.<名字>`，benchmark 变成 `.bench.perf.<名字>`，别的不变；名字只能是小写字母和数字。不设这个属性时一切照旧（`.qa`、`.bench`），CI 不设。
- `-Plagoon.dexUncompressed`：dex 不压缩存放（`useLegacyPackaging = false`），只用来打第 3 节 A3 的对照包。
- v0.1 源码拷贝（`E:\Projects\Lagoon-baseline\src-v0.1`，不在 git 里）的 qa 构建类型加了同样的 `-Plagoon.perfId`，原文件留在 `app/build.gradle.kts.orig`。

`git diff ui-u9 -- app benchmark gradle build.gradle.kts settings.gradle.kts gradle.properties` 只有 `app/build.gradle.kts` 这一处（11 行加、3 行改），所以从本 PR 的代码打出来的 `.qa.perf.u9` 就是 U9。

### 1.2 `tools/perf/coldstart.py`

```
python tools/perf/coldstart.py -s SERIAL check
python tools/perf/coldstart.py -s SERIAL cold --a qa.perf.v01=APK --b qa.perf.u9=APK [--blocks 5 --runs 6]
       [--method 2|1] [--min-profile KEY=N ...] [--no-prep] [--out DIR]
python tools/perf/coldstart.py -s SERIAL warm|hot --a KEY[=APK] --b KEY[=APK] [--no-prep]
```

- 必须带 `-s`。只碰 `WHITELIST` 里的 7 个完整包名（`qa.perf.v01`、`qa.perf.u9`、`qa.perf.new`、`qa.perf.nocompress`、`bench.perf.u9`、`bench.perf.new`、`bench.v01`），别的直接退出。开头、结尾记 `pm list packages mitchell0402`，结尾核对开头就有的非测量包都还在。
- 准备（第二种方法）：卸载 → 安装 → 首启 → 进程活着等 5 秒 → `INSTALL_PROFILE` 广播（`--include-stopped-packages`，只认 `result=1`）→ 3 秒 → `force-stop` → `compile -m speed-profile -f` → dexopt 状态必须是 `speed-profile`，`dump-profiles` 的方法数不低于 `--min-profile`。不合格整组不测。
- 每次启动：冷启动先停两个包、等 5 秒、确认两个都没有进程；温、热启动先按 Home 等 3 秒。唤醒后确认醒着、没有锁屏（字段查不到就作废）。温启动用 `am start -W --activity-clear-task`，热启动用普通的 `am start -W`。`LaunchState` 和模式不符就作废。
- 每轮两个包各启动一次，轮与轮之间先后顺序互换。每组开头记电量、电池温度、是否在充电、热状态。
- 输出 `<时间>-<模式>.json`（全部原始数、作废原因、配对、中位数、比值、bootstrap 区间、准备记录、设备指纹）和同名 `.log`。bootstrap：按对有放回重抽，中位数之比，10,000 次，种子 20261118。

### 1.3 `tools/perf/startup_trace.py`

```
python tools/perf/startup_trace.py -s SERIAL capture --key bench.perf.u9 --other bench.v01 -n 10 --out DIR
python tools/perf/startup_trace.py split --key bench.perf.u9 [--window-ms W] [--json OUT] TRACE...
```

- capture：包要先用 `coldstart.py` 准备好（`--blocks 0` 只准备不测）。每次停两个包、等 5 秒、确认没锁屏，后台开 perfetto（sched、binder、`am wm view gfx dalvik binder_driver res ss`，`atrace_apps` 指向这个包），1.5 秒后冷启动，`LaunchState` 不是 `COLD` 的不要。
- split 的各段：进程启动（`process.start_ts`）→ `bindApplication`（列出子切片，如 `Startup`、`LabGlassInit`）→ `activityStart`（子切片）→ **第一帧** = `activityStart` 之后第一个含 `traversal` 的 `Choreographer#doFrame` 结束。另外给出系统的 `launching: <包名>` 切片时长作对照。
- 主线程 Running 时间（sched）：固定窗口 [进程启动, 进程启动 + W]，W 默认是这批 trace 自己的第一帧 + 1000 ms，比较不同版本时用 `--window-ms` 传 U9 的中位数 + 1000（计划第 1.2 节）；另报「第一帧之前」「第一帧之后 1 秒」两段。
- 第一帧之后 10 帧里超过 2 个 vsync（120 Hz）的帧数、第一帧前 RenderThread 的 `shader_compile` 数、JIT 数、`VerifyClass`、`OpenDexFilesFromOat`。

## 2. 测量用的包

都在 `E:\Projects\Lagoon-baseline\s0`（v0.1 bench 用原来的 `v0.1\lagoon-v0.1-bench.apk`，它本来就是 `.bench.v01`）。

| 文件 | 包名 | 来源 | `classes.dex` | `baseline.prof` | 原包 |
| --- | --- | --- | --- | --- | --- |
| `lagoon-qa-perf-v01.apk` | `.qa.perf.v01` | v0.1 源码拷贝 | 8,517,812 | 3,006 | 完全相同（`v0.1\lagoon-v0.1-qa.apk`） |
| `lagoon-qa-perf-u9.apk` | `.qa.perf.u9` | 本 PR 的代码 | 9,287,580 | 9,101 | 完全相同（`u9\lagoon-u9-qa.apk`） |
| `lagoon-qa-perf-nocompress.apk` | `.qa.perf.nocompress` | 本 PR，`-Plagoon.dexUncompressed` | 9,287,580（不压缩存放） | 9,101 | 同上，APK 21.1 MB 对 15.9 MB |
| `lagoon-bench-perf-u9.apk` | `.bench.perf.u9` | 本 PR 的代码 | 9,411,188 | 9,143 | 原包 9,411,132（差 56 字节，0.0006 %），配置文件相同 |
| `v0.1\lagoon-v0.1-bench.apk` | `.bench.v01` | 原包 | — | — | — |

包名用 `aapt2 dump badging` 核对过。几个包在模拟器上能同时装着（provider 的 authority 用的是 `${applicationId}`）。

## 3. 模拟器空跑（2026-11-18，emulator-5554，API 37，从快照 `lagoon-v01-fulldata` 启动）

空跑时另一个 Gradle 构建在跑，所以启动时间（600–2,400 ms）没有意义，只看流程。日志在 `E:\Projects\Lagoon-baseline\s0\dryrun`。

| 检查 | 结果 |
| --- | --- |
| `-s` 必填 | 不带 `-s` 时 argparse 报错退出 |
| 白名单 | `--a qa=...`（`.qa`）被拒绝，退出码 1，什么都没碰；模拟器上原有的正式版、`.qa`、`.debug` 前后都在 |
| 准备：广播 | 5 个包结果码都是 `1` |
| 准备：dexopt + 配置文件 | 都是 `speed-profile`；方法数见下表 |
| 冷启动 | `LaunchState: COLD`，两个包都没进程后才启动 |
| 温启动 | 第一版用 `always_finish_activities=1`：**无效**，`LaunchState` 全是 `HOT`（只写设置不会更新系统里的开关）。改成 `--activity-clear-task` 后都是 `WARM`。脚本不再改任何系统设置，计划第 1.2 节已改 |
| 热启动 | `LaunchState: HOT` |
| 锁屏字段 | 模拟器上用到的是 `dumpsys power` 的 `mWakefulness` 和 `dumpsys window` 的 `isKeyguardShowing`。故意换成不存在的字段名时两种都作废并写原因。模拟器没设锁屏，所以「锁屏显示中」那条路没走到，P0 开头在手机上手工核对 |
| 输出 | JSON 和 log 正常；结尾核对包列表正常 |

配置文件行数（作真机下限的依据，下限 = 80 %）：

| 包 | 类 | 方法 | 真机 `--min-profile` |
| --- | --- | --- | --- |
| `.qa.perf.v01` | 542 | 1,392 | `qa.perf.v01=1113` |
| `.qa.perf.u9`、`.qa.perf.nocompress` | 4,065 | 13,346 | `qa.perf.u9=10676`、`qa.perf.nocompress=10676` |
| `.bench.perf.u9` | 4,077 | 13,380 | `bench.perf.u9=10704` |
| `.bench.v01` | 542 | 1,392 | `bench.v01=1113` |

**新发现：第一种方法（旧脚本）下根本没有编译。** 安装后马上 `compile -m speed-profile -f`，这时还没有任何配置文件，ART 只做了 `verify`，`dump-profiles` 是 0 类 0 方法（`.qa.perf.u9`）。所以 U9 真机 117 %、v0.2 120 % 那批数字是**两边都完全没有 AOT 编译**的状态，比审阅 1 说的「没装配置文件」更远离用户的实际状态。参考组照样记，但脚本对第一种方法不要求 `speed-profile`。

trace 空跑：`.bench.perf.u9`、`.bench.v01` 各录 2 次，split 都正常。模拟器上的数只作格式核对（U9 / v0.1 中位数：第一帧 643 / 678 ms，系统 `launching` 661 / 705 ms，`bindApplication` 73 / 141 ms，第一帧前主线程 Running 94 / 226 ms，第一帧前 `shader_compile` 7 / 4 次）。split 也拿 G 阶段的旧 trace（`results/g.pftrace`）试过，第一帧前 12 次着色器编译，和 g-report 记的一致。

## 4. 还没做的

- 手机上的锁屏、唤醒字段名（P0 开头手工核对，写进 `coldstart.py` 顶部注释）。
- P0 本身（第 5 节）。

## 5. P0 执行单（约 50–60 分钟，要 Mitchell 同意、手机解锁、充电、屏幕常亮）

`S` = `E:\Projects\Lagoon-baseline\s0`，`SER` = 手机的 adb 序列号，`F` = 第 3 节的 `--min-profile` 四个值。

1. `coldstart.py -s SER check`：看包列表（手机上的 `.qa`、正式版只记不碰）、唤醒和锁屏字段；手工 `dumpsys window | grep -i keyguard`、`dumpsys power | grep -i wake` 核对字段名，锁一次屏确认能看到「锁屏显示中」。
2. 门槛基线（第二种方法）：`cold --a qa.perf.v01=S/lagoon-qa-perf-v01.apk --b qa.perf.u9=S/lagoon-qa-perf-u9.apk F`（5 × 6，约 10 分钟）。
3. 参考（第一种方法）：同上加 `--method 1`（约 10 分钟）。
4. bench 对 qa（同为 U9）：`cold --a qa.perf.u9=... --b bench.perf.u9=... --blocks 2 F`。
5. dex 不压缩对照：`cold --a qa.perf.u9=... --b qa.perf.nocompress=... --blocks 2 F`。
6. trace：`cold --a bench.v01=... --b bench.perf.u9=... --blocks 0 F`（只准备），再 `startup_trace.py capture --key bench.perf.u9 --other bench.v01 -n 10` 和 `--key bench.v01 --other bench.perf.u9 -n 10`。
7. 收尾：`check` 再跑一次，核对手机上原有的包都在；测量包留在手机上（以后的会话会重新准备），Mitchell 要的话再卸。

第 2 步的结果如果 ≤ 1.10，按计划第 10 节第 2 条缩小范围。
