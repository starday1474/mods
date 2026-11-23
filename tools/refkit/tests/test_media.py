import subprocess

import pytest
from PIL import Image

from refkit import fetch, media, paths, store


def _fps(p):
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
                          "stream=r_frame_rate", "-of", "default=nw=1:nk=1", str(p)],
                         capture_output=True, text=True, check=True).stdout.strip()
    n, d = out.split("/")
    return float(n) / float(d)


def test_cut_clamps_end(sample_video, tmp_path):
    dst = media.cut(sample_video, tmp_path / "o.mp4", 1.0, 99)
    assert media.probe_duration(dst) == pytest.approx(2.0, abs=0.1)
    assert _fps(dst) == 30


def test_cut_rejects_bad_range(sample_video, tmp_path):
    with pytest.raises(ValueError):
        media.cut(sample_video, tmp_path / "o.mp4", 2.0, 1.0)


def test_slowmo_length(sample_video, tmp_path):
    clip = media.cut(sample_video, tmp_path / "c.mp4", 0, 1)
    slow = media.slowmo(clip, tmp_path / "s.mp4", 4.0)
    assert media.probe_duration(slow) == pytest.approx(4.0, abs=0.2)


def test_contact_sheet(sample_video, tmp_path):
    out = media.contact_sheet(sample_video, tmp_path / "sheet.png", cols=6, every=3)
    w, h = Image.open(out).size
    assert w == 6 * 320
    assert h == 5 * 240  # 30칸 / 6열


def test_import_unicode_path(tmp_env, sample_video):
    repo, lib = tmp_env
    ref = fetch.import_file("archer_draw", sample_video, start=0.5, end=2)
    assert (paths.ref_dir("archer_draw", ref.id) / "source.mp4").exists()
    saved = store.get("archer_draw", ref.id)
    assert saved.file == f"archer_draw/{ref.id}/source.mp4"
    text = (repo / "docs" / "refs" / "archer_draw.yaml").read_text(encoding="utf-8")
    assert f"file: archer_draw/{ref.id}/source.mp4" in text
    assert saved.start == 0.5 and saved.end == 2


def test_import_rejects_bad_range(tmp_env, sample_video):
    with pytest.raises(ValueError):
        fetch.import_file("m", sample_video, start=2, end=1)


def test_clip_ref_outputs(tmp_env, sample_video):
    ref = fetch.import_file("archer_draw", sample_video, start=0.5, end=2)
    out = media.clip_ref("archer_draw", ref)
    assert set(out) == {"clip", "slow", "sheet"}
    assert all(p.exists() for p in out.values())
    assert media.probe_duration(out["clip"]) == pytest.approx(1.5, abs=0.1)
