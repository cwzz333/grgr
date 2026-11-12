"""Огненный череп: flaming skull carried by an invisible vex (hitbox 0.4 x 0.8 blocks), spawned in swarms."""
from lib import *

c = Creature("fire_skull", id_base=1800, scale=1.0, res=2, hide_carrier=True, death_fx=False, hitbox=(0.4, 0.8))

BONE, CRACK, DARK, ORANGE = (234, 226, 204), (120, 100, 82), (22, 10, 8), (255, 138, 24)
bone = Mat(BONE, noise=0.05, mottle=(204, 192, 166), mottle_amt=0.5, mottle_size=3, shade=0.14,
           patterns=[speckle(CRACK, 0.03), stripes(CRACK, 1, 7, "d", 0.35, 0.5)])
# front: sockets with glowing pupils, nose hole, cheekbones, upper teeth
face = bone.with_(add=[
    rect(0.10, 0.22, 0.44, 0.58, DARK), rect(0.56, 0.22, 0.90, 0.58, DARK),
    rect(0.22, 0.30, 0.34, 0.50, ORANGE), rect(0.66, 0.30, 0.78, 0.50, ORANGE),
    rect(0.26, 0.34, 0.30, 0.44, (255, 226, 90)), rect(0.70, 0.34, 0.74, 0.44, (255, 226, 90)),
    rect(0.45, 0.62, 0.55, 0.78, DARK), rect(0.10, 0.60, 0.22, 0.68, (200, 186, 158)),
    rect(0.78, 0.60, 0.90, 0.68, (200, 186, 158))])
upper = bone.with_(add=[rect(0.14, 0.4, 0.18, 1.0, DARK), rect(0.31, 0.4, 0.35, 1.0, DARK), rect(0.48, 0.4, 0.52, 1.0, DARK),
                        rect(0.65, 0.4, 0.69, 1.0, DARK), rect(0.82, 0.4, 0.86, 1.0, DARK)])
side = bone.with_(add=[rect(0.55, 0.15, 0.85, 0.30, DARK, 0.4)])
jaw_m = bone.with_(base=(222, 214, 190), faces={"front": bone.with_(add=[
    rect(0.0, 0.0, 1.0, 0.45, (222, 214, 190)), rect(0.14, 0.0, 0.18, 0.5, DARK), rect(0.31, 0.0, 0.35, 0.5, DARK),
    rect(0.48, 0.0, 0.52, 0.5, DARK), rect(0.65, 0.0, 0.69, 0.5, DARK), rect(0.82, 0.0, 0.86, 0.5, DARK)])})
fire = Mat((255, 150, 30), noise=0.05, mottle=(255, 220, 70), mottle_amt=0.7, mottle_size=2, shade=0, bevel=0.1,
           patterns=[stripes((255, 90, 10), 1, 3, "v", 0.6)])
fire_hot = Mat((255, 196, 50), noise=0.04, mottle=(255, 232, 110), mottle_size=2, shade=0, bevel=0.1)

skull = c.part("skull", pivot=(0, 7, 0), anim=[bob(0.8, 0.8), swing("x", 3, 0.6)])
skull.box((-5.5, -2, -5), (5.5, 5.5, 5), bone, faces={"front": face, "left": side, "right": side})
skull.box((-4.5, 5.5, -4), (4.5, 6.5, 4), bone, skip=("bottom",))
skull.box((-4.5, -4, -1), (4.5, -2, 4.8), bone, faces={"front": upper})
skull.box((-4.6, -1.2, 4.9), (-3, 0.4, 5.2), bone)               # cheekbone ridges
skull.box((3, -1.2, 4.9), (4.6, 0.4, 5.2), bone)

jaw = c.part("jaw", pivot=(0, -4, 0), parent=skull,
             anim=[swing("x", 9, 2.4, wave="snap"), strike("x", 34)])
jaw.box((-3.8, -3, -0.5), (3.8, 0, 4.2), jaw_m)
jaw.box((-4.2, -2.2, -1), (-3, 0, 1.5), bone)
jaw.box((3, -2.2, -1), (4.2, 0, 1.5), bone)

flames = c.part("flames", pivot=(0, 5, -2), parent=skull, bright=True,
                anim=[pulse(0.08, 2.2), swing("z", 5, 3.0)])
flames.box((-5.5, -1.5, -6), (5.5, 1.5, 3), fire, skip=("bottom",))
flames.box((-4.5, 1.5, -5), (4.5, 5, 1.5), fire)
flames.box((-3, 5, -4), (3, 8, 0), fire_hot)
flames.box((-1.5, 8, -3), (1.5, 11, -0.5), fire_hot)
flames.box((-5, 0, -9), (-1, 3.5, -5), fire)
flames.box((1, 0, -10), (5, 4, -6), fire_hot)
flames.box((-2, 1, -13), (2, 3.5, -9), fire)
flames.box((-6, 1.5, -2), (-4.5, 6, 0.5), fire_hot)
flames.box((4.5, 1.5, -2), (6, 5, 0.5), fire_hot)
