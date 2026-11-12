# GodBox custom models

Creatures are built from `item_display` entities. Every part is the item `godbox:model` with a
`custom_model_data` id that selects a generated block-style model; the server poses the parts
(`Models.java`) and the client interpolates. Everything is generated from the Python definitions here.

```
tools/models/lib.py            definition API, texture painters, pose maths
tools/models/gen.py            generator + validator
tools/models/preview.py        software renderer (the only way to see a model without the game)
tools/models/creatures/*.py    one file per creature (file name = creature name)
tools/models/uvtest.py         proves the texture orientation against the game's tables (see UV_NOTES.md)
tools/models/UvProbe.java      dumps those tables from the real Minecraft jar
```

Generated (commit them): `src/main/resources/assets/godbox/models/item/model.json` (overrides),
`models/item/m/<creature>/<part>.json`, `textures/item/m/<creature>.png`,
`src/main/java/com/vladik/godbox/ModelParts.java`. After merging branches just re-run `gen.py`.

## Commands

```sh
pip3 install pillow numpy
python3 tools/models/gen.py                                   # all creatures -> project
python3 tools/models/preview.py crab                          # -> tools/models/build/preview/
# private iteration that does not touch the project outputs:
python3 tools/models/gen.py --only mycreature --out /tmp/me
python3 tools/models/preview.py mycreature --out /tmp/me --dest /tmp/me/preview --anim
```

`preview.py` writes `<name>_front34|side|top|front|god.png`, `<name>_textures.png` and with `--anim`
a strip of walk phases + the attack pose. `--t 0.4 --moving --strike 3` renders one animation phase.
The yellow box is the carrier's hitbox, ground squares are 1 block. `gen.py` validates every output
(JSON parses, coordinates within -16..32, legal element rotations, UVs, files exist, unique ids).

## Coordinates

Creature space: **+Z forward, +Y up (0 = the mob's feet), +X = the creature's left**. Units are model
pixels: 1 px = `scale / 16` blocks. A part may reach at most 24 px from its pivot in any direction
(Minecraft's -16..32 element limit) - split bigger shapes into several parts.

## Definition API (`from lib import *`)

```python
c = Creature("crab", id_base=1000, scale=3.0, res=2, hide_carrier=True, death_fx=True, hitbox=(5.85, 6.6))
body = c.part("body", pivot=(0, 16, 0), anim=[bob(0.4, 0.35)])
body.box((-17, -3, -12), (17, 3, 12), shell)
arm = c.part("arm_l", pivot=(12, -1, 9), parent=body, rot=(-10, 42, 0), scale=1.3, anim=[swing("y", 5, 0.3)])
```

- `Creature(name, id_base, scale=1.0, res=1, hide_carrier=True, death_fx=False, hitbox=None)` -
  `scale`: blocks per 16 px (2-4 for bosses); `res`: texels per px (2-3 for bosses so they stay detailed);
  `hide_carrier=False` for accessories on a visible mob; `hitbox=(w, h)` blocks, preview only.
- `c.part(name, pivot=(x,y,z), parent=None, rot=(rx,ry,rz), scale=1.0, bright=False, anim=[...])` - one
  display entity. `pivot` in px: creature space for root parts, relative to the parent's pivot (in the
  parent's rotated frame) for children. `rot` in degrees, applied yaw(Y) then pitch(X) then roll(Z),
  right-handed (`+ry` turns +Z towards +X, `+rz` turns +X towards +Y, `+rx` turns +Z down).
  `scale` and all motion are inherited by children. `bright=True` = full-bright, unshaded (eyes, lights).
  Use `mirror_rot(rot)` for the same part on the other side. Parents must be created first.
- `part.box(frm, to, tex, faces=None, skip=(), rot=None, origin=None)` - a cuboid in px relative to the
  part's pivot. `faces={"front": mat}` overrides single faces, `skip=("bottom",)` drops hidden ones,
  `rot=("y", 45)` tilts the box (only -45/-22.5/22.5/45, one axis) around `origin` (default: its centre).
  Face names: `front back left right top bottom` (creature directions).
- `Mat(base, noise=0.07, mottle=None, mottle_amt=0.6, mottle_size=5, shade=0.12, bevel=0.16, edge=0,
  patterns=[], faces={})` - procedural surface, seeded deterministically. `mat.with_(base=..., add=[...])`
  makes a variant. Patterns: `spots(color, density, size, alpha)`, `speckle(color, density)`,
  `stripes(color, width, gap, axis "v"|"h"|"d", alpha, wobble)`, `scales(color, size)`,
  `plates(size, seam, highlight)`, `rivets(color, spacing, margin)`, `gradient(top, bottom, alpha)`,
  `band(color, at, size)`, `rect(x0, y0, x1, y1, color)` (face fractions), `eye(sclera, pupil, iris, slit=)`,
  `on(faces, pattern)`; any `callable(img, ctx)` works too (img: float h x w x 3, ctx: face/w/h/res/rng).
  Face images are true outside views: side faces have +Y at the top, `top` has the creature's front at
  the top of the image, `bottom` its back.

## Worn parts (hats)

`c.part(..., display=head_display("tall"))` writes an item `display` block into the part's model, and
`part.variant(name, display)` exports the same geometry under another id with another display (a child model, no
extra texture space). `creatures/hats.py` uses this: every hat exists as `<hat>_biped`, `<hat>_villager` and
`<hat>_tall`, and `Models.equipHat(mob, "<hat>")` puts the right one into the HEAD slot (drop chance 0); the
vanilla `HeadFeatureRenderer` draws it, the mob keeps its vanilla model and animation. In a hat part y = 0 is the
top of the head, the head is x, z -4..4, +Z is the face; 1 px = 1 px of the mob's model. `c.ghost(frm, to, color)`
adds a preview-only box (the dummy heads). The transforms are derived in `UV_NOTES.md`.

## Animation vocabulary

Angles in degrees, `speed` in cycles per second, `phase` in cycles. `when`: `"always"`, `"moving"`
(fades in while the carrier walks, uses a walk clock) or `"idle"`. `wave`: `"sin"`, `"pos"` (only the
positive half - leg lift), `"abs"`, `"snap"` (0..1: opens slowly, shuts fast - pincers, jaws).

| call | effect |
| --- | --- |
| `swing(axis, amp, speed=1, phase=0, when="always", wave="sin")` | rotate around a local axis: legs, arms, jaws |
| `flap(amp, speed=1, phase=0, axis="z")` | wings (negative `amp` on the mirrored wing, phase-lag the tips) |
| `sway(axis, amp, speed=0.5, index=k, lag=0.12)` | tails/tentacles: segment `k` is parented to `k-1` |
| `bob(amp_px, speed=0.5, phase=0, when=, wave=, axis="y")` | translate |
| `spin(axis, turns_per_second)` | continuous rotation (keep below 2 turns/s) |
| `pulse(amp, speed=0.5, phase=0)` | scale breathing |
| `strike(axis, angle, ticks=10)` | one-shot attack pose, fired by `Models.trigger(carrier)` |

A part can have several descriptors; they are applied in order on top of the base rotation.
Alternating gait: give neighbouring legs `phase` 0 / 0.5 and the opposite side +0.5.

## Ids

`id_base` is the creature's first `custom_model_data`; it owns `id_base .. id_base+99` and part `i`
gets `id_base + i`. Pick a free multiple of 100 (crab = 1000) and never change it; `gen.py` refuses
overlapping ranges.

## Java side

```java
Models.attach(carrier, "crab");        // full model; hides the carrier if hide_carrier
Models.attachExtra(carrier, "crown");  // accessory on a visible mob
Models.trigger(carrier);               // strike() poses
Models.detach(carrier);
```

The carrier gets the tag `godbox_model_<name>` (`godbox_extra_<name>`), parts are never saved to disk
and are rebuilt from the tags after a reload (scan in `BossModels.tick`). Bosses go through
`BossModels.attach`; with `models.custom=false` in `config/godbox.properties` they fall back to the old
block-display models. `Models.pose` must stay in sync with `lib.pose`.
