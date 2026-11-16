"""Build Epic Fight animation JSON from small keyframe sources.

Usage:
  python3 tools/anim/build_anims.py SRC_DIR OUT_DIR [--preview DIR] [--check] [--jar PATH]

Each ``SRC_DIR/<name>.json`` becomes ``OUT_DIR/<name>.json`` (EF animation) and
``OUT_DIR/data/<name>.json`` (trail effect, EF's ``combat/data`` layout).

Source format::

    {"length": 0.6, "base": "epicfight:biped/living/idle",
     "keys": [{"t": 0.0, "rot": {"Arm_R": [-30, 0, 10]}, "translate": {"Root": [0, 0, 0]}}, ...],
     "phase": {"antic": 0.15, "contact": 0.3, "recovery": 0.45}, "trail": [0.2, 0.32]}

* ``base``: ``epicfight:<path>`` = first frame of that EF animation
  (``assets/epicfight/animmodels/animations/<path>.json`` in the EF jar), or
  ``rest`` = armature rest pose.
* ``rot``: degrees, ``local = base @ (Rx @ Ry @ Rz)`` (see efanim.pose_local).
  ``translate``: added to the joint's local position (Root: world-ish metres,
  +Y forward, +Z up).  Joints not named in a key keep the base pose.
* Keys must start at t=0 and end at t=length; every joint gets one transform
  per key, EF interpolates between keys.
* ``phase`` is not written to the asset: it documents the ComboAttackAnimation
  timings in RpgAnimations.java (a test keeps them in sync).
* ``--check`` builds into a temporary folder and exits 1 if any generated file
  is missing or differs from OUT_DIR.  ``--preview`` also writes ``<name>.gif``
  and ``<name>_sheet.png`` (contact sheet) per animation.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
import zipfile
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from tools.anim import efanim  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
DEFAULT_JAR = REPO / ".cache/jars/epic-fight-21.17.3.1-mc1.21.1-neoforge.jar"
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
    ph = src.get("phase") or {}
    if not (0 <= ph.get("antic", -1) <= ph.get("contact", -1) <= ph.get("recovery", -1) <= length):
        err("phase needs 0 <= antic <= contact <= recovery <= length")
    tr = src.get("trail")
    if not (isinstance(tr, list) and len(tr) == 2 and 0 <= tr[0] < tr[1] <= length):
        err("trail must be [start, end] within the animation length")


def build_animation(src: dict, arm: efanim.Armature, base: dict) -> efanim.Animation:
    times, poses = [], []
    for k in src["keys"]:
        rot = {j: tuple(float(x) for x in v) for j, v in (k.get("rot") or {}).items()}
        tr = {j: tuple(float(x) for x in v) for j, v in (k.get("translate") or {}).items()}
        times.append(float(k["t"]))
        poses.append(efanim.pose_local(arm, base, rot, tr))
    return efanim.Animation({j: efanim.Track(list(times), [p[j] for p in poses]) for j in arm.joints})


def trail_json(src: dict) -> dict:
    a, b = src["trail"]
    return {"trail_effects": [{"start_time": float(a), "end_time": float(b),
                               "joint": "Tool_R", "item_skin_hand": "main_hand"}]}


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
            bases[src["base"]] = load_base(src["base"], arm, jar)
        anim = build_animation(src, arm, bases[src["base"]])
        efanim.save_animation(anim, out_dir / f"{p.stem}.json", arm)
        (out_dir / "data" / f"{p.stem}.json").write_text(json.dumps(trail_json(src), indent=2) + "\n")
        written += [Path(f"{p.stem}.json"), Path("data", f"{p.stem}.json")]
        if preview_dir:
            from tools.anim import preview
            preview_dir.mkdir(parents=True, exist_ok=True)
            images = preview.render_gif(arm, mesh, anim, preview_dir / f"{p.stem}.gif")
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
        print(f"built {len(files) // 2} animation(s) into {out}")
        return 0
    with tempfile.TemporaryDirectory() as d:
        files = build_all(Path(a.src), Path(d), a.jar, a.preview)
        stale = [str(f) for f in files
                 if not (out / f).is_file() or (out / f).read_bytes() != (Path(d) / f).read_bytes()]
    if stale:
        print("generated animations are out of date (rerun build_anims.py): " + ", ".join(stale))
        return 1
    print(f"{len(files) // 2} animation(s) up to date")
    return 0


if __name__ == "__main__":
    sys.exit(main())
