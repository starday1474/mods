import json
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build_instance import DEFAULT_PACK_URL, build  # noqa: E402

NEO = "21.1.266"


def _build(tmp_path, url=DEFAULT_PACK_URL):
    boot = tmp_path / "boot.jar"
    boot.write_bytes(b"jar")
    out = build(tmp_path / "out.zip", url, NEO, bootstrap_jar=boot)
    z = zipfile.ZipFile(out)
    cfg = z.read("instance.cfg").decode("utf-8")
    return z, cfg


def test_zip_contents(tmp_path):
    z, _ = _build(tmp_path)
    names = set(z.namelist())
    assert {"instance.cfg", "mmc-pack.json", ".minecraft/packwiz-installer-bootstrap.jar"} <= names


def test_prelaunch_command(tmp_path):
    _, cfg = _build(tmp_path)
    assert f'PreLaunchCommand="$INST_JAVA" -jar packwiz-installer-bootstrap.jar {DEFAULT_PACK_URL}' in cfg
    assert "OverrideCommands=true" in cfg


def test_memory_defaults(tmp_path):
    _, cfg = _build(tmp_path)
    assert "MaxMemAlloc=6144" in cfg
    assert "OverrideMemory=true" in cfg


def test_jvm_args(tmp_path):
    _, cfg = _build(tmp_path)
    assert "-XX:+UseG1GC" in cfg
    assert "OverrideJavaArgs=true" in cfg


def test_neoforge_component(tmp_path):
    z, _ = _build(tmp_path)
    comps = {c["uid"]: c["version"] for c in json.loads(z.read("mmc-pack.json"))["components"]}
    assert comps["net.minecraft"] == "1.21.1"
    assert comps["net.neoforged"] == NEO


def test_custom_pack_url(tmp_path):
    url = "https://raw.githubusercontent.com/starday1474/mods/claude/wizardly-gates-b1hzqk/pack/pack.toml"
    _, cfg = _build(tmp_path, url)
    assert url in cfg
    assert DEFAULT_PACK_URL not in cfg


def test_default_url_is_main():
    assert DEFAULT_PACK_URL == "https://raw.githubusercontent.com/starday1474/mods/main/pack/pack.toml"
