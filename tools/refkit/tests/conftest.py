import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


@pytest.fixture
def tmp_env(monkeypatch, tmp_path):
    repo = tmp_path / "repo"
    lib = tmp_path / "라이브러리 폴더"
    repo.mkdir()
    lib.mkdir()
    monkeypatch.setenv("REFKIT_REPO", str(repo))
    monkeypatch.setenv("REFKIT_LIBRARY", str(lib))
    return repo, lib
