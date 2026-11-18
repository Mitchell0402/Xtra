# 动效 M5b 报告

日期：2026-11-18。计划：`docs/ui-overhaul/motion-audit.md`（修改版 2，已批准）第 5 节 M5 第 3 条（N10，第 8 节第 2 问的建议默认：保守版）。分支 `motion/m5b`。

## 结论

| 验收项 | 结果 |
| --- | --- |
| JVM 测试 | 全过（新增 `SettingsRefreshTest` 3 项） |
| 动效守护 / lint | 0 问题 / 通过 |
| 模拟器 | 改“显示标签”（Live）回来：主界面没有重建（logcat 里没有 relaunch）；改“紧凑数字”（Recreate）回来：主界面重建（logcat 有 `Schedule relaunch activity`），但看不到闪：旧画面盖着新画面，新画面第一帧后淡出，频道页的观看数从“2.7K”变成“2,772” |

## 1. 做了什么

1. **`refresh` 从布尔换成三级**（`Refresh.None / Live / Recreate`）：原来带 `refresh` 的 8 项 + 3 个分段顺序设置（`TabOrderSetting` 默认 `Recreate`）全部先是 `Recreate`；按计划第一批改成 `Live` 的 5 项：开播时长、标签、联播人数、直播预览样式、收藏剩余时长。名称显示、观看数缩写、刘海、分段顺序、语言、横屏聊天宽度、账号仍然 `Recreate`（播放器和聊天也读，或者只有窗口建立时读）。
2. **设置页回传最重的一级**：`SettingsActivity` 记下这次改过的项里最重的一级（`EXTRA_REFRESH`），语言、聊天宽度这类直接走 `markChanged()` 的算 `Recreate`；屏幕旋转后照样保留。旧的结果（没有这个字段）按 `Recreate` 处理（`SettingsResult.refresh`）。
3. **主界面按级处理**：`None` 什么都不做；`Live` 让外壳的 `settingsVersion` +1，经 `LocalSettingsVersion` 传给页面，读这几项的页面（关注、浏览、搜索、团队、热门直播、频道、游戏、“我的”）重新读设置、原地重画，不重建；“我的”页在“收藏剩余时长”从关到开时重跑一次 `updateBookmarkUsers()`（以前只在页面建立时跑）。`Recreate` 先等回到主界面的第一帧画完（`registerFrameCommitCallback`），用 `PixelCopy` 截下整窗，存进 `MainViewModel`（重建后还在），再 `recreate()`；新 Activity 在 `onCreate` 里用 M2a 的覆盖层把截图铺满，第一帧提交后 `SHORT` 淡掉并回收（覆盖层自带 1 秒保险）。不在冷启动路径上：只有带截图时才走这段。窗口截图不含视频，视频区由 M2a 的封面垫底接手。
4. **直播预览样式**还在三个 ViewModel 里决定分页的预取距离（`FollowedStreamsViewModel`、`GameStreamsViewModel`、`StreamSearchViewModel`），`Live` 时这三处要等下次建立才用新值；只影响预取多少，不影响显示，按计划接受。

## 2. 核对“只在主界面建立时读一次、又没有 refresh 标记”的项

主界面（`MainActivity`、外壳、主题）建立时读的设置里，和设置页能改的有交集的是：刘海（`Recreate`）、导航分段（`TabOrderSetting`，`Recreate`）、语言（`markChanged`，`Recreate`）、画中画开关（每次用时现读，不需要）、各页默认分段（只决定下次打开时停在哪一页，不需要重建）、玻璃效果（`GlassPolicy` 监听变化，已即时生效）。没有发现“改了要等重启才生效”的漏网项。“移除动画”是系统设置，不在本页。

## 3. 留给手机 1

- 改 `Recreate` 项回来的淡出在真机上是否完全看不到闪，尤其是播放器开着时（视频区靠封面垫底）。
