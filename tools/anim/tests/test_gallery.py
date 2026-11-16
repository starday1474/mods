import json

from tools.anim import gallery
from tools.anim.tests.test_build_anims import _src


def test_gallery_manifest(tmp_path):
    src, pv, out = tmp_path / "src", tmp_path / "pv", tmp_path / "out"
    src.mkdir()
    pv.mkdir()
    for name, group, length in (("mage_cast", "mage", 0.5), ("balanced_b", "balanced", 0.7),
                                ("balanced_a", "balanced", 0.6)):
        s = _src(length, "rest", 10)
        s["group"] = group
        s["desc_ko"] = f"{name} 설명"
        (src / f"{name}.json").write_text(json.dumps(s))
        (pv / f"{name}.gif").write_bytes(b"GIF89a" + name.encode())
    assert gallery.main([str(src), str(pv), str(out)]) == 0
    m = json.loads((out / "manifest.json").read_text())
    assert [e["name"] for e in m] == ["balanced_a", "balanced_b", "mage_cast"]
    assert m[0] == {"name": "balanced_a", "group": "balanced", "desc_ko": "balanced_a 설명",
                    "length": 0.6, "loop": False, "gif": "balanced_a.gif"}
    for e in m:
        assert (out / e["gif"]).read_bytes() == (pv / e["gif"]).read_bytes()
