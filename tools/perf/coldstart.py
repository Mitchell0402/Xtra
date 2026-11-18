"""Cold, warm and hot start measurement for Lagoon (docs/perf/startup-plan.md, sections 1.1-1.3 and S0 step 1).

Two packages (a = baseline, b = candidate) are prepared the way a user's install ends up, then launched one after
the other, the order turning every round (ABAB / BABA). Only the exact package ids in WHITELIST are ever installed,
uninstalled or force-stopped; every other Lagoon install on the device (.qa, release, .debug, ...) is listed in the
log at the start and checked again at the end.

usage (python 3.10+, the device must be unlocked, charging and set to stay awake):
  python tools/perf/coldstart.py -s SERIAL check
  python tools/perf/coldstart.py -s SERIAL cold --a qa.perf.v01=PATH.apk --b qa.perf.u9=PATH.apk [--blocks 5 --runs 6]
         [--method 2|1] [--no-prep] [--out DIR]
  python tools/perf/coldstart.py -s SERIAL warm --a ... --b ... [--blocks 2 --runs 6] [--no-prep]
  python tools/perf/coldstart.py -s SERIAL hot  --a ... --b ... [--blocks 2 --runs 6] [--no-prep]

  --a/--b take KEY=APK where KEY is the id after "io.github.mitchell0402.lagoon." (it must be in WHITELIST); with
  --no-prep the APK part may be left out (KEY only) and the installed, prepared package is used as it is.

Preparation (method 2, the gate): install, first launch, wait 5 s with the process alive, ProfileInstaller
broadcast (result code 1 only), wait 3 s, force-stop, compile speed-profile, then check the dexopt status AND the
number of classes / methods in the compiled profile (cmd package dump-profiles). --min-profile KEY=N sets the floor
for a package (S0 dry run: 80 % of the emulator count). Method 1 (reference only): install, compile speed-profile,
first launch, no broadcast.

Warm starts use `am start --activity-clear-task` (old instance finished, new one created in the live process); no
system setting is changed. Every launch's LaunchState must match the mode (COLD / WARM / HOT) or it is invalid.

Wake and keyguard: the fields differ between Android versions and vendors. A run whose fields cannot be found is
INVALID (never treated as passed). Field names checked by hand on: emulator API 37 (S0 dry run); OnePlus 15
CPH2747 Android 17: <fill in at the start of P0>.
"""
import argparse
import datetime
import json
import os
import random
import re
import statistics
import subprocess
import sys
import time

PREFIX = "io.github.mitchell0402.lagoon"
# exact package ids this script may install, uninstall or stop (plan 1.3); nothing is matched by prefix or suffix
WHITELIST = {f"{PREFIX}.{k}" for k in (
    "qa.perf.v01", "qa.perf.u9", "qa.perf.new", "qa.perf.nocompress",
    "bench.perf.u9", "bench.perf.new", "bench.v01",
)}
ACTIVITY = "com.github.andreyasadchy.xtra.ui.main.MainActivity"
RECEIVER = "androidx.profileinstaller.ProfileInstallReceiver"
INSTALL_PROFILE = "androidx.profileinstaller.action.INSTALL_PROFILE"
BOOTSTRAP_SEED = 20261118
BOOTSTRAP_ITERATIONS = 10_000

ADB = os.environ.get("ADB") or os.path.join(
    os.environ.get("ANDROID_HOME") or os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk"), "platform-tools", "adb")


class Device:
    def __init__(self, serial, log):
        self.serial = serial
        self.log = log

    def adb(self, *args, check=False, timeout=300):
        r = subprocess.run([ADB, "-s", self.serial, *args], capture_output=True, text=True, timeout=timeout,
                           encoding="utf-8", errors="replace")
        if check and r.returncode != 0:
            raise RuntimeError(f"adb {' '.join(args)} failed: {r.stdout}{r.stderr}")
        return r.stdout + r.stderr

    def sh(self, *args, **kw):
        return self.adb("shell", *args, **kw)

    def packages(self):
        out = self.sh("pm", "list", "packages", "mitchell0402")
        return sorted(line.split(":", 1)[1].strip() for line in out.splitlines() if line.startswith("package:"))


def guard(pkg):
    if pkg not in WHITELIST:
        sys.exit(f"refusing to touch {pkg}: not in the measurement whitelist {sorted(WHITELIST)}")
    return pkg


# --- device state -----------------------------------------------------------------------------------------------

WAKE_FIELDS = [r"mWakefulness=(\w+)"]
# first match wins; value must say the keyguard is NOT showing
KEYGUARD_FIELDS = [
    ("window", r"isKeyguardShowing=(true|false)"),
    ("window", r"mKeyguardShowing=(true|false)"),
    ("window policy", r"isKeyguardShowing=(true|false)"),
    ("window policy", r"mShowingLockscreen=(true|false)"),
    ("trust", r"keyguardShowing=(\w+)"),
]


def awake_and_unlocked(dev):
    """(ok, reason). Missing fields make ok False."""
    dev.sh("input", "keyevent", "KEYCODE_WAKEUP")
    power = dev.sh("dumpsys", "power")
    m = next((m for p in WAKE_FIELDS if (m := re.search(p, power))), None)
    if not m:
        return False, "wakefulness field not found"
    if m.group(1) != "Awake":
        return False, f"wakefulness={m.group(1)}"
    cache = {}
    for svc, pattern in KEYGUARD_FIELDS:
        if svc not in cache:
            cache[svc] = dev.sh("dumpsys", *svc.split())
        k = re.search(pattern, cache[svc])
        if k:
            return (k.group(1) == "false"), f"keyguard {pattern.split('=')[0]}={k.group(1)}"
    return False, "keyguard field not found"


def conditions(dev):
    batt = dev.sh("dumpsys", "battery")
    thermal = dev.sh("dumpsys", "thermalservice")
    g = lambda p, s: (m.group(1) if (m := re.search(p, s)) else None)
    return {
        "time": datetime.datetime.now().isoformat(timespec="seconds"),
        "battery_level": g(r"level: (\d+)", batt),
        "battery_temp_dC": g(r"temperature: (\d+)", batt),
        "plugged": (m.group(1) if (m := re.search(r"(AC|USB|Wireless|Dock) powered: true", batt)) else "no"),
        "thermal_status": g(r"Thermal Status: (\d+)", thermal),
    }


def fingerprint(dev):
    return {k: dev.sh("getprop", k).strip() for k in
            ("ro.product.model", "ro.build.fingerprint", "ro.build.version.sdk")}


# --- preparation ------------------------------------------------------------------------------------------------

def launch_raw(dev, pkg, *flags):
    out = dev.sh("am", "start", "-W", *flags, "-n", f"{pkg}/{ACTIVITY}")
    g = lambda p: (m.group(1) if (m := re.search(p, out)) else None)
    total, wait = g(r"TotalTime: (\d+)"), g(r"WaitTime: (\d+)")
    return {"total": int(total) if total else None, "wait": int(wait) if wait else None,
            "state": g(r"LaunchState: (\w+)"), "raw": out.strip() if not total else None}


def dexopt_status(dev, pkg):
    out = dev.sh("dumpsys", "package", pkg)
    sect = out.split("Dexopt state:", 1)[1] if "Dexopt state:" in out else out
    return sorted(set(re.findall(r"status=([\w-]+)", sect.split("\n\n", 1)[0] if sect else "")))


def profile_counts(dev, pkg):
    dev.sh("cmd", "package", "dump-profiles", "--dump-classes-and-methods", pkg)
    txt = dev.sh("cat", f"/data/misc/profman/{pkg}-primary.prof.txt")
    if "No such file" in txt or "Permission denied" in txt:
        return {"classes": None, "methods": None, "error": txt.strip()[:200]}
    lines = [ln.strip() for ln in txt.splitlines() if ln.strip()]
    methods = sum(1 for ln in lines if "->" in ln)
    classes = sum(1 for ln in lines if "->" not in ln and ln.startswith("L") and ln.endswith(";"))
    return {"classes": classes, "methods": methods}


def prepare(dev, key, apk, method, floor):
    pkg = guard(f"{PREFIX}.{key}")
    rec = {"package": pkg, "apk": apk, "method": method, "ok": False}
    if pkg in dev.packages():
        dev.adb("uninstall", pkg)
    out = dev.adb("install", apk)
    if "Success" not in out:
        rec["error"] = f"install: {out.strip()[:300]}"
        return rec
    if method == 1:
        dev.sh("cmd", "package", "compile", "-m", "speed-profile", "-f", pkg)
        rec["first_launch"] = launch_raw(dev, pkg)
        time.sleep(3)
        dev.sh("am", "force-stop", pkg)
    else:
        rec["first_launch"] = launch_raw(dev, pkg)
        time.sleep(5)  # process stays alive: force-stop would put the package back into the stopped state
        b = dev.sh("am", "broadcast", "-a", INSTALL_PROFILE, "--include-stopped-packages", "-n", f"{pkg}/{RECEIVER}")
        m = re.search(r"result=(-?\d+)", b)
        rec["broadcast_result"] = int(m.group(1)) if m else None
        if rec["broadcast_result"] != 1:
            rec["error"] = f"profile broadcast result {rec['broadcast_result']} (only 1 is accepted): {b.strip()[:200]}"
            return rec
        time.sleep(3)
        dev.sh("am", "force-stop", pkg)
        dev.sh("cmd", "package", "compile", "-m", "speed-profile", "-f", pkg)
    rec["dexopt"] = dexopt_status(dev, pkg)
    rec["profile"] = profile_counts(dev, pkg)
    if method == 1:  # reference only: whatever the state is (on API 37 it is "verify" with an empty profile) is recorded
        rec["ok"] = True
        return rec
    if rec["dexopt"] != ["speed-profile"]:
        rec["error"] = f"dexopt status {rec['dexopt']}, expected speed-profile"
        return rec
    n = rec["profile"].get("methods")
    if n is None:
        rec["error"] = f"profile not readable: {rec['profile']}"
        return rec
    if floor is not None and n < floor:
        rec["error"] = f"profile has {n} methods, floor {floor}"
        return rec
    rec["ok"] = True
    return rec


# --- launches ---------------------------------------------------------------------------------------------------

def measure(dev, mode, pkg, both):
    """One launch of pkg in the given mode; returns a record with valid True/False and a reason."""
    if mode == "cold":
        for p in both:
            dev.sh("am", "force-stop", p)
        time.sleep(5)
        alive = [p for p in both if dev.sh("pidof", p).strip()]
        if alive:
            return {"valid": False, "total": None, "reason": f"still running after force-stop: {alive}"}
    else:  # warm / hot: the app is brought back from Home
        dev.sh("input", "keyevent", "KEYCODE_HOME")
        time.sleep(3)
    ok, why = awake_and_unlocked(dev)
    # warm = process alive, activity created again: the clear-task launch finishes the old instance and starts a
    # new one in the running process (Back on the root activity does not finish it on Android 12+, that is hot)
    r = launch_raw(dev, pkg, *(["--activity-clear-task"] if mode == "warm" else []))
    r["device"] = why
    if not ok:
        r.update(valid=False, reason=why)
    elif r["total"] is None:
        r.update(valid=False, reason="no TotalTime")
    elif r["state"] != mode.upper():
        r.update(valid=False, reason=f"LaunchState {r['state']}, wanted {mode.upper()}")
    else:
        r["valid"] = True
    time.sleep(2)
    return r


def bootstrap(pairs):
    rng = random.Random(BOOTSTRAP_SEED)
    ratios = []
    for _ in range(BOOTSTRAP_ITERATIONS):
        s = [pairs[rng.randrange(len(pairs))] for _ in pairs]
        ratios.append(statistics.median(b for _, b in s) / statistics.median(a for a, _ in s))
    ratios.sort()
    return ratios[int(0.025 * len(ratios))], ratios[int(0.975 * len(ratios)) - 1]


def run(args):
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    os.makedirs(args.out, exist_ok=True)
    base = os.path.join(args.out, f"{stamp}-{args.mode}")
    logf = open(base + ".log", "w", encoding="utf-8")

    def log(*a):
        line = " ".join(str(x) for x in a)
        print(line, flush=True)
        logf.write(line + "\n")
        logf.flush()

    dev = Device(args.serial, log)
    result = {"mode": args.mode, "serial": args.serial, "device": fingerprint(dev), "whitelist": sorted(WHITELIST),
              "bootstrap": {"seed": BOOTSTRAP_SEED, "iterations": BOOTSTRAP_ITERATIONS}}
    before = dev.packages()
    others = [p for p in before if p not in WHITELIST]
    result["packages_before"] = before
    log("packages before:", before)
    if others:
        log("not measurement packages, left untouched:", others)

    if args.mode == "check":
        log("device:", result["device"])
        log("awake/unlocked:", awake_and_unlocked(dev))
        log("conditions:", conditions(dev))
        json.dump(result, open(base + ".json", "w"), indent=1)
        return 0

    specs = []
    for spec in (args.a, args.b):
        key, _, apk = spec.partition("=")
        guard(f"{PREFIX}.{key}")
        if not args.no_prep and not apk:
            sys.exit(f"{spec}: give KEY=APK, or --no-prep")
        specs.append((key, apk))
    pkgs = [f"{PREFIX}.{k}" for k, _ in specs]
    floors = dict(f.split("=") for f in args.min_profile)

    exit_code = 0
    try:
        if not args.no_prep:
            result["prep"] = []
            for key, apk in specs:
                p = prepare(dev, key, apk, args.method, int(floors[key]) if key in floors else None)
                result["prep"].append(p)
                log("prep", json.dumps({k: v for k, v in p.items() if k != "first_launch"}))
                if not p["ok"]:
                    log("PREPARATION FAILED, nothing measured:", p.get("error"))
                    exit_code = 2
                    return exit_code
        if args.mode in ("warm", "hot"):
            for p in pkgs:  # unmeasured start so the process exists
                launch_raw(dev, p)
                time.sleep(3)
        runs = {p: [] for p in pkgs}
        pairs = []
        rnd = 0
        for block in range(args.blocks):
            result.setdefault("blocks", []).append(conditions(dev))
            log("block", block + 1, result["blocks"][-1])
            for _ in range(args.runs):
                order = pkgs if rnd % 2 == 0 else pkgs[::-1]
                rec = {}
                for p in order:
                    rec[p] = measure(dev, args.mode, p, pkgs)
                    rec[p]["round"] = rnd
                    runs[p].append(rec[p])
                a, b = rec[pkgs[0]], rec[pkgs[1]]
                log(f"round {rnd:2d}", " ".join(f"{p.rsplit('lagoon.', 1)[1]}={rec[p]['total']}"
                                               f"{'' if rec[p]['valid'] else '(X ' + rec[p]['reason'] + ')'}"
                                               for p in order))
                if a["valid"] and b["valid"]:
                    pairs.append((a["total"], b["total"]))
                rnd += 1
        result["runs"] = runs
        result["pairs"] = pairs
        result["invalid"] = {p: [r["reason"] for r in runs[p] if not r["valid"]] for p in pkgs}
        if len(pairs) >= 3:
            ma = statistics.median(a for a, _ in pairs)
            mb = statistics.median(b for _, b in pairs)
            lo, hi = bootstrap(pairs)
            result["summary"] = {"a": pkgs[0], "b": pkgs[1], "pairs": len(pairs), "median_a": ma, "median_b": mb,
                                 "ratio_b_over_a": mb / ma, "ci95": [lo, hi]}
            log("summary", json.dumps(result["summary"]))
        else:
            log("too few valid pairs:", len(pairs))
            exit_code = 3
    finally:
        after = dev.packages()
        result["packages_after"] = after
        missing = [p for p in others if p not in after]
        log("packages after:", after)
        if missing:
            log("ERROR: packages that were there before are gone:", missing)
            exit_code = 4
        json.dump(result, open(base + ".json", "w"), indent=1)
        log("written", base + ".json")
    return exit_code


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("-s", "--serial", required=True, help="adb serial (required: never guess the device)")
    ap.add_argument("mode", choices=("check", "cold", "warm", "hot"))
    ap.add_argument("--a", help="baseline KEY=APK")
    ap.add_argument("--b", help="candidate KEY=APK")
    ap.add_argument("--blocks", type=int, default=5)
    ap.add_argument("--runs", type=int, default=6, help="rounds per block (one launch of each package per round)")
    ap.add_argument("--method", type=int, choices=(1, 2), default=2)
    ap.add_argument("--no-prep", action="store_true")
    ap.add_argument("--min-profile", action="append", default=[], metavar="KEY=N",
                    help="minimum number of methods in the compiled profile")
    ap.add_argument("--out", default="perf-out")
    args = ap.parse_args()
    if args.mode != "check" and not (args.a and args.b):
        ap.error("--a and --b are required")
    sys.exit(run(args))


if __name__ == "__main__":
    main()
