"""Generates the dragons' shared 3D model (Java layer definition) and each kind's textures.

Run from the project root:  python3 tools/make_dragon.py   (needs Pillow and numpy)
Then rebuild the mod. Every part is a list of boxes in Minecraft model pixels
(16 = one block, before the renderer scales the dragon up 1.8x). Y points DOWN and the
dragon faces -Z, as in all vanilla entity models. The ground is at y = 24.

The body is lean and athletic: a deep chest, a tucked-up waist, a long S-shaped neck,
long legs (the hind legs stand on their toes like a cat's) and big wings.
The skins are painted smoothly: every part fades from a dark back through the flanks
to a light belly, with soft scales and gentle mottling, so the colours blend.
"""
import math

import numpy as np
from PIL import Image

JAVA = "src/main/java/com/wildcreatures/client/DragonLayer.java"
TEXDIR = "src/main/resources/assets/wildcreatures/textures/entity/"
ITEMDIR = "src/main/resources/assets/wildcreatures/textures/item/"

PARTS = []  # (name, parent, pivot, rot, boxes) in parent-first order


def q(v):
    """Sizes snap to quarter pixels so every face lines up with the 4x texture grid."""
    return round(v * 4) / 4


def part(name, parent, pivot, rot=(0, 0, 0), boxes=()):
    PARTS.append((name, parent, pivot, rot, list(boxes)))


def box(x, y, z, w, h, d, mat="scale", fin=None, **faces):
    """faces may override a face's material: top/bottom/front/back/left/right.
    fin = (axis, base, span) for fin boxes: the fin grows along that axis (0 x, 1 y, 2 z) away from base."""
    return dict(o=(x, y, z), s=(q(w), q(h), q(d)), mat=mat, faces=faces, fin=fin)


def mirror_box(b):
    (x, y, z), (w, h, d) = b["o"], b["s"]
    faces = dict(b["faces"])
    if "left" in faces or "right" in faces:
        faces["left"], faces["right"] = b["faces"].get("right"), b["faces"].get("left")
    return dict(o=(-(x + w), y, z), s=(w, h, d), mat=b["mat"], faces={k: v for k, v in faces.items() if v}, fin=b.get("fin"))


def sided(name_r, parent, pivot, rot, boxes):
    """Adds a right (+x) part and its mirrored left (-x) twin."""
    part(name_r, parent, pivot, rot, boxes)
    name_l = name_r[:-1] + "l"
    parent_l = parent[:-1] + "l" if parent.endswith("_r") else parent
    part(name_l, parent_l, (-pivot[0], pivot[1], pivot[2]), (rot[0], -rot[1], -rot[2]),
         [mirror_box(b) for b in boxes])


def spikes(top, zs, height, width=1.2):
    """A row of slim two-step spikes standing on a surface at y = top."""
    out = []
    for z in zs:
        out.append(box(-width / 2, top - height * 0.6, z, width, height * 0.6, width * 1.8, "spike"))
        out.append(box(-width * 0.3, top - height, z + width * 0.45, width * 0.6, height * 0.4, width, "spike"))
    return out


def fin(top, z0, z1, heights, rays=(), strip=1.5, dip=0.6):
    """A thin fin standing up from a surface at y = top, running from z0 to z1 (Sea Dragon).
    heights = ((z, h), ...) control points for its height; the top edge scallops down between the stiff rays."""
    zs, hs = zip(*heights)
    span = max(hs)
    joints = (z0,) + tuple(rays) + (z1,)

    def h_at(z):
        h = float(np.interp(z, zs, hs))
        for a, b in zip(joints, joints[1:]):
            if a <= z <= b:
                h -= dip * math.sin(math.pi * (z - a) / (b - a))
                break
        return max(0.5, q(h))

    out, z = [], z0
    while z < z1 - 0.01:
        ln = min(strip, z1 - z)
        h = h_at(z + ln / 2)
        out.append(box(-0.25, top - h, z, 0.5, h, ln, "fin", fin=(1, top, span)))
        z += ln
    for r in rays:
        h = q(float(np.interp(r, zs, hs)) + 0.25)
        out.append(box(-0.375, top - h, r - 0.375, 0.75, h, 0.75, "finray", fin=(1, top, span)))
    return out


def fluke():
    """The Sea Dragon's tail fin: a tall, forked fin like a fish's, standing upright at the end of the tail."""
    out, z = [], 0.0
    while z < 11.0 - 0.01:
        zc = z + 0.5
        up = q(1.0 + 5.0 * (zc / 10) ** 1.5)          # top lobe, above the tail's centre line
        dn = q(0.75 + 3.5 * (zc / 10) ** 1.5)         # bottom lobe
        notch = q(max(0.0, zc - 7.5) * 1.1)           # the fork eats into the middle of the trailing edge
        if notch < 0.75:
            out.append(box(-0.25, -up, z, 0.5, up + dn, 1.0, "fin", fin=(1, 0.0, 6.0)))
        else:
            out.append(box(-0.25, -up, z, 0.5, up - notch, 1.0, "fin", fin=(1, 0.0, 6.0)))
            out.append(box(-0.25, notch, z, 0.5, dn - notch, 1.0, "fin", fin=(1, 0.0, 6.0)))
        z += 1.0
    return out


def frill():
    """A fan-shaped fin behind the jaw (Sea Dragon), spread by stiff spines. Grows backward (+z)."""
    out, z = [], 0.0
    while z < 7.0 - 0.01:
        zc = z + 0.5
        hh = q(0.5 + 2.25 * math.sqrt(max(0.0, 1 - (zc / 7.25) ** 2)) - 0.3 * abs(math.sin(zc * 1.4)))
        out.append(box(-0.25, -hh, z, 0.5, 2 * hh, 1.0, "fin", fin=(2, 0.0, 7.0)))
        z += 1.0
    for y, ln in ((-1.5, 5.75), (0.0, 7.0), (1.5, 5.75)):
        out.append(box(-0.375, y - 0.375, 0.0, 0.75, 0.75, ln, "finray", fin=(2, 0.0, 7.0)))
    return out


# ---------------- shared pieces ----------------
def toes(z):
    out = []
    for x in (-1.55, -0.45, 0.65):
        out.append(box(x, -0.25, z - 1.75, 0.9, 1.25, 2))               # toe
        out.append(box(x + 0.1, 0.35, z - 2.75, 0.7, 0.65, 1.25, "claw"))  # talon
    out.append(box(-0.4, 0.25, 1, 0.8, 0.75, 1.5, "claw"))               # dew claw at the back
    return out


# The wing membrane is a run of thin strips; each strip's depth follows the trailing edge,
# which scallops in between the finger bones like a bat's.
WING_IN, WING_OUT = 22.0, 20.0
STRUTS_IN, STRUTS_OUT = (7.5, 15.0), (5.0, 10.0, 15.0)


def edge_depth(x, length, root, end, struts, dip):
    base = root + (end - root) * (x / length) ** 1.4
    joints = (0.0,) + struts + (length,)
    for a, b in zip(joints, joints[1:]):
        if a <= x <= b:
            return base - dip * math.sin(math.pi * (x - a) / (b - a))
    return base


def membrane(length, root, end, struts, dip, strip=1.5):
    out, x = [], 0.0
    while x < length - 0.01:
        w = min(strip, length - x)
        d = edge_depth(x + w / 2, length, root, end, struts, dip)
        out.append(box(x, -0.25, 0.75, w, 0.5, max(1.0, d), "membrane"))
        x += w
    for s in struts:
        out.append(box(s - 0.375, -0.5, 0.75, 0.75, 0.75, edge_depth(s, length, root, end, struts, dip) - 1, "strut"))
    return out


def footprint(b):
    w, h, d = b["s"]
    return math.ceil(2 * d + 2 * w), math.ceil(d + h)


def rot_zyx(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    rxm = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    rym = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rzm = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rzm @ rym @ rxm


def build_model(kind):
    """Builds one body: "winged" (the shared dragon body) or "sea" (the same skeleton, so the same animations,
    but a fin along the back instead of spikes, a forked tail fin and fan-shaped frills behind the jaw)."""
    global PARTS
    PARTS = []
    sea = kind == "sea"

    # ---------------- body ----------------
    # A deep ribcage that narrows to a tucked-up waist and lean hips.
    part("body", "root", (0, 5.5, 0), boxes=[
        box(-5.5, -6, -16, 11, 12, 13),                                  # ribcage
        box(-4.5, -5, -18, 9, 10, 2),                                    # breastbone
        box(-4.5, -5.5, -3, 9, 9.5, 8.5),                                # waist
        box(-5, -6, 5, 10, 9, 9),                                        # hips
        box(-4, -5, 14, 8, 7, 1.5),                                      # rump, narrowing into the tail
        box(-2, -7, -15, 4, 1, 29, "ridge"),                             # spine
        box(-6, -4.5, -15, 0.5, 7, 7.5), box(5.5, -4.5, -15, 0.5, 7, 7.5),   # shoulder muscles
        box(-5.5, -4.5, 6, 0.5, 7, 7), box(5, -4.5, 6, 0.5, 7, 7),           # haunches
    ] + (fin(-7, -15, 14.5, ((-15, 1.0), (-10, 3.25), (-5, 4.25), (0, 3.5), (5, 3.25), (10, 2.5), (14.5, 1.75)),
             rays=(-11, -7, -3, 1, 5, 9, 12.5), dip=0.7) if sea else
         [b for z, hgt in ((-14.5, 2.2), (-11, 2.8), (-7.5, 3.0), (-4, 2.8), (-0.5, 2.5),
                           (3, 2.3), (6.5, 2.1), (10, 1.8), (13, 1.5))
          for b in spikes(-7, [z], hgt)]))

    # ---------------- neck and head ----------------
    part("neck1", "body", (0, -2.5, -17), (-0.7, 0, 0), [
        box(-3.5, -3.5, -12, 7, 7, 13),
        box(-1.5, -4, -11, 3, 0.5, 11, "ridge"),
    ] + (fin(-4, -11.5, 0.5, ((-11.5, 1.25), (0.5, 2.0)), rays=(-8, -4)) if sea else spikes(-4, (-10, -6.5, -3), 1.8, 1.0)))
    part("neck2", "neck1", (0, 0, -11.5), (0.25, 0, 0), [
        box(-3, -3, -12, 6, 6, 13),
        box(-1.25, -3.5, -11, 2.5, 0.5, 11, "ridge"),
    ] + (fin(-3.5, -11, 0.5, ((-11, 0.75), (0.5, 1.5)), rays=(-7.5, -3.5)) if sea else spikes(-3.5, (-10, -6.5, -3), 1.5, 0.9)))
    ear_frills = [] if sea else [
        box(-3.75, -3, 0.5, 0.5, 3.5, 3, "membrane"), box(3.25, -3, 0.5, 0.5, 3.5, 3, "membrane")]   # ear frills
    part("head", "neck2", (0, 0, -11.5), (0.45, 0, 0), [
        box(-3.5, -3.5, -7, 7, 5.5, 8),                                  # skull
        box(-2.5, -2.5, -16, 5, 3.5, 9.5, bottom="mouth"),               # snout
        box(-2, -3, -15.5, 4, 0.5, 8.5, "ridge"),                        # snout ridge
        box(-4, -4.5, -7, 2.75, 1.25, 4.5, "ridge"), box(1.25, -4.5, -7, 2.75, 1.25, 4.5, "ridge"),  # brows
        box(-3.75, -3, -6, 0.5, 1.5, 2.5, "eye"), box(3.25, -3, -6, 0.5, 1.5, 2.5, "eye"),
        box(-2, -3, -16.25, 1.25, 1, 1.5, "ridge"), box(0.75, -3, -16.25, 1.25, 1, 1.5, "ridge"),     # nostril rims
        box(-1.75, -2.5, -16.5, 0.75, 0.5, 0.5, "nostril"), box(1, -2.5, -16.5, 0.75, 0.5, 0.5, "nostril"),
        box(-0.5, -3.75, -14.5, 1, 0.75, 2.5, "bone"),                   # little nose horn
        box(-4.25, -1, -2.5, 0.75, 1.5, 4, "bone"), box(3.5, -1, -2.5, 0.75, 1.5, 4, "bone"),         # cheek spurs
    ] + ear_frills
      + [box(x, 1, z, 0.75, 1.25, 0.75, "tooth") for z in (-14.5, -12.5, -10.5, -8.5) for x in (-2.25, 1.5)]
      + [box(x, 1, -15.75, 0.75, 2, 0.75, "tooth") for x in (-1.75, 1)])                              # fangs
    part("jaw", "head", (0, 1, -6.5), (0.05, 0, 0), [
        box(-2.25, 0, -9.5, 4.5, 1.75, 10, top="mouth"),
        box(-1.5, -0.5, -8, 3, 0.5, 6.5, "tongue"),
        box(-0.5, 1.5, -3, 1, 1.25, 2.5, "spike"),                       # chin spike
    ] + [box(x, -1, z, 0.75, 1, 0.75, "tooth") for z in (-9, -7, -5, -3) for x in (-2, 1.25)])
    if sea:
        # long swept-back spines instead of horns, and a fan-shaped fin behind each jaw hinge
        sided("horn_r", "head", (2.25, -3.25, -1), (0.35, 0.2, 0), [box(-0.6, -0.6, 0, 1.25, 1.25, 7, "bone")])
        sided("horn_tip_r", "horn_r", (0, 0, 6.5), (0.15, 0, 0), [box(-0.4, -0.4, 0, 0.75, 0.75, 5, "horntip")])
        sided("frill_r", "head", (3.6, -0.5, -2.5), (0.05, 0.55, 0.1), frill())
    else:
        sided("horn_r", "head", (2.25, -3.25, -1), (0.55, 0.22, 0), [box(-0.75, -0.75, 0, 1.5, 1.5, 9, "bone")])
        sided("horn_tip_r", "horn_r", (0, 0, 8.5), (0.4, 0, 0), [box(-0.5, -0.5, 0, 1, 1, 7, "horntip")])
        sided("horn2_r", "head", (3.25, -1.5, -0.5), (0.2, 0.55, 0), [box(-0.5, -0.5, 0, 1, 1, 5, "bone")])

    # ---------------- tail ----------------
    part("tail1", "body", (0, -2.5, 15), (-0.22, 0, 0), [
        box(-3.75, -3.75, -1, 7.5, 7.5, 13), box(-1.5, -4.25, 0, 3, 0.5, 11, "ridge")]
        + (fin(-4.25, -1, 12, ((-1, 1.75), (12, 2.25)), rays=(3, 7.5)) if sea else spikes(-4.25, (1.5, 5.5, 9.5), 1.8, 1.0)))
    part("tail2", "tail1", (0, 0, 11.5), (-0.08, 0, 0), [
        box(-3, -3, -1, 6, 6, 13), box(-1.25, -3.5, 0, 2.5, 0.5, 11, "ridge")]
        + (fin(-3.5, -1, 12, ((-1, 2.0), (12, 1.75)), rays=(3, 7.5)) if sea else spikes(-3.5, (1.5, 5.5, 9.5), 1.5, 0.9)))
    part("tail3", "tail2", (0, 0, 11.5), (0.1, 0, 0), [
        box(-2.25, -2.25, -1, 4.5, 4.5, 13)]
        + (fin(-2.25, -1, 12, ((-1, 1.75), (12, 1.5)), rays=(3, 7.5)) if sea else spikes(-2.25, (2, 7), 1.2, 0.75)))
    part("tail4", "tail3", (0, 0, 11.5), (0.12, 0, 0), [
        box(-1.5, -1.5, -1, 3, 3, 13)]
        + (fin(-1.5, -1, 12, ((-1, 1.5), (12, 1.5)), rays=(3, 7.5)) if sea else spikes(-1.5, (3, 8), 0.9, 0.6)))
    part("tail_tip", "tail4", (0, 0, 11.5), boxes=[box(-1, -1, -1, 2, 2, 4)] + (fluke() if sea else [
        box(-3.5, -0.25, 1, 7, 0.5, 3, "spike"), box(-2.5, -0.25, 4, 5, 0.5, 2.5, "spike"),
        box(-1.5, -0.25, 6.5, 3, 0.5, 2, "spike"), box(-0.5, -0.25, 8.5, 1, 0.5, 1.5, "spike")]))

    # ---------------- legs (right side, mirrored to the left) ----------------
    sided("front_leg_r", "body", (5, 2.5, -11), (0.25, 0, 0), [
        box(-2.25, -4, -2.75, 4.5, 7.5, 5.5),                            # shoulder
        box(-2, 2.5, -2.1, 4, 4.5, 4.25)])                               # upper arm
    sided("front_shin_r", "front_leg_r", (0, 6, 0), (-0.45, 0, 0), [
        box(-1.6, -0.5, -1.6, 3.25, 9.75, 3.25),
        box(-0.4, 0.5, 1.5, 0.75, 1.5, 1.5, "spike")])                   # elbow spur
    sided("front_foot_r", "front_shin_r", (0, 9.38, 0), (0.2, 0, 0), [box(-1.9, 0, -2.25, 3.75, 1, 3.75)] + toes(-2.25))
    sided("hind_leg_r", "body", (4.5, 0.5, 9.5), (-0.55, 0, 0), [
        box(-2.5, -4.5, -3.75, 5, 9, 7.5),                               # thigh
        box(-2, 4, -2.5, 4, 4.5, 5)])
    sided("hind_shin_r", "hind_leg_r", (0, 8, 0), (1.25, 0, 0), [
        box(-1.5, -0.5, -1.5, 3, 8.25, 3),
        box(-0.4, -1, 1.25, 0.75, 1.5, 1.5, "spike")])                   # heel spur
    sided("hind_foot_r", "hind_shin_r", (0, 7.5, 0), (-0.85, 0, 0), [box(-1.25, -0.5, -1.25, 2.5, 5.25, 2.5)])
    sided("hind_paw_r", "hind_foot_r", (0, 4.5, 0), (0.15, 0, 0), [box(-1.9, 0, -2.25, 3.75, 1, 3.75)] + toes(-2.25))

    # ---------------- wings (the Sea Dragon swims with them) ----------------
    sided("wing_r", "body", (4.5, -5.5, -11), (0, 0, 0), [
        box(0, -1, -1, WING_IN + 0.5, 2, 2),                             # arm bone
        box(0.5, -1.5, -0.75, 9, 0.5, 1.5, "ridge"),                     # muscle along the top
        box(WING_IN - 1, -2.25, -1, 1.25, 1.5, 1.25, "claw"),            # thumb claw
    ] + membrane(WING_IN, 21.0, 17.0, STRUTS_IN, 3.0))
    sided("wing_tip_r", "wing_r", (WING_IN, 0, 0), (0, 0, 0), [
        box(0, -0.75, -0.75, WING_OUT, 1.5, 1.5),                        # finger bone
        box(WING_OUT - 0.5, -1, -1, 1.25, 1.25, 1.25, "claw"),
    ] + membrane(WING_OUT, 17.0, 1.5, STRUTS_OUT, 2.5))

    # ---------------- UV packing ----------------
    all_boxes = [b for p in PARTS for b in p[4]]
    order = sorted(all_boxes, key=lambda b: -footprint(b)[1])
    tw = 256
    x = y = row_h = 0
    for b in order:
        fw, fh = footprint(b)
        if x + fw > tw:
            x, y, row_h = 0, y + row_h, 0
        b["uv"] = (x, y)
        x += fw
        row_h = max(row_h, fh)
    th = 1
    while th < y + row_h:
        th *= 2
    print(f"{kind}: texture {tw}x{th}, {len(all_boxes)} boxes in {len(PARTS)} parts")

    # ---------------- rest pose: where every part sits in model space ----------------
    pose = {"root": (np.eye(3), np.zeros(3))}
    for name, parent, pivot, rot, _ in PARTS:
        pr, pt = pose[parent]
        pose[name] = (pr @ rot_zyx(*rot), pt + pr @ np.array(pivot, dtype=float))
    model = dict(kind=kind, parts=PARTS, pose=pose, tw=tw, th=th)

    head_pivot = to_model(model, "head", (0, 0, 0))
    mouth = to_model(model, "head", (0, 1, -16))
    print(f"  head pivot: {-head_pivot[2]:.1f} px forward, {24 - head_pivot[1]:.1f} px up; "
          f"mouth: {-mouth[2]:.1f} forward, {24 - mouth[1]:.1f} up")
    for foot in ("front_foot_r", "hind_paw_r"):
        print(f"  {foot} sole at y = {to_model(model, foot, (0, 1, 0))[1]:.2f} (ground is 24)")
    return model


def to_model(model, name, p):
    r, t = model["pose"][name]
    return t + r @ np.array(p, dtype=float)


MODELS = {kind: build_model(kind) for kind in ("winged", "sea")}


# ---------------- painting ----------------
# Painted at RES texels per model pixel, so the scales and gradients are much finer than
# one Minecraft pixel. The model still uses the TW x TH layout; Minecraft just samples
# the bigger image.
RES = 4
VARIANTS = [
    dict(name="red_dragon",
         dorsal=(92, 10, 16), flank=(162, 26, 24), flank2=(204, 62, 34), belly=(238, 160, 78),
         spike_base=(118, 18, 18), spike_tip=(38, 8, 10), ridge=0.85,
         horn_base=(232, 214, 178), horn_mid=(176, 148, 112), horn_tip=(48, 36, 32),
         membrane_root=(112, 18, 20), membrane_edge=(196, 70, 42), vein=(84, 12, 14),
         strut=(104, 16, 16), leg_dark=(70, 10, 12),
         eye=(255, 214, 60), eye_rim=(226, 96, 14), pupil=(20, 8, 4),
         claw=(36, 28, 30), tooth=(240, 232, 208), mouth=(92, 18, 26), tongue=(168, 52, 72), nostril=(22, 6, 6),
         scale_edge=-0.07, rime=0.0),
    dict(name="frost_dragon",
         dorsal=(134, 178, 216), flank=(186, 214, 238), flank2=(220, 236, 248), belly=(246, 250, 255),
         spike_base=(176, 218, 242), spike_tip=(240, 250, 255), ridge=0.92,
         horn_base=(246, 250, 253), horn_mid=(204, 226, 244), horn_tip=(104, 146, 196),
         membrane_root=(160, 198, 230), membrane_edge=(236, 246, 253), vein=(132, 174, 216),
         strut=(150, 188, 224), leg_dark=(118, 160, 206),
         eye=(170, 240, 255), eye_rim=(40, 122, 222), pupil=(10, 22, 52),
         claw=(64, 84, 116), tooth=(246, 250, 255), mouth=(74, 86, 132), tongue=(122, 102, 172), nostril=(34, 54, 86),
         scale_edge=-0.03, rime=0.10,
         item_gradient=[(20, (40, 70, 120)), (70, (150, 196, 232)), (120, (210, 232, 248)), (190, (250, 253, 255))]),
    dict(name="lightning_dragon",
         # navy back, royal blue sides, then a short warm band into a gold belly
         stops=[(0.0, (12, 20, 58)), (0.34, (24, 46, 124)), (0.6, (46, 84, 172)), (0.74, (72, 104, 176)),
                (0.85, (192, 148, 62)), (1.0, (240, 198, 98))],
         dorsal=(12, 20, 58), flank=(24, 46, 124), flank2=(46, 84, 172), belly=(240, 198, 98),
         spike_base=(204, 152, 50), spike_tip=(255, 230, 142), ridge=0.85,
         horn_base=(255, 238, 168), horn_mid=(216, 166, 64), horn_tip=(112, 72, 22),
         membrane_root=(14, 22, 64), membrane_edge=(48, 88, 178), vein=(220, 172, 66),
         strut=(158, 116, 40), leg_dark=(10, 14, 42),
         eye=(255, 248, 196), eye_rim=(255, 184, 40), pupil=(34, 18, 0),
         claw=(30, 28, 38), tooth=(240, 232, 208), mouth=(36, 20, 54), tongue=(112, 54, 112), nostril=(6, 8, 24),
         scale_edge=-0.08, rime=0.0,
         # thin branching lightning lines over the back and sides, glowing in the dark
         markings=dict(color=(255, 214, 104), glow=(210, 156, 44)),
         item_gradient=[(20, (8, 12, 42)), (70, (28, 54, 140)), (120, (66, 106, 198)), (190, (250, 214, 112))]),
    dict(name="sea_dragon", model="sea",
         # deep-sea countershading: dark navy-teal back, turquoise sides, pale seafoam belly
         stops=[(0.0, (8, 34, 56)), (0.3, (14, 74, 98)), (0.55, (30, 128, 140)), (0.72, (84, 178, 176)),
                (0.86, (170, 222, 206)), (1.0, (214, 240, 226))],
         dorsal=(8, 34, 56), flank=(14, 74, 98), flank2=(30, 128, 140), belly=(214, 240, 226),
         spike_base=(34, 110, 124), spike_tip=(170, 230, 220), ridge=0.88,
         horn_base=(206, 238, 228), horn_mid=(96, 172, 176), horn_tip=(18, 58, 76),
         membrane_root=(10, 52, 74), membrane_edge=(64, 168, 172), vein=(130, 214, 206),
         strut=(28, 96, 112), leg_dark=(6, 30, 44),
         fin_root=(12, 60, 82), fin_edge=(120, 216, 206), fin_ray=(6, 36, 52),
         eye=(170, 255, 236), eye_rim=(0, 168, 170), pupil=(2, 24, 30),
         claw=(30, 44, 52), tooth=(238, 240, 226), mouth=(40, 30, 58), tongue=(108, 70, 110), nostril=(4, 18, 26),
         scale_edge=-0.05, rime=0.0,
         # two rows of glowing spots along each side, like a deep-sea fish
         markings=dict(kind="spots", color=(150, 255, 236), glow=(60, 220, 210)),
         item_gradient=[(20, (4, 26, 40)), (70, (16, 84, 104)), (120, (40, 150, 156)), (190, (190, 240, 222))]),
]

BODY_LIKE = ("body", "neck1", "neck2", "head", "jaw", "tail1", "tail2", "tail3", "tail4", "tail_tip")
SCALE_SIZE = {"body": 10, "tail1": 9, "neck1": 8, "neck2": 7, "tail2": 8, "tail3": 7, "tail4": 6, "tail_tip": 5,
              "head": 5, "jaw": 5}


def is_limb(name):
    return any(k in name for k in ("leg", "shin", "foot", "paw"))


def core_range(boxes):
    """Vertical extent of a part's biggest box: its back is 0, its belly 1."""
    main = max(boxes, key=lambda b: b["s"][0] * b["s"][1] * b["s"][2] if b["mat"] == "scale" else 0)
    return main["o"][1], main["o"][1] + main["s"][1]


def smoothstep(a, b, t):
    t = np.clip((t - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def ramp(stops, t):
    """Smoothly blended colour ramp: stops = [(pos, (r, g, b)), ...]."""
    t = np.clip(t, 0.0, 1.0)
    out = np.zeros(t.shape + (3,))
    out[...] = stops[0][1]
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        k = smoothstep(p0, p1, t)[..., None]
        sel = (t >= p0)[..., None]
        out = np.where(sel, np.array(c0) + (np.array(c1) - np.array(c0)) * k, out)
    return out


def mix(a, b, t):
    return np.array(a, dtype=float) + (np.array(b, dtype=float) - np.array(a, dtype=float)) * np.asarray(t)[..., None]


def hash3(ix, iy, iz, salt):
    v = (ix.astype(np.int64) * 73856093) ^ (iy.astype(np.int64) * 19349663) ^ (iz.astype(np.int64) * 83492791) ^ (salt * 2654435761)
    v = (v * 2246822519) & 0xFFFFFFFF
    v ^= v >> 13
    return (v & 0xFFFF) / 65535.0


def vnoise(p, scale, salt):
    """Smooth 3D value noise in -1..1, continuous across faces and parts."""
    x, y, z = p[0] / scale, p[1] / scale, p[2] / scale
    ix, iy, iz = np.floor(x), np.floor(y), np.floor(z)
    fx, fy, fz = smoothstep(0, 1, x - ix), smoothstep(0, 1, y - iy), smoothstep(0, 1, z - iz)
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                out = out + w * hash3(ix + dx, iy + dy, iz + dz, salt)
    return out * 2 - 1


def scale_pattern(I, J, cw):
    """Overlapping rounded scales laid like roof shingles: each one shows its rounded lower edge.
    Returns (shade, edge 0..1) per texel."""
    ch = cw * 0.62
    R = cw * 0.64
    best_d = np.full(I.shape, 9.0)
    found = np.zeros(I.shape, bool)
    first = np.floor((J - R) / ch)
    for k in range(4):
        row = first + k
        cy = row * ch
        off = (row % 2) * cw / 2
        col = np.floor((I + 0.5 - off) / cw + 0.5)
        for dc in (-1, 0, 1):
            cx = (col + dc) * cw + off
            dx, dy = I + 0.5 - cx, J + 0.5 - cy
            dist = np.sqrt(dx * dx + dy * dy) / R
            take = (~found) & (dist <= 1.0)
            best_d = np.where(take, dist, best_d)
            found |= take
    best_d = np.where(found, best_d, 1.0)
    shade = 0.6 - best_d                       # bright middle, darker toward the rim
    edge = smoothstep(0.78, 0.98, best_d)
    return shade, edge


def face_grid(b, face):
    """Texture rectangle of one face, and the local position / outward normal of every texel on it."""
    (x0, y0, z0), (w, h, d) = b["o"], b["s"]
    u, v = b["uv"]
    regions = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
               "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}
    u0, v0, fw, fh = regions[face]
    U0, V0 = int(round(u0 * RES)), int(round(v0 * RES))
    FW, FH = max(1, int(round(fw * RES))), max(1, int(round(fh * RES)))
    J, I = np.mgrid[0:FH, 0:FW].astype(float)
    ti, tj = (I + 0.5) / FW, (J + 0.5) / FH
    one = np.ones_like(ti)
    if face == "top":
        p, n = (x0 + ti * w, y0 * one, z0 + d - tj * d), (0, -1, 0)
    elif face == "bottom":
        p, n = (x0 + ti * w, (y0 + h) * one, z0 + d - tj * d), (0, 1, 0)
    elif face == "right":
        p, n = (x0 * one, y0 + tj * h, z0 + d - ti * d), (-1, 0, 0)
    elif face == "front":
        p, n = (x0 + ti * w, y0 + tj * h, z0 * one), (0, 0, -1)
    elif face == "left":
        p, n = ((x0 + w) * one, y0 + tj * h, z0 + ti * d), (1, 0, 0)
    else:
        p, n = (x0 + w - ti * w, y0 + tj * h, (z0 + d) * one), (0, 0, 1)
    return (U0, V0, FW, FH), I, J, ti, tj, np.array(p), np.array(n, dtype=float)


def body_t(name, ytop, ybot, py, face):
    """0 on the back, 1 on the belly."""
    t = np.clip((py - ytop) / max(0.5, ybot - ytop), 0.0, 1.0)
    if face == "bottom":
        t = np.maximum(t, 0.9)
    if name == "jaw":
        t = 0.45 + 0.55 * t
    return t


def make_bolts(seed):
    """Zigzag lightning bolts running down the sides from the spine, from the base of the neck to the tail.
    Each is a list of (z along the body in model pixels, t from back 0 to belly 1)."""
    r = np.random.default_rng(seed)
    bolts, z = [], -36.0
    while z < 64:
        pts, t, zz, d = [(z, 0.05)], 0.05, z, 1 if r.random() < 0.5 else -1
        while t < 0.56:
            t += r.uniform(0.08, 0.13)
            zz += d * r.uniform(1.0, 2.4) + 0.9               # zig, zag, drifting toward the tail
            d = -d
            pts.append((zz, t))
        bolts.append(pts)
        if r.random() < 0.75:                                 # a fork off the middle
            bz, bt = pts[int(r.integers(1, len(pts) - 1))]
            fork, side = [(bz, bt)], 1 if r.random() < 0.5 else -1
            for _ in range(2):
                bt += r.uniform(0.07, 0.11)
                bz += side * r.uniform(1.4, 2.6)
                side = -side if r.random() < 0.5 else side
                fork.append((bz, bt))
            bolts.append(fork)
        z += r.uniform(8.0, 12.0)
    return bolts


BOLTS = make_bolts(5)


def lightning_lines(model, tb, height):
    """0..1 strength of the lightning bolt markings: a sharp bright core with a soft glow around it,
    kept off the belly. height turns the back-to-belly position into model pixels."""
    zm, ym = model[2], tb * height
    d = np.full(zm.shape, 99.0)
    for bolt in BOLTS:
        for (z1, t1), (z2, t2) in zip(bolt, bolt[1:]):
            y1, y2 = t1 * height, t2 * height
            ez, ey = z2 - z1, y2 - y1
            k = np.clip(((zm - z1) * ez + (ym - y1) * ey) / (ez * ez + ey * ey + 1e-9), 0, 1)
            d = np.minimum(d, np.hypot(zm - (z1 + k * ez), ym - (y1 + k * ey)))
    line = np.maximum(np.exp(-(d / 0.32) ** 2), 0.35 * np.exp(-(d / 0.9) ** 2))
    return line * (1.0 - smoothstep(0.62, 0.8, tb))


def spot_rows(model, tb, height):
    """0..1 strength of glowing spots in two rows along each side, like a deep-sea fish's lights.
    The spots sit at fixed places along the body (model z), so they line up across the parts."""
    zm, ym = model[2], tb * height
    out = np.zeros(zm.shape)
    for row_t, step, radius, offset in ((0.58, 3.2, 0.42, 0.0), (0.34, 4.6, 0.32, 1.7)):
        zc = np.round((zm - offset) / step) * step + offset
        d = np.hypot(zm - zc, ym - row_t * height)
        out = np.maximum(out, np.maximum(np.exp(-(d / radius) ** 4), 0.3 * np.exp(-(d / (radius * 2.2)) ** 2)))
    return out * smoothstep(0.1, 0.25, tb) * (1.0 - smoothstep(0.7, 0.8, tb))


def paint_variant(v):
    m = MODELS[v.get("model", "winged")]
    TW, TH = m["tw"], m["th"]
    img = np.zeros((TH * RES, TW * RES, 3))
    alpha = np.zeros((TH * RES, TW * RES))
    glow = np.zeros((TH * RES, TW * RES, 4))
    stops = v.get("stops") or [(0.0, v["dorsal"]), (0.38, v["flank"]), (0.72, v["flank2"]), (1.0, v["belly"])]
    rng = np.random.default_rng(11)
    for (name, parent, pivot, rot, boxes) in m["parts"]:
        ytop, ybot = core_range(boxes)
        rmat, tvec = m["pose"][name]
        inner_wing = name in ("wing_r", "wing_l")
        wing_len = WING_IN if inner_wing else WING_OUT
        struts = STRUTS_IN if inner_wing else STRUTS_OUT
        outer_sign = 1.0 if name.endswith("_r") else -1.0
        for b in boxes:
            (x0, y0, z0), (w, h, d) = b["o"], b["s"]
            for face in ("top", "bottom", "right", "front", "left", "back"):
                mat = b["faces"].get(face, b["mat"])
                (U0, V0, FW, FH), I, J, ti, tj, p, n = face_grid(b, face)
                px, py, pz = p
                model = tvec[:, None, None] + np.tensordot(rmat, p, axes=1)
                k = 1.0 + 0.035 * vnoise(model, 7.0, 1) + 0.02 * vnoise(model, 2.5, 2)
                tb = None                                                 # back (0) to belly (1), where markings may go
                if mat in ("scale", "ridge"):
                    if is_limb(name):
                        side = n[0] * outer_sign                          # +1 outer face, -1 inner face
                        t = 0.42 - 0.22 * side
                        if face == "top":
                            t = 0.2
                        elif face == "bottom":
                            t = 0.85
                        col = ramp(stops, t * np.ones_like(px))
                        g = smoothstep(10.0, 24.0, model[1])              # darker toward the feet
                        col = col + (np.array(v["leg_dark"]) - col) * (0.55 * g)[..., None]
                        cw = 5 if ("foot" in name or "paw" in name) else 6
                    elif name.startswith("wing"):
                        if face == "top":
                            t = np.full_like(px, 0.05)
                        elif face == "bottom":
                            t = np.full_like(px, 0.75)
                        else:
                            t = np.clip((py + 1.0) / 2.0, 0, 1) * 0.8
                        col = ramp(stops, t)
                        cw = 5
                    else:
                        col = ramp(stops, body_t(name, ytop, ybot, py, face))
                        cw = SCALE_SIZE.get(name, 6)
                        if name not in ("head", "jaw"):
                            tb = body_t(name, ytop, ybot, py, face)
                    if mat == "ridge":
                        col = col * v["ridge"]
                    if face == "bottom" and name in BODY_LIKE and name not in ("head", "jaw", "tail_tip"):
                        # belly plates across the underside
                        jz = (pz * RES) % 6.0
                        k = k * (1.03 - 0.025 * jz / 6.0 - 0.07 * np.exp(-((jz - 5.6) / 0.7) ** 2))
                    else:
                        JJ = (FH - 1 - J) if face == "top" else J
                        shade, edge = scale_pattern(I, JJ, cw)
                        k = k * (1.0 + 0.06 * shade + v["scale_edge"] * edge)
                        if v["rime"] and not is_limb(name) and name != "jaw":
                            up = 1.0 - np.clip(body_t(name, ytop, ybot, py, face) / 0.45, 0, 1)
                            col = col + (255 - col) * (v["rime"] * edge * up)[..., None]
                elif mat == "spike":
                    if (y0 + h / 2) < ytop:
                        t = (ytop - py) / 3.5
                    elif (y0 + h / 2) > ybot:
                        t = (py - ybot) / 2.5
                    else:
                        t = np.abs(px) / 3.5
                    col = mix(v["spike_base"], v["spike_tip"], smoothstep(0, 1, np.clip(t, 0, 1)))
                    if face == "top":
                        k = k * 1.06
                elif mat in ("bone", "horntip"):
                    t = np.clip((pz - z0) / max(d, 0.5), 0, 1)
                    a, c = (v["horn_base"], v["horn_mid"]) if mat == "bone" else (v["horn_mid"], v["horn_tip"])
                    col = mix(a, c, t)
                    k = k * (1.0 - 0.06 * np.exp(-(((pz * RES) % 5.0 - 4.5) / 0.6) ** 2))   # faint growth rings
                elif mat == "membrane":
                    if name == "head":
                        col = mix(v["membrane_root"], v["membrane_edge"], 0.7 * np.clip((pz - 0.5) / 3.0, 0, 1))
                    else:
                        s = np.clip((pz - 0.75) / (21.0 if inner_wing else 17.0), 0, 1)
                        along = 0.0 if inner_wing else np.clip(px / wing_len, 0, 1)
                        col = mix(v["membrane_root"], v["membrane_edge"], smoothstep(0, 1, 0.8 * s + 0.25 * along))
                        vein = np.zeros_like(px)
                        for sx in struts:
                            vein = np.maximum(vein, np.exp(-((px - sx) / 0.35) ** 2))
                        # thin veins fanning back from the bones
                        fan = np.abs(((px * 0.6 + pz * 0.35) % 3.0) - 1.5)
                        vein = np.maximum(vein, 0.5 * np.exp(-(fan / 0.12) ** 2) * smoothstep(0.1, 0.5, s) * (1 - s))
                        col = col + (np.array(v["vein"]) - col) * (0.45 * vein)[..., None]
                    if face == "bottom":
                        k = k * 1.06
                    elif face != "top":
                        k = k * 0.9
                elif mat == "strut":
                    col = mix(v["strut"], v["membrane_root"], 0.4 * np.clip((pz - 0.75) / 21.0, 0, 1))
                elif mat in ("fin", "finray"):
                    # dark at the base, fading to a pale edge, with faint stiff rays running out to the edge
                    axis, base, span = b["fin"]
                    t = np.clip(np.abs((px, py, pz)[axis] - base) / span, 0, 1)
                    col = mix(v["fin_root"], v["fin_edge"], smoothstep(0, 1, t))
                    if mat == "finray":
                        col = mix(v["fin_ray"], v["fin_edge"], 0.35 * t)
                    else:
                        across = pz if axis != 2 else py
                        ray = np.exp(-((((across * RES) % 6.0) - 3.0) / 0.7) ** 2)
                        col = col + (np.array(v["fin_ray"], float) - col) * (0.3 * ray * (1 - 0.5 * t))[..., None]
                        col = col + (np.array(v["fin_edge"], float) * 1.08 - col) * (0.35 * smoothstep(0.82, 1.0, t))[..., None]
                    if face not in ("left", "right"):
                        k = k * 0.9
                elif mat == "eye":
                    if face in ("left", "right"):
                        r = np.sqrt((ti - 0.5) ** 2 + ((tj - 0.5) * 0.8) ** 2)
                        col = mix(v["eye"], v["eye_rim"], smoothstep(0.0, 0.6, r))
                        slit = (np.abs(ti - 0.5) < 0.09) & (np.abs(tj - 0.5) < 0.42)
                        col = np.where(slit[..., None], np.array(v["pupil"], float), col)
                        col = np.where(((ti < 0.3) & (tj < 0.35))[..., None], np.array((255, 250, 230), float), col)
                    else:
                        col = np.array(v["eye_rim"], float) * 0.8 * np.ones(px.shape + (3,))
                    k = 1.0
                elif mat == "tooth":
                    col = mix(np.array(v["tooth"]) * 0.82, v["tooth"], tj if y0 >= 0 else 1 - tj)
                elif mat == "claw":
                    col = np.array(v["claw"], float) * np.ones(px.shape + (3,))
                    k = k * (1.35 if face == "top" else 0.85 if face == "bottom" else 1.0)
                elif mat == "mouth":
                    col = mix(np.array(v["mouth"]) * 0.75, v["mouth"], tj)
                elif mat == "tongue":
                    col = np.array(v["tongue"], float) * np.ones(px.shape + (3,))
                    k = k * (1 - 0.2 * np.exp(-(((ti - 0.5) / 0.06) ** 2)))
                elif mat == "nostril":
                    col = np.array(v["nostril"], float) * np.ones(px.shape + (3,))
                else:
                    raise ValueError(mat)
                mk = None
                if v.get("markings") and tb is not None:
                    pattern = spot_rows if v["markings"].get("kind") == "spots" else lightning_lines
                    mk = pattern(model, tb, ybot - ytop)
                    col = col + (np.array(v["markings"]["color"], float) - col) * (0.85 * mk)[..., None]
                col = col * np.asarray(k)[..., None] + rng.normal(0, 1.2, col.shape)
                img[V0:V0 + FH, U0:U0 + FW] = col
                if mk is not None:
                    gl = glow[V0:V0 + FH, U0:U0 + FW]
                    gl[..., :3] = np.maximum(gl[..., :3], np.array(v["markings"]["glow"], float) * mk[..., None])
                    gl[..., 3] = np.maximum(gl[..., 3], np.where(mk > 0.02, 255.0, 0.0))
                alpha[V0:V0 + FH, U0:U0 + FW] = 255
                if mat == "eye":
                    glow[V0:V0 + FH, U0:U0 + FW, :3] = col
                    glow[V0:V0 + FH, U0:U0 + FW, 3] = 255
    out = np.dstack([np.clip(img, 0, 255), alpha]).astype(np.uint8)
    Image.fromarray(out, "RGBA").save(TEXDIR + v["name"] + ".png", optimize=True)
    Image.fromarray(np.clip(glow, 0, 255).astype(np.uint8), "RGBA").save(TEXDIR + v["name"] + "_eyes.png", optimize=True)


def recolor_scale_item(v):
    """Makes NAME_scale.png from the red scale item by mapping each pixel's brightness onto a gradient."""
    src = Image.open(ITEMDIR + "red_dragon_scale.png").convert("RGBA")
    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    stops = v["item_gradient"]
    for yy in range(src.height):
        for xx in range(src.width):
            r, g, b, a = src.getpixel((xx, yy))
            if a == 0:
                continue
            lum = 0.3 * r + 0.59 * g + 0.11 * b
            lo, hi = stops[0], stops[-1]
            for s0, s1 in zip(stops, stops[1:]):
                if lum <= s1[0]:
                    lo, hi = s0, s1
                    break
            t = max(0.0, min(1.0, (lum - lo[0]) / (hi[0] - lo[0])))
            out.putpixel((xx, yy), tuple(int(lo[1][c] + (hi[1][c] - lo[1][c]) * t) for c in range(3)) + (a,))
    out.save(ITEMDIR + v["name"] + "_scale.png")


for variant in VARIANTS:
    paint_variant(variant)
    if "item_gradient" in variant:
        recolor_scale_item(variant)


# ---------------- Java ----------------
def fl(v):
    return f"{float(v)}F"


def var(name):
    return "p_" + name


def layer_method(model, method, doc):
    lines = []
    for name, parent, pivot, rot, boxes in model["parts"]:
        cubes = "CubeListBuilder.create()"
        for b in boxes:
            (x, y, z), (w, h, d) = b["o"], b["s"]
            cubes += f"\n                .texOffs({b['uv'][0]}, {b['uv'][1]}).addBox({fl(x)}, {fl(y)}, {fl(z)}, {fl(w)}, {fl(h)}, {fl(d)})"
        pose = f"PartPose.offsetAndRotation({', '.join(fl(v) for v in pivot)}, {', '.join(fl(v) for v in rot)})"
        lines.append(f'        PartDefinition {var(name)} = {var(parent)}.addOrReplaceChild("{name}", {cubes},\n                {pose});')
    return f"""{doc}    @SuppressWarnings("unused")
    public static LayerDefinition {method}() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition {var('root')} = mesh.getRoot();
{chr(10).join(lines)}
        return LayerDefinition.create(mesh, {model["tw"]}, {model["th"]});
    }}
"""


java = f"""// GENERATED by tools/make_dragon.py - edit that script instead of this file.
package com.wildcreatures.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** The winged dragons' shared body. */
public final class DragonLayer {{
    private DragonLayer() {{}}

{layer_method(MODELS["winged"], "create", "")}
{layer_method(MODELS["sea"], "createSea", "    /** The Sea Dragon: the same skeleton (so the same animations), with fins instead of spikes. */" + chr(10))}}}
"""
open(JAVA, "w").write(java)
print("wrote", JAVA, "and", ", ".join(v["name"] for v in VARIANTS), "textures")
