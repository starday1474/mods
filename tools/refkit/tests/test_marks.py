import json
from importlib import resources

import pytest

from refkit import anim, marks, store
from refkit.cli import main
from refkit.store import Ref


def test_parse_marks_last_and_clear(tmp_path):
    p = tmp_path / "m 파일.txt"
    p.write_text("start 0.1\nimpact 0.5\nclear 0\nstart 0.2\nimpact 0.6\nsettle 1.0\njunk",
                 encoding="utf-8")
    assert marks.parse_marks_file(p) == {"start": 0.2, "impact": 0.6, "settle": 1.0}


def test_parse_marks_missing_file(tmp_path):
    assert marks.parse_marks_file(tmp_path / "없음.txt") == {}


def test_validate_order():
    assert marks.validate({"start": 0.5, "impact": 0.3, "settle": 1}) != []
    assert marks.validate({"start": 0.1, "impact": 0.3, "settle": 1}) == []
    assert marks.validate({"start": 0.1}) != []


def test_ratios():
    assert marks.ratios_ref({"start": 0, "impact": 0.25, "settle": 1}) == {
        "windup": 0.25, "recovery": 0.75, "total": 1.0}
    r = marks.ratios_ours(0.6, {"antic": 0.1, "contact": 0.2, "recovery": 0.3})
    assert r["windup"] == pytest.approx(0.3333, abs=1e-3)
    assert r["recovery"] == pytest.approx(0.6667, abs=1e-3)
    assert r["total"] == 0.6


def test_load_phase(tmp_env):
    repo, _ = tmp_env
    d = repo / "mod" / "anim"
    d.mkdir(parents=True)
    (d / "a.json").write_text(json.dumps({"length": 0.6, "phase": {"contact": 0.1}}))
    (d / "b.json").write_text(json.dumps({"length": 0.6}))
    assert anim.load_phase("a") == (0.6, {"contact": 0.1})
    assert anim.load_phase("b") == (0.6, None)
    with pytest.raises(FileNotFoundError):
        anim.load_phase("nope")


def _setup_report(repo, windups):
    d = repo / "mod" / "anim"
    d.mkdir(parents=True)
    (d / "test_shot.json").write_text(json.dumps(
        {"length": 0.6, "phase": {"antic": 0.0, "contact": 0.06, "recovery": 0.54}}))
    for i, w in enumerate(windups):
        store.upsert("test_shot", Ref(id=f"r{i}", kind="video",
                                      marks={"start": 0.0, "impact": w, "settle": 1.0}))


def test_timing_report_suggests(tmp_env):
    repo, _ = tmp_env
    _setup_report(repo, [0.5, 0.4])
    out = marks.timing_report("test_shot")
    assert "예비 동작이 짧음" in out
    assert "1.2" in out and "1.5" in out


def test_timing_report_no_diff(tmp_env):
    repo, _ = tmp_env
    _setup_report(repo, [0.1, 0.1])
    assert "짧음" not in marks.timing_report("test_shot")


def test_timing_report_errors(tmp_env):
    repo, _ = tmp_env
    (repo / "mod" / "anim").mkdir(parents=True)
    with pytest.raises(FileNotFoundError):
        marks.timing_report("zzz")


def test_timing_cli(tmp_env, capsys):
    repo, _ = tmp_env
    _setup_report(repo, [0.5])
    assert main(["timing", "--motion", "test_shot"]) == 0
    assert "예비 동작이 짧음" in capsys.readouterr().out
    assert main(["timing", "--motion", "test_shot", "--anim", "없는동작"]) == 2


def test_mark_set_cli(tmp_env):
    store.upsert("m", Ref(id="r1", kind="video"))
    rc = main(["mark", "--motion", "m", "--id", "r1", "--set",
               "start=0.2", "impact=0.6", "settle=1.0"])
    assert rc == 0
    assert store.get("m", "r1").marks == {"start": 0.2, "impact": 0.6, "settle": 1.0}


def test_mark_set_bad_order_and_unknown(tmp_env, capsys):
    store.upsert("m", Ref(id="r1", kind="video"))
    assert main(["mark", "--motion", "m", "--id", "r1", "--set", "start=1", "impact=0.5",
                 "settle=2"]) == 2
    assert main(["mark", "--motion", "m", "--id", "r1", "--set", "foo=1"]) == 2
    assert main(["mark", "--motion", "m", "--id", "none", "--set", "start=1"]) == 2


def test_mark_mpv_flow(tmp_env, monkeypatch):
    _, lib = tmp_env
    store.upsert("m", Ref(id="r1", kind="video"))
    clip_dir = lib / "m" / "r1"
    clip_dir.mkdir(parents=True)
    (clip_dir / "clip.mp4").write_bytes(b"x")
    seen = {}

    def fake_run(clip, out):
        seen["clip"] = clip
        out.write_text("start 0.1\nimpact 0.5\nsettle 1.0\n", encoding="utf-8")

    monkeypatch.setattr(marks, "run_mpv", fake_run)
    monkeypatch.setattr("refkit.cli.tools.require", lambda n: n)
    assert main(["mark", "--motion", "m", "--id", "r1"]) == 0
    assert seen["clip"] == clip_dir / "clip.mp4"
    assert store.get("m", "r1").marks == {"start": 0.1, "impact": 0.5, "settle": 1.0}


def test_run_mpv_args(tmp_path, monkeypatch):
    calls = []
    monkeypatch.setattr(marks.tools, "require", lambda n: "/usr/bin/mpv")
    monkeypatch.setattr(marks.subprocess, "run", lambda cmd, **k: calls.append(cmd))
    marks.run_mpv(tmp_path / "활 clip.mp4", tmp_path / "out.txt")
    cmd = calls[0]
    assert cmd[0] == "/usr/bin/mpv"
    assert "--loop-file=inf" in cmd and "--speed=0.5" in cmd and "--keep-open=yes" in cmd
    assert f"--script-opts=refkit-out={tmp_path / 'out.txt'}" in cmd
    assert cmd[-1] == str(tmp_path / "활 clip.mp4")
    assert any(c.startswith("--script=") and c.endswith("refkit_marks.lua") for c in cmd)


def test_lua_script_packaged():
    f = resources.files("refkit") / "mpv" / "refkit_marks.lua"
    text = f.read_text(encoding="utf-8")
    assert "refkit-out" in text


def test_mark_without_mpv_exits_2(tmp_env, monkeypatch, capsys):
    store.upsert("m", Ref(id="r1", kind="video"))
    monkeypatch.setattr("refkit.tools.shutil.which", lambda n: None)
    assert main(["mark", "--motion", "m", "--id", "r1"]) == 2
    assert "winget install mpv" in capsys.readouterr().err
