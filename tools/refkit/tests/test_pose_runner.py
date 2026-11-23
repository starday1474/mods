import numpy as np
import pytest

from refkit import cli, paths, store
from refkit.pose import overlay, runner


def test_runner_frames(sample_video):
    fr = list(runner.frames(sample_video))
    assert len(fr) == 90
    assert all(f.shape == (240, 320, 3) for _, f in fr)
    assert fr[1][0] == pytest.approx(1 / 30)


@pytest.fixture
def mp_ready(tmp_env):
    pytest.importorskip("mediapipe")
    try:
        runner.model_path()
    except Exception as e:  # 오프라인 등
        pytest.skip(f"모델을 받을 수 없습니다: {e}")
    try:  # libEGL 등 시스템 라이브러리가 없으면 landmarker 생성에서 OSError
        from mediapipe.tasks.python import BaseOptions, vision
        opts = vision.PoseLandmarkerOptions(
            base_options=BaseOptions(model_asset_buffer=runner.model_path().read_bytes()),
            running_mode=vision.RunningMode.VIDEO)
        vision.PoseLandmarker.create_from_options(opts).close()
    except OSError as e:
        pytest.skip(f"mediapipe를 쓸 수 없습니다: {e}")


def test_runner_estimate_no_person(sample_video, mp_ready):
    world, img, fps = runner.estimate(sample_video)
    assert world.shape == (90, 33, 3)
    assert img.shape == (90, 33, 2)
    assert fps == pytest.approx(30.0)
    assert np.isnan(world).all()


def test_overlay_draw(sample_video, tmp_path):
    img = np.full((90, 33, 2), np.nan)
    img[:, 11] = [100, 100]
    img[:, 12] = [200, 100]
    out = overlay.draw(sample_video, img, tmp_path / "한글 out" / "pose_overlay.mp4")
    assert out.is_file() and out.stat().st_size > 0


def test_pose_cli_end_to_end(tmp_env, sample_video, mp_ready, capsys):
    from refkit import fetch
    ref = fetch.import_file("m", sample_video)
    rc = cli.main(["pose", "--motion", "m", "--id", ref.id])
    assert rc == 0
    d = paths.ref_dir("m", ref.id)
    assert (d / "pose_landmarks.npz").is_file()
    assert (d / "pose_angles.csv").is_file()
    assert not (d / "pose_overlay.mp4").exists()  # 사람이 없으면 오버레이를 만들지 않는다
    r = store.get("m", ref.id)
    assert r.pose["detected"] == 0.0 and r.pose["angles"] == {}
    assert "warning" in r.pose
