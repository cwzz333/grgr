"""Полевая пушка: появляется у королевств с порохом вместо катапульты (невидимая стойка-носитель, тег gb_cannon).

Scale 1.5: about 3 blocks long. +Z is the muzzle. The barrel kicks back on Models.trigger (strike).
"""
from lib import *

c = Creature("cannon", id_base=4100, scale=1.5, res=2, hide_carrier=False, death_fx=False, hitbox=(1.6, 1.4))

wood = Mat((128, 84, 46), mottle=(88, 54, 30), mottle_amt=0.55, mottle_size=3, bevel=0.2,
           patterns=[stripes((70, 42, 24), 1, 4, "h", 0.3, 0.8)])
plank = wood.with_(base=(146, 100, 58), add=[plates((6, 3), 0.25, 0.08)])
iron = Mat((62, 66, 76), mottle=(38, 40, 48), mottle_amt=0.5, mottle_size=3, bevel=0.28,
           patterns=[speckle((140, 146, 160), 0.04)])
band = Mat((218, 168, 58), mottle=(156, 108, 30), mottle_size=2, bevel=0.35)
rim = Mat((44, 44, 50), mottle=(24, 24, 28), mottle_size=2, bevel=0.25, patterns=[rivets((170, 174, 184), 3, 1)])
bore = Mat((70, 74, 84), noise=0.02, shade=0, bevel=0.15, patterns=[rect(0.22, 0.22, 0.78, 0.78, (12, 12, 14))])

# ---------------------------------------------------------------- carriage with trail and wheels
car = c.part("carriage", pivot=(0, 0, 0))
for s in (1, -1):
    car.box((s * 2.6, 4.5, -3), (s * 4.2, 9.5, 5), plank)                       # cheeks
    car.box((s * 2.6, 9.5, -1), (s * 4.2, 10.5, 3), band, skip=("bottom",))     # trunnion caps
    car.box((s * 1.2, 3.0, -12), (s * 3.4, 5.6, -3), wood)                      # trail beams
    car.box((s * 1.2, 0.8, -15), (s * 3.4, 3.4, -11), wood, rot=("x", 22.5))
    # wheels: octagon = two crossed boxes, iron tyre and hub
    for ang in (None, ("x", 45)):
        car.box((s * 5.2, 0.8, -4.2), (s * 6.4, 9.2, 4.2), wood.with_(faces={"left": plank, "right": plank}), rot=ang)
    car.box((s * 5.0, 3.8, -1.2), (s * 6.9, 6.2, 1.2), rim)                     # hub
    car.box((s * 5.1, 0, -1.6), (s * 6.5, 0.8, 1.6), rim)                       # tyre touching the ground
    car.box((s * 5.1, 9.2, -1.6), (s * 6.5, 10, 1.6), rim)
    car.box((s * 5.1, 3.4, 4.2), (s * 6.5, 6.6, 5), rim)
    car.box((s * 5.1, 3.4, -5), (s * 6.5, 6.6, -4.2), rim)
car.box((-5.2, 4.2, -0.8), (5.2, 5.8, 0.8), iron)                               # axle
car.box((-3.4, 3.2, -4), (3.4, 4.5, 4), plank)                                  # bed
car.box((-1.4, 0, -16), (1.4, 1.6, -13.5), iron)                                # trail spade
car.box((-3.6, 5.6, -9), (3.6, 6.4, -7.6), band)                                # trail bands
car.box((-3.6, 5.6, -5), (3.6, 6.4, -3.8), band)

# ---------------------------------------------------------------- barrel (raised 10 deg), recoils on fire
gun = c.part("barrel", pivot=(0, 9.5, 1), rot=(-10, 0, 0), anim=[strike("x", -9, 14)])
gun.box((-2.3, -2.3, -6), (2.3, 2.3, 2), iron)                                  # breech
gun.box((-1.9, -1.9, 2), (1.9, 1.9, 11.6), iron)                                # chase
gun.box((-2.5, -2.5, -6.6), (2.5, 2.5, -5.6), band)                             # base ring
gun.box((-1, -1, -8.2), (1, 1, -6.6), band)                                     # cascabel
gun.box((-2.5, -2.5, 1.4), (2.5, 2.5, 2.6), band)                               # reinforce ring
gun.box((-2.2, -2.2, 7), (2.2, 2.2, 8), band)
gun.box((-2.3, -2.3, 11.6), (2.3, 2.3, 13), iron.with_(base=(84, 88, 100), faces={"front": bore}))   # muzzle swell
gun.box((-3.4, -0.9, -0.9), (3.4, 0.9, 0.9), iron)                              # trunnions
gun.box((-0.4, 2.3, -4.6), (0.4, 2.9, -3.8), band, skip=("bottom",))            # touch hole
