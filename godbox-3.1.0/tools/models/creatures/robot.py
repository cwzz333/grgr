"""Мехагигант: iron-golem-carried battle mech boss (golem at scale 4: hitbox 5.6 x 10.8 blocks)."""
from lib import *

c = Creature("robot", id_base=1100, scale=4.0, res=2, hide_carrier=True, death_fx=True, hitbox=(5.6, 10.8))

STEEL, GUN, DARKM = (134, 154, 178), (80, 88, 104), (46, 50, 62)
YEL, ORANGE, BLACK = (244, 196, 24), (232, 128, 24), (22, 22, 26)
SIDES = ("front", "back", "left", "right")

steel = Mat(STEEL, mottle=(86, 102, 124), mottle_amt=0.45, mottle_size=5, shade=0.14,
            patterns=[plates((8, 6), 0.4, 0.15), speckle((176, 196, 214), 0.012), rivets((60, 68, 80), 8, 1)])
armor = steel.with_(base=(112, 138, 170), patterns=[plates((10, 8), 0.45, 0.2), rivets((200, 210, 224), 9, 1)])
gun = Mat(GUN, mottle=DARKM, mottle_amt=0.5, mottle_size=3, bevel=0.22,
          patterns=[speckle((120, 130, 146), 0.02), rivets((150, 158, 172), 7, 1)])
dark = Mat(DARKM, mottle=(20, 22, 28), mottle_amt=0.5, mottle_size=2, bevel=0.25, patterns=[speckle((90, 96, 110), 0.02)])
hazard = Mat(YEL, noise=0.05, bevel=0.2, patterns=[stripes(BLACK, 2, 2, "d", 0.95), speckle((120, 90, 10), 0.03)])
accent = Mat(ORANGE, mottle=(180, 90, 16), mottle_size=2, bevel=0.25, patterns=[speckle((255, 190, 90), 0.03)])
hot = Mat((70, 40, 28), noise=0.06, patterns=[stripes((255, 120, 20), 1, 3, "h", 0.9)], shade=0)
core = Mat((255, 224, 120), noise=0.05, shade=0, bevel=0.0, mottle=(255, 150, 30), mottle_size=2, mottle_amt=0.8,
           faces={"front": Mat((255, 236, 150), noise=0, shade=0, bevel=0,
                               patterns=[rect(0.2, 0.2, 0.8, 0.8, (255, 120, 20), 0.55),
                                         rect(0.38, 0.38, 0.62, 0.62, (255, 255, 230))])})
visor = Mat((255, 90, 30), noise=0.05, shade=0, bevel=0, mottle=(255, 200, 60), mottle_size=2, mottle_amt=0.7,
            patterns=[stripes((255, 240, 150), 1, 5, "v", 0.7)])
muzzle = Mat((255, 150, 40), noise=0.05, shade=0, bevel=0, mottle=(255, 240, 160), mottle_size=2, mottle_amt=0.8)
chestplate = steel.with_(base=(104, 124, 150), add=[on("front", rect(0.08, 0.84, 0.92, 0.94, YEL, 0.95)),
                                                  on("front", rect(0.08, 0.06, 0.92, 0.14, YEL, 0.95))])

# ---------------------------------------------------------------- pelvis (root) and legs
pelvis = c.part("pelvis", pivot=(0, 18, 0), anim=[bob(0.5, 0.35), bob(0.7, 2.4, when="moving", wave="abs")])
pelvis.box((-7, -1, -4.5), (7, 4, 4.5), gun)
pelvis.box((-7.5, 1, 4.5), (7.5, 3.5, 5.5), hazard)
pelvis.box((-2, 0, 5.5), (2, 3, 6.5), accent)
pelvis.box((-7.5, -1, -3), (-7, 3, 3), dark)
pelvis.box((7, -1, -3), (7.5, 3, 3), dark)

torso = c.part("torso", pivot=(0, 4, 0), parent=pelvis,
               anim=[swing("y", 3, 0.2), swing("y", 4, 1.2, when="moving"), strike("y", -14, 12)])
torso.box((-6, 0, -4), (6, 2, 4), dark)                                   # waist
torso.box((-9, 2, -6), (9, 14, 5.5), armor)                           # chest
torso.box((-9.5, 8, -5), (9.5, 14, 5), armor.with_(base=(88, 108, 134)))  # upper shelf
torso.box((-7.5, 3, 5.5), (7.5, 13, 7), chestplate)                       # front plate
torso.box((-4.5, 4.5, 7), (4.5, 11.5, 8), dark)                           # reactor housing
torso.box((-6, 2, 7), (6, 4, 8), hazard)
torso.box((-1, 13.5, 5.5), (1, 15, 7), accent)
for s in (1, -1):
    torso.box((s * 5.5, 5, 7), (s * 7, 12, 8), accent, rot=None)
    torso.box((s * 4, 14, -4), (s * 8, 17, 3), gun)                       # neck shoulder guards
# backpack
torso.box((-6, 2, -10), (6, 14, -5.5), gun)
torso.box((-6.5, 11, -10.5), (6.5, 13.5, -9), hazard)
for s in (1, -1):
    torso.box((s * 3.5 - 1.6, 10, -9.5), (s * 3.5 + 1.6, 22, -6), dark, rot=("x", 22.5), origin=(0, 12, -8))
    torso.box((s * 3.5 - 2.2, 20, -9.5), (s * 3.5 + 2.2, 21.5, -5), accent, rot=("x", 22.5), origin=(0, 12, -8))

reactor = c.part("reactor", pivot=(0, 8, 8), parent=torso, bright=True, anim=[pulse(0.08, 0.7)])
reactor.box((-3.8, -3.2, 0), (3.8, 3.2, 0.8), core)
reactor.box((-2, -4, 0), (2, 4, 0.5), core, skip=("front",))

# ---------------------------------------------------------------- head
head = c.part("head", pivot=(0, 14, 0), parent=torso, anim=[swing("y", 4, 0.25, 0.3), swing("y", -3, 1.2, when="moving")])
head.box((-3, 0, -3), (3, 2, 3), dark)
head.box((-6, 2, -5), (6, 10, 5.5), armor, skip=("bottom",))
head.box((-6.5, 5, -3), (-6, 10, 3), gun)
head.box((6, 5, -3), (6.5, 10, 3), gun)
head.box((-1.5, 10, -4.5), (1.5, 12.5, 4.5), hazard)                              # crest
head.box((-5, 10, -3), (5, 10.8, 4.5), gun, faces={"top": accent})
head.box((-5.6, 2, 5.5), (5.6, 4, 6.1), gun)                                 # chin guard
head.box((6, 4, -1), (7, 8.5, 0.6), gun)
head.box((6.2, 8.5, -0.8), (6.7, 12, -0.3), dark)                          # antenna
head.box((-6.6, 6, 3.5), (-6, 8.5, 5), accent)
visor_p = c.part("visor", pivot=(0, 6.8, 5.5), parent=head, bright=True, anim=[pulse(0.04, 0.9, 0.2)])
visor_p.box((-5.2, -1.8, 0), (5.2, 1.8, 0.8), visor)

# ---------------------------------------------------------------- left arm: heavy fist
def arm_base(side):
    return (side * 12, 11.5, 0)

arm_l = c.part("arm_l", pivot=arm_base(1), parent=torso,
               anim=[swing("x", 14, 1.2, 0.5, "moving"), swing("x", 2, 0.35, 0.2), strike("x", -85, 10)])
arm_l.box((-3, -4, -7), (6, 3, 7), armor, faces={"top": hazard})                                  # pauldron
arm_l.box((-2, 3, -6), (5, 5, 6), armor.with_(base=(120, 142, 168)))
arm_l.box((-0.5, 5, -2.5), (4.5, 8, 2.5), accent, rot=("y", 45))
arm_l.box((1, 8, -1), (3, 10, 1), hazard, rot=("y", 45))
arm_l.box((-3, 3, 6.4), (6, 4.2, 7), hazard)
arm_l.box((-2.5, -13, -3), (3.5, -4, 3), gun)                                # upper arm
arm_l.box((-3, -6, -3.6), (4, -4, 3.6), dark)
forearm_l = c.part("forearm_l", pivot=(0.5, -13, 0), parent=arm_l,
                   anim=[swing("x", -8, 1.2, 0.75, "moving", "pos"), swing("x", 1.5, 0.35, 0.5)])
forearm_l.box((-4, -3, -4), (5, 1, 4), dark)                                  # elbow
forearm_l.box((-4.5, -13, -4.5), (5.5, -3, 4.5), steel)                      # big gauntlet
forearm_l.box((-5, -7, -5), (6, -5, 5), hazard)
fist = c.part("fist_l", pivot=(0.5, -13, 0), parent=forearm_l, anim=[swing("x", 5, 1.2, 0.5, "moving")])
fist.box((-4.5, -4, -4.5), (5.5, 0, 4.5), gun)
for fx in (-3.5, -0.5, 2.5):
    fist.box((fx, -6.5, 1.5), (fx + 2.5, -1, 4.8), dark)                      # claws
fist.box((-5.5, -5, -2.5), (-3, -1, 1), dark, rot=("z", -22.5))              # thumb

# ---------------------------------------------------------------- right arm: cannon
arm_r = c.part("arm_r", pivot=arm_base(-1), parent=torso,
               anim=[swing("x", 14, 1.2, 0.0, "moving"), swing("x", 2, 0.35, 0.7), strike("x", 32, 10)])
arm_r.box((-6, -4, -7), (3, 3, 7), armor, faces={"top": hazard})
arm_r.box((-5, 3, -6), (2, 5, 6), armor.with_(base=(120, 142, 168)))
arm_r.box((-4.5, 5, -2.5), (0.5, 8, 2.5), accent, rot=("y", 45))
arm_r.box((-3, 8, -1), (-1, 10, 1), hazard, rot=("y", 45))
arm_r.box((-6, 3, 6.4), (3, 4.2, 7), hazard)
arm_r.box((-3.5, -13, -3), (2.5, -4, 3), gun)
arm_r.box((-4, -6, -3.6), (3, -4, 3.6), dark)
cannon = c.part("cannon", pivot=(-0.5, -13, 0), parent=arm_r, rot=(-72, 0, 0), anim=[swing("x", 3, 1.0, 0.4)])
cannon.box((-4.5, -11, -4.5), (4.5, 2, 4.5), steel)
cannon.box((-5, -6, -5), (5, -4.5, 5), hazard)
cannon.box((-3, -18, -3), (3, -11, 3), gun)
cannon.box((-4, -13, -4), (4, -11.5, 4), dark)
cannon.box((-4, -19.5, -4), (4, -17.5, 4), dark)
cannon.box((-1.5, 2, -1.5), (1.5, 4, 1.5), accent)
mz = c.part("muzzle", pivot=(0, -19, 0), parent=cannon, bright=True, anim=[pulse(0.25, 1.1)])
mz.box((-2.2, -2, -2.2), (2.2, 0.4, 2.2), muzzle)

# ---------------------------------------------------------------- legs
def leg(side, ph):
    n = "l" if side > 0 else "r"
    thigh = c.part("leg_" + n, pivot=(side * 4.5, 0, 0), parent=pelvis,
                   anim=[swing("x", -24, 1.1, ph, "moving"), swing("x", 1, 0.35, ph / 2)])
    thigh.box((-3, -9, -3.5), (3, 2, 3.5), steel)
    thigh.box((-3.4, -4, -4), (3.4, -2, 4), hazard)
    thigh.box((-2, -9.5, -3), (2, -8, 3), dark)
    shin = c.part("shin_" + n, pivot=(0, -9, 0), parent=thigh, anim=[swing("x", 28, 1.1, ph + 0.28, "moving", "pos")])
    shin.box((-3, -9.5, -3.5), (3, 0.5, 3), gun)
    shin.box((-3.6, -1, -4), (3.6, 2, 4), steel)                              # knee guard
    shin.box((-3.2, -7, -3.8), (3.2, -4.5, -3), accent)
    shin.box((-3.2, -7, 3), (3.2, -4.5, 3.8), accent)
    foot = c.part("foot_" + n, pivot=(0, -9, 0), parent=shin, anim=[swing("x", 10, 1.1, ph + 0.05, "moving")])
    foot.box((-3.5, -0.2, -4), (3.5, 3, 5.5), dark)
    foot.box((-4, 0, 4), (4, 2.5, 8), steel)
    foot.box((-3.5, 2.5, 5), (3.5, 3.4, 7.5), hazard)

leg(1, 0.0)
leg(-1, 0.5)
