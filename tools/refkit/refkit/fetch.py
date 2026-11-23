import shutil
import subprocess
from pathlib import Path

from . import paths, store, tools
from .policy import Policy
from .store import Ref


class FetchRefused(Exception):
    def __init__(self, reason: str):
        super().__init__(reason)
        self.reason = reason


def _rel(p: Path) -> str:
    return p.relative_to(paths.library()).as_posix()


def import_file(motion: str, src: Path, *, start=None, end=None, tags=(), take="",
                kind="own") -> Ref:
    src = Path(src)
    if not src.is_file():
        raise FileNotFoundError(f"파일이 없습니다: {src}")
    if start is not None and end is not None and end <= start:
        raise ValueError("끝 시각은 시작 시각보다 커야 합니다.")
    ref = store.upsert(motion, Ref(id="", kind=kind, start=start, end=end,
                                   tags=list(tags), take=take))
    dst = paths.ref_dir(motion, ref.id) / f"source{src.suffix.lower()}"
    shutil.copyfile(src, dst)
    return store.upsert(motion, Ref(id=ref.id, kind=ref.kind, file=_rel(dst)))


def _num(x: float) -> str:
    return f"{x:.3f}".rstrip("0").rstrip(".")


def fetch_ref(motion: str, ref: Ref, policy: Policy) -> Path:
    ok, reason = policy.can_fetch(ref)
    if not ok:
        raise FetchRefused(reason)
    if not ref.url:
        raise FetchRefused("url이 없어 내려받을 수 없습니다.")
    exe = tools.require("yt-dlp")
    d = paths.ref_dir(motion, ref.id)
    cmd = [exe, "-o", str(d / "source.%(ext)s")]
    sectioned = ref.start is not None or ref.end is not None
    if sectioned:
        s = 0.0 if ref.start is None else ref.start
        e = "inf" if ref.end is None else _num(ref.end)
        cmd += ["--download-sections", f"*{_num(s)}-{e}"]
    cmd.append(ref.url)
    subprocess.run(cmd, check=True)
    got = sorted(p for p in d.glob("source.*")
                 if p.suffix not in (".part", ".ytdl", ".section"))
    if not got:
        raise RuntimeError("yt-dlp가 끝났지만 내려받은 파일(source.*)이 없습니다.")
    got.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    marker = d / "source.section"
    if sectioned:
        marker.write_text("downloaded section only\n", encoding="utf-8")
    elif marker.exists():
        marker.unlink()
    store.upsert(motion, Ref(id=ref.id, kind=ref.kind, file=_rel(got[0])))
    return got[0]
