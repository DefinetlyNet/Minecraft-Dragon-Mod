"""Makes the Ashen Wastes block textures by recolouring vanilla textures (same pixel
patterns, new colours): burnt oak from oak, ashen soil from sand.

Run from the project root:
    python3 tools/make_biome_textures.py <path to the Minecraft 1.20.1 client jar>
"""
import random
import sys
import zipfile
from io import BytesIO
from PIL import Image

jar = zipfile.ZipFile(sys.argv[1])
OUT = "src/main/resources/assets/wildcreatures/textures/block"
V = "assets/minecraft/textures/block/"
rng = random.Random(7)


def load(name):
    return Image.open(BytesIO(jar.read(V + name))).convert("RGBA")


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            k = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return tuple(int(c0[i] + (c1[i] - c0[i]) * k) for i in range(3))
    return stops[-1][1]


def recolour(im, stops, mask=None):
    """Map each pixel's brightness (stretched to 0..1 over the image) onto the colour stops."""
    px = im.load()
    pts = [(x, y) for x in range(im.width) for y in range(im.height) if px[x, y][3] and (mask is None or mask(x, y))]
    ls = [lum(px[p]) for p in pts]
    lo, hi = min(ls), max(ls)
    out = im.copy()
    op = out.load()
    for p, l in zip(pts, ls):
        op[p] = ramp(stops, (l - lo) / (hi - lo + 1e-9)) + (px[p][3],)
    return out


BARK = [(0, (20, 10, 8)), (0.12, (58, 20, 10)), (0.2, (26, 23, 22)), (0.55, (44, 40, 38)), (0.85, (64, 59, 55)), (1, (84, 78, 72))]
CORE = [(0, (38, 27, 20)), (0.5, (66, 48, 34)), (1, (96, 72, 50))]
PLANK = [(0, (24, 18, 15)), (0.5, (52, 40, 32)), (1, (78, 61, 47))]
ASH = [(0, (46, 44, 44)), (0.5, (66, 63, 62)), (1, (92, 88, 85))]

recolour(load("oak_log.png"), BARK).save(f"{OUT}/burnt_oak_log.png")

top = load("oak_log_top.png")
# outer ring of the log end is bark, the middle is the cut wood
edge = lambda x, y: min(x, y, 15 - x, 15 - y) == 0
t = recolour(top, BARK, edge)
t2 = recolour(top, CORE, lambda x, y: not edge(x, y))
tp, t2p = t.load(), t2.load()
for x in range(16):
    for y in range(16):
        if not edge(x, y):
            tp[x, y] = t2p[x, y]
t.save(f"{OUT}/burnt_oak_log_top.png")

recolour(load("stripped_oak_log.png"), CORE).save(f"{OUT}/stripped_burnt_oak_log.png")
recolour(load("stripped_oak_log_top.png"), CORE).save(f"{OUT}/stripped_burnt_oak_log_top.png")
recolour(load("oak_planks.png"), PLANK).save(f"{OUT}/burnt_oak_planks.png")

ash = recolour(load("sand.png"), ASH)
ap = ash.load()
for _ in range(7):                      # a few pale flecks of fresh ash
    ap[rng.randrange(16), rng.randrange(16)] = (122, 117, 112, 255)
ash.save(f"{OUT}/ashen_soil.png")
print("done")
