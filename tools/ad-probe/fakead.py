"""Local test proxy that stitches a fake mid-roll into a real Twitch live stream.

The app is pointed at http://127.0.0.1:8765/live/$channel as a custom proxy (adb reverse).
Content comes from channel A; during each ad window the segments are replaced by
segments of channel B ("the ad"), titled and tagged the way Twitch tags stitched ads,
so a leaked ad is easy to recognise on a screenshot.
Usage: fakead.py <ad_channel> [cycle_s] [ad_start_s] [ad_len_s]
"""
import datetime as dt, re, sys, os, threading, time, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
sys.path.insert(0, os.path.dirname(__file__))
from probe import http, token, usher, pick_variant, CLIENTS

AD_CH = sys.argv[1]
CYCLE, AD_START, AD_LEN = (float(x) for x in (sys.argv[2:5] if len(sys.argv) >= 5 else (150, 45, 40)))
T0 = time.time()
lock = threading.Lock()
ad_map = {}          # content media sequence -> ad segment uri
ad_state = {"variant": None, "used": set()}


def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)


def ad_window(t):
    k = int((t - T0) // CYCLE)
    s = T0 + k * CYCLE + AD_START
    return (s, s + AD_LEN) if s <= t < s + AD_LEN else None


def ad_segments():
    if ad_state["variant"] is None:
        sig, tok = token(AD_CH, "web", "site", CLIENTS[0])
        ad_state["variant"] = pick_variant(http(usher(AD_CH, "web", sig, tok))[1])
    media = http(ad_state["variant"])[1]
    return re.findall(r"#EXTINF:[\d.]+,.*\n(https?://\S+)", media)


def rewrite_media(media):
    out, seq = [], None
    lines = media.splitlines()
    m = re.search(r"#EXT-X-MEDIA-SEQUENCE:(\d+)", media)
    seq = int(m.group(1)) if m else 0
    pdt = None
    in_ad = False
    i = 0
    fresh_ads = None
    first = True
    disc_at = 0
    while i < len(lines):
        l = lines[i]
        if l.startswith("#EXT-X-TWITCH-PREFETCH"):
            i += 1; continue
        if l.startswith("#EXT-X-PROGRAM-DATE-TIME:"):
            pdt = dt.datetime.fromisoformat(l.split(":", 1)[1].replace("Z", "+00:00")).timestamp()
            pdt_line = l
            i += 1; continue
        if l.startswith("#EXTINF"):
            uri = lines[i + 1]
            win = ad_window(pdt) if pdt else None
            if first:
                # discontinuity sequence = ad boundaries passed before the first segment
                n = 0
                k = 0
                while T0 + k * CYCLE + AD_START <= pdt:
                    n += 1
                    if T0 + k * CYCLE + AD_START + AD_LEN <= pdt:
                        n += 1
                    k += 1
                out.insert(disc_at, f"#EXT-X-DISCONTINUITY-SEQUENCE:{n}")
            if win and not in_ad and not first:
                out.append("#EXT-X-DISCONTINUITY")
            if win and not in_ad:
                start = dt.datetime.fromtimestamp(win[0], dt.timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
                out.append(f'#EXT-X-DATERANGE:ID="stitched-ad-fake-{int(win[0])}",CLASS="twitch-stitched-ad",START-DATE="{start}",DURATION={AD_LEN:.3f},X-TV-TWITCH-AD-ROLL-TYPE="MIDROLL"')
            if not win and in_ad and not first:
                out.append("#EXT-X-DISCONTINUITY")
            first = False
            in_ad = bool(win)
            out.append(pdt_line)
            if win:
                with lock:
                    if seq not in ad_map:
                        if fresh_ads is None:
                            fresh_ads = ad_segments()
                        cand = [u for u in fresh_ads if u not in ad_state["used"]] or fresh_ads
                        ad_map[seq] = cand[0]
                        ad_state["used"].add(cand[0])
                    uri = ad_map[seq]
                out.append(re.sub(r",.*$", ",Amazon|fake-creative", l))
            else:
                out.append(l)
            out.append(uri)
            seq += 1
            i += 2; continue
        out.append(l)
        if l.startswith("#EXT-X-MEDIA-SEQUENCE"):
            disc_at = len(out)
        i += 1
    return "\n".join(out) + "\n", in_ad


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send(self, body, ctype="application/vnd.apple.mpegurl"):
        b = body.encode()
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self):
        u = urllib.parse.urlparse(self.path)
        try:
            if u.path.startswith("/live/"):
                ch = u.path.split("/")[2]
                sig, tok = token(ch, "web", "site", CLIENTS[0])
                master = http(usher(ch, "web", sig, tok))[1]
                master = re.sub(r"^(https?://\S+\.m3u8\S*)$",
                                lambda m: "http://127.0.0.1:8765/media?u=" + urllib.parse.quote(m.group(1), safe=""),
                                master, flags=re.M)
                log("master for", ch)
                self.send(master)
            elif u.path == "/media":
                real = urllib.parse.parse_qs(u.query)["u"][0]
                body, in_ad = rewrite_media(http(real)[1])
                if in_ad:
                    log("serving media playlist with fake ad at the end")
                self.send(body)
            else:
                self.send_response(404); self.end_headers()
        except Exception as e:
            log("error", repr(e)[:200])
            self.send_response(502); self.end_headers()


log(f"fake ad proxy up; ad channel={AD_CH}, cycle={CYCLE}s, ad at +{AD_START}s for {AD_LEN}s")
ThreadingHTTPServer(("127.0.0.1", 8765), H).serve_forever()
