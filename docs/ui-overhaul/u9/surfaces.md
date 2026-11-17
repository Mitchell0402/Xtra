# U9 子界面巡检表（surfaces.md）

U9 计划第 5 节。代码里能列出的界面（弹层调用点、播放菜单页、聊天弹层、设置子页和弹层、路由、Activity、对话框）每个一行，`tools/ci/legacy_ui_guard.py` 检查这张表和代码一一对应：代码里新加了弹层没登记、或者表里的界面在代码里已经没有了，CI 都会失败。编号格式 `文件#函数[#序号]`，不用行号。最后一节是页内部件、状态和通知，不受这个检查约束。

截图名对应 `tools/ui-inventory/u9_walkthrough.py` 的输出文件（前缀是 `phone-portrait_`、`phone-landscape_`、`tablet_`），原图在电脑 `E:\Projects\Lagoon-audit\u9\`。结论：**新** = Lagoon 样式；**旧** = 旧 Material 组件；**主题** = 窗口主题问题；A1–C4 = 计划第 3 节的问题号；“改前 / 改后”两列分别是第 0 步（v0.2）和第 10 步（U9）的巡检结论。

“登” = 内容随登录变化；“输” = 有输入框，要拍键盘弹出。


## A.1 Activity 和对话框窗口

| 编号 | 界面 | 代码 | 备注 | 截图 | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| `DownloadDialog` | 下载弹层（`ComposeSheetDialog` 子类） | L / 主题 | A4，输（文件名、范围） | player-record |  |  |
| `IntegrityDialog` | 完整性校验 | L + Twitch 网页 / 主题 | A4、B7 |  |  |  |
| `LoginActivity` | 登录：网页登录、电视设备码 | L + Twitch 网页 | B7 |  |  |  |
| `MainActivity` | 主窗口：外壳、底部胶囊/侧栏、所有页面、播放器 | L | 登 | (全部) |  |  |
| `SettingsActivity` | 设置（首页、16 个子页、搜索、平板双栏） | L | 登（账号行） | settings-home |  |  |

## A.2 页面（导航路由）

| 编号 | 界面 | 代码 | 备注 | 截图 | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| `BrowseRoute` | 浏览（游戏网格） | L |  | browse |  |  |
| `ChannelRoute` | 频道页（含简介 Markdown） | L（B8 字体） | 登（关注按钮） | channel, channel-* |  |  |
| `FollowingRoute` | 关注（直播、回放、频道、游戏四段） | L | 登 | following-live, -videos, -channels, -games |  |  |
| `GameRoute` | 游戏页 | L |  | game, game-videos, game-clips |  |  |
| `GamesRoute` | 按标签的游戏 | L |  |  |  |  |
| `SavedRoute` | 我的（已保存、下载、书签、筛选） | L |  | me, me-downloads, me-bookmarks |  |  |
| `SearchRoute` | 搜索（直播、回放、频道、游戏） | L | 输 | search-* |  |  |
| `TeamRoute` | 团队页 | L |  |  |  |  |
| `TopStreamsRoute` | 热门直播 | L |  |  |  |  |

## A.3 `LagoonBottomSheet` / `ActionSheet` 调用点

| 编号 | 界面 | 代码 | 备注 | 截图 | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| `ChannelScreen.kt#ChannelClipCell` | 频道剪辑长按 | L |  |  |  |  |
| `ChannelScreen.kt#ChannelHeaderBlock` | 频道头直播长按 | L |  |  |  |  |
| `ChannelScreen.kt#ChannelScreen` | 频道“…” | L（登） |  | channel-more-sheet |  |  |
| `ChannelScreen.kt#ChannelVideoCell` | 频道回放长按 | L |  |  |  |  |
| `ChannelScreen.kt#SuggestionCell` | 频道推荐长按 | L |  |  |  |  |
| `ChatSheets.kt#ChatSheets` | 聊天弹层（6 种，见 A.6） | L | C2 两处 | chat-* |  |  |
| `ComposeSheetDialog.kt#onCreateView` | 对话框弹层宿主（`DownloadDialog` 用） | L | 按子类登记 | player-record |  |  |
| `FilterSheet.kt#FilterSheet` | 浏览/游戏/热门的筛选（含标签、语言两层） | L | C2 三处限高，输 |  |  |  |
| `FollowingScreen.kt#AccountSheet` | 关注页账号菜单 | L（登） |  | following-account-sheet |  |  |
| `FollowingScreen.kt#ChannelSortSheet` | 关注 › 频道排序 | L | 登 | following-channel-sort |  |  |
| `FollowingScreen.kt#ChannelsSegment` | 关注四段长按 | L（登） |  |  |  |  |
| `FollowingScreen.kt#ConfirmSheet` | 关注 › 确认（取关等） | L | 登 |  |  |  |
| `FollowingScreen.kt#GamesSegment` | 关注四段长按 | L（登） |  |  |  |  |
| `FollowingScreen.kt#LiveSegment` | 关注四段长按 | L（登） |  | following-live-longpress |  |  |
| `FollowingScreen.kt#VideoSortSheet` | 关注 › 回放排序 | L | 登 | following-video-sort |  |  |
| `FollowingScreen.kt#VideosSegment` | 关注四段长按 | L（登） |  |  |  |  |
| `GameScreen.kt#ClipCell` | 游戏页三类长按 | L |  |  |  |  |
| `GameScreen.kt#StreamCell` | 游戏页三类长按 | L |  |  |  |  |
| `GameScreen.kt#VideoCell` | 游戏页三类长按 | L |  |  |  |  |
| `GamesScreen.kt#GamesScreen` | 游戏网格长按 | L |  | browse-longpress |  |  |
| `LoginScreen.kt#LoginPromptSheet#1` | 以电视设备登录？ | L |  |  |  |  |
| `LoginScreen.kt#LoginPromptSheet#2` | 登录遇到问题 | L |  |  |  |  |
| `LoginScreen.kt#LoginPromptSheet#3` | 网页文字大小 | L |  |  |  |  |
| `PlayerSheets.kt#PlayerSheetHost` | 播放菜单（见 A.5） | L | C2 | player-menu-* |  |  |
| `SavedScreen.kt#BookmarkCell` | 下载、书签、筛选长按 | L |  |  |  |  |
| `SavedScreen.kt#BookmarkSortSheet` | 书签排序 | L |  |  |  |  |
| `SavedScreen.kt#DeleteDownloadSheet` | 删除下载 | L |  |  |  |  |
| `SavedScreen.kt#DownloadCell` | 下载、书签、筛选长按 | L |  |  |  |  |
| `SavedScreen.kt#FilterCell` | 下载、书签、筛选长按 | L |  |  |  |  |
| `SavedScreen.kt#MoveSheet` | 移到应用存储 | L |  |  |  |  |
| `SavedScreen.kt#QuestionSheet` | 我的 › 确认 | L |  |  |  |  |
| `SavedScreen.kt#SavedScreen` | 我的 › “…” | L |  |  |  |  |
| `SearchScreen.kt#ResultItem#1` | 搜索结果（直播/回放）长按 | L |  |  |  |  |
| `SearchScreen.kt#ResultItem#2` | 搜索结果（回放）长按 | L |  |  |  |  |
| `SearchScreen.kt#UserLookupSheet` | 搜索用户 | L | 输 | selection-sheet-field |  |  |
| `SettingsScreen.kt#AccountSheet` | 设置账号菜单 | L（登） |  |  |  |  |
| `SettingsSheets.kt#AdLogSheet` | 去广告日志 | L |  |  |  |  |
| `SettingsSheets.kt#ChoiceSheet` | 设置 › 单选 | L |  |  |  |  |
| `SettingsSheets.kt#ConfirmSheet` | 设置 › 确认操作 | L |  |  |  |  |
| `SettingsSheets.kt#SliderSheet` | 设置 › 滑块 | L |  |  |  |  |
| `SettingsSheets.kt#TabsSheet` | 设置 › 标签页顺序 | L |  |  |  |  |
| `SettingsSheets.kt#TextSheet` | 设置 › 文字输入 | L | 输 |  |  |  |
| `SourceListsPage.kt#CustomProxySheet` | 自定义代理 增/改 | L | 输 |  |  |  |
| `SourceListsPage.kt#StreamProxySheet` | 直播代理 增/改 | L | 输 |  |  |  |
| `SourceListsPage.kt#VideoSwapSheet` | 回放替换 增/改 | L | 输 |  |  |  |
| `TeamScreen.kt#MemberCell#1` | 团队成员（直播中/离线）长按 | L |  |  |  |  |
| `TeamScreen.kt#MemberCell#2` | 团队成员（离线）长按 | L |  |  |  |  |
| `TopStreamsScreen.kt#TopStreamsScreen` | 热门直播长按 | L |  |  |  |  |
| `UpdatePrompt.kt#UpdatePrompt#1` | 有新版本 | L |  |  |  |  |
| `UpdatePrompt.kt#UpdatePrompt#2` | 正在下载更新 | L |  |  |  |  |
| `ViewerList.kt#ViewerListSheet` | 观众列表 | L | C2 |  |  |  |

## A.5 播放菜单页（`PlayerSheet`）

| 编号 | 界面 | 代码 | 备注 | 截图 | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| `PlayerSheet.AdBlock` | 去广告 | L | C4 | player-menu-ad-blocking |  |  |
| `PlayerSheet.Aspect` | 画面比例 | L |  |  |  |  |
| `PlayerSheet.Chapters` | 章节 | L | 回放 |  |  |  |
| `PlayerSheet.Quality` | 画质 | L |  | player-menu-quality |  |  |
| `PlayerSheet.PlaylistTags` | 播放清单标签（调试，U9 新增，替代 A2） | L | 设置 › 调试打开后才有 | player-menu-playlist-tags |  |  |
| `PlayerSheet.Settings` | 播放设置首页（直播/回放/剪辑/离线各不同） | L |  | player-menu |  |  |
| `PlayerSheet.Sleep` | 睡眠定时 | L |  | player-menu-sleep-timer |  |  |
| `PlayerSheet.SleepAt` | 在指定时间（U9 新增，替代 A1） | L | 睡眠定时 › 在指定时间 | player-menu-sleep-at |  |  |
| `PlayerSheet.Speed` | 倍速 | L | 回放 |  |  |  |
| `PlayerSheet.Volume` | 音量 | L |  | player-menu-volume |  |  |

## A.6 聊天弹层（`ChatSheet`）

| 编号 | 界面 | 代码 | 备注 | 截图 | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| `ChatSheet.EmoteCard` | 表情卡片 | L | C1：主列表点表情直接打开 |  |  |  |
| `ChatSheet.Points` | 频道点数 | L | 登 | chat-channel-points |  |  |
| `ChatSheet.Poll` | 投票 | L | 登 |  |  |  |
| `ChatSheet.Prediction` | 预测 | L | 登 |  |  |  |
| `ChatSheet.Thread` | 回复串 | L | 点击规则见 C1（D2 已定） |  |  |  |
| `ChatSheet.UserCard` | 用户卡片 | L | 登（操作按钮） | chat-user-card |  |  |

## A.7 设置子页（`PageId`）和设置弹层（`SheetRequest`）

| 编号 | 界面 | 代码 | 备注 | 截图 | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| `PageId.About` | 设置 › 关于和更新 | L |  | settings-about-and-updates |  |  |
| `PageId.AdBlock` | 设置 › 去广告 | L |  | settings-ad-blocking |  |  |
| `PageId.Advanced` | 设置 › 高级 | L |  | settings-advanced |  |  |
| `PageId.ApiTokens` | 设置 › 高级 › API 令牌 | L |  | settings-api-token-settings |  |  |
| `PageId.Appearance` | 设置 › 外观 | L |  | settings-appearance |  |  |
| `PageId.Backup` | 设置 › 备份和恢复 | L |  | settings-backup-and-restore |  |  |
| `PageId.Buffer` | 设置 › 高级 › 缓冲 | L |  | settings-buffer-settings |  |  |
| `PageId.Chat` | 设置 › 聊天 | L |  | settings-chat |  |  |
| `PageId.CustomProxy` | 设置 › 去广告 › 自定义代理 | L |  | settings-custom-proxy-settings |  |  |
| `PageId.Debug` | 设置 › 高级 › 调试 | L |  | settings-debug |  |  |
| `PageId.Downloads` | 设置 › 下载和录制 | L |  | settings-downloads-and-recording |  |  |
| `PageId.Notifications` | 设置 › 通知 | L |  | settings-notifications |  |  |
| `PageId.Playback` | 设置 › 播放 | L |  | settings-playback |  |  |
| `PageId.PlayerButtons` | 设置 › 播放 › 播放器按钮 | L |  | settings-player-buttons |  |  |
| `PageId.StreamProxy` | 设置 › 去广告 › HTTP 代理 | L |  | settings-http-proxy-settings |  |  |
| `PageId.VideoSwap` | 设置 › 去广告 › 回放替换 | L |  | settings-video-swap-settings |  |  |
| `SheetRequest.Account` | 设置弹层：账号（登） | L |  |  |  |  |
| `SheetRequest.AdLog` | 设置弹层：去广告日志 | L |  |  |  |  |
| `SheetRequest.Choice` | 设置弹层：单选 | L |  |  |  |  |
| `SheetRequest.Confirm` | 设置弹层：确认操作 | L |  |  |  |  |
| `SheetRequest.ConfirmLogout` | 设置弹层：确认退出（登，巡检不点） | L |  |  |  |  |
| `SheetRequest.Slider` | 设置弹层：滑块 | L |  |  |  |  |
| `SheetRequest.Tabs` | 设置弹层：标签页顺序 | L |  |  |  |  |
| `SheetRequest.Text` | 设置弹层：文字输入（输） | L |  |  |  |  |

## 页内部件、状态和通知（不受登记检查约束）

| 编号 | 界面 | 代码 | 备注 | 截图 | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| `ChatScreen#InputBar` | 聊天输入栏、补全、回复条 | L | 登，输 | chat-keyboard |  |  |
| `EmotePicker#EmotePicker` | 表情面板 | L | 登 | chat-emotes |  |  |
| `ChatScreen#RoomModesRow` | 房间模式、事件条、点数、回到最新 | L | 登 | chat |  |  |
| `ChatScreen#ChatRow` | 聊天行（6 种高亮） | L（B6 硬编码色） | C1 点击规则 | chat |  |  |
| `PlayerFragment#pickSleepTime` | 时钟选择器 | **旧** | A1 | player-menu-sleep-at |  |  |
| `PlayerFragment#showPlaylistTags` | 播放清单标签（调试） | **旧** | A2 |  |  |  |
| `States` | 空状态、错误、加载、下拉刷新 | L |  | following-games |  |  |
| `PlayerStates` | 播放器加载、出错、离线、仅音频、迷你、画中画 | L | 画中画按钮 B3 | player |  |  |
| `Toasts` | 8 个页面的 ToastHost + 13 处系统 Toast | L / 系统 Toast | B1 |  |  |  |
| `Notifications` | 媒体播放、开播提醒、回放下载、直播录制 | 系统样式 + 我们的小图标 | B2、B3 |  |  |  |
| `Splash` | 系统启动画面 | L 底色 + 新图标 | 门槛 9 |  |  |  |
| `TextSelection` | 输入框文字选择工具栏和手柄（R3） | 系统 | A4 | selection-plain-field, selection-sheet-field |  |  |
