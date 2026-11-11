# 直播广告实测工具（阶段 0）

这些脚本在电脑上直接请求 Twitch，用来判断各候选源当前会不会被缝合广告，以及在模拟器上验证去广告逻辑。全部请求都是匿名的（不带登录态）。

| 文件 | 用途 |
| --- | --- |
| `probe.py <频道>` | 快照：对每个 platform/playerType 组合、每个自定义代理、流代理各拉 4 次媒体列表，报告是否有广告 |
| `monitor.py <分钟> <频道,频道,...>` | 长时监测：每个频道 × 每个源保持一个会话，每 4 秒拉一次媒体列表，记录每一次广告（类型、时长），写入 `monitor.log` |
| `fakead.py <广告频道> [周期 开始 时长]` | 本地假广告代理：把另一个频道的画面按 Twitch 的格式（`Amazon\|...` 分片标题、`stitched-ad-*` 日期区间、不连续标记）定时缝进真实直播。广告用的是另一个主播的画面，漏出来一眼就能看出 |
| `rawcap.sh <目录> <频道> <帧数>` | 在模拟器上打开直播，逐帧统计画面区域亮度和色块，配合 `fakead.py` 判断广告有没有漏出 |
| `samples/` | 实测抓到的真实广告媒体列表，阶段 2 写广告检测器的单元测试用 |

## 用假广告代理在模拟器上测试

1. `python fakead.py cohhcarnage 120 40 40`（每 120 秒插一次 40 秒广告，广告频道要在线）
2. `adb reverse tcp:8765 tcp:8765`
3. 在应用的“自定义代理”列表最前面加 `http://127.0.0.1:8765/live/$channel`（debug 包允许对 127.0.0.1 用明文 http，见 `app/src/debug/res/xml/network_security_config.xml`）
4. 打开一个直播，`logcat -s XtraAds` 可以看到广告检测和切换日志

## 2026-11-11 实测结果（美国出口，匿名，默认设置）

**结论：现有默认配置挡不住中插广告；唯一干净的源是 `android/autoplay`，但它最高只有 360p。**

25 分钟内监测 6 个英文大频道，其中 3 个频道共出现 4 次中插（5 条广告，15 到 30 秒，`X-TV-TWITCH-AD-ROLL-TYPE="MIDROLL"`）：

| 源 | 4 次中插中出现广告的次数 | 备注 |
| --- | --- | --- |
| `web/site`（应用默认 token 参数） | 4/4 | |
| `web/popout` | 4/4 | |
| `eu.luminous.dev`（默认第一个自定义代理） | 4/4 | 主列表请求 0.7 到 1.3 秒 |
| `as.luminous.dev` | 4/4 | 主列表请求 0.8 到 0.9 秒 |
| `android/autoplay` | 0/4 | 最高 360p30 |

快照测试（2 个频道、10 种 platform/playerType、3 个 Client-ID）：

- `web/embed`、`web/picture-by-picture`、`web/thunderdome` 在别的组合都干净的时候也会单独收到广告，和 2025 年 11 月 Twitch 开始校验这些播放器类型的记录一致，不能用作备用源。
- 所有 token 都是 `server_ads: true`，换 platform/playerType 或 Client-ID 不会改变这个标记，广告是否缝合是服务端另外决定的。
- 长时监测的 30 个会话在起播时都没有片头广告（`at_start` 全部为 false），抓到的广告全是中插。
- 自定义代理：`eu`、`eu2`、`as.luminous.dev` 可用；`eu3.luminous.dev` 超时；`lb-*.cdn-perfprod.com` 大部分超时或返回 502/403/404，只有 `lb-as` 偶尔能用。
- 流代理 `firefox.api.cdn-perfprod.com:2023` 只放行 Twitch 的域名，可以用来请求 token；它对中插是否有效这次没有测到（测试时没有遇到广告）。
- 模拟器上的应用没有登录，所以登录状态下的广告表现没有实测。
