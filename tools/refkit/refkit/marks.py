import math
import subprocess
from importlib import resources
from pathlib import Path

from . import anim, store, tools

LABELS = ("start", "impact", "settle")
THRESHOLD = 0.15  # 15%p
FANTASY = (1.2, 1.5)


def parse_marks_file(path: Path) -> dict[str, float]:
    out: dict[str, float] = {}
    path = Path(path)
    if not path.is_file():
        return out
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        parts = line.split()
        if len(parts) != 2:
            continue
        label, val = parts
        if label == "clear":
            out.clear()
            continue
        if label not in LABELS:
            continue
        try:
            v = float(val)
        except ValueError:
            continue
        if math.isfinite(v):
            out[label] = v
    return out


def validate(m: dict) -> list[str]:
    warns = [f"{k} 표시가 없습니다." for k in LABELS if k not in m]
    if warns:
        return warns
    if not m["start"] < m["impact"] < m["settle"]:
        warns.append("표시 순서가 맞지 않습니다: start < impact < settle 이어야 합니다.")
    return warns


def run_mpv(clip: Path, out: Path) -> None:
    mpv = tools.require("mpv")
    lua = resources.files("refkit") / "mpv" / "refkit_marks.lua"
    with resources.as_file(lua) as lua_path:
        cmd = [mpv, "--loop-file=inf", "--speed=0.5", "--keep-open=yes",
               f"--script={lua_path}", f"--script-opts=refkit-out={out}", str(clip)]
        subprocess.run(cmd)


def ratios_ref(m: dict) -> dict:
    total = m["settle"] - m["start"]
    return {"windup": (m["impact"] - m["start"]) / total,
            "recovery": (m["settle"] - m["impact"]) / total,
            "total": total}


def ratios_ours(length: float, phase: dict) -> dict:
    c = phase["contact"]
    return {"windup": c / length, "recovery": (length - c) / length, "total": length}


def timing_report(motion: str, anim_name: str | None = None) -> str:
    name = anim_name or motion
    length, phase = anim.load_phase(name)
    if not phase or "contact" not in phase:
        raise ValueError(f"mod/anim/{name}.json 에 phase.contact 가 없습니다.")
    ours = ratios_ours(length, phase)
    rows = []
    for r in store.load(motion):
        if all(k in r.marks for k in LABELS) and not validate(r.marks):
            rows.append((r.id, ratios_ref(r.marks)))
    lines = [f"동작 {motion} (우리: {name})", "id\t예비\t회복\t길이(초)"]
    for rid, q in rows:
        lines.append(f"{rid}\t{q['windup']:.2f}\t{q['recovery']:.2f}\t{q['total']:.2f}")
    lines.append(f"우리\t{ours['windup']:.2f}\t{ours['recovery']:.2f}\t{ours['total']:.2f}")
    if not rows:
        lines.append("표시가 있는 레퍼런스가 없습니다. mark로 먼저 표시하세요.")
        return "\n".join(lines)
    avg = {k: sum(q[k] for _, q in rows) / len(rows) for k in ("windup", "recovery", "total")}
    lines.insert(-1, f"평균\t{avg['windup']:.2f}\t{avg['recovery']:.2f}\t{avg['total']:.2f}")
    for key, label in (("windup", "예비 동작"), ("recovery", "회복 동작")):
        diff = ours[key] - avg[key]
        if abs(diff) > THRESHOLD:
            kind = "짧음" if diff < 0 else "김"
            mult = ours[key] / avg[key] if avg[key] else float("inf")
            lines.append(f"제안: {label}이 {kind}(레퍼런스 평균 대비 {mult:.1f}배)")
    lines.append(f"참고: 재미있는 판타지 동작은 예비 동작을 {FANTASY[0]}~{FANTASY[1]}배 "
                 "과장하는 편입니다.")
    return "\n".join(lines)
