# Texture orientation: what Minecraft 1.21.1 really does

Everything here was read from the named 1.21.1 jar (yarn 1.21.1+build.3) with `javap -p -c`, and the corner table
was printed by the game's own classes with `UvProbe.java`. `uvtest.py` checks the generator against it.

**Result: the mapping the pipeline already used is correct. No face is mirrored or upside-down, so `lib.py`,
`gen.py`, `preview.py` and `Models.java` needed no orientation change** (only the checks and these notes are new).

## Reproduce

```sh
JAR=.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/1.21.1-*/minecraft-merged-*.jar
CP="$(ls $JAR):$(find ~/.gradle/caches/modules-2 -name '*.jar' | tr '\n' ':')"
javac -proc:none -cp "$CP" -d /tmp/uvprobe tools/models/UvProbe.java
java -cp "/tmp/uvprobe:$CP" UvProbe > /tmp/uvprobe/probe.txt
python3 tools/models/uvtest.py --probe /tmp/uvprobe/probe.txt
```

## 1. Face vertices and UV corners (rotation 0)

`BakedQuadFactory.packVertexData` takes vertex `i` of a face from `CubeFace.getFace(direction).getCorner(i)`
(each corner picks `from` or `to` per axis through `getPositionMatrix`: `WEST`=from.x, `DOWN`=from.y,
`NORTH`=from.z, `EAST`=to.x, `UP`=to.y, `SOUTH`=to.z) and gives it the UV
`(ModelElementTexture.getU(i), getV(i))`. With `uv: [u0, v0, u1, v1]` and rotation 0 the bytecode of
`getU`/`getV` selects: vertex 0 → (u0, v0), 1 → (u0, v1), 2 → (u1, v1), 3 → (u1, v0). In a PNG, (u0, v0) is the
top-left texel of the rectangle, so vertex 0 = image top-left, 1 = bottom-left, 2 = bottom-right, 3 = top-right.

`UvProbe` output (element corner of every vertex):

| face | v0 = top-left | v1 = bottom-left | v2 = bottom-right | v3 = top-right |
| --- | --- | --- | --- | --- |
| down  | from.x from.y to.z   | from.x from.y from.z | to.x from.y from.z   | to.x from.y to.z   |
| up    | from.x to.y from.z   | from.x to.y to.z     | to.x to.y to.z       | to.x to.y from.z   |
| north | to.x to.y from.z     | to.x from.y from.z   | from.x from.y from.z | from.x to.y from.z |
| south | from.x to.y to.z     | from.x from.y to.z   | to.x from.y to.z     | to.x to.y to.z     |
| west  | from.x to.y from.z   | from.x from.y from.z | from.x from.y to.z   | from.x to.y to.z   |
| east  | to.x to.y to.z       | to.x from.y to.z     | to.x from.y from.z   | to.x to.y from.z   |

So in model space: side faces have +Y at the top of the image and are not mirrored when seen from outside;
`up` has north (−Z) at the top of the image and west (−X) on its left; `down` has south (+Z) at the top and west
on its left. Without an element rotation `BakedQuadFactory.encodeDirection` only snaps positions, it does not
change which UV belongs to which corner. Element `rotation` is applied by `rotateVertex` as a right-handed
rotation around the named axis through `origin`.

## 2. Item display, context "none"

- `DisplayEntityRenderer.render`: `matrices.multiply(rotationYXZ(-yaw°, pitch°, 0))` (fixed billboard), then the
  entity's `transformation` matrix (translation · left_rotation · scale · right_rotation), then the item.
- `ItemDisplayEntityRenderer.render`: `matrices.multiply(RotationAxis.POSITIVE_Y.rotation(π))` — the 180° Y flip.
- `ItemRenderer.renderItem`: `model.getTransformation().getTransformation(mode).apply(...)` — for
  `ModelTransformationMode.NONE` this is `Transformation.IDENTITY` — then `matrices.translate(-0.5, -0.5, -0.5)`.

A model point `p` (0..16) therefore lands at `Ry(-yaw) · T · flipY180(p / 16 − 0.5)`. Yaw 0 looks along +Z
(south) and `Ry(-yaw)` turns +Z to the mob's facing, so in a display entity's local space **+Z is the carrier's
facing and +X is its left**. The generator bakes the flip into the elements (`x' = 8 − x`, `z' = 8 − z`, pivot at
8, 8, 8), which maps creature faces to model faces like this:

| creature face | model face | image top | image left (seen from outside) |
| --- | --- | --- | --- |
| front (+Z) | north | up (+Y) | the creature's right (−X) = the viewer's left |
| back (−Z)  | south | up | the creature's left (+X) = the viewer's left |
| left (+X)  | west  | up | the creature's front (+Z) |
| right (−X) | east  | up | the creature's back (−Z) |
| top (+Y)   | up    | the creature's front | the creature's left |
| bottom (−Y)| down  | the creature's back | the creature's left |

That is exactly "the top of a painted face is up, its left is on the viewer's left from outside"; `top` is seen
from above with the front at the top, `bottom` from below with the back at the top. Because the flip is a 180°
turn around Y, element rotations around `y` keep their angle and rotations around `x`/`z` change sign
(`gen.py`). `uvtest.py` asserts all of this on an off-centre cuboid (marker texel in each image's top-left,
outward winding, rotated elements) and renders `build/uvtest/*.png` with a letter and an up-arrow on every face.

`Models.java` composes `rotateY(ry) · rotateX(rx) · rotateZ(rz)` (JOML, right-handed) per part and sends
translation + left_rotation + uniform scale; `lib.pose` does the same maths, `preview.py` renders it.

## 3. Items in the HEAD slot (`HeadFeatureRenderer.render`)

For an item that is not a skull and not head armour the bytecode does, inside the mob's model space (y down,
1 unit = 16 model px, the mob looks along −Z):

1. `matrices.scale(scaleX, scaleY, scaleZ)` (1, 1, 1 for all vanilla users);
2. baby mobs that are not villagers: `translate(0, 0.03125, 0)`, `scale(0.7)`, `translate(0, 1, 0)`;
3. `getContextModel().getHead().rotate(matrices)` — origin = the head's pivot (the neck);
4. `HeadFeatureRenderer.translate(matrices, villager)` with `villager = entity instanceof VillagerEntity ||
   ZombieVillagerEntity`: `translate(0, -0.25, 0)`, `multiply(POSITIVE_Y.rotationDegrees(180))`,
   `scale(0.625, -0.625, -0.625)`, and for villagers `translate(0, 0.1875, 0)`;
5. `HeldItemRenderer.renderItem(entity, stack, ModelTransformationMode.HEAD, false, ...)` → `ItemRenderer.renderItem`:
   the model's `display.head` (`Transformation.apply`: translate, rotate, scale), then `translate(-0.5)`.

Consequences:

- The item centre (element 8, 8, 8) is 4 model px above the head pivot (0.25 · 16), for villagers and zombie
  villagers 0.1875 · 0.625 · 16 = 1.875 px more.
- One item unit is 0.625 block: **one element px = 0.625 model px**. `display.head.scale = 1.6` makes element px
  equal model px (the limit is 4).
- Item +Y is up, item −Z (north) is the mob's face, item −X (west) is the mob's left — the same orientation the
  generator already bakes for item displays, so a hat part is authored like any other part (+Z = face).
- `display.head.translation` is in 1/16 item units before the scale: `t` model px up = `t · 1.6`.

Head sizes: zombie/skeleton/piglin heads are 8×8×8 (top 8 px above the pivot); villager, zombie villager,
illager and wandering trader heads are 8×10×8 (top 10 px above the pivot). Height of the head's top above the
item centre → `lib.HEAD_TOP`:

| head kind (`lib.head_display(kind)`) | mobs | top above item centre | `display.head` |
| --- | --- | --- | --- |
| `biped` | zombie, skeleton, piglin families | 8 − 4 = 4 px | translation [0, 6.4, 0], scale 1.6 |
| `villager` | villager, zombie villager | 10 − 4 − 1.875 = 4.125 px | translation [0, 6.6, 0], scale 1.6 |
| `tall` | illagers, wandering trader | 10 − 4 = 6 px | translation [0, 9.6, 0], scale 1.6 |

With that display a hat part has y = 0 at the top of the head and the head at x, z −4..4.
`HeadFeatureRenderer` is added by `VillagerEntityRenderer`, `WanderingTraderEntityRenderer`,
`IllagerEntityRenderer` and `BipedEntityRenderer` (zombies, skeletons, piglins, zombie villagers).
`WitchEntityRenderer` and `IronGolemEntityRenderer` have none: `Models.equipHat` returns false for them.

Items worn this way are lit like any held item: there is no per-entity brightness, so "glowing" gems on hats are
only unshaded bright texels, not emissive.
