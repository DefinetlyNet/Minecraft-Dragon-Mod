"""Dragon egg textures, models and blockstates.

Run from the project root:  python3 tools/make_eggs.py

Writes, for each kind of egg (red, frost, lightning, black):
  textures/block/<kind>_dragon_egg.png                    (whole)
  textures/block/<kind>_dragon_egg_slightly_cracked.png   (hatch=1)
  textures/block/<kind>_dragon_egg_very_cracked.png       (hatch=2)
plus models/block/dragon_egg.json (the egg shape every egg uses), the egg models, item models and blockstates.

The egg shape is the vanilla dragon egg's stack of boxes, but with its own UV layout: every side of every box takes
the rows of the texture at its own height (row 0 at the very top of the egg, row 15 at the bottom), so the colours run
smoothly from the bottom of the egg to the top. The tops and bottoms of the boxes (the little rings where the egg
narrows) take the texture row at that height.
"""
import json
import math
import os
import random

from PIL import Image

ASSETS = "src/main/resources/assets/wildcreatures"
TEX = f"{ASSETS}/textures/block"

# the vanilla dragon egg's boxes: (x1, y1, x2, y2), square in x and z
BOXES = [(6, 15, 10, 16), (5, 14, 11, 15), (5, 13, 11, 14), (3, 11, 13, 13),
         (2, 8, 14, 11), (1, 3, 15, 8), (2, 1, 14, 3), (3, 0, 13, 1)]


def egg_model():
    elements = []
    for x1, y1, x2, y2 in BOXES:
        side = [x1, 16 - y2, x2, 16 - y1]
        flipped = [x2, 16 - y2, x1, 16 - y1]
        elements.append({
            "from": [x1, y1, x1], "to": [x2, y2, x2],
            "faces": {
                "up": {"uv": [x1, 16 - y2, x2, 17 - y2], "texture": "#all"},
                "down": {"uv": [x1, 15 - y1, x2, 16 - y1], "texture": "#all"},
                "north": {"uv": side, "texture": "#all"},
                "south": {"uv": side, "texture": "#all"},
                "east": {"uv": flipped, "texture": "#all"},
                "west": {"uv": flipped, "texture": "#all"},
            },
        })
    return {"parent": "minecraft:block/block", "textures": {"particle": "#all"}, "elements": elements}


def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(stops, t):
    """Smooth colour ramp through (position, colour) stops."""
    t = max(0.0, min(1.0, t))
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        if t <= p1:
            f = (t - p0) / (p1 - p0) if p1 > p0 else 0
            f = f * f * (3 - 2 * f)
            return mix(c0, c1, f)
    return stops[-1][1]


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


KINDS = {
    "red": {
        "stops": [(0.0, hexc("4a0b10")), (0.45, hexc("9c1a17")), (0.8, hexc("c8402a")), (1.0, hexc("e0663a"))],
        "fleck": [hexc("f2b33d"), hexc("ffd27a"), hexc("d98a2b")],
        "crack": hexc("2a0507"), "glow": hexc("ffb347"), "seed": 11,
    },
    "frost": {
        "stops": [(0.0, hexc("6fa8d6")), (0.4, hexc("a9d3ef")), (0.75, hexc("d8eefa")), (1.0, hexc("f6fbff"))],
        "fleck": [hexc("4f8fc9"), hexc("7fb6e6"), hexc("ffffff")],
        "crack": hexc("3d6e99"), "glow": hexc("9ef0ff"), "seed": 23,
    },
    "lightning": {
        "stops": [(0.0, hexc("0d1440")), (0.45, hexc("1c2c78")), (0.8, hexc("2f4aa6")), (1.0, hexc("4166c8"))],
        "fleck": [hexc("c99a2e"), hexc("edbe4e")],
        "crack": hexc("05081f"), "glow": hexc("ffe58a"), "seed": 37,
    },
    "black": {
        "stops": [(0.0, hexc("09070d")), (0.5, hexc("16111e")), (0.85, hexc("261e33")), (1.0, hexc("32283f"))],
        "fleck": [hexc("5b3a8f"), hexc("7a52b8")],
        "crack": hexc("000000"), "glow": hexc("d8b4ff"), "seed": 53,
    },
}


def base_egg(kind):
    k = KINDS[kind]
    rnd = random.Random(k["seed"])
    img = Image.new("RGB", (16, 16))
    px = img.load()
    for v in range(16):
        h = 1.0 - (v + 0.5) / 16.0              # 1 at the top of the egg, 0 at the bottom
        for u in range(16):
            c = ramp(k["stops"], h)
            # a little rounder: each face is lighter down its middle
            c = shade(c, 1.06 - 0.16 * ((u - 7.5) / 7.5) ** 2)
            # a faint mottle so the shell isn't flat
            c = shade(c, 1.0 + rnd.uniform(-0.035, 0.035))
            px[u, v] = c
    # kind-specific markings
    if kind == "red":
        # overlapping scale arcs, darker on their lower edge, lighter on top, in staggered rows
        for row, v0 in enumerate(range(2, 15, 3)):
            off = 0 if row % 2 == 0 else 2
            for u0 in range(-off, 16, 4):
                for du in range(4):
                    u = u0 + du
                    if 0 <= u < 16:
                        edge = v0 + (1 if du in (0, 3) else 2)
                        if edge < 16:
                            px[u, edge] = shade(px[u, edge], 0.78)
                        if v0 < 16 and du in (1, 2):
                            px[u, v0] = shade(px[u, v0], 1.12)
        flecks(px, rnd, k["fleck"], 12, rows=(1, 14))
    elif kind == "frost":
        # pale crystal streaks running diagonally, and blue flecks
        for start in (1, 7, 12):
            u, v = start, rnd.randint(0, 3)
            while v < 15 and 0 <= u < 16:
                px[u, v] = mix(px[u, v], hexc("ffffff"), 0.55)
                v += 1
                u += 1 if (v % 2 == 0) else 0
        flecks(px, rnd, k["fleck"], 18, rows=(2, 15))
    elif kind == "lightning":
        # golden lightning bolts zigzagging down the shell
        for bolt in ([(9, 0), (7, 3), (10, 4), (6, 9), (9, 10), (7, 15)],
                     [(14, 6), (12, 9), (14, 10), (12, 14)]):
            for i, (u, v) in enumerate(polyline(bolt)):
                if 0 <= u < 16 and 0 <= v < 16:
                    px[u, v] = hexc("ffe58a") if (u, v) not in bolt else hexc("edbe4e")
        flecks(px, rnd, k["fleck"], 6, rows=(0, 16))
    elif kind == "black":
        # glowing violet veins, like the cracks on the Black Dragon itself
        for u, v in ((3, 1), (10, 0), (6, 8), (13, 9)):
            for _ in range(7):
                if not (0 <= u < 16 and 0 <= v < 16):
                    break
                px[u, v] = hexc("a56bf2")
                if rnd.random() < 0.5 and 0 <= u + 1 < 16:
                    px[u + 1, v] = mix(px[u + 1, v], hexc("6a3fb0"), 0.6)
                v += 1
                u += rnd.choice((-1, 0, 0, 1))
        flecks(px, rnd, k["fleck"], 10, rows=(0, 16))
    return img


def polyline(points):
    """The pixels along straight lines joining the points."""
    out = []
    for (u0, v0), (u1, v1) in zip(points, points[1:]):
        n = max(abs(u1 - u0), abs(v1 - v0))
        for i in range(n + 1):
            p = (round(u0 + (u1 - u0) * i / n), round(v0 + (v1 - v0) * i / n))
            if not out or out[-1] != p:
                out.append(p)
    return out


def flecks(px, rnd, colours, count, rows):
    for _ in range(count):
        u, v = rnd.randrange(16), rnd.randrange(*rows)
        c = rnd.choice(colours)
        px[u, v] = mix(px[u, v], c, 0.85)
        if rnd.random() < 0.3 and u + 1 < 16:
            px[u + 1, v] = mix(px[u + 1, v], c, 0.45)


# crack paths from the top of the egg down: list of (u, v) points, joined into lines
CRACKS_1 = [[(7, 0), (7, 1), (8, 2), (8, 3), (7, 4), (7, 5), (8, 6)],
            [(8, 3), (9, 3), (10, 4)]]
CRACKS_2 = CRACKS_1 + [
    [(8, 6), (8, 7), (9, 8), (9, 9), (8, 10)],
    [(7, 4), (6, 4), (5, 5), (5, 6), (4, 7)],
    [(2, 9), (3, 10), (3, 11), (4, 12)],
    [(12, 8), (13, 9), (12, 10), (12, 11), (13, 12)],
    [(10, 4), (11, 5), (12, 5)],
]


def crack(img, kind, paths, glow_spots):
    k = KINDS[kind]
    out = img.copy()
    px = out.load()
    for path in paths:
        for u, v in path:
            if 0 <= u < 16 and 0 <= v < 16:
                px[u, v] = k["crack"]
                # a lighter rim along one side of the crack, where the shell edge catches the light
                if u + 1 < 16 and (u + 1, v) not in path:
                    px[u + 1, v] = mix(px[u + 1, v], (255, 255, 255), 0.18)
    for u, v in glow_spots:
        px[u, v] = k["glow"]
    return out


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    os.makedirs(TEX, exist_ok=True)
    write_json(f"{ASSETS}/models/block/dragon_egg.json", egg_model())
    stages = [("", None), ("_slightly_cracked", (CRACKS_1, [(8, 2)])),
              ("_very_cracked", (CRACKS_2, [(8, 2), (7, 5), (9, 8), (5, 6), (13, 9)]))]
    for kind in KINDS:
        name = f"{kind}_dragon_egg"
        whole = base_egg(kind)
        for suffix, cracks in stages:
            img = whole if cracks is None else crack(whole, kind, *cracks)
            img.save(f"{TEX}/{name}{suffix}.png")
            write_json(f"{ASSETS}/models/block/{name}{suffix}.json",
                       {"parent": "wildcreatures:block/dragon_egg", "textures": {"all": f"wildcreatures:block/{name}{suffix}"}})
        write_json(f"{ASSETS}/blockstates/{name}.json", {"variants": {
            f"hatch={i}": {"model": f"wildcreatures:block/{name}{s}"} for i, (s, _) in enumerate(stages)}})
        write_json(f"{ASSETS}/models/item/{name}.json", {"parent": f"wildcreatures:block/{name}"})
    print("eggs written")


if __name__ == "__main__":
    main()
