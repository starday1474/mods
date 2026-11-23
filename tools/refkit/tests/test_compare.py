import json
import os
import subprocess
import time

import pytest

from refkit import compare, media, store
from refkit.store import Ref


def test_align_offsets():
    assert compare.align_offsets(0.8, 0.2) == pytest.approx((0.0, 0.6))
    assert compare.align_offsets(0.2, 0.8) == pytest.approx((0.6, 0.0))


def _size(p):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
         "stream=width,height", "-of", "csv=p=0", str(p)],
        capture_output=True, text=True, check=True).stdout.strip()
    w, h = out.split(",")
    return int(w), int(h)


def test_side_by_side(sample_video, tmp_path):
    out = tmp_path / "결과 폴더" / "compare.mp4"
    res = compare.side_by_side(sample_video, sample_video, out, 0.5, 0.0, height=240)
    assert res == out and out.is_file()
    w, h = _size(out)
    w1, h1 = _size(sample_video)
    assert h == 240 and w == 2 * w1
    assert media.probe_duration(out) == pytest.approx(3.5, abs=0.15)
    slow = out.with_name("compare_slow.mp4")
    assert slow.is_file()
    assert media.probe_duration(slow) == pytest.approx(7.0, abs=0.3)


def test_compare_skips_unmarked(tmp_env, capsys):
    store.upsert("활", Ref(id="a", kind="video", url="https://example.com/v"))
    assert compare.compare_motion("활") == []
    assert "mark" in capsys.readouterr().out


def test_render_ours_cached(tmp_env, monkeypatch):
    repo, lib = tmp_env
    (repo / "mod" / "anim").mkdir(parents=True)
    (repo / "mod" / "anim" / "bow.json").write_text(json.dumps({"length": 1.0}), encoding="utf-8")
    calls = []

    def fake_run(cmd, *a, **k):
        calls.append(cmd)
        open(cmd[-1], "wb").write(b"GIF89a")
        return subprocess.CompletedProcess(cmd, 0, "", "")

    monkeypatch.setattr(compare.subprocess, "run", fake_run)
    out = lib / "_ours" / "bow.gif"
    compare.render_ours("bow", out)
    compare.render_ours("bow", out)
    assert len(calls) == 1
    assert calls[0][1].endswith("preview.py")
    # 원본 json이 더 새로우면 다시 만든다
    j = repo / "mod" / "anim" / "bow.json"
    t = time.time() + 10
    os.utime(j, (t, t))
    compare.render_ours("bow", out)
    assert len(calls) == 2


def test_render_ours_prefers_built_json(tmp_env, monkeypatch):
    repo, lib = tmp_env
    (repo / "mod" / "anim").mkdir(parents=True)
    (repo / "mod" / "anim" / "bow.json").write_text(json.dumps({"length": 1.0}), encoding="utf-8")
    b = (repo / "mod/src/main/resources/assets/goddessrpg/animmodels/animations/biped/combat")
    b.mkdir(parents=True)
    (b / "bow.json").write_text("{}", encoding="utf-8")
    calls = []

    def fake_run(cmd, *a, **k):
        calls.append(cmd)
        open(cmd[-1], "wb").write(b"GIF89a")
        return subprocess.CompletedProcess(cmd, 0, "", "")

    monkeypatch.setattr(compare.subprocess, "run", fake_run)
    compare.render_ours("bow", lib / "_ours" / "bow.gif")
    assert calls[0][2] == str(b / "bow.json")
