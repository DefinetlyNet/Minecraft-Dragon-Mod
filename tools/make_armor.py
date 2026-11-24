"""Makes the Dragon Scale armor textures and icons by recolouring vanilla netherite armor
to dragon red (same shapes, new colours), and paints the helmet's two small horns.

Run from the project root:
    python3 tools/make_armor.py <path to the Minecraft 1.20.1 client jar>
(ForgeGradle keeps one at ~/.gradle/caches/forge_gradle/mcp_repo/.../downloadClient/client.jar)
The horn boxes themselves are in client/DragonArmorLayers.java.
"""
import sys
import zipfile
from io import BytesIO
from PIL import Image

jar = zipfile.ZipFile(sys.argv[1])
OUT = "src/main/resources/assets/wildcreatures/textures"

# netherite brightness -> dragon colour; the very brightest edges become a warm gold trim
RAMP = [(10, (22, 4, 4)), (17, (34, 6, 6)), (33, (64, 10, 10)), (42, (94, 16, 14)), (53, (128, 22, 18)),
        (62, (152, 28, 20)), (72, (176, 36, 24)), (88, (192, 46, 28)), (110, (226, 132, 52)), (200, (255, 210, 120))]


def lum(r, g, b):
    return 0.299 * r + 0.587 * g + 0.114 * b


def dragon(c):
    r, g, b, a = c
    if a == 0:
        return c
    L = lum(r, g, b)
    for (l0, c0), (l1, c1) in zip(RAMP, RAMP[1:]):
        if L <= l1:
            t = max(0.0, (L - l0) / (l1 - l0))
            return tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3)) + (a,)
    return RAMP[-1][1] + (a,)


def recolour(path):
    im = Image.open(BytesIO(jar.read(path))).convert("RGBA")
    px = im.load()
    for x in range(im.width):
        for y in range(im.height):
            px[x, y] = dragon(px[x, y])
    return im


V = "assets/minecraft/textures/"
layer1 = recolour(V + "models/armor/netherite_layer_1.png")
# horns: painted into the unused hat area (x 32..64, y 0..16); UVs match DragonArmorLayers
px = layer1.load()
for x in range(32, 64):
    for y in range(0, 16):
        px[x, y] = (0, 0, 0, 0)
BONE, BONE_D, TIP = (226, 206, 166), (176, 152, 116), (70, 52, 40)
for u in (32, 40):          # horn bases, 7x5 footprint each
    for x in range(u, u + 7):
        for y in range(0, 5):
            px[x, y] = (BONE if y < 3 else BONE_D) + (255,)
for u in (48, 54):          # horn tips, 5x4 footprint each
    for x in range(u, u + 5):
        for y in range(0, 4):
            px[x, y] = (TIP if y < 1 or x == u else (110, 86, 64)) + (255,)
layer1.save(f"{OUT}/models/armor/dragon_scale_layer_1.png")
recolour(V + "models/armor/netherite_layer_2.png").save(f"{OUT}/models/armor/dragon_scale_layer_2.png")

for piece in ("helmet", "chestplate", "leggings", "boots"):
    icon = recolour(V + f"item/netherite_{piece}.png")
    if piece == "helmet":
        p = icon.load()
        # find the helmet's top row and add a small horn above each upper corner
        top = min(y for y in range(16) for x in range(16) if p[x, y][3])
        cols = [x for x in range(16) if p[x, top][3]]
        left, right = min(cols), max(cols)
        icon2 = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        shift = 2 if top < 2 else 0
        icon2.paste(icon, (0, shift))
        p = icon2.load()
        t = top + shift
        for (x, y, c) in [(left, t - 1, BONE), (left - 1, t - 2, BONE), (left - 1, t - 3, TIP),
                          (right, t - 1, BONE), (right + 1, t - 2, BONE), (right + 1, t - 3, TIP)]:
            if 0 <= x < 16 and 0 <= y < 16:
                p[x, y] = c + (255,)
        icon = icon2
    icon.save(f"{OUT}/item/dragon_scale_{piece}.png")
print("done")
