import shutil
import subprocess
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parents[3]


def test_repo_guard():
    if not shutil.which("git"):
        pytest.skip("git 없음")
    r = subprocess.run(["git", "ls-files", "docs/refs"], cwd=REPO, capture_output=True, text=True)
    if r.returncode != 0:
        pytest.skip("git 저장소 아님")
    bad = [f for f in r.stdout.splitlines() if Path(f).suffix.lower() not in {".yaml", ".yml", ".md"}]
    assert not bad, f"docs/refs에는 메타데이터만 둘 수 있습니다: {bad}"
