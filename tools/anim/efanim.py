"""Read/write Epic Fight armature + animation JSON and compute poses (numpy only).

Conventions
-----------
* Matrices are 4x4, stored row-major as 16 floats in JSON; column-vector math
  (``world = parent_world @ local``), translation in column 3.
* Armature up axis: rest Head is above Root along **+Z** in the EF biped
  (Root z~0.76, Head z~1.51).
* Euler convention used by ``pose_local``: rotations about the joint's local
  X, then Y, then Z axes (intrinsic), in degrees, i.e. ``R = Rx @ Ry @ Rz``,
  post-multiplied after the base matrix: ``local = base @ R``.
  Translation offsets are added to the local position (column 3).
  Keyframes authored by later tasks use this convention.
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

# Canonical EF biped joint order, used when no armature is given to save.
BIPED_JOINTS = ("Root Thigh_R Leg_R Knee_R Thigh_L Leg_L Knee_L Torso Chest Head "
                "Shoulder_R Arm_R Hand_R Tool_R Elbow_R Shoulder_L Arm_L Hand_L "
                "Tool_L Elbow_L").split()


@dataclass
class Armature:
    joints: list
    parent: dict
    rest_local: dict


@dataclass
class Track:
    times: list
    local: list


@dataclass
class Animation:
    tracks: dict = field(default_factory=dict)


def _mat(vals):
    return np.array(vals, dtype=float).reshape(4, 4)


def load_armature(path) -> Armature:
    arm = json.loads(Path(path).read_text())["armature"]
    parent, rest = {}, {}

    def walk(node, par):
        parent[node["name"]] = par
        rest[node["name"]] = _mat(node["transform"])
        for c in node.get("children", []):
            walk(c, node["name"])

    for root in arm["hierarchy"]:
        walk(root, None)
    return Armature(list(arm["joints"]), parent, rest)


def load_animation(path) -> Animation:
    data = json.loads(Path(path).read_text())
    tracks = {}
    for t in data["animation"]:
        tracks[t["name"]] = Track([float(x) for x in t["time"]],
                                  [_mat(m) for m in t["transform"]])
    return Animation(tracks)


def save_animation(anim: Animation, path, arm: Armature | None = None) -> None:
    order = list(arm.joints) if arm is not None else list(BIPED_JOINTS)
    names = [j for j in order if j in anim.tracks]
    names += [j for j in anim.tracks if j not in names]
    out = []
    for n in names:
        t = anim.tracks[n]
        out.append({
            "name": n,
            "time": [round(float(x), 6) for x in t.times],
            "transform": [[round(float(v), 6) for v in m.reshape(16)] for m in t.local],
        })
    Path(path).write_text(json.dumps({"animation": out}))


def world_matrices(arm: Armature, local_by_joint: dict) -> dict:
    world = {}

    def get(j):
        if j not in world:
            p = arm.parent[j]
            world[j] = local_by_joint[j] if p is None else get(p) @ local_by_joint[j]
        return world[j]

    for j in arm.joints:
        get(j)
    return world


def _euler(rx, ry, rz):
    def rot(a, i, j):
        c, s = np.cos(np.radians(a)), np.sin(np.radians(a))
        m = np.eye(4)
        m[i, i] = m[j, j] = c
        m[i, j], m[j, i] = -s, s
        return m
    return rot(rx, 1, 2) @ rot(ry, 2, 0) @ rot(rz, 0, 1)


def pose_local(arm: Armature, base_local: dict, rot_deg: dict, translate: dict) -> dict:
    out = {}
    for j in arm.joints:
        m = base_local[j].copy()
        if j in rot_deg:
            m = m @ _euler(*rot_deg[j])
        if j in translate:
            m[:3, 3] += np.asarray(translate[j], dtype=float)
        out[j] = m
    return out


def quat_from_matrix(m):
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


def quat_to_matrix(q):
    w, x, y, z = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y)],
                     [2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x)],
                     [2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y)]])


def slerp(a, b, u):
    d = float(np.dot(a, b))
    if d < 0:
        b, d = -b, -d
    if d > 0.9995:
        q = a + u * (b - a)
        return q / np.linalg.norm(q)
    th = math.acos(min(1.0, d))
    return (math.sin((1 - u) * th) * a + math.sin(u * th) * b) / math.sin(th)
