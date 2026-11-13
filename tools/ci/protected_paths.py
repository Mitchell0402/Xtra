#!/usr/bin/env python3
"""Fails a pull request that touches protected code without a declaration in its description.

Protected (plan section 10.1, red lines 1, 2 and 5):
  - the ad-blocking pipeline: player/ads, ui/player/ExoPlayerService.kt, player/lowlatency, util/m3u8/PlaylistUtils.kt
  - the database package (schema version must stay 41)
  - signing configuration and key material
  - permissions declared in any AndroidManifest.xml
  - this check itself and the CI workflows

A PR that has to touch one of these adds a section to its description:

    ## Protected code declaration
    - player/ads: why the change is needed and how it was verified
    - signing: ...

Every protected area the diff touches must be named in that section by its label (left column of AREAS).

Usage: protected_paths.py <base-ref> <head-ref>   (PR body is read from the PR_BODY environment variable)
"""
import os
import re
import subprocess
import sys

SRC = "app/src/main/java/com/github/andreyasadchy/xtra/"

# label -> predicate on (path, added/removed diff lines of that file)
AREAS = [
    ("player/ads", lambda p, d: p.startswith(SRC + "player/ads/")),
    ("ExoPlayerService", lambda p, d: p == SRC + "ui/player/ExoPlayerService.kt"),
    ("player/lowlatency", lambda p, d: p.startswith(SRC + "player/lowlatency/")),
    ("PlaylistUtils", lambda p, d: p == SRC + "util/m3u8/PlaylistUtils.kt"),
    ("db", lambda p, d: p.startswith(SRC + "db/")),
    ("signing", lambda p, d: p.endswith((".jks", ".keystore", "keystore.properties"))
        or (p.endswith(".gradle.kts") and any(re.search(r"signingConfig|storeFile|storePassword|keyAlias|keyPassword|LAGOON_KEY", l) for l in d))),
    ("permissions", lambda p, d: p.endswith("AndroidManifest.xml") and any("uses-permission" in l for l in d)),
    ("ci", lambda p, d: p.startswith(".github/workflows/") or p.startswith("tools/ci/")),
]

HEADING = re.compile(r"^#{2,3}\s*(Protected code declaration|受保护代码声明)\s*$", re.I | re.M)


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True, encoding="utf-8").stdout


def changed_files(base, head):
    """path -> list of added/removed lines (without the +/- marker)."""
    out = git("diff", "--unified=0", "--no-color", "--no-renames", f"{base}...{head}")
    files, current = {}, None
    for line in out.splitlines():
        if line.startswith("diff --git "):
            current = line.split(" b/", 1)[1]
            files[current] = []
        elif current and line[:1] in "+-" and not line.startswith(("+++", "---")):
            files[current].append(line[1:])
    return files


def declaration(body):
    m = HEADING.search(body or "")
    if not m:
        return None
    rest = body[m.end():]
    nxt = re.search(r"^#{1,3}\s", rest, re.M)
    return rest[: nxt.start()] if nxt else rest


def main():
    base, head = sys.argv[1], sys.argv[2]
    touched = {}
    for path, diff in changed_files(base, head).items():
        for label, hit in AREAS:
            if hit(path, diff):
                touched.setdefault(label, []).append(path)
    if not touched:
        print("No protected code touched.")
        return 0
    decl = declaration(os.environ.get("PR_BODY", ""))
    missing = [label for label in touched if decl is None or label.lower() not in decl.lower()]
    for label, paths in touched.items():
        state = "MISSING DECLARATION" if label in missing else "declared"
        print(f"[{state}] {label}: " + ", ".join(sorted(paths)))
    if missing:
        print("\nThis PR touches protected code. Add a '## Protected code declaration' section to the PR "
              "description that names each area above (" + ", ".join(missing) + ") with the reason and how it "
              "was verified, then re-run this check (editing the description re-runs it).")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
