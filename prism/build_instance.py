"""친구용 Prism Launcher 인스턴스 zip 만들기.

사용법: python3 prism/build_instance.py build/RPG-modpack.zip [--pack-url URL]
"""
from __future__ import annotations

import argparse
import tomllib
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HERE = Path(__file__).resolve().parent
DEFAULT_PACK_URL = "https://raw.githubusercontent.com/starday1474/mods/main/pack/pack.toml"
BOOTSTRAP_URL = (
    "https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/"
    "packwiz-installer-bootstrap.jar"
)
CACHED_BOOTSTRAP = ROOT / "build" / "server" / "packwiz-installer-bootstrap.jar"


def _bootstrap() -> Path:
    if not CACHED_BOOTSTRAP.exists():
        CACHED_BOOTSTRAP.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(BOOTSTRAP_URL, timeout=120) as r:
            CACHED_BOOTSTRAP.write_bytes(r.read())
    return CACHED_BOOTSTRAP


def build(out_zip: Path, pack_url: str, neoforge_version: str, bootstrap_jar: Path | None = None) -> Path:
    cfg = (HERE / "instance.cfg").read_text(encoding="utf-8").replace("{PACK_URL}", pack_url)
    pack = (HERE / "mmc-pack.json").read_text(encoding="utf-8").replace("{NEOFORGE_VERSION}", neoforge_version)
    out_zip.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("instance.cfg", cfg)
        z.writestr("mmc-pack.json", pack)
        z.write(bootstrap_jar or _bootstrap(), ".minecraft/packwiz-installer-bootstrap.jar")
    return out_zip


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("out_zip", type=Path)
    p.add_argument("--pack-url", default=DEFAULT_PACK_URL)
    a = p.parse_args()
    neo = tomllib.loads((ROOT / "pack" / "pack.toml").read_text(encoding="utf-8"))["versions"]["neoforge"]
    print(build(a.out_zip, a.pack_url, neo))


if __name__ == "__main__":
    main()
