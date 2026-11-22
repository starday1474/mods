"""재각성의 결정(reawakening_crystal) 16x16 아이콘을 만든다. 빛나는 보라색 결정.

사용법: python3 tools/textures/make_crystal.py mod/src/main/resources/assets/goddessrpg/textures/item/reawakening_crystal.png
"""
from __future__ import annotations

import sys

from PIL import Image

# palette: outline, dark, mid, light, highlight, glow
O = (40, 12, 64, 255)
D = (88, 34, 140, 255)
M = (142, 70, 214, 255)
L = (190, 128, 245, 255)
H = (244, 222, 255, 255)
G = (170, 90, 255, 90)
_ = None

# a tall central shard with two smaller side shards, lit from the upper left
ROWS = [
    "................",
    ".......O........",
    "......OHO.......",
    "......OHLO......",
    ".....OHLMO......",
    ".....OLLMO..O...",
    "..O..OLMMDO.OLO.",
    ".OLO.OLMMDOOLMO.",
    ".OLMOOLMMDOOMDO.",
    ".OLMOOLMMDOOMDO.",
    "..OMDOLMDDOODO..",
    "..OMDOOMDDOODO..",
    "...ODOOMDDOODO..",
    "....OOOODDOOO...",
    "......OOOOO.....",
    "................",
]
COLORS = {"O": O, "D": D, "M": M, "L": L, "H": H}


def build() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(ROWS):
        for x, ch in enumerate(row):
            if ch in COLORS:
                px[x, y] = COLORS[ch]
    # soft glow: transparent pixels next to the outline get a faint violet halo
    for y in range(16):
        for x in range(16):
            if px[x, y][3] != 0:
                continue
            near = any(
                0 <= x + dx < 16 and 0 <= y + dy < 16 and ROWS[y + dy][x + dx] == "O"
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
            )
            if near and 0 < x < 15 and 0 < y < 15:
                px[x, y] = G
    # sparkles
    px[13, 2] = H
    px[2, 4] = L
    return img


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "reawakening_crystal.png"
    build().save(out)
    print("wrote", out)
