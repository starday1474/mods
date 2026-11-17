import json
import struct
import zipfile
from pathlib import Path

import numpy as np
import pytest

from tools.anim import catalog, efanim
from tools.anim.tests.test_efanim import JAR, REPO

MOVESET = REPO / "docs/design/vanguard-moveset.md"
needs_ef = pytest.mark.skipif(not JAR.exists(), reason="EF jar not available")


def _utf8_const(s: str) -> bytes:
    b = s.encode()
    return b"\x01" + struct.pack(">H", len(b)) + b


def _track(name, mats, times):
    return {"name": name, "time": times, "transform": [list(np.asarray(m, float).reshape(16)) for m in mats]}


def _anim_json(rot_joint="Arm_R", deg=90.0, times=(0.0, 0.1, 0.2)):
    """Rest pose everywhere; ``rot_joint`` rotates about its local X up to ``deg`` at the middle key."""
    arm = catalog.biped()[0]
    tracks = []
    for j in arm.joints:
        rest = arm.rest_local[j]
        if j == rot_joint:
            mats = [efanim.pose_local(arm, arm.rest_local, {j: [a, 0, 0]}, {})[j] for a in (0.0, deg, 0.0)]
        else:
            mats = [rest] * len(times)
        tracks.append(_track(j, mats, list(times)))
    return {"animation": tracks}


@pytest.fixture
def fake_jars(tmp_path):
    """A fake 'epic-fight' jar: foo (file + registered), unreg (file only), data/foo (trail data)."""
    jar = tmp_path / "epic-fight-0.0.jar"
    base = "assets/epicfight/animmodels/animations/"
    with zipfile.ZipFile(jar, "w") as z:
        z.writestr(base + "biped/combat/foo.json", json.dumps({"animation": [_track("Root", [np.eye(4)] * 2, [0, 0.5])]}))
        z.writestr(base + "biped/combat/unreg.json", json.dumps({"animation": [_track("Root", [np.eye(4)] * 2, [0, 0.5])]}))
        z.writestr(base + "biped/combat/data/foo.json", json.dumps({"trail_effects": []}))
        z.writestr("x/Anims.class", b"\xca\xfe\xba\xbe" + _utf8_const("biped/combat/foo") + b"\x07\x00\x02"
                   + _utf8_const("biped/combat/foo_suffix_only_here"))
    catalog.registered_paths.cache_clear()
    catalog.jar_animation_paths.cache_clear()
    yield tmp_path
    catalog.registered_paths.cache_clear()
    catalog.jar_animation_paths.cache_clear()


def test_ids_in_markdown_roles_and_tables():
    md = ("# 밸런스형\n\n본문 `epicfight:biped/combat/sword_auto1` 언급\n\n"
          "| 역할 | 동작 ID |\n|---|---|\n| 평타 1 | `epicfightx:biped/combat/sword_auto1` |\n"
          "## 쌍검\n| **대시** | `wom:biped/skill/dualsword_buster_release`, `epicfight:biped/combat/sword_dual_dash` |\n"
          "| x | `goddessrpg:biped/combat/balanced_auto1` |\n")
    got = catalog.ids_in_markdown(md)
    assert [(g[0], g[3], g[4]) for g in got] == [
        ("epicfight:biped/combat/sword_auto1", "밸런스형", False),
        ("epicfightx:biped/combat/sword_auto1", "밸런스형 / 평타 1", True),
        ("wom:biped/skill/dualsword_buster_release", "쌍검 / 대시", True),
        ("epicfight:biped/combat/sword_dual_dash", "쌍검 / 대시", True),
    ]


def test_verify_ids_flags_missing_unregistered_and_unknown(fake_jars):
    ids = [(i, *i.split(":"), "", True) for i in (
        "epicfight:biped/combat/foo",          # ok
        "epicfight:biped/combat/bar",          # no file
        "epicfight:biped/combat/unreg",        # file, but no class constant
        "epicfight:biped/combat/data/foo",     # trail data is not an animation
        "wom:biped/combat/foo",                # no wom jar in this dir
        "nope:biped/combat/foo",               # unknown namespace
    )]
    bad = dict(catalog.verify_ids(ids, jar_dir=fake_jars))
    assert "epicfight:biped/combat/foo" not in bad
    assert "no animation file" in bad["epicfight:biped/combat/bar"]
    assert "not registered" in bad["epicfight:biped/combat/unreg"]
    assert "no animation file" in bad["epicfight:biped/combat/data/foo"]
    assert "no jar" in bad["wom:biped/combat/foo"]
    assert "unknown namespace" in bad["nope:biped/combat/foo"]


def test_registered_paths_reads_exact_utf8_constants(fake_jars):
    reg = catalog.registered_paths(str(fake_jars / "epic-fight-0.0.jar"))
    assert "biped/combat/foo" in reg and "biped/combat/unreg" not in reg


def test_preview_index_dedupes_and_picks_offhand(tmp_path):
    md = tmp_path / "m.md"
    md.write_text("## 인파이트 — 쌍검\n| 평타 1 | `epicfightx:biped/combat/sword_dual_auto1` |\n"
                  "## 밸런스형\n| 평타 1 | `epicfightx:biped/combat/sword_auto1` |\n"
                  "## 우리 동작\n| balanced_auto1 | `epicfightx:biped/combat/sword_auto1` |\n")
    idx = catalog.preview_index([md])
    assert [e["id"] for e in idx] == ["epicfightx:biped/combat/sword_dual_auto1", "epicfightx:biped/combat/sword_auto1"]
    assert idx[0]["offhand"] == "sword" and idx[1]["offhand"] == "shield"
    assert idx[1]["role"] == "밸런스형 / 평타 1; 우리 동작 / balanced_auto1"
    assert idx[1]["gif"] == "epicfightx__biped_combat_sword_auto1.gif"
    assert idx[1]["mod"] == "Epic Fight Extra"


@needs_ef
def test_list_rows_metrics_and_skips(tmp_path):
    jar = tmp_path / "t.jar"
    base = "assets/epicfight/animmodels/animations/"
    with zipfile.ZipFile(jar, "w") as z:
        z.writestr(base + "biped/combat/swing.json", json.dumps(_anim_json("Arm_R", 90.0)))
        z.writestr(base + "biped/combat/data/swing.json", json.dumps({"trail_effects": []}))
        z.writestr(base + "biped/skill/pov/cam.json", json.dumps(_anim_json("Arm_R", 90.0)))
        z.writestr(base + "biped/living/attr.json", json.dumps(
            {"format": "attributes", "animation": [{"name": "Root", "time": [0], "transform": [{"loc": [0, 0, 0]}]}]}))
    rows = catalog.list_rows(jar)
    assert [r["id"] for r in rows] == ["epicfight:biped/combat/swing"]
    r = rows[0]
    assert r["length"] == 0.2 and r["keys"] == 3 and r["kps"] == 15.0 and r["joints_moved"] == 1
    assert catalog.list_rows(jar, prefix="biped/living") == []
    # a smaller rotation than the threshold does not count
    assert catalog.summary(catalog.parse_animation(_anim_json("Arm_R", 3.0)))["joints_moved"] == 0


@needs_ef
def test_list_cli_prints_tsv(capsys):
    assert catalog.main(["list", str(JAR), "--prefix", "biped/combat/sword_auto1"]) == 0
    out = capsys.readouterr().out.strip().splitlines()
    assert out[0].split("\t") == ["id", "path", "length", "keys", "kps", "joints_moved"]
    row = out[1].split("\t")
    assert row[0] == "epicfight:biped/combat/sword_auto1" and float(row[2]) == pytest.approx(0.6)


@needs_ef
def test_analyse_sword_auto1():
    _, anim = catalog.load_any("epicfight:biped/combat/sword_auto1")
    r = catalog.analyse(anim)
    assert sum(r["phase"].values()) == pytest.approx(1.0, abs=1e-3)
    assert r["hits"] == 1 and r["contact_s"][0] < r["contact_s"][1]
    assert r["hips_yaw"] > 40          # EF turns the whole body from Root
    assert r["region_max"]["legs"] > 30
    # a single-joint synthetic swing: no hip motion, no root motion
    s = catalog.analyse(catalog.parse_animation(_anim_json("Arm_R", 90.0)))
    assert s["hips_yaw"] < 1e-6 and s["root"]["max_fwd"] == 0


@needs_ef
def test_render_writes_gif(tmp_path):
    jar = tmp_path / "t.jar"
    with zipfile.ZipFile(jar, "w") as z:
        z.writestr("assets/epicfight/animmodels/animations/biped/combat/swing.json", json.dumps(_anim_json()))
    out = tmp_path / "o.gif"
    assert catalog.main(["render", str(jar), "biped/combat/swing", str(out), "--fps", "10"]) == 0
    from PIL import Image
    assert Image.open(out).n_frames == 3


@needs_ef
@pytest.mark.skipif(not MOVESET.exists(), reason="moveset doc not written yet")
def test_moveset_ids_exist_in_jars(capsys):
    for ns in catalog.NAMESPACE_JARS:
        try:
            catalog.jar_for(ns)
        except FileNotFoundError:
            pytest.skip(f"{ns} jar not available")
    assert catalog.main(["verify", str(MOVESET)]) == 0
    assert "MISSING" not in capsys.readouterr().out
