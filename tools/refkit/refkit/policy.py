from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urlparse

import yaml

from .store import Ref


@dataclass
class Policy:
    default: str = "link"
    fetch_allowed: list[str] = field(default_factory=list)

    def can_fetch(self, ref: Ref) -> tuple[bool, str]:
        allowed = set(self.fetch_allowed)
        if ref.kind == "own" and "own" in allowed:
            return True, "직접 만든 자료라 내려받을 수 있습니다."
        host = (urlparse(ref.url).hostname or "").lower()
        for a in allowed:
            if host and not a.startswith("license:") and a != "own":
                if host == a or host.endswith("." + a):
                    return True, f"허용된 출처({a})입니다."
        if ref.license and f"license:{ref.license.lower()}" in allowed:
            return True, f"허용된 라이선스({ref.license})입니다."
        return False, "허용된 출처가 아니어서 링크만 저장합니다. (tools/refkit/sources.yaml 참고)"


def load_policy(path: Path | None = None) -> Policy:
    p = path or Path(__file__).resolve().parents[1] / "sources.yaml"
    data = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
    return Policy(
        default=data.get("default", "link"),
        fetch_allowed=[str(x) for x in data.get("fetch_allowed", [])],
    )


def timestamped_url(url: str, start: float | None) -> str:
    if start is None:
        return url
    host = (urlparse(url).hostname or "").lower()
    if any(host == h or host.endswith("." + h) for h in ("youtube.com", "youtu.be", "bilibili.com")):
        sep = "&" if "?" in url else "?"
        return f"{url}{sep}t={int(start)}"
    return url
