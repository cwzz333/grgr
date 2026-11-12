"""Дракон: flying boss carried by an invisible phantom (AI off), hitbox 5.6 x 3.1 blocks; breathes fireballs."""
from lib import *

c = Creature("dragon", id_base=1300, scale=3.5, res=2, hide_carrier=True, death_fx=True, hitbox=(5.6, 3.1))

CRIMSON, DARK, GOLD, CREAM = (178, 26, 36), (96, 12, 28), (246, 150, 24), (240, 204, 120)
SIDES = ("front", "back", "left", "right")

scale_m = Mat(CRIMSON, mottle=DARK, mottle_amt=0.5, mottle_size=4, shade=0.14,
              patterns=[scales((120, 14, 30), 4, 0.45), speckle((255, 110, 60), 0.01)])
back = scale_m.with_(base=(196, 34, 40), add=[on("top", stripes((100, 12, 26), 1, 5, "h", 0.5))])
belly = Mat(CREAM, mottle=(214, 150, 80), mottle_amt=0.5, mottle_size=3,
            patterns=[stripes((170, 100, 50), 1, 2, "h", 0.6)])
under_s = scale_m.with_(faces={"bottom": belly})
plate = Mat((255, 170, 40), mottle=(210, 90, 20), mottle_size=2, bevel=0.3, shade=0.1)
horn = Mat((240, 228, 190), noise=0.04, patterns=[stripes((150, 120, 90), 1, 3, "h", 0.5)], bevel=0.25)
bone = Mat((70, 18, 26), mottle=(120, 30, 30), mottle_size=2, bevel=0.25)
membrane = Mat(GOLD, mottle=(214, 92, 18), mottle_amt=0.6, mottle_size=5, shade=0.08, bevel=0.06,
               patterns=[stripes((120, 30, 14), 1, 7, "d", 0.45, wobble=0.5), speckle((255, 220, 90), 0.02)])
membrane_o = membrane.with_(base=(236, 112, 18))
mouth = Mat((60, 8, 20), mottle=(150, 30, 30), mottle_size=2)
tooth = Mat((248, 240, 214), noise=0.03, shade=0.05)
glow = Mat((255, 236, 60), noise=0.03, shade=0, bevel=0,
           faces={f: Mat((255, 240, 80), noise=0, shade=0, bevel=0,
                         patterns=[eye((255, 240, 80), (30, 4, 4), iris=(255, 160, 0), slit=True)])
                  for f in ("front", "left", "right")})

# ---------------------------------------------------------------- body (centre y = 7 px)
body = c.part("body", pivot=(0, 7, 0), anim=[bob(1.0, 0.3), bob(0.5, 0.6, 0.25)])
body.box((-8, -5, -4), (8, 5.5, 8), back, faces={"bottom": belly})                 # chest
body.box((-6.5, -4.5, 8), (6.5, 4.5, 11.5), back, faces={"bottom": belly})         # shoulders
body.box((-6, -4.5, -11), (6, 4.5, -4), back, faces={"bottom": belly})             # hips
body.box((-6, -6.5, -9), (6, -4.5, 9.5), belly, faces={"top": scale_m})             # belly plates
for z in range(-8, 10, 3):                                                         # underside bands
    body.box((-6.4, -6.9, z - 0.3), (6.4, -6.3, z + 0.3), plate)
# spine ridge: big plates on the back
for z, h in ((9, 2.5), (6, 3.5), (3, 4), (0, 4.5), (-3, 4), (-6, 3.2), (-9, 2.5)):
    body.box((-0.9, 5.5, z - 1.4), (0.9, 5.5 + h, z + 1.4), plate, skip=("bottom",))
    body.box((-2.5, 5.2, z - 1.0), (-1.0, 5.2 + h * 0.4, z + 1.0), plate, skip=("bottom",))
    body.box((1.0, 5.2, z - 1.0), (2.5, 5.2 + h * 0.4, z + 1.0), plate, skip=("bottom",))
# fore legs, tucked
for s in (1, -1):
    body.box((s * 5, -9, 5), (s * 8, -4, 8.5), scale_m)
    body.box((s * 5.2, -9.5, 7.5), (s * 7.8, -8, 11), horn)
    body.box((s * 5.4, -7.5, 9.2), (s * 7.6, -5.5, 10.6), scale_m)

# ---------------------------------------------------------------- neck: 3 segments + head
neck_rot = [(-28, 0, 0), (14, 0, 0), (14, 0, 0)]
neck_piv = [(0, 1.5, 10.5), (0, 0, 6), (0, 0, 6)]
prev, neck = body, []
for i in range(3):
    n = c.part("neck%d" % i, pivot=neck_piv[i], parent=prev, rot=neck_rot[i],
               anim=[sway("y", 5, 0.4, i, 0.16), sway("x", 3, 0.3, i, 0.18), strike("x", 9, 10)])
    w = 3.6 - 0.2 * i
    n.box((-w, -w, -1), (w, w, 6.5), scale_m, faces={"bottom": belly})
    n.box((-1, w, 0.5), (1, w + 2.4, 4.6), plate, skip=("bottom",))
    prev = n
    neck.append(n)

head = c.part("head", pivot=(0, 0, 6), parent=prev, rot=(8, 0, 0), anim=[sway("y", 4, 0.4, 3, 0.16), strike("x", 14, 10)])
head.box((-4.5, -2.5, -1), (4.5, 3.5, 7), scale_m)                                   # skull
head.box((-3.2, -2, 7), (3.2, 2.5, 13.5), scale_m)                                   # snout
head.box((-3.3, 2.5, 8), (3.3, 3.2, 13), plate)                                      # nose ridge
for s in (1, -1):
    head.box((s * 1.0, 2.5, 12.6), (s * 2.2, 3.5, 13.8), bone)  # nostrils
    head.box((s * 3.6, 3.2, 1.5), (s * 5.4, 4.6, 6.5), horn)                         # brow ridge
    head.box((s * 3, 2.5, -8.5), (s * 5, 4.6, 0.5), horn, rot=("x", -22.5))          # main horn, swept back
    head.box((s * 4.5, -1.5, -2.5), (s * 7, 0.5, 1), horn, rot=("y", s * 22.5))      # cheek spike
    for z in (9, 12):
        head.box((s * 2.5, -3.6, z - 0.6), (s * 3.3, -2, z + 0.6), tooth)            # upper fangs
head.box((-0.8, 3.5, -2), (0.8, 6.5, 1.5), plate, skip=("bottom",))                  # crest
jaw = c.part("jaw", pivot=(0, -2.3, 0.5), parent=head,
             anim=[swing("x", 7, 0.35, wave="snap"), strike("x", 40, 10)])
jaw.box((-3.8, -1.5, 0), (3.8, 1, 7), scale_m, faces={"bottom": belly})
jaw.box((-2.7, -1.2, 7), (2.7, 1, 13), scale_m, faces={"bottom": belly})
jaw.box((-2.5, 1, 1), (2.5, 1.3, 12.5), mouth)
for s in (1, -1):
    jaw.box((s * 2.0, 1, 11.5), (s * 2.9, 3.2, 12.7), tooth)
    jaw.box((s * 2.9, 1, 5), (s * 3.6, 2.4, 6), tooth)
eyes = c.part("eyes", pivot=(0, 0, 0), parent=head, bright=True)
for s in (1, -1):
    eyes.box((s * 2.4, 3.4, 3.2), (s * 4.7, 4.4, 6.4), glow)
    eyes.box((s * 4.4, 0.8, 3.2), (s * 4.9, 3.4, 6.4), glow)

# ---------------------------------------------------------------- tail: 4 tapering segments + spade
tail_len, tail_w = (7.5, 7, 6.5, 6), (3.4, 2.8, 2.2, 1.6)
prev, off = body, (0, -0.5, -10.5)
for i in range(4):
    t = c.part("tail%d" % i, pivot=off, parent=prev, rot=(-3 if i else 4, 0, 0), anim=[sway("y", 8, 0.45, i, 0.15)])
    w, L = tail_w[i], tail_len[i]
    t.box((-w, -w * 0.8, -L - 0.5), (w, w * 0.8, 0.5), scale_m, faces={"bottom": belly})
    t.box((-0.8, w * 0.8, -L + 1), (0.8, w * 0.8 + 2.4 - 0.3 * i, -1), plate, skip=("bottom",))
    prev, off = t, (0, 0, -L)
tip = c.part("tail_tip", pivot=off, parent=prev, rot=(-3, 0, 0), anim=[sway("y", 8, 0.45, 4, 0.15)])
tip.box((-1, -1, -3), (1, 1, 0.5), scale_m)
tip.box((-4.2, -0.5, -9), (4.2, 0.5, -3), plate)                       # spade
tip.box((-2.6, -0.5, -11), (2.6, 0.5, -9), plate)
tip.box((-1, -0.7, -13.5), (1, 0.7, -11), horn)
for s in (1, -1):
    tip.box((s * 3.5, -0.5, -7), (s * 6, 0.5, -4.5), plate, rot=("y", s * 45))

# ---------------------------------------------------------------- hind legs
for s, side in ((1, "l"), (-1, "r")):
    th = c.part("hind_" + side, pivot=(s * 7, -2, -6.5), parent=body, rot=(-35, 0, 0), anim=[swing("x", 3, 0.4, 0.2 * (s > 0))])
    th.box((s * -2.5, -2.5, -4), (s * 2.5, 3.5, 3), scale_m)                         # thigh
    shin = c.part("shin_" + side, pivot=(0, -2.5, -0.5), parent=th, rot=(75, 0, 0))
    shin.box((s * -1.8, -1.8, -1.5), (s * 1.8, 1.4, 8), scale_m)
    shin.box((s * -2.4, -2.2, 6.5), (s * 2.4, 0.6, 10.5), horn)                      # foot
    for x in (-1.6, 0, 1.6):
        shin.box((x - 0.5, -2.0, 10), (x + 0.5, -0.4, 12.5), horn)

# ---------------------------------------------------------------- wings: inner + outer, both flap around Z
for s, side in ((1, "l"), (-1, "r")):
    rot = (0, 6, 9)
    inner = c.part("wing_" + side, pivot=(s * 6, 4, 2), parent=body, rot=rot if s > 0 else mirror_rot(rot),
                   anim=[flap(s * 20, 0.5), flap(s * 3, 1.0, 0.2)])
    inner.box((0, -2.5, -4.5), (s * 8, 2, 3), scale_m)                               # shoulder muscle
    inner.box((0, -1.4, -1.4), (s * 17, 1.4, 1.4), bone)                             # leading arm
    inner.box((s * 1, -0.5, -20), (s * 17, 0.5, -1.4), membrane)
    for x, zz in ((4.5, -17), (10, -18.5), (15, -19)):                                 # finger struts
        inner.box((s * x - 0.5, 0.5, zz), (s * x + 0.5, 1.4, -1.2), bone)
    inner.box((s * 15.5, 1.4, -1.2), (s * 17.5, 5.2, 1.2), horn)                     # wing thumb claw
    outer = c.part("wingtip_" + side, pivot=(s * 17, 0, 0), parent=inner,
                   rot=(0, 18, 0) if s > 0 else mirror_rot((0, 18, 0)),
                   anim=[flap(s * 16, 0.5, -0.14), flap(s * 5, 1.0, 0.05)])
    outer.box((0, -1.1, -1.1), (s * 20, 1.1, 1.1), bone)
    for x0, x1, z0 in ((0, 7, -19), (7, 13, -17), (13, 18, -12), (18, 20.5, -6)):
        outer.box((s * x0, -0.5, z0), (s * x1, 0.5, -1.1), membrane_o)
    for x, zz in ((3.5, -17), (9.5, -15), (15, -11)):
        outer.box((s * x - 0.5, 0.5, zz), (s * x + 0.5, 1.3, -1.0), bone)
    outer.box((s * 19, -0.9, -1.0), (s * 23, 0.9, 1.0), horn)
