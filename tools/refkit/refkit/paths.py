import os
from pathlib import Path


def repo() -> Path:
    env = os.environ.get("REFKIT_REPO")
    if env:
        return Path(env)
    return Path(__file__).resolve().parents[3]


def library() -> Path:
    env = os.environ.get("REFKIT_LIBRARY")
    if env:
        return Path(env)
    return Path.home() / "refkit-library"


def refs_dir() -> Path:
    return repo() / "docs" / "refs"


def check_name(name: str, what: str = "이름") -> str:
    """경로 구성 요소로 쓰이는 이름 검증."""
    if not name or name in (".", "..") or any(c in name for c in '/\\:*?"<>|'):
        raise ValueError(f"잘못된 {what}: {name!r}")
    return name


def ref_dir(motion: str, ref_id: str) -> Path:
    d = library() / check_name(motion, "동작 이름") / check_name(ref_id, "레퍼런스 id")
    d.mkdir(parents=True, exist_ok=True)
    return d
