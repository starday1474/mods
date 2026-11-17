"""Catalogue, analyse, render and verify Epic Fight animations shipped in mod jars.

Usage:
  python3 tools/anim/catalog.py list <jar> [--prefix biped/combat] [--threshold 5]
  python3 tools/anim/catalog.py analyse <jar|ns:path|file.json> [<entry>] [--json]
  python3 tools/anim/catalog.py render <jar> <entry> <out.gif> [--offhand none|shield|sword] [--fps 30]
  python3 tools/anim/catalog.py verify <doc.md> [<doc.md> ...]
  python3 tools/anim/catalog.py previews <doc.md> [--out build/ref-preview] [--force]

* ``<entry>`` is the animation path as Epic Fight registers it (``biped/combat/sword_auto1``) or the full
  zip entry (``assets/<ns>/animmodels/animations/<path>.json``).
* ``list`` prints a TSV: ``id  path  length  keys  kps  joints_moved``.  ``keys`` = most keys on any one
  track, ``kps`` = keys/length, ``joints_moved`` = biped joints whose rotation leaves its first-frame
  rotation by more than ``--threshold`` degrees at some sampled time.  ``*/data/*`` (trail/layer data),
  ``*/pov/*`` (first-person camera clips) and files that are not matrix-format animations are skipped.
* ``analyse`` prints timing (phase estimate from the Tool_R tip speed), per-region joint participation,
  root motion and follow-through numbers (see :func:`analyse`).
* An animation ID is ``<namespace>:<path>``; the jar for a namespace is found in ``.cache/jars``
  (:data:`NAMESPACE_JARS`).  ``verify`` checks that every such ID in the given Markdown files (a) has an
  animation file in that jar and (b) appears as a string constant in the jar's classes (EF registers
  ``builder.nextAccessor("<path>", ...)`` under the mod's namespace), and exits 1 otherwise.
* ``previews`` renders every ID found in Markdown table rows into ``<out>/<ns>__<path with / -> _>.gif``
  (30 fps, lowered until the GIF is <= 600KB; existing GIFs are kept unless ``--force``) and writes
  ``<out>/index.json`` = ``[{"id","mod","path","gif","role","offhand"[,"fps"]}]``, one entry per ID
  (role = section heading / first cell of each row it appears in, joined with ``; ``).

Jar files are only read (zipfile); nothing from them is written into the repository.
"""
from __future__ import annotations

import argparse
import json
import math
import re
import sys
import tempfile
import zipfile
from functools import lru_cache
from pathlib import Path

import numpy as np

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from tools.anim import efanim  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
JAR_DIR = REPO / ".cache/jars"
EF_JAR_GLOB = "epic-fight-*.jar"
ARM_ENTRY = "assets/epicfight/animmodels/entity/biped.json"
# namespace -> (jar glob in JAR_DIR, human name of the mod)
NAMESPACE_JARS = {
    "epicfight": ("epic-fight-*.jar", "Epic Fight"),
    "epicfightx": ("epicfight-extra-*.jar", "Epic Fight Extra"),
    "wom": ("WeaponsOfMiracles-*.jar", "Weapons of Miracles"),
}
ID_RE = re.compile(r"\b(epicfight|epicfightx|wom):(biped/[a-z0-9_/]+[a-z0-9_])")
ENTRY_RE = re.compile(r"^assets/([a-z0-9_.-]+)/animmodels/animations/(.+)\.json$")
SAMPLE_HZ = 60
JOINTS = list(efanim.BIPED_JOINTS)
REGIONS = {
    "hips": ["Root"],
    "torso": ["Torso", "Chest"],
    "head": ["Head"],
    "legs": ["Thigh_R", "Leg_R", "Knee_R", "Thigh_L", "Leg_L", "Knee_L"],
    "arm_r": ["Shoulder_R", "Arm_R", "Elbow_R", "Hand_R", "Tool_R"],
    "arm_l": ["Shoulder_L", "Arm_L", "Elbow_L", "Hand_L", "Tool_L"],
}
TIP = np.array([0.0, 0.0, -1.0])  # Tool_R local -Z = blade (preview.BLADE_AXIS)


# ---------------------------------------------------------------- jars and entries

def jar_for(namespace: str, jar_dir: Path = JAR_DIR) -> Path:
    if namespace not in NAMESPACE_JARS:
        raise KeyError(f"unknown namespace {namespace!r}")
    hits = sorted(Path(jar_dir).glob(NAMESPACE_JARS[namespace][0]))
    if not hits:
        raise FileNotFoundError(f"no jar for {namespace} in {jar_dir}")
    return hits[-1]


def entry_for(namespace: str, path: str) -> str:
    return f"assets/{namespace}/animmodels/animations/{path}.json"


def split_entry(entry: str):
    """'assets/ns/animmodels/animations/p.json' -> (ns, p); None if not an animation entry."""
    m = ENTRY_RE.match(entry)
    return (m[1], m[2]) if m else None


def _norm_entry(jar: Path, entry: str) -> str:
    if entry.startswith("assets/"):
        return entry
    if ":" in entry:
        ns, p = entry.split(":", 1)
        return entry_for(ns, p)
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            s = split_entry(n)
            if s and s[1] == entry:
                return n
    raise KeyError(f"{entry} not in {jar.name}")


def _is_anim_path(path: str) -> bool:
    """False for EF's per-animation data folders and first-person (pov) camera animations."""
    dirs = path.split("/")[:-1]
    return "data" not in dirs and "pov" not in dirs


@lru_cache(maxsize=None)
def registered_paths(jar: str) -> frozenset:
    """Animation-looking string constants (CONSTANT_Utf8 'biped/...') in the jar's classes."""
    out = set()
    pat = re.compile(rb"\x01(..)(biped/[A-Za-z0-9_/]+)", re.S)
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if not n.endswith(".class"):
                continue
            data = z.read(n)
            for m in pat.finditer(data):
                if int.from_bytes(m[1], "big") == len(m[2]):
                    out.add(m[2].decode())
    return frozenset(out)


@lru_cache(maxsize=None)
def jar_animation_paths(jar: str) -> dict:
    """{path: namespace} for every animation file in the jar."""
    out = {}
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            s = split_entry(n)
            if s and _is_anim_path(s[1]):
                out[s[1]] = s[0]
    return out


# ---------------------------------------------------------------- armature and sampling

@lru_cache(maxsize=1)
def biped():
    """(Armature, mesh json) of the EF biped, read from the EF jar."""
    jar = jar_for("epicfight")
    with zipfile.ZipFile(jar) as z:
        raw = z.read(ARM_ENTRY).decode()
    with tempfile.TemporaryDirectory() as d:
        p = Path(d, "arm.json")
        p.write_text(raw)
        return efanim.load_armature(p), json.loads(raw)


def parse_animation(data: dict) -> efanim.Animation:
    tracks = {}
    for t in data["animation"]:
        tracks[t["name"]] = efanim.Track([float(x) for x in t["time"]],
                                         [np.array(m, dtype=float).reshape(4, 4) for m in t["transform"]])
    return efanim.Animation(tracks)


def read_animation(jar: Path, entry: str) -> efanim.Animation:
    with zipfile.ZipFile(jar) as z:
        return parse_animation(json.loads(z.read(entry)))


def load_any(spec: str, entry: str | None = None) -> tuple[str, efanim.Animation]:
    """spec = jar path (+entry), 'ns:path' ID, or a local EF animation .json file."""
    p = Path(spec)
    if p.suffix == ".jar":
        e = _norm_entry(p, entry)
        return e, read_animation(p, e)
    if p.suffix == ".json" and p.exists():
        return str(p), parse_animation(json.loads(p.read_text()))
    ns, path = spec.split(":", 1)
    jar = jar_for(ns)
    e = entry_for(ns, path)
    return e, read_animation(jar, e)


def length_of(anim) -> float:
    return max(tr.times[-1] for tr in anim.tracks.values())


def _sample_all(anim, arm, hz=SAMPLE_HZ):
    from tools.anim import preview
    length = length_of(anim)
    n = max(2, int(round(length * hz)) + 1)
    ts = np.linspace(0, length, n)
    locs = [preview.sample(anim, float(t), arm) for t in ts]
    return ts, locs


def _angle(a, b) -> float:
    """Angle in degrees between two rotation matrices (3x3 or 4x4)."""
    r = a[:3, :3].T @ b[:3, :3]
    c = (np.trace(r) - 1) / 2
    return math.degrees(math.acos(max(-1.0, min(1.0, c))))


def joint_ranges(anim, arm=None, ts_locs=None) -> dict:
    """Per joint: max angle (deg) between its local rotation at any time and at t=0."""
    if arm is None:
        arm = biped()[0]
    ts, locs = ts_locs or _sample_all(anim, arm, hz=30)
    out = {}
    for j in JOINTS:
        if j not in anim.tracks:
            out[j] = 0.0
            continue
        r0 = locs[0][j]
        out[j] = max(_angle(r0, L[j]) for L in locs)
    return out


def summary(anim, threshold=5.0, arm=None) -> dict:
    keys = max(len(tr.times) for tr in anim.tracks.values())
    length = length_of(anim)
    rng = joint_ranges(anim, arm)
    return {"length": round(length, 4), "keys": keys,
            "kps": round(keys / length, 1) if length > 0 else 0.0,
            "joints_moved": sum(1 for v in rng.values() if v > threshold)}


# ---------------------------------------------------------------- detailed analysis

def _yaw_pitch(f):
    return math.degrees(math.atan2(-f[0], f[1])), math.degrees(math.asin(max(-1, min(1, f[2]))))


def _unwrap_deg(a):
    return np.degrees(np.unwrap(np.radians(np.asarray(a))))


def analyse(anim, arm=None, hz=SAMPLE_HZ) -> dict:
    """Numbers used in docs/design/motion-reference.md.

    * phase (fractions of length): Tool_R blade-tip speed relative to Root; contact = contiguous window
      around the peak where speed >= 50% of peak, antic = before it, recovery = after it.  ``hits`` =
      number of separate windows above 50% of peak (multi-hit combos).
    * accel_share = (peak time - contact start) / contact duration (< 0.5: fast build-up, ease-out strike).
    * settle = seconds from contact end until tip speed stays below 15% of peak.
    * follow_through = tip path length after contact / tip path length during contact.
    * torso_yaw / torso_pitch: range (max-min, deg) of Chest forward relative to Root (twist / lean);
      head_yaw: Head forward relative to Chest; hips_yaw: Root world yaw range; head_world_yaw: Head
      world yaw range (small while hips turn = the head stays on the target).
    * overshoot_yaw: degrees the chest keeps turning in the swing direction after contact end.
    * region_max: largest per-joint rotation excursion (deg, vs t=0) in each body region.
    * root: forward (+Y) and up (+Z) displacement, max rise, min dip (metres, world).
    """
    if arm is None:
        arm = biped()[0]
    ts, locs = _sample_all(anim, arm, hz)
    length = float(ts[-1])
    worlds = [efanim.world_matrices(arm, L) for L in locs]
    root_p = np.array([w["Root"][:3, 3] for w in worlds])
    tip = np.array([(w["Tool_R"][:3, :3] @ TIP + w["Tool_R"][:3, 3]) - w["Root"][:3, 3] for w in worlds])
    dt = ts[1] - ts[0]
    speed = np.r_[0.0, np.linalg.norm(np.diff(tip, axis=0), axis=1) / dt]
    pk = int(np.argmax(speed))
    peak = float(speed[pk])
    hot = speed >= 0.5 * peak
    cs = pk
    while cs > 0 and hot[cs - 1]:
        cs -= 1
    ce = pk
    while ce < len(ts) - 1 and hot[ce + 1]:
        ce += 1
    hits = int(np.sum(np.diff(np.r_[0, hot.astype(int)]) == 1))
    quiet = speed < 0.15 * peak
    st = ce
    for i in range(ce, len(ts)):
        if not quiet[i:].all():
            continue
        st = i
        break
    else:
        st = len(ts) - 1
    seg = np.r_[0.0, np.linalg.norm(np.diff(tip, axis=0), axis=1)]
    during = float(seg[cs + 1:ce + 1].sum())
    after = float(seg[ce + 1:].sum())

    def world_yp(j):
        """Yaw/pitch (deg, unwrapped) of world forward (+Y) carried by joint j's rotation since t=0."""
        r0 = worlds[0][j][:3, :3]
        yp = [_yaw_pitch(w[j][:3, :3] @ r0.T @ np.array([0, 1.0, 0])) for w in worlds]
        y, p = zip(*yp)
        return _unwrap_deg(y), np.asarray(p)

    ry, rp = world_yp("Root")
    cy, cp = world_yp("Chest")
    yh, _ = world_yp("Head")
    ty, tp, hy = cy - ry, cp - rp, yh - cy
    sign = np.sign(ty[ce] - ty[cs]) or 1.0
    overshoot = float(max(0.0, np.max(sign * (ty[ce:] - ty[ce]))))
    rng = joint_ranges(anim, arm, (ts, locs))
    keys = max(len(tr.times) for tr in anim.tracks.values())
    r = lambda x: round(float(x), 3)  # noqa: E731
    return {
        "length": r(length), "keys": keys, "kps": r(keys / length),
        "joints_moved": sum(1 for v in rng.values() if v > 5),
        "phase": {"antic": r(ts[cs] / length), "contact": r((ts[ce] - ts[cs]) / length),
                  "recovery": r((length - ts[ce]) / length)},
        "contact_s": [r(ts[cs]), r(ts[ce])], "hits": hits,
        "tip_peak_speed": r(peak),
        "accel_share": r((ts[pk] - ts[cs]) / max(ts[ce] - ts[cs], dt)),
        "settle_s": r(ts[st] - ts[ce]),
        "follow_through": r(after / during if during > 1e-9 else 0.0),
        "torso_yaw": r(np.ptp(ty)), "torso_pitch": r(np.ptp(tp)), "head_yaw": r(np.ptp(hy)),
        "hips_yaw": r(np.ptp(ry)), "head_world_yaw": r(np.ptp(yh)), "overshoot_yaw": r(overshoot),
        "region_max": {k: r(max(rng[j] for j in v)) for k, v in REGIONS.items()},
        "root": {"fwd": r(root_p[-1, 1] - root_p[0, 1]), "up": r(root_p[-1, 2] - root_p[0, 2]),
                 "max_rise": r(root_p[:, 2].max() - root_p[0, 2]),
                 "min_dip": r(root_p[:, 2].min() - root_p[0, 2]),
                 "max_fwd": r(root_p[:, 1].max() - root_p[0, 1])},
    }


# ---------------------------------------------------------------- list / render

def list_rows(jar: Path, prefix: str = "", threshold: float = 5.0):
    arm = biped()[0]
    rows = []
    with zipfile.ZipFile(jar) as z:
        for n in sorted(z.namelist()):
            s = split_entry(n)
            if not s or not _is_anim_path(s[1]) or not s[1].startswith(prefix):
                continue
            try:
                data = json.loads(z.read(n))
            except (ValueError, UnicodeDecodeError):
                continue
            if not isinstance(data, dict) or not data.get("animation") \
                    or not isinstance(data["animation"][0]["transform"][0], list):
                continue
            sm = summary(parse_animation(data), threshold, arm)
            rows.append({"id": f"{s[0]}:{s[1]}", "path": s[1], **sm})
    return rows


def render(jar: Path, entry: str, out: Path, offhand="none", fps=30):
    from tools.anim import preview
    arm, mesh = biped()
    e = _norm_entry(Path(jar), entry)
    anim = read_animation(Path(jar), e)
    preview.render_gif(arm, mesh, anim, out, fps=fps, offhand=offhand)
    return e


# ---------------------------------------------------------------- markdown: verify / previews

def ids_in_markdown(text: str):
    """[(id, ns, path, role)] for every animation ID, role = '<heading> / <first table cell>'."""
    out, heading = [], ""
    for line in text.splitlines():
        if line.startswith("#"):
            heading = line.lstrip("#").strip()
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")] if line.lstrip().startswith("|") else []
        role = f"{heading} / {cells[0].strip('`* ')}" if cells else heading
        for m in ID_RE.finditer(line):
            out.append((f"{m[1]}:{m[2]}", m[1], m[2], role, bool(cells)))
    return out


def verify_ids(ids, jar_dir: Path = JAR_DIR):
    """Return list of (id, reason) problems."""
    bad = []
    for i, ns, path, *_ in ids:
        try:
            jar = str(jar_for(ns, jar_dir))
        except (KeyError, FileNotFoundError) as e:
            bad.append((i, str(e)))
            continue
        files = jar_animation_paths(jar)
        if files.get(path) != ns:
            bad.append((i, f"no animation file {entry_for(ns, path)} in {Path(jar).name}"))
        elif path not in registered_paths(jar):
            bad.append((i, f"file exists but {path!r} is not registered in {Path(jar).name} classes"))
    return bad


def offhand_for(role: str, path: str) -> str:
    low = (role + " " + path).lower()
    if "dual" in low or "쌍검" in low:
        return "sword"
    if "tank" in low or "balanced" in low or "탱킹" in low or "밸런스" in low:
        return "shield"
    return "none"


def preview_index(md_files) -> list:
    """One entry per animation ID found in Markdown table rows, in first-seen order.  ``role`` joins
    every row the ID appears in (``; ``); the preview's off-hand item follows the first role."""
    index = {}
    for md in md_files:
        for i, ns, path, role, in_table in ids_in_markdown(Path(md).read_text()):
            if not in_table:
                continue
            if i in index:
                if role not in index[i]["role"].split("; "):
                    index[i]["role"] += "; " + role
                continue
            index[i] = {"id": i, "mod": NAMESPACE_JARS[ns][1], "path": path,
                        "gif": f"{ns}__{path.replace('/', '_')}.gif", "role": role,
                        "offhand": offhand_for(role, path)}
    return list(index.values())


def build_previews(md_files, out_dir: Path, fps_steps=(30, 20, 15, 10), max_bytes=600_000,
                   skip_existing=True):
    """Render every ID of :func:`preview_index` to ``out_dir`` (lower fps until <= max_bytes)."""
    out_dir.mkdir(parents=True, exist_ok=True)
    index = preview_index(md_files)
    for e in index:
        out = out_dir / e["gif"]
        ns = e["id"].split(":", 1)[0]
        if not (skip_existing and out.exists()):
            for f in fps_steps:
                render(jar_for(ns), entry_for(ns, e["path"]), out, e["offhand"], fps=f)
                e["fps"] = f
                if out.stat().st_size <= max_bytes:
                    break
        print(f"{e['gif']}\t{out.stat().st_size // 1024}KB", flush=True)
    (out_dir / "index.json").write_text(json.dumps(index, ensure_ascii=False, indent=1))
    return index


# ---------------------------------------------------------------- CLI

def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("list")
    p.add_argument("jar")
    p.add_argument("--prefix", default="")
    p.add_argument("--threshold", type=float, default=5.0)
    p = sub.add_parser("analyse")
    p.add_argument("spec")
    p.add_argument("entry", nargs="?")
    p.add_argument("--json", action="store_true")
    p = sub.add_parser("render")
    p.add_argument("jar")
    p.add_argument("entry")
    p.add_argument("out")
    p.add_argument("--offhand", default="none")
    p.add_argument("--fps", type=int, default=30)
    p = sub.add_parser("verify")
    p.add_argument("md", nargs="+")
    p = sub.add_parser("previews")
    p.add_argument("md", nargs="+")
    p.add_argument("--out", default=str(REPO / "build/ref-preview"))
    p.add_argument("--force", action="store_true")
    a = ap.parse_args(argv)

    if a.cmd == "list":
        print("id\tpath\tlength\tkeys\tkps\tjoints_moved")
        for r in list_rows(Path(a.jar), a.prefix, a.threshold):
            print(f"{r['id']}\t{r['path']}\t{r['length']}\t{r['keys']}\t{r['kps']}\t{r['joints_moved']}")
        return 0
    if a.cmd == "analyse":
        name, anim = load_any(a.spec, a.entry)
        res = analyse(anim)
        print(json.dumps({"entry": name, **res}, ensure_ascii=False, indent=None if a.json else 1))
        return 0
    if a.cmd == "render":
        render(Path(a.jar), a.entry, Path(a.out), a.offhand, a.fps)
        return 0
    if a.cmd == "verify":
        ids = [x for md in a.md for x in ids_in_markdown(Path(md).read_text())]
        bad = verify_ids(ids)
        for i, why in bad:
            print(f"MISSING {i}: {why}")
        print(f"{len(ids) - len(bad)}/{len(ids)} animation IDs ok ({len({x[0] for x in ids})} unique)")
        return 1 if bad or not ids else 0
    if a.cmd == "previews":
        build_previews(a.md, Path(a.out), skip_existing=not a.force)
        return 0
    return 2


if __name__ == "__main__":
    sys.exit(main())
