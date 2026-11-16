import json
import zipfile
from pathlib import Path

from lang_report import missing_keys, write_coverage


def make_jar(path: Path, modid: str, en: dict | None, ko: dict | None) -> Path:
    with zipfile.ZipFile(path, "w") as z:
        if en is not None:
            z.writestr(f"assets/{modid}/lang/en_us.json", json.dumps(en))
        if ko is not None:
            z.writestr(f"assets/{modid}/lang/ko_kr.json", json.dumps(ko))
    return path


def test_missing_keys_basic(tmp_path):
    jar = make_jar(tmp_path / "x.jar", "modx", {"a": "A", "b": "B", "c": "C"}, {"a": "가"})
    assert missing_keys(jar, tmp_path / "overlay") == {"modx": ["b", "c"]}


def test_overlay_fills_keys(tmp_path):
    jar = make_jar(tmp_path / "x.jar", "modx", {"a": "A", "b": "B", "c": "C"}, {"a": "가"})
    lang = tmp_path / "overlay" / "assets" / "modx" / "lang"
    lang.mkdir(parents=True)
    (lang / "ko_kr.json").write_text(json.dumps({"b": "나"}), encoding="utf-8")
    assert missing_keys(jar, tmp_path / "overlay") == {"modx": ["c"]}


def test_no_en_us_ignored(tmp_path):
    jar = make_jar(tmp_path / "x.jar", "modx", None, {"a": "가"})
    assert missing_keys(jar, tmp_path / "overlay") == {}


def test_fully_translated_listed_empty(tmp_path):
    jar = make_jar(tmp_path / "x.jar", "modx", {"a": "A"}, {"a": "가"})
    assert missing_keys(jar, tmp_path / "overlay") == {"modx": []}


def test_coverage_table(tmp_path):
    out = tmp_path / "coverage.md"
    write_coverage({"modx": {"total": 3, "missing": ["b", "c"]}}, out)
    text = out.read_text(encoding="utf-8")
    assert "| 모드 |" in text
    assert "modx" in text
    assert "66.7%" in text


def test_nested_jar_scanned(tmp_path):
    inner = make_jar(tmp_path / "inner.jar", "innermod", {"a": "A", "b": "B"}, {"a": "가"})
    outer = tmp_path / "outer.jar"
    with zipfile.ZipFile(outer, "w") as z:
        z.write(inner, "META-INF/jarjar/inner.jar")
    assert missing_keys(outer, tmp_path / "overlay") == {"innermod": ["b"]}
