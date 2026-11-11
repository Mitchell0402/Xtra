"""Long-running monitor: per channel and source, keep one session open and poll
the media playlist every few seconds; log every ad break seen (roll type, length)."""
import json, re, sys, time, os, threading
sys.path.insert(0, os.path.dirname(__file__))
from probe import http, token, usher, pick_variant, CLIENTS, OUT
import urllib.parse

MINUTES = float(sys.argv[1])
CHANNELS = sys.argv[2].split(",")
SOURCES = [("direct", "web", "site"), ("direct", "android", "autoplay"), ("direct", "web", "popout"),
           ("proxy", "eu.luminous.dev/live/$channel", None), ("proxy", "as.luminous.dev/live/$channel", None)]
lock = threading.Lock()
log = open(os.path.join(os.path.dirname(__file__), "monitor.log"), "a", encoding="utf-8")


def emit(**kw):
    with lock:
        log.write(json.dumps(kw) + "\n"); log.flush()


def master_for(ch, src):
    kind, a, b = src
    if kind == "direct":
        sig, tok = token(ch, a, b, CLIENTS[0])
        return http(usher(ch, a, sig, tok))[1]
    url = "https://" + a.replace("$channel", ch) + "?" + urllib.parse.urlencode(
        {"allow_source": "true", "allow_audio_only": "true", "fast_bread": "true", "include_unavailable": "true", "supported_codecs": "av1,h265,h264"})
    t0 = time.time()
    m = http(url, timeout=8)[1]
    emit(ch=ch, src=a, event="master_latency", s=round(time.time() - t0, 2))
    return m


def watch(ch, src):
    name = src[1] if src[0] == "proxy" else f"{src[1]}/{src[2]}"
    try:
        variant = pick_variant(master_for(ch, src))
    except Exception as e:
        emit(ch=ch, src=name, event="error", err=repr(e)[:150]); return
    end = time.time() + MINUTES * 60
    seen = set(); first = True; polls = 0; ad_polls = 0
    while time.time() < end:
        try:
            media = http(variant)[1]
        except Exception as e:
            emit(ch=ch, src=name, event="poll_error", err=repr(e)[:120]); time.sleep(5); continue
        polls += 1
        ads = re.findall(r'#EXT-X-DATERANGE:ID="(stitched-ad-[^"]+)".*?X-TV-TWITCH-AD-ROLL-TYPE="(\w+)"', media)
        ads += [(i, "?") for i in re.findall(r'#EXT-X-DATERANGE:ID="(stitched-ad-[^"]+)"', media) if i not in [x[0] for x in ads]]
        titled = len(re.findall(r"#EXTINF:[\d.]+,(Amazon|Adform|DCM)", media))
        if ads or titled:
            ad_polls += 1
        for ad_id, roll in ads:
            if ad_id not in seen:
                seen.add(ad_id)
                dur = re.search(re.escape(ad_id) + r'".*?DURATION=([\d.]+)', media)
                pod = re.search(re.escape(ad_id) + r'".*?POD-LENGTH="(\d+)"', media)
                emit(ch=ch, src=name, event="ad", roll=roll, at_start=first, dur=dur and float(dur.group(1)),
                     pod=pod and int(pod.group(1)), t=time.strftime("%H:%M:%S"))
                with open(os.path.join(OUT, f"mon_{ch}_{name.replace('/', '_')}_{ad_id}.m3u8"), "w", encoding="utf-8") as f:
                    f.write(media)
        first = False
        time.sleep(4)
    emit(ch=ch, src=name, event="done", polls=polls, ad_polls=ad_polls, ads=len(seen))


threads = [threading.Thread(target=watch, args=(c, s)) for c in CHANNELS for s in SOURCES]
for t in threads: t.start()
for t in threads: t.join()
emit(event="all_done")
