"""Титан: a colossal mossy stone titan carried by a normal-size Warden (hitbox 0.9 x 2.9 blocks, model ~3.6 tall)."""
from lib import *

c = Creature("titan", id_base=1200, scale=1.25, res=2, hide_carrier=True, death_fx=True, hitbox=(0.9, 2.9))

STONE, DARK, MOSS, TEAL = (150, 154, 158), (84, 88, 96), (84, 160, 56), (70, 255, 226)
SIDES = ("front", "back", "left", "right")

stone = Mat(STONE, mottle=DARK, mottle_amt=0.4, mottle_size=4, bevel=0.2, shade=0.16,
            patterns=[stripes((50, 54, 60), 1, 9, "d", 0.5, 2), speckle((196, 200, 204), 0.02), speckle((56, 58, 64), 0.03)])
moss = Mat(MOSS, mottle=(46, 110, 40), mottle_amt=0.7, mottle_size=3, bevel=0.1, shade=0.1,
           patterns=[spots((150, 214, 80), 0.05, (1, 2), 0.8), speckle((34, 80, 34), 0.05)])
mossy = stone.with_(faces={"top": moss}, add=[on(SIDES, band(MOSS, "top", 0.25, 0.8))])
dark = Mat((100, 104, 112), mottle=(58, 62, 70), mottle_amt=0.6, mottle_size=3, bevel=0.22,
           patterns=[stripes((36, 38, 44), 1, 6, "d", 0.55, 2), speckle((150, 154, 160), 0.02)])
horn = Mat((222, 212, 176), mottle=(156, 144, 112), mottle_amt=0.6, mottle_size=3, bevel=0.2,
           patterns=[stripes((112, 100, 80), 1, 3, "h", 0.5)])
fist = dark.with_(base=(116, 120, 128), faces={"top": moss})
rune = Mat(TEAL, noise=0.04, mottle=(190, 255, 240), mottle_amt=0.5, mottle_size=2, shade=0, bevel=0.05)
core = Mat((120, 255, 236), noise=0.03, mottle=(255, 255, 255), mottle_amt=0.55, mottle_size=3, shade=0, bevel=0.2)
eyem = Mat((170, 255, 245), noise=0, shade=0, bevel=0)

# ---------------------------------------------------------------- torso (origin = hip height, 20 px)
torso = c.part("torso", pivot=(0, 20, 0),
               anim=[bob(0.5, 0.3), bob(0.9, 1.2, when="moving", wave="abs"), pulse(0.012, 0.3),
                     swing("z", 1.5, 0.6, when="moving"), strike("x", 14, 14)])
torso.box((-8, -3, -6), (8, 3, 6), dark)                                    # pelvis
torso.box((-6, 3, -5), (6, 8, 5), stone)                                    # waist
torso.box((-9.5, 8, -7), (9.5, 14.5, 8), mossy)                             # chest
torso.box((-7, 8.5, 8), (7, 13.5, 9.5), dark)                               # chest plate
torso.box((-8, 7, -10), (8, 14, -7), mossy)                                 # back hump
torso.box((-5, 14, -9), (5, 16, -4), moss, skip=("bottom",))
torso.box((-8, 14.5, -2), (-3, 15.8, 6), moss, skip=("bottom",))
torso.box((3, 14.5, -2), (8, 15.8, 6), moss, skip=("bottom",))
torso.box((-2.5, 2, 5), (2.5, 6, 6.5), dark)                                # belt buckle
torso.box((-8.5, -3, -6), (8.5, -1, 6.5), mossy.with_(faces={"top": stone}))
for x, z in ((-6, 4), (6, 4), (0, -8)):                                     # broken stone chunks
    torso.box((x - 2, 14.5, z - 2), (x + 2, 17, z + 2), stone, rot=("y", 22.5))

runes = c.part("runes", pivot=(0, 0, 0), parent=torso, bright=True, anim=[pulse(0.01, 0.5, 0.25)])
for x in (-5.5, 5.5):                                                       # chest frame
    runes.box((x - 0.4, 9, 9.4), (x + 0.4, 13, 9.8), rune)
runes.box((-5.5, 8.8, 9.4), (5.5, 9.6, 9.8), rune)
runes.box((-5.5, 12.4, 9.4), (-3, 13.2, 9.8), rune)
runes.box((3, 12.4, 9.4), (5.5, 13.2, 9.8), rune)
for x in (-4.5, 0, 4.5):                                                    # waist lines
    runes.box((x - 0.4, 3.8, 5.1), (x + 0.4, 7.2, 5.5), rune)
runes.box((-4.5, 5.2, 5.1), (4.5, 6, 5.5), rune)
for x in (-9.8, 9.8):                                                       # side lines
    runes.box((x - 0.2, 9, -4), (x + 0.2, 13.5, 5), rune)
runes.box((-0.5, 7.5, -10.2), (0.5, 14, -10.0), rune)                       # spine
runes.box((-4, 11, -10.2), (4, 11.8, -10.0), rune)
runes.box((-4, 8.8, -10.2), (4, 9.5, -10.0), rune)
runes.box((-7.5, 2.2, -6.2), (7.5, 2.9, -6.0), rune)
heart = c.part("core", pivot=(0, 11, 9.4), parent=torso, bright=True, anim=[pulse(0.16, 0.6)])
heart.box((-2.2, -2.2, -0.2), (2.2, 2.2, 1.6), core)

# ---------------------------------------------------------------- head with two big horns
head = c.part("head", pivot=(0, 14, 2.5), parent=torso, anim=[swing("x", 1.5, 0.3, 0.2), swing("y", 2, 0.17)])
head.box((-3, -2, -3), (3, 1, 3), dark)                                     # neck
head.box((-6.2, 0, -5.5), (6.2, 10, 5.5), mossy)                                 # skull
head.box((-6.8, 5, 5), (6.8, 7.4, 6.8), dark)                               # brow ridge
head.box((-4.6, -0.5, 5.5), (4.6, 2.8, 6.8), stone)                               # jaw
for x in (-2.8, 2.8):
    head.box((x - 0.8, 2.5, 5.2), (x + 0.8, 4.2, 6.6), horn)                # tusks
head.box((-4.5, 10, -3), (3.5, 11, 3.5), moss, skip=("bottom",))
for s in (1, -1):
    head.box((s * 5.5, 4.5, -2), (s * 8, 8, 3), horn)                       # horn base
    head.box((s * 7.8, 6.5, -1.7), (s * 10.5, 9.5, 2.7), horn)
    head.box((s * 8.8, 9.2, -1.2), (s * 11.4, 12.2, 2.2), horn)
    head.box((s * 8.2, 11.8, 0.0), (s * 10.6, 14.2, 2.8), horn)             # curves inward and forward
    head.box((s * 6.6, 13.8, 1.2), (s * 8.8, 16, 4), horn.with_(base=(240, 232, 204)))
eyes = c.part("eyes", pivot=(0, 5.2, 6.9), parent=head, bright=True)
for s in (1, -1):
    eyes.box((s * 1.4, -0.4, 0.0), (s * 4.4, 1.3, 0.9), eyem)
    eyes.box((s * 3, 1.3, 0.0), (s * 4.6, 1.8, 0.7), eyem)

# ---------------------------------------------------------------- arms: swing counter to the legs
for s, side in ((1, "l"), (-1, "r")):
    ph = 0.5 if s > 0 else 0.0
    arm = c.part("arm_" + side, pivot=(s * 15.5, 12, 0), parent=torso,
                 anim=[swing("x", 24, 1.0, ph, when="moving"), swing("z", s * 1.5, 0.3, ph), strike("x", -150, 14)])
    arm.box((-5, -3, -6), (5, 3.5, 6), mossy)                           # shoulder pad
    arm.box((-4.2, -13, -4.5), (4.2, -3, 4.5), stone)
    arm.box((-4.8, -9, -5), (4.8, -6.5, 5), dark)
    arm.box((-1.6, 3.5, -1.6), (1.6, 8, 1.6), stone, rot=("z", 22.5))                  # shoulder spikes
    arm.box((-1.4 + s * 3, 3, 1.5), (1.4 + s * 3, 6.5, 4.4), stone, rot=("x", -22.5))
    fore = c.part("fore_" + side, pivot=(0, -13, 0), parent=arm,
                  anim=[swing("x", -8, 1.0, ph + 0.15, when="moving", wave="pos"), strike("x", -25, 14)])
    fore.box((-4.8, -9, -4.8), (4.8, 0.5, 4.8), mossy.with_(faces={"top": stone}))
    fore.box((-6, -17, -6.5), (6, -8, 6.5), fist)                       # big fist
    for x in (-3.9, -1.3, 1.3, 3.9):
        fore.box((x - 1.2, -18.4, 4.8), (x + 1.2, -14, 7.4), dark)          # knuckles
    fb = c.part("band_" + side, pivot=(0, 0, 0), parent=fore, bright=True)
    fb.box((-5.3, -5.6, -5.3), (5.3, -4.4, 5.3), rune, skip=("top", "bottom"))
    fb.box((s * 5.0, -8, -2), (s * 5.3, -5, 2), rune)

# ---------------------------------------------------------------- legs
for s, side in ((1, "l"), (-1, "r")):
    ph = 0.0 if s > 0 else 0.5
    leg = c.part("leg_" + side, pivot=(s * 7, 20, 0), anim=[swing("x", 24, 1.0, ph, when="moving")])
    leg.box((-4.5, -10, -4.5), (4.5, 1, 4.5), stone)
    leg.box((-5, -5, -5), (5, -2.5, 5), dark)
    leg.box((-4.5, 0, -4.5), (4.5, 1.2, 4.5), moss, skip=("bottom",))
    shin = c.part("shin_" + side, pivot=(0, -10, 0), parent=leg,
                  anim=[swing("x", 26, 1.0, ph + 0.25, when="moving", wave="pos")])
    shin.box((-4, -8, -4), (4, 0.5, 4), mossy)
    shin.box((-5.5, -10, -6), (5.5, -6.5, 7.5), dark)                       # foot
    shin.box((-5.8, -10, 5.5), (5.8, -7.4, 8), stone)                       # toe block
    kn = c.part("knee_" + side, pivot=(0, 0, 0), parent=shin, bright=True, anim=[pulse(0.1, 0.5, 0.2 * s)])
    kn.box((-1.6, -1.6, 4.0), (1.6, 1.6, 4.5), rune)
