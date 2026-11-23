import subprocess
import sys
from pathlib import Path

from . import anim, marks, media, paths, store, tools


def render_ours(anim_name: str, out_gif: Path) -> Path:
    """우리 동작을 preview.py로 GIF로 만든다. 원본 json보다 새 캐시가 있으면 재사용."""
    paths.check_name(anim_name, "동작 이름")
    src = paths.repo() / "mod" / "anim" / f"{anim_name}.json"
    if not src.is_file():
        raise FileNotFoundError(f"mod/anim/{anim_name}.json 이 없습니다.")
    out_gif = Path(out_gif)
    if out_gif.is_file() and out_gif.stat().st_mtime >= src.stat().st_mtime:
        return out_gif
    out_gif.parent.mkdir(parents=True, exist_ok=True)
    preview = paths.repo() / "tools" / "anim" / "preview.py"
    r = subprocess.run([sys.executable, str(preview), str(src), str(out_gif)],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0 or not out_gif.is_file():
        raise RuntimeError(f"우리 동작 미리보기에 실패했습니다: {(r.stderr or '').strip()}")
    return out_gif


def align_offsets(ref_impact: float, our_contact: float) -> tuple[float, float]:
    """타격 시각이 같아지도록 앞에 붙일 대기 시간(ref_pad, our_pad)."""
    d = ref_impact - our_contact
    return (0.0, d) if d >= 0 else (-d, 0.0)


def side_by_side(ref_clip: Path, ours: Path, out: Path, ref_pad: float, our_pad: float,
                 height: int = 480) -> Path:
    ref_clip, ours, out = Path(ref_clip), Path(ours), Path(out)
    d_ref = media.probe_duration(ref_clip) + ref_pad
    d_our = media.probe_duration(ours) + our_pad
    total = max(d_ref, d_our)

    def chain(idx, pad, dur, label):
        return (f"[{idx}:v]fps=30,tpad=start_duration={pad:.3f}:start_mode=clone"
                f":stop_duration={total - dur + 0.1:.3f}:stop_mode=clone,"
                f"scale=-2:{int(height)},setsar=1[{label}]")

    fc = ";".join([chain(0, ref_pad, d_ref, "a"), chain(1, our_pad, d_our, "b"),
                   "[a][b]hstack=inputs=2[v]"])
    out.parent.mkdir(parents=True, exist_ok=True)
    media._ffmpeg("-i", ref_clip, "-i", ours, "-filter_complex", fc, "-map", "[v]",
                  "-t", f"{total:.3f}", "-an", "-c:v", "libx264", "-pix_fmt", "yuv420p", out)
    media._ffmpeg("-i", out, "-vf", "setpts=2*PTS", "-an", "-c:v", "libx264",
                  "-pix_fmt", "yuv420p", out.with_name("compare_slow.mp4"))
    return out


def compare_motion(motion: str, anim_name: str | None = None) -> list[Path]:
    name = anim_name or motion
    refs = store.load(motion)
    results: list[Path] = []
    ours = None
    phase = None
    for r in refs:
        if not all(k in r.marks for k in marks.LABELS) or marks.validate(r.marks):
            print(f"{r.id}: 먼저 mark 하세요. (표시가 없어 건너뜁니다)")
            continue
        tools.require("ffmpeg")
        tools.require("ffprobe")
        if ours is None:
            _length, phase = anim.load_phase(name)
            if not phase or "contact" not in phase:
                raise ValueError(f"mod/anim/{name}.json 에 phase.contact 가 없습니다.")
            ours = render_ours(name, paths.library() / "_ours" / f"{name}.gif")
        d = paths.ref_dir(motion, r.id)
        clip = d / "clip.mp4"
        if not clip.is_file():
            try:
                clip = media.clip_ref(motion, r)["clip"]
            except FileNotFoundError:
                print(f"{r.id}: 영상 파일이 없어 건너뜁니다. import 또는 fetch를 먼저 하세요.")
                continue
        ref_pad, our_pad = align_offsets(r.marks["impact"], phase["contact"])
        results.append(side_by_side(clip, ours, d / "compare.mp4", ref_pad, our_pad))
        print(f"{r.id}: {d / 'compare.mp4'}")
    return results
