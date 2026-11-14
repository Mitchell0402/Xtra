"""Writes the fixed frosted-glass grain used by the glass fallback material (stage G).

White pixels with random alpha, seeded, so every build and every screenshot test draws the same grain.
Run from the repo root: python tools/glass/make_noise.py
"""
import random
import struct
import zlib

SIZE = 96
SEED = 20261114
OUT = "app/src/main/res/drawable-nodpi/glass_noise.png"


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def main():
    rnd = random.Random(SEED)
    rows = bytearray()
    for _ in range(SIZE):
        rows.append(0)  # filter: none
        for _ in range(SIZE):
            # grey+alpha: white grain, alpha roughly gaussian around the middle so no pixel dominates
            a = int(max(0, min(255, rnd.gauss(128, 64))))
            rows += bytes((255, a))
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 4, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(rows), 9))
    png += chunk(b"IEND", b"")
    with open(OUT, "wb") as f:
        f.write(png)
    print(OUT, len(png), "bytes")


if __name__ == "__main__":
    main()
