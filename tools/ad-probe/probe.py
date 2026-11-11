"""Stage 0 probe: for each candidate source, fetch a live media playlist a few
times and report whether Twitch stitched ads into it. Anonymous requests only."""
import json, random, re, sys, time, uuid, urllib.request, urllib.parse, ssl, os
from concurrent.futures import ThreadPoolExecutor

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
HASH = "ed230aa1e33e07eebb8928504583da78a5173989fadfb1ac94be06a04f3cdbe9"
OUT = os.path.join(os.path.dirname(__file__), "samples")
os.makedirs(OUT, exist_ok=True)


def http(url, data=None, headers=None, proxy=None, timeout=10):
    h = {"User-Agent": UA}
    h.update(headers or {})
    req = urllib.request.Request(url, data=data, headers=h)
    handlers = []
    if proxy:
        handlers.append(urllib.request.ProxyHandler({"http": proxy, "https": proxy}))
    opener = urllib.request.build_opener(*handlers)
    with opener.open(req, timeout=timeout) as r:
        return r.status, r.read().decode("utf-8", "replace")


def token(login, platform, player_type, client_id, proxy=None):
    body = json.dumps({
        "extensions": {"persistedQuery": {"sha256Hash": HASH, "version": 1}},
        "operationName": "PlaybackAccessToken",
        "variables": {"isLive": True, "login": login, "isVod": False, "vodID": "",
                      "playerType": player_type, "platform": platform},
    }).encode()
    _, txt = http("https://gql.twitch.tv/gql", body, {
        "Client-Id": client_id, "X-Device-Id": uuid.uuid4().hex, "Content-Type": "application/json"}, proxy=proxy)
    t = json.loads(txt)["data"]["streamPlaybackAccessToken"]
    return t["signature"], t["value"]


def usher(login, platform, sig, tok):
    q = urllib.parse.urlencode({
        "allow_source": "true", "allow_audio_only": "true", "fast_bread": "true",
        "include_unavailable": "true", "p": random.randint(0, 9999999), "platform": platform,
        "sig": sig, "supported_codecs": "av1,h265,h264", "token": tok})
    return f"https://usher.ttvnw.net/api/v2/channel/hls/{login}.m3u8?{q}"


AD_TITLES = ("Amazon", "Adform", "DCM")


def ad_info(media):
    """Same rules as ExoPlayerService.checkPlaylist, but over the whole list."""
    titles = re.findall(r"#EXTINF:[\d.]+,(.*)", media)
    ad_seg = [t for t in titles if t.split("|")[0] in AD_TITLES]
    live_seg = [t for t in titles if t.startswith("live")]
    dr = re.findall(r"#EXT-X-DATERANGE:(.*)", media)
    ad_dr = [d for d in dr if 'ID="stitched-ad-' in d or 'twitch-stitched-ad' in d or "X-TV-TWITCH-AD-" in d]
    return {"segments": len(titles), "ad_segments": len(ad_seg), "live_segments": len(live_seg),
            "ad_dateranges": len(ad_dr), "other_titles": sorted(set(t.split("|")[0] for t in titles) - {"live", *AD_TITLES})}


def pick_variant(master):
    lines = master.splitlines()
    best = None
    for i, l in enumerate(lines):
        if l.startswith("#EXT-X-STREAM-INF"):
            bw = int(re.search(r"BANDWIDTH=(\d+)", l).group(1))
            url = lines[i + 1] if not lines[i + 1].startswith("#") else lines[i + 2]
            if "audio_only" in l or "audio_only" in url:
                continue
            if best is None or bw > best[0]:
                best = (bw, url)
    return best[1] if best else None


def probe_master(name, login, master, polls=4, gap=4):
    variant = pick_variant(master)
    if not variant:
        return {"source": name, "error": "no variant"}
    hits, info = 0, None
    for i in range(polls):
        _, media = http(variant)
        info = ad_info(media)
        if info["ad_segments"] or info["ad_dateranges"]:
            hits += 1
            with open(os.path.join(OUT, f"{login}_{name.replace('/', '_').replace(':', '_')}_{int(time.time())}.m3u8"), "w", encoding="utf-8") as f:
                f.write(media)
        if i < polls - 1:
            time.sleep(gap)
    return {"source": name, "ad_polls": f"{hits}/{polls}", "last": info}


def run_direct(login, platform, player_type, client_id, proxy=None):
    name = f"{platform}/{player_type}@{client_id[:4]}" + ("+tokproxy" if proxy else "")
    try:
        sig, tok = token(login, platform, player_type, client_id, proxy)
        flags = json.loads(tok)
        _, master = http(usher(login, platform, sig, tok))
        r = probe_master(name, login, master)
        r["token_flags"] = {k: flags.get(k) for k in ("server_ads", "show_ads", "hide_ads", "adblock", "geoblock_reason", "turbo", "subscriber")}
        r["usher_country"] = (re.search(r'USER-COUNTRY="(\w+)"', master) or [None, None])[1]
        return r
    except Exception as e:
        return {"source": name, "error": repr(e)[:160]}


def run_custom_proxy(login, url_tpl):
    url = "https://" + url_tpl.replace("$channel", login) + "?" + urllib.parse.urlencode(
        {"allow_source": "true", "allow_audio_only": "true", "fast_bread": "true",
         "include_unavailable": "true", "supported_codecs": "av1,h265,h264"})
    t0 = time.time()
    try:
        status, master = http(url, timeout=6)
        if "#EXTM3U" not in master:
            return {"source": url_tpl, "error": f"HTTP {status} non-m3u8: {master[:80]}"}
        r = probe_master(url_tpl, login, master)
        r["latency_s"] = round(time.time() - t0, 2)
        r["usher_country"] = (re.search(r'USER-COUNTRY="(\w+)"', master) or [None, None])[1]
        return r
    except Exception as e:
        return {"source": url_tpl, "error": repr(e)[:160], "latency_s": round(time.time() - t0, 2)}


CUSTOM = ["eu.luminous.dev/live/$channel", "eu2.luminous.dev/live/$channel", "eu3.luminous.dev/live/$channel",
          "as.luminous.dev/live/$channel", "lb-eu.cdn-perfprod.com/live/$channel", "lb-eu2.cdn-perfprod.com/live/$channel",
          "lb-eu3.cdn-perfprod.com/live/$channel", "lb-eu4.cdn-perfprod.com/live/$channel", "lb-eu5.cdn-perfprod.com/live/$channel",
          "lb-na.cdn-perfprod.com/live/$channel", "lb-sa.cdn-perfprod.com/live/$channel", "lb-as.cdn-perfprod.com/live/$channel"]
COMBOS = [("web", "site"), ("android", "autoplay"), ("web", "embed"), ("web", "popout"), ("ios", "site"),
          ("android", "site"), ("web", "autoplay"), ("web", "picture-by-picture"), ("web", "frontpage"), ("web", "thunderdome")]
CLIENTS = ["kd1unb4b3q4t58fwlpcbzcbnm76a8fp", "ue6666qo983tsx6so1t0vnawi233wa", "kimne78kx3ncx6brgo4mv6wki5h1ko"]

if __name__ == "__main__":
    login = sys.argv[1]
    jobs = []
    with ThreadPoolExecutor(12) as ex:
        for c in CLIENTS:
            for p, t in COMBOS:
                jobs.append(ex.submit(run_direct, login, p, t, c))
        jobs.append(ex.submit(run_direct, login, "web", "site", CLIENTS[0], "http://firefox.api.cdn-perfprod.com:2023"))
        for u in CUSTOM:
            jobs.append(ex.submit(run_custom_proxy, login, u))
        res = [j.result() for j in jobs]
    print(json.dumps({"channel": login, "time": time.strftime("%H:%M:%S"), "results": res}, ensure_ascii=False))
