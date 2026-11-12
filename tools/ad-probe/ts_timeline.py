#!/usr/bin/env python3
"""Prints the video timeline of an MPEG-TS recording: continuous runs of PTS and the jumps between them.

Used to check that a live recording left out stitched ad breaks: a skipped break shows up as a forward PTS jump
about as long as the break (the content timeline went on while the ads played), while recorded ads would add
their own runs with a different PTS base and make the summed run time match the wall-clock recording time.

Usage: ts_timeline.py <file.ts>
"""
import sys

PKT = 188
WRAP = 1 << 33


def pts_of(payload):
    if len(payload) < 14 or payload[0:3] != b"\x00\x00\x01":
        return None
    stream_id = payload[3]
    if not 0xE0 <= stream_id <= 0xEF:
        return None
    if payload[7] & 0x80 == 0:
        return None
    p = payload[9:14]
    return ((p[0] >> 1) & 0x07) << 30 | p[1] << 22 | (p[2] >> 1) << 15 | p[3] << 7 | p[4] >> 1


def main(path):
    runs = []  # [start_pts, last_pts]
    with open(path, "rb") as f:
        data = f.read()
    i = data.find(b"\x47")
    while i + PKT <= len(data):
        pkt = data[i:i + PKT]
        i += PKT
        if pkt[0] != 0x47 or not pkt[1] & 0x40:
            continue
        afc = (pkt[3] >> 4) & 3
        off = 4
        if afc in (2, 3):
            off += 1 + pkt[4]
        if afc == 2 or off >= PKT:
            continue
        pts = pts_of(pkt[off:])
        if pts is None:
            continue
        if runs:
            delta = (pts - runs[-1][1]) % WRAP
            if delta > WRAP // 2:
                delta -= WRAP
            if 0 <= delta <= 90000 * 2 or -90000 // 2 <= delta < 0:  # normal frame step or B-frame reorder
                runs[-1][1] = max(runs[-1][1], pts) if delta >= 0 else runs[-1][1]
                continue
        runs.append([pts, pts])
    total = 0.0
    print(f"{len(runs)} continuous run(s)")
    for n, (a, b) in enumerate(runs):
        length = ((b - a) % WRAP) / 90000
        total += length
        gap = ""
        if n + 1 < len(runs):
            jump = ((runs[n + 1][0] - b) % WRAP)
            jump = jump - WRAP if jump > WRAP // 2 else jump
            gap = f", then PTS jumps {jump / 90000:+.1f} s"
        print(f"  run {n + 1}: {length:8.1f} s{gap}")
    print(f"recorded video: {total:.1f} s ({total / 60:.1f} min)")


if __name__ == "__main__":
    main(sys.argv[1])
