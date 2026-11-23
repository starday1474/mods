import argparse
import csv
import subprocess
import sys
import tempfile
import webbrowser

from pathlib import Path

import numpy as np

from . import board, compare, doctor, fetch, marks, media, paths, policy, store, tools
from .pose import analysis, overlay, runner
from .store import Ref


def _cmd_add(a) -> int:
    try:
        start = store.parse_time(a.start) if a.start is not None else None
        end = store.parse_time(a.end) if a.end is not None else None
    except ValueError as e:
        print(f"오류: {e}", file=sys.stderr)
        return 2
    if start is not None and end is not None and end <= start:
        print("오류: 끝 시각은 시작 시각보다 커야 합니다.", file=sys.stderr)
        return 2
    if a.kind != "own" and not a.url:
        print("오류: own이 아니면 url이 필요합니다.", file=sys.stderr)
        return 2
    tags = [t.strip() for t in (a.tags or "").split(",") if t.strip()]
    ref = Ref(id=a.id or "", kind=a.kind, url=a.url, start=start, end=end,
              tags=tags, take=a.take or "", license=a.license or "")
    try:
        saved = store.upsert(a.motion, ref)
    except ValueError as e:
        print(f"오류: {e}", file=sys.stderr)
        return 2
    print(f"저장했습니다: {a.motion}/{saved.id}")
    return 0


def _cmd_list(a) -> int:
    if a.motion:
        try:
            refs = store.load(a.motion)
        except ValueError as e:
            print(f"오류: {e}", file=sys.stderr)
            return 2
        if not refs:
            print(f"{a.motion}: 레퍼런스가 없습니다.")
            return 0
        for r in refs:
            span = ""
            if r.start is not None:
                span = store.format_time(r.start)
                if r.end is not None:
                    span += "-" + store.format_time(r.end)
            print("\t".join([r.id, r.kind, span, ",".join(r.tags), r.take, r.url]))
        return 0
    d = paths.refs_dir()
    names = sorted(p.stem for p in d.glob("*.yaml")) if d.is_dir() else []
    if not names:
        print("등록된 동작이 없습니다.")
        return 0
    for n in names:
        print(f"{n}\t{len(store.load(n))}개")
    return 0


def _time_range(a):
    start = store.parse_time(a.start) if a.start is not None else None
    end = store.parse_time(a.end) if a.end is not None else None
    if start is not None and end is not None and end <= start:
        raise ValueError("끝 시각은 시작 시각보다 커야 합니다.")
    return start, end


def _cmd_import(a) -> int:
    try:
        start, end = _time_range(a)
        tags = [t.strip() for t in (a.tags or "").split(",") if t.strip()]
        ref = fetch.import_file(a.motion, Path(a.file), start=start, end=end,
                                tags=tags, take=a.take or "")
    except (ValueError, FileNotFoundError) as e:
        print(f"오류: {e}", file=sys.stderr)
        return 2
    print(f"가져왔습니다: {a.motion}/{ref.id}")
    return 0


def _cmd_fetch(a) -> int:
    pol = policy.load_policy()
    try:
        refs = store.load(a.motion)
    except ValueError as e:
        print(f"오류: {e}", file=sys.stderr)
        return 2
    if a.id:
        refs = [r for r in refs if r.id == a.id]
        if not refs:
            print(f"오류: 없는 레퍼런스입니다: {a.id}", file=sys.stderr)
            return 2
    rc = 0
    for r in refs:
        try:
            p = fetch.fetch_ref(a.motion, r, pol)
            print(f"내려받았습니다: {r.id} -> {p}")
        except fetch.FetchRefused as e:
            print(f"{r.id}: {e.reason} 링크 모드로 유지합니다.")
        except subprocess.CalledProcessError:
            print(f"오류: {r.id} 다운로드에 실패했습니다.", file=sys.stderr)
            rc = 2
    return rc


def _cmd_clip(a) -> int:
    tools.require("ffmpeg")
    tools.require("ffprobe")
    try:
        ref = store.get(a.motion, a.id)
        out = media.clip_ref(a.motion, ref)
    except KeyError:
        print(f"오류: 없는 레퍼런스입니다: {a.id}", file=sys.stderr)
        return 2
    except (ValueError, FileNotFoundError, RuntimeError) as e:
        print(f"오류: {e}", file=sys.stderr)
        return 2
    for k, p in out.items():
        print(f"{k}: {p}")
    return 0


def _cmd_mark(a) -> int:
    try:
        ref = store.get(a.motion, a.id)
    except KeyError:
        print(f"오류: 없는 레퍼런스입니다: {a.id}", file=sys.stderr)
        return 2
    except ValueError as e:
        print(f"오류: {e}", file=sys.stderr)
        return 2
    if a.set:
        m = {}
        try:
            for item in a.set:
                k, sep, v = item.partition("=")
                if not sep or k not in marks.LABELS:
                    raise ValueError(f"잘못된 표시: {item!r} (start=, impact=, settle= 형식)")
                m[k] = store.parse_time(v)
        except ValueError as e:
            print(f"오류: {e}", file=sys.stderr)
            return 2
        merged = {**ref.marks, **m}
        warns = marks.validate(merged) if len(merged) == 3 else []
        if len(merged) == 3 and warns:
            print("오류: " + " ".join(warns), file=sys.stderr)
            return 2
    else:
        tools.require("mpv")
        try:
            clip = paths.ref_dir(a.motion, ref.id) / "clip.mp4"
            if not clip.is_file():
                tools.require("ffmpeg")
                tools.require("ffprobe")
                clip = media.clip_ref(a.motion, ref)["clip"]
        except (ValueError, FileNotFoundError, RuntimeError) as e:
            print(f"오류: {e}", file=sys.stderr)
            return 2
        with tempfile.TemporaryDirectory() as td:
            out = Path(td) / "marks.txt"
            marks.run_mpv(clip, out)
            m = marks.parse_marks_file(out)
        if not m:
            print("표시가 없습니다. 저장하지 않았습니다.")
            return 2
        for w in marks.validate(m):
            print(f"경고: {w}")
        merged = m
    ref.marks = merged
    store.upsert(a.motion, ref)
    print("표시를 저장했습니다: " + ", ".join(f"{k}={merged[k]:g}" for k in marks.LABELS if k in merged))
    return 0


def _cmd_timing(a) -> int:
    try:
        print(marks.timing_report(a.motion, a.anim))
    except (ValueError, FileNotFoundError, KeyError) as e:
        print(f"오류: {e}", file=sys.stderr)
        return 2
    return 0


def _cmd_compare(a) -> int:
    try:
        outs = compare.compare_motion(a.motion, a.anim)
    except (ValueError, FileNotFoundError, RuntimeError, KeyError) as e:
        print(f"오류: {e}", file=sys.stderr)
        return 2
    print(f"비교 영상 {len(outs)}개를 만들었습니다.")
    return 0


def _cmd_board(a) -> int:
    try:
        if a.share:
            p = board.build_share(a.motion, Path(a.share), a.anim)
        else:
            p = board.build(a.motion, a.anim)
    except (ValueError, OSError) as e:
        print(f"오류: {e}", file=sys.stderr)
        return 2
    print(f"만들었습니다: {p}")
    if a.open and not a.share:
        webbrowser.open(p.as_uri())
    return 0


def _cmd_pose(a) -> int:
    try:
        ref = store.get(a.motion, a.id)
    except KeyError:
        print(f"오류: 없는 레퍼런스입니다: {a.id}", file=sys.stderr)
        return 2
    except ValueError as e:
        print(f"오류: {e}", file=sys.stderr)
        return 2
    if "game" in ref.tags:
        print("오류: 자세 추정은 실제 사람 영상에만 씁니다 (game 태그가 있는 레퍼런스는 거절).",
              file=sys.stderr)
        return 2
    runner.require_mediapipe()
    tools.require("ffmpeg")
    tools.require("ffprobe")
    try:
        d = paths.ref_dir(a.motion, ref.id)
        clip = d / "clip.mp4"
        if not clip.is_file():
            clip = media.clip_ref(a.motion, ref)["clip"]
        world, img, fps = runner.estimate(clip)
        summary = analysis.summarize(world, fps)
        np.savez_compressed(d / "pose_landmarks.npz", world=world, image=img, fps=fps)
        with open(d / "pose_angles.csv", "w", newline="", encoding="utf-8") as f:
            ang = analysis.joint_angles(world)
            w = csv.writer(f)
            w.writerow(["time", *ang])
            for i in range(len(world)):
                w.writerow([f"{i / fps:.3f}", *("" if np.isnan(v[i]) else f"{v[i]:.1f}" for v in ang.values())])
        if not a.no_overlay and summary["detected"] > 0:
            overlay.draw(clip, img, d / "pose_overlay.mp4")
    except (ValueError, FileNotFoundError, RuntimeError) as e:
        print(f"오류: {e}", file=sys.stderr)
        return 2
    refs = store.load(a.motion)
    cur = next(r for r in refs if r.id == ref.id)
    cur.pose = summary
    sug = summary.get("suggested_marks")
    if a.use_suggested_marks and sug and not cur.marks:
        cur.marks = dict(sug)
        print("추천 표시를 저장했습니다: " + ", ".join(f"{k}={v:g}" for k, v in sug.items()))
    store.save(a.motion, refs)
    print(f"자세 통계를 저장했습니다: 프레임 {summary['frames']}, 검출 {summary['detected']:.0%}")
    if summary.get("warning"):
        print(f"경고: {summary['warning']}")
    return 0


def _cmd_doctor(a) -> int:
    return doctor.run()


def main(argv: list[str] | None = None) -> int:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(errors="replace")
        except (AttributeError, ValueError, OSError):
            pass
    ap = argparse.ArgumentParser(prog="refkit", description="동작 레퍼런스 수집/비교 도구")
    sub = ap.add_subparsers(dest="cmd")

    p = sub.add_parser("add", help="레퍼런스 추가(같은 url+start면 갱신)")
    p.add_argument("url", nargs="?", default="")
    p.add_argument("--motion", required=True)
    p.add_argument("--kind", default="video", choices=store.KINDS)
    p.add_argument("--start")
    p.add_argument("--end")
    p.add_argument("--tags")
    p.add_argument("--take")
    p.add_argument("--license")
    p.add_argument("--id")
    p.set_defaults(fn=_cmd_add)

    p = sub.add_parser("list", help="동작별 개수 또는 항목 표")
    p.add_argument("--motion")
    p.set_defaults(fn=_cmd_list)

    p = sub.add_parser("import", help="로컬 영상 가져오기(항상 허용)")
    p.add_argument("file")
    p.add_argument("--motion", required=True)
    p.add_argument("--start")
    p.add_argument("--end")
    p.add_argument("--tags")
    p.add_argument("--take")
    p.set_defaults(fn=_cmd_import)

    p = sub.add_parser("fetch", help="정책이 허용한 레퍼런스 내려받기")
    p.add_argument("--motion", required=True)
    p.add_argument("--id")
    p.set_defaults(fn=_cmd_fetch)

    p = sub.add_parser("clip", help="구간 자르기, 슬로모션, 컨택트 시트")
    p.add_argument("--motion", required=True)
    p.add_argument("--id", required=True)
    p.set_defaults(fn=_cmd_clip)

    p = sub.add_parser("mark", help="mpv로 타이밍 표시(또는 --set으로 직접 입력)")
    p.add_argument("--motion", required=True)
    p.add_argument("--id", required=True)
    p.add_argument("--set", nargs="+", metavar="LABEL=SEC")
    p.set_defaults(fn=_cmd_mark)

    p = sub.add_parser("timing", help="레퍼런스와 우리 동작 타이밍 비교")
    p.add_argument("--motion", required=True)
    p.add_argument("--anim")
    p.set_defaults(fn=_cmd_timing)

    p = sub.add_parser("compare", help="레퍼런스와 우리 동작 나란히 비교 영상")
    p.add_argument("--motion", required=True)
    p.add_argument("--anim")
    p.set_defaults(fn=_cmd_compare)

    p = sub.add_parser("board", help="정리 보드 HTML 만들기")
    p.add_argument("--motion", required=True)
    p.add_argument("--anim")
    p.add_argument("--open", action="store_true")
    p.add_argument("--share", metavar="OUT.html")
    p.set_defaults(fn=_cmd_board)

    p = sub.add_parser("pose", help="자세 추정(각도/타이밍 통계)")
    p.add_argument("--motion", required=True)
    p.add_argument("--id", required=True)
    p.add_argument("--no-overlay", action="store_true")
    p.add_argument("--use-suggested-marks", action="store_true")
    p.set_defaults(fn=_cmd_pose)

    p = sub.add_parser("doctor", help="필요한 도구 점검")
    p.set_defaults(fn=_cmd_doctor)

    try:
        a = ap.parse_args(argv)
    except SystemExit as e:
        return int(e.code or 0)
    if not getattr(a, "fn", None):
        ap.print_help()
        return 2
    try:
        return a.fn(a)
    except tools.MissingTool as e:
        print(e.message(), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
