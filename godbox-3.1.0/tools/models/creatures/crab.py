"""Крабзилла: the ravager-carried giant crab boss (ravager at scale 3: hitbox 5.85 x 6.6 blocks)."""
from lib import *

c = Creature("crab", id_base=1000, scale=3.0, res=3, hide_carrier=True, death_fx=True, hitbox=(5.85, 6.6))

RED, DARK, ORANGE, CREAM = (198, 50, 32), (132, 24, 28), (238, 124, 48), (230, 204, 158)
GROOVE = (120, 22, 26)
SIDES = ("front", "back", "left", "right")

shell = Mat(RED, mottle=DARK, mottle_amt=0.5, mottle_size=6, shade=0.14,
            patterns=[spots(ORANGE, 0.02, (1, 2), 0.85), speckle((255, 176, 96), 0.008), speckle((96, 16, 24), 0.02)])
# the dome gets the H-shaped groove real crabs have on their back
dome = shell.with_(base=(212, 70, 36), mottle_amt=0.4, add=[
    on("top", rect(0.27, 0.12, 0.31, 0.88, GROOVE, 0.6)), on("top", rect(0.69, 0.12, 0.73, 0.88, GROOVE, 0.6)),
    on("top", rect(0.27, 0.47, 0.73, 0.52, GROOVE, 0.6)), on("top", spots((250, 160, 70), 0.03, (1, 2)))])
under = Mat(CREAM, mottle=(196, 160, 112), mottle_amt=0.5, patterns=[stripes((168, 128, 92), 1, 3, "h", 0.55)])
rim = shell.with_(base=(170, 36, 28), faces={"bottom": under},
                  add=[on(SIDES, band((236, 150, 84), "bottom", 0.18, 0.75))])
limb = Mat((184, 44, 30), mottle=DARK, mottle_amt=0.55, mottle_size=3, bevel=0.22,
           patterns=[speckle(ORANGE, 0.05)], faces={"bottom": under.with_(patterns=[])})
joint = Mat((214, 84, 40), mottle=RED, mottle_size=2, bevel=0.25)
tip = Mat((58, 22, 28), mottle=(110, 30, 30), mottle_size=2, bevel=0.3)
tooth = Mat((240, 226, 190), noise=0.04, patterns=[stripes((150, 110, 90), 1, 1, "v", 0.8)], shade=0.05)
spike = Mat((246, 150, 60), mottle=(200, 70, 36), mottle_size=2, bevel=0.3)
barnacle = Mat((186, 188, 172), mottle=(120, 128, 116), mottle_size=2, bevel=0.3,
               faces={"top": Mat((170, 172, 158), patterns=[rect(0.25, 0.25, 0.75, 0.75, (52, 54, 50))])})
mouth = Mat((70, 20, 26), mottle=(30, 10, 14), mottle_size=2, patterns=[stripes((20, 6, 10), 1, 2, "v", 0.7)])
maxilla = Mat((226, 146, 92), mottle=(188, 98, 60), mottle_size=2, patterns=[stripes((140, 64, 48), 1, 2, "h", 0.6)])
glow = Mat((255, 214, 64), noise=0.05, mottle=(255, 150, 30), mottle_size=2, shade=0, bevel=0.1,
           faces={f: Mat((255, 224, 90), noise=0, shade=0, bevel=0,
                          patterns=[eye((255, 224, 90), (20, 8, 8), iris=(255, 140, 20), slit=True)])
                  for f in ("front", "left", "right")})

# ---------------------------------------------------------------- carapace
body = c.part("body", pivot=(0, 16, 0), anim=[bob(0.4, 0.35), bob(0.6, 2.8, when="moving", wave="abs")])
body.box((-13, -6, -9), (13, -3, 9), under)                      # belly
for hw, z0, z1 in ((12, 10, 13), (16, 6, 10), (18, 0, 6), (16, -5, 0), (13, -9, -5), (9, -12, -9)):
    body.box((-hw, -3, z0), (hw, 3, z1), rim)                    # stepped, roughly hexagonal outline
body.box((-10, 3, 9), (10, 5, 11.5), shell, skip=("bottom",))
body.box((-15, 3, -1), (15, 6, 9), shell, skip=("bottom",))
body.box((-11.5, 3, -9), (11.5, 6, -1), shell, skip=("bottom",))
body.box((-11, 6, -6), (11, 8, 7.5), dome, skip=("bottom",))
body.box((-7, 8, -3), (7, 9.5, 5.5), dome, skip=("bottom",))
# side spines
for s in (1, -1):
    body.box((s * 17, -1, 3.5), (s * 21, 1, 7.5), spike, rot=("y", 45))
    body.box((s * 15.5, -1, -3.5), (s * 18.5, 1, -0.5), spike, rot=("y", 45))
    body.box((s * 12.2, -1, -8), (s * 14.8, 1, -5.4), spike, rot=("y", 45))
# teeth along the front edge
for x in (-10, -7, 7, 10):
    body.box((x - 1.1, 0.6, 12.1), (x + 1.1, 2.8, 14.3), spike, rot=("y", 45))
# bumps on the dome
for x, y, z, w in ((-9, 8, 5, 2), (9, 8, 5, 2), (0, 9.5, 3, 3), (-8, 6, -7.5, 2), (8, 6, -7.5, 2),
                   (-13, 6, 6, 2), (13, 6, 6, 2), (-4, 9.5, -1, 2), (4, 9.5, -1, 2), (0, 8, -4.5, 2)):
    body.box((x - w / 2, y, z - w / 2), (x + w / 2, y + 1, z + w / 2), spike, skip=("bottom",))
# barnacles
for x, y, z, w, hgt in ((-13, 6, 1, 2, 1.5), (-13.5, 6, -1, 1.5, 1), (6, 8, -4.5, 2, 1.5), (10, 6, -7, 1.5, 1)):
    body.box((x - w / 2, y, z - w / 2), (x + w / 2, y + hgt, z + w / 2), barnacle, skip=("bottom",))
# mouth with two maxilliped plates
body.box((-5, -3.5, 12.6), (5, 1.5, 13.4), mouth, skip=("back",))
for s in (1, -1):
    body.box((s * 0.3, -4.6, 13.2), (s * 3.3, -0.6, 14.2), maxilla)

# ---------------------------------------------------------------- eyes on stalks
for s, side in ((1, "l"), (-1, "r")):
    stalk = c.part("stalk_" + side, pivot=(s * 5.5, 4.5, 10.3), parent=body, rot=(-8, 0, -s * 12),
                   anim=[swing("z", 5, 0.33, 0.25 * (s > 0)), swing("x", 5, 0.21, 0.5 * (s > 0))])
    stalk.box((-1, 0, -1), (1, 6.5, 1), limb)
    eye_ = c.part("eye_" + side, pivot=(0, 6.5, 0), parent=stalk, bright=True)
    eye_.box((-1.75, 0, -1.75), (1.75, 3.5, 1.75), glow)

# ---------------------------------------------------------------- claws: left = big crusher, right = smaller cutter
for s, side, size, ph in ((1, "l", 1.3, 0.0), (-1, "r", 0.95, 0.45)):
    arm = c.part("arm_" + side, pivot=(s * 12.5, -1, 9.5), parent=body, rot=(-10, s * 42, 0), scale=size,
                 anim=[swing("y", s * 5, 0.3, ph), strike("x", -22, 12)])
    arm.box((-2.5, -2.5, 0), (2.5, 2.5, 10), limb)
    arm.box((-3, -3, 8.5), (3, 3, 12), joint)
    pincer = c.part("pincer_" + side, pivot=(0, 0, 11), parent=arm, rot=(12, -s * 56, 0),
                    anim=[strike("y", -s * 20, 12)])
    pincer.box((-3.5, -4.5, 0), (3.5, 4.5, 9), shell.with_(faces={"bottom": under}))        # palm
    for z in (1.5, 4.5, 7.5):                                                               # knuckle spikes
        pincer.box((-1, 4.5, z - 1), (1, 5.5, z + 1), spike, skip=("bottom",))
    pincer.box((-2.5, -4.5, 9), (2.5, -1.5, 16), limb)                                      # fixed finger
    pincer.box((-1.5, -4.5, 16), (1.5, -2.2, 19.5), tip)
    pincer.box((-1, -1.5, 9.5), (1, -0.6, 15.5), tooth, skip=("bottom",))
    finger = c.part("finger_" + side, pivot=(0, 2, 8.5), parent=pincer,
                    anim=[swing("x", -30, 0.4, ph, wave="snap"), strike("x", -40, 9)])
    finger.box((-2.5, -1.2, 0), (2.5, 2.5, 8), limb)
    finger.box((-1.5, -1.2, 8), (1.5, 1.4, 11.5), tip)
    finger.box((-1, -2.1, 1.5), (1, -1.2, 7.5), tooth, skip=("top",))

# ---------------------------------------------------------------- 8 walking legs, two segments each
LEG_Z = (7.5, 2.5, -2.5, -7.5)
LEG_YAW = (-36, -12, 12, 36)       # fan: front legs lean forward, back legs backward (left side values)
for s, side in ((1, "l"), (-1, "r")):
    for i in range(4):
        rot = (0, LEG_YAW[i], 30)
        ph = (i % 2) * 0.5 + (0.5 if s < 0 else 0.0)
        upper = c.part("leg_%s%d" % (side, i), pivot=(s * 14, 14, LEG_Z[i]), rot=rot if s > 0 else mirror_rot(rot),
                       anim=[swing("y", 15, 1.4, ph, when="moving"),
                             swing("z", s * 11, 1.4, ph + 0.25, when="moving", wave="pos")])
        upper.box((0, -1.5, -1.5), (s * 10, 1.5, 1.5), limb)
        upper.box((s * 8.8, -1.9, -1.9), (s * 11.2, 1.9, 1.9), joint)
        lower = c.part("shin_%s%d" % (side, i), pivot=(s * 10, 0, 0), parent=upper, rot=(0, 0, -s * 105),
                       anim=[swing("z", -s * 9, 1.4, ph + 0.25, when="moving", wave="pos")])
        lower.box((0, -1.3, -1.3), (s * 12, 1.3, 1.3), limb)
        lower.box((s * 12, -0.9, -0.9), (s * 20, 0.9, 0.9), tip)
