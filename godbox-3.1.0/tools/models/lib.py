"""GodBox model definition API. See README.md.

Coordinate conventions (creature space, the same as a display entity's local space):
  +Z = forward (where the mob looks), +Y = up (0 = the mob's feet), +X = the creature's LEFT.
  Units are model pixels; 1 px = creature.scale / 16 blocks in the world.
"""
import math
import zlib

import numpy as np

FACES = ("front", "back", "left", "right", "top", "bottom")
# creature face -> Minecraft model face (item displays turn the model 180 deg around Y,
# the generator bakes that flip into the element coordinates)
MODEL_FACE = {"front": "north", "back": "south", "left": "west", "right": "east", "top": "up", "bottom": "down"}
ELEMENT_ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)
IDS_PER_CREATURE = 100

REGISTRY = []


# ---------------------------------------------------------------- colours / rng

def rgb(c):
    """(r,g,b) 0..255 or '#rrggbb' -> float array."""
    if isinstance(c, str):
        c = c.lstrip("#")
        c = tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))
    return np.array(c[:3], dtype=np.float64)


def seeded(*key):
    return np.random.default_rng(zlib.crc32("/".join(str(k) for k in key).encode()))


def value_noise(h, w, cell, rng):
    """Smooth 0..1 noise with blobs about `cell` texels across."""
    cell = max(1.0, float(cell))
    gh, gw = int(h / cell) + 3, int(w / cell) + 3
    g = rng.random((gh, gw))
    ys = np.arange(h) / cell + rng.random()
    xs = np.arange(w) / cell + rng.random()
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = (ys - y0)[:, None], (xs - x0)[None, :]
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    a = g[y0][:, x0]
    b = g[y0][:, x0 + 1]
    c = g[y0 + 1][:, x0]
    d = g[y0 + 1][:, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


class Ctx:
    """Passed to patterns: face name, size in texels, texel density and a seeded rng."""

    def __init__(self, face, w, h, res, rng):
        self.face, self.w, self.h, self.res, self.rng = face, w, h, res, rng


def _blend(img, mask, color, alpha=1.0):
    m = (np.asarray(mask, dtype=np.float64) * alpha)[..., None]
    img[:] = img * (1 - m) + rgb(color) * m


# ---------------------------------------------------------------- patterns
# A pattern is a callable(img, ctx): img is a float (h, w, 3) array, edited in place.
# Every face image is the true view of that face from outside: side faces have +Y at the top,
# "top" has the creature's front at the top of the image, "bottom" has its back at the top.

def spots(color, density=0.05, size=(1, 3), alpha=1.0):
    """Irregular round blotches. density = covered fraction, size in px (scaled by res)."""
    def f(img, ctx):
        lo, hi = size[0] * ctx.res, size[1] * ctx.res
        n = max(1, int(round(ctx.w * ctx.h * density / (((lo + hi) / 2) ** 2))))
        yy, xx = np.mgrid[0:ctx.h, 0:ctx.w]
        mask = np.zeros((ctx.h, ctx.w))
        for _ in range(n):
            cx, cy = ctx.rng.random() * ctx.w, ctx.rng.random() * ctx.h
            r = (lo + ctx.rng.random() * (hi - lo)) / 2
            ex = 0.7 + ctx.rng.random() * 0.6
            mask[((xx + 0.5 - cx) / ex) ** 2 + ((yy + 0.5 - cy) * ex) ** 2 <= r * r + 0.3] = 1
        _blend(img, mask, color, alpha)
    return f


def speckle(color, density=0.05, alpha=1.0):
    """Single-texel freckles."""
    def f(img, ctx):
        _blend(img, ctx.rng.random((ctx.h, ctx.w)) < density, color, alpha)
    return f


def stripes(color, width=2, gap=4, axis="v", alpha=1.0, wobble=0.0, offset=0):
    """axis 'v' = vertical bars, 'h' = horizontal bands, 'd' = diagonal. Sizes in px."""
    def f(img, ctx):
        yy, xx = np.mgrid[0:ctx.h, 0:ctx.w].astype(np.float64)
        t = {"v": xx, "h": yy, "d": xx + yy}[axis] + offset * ctx.res
        if wobble:
            t = t + (value_noise(ctx.h, ctx.w, 4 * ctx.res, ctx.rng) - 0.5) * 2 * wobble * ctx.res
        period = (width + gap) * ctx.res
        _blend(img, (t % period) < width * ctx.res, color, alpha)
    return f


def scales(color, size=4, alpha=0.5):
    """Overlapping fish/reptile scales: a darker U outline per scale."""
    def f(img, ctx):
        s = max(2, int(size * ctx.res))
        yy, xx = np.mgrid[0:ctx.h, 0:ctx.w]
        row = yy // s
        xo = (xx + (row % 2) * (s // 2)) % s
        yo = yy % s
        _blend(img, ((yo == s - 1) & (xo > 0) & (xo < s - 1)) | ((xo == 0) & (yo >= s // 2) & (yo < s - 1)), color, alpha)
    return f


def plates(size=(8, 6), seam=0.35, highlight=0.15, stagger=True):
    """Armour plates / bricks: dark seams with a light rim under them."""
    def f(img, ctx):
        pw, ph = max(2, int(size[0] * ctx.res)), max(2, int(size[1] * ctx.res))
        yy, xx = np.mgrid[0:ctx.h, 0:ctx.w]
        row = yy // ph
        xo = (xx + ((row % 2) * (pw // 2) if stagger else 0)) % pw
        yo = yy % ph
        dark = (xo == 0) | (yo == 0)
        light = ((xo == 1) | (yo == 1)) & ~dark
        img[dark] *= 1 - seam
        img[light] = np.minimum(255, img[light] * (1 + highlight))
    return f


def rivets(color, spacing=8, margin=2, shadow=0.35):
    """A grid of 1-texel rivets with a shadow texel below."""
    def f(img, ctx):
        sp, mg = max(2, int(spacing * ctx.res)), int(margin * ctx.res)
        for y in range(mg, ctx.h - mg, sp):
            for x in range(mg, ctx.w - mg, sp):
                img[y, x] = rgb(color)
                if y + 1 < ctx.h:
                    img[y + 1, x] *= 1 - shadow
    return f


def gradient(top, bottom, alpha=1.0, axis="v"):
    """Blend towards `top` colour at the top (left for axis='h') and `bottom` at the other end."""
    def f(img, ctx):
        n = ctx.h if axis == "v" else ctx.w
        t = (np.arange(n) / max(1, n - 1))
        t = t[:, None, None] if axis == "v" else t[None, :, None]
        img[:] = img * (1 - alpha) + (rgb(top) * (1 - t) + rgb(bottom) * t) * alpha
    return f


def band(color, at="bottom", size=0.25, alpha=1.0):
    """A solid band along one edge; size is a fraction of the face."""
    def f(img, ctx):
        m = np.zeros((ctx.h, ctx.w))
        nh, nw = max(1, int(round(ctx.h * size))), max(1, int(round(ctx.w * size)))
        if at == "bottom":
            m[ctx.h - nh:, :] = 1
        elif at == "top":
            m[:nh, :] = 1
        elif at == "left":
            m[:, :nw] = 1
        else:
            m[:, ctx.w - nw:] = 1
        _blend(img, m, color, alpha)
    return f


def rect(x0, y0, x1, y1, color, alpha=1.0):
    """A rectangle in face fractions (0..1, origin top-left)."""
    def f(img, ctx):
        m = np.zeros((ctx.h, ctx.w))
        ax, bx = int(round(x0 * ctx.w)), max(int(round(x0 * ctx.w)) + 1, int(round(x1 * ctx.w)))
        ay, by = int(round(y0 * ctx.h)), max(int(round(y0 * ctx.h)) + 1, int(round(y1 * ctx.h)))
        m[ay:by, ax:bx] = 1
        _blend(img, m, color, alpha)
    return f


def eye(sclera, pupil, iris=None, glint=(255, 255, 255), slit=False):
    """Fills the face with an eye: sclera, optional iris ring, pupil (round or vertical slit), glint texel."""
    def f(img, ctx):
        img[:] = rgb(sclera)
        yy, xx = np.mgrid[0:ctx.h, 0:ctx.w]
        cx, cy = (ctx.w - 1) / 2, (ctx.h - 1) / 2
        d = np.maximum(np.abs(xx - cx) / max(1, ctx.w) * 2, np.abs(yy - cy) / max(1, ctx.h) * 2)
        if iris is not None:
            img[d <= 0.72] = rgb(iris)
        if slit:
            img[(np.abs(xx - cx) <= max(0.5, ctx.w * 0.12)) & (np.abs(yy - cy) <= ctx.h * 0.36)] = rgb(pupil)
        else:
            img[d <= 0.42] = rgb(pupil)
        if glint is not None and ctx.w >= 4:
            img[max(0, int(cy - ctx.h * 0.25)), min(ctx.w - 1, int(cx + ctx.w * 0.2))] = rgb(glint)
    return f


def on(faces, pattern):
    """Apply `pattern` only on the listed faces, e.g. on(('top',), spots(...))."""
    faces = (faces,) if isinstance(faces, str) else tuple(faces)

    def f(img, ctx):
        if ctx.face in faces:
            pattern(img, ctx)
    return f


# ---------------------------------------------------------------- material

class Mat:
    """A procedural Minecraft-style surface.

    base        base colour
    noise       per-texel brightness jitter (0..1)
    mottle      second colour blended in posterised blotches (mottle_amt strength, mottle_size px across)
    shade       vertical light: side faces get lighter at the top / darker at the bottom,
                the top face is lifted and the bottom face darkened by the same amount
    bevel       highlight on the top/left edge texels and shadow on the bottom/right ones
    edge        darken the whole 1-texel border (outline look)
    patterns    list of pattern callables applied after the mottling
    faces       {face: Mat} overrides for specific faces
    """

    def __init__(self, base, noise=0.07, mottle=None, mottle_amt=0.6, mottle_size=5, shade=0.12, bevel=0.16,
                 edge=0.0, patterns=(), faces=None):
        self.base, self.noise, self.mottle, self.mottle_amt, self.mottle_size = base, noise, mottle, mottle_amt, mottle_size
        self.shade, self.bevel, self.edge, self.patterns, self.faces = shade, bevel, edge, list(patterns), dict(faces or {})

    def with_(self, **kw):
        """A copy with some fields replaced; `add=[patterns]` appends patterns."""
        m = Mat(self.base, self.noise, self.mottle, self.mottle_amt, self.mottle_size, self.shade, self.bevel,
                self.edge, self.patterns, self.faces)
        m.patterns += list(kw.pop("add", ()))
        for k, v in kw.items():
            if not hasattr(m, k):
                raise TypeError("Mat has no field " + k)
            setattr(m, k, v)
        return m

    def paint(self, face, w, h, res, rng):
        if face in self.faces:
            return self.faces[face].paint(face, w, h, res, rng)
        ctx = Ctx(face, w, h, res, rng)
        img = np.empty((h, w, 3))
        img[:] = rgb(self.base)
        if self.mottle is not None:
            n = value_noise(h, w, self.mottle_size * res, rng)
            q = np.clip(np.floor(n * 5) / 4, 0, 1)  # posterised so the blotches stay pixel-art crisp
            _blend(img, q, self.mottle, self.mottle_amt)
        for p in self.patterns:
            p(img, ctx)
        if self.shade:
            if face == "top":
                img *= 1 + self.shade * 0.6
            elif face == "bottom":
                img *= 1 - self.shade
            else:
                img *= (1 + self.shade * np.linspace(1, -1, h))[:, None, None]
        if self.noise:
            img *= 1 + self.noise * (rng.random((h, w, 1)) * 2 - 1)
        if self.bevel and w >= 3 and h >= 3:
            img[0, :] *= 1 + self.bevel * 0.8
            img[1:, 0] *= 1 + self.bevel * 0.5
            img[-1, :] *= 1 - self.bevel
            img[:-1, -1] *= 1 - self.bevel * 0.6
        if self.edge and w >= 3 and h >= 3:
            img[[0, -1], :] *= 1 - self.edge
            img[1:-1, [0, -1]] *= 1 - self.edge
        out = np.empty((h, w, 4), dtype=np.uint8)
        out[..., :3] = np.clip(np.rint(img), 0, 255)
        out[..., 3] = 255
        return out


# ---------------------------------------------------------------- animation vocabulary
# Angles in degrees, speed in cycles per second, phase in cycles (0..1).
# when: "always" | "moving" (fades in while the carrier walks, driven by the walk clock) | "idle" (the opposite)
# wave: "sin" (-1..1) | "pos" (max(0, sin)) | "abs" (|sin|) | "snap" (0..1: opens slowly, shuts fast)

ANIM_TYPES = ("swing", "bob", "spin", "pulse", "strike")
WHENS = ("always", "moving", "idle")
WAVES = ("sin", "pos", "abs", "snap")
AXES = ("x", "y", "z")


def _anim(type_, axis, amp, speed, phase, when, wave):
    if axis not in AXES or when not in WHENS or wave not in WAVES:
        raise ValueError("bad animation descriptor: %r %r %r" % (axis, when, wave))
    return {"type": type_, "axis": axis, "amp": float(amp), "speed": float(speed), "phase": float(phase),
            "when": when, "wave": wave}


def swing(axis, amp, speed=1.0, phase=0.0, when="always", wave="sin"):
    """Rotate around a local axis by amp * wave. Legs, arms, jaws, claw fingers."""
    return _anim("swing", axis, amp, speed, phase, when, wave)


def flap(amp, speed=1.0, phase=0.0, axis="z", when="always"):
    """Wing beat: a swing around Z (use a negative amp on the mirrored wing; phase-lag the wing tips)."""
    return _anim("swing", axis, amp, speed, phase, when, "sin")


def sway(axis, amp, speed=0.5, index=0, lag=0.12, when="always"):
    """Tail / tentacle chain: give segment k (parented to segment k-1) index=k, each one lags the previous."""
    return _anim("swing", axis, amp, speed, -lag * index, when, "sin")


def bob(amp, speed=0.5, phase=0.0, when="always", wave="sin", axis="y"):
    """Translate along an axis of the parent frame by amp px * wave."""
    return _anim("bob", axis, amp, speed, phase, when, wave)


def spin(axis, speed, when="always"):
    """Continuous rotation, `speed` turns per second (keep below 2 so interpolation takes the short way)."""
    return _anim("spin", axis, 360.0, speed, 0.0, when, "sin")


def pulse(amp, speed=0.5, phase=0.0, when="always"):
    """Scale breathing: scale *= 1 + amp * sin."""
    return _anim("pulse", "y", amp, speed, phase, when, "sin")


def strike(axis, angle, ticks=10):
    """Attack pose: when Models.trigger(carrier) is called, rotate by `angle` and ease back over `ticks`."""
    return _anim("strike", axis, angle, ticks, 0.0, "always", "sin")


def mirror_rot(rot):
    """Base rotation of the part mirrored to the other side (X -> -X)."""
    return (rot[0], -rot[1], -rot[2])


# ---------------------------------------------------------------- geometry

class Box:
    def __init__(self, frm, to, tex, faces, skip, rot, origin):
        self.frm = tuple(float(min(a, b)) for a, b in zip(frm, to))
        self.to = tuple(float(max(a, b)) for a, b in zip(frm, to))
        self.tex, self.faces, self.skip = tex, dict(faces or {}), tuple(skip)
        self.rot = rot
        self.origin = tuple(float(v) for v in origin) if origin is not None else tuple((a + b) / 2 for a, b in zip(self.frm, self.to))
        if rot is not None and (rot[0] not in AXES or float(rot[1]) not in ELEMENT_ANGLES):
            raise ValueError("box rot must be (axis, angle) with angle in %s" % (ELEMENT_ANGLES,))
        for f in list(self.faces) + list(self.skip):
            if f not in FACES:
                raise ValueError("unknown face " + f)

    def face_size(self, face):
        dx, dy, dz = (b - a for a, b in zip(self.frm, self.to))
        return {"front": (dx, dy), "back": (dx, dy), "left": (dz, dy), "right": (dz, dy),
                "top": (dx, dz), "bottom": (dx, dz)}[face]


class Part:
    def __init__(self, creature, name, pivot, parent, rot, scale, bright, anim, display=None, base=None):
        self.creature, self.name = creature, name
        self.display = _check_display(display)
        self.base = base
        self.pivot = tuple(float(v) for v in pivot)
        self.parent = parent
        self.rot = tuple(float(v) for v in rot)
        self.scale, self.bright = float(scale), bool(bright)
        self.anim = [anim] if isinstance(anim, dict) else list(anim)
        self.boxes = []

    def box(self, frm, to, tex, faces=None, skip=(), rot=None, origin=None):
        """Add a cuboid. frm/to are px relative to this part's pivot, in the part's own (unrotated) frame.

        tex     Mat for all faces; faces={face: Mat} overrides single faces; skip=(faces...) omits faces
        rot     optional ('x'|'y'|'z', angle) with angle in -45/-22.5/22.5/45, around `origin`
                (px, default: the box centre). Right-handed, in creature axes.
        """
        if self.base is not None:
            raise ValueError("%s: a variant shares the boxes of %s" % (self.name, self.base.name))
        self.boxes.append(Box(frm, to, tex, faces, skip, rot, origin))
        return self

    def variant(self, name, display, pivot=None):
        """The same geometry under another id with its own `display` (written as a child model)."""
        if any(p.name == name for p in self.creature.parts):
            raise ValueError("%s: duplicate part %s" % (self.creature.name, name))
        p = Part(self.creature, name, self.pivot if pivot is None else pivot, self.parent, self.rot, self.scale,
                 self.bright, self.anim, display, self)
        self.creature.parts.append(p)
        return p


class Creature:
    """A model made of named parts.

    name          key used by Models.attach(carrier, name); also the texture / model file prefix
    id_base       first custom_model_data id; the creature owns id_base .. id_base+99. Never reuse or change.
    scale         blocks per 16 px (2..4 for bosses)
    res           texels per px (2 = 32 texels across a 16 px face)
    hide_carrier  True: the vanilla mob gets invisibility (full replacement); False: accessory on a visible mob
    death_fx      explosion particles when the carrier dies
    hitbox        (width, height) in blocks, only drawn in previews
    """

    def __init__(self, name, id_base, scale=1.0, res=1, hide_carrier=True, death_fx=False, hitbox=None):
        self.name, self.id_base, self.scale, self.res = name, int(id_base), float(scale), int(res)
        self.hide_carrier, self.death_fx, self.hitbox = bool(hide_carrier), bool(death_fx), hitbox
        self.parts = []
        self.ghosts = []
        REGISTRY.append(self)

    def ghost(self, frm, to, color):
        """Preview-only flat box in creature space (px), e.g. the mob's head under a hat. Not exported to the game."""
        self.ghosts.append([[float(v) for v in frm], [float(v) for v in to], [int(v) for v in rgb(color)]])
        return self

    def part(self, name, pivot=(0, 0, 0), parent=None, rot=(0, 0, 0), scale=1.0, bright=False, anim=(), display=None):
        """Add a part (one display entity).

        pivot   px; for a root part in creature space, for a child relative to the parent's pivot in the
                parent's frame. The part rotates/scales around it.
        parent  a Part created earlier (its rotation, scale and motion are inherited)
        rot     base rotation (rx, ry, rz) degrees, applied as yaw(Y) * pitch(X) * roll(Z), right-handed
        scale   extra uniform scale, inherited by children
        bright  full-bright and unshaded (glowing eyes, lights)
        anim    one descriptor or a list (swing/flap/sway/bob/spin/pulse/strike)
        display optional item display transforms written into the model JSON, e.g. head_display("tall") or
                {"head": {"translation": [0, 9.6, 0], "scale": [1.6, 1.6, 1.6]}} - for parts worn as items
                (HEAD slot); item_display parts use context "none" and ignore it
        """
        if any(p.name == name for p in self.parts):
            raise ValueError("%s: duplicate part %s" % (self.name, name))
        if parent is not None and parent not in self.parts:
            raise ValueError("%s: parent of %s must be created first" % (self.name, name))
        p = Part(self, name, pivot, parent, rot, scale, bright, anim, display)
        self.parts.append(p)
        return p


# ---------------------------------------------------------------- item display transforms (worn parts)

DISPLAY_CONTEXTS = ("thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand",
                    "head", "gui", "ground", "fixed")
# Height of the head's top above the point where HeadFeatureRenderer puts the item centre, in mob-model px
# (derivation in UV_NOTES.md): 8 px heads; 10 px villager heads (the renderer lifts those by 0.1875 item units);
# 10 px heads that are not VillagerEntity/ZombieVillagerEntity (illagers, wandering trader).
HEAD_TOP = {"biped": 4.0, "villager": 4.125, "tall": 6.0}
HEAD_ITEM_PX = 0.625  # HeadFeatureRenderer scales the item by 0.625: one element px = 0.625 mob-model px


def head_display(kind):
    """display= for a hat: element px become mob-model px and the part's pivot sits on top of the head, centred.

    kind: "biped" (zombies, skeletons, piglins), "villager" (villager, zombie villager), "tall" (illagers, trader).
    In the part, y = 0 is the top of the head; the head is x,z -4..4 and y -8..0 (biped) or -10..0.
    """
    k = 1.0 / HEAD_ITEM_PX
    return {"head": {"translation": [0, round(HEAD_TOP[kind] * k, 4), 0], "scale": [k, k, k]}}


def _check_display(display):
    if display is None:
        return None
    out = {}
    for ctx, t in display.items():
        if ctx not in DISPLAY_CONTEXTS:
            raise ValueError("unknown display context " + ctx)
        o = {}
        for key, lim in (("rotation", None), ("translation", 80.0), ("scale", 4.0)):
            if key in t:
                v = [float(x) for x in t[key]]
                if len(v) != 3 or (lim is not None and any(abs(x) > lim for x in v)):
                    raise ValueError("display %s.%s must be 3 numbers within +-%s (Minecraft clamps it)" % (ctx, key, lim))
                o[key] = v
        out[ctx] = o
    return out


# ---------------------------------------------------------------- pose maths (mirrored in Models.java)

def quat_axis(axis, deg):
    h = math.radians(deg) / 2
    q = [0.0, 0.0, 0.0, math.cos(h)]
    q["xyz".index(axis)] = math.sin(h)
    return q


def quat_mul(a, b):
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return [aw * bx + ax * bw + ay * bz - az * by,
            aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw,
            aw * bw - ax * bx - ay * by - az * bz]


def quat_rot(q, v):
    x, y, z, w = q
    m = np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                  [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                  [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
    return m @ np.asarray(v, dtype=np.float64)


def quat_yxz(rot):
    return quat_mul(quat_mul(quat_axis("y", rot[1]), quat_axis("x", rot[0])), quat_axis("z", rot[2]))


def wave_value(wave, cycles):
    if wave == "snap":
        x = cycles - math.floor(cycles)
        if x < 0.7:
            t = x / 0.7
            return t * t * (3 - 2 * t)
        return max(0.0, 1 - (x - 0.7) / 0.1)
    s = math.sin(2 * math.pi * cycles)
    return max(0.0, s) if wave == "pos" else abs(s) if wave == "abs" else s


def strike_envelope(t):
    """t = 0..1 since the trigger: fast wind-up, slow return."""
    if t <= 0 or t >= 1:
        return 0.0
    return t / 0.25 if t < 0.25 else (1 - (t - 0.25) / 0.75) ** 2


def pose(rig, time=0.0, walk=0.0, move_w=0.0, strike_ticks=-1.0):
    """Rest/animated pose: list of (pos_blocks, quat, scale_blocks_per_16px) per part.

    time/walk in seconds, move_w 0..1, strike_ticks = ticks since the attack trigger (<0: none).
    """
    k = rig["scale"] / 16.0
    out = []
    for p in rig["parts"]:
        if p["parent"] >= 0:
            pp, pr, ps = out[p["parent"]]
        else:
            pp, pr, ps = np.zeros(3), [0.0, 0.0, 0.0, 1.0], 1.0
        off = np.array(p["pivot"], dtype=np.float64)
        rot = quat_mul(pr, quat_yxz(p["rot"]))
        scl = ps * p["scale"]
        for a in p["anims"]:
            w = 1.0 if a["when"] == "always" else move_w if a["when"] == "moving" else 1 - move_w
            t = walk if a["when"] == "moving" else time
            ax = a["axis"]
            if a["type"] == "strike":
                v = a["amp"] * strike_envelope(strike_ticks / a["speed"]) if strike_ticks >= 0 else 0.0
                rot = quat_mul(rot, quat_axis(ax, v))
                continue
            if a["type"] == "spin":
                rot = quat_mul(rot, quat_axis(ax, (360.0 * a["speed"] * t) % 360.0))
                continue
            v = w * a["amp"] * wave_value(a["wave"], a["speed"] * t + a["phase"])
            if a["type"] == "swing":
                rot = quat_mul(rot, quat_axis(ax, v))
            elif a["type"] == "bob":
                off["xyz".index(ax)] += v
            elif a["type"] == "pulse":
                scl *= 1 + v
        pos = pp + quat_rot(pr, off) * ps * k
        out.append((pos, rot, scl))
    return [(pos, rot, scl * rig["scale"]) for pos, rot, scl in out]
