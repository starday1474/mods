import json
import math
import zipfile

import numpy as np
import pytest
from PIL import Image

from tools.anim import efanim, preview
from tools.anim.tests.test_efanim import ANIM, ARM, JAR


@pytest.fixture(scope="module")
def data(tmp_path_factory):
    if not JAR.exists():
        pytest.skip("EF jar not available")
    d = tmp_path_factory.mktemp("pv")
    with zipfile.ZipFile(JAR) as z:
        (d / "b.json").write_bytes(z.read(ARM))
        (d / "s.json").write_bytes(z.read(ANIM))
    return (efanim.load_armature(d / "b.json"), json.loads((d / "b.json").read_text()),
            efanim.load_animation(d / "s.json"))


def test_rest_skin_matches_bind(data):
    arm, mesh, _ = data
    w = efanim.world_matrices(arm, arm.rest_local)
    got = preview.skin(arm, mesh, w)
    want = np.array(mesh["vertices"]["positions"]["array"]).reshape(-1, 3)
    assert got.shape == want.shape
    assert np.abs(got - want).max() < 1e-4


def test_sample_endpoints(data):
    _, _, anim = data
    last = max(t.times[-1] for t in anim.tracks.values())
    s0, s1 = preview.sample(anim, 0), preview.sample(anim, last)
    for j, tr in anim.tracks.items():
        assert np.abs(s0[j] - tr.local[0]).max() < 1e-6
        assert np.abs(s1[j] - tr.local[-1]).max() < 1e-6
    # clamping and midpoint sanity
    assert np.abs(preview.sample(anim, -1)["Tool_R"] - anim.tracks["Tool_R"].local[0]).max() < 1e-6


def test_gif_written(data, tmp_path):
    arm, mesh, anim = data
    out = tmp_path / "o.gif"
    preview.render_gif(arm, mesh, anim, out)
    length = max(t.times[-1] for t in anim.tracks.values())
    im = Image.open(out)
    assert im.n_frames == math.ceil(length * 20) + 1
    assert im.width > im.height


@pytest.mark.parametrize("item", ["shield", "sword"])
def test_offhand_gif_and_pixels_differ(data, tmp_path, item):
    arm, mesh, anim = data
    none = preview.render_gif(arm, mesh, anim, tmp_path / "n.gif", offhand="none")
    out = tmp_path / "o.gif"
    imgs = preview.render_gif(arm, mesh, anim, out, offhand=item)
    assert out.stat().st_size > 0 and len(imgs) == len(none)
    diff = np.abs(np.asarray(imgs[0], dtype=int) - np.asarray(none[0], dtype=int))
    assert diff.max() > 0


def test_offhand_invalid(data, tmp_path):
    arm, mesh, anim = data
    with pytest.raises(ValueError, match="offhand"):
        preview.render_gif(arm, mesh, anim, tmp_path / "x.gif", offhand="axe")
