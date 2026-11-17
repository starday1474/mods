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
    assert im.n_frames == math.ceil(length * 30) + 1
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


def test_bow_drawn_as_longbow_differs_from_sword(data, tmp_path):
    arm, mesh, anim = data
    sword = preview.render_gif(arm, mesh, anim, tmp_path / "s.gif", offhand="sword")
    bow = preview.render_gif(arm, mesh, anim, tmp_path / "b.gif", offhand="bow")
    diff = np.abs(np.asarray(bow[0], dtype=int) - np.asarray(sword[0], dtype=int))
    assert diff.max() > 0


def test_longbow_geometry_centred_on_grip():
    tl = np.eye(4)
    tl[:3, 3] = (0.0, 1.0, 1.4)
    far = np.eye(4)
    far[:3, 3] = (2.0, -2.0, 0.0)  # string hand nowhere near the bow: braced string
    limb, string = preview.longbow(tl, far)
    tips = limb[0], limb[-1]
    span = np.linalg.norm(tips[0] - tips[1])
    assert abs(span - preview.BOW_LENGTH) < 0.05 and 1.6 <= preview.BOW_LENGTH <= 1.8
    # tips lie along the limb axis (Tool_L local Z), symmetric about the grip
    mid = (tips[0] + tips[1]) / 2
    assert abs(mid[2] - 1.4) < 1e-6
    assert abs(abs(tips[0][2] - tips[1][2]) - span) < 0.01
    # braced: the string runs tip to tip; the limbs curve toward it (local +Y), the grip is farthest from it
    assert len(string) == 2 and np.allclose(string[0], tips[0]) and np.allclose(string[-1], tips[-1])
    assert tips[0][1] > tl[1, 3] and tips[1][1] > tl[1, 3]
    # string hand drawn back behind the grip (local +Y): the string bends through the hand
    hand = np.eye(4)
    hand[:3, 3] = tl[:3, 3] + np.array([0.0, 0.7, 0.0])
    limb2, string2 = preview.longbow(tl, hand)
    assert len(string2) == 3 and np.allclose(string2[1], hand[:3, 3])
    assert preview.nocked_arrow(tl, far) is None
    arrow = preview.nocked_arrow(tl, hand)
    assert np.allclose(arrow[0], hand[:3, 3]) and arrow[1][1] < tl[1, 3]  # point passes the grip
