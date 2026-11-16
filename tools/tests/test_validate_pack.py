from pathlib import Path

import pack_rules
from validate_pack import normalize, validate, main


def make_pack(tmp_path: Path, mods: dict[str, dict], mc: str = "1.21.1") -> Path:
    """mods: filename stem -> {"name": str, "side": str, "cf": bool}"""
    pack = tmp_path / "pack"
    (pack / "mods").mkdir(parents=True)
    (pack / "pack.toml").write_text(
        f'name = "t"\n[versions]\nminecraft = "{mc}"\nneoforge = "21.1.266"\n',
        encoding="utf-8",
    )
    for stem, info in mods.items():
        side = info.get("side", "both")
        update = (
            "[update.curseforge]\nfile-id = 1\nproject-id = 2\n"
            if info.get("cf")
            else '[update.modrinth]\nmod-id = "x"\nversion = "y"\n'
        )
        (pack / "mods" / f"{stem}.pw.toml").write_text(
            f'name = "{info.get("name", stem)}"\nfilename = "{stem}.jar"\nside = "{side}"\n\n'
            f'[download]\nurl = "https://example.com/{stem}.jar"\nhash-format = "sha512"\nhash = "00"\n\n'
            + update,
            encoding="utf-8",
        )
    return pack


def required_mods() -> dict[str, dict]:
    mods = {}
    for name in pack_rules.REQUIRED:
        mods[name] = {"side": "client" if name in pack_rules.CLIENT_ONLY else "both"}
    return mods


def test_ok_pack_passes(tmp_path):
    mods = required_mods() | {"waystones": {}}
    report = validate(make_pack(tmp_path, mods), check_defaults_files=False)
    assert report.errors == []


def test_wrong_mc_version(tmp_path):
    report = validate(make_pack(tmp_path, required_mods(), mc="1.20.1"), check_defaults_files=False)
    assert any("1.21.1" in e for e in report.errors)


def test_denied_mod_by_filename(tmp_path):
    mods = required_mods() | {"irons-spells-n-spellbooks": {}}
    report = validate(make_pack(tmp_path, mods), check_defaults_files=False)
    assert any("ironsspellsnspellbooks" in e for e in report.errors)


def test_denied_dependency_detected(tmp_path):
    mods = required_mods() | {"some-lib": {"name": "Better Combat"}}
    report = validate(make_pack(tmp_path, mods), check_defaults_files=False)
    assert any("bettercombat" in e for e in report.errors)


def test_too_many_mods(tmp_path):
    mods = required_mods()
    for i in range(201 - len(mods)):
        mods[f"filler-{i}"] = {}
    report = validate(make_pack(tmp_path, mods), check_defaults_files=False)
    assert any("200" in e for e in report.errors)


def test_missing_required(tmp_path):
    mods = required_mods()
    del mods["essential"]
    report = validate(make_pack(tmp_path, mods), check_defaults_files=False)
    assert any("essential" in e for e in report.errors)


def test_allow_missing_required_flag(tmp_path):
    mods = required_mods()
    del mods["essential"]
    report = validate(make_pack(tmp_path, mods), allow_missing_required=True, check_defaults_files=False)
    assert report.errors == []
    assert any("essential" in w for w in report.warnings)


def test_client_only_side(tmp_path):
    mods = required_mods()
    mods["sodium"] = {"side": "both"}
    report = validate(make_pack(tmp_path, mods), check_defaults_files=False)
    assert any("sodium" in e and "client" in e for e in report.errors)


def test_curseforge_mod_warns(tmp_path):
    mods = required_mods() | {"true-ending": {"cf": True}}
    report = validate(make_pack(tmp_path, mods), check_defaults_files=False)
    assert any("trueending" in w for w in report.warnings)


def test_mod_count_reported(tmp_path):
    mods = required_mods() | {"a": {}, "b": {}}
    report = validate(make_pack(tmp_path, mods), check_defaults_files=False)
    assert report.mod_count == len(mods)


def test_normalize():
    assert normalize("Iron's Spells 'n Spellbooks") == "ironsspellsnspellbooks"
    assert normalize("l_enders-cataclysm") == "lenderscataclysm"


def test_cli_exit_codes(tmp_path):
    good = make_pack(tmp_path / "g", required_mods())
    bad = make_pack(tmp_path / "b", required_mods() | {"journeymap": {}})
    assert main([str(good), "--skip-defaults"]) == 0
    assert main([str(bad), "--skip-defaults"]) == 1


# --- Task 5: first-launch defaults ---
from validate_pack import check_defaults

GOOD_OPTIONS = (
    "renderDistance:10\nsimulationDistance:6\nentityDistanceScaling:0.75\nparticles:1\n"
    "lang:ko_kr\nresourcePacks:[\"vanilla\",\"mod_resources\",\"file/rpg-korean\"]\n"
)


def make_defaults(pack: Path, options: str = GOOD_OPTIONS, iris: str = "enableShaders=false\n",
                  dh: str = "[client]\nquickEnableRendering = false\n") -> Path:
    y = pack / "config" / "yosbr"
    (y / "config").mkdir(parents=True, exist_ok=True)
    (y / "options.txt").write_text(options, encoding="utf-8")
    (y / "config" / "iris.properties").write_text(iris, encoding="utf-8")
    (y / "config" / "DistantHorizons.toml").write_text(dh, encoding="utf-8")
    return pack


def test_defaults_present_and_correct(tmp_path):
    pack = make_defaults(make_pack(tmp_path, required_mods()))
    assert check_defaults(pack) == []


def test_defaults_missing_files(tmp_path):
    pack = make_pack(tmp_path, required_mods())
    assert any("options.txt" in e for e in check_defaults(pack))


def test_defaults_wrong_value(tmp_path):
    pack = make_defaults(make_pack(tmp_path, required_mods()),
                         options=GOOD_OPTIONS.replace("renderDistance:10", "renderDistance:12"))
    assert any("renderDistance" in e for e in check_defaults(pack))


def test_defaults_resource_pack_enabled(tmp_path):
    pack = make_defaults(make_pack(tmp_path, required_mods()),
                         options=GOOD_OPTIONS.replace(',"file/rpg-korean"', ""))
    assert any("rpg-korean" in e for e in check_defaults(pack))


def test_iris_disabled(tmp_path):
    pack = make_defaults(make_pack(tmp_path, required_mods()), iris="enableShaders=true\n")
    assert any("enableShaders" in e for e in check_defaults(pack))


def test_dh_disabled(tmp_path):
    pack = make_defaults(make_pack(tmp_path, required_mods()),
                         dh="[client]\nquickEnableRendering = true\n")
    assert any("quickEnableRendering" in e for e in check_defaults(pack))


def test_no_root_options_txt(tmp_path):
    pack = make_defaults(make_pack(tmp_path, required_mods()))
    (pack / "options.txt").write_text("renderDistance:10\n", encoding="utf-8")
    assert any("options.txt" in e for e in check_defaults(pack))


def test_validate_runs_defaults_check(tmp_path):
    report = validate(make_pack(tmp_path, required_mods()))
    assert any("options.txt" in e for e in report.errors)
