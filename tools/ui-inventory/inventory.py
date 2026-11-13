#!/usr/bin/env python3
"""Generates docs/ui-overhaul/feature-inventory.md, the app-wide feature list every UI stage claims items from
(plan section 7.8 and 10.8). It is built from the code, not from the plan:

  - navigation destinations (nav_graph.xml) and activities
  - settings pages (settings_nav_graph.xml) with every preference: key, type, default, title
  - dialog classes, alert dialogs per file, option / popup / context menus with their items
  - background components (services, receivers, workers)

Each row gets the stage that rewrites it (owner rules below) and an empty "done" box that the stage ticks.
Run from the repository root: python tools/ui-inventory/inventory.py
"""
import glob
import os
import re
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "app/src/main/res")
SRC = os.path.join(ROOT, "app/src/main/java/com/github/andreyasadchy/xtra")
A = "{http://schemas.android.com/apk/res/android}"
APP = "{http://schemas.android.com/apk/res-auto}"
OUT = os.path.join(ROOT, "docs/ui-overhaul/feature-inventory.md")

# package / file -> stage that rewrites it (plan sections 7 and 9)
OWNERS = [
    (r"fragment_media_pager", "U2"),  # top_menu is the shared top bar, rebuilt with the shell
    (r"ui/chat/|Chat", "U7"),
    (r"ui/player/|Player|SleepTimer|Volume|IntegrityDialog_player", "U6"),
    (r"ui/settings/|ui/login/|IntegrityDialog|Settings|Login|activity_settings", "U3"),
    (r"ui/games?/|ui/search/|GamesSort|SearchTags|SelectLanguages|StreamsSort|ui/top/|fragment_search", "U4"),
    (r"ui/channel/|ui/team/|ui/saved/|ui/download/|ui/bookmarks?/|ui/filters?/|Bookmark|Download|Videos|Clips|Filters|VideosSort|fragment_channel|fragment_team", "U5"),
    (r"ui/following/|ui/main/|FollowedChannelsSort", "U2"),
]


def owner(path):
    for pattern, stage in OWNERS:
        if re.search(pattern, path):
            return stage
    return "U8"


def strings():
    s = {}
    for e in ET.parse(os.path.join(RES, "values/strings.xml")).getroot():
        if e.tag == "string":
            s[e.get("name")] = "".join(e.itertext()).replace("\\'", "'").strip()
    return s


STR = strings()


def text(v):
    if v and v.startswith("@string/"):
        return STR.get(v[8:], v)
    return v or ""


def find_file(cls):
    name = cls.split(".")[-1].split("$")[0]
    hits = glob.glob(os.path.join(SRC, "**", name + ".kt"), recursive=True)
    return os.path.relpath(hits[0], ROOT).replace("\\", "/") if hits else ""


def nav_destinations():
    rows = []
    for e in ET.parse(os.path.join(RES, "navigation/nav_graph.xml")).getroot():
        if e.tag in ("fragment", "dialog"):
            cls = e.get(A + "name")
            f = find_file(cls)
            args = [a.get(A + "name") for a in e if a.tag == "argument"]
            rows.append((e.get(A + "id").split("/")[-1], cls.split(".")[-1], f, ", ".join(args), owner(f)))
    return rows


def settings_pages():
    pages = []
    for e in ET.parse(os.path.join(RES, "navigation/settings_nav_graph.xml")).getroot():
        if e.tag == "fragment":
            pages.append((e.get(A + "id").split("/")[-1], e.get(A + "name").split(".")[-1]))
    return pages


def preferences(xml_name):
    rows = []
    path = os.path.join(RES, "xml", xml_name)
    if not xml_name or not os.path.exists(path):
        return rows
    for e in ET.parse(path).getroot().iter():
        key = e.get(APP + "key") or e.get(A + "key")
        if not key:
            continue
        typ = e.tag.split(".")[-1]
        default = e.get(APP + "defaultValue") or e.get(A + "defaultValue") or ""
        title = text(e.get(APP + "title") or e.get(A + "title"))
        rows.append((key, typ, default, title))
    return rows


def menus():
    rows = []
    for f in sorted(glob.glob(os.path.join(RES, "menu/*.xml"))):
        items = [text(i.get(A + "title")) or i.get(A + "id", "").split("/")[-1] for i in ET.parse(f).getroot().iter("item")]
        users = [os.path.relpath(p, ROOT).replace("\\", "/") for p in glob.glob(os.path.join(SRC, "**/*.kt"), recursive=True)
                 if "R.menu." + os.path.basename(f)[:-4] in open(p, encoding="utf-8").read()]
        users += [os.path.relpath(p, ROOT).replace("\\", "/") for p in glob.glob(os.path.join(RES, "layout*/*.xml"))
                  if "@menu/" + os.path.basename(f)[:-4] + '"' in open(p, encoding="utf-8").read()]
        rows.append((os.path.basename(f)[:-4], ", ".join(items), ", ".join(os.path.basename(u) for u in users), owner(" ".join(users))))
    return rows


def dialogs():
    rows = []
    for p in sorted(glob.glob(os.path.join(SRC, "**/*.kt"), recursive=True)):
        src = open(p, encoding="utf-8").read()
        rel = os.path.relpath(p, ROOT).replace("\\", "/")
        name = os.path.basename(p)[:-3]
        is_class = re.search(r"class \w+[^{]*:\s*(\w*DialogFragment|ExpandedBottomSheet|BottomSheetDialogFragment)\(", src)
        alerts = src.count("getAlertDialogBuilder()")
        popups = src.count("PopupMenu(")
        if is_class or alerts or popups:
            kind = []
            if is_class:
                kind.append(is_class.group(1))
            if alerts:
                kind.append(f"{alerts} alert dialog(s)")
            if popups:
                kind.append(f"{popups} popup menu(s)")
            rows.append((name, rel, "; ".join(kind), owner(rel)))
    return rows


def manifest():
    rows = []
    root = ET.parse(os.path.join(ROOT, "app/src/main/AndroidManifest.xml")).getroot()
    for e in root.find("application"):
        if e.tag in ("activity", "service", "receiver"):
            rows.append((e.tag, e.get(A + "name")))
    workers = [os.path.basename(p)[:-3] for p in glob.glob(os.path.join(SRC, "**/*.kt"), recursive=True)
               if re.search(r":\s*CoroutineWorker\(", open(p, encoding="utf-8").read())]
    rows += [("worker", w) for w in workers]
    return rows


def main():
    out = ["# Lagoon feature inventory", "",
           "Generated by `tools/ui-inventory/inventory.py` from the code at U0 (v0.1 + U0 guardrails). Every UI stage claims "
           "its rows by stage, ticks them when the rewritten screen keeps the feature, and writes a reason next to anything it "
           "removes on purpose (removals listed in plan section 12 need Mitchell's approval). U8 checks that no row is left "
           "unticked. The owner column follows plan sections 7.8 and 9; a stage may move a row to another stage in its PR.", ""]

    nav = nav_destinations()
    out += [f"## 1. Navigation destinations ({len(nav)})", "", "| Done | Destination | Class | File | Arguments | Stage |", "|---|---|---|---|---|---|"]
    out += [f"| [ ] | `{d}` | {c} | `{f}` | {a} | {s} |" for d, c, f, a, s in nav]
    out += ["", "Outside the navigation graph:", "",
            "| Done | Screen | Where | Stage |", "|---|---|---|---|",
            "| [ ] | Player (live, video, clip, offline; mini player; picture-in-picture) | `MainActivity` playerContainer, `ui/player/` | U6 |",
            "| [ ] | Chat (live and replay) | `ui/chat/ChatFragment` inside the player | U7 |",
            "| [ ] | Settings activity | `ui/settings/SettingsActivity` | U3 |",
            "| [ ] | Login (web login, TV device code login, integrity check) | `ui/login/LoginActivity` | U3 |",
            "| [ ] | Deep links: channel, video with timestamp, clip, game directory | `MainActivity` intent filter (twitch.tv) | U2 |",
            "| [ ] | In-app update check and install | `MainActivity`, `SettingsActivity` | U3 |", ""]

    pages = settings_pages()
    xml_for = {
        "SettingsFragment": "root_preferences.xml", "ThemeSettingsFragment": "theme_preferences.xml", "UiSettingsFragment": "ui_preferences.xml",
        "ChatSettingsFragment": "chat_preferences.xml", "PlayerSettingsFragment": "player_preferences.xml",
        "PlayerButtonSettingsFragment": "player_button_preferences.xml", "PlayerMenuSettingsFragment": "player_menu_preferences.xml",
        "BufferSettingsFragment": "buffer_preferences.xml", "AdBlockSettingsFragment": "ad_block_preferences.xml",
        "PlaybackSettingsFragment": "playback_preferences.xml", "ApiTokenSettingsFragment": "api_token_preferences.xml",
        "DownloadSettingsFragment": "download_preferences.xml", "UpdateSettingsFragment": "update_preferences.xml",
        "DebugSettingsFragment": "debug_preferences.xml",
    }
    total = sum(len(preferences(xml_for.get(c.split("$")[-1], ""))) for _, c in pages)
    out += [f"## 2. Settings ({len(pages)} destinations, {total} preference entries)", "",
            "Restyled in U3. Chat and player-button pages are restyled in U3 and trimmed in U7 / U6 (plan 7.7). Keys read by "
            "ExoPlayerService and player/ads must keep key, type and default (plan 10.1).", ""]
    for dest, cls in pages:
        prefs = preferences(xml_for.get(cls.split("$")[-1], ""))
        out += [f"### `{dest}` ({cls}, {len(prefs)} entries)", ""]
        if prefs:
            out += ["| Done | Key | Type | Default | Title |", "|---|---|---|---|---|"]
            out += [f"| [ ] | `{k}` | {t} | {d} | {ti} |" for k, t, d, ti in prefs]
        else:
            out += ["Built in code (list editor or search page); see the class."]
        out += [""]

    dl = dialogs()
    out += [f"## 3. Dialogs, alert dialogs and popup menus ({len(dl)} files)", "", "| Done | File | Kind | Stage |", "|---|---|---|---|"]
    out += [f"| [ ] | `{r}` | {k} | {s} |" for n, r, k, s in dl]
    out += [""]

    mn = menus()
    out += [f"## 4. Menus ({len(mn)} menu resources)", "", "| Done | Menu | Items | Used by | Stage |", "|---|---|---|---|---|"]
    out += [f"| [ ] | `{m}` | {i} | {u} | {s} |" for m, i, u, s in mn]
    out += [""]

    out += ["## 5. Background features (must keep working through every stage, plan 10.1 red line 4)", "",
            "| Done | Kind | Component |", "|---|---|---|"]
    out += [f"| [ ] | {k} | `{n}` |" for k, n in manifest()]
    out += ["| [ ] | feature | Background audio, picture-in-picture, sleep timer and its screen lock (device admin), media buttons, "
            "live notifications, channel points collection |", ""]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(out))
    print(f"{len(nav)} destinations, {len(pages)} settings pages / {total} entries, {len(dl)} dialog files, {len(mn)} menus -> {OUT}")


if __name__ == "__main__":
    main()
