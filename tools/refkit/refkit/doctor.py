import importlib.util
import os

from . import paths, tools


def _writable(p) -> bool:
    while not p.exists() and p != p.parent:
        p = p.parent
    return os.access(p, os.W_OK)


def run() -> int:
    missing_required = False
    for name in ("ffmpeg", "ffprobe", "yt-dlp", "mpv"):
        path = tools.shutil.which(name)
        if path:
            print(f"✅ {name}: {path}")
        else:
            print(f"❌ {name}: 설치 명령: {tools.INSTALL_HINTS[name]}")
            if name == "ffmpeg":
                missing_required = True
    try:
        has_mp = importlib.util.find_spec("mediapipe") is not None
    except (ImportError, ValueError):
        has_mp = False
    if has_mp:
        print("✅ mediapipe")
    else:
        print(f"❌ mediapipe: 설치 명령: {tools.MEDIAPIPE_HINT}")
    lib = paths.library()
    if _writable(lib):
        print(f"✅ 라이브러리 경로: {lib}")
    else:
        print(f"❌ 라이브러리 경로에 쓸 수 없습니다: {lib} (REFKIT_LIBRARY로 바꿀 수 있습니다)")
    return 1 if missing_required else 0
