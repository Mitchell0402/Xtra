"""Cold-start perfetto traces and their split into fixed segments (docs/perf/startup-plan.md S0 step 4, section 1.2).

capture: N cold starts of an already prepared, profileable package (prepare it with coldstart.py first), each one
         recorded with perfetto (sched, binder, am/wm/view/gfx/dalvik atrace). Only measurement packages
         (coldstart.WHITELIST) are stopped.
  python tools/perf/startup_trace.py -s SERIAL capture --key bench.perf.u9 [--other bench.v01] [-n 10] [--out DIR]
split:   one table per trace and the medians, as JSON.
  python tools/perf/startup_trace.py split --key bench.perf.u9 [--window-ms W] TRACE...

Segments (all on the app's main thread unless named): process start -> bindApplication -> activityStart ->
first Choreographer#doFrame end ("first frame"). Main-thread Running time is summed over a fixed window
[process start, process start + W]; W defaults to the first-frame end of this set of traces + 1000 ms; pass the U9
median + 1000 ms with --window-ms so every version is measured over the same length (plan 1.2).
"""
import argparse
import json
import os
import statistics
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
import coldstart  # noqa: E402

VSYNC_MS = 1000 / 120
CONFIG = """
buffers {{ size_kb: 65536 fill_policy: RING_BUFFER }}
data_sources {{ config {{ name: "linux.ftrace" ftrace_config {{
  ftrace_events: "sched/sched_switch" ftrace_events: "sched/sched_wakeup" ftrace_events: "sched/sched_process_exit"
  ftrace_events: "task/task_newtask" ftrace_events: "task/task_rename" ftrace_events: "power/cpu_frequency"
  atrace_categories: "am" atrace_categories: "wm" atrace_categories: "view" atrace_categories: "gfx"
  atrace_categories: "dalvik" atrace_categories: "binder_driver" atrace_categories: "res" atrace_categories: "ss"
  atrace_apps: "{pkg}"
}} }} }}
data_sources {{ config {{ name: "linux.process_stats" process_stats_config {{ scan_all_processes_on_start: true }} }} }}
duration_ms: {duration}
"""


def capture(args):
    dev = coldstart.Device(args.serial, print)
    pkg = coldstart.guard(f"{coldstart.PREFIX}.{args.key}")
    both = [pkg] + ([coldstart.guard(f"{coldstart.PREFIX}.{args.other}")] if args.other else [])
    os.makedirs(args.out, exist_ok=True)
    done = 0
    while done < args.n:
        for p in both:
            dev.sh("am", "force-stop", p)
        time.sleep(5)
        ok, why = coldstart.awake_and_unlocked(dev)
        if not ok:
            print("skip:", why)
            time.sleep(5)
            continue
        remote = f"/data/misc/perfetto-traces/startup-{args.key}-{done}.pftrace"
        cfg = CONFIG.format(pkg=pkg, duration=args.duration_ms)
        subprocess.run([coldstart.ADB, "-s", args.serial, "shell", "perfetto", "--background", "--txt", "-c", "-",
                        "-o", remote], input=cfg, text=True, capture_output=True)
        time.sleep(1.5)
        r = coldstart.launch_raw(dev, pkg)
        time.sleep(args.duration_ms / 1000 + 1)
        if r["state"] != "COLD" or r["total"] is None:
            print("invalid launch:", r)
            continue
        local = os.path.join(args.out, f"{args.key}-{done:02d}.pftrace")
        dev.adb("pull", remote, local)
        dev.sh("rm", remote)
        print(f"{local} TotalTime={r['total']}", flush=True)
        done += 1


Q_PROC = """
select p.upid, p.pid, p.start_ts, t.utid as main_utid from process p join thread t on t.upid = p.upid and t.tid = p.pid
where p.name = '{pkg}' or p.cmdline = '{pkg}' order by p.start_ts desc limit 1
"""


def q(tp, sql):
    return list(tp.query(sql))


def split_one(path, pkg, window_ms):
    from perfetto.trace_processor import TraceProcessor
    tp = TraceProcessor(trace=path)
    try:
        proc = q(tp, Q_PROC.format(pkg=pkg))
        if not proc:
            return {"trace": path, "error": "process not found"}
        p = proc[0]
        main = p.main_utid
        main_slices = lambda name: q(tp, f"""
            select s.ts, s.dur, s.name, s.depth from slice s join thread_track tt on s.track_id = tt.id
            where tt.utid = {main} and s.name glob '{name}' order by s.ts""")
        first_sched = q(tp, f"select min(ts) as ts from sched where utid = {main}")[0].ts
        start = p.start_ts or first_sched
        if p.start_ts is None and first_sched is None:
            return {"trace": path, "error": "no process start"}
        bind = main_slices("bindApplication")
        act = main_slices("activityStart")
        frames = [f for f in main_slices("Choreographer#doFrame [0-9]*") if f.depth == 0]
        # the first frame is the first doFrame after activityStart that runs a traversal (a doFrame can run
        # earlier with nothing to draw, e.g. while bindApplication finishes)
        after = [f for f in frames if not act or f.ts >= act[0].ts + act[0].dur]
        with_traversal = [f for f in after if q(tp, f"""select count(*) as n from slice where name = 'traversal'
                          and ts >= {f.ts} and ts < {f.ts + f.dur} and track_id = (
                          select track_id from slice where ts = {f.ts} and name glob 'Choreographer#doFrame*'
                          limit 1)""")[0].n]
        first = (with_traversal or after or [None])[0]
        if first is None:
            return {"trace": path, "error": "no first frame"}
        frames = frames[frames.index(first):]
        ff_end = first.ts + first.dur
        launch = q(tp, f"""select ts, dur from slice where name = 'launching: {pkg}' order by ts limit 1""")
        w_end = start + int((window_ms if window_ms else (ff_end - start) / 1e6 + 1000) * 1e6)

        def running(t0, t1):
            r = q(tp, f"""select sum(min(ts + dur, {t1}) - max(ts, {t0})) as d from sched
                          where utid = {main} and ts < {t1} and ts + dur > {t0}""")
            return (r[0].d or 0) / 1e6

        def total(name, t1):
            r = q(tp, f"""select count(*) as n, coalesce(sum(s.dur), 0) as d from slice s
                          join thread_track tt on s.track_id = tt.id
                          where tt.utid = {main} and s.name glob '{name}' and s.ts < {t1}""")
            return {"n": r[0].n, "ms": r[0].d / 1e6}

        rt = q(tp, f"select utid from thread where upid = {p.upid} and name = 'RenderThread'")
        shader = None
        if rt:
            shader = q(tp, f"""select count(*) as n from slice s join thread_track tt on s.track_id = tt.id
                               where tt.utid = {rt[0].utid} and s.name glob '*shader*compile*' and s.ts < {ff_end}""")[0].n
        jit = q(tp, f"""select count(*) as n from slice s join thread_track tt on s.track_id = tt.id
                        join thread t using(utid) where t.upid = {p.upid} and s.name glob 'JIT compiling*'
                        and s.ts < {ff_end}""")[0].n
        children = lambda parent: [
            {"name": c.name, "ms": round(c.dur / 1e6, 2)} for c in q(tp, f"""
                select s.name, s.dur from slice s where s.parent_id = (
                    select s2.id from slice s2 join thread_track tt on s2.track_id = tt.id
                    where tt.utid = {main} and s2.name = '{parent}' order by s2.ts limit 1)
                order by s.dur desc limit 12""")]
        next10 = frames[1:11]
        return {
            "trace": os.path.basename(path),
            "first_frame_ms": round((ff_end - start) / 1e6, 2),
            "system_launch_ms": round(launch[0].dur / 1e6, 2) if launch else None,
            "bindApplication_ms": round(bind[0].dur / 1e6, 2) if bind else None,
            "activityStart_ms": round(act[0].dur / 1e6, 2) if act else None,
            "first_doFrame_ms": round(frames[0].dur / 1e6, 2),
            "window_ms": round((w_end - start) / 1e6, 1),
            "main_running_window_ms": round(running(start, w_end), 2),
            "main_running_before_first_frame_ms": round(running(start, ff_end), 2),
            "main_running_1s_after_first_frame_ms": round(running(ff_end, ff_end + int(1e9)), 2),
            "next10_over_2vsync": sum(1 for f in next10 if f.dur / 1e6 > 2 * VSYNC_MS),
            "next10_ms": [round(f.dur / 1e6, 1) for f in next10],
            "shader_compile_before_first_frame": shader,
            "jit_before_first_frame": jit,
            "verify_class_before_first_frame": total("VerifyClass*", ff_end),
            "open_dex_before_first_frame": total("OpenDexFilesFromOat*", ff_end),
            "bindApplication_children": children("bindApplication"),
            "activityStart_children": children("activityStart"),
        }
    finally:
        tp.close()


def split(args):
    pkg = f"{coldstart.PREFIX}.{args.key}"
    rows = [split_one(t, pkg, args.window_ms) for t in args.traces]
    ok = [r for r in rows if "error" not in r]
    med = {}
    for k, v in (ok[0].items() if ok else []):
        if isinstance(v, (int, float)) and not isinstance(v, bool):
            vals = [r[k] for r in ok if isinstance(r.get(k), (int, float))]
            if vals:
                med[k] = statistics.median(vals)
        elif isinstance(v, dict) and "ms" in v:
            med[k + "_ms"] = statistics.median(r[k]["ms"] for r in ok)
    out = {"package": pkg, "traces": len(rows), "errors": [r for r in rows if "error" in r], "median": med,
           "rows": rows}
    print(json.dumps(out if args.full else {"package": pkg, "traces": len(rows), "errors": out["errors"],
                                            "median": med}, indent=1))
    if args.json:
        json.dump(out, open(args.json, "w"), indent=1)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("capture")
    c.add_argument("-s", "--serial", required=True)
    c.add_argument("--key", required=True)
    c.add_argument("--other", help="the other measured package, stopped before every start too")
    c.add_argument("-n", type=int, default=10)
    c.add_argument("--duration-ms", type=int, default=6000)
    c.add_argument("--out", default="perf-out/traces")
    s = sub.add_parser("split")
    s.add_argument("--key", required=True)
    s.add_argument("--window-ms", type=float)
    s.add_argument("--json")
    s.add_argument("--full", action="store_true")
    s.add_argument("traces", nargs="+")
    a = ap.parse_args()
    capture(a) if a.cmd == "capture" else split(a)


if __name__ == "__main__":
    main()
