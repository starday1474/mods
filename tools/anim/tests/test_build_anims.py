import json
import math
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
    stems = sorted(p.stem for p in SRC.glob("*.json"))
    assert {"balanced_auto1", "balanced_auto2", "balanced_auto3"} <= set(stems)
    assert stems == sorted(p.stem for p in OUT.glob("*.json"))
    assert build_anims.main([str(SRC), str(OUT), "--check"]) == 0


def _java_combo_phases(java, stem):
    """Numbers of the ComboAttackAnimation registered for biped/combat/<stem>, or None.

    The match stays inside one ``nextAccessor(...)`` statement (no ``;`` crossing).
    """
    m = re.search(r'"biped/combat/%s"[^;]*?new ComboAttackAnimation\(\s*([\d.]+)F,\s*([\d.]+)F,\s*([\d.]+)F,\s*([\d.]+)F'
                  % re.escape(stem), java, re.S)
    return [float(x) for x in m.groups()[1:]] if m else None


def test_java_phase_regex_stays_in_statement():
    java = (
        'builder.nextAccessor("biped/combat/foo_idle", a -> new StaticAnimation(true, 0.2F, a, armature));\n'
        'builder.nextAccessor("biped/combat/foo_hit", a -> new ComboAttackAnimation(0.1F, 0.2F, 0.3F, 0.4F, a));\n'
    )
    assert _java_combo_phases(java, "foo_idle") is None
    assert _java_combo_phases(java, "foo_hit") == [0.2, 0.3, 0.4]


def test_java_phases_match_sources():
    """RpgAnimations.java must use the phase times written in mod/anim (for ComboAttackAnimation registrations)."""
    java = JAVA.read_text()
    checked = 0
    for p in sorted(SRC.glob("*.json")):
        got = _java_combo_phases(java, p.stem)
        if got is None:
            continue
        src = json.loads(p.read_text())
        if "phase" not in src:
            continue
        ph = src["phase"]
        checked += 1
        assert got == pytest.approx([ph["antic"], ph["contact"], ph["recovery"]]), p.stem
        assert ph["recovery"] <= src["length"]
    assert checked, "RpgAnimations.java references no mod/anim source"


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


def test_offhand_validation():
    s = _src(0.5, "rest", 30)
    for ok in ("none", "shield", "sword", "bow"):
        build_anims.validate_source("t", {**s, "offhand": ok})
    with pytest.raises(ValueError, match=r"t: offhand.*'staff'"):
        build_anims.validate_source("t", {**s, "offhand": "staff"})


def test_trail_joint_validation():
    s = _src(0.5, "rest", 30)
    with pytest.raises(ValueError, match=r"t: trail_joint"):
        build_anims.validate_source("t", {**s, "trail_joint": "Hand_L"})


def test_trail_json_joint():
    s = _src(0.5, "rest", 30)
    assert build_anims.trail_json(s)["trail_effects"][0] == {
        "start_time": 0.1, "end_time": 0.2, "joint": "Tool_R", "item_skin_hand": "main_hand"}
    t = build_anims.trail_json({**s, "trail_joint": "Tool_L"})["trail_effects"][0]
    assert t["joint"] == "Tool_L" and t["item_skin_hand"] == "off_hand"


@needs_jar
def test_preview_passes_offhand(tmp_path, monkeypatch):
    from tools.anim import preview
    seen = []
    real = preview.render_gif
    monkeypatch.setattr(preview, "render_gif",
                        lambda *a, **k: (seen.append(k.get("offhand")), real(*a, **k))[1])
    src = tmp_path / "src"
    src.mkdir()
    (src / "p.json").write_text(json.dumps({**_src(0.2, "rest", 30), "offhand": "shield",
                                            "phase": {"antic": 0.05, "contact": 0.1, "recovery": 0.15},
                                            "trail": [0.05, 0.1]}))
    assert build_anims.main([str(src), str(tmp_path / "o"), "--preview", str(tmp_path / "pv")]) == 0
    assert seen == ["shield"]


LAYER = {"layer": "COMPOSITE_LAYER", "priority": "MIDDLE",
         "masks": [{"livingmotion": "ALL", "type": "root_upper_joints"}]}


def _one_src(tmp_path, **extra):
    src = tmp_path / "src"
    src.mkdir()
    s = _src(0.6, "rest", 10)
    for k in extra.pop("drop", ()):
        s.pop(k)
    s.update(extra)
    (src / "x.json").write_text(json.dumps(s))
    return src


def test_layer_must_be_object():
    s = _src(0.6, "rest", 10)
    s["layer"] = "COMPOSITE_LAYER"
    with pytest.raises(ValueError, match="layer"):
        build_anims.validate_source("x", s)


def test_data_json_merges_trail_and_layer():
    s = _src(0.6, "rest", 10)
    assert build_anims.data_json({**s, "layer": LAYER}) == {**build_anims.trail_json(s), **LAYER}
    assert build_anims.data_json({k: v for k, v in s.items() if k != "trail"}) is None
    only = {k: v for k, v in s.items() if k != "trail"}
    assert build_anims.data_json({**only, "layer": LAYER}) == LAYER


@needs_jar
def test_layer_only_writes_data_file(tmp_path):
    src = _one_src(tmp_path, drop=["trail"], layer=LAYER)
    out = tmp_path / "out"
    assert build_anims.main([str(src), str(out)]) == 0
    assert json.loads((out / "data/x.json").read_text()) == LAYER


@needs_jar
def test_trail_and_layer_merge_and_check_drift(tmp_path):
    src = _one_src(tmp_path, layer=LAYER)
    out = tmp_path / "out"
    assert build_anims.main([str(src), str(out)]) == 0
    data = json.loads((out / "data/x.json").read_text())
    assert "trail_effects" in data and data["layer"] == "COMPOSITE_LAYER"
    assert build_anims.main([str(src), str(out), "--check"]) == 0
    (out / "data/x.json").write_text(json.dumps({"trail_effects": data["trail_effects"]}) + "\n")
    assert build_anims.main([str(src), str(out), "--check"]) == 1


def test_bow_offhand_accepted():
    s = _src(0.6, "rest", 10)
    build_anims.validate_source("x", {**s, "offhand": "bow"})


# ---- ease / bake -------------------------------------------------------

def _bake_src(bake=None, ease=None, length=0.6):
    s = _src(length, "rest", 90)
    del s["phase"], s["trail"]
    if bake is not None:
        s["bake"] = bake
    if ease is not None:
        s["keys"][1]["ease"] = ease
    return s


def _build(src, tmp_path, name="b"):
    d = tmp_path / "s"
    d.mkdir(parents=True, exist_ok=True)
    (d / f"{name}.json").write_text(json.dumps(src))
    out = tmp_path / "o"
    build_anims.main([str(d), str(out)])
    return efanim.load_animation(out / f"{name}.json")


def _angle(anim, t, joint="Arm_R"):
    from tools.anim import preview
    tr = anim.tracks[joint]
    m = preview._sample_track(tr, t)
    q0 = efanim.quat_from_matrix(tr.local[0])
    q = efanim.quat_from_matrix(m)
    return 2 * math.acos(min(1.0, abs(float(q0 @ q))))


@needs_jar
def test_bake_absent_means_unbaked(tmp_path):
    anim = _build(_bake_src(), tmp_path)
    assert len(anim.tracks["Arm_R"].times) == 3


@needs_jar
def test_bake_key_times_and_count(tmp_path):
    anim = _build(_bake_src(bake=30), tmp_path)
    ts = anim.tracks["Arm_R"].times
    assert ts[0] == 0.0 and ts[-1] == pytest.approx(0.6)
    assert any(abs(t - 0.3) < 1e-9 for t in ts)
    # 30 Hz grid over 0.6 s: 19 grid points + nothing extra (0.3 is on the grid)
    assert len(ts) == 19
    assert all(len(tr.times) == len(ts) for tr in anim.tracks.values())
    assert all(b > a for a, b in zip(ts, ts[1:]))


@needs_jar
def test_bake_preserves_key_poses(tmp_path):
    plain = _build(_bake_src(), tmp_path / "a")
    baked = _build(_bake_src(bake=30, ease="inout"), tmp_path / "b")
    for j in JOINTS:
        for t in (0.0, 0.3, 0.6):
            from tools.anim import preview
            d = abs(preview._sample_track(plain.tracks[j], t) - preview._sample_track(baked.tracks[j], t)).max()
            assert d < 1e-5


@needs_jar
def test_ease_in_slower_than_linear_at_midsegment(tmp_path):
    lin = _build(_bake_src(bake=30), tmp_path / "a")
    ein = _build(_bake_src(bake=30, ease="in"), tmp_path / "b")
    assert _angle(ein, 0.15) < _angle(lin, 0.15) - 0.05
    eout = _build(_bake_src(bake=30, ease="out"), tmp_path / "c")
    assert _angle(eout, 0.15) > _angle(lin, 0.15) + 0.05


@needs_jar
def test_back_overshoots(tmp_path):
    lin = _build(_bake_src(bake=30), tmp_path / "a")
    back = _build(_bake_src(bake=30, ease="back"), tmp_path / "b")
    target = _angle(lin, 0.3)
    assert max(_angle(back, t / 100) for t in range(15, 30)) > target * 1.03


@needs_jar
def test_bake_loop_still_closes(tmp_path):
    s = _bake_src(bake=30, ease="back")
    s["loop"] = True
    s["keys"][-1] = {"t": 0.6}
    s["keys"][1]["translate"] = {}
    anim = _build(s, tmp_path)
    for tr in anim.tracks.values():
        assert abs(tr.local[0] - tr.local[-1]).max() < 1e-6


@needs_jar
def test_back_ease_never_moves_root_backwards(tmp_path):
    anim = _build(_bake_src(bake=30, ease="back"), tmp_path)
    ys = [m[1, 3] for m in anim.tracks["Root"].local]
    assert all(b >= a - 1e-9 for a, b in zip(ys, ys[1:]))


def test_bake_ease_validation():
    s = _bake_src(bake=5)
    with pytest.raises(ValueError, match="bake"):
        build_anims.validate_source("x", s)
    s = _bake_src(bake=61)
    with pytest.raises(ValueError, match="bake"):
        build_anims.validate_source("x", s)
    s = _bake_src(bake=30.5)
    with pytest.raises(ValueError, match="bake"):
        build_anims.validate_source("x", s)
    s = _bake_src(bake=30, ease="bounce")
    with pytest.raises(ValueError, match="ease"):
        build_anims.validate_source("x", s)
    s = _bake_src(ease="in")
    with pytest.raises(ValueError, match="x.*ease.*bake"):
        build_anims.validate_source("x", s)
    build_anims.validate_source("x", _bake_src(bake=30, ease="back"))
