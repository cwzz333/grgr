"""НЛО: flying saucer carried by an invisible ghast (hitbox 4 x 4 blocks, floats in the air)."""
import math
from lib import *

c = Creature("ufo", id_base=1500, scale=2.5, res=2, hide_carrier=True, death_fx=True, hitbox=(4.0, 4.0))

METAL, DARKM = (176, 184, 198), (62, 70, 86)
hull_m = Mat(METAL, noise=0.05, mottle=(150, 158, 174), mottle_amt=0.5, mottle_size=4, shade=0.14,
             patterns=[plates((8, 5), 0.3, 0.2), rivets((236, 240, 248), 7, 2), speckle((120, 128, 144), 0.02)])
top_m = hull_m.with_(base=(194, 202, 216), patterns=[plates((9, 9), 0.3, 0.25), rivets((240, 244, 250), 8, 2)])
mid_m = hull_m.with_(base=(138, 148, 166), patterns=[stripes((90, 98, 116), 1, 5, "v", 0.7), rivets((210, 216, 228), 6, 2)])
under_m = Mat(DARKM, mottle=(44, 50, 64), mottle_amt=0.6, mottle_size=3,
              patterns=[plates((7, 7), 0.45, 0.1), rivets((120, 130, 150), 7, 2)])
leg_m = Mat((96, 104, 120), mottle=(60, 66, 80), mottle_size=2, bevel=0.3)
glass = Mat((150, 232, 238), noise=0.03, mottle=(190, 250, 252), mottle_amt=0.6, mottle_size=3, shade=0.1,
            patterns=[stripes((235, 255, 255), 1, 6, "d", 0.55), spots((235, 255, 255), 0.03, (1, 2), 0.8)])
rim_m = Mat((120, 128, 146), mottle=(84, 92, 110), mottle_size=2, bevel=0.3)
lamp_a = Mat((255, 150, 40), noise=0.04, shade=0, bevel=0.2, mottle=(255, 220, 90), mottle_size=2)
lamp_b = Mat((255, 60, 190), noise=0.04, shade=0, bevel=0.2, mottle=(255, 170, 240), mottle_size=2)
beam = Mat((90, 255, 200), noise=0.05, shade=0, bevel=0.15, mottle=(190, 255, 255), mottle_size=2,
           patterns=[stripes((40, 220, 190), 1, 3, "h", 0.5)])


def disc(part, r, y0, y1, mat, eps=0.0, **kw):
    """Round-ish disc from four slabs (0, 90, 45, -45 degrees)."""
    w = r * 0.415
    part.box((-r, y0, -w), (r, y1, w), mat, **kw)
    part.box((-w, y0, -r), (w, y1 - eps, r), mat, **kw)
    part.box((-r, y0, -w), (r, y1 - 2 * eps, w), mat, rot=("y", 45), **kw)
    part.box((-r, y0, -w), (r, y1 - 3 * eps, w), mat, rot=("y", -45), **kw)


def tangent_box(part, k, apothem, length, thick, y0, y1, mat):
    phi = 45 * k
    m = phi % 180
    if m == 90:
        ext = (thick / 2, length / 2)
        rot = None
    else:
        ext = (length / 2, thick / 2)
        rot = None if m == 0 else ("y", 45 if m == 45 else -45)
    cx, cz = apothem * math.sin(math.radians(phi)), apothem * math.cos(math.radians(phi))
    part.box((cx - ext[0], y0, cz - ext[1]), (cx + ext[0], y1, cz + ext[1]), mat, rot=rot)


# ---------------------------------------------------------------- hull (root)
hull = c.part("hull", pivot=(0, 11, 0), anim=[bob(0.8, 0.35), bob(0.5, 0.8, phase=0.25)])
disc(hull, 7, -8.5, -6.5, under_m, skip=("top",))
disc(hull, 12, -6.5, -4, under_m, 0.02, skip=("top",))
disc(hull, 17, -4, -1.5, mid_m, 0.02)
disc(hull, 18.5, -1.5, 1, hull_m, 0.02)
disc(hull, 16, 1, 3, top_m, 0.02, skip=("bottom",))
disc(hull, 13, 3, 5, top_m, 0.02, skip=("bottom",))
disc(hull, 10.5, 5, 6.5, mid_m, 0.02, skip=("bottom",))
for ang in (90, 210, 330):                                       # landing nubs
    x, z = 10 * math.sin(math.radians(ang)), 10 * math.cos(math.radians(ang))
    hull.box((x - 1.5, -10.5, z - 1.5), (x + 1.5, -6.5, z + 1.5), leg_m)
    hull.box((x - 2.5, -11, z - 2.5), (x + 2.5, -10, z + 2.5), leg_m)

# ---------------------------------------------------------------- glass dome
dome = c.part("dome", pivot=(0, 6, 0), parent=hull)
disc(dome, 9, 0, 2.5, glass, 0.02, skip=("bottom",))
disc(dome, 7.8, 2.5, 5, glass, 0.02, skip=("bottom",))
disc(dome, 6, 5, 7.5, glass, 0.02, skip=("bottom",))
disc(dome, 3.8, 7.5, 9.5, glass, 0.02, skip=("bottom",))
dome.box((-0.5, 9.5, -0.5), (0.5, 13, 0.5), rim_m)                 # antenna
dome.box((-1.2, 13, -1.2), (1.2, 15.4, 1.2), lamp_b)

# ---------------------------------------------------------------- spinning light ring
ring = c.part("ring", pivot=(0, 0, 0), parent=hull, bright=True, anim=[spin("y", 0.25)])
for k in range(8):
    tangent_box(ring, k, 19.6, 16.6, 1.6, -1.2, 1.2, rim_m.with_(base=(210, 220, 235)))
    tangent_box(ring, k, 20.4, 5.0, 2.4, -1.8, 1.8, lamp_a if k % 2 == 0 else lamp_b)

# ---------------------------------------------------------------- beam emitter
emit = c.part("emitter", pivot=(0, -8.5, 0), parent=hull, bright=True,
              anim=[pulse(0.12, 1.2), bob(0.6, 1.2), strike("x", 25)])
disc(emit, 6.5, -2.5, 0, beam, 0.02, skip=("top",))
disc(emit, 4, -4.5, -2.5, beam.with_(base=(200, 255, 255)), 0.02)
