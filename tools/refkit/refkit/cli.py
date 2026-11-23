import argparse
import subprocess
import sys

from pathlib import Path

from . import doctor, fetch, media, paths, policy, store, tools
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


def _cmd_doctor(a) -> int:
    return doctor.run()


def main(argv: list[str] | None = None) -> int:
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
