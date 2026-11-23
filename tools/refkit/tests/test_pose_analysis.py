import sys

import numpy as np
import pytest

from refkit import cli, store
from refkit.pose import analysis


def test_angle_right():
    a = np.array([[1.0, 0, 0]])
    b = np.array([[0.0, 0, 0]])
    c = np.array([[0.0, 1, 0]])
    assert analysis.angle(a, b, c)[0] == pytest.approx(90.0)


def test_angle_nan_propagates():
    a = np.array([[1.0, 0, 0], [np.nan, 0, 0]])
    b = np.zeros((2, 3))
    c = np.array([[0.0, 1, 0], [0.0, 1, 0]])
    out = analysis.angle(a, b, c)
    assert out[0] == pytest.approx(90.0)
    assert np.isnan(out[1])


def _pose(n=3, twist_deg=0.0):
    lm = np.zeros((n, 33, 3))
    t = np.radians(twist_deg)
    lm[:, 23] = [-0.1, 0.0, 0.0]
    lm[:, 24] = [0.1, 0.0, 0.0]
    lm[:, 11] = [-0.2 * np.cos(t), -0.5, -0.2 * np.sin(t)]
    lm[:, 12] = [0.2 * np.cos(t), -0.5, 0.2 * np.sin(t)]
    return lm


def test_torso_twist():
    out = analysis.joint_angles(_pose(twist_deg=30.0))
    assert out["torso_twist"][0] == pytest.approx(30.0, abs=0.5)
    assert out["torso_lean"][0] == pytest.approx(0.0, abs=0.5)
    assert set(out) >= {"elbow_l", "elbow_r", "shoulder_l", "shoulder_r",
                        "knee_l", "knee_r", "torso_twist", "torso_lean"}


def _hit_pose(fps=30, n=60):
    lm = np.zeros((n, 33, 3))
    for i in range(n):
        t = i / fps
        lm[i, 16, 0] = float(np.clip((t - 0.5) / 0.2, 0, 1))
    return lm


def test_suggest_marks():
    m = analysis.suggest_marks(_hit_pose(), 30.0)
    assert 0.5 <= m["impact"] <= 0.7
    assert m["start"] < m["impact"] < m["settle"]


def test_suggest_marks_too_few_frames():
    assert analysis.suggest_marks(np.full((4, 33, 3), np.nan), 30.0) is None


def test_speed_nan_neighbor():
    lm = np.zeros((5, 33, 3))
    lm[:, 16, 0] = np.arange(5) * 0.1
    lm[2] = np.nan
    s = analysis.speed(lm, 16, 10.0)
    assert np.isnan(s[1]) and np.isnan(s[2]) and np.isnan(s[3])
    assert s[0] == pytest.approx(1.0)


def test_summarize_detection_warning():
    lm = _hit_pose(n=100)
    lm[:60] = np.nan
    s = analysis.summarize(lm, 30.0)
    assert s["frames"] == 100
    assert s["detected"] == pytest.approx(0.4)
    assert "warning" in s
    allnan = analysis.summarize(np.full((10, 33, 3), np.nan), 30.0)
    assert allnan["angles"] == {}
    assert allnan["suggested_marks"] is None
    assert allnan["detected"] == 0.0


def test_summarize_values_rounded():
    s = analysis.summarize(_pose(n=10, twist_deg=30.0), 30.0)
    a = s["angles"]["torso_twist"]
    assert a["min"] == a["max"] == pytest.approx(30.0, abs=0.5)
    assert a["range"] == 0.0
    assert "warning" not in s


def test_pose_cli_rejects_game_tag(tmp_env, capsys):
    store.upsert("m", store.Ref(id="v-01", kind="video", url="u", tags=["game"]))
    rc = cli.main(["pose", "--motion", "m", "--id", "v-01"])
    assert rc == 2
    cap = capsys.readouterr()
    assert "자세 추정은 실제 사람 영상에만 씁니다" in cap.out + cap.err


def test_pose_cli_without_mediapipe(tmp_env, capsys, monkeypatch):
    store.upsert("m", store.Ref(id="v-01", kind="video", url="u"))
    monkeypatch.setitem(sys.modules, "mediapipe", None)
    rc = cli.main(["pose", "--motion", "m", "--id", "v-01"])
    assert rc == 2
    cap = capsys.readouterr()
    assert "pip install -e tools/refkit[pose]" in cap.out + cap.err
