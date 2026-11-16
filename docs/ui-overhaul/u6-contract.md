# U6 契约清单：播放页与播放服务

计划 8、10.1 红线 1、10.2「播放契约测试」。播放服务 `ExoPlayerService` 和 `player/ads` 一行没改；播放页只通过 `ui/player/PlaybackBackend.kt` 读写服务，服务通过 `ExoPlayerService.Listener` 的 8 个回调通知播放页，这一层在 `ui/player/PlayerConnection.kt`，行为照搬 v0.1 的 `ExoPlayerFragment`。测试是 `app/src/test/.../ui/player/PlayerContractTest.kt`（假服务、假播放器）。

## 1. 服务回调（8 个）

| 回调 | v0.1 做什么 | U6 去哪里 | 测试 |
| --- | --- | --- | --- |
| `started()` | 第一次：`start()`（直播信息、关注状态、章节、聊天）；之后：回放聊天重载；`restoreQuality` 为真时恢复之前的画质 | `PlayerConnection.onServiceStarted` → `Host.onFirstStart` / `onLaterStart`，`restoreQualityIfNeeded` | `theQualityBeforeTheAdBreakComesBackWhenTheServiceRestarts`、`theFirstStartAlsoRestoresTheQuality`、`theFirstStartWaitsForTheNetworkCheck` |
| `loaded()` | 画质、下载、仅音频按钮变为可用，弹层里的画质文字更新 | `state.loaded`、`state.qualityLabel`，`Host.onLoaded`（“仅听音频”起播在这里切音频） | `loadedEnablesQualityAndTellsThePage` |
| `changePlayerMode()` | 仅音频时控制层常驻、屏幕常亮、关闭自动画中画；否则恢复 | `Host.changePlayerMode`，`state.audioOnly` | `changePlayerModeFollowsAudioOnly` |
| `toast(resId, duration)` | 提示 | `Host.toast` | `toastsReachThePage` |
| `toast(text, duration)` | 提示 | `Host.toast` | 同上 |
| `updateVideoInfo()` | 标题、分类更新 | `state.title`、`state.gameName` | `updateVideoInfoShowsTheNewTitleAndGame` |
| `setPlayerSurface(secondaryPlayer)` | 广告时把同一个 SurfaceView 和播放器监听从主播放器移到副播放器，广告后移回 | 原样搬到 `serviceListener.setPlayerSurface`；仍是同一个 SurfaceView（不是 Media3 的 Compose 画面组件，见报告） | `theSurfaceFollowsTheAdSwapAndComesBack`、`aPageOpenedDuringASwappedAdBreakShowsTheSecondaryPlayer`、`swappingToTheSecondaryPlayerShowsVideoEvenAfterAudioOnly` |
| `adStatus(text)` | 画面左上角的广告状态，null 时隐藏 | `state.adStatus`，`PlayerScreen` 画在视频左上角 | `adStatusAppearsAndGoes` |

所有回调在页面的视图已销毁后都不做事（v0.1 每个回调都判断 `view != null`）：`callbacksAfterThePageIsGoneAreIgnored`。

## 2. 绑定与解绑

| 时机 | v0.1 | U6 | 测试 |
| --- | --- | --- | --- |
| 连上服务 | 画面和监听放到副播放器（有的话）或主播放器；恢复睡眠定时；停止服务计时；画中画按钮 | `PlayerConnection.attach` + `ExoPlayerFragment.onServiceConnected` | `surfaceAndListenerGoToTheMainPlayer` |
| `onStop` | `stop(isInPIPMode)`、存睡眠定时、开始服务计时、去掉监听、解绑 | `ExoPlayerFragment.onStop` → `PlayerConnection.detach` | `detachLeavesThePlayerAndTheService` |
| 关闭 | 暂停、停止、删播放状态、解绑、`stopSelf` | `ExoPlayerFragment.close` | 手动验收 |
| 画中画里的“仅音频” | `startAudioOnly`、存睡眠定时、开始服务计时、解绑 | `ExoPlayerFragment.startAudioOnly` | 手动验收 |
| 断网和恢复 | 直播重启播放器，回放 `prepare`；断网时回放 `stop` | `ExoPlayerFragment.onNetworkRestored/Lost` | 手动验收 |

## 3. 播放器事件（Player.Listener）

| 事件 | v0.1 | U6 |
| --- | --- | --- |
| 播放状态、playWhenReady、可用命令 | 播放暂停按钮、缓冲圈、画中画按钮；回放播完显示控制层 | `state.showPlayButton/buffering/ended`，`Host.onPlayButtonChanged`（`playerEventsBecomePageState`） |
| 视频尺寸 | 画面比例 | `state.videoAspect` → `AspectRatioFrameLayout` |
| 字幕 cue | `SubtitleView` | `Host.onCues` → `SubtitleView`（仍是 View） |
| 跳转 | 回放聊天跟着跳 | `Host.onSeek`（`aSeekMovesTheReplayChat`） |
| 倍速 | 回放聊天倍速 | `state.speed`、`Host.onSpeedChanged` |
| isPlaying | 屏幕常亮 | `state.isPlaying`、`Host.onIsPlayingChanged` |
| 轨道 | 字幕按钮；回放聊天开始加载 | `state.subtitles`、`Host.onTracksLoaded` |
| 时间线 | 时长 | `state.durationMs` |
| 轨道选择参数（视频关闭） | 隐藏画面 | `state.videoDisabled`（隐藏 SurfaceView，保留黑底） |

## 4. 播放页读写的服务成员

读：`type`、`videoId`、`clipId`、`channelId/Login/Name/Image`、`thumbnail`、`createdAt`、`viewerCount`、`durationSeconds`、`videoType`、`videoOffsetSeconds`、`videoCreatedAt`、`videoAnimatedPreviewURL`、`chatUrl`、`qualities`、`quality`、`previousQuality`、`started`、`loaded`、`player`、`secondaryPlayer`、`integrity`。

写：`streamId`、`gameId`、`gameSlug`、`gameName`、`title`（直播信息更新时）、`restoreQuality`。

调用：`togglePause`、`pause`、`seekBack`、`seekForward`、`seekTo`、`seekToDefaultPosition`、`player.setPlaybackSpeed`、`changeVolume`、`getCurrentVolume`、`restartPlayer`、`toggleDynamicsProcessing`、`toggleSubtitles`、`getPlaylistTags`、`changeQuality`、`startAudioOnly`、`setSleepTimer`、`setStopServiceTimer`、`stop()`、`stop(isInPIPMode)`、`stopSelf`、`retry`。

和 v0.1 相同，没有新增或去掉任何一个。
