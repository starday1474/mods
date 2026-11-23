import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw

from . import paths, store, tools


def _ffmpeg(*args) -> None:
    cmd = [tools.require("ffmpeg"), "-nostdin", "-y", "-hide_banner", "-loglevel", "error", *map(str, args)]
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise RuntimeError(f"ffmpeg 실패: {r.stderr.strip()}")


def _probe(path: Path, *args) -> str:
    cmd = [tools.require("ffprobe"), "-v", "error", *args, str(path)]
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise RuntimeError(f"ffprobe 실패: {r.stderr.strip()}")
    return r.stdout.strip()


def _t(x: float) -> str:
    return f"{x:.3f}"


def probe_duration(path: Path) -> float:
    return float(_probe(path, "-show_entries", "format=duration", "-of", "default=nw=1:nk=1"))


def _probe_fps(path: Path) -> float:
    out = _probe(path, "-select_streams", "v:0", "-show_entries", "stream=r_frame_rate",
                 "-of", "default=nw=1:nk=1")
    num, _, den = out.partition("/")
    return float(num) / float(den or 1)


def cut(src: Path, dst: Path, start: float | None, end: float | None, fps: int = 30) -> Path:
    dur = probe_duration(src)
    s = 0.0 if start is None else float(start)
    e = dur if end is None else min(float(end), dur)
    if e <= s:
        raise ValueError("끝 시각은 시작 시각보다 커야 합니다.")
    vf = f"fps={fps},scale=-2:min(720\\,trunc(ih/2)*2),format=yuv420p"
    dst.parent.mkdir(parents=True, exist_ok=True)
    _ffmpeg("-ss", _t(s), "-i", src, "-t", _t(e - s), "-vf", vf,
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-r", fps, dst)
    return dst


def slowmo(src: Path, dst: Path, factor: float = 4.0) -> Path:
    dst.parent.mkdir(parents=True, exist_ok=True)
    _ffmpeg("-i", src, "-vf", f"setpts={factor:g}*PTS", "-an",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", dst)
    return dst


def contact_sheet(src: Path, dst: Path, cols: int = 6, every: int = 3) -> Path:
    fps = _probe_fps(src)
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        _ffmpeg("-i", src, "-vf", f"select=not(mod(n\\,{int(every)})),scale='min(320,iw)':-2",
                "-fps_mode", "vfr", td / "f%05d.png")
        frames = sorted(td.glob("f*.png"))
        if not frames:
            raise RuntimeError("프레임을 뽑지 못했습니다.")
        images = [Image.open(f).convert("RGB") for f in frames]
        w, h = images[0].size
        rows = -(-len(images) // cols)
        sheet = Image.new("RGB", (w * cols, h * rows), "black")
        draw = ImageDraw.Draw(sheet)
        for i, im in enumerate(images):
            x, y = (i % cols) * w, (i // cols) * h
            sheet.paste(im, (x, y))
            label = f"{i * every / fps:.2f}s"
            box = draw.textbbox((x + 2, y + 2), label)
            draw.rectangle(box, fill="black")
            draw.text((x + 2, y + 2), label, fill="white")
        dst.parent.mkdir(parents=True, exist_ok=True)
        sheet.save(dst)
        for im in images:
            im.close()
    return dst


def source_path(ref: store.Ref) -> Path:
    if not ref.file:
        raise FileNotFoundError("원본 파일이 없습니다. import 또는 fetch를 먼저 하세요.")
    p = paths.library() / ref.file
    if not p.is_file():
        raise FileNotFoundError(f"원본 파일을 찾을 수 없습니다: {p}")
    return p


def clip_ref(motion: str, ref: store.Ref) -> dict[str, Path]:
    tools.require("ffmpeg")
    tools.require("ffprobe")
    src = source_path(ref)
    d = paths.ref_dir(motion, ref.id)
    start, end = ref.start, ref.end
    if (d / "source.section").exists():  # fetch가 이미 구간만 받아 둔 경우
        start = end = None
    clip = cut(src, d / "clip.mp4", start, end, 30)
    return {
        "clip": clip,
        "slow": slowmo(clip, d / "slow.mp4", 4.0),
        "sheet": contact_sheet(clip, d / "sheet.png"),
    }
