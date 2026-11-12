"""Прыгучий череп: lava-cracked basalt skull carried by an invisible magma cube (hitbox 1.04 x 1.04 blocks)."""
from lib import *

c = Creature("jumpy_skull", id_base=1900, scale=1.0, res=2, hide_carrier=True, death_fx=False, hitbox=(1.04, 1.04))

LAVA, HOT, DARK = (255, 106, 18), (255, 196, 60), (14, 8, 8)
rock = Mat((70, 66, 74), noise=0.06, mottle=(46, 44, 52), mottle_amt=0.6, mottle_size=4, shade=0.16,
           patterns=[stripes(LAVA, 1, 9, "d", 0.85, 1.0), stripes(HOT, 1, 13, "v", 0.5, 1.5),
                     speckle((120, 116, 128), 0.03), speckle(LAVA, 0.012)])
face = rock.with_(add=[
    rect(0.07, 0.15, 0.43, 0.72, DARK), rect(0.57, 0.15, 0.93, 0.72, DARK),
    rect(0.46, 0.74, 0.54, 0.92, DARK), rect(0.0, 0.0, 1.0, 0.04, LAVA, 0.6)])
top = rock.with_(add=[stripes(LAVA, 1, 6, "d", 0.7, 2.0), spots(HOT, 0.02, (1, 2), 0.8)])
cheek = rock.with_(patterns=[stripes(LAVA, 1, 6, "d", 0.7, 1.0), speckle((120, 116, 128), 0.03)])
jaw_m = rock.with_(base=(58, 54, 62), faces={"front": rock.with_(add=[
    rect(0.05, 0.0, 0.95, 0.3, (30, 24, 24)), rect(0.18, 0.0, 0.24, 0.3, (200, 190, 170)),
    rect(0.40, 0.0, 0.46, 0.3, (200, 190, 170)), rect(0.54, 0.0, 0.60, 0.3, (200, 190, 170)),
    rect(0.76, 0.0, 0.82, 0.3, (200, 190, 170))])})
tooth = Mat((214, 204, 182), noise=0.04, mottle=(170, 160, 140), mottle_size=2, bevel=0.3)
eye = Mat((255, 214, 60), noise=0.04, shade=0, bevel=0.15, mottle=LAVA, mottle_amt=0.5, mottle_size=2)
glow = Mat(LAVA, noise=0.06, shade=0, bevel=0.1, mottle=HOT, mottle_amt=0.7, mottle_size=2)

skull = c.part("skull", pivot=(0, 9, 0),
               anim=[bob(1.5, 1.6, when="moving", wave="abs"), pulse(0.04, 0.7), pulse(0.07, 1.6, when="moving")])
skull.box((-8, -3, -8), (8, 7, 8), rock, faces={"front": face, "top": top})
skull.box((-7, 7, -7), (7, 8, 7), top, skip=("bottom",))          # chamfered crown
skull.box((-9, -1, -5), (-8, 4, 3), cheek)                        # temple plates
skull.box((8, -1, -5), (9, 4, 3), cheek)
skull.box((-6.5, -6, -4), (-3.5, -3, 8), cheek, skip=("top",))     # cheek blocks frame the open mouth
skull.box((3.5, -6, -4), (6.5, -3, 8), cheek, skip=("top",))
skull.box((-3.5, -6, -4), (3.5, -3, -1), rock)                    # back of the mouth
for x in (-3, -1, 1, 3):
    skull.box((x - 0.6, -5.5, 6.5), (x + 0.6, -3, 7.6), tooth)

jaw = c.part("jaw", pivot=(0, -6, -3), parent=skull,
             anim=[swing("x", 14, 1.6, when="moving", wave="snap"), swing("x", 2.5, 0.5), strike("x", 38)])
jaw.box((-6.5, -3, 0), (6.5, 0.5, 11), jaw_m, faces={"top": cheek})
jaw.box((-7.2, -2, 0), (-6.3, 2, 6), cheek)
jaw.box((6.3, -2, 0), (7.2, 2, 6), cheek)

glowp = c.part("glow", pivot=(0, 0, 0), parent=skull, bright=True, anim=[pulse(0.05, 1.5)])
glowp.box((-5.8, 1, 7.6), (-2.2, 4.4, 8.5), eye)
glowp.box((2.2, 1, 7.6), (5.8, 4.4, 8.5), eye)
glowp.box((-3.4, -5.7, -0.8), (3.4, -3.2, 5.5), glow)             # lava inside the mouth
glowp.box((-0.6, 7.1, -2), (0.6, 8.1, 2), glow)
