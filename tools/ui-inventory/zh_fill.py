#!/usr/bin/env python3
"""One-off (U1): replaces English placeholder copies in values-zh-rCN/strings.xml with Chinese translations.

Upstream Xtra left about 200 zh-CN strings identical to the English ones, so the Chinese UI showed English in many
settings and dialogs. Format-only strings (timestamps, library names, emoji poll formats) stay as they are.
Run from the repo root: python tools/ui-inventory/zh_fill.py
"""
import re

T = {
    'use_eventsub_chat': 'EventSub 聊天', 'draw_behind_cutouts': '内容延伸到挖孔区域', 'text_size': '文字大小',
    'backup_settings': '备份设置和数据', 'restore_settings': '恢复设置和数据', 'share': '分享', 'check_updates': '检查更新',
    'no_updates_found': '已是最新版本', 'update_available': '有可用更新', 'update_message': '下载最新版本？',
    'update_using_browser': '在浏览器中打开更新链接', 'auto_check_updates': '启动时检查更新', 'update_check_frequency': '检查更新频率',
    'update_check_frequency_summary': '每 %s 天', 'notification_live_channel_title': '开播通知', 'live_notification': '%s 开播了',
    'live_notifications': '开播通知', 'sleep_timer_lock_screen': '睡眠定时结束后锁屏',
    'sleep_timer_time_picker': '睡眠定时使用 Material 时间选择器', 'stop_sleep_timer_message': '停止睡眠定时？',
    'enable_notifications': '开启通知', 'disable_notifications': '关闭通知', 'enabled_notifications': '已开启通知',
    'disabled_notifications': '已关闭通知', 'name_display': '用户名显示', 'name_display_summary': '显示名与登录名不同时显示哪一个',
    'name_display_entry1': '显示名和登录名', 'name_display_entry2': '只显示显示名', 'name_display_entry3': '只显示登录名',
    'show_system_message_emotes': '系统消息中显示表情', 'replying_to_message': '回复 %1$s\\: %2$s',
    'channel_stv_emote': '频道 7TV 表情', 'channel_bttv_emote': '频道 BTTV 表情', 'channel_ffz_emote': '频道 FFZ 表情',
    'global_stv_emote': '全局 7TV 表情', 'global_bttv_emote': '全局 BTTV 表情', 'global_ffz_emote': '全局 FFZ 表情',
    'channel_sub_emote': '%1$s 第 %2$s 档订阅表情', 'channel_follower_emote': '%s 关注者表情', 'bits_reward_emote': '%d Bits 奖励',
    'show_name_paints': '显示 7TV 名字特效', 'show_stv_badges': '显示 7TV 徽章', 'show_stv_personal_emotes': '显示 7TV 个人表情',
    'stv_live_updates': '7TV 表情实时更新', 'personal_stv_emote': '个人 7TV 表情', 'show_polls': '显示投票',
    'poll_title': '当前投票：%s', 'remaining_time': '剩余 %s', 'show_predictions': '显示预测', 'prediction_title': '当前预测：%s',
    'prediction_locked': '已锁定', 'prediction_refunded': '已退还', 'download_settings': '下载', 'update_settings': '更新设置',
    'playback_settings': '播放', 'chat_settings': '聊天', 'ui_settings': '界面', 'debug_settings': '调试',
    'download_threads': '下载线程数', 'download_threads_summary': '同时下载的分片数量', 'minimize': '最小化',
    'player_menu_settings': '播放器菜单设置', 'chapters': '章节', 'seek_to_live': '跳到直播最新处', 'audio_compressor': '音频压缩',
    'subtitles': '字幕', 'chat_input_toggle': '聊天输入框开关', 'chat_toggle': '聊天开关', 'fullscreen': '全屏',
    'supported_codecs': '支持的编码', 'hide_during_ads': '广告期间隐藏播放器', 'include_gql_token_stream': '直播请求附带 GQL 令牌',
    'include_gql_token_video': '回放请求附带 GQL 令牌', 'min_buffer': '最小缓冲', 'min_buffer_summary': '最小缓冲，单位毫秒',
    'max_buffer': '最大缓冲', 'max_buffer_summary': '最大缓冲，单位毫秒', 'buffer_for_playback_summary': '开始播放所需的缓冲，单位毫秒',
    'rebuffer_summary': '缓冲耗尽后恢复播放所需的缓冲，单位毫秒', 'live_min_speed': '直播最低速度',
    'live_min_speed_summary': '直播的最低播放速度（小数）', 'live_max_speed': '直播最高速度',
    'live_max_speed_summary': '直播的最高播放速度（小数）', 'live_target_offset_summary': '与直播最新处的距离，单位毫秒',
    'hide_stream_previews': '隐藏直播预览图', 'player_settings': '播放器', 'save_video_positions': '记住回放的观看位置',
    'delete_video_positions': '删除观看位置', 'delete_video_positions_message': '确定删除所有已保存的观看位置吗？',
    'enabled_setting': '已开启', 'disabled_setting': '已关闭', 'use_background_audio': '后台播放声音',
    'avoid_rounded_corners': '避开屏幕圆角', 'api_web_gql_token': '网页 GQL 令牌', 'chat_size_modifier': '缩放比例',
    'chat_emote_size': '表情大小', 'chat_badge_size': '徽章大小', 'overlay_emotes': '叠加表情', 'network_library': '网络库',
    'translate_chat_messages': '翻译聊天消息', 'downloaded_languages': '已下载的语言', 'translate_target_language': '目标语言',
    'translate_message': '翻译消息', 'translated_message': '译自%1$s：%2$s', 'translate_failed_id': '无法识别语言',
    'download_language_model_message': '下载 %s 的语言模型？', 'translate_from_selected': '从所选语言翻译',
    'translate_all_messages': '翻译本频道的所有消息', 'show_more_websocket_info': '显示更多 WebSocket 信息',
    'websocket_connected': '%s：已连接', 'websocket_disconnected': '%1$s：已断开 - %2$s', 'sort_and_type': '排序：%1$s。类型：%2$s',
    'search_tags': '搜索标签', 'invalid_directory': '目录无效', 'suggestions': '建议', 'languages': '语言', 'recent': '最近开播',
    'filters': '筛选器', 'sort_by': '排序：%1$s', 'following_tabs': '关注页标签', 'saved_tabs': '已保存页标签',
    'channel_tabs': '频道页标签', 'game_tabs': '游戏页标签', 'search_tabs': '搜索页标签', 'add_tag': '添加标签',
    'save_filters': '保存筛选器', 'delete_filter_message': '确定删除这个筛选器吗？', 'move_player_freely': '可以自由拖动迷你播放器',
    'store_recent_searches': '保存最近搜索', 'delete_recent_searches': '删除最近搜索',
    'delete_recent_searches_message': '确定删除所有最近搜索吗？', 'deletion_date': '删除日期', 'creation_date': '创建日期',
    'saved_date': '保存日期', 'about': '关于', 'team': '团队：%s', 'owner': '所有者：%s',
    'use_background_audio_pip_closed': '关闭画中画时后台播放声音', 'old_username': '曾用名：%s',
    'default_cellular_quality': '移动网络下的默认画质', 'player': '播放器', 'compact_number_formatting': '紧凑数字格式',
    'less_rounded_corners': '较小圆角', 'reduced_padding': '紧凑边距', 'compact_text': '紧凑文字', 'next': '下一步',
    'tv_login_message': '要使用全部功能，需要以安卓电视设备的身份登录。\\n\\n步骤：\\n• 复制网页上显示的代码。\\n• 点底部的“下一步”。'
                        '\\n• 输入代码并点“激活”（Activate）。\\n• 点“授权”（Authorize）。',
    'open_url': '打开网址',
    'external_login_message': '可以用外部浏览器登录。\\n\\n步骤：\\n• 点“打开网址”打开登录页。\\n• 登录账号并点“授权”（Authorize）。'
                              '\\n• 会出现一个报错页面。复制该页面的网址，粘贴到下面的输入框并点“确定”。',
    'loading': '加载中…', 'copy_code': '复制代码',
    'external_tv_login_message': '要使用全部功能，需要以安卓电视设备的身份登录。\\n\\n步骤：\\n• 复制屏幕上显示的代码。\\n• 点“打开网址”。'
                                 '\\n• 输入代码并点“激活”（Activate）。\\n• 点“授权”（Authorize）。\\n• 回到 Lagoon 并点“下一步”。',
    'activate_notifications_when_following': '关注时开启开播通知', 'account': '账号',
    'downloading_update_progress': '正在下载更新… %1$s / %2$s', 'downloading_update': '正在下载更新…',
    'concurrent_downloads': '同时下载数', 'loaded_cached_stv_emotes': '已载入缓存的 7TV 表情',
    'loaded_cached_bttv_emotes': '已载入缓存的 BTTV 表情', 'loaded_cached_ffz_emotes': '已载入缓存的 FFZ 表情',
    'find_unlisted_video': '查找未公开的视频', 'video_not_found': '找不到视频', 'recent_messages_api_url': '历史消息 API 地址',
    'access_local_network_title': '授予本地网络权限', 'access_local_network_desc': '连接本地代理需要此权限',
    'custom_proxy_settings': '自定义代理设置', 'add_proxy': '添加代理', 'delete_proxy_message': '确定删除这个代理吗？',
    'add_proxy_query_params': '添加网址参数（allow_source、allow_audio_only、fast_bread）', 'proxy_url': '代理地址',
    'online': '在线', 'offline': '离线', 'proxy_error': '代理出错。%s 离线',
    'show_stream_notification_seekbar': '观看直播时在媒体通知上显示进度条', 'use_custom_proxies': '使用自定义代理',
    'use_stream_proxies': '使用 HTTP 代理', 'platform_param': '平台', 'player_type_param': '播放器类型',
    'use_video_swap': '使用视频替换', 'use_video_swap_desc': '广告期间替换播放列表', 'video_swap_settings': '视频替换设置',
    'default_values': '默认值', 'add_item': '添加条目', 'delete_item_message': '确定删除这个条目吗？', 'chat_gif_size': 'GIF 大小',
    'show_gif_messages': '显示 GIF 消息', 'proxy_timeout': '代理超时', 'proxy_timeout_summary': '代理超时，单位毫秒',
    'starting_video_swap': '开始视频替换', 'stopping_video_swap': '停止视频替换', 'starting_proxy': '启动代理',
    'stopping_proxy': '停止代理',
    # zh copies that kept an older English wording
    'update_url': '更新检查地址', 'api_token_settings': 'API 令牌设置', 'use_webp_images': '使用 WebP 图片',
    'navigation_tabs': '导航标签', 'translate_failed': '无法从%s翻译。点这里下载语言模型',
    'use_background_audio_locked': '锁屏时后台播放声音', 'use_background_audio_pip_locked': '画中画时锁屏后台播放声音',
}
# strings whose zh value is an older English copy, so they differ from today's English text
STALE = {'update_url', 'api_token_settings', 'use_webp_images', 'navigation_tabs', 'translate_failed',
         'use_background_audio_locked', 'use_background_audio_pip_locked'}

EN = 'app/src/main/res/values/strings.xml'
ZH = 'app/src/main/res/values-zh-rCN/strings.xml'


def strings(text):
    return {m.group(1): m.group(2) for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', text, re.S)}


def main():
    en = strings(open(EN, encoding='utf-8').read())
    zh_text = open(ZH, encoding='utf-8').read()
    zh = strings(zh_text)
    done = 0
    for key, value in T.items():
        if zh.get(key) != en.get(key) and key not in STALE:
            continue  # already translated
        pattern = re.compile(r'(<string name="%s"[^>]*>)(.*?)(</string>)' % re.escape(key), re.S)
        zh_text, n = pattern.subn(lambda m: m.group(1) + value + m.group(3), zh_text, count=1)
        done += n
    open(ZH, 'w', encoding='utf-8', newline='\n').write(zh_text)
    left = sorted(k for k, v in strings(zh_text).items() if en.get(k) == v)
    print(f'translated {done}; still identical to English: {len(left)}: {left}')


if __name__ == '__main__':
    main()
