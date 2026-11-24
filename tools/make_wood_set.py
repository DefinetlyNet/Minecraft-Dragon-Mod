"""Makes the burnt oak wood set (fence, gate, door, trapdoor, button, pressure plate, signs,
sapling) by copying vanilla oak's block files and recipes with burnt oak names, and
recolouring vanilla oak textures to charred colours. Also makes the burnt stick and burnt
bush textures.

Run from the project root:
    python3 tools/make_wood_set.py <path to the Minecraft 1.20.1 client jar>
"""
import json
import os
import re
import sys
import zipfile
from io import BytesIO
from PIL import Image

jar = zipfile.ZipFile(sys.argv[1])
M = "wildcreatures"
RES = "src/main/resources"
KINDS = ["fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate", "sign", "wall_sign",
         "hanging_sign", "wall_hanging_sign", "sapling"]
CUTOUT = ("door", "trapdoor", "sapling")


def rename(text):
    for a, b in [("minecraft:block/oak_", f"{M}:block/burnt_oak_"), ("minecraft:item/oak_", f"{M}:item/burnt_oak_"),
                 ("minecraft:blocks/oak_", f"{M}:blocks/burnt_oak_"), ("minecraft:stripped_oak_log", f"{M}:stripped_burnt_oak_log"),
                 ("minecraft:oak_", f"{M}:burnt_oak_"), ('"minecraft:stick"', f'"{M}:burnt_stick"')]:
        text = text.replace(a, b)
    return text


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)


names = jar.namelist()
for n in names:
    m = re.match(r"(assets|data)/minecraft/(blockstates|models/block|models/item|recipes|loot_tables/blocks)/oak_(.+)\.json$", n)
    if not m:
        continue
    root, folder, rest = m.groups()
    kind = next((k for k in sorted(KINDS, key=len, reverse=True) if rest == k or rest.startswith(k + "_")), None)
    if kind is None or (rest.startswith("fence_gate") and kind == "fence"):
        continue
    if folder in ("recipes",) and kind in ("wall_sign", "wall_hanging_sign"):
        continue
    text = rename(jar.read(n).decode())
    if folder == "models/block" and kind.split("_")[0] in CUTOUT or (folder == "models/block" and kind == "sapling"):
        d = json.loads(text)
        if "textures" in d:
            d["render_type"] = "minecraft:cutout"
        text = json.dumps(d, indent=2)
    ns = M
    write(f"{RES}/{root}/{ns}/{folder}/burnt_oak_{rest}.json", text)

# ---- textures ----
def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            k = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return tuple(int(c0[i] + (c1[i] - c0[i]) * k) for i in range(3))
    return stops[-1][1]


def recolour(path, stops, keep_grey=False):
    im = Image.open(BytesIO(jar.read(path))).convert("RGBA")
    px = im.load()
    pts = [(x, y) for x in range(im.width) for y in range(im.height) if px[x, y][3]]
    def greyish(c):
        return max(c[:3]) - min(c[:3]) < 14
    use = [p for p in pts if not (keep_grey and greyish(px[p]))]
    ls = [lum(px[p]) for p in use]
    lo, hi = min(ls), max(ls)
    for p in use:
        px[p] = ramp(stops, (lum(px[p]) - lo) / (hi - lo + 1e-9)) + (px[p][3],)
    return im


PLANK = [(0, (24, 18, 15)), (0.5, (52, 40, 32)), (1, (78, 61, 47))]
CHAR = [(0, (16, 13, 12)), (0.55, (44, 38, 35)), (0.85, (70, 62, 56)), (1, (150, 60, 24))]   # brightest bits glow like embers
V = "assets/minecraft/textures/"
T = f"{RES}/assets/{M}/textures/"
jobs = [("block/oak_door_top.png", "block/burnt_oak_door_top.png", PLANK, True),
        ("block/oak_door_bottom.png", "block/burnt_oak_door_bottom.png", PLANK, True),
        ("block/oak_trapdoor.png", "block/burnt_oak_trapdoor.png", PLANK, True),
        ("item/oak_door.png", "item/burnt_oak_door.png", PLANK, True),
        ("item/oak_sign.png", "item/burnt_oak_sign.png", PLANK, False),
        ("item/oak_hanging_sign.png", "item/burnt_oak_hanging_sign.png", PLANK, True),
        ("entity/signs/oak.png", "entity/signs/burnt_oak.png", PLANK, False),
        ("entity/signs/hanging/oak.png", "entity/signs/hanging/burnt_oak.png", PLANK, True),
        ("gui/hanging_signs/oak.png", "gui/hanging_signs/burnt_oak.png", PLANK, True),
        ("block/oak_sapling.png", "block/burnt_oak_sapling.png", CHAR, False),
        ("item/stick.png", "item/burnt_stick.png", [(0, (20, 16, 14)), (0.6, (48, 40, 35)), (1, (82, 70, 60))], False),
        ("block/dead_bush.png", "block/burnt_bush.png", CHAR, False)]
for src, dst, stops, keep in jobs:
    os.makedirs(os.path.dirname(T + dst), exist_ok=True)
    recolour(V + src, stops, keep).save(T + dst)
print("done")
