"""Renders a quick isometric preview (PNG) of a generated house, seen from the front-left - to check a
design before loading it in the game. Rough colors, no textures.

    python preview.py <out.png>   (renders all three houses side by side)
"""
import struct
import sys
import zlib

import numpy as np

import ponyville_houses as houses

COLORS = {
    "stripped_acacia_log": (176, 96, 80), "stripped_acacia_wood": (160, 86, 72), "birch_planks": (236, 222, 170),
    "hay_block": (206, 176, 78), "oak_planks": (170, 136, 86), "pink_stained_glass_pane": (232, 170, 210),
    "cherry_door": (226, 140, 170), "birch_door": (220, 205, 150), "flowering_azalea_leaves": (110, 160, 80),
    "pink_wool": (238, 140, 180), "white_wool": (240, 240, 240), "dirt_path": (150, 120, 70), "oak_stairs": (170, 136, 86),
    "oak_fence": (170, 136, 86), "lantern": (90, 80, 70), "pink_bed": (235, 130, 170),
}
SKIP = ("air", "jigsaw", "_marker")
A = 10  # pixels per block


def render(structure, width=420, height=420):
    img = np.full((height, width, 3), (178, 222, 232), dtype=np.uint8)
    cells = [(p, c[0].split(":")[1]) for p, c in structure.blocks.items() if not any(k in c[0] for k in SKIP)]
    xs = [p[0] for p, _ in cells]; zs = [p[2] for p, _ in cells]; ys = [p[1] for p, _ in cells]
    cx, cz, top = (min(xs) + max(xs)) / 2, (min(zs) + max(zs)) / 2, max(ys)

    def proj(x, y, z):
        # Nearer the viewer (smaller x + z) is lower on the screen; higher y is higher.
        return (width / 2 + (x - cx - (z - cz)) * A * 0.87, height * 0.62 - (y - top / 2) * A - (x - cx + z - cz) * A * 0.5)

    occupied = {p for p, _ in cells}
    # Far first: viewer stands at -x, -z, above.
    for (x, y, z), name in sorted(cells, key=lambda c: (-(c[0][0] + c[0][2]), c[0][1])):
        base = np.array(COLORS.get(name, (200, 120, 200)), dtype=float)
        faces = []
        if (x, y + 1, z) not in occupied:
            faces.append(([(x, y + 1, z), (x + 1, y + 1, z), (x + 1, y + 1, z + 1), (x, y + 1, z + 1)], 1.0))
        if (x, y, z - 1) not in occupied:
            faces.append(([(x, y, z), (x + 1, y, z), (x + 1, y + 1, z), (x, y + 1, z)], 0.82))
        if (x - 1, y, z) not in occupied:
            faces.append(([(x, y, z), (x, y, z + 1), (x, y + 1, z + 1), (x, y + 1, z)], 0.66))
        for quad, shade in faces:
            fill(img, [proj(*v) for v in quad], (base * shade).astype(np.uint8))
    return img


def fill(img, pts, color):
    pts = np.array(pts)
    x0, y0 = np.floor(pts.min(axis=0)).astype(int)
    x1, y1 = np.ceil(pts.max(axis=0)).astype(int)
    x0, y0 = max(x0, 0), max(y0, 0)
    x1, y1 = min(x1, img.shape[1] - 1), min(y1, img.shape[0] - 1)
    if x1 < x0 or y1 < y0:
        return
    gy, gx = np.mgrid[y0:y1 + 1, x0:x1 + 1]
    inside = np.ones(gx.shape, dtype=bool)
    n = len(pts)
    sign = None
    for i in range(n):
        ax, ay = pts[i]; bx, by = pts[(i + 1) % n]
        cross = (bx - ax) * (gy + 0.5 - ay) - (by - ay) * (gx + 0.5 - ax)
        s = cross >= 0
        if sign is None:
            sign = np.sign(((pts[(i + 2) % n][0] - ax) * (by - ay) - (pts[(i + 2) % n][1] - ay) * (bx - ax)))
        inside &= s if sign <= 0 else ~s | (cross == 0)
    img[y0:y1 + 1, x0:x1 + 1][inside] = color


def save_png(path, img):
    h, w, _ = img.shape
    raw = b"".join(b"\x00" + img[y].tobytes() for y in range(h))
    def chunk(k, d): return struct.pack(">I", len(d)) + k + d + struct.pack(">I", zlib.crc32(k + d) & 0xFFFFFFFF)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    panels = [render(build()) for build in (houses.cottage, houses.family_home, houses.work_home)]
    save_png(sys.argv[1], np.concatenate(panels, axis=1))
