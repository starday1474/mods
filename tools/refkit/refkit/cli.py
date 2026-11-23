import argparse
import sys

from . import paths, store
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

    try:
        a = ap.parse_args(argv)
    except SystemExit as e:
        return int(e.code or 0)
    if not getattr(a, "fn", None):
        ap.print_help()
        return 2
    return a.fn(a)


if __name__ == "__main__":
    sys.exit(main())
