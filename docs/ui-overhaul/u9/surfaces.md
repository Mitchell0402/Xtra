# U9 子界面巡检表（surfaces.md）

U9 计划第 5 节。代码里能列出的界面（弹层调用点、播放菜单页、聊天弹层、设置子页和弹层、路由、Activity、对话框）每个一行，`tools/ci/legacy_ui_guard.py` 检查这张表和代码一一对应：代码里新加了弹层没登记、或者表里的界面在代码里已经没有了，CI 都会失败。编号格式 `文件#函数[#序号]`，不用行号。最后一节是页内部件、状态和通知，不受这个检查约束。

截图名对应 `tools/ui-inventory/u9_walkthrough.py` 的输出文件（前缀是 `phone-portrait_`、`phone-landscape_`、`tablet_`），原图在电脑 `E:\Projects\Lagoon-audit\u9\`。结论：**新** = Lagoon 样式；**旧** = 旧 Material 组件；**主题** = 窗口主题问题；A1–C4 = 计划第 3 节的问题号；“改前 / 改后”两列分别是第 0 步（v0.2）和第 10 步（U9）的巡检结论。

“登” = 内容随登录变化；“输” = 有输入框，要拍键盘弹出。


## A.1 Activity 和对话框窗口

| 编号 | 界面 | 代码 | 备注 | 截图 | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| `DownloadDialog` | 下载弹层（`ComposeSheetDialog` 子类） | L / 主题 | A4，输（文件名、范围） | player-record | 主题（A4） | 新（`Theme.Lagoon.Dialog`） |
| `IntegrityDialog` | 完整性校验 | L + Twitch 网页 / 主题 | A4、B7 |  | 主题（A4） | 新（`Theme.Lagoon.Dialog`；网页 B7 未做） |
| `LoginActivity` | 登录：网页登录、电视设备码 | L + Twitch 网页 | B7 |  | 新 | 新 |
| `MainActivity` | 主窗口：外壳、底部胶囊/侧栏、所有页面、播放器 | L | 登 | (全部) | 新 | 新 |
| `SettingsActivity` | 设置（首页、16 个子页、搜索、平板双栏） | L | 登（账号行） | settings-home | 新 | 新 |

## A.2 页面（导航路由）

| 编号 | 界面 | 代码 | 备注 | 截图 | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| `BrowseRoute` | 浏览（游戏网格） | L |  | browse | 新 | 新 |
| `ChannelRoute` | 频道页（含简介 Markdown） | L（B8 字体） | 登（关注按钮） | channel, channel-* | B8 | 新（B8 简介字体） |
| `FollowingRoute` | 关注（直播、回放、频道、游戏四段） | L | 登 | following-live, -videos, -channels, -games | 新 | 新 |
| `GameRoute` | 游戏页 | L |  | game, game-videos, game-clips | 新 | 新 |
| `GamesRoute` | 按标签的游戏 | L |  |  | 新 | 新 |
| `SavedRoute` | 我的（已保存、下载、书签、筛选） | L |  | me, me-downloads, me-bookmarks | 新 | 新 |
| `SearchRoute` | 搜索（直播、回放、频道、游戏） | L | 输 | search-* | 新 | 新 |
| `TeamRoute` | 团队页 | L |  |  | 新 | 新 |
| `TopStreamsRoute` | 热门直播 | L |  |  | 新 | 新 |

## A.3 `LagoonBottomSheet` / `ActionSheet` 调用点

| 编号 | 界面 | 代码 | 备注 | 截图 | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| `ChannelScreen.kt#ChannelClipCell` | 频道剪辑长按 | L |  |  | 新 | 新 |
| `ChannelScreen.kt#ChannelHeaderBlock` | 频道头直播长按 | L |  |  | 新 | 新 |
| `ChannelScreen.kt#ChannelScreen` | 频道“…” | L（登） |  | channel-more-sheet | 新 | 新 |
| `ChannelScreen.kt#ChannelVideoCell` | 频道回放长按 | L |  |  | 新 | 新 |
| `ChannelScreen.kt#SuggestionCell` | 频道推荐长按 | L |  |  | 新 | 新 |
| `ChatSheets.kt#ChatSheets` | 聊天弹层（6 种，见 A.6） | L | C2 两处 | chat-* | C2 | 新（C2） |
| `ComposeSheetDialog.kt#onCreateView` | 对话框弹层宿主（`DownloadDialog` 用） | L | 按子类登记 | player-record | 主题（A4）、C2 | 新（A4、C2） |
| `FilterSheet.kt#FilterSheet` | 浏览/游戏/热门的筛选（含标签、语言两层） | L | C2 三处限高，输 |  | C2 | 新（C2） |
| `FollowingScreen.kt#AccountSheet` | 关注页账号菜单 | L（登） |  | following-account-sheet | 新 | 新 |
| `FollowingScreen.kt#ChannelSortSheet` | 关注 › 频道排序 | L | 登 | following-channel-sort | 新 | 新（横屏可滚动） |
| `FollowingScreen.kt#ChannelsSegment` | 关注四段长按 | L（登） |  |  | 新 | 新 |
| `FollowingScreen.kt#ConfirmSheet` | 关注 › 确认（取关等） | L | 登 |  | 新 | 新 |
| `FollowingScreen.kt#GamesSegment` | 关注四段长按 | L（登） |  |  | 新 | 新 |
| `FollowingScreen.kt#LiveSegment` | 关注四段长按 | L（登） |  | following-live-longpress | 新 | 新 |
| `FollowingScreen.kt#VideoSortSheet` | 关注 › 回放排序 | L | 登 | following-video-sort | 新 | 新（横屏可滚动） |
| `FollowingScreen.kt#VideosSegment` | 关注四段长按 | L（登） |  |  | 新 | 新 |
| `GameScreen.kt#ClipCell` | 游戏页三类长按 | L |  |  | 新 | 新 |
| `GameScreen.kt#StreamCell` | 游戏页三类长按 | L |  |  | 新 | 新 |
| `GameScreen.kt#VideoCell` | 游戏页三类长按 | L |  |  | 新 | 新 |
| `GamesScreen.kt#GamesScreen` | 游戏网格长按 | L |  | browse-longpress | 新 | 新 |
| `LoginScreen.kt#LoginPromptSheet#1` | 以电视设备登录？ | L |  |  | 新 | 新 |
| `LoginScreen.kt#LoginPromptSheet#2` | 登录遇到问题 | L |  |  | 新 | 新 |
| `LoginScreen.kt#LoginPromptSheet#3` | 网页文字大小 | L |  |  | 新 | 新 |
| `PlayerSheets.kt#PlayerSheetHost` | 播放菜单（见 A.5） | L | C2 | player-menu-* | C2 | 新（C2，横屏可滚动） |
| `SavedScreen.kt#BookmarkCell` | 下载、书签、筛选长按 | L |  |  | 新 | 新 |
| `SavedScreen.kt#BookmarkSortSheet` | 书签排序 | L |  |  | 新 | 新（横屏可滚动） |
| `SavedScreen.kt#DeleteDownloadSheet` | 删除下载 | L |  |  | 新 | 新 |
| `SavedScreen.kt#DownloadCell` | 下载、书签、筛选长按 | L |  |  | 新 | 新 |
| `SavedScreen.kt#FilterCell` | 下载、书签、筛选长按 | L |  |  | 新 | 新 |
| `SavedScreen.kt#MoveSheet` | 移到应用存储 | L |  |  | 新 | 新 |
| `SavedScreen.kt#QuestionSheet` | 我的 › 确认 | L |  |  | 新 | 新 |
| `SavedScreen.kt#SavedScreen` | 我的 › “…” | L |  |  | 新 | 新 |
| `SearchScreen.kt#ResultItem#1` | 搜索结果（直播/回放）长按 | L |  |  | 新 | 新 |
| `SearchScreen.kt#ResultItem#2` | 搜索结果（回放）长按 | L |  |  | 新 | 新 |
| `SearchScreen.kt#UserLookupSheet` | 搜索用户 | L | 输 | selection-sheet-field | 新 | 新 |
| `SettingsScreen.kt#AccountSheet` | 设置账号菜单 | L（登） |  |  | 新 | 新 |
| `SettingsSheets.kt#AdLogSheet` | 去广告日志 | L |  |  | 新 | 新 |
| `SettingsSheets.kt#ChoiceSheet` | 设置 › 单选 | L |  |  | 新 | 新 |
| `SettingsSheets.kt#ConfirmSheet` | 设置 › 确认操作 | L |  |  | 新 | 新 |
| `SettingsSheets.kt#SliderSheet` | 设置 › 滑块 | L |  |  | 新 | 新 |
| `SettingsSheets.kt#TabsSheet` | 设置 › 标签页顺序 | L |  |  | 新 | 新 |
| `SettingsSheets.kt#TextSheet` | 设置 › 文字输入 | L | 输 |  | 新 | 新 |
| `SourceListsPage.kt#CustomProxySheet` | 自定义代理 增/改 | L | 输 |  | 新 | 新 |
| `SourceListsPage.kt#StreamProxySheet` | 直播代理 增/改 | L | 输 |  | 新 | 新 |
| `SourceListsPage.kt#VideoSwapSheet` | 回放替换 增/改 | L | 输 |  | 新 | 新 |
| `TeamScreen.kt#MemberCell#1` | 团队成员（直播中/离线）长按 | L |  |  | 新 | 新 |
| `TeamScreen.kt#MemberCell#2` | 团队成员（离线）长按 | L |  |  | 新 | 新 |
| `TopStreamsScreen.kt#TopStreamsScreen` | 热门直播长按 | L |  |  | 新 | 新 |
| `UpdatePrompt.kt#UpdatePrompt#1` | 有新版本 | L |  |  | 新 | 新 |
| `UpdatePrompt.kt#UpdatePrompt#2` | 正在下载更新 | L |  |  | 新 | 新 |
| `ViewerList.kt#ViewerListSheet` | 观众列表 | L | C2 |  | C2 | 新（C2） |

## A.5 播放菜单页（`PlayerSheet`）

| 编号 | 界面 | 代码 | 备注 | 截图 | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| `PlayerSheet.AdBlock` | 去广告 | L | C4 | player-menu-ad-blocking | C4 | 新（C4 直达设置 › 去广告） |
| `PlayerSheet.Aspect` | 画面比例 | L |  |  | 新 | 新 |
| `PlayerSheet.Chapters` | 章节 | L | 回放 |  | 新 | 新 |
| `PlayerSheet.Quality` | 画质 | L |  | player-menu-quality | 新 | 新 |
| `PlayerSheet.PlaylistTags` | 播放清单标签（调试，U9 新增，替代 A2） | L | 设置 › 调试打开后才有 | player-menu-playlist-tags | （U9 新增） | 新（单元/截图测试；调试开关下才出现） |
| `PlayerSheet.Settings` | 播放设置首页（直播/回放/剪辑/离线各不同） | L |  | player-menu | 新 | 新 |
| `PlayerSheet.Sleep` | 睡眠定时 | L |  | player-menu-sleep-timer | 新（入口 A1） | 新（进入 SleepAt 子页） |
| `PlayerSheet.SleepAt` | 在指定时间（U9 新增，替代 A1） | L | 睡眠定时 › 在指定时间 | player-menu-sleep-at | （U9 新增） | 新 |
| `PlayerSheet.Speed` | 倍速 | L | 回放 |  | 新 | 新 |
| `PlayerSheet.Volume` | 音量 | L |  | player-menu-volume | 新 | 新 |

## A.6 聊天弹层（`ChatSheet`）

| 编号 | 界面 | 代码 | 备注 | 截图 | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| `ChatSheet.EmoteCard` | 表情卡片 | L | C1：主列表点表情直接打开 |  | C1（主列表点不到） | 新（主列表点表情直接打开） |
| `ChatSheet.Points` | 频道点数 | L | 登 | chat-channel-points | 新 | 新 |
| `ChatSheet.Poll` | 投票 | L | 登 |  | 新 | 新 |
| `ChatSheet.Prediction` | 预测 | L | 登 |  | 新 | 新 |
| `ChatSheet.Thread` | 回复串 | L | 点击规则见 C1（D2 已定） |  | C1 | 新（C1 同一规则） |
| `ChatSheet.UserCard` | 用户卡片 | L | 登（操作按钮） | chat-user-card | 新 | 新 |

## A.7 设置子页（`PageId`）和设置弹层（`SheetRequest`）

| 编号 | 界面 | 代码 | 备注 | 截图 | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| `PageId.About` | 设置 › 关于和更新 | L |  | settings-about-and-updates | 新 | 新 |
| `PageId.AdBlock` | 设置 › 去广告 | L |  | settings-ad-blocking | 新 | 新 |
| `PageId.Advanced` | 设置 › 高级 | L |  | settings-advanced | 新 | 新 |
| `PageId.ApiTokens` | 设置 › 高级 › API 令牌 | L |  | settings-api-token-settings | 新 | 新 |
| `PageId.Appearance` | 设置 › 外观 | L |  | settings-appearance | 新 | 新 |
| `PageId.Backup` | 设置 › 备份和恢复 | L |  | settings-backup-and-restore | 新 | 新 |
| `PageId.Buffer` | 设置 › 高级 › 缓冲 | L |  | settings-buffer-settings | 新 | 新 |
| `PageId.Chat` | 设置 › 聊天 | L |  | settings-chat | 新 | 新 |
| `PageId.CustomProxy` | 设置 › 去广告 › 自定义代理 | L |  | settings-custom-proxy-settings | 新 | 新 |
| `PageId.Debug` | 设置 › 高级 › 调试 | L |  | settings-debug | 新 | 新 |
| `PageId.Downloads` | 设置 › 下载和录制 | L |  | settings-downloads-and-recording | 新 | 新 |
| `PageId.Notifications` | 设置 › 通知 | L |  | settings-notifications | 新 | 新 |
| `PageId.Playback` | 设置 › 播放 | L |  | settings-playback | 新 | 新 |
| `PageId.PlayerButtons` | 设置 › 播放 › 播放器按钮 | L |  | settings-player-buttons | 新 | 新 |
| `PageId.StreamProxy` | 设置 › 去广告 › HTTP 代理 | L |  | settings-http-proxy-settings | 新 | 新 |
| `PageId.VideoSwap` | 设置 › 去广告 › 回放替换 | L |  | settings-video-swap-settings | 新 | 新 |
| `SheetRequest.Account` | 设置弹层：账号（登） | L |  |  | 新 | 新 |
| `SheetRequest.AdLog` | 设置弹层：去广告日志 | L |  |  | 新 | 新 |
| `SheetRequest.Choice` | 设置弹层：单选 | L |  |  | 新 | 新 |
| `SheetRequest.Confirm` | 设置弹层：确认操作 | L |  |  | 新 | 新 |
| `SheetRequest.ConfirmLogout` | 设置弹层：确认退出（登，巡检不点） | L |  |  | 新 | 新 |
| `SheetRequest.Slider` | 设置弹层：滑块 | L |  |  | 新 | 新 |
| `SheetRequest.Tabs` | 设置弹层：标签页顺序 | L |  |  | 新 | 新 |
| `SheetRequest.Text` | 设置弹层：文字输入（输） | L |  |  | 新 | 新 |

## 页内部件、状态和通知（不受登记检查约束）

| 编号 | 界面 | 代码 | 备注 | 截图 | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| `ChatScreen#InputBar` | 聊天输入栏、补全、回复条 | L | 登，输 | chat-keyboard | 新 | 新 |
| `EmotePicker#EmotePicker` | 表情面板 | L | 登 | chat-emotes | 新 | 新 |
| `ChatScreen#RoomModesRow` | 房间模式、事件条、点数、回到最新 | L | 登 | chat | 新 | 新 |
| `ChatScreen#ChatRow` | 聊天行（6 种高亮） | L（B6 硬编码色） | C1 点击规则 | chat | C1、B6 | 新（C1 点击规则、B6 令牌） |
| `PlayerFragment#pickSleepTime` | 时钟选择器 | **旧** | A1 | player-menu-sleep-at | **旧**（A1，紫色时钟） | 已删除，改为 `PlayerSheet.SleepAt` |
| `PlayerFragment#showPlaylistTags` | 播放清单标签（调试） | **旧** | A2 |  | **旧**（A2） | 已删除，改为 `PlayerSheet.PlaylistTags` |
| `States` | 空状态、错误、加载、下拉刷新 | L |  | following-games | 新 | 新 |
| `PlayerStates` | 播放器加载、出错、离线、仅音频、迷你、画中画 | L | 画中画按钮 B3 | player | B3（画中画按钮） | 新（画中画按钮 Lucide，B3） |
| `Toasts` | 8 个页面的 ToastHost + 13 处系统 Toast | L / 系统 Toast | B1 |  | B1 | 新（B1 全局提示条；画中画/后台时系统 Toast） |
| `Notifications` | 媒体播放、开播提醒、回放下载、直播录制 | 系统样式 + 我们的小图标 | B2、B3 |  | B2、B3 | 新小图标和强调色（系统样式，B2/B3） |
| `Splash` | 系统启动画面 | L 底色 + 新图标 | 门槛 9 |  | 新 | 不变（属性对照测试） |
| `TextSelection` | 输入框文字选择工具栏和手柄（R3） | 系统 | A4 | selection-plain-field, selection-sheet-field | 主题（淡紫选中色，A3） | 新（青绿，页面和弹层一致，R3 已核实） |
