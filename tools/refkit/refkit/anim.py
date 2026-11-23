"""우리 동작(mod/anim/*.json)을 읽기만 한다."""
import json

from . import paths


def load_phase(name: str) -> tuple[float, dict | None]:
    paths.check_name(name, "동작 이름")
    p = paths.repo() / "mod" / "anim" / f"{name}.json"
    if not p.is_file():
        raise FileNotFoundError(f"mod/anim/{name}.json 이 없습니다.")
    data = json.loads(p.read_text(encoding="utf-8"))
    return float(data["length"]), data.get("phase")
