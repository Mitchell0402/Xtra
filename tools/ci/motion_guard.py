#!/usr/bin/env python3
"""Motion guard (motion plan M0, item 2).

Every animation value comes from LagoonMotion (ui/design/theme). Under app/src/main/java/.../ui, outside
ui/design/theme, this fails on:

  spec        a hard-coded spec: tween(, spring(, keyframes {, snap(, repeatable(, infiniteRepeatable(
  animate-to  animateTo(x) or animateTo(x, initialVelocity = v) without a spec (the default spring is not ours)
  visibility  AnimatedVisibility( without both enter = and exit =
  content     AnimatedContent( without transitionSpec =
  item        animateItem() or animateContentSize() without a spec
  infinite    rememberInfiniteTransition anywhere: continuous animations go through continuousTransition() in
              ui/design/theme/LagoonLoops.kt, which stops ambient ones over glass (glass decision 4a)

    python tools/ci/motion_guard.py [--root .]

Known exceptions live in tools/ci/motion_allowlist.txt, one per line:  <rule> <path> <count>  # reason
The count must match exactly: a new hit in the same file fails, and so does an entry that has gone stale.
"""
import argparse
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from legacy_ui_guard import strip_comments  # noqa: E402

UI_DIR = "app/src/main/java/com/github/andreyasadchy/xtra/ui/"
THEME_DIR = UI_DIR + "design/theme/"
ALLOWLIST = "tools/ci/motion_allowlist.txt"

SPEC_RX = re.compile(r"(?<![\w.])(tween|spring|keyframes|keyframesWithSpline|snap|repeatable|infiniteRepeatable)\s*(?:<[^>()]*>)?\s*[({]")
SPEC_QUALIFIED_RX = re.compile(r"\banimation\.core\.(tween|spring|keyframes|snap|repeatable|infiniteRepeatable)\s*\(")
ANIMATE_TO_RX = re.compile(r"\.animateTo\s*\(")
VISIBILITY_RX = re.compile(r"(?<![\w])AnimatedVisibility\s*\(")
CONTENT_RX = re.compile(r"(?<![\w])AnimatedContent\s*(?:<[^>()]*>)?\s*\(")
ITEM_RX = re.compile(r"(?<![\w])(animateItem|animateContentSize)\s*\(")
INFINITE_RX = re.compile(r"(?<![\w])rememberInfiniteTransition\b")
FUN_DEF_RX = re.compile(r"\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?$")
STRING_RX = re.compile(r'"(?:\\.|[^"\\\n])*"')


def blank_strings(text):
    """Blanks string literal contents (same length, so offsets and line numbers stay)."""
    return STRING_RX.sub(lambda m: '"' + " " * (len(m.group(0)) - 2) + '"', text)


def call_args(text, open_paren):
    """Top-level arguments of the call whose '(' is at open_paren, as stripped strings."""
    depth, i, n = 0, open_paren, len(text)
    start = open_paren + 1
    args = []
    while i < n:
        c = text[i]
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            i = j + 1
            continue
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                args.append(text[start:i])
                break
        elif c == "," and depth == 1:
            args.append(text[start:i])
            start = i + 1
        i += 1
    return [a.strip() for a in args if a.strip()]


def named(args):
    return {a.split("=", 1)[0].strip() for a in args if re.match(r"^\w+\s*=(?!=)", a)}


def is_definition(text, pos):
    line_start = text.rfind("\n", 0, pos) + 1
    return bool(FUN_DEF_RX.search(text[line_start:pos]))


def scanned_files(root):
    base_dir = os.path.join(root, UI_DIR)
    for base, _, files in os.walk(base_dir):
        for f in files:
            if f.endswith(".kt"):
                path = os.path.relpath(os.path.join(base, f), root).replace(os.sep, "/")
                if not path.startswith(THEME_DIR):
                    yield path


def find_violations(root):
    """[(rule, path, line, what)]"""
    hits = []
    for path in scanned_files(root):
        with open(os.path.join(root, path), encoding="utf-8", errors="replace") as fh:
            text = blank_strings(strip_comments(fh.read(), "code"))

        def add(rule, pos, what):
            hits.append((rule, path, text.count("\n", 0, pos) + 1, what))

        for rx in (SPEC_RX, SPEC_QUALIFIED_RX):
            for m in rx.finditer(text):
                if not is_definition(text, m.start()):
                    add("spec", m.start(), m.group(1) + "(")
        for m in ANIMATE_TO_RX.finditer(text):
            args = call_args(text, m.end() - 1)
            positional = [a for a in args if not re.match(r"^\w+\s*=(?!=)", a)]
            if len(positional) < 2 and "animationSpec" not in named(args):
                add("animate-to", m.start(), "animateTo without a spec")
        for m in VISIBILITY_RX.finditer(text):
            if is_definition(text, m.start()):
                continue
            names = named(call_args(text, m.end() - 1))
            if not {"enter", "exit"} <= names:
                add("visibility", m.start(), "AnimatedVisibility without enter/exit")
        for m in CONTENT_RX.finditer(text):
            if is_definition(text, m.start()):
                continue
            if "transitionSpec" not in named(call_args(text, m.end() - 1)):
                add("content", m.start(), "AnimatedContent without transitionSpec")
        for m in ITEM_RX.finditer(text):
            if is_definition(text, m.start()):
                continue
            if not call_args(text, m.end() - 1):
                add("item", m.start(), m.group(1) + "() without a spec")
        for m in INFINITE_RX.finditer(text):
            add("infinite", m.start(), "rememberInfiniteTransition (use continuousTransition)")
    return hits


def read_allowlist(root):
    entries = []
    path = os.path.join(root, ALLOWLIST)
    if not os.path.exists(path):
        return entries
    with open(path, encoding="utf-8") as fh:
        for no, raw in enumerate(fh, 1):
            line = raw.split("#", 1)[0].strip()
            if not line:
                continue
            if "#" not in raw:
                raise SystemExit(f"{ALLOWLIST}:{no}: every entry needs a '# reason'")
            parts = line.split()
            if len(parts) != 3 or not parts[2].isdigit():
                raise SystemExit(f"{ALLOWLIST}:{no}: expected '<rule> <path> <count>  # reason'")
            entries.append({"rule": parts[0], "path": parts[1], "count": int(parts[2]), "line": no})
    return entries


def run(root):
    problems = []
    hits = find_violations(root)
    entries = read_allowlist(root)
    by_key = {}
    for h in hits:
        by_key.setdefault((h[0], h[1]), []).append(h)
    allowed = {(e["rule"], e["path"]): e for e in entries}
    for key, group in sorted(by_key.items()):
        e = allowed.get(key)
        if e and e["count"] == len(group):
            continue
        if e:
            problems.append(f"[allowlist] {ALLOWLIST}:{e['line']}: {key[0]} {key[1]} allows {e['count']}, found {len(group)}")
        for rule, path, no, what in group:
            problems.append(f"[{rule}] {path}:{no}: {what}")
    for key, e in allowed.items():
        if key not in by_key:
            problems.append(f"[allowlist] {ALLOWLIST}:{e['line']}: entry no longer matches anything, remove it")
    return problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    args = ap.parse_args()
    problems = run(args.root)
    for p in problems:
        print(p)
    print(f"motion guard: {len(problems)} problem(s)")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
