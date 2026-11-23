import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


@pytest.fixture
def tmp_env(monkeypatch, tmp_path):
    repo = tmp_path / "repo"
    lib = tmp_path / "라이브러리 폴더"
    repo.mkdir()
    lib.mkdir()
    monkeypatch.setenv("REFKIT_REPO", str(repo))
    monkeypatch.setenv("REFKIT_LIBRARY", str(lib))
    return repo, lib


@pytest.fixture
def sample_video(tmp_path):
    import shutil
    import subprocess

    if not shutil.which("ffmpeg"):
        pytest.skip("ffmpeg가 없습니다")
    d = tmp_path / "활 영상 폴더"
    d.mkdir()
    out = d / "내 영상.mp4"
    subprocess.run(
        ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-f", "lavfi",
         "-i", "testsrc=size=320x240:rate=30", "-t", "3", "-pix_fmt", "yuv420p", str(out)],
        check=True)
    return out
