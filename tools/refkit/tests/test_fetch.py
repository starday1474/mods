import subprocess

import pytest

from refkit import fetch, paths, policy, store
from refkit.store import Ref


def test_fetch_refused_youtube(tmp_env, monkeypatch):
    calls = []
    monkeypatch.setattr(subprocess, "run", lambda *a, **k: calls.append(a))
    ref = Ref(id="v1", kind="video", url="https://www.youtube.com/watch?v=x")
    with pytest.raises(fetch.FetchRefused) as ei:
        fetch.fetch_ref("m", ref, policy.load_policy())
    assert ei.value.reason
    assert calls == []


def test_fetch_allowed_builds_command(tmp_env, monkeypatch):
    monkeypatch.setattr("shutil.which", lambda n: f"/bin/{n}")
    calls = []

    def fake(cmd, **kw):
        calls.append(cmd)
        d = paths.ref_dir("m", "v1")
        (d / "source.mp4").write_bytes(b"x")

    monkeypatch.setattr(subprocess, "run", fake)
    ref = Ref(id="v1", kind="video", url="https://v.example/x", license="cc-by", start=1, end=3)
    store.upsert("m", ref)
    out = fetch.fetch_ref("m", ref, policy.load_policy())
    cmd = calls[0]
    assert isinstance(cmd, list) and cmd[0] == "/bin/yt-dlp"
    assert "--download-sections" in cmd and "*1-3" in cmd
    assert cmd[-1] == "https://v.example/x"
    assert out.name == "source.mp4"
    assert store.get("m", "v1").file == "m/v1/source.mp4"
