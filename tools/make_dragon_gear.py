"""Makes the textures for the dragon gear: the big Dragon Sword (32x32, dark jagged frame with a
glowing, animated lava core), the dragon pickaxe/axe/shovel (red reskins of the diamond tools
with burnt-stick handles), the Dragon Essence and the Dragon Shield.

Run from the project root:
    python3 tools/make_dragon_gear.py <path to the Minecraft 1.20.1 client jar>
"""
import json
import math
import os
import random
import sys
import zipfile
from io import BytesIO
from PIL import Image

jar = zipfile.ZipFile(sys.argv[1]) if len(sys.argv) > 1 else None
OUT = "src/main/resources/assets/wildcreatures/textures"
rng = random.Random(5)


def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            k = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return tuple(int(round(c0[i] + (c1[i] - c0[i]) * k)) for i in range(3))
    return stops[-1][1]


LAVA = [(0.0, (96, 12, 8)), (0.28, (168, 28, 14)), (0.5, (226, 78, 18)), (0.7, (255, 146, 36)),
        (0.86, (255, 206, 92)), (1.0, (255, 244, 186))]
F_DARK, F_MID, F_HI, F_EDGE = (17, 11, 16), (33, 23, 31), (62, 46, 58), (9, 6, 9)

# ------------------------------------------------------------------ sword
S = 32
P0 = (3.0, 29.0)                                 # pommel centre (texture coords, y down)
A = (1 / math.sqrt(2), -1 / math.sqrt(2))        # along the blade, towards the tip
PERP = (1 / math.sqrt(2), 1 / math.sqrt(2))      # across the blade (negative = upper-left, the lit side)
TIP = 38.9
HANDLE = (2.0, 10.4)
GUARD_T = 11.2
BLADE0 = 12.4
FRAME = 2.05                                     # frame thickness (three diagonal pixel rows)


def blade_hw(t):
    """Half-width of the blade at distance t along it: a wide wedge at the guard, then a long
    broad blade, then a sharp point."""
    if t < BLADE0:
        return 0
    if t < 17.0:
        hw = 4.5 - (t - BLADE0) / (17.0 - BLADE0) * 1.0
    else:
        hw = 3.5 - (t - 17.0) * 0.012
    if t > 32.0:
        hw *= max(0.0, (TIP - t) / (TIP - 32.0)) ** 0.9
    return hw


# jagged teeth on the frame: (t where it starts, side, how far it sticks out)
TEETH = [(14.6, -1, 2.9), (21.2, -1, 2.6), (27.6, -1, 2.0),
         (17.6, 1, 2.6), (24.4, 1, 2.2)]


def tooth(t, w):
    aw = abs(w)
    for st, side, ln in TEETH:
        if w * side <= 0:
            continue
        out = aw - blade_hw(st)
        if 0 < out <= ln:
            centre = st + 0.5 + out * 0.75         # leans towards the tip
            half = 1.6 * (1 - out / (ln + 1.3))
            if abs(t - centre) <= half:
                return True
    return False


def classify(x, y):
    """Returns (part, t, w) for a pixel centre, part in frame/core/handle/pommel/pommel_core/None."""
    dx, dy = x + 0.5 - P0[0], y + 0.5 - P0[1]
    t = dx * A[0] + dy * A[1]
    w = dx * PERP[0] + dy * PERP[1]
    aw = abs(w)
    if abs(t) + aw <= 2.5:                                       # pommel: a diamond
        return ("pommel_core" if abs(t) + aw <= 0.75 else "pommel"), t, w
    if HANDLE[0] <= t <= HANDLE[1] and aw <= 0.75:
        return "handle", t, w
    # guard: a two-row bar whose ends hook up towards the tip like horns
    gt = GUARD_T + max(0.0, aw - 5.4) * 1.1
    if aw <= 8.6 and abs(t - gt) <= 1.3:
        return ("core" if aw <= 4.6 and abs(t - gt) <= 0.45 else "frame"), t, w
    hw = blade_hw(t)
    if hw > 0 and aw <= hw:
        return ("frame" if aw > hw - FRAME or hw < FRAME + 0.3 else "core"), t, w
    if t > BLADE0 and tooth(t, w):
        return "frame", t, w
    return None, t, w


def sword_frame():
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = im.load()
    parts = {(x, y): classify(x, y)[0] for y in range(S) for x in range(S)}
    for (x, y), part in parts.items():
        if part is None or part in ("core", "pommel_core"):
            continue
        _, t, w = classify(x, y)
        if part == "handle":
            c = (128, 26, 20) if w < 0 else (86, 14, 12)
            if int(t * 1.1) % 3 == 0:                            # leather wraps
                c = tuple(int(v * 0.55) for v in c)
            px[x, y] = c + (255,)
            continue
        near = [parts.get((x + dx, y + dy)) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        diag = [parts.get((x + dx, y + dy)) for dx, dy in ((1, 1), (-1, -1), (1, -1), (-1, 1))]
        if any(n in ("core", "pommel_core") for n in near):
            c = (52, 17, 21)                                     # warm rim next to the lava
        elif any(n is None for n in near):
            lit = w < 0 and (parts.get((x - 1, y)) is None or parts.get((x, y - 1)) is None)
            c = F_HI if lit else F_EDGE
        else:
            c = F_MID if (x * 3 + y) % 4 else F_DARK
        px[x, y] = c + (255,)
    return im, parts


def sword_core(parts, frames=8):
    strip = Image.new("RGBA", (S, S * frames), (0, 0, 0, 0))
    px = strip.load()
    for k in range(frames):
        phase = 2 * math.pi * k / frames
        for (x, y), part in parts.items():
            if part not in ("core", "pommel_core"):
                continue
            _, t, w = classify(x, y)
            if part == "pommel_core":
                c = ramp(LAVA, 0.72 + 0.18 * math.sin(phase))
            else:
                inner = max(blade_hw(t) - FRAME, 1.2) if t >= BLADE0 else 3.4
                v = max(0.0, 1 - abs(w) / (inner + 0.35))
                flow = math.sin(0.8 * t - phase + 1.6 * math.sin(0.6 * w + 0.23 * t))
                flow2 = math.sin(0.33 * t + 0.8 * w - 2 * phase + 1.3)
                b = 0.12 + 0.8 * v ** 0.75 + 0.14 * flow + 0.06 * flow2
                c = ramp(LAVA, b)
            px[x, y + k * S] = c + (255,)
    return strip


frame, parts = sword_frame()
core = sword_core(parts)
os.makedirs(f"{OUT}/item", exist_ok=True)
frame.save(f"{OUT}/item/dragon_sword.png")
core.save(f"{OUT}/item/dragon_sword_core.png")
with open(f"{OUT}/item/dragon_sword_core.png.mcmeta", "w") as f:
    json.dump({"animation": {"frametime": 3, "interpolate": True}}, f)

# ------------------------------------------------------------------ pickaxe, axe, shovel
# Red reskins of the diamond tools: the head becomes dragon red with a gold-ember edge, the handle
# becomes a burnt stick, the handle end gets a red cap, and each head gets a small dragon spike and
# a couple of glowing ember pixels.
HEAD = {(8, 37, 32): (30, 6, 6), (14, 63, 54): (66, 10, 10), (30, 138, 119): (120, 20, 16),
        (39, 178, 154): (166, 32, 22), (43, 199, 172): (206, 58, 28), (51, 235, 203): (255, 164, 64)}
HANDLE_C = {(40, 30, 11): (14, 11, 10), (73, 54, 21): (30, 25, 22), (104, 78, 30): (48, 41, 36),
            (137, 103, 39): (70, 60, 52)}
EMBER = (255, 118, 30)
EXTRA = {
    "pickaxe": {"spike": [(8, 1, (120, 20, 16)), (8, 0, (30, 6, 6))], "ember": [(8, 3), (13, 8)]},
    "axe": {"spike": [(5, 4, (66, 10, 10)), (5, 3, (30, 6, 6))], "ember": [(8, 3), (10, 5)]},
    "shovel": {"spike": [(14, 2, (166, 32, 22)), (15, 2, (30, 6, 6))], "ember": [(11, 4), (12, 5)]},
}
if jar:
    for tool, extra in EXTRA.items():
        im = Image.open(BytesIO(jar.read(f"assets/minecraft/textures/item/diamond_{tool}.png"))).convert("RGBA")
        px = im.load()
        handle = []
        for y in range(16):
            for x in range(16):
                c = px[x, y]
                if c[3] == 0:
                    continue
                if c[:3] in HEAD:
                    px[x, y] = HEAD[c[:3]] + (255,)
                elif c[:3] in HANDLE_C:
                    px[x, y] = HANDLE_C[c[:3]] + (255,)
                    handle.append((x, y))
        low = max(y for _, y in handle)                          # red cap on the end of the handle
        for x, y in handle:
            if y == low:
                px[x, y] = (88, 14, 12, 255)
        for x, y, c in extra["spike"]:
            px[x, y] = c + (255,)
        for x, y in extra["ember"]:
            px[x, y] = EMBER + (255,)
        im.save(f"{OUT}/item/dragon_{tool}.png")

# ------------------------------------------------------------------ dragon essence
# A small gem: a dark red shell (layer 0) around a glowing core that swirls and pulses (layer 1,
# drawn full-bright in game).
def essence():
    base = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    frames = 8
    glow = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 0))
    bp, gp = base.load(), glow.load()
    cx, cy = 7.5, 8.0

    def inside(x, y, grow=0.0):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        return abs(dx) * 0.95 + abs(dy) * 0.72 + max(0.0, -dy) * 0.18 <= 5.6 + grow

    for y in range(16):
        for x in range(16):
            if not inside(x, y):
                continue
            edge = any(not inside(x + a, y + b) for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                lit = (x + 0.5 < cx and y + 0.5 < cy + 1)
                bp[x, y] = ((132, 40, 30) if lit else (46, 6, 8)) + (255,)
                continue
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx * 0.95, dy * 0.75) / 5.0
            ang = math.atan2(dy, dx)
            for k in range(frames):
                ph = 2 * math.pi * k / frames
                swirl = math.sin(3 * ang + 5 * r - ph)
                b = 1.05 - r * 0.95 + 0.16 * swirl + 0.1 * math.sin(ph)
                gp[x, y + 16 * k] = ramp(LAVA, b) + (255,)
    # a sparkle
    for k in range(frames):
        if (x := 5) is not None and k % 4 in (0, 1):
            gp[5, 5 + 16 * k] = (255, 250, 225, 255)
    base.save(f"{OUT}/item/dragon_essence.png")
    glow.save(f"{OUT}/item/dragon_essence_glow.png")
    with open(f"{OUT}/item/dragon_essence_glow.png.mcmeta", "w") as f:
        json.dump({"animation": {"frametime": 3, "interpolate": True}}, f)


essence()

# ------------------------------------------------------------------ dragon shield
# Texture layout follows client/DragonShieldModel (64x64 UV, painted at 4x = 256x256):
#   plate 12x22x1 at (0,0): outer face (1,1), inner face (14,1)
#   handle 2x6x6 at (26,0), boss 6x8x1 at (0,24) (front face (1,25)), horns 2x3x2 at (16,24) and (24,24)
X4 = 4
SH = Image.new("RGBA", (64 * X4, 64 * X4), (0, 0, 0, 0))
GL = Image.new("RGBA", (64 * X4, 64 * X4), (0, 0, 0, 0))
sp, gl = SH.load(), GL.load()
RIM = [(0, (64, 40, 16)), (0.5, (150, 104, 40)), (1, (214, 168, 82))]
BONE, BONE_D, TIP = (226, 206, 166), (176, 152, 116), (70, 52, 40)


def fill(u, v, w, h, fn):
    for y in range(v * X4, (v + h) * X4):
        for x in range(u * X4, (u + w) * X4):
            c = fn(x - u * X4, y - v * X4, w * X4, h * X4)
            if c:
                sp[x, y] = c + (255,) if len(c) == 3 else c


def metal(x, y, w, h):
    return ramp(RIM, 0.55 + 0.35 * math.sin((x + y) * 0.35) * 0.5 + (0.25 if y < h / 2 else -0.1))


def scales(x, y, w, h):
    """Overlapping dragon scales with a bronze rim around the edge."""
    border = 4
    if x < border or y < border or x >= w - border or y >= h - border:
        inset = min(x, y, w - 1 - x, h - 1 - y)
        return ramp(RIM, [0.15, 0.95, 0.7, 0.45][inset])
    sw, sh = 10, 7                                        # one scale is 10 wide, rows 7 apart
    row = (y - border) // sh
    off = (sw // 2) * (row % 2)
    cx = ((x - border + off) // sw) * sw + sw / 2 - off + border
    cy = row * sh + border
    dx, dy = (x + 0.5 - cx) / (sw / 2), (y + 0.5 - cy) / sh
    d = math.hypot(dx, dy * 0.9)
    if d > 1.0:                                           # covered by the row above
        return (96, 14, 12)
    if d > 0.86 or dy < 0:
        return (40, 6, 7)
    shade = 1 - dy * 0.75
    c = ramp([(0, (78, 12, 10)), (0.6, (150, 26, 18)), (1, (198, 52, 30))], shade)
    if 0.62 < d < 0.8 and dy < 0.35:
        c = tuple(min(255, int(v * 1.25)) for v in c)     # glossy edge
    return c


def planks(x, y, w, h):
    border = 4
    if x < border or y < border or x >= w - border or y >= h - border:
        return ramp(RIM, 0.35)
    if (x - border) % 12 in (0,):
        return (16, 12, 11)
    if abs(y - h * 0.3) < 3 or abs(y - h * 0.7) < 3:     # iron straps across the back
        return (58, 52, 50) if (x % 6) else (90, 82, 78)
    v = 0.5 + 0.25 * math.sin(x * 0.9 + (x // 12) * 3.0 + y * 0.05)
    return ramp([(0, (26, 20, 17)), (1, (66, 54, 44))], v)


def leather(x, y, w, h):
    return (40, 20, 16) if (x + y) % 6 else (60, 30, 22)


def horn_base(x, y, w, h):
    """2x2x2 horn base: top/bottom faces in the first 2 rows, sides below (top of a side = towards the tip)."""
    d = 2 * X4
    if y < d:
        return BONE_D if x < 2 * d else (60, 44, 34)       # top face shows under the tip, bottom is hidden
    t = (y - d) / max(1, h - d - 1)                       # 0 at the top of the side, 1 at the base
    c = ramp([(0, (192, 170, 132)), (1, BONE)], t)
    if (y - d) % 3 == 0:                                  # growth rings
        c = tuple(int(v * 0.82) for v in c)
    if x % 8 == 0:                                        # darker face edges so the shape reads
        c = tuple(int(v * 0.9) for v in c)
    return c


def horn_tip(x, y, w, h):
    """1x1.5x1 horn tip, darkening to a charred point."""
    d = 1 * X4
    if y < d:
        return (34, 24, 20)
    t = min(1.0, (y - d) / (1.5 * X4 - 1))                # 0 at the point, 1 where it meets the base
    c = ramp([(0, (40, 28, 24)), (0.55, (104, 80, 60)), (1, (176, 152, 116))], t)
    if t < 0.18 and x % 4 in (1, 2):
        c = (120, 34, 20)                                 # a faint ember glint at the point
    return c


# plate: edges, outer face, inner face
fill(1, 0, 12, 1, metal); fill(13, 0, 12, 1, metal)
fill(0, 1, 1, 22, metal); fill(13, 1, 1, 22, metal)
fill(1, 1, 12, 22, scales)
fill(14, 1, 12, 22, planks)
fill(26, 0, 16, 12, leather)
# boss: bronze sides, a dragon eye on the front
fill(0, 24, 14, 9, metal)


def eye(x, y, w, h):
    cx, cy = w / 2, h / 2
    dx, dy = (x + 0.5 - cx) / (w / 2 - 3), (y + 0.5 - cy) / (h / 2 - 3)
    if abs(dx) > 1.0 or abs(dy) > 1.0 or x < 2 or y < 2 or x >= w - 2 or y >= h - 2:
        return ramp(RIM, 0.75)
    lid = abs(dx) ** 1.6 + abs(dy) ** 1.6
    if lid > 1.0:
        return (34, 6, 6) if lid < 1.35 else (110, 18, 14)
    if abs(dx) < 0.16 + 0.1 * (1 - abs(dy)):
        return (10, 4, 4)                                  # slit pupil
    return ramp(LAVA, 0.95 - 0.5 * lid)


fill(1, 25, 6, 8, eye)
# horns: base 2x2x2 at (16,24)/(24,24), tip 1x1.5x1 at (16,28)/(24,28)
fill(16, 24, 8, 4, horn_base)
fill(24, 24, 8, 4, horn_base)
fill(16, 28, 4, 3, horn_tip)
fill(24, 28, 4, 3, horn_tip)

# glow: the eye's iris and a few ember cracks running out from the boss
for y in range(25 * X4, 33 * X4):
    for x in range(1 * X4, 7 * X4):
        c = sp[x, y]
        if c[3] and c[0] > 200 and c[1] > 60 and not (c[0] > 200 and c[1] > 150 and c[2] > 70 and abs(c[0] - c[1]) < 70):
            gl[x, y] = (c[0], c[1], c[2], 255)
crack_rng = random.Random(3)
for n in range(7):
    x, y = 6.5 * X4 + crack_rng.uniform(-6, 6), 12 * X4 + crack_rng.uniform(-10, 10)
    a = crack_rng.uniform(0, 2 * math.pi)
    for step in range(crack_rng.randint(10, 22)):
        a += crack_rng.uniform(-0.5, 0.5)
        x += math.cos(a) * 1.3
        y += math.sin(a) * 1.3
        ix, iy = int(x), int(y)
        if 1 * X4 + 4 <= ix < 13 * X4 - 4 and 1 * X4 + 4 <= iy < 23 * X4 - 4:
            sp[ix, iy] = (255, 120, 30, 255)
            gl[ix, iy] = (255, 110, 26, 255)
os.makedirs(f"{OUT}/entity", exist_ok=True)
SH.save(f"{OUT}/entity/dragon_shield.png")
GL.save(f"{OUT}/entity/dragon_shield_glow.png")
print("done")
