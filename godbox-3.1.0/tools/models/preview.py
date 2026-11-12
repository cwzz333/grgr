#!/usr/bin/env python3
"""Software renderer for generated GodBox models (the game is not needed).

It reads the generated model JSON + PNG + rig and poses the parts with the same maths as Models.java,
following Minecraft's conventions: item-display flip, element face vertex order, UV rotation 0.

  python3 tools/models/preview.py crab                       # project build -> tools/models/build/preview
  python3 tools/models/preview.py crab --out /tmp/me --dest /tmp/me/preview
  python3 tools/models/preview.py crab --t 0.4 --moving      # an animation phase
  python3 tools/models/preview.py crab --anim                # also a strip of 4 walk phases
"""
import argparse
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
import lib  # noqa: E402

SS = 2  # supersampling

# Minecraft's quad corners per face as (x, y, z) selectors into (from, to); the corners carry the
# UVs (u0,v0), (u0,v1), (u1,v1), (u1,v0) in this order (net.minecraft.client.render.model.CubeFace).
CORNERS = {
    "down": ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)),
    "up": ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
    "north": ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)),
    "south": ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
    "west": ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)),
    "east": ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
}

VIEWS = {  # name: (camera forward, up hint)
    "front34": ((-1.0, -0.75, -1.0), (0, 1, 0)),
    "side": ((-1.0, -0.12, 0.0), (0, 1, 0)),
    "top": ((0.0, -1.0, 0.0), (0, 0, 1)),
    "front": ((0.0, -0.12, -1.0), (0, 1, 0)),
    "god": ((0.45, -1.0, 0.55), (0, 1, 0)),
}


def rot_matrix(axis, deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def load(out, name):
    if out:
        assets, rig_dir = os.path.join(out, "assets", "godbox"), os.path.join(out, "rig")
    else:
        assets = os.path.join(ROOT, "src", "main", "resources", "assets", "godbox")
        rig_dir = os.path.join(HERE, "build", "rig")
    rig = json.load(open(os.path.join(rig_dir, name + ".json")))

    def path(kind, ident, ext):
        return os.path.join(assets, kind, ident.split(":", 1)[1] + ext)

    models = [json.load(open(path("models", p["model"], ".json"))) for p in rig["parts"]]
    # variants (child models that only add a display transform) are not drawn a second time
    models = [m if "elements" in m else {"elements": []} for m in models]
    tex = np.asarray(Image.open(path("textures", rig["texture"], ".png")).convert("RGBA"))
    return rig, models, tex


def build_quads(rig, models, poses):
    """World-space quads: (4x3 verts, uv rect, fullbright)."""
    quads = []
    for part, model, (pos, quat, scale) in zip(rig["parts"], models, poses):
        for el in model["elements"]:
            ft = (np.array(el["from"], dtype=float), np.array(el["to"], dtype=float))
            rot = el.get("rotation")
            for face, fd in el["faces"].items():
                v = np.array([[ft[c[0]][0], ft[c[1]][1], ft[c[2]][2]] for c in CORNERS[face]])
                if rot:
                    o = np.array(rot["origin"], dtype=float)
                    v = (v - o) @ rot_matrix(rot["axis"], rot["angle"]).T + o
                # ItemRenderer: translate(-0.5); ItemDisplayEntityRenderer: rotate 180 deg around Y
                v = v / 16.0 - 0.5
                v[:, 0], v[:, 2] = -v[:, 0], -v[:, 2]
                # display transformation: translation * left_rotation * scale
                w = np.array([pos + lib.quat_rot(quat, p * scale) for p in v])
                quads.append((w, fd["uv"], part["bright"] or el.get("shade") is False, None))
    return quads


def ghost_quads(rig):
    """Preview-only flat boxes (Creature.ghost): creature px -> blocks, same axes as the parts."""
    quads, k = [], rig["scale"] / 16.0
    for frm, to, col in rig.get("ghosts", ()):
        ft = (np.array(frm) * k, np.array(to) * k)
        for face, corners in CORNERS.items():
            v = np.array([[ft[c[0]][0], ft[c[1]][1], ft[c[2]][2]] for c in corners])
            n = np.cross(v[1] - v[0], v[3] - v[0])
            n /= np.linalg.norm(n)
            shade = min(1.0, n[0] ** 2 * 0.6 + n[1] ** 2 * (3 + n[1]) / 4 + n[2] ** 2 * 0.8)
            quads.append((v, None, True, tuple(int(x * shade) for x in col)))
    return quads


def ground_quads(lo, hi):
    quads = []
    for x in range(int(math.floor(lo[0])) - 2, int(math.ceil(hi[0])) + 2):
        for z in range(int(math.floor(lo[2])) - 2, int(math.ceil(hi[2])) + 2):
            col = (104, 142, 74) if (x + z) % 2 == 0 else (94, 131, 66)
            v = np.array([[x, 0, z], [x, 0, z + 1], [x + 1, 0, z + 1], [x + 1, 0, z]], dtype=float)
            quads.append((v, None, True, col))
    return quads


def basis(view):
    f = np.array(view[0], dtype=float)
    f /= np.linalg.norm(f)
    r = np.cross(f, np.array(view[1], dtype=float))
    r /= np.linalg.norm(r)
    return r, np.cross(r, f), f


def render(quads, tex, view, size, bounds, hitbox=None):
    r, u, f = basis(view)
    corners = np.array([[bounds[i][0], bounds[j][1], bounds[k][2]] for i in (0, 1) for j in (0, 1) for k in (0, 1)])
    cx, cy = corners @ r, corners @ u
    span = max(cx.max() - cx.min(), cy.max() - cy.min()) + 1.5
    w = h = size * SS
    ppb = w / span
    mx, my = (cx.max() + cx.min()) / 2, (cy.max() + cy.min()) / 2

    def project(p):
        return np.stack([(p @ r - mx) * ppb + w / 2, h / 2 - (p @ u - my) * ppb, p @ f], axis=-1)

    color = np.empty((h, w, 3))
    color[:] = (150, 190, 232)
    depth = np.full((h, w), np.inf)
    th, tw = tex.shape[:2]
    for verts, uv, bright, flat in quads:
        n = np.cross(verts[1] - verts[0], verts[3] - verts[0])
        ln = np.linalg.norm(n)
        if ln < 1e-12:
            continue
        n /= ln
        if n @ f >= -1e-6:  # back face (the game culls them too)
            continue
        s = project(verts)
        p0, e1, e2 = s[0], s[3] - s[0], s[1] - s[0]
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            continue
        x0, x1 = int(max(0, math.floor(s[:, 0].min()))), int(min(w, math.ceil(s[:, 0].max()) + 1))
        y0, y1 = int(max(0, math.floor(s[:, 1].min()))), int(min(h, math.ceil(s[:, 1].max()) + 1))
        if x0 >= x1 or y0 >= y1:
            continue
        yy, xx = np.mgrid[y0:y1, x0:x1]
        dx, dy = xx + 0.5 - p0[0], yy + 0.5 - p0[1]
        a = (dx * e2[1] - dy * e2[0]) / det
        b = (e1[0] * dy - e1[1] * dx) / det
        z = p0[2] + a * e1[2] + b * e2[2]
        inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1) & (z < depth[y0:y1, x0:x1])
        if not inside.any():
            continue
        if flat is not None:
            rgb = np.empty(a.shape + (3,))
            rgb[:] = flat
            alpha = np.ones(a.shape, dtype=bool)
        else:
            u0, v0, u1, v1 = (uv[0] / 16 * tw, uv[1] / 16 * th, uv[2] / 16 * tw, uv[3] / 16 * th)
            tx = np.clip(np.floor(u0 + a * (u1 - u0)), min(u0, u1), max(u0, u1) - 1).astype(int)
            ty = np.clip(np.floor(v0 + b * (v1 - v0)), min(v0, v1), max(v0, v1) - 1).astype(int)
            texel = tex[np.clip(ty, 0, th - 1), np.clip(tx, 0, tw - 1)]
            rgb, alpha = texel[..., :3].astype(float), texel[..., 3] > 127
        # Minecraft's directional face shading
        shade = 1.0 if bright else min(1.0, n[0] ** 2 * 0.6 + n[1] ** 2 * (3 + n[1]) / 4 + n[2] ** 2 * 0.8)
        m = inside & alpha
        color[y0:y1, x0:x1][m] = rgb[m] * shade
        depth[y0:y1, x0:x1][m] = z[m]
    img = Image.fromarray(np.clip(color, 0, 255).astype(np.uint8), "RGB").resize((size, size), Image.LANCZOS)
    if hitbox:
        hw, hh = hitbox[0] / 2, hitbox[1]
        c = np.array([[sx * hw, y, sz * hw] for y in (0, hh) for sx, sz in ((-1, -1), (1, -1), (1, 1), (-1, 1))])
        s = project(c)[:, :2] / SS
        d = ImageDraw.Draw(img)
        for i in range(4):
            for a_, b_ in ((i, (i + 1) % 4), (i + 4, (i + 1) % 4 + 4), (i, i + 4)):
                d.line([tuple(s[a_]), tuple(s[b_])], fill=(255, 235, 60), width=1)
    return img


def scene(rig, models, **pose_args):
    poses = lib.pose(rig, **pose_args)
    quads = build_quads(rig, models, poses)
    pts = np.concatenate([q[0] for q in quads])
    bounds = (pts.min(axis=0), pts.max(axis=0))
    return quads + ghost_quads(rig) + ground_quads(*bounds), bounds


def label(img, text):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 8 + 7 * len(text), 16], fill=(0, 0, 0))
    d.text((4, 2), text, fill=(255, 255, 255))
    return img


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("creature")
    ap.add_argument("--out", help="the directory given to gen.py --out (default: the project build)")
    ap.add_argument("--dest", help="where to write the PNGs")
    ap.add_argument("--prefix", help="file name prefix (default: the creature name)")
    ap.add_argument("--size", type=int, default=720)
    ap.add_argument("--t", type=float, default=0.0, help="animation time in seconds (0 = rest pose)")
    ap.add_argument("--moving", action="store_true", help="pose as if the carrier walks")
    ap.add_argument("--strike", type=float, default=-1.0, help="ticks since the attack trigger")
    ap.add_argument("--anim", action="store_true", help="also write a strip of 4 walk phases")
    ap.add_argument("--views", default=",".join(VIEWS), help="comma separated: " + ", ".join(VIEWS))
    args = ap.parse_args()

    rig, models, tex = load(args.out, args.creature)
    dest = args.dest or (os.path.join(args.out, "preview") if args.out else os.path.join(HERE, "build", "preview"))
    os.makedirs(dest, exist_ok=True)
    prefix = args.prefix or args.creature
    pa = dict(time=args.t, walk=args.t if args.moving else 0.0, move_w=1.0 if args.moving else 0.0,
              strike_ticks=args.strike)
    quads, bounds = scene(rig, models, **pa)
    written = []
    for name in args.views.split(","):
        img = label(render(quads, tex, VIEWS[name], args.size, bounds, rig.get("hitbox")), "%s  %s" % (rig["name"], name))
        written.append(os.path.join(dest, "%s_%s.png" % (prefix, name)))
        img.save(written[-1])
    if args.anim:
        n = 4
        strip = Image.new("RGB", (args.size * n // 2, args.size // 2))
        for i in range(n):
            t = 0.25 + i * 0.3
            q, b = scene(rig, models, time=t, walk=t, move_w=1.0, strike_ticks=3.0 if i == n - 1 else -1.0)
            fr = render(q, tex, VIEWS["front34"], args.size // 2, bounds)
            strip.paste(label(fr, "t=%.2f%s" % (t, " strike" if i == n - 1 else "")), (i * args.size // 2, 0))
        written.append(os.path.join(dest, prefix + "_anim.png"))
        strip.save(written[-1])
    # raw texture sheet on a checkerboard, nearest-neighbour upscaled
    k = max(1, 768 // tex.shape[1])
    big = Image.fromarray(tex, "RGBA").resize((tex.shape[1] * k, tex.shape[0] * k), Image.NEAREST)
    yy, xx = np.mgrid[0:big.height, 0:big.width]
    chk = np.where(((yy // 8 + xx // 8) % 2)[..., None] == 0, 46, 58).astype(np.uint8).repeat(3, axis=2)
    sheet = Image.fromarray(chk, "RGB")
    sheet.paste(big, (0, 0), big)
    written.append(os.path.join(dest, prefix + "_textures.png"))
    sheet.save(written[-1])
    print("\n".join(written))


if __name__ == "__main__":
    main()
