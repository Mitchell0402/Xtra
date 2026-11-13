#!/usr/bin/env bash
# Captures the main screens of an installed Lagoon build for the screen archive (plan U0: "v0.1 各界面截图留档").
# Usage: capture_screens.sh <package> <output dir>     (ANDROID_SERIAL selects the device)
# Anonymous session: pages that need a login show their logged-out state.
set -u
P=$1
S=$2
export PYTHONIOENCODING=utf-8 MSYS_NO_PATHCONV=1
A=${ADB_EXE:-/c/Users/Mitchell/AppData/Local/Android/Sdk/platform-tools/adb.exe}
U="env -u ADB python $(dirname "$0")/adbui.py"
shot() { $U shot "$S" "$1" >/dev/null; echo "$1"; }
tap() { $U tap "$@" >/dev/null 2>&1; sleep 2.5; }
link() { $U link "$1" "$P" >/dev/null; sleep 4; }
back() { $A shell input keyevent 4; sleep 1.5; }

$A shell am start -n "$P/com.github.andreyasadchy.xtra.ui.main.MainActivity" >/dev/null; sleep 4
tap "Following"; shot 01-following-live
tap "Games" 0; shot 02-following-games
tap "Channels"; shot 03-following-channels
tap "Games" 1; sleep 2; shot 04-games
link "https://www.twitch.tv/directory/all"; shot 05-top-streams
link "https://www.twitch.tv/directory/category/just-chatting"; shot 06-game-streams
tap "Videos"; shot 07-game-videos
tap "Clips"; shot 08-game-clips
link "https://www.twitch.tv/jokerdtv"; shot 09-channel-videos
tap "Clips"; shot 10-channel-clips
tap "About"; shot 11-channel-about
tap "Suggestions"; shot 12-channel-suggestions
link "https://www.twitch.tv/team/orbit"; shot 13-team
tap "Search"; sleep 1; $A shell input text "cohh"; sleep 3; shot 14-search
back; back
tap "Saved"; shot 15-saved-downloads
tap "Bookmarks"; shot 16-saved-bookmarks
tap "Filters"; shot 17-saved-filters
tap "Following"
tap "More options" 0; shot 18-overflow-menu
tap "Settings"; sleep 2; shot 19-settings-root
for page in "Theme" "Interface" "Chat" "Player" "Playback" "Buffer" "Ad blocking" "Downloads" "Updates" "API token" "Debug"; do
  if $U tap "$page" >/dev/null 2>&1; then sleep 2; shot "20-settings-$(echo "$page" | tr ' A-Z' '-a-z')"; back; else echo "no settings entry $page"; fi
done
back
tap "More options" 0; tap "Log in"; sleep 4; shot 30-login
back
