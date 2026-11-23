"""랜드마크(월드 좌표) -> 각도/속도/타이밍 통계. mediapipe 없이 동작한다."""
import warnings

import numpy as np

JOINTS = {
    "nose": 0,
    "shoulder_l": 11, "shoulder_r": 12,
    "elbow_l": 13, "elbow_r": 14,
    "wrist_l": 15, "wrist_r": 16,
    "hip_l": 23, "hip_r": 24,
    "knee_l": 25, "knee_r": 26,
    "ankle_l": 27, "ankle_r": 28,
}
NO_PERSON_WARNING = "사람이 잡힌 프레임이 50% 미만"


def angle(a, b, c) -> np.ndarray:
    """b를 꼭짓점으로 하는 각도(도). (N,3) 입력, NaN은 전파된다."""
    a, b, c = (np.asarray(x, dtype=float) for x in (a, b, c))
    u, v = a - b, c - b
    nu, nv = np.linalg.norm(u, axis=-1), np.linalg.norm(v, axis=-1)
    with np.errstate(invalid="ignore", divide="ignore"):
        cos = np.einsum("...i,...i->...", u, v) / (nu * nv)
    return np.degrees(np.arccos(np.clip(cos, -1.0, 1.0)))


def _line_angle(p_l: np.ndarray, p_r: np.ndarray) -> np.ndarray:
    """좌->우 선의 수평면(x-z) 각도(도)."""
    d = p_r - p_l
    return np.degrees(np.arctan2(d[:, 2], d[:, 0]))


def joint_angles(lm: np.ndarray) -> dict[str, np.ndarray]:
    j = JOINTS
    out = {}
    for s in ("l", "r"):
        out[f"elbow_{s}"] = angle(lm[:, j[f"shoulder_{s}"]], lm[:, j[f"elbow_{s}"]], lm[:, j[f"wrist_{s}"]])
        out[f"shoulder_{s}"] = angle(lm[:, j[f"elbow_{s}"]], lm[:, j[f"shoulder_{s}"]], lm[:, j[f"hip_{s}"]])
        out[f"knee_{s}"] = angle(lm[:, j[f"hip_{s}"]], lm[:, j[f"knee_{s}"]], lm[:, j[f"ankle_{s}"]])
    diff = _line_angle(lm[:, j["shoulder_l"]], lm[:, j["shoulder_r"]]) - \
        _line_angle(lm[:, j["hip_l"]], lm[:, j["hip_r"]])
    out["torso_twist"] = np.abs((diff + 180.0) % 360.0 - 180.0)
    sh = (lm[:, j["shoulder_l"]] + lm[:, j["shoulder_r"]]) / 2
    hip = (lm[:, j["hip_l"]] + lm[:, j["hip_r"]]) / 2
    up = sh - hip
    vertical = np.zeros_like(up)
    vertical[:, 1] = -1.0  # MediaPipe 월드 좌표는 y가 아래쪽이다
    out["torso_lean"] = angle(up + hip, hip, hip + vertical)
    return out


def speed(lm: np.ndarray, joint: int, fps: float) -> np.ndarray:
    """프레임 간 이동 속도(m/s). 이웃 프레임이 NaN이면 NaN."""
    p = lm[:, joint]
    n = len(p)
    out = np.full(n, np.nan)
    if n < 2:
        return out
    out[0] = np.linalg.norm(p[1] - p[0]) * fps
    out[-1] = np.linalg.norm(p[-1] - p[-2]) * fps
    if n > 2:
        out[1:-1] = np.linalg.norm(p[2:] - p[:-2], axis=1) * fps / 2
    out[np.isnan(p).any(axis=1)] = np.nan
    return out


def _smooth(x: np.ndarray, w: int = 5) -> np.ndarray:
    pad = w // 2
    padded = np.concatenate([np.full(pad, np.nan), x, np.full(pad, np.nan)])
    win = np.lib.stride_tricks.sliding_window_view(padded, w)
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", RuntimeWarning)
        return np.nanmean(win, axis=1)


def suggest_marks(lm: np.ndarray, fps: float, joint: int = 16) -> dict[str, float] | None:
    sp = speed(lm, joint, fps)
    if np.count_nonzero(~np.isnan(sp)) < 10:
        return None
    sm = _smooth(sp)
    if np.isnan(sm).all():
        return None
    peak = float(np.nanmax(sm))
    if not peak > 0:
        return None
    i = int(np.nanargmax(sm))
    low = sm < 0.2 * peak  # NaN은 False
    before = np.flatnonzero(low[:i])
    after = np.flatnonzero(low[i + 1:])
    start = int(before[-1]) if len(before) else 0
    settle = int(after[0]) + i + 1 if len(after) else len(sm) - 1
    return {"start": start / fps, "impact": i / fps, "settle": settle / fps}


def summarize(lm: np.ndarray, fps: float) -> dict:
    n = len(lm)
    valid = ~np.isnan(lm).all(axis=(1, 2)) if n else np.zeros(0, bool)
    detected = float(valid.mean()) if n else 0.0
    res: dict = {"frames": n, "detected": round(detected, 3), "angles": {}}
    if detected > 0:
        for name, v in joint_angles(lm).items():
            if np.isnan(v).all():
                continue
            lo, hi = float(np.nanmin(v)), float(np.nanmax(v))
            res["angles"][name] = {"min": round(lo, 1), "max": round(hi, 1), "range": round(hi - lo, 1)}
    marks = suggest_marks(lm, fps) if detected > 0 else None
    res["suggested_marks"] = {k: round(v, 3) for k, v in marks.items()} if marks else None
    if detected < 0.5:
        res["warning"] = NO_PERSON_WARNING
    return res
