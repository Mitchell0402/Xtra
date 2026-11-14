package com.github.andreyasadchy.xtra.ui.settings.model

import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.util.C

/**
 * Every settings page and row (plan 7.7). The v0.1 list had 22 flat entries on the first page and 18 destinations;
 * here they are nine categories with sub-pages. The key map (kept, moved, removed) is docs/ui-overhaul/u3-settings-map.md.
 *
 * Rules (plan 8 and 10.1): keys, storage types and defaults are the v0.1 ones; nothing is migrated or rewritten.
 */
object SettingsCatalog {

    // ---- appearance ---------------------------------------------------------------------------------------------

    val appearance = SettingsPageSpec(
        id = PageId.Appearance,
        title = R.string.st_page_appearance,
        summary = R.string.st_page_appearance_summary,
        icon = LucideIcons.Palette,
        groups = listOf(
            SettingsGroupSpec(
                R.string.st_group_display, listOf(
                    ChoiceSetting(C.UI_LANGUAGE, "auto", R.string.language, ChoiceSource.Dynamic(DYNAMIC_LANGUAGE), icon = LucideIcons.Languages),
                    ToggleSetting(C.UI_GLASS, true, R.string.glass_effects, R.string.glass_effects_summary, LucideIcons.Layers),
                    ToggleSetting(C.UI_DRAW_BEHIND_CUTOUTS, false, R.string.draw_behind_cutouts, R.string.st_cutouts_summary, LucideIcons.Smartphone, refresh = true),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_cards, listOf(
                    ChoiceSetting(C.COMPACT_STREAMS, "disabled", R.string.hide_stream_previews, ChoiceSource.Arrays(R.array.compactStreamsEntries, R.array.compactStreamsValues), R.string.st_compact_streams_summary, LucideIcons.Rows3, refresh = true),
                    ChoiceSetting(C.UI_NAME_DISPLAY, "0", R.string.name_display, ChoiceSource.Arrays(R.array.nameDisplayEntries, R.array.nameDisplayValues), R.string.name_display_summary, LucideIcons.Type, refresh = true),
                    ToggleSetting(C.UI_ROUND_USER_IMAGE, true, R.string.rounduserimage, icon = LucideIcons.User, refresh = true),
                    ToggleSetting(C.UI_TRUNCATE_VIEW_COUNT, true, R.string.compact_number_formatting, R.string.st_truncate_summary, LucideIcons.Hash, refresh = true),
                    ToggleSetting(C.UI_UPTIME, true, R.string.show_uptime, icon = LucideIcons.Clock, refresh = true),
                    ToggleSetting(C.UI_TAGS, true, R.string.show_tags, icon = LucideIcons.Tag, refresh = true),
                    ToggleSetting(C.UI_BROADCASTERS_COUNT, true, R.string.broadcastersCount, icon = LucideIcons.Users, refresh = true),
                    ToggleSetting(C.UI_BOOKMARK_TIME_LEFT, true, R.string.show_vod_time_left, icon = LucideIcons.Bookmark, refresh = true),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_layout, listOf(
                    ChoiceSetting(C.PORTRAIT_COLUMN_COUNT, "1", R.string.portrait_column_count, ChoiceSource.Arrays(R.array.portraitColumnCount, R.array.portraitColumnCount), R.string.st_columns_summary, LucideIcons.Columns2, refresh = true),
                    ChoiceSetting(C.LANDSCAPE_COLUMN_COUNT, "2", R.string.landscape_column_count, ChoiceSource.Arrays(R.array.landscapeColumnCount, R.array.landscapeColumnCount), R.string.st_columns_summary, LucideIcons.LayoutGrid, refresh = true),
                    ToggleSetting(C.UI_SAVED_PAGER, true, R.string.use_savedpager, R.string.st_pager_summary, LucideIcons.Folder, refresh = true),
                    ToggleSetting(C.UI_GAME_PAGER, true, R.string.use_gamepager, R.string.st_pager_summary, LucideIcons.Gamepad2, refresh = true),
                    ToggleSetting(C.UI_SCROLL_TOP, true, R.string.show_scrolltop, icon = LucideIcons.ArrowUp, refresh = true),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_tabs, listOf(
                    TabOrderSetting(C.UI_NAVIGATION_TAB_LIST, C.DEFAULT_NAVIGATION_TAB_LIST, R.string.navigation_tabs,
                        listOf("2" to R.string.following, "0" to R.string.shell_nav_browse, "3" to R.string.shell_nav_mine), R.string.st_tabs_summary, LucideIcons.ListOrdered),
                    TabOrderSetting(C.UI_FOLLOWING_TABS, C.DEFAULT_FOLLOWING_TABS, R.string.following_tabs,
                        listOf("0" to R.string.games, "1" to R.string.live, "2" to R.string.videos, "3" to R.string.channels), icon = LucideIcons.Heart),
                    TabOrderSetting(C.UI_SAVED_TABS, C.DEFAULT_SAVED_TABS, R.string.saved_tabs,
                        listOf("0" to R.string.bookmarks, "1" to R.string.downloads, "2" to R.string.filters), icon = LucideIcons.Folder),
                    TabOrderSetting(C.UI_CHANNEL_TABS, C.DEFAULT_CHANNEL_TABS, R.string.channel_tabs,
                        listOf("0" to R.string.suggestions, "1" to R.string.videos, "2" to R.string.clips, "3" to R.string.chat, "4" to R.string.about), icon = LucideIcons.Tv),
                    TabOrderSetting(C.UI_GAME_TABS, C.DEFAULT_GAME_TABS, R.string.game_tabs,
                        listOf("0" to R.string.videos, "1" to R.string.live, "2" to R.string.clips), icon = LucideIcons.Gamepad2),
                    TabOrderSetting(C.UI_SEARCH_TABS, C.DEFAULT_SEARCH_TABS, R.string.search_tabs,
                        listOf("0" to R.string.videos, "1" to R.string.streams, "2" to R.string.channels, "3" to R.string.games), icon = LucideIcons.Search),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_browsing, listOf(
                    ChoiceSetting(C.UI_FOLLOW_BUTTON, "0", R.string.follow_button, ChoiceSource.Arrays(R.array.followButtonEntries, R.array.miscValues), R.string.st_follow_button_summary, LucideIcons.UserPlus),
                    ToggleSetting(C.UI_STORE_RECENT_SEARCHES, true, R.string.store_recent_searches, icon = LucideIcons.History),
                    ActionRow("delete_recent_searches", SettingAction.DeleteRecentSearches, R.string.delete_recent_searches, icon = LucideIcons.Trash2, destructive = true),
                )
            ),
        ),
    )

    // ---- playback -----------------------------------------------------------------------------------------------

    val playback = SettingsPageSpec(
        id = PageId.Playback,
        title = R.string.st_page_playback,
        summary = R.string.st_page_playback_summary,
        icon = LucideIcons.Play,
        groups = listOf(
            SettingsGroupSpec(
                R.string.st_group_quality, listOf(
                    ChoiceSetting(C.PLAYER_DEFAULT_QUALITY, "saved", R.string.player_defaultquality, ChoiceSource.Arrays(R.array.playerQualityEntries, R.array.playerQualityValues), icon = LucideIcons.Gauge),
                    ChoiceSetting(C.PLAYER_DEFAULT_CELLULAR_QUALITY, "saved", R.string.default_cellular_quality, ChoiceSource.Arrays(R.array.playerQualityEntries, R.array.playerQualityValues), icon = LucideIcons.Radio),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_background, listOf(
                    ToggleSetting(C.PLAYER_PICTURE_IN_PICTURE, true, R.string.picture_in_picture, R.string.st_pip_summary, LucideIcons.PictureInPicture2, SettingCondition.PictureInPicture),
                    ToggleSetting(C.PLAYER_BACKGROUND_AUDIO, true, R.string.use_background_audio, icon = LucideIcons.Headphones),
                    ToggleSetting(C.PLAYER_BACKGROUND_AUDIO_LOCKED, true, R.string.use_background_audio_locked, icon = LucideIcons.Lock),
                    ToggleSetting(C.PLAYER_BACKGROUND_AUDIO_PIP_CLOSED, false, R.string.use_background_audio_pip_closed, condition = SettingCondition.PictureInPicture),
                    ToggleSetting(C.PLAYER_BACKGROUND_AUDIO_PIP_LOCKED, true, R.string.use_background_audio_pip_locked, condition = SettingCondition.PictureInPicture),
                    ToggleSetting(C.PLAYER_DISABLE_BACKGROUND_VIDEO, true, R.string.disable_background_video, R.string.st_background_video_summary),
                    ToggleSetting(C.PLAYER_USE_BACKGROUND_AUDIO_TRACK, false, R.string.use_background_audio_track, R.string.st_background_track_summary),
                    ToggleSetting(C.PLAYER_SHOW_STREAM_NOTIFICATION_SEEKBAR, true, R.string.show_stream_notification_seekbar),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_sound_screen, listOf(
                    ToggleSetting(C.PLAYER_AUDIO_FOCUS, false, R.string.use_audio_focus, icon = LucideIcons.Volume2),
                    ToggleSetting(C.PLAYER_HANDLE_AUDIO_BECOMING_NOISY, true, R.string.handle_audio_becoming_noisy, icon = LucideIcons.Headphones),
                    ToggleSetting(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false, R.string.keep_screen_on_when_paused, icon = LucideIcons.Smartphone),
                    ToggleSetting(C.PLAYER_ROUNDED_CORNER_PADDING, false, R.string.avoid_rounded_corners, icon = LucideIcons.Maximize, condition = SettingCondition.AndroidS),
                    ToggleSetting(C.PLAYER_MOVE_FREELY, false, R.string.move_player_freely, R.string.st_move_freely_summary, LucideIcons.Minimize),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_progress, listOf(
                    ToggleSetting(C.PLAYER_USE_VIDEO_POSITIONS, true, R.string.save_video_positions, R.string.st_positions_summary, LucideIcons.History),
                    ActionRow("delete_video_positions", SettingAction.DeleteVideoPositions, R.string.delete_video_positions, icon = LucideIcons.Trash2, destructive = true),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_player_controls, listOf(
                    PageLink("player_button_settings", PageId.PlayerButtons, R.string.player_buttons, R.string.st_player_buttons_summary, LucideIcons.SlidersHorizontal),
                    PageLink("player_menu_settings", PageId.PlayerMenu, R.string.player_menu_settings, R.string.st_player_menu_summary, LucideIcons.EllipsisVertical),
                )
            ),
        ),
    )

    /** 33 v0.1 entries, restyled and grouped; trimming them is U6's call (plan 7.7). */
    val playerButtons = SettingsPageSpec(
        id = PageId.PlayerButtons,
        title = R.string.player_buttons,
        icon = LucideIcons.SlidersHorizontal,
        parent = PageId.Playback,
        groups = listOf(
            SettingsGroupSpec(
                R.string.st_group_buttons, listOf(
                    ToggleSetting(C.PLAYER_MINIMIZE, true, R.string.minimize, icon = LucideIcons.Minimize),
                    ToggleSetting(C.PLAYER_SETTINGS, true, R.string.quality, icon = LucideIcons.Gauge),
                    ToggleSetting(C.PLAYER_MENU, true, R.string.player_menu, icon = LucideIcons.EllipsisVertical),
                    ToggleSetting(C.PLAYER_FULLSCREEN, true, R.string.fullscreen, icon = LucideIcons.Maximize),
                    ToggleSetting(C.PLAYER_CHAT_TOGGLE, true, R.string.chat_toggle, icon = LucideIcons.MessageSquare),
                    ToggleSetting(C.PLAYER_CHAT_BAR_TOGGLE, false, R.string.chat_input_toggle, icon = LucideIcons.Keyboard),
                    ToggleSetting(C.PLAYER_SPEED_BUTTON, true, R.string.playback_speed, icon = LucideIcons.Gauge),
                    ToggleSetting(C.PLAYER_ASPECT, true, R.string.aspect_ratio, icon = LucideIcons.Maximize),
                    ToggleSetting(C.PLAYER_VOLUME_BUTTON, true, R.string.volume, icon = LucideIcons.Volume2),
                    ToggleSetting(C.PLAYER_AUDIO_COMPRESSOR_BUTTON, true, R.string.audio_compressor, icon = LucideIcons.AudioLines),
                    ToggleSetting(C.PLAYER_MODE, false, R.string.audio_only, icon = LucideIcons.Headphones),
                    ToggleSetting(C.PLAYER_SUBTITLES, false, R.string.subtitles, icon = LucideIcons.Captions),
                    ToggleSetting(C.PLAYER_GAMES_BUTTON, true, R.string.chapters, icon = LucideIcons.Gamepad2),
                    ToggleSetting(C.PLAYER_RESTART, true, R.string.restart_player, icon = LucideIcons.RotateCw),
                    ToggleSetting(C.PLAYER_SEEK_LIVE, false, R.string.seek_to_live, icon = LucideIcons.SkipForward),
                    ToggleSetting(C.PLAYER_PAUSE, false, R.string.player_pause, icon = LucideIcons.Pause),
                    ToggleSetting(C.PLAYER_DOWNLOAD, false, R.string.download, icon = LucideIcons.Download),
                    ToggleSetting(C.PLAYER_FOLLOW, false, R.string.follow, icon = LucideIcons.Heart),
                    ToggleSetting(C.PLAYER_SLEEP, false, R.string.sleep_timer, icon = LucideIcons.Timer),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_player_info, listOf(
                    ToggleSetting(C.PLAYER_CHANNEL, true, R.string.player_channel),
                    ToggleSetting(C.PLAYER_TITLE, true, R.string.show_title),
                    ToggleSetting(C.PLAYER_CATEGORY, true, R.string.show_category),
                    ToggleSetting(C.PLAYER_SHOW_UPTIME, true, R.string.show_uptime),
                    ToggleSetting(C.PLAYER_VIEWER_ICON, true, R.string.show_view_count_icon),
                    ToggleSetting(C.PLAYER_VIEWER_LIST, false, R.string.player_viewerlist),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_gestures, listOf(
                    ToggleSetting(C.PLAYER_DOUBLE_TAP, true, R.string.player_doubletap, icon = LucideIcons.SquareMousePointer),
                    TextSetting(C.PLAYER_REWIND, "10", R.string.rewind, TextKind.Integer, unit = R.string.seconds_full, icon = LucideIcons.RotateCcw),
                    TextSetting(C.PLAYER_FORWARD, "10", R.string.forward, TextKind.Integer, unit = R.string.seconds_full, icon = LucideIcons.RotateCw),
                    TextSetting(C.PLAYER_SPEED_LIST, "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0", R.string.playback_speeds, TextKind.Multiline, R.string.st_speed_list_summary, icon = LucideIcons.Gauge),
                )
            ),
            SettingsGroupSpec(
                R.string.sleep_timer, listOf(
                    ToggleSetting(C.SLEEP_TIMER_USE_TIME_PICKER, false, R.string.sleep_timer_time_picker, icon = LucideIcons.Clock),
                    ToggleSetting(C.SLEEP_TIMER_LOCK, false, R.string.sleep_timer_lock_screen, R.string.st_sleep_lock_summary, LucideIcons.Lock),
                    ActionRow("admin_settings", SettingAction.DeviceAdminSettings, R.string.admin_settings, R.string.st_admin_summary, LucideIcons.Shield),
                )
            ),
        ),
    )

    val playerMenu = SettingsPageSpec(
        id = PageId.PlayerMenu,
        title = R.string.player_menu_settings,
        icon = LucideIcons.EllipsisVertical,
        parent = PageId.Playback,
        groups = listOf(
            SettingsGroupSpec(
                R.string.st_group_menu_video, listOf(
                    ToggleSetting(C.PLAYER_MENU_QUALITY, false, R.string.quality, icon = LucideIcons.Gauge),
                    ToggleSetting(C.PLAYER_MENU_SPEED, false, R.string.playback_speed, icon = LucideIcons.Gauge),
                    ToggleSetting(C.PLAYER_MENU_ASPECT, false, R.string.aspect_ratio, icon = LucideIcons.Maximize),
                    ToggleSetting(C.PLAYER_MENU_VOLUME, false, R.string.volume, icon = LucideIcons.Volume2),
                    ToggleSetting(C.PLAYER_MENU_SUBTITLES, true, R.string.toggle_subtitles, icon = LucideIcons.Captions),
                    ToggleSetting(C.PLAYER_MENU_GAMES, false, R.string.chapters, icon = LucideIcons.Gamepad2),
                    ToggleSetting(C.PLAYER_MENU_RESTART, false, R.string.restart_player, icon = LucideIcons.RotateCw),
                    ToggleSetting(C.PLAYER_MENU_SLEEP, true, R.string.sleep_timer, icon = LucideIcons.Timer),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_menu_share, listOf(
                    ToggleSetting(C.PLAYER_MENU_DOWNLOAD, true, R.string.download, icon = LucideIcons.Download),
                    ToggleSetting(C.PLAYER_MENU_BOOKMARK, true, R.string.bookmark, icon = LucideIcons.Bookmark),
                    ToggleSetting(C.PLAYER_MENU_SHARE, true, R.string.share, icon = LucideIcons.Share2),
                    ToggleSetting(C.PLAYER_MENU_FIND_VOD, true, R.string.find_unlisted_video, icon = LucideIcons.Search),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_menu_chat, listOf(
                    ToggleSetting(C.PLAYER_MENU_VIEWER_LIST, true, R.string.viewer_list, icon = LucideIcons.Users),
                    ToggleSetting(C.PLAYER_MENU_CHAT_BAR, true, R.string.toggle_chat_bar, icon = LucideIcons.Keyboard),
                    ToggleSetting(C.PLAYER_MENU_CHAT_TOGGLE, false, R.string.toggle_chat, icon = LucideIcons.MessageSquare),
                    ToggleSetting(C.PLAYER_MENU_RELOAD_EMOTES, true, R.string.reload_emotes, icon = LucideIcons.Smile),
                    ToggleSetting(C.PLAYER_MENU_CHAT_DISCONNECT, true, R.string.disconnect_chat, icon = LucideIcons.WifiOff),
                )
            ),
        ),
    )

    // ---- chat ---------------------------------------------------------------------------------------------------

    /** 47 v0.1 entries, restyled and grouped; trimming them (and the translation removal) is U7's call (plan 7.7). */
    val chat = SettingsPageSpec(
        id = PageId.Chat,
        title = R.string.st_page_chat,
        summary = R.string.st_page_chat_summary,
        icon = LucideIcons.MessageSquare,
        groups = listOf(
            SettingsGroupSpec(
                R.string.st_group_chat_look, listOf(
                    ToggleSetting(C.CHAT_BOLD_NAMES, false, R.string.bold_usernames, icon = LucideIcons.Type),
                    ToggleSetting(C.CHAT_RANDOM_COLOR, true, R.string.randomcolor, icon = LucideIcons.Palette),
                    ToggleSetting(C.CHAT_THEME_ADAPTED_USERNAME_COLOR, true, R.string.theme_adapted_username_color, R.string.st_adapted_color_summary, LucideIcons.Sparkles),
                    ToggleSetting(C.CHAT_TIMESTAMPS, false, R.string.timestamps, icon = LucideIcons.Clock),
                    ChoiceSetting(C.CHAT_TIMESTAMP_FORMAT, "0", R.string.timestamp_format, ChoiceSource.Arrays(R.array.timestampEntries, R.array.timestampValues), dependsOn = C.CHAT_TIMESTAMPS),
                    TextSetting(C.CHAT_TEXT_SIZE, "14", R.string.text_size, TextKind.Decimal, icon = LucideIcons.Type),
                    SliderSetting(C.CHAT_SIZE_MODIFIER, 100, 1, 200, R.string.chat_size_modifier, unit = R.string.st_percent),
                    SliderSetting("chatWidth", 30, 0, 100, R.string.landscape_chat_width, R.string.st_chat_width_summary, unit = R.string.st_percent, icon = LucideIcons.Columns2),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_chat_emotes, listOf(
                    ToggleSetting(C.ANIMATED_EMOTES, true, R.string.animated_emotes, icon = LucideIcons.Smile),
                    ToggleSetting(C.CHAT_ZERO_WIDTH, true, R.string.overlay_emotes, icon = LucideIcons.Layers),
                    ToggleSetting(C.CHAT_SYSTEM_MESSAGE_EMOTES, true, R.string.show_system_message_emotes),
                    TextSetting(C.CHAT_EMOTE_SIZE, "29.5", R.string.chat_emote_size, TextKind.Decimal),
                    TextSetting(C.CHAT_BADGE_SIZE, "18.5", R.string.chat_badge_size, TextKind.Decimal),
                    ToggleSetting(C.CHAT_SHOW_GIF_MESSAGES, true, R.string.show_gif_messages, icon = LucideIcons.Image),
                    TextSetting(C.CHAT_GIF_SIZE, "79.5", R.string.chat_gif_size, TextKind.Decimal, dependsOn = C.CHAT_SHOW_GIF_MESSAGES),
                    ChoiceSetting(C.CHAT_IMAGE_LIBRARY, "0", R.string.image_library, ChoiceSource.Arrays(R.array.imageLibraryEntries, R.array.imageLibraryValues)),
                    ToggleSetting(C.CHAT_USE_WEBP, true, R.string.use_webp_images),
                    ChoiceSetting(C.CHAT_IMAGE_QUALITY, "4", R.string.image_quality, ChoiceSource.Arrays(R.array.imageQualityEntries, R.array.imageQualityValues)),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_chat_third_party, listOf(
                    ToggleSetting(C.CHAT_ENABLE_STV, true, R.string.enable_stv),
                    ToggleSetting(C.CHAT_ENABLE_BTTV, true, R.string.enable_bttv),
                    ToggleSetting(C.CHAT_ENABLE_FFZ, true, R.string.enable_ffz),
                    ToggleSetting(C.CHAT_SHOW_PAINTS, true, R.string.show_name_paints),
                    ToggleSetting(C.CHAT_SHOW_STV_BADGES, true, R.string.show_stv_badges),
                    ToggleSetting(C.CHAT_SHOW_PERSONAL_EMOTES, true, R.string.show_stv_personal_emotes),
                    ToggleSetting(C.CHAT_STV_LIVE_UPDATES, true, R.string.stv_live_updates),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_chat_messages, listOf(
                    SliderSetting(C.CHAT_LIMIT, 600, 1, 1000, R.string.message_limit, R.string.st_chat_limit_summary, icon = LucideIcons.Rows3),
                    ToggleSetting(C.CHAT_RECENT, true, R.string.recentMsg_enable, icon = LucideIcons.History),
                    SliderSetting(C.CHAT_RECENT_LIMIT, 100, 1, 800, R.string.recentMsg_limit, dependsOn = C.CHAT_RECENT),
                    TextSetting(C.CHAT_RECENT_MESSAGES_URL, "https://recent-messages.robotty.de/api/v2/recent-messages/\$channel", R.string.recent_messages_api_url, TextKind.Url, R.string.stream_proxy_url_desc, dependsOn = C.CHAT_RECENT),
                    ToggleSetting(C.CHAT_SHOW_USER_NOTICE, true, R.string.show_usernotice),
                    ToggleSetting(C.CHAT_SHOW_CLEAR_MSG, true, R.string.show_clearmsg),
                    ToggleSetting(C.CHAT_SHOW_CLEAR_CHAT, true, R.string.show_clearchat),
                    ChoiceSetting(C.CHAT_FIRST_MSG_VISIBILITY, "0", R.string.chat_firstmsg_visibility, ChoiceSource.Arrays(R.array.chatFirstMsgEntries, R.array.miscValues)),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_chat_interaction, listOf(
                    ToggleSetting(C.CHAT_POINTS_COLLECT, true, R.string.points_collect, icon = LucideIcons.Gift),
                    ToggleSetting(C.CHAT_POINTS_NOTIFY, false, R.string.points_notify, icon = LucideIcons.Bell),
                    ToggleSetting(C.CHAT_RAIDS_SHOW, true, R.string.show_raids, icon = LucideIcons.Users),
                    ToggleSetting(C.CHAT_RAIDS_AUTO_SWITCH, true, R.string.auto_switch_raids),
                    ToggleSetting(C.CHAT_POLLS_SHOW, true, R.string.show_polls),
                    ToggleSetting(C.CHAT_PREDICTIONS_SHOW, true, R.string.show_predictions),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_chat_translation, listOf(
                    ToggleSetting(C.CHAT_TRANSLATE, false, R.string.translate_chat_messages, icon = LucideIcons.Languages, condition = SettingCondition.TranslateSupported),
                    ChoiceSetting(C.CHAT_TRANSLATE_TARGET, "en", R.string.translate_target_language, ChoiceSource.Dynamic(DYNAMIC_TRANSLATE), condition = SettingCondition.TranslateSupported, dependsOn = C.CHAT_TRANSLATE),
                    ActionRow("downloaded_languages", SettingAction.DownloadedLanguages, R.string.downloaded_languages, condition = SettingCondition.TranslateSupported, dependsOn = C.CHAT_TRANSLATE),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_chat_connection, listOf(
                    ToggleSetting(C.PLAYER_KEEP_CHAT_OPEN, false, R.string.keep_chat_open, icon = LucideIcons.Radio),
                    ToggleSetting(C.CHAT_USE_WEBSOCKET, true, R.string.use_websocket),
                    ToggleSetting(C.CHAT_USE_SSL, true, R.string.chat_ssl, icon = LucideIcons.Lock),
                    ToggleSetting(C.CHAT_PUB_SUB_ENABLED, true, R.string.enable_pubsub),
                    ToggleSetting(C.CHAT_DISABLE, false, R.string.disable_chat, icon = LucideIcons.Ban),
                )
            ),
        ),
    )

    // ---- ad blocking --------------------------------------------------------------------------------------------

    /** The switches the presets set (AdBlockPreset); the page draws them under the status card and the presets. */
    val adBlockLayers = listOf(
        ToggleSetting(C.PLAYER_FILTER_AD_SEGMENTS, true, R.string.ad_block_filter, R.string.ad_block_filter_desc, LucideIcons.Scissors),
        ToggleSetting(C.PLAYER_USE_VIDEO_SWAP, true, R.string.use_video_swap, R.string.ad_block_video_swap_desc, LucideIcons.Film),
        ToggleSetting(C.PLAYER_USE_CUSTOM_PROXY, true, R.string.use_custom_proxies, R.string.ad_block_custom_proxy_desc, LucideIcons.Globe),
        ToggleSetting(C.PLAYER_USE_STREAM_PROXY, false, R.string.use_stream_proxies, R.string.ad_block_stream_proxy_desc, LucideIcons.Network),
        ToggleSetting(C.PLAYER_HIDE_ADS, true, R.string.hide_during_ads, R.string.ad_block_hide_desc, LucideIcons.Eye),
    )

    val adBlock = SettingsPageSpec(
        id = PageId.AdBlock,
        title = R.string.ad_block_settings,
        summary = R.string.ad_block_settings_desc,
        icon = LucideIcons.ShieldCheck,
        highlight = true,
        groups = listOf(
            SettingsGroupSpec(R.string.st_group_ad_layers, adBlockLayers),
            SettingsGroupSpec(
                R.string.st_group_ad_sources, listOf(
                    PageLink("video_swap_settings", PageId.VideoSwap, R.string.video_swap_settings, R.string.st_video_swap_summary, LucideIcons.Film),
                    PageLink("custom_proxy_settings", PageId.CustomProxy, R.string.custom_proxy_settings, R.string.st_custom_proxy_summary, LucideIcons.Globe),
                    PageLink("stream_proxy_settings", PageId.StreamProxy, R.string.proxy_settings, R.string.st_stream_proxy_summary, LucideIcons.Network),
                )
            ),
            SettingsGroupSpec(
                R.string.ad_block_advanced, listOf(
                    TextSetting(C.PROXY_TIMEOUT, "3000", R.string.proxy_timeout, TextKind.Integer, R.string.proxy_timeout_summary, R.string.st_unit_ms, LucideIcons.Timer),
                    ActionRow("ad_block_log", SettingAction.AdLog, R.string.ad_block_log, R.string.ad_block_log_desc, LucideIcons.List),
                    ActionRow("ad_block_stats_reset", SettingAction.ResetAdStats, R.string.ad_block_stats_reset, icon = LucideIcons.RotateCcw, destructive = true),
                )
            ),
        ),
    )

    val videoSwap = SettingsPageSpec(PageId.VideoSwap, R.string.video_swap_settings, LucideIcons.Film, emptyList(), parent = PageId.AdBlock)
    val customProxy = SettingsPageSpec(PageId.CustomProxy, R.string.custom_proxy_settings, LucideIcons.Globe, emptyList(), parent = PageId.AdBlock)
    val streamProxy = SettingsPageSpec(PageId.StreamProxy, R.string.proxy_settings, LucideIcons.Network, emptyList(), parent = PageId.AdBlock)

    // ---- downloads and recording --------------------------------------------------------------------------------

    val downloads = SettingsPageSpec(
        id = PageId.Downloads,
        title = R.string.st_page_downloads,
        summary = R.string.st_page_downloads_summary,
        icon = LucideIcons.Download,
        groups = listOf(
            SettingsGroupSpec(
                R.string.downloads, listOf(
                    ToggleSetting(C.DOWNLOAD_WIFI_ONLY, false, R.string.download_wifi_only, icon = LucideIcons.Radio),
                    ToggleSetting(C.DOWNLOAD_PLAYLIST_TO_FILE, false, R.string.download_vod_to_file, R.string.st_single_file_summary, LucideIcons.Film),
                    SliderSetting(C.DOWNLOAD_LIMIT, 2, 1, 10, R.string.concurrent_downloads, icon = LucideIcons.Download),
                    SliderSetting(C.DOWNLOAD_CONCURRENT_LIMIT, 10, 1, 20, R.string.download_threads, R.string.download_threads_summary, icon = LucideIcons.Activity),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_recording, listOf(
                    TextSetting(C.DOWNLOAD_STREAM_LIVE_CHECK, "2", R.string.download_live_check, TextKind.Integer, R.string.download_live_check_summary, R.string.seconds_full, LucideIcons.RefreshCw),
                    TextSetting(C.DOWNLOAD_STREAM_OFFLINE_CHECK, "10", R.string.download_offline_check, TextKind.Integer, R.string.download_offline_check_summary, R.string.seconds_full, LucideIcons.Radio),
                    TextSetting(C.DOWNLOAD_STREAM_START_WAIT, "120", R.string.download_start_wait, TextKind.Integer, R.string.download_start_wait_summary, R.string.st_unit_minutes, LucideIcons.Timer),
                    TextSetting(C.DOWNLOAD_STREAM_END_WAIT, "15", R.string.download_end_wait, TextKind.Integer, R.string.download_end_wait_summary, R.string.st_unit_minutes, LucideIcons.Timer),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_import, listOf(
                    ActionRow("import_app_downloads", SettingAction.ImportAppDownloads, R.string.import_app_downloads, R.string.st_import_summary, LucideIcons.Upload),
                )
            ),
        ),
    )

    // ---- notifications ------------------------------------------------------------------------------------------

    val notifications = SettingsPageSpec(
        id = PageId.Notifications,
        title = R.string.st_page_notifications,
        summary = R.string.st_page_notifications_summary,
        icon = LucideIcons.Bell,
        groups = listOf(
            SettingsGroupSpec(
                null, listOf(
                    ToggleSetting(C.LIVE_NOTIFICATIONS_ENABLED, false, R.string.live_notifications, R.string.st_live_notifications_summary, LucideIcons.BellRing),
                    ToggleSetting(C.UI_ACTIVATE_NOTIFICATIONS_WHEN_FOLLOWING, true, R.string.activate_notifications_when_following, R.string.st_follow_notify_summary, LucideIcons.UserPlus),
                ),
                description = R.string.st_notifications_footer,
            ),
        ),
    )

    // ---- backup and restore -------------------------------------------------------------------------------------

    val backup = SettingsPageSpec(
        id = PageId.Backup,
        title = R.string.st_page_backup,
        summary = R.string.st_page_backup_summary,
        icon = LucideIcons.Archive,
        groups = listOf(
            SettingsGroupSpec(
                null, listOf(
                    ActionRow("backup_settings", SettingAction.Backup, R.string.backup_settings, R.string.st_backup_summary, LucideIcons.Download),
                    ActionRow("restore_settings", SettingAction.Restore, R.string.restore_settings, R.string.st_restore_summary, LucideIcons.Upload),
                ),
                description = R.string.st_backup_footer,
            ),
        ),
    )

    // ---- advanced -----------------------------------------------------------------------------------------------

    val advanced = SettingsPageSpec(
        id = PageId.Advanced,
        title = R.string.st_page_advanced,
        summary = R.string.st_page_advanced_summary,
        icon = LucideIcons.Wrench,
        groups = listOf(
            SettingsGroupSpec(
                R.string.st_group_network, listOf(
                    ChoiceSetting(C.NETWORK_LIBRARY, C.OKHTTP, R.string.network_library, ChoiceSource.Dynamic(DYNAMIC_NETWORK), R.string.st_network_summary, LucideIcons.Network, SettingCondition.NetworkChoice),
                    PageLink("buffer_settings", PageId.Buffer, R.string.buffer_settings, R.string.st_buffer_summary, LucideIcons.Gauge),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_requests, listOf(
                    TextSetting(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264", R.string.supported_codecs, TextKind.Text, R.string.st_codecs_summary, icon = LucideIcons.Film),
                    ToggleSetting(C.TOKEN_INCLUDE_TOKEN_STREAM, true, R.string.include_gql_token_stream, R.string.sub_only_summary, LucideIcons.KeyRound),
                    ToggleSetting(C.TOKEN_INCLUDE_TOKEN_VIDEO, true, R.string.include_gql_token_video, R.string.sub_only_summary, LucideIcons.KeyRound),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_developer, listOf(
                    PageLink("api_token_settings", PageId.ApiTokens, R.string.api_token_settings, R.string.st_api_summary, LucideIcons.KeyRound),
                    PageLink("debug_settings", PageId.Debug, R.string.debug_settings, R.string.st_debug_summary, LucideIcons.Bug),
                )
            ),
        ),
    )

    val buffer = SettingsPageSpec(
        id = PageId.Buffer,
        title = R.string.buffer_settings,
        icon = LucideIcons.Gauge,
        parent = PageId.Advanced,
        groups = listOf(
            SettingsGroupSpec(
                R.string.st_group_buffer, listOf(
                    TextSetting(C.PLAYER_BUFFER_MIN, "15000", R.string.min_buffer, TextKind.Integer, R.string.min_buffer_summary, R.string.st_unit_ms),
                    TextSetting(C.PLAYER_BUFFER_MAX, "50000", R.string.max_buffer, TextKind.Integer, R.string.max_buffer_summary, R.string.st_unit_ms),
                    TextSetting(C.PLAYER_BUFFER_PLAYBACK, "2000", R.string.buffer_for_playback, TextKind.Integer, R.string.buffer_for_playback_summary, R.string.st_unit_ms),
                    TextSetting(C.PLAYER_BUFFER_REBUFFER, "2000", R.string.rebuffer, TextKind.Integer, R.string.rebuffer_summary, R.string.st_unit_ms),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_live_latency, listOf(
                    TextSetting(C.PLAYER_LIVE_MIN_SPEED, "", R.string.live_min_speed, TextKind.Decimal, R.string.live_min_speed_summary),
                    TextSetting(C.PLAYER_LIVE_MAX_SPEED, "", R.string.live_max_speed, TextKind.Decimal, R.string.live_max_speed_summary),
                    TextSetting(C.PLAYER_LIVE_TARGET_OFFSET, "2000", R.string.live_target_offset, TextKind.Integer, R.string.live_target_offset_summary, R.string.st_unit_ms),
                )
            ),
        ),
    )

    val apiTokens = SettingsPageSpec(
        id = PageId.ApiTokens,
        title = R.string.api_token_settings,
        icon = LucideIcons.KeyRound,
        parent = PageId.Advanced,
        groups = listOf(
            SettingsGroupSpec(
                R.string.st_group_login, listOf(
                    ChoiceSetting(C.API_LOGIN, "0", R.string.api_login, ChoiceSource.Arrays(R.array.loginApiEntries, R.array.miscValues), R.string.st_api_login_summary, LucideIcons.LogIn),
                    ToggleSetting(C.VALIDATE_TOKENS, true, R.string.api_validate_tokens, icon = LucideIcons.CircleCheck),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_clients, listOf(
                    TextSetting(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi", R.string.api_helix_client_id),
                    TextSetting(C.HELIX_REDIRECT, "https://localhost", R.string.api_helix_redirect, TextKind.Url),
                    TextSetting(C.GQL_CLIENT_ID2, "kd1unb4b3q4t58fwlpcbzcbnm76a8fp", R.string.api_gql_client_id),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_account_data, listOf(
                    TextSetting(C.USER_ID, null, R.string.api_user_id, store = PrefStore.Token),
                    TextSetting(C.USERNAME, null, R.string.api_user_login, store = PrefStore.Token),
                    TextSetting(C.TOKEN, null, R.string.api_helix_token, TextKind.Secret, store = PrefStore.Token),
                    TextSetting(C.GQL_TOKEN2, null, R.string.api_gql_token, TextKind.Secret, store = PrefStore.Token),
                    TextSetting(C.GQL_TOKEN_WEB, null, R.string.api_web_gql_token, TextKind.Secret, store = PrefStore.Token),
                ),
                description = R.string.st_account_data_footer,
            ),
        ),
    )

    val debug = SettingsPageSpec(
        id = PageId.Debug,
        title = R.string.debug_settings,
        icon = LucideIcons.Bug,
        parent = PageId.Advanced,
        groups = listOf(
            SettingsGroupSpec(
                R.string.st_group_debug_chat, listOf(
                    ToggleSetting(C.DEBUG_CHAT_FULL_MSG, false, R.string.show_copy_fullmsg),
                    ToggleSetting(C.DEBUG_API_COMMANDS, true, R.string.debug_api_commands),
                    ToggleSetting(C.DEBUG_API_CHAT_MESSAGES, true, R.string.use_api_chat_messages),
                    ToggleSetting(C.DEBUG_WEBSOCKET_INFO, false, R.string.show_more_websocket_info),
                    ToggleSetting(C.DEBUG_EVENT_SUB_CHAT, false, R.string.use_eventsub_chat),
                    ToggleSetting(C.DEBUG_PLAYER_MENU_PLAYLIST_TAGS, false, R.string.show_playlist_tags),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_integrity, listOf(
                    ToggleSetting(C.ENABLE_INTEGRITY, false, R.string.use_integrity, R.string.st_integrity_summary, LucideIcons.ShieldCheck),
                    ToggleSetting(C.USE_WEBVIEW_INTEGRITY, true, R.string.get_webview_integrity, dependsOn = C.ENABLE_INTEGRITY),
                    ToggleSetting(C.GET_ALL_GQL_HEADERS, false, R.string.get_all_headers),
                    TextSetting(C.GQL_HEADERS, null, R.string.gql_headers, TextKind.Secret, store = PrefStore.Token),
                    ActionRow("get_integrity_token", SettingAction.IntegrityToken, R.string.get_integrity_token, icon = LucideIcons.RefreshCw),
                )
            ),
        ),
    )

    // ---- about and updates --------------------------------------------------------------------------------------

    val about = SettingsPageSpec(
        id = PageId.About,
        title = R.string.st_page_about,
        summary = R.string.st_page_about_summary,
        icon = LucideIcons.Info,
        groups = listOf(
            SettingsGroupSpec(
                R.string.st_group_updates, listOf(
                    ActionRow("check_updates", SettingAction.CheckUpdates, R.string.check_updates, R.string.st_check_updates_summary, LucideIcons.RefreshCw),
                    ToggleSetting(C.UPDATE_CHECK_ENABLED, false, R.string.auto_check_updates, icon = LucideIcons.BellRing),
                    TextSetting(C.UPDATE_CHECK_FREQUENCY, "7", R.string.update_check_frequency, TextKind.Integer, unit = R.string.update_check_frequency_summary, dependsOn = C.UPDATE_CHECK_ENABLED),
                    ToggleSetting(C.UPDATE_USE_BROWSER, false, R.string.update_using_browser, icon = LucideIcons.ExternalLink),
                    // v0.1 read it with a null default (the XML default pointed at upstream Xtra); null means the Lagoon releases
                    TextSetting(C.UPDATE_URL, null, R.string.update_url, TextKind.Url, R.string.st_update_url_summary),
                )
            ),
            SettingsGroupSpec(
                R.string.st_group_project, listOf(
                    ActionRow("project_page", SettingAction.OpenProjectPage, R.string.st_project_page, R.string.st_project_page_summary, LucideIcons.Code),
                )
            ),
        ),
    )

    /** The home screen's categories in the design's three groups (plan 7.7, design B-Settings). */
    val homeGroups: List<Pair<Int, List<SettingsPageSpec>>> = listOf(
        R.string.st_home_group_watch to listOf(adBlock, playback, chat, downloads),
        R.string.st_home_group_app to listOf(appearance, notifications, backup),
        R.string.st_home_group_other to listOf(advanced, about),
    )

    val categories: List<SettingsPageSpec> = homeGroups.flatMap { it.second }

    val pages: List<SettingsPageSpec> = listOf(
        appearance, playback, playerButtons, playerMenu, chat, adBlock, videoSwap, customProxy, streamProxy,
        downloads, notifications, backup, advanced, buffer, apiTokens, debug, about,
    )

    fun page(id: PageId): SettingsPageSpec = pages.first { it.id == id }

    /** Every stored setting with the page it is on. */
    val stored: List<Pair<SettingsPageSpec, StoredSetting>> =
        pages.flatMap { page -> page.items.filterIsInstance<StoredSetting>().map { page to it } }

    fun find(id: String): Pair<SettingsPageSpec, SettingItem>? =
        pages.firstNotNullOfOrNull { page -> page.items.firstOrNull { it.id == id }?.let { page to it } }

    const val DYNAMIC_LANGUAGE = "language"
    const val DYNAMIC_NETWORK = "network"
    const val DYNAMIC_TRANSLATE = "translate"

    /**
     * v0.1 entries that are not on any page any more, with the reason (plan 7.7: the removal list goes into the U3
     * PR). Their stored values stay where they are; nothing is deleted from storage.
     */
    val removed: Map<String, String> = mapOf(
        C.THEME to "dark only (U1, plan 4 and 11); hidden since U1, nothing reads it",
        C.UI_THEME_FOLLOW_SYSTEM to "dark only (U1); hidden since U1, nothing reads it",
        C.UI_THEME_DARK_ON to "dark only (U1); hidden since U1, nothing reads it",
        C.UI_THEME_DARK_OFF to "dark only (U1); hidden since U1, nothing reads it",
        C.UI_THEME_ROUNDED_CORNERS to "one corner style (plan 4); hidden since U1, nothing reads it",
        C.UI_THEME_REDUCED_PADDING to "one density (plan 4); hidden since U1, nothing reads it",
        C.UI_THEME_COMPACT_TEXT to "one type scale (plan 4); hidden since U1, nothing reads it",
        C.UI_THEME_MATERIAL3 to "one design system (U1); hidden since U1, nothing reads it",
        C.UI_THEME_BOTTOM_NAV_COLOR to "the floating pill has one colour (U2); nothing reads it",
        C.UI_THEME_APPBAR_LIFT to "hidden since U1; the old pages still read it (default on) until they are rewritten",
        C.UI_FOLLOW_PAGER to "the Following page is one Compose page with segments (U2); nothing reads it",
        "ad_block_preset" to "was a second copy of the switches; the page now works the preset out from them (AdBlockPreset.match)",
        "theme_settings" to "link to the removed theme page",
    )
}
