"""Кракен: sea boss carried by an invisible elder guardian (scale 3: hitbox 6.0 x 6.0 blocks, seen mostly from above)."""
import math
from lib import *

c = Creature("kraken", id_base=1400, scale=3.0, res=2, hide_carrier=True, death_fx=True, hitbox=(6.0, 6.0))

INDIGO, PURPLE, TEAL, PALE = (58, 40, 120), (104, 40, 128), (28, 110, 128), (232, 214, 176)

mantle = Mat(PURPLE, mottle=INDIGO, mottle_amt=0.7, mottle_size=6, shade=0.14, bevel=0.12,
             patterns=[spots(TEAL, 0.05, (2, 4), 0.55), spots((196, 170, 220), 0.025, (1, 2), 0.8),
                       speckle((24, 14, 60), 0.01)],
             faces={"bottom": Mat(PALE, mottle=(188, 150, 170), mottle_amt=0.5,
                                  patterns=[stripes((150, 110, 140), 1, 3, "h", 0.5)])})
cap = mantle.with_(base=(80, 52, 150), mottle=(40, 70, 130), add=[
    on("top", stripes((30, 20, 80), 2, 5, "h", 0.4, 0.4)), on("top", spots((236, 210, 160), 0.03, (1, 2), 0.85))])
fin = Mat(TEAL, mottle=INDIGO, mottle_amt=0.6, mottle_size=4, bevel=0.2,
          patterns=[stripes((20, 70, 90), 1, 3, "h", 0.5), spots(PALE, 0.04, (1, 2), 0.7)])
skin = Mat((150, 62, 170), mottle=(60, 110, 170), mottle_amt=0.5, mottle_size=3, bevel=0.2,
           patterns=[spots((236, 200, 250), 0.03, (1, 2), 0.8)],
           faces={"bottom": Mat(PALE, mottle=(214, 150, 170), mottle_amt=0.5, bevel=0.25,
                                patterns=[spots((200, 60, 110), 0.3, (2, 3), 0.95)])})
skin_tip = skin.with_(base=(120, 80, 200), mottle=(200, 70, 150))
beak_m = Mat((24, 14, 30), mottle=(70, 30, 50), mottle_size=2, bevel=0.3, patterns=[stripes((8, 4, 12), 1, 2, "v", 0.7)])
glow = Mat((210, 255, 60), noise=0.05, mottle=(150, 230, 40), mottle_size=2, shade=0, bevel=0.1,
           faces={f: Mat((200, 255, 70), noise=0, shade=0, bevel=0,
                         patterns=[eye((200, 255, 70), (12, 8, 24), iris=(150, 240, 40), slit=True)])
                  for f in ("front", "left", "right")})

# ---------------------------------------------------------------- mantle (stepped dome, tapering to the back)
body = c.part("body", pivot=(0, 15, -2), anim=[bob(0.5, 0.3), pulse(0.015, 0.45)])
body.box((-13, -8, 0), (13, 2, 14), mantle)                                  # head
body.box((-11, 2, 1), (11, 8, 13), cap)
body.box((-8, 8, 3), (8, 10, 11), cap, skip=("bottom",))
for z0, z1, hw, y0, y1 in ((-4, 0, 14, -8, 9), (-8, -4, 14.5, -8, 12), (-12, -8, 14, -8, 14),
                           (-16, -12, 12, -7, 13), (-19, -16, 9.5, -6, 11), (-22, -19, 6, -5, 7)):
    body.box((-hw, y0, z0), (hw, y1 - 3, z1), mantle)
    body.box((-hw + 2, y1 - 3, z0), (hw - 2, y1, z1), cap)
    body.box((-hw + 5, y1, z0 + 0.5), (hw - 5, y1 + 1.5, z1 - 0.5), cap, skip=("bottom",))
for z, y in ((-5, 11), (-10, 15.5), (-15, 14.5)):                              # warty ridge
    body.box((-1.5, y, z - 1.5), (1.5, y + 1.6, z + 1.5), skin_tip, skip=("bottom",))
for x in (-9, 9):
    body.box((x - 1.5, 10, -9), (x + 1.5, 11.5, -6), skin_tip, skip=("bottom",))

# beak under the front: opens slowly, shuts fast
beak = c.part("beak", pivot=(0, -6, 14), parent=body, rot=(15, 0, 0),
              anim=[swing("x", -14, 0.35, wave="snap"), strike("x", -20, 8)])
beak.box((-2.5, -1.5, 0), (2.5, 1.5, 5), beak_m)
beak.box((-1.5, -2.5, 3), (1.5, -0.5, 6.5), beak_m)

# ---------------------------------------------------------------- eyes and rear fins
for s, side in ((1, "l"), (-1, "r")):
    e = c.part("eye_" + side, pivot=(s * 12.5, 3, 8), parent=body, bright=True, rot=(0, s * 25, 0))
    e.box((-3, -4, -4), (3, 4, 4), glow)
    f = c.part("fin_" + side, pivot=(s * 12, 2, -10), parent=body, rot=(0, s * 30, 0),
               anim=[swing("y", s * 7, 0.4, 0.2)])
    f.box((min(0, s * 10), -1, -8), (max(0, s * 10), 1, 4), fin)
    f.box((min(s * 3, s * 8), -1.5, -5), (max(s * 3, s * 8), 1.5, 1), cap)

# ---------------------------------------------------------------- 8 tentacles x 3 segments, fanned like a star
YAWS = (-80, -57, -34, -11, 11, 34, 57, 80)        # index 3, 4 are the front pair
SEG = ((10, 4.5, 3.5), (9, 3.5, 2.5), (8, 2.5, 0.9))   # length, base half-width, tip half-width
for i, yaw in enumerate(YAWS):
    sgn = 1 if i % 2 == 0 else -1                   # neighbours sway in opposite phase
    a = math.radians(yaw)
    pos = (10 * math.sin(a), -5.5, 13 + 3 * math.cos(a))
    front = i in (3, 4)
    parent = body
    for k, (ln, w0, w1) in enumerate(SEG):
        anim = [sway("y", sgn * (7 + 3 * k), 0.35, k), sway("x", 3, 0.28, k)]
        if front:
            anim.append(strike("y", (1 if yaw < 0 else -1) * 14, 8))
            anim.append(strike("x", -16, 8))
        if k == 0:
            seg = c.part("tent%d_0" % i, pivot=pos, parent=body, rot=(4, yaw, 0), anim=anim)
        else:
            seg = c.part("tent%d_%d" % (i, k), pivot=(0, 0, SEG[k - 1][0]), parent=parent,
                         rot=(-4, sgn * 9, 0), anim=anim)
        m = skin if k < 2 else skin_tip
        seg.box((-w0, -w0 * 0.8, 0), (w0, w0 * 0.8, ln * 0.55), m)
        seg.box((-(w0 + w1) / 2, -(w0 + w1) * 0.4, ln * 0.55), ((w0 + w1) / 2, (w0 + w1) * 0.4, ln + 0.5), m)
        parent = seg
