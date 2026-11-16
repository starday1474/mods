"""packwiz 모드팩 규칙 검사.

사용법: python3 tools/validate_pack.py pack [--allow-missing-required] [--skip-defaults]
"""
from __future__ import annotations

import argparse
import re
import sys
import tomllib
from dataclasses import dataclass, field
from pathlib import Path

import pack_rules


@dataclass
class Report:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    mod_count: int = 0


def normalize(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower())


def _mod_names(stem: str, data: dict) -> set[str]:
    return {normalize(stem), normalize(data.get("name", ""))} - {""}


def check_defaults(pack_dir: Path) -> list[str]:
    return []


def validate(
    pack_dir: Path,
    allow_missing_required: bool = False,
    check_defaults_files: bool = True,
) -> Report:
    report = Report()
    pack = tomllib.loads((pack_dir / "pack.toml").read_text(encoding="utf-8"))
    mc = pack.get("versions", {}).get("minecraft")
    if mc != pack_rules.MC_VERSION:
        report.errors.append(f"Minecraft 버전이 {pack_rules.MC_VERSION}이 아님: {mc}")
    if "neoforge" not in pack.get("versions", {}):
        report.errors.append("NeoForge 버전이 지정되지 않음")

    present: set[str] = set()
    mod_files = sorted((pack_dir / "mods").glob("*.pw.toml"))
    report.mod_count = len(mod_files)
    for path in mod_files:
        stem = path.name.removesuffix(".pw.toml")
        data = tomllib.loads(path.read_text(encoding="utf-8"))
        names = _mod_names(stem, data)
        present |= names
        for n in names:
            if n in pack_rules.DENIED:
                report.errors.append(f"넣지 않기로 한 모드: {n} ({path.name}) — {pack_rules.DENIED[n]}")
            if n in pack_rules.CLIENT_ONLY and data.get("side") != "client":
                report.errors.append(f"클라이언트 전용 모드 {n}의 side가 client가 아님: {data.get('side')}")
        if "curseforge" in data.get("update", {}):
            report.warnings.append(f"CurseForge 모드 (자동 다운로드 확인 필요): {normalize(stem)}")

    if report.mod_count > pack_rules.MAX_MODS:
        report.errors.append(f"모드 수 {report.mod_count}개가 {pack_rules.MAX_MODS}개를 넘음")

    for req in sorted(pack_rules.REQUIRED - present):
        msg = f"필수 모드 없음: {req}"
        (report.warnings if allow_missing_required else report.errors).append(msg)

    if check_defaults_files:
        report.errors.extend(check_defaults(pack_dir))
    return report


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("pack_dir", type=Path)
    p.add_argument("--allow-missing-required", action="store_true")
    p.add_argument("--skip-defaults", action="store_true")
    a = p.parse_args(argv)
    r = validate(a.pack_dir, a.allow_missing_required, not a.skip_defaults)
    for w in r.warnings:
        print(f"경고: {w}")
    for e in r.errors:
        print(f"오류: {e}")
    print(f"모드 수: {r.mod_count}")
    return 1 if r.errors else 0


if __name__ == "__main__":
    sys.exit(main())
