"""Огненный элементаль: a floating fire spirit carried by a blaze (hitbox 0.6 x 1.8 blocks, model ~2.1 tall)."""
from lib import *

c = Creature("fire_elemental", id_base=1700, scale=1.0, res=2, hide_carrier=True, death_fx=False, hitbox=(0.6, 1.8))

CRACK, HOT = (255, 130, 24), (255, 226, 80)

magma = Mat((104, 38, 24), mottle=(60, 22, 18), mottle_amt=0.6, mottle_size=3, bevel=0.2, shade=0.14,
            patterns=[stripes(CRACK, 1, 3, "d", 0.95, 3), stripes((255, 190, 50), 1, 7, "v", 0.9, 2),
                      speckle(HOT, 0.03)])
plate = magma.with_(base=(80, 32, 24), patterns=[stripes(CRACK, 1, 5, "d", 0.9, 3), speckle(HOT, 0.04)])
skull = magma.with_(base=(62, 28, 24), faces={"front": magma.with_(add=[rect(0.2, 0.62, 0.8, 0.78, (30, 12, 12))])})
glow = Mat((255, 150, 30), noise=0.05, mottle=HOT, mottle_amt=0.7, mottle_size=3, shade=0, bevel=0.1,
           patterns=[gradient((255, 236, 100), (230, 70, 12), 0.55), speckle((255, 250, 190), 0.03)])
flame = glow.with_(patterns=[gradient((255, 245, 150), (240, 80, 10), 0.8), stripes((255, 200, 60), 1, 4, "v", 0.4, 2)])
fistm = Mat((255, 120, 24), noise=0.06, mottle=(90, 30, 18), mottle_amt=0.75, mottle_size=3, shade=0, bevel=0.2,
            patterns=[stripes((255, 214, 70), 1, 7, "d", 0.8, 3), speckle((255, 240, 160), 0.04)])
corem = Mat((255, 246, 170), noise=0.03, mottle=(255, 200, 60), mottle_amt=0.6, mottle_size=2, shade=0, bevel=0.15)
eyem = Mat((255, 255, 224), noise=0, shade=0, bevel=0)

# ---------------------------------------------------------------- body: tapering molten shell
body = c.part("body", pivot=(0, 19, 0), anim=[bob(1.2, 0.5), swing("z", 1.5, 0.35), strike("x", 12, 10)])
body.box((-5, -4, -4), (5, 0, 4), plate)                           # waist
body.box((-7, 0, -5), (7, 7, 5), magma)                                # chest
body.box((-9, 4.5, -4.5), (9, 7.5, 4.5), plate)  # shoulder bar
body.box((-3.5, 7.5, -3), (3.5, 8.5, 3), plate)                            # collar
body.box((-6.6, -0.5, 4.5), (-3.4, 6.5, 5.3), plate)                       # chest ribs framing the core
body.box((3.4, -0.5, 4.5), (6.6, 6.5, 5.3), plate)
body.box((-3.4, 5.4, 4.5), (3.4, 6.5, 5.3), plate)
body.box((-3.4, -0.5, 4.5), (3.4, 0.6, 5.3), plate)
for s in (1, -1):                                                          # shoulder horns
    body.box((s * 6.5, 7.5, -1.5), (s * 9.5, 10.5, 1.5), plate, rot=("z", 22.5))

core = c.part("core", pivot=(0, 3, 4.6), parent=body, bright=True, anim=[pulse(0.12, 0.8)])
core.box((-2.4, -2.4, -0.4), (2.4, 2.4, 1.0), corem)

# ---------------------------------------------------------------- head with flame crest
head = c.part("head", pivot=(0, 8.5, 0), parent=body, anim=[swing("y", 4, 0.3), swing("x", 2, 0.45, 0.2)])
head.box((-3.8, 0, -3.5), (3.8, 6.5, 3.5), skull)
head.box((-4.2, 4.6, -3.9), (4.2, 5.4, 3.9), plate)                        # brow band
head.box((-2.6, -1, -2.4), (2.6, 0, 2.4), plate)

crest = c.part("crest", pivot=(0, 0, 0), parent=head, bright=True, anim=[sway("x", 4, 0.9, index=0)])
for x, z, y1 in ((0, 0, 11), (-2.6, 0.5, 8.5), (2.6, 0.5, 8.5), (0, -2.6, 9), (0, 2.4, 8)):
    crest.box((x - 1.3, 6.3, z - 1.3), (x + 1.3, y1, z + 1.3), flame)
crest.box((-0.8, 11, -0.8), (0.8, 13, 0.8), flame.with_(base=(255, 240, 140)))
crest.box((-0.6, 8.5, 2.4), (0.6, 10.5, 3.6), flame)
for s in (1, -1):
    crest.box((s * 0.9, 3.4, 3.4), (s * 3.2, 5.2, 4.1), eyem)               # eyes
    crest.box((s * 2.2, 5.2, 3.4), (s * 3.4, 5.7, 4.0), eyem)

# ---------------------------------------------------------------- flame tail
tail = c.part("tail", pivot=(0, -4, 0), parent=body, bright=True, anim=[sway("z", 6, 0.7, index=0), sway("x", 4, 0.5, index=0)])
tail.box((-4.4, -6, -3.8), (4.4, 0, 3.8), flame)
tail.box((-3, -9, -2.6), (3, -6, 2.6), flame)
tail2 = c.part("tail2", pivot=(0, -6, 0), parent=tail, bright=True, anim=[sway("z", 10, 0.7, index=1), sway("x", 6, 0.5, index=1)])
tail2.box((-1.8, -6, -1.6), (1.8, 0, 1.6), flame.with_(base=(255, 170, 40)))
tail2.box((-0.9, -10, -0.9), (0.9, -6, 0.9), flame.with_(base=(255, 220, 90)))

# ---------------------------------------------------------------- floating magma fists
for s, side in ((1, "l"), (-1, "r")):
    ph = 0.0 if s > 0 else 0.5
    arm = c.part("arm_" + side, pivot=(s * 12.5, 2, 3), parent=body, bright=True, rot=(-18, s * 10, 0),
                 anim=[bob(1.6, 0.6, ph), swing("x", 10, 0.45, ph), swing("z", s * 4, 0.3, ph), strike("x", -85, 10)])
    arm.box((-3.2, -2, -3.2), (3.2, 4.5, 3.2), fistm)                       # fist
    arm.box((-2.2, 4.5, -2.2), (2.2, 9, 2.2), flame)                        # forearm wisp
    arm.box((-1.2, 9, -1.2), (1.2, 12, 1.2), flame.with_(base=(255, 220, 90)))
    arm.box((-3.8, -3.2, -3.8), (3.8, -2, 3.8), plate)                      # dark knuckle band

# ---------------------------------------------------------------- orbiting flame shards
ring = c.part("shards", pivot=(0, 1, 0), parent=body, bright=True, anim=[spin("y", 0.4), bob(1.0, 0.6, 0.3)])
for i, (x, z, y) in enumerate(((14, 0, 0), (0, 14, 5), (-14, 0, 0), (0, -14, 5))):
    ring.box((x - 2.4, y - 3.5, z - 2.4), (x + 2.4, y + 4.5, z + 2.4), flame, rot=("y", 45))
    ring.box((x - 1.4, y + 4.5, z - 1.4), (x + 1.4, y + 8, z + 1.4), flame.with_(base=(255, 235, 120)), rot=("y", 45))
