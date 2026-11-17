# U8 功能总表核对（feature-inventory.md）

日期：2026-11-17。只读核对，分支 `ui/u8-release`（= master 83d2948c，U7 合并后）。对照基准 v0.1 = 8cb7ce91（tag `v0.1`）。
v0.1 以来 80 个提交、963 个文件变动。

## 0. 结论速览

- `feature-inventory.md` 里还有 **12 行没勾**：第 3 节 2 行（都写着 U8）和第 5 节“后台功能” 10 行（没有写负责阶段，按表头的意思应由 U8 在最终检查后勾选）。
- 12 行里**没有一项功能真的丢了**：2 个对话框行的类已经删掉或只剩一处调试用法；10 个后台组件都还在清单和代码里，各阶段报告里都实测过（JobInfoSchedulerService 一行本来就不是功能，见下）。
- 但核对时发现总表**没有覆盖到**的问题：设置页上还有 **4 个设置项已经没有任何代码读取**（改了没效果），另有 2 个“使用标签页”开关只改目的地 id、页面看起来完全一样。这些没有写进任何报告的删除说明，算是“悄悄失效”，见第 3 节，需要 U8 决定删掉还是补回。
- `u3-settings-map.md` 生成器有两处错误标注（第 4 节）。

## 1. 没勾的行

### 1.1 第 3 节 对话框（2 行，都归 U8）

| 行 | 现在的代码 | 判断 |
| --- | --- | --- |
| `ui/common/RadioButtonDialogFragment.kt`（BottomSheetDialogFragment），U8 | 文件已不存在，代码里没有任何引用。由提交 29ca7899 删除（U6）。u6-report.md 第 1 节“播放设置弹层”：“取代了 PlayerSettingsDialog、PlayerVolumeDialog、SleepTimerDialog、PlayerGamesDialog 和画质、倍速用的 RadioButtonDialogFragment，五个类和六个布局都删了。”画质、倍速现在是 `ui/player/PlayerSheets.kt` 里设置弹层的二级页 | **已用 Compose 重写**（U6），只是 U6 没回来勾这一行。U8 可以直接勾，备注“U6：画质、倍速页（PlayerSheets.kt），文件已删” |
| `util/ContextExtensions.kt`（1 个 alert dialog），U8 | `getAlertDialogBuilder()`（第 53 行）还在，`USE_MATERIAL3 = true` 常量也还在。全应用只剩一处调用：`ui/player/PlayerFragment.kt:1250` 的 `showPlaylistTags()`，即“调试 › 显示播放清单标签”打开的系统对话框（U6 报告里说“调试用的播放清单标签对话框，原样保留”）。`ui/main/MainActivity.kt:33` 和 `:101` 还 import 了 `AlertDialog` 和 `getAlertDialogBuilder`，但已经没有用到 | **功能还在**（调试功能，仍是系统对话框）。U8 二选一：把这个调试对话框改成 Compose 弹层后删掉 `getAlertDialogBuilder` 和 `USE_MATERIAL3`；或保留并在总表写明“调试用，保留系统对话框”。MainActivity 两个多余的 import 顺手删 |

### 1.2 第 5 节 后台功能（10 行，没有负责阶段）

表头写的是“必须在每个阶段都保持可用（计划 10.1 红线 4）”，没有阶段列；U3 勾了 SettingsActivity 和 LoginActivity，其余留给最后。逐项核对：

| 行 | 现在的代码 | 哪份报告实测过 | 判断 |
| --- | --- | --- | --- |
| activity `.ui.main.MainActivity` | 清单第 28 行，启动器入口和 twitch.tv 深链 intent-filter 都在 | U2（深链、开播通知、重建、进程被杀恢复）、U6（进程被杀恢复） | 在。U8 会大改它（去掉 nav_graph），所以应在 U8 最终验收后再勾 |
| service `.ui.player.ExoPlayerService` | 清单第 54 行；U6 起 `PlaybackBackend`/`ExoPlaybackBackend` 一对一转发，服务本身没改 | U6 播放对照表、去广告回归（U2、U5、U6、U7） | 在 |
| service `.ui.download.VideoDownloadService` | 清单第 58 行 | U5（30 秒和 8 分钟回放下载、暂停恢复、离线播放、删除） | 在 |
| service `.ui.download.StreamDownloadService` | 清单第 62 行 | U5（直播录制 9 分 39 秒、录制去广告） | 在 |
| service `androidx.appcompat.app.AppLocalesMetadataHolderService` | 清单第 66 行（`autoStoreLocales`） | U1（语言选择只剩三项，切换简体中文） | 在 |
| service `com.google.android.datatransport…JobInfoSchedulerService` | v0.1 清单里这一项是 `tools:node="remove"`（给 ML Kit 翻译库用的“移除”规则），也就是 v0.1 本来就**没有**这个服务；盘点脚本把移除规则当成了组件。U7 删 ML Kit 时把这条规则一起删了（u7-report.md：“ML Kit 两个库、翻译相关代码和设置、清单里为它加的 service 移除规则、混淆规则都删了”）。U7 之后的 debug 合并清单（`app/build/intermediates/merged_manifest/debug/…`，11-17）里已经没有 datatransport | — | **不是功能，随 ML Kit 一起移除（U7）**。U8 勾掉并备注即可 |
| receiver `.util.MediaButtonReceiver` | 清单第 75 行，`util/MediaButtonReceiver.kt` | U6（媒体键 `KEYCODE_MEDIA_PAUSE / PLAY_PAUSE` 生效） | 在 |
| receiver `android.app.admin.DeviceAdminReceiver` | 清单第 83 行；睡眠定时锁屏 `MainActivity.kt:1023 lockNow()`，申请权限 `PlayerFragment.kt:936` | U6（没有权限时点开关去申请，和 v0.1 一样） | 在 |
| worker `LiveNotificationWorker` | `ui/main/LiveNotificationWorker.kt`，MainActivity、SettingsViewModel、ChannelPage 调度 | U2（debug 包上“xQc is live”通知，点开进直播间） | 在 |
| 功能：后台音频、画中画、睡眠定时及锁屏、媒体键、开播通知、频道点数领取 | 后台音频 `ExoPlayerService.kt:1728`；画中画 `MainActivity.kt:651/971`、`PlayerFragment.kt:1050`；点数 `ui/chat/ChatViewModel.kt`（`chat_points_collect`） | U6（后台音频、画中画、睡眠定时）、U7（点数：0→10→20→80→90，自动领取通过） | 在。点数在 U7 还**变好了**：电视码登录的账号以前从不涨点数 |

小结：这 10 行都应该在 U8 的冒烟和最终验收之后勾上；没有要补的功能。

## 2. 总表里写着 U8 的其他事项（来自各阶段报告）

总表里只有上面 2 行明写 U8；报告里留给 U8 的事项：

| 来源 | 留给 U8 的事 | 现在的状态 |
| --- | --- | --- |
| u2-report.md“没有做的” | 返回手势的跟手预览（旧 Fragment 转场不支持） | 未做，等纯 Compose 导航 |
| u3-report.md 第 2 节 | “顶栏随滚动抬起”到 U8 删掉旧页面时一起删 | 已经没有任何代码读 `ui_theme_appbar_lift` 了（旧页面都已删），只剩 `C.kt` 常量和 `SettingsCatalog.removed` 的说明；说明文字“the old pages still read it”已过时 |
| u4-report.md 第 1 节 | `ui_gamepager` 不再改变布局，“U8 换纯 Compose 导航时一起删” | 还在设置页（外观 › 布局）显示；`FollowingFragment.kt:258`、`MainActivity.kt:515`、`PlayerFragment.kt:708` 用它选目的地 id |
| u4-report.md、u5-report.md | 关注页游戏段、搜索结果 → 游戏页，头像 → 频道页的共享元素转场 | 未做 |
| u4–u7 报告 | 冷启动和 v0.1 的差距（模拟器约 130%；真机 U4 时 100–106%） | 未做；U5–U7 真机冷启动、U6 真机丢帧也还没补测 |
| u7-report.md | 数据库里“全频道翻译”的表和 `TranslatedChannelsDao`（红线目录 db/）留给 U8 清理 | 还在：`db/TranslatedChannelsDao.kt`、`db/AppDatabase.kt`、`model/ui/TranslatedChannel.kt`。删表要改数据库版本，PR 需要受保护代码声明 |
| 任务说明 | 删掉 nav_graph 的 13 个 Fragment 目的地，换纯 Compose 导航 | `res/navigation/nav_graph.xml` 仍有 13 个 `<fragment>`；10 个 Fragment 类都还在（各自只是包一个 Compose 页） |
| 任务说明 | 删除旧主题资源 | `res/values/styles.xml` 里还有 Light / Amoled / Blue 主题及 ReducedPadding、CompactText、SmallCorners、NoCorners 的全部组合和 Compact* 样式 |

另外核对时看到、可以在 U8 一起删的残留：`ui/view/GridRecyclerView.kt`、`CustomSwipeRefreshLayout.kt`、`StrictEditText.kt`、`TextWithShadow.kt` 没有任何引用（`MarginItemDecoration.kt` 只被 GridRecyclerView 引用）；`res/layout` 只剩 `activity_main.xml` 和 `material_switch.xml`；`res/menu` 已经全部删光。

## 3. 总表没覆盖到的问题：设置还在、功能没了

总表第 2 节的设置行全部勾了，但“勾了”只表示设置页上还有这一行。用脚本把 `SettingsCatalog` 里 158 个存储键逐个在代码里找读取处（排除 `SettingsCatalog.kt` 和 `C.kt`），再人工看读取处是不是死代码：

| 设置（外观页） | 键 | 现在 | 什么时候失效的 | 有没有写进报告 |
| --- | --- | --- | --- | --- |
| 圆形头像 Circle profile pictures | `ui_rounduserimage` | 没有任何读取。Compose 的 `Avatar` 一律是圆的 | v0.1 由各 Adapter、ChannelPagerFragment、ChatFragment、MessageClickedDialog 读取；U4、U5、U7 删这些类时一并失效（`git log -S UI_ROUND_USER_IMAGE`：e3693239、813d4983、27cf3d6c） | **没有**。总表和 settings map 都写“kept” |
| 显示“回到顶部”按钮 Show scroll to top button | `ui_scrolltop` | 没有任何读取。v0.1 唯一读取处是 `PagedListFragment`，U5 删了；Compose 页靠“再点一次导航项回到顶部” | U5（813d4983） | **没有** |
| 竖屏列数 Portrait column count | `columnsPortrait` | 只剩没人用的 `ui/view/GridRecyclerView.kt` 和 `MainActivity.kt:1202` 的旧版本迁移写入。Compose 网格按窗口宽度定列数（U4：手机 3 列、600–840dp 4 列、平板 6 列；关注页 1/2/3/4 列） | 随最后一个旧列表页（U5） | 半写了：设置说明 `st_columns_summary` 是“Used by pages that are not redesigned yet”，可现在已经没有这样的页面 |
| 横屏列数 Landscape column count | `columnsLandscape` | 同上 | 同上 | 同上 |
| 已保存页使用标签页 Use tabs for "Saved" page | `ui_savedpager` | `MainActivity.kt:1068` 用它选 `savedPagerFragment` 还是 `savedMediaFragment`，两个都显示同一个 Compose 页 | U5 | 写了（u5-report：“`ui_savedpager` 的两个目的地显示同一个页面”），但设置项还显示 |
| 游戏页使用标签页 Use tabs for "Games" page | `ui_gamepager` | 同上，只选目的地 id | U4 | 写了（u4-report，说 U8 删） |

判断：前 4 项属于**事实上已经删除、但没有按计划 7.7 / 12 写明原因**的设置；后 2 项是已知、计划在 U8 删除的。建议 U8 把这 6 项移进 `SettingsCatalog.removed`（附原因），或者补回列数设置（Compose 网格读这两个键）。发布说明草稿里把它们列为“待定”。

其他保留设置都至少有一处活的读取。

## 4. `u3-settings-map.md` 的两处错误标注

1. **`player_settings` 键冲突。** v0.1 里同一个键名出现两次：首页的页面链接“播放器 Player”（现在是“播放”页）和播放器按钮页的开关“画质 Quality”。U6 把开关放进 `SettingsCatalog.removed` 后，生成器把首页那一行也标成了 removed（“moved into the player's settings sheet…”）。实际上“播放器”页面一直在（现在叫“播放”，`SettingsCatalog.playback`）。
2. **`player_menu_settings` 写成 “moved → page playerMenu”。** 这是 `tools/ui-inventory/settings_map.py` 第 73 行写死的映射；U6 已经删掉“播放器菜单设置”页（17 个开关全部去掉），`SettingsCatalog` 里没有 `playerMenu` 页了。应改成 removed。

两处一正一负，所以表头“removed: 43”的数字碰巧是对的：`SettingsCatalog.removed` 有 42 个键，加上“播放器菜单设置”这个页面入口共 43 行；“moved: 19”应为 19（首页“播放器”链接应算 moved，播放器菜单链接应算 removed）。

## 5. 各报告写明“故意删除”的行

| 阶段 | 删了什么 | 报告原话（摘要） | 总表 / 设置表怎么记的 |
| --- | --- | --- | --- |
| U1 | 主题：深色、黑色、浅色、蓝色和三种动态（Material You）主题，只剩一套深色 | u1-report：“主题固定为深色：`applyTheme()` 不再读主题相关设置，所有页面用深色主题；设置里的‘主题’入口隐藏。” | 第 2 节 `theme_settings` 及主题页 10 行，备注“U1: removed on purpose, app is dark only” |
| U1 | 12 套翻译，语言只剩跟随系统、English、简体中文 | u1-report：“删除简体中文和英文以外的 12 套翻译” | `ui_language` 行备注 |
| U1 | 安卓电视桌面入口、电视横幅、“触屏非必需”声明（电视设备码登录保留） | u1-report：“删除电视桌面入口、电视横幅和‘触屏非必需’声明” | 总表没有对应行（清单 uses-feature 不在盘点范围） |
| U2 | 底部导航的“热门”标签（按标签看直播的页面保留） | u2-report：“‘热门’标签不再出现，存着它为默认页的旧设置回落到关注页” | `rootTopFragment` 行“no tab since U2” |
| U2 | “关注页使用标签页” `ui_followpager`；“底栏颜色”不再读取 | u2-report 第 2 节 | 主题页、外观页对应行 |
| U3 | 主题类 8 项、底栏颜色、顶栏随滚动抬起、关注页标签页、广告屏蔽预设（改为由五个开关推算）、主题页入口，共 13 项 | u3-report 第 2 节“删减和移动” | `u3-settings-map.md` |
| U4 | 排序、语言、标签、游戏排序四个对话框合成一个筛选弹层（功能都在） | u4-report“一个筛选弹层” | 第 3 节对应行 |
| U5 | 书签、筛选器的删除确认改为撤销；团队页顶栏没有“设置”“登录”；“建议”改叫“相似” | u5-report“与计划的偏差” | 第 3 节、第 4 节 `team_menu` 行 |
| U6 | “播放器菜单设置”页 17 个开关；“播放器按钮”页默认开着的 8 个（画质、菜单、倍速、宽高比、音量、音频压缩、章节、重开），这些操作都进了设置弹层 | u6-report“播放器设置的删减（你选的方案 1）”：“去掉的 25 个键只是不再读，存储里不删” | `SettingsCatalog.removed`，settings map |
| U7 | 聊天翻译（翻译开关、目标语言、已下载语言、全频道翻译）和 ML Kit；“图片库”（Glide） | u7-report“删除聊天翻译（计划 12）”和“聊天设置”：“聊天翻译 3 项……随翻译删除；‘图片库’（Coil / Glide）随 Glide 删除” | `SettingsCatalog.removed`；第 3 节 ChatFragment 行“the dialog was the translation model download, removed with translation” |
| U0 | 最低系统版本 Android 7.0（API 24）→ Android 10（API 29） | u0-report 第 2 节：“最低系统版本 24 → 29（Android 10）” | 总表不涉及 |

所有删除项的存储值都保留（`SettingsCatalog.removed` 的注释：“Their stored values stay where they are; nothing is deleted from storage”），回退到 v0.1 不会丢设置。
