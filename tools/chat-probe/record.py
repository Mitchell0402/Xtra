#!/usr/bin/env python3
"""Records real Twitch chat (anonymous, read-only IRC) for the chat list stress test.

    python record.py --top 5                 # list the busiest live channels
    python record.py <login> --seconds 180   # record PRIVMSG/USERNOTICE lines with arrival times

Output: samples/<login>-<UTC date>.irc, one line per message: "<ms since start>\t<raw IRC line>".
User names, ids and @mentions are replaced by same-length pseudonyms so emote ranges in the tags
stay valid and no real account names end up in the repository.
"""
import argparse
import hashlib
import json
import os
import re
import socket
import string
import time
import urllib.request
from datetime import datetime, timezone

GQL_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"  # Twitch web client id, the same one the app uses for public data
HERE = os.path.dirname(os.path.abspath(__file__))


def top_channels(n):
    q = {"query": "query { streams(first: %d) { edges { node { broadcaster { login } viewersCount game { name } } } } }" % n}
    req = urllib.request.Request("https://gql.twitch.tv/gql", json.dumps(q).encode(), {"Client-Id": GQL_CLIENT_ID})
    data = json.load(urllib.request.urlopen(req, timeout=15))
    return [(e["node"]["broadcaster"]["login"], e["node"]["viewersCount"], (e["node"].get("game") or {}).get("name"))
            for e in data["data"]["streams"]["edges"]]


class Anonymizer:
    def __init__(self, salt):
        self.salt = salt
        self.names = {}

    def name(self, real):
        if not real:
            return real
        key = real.lower()
        if key not in self.names:
            h = hashlib.sha256((self.salt + key).encode()).hexdigest()
            alphabet = string.ascii_lowercase + string.digits
            fake = "".join(alphabet[int(h[i:i + 2], 16) % len(alphabet)] for i in range(0, 2 * len(real), 2))[:len(real)]
            self.names[key] = "u" + fake[1:] if len(fake) > 1 else "u"
        fake = self.names[key]
        return fake.upper() if real.isupper() else fake

    def number(self, real):
        return str(int(hashlib.sha256((self.salt + real).encode()).hexdigest()[:12], 16) % 10 ** max(len(real), 1)) if real else real

    def line(self, raw):
        tags, rest = (raw[1:].split(" ", 1) if raw.startswith("@") else ("", raw))
        out_tags = []
        for t in tags.split(";") if tags else []:
            k, _, v = t.partition("=")
            if k in ("display-name", "login", "reply-parent-display-name", "reply-parent-user-login", "reply-thread-parent-user-login",
                     "reply-thread-parent-display-name", "msg-param-login", "msg-param-displayName", "msg-param-recipient-user-name",
                     "msg-param-recipient-display-name", "msg-param-sender-login", "msg-param-sender-name"):
                v = self.name(v)
            elif k in ("user-id", "reply-parent-user-id", "msg-param-recipient-id", "msg-param-user-id"):
                v = self.number(v)
            elif k == "reply-parent-msg-body":
                v = re.sub(r"@(\w+)", lambda m: "@" + self.name(m.group(1)), v)
            out_tags.append(f"{k}={v}")
        m = re.match(r":(\w+)!\w+@\w+(\.tmi\.twitch\.tv .*)", rest)
        if m:
            n = self.name(m.group(1))
            rest = f":{n}!{n}@{n}{m.group(2)}"
        cmd, sep, text = rest.partition(" :")
        if sep and " PRIVMSG " in cmd or " USERNOTICE " in cmd:
            text = re.sub(r"@(\w+)", lambda mm: "@" + self.name(mm.group(1)), text)
        return ("@" + ";".join(out_tags) + " " if out_tags else "") + cmd + (sep + text if sep else "")


def record(login, seconds):
    s = socket.create_connection(("irc.chat.twitch.tv", 6667), timeout=30)
    nick = "justinfan%d" % (int(time.time()) % 100000)
    s.sendall(b"CAP REQ :twitch.tv/tags twitch.tv/commands\r\n")
    s.sendall(f"PASS SCHMOOPIIE\r\nNICK {nick}\r\nJOIN #{login}\r\n".encode())
    anon = Anonymizer(os.urandom(8).hex())
    os.makedirs(os.path.join(HERE, "samples"), exist_ok=True)
    path = os.path.join(HERE, "samples", f"{login}-{datetime.now(timezone.utc):%Y%m%d}.irc")
    start = time.monotonic()
    buf, count = b"", 0
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        out.write(f"# channel={login} recorded={datetime.now(timezone.utc):%Y-%m-%dT%H:%MZ} seconds={seconds}\n")
        while time.monotonic() - start < seconds:
            try:
                data = s.recv(65536)
            except socket.timeout:
                continue
            if not data:
                break
            buf += data
            *lines, buf = buf.split(b"\r\n")
            for raw in lines:
                line = raw.decode("utf-8", "replace")
                if line.startswith("PING"):
                    s.sendall(b"PONG :tmi.twitch.tv\r\n")
                elif " PRIVMSG " in line or " USERNOTICE " in line:
                    out.write(f"{int((time.monotonic() - start) * 1000)}\t{anon.line(line)}\n")
                    count += 1
    s.close()
    print(f"{count} messages in {seconds} s ({count / seconds:.1f}/s) -> {path}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("login", nargs="?")
    ap.add_argument("--seconds", type=int, default=180)
    ap.add_argument("--top", type=int)
    a = ap.parse_args()
    if a.top:
        for login, viewers, game in top_channels(a.top):
            print(f"{viewers:>8}  {login}  ({game})")
    else:
        record(a.login, a.seconds)
