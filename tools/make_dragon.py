"""Generates the Red Dragon's 3D model (Java layer definition) and its textures.

Run from the project root:  python3 tools/make_dragon.py
Then rebuild the mod. Every part is a list of boxes in Minecraft model pixels
(16 = one block, before the renderer scales the dragon up). Y points DOWN and the
dragon faces -Z, as in all vanilla entity models. The ground is at y = 24.
"""
import random
from PIL import Image

random.seed(7)
JAVA = "src/main/java/com/wildcreatures/client/RedDragonLayer.java"
TEX = "src/main/resources/assets/wildcreatures/textures/entity/red_dragon.png"
GLOW = "src/main/resources/assets/wildcreatures/textures/entity/red_dragon_eyes.png"

PARTS = []  # (name, parent, pivot, rot, boxes) in parent-first order


def part(name, parent, pivot, rot=(0, 0, 0), boxes=()):
    PARTS.append((name, parent, pivot, rot, list(boxes)))


def box(x, y, z, w, h, d, mat="scale", **faces):
    """faces may override a face's material: top/bottom/front/back/left/right."""
    return dict(o=(x, y, z), s=(w, h, d), mat=mat, faces=faces)


def mirror_box(b):
    (x, y, z), (w, h, d) = b["o"], b["s"]
    faces = dict(b["faces"])
    if "left" in faces or "right" in faces:
        faces["left"], faces["right"] = b["faces"].get("right"), b["faces"].get("left")
    return dict(o=(-(x + w), y, z), s=(w, h, d), mat=b["mat"], faces={k: v for k, v in faces.items() if v})


def sided(name_r, parent, pivot, rot, boxes):
    """Adds a right (+x) part and its mirrored left (-x) twin."""
    part(name_r, parent, pivot, rot, boxes)
    name_l = name_r[:-1] + "l"
    parent_l = parent[:-1] + "l" if parent.endswith("_r") else parent
    part(name_l, parent_l, (-pivot[0], pivot[1], pivot[2]), (rot[0], -rot[1], -rot[2]),
         [mirror_box(b) for b in boxes])


BELLY = dict(bottom="belly")

# ---------------- body ----------------
part("body", "root", (0, 4, 0), boxes=[
    box(-8, -7, -14, 16, 14, 28, **BELLY),
    box(-7, -5, -17, 14, 13, 4, front="belly", bottom="belly"),     # chest
    box(-6, 7, -13, 12, 1, 24, "belly"),                            # belly plates
    box(-7, -8, -11, 14, 1, 23, "ridge"),                           # darker back
    box(-6, -9, -8, 12, 1, 16, "ridge"),                            # spine ridge
    box(-9, -5, -14, 1, 9, 8), box(8, -5, -14, 1, 9, 8),            # shoulder muscles
    box(-9, -4, 4, 1, 9, 9), box(8, -4, 4, 1, 9, 9),                # hip muscles
] + [box(-1, -12, z, 2, 3, 4, "spike") for z in (-13, -7, -1, 5)]
  + [box(-0.5, -14, z + 1, 1, 2, 2, "spike") for z in (-13, -7, -1, 5)]
  + [box(-0.5, -11, z, 1, 2, 3, "spike") for z in (-4, 2, 9)]
  + [box(x, -10, z, 1, 1, 2, "spike") for z in (-10, -4, 2, 8) for x in (-4.5, 3.5)])

# ---------------- neck and head ----------------
part("neck1", "body", (0, -2, -15), (-0.65, 0, 0), [
    box(-4.5, -4.5, -11, 9, 9, 12, **BELLY),
    box(-1, -7.5, -9, 2, 3, 3, "spike"), box(-1, -7.5, -4, 2, 3, 3, "spike"),
    box(-5.5, -3, -9, 1, 5, 7, "membrane"), box(4.5, -3, -9, 1, 5, 7, "membrane")])   # neck frills
part("neck2", "neck1", (0, 0, -10), (0.2, 0, 0), [
    box(-3.5, -3.5, -11, 7, 7, 12, **BELLY),
    box(-1, -6, -8, 2, 3, 3, "spike"), box(-1, -6, -3, 2, 3, 3, "spike"),
    box(-4.5, -2, -9, 1, 4, 7, "membrane"), box(3.5, -2, -9, 1, 4, 7, "membrane")])
part("head", "neck2", (0, 0, -10), (0.45, 0, 0), [
    box(-5, -5, -9, 10, 8, 10, bottom="belly"),                   # skull
    box(-3.5, -3, -19, 7, 5, 10, bottom="mouth"),                   # upper snout
    box(-3, -4, -18, 6, 1, 7, "ridge"),                             # snout ridge
    box(-5.5, -6, -9, 4, 2, 5, "ridge"), box(1.5, -6, -9, 4, 2, 5, "ridge"),   # brows
    box(-5.5, -7, -6, 1, 1, 2, "spike"), box(4.5, -7, -6, 1, 1, 2, "spike"),   # brow spikes
    box(-3, -3.5, -19, 2, 1, 2, "ridge"), box(1, -3.5, -19, 2, 1, 2, "ridge"),  # nostril rims
    box(-2.5, -3.2, -19.2, 1, 1, 1, "nostril"), box(1.5, -3.2, -19.2, 1, 1, 1, "nostril"),
    box(-1, -5, -16, 2, 2, 3, "bone"), box(-0.5, -6, -15, 1, 1, 2, "bone"),   # nose horn
    box(-5.5, -4, -8, 1, 2, 3, "eye"), box(4.5, -4, -8, 1, 2, 3, "eye"),
    box(-6.5, -2, -3, 2, 2, 6, "bone"), box(4.5, -2, -3, 2, 2, 6, "bone"),     # cheek spikes
    box(-6, 0, -1, 1, 1, 4, "bone"), box(5, 0, -1, 1, 1, 4, "bone"),
    box(-5.5, -4, 0, 1, 6, 3, "membrane"), box(4.5, -4, 0, 1, 6, 3, "membrane"),  # ear frills
] + [box(x, 2, z, 1, 2, 1, "tooth") for z in (-16, -14, -12, -10) for x in (-3.2, 2.2)]
  + [box(x, 2, -18.5, 1, 3, 1, "tooth") for x in (-2.7, 1.7)])                   # front fangs
part("jaw", "head", (0, 2, -7), (0.05, 0, 0), [
    box(-3, 0, -12, 6, 3, 12, top="mouth", bottom="belly"),
    box(-2, -0.5, -10, 4, 1, 8, "tongue"),
    box(-1, 2.5, -4, 2, 2, 3, "spike"),                              # chin spike
    box(-3.5, 1, -4, 1, 1, 3, "spike"), box(2.5, 1, -4, 1, 1, 3, "spike"),     # jaw spikes
] + [box(x, -2, z, 1, 2, 1, "tooth") for z in (-11, -9, -7, -5) for x in (-2.5, 1.5)])
sided("horn_r", "head", (3, -4, -2), (0.5, 0.3, 0), [box(-1, -1, 0, 2, 2, 10, "bone")])
sided("horn_tip_r", "horn_r", (0, 0, 9), (0.4, 0, 0), [box(-0.5, -0.5, 0, 1, 1, 6, "horntip")])
sided("horn2_r", "head", (4.5, -2, -1), (0.2, 0.7, 0), [box(-0.5, -0.5, 0, 1, 1, 6, "bone")])

# ---------------- tail ----------------
part("tail1", "body", (0, -3, 13), (-0.25, 0, 0), [
    box(-5, -5, 0, 10, 10, 12, **BELLY),
    box(-1, -8, 2, 2, 3, 3, "spike"), box(-1, -8, 7, 2, 3, 3, "spike"),
    box(-0.5, -9, 3, 1, 1, 2, "spike"), box(-0.5, -9, 8, 1, 1, 2, "spike")])
part("tail2", "tail1", (0, 0, 11), (-0.1, 0, 0), [
    box(-4, -4, 0, 8, 8, 12, **BELLY),
    box(-1, -6.5, 3, 2, 3, 3, "spike"), box(-1, -6.5, 8, 2, 3, 3, "spike")])
part("tail3", "tail2", (0, 0, 11), (0.12, 0, 0), [
    box(-3, -3, 0, 6, 6, 12, **BELLY),
    box(-0.5, -5, 4, 1, 2, 3, "spike"), box(-0.5, -5, 8, 1, 2, 3, "spike")])
part("tail4", "tail3", (0, 0, 11), (0.15, 0, 0), [
    box(-2, -2, 0, 4, 4, 12, **BELLY), box(-0.5, -3.5, 2, 1, 2, 3, "spike"), box(-0.5, -3.5, 7, 1, 2, 3, "spike")])
part("tail_tip", "tail4", (0, 0, 11), boxes=[
    box(-1.5, -1.5, 0, 3, 3, 3),
    box(-4.5, -0.5, 1, 9, 1, 4, "spike"), box(-3, -0.5, 5, 6, 1, 3, "spike"),
    box(-1.5, -0.5, 8, 3, 1, 2, "spike"), box(-0.5, -0.5, 10, 1, 1, 2, "spike"),
    box(-0.5, -3, 1, 1, 2, 4, "spike")])

# ---------------- legs (right side, mirrored to the left) ----------------
def toes(z):
    out = []
    for x in (-2.5, -0.5, 1.5):
        out.append(box(x, -0.5, z, 1, 1.5, 2))                   # toe
        out.append(box(x, 0, z - 1.5, 1, 1, 2, "claw"))          # talon
    out.append(box(-0.5, 0, 1.5, 1, 1, 2, "claw"))               # back claw
    return out

sided("front_leg_r", "body", (8, 4, -9), (0, 0, 0), [
    box(-2, -2, -3, 5, 11, 6), box(-1.5, -3, -3.5, 5, 6, 7)])
sided("front_shin_r", "front_leg_r", (0.5, 8, 0), (0, 0, 0), [
    box(-2, 0, -2.5, 4, 7, 5), box(-0.5, 1, 2.5, 1, 2, 2, "spike")])
sided("front_foot_r", "front_shin_r", (0, 7, 0), (0, 0, 0), [box(-2.5, 0, -3, 5, 1, 5)] + toes(-5))
sided("hind_leg_r", "body", (8, 3, 9), (0, 0, 0), [
    box(-2.5, -4, -5, 6, 13, 9), box(-2, -5, -4, 6, 8, 8)])
sided("hind_shin_r", "hind_leg_r", (0.5, 8, 2), (0, 0, 0), [
    box(-2, 0, -2, 4, 8, 4), box(-0.5, -1, 2, 1, 2, 2, "spike")])
sided("hind_foot_r", "hind_shin_r", (0, 8, 0), (0, 0, 0), [box(-2.5, 0, -3, 5, 1, 6)] + toes(-5))

# ---------------- wings ----------------
inner = [(0, 22), (5, 19), (10, 21), (15, 17)]
sided("wing_r", "body", (6, -6, -6), (0, 0, 0), [
    box(0, -1.5, -1.5, 20, 3, 3), box(1, -2, -1, 8, 1, 2, "ridge"),
] + [box(x, -0.5, 1, 5, 1, d, "membrane") for x, d in inner]
  + [box(x, -1, 1, 1, 1, d - 2, "strut") for x, d in ((5, 19), (10, 21), (15, 17))]   # finger struts
  + [box(18.5, -3.5, -1.5, 2, 2, 2, "claw"), box(19, -5, -1, 1, 2, 1, "claw")])
tip = [(0, 20), (3, 17), (6, 18), (9, 14), (12, 12), (15, 9)]
sided("wing_tip_r", "wing_r", (20, 0, 0), (0, 0, 0), [
    box(0, -1, -1, 18, 2, 2), box(17, -1.5, -1.5, 2, 2, 2, "claw"),
] + [box(x, -0.5, 1, 3, 1, d, "membrane") for x, d in tip]
  + [box(x, -1, 1, 1, 1, d - 2, "strut") for x, d in ((3, 17), (9, 14), (15, 9))])

# ---------------- UV packing ----------------
def footprint(b):
    import math
    w, h, d = b["s"]
    return math.ceil(2 * d + 2 * w), math.ceil(d + h)


all_boxes = [b for p in PARTS for b in p[4]]
order = sorted(all_boxes, key=lambda b: -footprint(b)[1])
TW = 256
x = y = row_h = 0
for b in order:
    fw, fh = footprint(b)
    if x + fw > TW:
        x, y, row_h = 0, y + row_h, 0
    b["uv"] = (x, y)
    x += fw
    row_h = max(row_h, fh)
TH = 1
while TH < y + row_h:
    TH *= 2
print(f"texture {TW}x{TH}, {len(all_boxes)} boxes in {len(PARTS)} parts")

# ---------------- painting ----------------
# The texture is painted at RES texels per model pixel, so scales can be much finer
# than one Minecraft pixel. The model still uses the TW x TH layout; Minecraft just
# samples the bigger image.
RES = 4
COL = {
    "scale": (172, 28, 22), "ridge": (118, 16, 14), "belly": (236, 148, 54), "spike": (64, 10, 10),
    "membrane": (128, 30, 24), "bone": (228, 210, 170), "horntip": (72, 56, 44), "eye": (255, 206, 40),
    "tooth": (244, 238, 218), "claw": (36, 30, 30), "nostril": (20, 4, 4), "mouth": (104, 20, 28),
    "tongue": (170, 50, 70), "strut": (96, 14, 12),
}
img = Image.new("RGBA", (TW * RES, TH * RES), (0, 0, 0, 0))
glow = Image.new("RGBA", (TW * RES, TH * RES), (0, 0, 0, 0))
px, gpx = img.load(), glow.load()


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c) + (255,)


def h01(a, b, salt=0):
    """Stable pseudo-random number in 0..1 for a cell."""
    v = (a * 73856093) ^ (b * 19349663) ^ (salt * 83492791)
    return ((v * 2654435761) & 0xFFFF) / 65535.0


def scale_shade(x, y, cw=8, ch=5):
    """Brightness of overlapping, rounded scales laid like roof shingles (the row above
    covers the top of the row below, so each scale shows a rounded lower edge).
    x, y in texels on the face. Returns (brightness, row, col)."""
    R = cw * 0.64
    first = int((y - R) // ch)
    for row in range(first, first + 4):
        cy = row * ch
        if abs(y + 0.5 - cy) > R:
            continue
        off = (row % 2) * cw / 2
        col = int((x + 0.5 - off) // cw + 0.5)
        for c in (col - 1, col, col + 1):
            cx = c * cw + off
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            dist = (dx * dx + dy * dy) ** 0.5 / R
            if dist <= 1.0:
                var = (h01(row, c) - 0.5) * 0.14
                k = 1.15 - 0.38 * dist + var
                if dy < 0:
                    k -= 0.15                       # tucked under the row above
                if abs(dx) < 0.8 and dy > 0:
                    k += 0.07                       # keel down the middle
                if dist > 0.82:
                    k = 0.7 + var * 0.5             # dark outline
                return k, row, c
    return 0.6, 0, 0


def paint_face(u0, v0, fw, fh, mat, face):
    base = COL[mat]
    U0, V0, FW, FH = int(round(u0 * RES)), int(round(v0 * RES)), max(1, round(fw * RES)), max(1, round(fh * RES))
    for i in range(FW):
        for j in range(FH):
            r, g, b = base
            k = 1.0
            ti, tj = i / FW, j / FH          # 0..1 across the face
            if mat in ("scale", "ridge"):
                sk, row, col = scale_shade(i, j)
                k = sk
                if face == "top":
                    k *= 0.8 if mat == "scale" else 0.9
                elif face in ("left", "right", "front", "back"):
                    k *= 0.86 + 0.2 * tj    # darker along the back, lighter low down
                # a few scales tinted orange for variety
                if h01(row, col, 5) > 0.93:
                    r, g, b = r * 1.08, g * 1.6, b
            elif mat == "belly":
                plate = RES * 2
                pj = j % plate
                k = 1.08 - 0.2 * pj / plate
                if pj >= plate - 1:
                    k = 0.76
                if i < 1 or i >= FW - 1:
                    k *= 0.8
                k *= 1.0 + (h01(j // plate, 0, 9) - 0.5) * 0.1
            elif mat == "membrane":
                k = 0.85 + 0.25 * (1 - tj) if face in ("top", "bottom") else 0.8
                if face in ("top", "bottom"):
                    # thin veins branching across the membrane
                    if i % (RES * 3) == 0 or (j + i // 2) % (RES * 5) == 0:
                        r, g, b = 170, 52, 36
                    if tj > 0.92:
                        k *= 0.7             # darker trailing edge
            elif mat == "spike":
                k = 1.25 - 0.6 * (1 - tj) if face in ("left", "right", "front", "back") else 0.7
                if i in (0, FW - 1):
                    k *= 0.8
            elif mat == "strut":
                k = 1.1 if j % RES < 2 else 0.85
            elif mat == "eye":
                if face in ("left", "right", "front"):
                    cx = abs(ti - 0.5)
                    if cx < 0.1:
                        r, g, b = 15, 8, 4               # slit pupil
                    else:
                        k = 1.05 - cx * 0.6              # iris: bright centre, orange rim
                        g = g * (1.05 - cx * 0.9)
                    if ti < 0.25 and tj < 0.3:
                        r, g, b, k = 255, 250, 220, 1.0  # glint
            elif mat in ("bone", "horntip"):
                along = tj if face in ("top", "bottom") else ti
                k = 1.05 - 0.4 * along
                if (int(along * fh * RES) if face in ("top", "bottom") else i) % (RES * 2) == 0:
                    k *= 0.85                              # growth rings
                if face in ("left", "right"):
                    k *= 0.92
            elif mat == "tooth":
                k = 0.8 + 0.25 * tj
            elif mat == "claw":
                k = 1.6 if (i == 1 and j < FH - 1) else 1.0
            elif mat in ("mouth", "tongue"):
                k = 0.85 + 0.25 * tj
                if mat == "tongue" and abs(ti - 0.5) < 0.06:
                    k *= 0.75
            n = random.randint(-5, 5)
            px[U0 + i, V0 + j] = clamp((r * k + n, g * k + n, b * k + n))
            if mat == "eye":
                gpx[U0 + i, V0 + j] = px[U0 + i, V0 + j]


for b in all_boxes:
    w, h, d = b["s"]
    u, v = b["uv"]
    f = b["faces"]
    m = b["mat"]
    paint_face(u + d, v, w, d, f.get("top", m), "top")
    paint_face(u + d + w, v, w, d, f.get("bottom", m), "bottom")
    paint_face(u, v + d, d, h, f.get("right", m), "right")
    paint_face(u + d, v + d, w, h, f.get("front", m), "front")
    paint_face(u + d + w, v + d, d, h, f.get("left", m), "left")
    paint_face(u + 2 * d + w, v + d, w, h, f.get("back", m), "back")

img.save(TEX)
glow.save(GLOW)

# ---------------- Java ----------------
def fl(v):
    return f"{float(v)}F"


def var(name):
    return "p_" + name


lines = []
for name, parent, pivot, rot, boxes in PARTS:
    cubes = "CubeListBuilder.create()"
    for b in boxes:
        (x, y, z), (w, h, d) = b["o"], b["s"]
        cubes += f"\n                .texOffs({b['uv'][0]}, {b['uv'][1]}).addBox({fl(x)}, {fl(y)}, {fl(z)}, {fl(w)}, {fl(h)}, {fl(d)})"
    pose = f"PartPose.offsetAndRotation({', '.join(fl(v) for v in pivot)}, {', '.join(fl(v) for v in rot)})"
    lines.append(f'        PartDefinition {var(name)} = {var(parent)}.addOrReplaceChild("{name}", {cubes},\n                {pose});')

java = f"""// GENERATED by tools/make_dragon.py - edit that script instead of this file.
package com.wildcreatures.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

public final class RedDragonLayer {{
    private RedDragonLayer() {{}}

    @SuppressWarnings("unused")
    public static LayerDefinition create() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition {var('root')} = mesh.getRoot();
{chr(10).join(lines)}
        return LayerDefinition.create(mesh, {TW}, {TH});
    }}
}}
"""
open(JAVA, "w").write(java)
print("wrote", JAVA, TEX, GLOW)
