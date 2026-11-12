#!/usr/bin/env python3
"""Proves that lib.py / gen.py / preview.py map textures the way Minecraft 1.21.1 does. See UV_NOTES.md.

An asymmetric, off-centre test cuboid is generated with the real generator. Every face carries a marker in the
top-left texel of its painted image. The script then places the baked model in the world with the rules read from
the game's bytecode (GAME_CORNERS below is the output of UvProbe.java run against the real jar) and checks, for
each face, that
  * it lies on the creature side it is named after (front = +Z, left = +X, top = +Y ...),
  * the top edge of the painted image is up (+Y; for `top` the creature's front, for `bottom` its back),
  * the left edge of the image is on the viewer's left when looking at the face from outside,
  * the top-left texel of the atlas rectangle really is the marker,
  * element rotations turn the same way in creature space as box(rot=...) promises,
  * preview.py uses exactly the game's corner table.

  python3 tools/models/uvtest.py                    # checks + renders tools/models/build/uvtest/*.png
  python3 tools/models/uvtest.py --probe probe.txt  # also compare GAME_CORNERS with a fresh UvProbe dump
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen  # noqa: E402
import lib  # noqa: E402
import preview  # noqa: E402

# UvProbe.java output (net.minecraft.client.render.model.CubeFace + ModelElementTexture, rotation 0):
# face -> 4 vertices in order, each (x, y, z) selecting 0 = `from` / 1 = `to`; vertex i gets UV_OF_VERTEX[i].
GAME_CORNERS = {
    "down": ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)),
    "up": ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
    "north": ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)),
    "south": ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
    "west": ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)),
    "east": ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
}
UV_OF_VERTEX = ((0, 0), (0, 1), (1, 1), (1, 0))  # (u index, v index): 0 = u0/v0 (image left/top), 1 = u1/v1

# what the pipeline promises (README): outward normal and the world direction of the image's top edge
NORMAL = {"front": (0, 0, 1), "back": (0, 0, -1), "left": (1, 0, 0), "right": (-1, 0, 0), "top": (0, 1, 0), "bottom": (0, -1, 0)}
IMAGE_UP = {"front": (0, 1, 0), "back": (0, 1, 0), "left": (0, 1, 0), "right": (0, 1, 0), "top": (0, 0, 1), "bottom": (0, 0, -1)}
MARKER = (255, 0, 255)
LETTER = {"front": "F", "back": "B", "left": "L", "right": "R", "top": "T", "bottom": "U"}
COLOR = {"front": (200, 60, 50), "back": (60, 90, 200), "left": (60, 170, 80), "right": (220, 190, 60),
         "top": (230, 230, 230), "bottom": (90, 60, 110)}


def game_to_creature(v):
    """Element coordinates (0..16 space) -> creature px, as the game does for an item_display, context `none`:
    ItemRenderer.renderItem translates by -0.5 (blocks), ItemDisplayEntityRenderer turns 180 deg around Y."""
    p = np.asarray(v, dtype=float) / 16.0 - 0.5
    p[0], p[2] = -p[0], -p[2]
    return p * 16.0


def face_mat(face):
    def paint(img, ctx):
        img[:] = lib.rgb(COLOR[face])
        im = Image.new("L", (ctx.w, ctx.h), 0)
        d = ImageDraw.Draw(im)
        d.text((2, 1), LETTER[face], fill=255)
        d.polygon([(ctx.w - 2, 1), (ctx.w - 6, 7), (ctx.w - 2, 7)], fill=255)  # arrow head pointing at the top edge
        d.line([(ctx.w - 4, 3), (ctx.w - 4, ctx.h - 3)], fill=255)
        big = np.asarray(im) > 127
        img[big] = (20, 20, 20)
        img[0, 0] = MARKER
    return lib.Mat(COLOR[face], noise=0, shade=0, bevel=0, patterns=[paint])


def build():
    lib.REGISTRY.clear()
    c = lib.Creature("uvtest", id_base=9000, scale=1.0, res=4)
    p = c.part("cube", pivot=(0, 8, 0))
    mats = {f: face_mat(f) for f in lib.FACES}
    p.box((1, -5, -3), (7, 5, 9), mats["front"], faces=mats)            # off-centre, all sides different
    q = c.part("tilted", pivot=(-10, 8, 0))
    q.box((-2, -2, 0), (2, 2, 8), mats["front"], faces=mats, rot=("y", 22.5), origin=(0, 0, 0))
    r = c.part("tilted_x", pivot=(-10, 20, 0))
    r.box((-2, -2, 0), (2, 2, 8), mats["front"], faces=mats, rot=("x", 22.5), origin=(0, 0, 0))
    s = c.part("tilted_z", pivot=(10, 22, 0))
    s.box((0, -2, -2), (8, 2, 2), mats["front"], faces=mats, rot=("z", 22.5), origin=(0, 0, 0))
    atlas, models, rig = gen.build_creature(c)
    lib.REGISTRY.clear()
    return c, np.asarray(atlas), models, rig


def element_vertices(el, face):
    ft = (np.array(el["from"], dtype=float), np.array(el["to"], dtype=float))
    v = np.array([[ft[c[0]][0], ft[c[1]][1], ft[c[2]][2]] for c in GAME_CORNERS[face]])
    rot = el.get("rotation")
    if rot:  # BakedQuadFactory.rotateVertex: right-handed rotation around the named axis through `origin`
        o = np.array(rot["origin"], dtype=float)
        v = (v - o) @ preview.rot_matrix(rot["axis"], rot["angle"]).T + o
    return np.array([game_to_creature(x) for x in v])


def check():
    errs = []
    c, atlas, models, rig = build()
    if preview.CORNERS != GAME_CORNERS:
        errs.append("preview.CORNERS differs from the game's CubeFace table")
    box = c.parts[0].boxes[0]
    el = models["cube"]["elements"][0]
    rows = []
    for face in lib.FACES:
        mf = lib.MODEL_FACE[face]
        fd = el["faces"][mf]
        v = element_vertices(el, mf) - np.array([0, 0, 0])  # creature px relative to the part pivot (element 8,8,8)
        n = np.array(NORMAL[face], dtype=float)
        axis = int(np.argmax(np.abs(n)))
        want = (box.to if n[axis] > 0 else box.frm)[axis]
        if not np.allclose(v[:, axis], want):
            errs.append("%s: face is at %s=%s, expected %s" % (face, "xyz"[axis], v[0, axis], want))
        top_left, bottom_left, top_right = v[0], v[1], v[3]  # UV_OF_VERTEX: 0 = (u0,v0), 1 = (u0,v1), 3 = (u1,v0)
        up = (top_left - bottom_left) / np.linalg.norm(top_left - bottom_left)
        right = (top_right - top_left) / np.linalg.norm(top_right - top_left)
        if not np.allclose(up, IMAGE_UP[face]):
            errs.append("%s: image top points to %s, expected %s" % (face, up, IMAGE_UP[face]))
        # looking at the face from outside: view direction d = -n, screen right = d x up
        if not np.allclose(right, np.cross(-n, np.array(IMAGE_UP[face], dtype=float))):
            errs.append("%s: image is mirrored (its left edge is on the viewer's right)" % face)
        # vertex winding must face outwards (counter-clockwise seen from outside), or the game culls the quad
        if not np.allclose(np.cross(v[1] - v[0], v[3] - v[0]) / np.linalg.norm(np.cross(v[1] - v[0], v[3] - v[0])), n):
            errs.append("%s: quad winding faces inwards" % face)
        side = atlas.shape[0]
        px = atlas[int(round(fd["uv"][1] / 16 * side)), int(round(fd["uv"][0] / 16 * side)), :3]
        if tuple(int(x) for x in px) != MARKER:
            errs.append("%s: atlas texel at (u0, v0) is not the image's top-left marker" % face)
        rows.append((face, mf, tuple(int(x) for x in np.rint(top_left)), up, right))
    # element rotations: box(rot=(axis, +22.5)) must turn right-handed around that creature axis
    for part, axis, tip in (("tilted", "y", (0, 0, 8)), ("tilted_x", "x", (0, 0, 8)), ("tilted_z", "z", (8, 0, 0))):
        el = models[part]["elements"][0]
        mf = lib.MODEL_FACE["front" if axis != "z" else "left"]
        centre = element_vertices(el, mf).mean(axis=0)
        want = preview.rot_matrix(axis, 22.5) @ np.array(tip, dtype=float)
        if not np.allclose(centre, want, atol=1e-6):
            errs.append("element rotation around %s: face centre %s, expected %s" % (axis, centre, want))
    return errs, rows, (c, atlas, models, rig)


def compare_probe(path):
    """Compare GAME_CORNERS / UV_OF_VERTEX with a UvProbe dump: lines `face i from.x to.y from.z u0 v1`."""
    errs, seen = [], 0
    for line in open(path):
        t = line.split()
        if len(t) != 7 or t[0] not in GAME_CORNERS:
            continue
        seen += 1
        i = int(t[1])
        sel = tuple(0 if s.startswith("from") else 1 for s in t[2:5])
        if sel != GAME_CORNERS[t[0]][i] or (int(t[5][1]), int(t[6][1])) != UV_OF_VERTEX[i]:
            errs.append("probe mismatch: " + line.strip())
    if seen != 24:
        errs.append("probe file has %d of 24 vertex lines" % seen)
    return errs


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--probe", help="output of UvProbe.java to compare the embedded table with")
    ap.add_argument("--dest", default=os.path.join(HERE, "build", "uvtest"))
    args = ap.parse_args()
    errs, rows, (c, atlas, models, rig) = check()
    if args.probe:
        errs += compare_probe(args.probe)
    print("%-7s %-6s %-22s %-14s %s" % ("face", "model", "image top-left (px)", "image up", "image right"))
    names = {(1, 0, 0): "+X left", (-1, 0, 0): "-X right", (0, 1, 0): "+Y up", (0, -1, 0): "-Y down",
             (0, 0, 1): "+Z front", (0, 0, -1): "-Z back"}
    for face, mf, tl, up, right in rows:
        print("%-7s %-6s %-22s %-14s %s" % (face, mf, tl, names[tuple(int(round(x)) for x in up)],
                                           names[tuple(int(round(x)) for x in right)]))
    os.makedirs(args.dest, exist_ok=True)
    poses = lib.pose(rig)
    quads = preview.build_quads(rig, [models[p["name"]] for p in rig["parts"]], poses)
    pts = np.concatenate([q[0] for q in quads])
    bounds = (pts.min(axis=0), pts.max(axis=0))
    quads += preview.ground_quads(*bounds)
    views = {"front_left_top": ((-1.0, -0.75, -1.0), (0, 1, 0)), "back_right_top": ((1.0, -0.75, 1.0), (0, 1, 0)),
             "below_front": ((-0.6, 0.9, -1.0), (0, 1, 0))}
    for name, view in views.items():
        img = preview.label(preview.render(quads, atlas, view, 560, bounds), "uvtest " + name)
        img.save(os.path.join(args.dest, "uvtest_%s.png" % name))
    print("renders ->", os.path.relpath(args.dest))
    for e in errs:
        print("FAIL", e)
    if errs:
        raise SystemExit("%d check(s) failed" % len(errs))
    print("ok: all faces upright, unmirrored and on the named side; element rotations and preview table agree")


if __name__ == "__main__":
    main()
