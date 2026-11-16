"""Build the animation gallery manifest.

Usage:
  python3 tools/anim/gallery.py SRC_DIR PREVIEW_DIR OUT_DIR

Writes ``OUT_DIR/manifest.json`` -- ``[{"name","group","desc_ko","length","loop","gif"}]``
sorted by group order, then name -- and copies each ``<name>.gif`` from PREVIEW_DIR.
"""
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

GROUP_ORDER = ["balanced", "tank", "infight", "archer", "mage", "summoner", "healer", "relic", "common"]


def _group_key(group: str):
    return (GROUP_ORDER.index(group), "") if group in GROUP_ORDER else (len(GROUP_ORDER), group)


def build_manifest(src_dir: Path, preview_dir: Path, out_dir: Path) -> list:
    entries = []
    for p in sorted(Path(src_dir).glob("*.json")):
        src = json.loads(p.read_text())
        for f in ("group", "desc_ko", "length"):
            if f not in src:
                raise ValueError(f"{p.stem}: missing field {f!r}")
        gif = Path(preview_dir) / f"{p.stem}.gif"
        if not gif.is_file():
            raise ValueError(f"{p.stem}: preview gif not found at {gif}")
        entries.append({"name": p.stem, "group": src["group"], "desc_ko": src["desc_ko"],
                        "length": src["length"], "loop": bool(src.get("loop", False)),
                        "gif": gif.name})
    entries.sort(key=lambda e: (_group_key(e["group"]), e["name"]))
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    for e in entries:
        shutil.copyfile(Path(preview_dir) / e["gif"], out_dir / e["gif"])
    (out_dir / "manifest.json").write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n")
    return entries


def main(argv=None) -> int:
    a = sys.argv[1:] if argv is None else argv
    if len(a) != 3:
        print(__doc__)
        return 2
    n = len(build_manifest(Path(a[0]), Path(a[1]), Path(a[2])))
    print(f"gallery manifest with {n} entries in {a[2]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
