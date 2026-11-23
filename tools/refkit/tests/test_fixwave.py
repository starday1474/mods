import json
import shutil
import subprocess

import pytest
import yaml
from PIL import Image

from refkit import cli, compare, fetch, media, paths, store
from refkit.pose import analysis, runner
from refkit.store import Ref

needs_ffmpeg = pytest.mark.skipif(not (shutil.which("ffmpeg") and shutil.which("ffprobe")),
                                  reason="ffmpeg가 없습니다")


def _gif(path, n, ms, size=(64, 64)):
    frames = [Image.new("RGB", size, (i * 10 % 256, 40, 80)) for i in range(n)]
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=ms, loop=0)
    return path


@needs_ffmpeg
def test_side_by_side_retimes_ours_to_30fps(sample_video, tmp_path):
    gif = _gif(tmp_path / "ours.gif", 19, 33)  # GIF에는 30ms로 저장된다
    assert media.probe_duration(gif) == pytest.approx(19 * 0.03, abs=0.02)  # 원래는 10% 빠름
    out = tmp_path / "o" / "compare.mp4"
    # 레퍼런스(3초)가 더 길어 전체 길이는 3.0.
    compare.side_by_side(sample_video, gif, out, 0.0, 0.0, height=64)
    assert media.probe_duration(out) == pytest.approx(3.0, abs=0.1)
    # 우리 쪽 길이가 n/30 임을 확인: 레퍼런스를 짧게 만들고 our_pad로 총 길이를 본다.
    short = tmp_path / "short.mp4"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i",
                    "testsrc=size=64x64:rate=30", "-t", "0.2", "-pix_fmt", "yuv420p", str(short)],
                   check=True)
    out2 = tmp_path / "o2" / "compare.mp4"
    compare.side_by_side(short, gif, out2, 0.0, 0.5, height=64)
    assert media.probe_duration(out2) == pytest.approx(0.5 + 19 / 30, abs=0.1)


def _cp(cmd, rc=0):
    return subprocess.CompletedProcess(cmd, rc, "", "")


def test_cli_fetch_no_file_is_error_not_traceback(tmp_env, monkeypatch, capsys):
    monkeypatch.setattr("shutil.which", lambda n: f"/bin/{n}")
    monkeypatch.setattr(subprocess, "run", lambda cmd, **k: _cp(cmd))  # exit 0, 파일 없음
    store.upsert("m", Ref(id="v1", kind="video", url="https://v.example/x", license="cc-by"))
    assert cli.main(["fetch", "--motion", "m", "--id", "v1"]) == 2
    err = capsys.readouterr().err
    assert "오류" in err and "Traceback" not in err


def test_fetch_all_skips_urlless_and_downloaded(tmp_env, monkeypatch, capsys):
    called = []
    monkeypatch.setattr(fetch, "fetch_ref", lambda *a, **k: called.append(a))
    store.upsert("m", Ref(id="own-01", kind="own"))
    store.upsert("m", Ref(id="v1", kind="video", url="https://v.example/x", file="m/v1/source.mp4"))
    assert cli.main(["fetch", "--motion", "m"]) == 0
    assert called == []
    assert "링크 모드" not in capsys.readouterr().out


def test_store_empty_yaml_fields_normalized(tmp_env):
    p = paths.refs_dir()
    p.mkdir(parents=True)
    (p / "m.yaml").write_text(
        "motion: m\nrefs:\n- id: a\n  kind: video\n  tags:\n  pose:\n  marks:\n", encoding="utf-8")
    r = store.load("m")[0]
    assert r.tags == [] and r.pose == {} and r.marks == {}


def test_store_bad_yaml_is_korean_error(tmp_env, capsys):
    p = paths.refs_dir()
    p.mkdir(parents=True)
    (p / "m.yaml").write_text("refs: [unclosed\n  - : :", encoding="utf-8")
    with pytest.raises(ValueError, match="YAML"):
        store.load("m")
    for argv in (["list", "--motion", "m"], ["list"], ["board", "--motion", "m"],
                 ["mark", "--motion", "m", "--id", "a", "--set", "start=1"]):
        assert cli.main(argv) == 2
        err = capsys.readouterr().err
        assert "오류" in err and "Traceback" not in err


def _pose_setup(tmp_env, monkeypatch, warning):
    store.upsert("m", Ref(id="a", kind="video"))
    d = paths.ref_dir("m", "a")
    (d / "clip.mp4").write_bytes(b"x")
    import numpy as np
    monkeypatch.setattr(runner, "require_mediapipe", lambda: None)
    monkeypatch.setattr("shutil.which", lambda n: f"/bin/{n}")
    monkeypatch.setattr(runner, "estimate",
                        lambda clip: (np.zeros((3, 33, 3)), np.zeros((3, 33, 2)), 30.0))
    summary = {"frames": 3, "detected": 0.3 if warning else 1.0,
               "suggested_marks": {"start": 0.0, "impact": 0.05, "settle": 0.1}}
    if warning:
        summary["warning"] = "검출률이 낮습니다."
    monkeypatch.setattr(analysis, "summarize", lambda w, fps: summary)
    monkeypatch.setattr(analysis, "joint_angles", lambda w: {})


@pytest.mark.parametrize("warning,applied", [(True, False), (False, True)])
def test_pose_suggested_marks_skipped_on_warning(tmp_env, monkeypatch, capsys, warning, applied):
    _pose_setup(tmp_env, monkeypatch, warning)
    assert cli.main(["pose", "--motion", "m", "--id", "a", "--no-overlay",
                     "--use-suggested-marks"]) == 0
    assert bool(store.get("m", "a").marks) is applied
    out = capsys.readouterr().out
    assert ("적용하지 않았습니다" in out) is warning


def test_lua_clear_guarded():
    from pathlib import Path
    t = (Path(runner.__file__).parents[1] / "mpv" / "refkit_marks.lua").read_text(encoding="utf-8")
    assert 'if append("clear", 0) then' in t


@needs_ffmpeg
def test_end_to_end_chain(tmp_env, sample_video, monkeypatch, capsys):
    repo, lib = tmp_env
    anim = repo / "mod" / "anim"
    anim.mkdir(parents=True)
    (anim / "bow.json").write_text(json.dumps(
        {"length": 1.0, "phase": {"antic": 0.4, "contact": 0.5, "recovery": 0.1}, "keys": []}),
        encoding="utf-8")
    built = (repo / "mod/src/main/resources/assets/goddessrpg/animmodels/animations/biped/combat")
    built.mkdir(parents=True)
    (built / "bow.json").write_text("{}", encoding="utf-8")

    def fake_preview(cmd, *a, **k):  # preview.py 대신 작은 GIF를 쓴다
        _gif(cmd[-1], 30, 33)
        return _cp(cmd)

    real_run = subprocess.run
    # compare.subprocess 는 subprocess 모듈 자체이므로 ffmpeg 호출은 진짜로 흘려보낸다.
    def selective(cmd, *a, **k):
        if "preview.py" in str(cmd[1] if len(cmd) > 1 else ""):
            return fake_preview(cmd)
        return real_run(cmd, *a, **k)

    monkeypatch.setattr(subprocess, "run", selective)

    def ok(*argv):
        rc = cli.main(list(argv))
        cap = capsys.readouterr()
        assert rc == 0, cap.err
        return cap.out

    ok("import", str(sample_video), "--motion", "bow", "--start", "0", "--end", "2")
    ok("clip", "--motion", "bow", "--id", "own-01")
    ok("mark", "--motion", "bow", "--id", "own-01", "--set", "start=0.2", "impact=1.0", "settle=1.8")
    assert "own-01" in ok("timing", "--motion", "bow")
    ok("compare", "--motion", "bow")
    assert (paths.ref_dir("bow", "own-01") / "compare.mp4").is_file()
    ok("board", "--motion", "bow")
    assert yaml.safe_load((paths.refs_dir() / "bow.yaml").read_text(encoding="utf-8"))["refs"]


@needs_ffmpeg
def test_side_by_side_filter_retimes_input1_only(sample_video, tmp_path, monkeypatch):
    gif = _gif(tmp_path / "ours.gif", 19, 33)
    seen = []
    monkeypatch.setattr(media, "_ffmpeg", lambda *a: seen.append([str(x) for x in a]))
    compare.side_by_side(sample_video, gif, tmp_path / "c.mp4", 0.0, 0.0)
    fc = seen[0][seen[0].index("-filter_complex") + 1]
    assert "[1:v]setpts=PTS*10/9,fps=30" in fc and "[0:v]fps=30" in fc


@needs_ffmpeg
def test_side_by_side_static_holds_keep_duration(tmp_path):
    # 검정 3장 + 회색 2장(33ms): Pillow가 [90, 60]ms 두 프레임으로 합쳐 저장한다.
    black, grey = Image.new("RGB", (64, 64), "black"), Image.new("RGB", (64, 64), (128, 128, 128))
    gif = tmp_path / "hold.gif"
    frames = [black] * 3 + [grey] * 2
    frames[0].save(gif, save_all=True, append_images=frames[1:], duration=33, loop=0)
    with Image.open(gif) as im:
        assert im.n_frames == 2
    short = tmp_path / "short.mp4"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i",
                    "testsrc=size=64x64:rate=30", "-t", "0.1", "-pix_fmt", "yuv420p", str(short)],
                   check=True)
    out = tmp_path / "c.mp4"
    compare.side_by_side(short, gif, out, 0.0, 0.5, height=64)
    assert media.probe_duration(out) == pytest.approx(0.5 + 5 / 30, abs=1 / 30 + 0.02)
