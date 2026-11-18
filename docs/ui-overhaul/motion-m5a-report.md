# 动效 M5a 报告

日期：2026-11-18。计划：`docs/ui-overhaul/motion-audit.md`（修改版 2，已批准）第 5 节 M5 的第 1、2、4、5、6 条。分支 `motion/m5a`。第 3 条（改设置回来不整屏重建）单独是 M5b。

## 结论

| 验收项 | 结果 |
| --- | --- |
| JVM 测试 | 全过（新增 `SettingsNavMotionTest` 3 项） |
| 动效守护 / lint | 0 问题 / 通过；允许名单多了两条（见第 1 节第 3 条） |
| 模拟器 | 设置页进出、设置子页面的返回手势按住一半和松手，见第 2 节 |

## 1. 做了什么

1. **设置、登录的进出动画**（N9）：`LagoonViewMotion.pageTransitions` 在两个 Activity 的 `onCreate` 里用 `overrideActivityTransition`（Android 14+）配和页面前进/返回一样的动画：新页从右边滑入并淡入，旧页缩到 96 %、暗到 40 %；返回反过来。300 ms（`LagoonMotion.MEDIUM`），曲线是 `Standard`/`Exit` 的 `pathInterpolator` 资源。Android 14 以下保持系统默认。跨 Activity 的预测式返回（Android 15+）能不能预览，要在手机上确认。
2. **登录/退出后重启主界面**（N11）：从“无动画”改成 200 ms 淡入淡出。
3. **设置和筛选弹层的返回跟手**（T3）：手机上的设置页、筛选弹层的子页面换成 `SeekableTransitionState` + `PredictiveBackHandler`（和浏览页的海报→游戏页一样）：返回手势按住时页面跟着滑开、下面一页露出来，松手才真正返回，取消就滑回来。从别处直接打开的设置页（播放器的广告拦截页）返回照旧直接离开设置。运动守护对 `SeekableTransitionState.animateTo` 的“没传规格”有误报（不传规格才会走 `transitionSpec`，也就是 `LagoonMotion`），所以在 `tools/ci/motion_allowlist.txt` 加了两条，和 `BrowseDetail` 那条同理。
4. **设置宽屏右栏换页**（T2）：换分类用 `sibling`，进子页面/返回用 `forward`/`back`（`SettingsPaneRules` 按页面深度和所属分类判断）。
5. **导航胶囊**（N13、N14）：进画中画时胶囊和侧栏淡出、出来时淡入；窗口变宽变窄时侧栏淡入淡出，胶囊淡入（胶囊变成侧栏时胶囊是直接消失的，侧栏淡入，算半个交叉淡入）。
6. **没做的：弹层用 `motionScheme`**（D1）。Material3 1.4.0 里 `MotionScheme` 和带 `motionScheme` 参数的 `MaterialTheme` 都还是 `internal`，配不了；计划的退路“给 `SheetState` 传规格”也不行，`rememberModalBottomSheetState` 不收动画规格。确认过 1.4.0 的 `ModalBottomSheet`、`Switch`、下拉刷新确实读 `MotionScheme`，等 Material3 公开这个 API 后一处配上即可。内容变高的 `animateContentSize`（D2）也没做：弹层里的子页面切换已经由 `AnimatedContent` 的 `SizeTransform` 处理高度变化，找不到单独需要它的地方。

## 2. 模拟器（emulator-5554）

`E:/Projects/Lagoon-audit/motion/m5a-*`：

- `m5a-settings`：从“我的”打开设置：设置页从右边滑入，下面的关注页缩小变暗；进“外观”再返回。
- `m5a-back.jpg`：在“外观”上按住返回手势一半：外观页向右滑开、设置首页从下面露出来；松手后回到设置首页。

## 3. 留给手机 1

- Android 15+ 上跨 Activity 的预测式返回（关设置回主界面）是否还能预览。
- 登录/退出后的淡入淡出（模拟器没登录，没测）。
