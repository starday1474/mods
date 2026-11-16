import json
import os
import zipfile
from pathlib import Path

import numpy as np
import pytest

from tools.anim import efanim

REPO = Path(__file__).resolve().parents[3]
JAR = Path(os.environ.get("EF_JAR", REPO / ".cache/jars/epic-fight-21.17.3.1-mc1.21.1-neoforge.jar"))
ARM = "assets/epicfight/animmodels/entity/biped.json"
ANIM = "assets/epicfight/animmodels/animations/biped/combat/sword_auto1.json"

JOINTS = ("Root Thigh_R Leg_R Knee_R Thigh_L Leg_L Knee_L Torso Chest Head Shoulder_R Arm_R "
          "Hand_R Tool_R Elbow_R Shoulder_L Arm_L Hand_L Tool_L Elbow_L").split()


@pytest.fixture(scope="module")
def files(tmp_path_factory):
    if not JAR.exists():
        pytest.skip("EF jar not available")
    d = tmp_path_factory.mktemp("ef")
    with zipfile.ZipFile(JAR) as z:
        a, b = d / "biped.json", d / "sword_auto1.json"
        a.write_bytes(z.read(ARM))
        b.write_bytes(z.read(ANIM))
    return a, b


@pytest.fixture(scope="module")
def arm(files):
    return efanim.load_armature(files[0])


def test_armature_has_20_joints(arm):
    assert arm.joints == JOINTS


def test_roundtrip_sword_auto1(files, tmp_path):
    a = efanim.load_animation(files[1])
    out = tmp_path / "o.json"
    efanim.save_animation(a, out, arm=efanim.load_armature(files[0]))
    b = efanim.load_animation(out)
    assert set(a.tracks) == set(b.tracks)
    for j, t in a.tracks.items():
        u = b.tracks[j]
        assert np.allclose(t.times, u.times, atol=1e-6)
        assert len(t.local) == len(u.local)
        for m, n in zip(t.local, u.local):
            assert np.abs(m - n).max() < 1e-6


def _up(arm):
    w = efanim.world_matrices(arm, arm.rest_local)
    d = w["Head"][:3, 3] - w["Root"][:3, 3]
    return int(np.argmax(np.abs(d))), d


def test_rest_pose_head_above_root(arm):
    axis, d = _up(arm)
    assert d[axis] > 0 and abs(d[axis]) > 0.1


def test_zero_rotation_is_identity(arm):
    out = efanim.pose_local(arm, arm.rest_local, {}, {})
    for j in arm.joints:
        assert np.allclose(out[j], arm.rest_local[j])


def test_arm_rotation_moves_tool(arm):
    w0 = efanim.world_matrices(arm, arm.rest_local)
    p = efanim.pose_local(arm, arm.rest_local, {"Arm_R": (90, 0, 0)}, {})
    w1 = efanim.world_matrices(arm, p)
    assert np.linalg.norm(w1["Tool_R"][:3, 3] - w0["Tool_R"][:3, 3]) >= 0.1
    assert np.allclose(w1["Head"], w0["Head"])
