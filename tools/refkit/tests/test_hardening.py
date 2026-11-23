import io
import subprocess
from pathlib import Path

import pytest

from refkit import board, cli, fetch, marks, paths, policy, store
from refkit.store import Ref


def test_main_survives_ascii_stdout(monkeypatch, tmp_env):
    out = io.TextIOWrapper(io.BytesIO(), encoding="ascii")
    err = io.TextIOWrapper(io.BytesIO(), encoding="ascii")
    monkeypatch.setattr("sys.stdout", out)
    monkeypatch.setattr("sys.stderr", err)
    assert cli.main(["list", "--motion", "한글동작"]) == 0
    out.flush()


def test_lua_uses_append_file():
    lua = (Path(marks.__file__).parent / "mpv" / "refkit_marks.lua").read_text(encoding="utf-8")
    assert "append_file" in lua and "mp.utils" in lua


@pytest.mark.parametrize("bad", ["nan", "inf", "-inf"])
def test_marks_file_rejects_nonfinite(tmp_path, bad):
    p = tmp_path / "m.txt"
    p.write_text(f"start 1.0\nimpact {bad}\nsettle 3.0\n", encoding="utf-8")
    assert marks.parse_marks_file(p) == {"start": 1.0, "settle": 3.0}


def test_board_link_only_http(tmp_env):
    store.upsert("m", Ref(id="a", kind="video", url="javascript:alert(1)", take="<script>x</script>"))
    store.upsert("m", Ref(id="b", kind="video", url="https://v.example/x"))
    out = board.build_share("m", tmp_env[1] / "o.html")
    h = out.read_text(encoding="utf-8")
    assert 'href="javascript' not in h
    assert 'href="https://v.example/x' in h
    assert "&lt;script&gt;" in h and "<script>" not in h


def _fetch_setup(monkeypatch, files):
    monkeypatch.setattr("shutil.which", lambda n: f"/bin/{n}")

    def fake(cmd, **kw):
        d = paths.ref_dir("m", "v1")
        for i, n in enumerate(files):
            p = d / n
            p.write_bytes(b"x")
            import os
            os.utime(p, (1000 + i * 10, 1000 + i * 10))

    monkeypatch.setattr(subprocess, "run", fake)


def test_fetch_no_file_raises(tmp_env, monkeypatch):
    _fetch_setup(monkeypatch, [])
    ref = Ref(id="v1", kind="video", url="https://v.example/x", license="cc-by")
    store.upsert("m", ref)
    with pytest.raises(RuntimeError, match="파일"):
        fetch.fetch_ref("m", ref, policy.load_policy())


def test_fetch_picks_newest(tmp_env, monkeypatch):
    _fetch_setup(monkeypatch, ["source.mp4", "source.webm"])
    ref = Ref(id="v1", kind="video", url="https://v.example/x", license="cc-by")
    store.upsert("m", ref)
    assert fetch.fetch_ref("m", ref, policy.load_policy()).name == "source.webm"
