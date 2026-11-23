import re

from refkit import board, store
from refkit.store import Ref


def _setup(lib):
    store.upsert("활", Ref(id="video-01", kind="video", url="https://www.youtube.com/watch?v=abc",
                          start=62, tags=["활", "측면"], take="당김이 길고 놓을 때 짧다",
                          marks={"start": 0.0, "impact": 0.8, "settle": 1.6},
                          pose={"frames": 40, "detected": 38,
                                "angles": {"elbow": {"min": 40.0, "max": 170.0, "range": 130.0}},
                                "suggested_marks": {"impact": 0.8}, "warning": "일부 프레임 누락"}))
    store.upsert("활", Ref(id="video-02", kind="video", file="활/video-02/source.mp4", take="가져온 영상"))
    d = lib / "활" / "video-02"
    d.mkdir(parents=True)
    (d / "sheet.png").write_bytes(b"png")
    (d / "compare.mp4").write_bytes(b"mp4")


def test_board_contains_cards(tmp_env):
    repo, lib = tmp_env
    _setup(lib)
    p = board.build("활")
    assert p == lib / "활" / "board.html"
    html = p.read_text(encoding="utf-8")
    assert len(re.findall(r'class="card"', html)) == 2
    assert "당김이 길고" in html
    assert "t=62" in html
    assert "일부 프레임 누락" in html and "elbow" in html
    assert 'src="video-02/sheet.png"' in html
    assert not re.search(r"<script[^>]+src=[\"']http", html)
    assert not re.search(r"<link[^>]+href=[\"']http", html)


def test_share_has_no_media(tmp_env):
    repo, lib = tmp_env
    _setup(lib)
    out = lib / "share.html"
    board.build_share("활", out)
    html = out.read_text(encoding="utf-8")
    assert "당김이 길고" in html and "t=62" in html
    assert not re.search(r"\.(mp4|png|gif)", html)
