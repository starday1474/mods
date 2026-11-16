"""packwiz pw.toml에 적힌 모드 jar를 캐시 폴더에 받는다 (해시 검증).

사용법: python3 tools/fetch_jars.py pack .cache/jars
"""
from __future__ import annotations

import hashlib
import sys
import tomllib
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path


class HashMismatch(Exception):
    pass


@dataclass
class FetchResult:
    downloaded: list[Path] = field(default_factory=list)
    skipped: list[str] = field(default_factory=list)


def _digest(path: Path, algo: str) -> str:
    return hashlib.new(algo, path.read_bytes()).hexdigest()


def fetch(pack_dir: Path, cache_dir: Path) -> FetchResult:
    cache_dir.mkdir(parents=True, exist_ok=True)
    result = FetchResult()
    for pw in sorted((pack_dir / "mods").glob("*.pw.toml")):
        data = tomllib.loads(pw.read_text(encoding="utf-8"))
        dl = data.get("download", {})
        stem = pw.name.removesuffix(".pw.toml")
        if "url" not in dl:
            result.skipped.append(stem)
            continue
        target = cache_dir / data["filename"]
        algo, want = dl["hash-format"], dl["hash"].lower()
        if target.exists() and _digest(target, algo) == want:
            result.downloaded.append(target)
            continue
        req = urllib.request.Request(dl["url"], headers={"User-Agent": "rpg-modpack-tools"})
        with urllib.request.urlopen(req, timeout=120) as r:
            target.write_bytes(r.read())
        if _digest(target, algo) != want:
            target.unlink()
            raise HashMismatch(f"{stem}: {algo} 해시 불일치")
        result.downloaded.append(target)
    return result


if __name__ == "__main__":
    res = fetch(Path(sys.argv[1]), Path(sys.argv[2]))
    print(f"받음: {len(res.downloaded)}개, 건너뜀(URL 없음): {', '.join(res.skipped) or '없음'}")
