#!/usr/bin/env python3
"""Writes docs/ui-overhaul/u3-settings-map.md: where every v0.1 preference entry went in the U3 settings pages.

Reads the v0.1 preference XML kept in app/src/test/resources/legacy-preferences and the catalogue
(ui/settings/model/SettingsCatalog.kt); page and group names come from the English strings.

    python tools/ui-inventory/settings_map.py
"""
import glob
import os
import re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
APP = os.path.join(ROOT, "app", "src")
SRC = os.path.join(APP, "main", "java", "com", "github", "andreyasadchy", "xtra")
OUT = os.path.join(ROOT, "docs", "ui-overhaul", "u3-settings-map.md")


def strings():
    names = {}
    for f in glob.glob(os.path.join(APP, "main", "res", "values", "*.xml")):
        for k, v in re.findall(r'<string name="(\w+)"[^>]*>(.*?)</string>', open(f, encoding="utf-8").read(), re.S):
            names[k] = v.replace("\\'", "'").replace('\\"', '"')
    return names


def main():
    s = strings()
    consts = dict(re.findall(r'const val (\w+)\s*=\s*"([^"]*)"', open(os.path.join(SRC, "util", "C.kt"), encoding="utf-8").read()))
    cat = open(os.path.join(SRC, "ui", "settings", "model", "SettingsCatalog.kt"), encoding="utf-8").read()

    # page val -> title; walk the file keeping the current page and group
    location = {}
    page_titles = {}
    page = group = None
    pending_group = False
    for line in cat.splitlines():
        m = re.match(r"\s*val (\w+) = SettingsPageSpec\(", line)
        if m:
            page, group = m.group(1), None
            continue
        m = re.match(r"\s*title = R\.string\.(\w+),", line)
        if m and page and page not in page_titles:
            page_titles[page] = s.get(m.group(1), m.group(1))
        m = re.search(r"SettingsGroupSpec\(\s*(?:R\.string\.(\w+)|null)?", line)
        if m:
            pending_group = m.group(1) is None and line.rstrip().endswith("(")
            if not pending_group:
                group = s.get(m.group(1), "") if m.group(1) else ""
        elif pending_group:
            m = re.match(r"\s*(?:R\.string\.(\w+)|null),", line)
            group = s.get(m.group(1), "") if (m and m.group(1)) else ""
            pending_group = False
        key = None
        m = re.search(r"(?:Toggle|Choice|Slider|Text|TabOrder)Setting\((?:C\.(\w+)|\"([^\"]+)\")", line)
        if m:
            key = consts.get(m.group(1)) if m.group(1) else m.group(2)
        m = re.search(r"(?:ActionRow|PageLink)\(\"([^\"]+)\"", line)
        if m:
            key = m.group(1)
        if key and page and key not in location:
            location[key] = (page, group)
        if line.startswith("    val adBlockLayers"):
            page, group = "adBlock", s["st_group_ad_layers"]
    removed = {}
    for c, lit, reason in re.findall(r'(?:C\.(\w+)|"(\w+)") to "([^"]+)"', cat.split("val removed")[1]):
        removed[consts.get(c) if c else lit] = reason
    # the v0.1 rows that only opened a page
    links = {
        "theme_settings": None, "ui_settings": "appearance", "chat_settings": "chat", "player_settings": "playback",
        "player_button_settings": "playerButtons", "buffer_settings": "buffer", "ad_block_settings": "adBlock",
        "playback_settings": "advanced", "api_token_settings": "apiTokens", "download_settings": "downloads",
        "update_settings": "about", "debug_settings": "debug", "player_menu_settings": "playerMenu",
        "ad_block_advanced": "adBlock", "ad_block_stats": "adBlock",
    }
    tab_dialogs = {k + "_dialog": k for k in ["ui_navigation_tab_list", "ui_following_tabs", "ui_saved_tabs", "ui_channel_tabs", "ui_game_tabs", "ui_search_tabs"]}

    rows = []
    for f in sorted(glob.glob(os.path.join(APP, "test", "resources", "legacy-preferences", "*.xml"))):
        for m in re.finditer(r'<([\w.]+)\s([^>]*?)/?>', open(f, encoding="utf-8").read(), re.S):
            a = dict(re.findall(r'(?:android|app):(\w+)="([^"]*)"', m.group(2)))
            if "key" not in a:
                continue
            key, tag = a["key"], m.group(1).split(".")[-1]
            title = s.get(a.get("title", "").replace("@string/", ""), "")
            if key in removed:
                where, state = "removed: " + removed[key], "removed"
            elif tag in ("Preference", "PreferenceCategory") and key in links:
                p = links[key]
                where, state = ("page link, now the page " + page_titles.get(p, p)) if p else "removed (theme page)", "moved"
                if key == "ad_block_stats":
                    where, state = "the status card at the top of Ad blocking", "moved"
                if key == "ad_block_advanced":
                    where, state = "group Advanced on Ad blocking", "moved"
            elif key in tab_dialogs:
                p, g = location[tab_dialogs[key]]
                where, state = f"{page_titles[p]} › {g} (sheet, key {tab_dialogs[key]})", "moved"
            elif key in location:
                p, g = location[key]
                where, state = f"{page_titles[p]} › {g}" if g else page_titles[p], "kept"
            else:
                where, state = "NOT FOUND", "?"
            rows.append((os.path.basename(f).replace("_preferences.xml", ""), key, tag.replace("Preference", "").replace("Compat", "") or "link", a.get("defaultValue", ""), title, state, where))

    counts = {}
    for r in rows:
        counts[r[5]] = counts.get(r[5], 0) + 1
    with open(OUT, "w", encoding="utf-8", newline="\n") as out:
        out.write("# U3 settings map\n\nGenerated by `tools/ui-inventory/settings_map.py`. Every v0.1 preference entry (the U0 inventory's 225 plus "
                  "`ui_glass_effects` from stage G) and where it is now. Keys, storage types and defaults are unchanged for every kept "
                  "entry (`SettingsCatalogTest`); removed entries keep their stored value, nothing is deleted from storage.\n\n")
        out.write(" · ".join(f"{k}: {v}" for k, v in sorted(counts.items())) + "\n\n")
        out.write("| v0.1 page | Key | Type | Default | Title | State | Now |\n|---|---|---|---|---|---|---|\n")
        for r in rows:
            out.write("| " + " | ".join(c.replace("|", "\\|").replace("\n", " ") for c in r) + " |\n")
    print(counts)


if __name__ == "__main__":
    main()
