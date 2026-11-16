import hashlib
import http.server
import threading
from functools import partial
from pathlib import Path

import pytest

from fetch_jars import FetchResult, HashMismatch, fetch


@pytest.fixture
def server(tmp_path):
    root = tmp_path / "www"
    root.mkdir()
    handler = partial(http.server.SimpleHTTPRequestHandler, directory=str(root))
    httpd = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    t = threading.Thread(target=httpd.serve_forever, daemon=True)
    t.start()
    yield root, f"http://127.0.0.1:{httpd.server_address[1]}"
    httpd.shutdown()


def write_mod(pack: Path, stem: str, url: str | None, sha512: str) -> None:
    (pack / "mods").mkdir(parents=True, exist_ok=True)
    url_line = f'url = "{url}"\n' if url else ""
    (pack / "mods" / f"{stem}.pw.toml").write_text(
        f'name = "{stem}"\nfilename = "{stem}.jar"\nside = "both"\n\n'
        f'[download]\n{url_line}hash-format = "sha512"\nhash = "{sha512}"\n',
        encoding="utf-8",
    )


def test_fetch_verifies_hash(tmp_path, server):
    root, base = server
    data = b"jar-bytes"
    (root / "a.jar").write_bytes(data)
    pack, cache = tmp_path / "pack", tmp_path / "cache"
    write_mod(pack, "a", f"{base}/a.jar", hashlib.sha512(data).hexdigest())
    result = fetch(pack, cache)
    assert isinstance(result, FetchResult)
    assert result.downloaded == [cache / "a.jar"]
    assert (cache / "a.jar").read_bytes() == data


def test_fetch_wrong_hash_removes_file(tmp_path, server):
    root, base = server
    (root / "a.jar").write_bytes(b"jar-bytes")
    pack, cache = tmp_path / "pack", tmp_path / "cache"
    write_mod(pack, "a", f"{base}/a.jar", "00" * 64)
    with pytest.raises(HashMismatch):
        fetch(pack, cache)
    assert not (cache / "a.jar").exists()


def test_fetch_skips_without_url(tmp_path):
    pack, cache = tmp_path / "pack", tmp_path / "cache"
    write_mod(pack, "cfmod", None, "00")
    result = fetch(pack, cache)
    assert result.skipped == ["cfmod"]
    assert result.downloaded == []


def test_fetch_reuses_cached_file(tmp_path, server):
    root, base = server
    data = b"jar-bytes"
    pack, cache = tmp_path / "pack", tmp_path / "cache"
    cache.mkdir()
    (cache / "a.jar").write_bytes(data)  # 서버에는 파일이 없음 → 캐시 재사용해야 함
    write_mod(pack, "a", f"{base}/a.jar", hashlib.sha512(data).hexdigest())
    assert fetch(pack, cache).downloaded == [cache / "a.jar"]
