import pytest

from refkit import cli, paths, store
from refkit.store import Ref

URL = "https://www.youtube.com/watch?v=abc"


def test_parse_time():
    assert store.parse_time("62") == 62.0
    assert store.parse_time("1:02") == 62.0
    assert store.parse_time("1:02.5") == 62.5
    assert store.parse_time("0:01:02") == 62.0
    assert store.parse_time(3) == 3.0
    for bad in ("abc", "-1"):
        with pytest.raises(ValueError):
            store.parse_time(bad)


def test_upsert_dedup_and_unicode(tmp_env):
    store.upsert("m", Ref(id="", kind="video", url=URL, start=10.0))
    store.upsert("m", Ref(id="", kind="video", url=URL, start=10.0, take="가이 호흡"))
    refs = store.load("m")
    assert len(refs) == 1
    assert refs[0].take == "가이 호흡"
    text = (paths.refs_dir() / "m.yaml").read_text(encoding="utf-8")
    assert "가이 호흡" in text
    assert "0:10" in text
    assert "\\u" not in text


def test_upsert_merge_ignores_empty(tmp_env):
    store.upsert("m", Ref(id="", kind="video", url=URL, start=1.0, tags=["a"], take="x"))
    store.upsert("m", Ref(id="", kind="video", url=URL, start=1.0, take=""))
    r = store.load("m")[0]
    assert r.take == "x" and r.tags == ["a"]


def test_new_ids(tmp_env):
    a = store.upsert("m", Ref(id="", kind="video", url="https://a/1"))
    b = store.upsert("m", Ref(id="", kind="video", url="https://a/2"))
    c = store.upsert("m", Ref(id="", kind="own"))
    assert (a.id, b.id, c.id) == ("video-01", "video-02", "own-01")
    assert store.get("m", "video-02").url == "https://a/2"
    with pytest.raises(KeyError):
        store.get("m", "nope")


def test_add_rejects_end_before_start(tmp_env):
    rc = cli.main(["add", URL, "--motion", "m", "--start", "10", "--end", "5"])
    assert rc == 2
    assert not (paths.refs_dir() / "m.yaml").exists()


def test_add_and_list(tmp_env, capsys):
    assert cli.main(["add", URL, "--motion", "m", "--start", "1:02", "--tags", "a,b", "--take", "활"]) == 0
    assert cli.main(["list"]) == 0
    assert "m" in capsys.readouterr().out
    assert cli.main(["list", "--motion", "m"]) == 0
    assert "video-01" in capsys.readouterr().out
    assert store.load("m")[0].start == 62.0
    assert store.load("m")[0].tags == ["a", "b"]


def test_ref_dir_created(tmp_env):
    d = paths.ref_dir("m", "video-01")
    assert d.is_dir() and d.parent.parent == paths.library()
