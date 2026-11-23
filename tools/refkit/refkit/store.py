import math
from dataclasses import dataclass, field, fields

import yaml

from . import paths

KINDS = ("video", "image", "mocap", "own")


def parse_time(s) -> float:
    """'62', '1:02', '1:02.5', '0:01:02' 또는 숫자 -> 초."""
    if isinstance(s, bool):
        raise ValueError(f"잘못된 시간: {s!r}")
    if isinstance(s, (int, float)):
        v = float(s)
    else:
        text = str(s).strip()
        parts = text.split(":")
        if not text or len(parts) > 3:
            raise ValueError(f"잘못된 시간: {s!r}")
        try:
            nums = [float(p) for p in parts]
        except ValueError:
            raise ValueError(f"잘못된 시간: {s!r}") from None
        if any(p.strip().startswith(("-", "+")) for p in parts):
            raise ValueError(f"잘못된 시간: {s!r}")
        v = 0.0
        for n in nums:
            v = v * 60 + n
    if not math.isfinite(v) or v < 0:
        raise ValueError(f"잘못된 시간: {s!r}")
    return v


def format_time(t: float) -> str:
    t = round(float(t), 3)
    m, sec = divmod(t, 60)
    m = int(m)
    whole = int(sec)
    frac = round(sec - whole, 3)
    out = f"{m}:{whole:02d}"
    if frac:
        out += f"{frac:.3f}".rstrip("0")[1:]
    return out


@dataclass
class Ref:
    id: str
    kind: str
    url: str = ""
    start: float | None = None
    end: float | None = None
    tags: list[str] = field(default_factory=list)
    take: str = ""
    license: str = ""
    marks: dict[str, float] = field(default_factory=dict)
    pose: dict = field(default_factory=dict)
    file: str = ""


def _path(motion: str):
    return paths.refs_dir() / f"{paths.check_name(motion, '동작 이름')}.yaml"


def _to_dict(r: Ref) -> dict:
    d: dict = {"id": r.id, "kind": r.kind}
    if r.url:
        d["url"] = r.url
    if r.start is not None:
        d["start"] = format_time(r.start)
    if r.end is not None:
        d["end"] = format_time(r.end)
    if r.tags:
        d["tags"] = list(r.tags)
    if r.take:
        d["take"] = r.take
    if r.license:
        d["license"] = r.license
    if r.marks:
        d["marks"] = {k: format_time(v) for k, v in r.marks.items()}
    if r.pose:
        d["pose"] = r.pose
    if r.file:
        d["file"] = r.file
    return d


def _from_dict(d: dict) -> Ref:
    known = {f.name for f in fields(Ref)}
    kw = {k: v for k, v in d.items() if k in known}
    for k in ("start", "end"):
        if kw.get(k) is not None:
            kw[k] = parse_time(kw[k])
    if kw.get("marks"):
        kw["marks"] = {k: parse_time(v) for k, v in kw["marks"].items()}
    for k in ("url", "take", "license", "file"):
        if kw.get(k) is None:
            kw.pop(k, None)
    return Ref(**kw)


def load(motion: str) -> list[Ref]:
    p = _path(motion)
    if not p.exists():
        return []
    data = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
    return [_from_dict(d) for d in data.get("refs", [])]


def save(motion: str, refs: list[Ref]):
    p = _path(motion)
    p.parent.mkdir(parents=True, exist_ok=True)
    doc = {"motion": motion, "refs": [_to_dict(r) for r in refs]}
    p.write_text(
        yaml.safe_dump(doc, allow_unicode=True, sort_keys=False), encoding="utf-8"
    )
    return p


def _empty(v) -> bool:
    return v is None or v == "" or v == [] or v == {}


def _merge(old: Ref, new: Ref) -> None:
    for f in fields(Ref):
        nv = getattr(new, f.name)
        if _empty(nv):
            continue
        ov = getattr(old, f.name)
        if isinstance(ov, dict) and isinstance(nv, dict):
            ov.update(nv)
        else:
            setattr(old, f.name, nv)


def _next_id(refs: list[Ref], kind: str) -> str:
    n = 0
    prefix = kind + "-"
    for r in refs:
        if r.id.startswith(prefix) and r.id[len(prefix):].isdigit():
            n = max(n, int(r.id[len(prefix):]))
    return f"{kind}-{n + 1:02d}"


def upsert(motion: str, ref: Ref) -> Ref:
    refs = load(motion)
    for r in refs:
        same_id = bool(ref.id) and r.id == ref.id
        same_src = bool(ref.url) and r.url == ref.url and r.start == ref.start
        if same_id or same_src:
            _merge(r, ref)
            save(motion, refs)
            return r
    if not ref.id:
        ref.id = _next_id(refs, ref.kind)
    refs.append(ref)
    save(motion, refs)
    return ref


def get(motion: str, ref_id: str) -> Ref:
    for r in load(motion):
        if r.id == ref_id:
            return r
    raise KeyError(ref_id)
