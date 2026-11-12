#!/usr/bin/env python3
"""Tiny adb UI driver used to capture screen baselines on the emulator (U0) and to run manual-test steps.

    python adbui.py shot <dir> <name>            screenshot to <dir>/<name>.png
    python adbui.py tap <text|desc|res-id>       tap the first node whose text, content-desc or resource-id matches
    python adbui.py find <regex>                 print matching nodes (text / desc / id / bounds)
    python adbui.py link <url> [package]         open a twitch.tv link in the app
    python adbui.py back | home

ADB defaults to the SDK's platform-tools; override with the ADB environment variable.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB") or os.path.expanduser("~/AppData/Local/Android/Sdk/platform-tools/adb.exe")
ENV = dict(os.environ, MSYS_NO_PATHCONV="1")


def adb(*args, binary=False):
    r = subprocess.run([ADB, *args], capture_output=True, env=ENV)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def nodes():
    for _ in range(5):
        out = adb("exec-out", "uiautomator", "dump", "/dev/tty")
        start = out.find("<?xml")
        end = out.rfind("</hierarchy>")
        if start != -1 and end != -1:
            return list(ET.fromstring(out[start:end + len("</hierarchy>")]).iter("node"))
        time.sleep(1)
    return []


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(pattern):
    rx = re.compile(pattern, re.I)
    return [n for n in nodes() if any(rx.search(n.get(k) or "") for k in ("text", "content-desc", "resource-id"))]


def tap(pattern, index=0):
    hits = find(f"^(.*[:/])?{pattern}$")
    if len(hits) <= index:
        raise SystemExit(f"no node matches {pattern!r}")
    x, y = center(hits[index])
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1.5)


def shot(directory, name):
    os.makedirs(directory, exist_ok=True)
    path = os.path.join(directory, name + ".png")
    with open(path, "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", binary=True))
    print(path)


def main():
    cmd, *rest = sys.argv[1:]
    if cmd == "shot":
        shot(*rest)
    elif cmd == "tap":
        tap(rest[0], int(rest[1]) if len(rest) > 1 else 0)
    elif cmd == "find":
        for n in find(rest[0]):
            print(repr(n.get("text")), repr(n.get("content-desc")), n.get("resource-id"), n.get("bounds"))
    elif cmd == "link":
        args = ["shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", rest[0]]
        if len(rest) > 1:
            args += [rest[1]]
        adb(*args)
        time.sleep(3)
    elif cmd == "back":
        adb("shell", "input", "keyevent", "4")
        time.sleep(1)
    elif cmd == "home":
        adb("shell", "input", "keyevent", "3")
        time.sleep(1)


if __name__ == "__main__":
    main()
