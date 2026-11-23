import subprocess
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from .. import tools
from . import runner

BONES = [(11, 12), (11, 13), (13, 15), (12, 14), (14, 16), (11, 23), (12, 24), (23, 24),
         (23, 25), (25, 27), (24, 26), (26, 28), (0, 11), (0, 12)]


def draw(video: Path, lm_img: np.ndarray, out: Path) -> Path:
    """이미지 좌표 랜드마크로 뼈대 선을 그려 영상으로 인코딩한다."""
    w, h, fps = runner.video_info(video)
    out.parent.mkdir(parents=True, exist_ok=True)
    cmd = [tools.require("ffmpeg"), "-nostdin", "-y", "-hide_banner", "-loglevel", "error",
           "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{w}x{h}", "-r", f"{fps:g}", "-i", "-",
           "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2", "-c:v", "libx264", "-pix_fmt", "yuv420p", str(out)]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stderr=subprocess.DEVNULL)
    try:
        for i, (_, frame) in enumerate(runner.frames(video)):
            im = Image.fromarray(frame)
            if i < len(lm_img):
                d = ImageDraw.Draw(im)
                pts = lm_img[i]
                for a, b in BONES:
                    if not (np.isnan(pts[a]).any() or np.isnan(pts[b]).any()):
                        d.line([tuple(pts[a]), tuple(pts[b])], fill=(0, 255, 0), width=3)
            p.stdin.write(im.tobytes())
    except BrokenPipeError:
        pass
    except BaseException:
        p.kill()  # 예외가 나면 ffmpeg가 입력을 기다리며 멈추지 않게 한다
        raise
    finally:
        try:
            p.stdin.close()
        except OSError:
            pass
        rc = p.wait()
    if rc != 0:
        raise RuntimeError("ffmpeg 인코딩에 실패했습니다.")
    return out
