#!/usr/bin/env python3
"""Prints the metrics of a Macrobenchmark benchmarkData.json (medians, frame percentiles, jank share)."""
import glob
import json
import sys

for f in sys.argv[1:] or glob.glob("benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/*/*benchmarkData.json"):
    for b in json.load(open(f, encoding="utf-8"))["benchmarks"]:
        print(b["name"], b.get("params", ""))
        for k, v in b["metrics"].items():
            print(f"   {k}: median {v['median']:.2f}  runs {[round(x, 2) for x in v['runs']]}")
        for k, v in b.get("sampledMetrics", {}).items():
            print(f"   {k}: " + "  ".join(f"{p} {v[p]:.2f}" for p in ("P50", "P90", "P95", "P99") if p in v))
