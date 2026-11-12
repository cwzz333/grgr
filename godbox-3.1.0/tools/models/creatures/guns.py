"""Огнестрельное оружие: мушкет (арбалет с custom_model_data в руках солдат) и пушечное ядро.

Not an attached creature. `musket` replaces the look of a vanilla crossbow that carries its custom_model_data
(gen.py writes the override into assets/minecraft/models/item/crossbow.json), so pillagers keep their crossbow AI
and pose. The barrel points to +Z; the display transforms put the grip into the hand. `ball` is the item shown by
the flying cannon ball (ThrownItem renders it with the `ground` context).
"""
from lib import *

c = Creature("guns", id_base=4000, scale=1.0, res=2, hide_carrier=False)

SIDES = ("left", "right")
wood = Mat((122, 78, 44), mottle=(84, 50, 28), mottle_amt=0.55, mottle_size=3, bevel=0.2,
           patterns=[stripes((66, 38, 22), 1, 3, "h", 0.3, 0.8)])
dark_wood = wood.with_(base=(96, 58, 34))
iron = Mat((70, 74, 84), mottle=(44, 46, 54), mottle_amt=0.5, mottle_size=2, bevel=0.3,
           patterns=[speckle((150, 156, 170), 0.05)])
steel = Mat((150, 156, 168), mottle=(104, 110, 124), mottle_size=2, bevel=0.3)
brass = Mat((222, 172, 60), mottle=(160, 112, 30), mottle_size=2, bevel=0.35)
bore = Mat((16, 16, 18), noise=0.02, shade=0, bevel=0)

# vanilla crossbow in a hand: the item's -Z (our +Z after the generator's flip) is the aiming direction,
# tilted 15 deg towards the other hand; see UV_NOTES.md
HAND = {"rotation": [0, -15, 0], "translation": [0, 2.5, -1.5], "scale": [1.0, 1.0, 1.0]}
LEFT = {"rotation": [0, 15, 0], "translation": [0, 2.5, -1.5], "scale": [1.0, 1.0, 1.0]}
musket = c.part("musket", pivot=(0, 8, 0), display={
    "thirdperson_righthand": HAND, "thirdperson_lefthand": LEFT,
    "firstperson_righthand": {"rotation": [0, -8, 0], "translation": [1.5, 3.5, -2], "scale": [0.8, 0.8, 0.8]},
    "firstperson_lefthand": {"rotation": [0, 8, 0], "translation": [1.5, 3.5, -2], "scale": [0.8, 0.8, 0.8]},
    "ground": {"rotation": [0, 0, 90], "translation": [0, 2, 0], "scale": [0.7, 0.7, 0.7]},
    "gui": {"rotation": [20, 60, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]},
    "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]}})
# stock: butt drops towards the shoulder
musket.box((-1, -4.5, -10), (1, -0.5, -7), dark_wood)
musket.box((-1, -3.5, -7), (1, 0, -4), wood)
musket.box((-1.1, -4.7, -10.4), (1.1, -0.3, -10), brass)                     # butt plate
musket.box((-1, -2.2, -4), (1, 0.6, 0), wood)                                # wrist / grip
musket.box((-1, -1.2, 0), (1, 0.9, 9), wood)                                 # fore-stock under the barrel
musket.box((-0.75, 0.9, -3), (0.75, 2.3, 13), iron,                          # barrel
           faces={"front": bore.with_(patterns=[rect(0, 0, 1, 1, (70, 74, 84)), rect(0.25, 0.25, 0.75, 0.75, (16, 16, 18))])})
musket.box((-0.9, 0.7, 12), (0.9, 2.5, 12.8), steel)                         # muzzle band
musket.box((-1.1, -1.3, 4), (1.1, 1.1, 5), brass)                            # barrel bands
musket.box((-1.1, -1.3, 8), (1.1, 1.1, 9), brass)
musket.box((-0.3, 2.3, 11.5), (0.3, 2.9, 12.3), steel)                       # front sight
# flintlock on the right side (-X): lock plate, pan, cock
musket.box((-1.5, 0.0, -3), (-1, 1.6, 1.5), steel)
musket.box((-1.9, 1.0, -0.5), (-1, 1.6, 0.8), brass)
musket.box((-1.6, 1.6, -3), (-1.1, 3.4, -2), iron)
musket.box((-1.6, 2.8, -2.4), (-1.1, 3.6, -0.8), iron)
# trigger guard and trigger
musket.box((-0.4, -3.2, -3.2), (0.4, -2.6, 0.2), brass)
musket.box((-0.4, -2.6, -3.2), (0.4, -2.2, -2.6), brass)
musket.box((-0.4, -2.6, -0.4), (0.4, -1.2, 0.2), brass)
musket.box((-0.25, -2.6, -1.9), (0.25, -1.4, -1.4), iron)
musket.box((-0.3, -1.9, 1), (0.3, -1.3, 10.5), steel)                        # ramrod

# cannon ball: a rounded iron shot (three crossed boxes)
shot = Mat((52, 54, 60), mottle=(30, 30, 36), mottle_size=2, bevel=0.3, patterns=[speckle((120, 124, 134), 0.04)])
ball = c.part("ball", pivot=(14, 8, 0), display={
    "ground": {"translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    "gui": {"rotation": [20, 30, 0], "scale": [1.2, 1.2, 1.2]}})
ball.box((-3, -4, -3), (3, 4, 3), shot)
ball.box((-4, -3, -3), (4, 3, 3), shot)
ball.box((-3, -3, -4), (3, 3, 4), shot)

c.item_override("minecraft:crossbow", musket)
