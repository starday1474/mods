import shutil

from refkit import cli, doctor, fetch


def test_doctor_runs(tmp_env, capsys):
    rc = doctor.run()
    out = capsys.readouterr().out
    assert isinstance(rc, int)
    assert "ffmpeg" in out and "mediapipe" in out


def test_doctor_missing_ffmpeg(tmp_env, monkeypatch, capsys):
    monkeypatch.setattr(shutil, "which", lambda n: None)
    assert doctor.run() == 1
    assert "winget install Gyan.FFmpeg" in capsys.readouterr().out


def test_missing_tool_message(tmp_env, sample_video, monkeypatch, capsys):
    ref = fetch.import_file("m", sample_video)
    monkeypatch.setattr(shutil, "which", lambda n: None)
    rc = cli.main(["clip", "--motion", "m", "--id", ref.id])
    err = capsys.readouterr().err
    assert rc == 2
    assert "winget install Gyan.FFmpeg" in err
    assert "Traceback" not in err


def test_cli_import_and_clip(tmp_env, sample_video, capsys):
    assert cli.main(["import", str(sample_video), "--motion", "m", "--start", "0:00.5",
                     "--end", "2", "--tags", "a,b", "--take", "내 메모"]) == 0
    assert cli.main(["clip", "--motion", "m", "--id", "own-01"]) == 0
    assert cli.main(["import", str(sample_video), "--motion", "m", "--start", "3", "--end", "1"]) == 2


def test_cli_fetch_refused_keeps_link(tmp_env, capsys):
    assert cli.main(["add", "https://www.youtube.com/watch?v=x", "--motion", "m"]) == 0
    assert cli.main(["fetch", "--motion", "m"]) == 0
    assert "링크 모드로 유지" in capsys.readouterr().out
