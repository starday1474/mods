"""Render an Epic Fight animation on the biped mesh to a side-by-side GIF.

Usage: python3 tools/anim/preview.py <anim.json|jar:path> <out.gif>
(``jar:`` reads the entry from the EF jar, $EF_JAR or .cache/jars/...)

Blade axis: the hand-held tool (Tool_R) is drawn as a 1-block stick along
``BLADE_AXIS`` (local axis of the Tool_R joint, sign included).  Chosen from
sword_auto1: see ``BLADE_AXIS`` comment.
"""
from __future__ import annotations

import json
import math
import os
import sys
import zipfile
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
from matplotlib.backends.backend_agg import FigureCanvasAgg  # noqa: E402
from mpl_toolkits.mplot3d.art3d import Poly3DCollection  # noqa: E402
from PIL import Image  # noqa: E402

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from tools.anim import efanim  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
DEFAULT_JAR = REPO / ".cache/jars/epic-fight-21.17.3.1-mc1.21.1-neoforge.jar"
ARM_ENTRY = "assets/epicfight/animmodels/entity/biped.json"

# Local axis of Tool_R along which the blade points (index, sign): -Z.
# Reasoning (EF biped faces +Y, up is +Z, right is +X; verified from the head
# mesh: the face UV region sits on the +Y side).  Candidates were rendered on
# sword_auto1.  Tool_R local -Z points forward (+Y) at rest (the way a held
# sword points in third person), is cocked back over the shoulder at the
# wind-up, and finishes pointing low and across the body (down-left) along
# the cut direction.  +Y/-Y (the axis that varies most) pointed down from the
# raised fist at wind-up or back into the body at the end; +Z is the reverse
# of the good one.
BLADE_AXIS = (2, -1)

PART_COLORS = {
    "head": "#e8b88a", "hat": "#8a5a2b", "torso": "#3b7dd8", "jacket": "#2a5da8",
    "rightArm": "#d84a3b", "rightSleeve": "#b03428", "leftArm": "#3bb06b",
    "leftSleeve": "#2a8a52", "rightLeg": "#8a4ad8", "rightPants": "#6a34b0",
    "leftLeg": "#d8a63b", "leftPants": "#b0832a",
}


def _vertices(mesh):
    return mesh["vertices"] if "vertices" in mesh else mesh


def skin(arm, mesh, world_by_joint) -> np.ndarray:
    v = _vertices(mesh)
    pos = np.array(v["positions"]["array"], dtype=float).reshape(-1, 3)
    vcounts = v["vcounts"]["array"]
    vidx = v["vindices"]["array"]
    weights = v["weights"]["array"]
    rest = efanim.world_matrices(arm, arm.rest_local)
    skin_m = {j: world_by_joint[j] @ np.linalg.inv(rest[j]) for j in arm.joints}
    out = np.zeros_like(pos)
    k = 0
    for i, n in enumerate(vcounts):
        h = np.append(pos[i], 1.0)
        acc = np.zeros(3)
        for _ in range(n):
            j, w = arm.joints[vidx[k]], weights[vidx[k + 1]]
            k += 2
            acc += w * (skin_m[j] @ h)[:3]
        out[i] = acc
    return out


def _quat(m):
    r = m[:3, :3]
    q = np.empty(4)
    tr = np.trace(r)
    if tr > 0:
        s = math.sqrt(tr + 1) * 2
        q[:] = [s / 4, (r[2, 1] - r[1, 2]) / s, (r[0, 2] - r[2, 0]) / s, (r[1, 0] - r[0, 1]) / s]
    else:
        i = int(np.argmax(np.diag(r)))
        a, b = (i + 1) % 3, (i + 2) % 3
        s = math.sqrt(1 + r[i, i] - r[a, a] - r[b, b]) * 2
        q[0] = (r[b, a] - r[a, b]) / s
        q[1 + i] = s / 4
        q[1 + a] = (r[a, i] + r[i, a]) / s
        q[1 + b] = (r[b, i] + r[i, b]) / s
    return q / np.linalg.norm(q)


def _rot(q):
    w, x, y, z = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y)],
                     [2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x)],
                     [2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y)]])


def _slerp(a, b, u):
    d = float(np.dot(a, b))
    if d < 0:
        b, d = -b, -d
    if d > 0.9995:
        q = a + u * (b - a)
        return q / np.linalg.norm(q)
    th = math.acos(min(1.0, d))
    return (math.sin((1 - u) * th) * a + math.sin(u * th) * b) / math.sin(th)


def _sample_track(tr, t):
    ts = tr.times
    if t <= ts[0] or len(ts) == 1:
        return tr.local[0].copy()
    if t >= ts[-1]:
        return tr.local[-1].copy()
    i = int(np.searchsorted(ts, t, side="right")) - 1
    u = (t - ts[i]) / (ts[i + 1] - ts[i])
    a, b = tr.local[i], tr.local[i + 1]
    m = np.eye(4)
    m[:3, :3] = _rot(_slerp(_quat(a), _quat(b), u))
    m[:3, 3] = (1 - u) * a[:3, 3] + u * b[:3, 3]
    return m


def sample(anim, t, arm=None) -> dict:
    """Local matrices per joint at time t (clamped). Joints without a track
    are omitted unless ``arm`` is given, then they fall back to rest."""
    out = {j: _sample_track(tr, t) for j, tr in anim.tracks.items()}
    if arm is not None:
        for j in arm.joints:
            out.setdefault(j, arm.rest_local[j].copy())
    return out


def _faces(mesh):
    v = _vertices(mesh)
    res = []
    for name, p in v["parts"].items():
        idx = p["array"] if isinstance(p, dict) else p
        pi = idx[0::3]  # (position, uv, normal) triples per face vertex
        for k in range(0, len(pi) - 2, 3):
            res.append((name, pi[k:k + 3]))
    return res


VIEWS = {"side": (0, 0), "front": (0, 90)}  # (elev, azim); camera at +Y sees the face (model faces +Y)


def render_gif(arm, mesh, anim, out_path, fps=20, views=("side", "front")):
    length = max(tr.times[-1] for tr in anim.tracks.values())
    n = math.ceil(length * fps) + 1
    faces = _faces(mesh)
    colors = [PART_COLORS.get(nm, "#999999") for nm, _ in faces]
    tri = np.array([f for _, f in faces])
    tool_axis = np.zeros(3)
    tool_axis[BLADE_AXIS[0]] = BLADE_AXIS[1]

    frames_data = []
    for i in range(n):
        world = efanim.world_matrices(arm, sample(anim, min(i / fps, length), arm))
        frames_data.append((skin(arm, mesh, world), world["Tool_R"]))
    allp = np.concatenate([p for p, _ in frames_data])
    lo, hi = allp.min(0) - 0.15, allp.max(0) + 0.15
    for _, tm in frames_data:
        tip = tm[:3, 3] + tm[:3, :3] @ tool_axis
        lo, hi = np.minimum(lo, tip - .05), np.maximum(hi, tip + .05)
    c, r = (lo + hi) / 2, (hi - lo).max() / 2

    images = []
    for pts, tm in frames_data:
        fig = plt.figure(figsize=(4 * len(views), 4), dpi=80)
        for vi, vn in enumerate(views):
            ax = fig.add_subplot(1, len(views), vi + 1, projection="3d")
            ax.set_xlim(c[0] - r, c[0] + r)
            ax.set_ylim(c[1] - r, c[1] + r)
            ax.set_zlim(c[2] - r, c[2] + r)
            ax.set_box_aspect((1, 1, 1), zoom=1.45)
            ax.computed_zorder = False  # honour explicit zorder: stick drawn on top
            ax.view_init(*VIEWS[vn])
            ax.set_axis_off()
            ax.set_title(vn, fontsize=8)
            pc = Poly3DCollection(pts[tri], facecolors=colors, edgecolors="#222", linewidths=0.3, zorder=1)
            ax.add_collection3d(pc)
            a = tm[:3, 3]
            b = a + tm[:3, :3] @ tool_axis
            ax.plot(*zip(a, b), color="#111", linewidth=3, zorder=10)
        fig.subplots_adjust(0, 0, 1, 0.95, 0, 0)
        canvas = FigureCanvasAgg(fig)
        canvas.draw()
        images.append(Image.fromarray(np.asarray(canvas.buffer_rgba())[..., :3].copy()))
        plt.close(fig)
    pal = [im.quantize(256) for im in images]
    Path(out_path).parent.mkdir(parents=True, exist_ok=True)
    pal[0].save(out_path, save_all=True, append_images=pal[1:], duration=int(1000 / fps), loop=0)
    return images


def _read(spec):
    if spec.startswith("jar:"):
        jar = Path(os.environ.get("EF_JAR", DEFAULT_JAR))
        with zipfile.ZipFile(jar) as z:
            return z.read(spec[4:]).decode()
    return Path(spec).read_text()


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        a, b = Path(d, "arm.json"), Path(d, "anim.json")
        a.write_text(_read("jar:" + ARM_ENTRY))
        b.write_text(_read(argv[0]))
        arm, anim = efanim.load_armature(a), efanim.load_animation(b)
        mesh = json.loads(a.read_text())
    render_gif(arm, mesh, anim, argv[1])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
