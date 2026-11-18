#!/usr/bin/env python3
"""Legacy-UI guard (U9 plan section 8, gate 1).

Fails when code brings back an old Material / platform UI piece, a hard-coded colour outside the theme, a sheet height
read straight from the screen, an unregistered layout file, or a sub-screen that is missing from
docs/ui-overhaul/u9/surfaces.md (and the other way round: a surfaces.md row whose code is gone).

    python tools/ci/legacy_ui_guard.py [--root .]

Allowed exceptions live in tools/ci/legacy_ui_allowlist.txt, one per line:  <rule> <path> [<token>]  # reason
A path is relative to the repository root; a trailing / covers a directory; <token> narrows the entry to hits of
that token. Every entry must still match something, so the list cannot go stale.
"""
import argparse
import os
import re
import sys
from collections import defaultdict

APP_SRC = "app/src"
SCAN_EXT = (".kt", ".java")
SURFACES_MD = "docs/ui-overhaul/u9/surfaces.md"
ALLOWLIST = "tools/ci/legacy_ui_allowlist.txt"

# rule "legacy": banned anywhere (code identifiers and string literals; comments do not count)
LEGACY_TOKENS = [
    "MaterialAlertDialogBuilder", "AlertDialog.Builder", "android.app.AlertDialog", "ProgressDialog",
    "MaterialTimePicker", "DatePickerDialog", "TimePickerDialog", "PopupMenu", "ListPopupWindow", "PopupWindow",
    "Spinner", "NumberPicker", "registerForContextMenu", "BottomSheetDialogFragment", "Snackbar",
    "com.google.android.material", "com/google/android/material", "Theme.Material3", "Theme.MaterialComponents",
    "Theme_DeviceDefault", "Theme.DeviceDefault", "Toast.makeText",
]
# rule "compose": Material 3 composables we never use (Lagoon has its own sheet, toast and picker)
COMPOSE_TOKENS = [
    "AlertDialog", "BasicAlertDialog", "DropdownMenu", "ExposedDropdownMenuBox", "SnackbarHost", "DatePicker",
    "TimePicker", "TooltipBox",
]
# rule "m3-import": outside ui/design only these androidx.compose.material3 names may be imported
M3_ALLOWED = {"Text", "Icon", "LocalContentColor", "ripple", "MaterialTheme", "minimumInteractiveComponentSize",
              "ExperimentalMaterial3Api"}
DESIGN_DIR = "app/src/main/java/com/github/andreyasadchy/xtra/ui/design/"
THEME_DIR = DESIGN_DIR + "theme/"


def strip_comments(text, kind):
    """Blanks comments but keeps line numbers and string literals."""
    if kind == "xml":
        return re.sub(r"<!--.*?-->", lambda m: re.sub(r"[^\n]", " ", m.group(0)), text, flags=re.S)
    if kind == "pro":
        return re.sub(r"#[^\n]*", "", text)
    if kind == "txt":
        return text
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if text.startswith("//", i):
            j = text.find("\n", i)
            j = n if j == -1 else j
            i = j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            j = n if j == -1 else j + 2
            out.append(re.sub(r"[^\n]", " ", text[i:j]))
            i = j
        elif text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j == -1 else j + 3
            out.append(text[i:j])
            i = j
        elif c == '"':
            j = i + 1
            while j < n and text[j] != '"' and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            out.append(text[i:j + 1])
            i = j + 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


def scanned_files(root):
    for base, dirs, files in os.walk(os.path.join(root, APP_SRC)):
        dirs[:] = [d for d in dirs if d not in ("screenshots",)]
        for f in files:
            path = os.path.relpath(os.path.join(base, f), root).replace(os.sep, "/")
            if f.endswith(SCAN_EXT):
                yield path, "code"
            elif f.endswith(".xml") and ("/res/" in path or f == "AndroidManifest.xml"):
                yield path, "xml"
            elif f == "baseline-prof.txt":
                yield path, "txt"
    for extra in ("app/proguard-rules.pro",):
        if os.path.exists(os.path.join(root, extra)):
            yield extra, "pro"


PREFIX_TOKENS = {"com.google.android.material", "com/google/android/material", "Theme.Material3",
                 "Theme.MaterialComponents", "Theme_DeviceDefault", "Theme.DeviceDefault"}


def token_rx(token):
    # theme and package names also match their longer forms (Theme_DeviceDefault_NoActionBar, ...material.dialog)
    # package names also inside JVM descriptors (Lcom/google/android/material/...;) in baseline-prof.txt
    if token in PREFIX_TOKENS:
        return re.compile(re.escape(token))
    return re.compile(r"(?<![\w])" + re.escape(token) + r"(?![\w])")


LEGACY_RX = [(t, token_rx(t)) for t in LEGACY_TOKENS]
COMPOSE_RX = [(t, re.compile(r"(?<![\w.])" + re.escape(t) + r"\s*\(")) for t in COMPOSE_TOKENS]
COLOR_RX = re.compile(r"(?<![\w.])Color\(\s*0x", re.I)
SCREEN_H_RX = re.compile(r"\bscreenHeightDp\b")
M3_IMPORT_RX = re.compile(r"^\s*import\s+androidx\.compose\.material3\.([\w.]+)", re.M)


def find_violations(root):
    """[(rule, path, line, token)]"""
    hits = []
    for path, kind in scanned_files(root):
        with open(os.path.join(root, path), encoding="utf-8", errors="replace") as f:
            text = strip_comments(f.read(), kind)
        lines = text.split("\n")
        for no, line in enumerate(lines, 1):
            for token, rx in LEGACY_RX:
                if rx.search(line):
                    hits.append(("legacy", path, no, token))
            if kind != "code":
                continue
            for token, rx in COMPOSE_RX:
                if rx.search(line):
                    hits.append(("compose", path, no, token))
            if COLOR_RX.search(line) and not path.startswith(THEME_DIR) and "/test/" not in path:
                hits.append(("color", path, no, "Color(0x"))
            if SCREEN_H_RX.search(line) and "/test/" not in path:
                hits.append(("screen-height", path, no, "screenHeightDp"))
        if kind == "code" and not path.startswith(DESIGN_DIR) and "/test/" not in path:
            for m in M3_IMPORT_RX.finditer(text):
                name = m.group(1).split(".")[-1]
                if name not in M3_ALLOWED:
                    no = text.count("\n", 0, m.start()) + 1
                    hits.append(("m3-import", path, no, name))
    # layouts: the only "compiles but crashes at run time" place, so each file is registered
    for base, _, files in os.walk(os.path.join(root, APP_SRC)):
        rel = os.path.relpath(base, root).replace(os.sep, "/")
        if re.search(r"/res/layout[^/]*$", rel):
            for f in files:
                hits.append(("layout", f"{rel}/{f}", 0, f))
    return hits


# ---------------------------------------------------------------- surfaces

FUN_RX = re.compile(r"^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:private|internal|public|protected|override|inline|suspend|operator)\s+)*fun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)\s*[(<]")
SURFACE_KINDS = re.compile(r"^(\w+\.kt#\w+(#\d+)?|PlayerSheet\.\w+|ChatSheet\.\w+|SheetRequest\.\w+|PageId\.\w+|\w+Route|\w+Activity|\w+Dialog)$")


def code_surfaces(root):
    main = os.path.join(root, "app/src/main")
    found = set()
    calls = defaultdict(int)
    for base, _, files in os.walk(os.path.join(main, "java")):
        for f in files:
            if not f.endswith(".kt"):
                continue
            path = os.path.join(base, f)
            with open(path, encoding="utf-8") as fh:
                text = strip_comments(fh.read(), "code")
            func = None
            sites = []
            for line in text.split("\n"):
                m = FUN_RX.match(line)
                if m:
                    func = m.group(1)
                for call in re.finditer(r"(?<![\w.])(LagoonBottomSheet|ActionSheet)\s*\(", line):
                    if not re.search(r"\bfun\s+" + call.group(1) + r"\b", line) and func:
                        sites.append(func)
            if not path.replace(os.sep, "/").endswith("ui/design/components/Sheets.kt"):  # the definitions
                for fn in sites:
                    calls[(f, fn)] += 1
            if "enum class PlayerSheet" in text:
                body = re.search(r"enum class PlayerSheet\s*\{([^}]*)\}", text).group(1)
                found |= {f"PlayerSheet.{e.strip()}" for e in body.split(",") if e.strip()}
            for sealed in ("ChatSheet", "SheetRequest"):
                for m in re.finditer(r"(?:data\s+)?(?:class|object)\s+(\w+)[^\n]*:\s*" + sealed + r"\b", text):
                    found.add(f"{sealed}.{m.group(1)}")
            if "enum class PageId" in text:
                body = re.search(r"enum class PageId\s*\{([^}]*)\}", text).group(1)
                found |= {f"PageId.{e.strip()}" for e in body.split(",") if e.strip()}
            if f == "Routes.kt":
                found |= {m.group(1) for m in re.finditer(r"(?:data\s+)?(?:object|class)\s+(\w+Route)\b", text)}
            for m in re.finditer(r"^(?!abstract)\s*(?:open\s+)?class\s+(\w+)\s*(?:\([^)]*\))?\s*:\s*(\w+)\(", text, re.M):
                if m.group(2) in ("DialogFragment", "ComposeSheetDialog"):
                    found.add(m.group(1))
    for (f, fn), n in calls.items():
        if n == 1:
            found.add(f"{f}#{fn}")
        else:
            found |= {f"{f}#{fn}#{i}" for i in range(1, n + 1)}
    with open(os.path.join(main, "AndroidManifest.xml"), encoding="utf-8") as fh:
        manifest = strip_comments(fh.read(), "xml")
    found |= {m.group(1).split(".")[-1] for m in re.finditer(r'<activity[^>]*?android:name="([^"]+)"', manifest, re.S)}
    return found


def md_surfaces(root):
    path = os.path.join(root, SURFACES_MD)
    if not os.path.exists(path):
        return None
    ids = set()
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            m = re.match(r"^\|\s*`([^`]+)`", line)
            if m:
                for part in m.group(1).split(" / "):
                    if SURFACE_KINDS.match(part.strip()):
                        ids.add(part.strip())
    return ids


# ---------------------------------------------------------------- allowlist

def read_allowlist(root):
    entries = []
    with open(os.path.join(root, ALLOWLIST), encoding="utf-8") as fh:
        for no, raw in enumerate(fh, 1):
            line = raw.split("#", 1)[0].strip()
            if not line:
                continue
            if "#" not in raw:
                raise SystemExit(f"{ALLOWLIST}:{no}: every entry needs a '# reason'")
            parts = line.split()
            entries.append({"rule": parts[0], "path": parts[1], "token": parts[2] if len(parts) > 2 else None,
                            "line": no, "used": False})
    return entries


def allowed(hit, entries):
    rule, path, _, token = hit
    for e in entries:
        if e["rule"] != rule:
            continue
        if not (path == e["path"] or (e["path"].endswith("/") and path.startswith(e["path"]))):
            continue
        if e["token"] and e["token"] != token:
            continue
        e["used"] = True
        return True
    return False


def run(root):
    problems = []
    entries = read_allowlist(root)
    for hit in find_violations(root):
        if not allowed(hit, entries):
            rule, path, no, token = hit
            problems.append(f"[{rule}] {path}:{no}: {token}")
    for e in entries:
        if not e["used"]:
            problems.append(f"[allowlist] {ALLOWLIST}:{e['line']}: entry no longer matches anything, remove it")
    code = code_surfaces(root)
    md = md_surfaces(root)
    if md is None:
        problems.append(f"[surfaces] {SURFACES_MD} is missing")
    else:
        for s in sorted(code - md):
            problems.append(f"[surfaces] {s}: sub-screen not registered in {SURFACES_MD}")
        for s in sorted(md - code):
            problems.append(f"[surfaces] {s}: listed in {SURFACES_MD} but no longer in the code")
    return problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    ap.add_argument("--list-surfaces", action="store_true")
    args = ap.parse_args()
    if args.list_surfaces:
        for s in sorted(code_surfaces(args.root)):
            print(s)
        return
    problems = run(args.root)
    for p in problems:
        print(p)
    print(f"legacy UI guard: {len(problems)} problem(s)")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
