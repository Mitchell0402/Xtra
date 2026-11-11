"""Stage 3 research: which platform/playerType combos that allow more than 360p still get
mid-rolls. Keeps one anonymous session per channel and combo, polls its best variant's media
playlist and logs every ad seen to hunt.log (JSON lines). Summarize with hunt.py --report.
Usage: hunt.py <minutes> <channel,channel,...>
       hunt.py --report"""
import json, re, sys, time, os, threading, random
sys.path.insert(0, os.path.dirname(__file__))
from probe import http, token, usher, pick_variant, CLIENTS

LOG = os.path.join(os.path.dirname(__file__), "hunt.log")
COMBOS = [("web", "site"), ("android", "autoplay"),  # references: always ads / clean but 360p
          ("android", "pulsar"), ("android", "embed"), ("ios", "embed"), ("web", "frontpage"),
          ("android", "frontpage"), ("web", "channel_home_live"), ("web", "picture-by-picture-CORE"),
          ("web", "mobile_web"), ("web", "dashboard"), ("web", "squad"), ("web", "onboarding"),
          ("web", "mod_view"), ("web", "highlighter"), ("web", "feed"), ("web", "clips-viewing"),
          ("web", "amazon"), ("tvapps", "site"), ("web", "live_preview")]
lock = threading.Lock()


def emit(**kw):
    with lock, open(LOG, "a", encoding="utf-8") as f:
        f.write(json.dumps(kw) + "\n")


def watch(ch, platform, player_type, minutes):
    name = f"{platform}/{player_type}"
    time.sleep(random.uniform(0, 20))
    try:
        sig, tok = token(ch, platform, player_type, CLIENTS[0])
        master = http(usher(ch, platform, sig, tok))[1]
        variant = pick_variant(master)
        height = max([int(h) for h in re.findall(r"RESOLUTION=\d+x(\d+)", master)] or [0])
    except Exception as e:
        emit(ch=ch, src=name, event="error", err=repr(e)[:150]); return
    emit(ch=ch, src=name, event="start", height=height)
    end = time.time() + minutes * 60
    seen = set()
    while time.time() < end:
        try:
            media = http(variant)[1]
        except Exception as e:
            emit(ch=ch, src=name, event="poll_error", err=repr(e)[:120]); time.sleep(6); continue
        ads = re.findall(r'#EXT-X-DATERANGE:ID="(stitched-ad-[^"]+)"', media)
        titled = [t for t in re.findall(r"#EXTINF:[\d.]+,(.*)", media) if t and t != "live"]
        if (ads or titled) and not seen.intersection(ads):
            emit(ch=ch, src=name, event="ad", ids=ads, titles=sorted(set(titled))[:3], t=int(time.time()))
        seen.update(ads)
        time.sleep(6)
    emit(ch=ch, src=name, event="done")


def report():
    rows = [json.loads(l) for l in open(LOG, encoding="utf-8")]
    heights = {(r["ch"], r["src"]): r["height"] for r in rows if r.get("event") == "start"}
    ads = [r for r in rows if r.get("event") == "ad"]
    # a break: ads on a channel within 90 s of each other
    breaks = []
    for r in sorted(ads, key=lambda r: (r["ch"], r["t"])):
        if breaks and breaks[-1]["ch"] == r["ch"] and r["t"] - breaks[-1]["t1"] < 90:
            breaks[-1]["src"].add(r["src"]); breaks[-1]["t1"] = r["t"]
        else:
            breaks.append({"ch": r["ch"], "t0": r["t"], "t1": r["t"], "src": {r["src"]}})
    srcs = [f"{p}/{t}" for p, t in COMBOS]
    print(f"{len(breaks)} breaks")
    for b in breaks:
        watched = [s for s in srcs if (b["ch"], s) in heights]
        print(time.strftime("%H:%M", time.localtime(b["t0"])), b["ch"], f"{len(b['src'])}/{len(watched)} sources with ads; clean:",
              [s for s in watched if s not in b["src"]])
    print("per source: breaks with ads / breaks while watched, max height")
    for s in srcs:
        n = sum(1 for b in breaks if (b["ch"], s) in heights)
        k = sum(1 for b in breaks if s in b["src"])
        h = max([v for (c, x), v in heights.items() if x == s] or [0])
        print(f"  {s:32} {k}/{n}  {h}p")


if __name__ == "__main__":
    if sys.argv[1] == "--report":
        report(); sys.exit()
    minutes = float(sys.argv[1])
    threads = [threading.Thread(target=watch, args=(c, p, t, minutes)) for c in sys.argv[2].split(",") for p, t in COMBOS]
    for t in threads: t.start()
    for t in threads: t.join()
    emit(event="all_done")
