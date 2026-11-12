"""Кристальный голем: iron-golem-carried rock giant with amethyst growths (hitbox 1.4 x 2.7 blocks)."""
from lib import *

c = Creature("crystal_golem", id_base=1600, scale=1.0, res=2, hide_carrier=True, death_fx=False, hitbox=(1.4, 2.7))

SLATE, DEEP = (104, 112, 144), (66, 72, 98)
AME, AME_D, AME_L = (190, 80, 250), (130, 40, 210), (244, 170, 255)

rock = Mat(SLATE, mottle=DEEP, mottle_amt=0.7, mottle_size=4, shade=0.15, bevel=0.2,
           patterns=[plates((8, 6), 0.35, 0.12), speckle((130, 136, 168), 0.02), speckle((28, 28, 42), 0.03)])
rock_d = rock.with_(base=(76, 82, 110), mottle=(52, 56, 80))
rock_l = rock.with_(base=(130, 138, 170), mottle=(92, 100, 130))
crystal = Mat(AME, noise=0.06, mottle=AME_D, mottle_amt=0.7, mottle_size=3, shade=0.2, bevel=0.3,
              patterns=[stripes(AME_L, 1, 4, "v", 0.45), speckle((255, 220, 255), 0.02)])
crystal_l = crystal.with_(base=(206, 110, 250), mottle=AME)
glow = Mat((236, 130, 255), noise=0.05, shade=0, bevel=0, mottle=(255, 210, 255), mottle_amt=0.7, mottle_size=2)
eyes = Mat((255, 200, 255), noise=0, shade=0, bevel=0, mottle=(255, 255, 255), mottle_amt=0.5, mottle_size=1)

# ---------------------------------------------------------------- torso
body = c.part("body", pivot=(0, 14, 0), anim=[bob(0.5, 0.4), bob(0.5, 2.2, when="moving", wave="abs"),
                                              swing("y", 3, 1.1, when="moving"), strike("x", 10, 10)])
body.box((-6, 0, -4), (6, 4, 4), rock_d)                                   # hips
body.box((-8, 4, -5.5), (8, 18, 5), rock)                                  # chest
body.box((-10, 13, -5), (10, 19, 5), rock_l)                               # hunched shoulders
body.box((-6, 4, 5), (6, 12, 6), rock_d, skip=("back",))                   # chest plate for the core
body.box((-7, 6, -7), (7, 17, -5.5), rock_d)                               # back slab
for s in (1, -1):
    body.box((s * 5.5 - 1.5, 0, 5), (s * 5.5 + 1.5, 4, 6), crystal, rot=("z", 22.5))
body.box((-3, 15, 5), (3, 17, 6), rock_d)

core = c.part("core", pivot=(0, 8.5, 6), parent=body, bright=True, anim=[pulse(0.1, 0.6)])
core.box((-3.2, -3.6, 0), (3.2, 3.6, 1.2), glow)
core.box((-1, -4.5, 0), (1, 4.5, 1.2), glow, rot=("z", 22.5))

back = c.part("crystals_back", pivot=(0, 12, -7), parent=body, anim=[pulse(0.03, 0.4)])
back.box((-3.5, 0, -3.5), (3.5, 22, 3.5), crystal, rot=("x", -22.5))
back.box((-11, 0, -3), (-5, 15, 3), crystal_l, rot=("z", 22.5))
back.box((5, 0, -3), (11, 15, 3), crystal_l, rot=("z", -22.5))
back.box((-5, 2, -2.5), (-1, 14, 2.5), crystal, rot=("x", 45))
back.box((1, 2, -2.5), (5, 14, 2.5), crystal_l, rot=("x", -22.5))
back.box((-6, 0, -3), (6, 3, 3), rock_d)

# ---------------------------------------------------------------- head
head = c.part("head", pivot=(0, 19, 1), parent=body, anim=[swing("y", 4, 0.25, 0.3), swing("x", 2, 0.4)])
head.box((-4.5, 0, -4), (4.5, 8, 4.5), rock_l, skip=("bottom",))
head.box((-5, 5, 3), (5, 6.5, 5), rock_d)                                  # brow ridge
head.box((-2, 0, 4.5), (2, 3, 6), rock)                                    # nose / jaw
head.box((-2.5, 8, -3), (2.5, 17, 2), crystal, rot=("z", 22.5))          # crown shard
head.box((-5, 8, -3), (-2, 14, 1), crystal_l, rot=("x", 22.5))
eye = c.part("eyes", pivot=(0, 3.6, 4.6), parent=head, bright=True)
eye.box((-3.8, -1.2, 0), (-1, 1.2, 0.7), eyes)
eye.box((1, -1.2, 0), (3.8, 1.2, 0.7), eyes)

# ---------------------------------------------------------------- arms
def arm(side, ph):
    n = "l" if side > 0 else "r"
    a = c.part("arm_" + n, pivot=(side * 12.5, 17, 0), parent=body,
               anim=[swing("x", 22, 1.1, ph, "moving"), swing("x", 1.5, 0.4, ph * 0.5), strike("x", -88, 10)])
    a.box((-4, -3, -4), (4, 3, 4), rock_l)                                 # shoulder rock
    a.box((-2.5, -12, -2.5), (2.5, -3, 2.5), rock)
    s = side
    a.box((-2.5, 3, -2.5), (s * 0 + 2.5, 14, 2.5), crystal, rot=("z", -22.5 * s))
    a.box((s * 2 - 2, 2, 0), (s * 2 + 2, 10, 4), crystal_l, rot=("x", 22.5))
    a.box((s * -2.5 - 1.2, 2, -3.5), (s * -2.5 + 1.2, 7, -1), crystal, rot=("z", 45))
    f = c.part("forearm_" + n, pivot=(0, -12, 0), parent=a,
               anim=[swing("x", -10, 1.1, ph + 0.2, "moving", "pos"), strike("x", -25, 10)])
    f.box((-3.2, -11, -3.2), (3.2, 0, 3.2), rock)
    f.box((-4, -17, -4), (4, -11, 4), rock_d)                              # fist
    f.box((-2.2, -9, -7), (2.2, -1, -3), crystal, rot=("x", 22.5))        # forearm shard
    f.box((s * 3 - 1.6, -10, -2.5), (s * 3 + 1.6, -2, 2.5), crystal_l, rot=("z", 22.5 * s))
    f.box((-3.8, -17.5, -3.8), (3.8, -16, 3.8), rock_l)
    return a

arm(1, 0.5)
arm(-1, 0.0)

# ---------------------------------------------------------------- legs
for side, ph in ((1, 0.0), (-1, 0.5)):
    n = "l" if side > 0 else "r"
    leg = c.part("leg_" + n, pivot=(side * 4.5, 14, 0),
                 anim=[swing("x", -22, 1.1, ph, "moving"), swing("x", 1, 0.4, ph)])
    leg.box((-3.5, -9, -3.5), (3.5, 0, 3.5), rock)
    leg.box((-4, -14, -4), (4, -9, 4.5), rock_d)
    leg.box((-4.4, -14, -5), (4.4, -11, 5), rock_l)
