"""모드 jar의 en_us 대비 ko_kr 누락 키 보고.

사용법: python3 tools/lang_report.py .cache/jars pack/resourcepacks/rpg-korean docs/translation/coverage.md
"""
from __future__ import annotations

import io
import json
import re
import sys
import zipfile
from pathlib import Path

_LANG = re.compile(r"^assets/([^/]+)/lang/(en_us|ko_kr)\.json$")


def _load(raw: bytes) -> dict:
    try:
        return json.loads(raw.decode("utf-8-sig"))
    except (ValueError, UnicodeDecodeError):
        return {}


def key_stats(jar: Path, overlay_dir: Path) -> dict[str, dict]:
    """modid -> {"total": en_us 키 수, "missing": ko_kr에 없는 키 목록(정렬)}"""
    en: dict[str, dict] = {}
    ko: dict[str, dict] = {}

    def scan(z: zipfile.ZipFile) -> None:
        for name in z.namelist():
            m = _LANG.match(name)
            if m:
                (en if m.group(2) == "en_us" else ko)[m.group(1)] = _load(z.read(name))
            elif name.startswith("META-INF/jarjar/") and name.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(z.read(name))) as inner:
                    scan(inner)

    with zipfile.ZipFile(jar) as z:
        scan(z)
    stats = {}
    for modid, en_keys in en.items():
        have = set(ko.get(modid, {}))
        overlay = overlay_dir / "assets" / modid / "lang" / "ko_kr.json"
        if overlay.exists():
            have |= set(_load(overlay.read_bytes()))
        stats[modid] = {"total": len(en_keys), "missing": sorted(set(en_keys) - have)}
    return stats


def missing_keys(jar: Path, overlay_dir: Path) -> dict[str, list[str]]:
    return {m: s["missing"] for m, s in key_stats(jar, overlay_dir).items()}


def write_coverage(results: dict[str, dict], out: Path) -> None:
    lines = [
        "# 한국어 번역 현황",
        "",
        "`python3 tools/lang_report.py`로 자동 생성. 누락 비율이 높은 모드부터 번역합니다.",
        "",
        "| 모드 | 전체 키 | 누락 키 | 누락 비율 |",
        "|---|---|---|---|",
    ]
    def ratio(s):
        return len(s["missing"]) / s["total"] if s["total"] else 0.0
    for modid, s in sorted(results.items(), key=lambda kv: (-ratio(kv[1]), kv[0])):
        lines.append(f"| {modid} | {s['total']} | {len(s['missing'])} | {ratio(s) * 100:.1f}% |")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(jar_dir: Path, overlay_dir: Path, out: Path) -> None:
    results: dict[str, dict] = {}
    for jar in sorted(jar_dir.glob("*.jar")):
        results.update(key_stats(jar, overlay_dir))
    write_coverage(results, out)
    print(f"{len(results)}개 모드 분석 → {out}")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
