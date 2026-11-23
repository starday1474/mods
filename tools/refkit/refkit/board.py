import html
from pathlib import Path
from urllib.parse import quote, urlparse

from . import anim, compare, marks, paths, policy, store

CSS = """
body{font-family:sans-serif;margin:16px;background:#fafafa;color:#222}
.card{background:#fff;border:1px solid #ccc;border-radius:6px;padding:12px;margin:12px 0}
.card h2{margin:0 0 6px;font-size:1.1em}
.tag{background:#eef;border-radius:3px;padding:1px 6px;margin-right:4px;font-size:.85em}
img,video{max-width:100%}pre{background:#f0f0f0;padding:8px;overflow:auto}
table{border-collapse:collapse}td,th{border:1px solid #ccc;padding:2px 8px}
.warn{color:#b00}
"""


def _e(x) -> str:
    return html.escape(str(x), quote=True)


def _rel(p: Path, base: Path) -> str:
    rel = Path(p).resolve().relative_to(base.resolve()) if Path(p).resolve().is_relative_to(base.resolve()) \
        else Path("..") / Path(p).resolve().relative_to(base.resolve().parent)
    return quote(rel.as_posix(), safe="/.")


def _marks_html(r: store.Ref) -> str:
    if not all(k in r.marks for k in marks.LABELS) or marks.validate(r.marks):
        return ""
    q = marks.ratios_ref(r.marks)
    return (f"<p>예비 {q['windup']:.0%} / 회복 {q['recovery']:.0%} / 길이 {q['total']:.2f}초 "
            f"(start {r.marks['start']:g}, impact {r.marks['impact']:g}, settle {r.marks['settle']:g})</p>")


def _pose_html(r: store.Ref) -> str:
    p = r.pose
    if not p:
        return ""
    out = ["<div class=\"pose\">"]
    if "frames" in p:
        out.append(f"<p>자세 통계: 프레임 {_e(p.get('frames'))}, 검출 {_e(p.get('detected', '?'))}</p>")
    if p.get("warning"):
        out.append(f"<p class=\"warn\">경고: {_e(p['warning'])}</p>")
    angles = p.get("angles") or {}
    if angles:
        out.append("<table><tr><th>각도</th><th>최소</th><th>최대</th><th>범위</th></tr>")
        for n, a in angles.items():
            out.append(f"<tr><td>{_e(n)}</td><td>{_e(a.get('min'))}</td><td>{_e(a.get('max'))}</td>"
                       f"<td>{_e(a.get('range'))}</td></tr>")
        out.append("</table>")
    if p.get("suggested_marks"):
        out.append("<p>추천 표시: " + ", ".join(f"{_e(k)}={_e(v)}" for k, v in p["suggested_marks"].items()) + "</p>")
    out.append("</div>")
    return "".join(out)


def _card(motion: str, r: store.Ref, media: bool, base: Path) -> str:
    out = ['<article class="card">', f"<h2>{_e(r.id)} <small>({_e(r.kind)})</small></h2>"]
    if r.tags:
        out.append("<p>" + "".join(f"<span class=\"tag\">{_e(t)}</span>" for t in r.tags) + "</p>")
    if r.take:
        out.append(f"<p class=\"take\">{_e(r.take)}</p>")
    if r.url and urlparse(r.url).scheme.lower() in ("http", "https"):
        label = store.format_time(r.start) if r.start is not None else "링크"
        url = policy.timestamped_url(r.url, r.start)
        out.append(f"<p><a href=\"{_e(url)}\">{_e(label)} 에서 보기</a></p>")
    out.append(_marks_html(r))
    out.append(_pose_html(r))
    if media:
        d = paths.library() / motion / r.id
        if (d / "sheet.png").is_file():
            out.append(f"<img src=\"{_rel(d / 'sheet.png', base)}\" alt=\"컨택트 시트\">")
        if (d / "compare.mp4").is_file():
            out.append(f"<video controls loop src=\"{_rel(d / 'compare.mp4', base)}\"></video>")
    out.append("</article>")
    return "".join(out)


def _page(title: str, top: str, cards: list[str]) -> str:
    return (f"<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\">"
            f"<title>{_e(title)}</title><style>{CSS}</style></head><body>"
            f"<h1>{_e(title)}</h1>{top}{''.join(cards)}</body></html>")


def _timing_text(motion: str, anim_name: str | None) -> str | None:
    try:
        return marks.timing_report(motion, anim_name)
    except (ValueError, FileNotFoundError, KeyError):
        return None


def build(motion: str, anim_name: str | None = None) -> Path:
    base = paths.library() / paths.check_name(motion, "동작 이름")
    base.mkdir(parents=True, exist_ok=True)
    name = anim_name or motion
    top = []
    try:
        gif = compare.render_ours(name, paths.library() / "_ours" / f"{name}.gif")
        top.append(f"<p><img src=\"{_rel(gif, base)}\" alt=\"우리 동작\"></p>")
    except (ValueError, FileNotFoundError, RuntimeError, OSError):
        gif = paths.library() / "_ours" / f"{name}.gif"
        if gif.is_file():
            top.append(f"<p><img src=\"{_rel(gif, base)}\" alt=\"우리 동작\"></p>")
    rep = _timing_text(motion, anim_name)
    if rep:
        top.append(f"<pre>{_e(rep)}</pre>")
    cards = [_card(motion, r, True, base) for r in store.load(motion)]
    out = base / "board.html"
    out.write_text(_page(f"{motion} 레퍼런스 보드", "".join(top), cards), encoding="utf-8")
    return out


def build_share(motion: str, out: Path, anim_name: str | None = None) -> Path:
    base = paths.library() / paths.check_name(motion, "동작 이름")
    rep = _timing_text(motion, anim_name)
    top = f"<pre>{_e(rep)}</pre>" if rep else ""
    cards = [_card(motion, r, False, base) for r in store.load(motion)]
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(_page(f"{motion} 레퍼런스 (공유용)", top, cards), encoding="utf-8")
    return out
