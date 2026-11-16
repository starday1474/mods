import json
import re
from pathlib import Path

import pytest

from tools.anim import build_anims, efanim
from tools.anim.tests.test_efanim import JAR, JOINTS

REPO = Path(__file__).resolve().parents[3]
SRC = REPO / "mod/anim"
OUT = REPO / "mod/src/main/resources/assets/goddessrpg/animmodels/animations/biped/combat"
JAVA = REPO / "mod/src/main/java/dev/goddessrpg/compat/epicfight/RpgAnimations.java"

needs_jar = pytest.mark.skipif(not JAR.exists(), reason="EF jar not available")


def _src(length, base, swing):
    return {
        "length": length, "base": base, "group": "test", "desc_ko": "시험 동작",
        "keys": [
            {"t": 0.0},
            {"t": length / 2, "rot": {"Arm_R": [swing, 0, -10], "Chest": [0, 15, 0]},
             "translate": {"Root": [0, 0.2, 0]}},
            {"t": length, "translate": {"Root": [0, 0.2, 0]}},
        ],
        "phase": {"antic": 0.1, "contact": 0.2, "recovery": 0.4},
        "trail": [0.1, 0.2],
    }


@pytest.fixture
def srcdir(tmp_path):
    d = tmp_path / "src"
    d.mkdir()
    (d / "a1.json").write_text(json.dumps(_src(0.6, "epicfight:biped/living/idle", 120)))
    (d / "a2.json").write_text(json.dumps(_src(0.5, "rest", -60)))
    (d / "a3.json").write_text(json.dumps(_src(0.7, "epicfight:biped/living/idle", 45)))
    return d


@needs_jar
def test_builds_three(srcdir, tmp_path):
    out = tmp_path / "out"
    assert build_anims.main([str(srcdir), str(out)]) == 0
    for name, length in (("a1", 0.6), ("a2", 0.5), ("a3", 0.7)):
        anim = efanim.load_animation(out / f"{name}.json")
        assert sorted(anim.tracks) == sorted(JOINTS)
        for tr in anim.tracks.values():
            assert tr.times[0] == 0.0
            assert tr.times[-1] == pytest.approx(length)
            assert len(tr.times) == 3
        trail = json.loads((out / "data" / f"{name}.json").read_text())
        assert trail == {"trail_effects": [{"start_time": 0.1, "end_time": 0.2,
                                            "joint": "Tool_R", "item_skin_hand": "main_hand"}]}


@needs_jar
def test_base_pose_and_root_translate(srcdir, tmp_path):
    out = tmp_path / "out"
    build_anims.main([str(srcdir), str(out)])
    arm = build_anims.load_ef_armature(JAR)
    base = build_anims.load_base("epicfight:biped/living/idle", arm, JAR)
    anim = efanim.load_animation(out / "a1.json")
    # first key without overrides is exactly the base pose
    for j in JOINTS:
        assert abs(anim.tracks[j].local[0] - base[j]).max() < 1e-5
    # Root translation is added to the local position
    d = anim.tracks["Root"].local[1][:3, 3] - base["Root"][:3, 3]
    assert d == pytest.approx([0, 0.2, 0], abs=1e-5)
    rest = efanim.load_animation(out / "a2.json")
    assert abs(rest.tracks["Head"].local[0] - arm.rest_local["Head"]).max() < 1e-5


@needs_jar
def test_check_detects_drift(srcdir, tmp_path, capsys):
    out = tmp_path / "out"
    assert build_anims.main([str(srcdir), str(out), "--check"]) == 1  # nothing built yet
    assert build_anims.main([str(srcdir), str(out)]) == 0
    assert build_anims.main([str(srcdir), str(out), "--check"]) == 0
    p = out / "a2.json"
    data = json.loads(p.read_text())
    data["animation"][0]["time"][-1] = 9.0
    p.write_text(json.dumps(data))
    assert build_anims.main([str(srcdir), str(out), "--check"]) == 1
    assert "a2.json" in capsys.readouterr().out
    build_anims.main([str(srcdir), str(out)])
    (out / "data" / "a3.json").write_text("{}")
    assert build_anims.main([str(srcdir), str(out), "--check"]) == 1


def test_rejects_bad_source(tmp_path):
    bad = _src(0.6, "rest", 10)
    bad["keys"][-1]["t"] = 0.5  # last key must sit at length
    with pytest.raises(ValueError, match="length"):
        build_anims.validate_source("x", bad)
    bad = _src(0.6, "rest", 10)
    bad["keys"][1]["rot"]["Arm_Q"] = [0, 0, 0]
    with pytest.raises(ValueError, match="Arm_Q"):
        build_anims.validate_source("x", bad)
    bad = _src(0.6, "rest", 10)
    bad["trail"] = [0.3, 0.9]
    with pytest.raises(ValueError, match="trail"):
        build_anims.validate_source("x", bad)


@needs_jar
def test_preview_writes_gif_and_sheet(tmp_path):
    src = tmp_path / "src"
    src.mkdir()
    s = _src(0.2, "rest", 30)
    s["phase"] = {"antic": 0.05, "contact": 0.1, "recovery": 0.15}
    s["trail"] = [0.05, 0.1]
    (src / "p.json").write_text(json.dumps(s))
    pv = tmp_path / "pv"
    assert build_anims.main([str(src), str(tmp_path / "out"), "--preview", str(pv)]) == 0
    assert (pv / "p.gif").stat().st_size > 0
    assert (pv / "p_sheet.png").stat().st_size > 0


@needs_jar
def test_repo_assets_up_to_date():
    assert sorted(p.stem for p in SRC.glob("*.json")) == ["balanced_auto1", "balanced_auto2", "balanced_auto3"]
    assert build_anims.main([str(SRC), str(OUT), "--check"]) == 0


def test_java_phases_match_sources():
    """RpgAnimations.java must use the phase times written in mod/anim."""
    java = JAVA.read_text()
    for p in sorted(SRC.glob("*.json")):
        src = json.loads(p.read_text())
        ph = src["phase"]
        m = re.search(r'"biped/combat/%s".*?new ComboAttackAnimation\(\s*([\d.]+)F,\s*([\d.]+)F,\s*([\d.]+)F,\s*([\d.]+)F'
                      % p.stem, java, re.S)
        assert m, p.stem
        assert [float(x) for x in m.groups()[1:]] == pytest.approx([ph["antic"], ph["contact"], ph["recovery"]])
        assert ph["recovery"] <= src["length"]


def test_root_never_moves_back():
    """EF turns Root translation into player movement: forward offset must never be undone."""
    for p in sorted(SRC.glob("*.json")):
        src = json.loads(p.read_text())
        if src.get("root_back"):
            continue
        ys = [(k.get("translate") or {}).get("Root", [0, 0, 0])[1] for k in src["keys"]]
        assert all(b >= a for a, b in zip(ys, ys[1:])), (p.stem, ys)


def _back_src():
    s = _src(0.6, "rest", 10)
    s["keys"][-1]["translate"] = {"Root": [0, 0.0, 0]}  # 0.2 -> 0.0: moves back
    return s


def test_root_back_flag():
    bad = _back_src()
    with pytest.raises(ValueError, match="root_back"):
        build_anims.validate_source("x", bad)
    bad["root_back"] = True
    build_anims.validate_source("x", bad)


def _loop_src():
    s = _src(0.6, "rest", 10)
    s["loop"] = True
    s["keys"][1]["translate"] = {}
    del s["keys"][-1]["translate"]
    s["keys"][1]["rot"]["Chest"] = [0, 15, 0]
    s["keys"][0]["rot"] = {"Chest": [0, 3, 0]}
    s["keys"][-1]["rot"] = {"Chest": [0, 3, 0]}
    return s


def test_loop_closes():
    ok = _loop_src()
    build_anims.validate_source("x", ok)
    bad = _loop_src()
    bad["keys"][-1]["rot"] = {"Chest": [0, 9, 0]}
    with pytest.raises(ValueError, match="loop"):
        build_anims.validate_source("x", bad)
    # missing entries count as defaults (0)
    bad = _loop_src()
    del bad["keys"][-1]["rot"]
    with pytest.raises(ValueError, match="loop"):
        build_anims.validate_source("x", bad)
    bad = _loop_src()
    bad["keys"][-1]["translate"] = {"Root": [0, 0.1, 0]}
    with pytest.raises(ValueError, match="loop"):
        build_anims.validate_source("x", bad)


@pytest.mark.parametrize("field", ["group", "desc_ko"])
def test_missing_meta_fails(field):
    bad = _src(0.6, "rest", 10)
    del bad[field]
    with pytest.raises(ValueError, match=field):
        build_anims.validate_source("x", bad)


@needs_jar
def test_missing_base_fails(tmp_path):
    src = tmp_path / "src"
    src.mkdir()
    (src / "nope.json").write_text(json.dumps(_src(0.6, "epicfight:biped/living/does_not_exist", 10)))
    with pytest.raises(ValueError, match="nope.*does_not_exist"):
        build_anims.main([str(src), str(tmp_path / "out")])


@needs_jar
def test_no_phase_no_trail_file(tmp_path):
    src = tmp_path / "src"
    src.mkdir()
    s = _src(0.6, "rest", 10)
    del s["phase"], s["trail"]
    (src / "idle.json").write_text(json.dumps(s))
    out = tmp_path / "out"
    assert build_anims.main([str(src), str(out)]) == 0
    assert (out / "idle.json").is_file()
    assert not (out / "data" / "idle.json").exists()
    assert build_anims.main([str(src), str(out), "--check"]) == 0
