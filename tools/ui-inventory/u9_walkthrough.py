#!/usr/bin/env python3
"""Sub-screen walkthrough (U9 plan section 5 and 4.5 items 7-8).

Walks the surfaces listed in docs/ui-overhaul/u9/surfaces.md on a connected emulator, takes one screenshot per
surface and records logcat for the whole run, then scans the log for the crash patterns of plan 4.5 item 8.
Run it before and after a change with the same arguments; the shot names are the surface ids' short names.

    python u9_walkthrough.py <out dir> [--package io.github.mitchell0402.lagoon.qa] [--only regex] [--landscape]
                                       [--tablet] [--list]

The app must be logged in (snapshot lagoon-v01-fulldata plus a qa overlay install). The walk never sends chat,
follows, votes or redeems anything (plan section 5). Steps that cannot find their target are reported as SKIP
with the reason and the walk goes on, so one moved button does not hide the rest of the run.
"""
import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB") or os.path.expanduser("~/AppData/Local/Android/Sdk/platform-tools/adb.exe")
ENV = dict(os.environ, MSYS_NO_PATHCONV="1")
CRASH_PATTERNS = [
    "ClassNotFoundException", "NoClassDefFoundError", "NoSuchMethodError", "NoSuchFieldError",
    "Resources$NotFoundException", "InflateException", "Failed to instantiate custom view inflater", "FATAL EXCEPTION",
]
CHANNEL = os.environ.get("U9_CHANNEL", "cohhcarnage")
VOD_CHANNEL = os.environ.get("U9_VOD_CHANNEL", "cohhcarnage")


class Skip(Exception):
    pass


class Driver:
    def __init__(self, out, package):
        self.out = out
        self.package = package
        self.prefix = ""
        self.results = []
        os.makedirs(out, exist_ok=True)

    # adb plumbing
    def adb(self, *args, binary=False):
        r = subprocess.run([ADB, *args], capture_output=True, env=ENV)
        return r.stdout if binary else r.stdout.decode("utf-8", "replace")

    def sh(self, cmd):
        return self.adb("shell", cmd)

    def nodes(self):
        for _ in range(5):
            out = self.adb("exec-out", "uiautomator", "dump", "/dev/tty")
            start, end = out.find("<?xml"), out.rfind("</hierarchy>")
            if start != -1 and end != -1:
                return list(ET.fromstring(out[start:end + len("</hierarchy>")]).iter("node"))
            time.sleep(1)
        return []

    @staticmethod
    def bounds(node):
        return tuple(map(int, re.findall(r"\d+", node.get("bounds"))))

    def find(self, pattern, nodes=None):
        rx = re.compile(f"^(.*[:/])?(?:{pattern})$", re.I | re.S)
        hits = []
        for n in nodes if nodes is not None else self.nodes():
            if any(rx.search(n.get(k) or "") for k in ("text", "content-desc", "resource-id")):
                x1, y1, x2, y2 = self.bounds(n)
                if x2 > x1 and y2 > y1:
                    hits.append(n)
        return hits

    def exists(self, pattern):
        return bool(self.find(pattern))

    def wait_for(self, pattern, timeout=8.0):
        end = time.time() + timeout
        while time.time() < end:
            if self.find(pattern):
                return True
            time.sleep(0.5)
        return False

    def xy(self, pattern, index=0, nodes=None):
        hits = self.find(pattern, nodes)
        if len(hits) <= index:
            raise Skip(f"no node matches {pattern!r}")
        x1, y1, x2, y2 = self.bounds(hits[index])
        return (x1 + x2) // 2, (y1 + y2) // 2

    def tap_xy(self, x, y, wait=1.2):
        self.sh(f"input tap {x} {y}")
        time.sleep(wait)

    def tap(self, pattern, index=0, wait=1.2):
        self.tap_xy(*self.xy(pattern, index), wait=wait)

    def long(self, pattern, index=0, wait=1.5):
        x, y = self.xy(pattern, index)
        self.sh(f"input swipe {x} {y} {x} {y} 900")
        time.sleep(wait)

    def swipe(self, x1, y1, x2, y2, ms=300, wait=1.0):
        self.sh(f"input swipe {x1} {y1} {x2} {y2} {ms}")
        time.sleep(wait)

    def key(self, code, wait=1.0):
        self.sh(f"input keyevent {code}")
        time.sleep(wait)

    def back(self, n=1):
        for _ in range(n):
            self.key(4, wait=0.8)

    def text(self, value):
        self.sh("input text " + value.replace(" ", "%s"))
        time.sleep(0.8)

    def size(self):
        out = self.sh("wm size")
        m = re.findall(r"(\d+)x(\d+)", out)
        w, h = map(int, m[-1])
        rot = self.sh("dumpsys input | grep -m1 SurfaceOrientation")
        if re.search(r"SurfaceOrientation:\s*[13]", rot):
            w, h = h, w
        return w, h

    def shot(self, name):
        path = os.path.join(self.out, self.prefix + name + ".png")
        with open(path, "wb") as f:
            f.write(self.adb("exec-out", "screencap", "-p", binary=True))
        return path

    # app helpers
    def start(self):
        self.sh(f"am start -W -n {self.package}/com.github.andreyasadchy.xtra.ui.main.MainActivity")
        time.sleep(3)

    def stop(self):
        self.sh(f"am force-stop {self.package}")
        time.sleep(1)

    def clear_player_restore(self):
        # the qa app restores its last stream on cold start; stop it so every walk starts on the shell
        self.sh(f"am force-stop {self.package}")

    def link(self, url, wait=4.0):
        self.sh(f"am start -a android.intent.action.VIEW -d {url} {self.package}")
        time.sleep(wait)

    def close_player(self):
        """Closes the player if one is open (maximized or mini)."""
        for _ in range(3):
            nodes = self.nodes()
            if not self.find(".*playerContainer", nodes) or not self.find(".*playerVideo", nodes):
                return
            if self.find("Minimize", nodes) or self.find("Pause|Play", nodes):
                pass
            x1, y1, x2, y2 = self.bounds(self.find(".*playerVideo", nodes)[0])
            w, h = self.size()
            if y2 - y1 > h * 0.15 and x2 - x1 > w * 0.5:
                self.back()
                continue
            # mini player: swipe it off to the right
            self.swipe((x1 + x2) // 2, (y1 + y2) // 2, w - 5, (y1 + y2) // 2, 200, wait=1.5)

    def player_control(self, desc):
        """Shows the player controls and taps one in the same dump (controls hide after a few seconds)."""
        nodes = self.nodes()
        if not self.find(desc, nodes):
            video = self.find(".*playerVideo", nodes)
            if not video:
                raise Skip("no player")
            x1, y1, x2, y2 = self.bounds(video[0])
            self.tap_xy((x1 + x2) // 2, y1 + (y2 - y1) // 4, wait=0.3)
            nodes = self.nodes()
        self.tap_xy(*self.xy(desc, nodes=nodes))

    def scroll_down(self, frac=0.5):
        w, h = self.size()
        self.swipe(w // 2, int(h * 0.75), w // 2, int(h * (0.75 - frac)), 400)

    def scroll_to(self, pattern, tries=8):
        for _ in range(tries):
            if self.exists(pattern):
                return
            self.scroll_down()
        raise Skip(f"{pattern!r} not found after scrolling")

    def run(self, name, fn):
        t = time.time()
        try:
            fn(self)
            self.results.append((name, "OK", ""))
            status = "OK"
        except Skip as e:
            self.results.append((name, "SKIP", str(e)))
            status = f"SKIP {e}"
        except Exception as e:  # noqa: BLE001 - keep walking, report at the end
            self.results.append((name, "ERROR", repr(e)))
            status = f"ERROR {e!r}"
        print(f"{name:40s} {status} ({time.time() - t:.0f}s)", flush=True)


# ---------------------------------------------------------------- steps
# Each step leaves the app where the next one expects it (mostly: the shell, no sheet open). Names are the short
# names used for the shot files and in surfaces.md.

def home(d):
    d.back(3)
    d.close_player()
    d.start()


def s_following(d):
    d.close_player()
    for tab in ("Live", "Videos", "Channels", "Games"):
        d.tap(tab)
        time.sleep(1.5)
        d.shot(f"following-{tab.lower()}")
    d.tap("Live")


def s_following_sheets(d):
    d.tap("Videos")
    time.sleep(1.5)
    if d.exists("Sort.*|Newest.*|Upload date|Date.*"):
        d.tap("Sort.*|Newest.*|Upload date|Date.*")
        d.shot("following-video-sort")
        d.back()
    d.tap("Channels")
    time.sleep(1.5)
    if d.exists("Sort.*|Last broadcast.*|Followed.*"):
        d.tap("Sort.*|Last broadcast.*|Followed.*")
        d.shot("following-channel-sort")
        d.back()
    d.tap("Live")


def s_following_longpress(d):
    d.tap("Live")
    time.sleep(1.5)
    w, h = d.size()
    d.sh(f"input swipe {w // 2} {int(h * 0.35)} {w // 2} {int(h * 0.35)} 900")
    time.sleep(1.5)
    d.shot("following-live-longpress")
    d.back()


def s_account_sheet(d):
    d.tap("Account and settings")
    d.shot("following-account-sheet")
    d.back()


def s_browse(d):
    d.tap("Browse")
    time.sleep(2.5)
    d.shot("browse")
    w, h = d.size()
    d.sh(f"input swipe {w // 4} {int(h * 0.45)} {w // 4} {int(h * 0.45)} 900")
    time.sleep(1.5)
    d.shot("browse-longpress")
    d.back()


def s_browse_filter(d):
    d.tap("Browse")
    time.sleep(1)
    d.tap("Tags")
    d.shot("browse-filter")
    d.back()


def s_game(d):
    d.tap("Browse")
    time.sleep(1.5)
    w, h = d.size()
    d.tap_xy(w // 4, int(h * 0.45), wait=3)
    d.shot("game")
    nodes = d.nodes()
    for tab in ("Videos", "Clips"):
        if d.find(tab, nodes):
            d.tap(tab)
            time.sleep(2)
            d.shot(f"game-{tab.lower()}")
    if d.exists("Filter.*|Filters|Sort.*"):
        d.tap("Filter.*|Filters|Sort.*")
        d.shot("game-filter")
        d.back()
    d.back()


def s_top(d):
    d.tap("Browse")
    time.sleep(1)
    if not d.exists("Top streams|Live|Top"):
        raise Skip("no top streams entry")
    d.tap("Top streams|Live|Top")
    time.sleep(2.5)
    d.shot("top-streams")
    d.back()


def s_me(d):
    d.tap("Me")
    time.sleep(2)
    d.shot("me")
    for tab in ("Downloads", "Bookmarks", "Saved"):
        if d.exists(tab):
            d.tap(tab)
            time.sleep(1.5)
            d.shot(f"me-{tab.lower()}")
    if d.exists("More options|More|Menu"):
        d.tap("More options|More|Menu")
        d.shot("me-menu")
        d.back()


def s_search(d):
    d.tap("Search")
    time.sleep(1.5)
    d.shot("search-empty")
    d.text("cohh")
    time.sleep(3)
    d.shot("search-results-keyboard")
    d.key(111)  # escape: hide keyboard
    d.back()
    for tab in ("Live", "Videos", "Channels", "Games"):
        if d.exists(tab):
            d.tap(tab)
            time.sleep(2)
            d.shot(f"search-{tab.lower()}")
    if d.exists("Get user by.*"):
        d.tap("Get user by.*")
        d.shot("search-user-lookup")
        d.back()
    d.back(2)


def s_channel(d):
    d.link(f"https://www.twitch.tv/{CHANNEL}/videos", wait=5)
    d.close_player()
    d.shot("channel")
    for tab in ("Videos", "Clips", "About", "Chat"):
        if d.exists(tab):
            d.tap(tab)
            time.sleep(2)
            d.shot(f"channel-{tab.lower()}")
    if d.exists("More options|More"):
        d.tap("More options|More")
        d.shot("channel-more-sheet")
        d.back()
    d.back(2)


def s_settings(d):
    d.tap("Account and settings")
    if not d.exists("Settings"):
        raise Skip("no settings entry in the account sheet")
    d.tap("Settings")
    time.sleep(2)
    d.shot("settings-home")
    w, h = d.size()
    seen = []
    for _ in range(6):
        nodes = d.nodes()
        rows = [n for n in nodes if (n.get("text") or "") and n.get("clickable") != "false"]
        rows = [n.get("text") for n in nodes if n.get("text") and d.bounds(n)[1] > h * 0.12]
        new = [r for r in rows if r not in seen and len(r) < 30]
        if not new:
            break
        seen += new
        d.scroll_down(0.4)
    d.shot("settings-home-bottom")
    d.back()


def s_settings_pages(d):
    # (page on the settings home, sub-pages reached from it); 16 PageIds in all
    pages = [("Appearance", []), ("Playback", ["Player buttons"]), ("Chat", []),
             ("Ad blocking", ["Video swap settings", "Custom proxy settings", "HTTP proxy settings"]),
             ("Downloads and recording", []), ("Notifications", []), ("Backup and restore", []),
             ("Advanced", ["Buffer settings", "API token settings", "Debug"]), ("About and updates", [])]
    w, h = d.size()

    def to_top():
        for _ in range(3):
            d.swipe(w // 2, int(h * 0.3), w // 2, int(h * 0.9), 150, wait=0.4)

    def visit(p):
        to_top()
        try:
            d.scroll_to(p, tries=10)
            d.tap(p, wait=2)
        except Skip as e:
            d.results.append((f"settings:{p}", "SKIP", str(e)))
            return False
        safe = re.sub(r"[^a-z]+", "-", p.lower()).strip("-")
        d.shot(f"settings-{safe}")
        d.scroll_down(0.6)
        d.shot(f"settings-{safe}-2")
        return True

    d.tap("Account and settings")
    d.tap("Settings")
    time.sleep(2)
    for page, subs in pages:
        if not visit(page):
            continue
        for sub in subs:
            if visit(sub):
                d.back()
                time.sleep(1)
        d.back()
        time.sleep(1)
    d.back()


def open_live(d):
    """Opens the first live stream on Following > Live (the snapshot account follows a few big channels)."""
    if d.exists(".*playerVideo") and d.exists(".*chatFragmentContainer"):
        return
    d.tap("Following", wait=1)
    d.tap("Live", wait=2.5)
    nodes = d.nodes()
    badges = [n for n in nodes if re.fullmatch(r"[\d.,]+[KM]?", n.get("content-desc") or "")]
    if not badges:
        raise Skip("no live stream on Following")
    x1, y1, x2, y2 = d.bounds(badges[0])
    d.tap_xy(x2 + 300, y2 + 150, wait=6)
    if not d.wait_for(".*playerVideo", 10):
        raise Skip("player did not open")
    time.sleep(4)


def menu_item(d, item):
    if not d.exists("Quality"):
        d.player_control("Playback settings")
    w, h = d.size()
    for _ in range(4):
        if d.exists(item):
            break
        d.swipe(w // 2, int(h * 0.92), w // 2, int(h * 0.62), 500)  # scroll inside the sheet
    d.tap(item, wait=2)


def s_player(d):
    open_live(d)
    d.shot("player")
    d.player_control("Playback settings")
    d.shot("player-menu")
    for item in ("Quality", "Sleep timer", "Volume", "Ad blocking|Ads?", "Aspect.*|Resize.*"):
        nodes = d.nodes()
        if not d.find(item, nodes):
            d.results.append((f"player-menu:{item}", "SKIP", "not in menu"))
            continue
        d.tap(item)
        safe = re.sub(r"[^a-z]+", "-", item.split("|")[0].lower()).strip("-")
        d.shot(f"player-menu-{safe}")
        if item == "Sleep timer" and d.exists("At a time.*|At time.*|Pick.*time.*|At .*"):
            d.tap("At a time.*|At time.*|Pick.*time.*|At .*", wait=2)
            d.shot("player-menu-sleep-at")
            d.back()
            if not d.exists("Quality"):
                d.player_control("Playback settings")
            continue
        d.back()
        if not d.exists("Quality"):
            d.player_control("Playback settings")
    d.back()


def s_player_sheets(d):
    open_live(d)
    for item in ("Record", "Viewers"):
        try:
            menu_item(d, item)
        except Skip:
            d.results.append((f"player:{item}", "SKIP", "no menu item"))
            d.back()
            continue
        d.shot(f"player-{item.lower()}")
        d.back(2)
    # C3: "Find unlisted video" opens a VOD page (may find nothing for some channels)
    try:
        menu_item(d, "Find unlisted video")
        time.sleep(4)
        d.shot("player-unlisted-video")
    except Skip:
        d.results.append(("player:unlisted", "SKIP", "no menu item"))
    d.back(2)


def s_chat(d):
    open_live(d)
    if not d.exists(".*chatFragmentContainer"):
        raise Skip("no chat")
    d.shot("chat")
    nodes = d.nodes()
    rows = [n for n in nodes if ":" in (n.get("text") or "") and d.bounds(n)[1] > 1100]
    if rows:
        x1, y1, x2, y2 = d.bounds(rows[-1])
        d.tap_xy(x1 + 60, (y1 + y2) // 2, wait=2)
        d.shot("chat-user-card")
        d.back()
    for desc in ("Emotes?|Emote picker", "Channel points.*|Points.*", "Chat settings|Room modes?"):
        if d.exists(desc):
            d.tap(desc, wait=2)
            safe = re.sub(r"[^a-z]+", "-", desc.split("|")[0].lower()).strip("-")
            d.shot(f"chat-{safe}")
            d.back()
    edits = [n for n in d.nodes() if n.get("class") == "android.widget.EditText"]
    if edits:
        x1, y1, x2, y2 = d.bounds(edits[-1])
        d.tap_xy((x1 + x2) // 2, (y1 + y2) // 2, wait=2)
        d.shot("chat-keyboard")
        d.back()  # hides the keyboard; nothing is typed or sent
    else:
        d.results.append(("chat:input", "SKIP", "no chat input"))


def s_text_selection(d):
    """R3: text selection toolbar in a sheet's text field vs a plain field (search)."""
    d.tap("Search")
    time.sleep(1)
    d.text("selection")
    w, h = d.size()
    nodes = d.nodes()
    edits = [n for n in nodes if n.get("class") == "android.widget.EditText"]
    if not edits:
        raise Skip("no text field")
    x1, y1, x2, y2 = d.bounds(edits[0])
    d.sh(f"input swipe {x1 + 80} {(y1 + y2) // 2} {x1 + 80} {(y1 + y2) // 2} 1000")
    time.sleep(1.5)
    d.shot("selection-plain-field")
    d.back()  # the selection toolbar
    d.back()  # the keyboard
    if not d.exists("Get user by.*"):
        d.tap("Search")
    if d.exists("Get user by.*"):
        d.tap("Get user by.*")
        d.text("selection")
        nodes = d.nodes()
        edits = [n for n in nodes if n.get("class") == "android.widget.EditText"]
        if edits:
            x1, y1, x2, y2 = d.bounds(edits[-1])
            d.sh(f"input swipe {x1 + 80} {(y1 + y2) // 2} {x1 + 80} {(y1 + y2) // 2} 1000")
            time.sleep(1.5)
            d.shot("selection-sheet-field")
        d.back(3)
    d.back(2)


STEPS = [
    ("following", s_following),
    ("following-sheets", s_following_sheets),
    ("following-longpress", s_following_longpress),
    ("account-sheet", s_account_sheet),
    ("browse", s_browse),
    ("browse-filter", s_browse_filter),
    ("game", s_game),
    ("top", s_top),
    ("me", s_me),
    ("search", s_search),
    ("text-selection", s_text_selection),
    ("channel", s_channel),
    ("settings", s_settings),
    ("settings-pages", s_settings_pages),
    ("player", s_player),
    ("player-sheets", s_player_sheets),
    ("chat", s_chat),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--package", default="io.github.mitchell0402.lagoon.qa")
    ap.add_argument("--only")
    ap.add_argument("--landscape", action="store_true")
    ap.add_argument("--tablet", action="store_true", help="wm size 2560x1600 + density 320 (tablet landscape)")
    ap.add_argument("--list", action="store_true")
    args = ap.parse_args()
    if args.list:
        for name, _ in STEPS:
            print(name)
        return
    d = Driver(args.out, args.package)
    form = "phone-portrait"
    # Rotation: the emulator snapshot has the orientation sensor off, and Android 14+ ignores user_rotation for apps,
    # so landscape is a landscape-shaped display instead (same dp as the phone rotated: 997 x 448 at 480 dpi; the
    # status bar stays on the long edge and the cutout is not on a side - check those on a phone).
    if args.tablet:
        d.sh("wm size 2560x1600")
        d.sh("wm density 320")
        form = "tablet"
    elif args.landscape:
        d.sh("wm size 2992x1344")
        form = "phone-landscape"
    time.sleep(2)
    d.prefix = form + "_"
    log_path = os.path.join(args.out, f"logcat-{form}.txt")
    d.adb("logcat", "-c")
    log = open(log_path, "wb")
    logcat = subprocess.Popen([ADB, "logcat", "-v", "threadtime"], stdout=log, stderr=subprocess.STDOUT, env=ENV)
    try:
        d.stop()
        d.start()
        for name, fn in STEPS:
            if args.only and not re.search(args.only, name):
                continue
            d.run(name, fn)
            d.run(f"{name}:home", home)
    finally:
        logcat.terminate()
        log.close()
        if args.tablet or args.landscape:
            d.sh("wm size reset")
            d.sh("wm density reset")
    with open(log_path, encoding="utf-8", errors="replace") as f:
        crashes = [line.rstrip() for line in f if any(p in line for p in CRASH_PATTERNS)]
    with open(os.path.join(args.out, f"result-{form}.txt"), "w", encoding="utf-8") as f:
        for name, status, why in d.results:
            f.write(f"{name}\t{status}\t{why}\n")
        f.write(f"\ncrash lines: {len(crashes)}\n")
        f.writelines(c + "\n" for c in crashes)
    print(f"\ncrash lines: {len(crashes)}")
    for c in crashes[:20]:
        print("  " + c)
    sys.exit(1 if crashes else 0)


if __name__ == "__main__":
    main()
