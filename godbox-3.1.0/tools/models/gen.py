#!/usr/bin/env python3
"""Generates GodBox model assets from tools/models/creatures/*.py. See README.md.

  python3 tools/models/gen.py                      # everything, into the project
  python3 tools/models/gen.py --only crab --out /tmp/me   # private preview build
"""
import argparse
import importlib.util
import json
import math
import os
import shutil
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
import lib  # noqa: E402

NS = "godbox"
PAD = 1  # texels of edge-extended padding around every face in the atlas


def load_creatures(only_files=None):
    lib.REGISTRY.clear()
    d = os.path.join(HERE, "creatures")
    for fn in sorted(os.listdir(d)):
        if not fn.endswith(".py") or fn.startswith("_"):
            continue
        if only_files is not None and fn[:-3] not in only_files:
            continue
        spec = importlib.util.spec_from_file_location("creature_" + fn[:-3], os.path.join(d, fn))
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
    creatures = list(lib.REGISTRY)
    names, ranges = set(), []
    for c in creatures:
        if c.name in names:
            raise SystemExit("duplicate creature name: " + c.name)
        names.add(c.name)
        if c.id_base < 1:
            raise SystemExit(c.name + ": id_base must be >= 1")
        if len(c.parts) > lib.IDS_PER_CREATURE:
            raise SystemExit("%s: more than %d parts" % (c.name, lib.IDS_PER_CREATURE))
        for other, lo in ranges:
            if abs(lo - c.id_base) < lib.IDS_PER_CREATURE:
                raise SystemExit("id ranges overlap: %s (%d) and %s (%d); each creature owns %d ids"
                                 % (other, lo, c.name, c.id_base, lib.IDS_PER_CREATURE))
        ranges.append((c.name, c.id_base))
    return sorted(creatures, key=lambda c: c.id_base)


# ---------------------------------------------------------------- texture atlas

def pack(sizes):
    """Shelf-pack (w, h) rectangles (already padded) into the smallest power-of-two square."""
    order = sorted(range(len(sizes)), key=lambda i: (-sizes[i][1], -sizes[i][0]))
    side = 16
    while True:
        pos, x, y, row = [None] * len(sizes), 0, 0, 0
        ok = True
        for i in order:
            w, h = sizes[i]
            if w > side:
                ok = False
                break
            if x + w > side:
                x, y, row = 0, y + row, 0
            if y + h > side:
                ok = False
                break
            pos[i] = (x, y)
            x, row = x + w, max(row, h)
        if ok:
            return side, pos
        side *= 2
        if side > 2048:
            raise SystemExit("texture atlas would exceed 2048 px; lower `res` or split the creature")


def build_creature(c):
    """Returns (atlas image, {part name: model json}, rig dict)."""
    faces = []  # (part, box index, face, image)
    for p in c.parts:
        for bi, b in enumerate(p.boxes):
            for f in lib.FACES:
                if f in b.skip:
                    continue
                fw, fh = b.face_size(f)
                if fw <= 0 or fh <= 0:
                    continue
                w, h = max(1, math.ceil(fw * c.res - 1e-6)), max(1, math.ceil(fh * c.res - 1e-6))
                mat = b.faces.get(f, b.tex)
                img = mat.paint(f, w, h, c.res, lib.seeded(c.name, p.name, bi, f))
                faces.append((p, bi, f, img))
    side, pos = pack([(img.shape[1] + 2 * PAD, img.shape[0] + 2 * PAD) for _, _, _, img in faces])
    atlas = np.zeros((side, side, 4), dtype=np.uint8)
    uv = {}
    for (p, bi, f, img), (x, y) in zip(faces, pos):
        h, w = img.shape[:2]
        atlas[y:y + h + 2 * PAD, x:x + w + 2 * PAD] = np.pad(img, ((PAD, PAD), (PAD, PAD), (0, 0)), mode="edge")
        k = 16.0 / side
        uv[(p.name, bi, f)] = [round((x + PAD) * k, 5), round((y + PAD) * k, 5),
                               round((x + PAD + w) * k, 5), round((y + PAD + h) * k, 5)]

    tex = "%s:item/m/%s" % (NS, c.name)
    models = {}
    for p in c.parts:
        if p.base is not None:  # a variant: the base part's geometry with another display transform
            models[p.name] = {"parent": "%s:item/m/%s/%s" % (NS, c.name, p.base.name)}
            if p.display:
                models[p.name]["display"] = p.display
            continue
        elements = []
        for bi, b in enumerate(p.boxes):
            (fx, fy, fz), (tx, ty, tz) = b.frm, b.to
            # item displays show the model turned 180 deg around Y: bake the flip in here
            el = {"from": [8 - tx, 8 + fy, 8 - tz], "to": [8 - fx, 8 + ty, 8 - fz]}
            if b.rot is not None:
                axis, ang = b.rot[0], float(b.rot[1])
                ox, oy, oz = b.origin
                el["rotation"] = {"origin": [8 - ox, 8 + oy, 8 - oz], "axis": axis,
                                  "angle": ang if axis == "y" else -ang}
            if p.bright:
                el["shade"] = False
            el["faces"] = {lib.MODEL_FACE[f]: {"uv": uv[(p.name, bi, f)], "texture": "#t"}
                           for f in lib.FACES if (p.name, bi, f) in uv}
            elements.append(el)
        models[p.name] = {"textures": {"t": tex, "particle": tex}, "elements": elements}
        if p.display:
            models[p.name]["display"] = p.display

    index = {p.name: i for i, p in enumerate(c.parts)}
    rig = {"name": c.name, "id_base": c.id_base, "scale": c.scale, "res": c.res, "hide_carrier": c.hide_carrier,
           "death_fx": c.death_fx, "hitbox": c.hitbox, "texture": tex, "ghosts": c.ghosts,
           "item_overrides": [[item, index[p.name]] for item, p in c.overrides],
           "parts": [{"name": p.name, "cmd": c.id_base + i, "model": "%s:item/m/%s/%s" % (NS, c.name, p.name),
                      "parent": index[p.parent.name] if p.parent is not None else -1,
                      "pivot": list(p.pivot), "rot": list(p.rot), "scale": p.scale, "bright": p.bright,
                      "anims": p.anim, "display": p.display,
                      "base": index[p.base.name] if p.base is not None else -1} for i, p in enumerate(c.parts)]}
    return Image.fromarray(atlas, "RGBA"), models, rig


# ---------------------------------------------------------------- java table

def jf(v):
    return repr(round(float(v), 5)) + "f"


def java_table(rigs):
    o = ["package com.vladik.godbox;", "",
         "import java.util.HashMap;", "import java.util.Map;", "",
         "/** GENERATED by tools/models/gen.py from tools/models/creatures/*.py - do not edit by hand. */",
         "public final class ModelParts {",
         "    public static final int SWING = 0, BOB = 1, SPIN = 2, PULSE = 3, STRIKE = 4;",
         "    public static final int ALWAYS = 0, MOVING = 1, IDLE = 2;",
         "    public static final int SIN = 0, POS = 1, ABS = 2, SNAP = 3;",
         "",
         "    /** amp: degrees (px for BOB, fraction for PULSE); speed: cycles per second (ticks for STRIKE); phase: cycles. */",
         "    public record Anim(int type, int axis, float amp, float speed, float phase, int when, int wave) {}",
         "",
         "    /** pivot in px relative to the parent part (or the carrier's feet), rotation in degrees (yaw*pitch*roll). */",
         "    public record Part(String name, int cmd, int parent, float px, float py, float pz,",
         "                       float rx, float ry, float rz, float scale, boolean bright, Anim[] anims) {}",
         "",
         "    /** scale: blocks per 16 px. */",
         "    public record Creature(String name, float scale, boolean hideCarrier, boolean deathFx, Part[] parts) {}",
         "",
         "    private static final Map<String, Creature> ALL = new HashMap<>();",
         "",
         "    private ModelParts() {}",
         "",
         "    public static Creature get(String name) {",
         "        return ALL.get(name);",
         "    }",
         "",
         "    public static boolean has(String name) {",
         "        return ALL.containsKey(name);",
         "    }",
         "",
         "    private static Anim a(int type, int axis, float amp, float speed, float phase, int when, int wave) {",
         "        return new Anim(type, axis, amp, speed, phase, when, wave);",
         "    }",
         "",
         "    private static Part p(String name, int cmd, int parent, float px, float py, float pz,",
         "                          float rx, float ry, float rz, float scale, boolean bright, Anim... anims) {",
         "        return new Part(name, cmd, parent, px, py, pz, rx, ry, rz, scale, bright, anims);",
         "    }",
         "",
         "    static {"]
    for r in rigs:
        o.append("        %s();" % jname(r["name"]))
    o.append("    }")
    for r in rigs:
        o += ["", "    private static void %s() {" % jname(r["name"]),
              "        ALL.put(\"%s\", new Creature(\"%s\", %s, %s, %s, new Part[]{" % (
                  r["name"], r["name"], jf(r["scale"]), str(r["hide_carrier"]).lower(), str(r["death_fx"]).lower())]
        for p in r["parts"]:
            anims = "".join(", a(%s, %d, %s, %s, %s, %s, %s)" % (
                a["type"].upper(), lib.AXES.index(a["axis"]), jf(a["amp"]), jf(a["speed"]), jf(a["phase"]),
                a["when"].upper(), a["wave"].upper()) for a in p["anims"])
            o.append("                p(\"%s\", %d, %d, %s, %s, %s, %s, %s, %s, %s, %s%s)," % (
                p["name"], p["cmd"], p["parent"], jf(p["pivot"][0]), jf(p["pivot"][1]), jf(p["pivot"][2]),
                jf(p["rot"][0]), jf(p["rot"][1]), jf(p["rot"][2]), jf(p["scale"]), str(p["bright"]).lower(), anims))
        o += ["        }));", "    }"]
    o += ["}", ""]
    return "\n".join(o)


def jname(name):
    return "c_" + "".join(ch if ch.isalnum() else "_" for ch in name)


# ---------------------------------------------------------------- vanilla item overrides

def write_item_overrides(assets, rigs):
    """Vanilla items that turn into one of our parts by custom_model_data (Creature.item_override).

    Minecraft takes the LAST matching override, so ours go after the vanilla ones (pulling/charged of the
    crossbow) and win whatever the item's state is.
    """
    by_item = {}
    for r in rigs:
        for item, pi in r.get("item_overrides", ()):
            by_item.setdefault(item, []).append(r["parts"][pi])
    for item, parts in by_item.items():
        ns, name = item.split(":", 1)
        src = os.path.join(HERE, "vanilla", name + ".json")
        if not os.path.isfile(src):
            raise SystemExit("item_override: put the vanilla model of %s into %s" % (item, os.path.relpath(src)))
        model = json.load(open(src))
        model.setdefault("overrides", [])
        for p in sorted(parts, key=lambda p: p["cmd"]):
            model["overrides"].append({"predicate": {"custom_model_data": p["cmd"]}, "model": p["model"]})
        d = os.path.join(assets, "..", ns, "models", "item")
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, name + ".json"), "w") as f:
            json.dump(model, f, indent=1)


# ---------------------------------------------------------------- validation

def validate(assets, rigs):
    """Checks every generated file. Returns a list of problems (empty = fine)."""
    errs = []

    def res_path(kind, ident, ext):
        ns, path = ident.split(":", 1)
        return os.path.join(assets, "..", ns, kind, path + ext) if ns != NS else os.path.join(assets, kind, path + ext)

    base = os.path.join(assets, "models", "item", "model.json")
    try:
        ov = json.load(open(base))["overrides"]
    except Exception as e:  # noqa: BLE001
        return ["model.json: %s" % e]
    ids = [o["predicate"]["custom_model_data"] for o in ov]
    if len(set(ids)) != len(ids):
        errs.append("model.json: duplicate custom_model_data ids")
    if ids != sorted(ids):
        errs.append("model.json: overrides must be sorted by custom_model_data")
    by_id = {o["predicate"]["custom_model_data"]: o["model"] for o in ov}
    for o in ov:
        if not os.path.isfile(res_path("models", o["model"], ".json")):
            errs.append("model.json: missing model file for " + o["model"])
    for r in rigs:
        for p in r["parts"]:
            if by_id.get(p["cmd"]) != p["model"]:
                errs.append("%s/%s: id %d is not mapped to %s" % (r["name"], p["name"], p["cmd"], p["model"]))
            if not (p["parent"] < r["parts"].index(p)):
                errs.append("%s/%s: parent must come first" % (r["name"], p["name"]))
            path = res_path("models", p["model"], ".json")
            try:
                m = json.load(open(path))
            except Exception as e:  # noqa: BLE001
                errs.append("%s: %s" % (path, e))
                continue
            who = "%s/%s" % (r["name"], p["name"])
            for ctx, t in m.get("display", {}).items():
                if ctx not in lib.DISPLAY_CONTEXTS:
                    errs.append("%s: unknown display context %s" % (who, ctx))
                if any(abs(v) > 80 for v in t.get("translation", ())) or any(abs(v) > 4 for v in t.get("scale", ())):
                    errs.append("%s: display %s exceeds Minecraft's limits (translation 80, scale 4)" % (who, ctx))
            if "parent" in m:
                if p["base"] < 0 or m["parent"] != r["parts"][p["base"]]["model"]:
                    errs.append("%s: parent %s is not its base part" % (who, m["parent"]))
                elif not os.path.isfile(res_path("models", m["parent"], ".json")):
                    errs.append("%s: missing parent model %s" % (who, m["parent"]))
                continue
            for t in m.get("textures", {}).values():
                if not os.path.isfile(res_path("textures", t, ".png")):
                    errs.append("%s: missing texture %s" % (who, t))
            if not m.get("elements"):
                errs.append("%s: no elements" % who)
            for i, el in enumerate(m.get("elements", [])):
                for key in ("from", "to"):
                    if any(v < -16 - 1e-6 or v > 32 + 1e-6 for v in el[key]):
                        errs.append("%s box %d: %s %s outside -16..32 (the part reaches more than 24 px from its pivot)"
                                    % (who, i, key, el[key]))
                rot = el.get("rotation")
                if rot and (rot["angle"] not in lib.ELEMENT_ANGLES or rot["axis"] not in lib.AXES):
                    errs.append("%s box %d: illegal rotation %s" % (who, i, rot))
                if not el.get("faces"):
                    errs.append("%s box %d: no faces" % (who, i))
                for fn, f in el.get("faces", {}).items():
                    if fn not in lib.MODEL_FACE.values():
                        errs.append("%s box %d: bad face %s" % (who, i, fn))
                    if any(v < 0 or v > 16 for v in f["uv"]):
                        errs.append("%s box %d: uv out of 0..16" % (who, i))
                    if f["texture"].lstrip("#") not in m.get("textures", {}):
                        errs.append("%s box %d: unknown texture ref %s" % (who, i, f["texture"]))
    return errs


# ---------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--only", action="append", help="creature name(s) to (re)paint; with --out only their definition files are loaded")
    ap.add_argument("--out", help="private output directory (default: write into the project)")
    args = ap.parse_args()

    only = set(args.only) if args.only else None
    if args.out:
        out = os.path.abspath(args.out)
        assets = os.path.join(out, "assets", NS)
        java = os.path.join(out, "java", "ModelParts.java")
        rig_dir = os.path.join(out, "rig")
        # a private build may load just the requested files (file name = creature name by convention)
        files = only if only and all(os.path.isfile(os.path.join(HERE, "creatures", n + ".py")) for n in only) else None
        creatures = load_creatures(files)
    else:
        assets = os.path.join(ROOT, "src", "main", "resources", "assets", NS)
        java = os.path.join(ROOT, "src", "main", "java", "com", "vladik", "godbox", "ModelParts.java")
        rig_dir = os.path.join(HERE, "build", "rig")
        creatures = load_creatures()
    if only and not only <= {c.name for c in creatures}:
        raise SystemExit("unknown creature(s): " + ", ".join(sorted(only - {c.name for c in creatures})))

    model_dir = os.path.join(assets, "models", "item")
    tex_dir = os.path.join(assets, "textures", "item", "m")
    for d in (model_dir, tex_dir, rig_dir, os.path.dirname(java)):
        os.makedirs(d, exist_ok=True)

    rigs = []
    for c in creatures:
        if not c.parts or any(not p.boxes and p.base is None for p in c.parts):
            raise SystemExit(c.name + ": every part needs at least one box")
        atlas, models, rig = build_creature(c)
        rigs.append(rig)
        if only and c.name not in only:
            continue
        cdir = os.path.join(model_dir, "m", c.name)
        shutil.rmtree(cdir, ignore_errors=True)
        os.makedirs(cdir)
        for name, m in models.items():
            with open(os.path.join(cdir, name + ".json"), "w") as f:
                json.dump(m, f, separators=(",", ":"))
        atlas.save(os.path.join(tex_dir, c.name + ".png"))
        with open(os.path.join(rig_dir, c.name + ".json"), "w") as f:
            json.dump(rig, f, indent=1)
        print("%-12s %2d parts, %3d boxes, texture %dx%d, ids %d..%d" % (
            c.name, len(c.parts), sum(len(p.boxes) for p in c.parts), atlas.width, atlas.height,
            c.id_base, c.id_base + len(c.parts) - 1))

    overrides = [{"predicate": {"custom_model_data": p["cmd"]}, "model": p["model"]} for r in rigs for p in r["parts"]]
    with open(os.path.join(model_dir, "model.json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/barrier"},
                   "overrides": overrides}, f, indent=1)
    with open(java, "w") as f:
        f.write(java_table(rigs))
    write_item_overrides(assets, rigs)

    errs = validate(assets, rigs)
    for e in errs:
        print("ERROR", e)
    if errs:
        raise SystemExit("%d problem(s)" % len(errs))
    print("ok: %d creature(s), %d parts -> %s" % (len(rigs), len(overrides), os.path.relpath(assets)))


if __name__ == "__main__":
    main()
