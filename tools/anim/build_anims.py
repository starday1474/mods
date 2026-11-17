"""Build Epic Fight animation JSON from small keyframe sources.

Usage:
  python3 tools/anim/build_anims.py SRC_DIR OUT_DIR [--preview DIR] [--check] [--jar PATH]

Each ``SRC_DIR/<name>.json`` becomes ``OUT_DIR/<name>.json`` (EF animation) and
``OUT_DIR/data/<name>.json`` (EF's ``combat/data`` layout: trail effect and/or layer; only when ``trail`` or ``layer`` is set).

Source format::

    {"length": 0.6, "base": "epicfight:biped/living/idle",
     "keys": [{"t": 0.0, "rot": {"Arm_R": [-30, 0, 10]}, "translate": {"Root": [0, 0, 0]}}, ...],
     "group": "balanced", "desc_ko": "한 줄 설명", "loop": false, "root_back": false,
     "phase": {"antic": 0.15, "contact": 0.3, "recovery": 0.45}, "trail": [0.2, 0.32],
     "offhand": "shield", "trail_joint": "Tool_R",
     "layer": {"layer": "COMPOSITE_LAYER", "priority": "MIDDLE",
               "masks": [{"livingmotion": "ALL", "type": "root_upper_joints"}]}}

* ``base``: ``epicfight:<path>`` = first frame of that EF animation
  (``assets/epicfight/animmodels/animations/<path>.json`` in the EF jar), or
  ``rest`` = armature rest pose.
* ``rot``: degrees, ``local = base @ (Rx @ Ry @ Rz)`` (see efanim.pose_local).
  ``translate``: added to the joint's local position (Root: world-ish metres,
  +Y forward, +Z up).  Joints not named in a key keep the base pose.
* Keys must start at t=0 and end at t=length; every joint gets one transform
  per key, EF interpolates between keys.
* ``group`` and ``desc_ko`` (one-line Korean description) are required (gallery).
* ``loop``: optional; first and last key must resolve to the same pose.
  ``root_back``: optional; without it Root translate y must never decrease
  (EF turns Root movement into player movement).
* ``phase`` / ``trail`` are optional (attacks only); without ``trail`` (and ``layer``) no
  ``data/<name>.json`` is written.
* ``phase`` is not written to the asset: it documents the ComboAttackAnimation
  timings in RpgAnimations.java (a test keeps them in sync).
* ``offhand``: optional, ``"none"`` (default) | ``"shield"`` | ``"sword"`` | ``"bow"``; only the
  preview uses it (draws the left-hand item on Tool_L so poses read correctly; a bow is drawn as a 1.7-block longbow with its string).
* ``layer``: optional JSON object copied verbatim into ``data/<name>.json`` next to ``trail_effects`` (the
  keys EF reads from ``combat/data``, e.g. ``layer``, ``priority``, ``masks`` as in ``bow_aim_mid``); a data
  file is written if ``trail`` or ``layer`` is set.
* ``trail_joint``: optional, ``"Tool_R"`` (default) | ``"Tool_L"``; the joint the trail
  follows (``item_skin_hand`` is ``off_hand`` for Tool_L, ``main_hand`` otherwise).
* ``bake``: optional integer keys/sec (10..60, e.g. 30).  Intermediate keys are generated on a uniform
  grid between source keys (original key times are kept exactly; t=0 and t=length are always keys);
  rotation = quaternion slerp, translation = lerp, both with the eased parameter.  Phase/trail times
  are unaffected.  Without ``bake`` the output is unchanged (plain keys, EF interpolates).
* ``ease`` (per key, needs ``bake``): ``linear`` (default) | ``in`` (cubic ease-in) | ``out`` (cubic
  ease-out) | ``inout`` (smoothstep) | ``back`` (ease-out with ~10% overshoot, Penner s=1.70158).  It
  describes the segment ARRIVING at that key (previous key -> this key); the first key's ease is unused.
  Root translation is clamped to the target (no overshoot) unless ``root_back``.
* ``--check`` builds into a temporary folder and exits 1 if any generated file
  is missing or differs from OUT_DIR.  ``--preview`` also writes ``<name>.gif``
  and ``<name>_sheet.png`` (contact sheet) per animation.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys
import tempfile
import zipfile
from pathlib import Path

import numpy as np

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from tools.anim import efanim  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
DEFAULT_JAR = REPO / ".cache/jars/epic-fight-21.17.3.1-mc1.21.1-neoforge.jar"
OFFHANDS = ("none", "shield", "sword", "bow")
TRAIL_JOINTS = ("Tool_R", "Tool_L")
EASES = ("linear", "in", "out", "inout", "back")
BAKE_RANGE = (10, 60)
_BACK_S = 1.70158
ARM_ENTRY = "assets/epicfight/animmodels/entity/biped.json"


def _jar(jar=None) -> Path:
    return Path(jar or os.environ.get("EF_JAR", DEFAULT_JAR))


def _read_jar(jar, entry) -> bytes:
    with zipfile.ZipFile(_jar(jar)) as z:
        return z.read(entry)


def _load_from_bytes(loader, data: bytes):
    with tempfile.TemporaryDirectory() as d:
        p = Path(d, "x.json")
        p.write_bytes(data)
        return loader(p)


def load_ef_armature(jar=None) -> efanim.Armature:
    return _load_from_bytes(efanim.load_armature, _read_jar(jar, ARM_ENTRY))


def load_base(spec: str, arm: efanim.Armature, jar=None) -> dict:
    if spec == "rest":
        return {j: m.copy() for j, m in arm.rest_local.items()}
    if not spec.startswith("epicfight:"):
        raise ValueError(f"unsupported base {spec!r} (use 'rest' or 'epicfight:<path>')")
    entry = f"assets/epicfight/animmodels/animations/{spec.split(':', 1)[1]}.json"
    anim = _load_from_bytes(efanim.load_animation, _read_jar(jar, entry))
    return {j: (anim.tracks[j].local[0].copy() if j in anim.tracks else arm.rest_local[j].copy())
            for j in arm.joints}


def validate_source(name: str, src: dict) -> None:
    def err(msg):
        raise ValueError(f"{name}: {msg}")

    length = float(src.get("length", 0))
    if length <= 0:
        err("length must be > 0")
    keys = src.get("keys") or []
    if len(keys) < 2:
        err("need at least two keys")
    times = [float(k["t"]) for k in keys]
    if times[0] != 0.0 or abs(times[-1] - length) > 1e-9:
        err(f"keys must start at 0 and end at length {length} (got {times[0]}..{times[-1]})")
    if any(b <= a for a, b in zip(times, times[1:])):
        err("key times must increase")
    joints = set(efanim.BIPED_JOINTS)
    for k in keys:
        for kind in ("rot", "translate"):
            for j, v in (k.get(kind) or {}).items():
                if j not in joints:
                    err(f"unknown joint {j!r} at t={k['t']}")
                if len(v) != 3:
                    err(f"{kind} {j} at t={k['t']} needs 3 numbers")
    for field in ("group", "desc_ko"):
        if not isinstance(src.get(field), str) or not src[field].strip():
            err(f"missing required field {field!r}")
    if "phase" in src:
        ph = src["phase"] or {}
        if not (0 <= ph.get("antic", -1) <= ph.get("contact", -1) <= ph.get("recovery", -1) <= length):
            err("phase needs 0 <= antic <= contact <= recovery <= length")
    if "trail" in src:
        tr = src["trail"]
        if not (isinstance(tr, list) and len(tr) == 2 and 0 <= tr[0] < tr[1] <= length):
            err("trail must be [start, end] within the animation length")
    if src.get("offhand", "none") not in OFFHANDS:
        err(f"offhand {src['offhand']!r} must be one of {', '.join(OFFHANDS)}")
    if "bake" in src:
        b = src["bake"]
        if isinstance(b, bool) or not isinstance(b, int) or not BAKE_RANGE[0] <= b <= BAKE_RANGE[1]:
            err(f"bake must be an integer keys/sec in {BAKE_RANGE[0]}..{BAKE_RANGE[1]}, got {b!r}")
    for k in keys:
        if "ease" in k:
            if k["ease"] not in EASES:
                err(f"ease {k['ease']!r} at t={k['t']} must be one of {', '.join(EASES)}")
            if "bake" not in src:
                err(f"ease at t={k['t']} needs a source-level \"bake\" (easing can't be expressed without baked keys)")
    if "layer" in src and not isinstance(src["layer"], dict):
        err("layer must be a JSON object")
    if src.get("trail_joint", "Tool_R") not in TRAIL_JOINTS:
        err(f"trail_joint {src['trail_joint']!r} must be one of {', '.join(TRAIL_JOINTS)}")
    if not src.get("root_back"):
        ys = [(k.get("translate") or {}).get("Root", [0, 0, 0])[1] for k in keys]
        if any(b < a - 1e-9 for a, b in zip(ys, ys[1:])):
            err(f"Root moves backwards (y {ys}); set \"root_back\": true if intended")
    if src.get("loop"):
        def resolved(k, kind, default):
            return {j: list(map(float, (k.get(kind) or {}).get(j, default))) for j in joints}
        for kind in ("rot", "translate"):
            a, b = resolved(keys[0], kind, (0, 0, 0)), resolved(keys[-1], kind, (0, 0, 0))
            bad = [j for j in joints if any(abs(x - y) > 1e-6 for x, y in zip(a[j], b[j]))]
            if bad:
                err(f"loop: first and last key differ in {kind} of {', '.join(sorted(bad))}")


def ease_value(name: str, u: float) -> float:
    """Eased progress for u in [0,1]; ``back`` overshoots 1 by ~10% (Penner back-out)."""
    if name == "in":
        return u * u * u
    if name == "out":
        return 1 - (1 - u) ** 3
    if name == "inout":
        return u * u * (3 - 2 * u)
    if name == "back":
        c = _BACK_S + 1
        return 1 + c * (u - 1) ** 3 + _BACK_S * (u - 1) ** 2
    return u


def _blend(a, b, f, f_translate):
    m = np.eye(4)
    m[:3, :3] = efanim.quat_to_matrix(efanim.slerp(efanim.quat_from_matrix(a), efanim.quat_from_matrix(b), f))
    m[:3, 3] = (1 - f_translate) * a[:3, 3] + f_translate * b[:3, 3]
    return m


def _bake(src: dict, times: list, poses: list):
    """Insert keys on a uniform ``bake`` Hz grid between source keys.

    ``ease`` on key i shapes the segment arriving at it (key i-1 -> key i).  Resolved
    local matrices are interpolated (slerp / lerp with the eased parameter).  Root
    translation never overshoots unless ``root_back`` (EF turns Root motion into movement).
    """
    hz = int(src["bake"])
    root_back = bool(src.get("root_back"))
    out_t, out_p = [times[0]], [poses[0]]
    for i in range(1, len(times)):
        t0, t1 = times[i - 1], times[i]
        ease = src["keys"][i].get("ease", "linear")
        k = math.floor(t0 * hz + 1e-9) + 1
        while k / hz < t1 - 1e-9:
            t = round(k / hz, 6)
            u = (t - t0) / (t1 - t0)
            f = ease_value(ease, u)
            pose = {}
            for j in poses[i]:
                ft = f if (root_back or j != "Root") else min(max(f, 0.0), 1.0)
                pose[j] = _blend(poses[i - 1][j], poses[i][j], f, ft)
            out_t.append(t)
            out_p.append(pose)
            k += 1
        out_t.append(t1)
        out_p.append(poses[i])
    return out_t, out_p


def build_animation(src: dict, arm: efanim.Armature, base: dict) -> efanim.Animation:
    times, poses = [], []
    for k in src["keys"]:
        rot = {j: tuple(float(x) for x in v) for j, v in (k.get("rot") or {}).items()}
        tr = {j: tuple(float(x) for x in v) for j, v in (k.get("translate") or {}).items()}
        times.append(float(k["t"]))
        poses.append(efanim.pose_local(arm, base, rot, tr))
    if "bake" in src:
        times, poses = _bake(src, times, poses)
    return efanim.Animation({j: efanim.Track(list(times), [p[j] for p in poses]) for j in arm.joints})


def trail_json(src: dict) -> dict:
    a, b = src["trail"]
    joint = src.get("trail_joint", "Tool_R")
    return {"trail_effects": [{"start_time": float(a), "end_time": float(b), "joint": joint,
                               "item_skin_hand": "off_hand" if joint == "Tool_L" else "main_hand"}]}


def data_json(src: dict) -> dict | None:
    """Content of ``data/<name>.json`` (trail effects and/or layer), or None if neither is set."""
    out: dict = {}
    if "trail" in src:
        out.update(trail_json(src))
    if "layer" in src:
        out.update(src["layer"])
    return out or None


def _contact_sheet(images, out, cols=3, step=2):
    from PIL import Image
    sel = images[::step]
    if sel[-1] is not images[-1]:
        sel.append(images[-1])
    w, h = sel[0].size
    rows = (len(sel) + cols - 1) // cols
    sheet = Image.new("RGB", (w * cols, h * rows), "white")
    for i, im in enumerate(sel):
        sheet.paste(im, ((i % cols) * w, (i // cols) * h))
    sheet.save(out)


def build_all(src_dir: Path, out_dir: Path, jar=None, preview_dir: Path | None = None) -> list:
    """Writes generated files under out_dir; returns their paths relative to out_dir."""
    srcs = sorted(Path(src_dir).glob("*.json"))
    if not srcs:
        raise ValueError(f"no sources in {src_dir}")
    arm = load_ef_armature(jar)
    bases: dict = {}
    written = []
    (out_dir / "data").mkdir(parents=True, exist_ok=True)
    mesh = json.loads(_read_jar(jar, ARM_ENTRY)) if preview_dir else None
    for p in srcs:
        src = json.loads(p.read_text())
        validate_source(p.stem, src)
        if src["base"] not in bases:
            try:
                bases[src["base"]] = load_base(src["base"], arm, jar)
            except KeyError as e:
                raise ValueError(f"{p.stem}: base {src['base']!r} not found in EF jar ({e})") from None
        anim = build_animation(src, arm, bases[src["base"]])
        efanim.save_animation(anim, out_dir / f"{p.stem}.json", arm)
        written.append(Path(f"{p.stem}.json"))
        data = data_json(src)
        if data is not None:
            (out_dir / "data" / f"{p.stem}.json").write_text(json.dumps(data, indent=2) + "\n")
            written.append(Path("data", f"{p.stem}.json"))
        if preview_dir:
            from tools.anim import preview
            preview_dir.mkdir(parents=True, exist_ok=True)
            images = preview.render_gif(arm, mesh, anim, preview_dir / f"{p.stem}.gif",
                                        offhand=src.get("offhand", "none"))
            _contact_sheet(images, preview_dir / f"{p.stem}_sheet.png")
    return written


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("src")
    ap.add_argument("out")
    ap.add_argument("--preview", type=Path)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--jar")
    a = ap.parse_args(argv)
    out = Path(a.out)
    if not a.check:
        files = build_all(Path(a.src), out, a.jar, a.preview)
        print(f"built {sum(1 for f in files if f.parent.name != 'data')} animation(s) into {out}")
        return 0
    with tempfile.TemporaryDirectory() as d:
        files = build_all(Path(a.src), Path(d), a.jar, a.preview)
        stale = [str(f) for f in files
                 if not (out / f).is_file() or (out / f).read_bytes() != (Path(d) / f).read_bytes()]
    if stale:
        print("generated animations are out of date (rerun build_anims.py): " + ", ".join(stale))
        return 1
    print(f"{sum(1 for f in files if f.parent.name != 'data')} animation(s) up to date")
    return 0


if __name__ == "__main__":
    sys.exit(main())
