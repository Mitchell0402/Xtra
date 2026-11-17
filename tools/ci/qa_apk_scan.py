#!/usr/bin/env python3
"""qa APK scan for the removed Material Components library (U9 plan 4.5 item 4, gate 9).

    python tools/ci/qa_apk_scan.py <app-qa.apk> <mapping.txt> [--aapt2 PATH]

Fails when anything of com.google.android.material is left in the R8-built qa APK:
  1. a class in the R8 mapping,
  2. the package name in any dex file (catches reflection strings: Class.forName, theme class names),
  3. the package name in any packaged XML (layouts, manifest, values) in UTF-8 or UTF-16,
  4. a resource named mtrl_*, m3_* or design_* in the resource table. Exceptions, exactly: Compose Material 3's
     own m3c_* resources, and the project's design_* strings (app/src/main/res/values/strings_design.xml).
"""
import argparse
import glob
import os
import re
import shutil
import subprocess
import sys
import zipfile

NEEDLES = [b"com.google.android.material", b"com/google/android/material"]
NEEDLES += [n.decode().encode("utf-16-le") for n in NEEDLES]
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def find_aapt2(explicit):
    if explicit:
        return explicit
    found = shutil.which("aapt2")
    if found:
        return found
    for home in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"),
                 os.path.expanduser("~/AppData/Local/Android/Sdk")):
        if home:
            tools = sorted(glob.glob(os.path.join(home, "build-tools", "*", "aapt2*")))
            if tools:
                return tools[-1]
    raise SystemExit("aapt2 not found (set ANDROID_HOME or pass --aapt2)")


def own_design_strings():
    path = os.path.join(ROOT, "app/src/main/res/values/strings_design.xml")
    with open(path, encoding="utf-8") as f:
        return set(re.findall(r'name="(design_\w+)"', f.read()))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("apk")
    ap.add_argument("mapping")
    ap.add_argument("--aapt2")
    args = ap.parse_args()
    problems = []

    with open(args.mapping, encoding="utf-8", errors="replace") as f:
        classes = [line.split(" -> ")[0] for line in f if line.startswith("com.google.android.material.")]
    problems += [f"mapping: class {c}" for c in classes[:20]]

    with zipfile.ZipFile(args.apk) as z:
        for name in z.namelist():
            if not (name.endswith(".dex") or name.endswith(".xml") or name.endswith(".arsc")):
                continue
            data = z.read(name)
            for needle in NEEDLES:
                if needle in data:
                    problems.append(f"{name}: contains {needle.decode('utf-8', 'replace').replace(chr(0), '')!r}")
                    break

    allowed_design = own_design_strings()
    out = subprocess.run([find_aapt2(args.aapt2), "dump", "resources", args.apk], capture_output=True, text=True, errors="replace").stdout
    if "resource 0x" not in out:
        problems.append("aapt2 dump resources printed no resources")
    for kind, name in re.findall(r"resource 0x[0-9a-f]+ (\w+)/((?:mtrl|m3|design)_\w*)", out):
        if kind == "string" and name in allowed_design:
            continue
        problems.append(f"resource {kind}/{name}")

    for p in problems:
        print(p)
    print(f"qa APK scan: {len(problems)} problem(s); {os.path.getsize(args.apk)} bytes")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
