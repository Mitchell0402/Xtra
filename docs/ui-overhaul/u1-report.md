# U1 报告：组件库与地基

日期：2026-11-13。分支 `ui/u1-foundation`。

## 结论

| 门槛（计划 10.6 U1） | 结果 |
| --- | --- |
| 14 个组件的截图基线建立 | TODO |
| 主题固定为深色后旧界面全部可用、外观无异常 | TODO |
| 删除翻译后没有缺失的文字 | TODO |
| 安装包体积实测数字 | TODO |

## 1. 做了什么

- **设计参数**（`ui/design/theme`）：颜色、字体、圆角、间距、宽度分档、动效全部集中定义，界面代码只从这里取值。
  - 颜色按计划第 4 节：四层灰 `#111113 / #1B1B1F / #26262B / #303036`，强调色 `#5EE2C4`，柔红作警示，三级文字。提示级文字从计划的 40% 白调到 50% 白：40% 在卡片层上只有 3.9:1，达不到 10.5 要求的 4.5:1；单元测试逐层核对对比度。
  - 字体：Plus Jakarta Sans 四个字重（常规、中、半粗、粗），只保留拉丁字符子集，共 195 KB；中文走系统字体。字号只有 28、22、17、15、13、11 六档，单元测试保证。
  - 动效：4 档时长、2 条曲线、2 种弹簧，与计划第 5 节一致，单元测试核对数值。系统开启“移除动画”时，位移和缩放直接到位，只保留淡入淡出。
- **图标**：Lucide 0.460.0 的 86 个图标（ISC 许可），由 `tools/icons/lucide_to_compose.py` 从 SVG 生成 Compose 矢量，统一 1.75 线宽；14 个图标另有选中态的填充版本。
- **14 个组件**（`ui/design/components`），见第 2 节。
- **组件展示页**：只在调试版里，桌面上有单独的图标“Lagoon 组件”。三个分页：组件（14 个组件的全部状态）、动效（逐项可试）、参数（颜色、字号、动效数值）。
- **主题固定为深色**：`applyTheme()` 不再读主题相关设置，所有页面用深色主题；设置里的“主题”入口隐藏。设置值原样留在存储里，不改写，旧版本回退后仍能读到。
- **语言**：删除简体中文和英文以外的 12 套翻译；语言选项只剩“跟随系统、English、中文（简体）”。
- **安卓电视**：删除电视桌面入口、电视横幅和“触屏非必需”声明。以电视设备身份登录的流程没有动（U3）。
- 版本号 versionCode 2 → 3。

## 2. 组件清单

| 计划第 6 节的组件 | 代码 | 状态与动效 |
| --- | --- | --- |
| 直播卡片 | `LiveCard`、`LiveCardCompact` | 直播红点呼吸、人数滚动、按压缩放、长按菜单、封面淡入；封面可作共享元素 |
| 视频卡片 | `VideoCard`、`WatchProgress` | 时长角标、观看进度条、右侧操作位（如下载） |
| 游戏海报 | `GamePoster` | 3:4 封面、人数角标 |
| 频道行 | `ChannelRow`、`Avatar` | 直播中青绿环；无头像时显示首字 |
| 设置行 | `SettingRow`、`SettingIconTile` | 当前值、开关（整行可点）、箭头、高亮、禁用 |
| 分组卡片 | `SettingsGroup`、`SettingDependents` | 组标题和组说明；开关控制的子项展开收起 |
| 底部弹层 | `LagoonBottomSheet`、`SheetSurface` | 半高、全高、拖动条、下拉关闭、背景变暗 |
| 操作菜单 | `ActionMenu`、`ActionSheet` | 图标加文字，危险操作用红色；先收起弹层再执行 |
| 选择器 | `SingleChoiceList`、`MultiChoiceList`、`LagoonSlider`、`SegmentedControl` | 对勾弹出动画；分段选中块用弹簧滑动 |
| 筛选标签条 | `FilterChipRow`、`FilterChip` | 选中、下拉箭头、图标 |
| 导航 | `FloatingNavBar`、`FloatingNavRail`、`LargeTopBar` | 选中块滑动、选中项展开文字；向下滚动收起导航（迷你播放器时可锁定）；大标题随滚动收成小标题 |
| 状态页 | `SkeletonLiveCard`、`SkeletonRow`、`shimmer`、`EmptyState`、`ErrorState`、`staggeredAppear`、`LagoonPullToRefresh` | 微光扫过；首屏逐项淡入（间隔 30 毫秒，只前 12 项）；下拉刷新拉伸和触感 |
| 提示条 | `ToastHost`、`ToastHostState` | 弹簧升起；带撤销；排队显示 |
| 按钮与开关 | `LagoonButton`、`LagoonIconButton`、`LagoonSwitch`、`LagoonTextField`、`FollowButton` | 主次危险三种按钮；48dp 触控区；开关触感；关注图标弹跳 |

公共能力：`Modifier.pressable`（按压缩到 97% 并变亮一档，松手用“弹”弹簧回位，长按触感），`LagoonImage`（Coil 图片加载完成淡入，内存缓存命中直接显示），`LagoonMotion.sibling / forward / back`（四种转场里的两种页面级转场；卡片到详情用 Compose 的共享元素，展示页里有示例；临时层由弹层组件负责）。

## 3. 测试

TODO

## 4. 手动验收

TODO

## 5. 没有测到的项目

TODO
