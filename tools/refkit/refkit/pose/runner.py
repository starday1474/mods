"""영상 -> 랜드마크. mediapipe는 선택 의존성이라 estimate 안에서만 import한다.

확인한 버전: mediapipe 1.2.3 (Tasks API)
  mediapipe.tasks.python.vision.PoseLandmarker / PoseLandmarkerOptions / RunningMode.VIDEO
  mediapipe.Image(image_format=mediapipe.ImageFormat.SRGB, data=...)
  landmarker.detect_for_video(image, timestamp_ms) -> result.pose_world_landmarks / pose_landmarks
"""
import contextlib
import os
import subprocess
import sys
import urllib.request
from pathlib import Path
from typing import Iterator

import numpy as np

from .. import media, paths, tools

MODEL_URL = ("https://storage.googleapis.com/mediapipe-models/pose_landmarker/"
             "pose_landmarker_full/float16/latest/pose_landmarker_full.task")


def require_mediapipe():
    try:
        import mediapipe  # noqa: F401
    except ImportError:
        raise tools.MissingTool("mediapipe", tools.MEDIAPIPE_HINT) from None


def model_path() -> Path:
    """모델을 <library>/_models/에 한 번만 받는다."""
    d = paths.library() / "_models"
    dst = d / MODEL_URL.rsplit("/", 1)[1]
    if dst.is_file() and dst.stat().st_size > 0:
        return dst
    d.mkdir(parents=True, exist_ok=True)
    tmp = dst.with_suffix(".part")
    try:
        with urllib.request.urlopen(MODEL_URL, timeout=60) as r, open(tmp, "wb") as f:
            while chunk := r.read(1 << 20):
                f.write(chunk)
        tmp.replace(dst)
    except Exception as e:
        tmp.unlink(missing_ok=True)
        raise RuntimeError(f"자세 모델을 내려받지 못했습니다: {e}") from e
    return dst


def video_info(video: Path) -> tuple[int, int, float]:
    out = media._probe(video, "-select_streams", "v:0", "-show_entries", "stream=width,height",
                       "-of", "csv=p=0:s=x")
    w, h = (int(x) for x in out.split("x")[:2])
    return w, h, media._probe_fps(video)


def frames(video: Path) -> Iterator[tuple[float, np.ndarray]]:
    w, h, fps = video_info(video)
    cmd = [tools.require("ffmpeg"), "-nostdin", "-hide_banner", "-loglevel", "error", "-i", str(video),
           "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
    size = w * h * 3
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    try:
        i = 0
        while True:
            buf = p.stdout.read(size)
            if len(buf) < size:
                break
            yield i / fps, np.frombuffer(buf, np.uint8).reshape(h, w, 3)
            i += 1
    finally:
        p.stdout.close()
        p.kill()
        p.wait()


@contextlib.contextmanager
def _quiet_stderr():
    """네이티브 라이브러리의 로그가 fd 2로 나오는 것을 잠시 막는다."""
    saved = None
    try:
        sys.stderr.flush()
        saved = os.dup(2)
        devnull = os.open(os.devnull, os.O_WRONLY)
        try:
            os.dup2(devnull, 2)
        finally:
            os.close(devnull)
    except (OSError, ValueError):
        pass
    try:
        yield
    finally:
        if saved is not None:
            os.dup2(saved, 2)
            os.close(saved)


def estimate(video: Path) -> tuple[np.ndarray, np.ndarray, float]:
    require_mediapipe()
    os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"
    os.environ["GLOG_minloglevel"] = "3"
    import mediapipe as mp
    from mediapipe.tasks.python import BaseOptions, vision

    tools.require("ffmpeg")
    tools.require("ffprobe")
    w, h, fps = video_info(video)
    # 한글 경로 문제를 피하려고 파일 경로 대신 버퍼로 넘긴다.
    buf = model_path().read_bytes()
    opts = vision.PoseLandmarkerOptions(
        base_options=BaseOptions(model_asset_buffer=buf),
        running_mode=vision.RunningMode.VIDEO, num_poses=1)
    world, image = [], []
    with _quiet_stderr(), vision.PoseLandmarker.create_from_options(opts) as lmk:
        for t, frame in frames(video):
            res = lmk.detect_for_video(
                mp.Image(image_format=mp.ImageFormat.SRGB, data=np.ascontiguousarray(frame)),
                int(round(t * 1000)))
            if res.pose_world_landmarks and res.pose_landmarks:
                wl, il = res.pose_world_landmarks[0], res.pose_landmarks[0]
                world.append([[p.x, p.y, p.z] for p in wl])
                image.append([[p.x * w, p.y * h] for p in il])
            else:
                world.append(np.full((33, 3), np.nan))
                image.append(np.full((33, 2), np.nan))
    return (np.array(world, dtype=float).reshape(-1, 33, 3),
            np.array(image, dtype=float).reshape(-1, 33, 2), fps)
