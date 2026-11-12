"""Головные уборы значимых юнитов: предмет godbox:model в слоте HEAD обычного моба (ванильная анимация).

Not a creature that is attached: every part is a hat worn through the vanilla HeadFeatureRenderer. In a part
y = 0 is the top of the mob's head, the head is x,z -4..4 and reaches down to y = -8 (zombies, skeletons) or
y = -10 (villagers, illagers, wandering traders); +Z is the face. Each hat has three variants with the head
display transform of the three head kinds (lib.head_display): <hat>_biped, <hat>_villager, <hat>_tall -
Models.equipHat picks one by the mob's type. The preview shows the hats on dummy heads (ghost boxes).
"""
from lib import *

c = Creature("hats", id_base=3000, scale=1.0, res=2, hide_carrier=False)

SIDES = ("front", "back", "left", "right")
SKIN, ROBE = (189, 139, 114), (92, 70, 56)
_x = [-81]


def hat(name):
    x = _x[0]
    _x[0] += 18
    c.ghost((x - 4, 22, -4), (x + 4, 32, 4), SKIN)          # 8 x 10 x 8 head
    c.ghost((x - 1, 21, 4), (x + 1, 25, 6), (170, 120, 98))  # nose
    c.ghost((x - 4, 10, -3), (x + 4, 22, 3), ROBE)
    return c.part(name, pivot=(x, 32, 0))


def ring(p, r, y0, y1, t, mat, **kw):
    """Square band around the head: outer half-size r, wall thickness t."""
    p.box((-r, y0, r - t), (r, y1, r), mat, **kw)
    p.box((-r, y0, -r), (r, y1, -r + t), mat, **kw)
    p.box((r - t, y0, -r + t), (r, y1, r - t), mat, **kw)
    p.box((-r, y0, -r + t), (-r + t, y1, r - t), mat, **kw)


gold = Mat((246, 198, 56), noise=0.05, mottle=(204, 142, 30), mottle_amt=0.45, mottle_size=2, bevel=0.3)
steel = Mat((170, 178, 192), mottle=(122, 130, 146), mottle_amt=0.45, mottle_size=3, bevel=0.25,
            patterns=[rivets((226, 230, 238), 4, 1)])
dark_steel = steel.with_(base=(104, 110, 124), mottle=(70, 76, 90))
plume = Mat((206, 36, 40), mottle=(140, 16, 28), mottle_size=2, patterns=[stripes((250, 110, 90), 1, 2, "v", 0.45)])


def gem(color, hi):
    return Mat(color, noise=0.03, shade=0, bevel=0.35, patterns=[rect(0.2, 0.15, 0.5, 0.45, hi, 0.9)])


# ---------------------------------------------------------------- king's crown
crown = hat("crown")
velvet = Mat((158, 24, 44), mottle=(110, 12, 34), mottle_size=2, patterns=[speckle((220, 80, 96), 0.05)])
crown.box((-3.6, 0, -3.6), (3.6, 2.2, 3.6), velvet, skip=("bottom",))
ring(crown, 4.7, 0, 2.6, 1.1, gold.with_(add=[on(SIDES, band((255, 236, 140), "top", 0.2, 0.8)),
                                             on(SIDES, band((176, 112, 24), "bottom", 0.2, 0.8))]))
for x, z in ((-4.2, 4.2), (4.2, 4.2), (-4.2, -4.2), (4.2, -4.2)):                 # corner points
    crown.box((x - 0.6, 2.6, z - 0.6), (x + 0.6, 4.6, z + 0.6), gold, skip=("bottom",))
for x, z in ((0, 4.2), (0, -4.2), (4.2, 0), (-4.2, 0)):                           # taller middle points
    crown.box((x - 0.8, 2.6, z - 0.6) if z else (x - 0.6, 2.6, -0.8), (x + 0.8, 5.4, z + 0.6) if z else (x + 0.6, 5.4, 0.8),
              gold, skip=("bottom",))
    crown.box((x - 0.5, 5.4, z - 0.5), (x + 0.5, 6.2, z + 0.5), gem((250, 250, 255), (255, 255, 255)), skip=("bottom",))
crown.box((-0.9, 0.5, 4.7), (0.9, 2.1, 5.2), gem((226, 30, 48), (255, 170, 170)), skip=("back",))
crown.box((-0.9, 0.5, -5.2), (0.9, 2.1, -4.7), gem((40, 190, 90), (190, 255, 200)), skip=("front",))
crown.box((4.7, 0.5, -0.9), (5.2, 2.1, 0.9), gem((50, 110, 236), (180, 210, 255)), skip=("right",))
crown.box((-5.2, 0.5, -0.9), (-4.7, 2.1, 0.9), gem((50, 110, 236), (180, 210, 255)), skip=("left",))

# ---------------------------------------------------------------- hero helmet (open face, nose guard, crest)
hero = hat("hero_helmet")
hero.box((-4.7, -2.6, -4.7), (4.7, 0.8, 4.7), steel, skip=("bottom",))
hero.box((-3.4, 0.8, -3.4), (3.4, 1.5, 3.4), steel, skip=("bottom",))
hero.box((-4.7, -7.5, -4.7), (4.7, -2.6, -3.9), steel)                             # neck guard
for s in (1, -1):
    hero.box((s * 3.9, -7, -3.9), (s * 4.7, -2.6, 1.5), steel)                     # cheek plates
    hero.box((s * 3.9, -5.2, 1.5), (s * 4.7, -2.6, 3.2), steel)
hero.box((-4.9, -2.6, 4.1), (4.9, -1.4, 5.0), gold)                                # brow band
hero.box((-0.6, -6.2, 4.1), (0.6, -2.6, 5.0), dark_steel)                          # nose guard
hero.box((-0.7, 0.8, -4.6), (0.7, 3.0, 3.8), plume, skip=("bottom",))              # crest
hero.box((-0.7, 3.0, -3.6), (0.7, 3.8, 2.4), plume, skip=("bottom",))

# ---------------------------------------------------------------- legend helmet (full helm, gold trim, wings, tall plume)
legend = hat("legend_helmet")
bright_steel = steel.with_(base=(206, 212, 226), mottle=(150, 160, 184))
legend.box((-4.7, -2.6, -4.7), (4.7, 0.8, 4.7), bright_steel, skip=("bottom",))
legend.box((-3.4, 0.8, -3.4), (3.4, 1.6, 3.4), gold, skip=("bottom",))
legend.box((-4.7, -8.5, -4.7), (4.7, -2.6, -3.9), bright_steel)
for s in (1, -1):
    legend.box((s * 3.9, -8.5, -3.9), (s * 4.7, -2.6, 4.1), bright_steel)
    legend.box((s * 1.2, -8.5, 4.1), (s * 4.7, -5.4, 4.9), bright_steel)           # cheek plates of the T-visor
    legend.box((s * 1.2, -8.7, 4.3), (s * 1.9, -5.2, 5.1), gold)
    # swept-back wings
    wing = Mat((244, 244, 236), mottle=(190, 196, 206), mottle_size=2, patterns=[stripes((150, 158, 176), 1, 2, "d", 0.5)])
    legend.box((s * 4.7, -3.0, -2.0), (s * 5.5, 1.5, 1.5), gold)
    legend.box((s * 4.9, 0.0, -4.0), (s * 5.7, 4.5, -0.5), wing)
    legend.box((s * 5.1, 2.5, -6.0), (s * 5.9, 7.0, -2.5), wing)
    legend.box((s * 5.3, 5.5, -7.5), (s * 6.1, 9.0, -4.5), wing)
legend.box((-4.9, -2.6, 4.1), (4.9, -1.2, 5.1), gold)                              # brow band
legend.box((-4.9, -9.0, -4.9), (4.9, -8.2, 4.3), gold, skip=("top",))              # bottom trim
legend.box((-0.8, 1.6, -5.0), (0.8, 4.5, 4.2), plume, skip=("bottom",))
legend.box((-0.8, 4.5, -6.5), (0.8, 6.5, 2.0), plume, skip=("bottom",))
legend.box((-0.8, 2.0, -8.0), (0.8, 5.5, -5.0), plume)
legend.box((-0.9, -1.0, 4.7), (0.9, 0.8, 5.4), gem((60, 200, 255), (220, 250, 255)), skip=("back",))

# ---------------------------------------------------------------- kingdom mage hat (blue, starry, gold band)
mage = hat("mage_hat")
cloth = Mat((50, 70, 196), mottle=(30, 40, 140), mottle_amt=0.5, mottle_size=3,
            patterns=[speckle((255, 232, 120), 0.03), spots((255, 240, 150), 0.012, (1, 1.5))])
mage.box((-7, 0.1, -7), (7, 1.0, 7), cloth.with_(add=[on("top", rect(0, 0, 1, 0.07, (240, 196, 60))),
                                                    on("top", rect(0, 0.93, 1, 1, (240, 196, 60))),
                                                    on("top", rect(0, 0, 0.07, 1, (240, 196, 60))),
                                                    on("top", rect(0.93, 0, 1, 1, (240, 196, 60)))]))
mage.box((-4.4, 1.0, -4.4), (4.4, 3.4, 4.4), gold.with_(add=[on("front", rect(0.4, 0.1, 0.6, 0.9, (120, 70, 16)))]), skip=("bottom",))
mage.box((-3.6, 3.4, -3.6), (3.6, 6.2, 3.6), cloth, skip=("bottom",))
mage.box((-2.6, 6.2, -3.2), (2.6, 9.0, 2.2), cloth, skip=("bottom",))
mage.box((-1.6, 9.0, -3.6), (1.6, 11.4, 0.6), cloth, skip=("bottom",))
mage.box((-0.9, 10.2, -5.4), (0.9, 12.0, -2.8), cloth)
mage.box((-0.8, 8.6, -6.6), (0.8, 10.4, -4.8), gem((255, 226, 90), (255, 255, 220)))      # star at the bent tip

# ---------------------------------------------------------------- necromancer hood
necro = hat("necro_hood")
shroud = Mat((40, 28, 52), mottle=(20, 14, 28), mottle_amt=0.6, mottle_size=3,
             patterns=[stripes((72, 48, 92), 1, 3, "v", 0.35, 1.0)])
rune = Mat((120, 232, 96), noise=0.04, shade=0, bevel=0.2, patterns=[stripes((30, 80, 40), 1, 2, "h", 0.7)])
necro.box((-4.8, -1.2, -4.8), (4.8, 1.0, 5.4), shroud, skip=("bottom",))
necro.box((-3.2, 1.0, -5.6), (3.2, 2.0, 2.0), shroud, skip=("bottom",))
necro.box((-2.0, 0.2, -6.6), (2.0, 1.6, -4.8), shroud)                              # drooping peak at the back
necro.box((-4.8, -10.5, -4.8), (4.8, -1.2, -3.9), shroud)
for s in (1, -1):
    necro.box((s * 3.9, -10.5, -3.9), (s * 4.8, -1.2, 4.6), shroud)
    necro.box((s * 3.7, -10.5, 4.6), (s * 4.9, -1.2, 5.4), rune)                    # glowing rune trim
necro.box((-3.7, -1.9, 4.6), (3.7, -1.2, 5.4), rune)
bone = Mat((232, 226, 204), mottle=(180, 172, 150), mottle_size=2, bevel=0.2)
necro.box((-1.6, -1.0, 5.4), (1.6, 1.6, 6.0), bone.with_(faces={"front": bone.with_(patterns=[
    rect(0.12, 0.2, 0.42, 0.6, (24, 14, 30)), rect(0.58, 0.2, 0.88, 0.6, (24, 14, 30)), rect(0.44, 0.62, 0.56, 0.8, (24, 14, 30))])}),
    skip=("back",))
necro.box((-1.1, -1.9, 5.4), (1.1, -1.0, 5.9), bone.with_(patterns=[stripes((60, 50, 60), 1, 1, "v", 0.8)]), skip=("back",))

# ---------------------------------------------------------------- evil mage hat (black, jagged brim, crooked tip)
evil = hat("evil_mage_hat")
black = Mat((34, 28, 40), mottle=(16, 12, 22), mottle_size=3, patterns=[speckle((120, 40, 150), 0.04)])
crimson = Mat((186, 26, 44), mottle=(120, 10, 30), mottle_size=2, bevel=0.3)
evil.box((-6.5, 0.1, -6.5), (6.5, 0.9, 6.5), black)
evil.box((-6.2, 0.2, -6.2), (6.2, 0.8, 6.2), black.with_(base=(58, 30, 70)), rot=("y", 45))      # jagged star brim
evil.box((-4.4, 0.9, -4.4), (4.4, 3.2, 4.4), crimson.with_(add=[on("front", rect(0.38, 0.1, 0.62, 0.9, (250, 200, 60))),
                                                               on("front", rect(0.45, 0.3, 0.55, 0.7, (60, 10, 20)))]), skip=("bottom",))
evil.box((-3.7, 3.2, -3.7), (3.7, 6.5, 3.7), black, skip=("bottom",))
evil.box((-2.8, 6.5, -2.6), (2.8, 9.8, 3.0), black, skip=("bottom",))
evil.box((-1.9, 9.8, -1.2), (1.9, 12.6, 3.6), black, skip=("bottom",))
evil.box((-1.1, 12.0, 1.4), (1.1, 14.0, 5.2), black)
evil.box((-0.7, 10.6, 4.4), (0.7, 12.6, 6.2), crimson)                              # tip hangs forward

# ---------------------------------------------------------------- white mage circlet
circlet = hat("white_circlet")
silver = Mat((244, 238, 200), noise=0.04, mottle=(210, 190, 120), mottle_size=2, bevel=0.3)
ring(circlet, 4.7, -3.4, -2.2, 0.8, silver)
circlet.box((-1.6, -3.9, 4.6), (1.6, -1.7, 5.1), silver, skip=("back",))
circlet.box((-0.6, -1.7, 4.6), (0.6, -0.4, 5.1), silver, skip=("back",))
circlet.box((-1.0, -3.6, 5.1), (1.0, -2.0, 5.6), Mat((130, 244, 255), noise=0, shade=0, bevel=0.2,
                                                     patterns=[rect(0.2, 0.15, 0.55, 0.5, (255, 255, 255))]), skip=("back",))
for s in (1, -1):                                                                    # small wings over the temples
    circlet.box((s * 4.7, -3.0, -0.5), (s * 5.2, -0.8, 2.5), silver)
    circlet.box((s * 4.7, -1.4, -2.5), (s * 5.2, 0.6, 0.5), silver)

# ---------------------------------------------------------------- plague doctor: beak mask + wide hat
plague = hat("plague_mask")
leather = Mat((36, 32, 34), mottle=(18, 16, 18), mottle_size=3, patterns=[speckle((70, 62, 60), 0.04)])
maskm = Mat((222, 208, 176), mottle=(176, 158, 124), mottle_amt=0.5, mottle_size=3,
            patterns=[stripes((110, 92, 70), 1, 3, "v", 0.35)])
plague.box((-6.6, 0.1, -6.6), (6.6, 0.9, 6.6), leather)
plague.box((-4.5, 0.9, -4.5), (4.5, 4.2, 4.5), leather.with_(add=[on(SIDES, band((120, 110, 104), "bottom", 0.3))]), skip=("bottom",))
plague.box((-4.4, -9.6, 3.9), (4.4, -0.6, 4.7), maskm)                               # face plate
plague.box((-4.7, -9.9, -4.7), (4.7, -8.6, 3.9), leather.with_(base=(52, 44, 44)))   # collar strap
for s in (1, -1):
    plague.box((s * 3.9, -8.6, -4.6), (s * 4.7, -0.6, 3.9), leather.with_(base=(52, 44, 44)))   # hood sides
    plague.box((s * 1.0, -5.4, 4.7), (s * 3.6, -2.8, 5.3), Mat((150, 140, 120), bevel=0.3), skip=("back",))    # lens rims
    plague.box((s * 1.5, -4.9, 5.3), (s * 3.1, -3.3, 5.6), Mat((30, 44, 52), noise=0, shade=0, bevel=0,
               patterns=[rect(0.15, 0.15, 0.5, 0.5, (150, 220, 230))]), skip=("back",))
plague.box((-4.7, -9.6, -4.7), (4.7, -0.6, -3.9), leather.with_(base=(52, 44, 44)))
beak = maskm.with_(base=(206, 188, 150), add=[stripes((90, 74, 56), 1, 2, "h", 0.3)])
plague.box((-1.7, -8.4, 4.7), (1.7, -5.2, 7.6), beak, skip=("back",))
plague.box((-1.2, -8.8, 7.6), (1.2, -6.0, 10.2), beak, skip=("back",))
plague.box((-0.7, -9.3, 10.2), (0.7, -7.2, 12.4), beak.with_(base=(150, 132, 100)), skip=("back",))

# ---------------------------------------------------------------- druid: leaf wreath with antlers
druid = hat("druid_crown")
leaf = Mat((62, 150, 52), mottle=(30, 96, 36), mottle_amt=0.6, mottle_size=2,
           patterns=[speckle((150, 220, 90), 0.08), spots((214, 40, 48), 0.03, (1, 1))])
antler = Mat((208, 182, 132), mottle=(150, 120, 80), mottle_size=2, bevel=0.25, patterns=[stripes((120, 94, 60), 1, 3, "h", 0.3)])
ring(druid, 4.9, -2.8, -0.6, 1.2, leaf)
for x, z, h in ((-3.5, 4.4, 1.6), (0, 4.5, 2.4), (3.5, 4.4, 1.6), (-3.5, -4.4, 1.4), (0, -4.5, 2.0), (3.5, -4.4, 1.4),
                (4.4, 2.0, 1.2), (-4.4, 2.0, 1.2), (4.4, -2.0, 1.6), (-4.4, -2.0, 1.6)):
    druid.box((x - 0.8, -0.6, z - 0.8), (x + 0.8, -0.6 + h, z + 0.8), leaf, rot=("y", 45))
druid.box((-0.7, -2.4, 4.9), (0.7, -1.0, 5.4), gem((255, 196, 40), (255, 250, 200)), skip=("back",))  # amber stone
for s in (1, -1):
    druid.box((s * 3.4, -0.6, -1.2), (s * 4.6, 6.0, 0.0), antler, rot=("z", -s * 22.5), origin=(s * 4.0, -0.6, -0.6))  # beam
    druid.box((s * 6.0, 4.6, -1.1), (s * 7.0, 9.4, -0.1), antler)                    # upright tine
    druid.box((s * 5.0, 3.2, 0.0), (s * 5.9, 4.2, 3.0), antler)                      # brow tine pointing forward
    druid.box((s * 7.0, 6.2, -1.1), (s * 9.2, 7.2, -0.1), antler)                    # outer tine
    druid.box((s * 8.4, 7.2, -1.1), (s * 9.2, 9.0, -0.1), antler)

# ---------------------------------------------------------------- bandit hood with a face bandana
bandit = hat("bandit_hood")
hoodm = Mat((112, 44, 36), mottle=(70, 24, 24), mottle_amt=0.6, mottle_size=3, patterns=[stripes((146, 70, 52), 1, 4, "v", 0.3, 1.0)])
scarf = Mat((44, 44, 52), mottle=(24, 24, 30), mottle_size=2, patterns=[stripes((90, 90, 104), 1, 2, "d", 0.35)])
bandit.box((-4.8, -1.4, -4.8), (4.8, 0.9, 5.0), hoodm, skip=("bottom",))
bandit.box((-3.4, 0.9, -4.2), (3.4, 1.7, 3.0), hoodm, skip=("bottom",))
bandit.box((-4.8, -10.4, -4.8), (4.8, -1.4, -3.9), hoodm)
for s in (1, -1):
    bandit.box((s * 3.9, -10.4, -3.9), (s * 4.8, -1.4, 3.2), hoodm)
    bandit.box((s * 3.9, -3.2, 3.2), (s * 4.8, -1.4, 5.0), hoodm)
bandit.box((-4.8, -2.2, 4.2), (4.8, -1.4, 5.2), hoodm.with_(base=(84, 30, 28)))      # hood rim over the brow
bandit.box((-4.6, -10.4, 3.2), (4.6, -6.2, 4.7), scarf)                              # bandana over the lower face
bandit.box((-1.5, -11.4, 4.7), (1.5, -6.6, 6.5), scarf, skip=("back",))              # ... and the nose
bandit.box((-2.4, -12.2, 4.0), (2.4, -10.4, 4.7), scarf)                             # hanging corner
bandit.box((-1.2, -6.0, -6.0), (1.2, -4.4, -4.8), scarf)                             # knot at the back
bandit.box((-0.6, -9.0, -5.8), (0.6, -6.0, -5.0), scarf)

# ---------------------------------------------------------------- variants per head kind
for base in list(c.parts):
    for kind in ("biped", "villager", "tall"):
        base.variant("%s_%s" % (base.name, kind), head_display(kind))
