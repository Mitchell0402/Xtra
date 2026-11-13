#!/usr/bin/env python3
"""Groups the JVM test results into the plan's categories and prints a Markdown table.

Naming convention for test classes (plan section 10.2):
  *ScreenshotTest   Roborazzi screenshot tests
  *InteractionTest  Compose + Robolectric interaction tests
  *ContractTest     player UI <-> ExoPlayerService contract tests (from U6)
  anything else     plain JUnit unit tests
Usage: test_summary.py <junit-xml-dir>
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

CATEGORIES = [("ScreenshotTest", "Screenshot (Roborazzi)"), ("InteractionTest", "Interaction (Compose + Robolectric)"),
              ("ContractTest", "Player contract"), ("", "Unit (JUnit)")]


def main():
    stats = {name: [0, 0, 0] for _, name in CATEGORIES}  # tests, failures, skipped
    failed = []
    for f in glob.glob(os.path.join(sys.argv[1], "*.xml")):
        for case in ET.parse(f).getroot().iter("testcase"):
            cls = case.get("classname", "")
            name = next(n for suffix, n in CATEGORIES if cls.split(".")[-1].endswith(suffix))
            stats[name][0] += 1
            if case.find("failure") is not None or case.find("error") is not None:
                stats[name][1] += 1
                failed.append(f"{cls}.{case.get('name')}")
            if case.find("skipped") is not None:
                stats[name][2] += 1
    lines = ["| Category | Tests | Failed | Skipped |", "|---|---:|---:|---:|"]
    lines += [f"| {n} | {t} | {fl} | {s} |" for n, (t, fl, s) in stats.items()]
    if failed:
        lines += ["", "Failed:"] + [f"- `{x}`" for x in failed]
    print("\n".join(lines))


if __name__ == "__main__":
    main()
