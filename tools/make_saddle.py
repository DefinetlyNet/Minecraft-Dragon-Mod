"""Generates the dragon saddle: its model (Java layer definition) and its texture.

Run from the project root:  python3 tools/make_saddle.py   (needs Pillow and numpy)
The boxes are in the dragon body's own model pixels (the renderer draws them on the body part, so the saddle
moves with it). Y points down and the dragon faces -Z. The spine ridge tops out at y = -7, the ribcage
sides are at x = +-5.5 and its belly at y = 6. A wide leather seat with a raised pommel and cantle sits
behind the wings on a purple saddle cloth with gold trim, with flaps down the sides, a girth strap under the
chest and a pair of stirrups.
"""
import numpy as np
from PIL import Image

JAVA = "src/main/java/com/wildcreatures/client/DragonSaddleModel.java"
TEXTURE = "src/main/resources/assets/wildcreatures/textures/entity/dragon_saddle.png"
TW = TH = 64
RES = 4

LEATHER = np.array((118, 66, 34), float)
LEATHER_DARK = np.array((70, 38, 20), float)
CLOTH = np.array((62, 22, 92), float)
GOLD = np.array((226, 182, 78), float)
IRON = np.array((150, 150, 160), float)
STITCH = np.array((222, 190, 140), float)

# name, (x, y, z), (w, h, d), material
BOXES = [
    ("cloth", (-6.0, -7.0, -10.5), (12.0, 1.0, 12.0), "cloth"),            # saddle cloth over the back
    ("seat", (-4.75, -8.25, -9.5), (9.5, 1.25, 10.0), "seat"),               # the padded seat
    ("pommel", (-1.75, -10.0, -10.0), (3.5, 1.75, 1.75), "leather_gold"),    # raised front
    ("cantle", (-4.25, -9.75, -0.5), (8.5, 1.5, 1.5), "leather_gold"),       # raised back
    ("flap_r", (6.0, -7.0, -9.25), (0.5, 6.0, 7.5), "flap"),                 # side flaps
    ("flap_l", (-6.5, -7.0, -9.25), (0.5, 6.0, 7.5), "flap"),
    ("girth_r", (5.5, -1.0, -6.5), (0.5, 7.0, 1.5), "strap"),                # girth strap round the chest
    ("girth_l", (-6.0, -1.0, -6.5), (0.5, 7.0, 1.5), "strap"),
    ("girth", (-5.5, 6.0, -6.5), (11.0, 0.5, 1.5), "strap"),
    ("stirrup_strap_r", (6.5, -2.0, -3.5), (0.25, 3.5, 0.5), "strap"),       # stirrups
    ("stirrup_strap_l", (-6.75, -2.0, -3.5), (0.25, 3.5, 0.5), "strap"),
    ("stirrup_r", (6.25, 1.5, -4.25), (0.75, 0.5, 2.0), "iron"),
    ("stirrup_l", (-7.0, 1.5, -4.25), (0.75, 0.5, 2.0), "iron"),
]


def footprint(size):
    w, h, d = size
    return int(np.ceil(2 * d + 2 * w)), int(np.ceil(d + h))


# pack the boxes' texture areas in rows, tallest first
order = sorted(range(len(BOXES)), key=lambda i: -footprint(BOXES[i][2])[1])
uv = {}
x = y = row = 0
for i in order:
    fw, fh = footprint(BOXES[i][2])
    if x + fw > TW:
        x, y, row = 0, y + row, 0
    uv[i] = (x, y)
    x += fw
    row = max(row, fh)
assert y + row <= TH, "saddle texture is full"


def faces(u, v, size):
    """Minecraft's box layout: each face's texture rectangle (in texture pixels) and its (across, down) size in model pixels."""
    w, h, d = size
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


rng = np.random.default_rng(7)
img = np.zeros((TH * RES, TW * RES, 4))
for i, (name, origin, size, mat) in enumerate(BOXES):
    u, v = uv[i]
    for face, (fu, fv, fw, fh) in faces(u, v, size).items():
        x0, y0 = int(round(fu * RES)), int(round(fv * RES))
        x1, y1 = int(round((fu + fw) * RES)), int(round((fv + fh) * RES))
        if x1 <= x0 or y1 <= y0:
            continue
        H, W = y1 - y0, x1 - x0
        jj, ii = np.mgrid[0:H, 0:W]
        # distance from the face's edge, in model pixels
        edge = np.minimum(np.minimum(ii + 0.5, W - ii - 0.5), np.minimum(jj + 0.5, H - jj - 0.5)) / RES
        tj = (jj + 0.5) / H
        grain = rng.normal(0, 1, (H, W))
        if mat in ("seat", "leather_gold", "flap"):
            col = LEATHER * (1.06 - 0.12 * tj[..., None]) if face != "top" else LEATHER * 1.08 * np.ones((H, W, 1))
            if mat == "seat" and face == "top":
                # quilted cushion: soft diamond tufts
                a = ((ii / RES) + (jj / RES)) % 2.5 - 1.25
                b = ((ii / RES) - (jj / RES)) % 2.5 - 1.25
                tuft = np.exp(-(a ** 2 + b ** 2) / 0.35)
                col = col * (0.9 + 0.16 * tuft[..., None])
            stitch = (np.abs(edge - 0.45) < 0.12) & ((((ii + jj) // 2) % 3) != 0)
            col = np.where(stitch[..., None], STITCH * 0.85, col)
            if mat == "leather_gold":
                col = np.where((edge < 0.4)[..., None], GOLD * (1.0 + 0.1 * (1 - tj))[..., None], col)
            col = col * (1 - 0.18 * np.exp(-edge / 0.18))[..., None]        # darker worn edges
        elif mat == "cloth":
            weave = 0.06 * np.sin(ii * np.pi / 2) * np.sin(jj * np.pi / 2)
            col = CLOTH * (1.0 + weave)[..., None]
            col = np.where((edge < 0.55)[..., None], GOLD * (0.95 + 0.1 * (1 - tj))[..., None], col)
            col = np.where(((edge > 0.55) & (edge < 0.75))[..., None], CLOTH * 0.6, col)
        elif mat == "strap":
            col = LEATHER_DARK * (1.08 - 0.15 * tj[..., None])
            col = col * (1 - 0.2 * np.exp(-edge / 0.12))[..., None]
        elif mat == "iron":
            col = IRON * (1.12 - 0.3 * tj[..., None])
        else:
            raise ValueError(mat)
        shade = {"top": 1.0, "bottom": 0.75, "front": 0.92, "back": 0.92, "left": 0.86, "right": 0.86}[face]
        col = col * shade + grain[..., None] * 3.0
        img[y0:y1, x0:x1, :3] = col
        img[y0:y1, x0:x1, 3] = 255
Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(TEXTURE, optimize=True)


def fl(v):
    return f"{float(v)}F"


cubes = "CubeListBuilder.create()"
for i, (name, (bx, by, bz), (w, h, d), mat) in enumerate(BOXES):
    cubes += f"\n                .texOffs({uv[i][0]}, {uv[i][1]}).addBox({fl(bx)}, {fl(by)}, {fl(bz)}, {fl(w)}, {fl(h)}, {fl(d)})   /* {name} */"
java = f"""// GENERATED by tools/make_saddle.py - edit that script instead of this file.
package com.wildcreatures.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** The dragon saddle, in the dragon body's own coordinates (DragonSaddleLayer draws it on the body). */
public final class DragonSaddleModel {{
    private DragonSaddleModel() {{}}

    public static LayerDefinition createLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("saddle", {cubes},
                PartPose.ZERO);
        return LayerDefinition.create(mesh, {TW}, {TH});
    }}
}}
"""
open(JAVA, "w").write(java)
print("wrote", JAVA, "and", TEXTURE)
