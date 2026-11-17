# Lagoon v0.2 发布说明

> 2026-11-17。GitHub Release 正文用下面的“中文”和“English”两节；开发者附录只留在仓库里。

---

## 中文

### 一句话

Lagoon v0.2 换上了全新的界面：每个页面、播放器和聊天都重做了，安装包从 36 MB 变成 17 MB。**去广告的方式和设置没有变**，v0.1 的登录、关注、书签、下载、筛选器和设置在覆盖安装后都会保留。

> **需要 Android 10 或更高版本。** Android 7–9 的设备装不了 v0.2，请继续用 v0.1。
> 用 Android 12 及以上、原来用默认“动态深色”主题的，更新后颜色会变成固定的 Lagoon 深色。
> 如果 v0.1 里的“检查更新”没有找到 v0.2，请直接下载下面的 APK 覆盖安装（v0.1 的更新设置页可能存了上游项目的地址；v0.2 起会忽略它）。

### 新功能和变化

**全新的 Lagoon 设计**
- 统一的深色界面：四层灰底、青绿色强调色、新的字体和图标，按压、展开、切换页面都有动画；系统开了“移除动画”时只保留淡入淡出。
- 底部是悬浮的导航胶囊（关注、浏览、我的），横屏和平板换成左侧竖栏。
- 磨砂玻璃效果：导航胶囊、关注页的分段标签和提示条在 Android 12L 及以上是真正的模糊玻璃。不喜欢可以在 设置 › 外观 › “透明效果” 里关掉；省电模式、高对比度文字等情况下会自动换成不透明的样式。
- 平板和宽屏有专门的双栏布局（设置、频道页、播放器）。

**关注**
- 直播、视频、频道、游戏四个分段；顶部一排正在直播的头像，点一下直接进直播间。
- 长按卡片弹出操作菜单：打开频道或游戏、按标签浏览、录制、分享、取消关注等。
- 加载时显示骨架屏，下拉刷新；断网时显示错误和“重试”，网络恢复后自动刷新。

**浏览和搜索**
- 游戏是海报网格，点开游戏时封面会“飞”到游戏页页头；返回手势可以跟着手指退回去。
- 排序、语言、标签合并成一个筛选弹层，可以“为这个游戏记住”“设为默认”“存到我的筛选器”。
- 搜索合成一页：输入时即时出结果，频道、直播、游戏、视频分组显示，每组可“查看全部”；最近搜索还在；搜索框旁边可以按 ID 或登录名查找用户。

**频道页和团队页**
- 新的页头：横幅、头像（直播中带青绿环）、简介、关注按钮和通知铃铛；正在直播时有一张直播卡片。
- 分段：相似（原来叫“建议”）、视频、剪辑、聊天、关于。

**我的（书签、下载、筛选器）**
- 下载显示进度环、速度、剩余时间；录制中显示已录时长和大小。
- 书签、下载、筛选器都可以左滑删除，5 秒内可以撤销。
- 下载弹层重做：画质按钮，用两端带预览画面的滑块选择下载哪一段，也可以精确输入时间。

**播放器**
- 新的控制层和手势：单击显示或隐藏控制，回放双击左右快退快进，往下拖缩成迷你播放器，往上滑进全屏，横屏左右两侧上下滑调亮度和音量。
- 齿轮菜单改成底部设置弹层：画质、倍速、睡眠定时、仅音频、录制或下载、广告屏蔽状态在上面，其余操作都在“更多”里。
- 睡眠定时可以用滑块选 1 到 240 分钟，也可以设到指定时间。
- 横屏聊天可以选择并排或“叠在画面上”（新设置）；平板上视频和聊天中间的分隔条可以拖动。
- 迷你播放器是导航上方的卡片，左右划走就关闭。
- 直播的长按菜单新增“仅听音频”，起播就是音频模式。

**聊天（完全重写）**
- 刷屏时更流畅：同样每秒 30 条消息，真机上画出的帧数比 v0.1 多 37%。
- 表情面板：占键盘的位置，可以搜索，按最近使用、个人、本频道、Twitch、7TV、BTTV、FFZ 分组。
- 点消息打开用户卡片：回复、提及、复制、跟发、打开频道，下面列出这个人在本聊天室的发言。
- 慢速模式、仅订阅者、仅表情等限制显示成输入框上方的常驻小标签。
- 投票、预测、突袭显示在聊天顶部的一行细栏里，点开看详情。
- 观众列表改成弹层，可以搜索。
- 用户名颜色自动提亮，保证在深色背景上看得清。

**频道点数**
- 输入框旁边显示点数余额，点开可以看到详情；“自动领取奖励”关掉时，有奖励可领会出现“领取”按钮。
- 修复：用“电视设备码”登录的账号以前从不累计观看点数，现在所有登录方式都会累计，并自动领取奖励。

**设置和登录**
- 设置重做成九个分类（广告屏蔽、播放、聊天、下载与录制、外观、通知、备份与恢复、高级、关于与更新），可以搜索所有设置项。
- 外观、播放器按钮和聊天设置页顶部有实时预览。
- 广告屏蔽页顶部是统计卡片：当前模式、累计拦截的次数和分钟数、各种手段分别处理了多少次。
- 登录页重新设计；电视码登录时验证码大字显示，一键复制。

**更小、更稳**
- 安装包从 36.0 MB 降到 16.9 MB（去掉了聊天翻译用的 ML Kit 和 Glide）。
- 修复：断网时冷启动会闪退。
- 修复：网络请求恰好在超时那一刻完成时应用会闪退。
- 修复：很长的广告时段结束后，画面可能一直黑屏转圈（现在会跳回直播最新位置）。
- 修复：简体中文里“升序”“降序”译反了；约 200 条之前是英文的界面文字都译成了中文。
- 恢复备份后应用总是重启，确保恢复的设置全部生效。
- 页面切换改用一套导航：每个导航项各自记住自己的页面层级，应用在后台被系统回收后回来，页面和返回顺序都还在。
- 检查更新总是指向 Lagoon 自己的发布页。

**去广告：没有变化。** 拦截方式、来源列表和所有去广告设置都和 v0.1 一样，每个阶段都在已登录和未登录两种情况下做过回归测试，广告播出 0 次。

### 删除的内容

更新后下面这些不再提供。被删设置的旧值仍保存在手机上，没有被清除。

**系统要求**
- 最低系统版本从 Android 7.0 提高到 **Android 10**。Android 7–9 的设备无法安装 v0.2。

**主题（只剩一套深色主题）**
- 删除全部主题选项：动态深色、动态黑色、动态浅色（跟随壁纸取色）、深色、黑色（AMOLED）、浅色、蓝色。Android 12 及以上原来默认的“动态深色”也会变成新的固定深色。
- 同时删除的外观设置：跟随系统主题、深色主题开启时 / 关闭时用哪个主题、圆角（开 / 较小 / 关）、紧凑边距、紧凑文字、Material 3 主题、滚动时抬起顶栏、底部导航颜色。

**语言（只剩跟随系统、English、简体中文）**
- 删除 12 种界面语言：阿拉伯语、德语、西班牙语、法语、加利西亚语、印度尼西亚语、意大利语、日语、葡萄牙语（巴西）、俄语、土耳其语、繁体中文。原来选了这些语言的用户会回到跟随系统（系统语言不是中文时显示英文）。

**设置**
- “关注页使用标签页”：关注页现在是一个带分段的页面。
- 广告屏蔽的“预设”不再单独保存，而是由五个开关自动推算——用起来没有区别。
- 整个“播放器菜单设置”页（17 个开关：画质、播放速度、观众名单、章节、下载、书签、分享、查找未公开的视频、睡眠定时器、宽高比例、音量、开关字幕、重新启动播放器、开关聊天输入、开关聊天室、重新载入表情符号、从聊天室断开）：设置弹层里总是列出当前可用的全部操作。
- “播放器按钮”页里的 8 个开关：画质、播放器菜单、播放速度、宽高比例、音量、音频压缩、章节、重新启动播放器。这些操作都在设置弹层里。其余快捷按钮（下载、关注、睡眠定时、回到直播、仅音频、字幕、聊天输入框）仍可以打开，缩小、全屏、聊天开关仍可以关闭。
- 聊天的“图片库”（Coil / Glide）：聊天只用 Coil。
- 外观页里的“竖屏列数”“横屏列数”“圆形头像”“显示回到顶部按钮”“已保存页使用标签页”“游戏页使用标签页”：新页面不再用它们（列数按屏幕宽度自动决定，头像统一是圆的，再点一次当前导航项回到顶部，“我的”和游戏页都是一个带分段的页面）。

**功能**
- **聊天翻译**：翻译聊天消息、目标语言、已下载的语言（翻译模型下载）和按频道自动翻译全部删除。
- **安卓电视**：不再出现在电视桌面上。用“电视设备码”登录的方式保留。
- 底部导航的“热门”标签：点直播标签仍会打开按标签看直播的页面。原来把“热门”设为默认页的，会改为打开关注页。
- 小的行为变化：删除书签和筛选器不再弹确认框，改为可撤销；团队页顶栏不再有“设置”和“登录”（在关注、浏览、我的页面的账号按钮里）；频道页的聊天在切到“聊天”分段时才连接；最近搜索只在提交搜索或打开结果时记录，不再记录输入到一半的词。

---

## English

### In one line

Lagoon v0.2 is a full redesign: every page, the player and chat have been rebuilt, and the app download shrank from 36 MB to 17 MB. **Ad blocking works the same way, with the same settings.** If you install over v0.1, your login, follows, bookmarks, downloads, filters and settings stay.

> **Requires Android 10 or newer.** Devices on Android 7–9 cannot install v0.2; keep using v0.1 there.
> On Android 12 and newer, if you used the default "Dynamic dark" theme, colors change to the fixed Lagoon dark theme.
> If "Check for updates" in v0.1 does not find v0.2, download the APK below and install it over v0.1 (v0.1's update settings page may have stored the upstream project's address; v0.2 ignores it).

### What's new

**New Lagoon design**
- One dark look throughout: layered greys, a teal accent, a new typeface and icons, with motion on presses, sheets and page changes. If "Remove animations" is on in your system settings, the app only fades.
- A floating navigation pill (Following, Browse, Me) sits at the bottom. On landscape and tablet screens it becomes a side rail.
- Frosted glass: on Android 12L and newer, the navigation pill, Following tabs and toasts are blurred glass. You can turn this off under Settings › Appearance › "Transparency effects". It also switches to a solid look by itself in battery saver, with high-contrast text, and similar cases.
- Tablets and wide screens get two-pane layouts (settings, channel page, player).

**Following**
- Four tabs: Live, Videos, Channels and Games. A row of live avatars at the top takes you straight into a stream.
- Long-press any card for actions: open the channel or game, browse a tag, record, share, unfollow.
- Lists show placeholder cards while loading and support pull to refresh. When you're offline the page shows an error with Retry, and it refreshes on its own once you're back online.

**Browse and search**
- Games appear as a poster grid. When you open a game, the cover flies into the game page, and the back gesture follows your finger.
- Sort, languages and tags are now one filter sheet, with "Remember for this game", "Set as default" and "Save to my filters".
- Search is one page. Results update as you type and are grouped into channels, streams, games and videos, with "See all" for each group. Recent searches are kept, and a button next to the search field looks up a user by ID or login.

**Channel and team pages**
- New header with banner, avatar (teal ring when live), bio, follow button and notification bell. A live card appears when the channel is streaming.
- Tabs: Similar (formerly "Suggested"), Videos, Clips, Chat, About.

**Me (bookmarks, downloads, filters)**
- Downloads show a progress ring, speed and time left. Recordings show elapsed time and size.
- Swipe left to delete bookmarks, downloads and filters, with 5 seconds to undo.
- The download sheet is new: pick the quality, then choose a time range with a slider that shows a preview frame at each end, or type exact times.

**Player**
- New controls and gestures. Tap shows or hides the controls. On videos, double-tap left or right to seek. Drag down for the mini player, swipe up for fullscreen, and in landscape swipe up or down on the left or right side to change brightness or volume.
- The gear menu is now a settings sheet. Quality, speed, sleep timer, audio only, record/download and ad-block status are at the top, and every other action is under "More".
- Set the sleep timer with a 1–240 minute slider, or to a clock time.
- In landscape, chat can sit beside the video or on top of it (new setting). On tablets you can drag the divider between video and chat.
- The mini player is a card above the navigation. Swipe it away to close it.
- New "Listen audio only" action on long-pressed streams.

**Chat (rewritten)**
- Smoother in busy chats: at 30 messages a second, it draws 37% more frames than v0.1 on a real phone.
- The emote panel opens where the keyboard is. It has search and groups emotes by Recent, Personal, Channel, Twitch, 7TV, BTTV and FFZ.
- Tap a message to open a user card with Reply, Mention, Copy, Repeat and Channel, followed by that person's messages in the room.
- Slow mode, subs-only, emote-only and similar limits show as chips above the input.
- Polls, predictions and raids show in a slim bar at the top of chat. Tap it for details.
- The viewer list is a searchable sheet.
- Username colors are brightened so every name stays readable on the dark background.

**Channel points**
- Your points balance is shown next to the chat input, and tapping it shows details. When "Claim rewards automatically" is off, a Claim button appears whenever a reward is ready.
- Fix: accounts that logged in with the TV device code never earned watch-time points before. Every login method earns them now, and rewards are claimed automatically.

**Settings and login**
- Settings are reorganised into nine categories: Ad blocking, Playback, Chat, Downloads and recording, Appearance, Notifications, Backup and restore, Advanced, About and updates. Every setting can be found by search.
- The Appearance, Player buttons and Chat settings pages have live previews at the top.
- The Ad blocking page opens with a stats card: current mode, total ads blocked and minutes saved, and how many each method handled.
- The login flow is redesigned. The TV activation code is shown in large type with a copy button.

**Smaller and steadier**
- App download size went from 36.0 MB to 16.9 MB (ML Kit and Glide are gone).
- Fixed a crash when starting the app with no network.
- Fixed a crash when a network request finished at the same moment it timed out.
- Fixed a black, endlessly buffering screen after very long ad breaks. The player now jumps back to the live edge.
- Fixed "Ascending" and "Descending" being swapped in Simplified Chinese, and translated about 200 Chinese strings that were still in English.
- Restoring a backup now always restarts the app, so every restored setting takes effect.
- Pages now share one navigation: each tab remembers its own stack of pages, and both survive the system closing the app in the background.
- The update check always goes to Lagoon's own releases.

**Ad blocking: unchanged.** Blocking methods, source lists and every ad-blocking setting are the same as in v0.1. Each stage was regression-tested logged in and logged out, with 0 ads shown.

### Removed

These are no longer available. Values you had set for removed settings stay on your device and are not erased.

**System requirement**
- The app now requires **Android 10** or newer (was Android 7.0). Devices on Android 7–9 cannot install v0.2.

**Themes (one dark theme only)**
- All theme choices are removed: Dynamic dark, Dynamic black and Dynamic light (wallpaper colors), Dark, Black (AMOLED), Light, Blue. If you were on the default "Dynamic dark" on Android 12+, you now get the fixed Lagoon dark theme.
- These appearance settings are also gone: Change based on device theme, Dark theme on / Dark theme off, Rounded corners (on / less rounded / off), Reduced padding, Compact text, Material 3 theme, Lift app bar on scroll, Bottom navigation color.

**Languages (System default, English and Simplified Chinese only)**
- 12 UI languages are removed: Arabic, German, Spanish, French, Galician, Indonesian, Italian, Japanese, Portuguese (Brazil), Russian, Turkish, Traditional Chinese. If you had picked one of these, the app now follows your system language, and shows English unless your system is set to Chinese.

**Settings**
- Use tabs for "Following" page: Following is now one page with tabs.
- The ad-blocking "Preset" is no longer stored separately. It is worked out from the five switches, so nothing changes in use.
- The whole "Player menu settings" page (17 switches: Quality, Playback speed, Viewer list, Chapters, Download, Bookmark, Share, Find unlisted video, Sleep timer, Aspect ratio, Volume, Toggle subtitles, Restart player, Toggle chat input, Toggle chat, Reload emotes, Disconnect from chat). The settings sheet always lists every action that applies.
- 8 switches on the "Player buttons" page: Quality, Player menu, Playback speed, Aspect ratio, Volume, Audio compressor, Chapters, Restart player. These actions are all in the settings sheet. The remaining shortcut buttons (Download, Follow, Sleep timer, Seek to live, Audio only, Subtitles, Chat input) can still be turned on, and Minimize, Fullscreen and Chat toggle can still be turned off.
- Chat "Image library" (Coil / Glide): chat uses Coil only.
- Appearance › Portrait column count, Landscape column count, Circle profile pictures, Show scroll to top button, Use tabs for "Saved" page, Use tabs for "Games" page. The new pages no longer use them: column counts follow the screen width, avatars are always round, tapping the current tab scrolls to the top, and Me and game pages are single pages with tabs.

**Features**
- **Chat translation**: Translate chat messages, Target language, Downloaded languages (model downloads) and per-channel auto-translation are all removed.
- **Android TV**: the app no longer appears on the TV launcher. Logging in with the TV device code is still supported.
- The "Top" tab in the bottom navigation is gone. Tapping a stream tag still opens streams for that tag. If "Top" was your default tab, the app now opens Following.
- Small behaviour changes:
  - Deleting a bookmark or filter no longer asks for confirmation. You get an undo instead.
  - The team page bar no longer has Settings or Log in. Those are under the account button on Following, Browse and Me.
  - Channel chat connects only when you open the Chat tab.
  - Recent searches are saved when you submit a search or open a result, not while you are still typing.

---

## 开发者附录 / Developer appendix

| 项目 | v0.1（8cb7ce91） | v0.2 |
| --- | --- | --- |
| qa 安装包体积 | 36.04 MB | 16.90 MB（U7；U6 时 37.06 MB，去掉 ML Kit 的 arm64 原生库和 Glide 后下降） |
| minSdk / targetSdk | 24 / 37 | 29 / 37 |
| versionCode / versionName | 1 / 0.1 | 10 / 0.2 |
| 界面语言 | 14 种 + 跟随系统 | 2 种（en、zh-CN）+ 跟随系统 |
| 主题选项 | 7 种（含 3 种动态）+ 圆角 3 档、紧凑边距、紧凑文字、Material 3 开关 | 1 种（固定深色） |
| 设置项（含 G 阶段的 `ui_glass_effects`） | 226 | kept 164 · moved 19 · removed 43（`u3-settings-map.md`）；`SettingsCatalog.removed` 42 个键 + “播放器菜单设置”入口 |
| 按阶段删除的设置键 | — | U3：13（主题 8、底栏颜色、顶栏抬起、关注页标签页、广告预设、主题页入口）；U6：25（播放器菜单 17 + 播放器按钮 8）；U7：4（翻译 3 + 图片库） |
| U8 移除的失效设置 | — | `columnsPortrait`、`columnsLandscape`、`ui_rounduserimage`、`ui_scrolltop`、`ui_savedpager`、`ui_gamepager`（进 `SettingsCatalog.removed`，共 48 个键） |
| 新增设置键 | — | `ui_glass_effects`（G）、`player_landscape_chat_overlay`（U6） |
| 依赖 | Glide 4.16 + webpdecoder、ML Kit translate 17.0.3 + language-id 17.0.6 | 删除；新增 Compose BOM 2026.09.00、Haze 2.0.1、paging-compose、coil-compose、media3-ui-compose |
| 清单 | leanback / 触屏非必需 uses-feature、TV banner、LEANBACK_LAUNCHER、ML Kit 的 JobInfoSchedulerService 移除规则 | 全部删除；权限不变 |
| JVM 测试 | 原有去广告测试 | 约 611 项（U7），含单元、交互、契约、Roborazzi 截图 |
| 提交 / 文件 | — | v0.1 之后 80 个提交（12 个合并的 PR：#9 U0、#11 长广告后回到直播边缘、#12 U1、#13 U2、#14 G、#15 U3、#16 启动修复、#17 U4、#18 U5、#19 U6、#20 U7），963 个文件变动（+58,235 / −37,606） |
| 冷启动（模拟器 `am start -W`，装上各自的基线配置文件后 speed-profile） | 基准 | U8 109.9%（U7 115.2%）；见 u8-report.md |
| 聊天 30 条/秒（OnePlus 15） | 2621–2662 帧 / 60 秒 | 3587–3605 帧，帧超时 P95 −5.1 / −7.1 ms |
| 去广告回归 | — | U2、U5、U6、U7 已登录和未登录：广告播出 0 次；`ExoPlayerService` / `player/ads` 只有 PR #11 改过（长广告后回到直播边缘） |
