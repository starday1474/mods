import shutil

INSTALL_HINTS = {
    "ffmpeg": "winget install Gyan.FFmpeg",
    "ffprobe": "winget install Gyan.FFmpeg",
    "yt-dlp": "winget install yt-dlp.yt-dlp",
    "mpv": "winget install mpv (없으면 winget search mpv)",
}
MEDIAPIPE_HINT = "pip install -e tools/refkit[pose]"


class MissingTool(Exception):
    def __init__(self, name: str, install_hint: str):
        super().__init__(name)
        self.name = name
        self.install_hint = install_hint

    def message(self) -> str:
        return f"오류: {self.name}을(를) 찾을 수 없습니다. 설치: {self.install_hint}"


def require(name: str) -> str:
    path = shutil.which(name)
    if not path:
        raise MissingTool(name, INSTALL_HINTS.get(name, ""))
    return path
