#!/usr/bin/env python3
"""Build a single self-contained HTML design board from a board spec.

Reference implementation for the compose-preview-design-board skill. Turns a
board spec (categories -> groups -> items, each item pointing at a rendered
@Preview PNG) into one HTML file with every image inlined as a base64 data
URI, so the result has no external dependencies and travels as one artifact.

Contract (see SKILL.md):
  - reads the spec from --spec <file> or stdin; writes to --out <file> or stdout
  - inlines every item `src` PNG as a data:image/png;base64,... URI
  - a missing/unreadable PNG renders as a visible placeholder, never an error
  - output is deterministic: no timestamps, random ids, or absolute paths
  - `src` paths resolve relative to --base (default: current directory)

Spec schema:
  top      title, tagline, footer, optional palette[], categories[]
  category badge, title, intro, groups[]
  group    title, note, layout ("row"|"grid"|"wide"), items[]
  item     src, caption, optional sub
  palette  list of "#rrggbb" strings, or {name, value|color|hex} objects
"""

from __future__ import annotations

import argparse
import base64
import html
import json
import mimetypes
import sys
from pathlib import Path

LAYOUTS = {"row", "grid", "wide"}

CSS = """
:root { color-scheme: light dark; }
* { box-sizing: border-box; }
body {
  margin: 0; padding: 2.5rem clamp(1rem, 4vw, 3rem);
  font: 15px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  color: #1a1a1a; background: #f6f7f9;
}
header.board { max-width: 1100px; margin: 0 auto 2.5rem; }
header.board h1 { font-size: 2rem; margin: 0 0 .25rem; }
header.board .tagline { font-size: 1.05rem; color: #555; margin: 0; }
.palette { display: flex; flex-wrap: wrap; gap: .75rem; margin: 1.25rem 0 0; padding: 0; list-style: none; }
.palette li { display: flex; align-items: center; gap: .5rem; font-size: .85rem; color: #555; }
.palette .swatch { width: 1.5rem; height: 1.5rem; border-radius: 6px; border: 1px solid rgba(0,0,0,.12); }
main { max-width: 1100px; margin: 0 auto; }
section.category { margin: 0 0 3rem; }
.badge {
  display: inline-block; font-size: .72rem; font-weight: 600; letter-spacing: .06em;
  text-transform: uppercase; color: #fff; background: #4f46e5;
  padding: .2rem .55rem; border-radius: 999px; margin: 0 0 .5rem;
}
section.category > h2 { font-size: 1.5rem; margin: 0 0 .35rem; }
section.category > .intro { color: #555; margin: 0 0 1.5rem; max-width: 70ch; }
.group { margin: 0 0 2rem; }
.group h3 { font-size: 1.1rem; margin: 0 0 .2rem; }
.group .note { color: #666; font-size: .92rem; margin: 0 0 1rem; max-width: 70ch; }
.items { display: flex; gap: 1.25rem; }
.items.row { flex-wrap: wrap; }
.items.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); }
.items.wide { flex-direction: column; }
figure { margin: 0; }
figure img, .placeholder {
  display: block; width: 100%; border-radius: 10px;
  border: 1px solid rgba(0,0,0,.08); background: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,.06);
}
.items.row figure { width: 220px; }
.placeholder {
  display: flex; align-items: center; justify-content: center; text-align: center;
  min-height: 140px; padding: 1rem; color: #b91c1c;
  border-style: dashed; border-color: #f0b4b4; background: #fef2f2;
  font-size: .8rem; word-break: break-all;
}
figcaption { margin: .5rem 0 0; font-size: .9rem; font-weight: 500; }
figcaption .sub { display: block; font-weight: 400; color: #777; font-size: .82rem; }
footer.board { max-width: 1100px; margin: 3rem auto 0; padding-top: 1.5rem;
  border-top: 1px solid rgba(0,0,0,.1); color: #777; font-size: .85rem; }
""".strip()


def esc(value) -> str:
    return html.escape("" if value is None else str(value))


def data_uri(src: str, base: Path) -> str | None:
    """Return a base64 data URI for `src`, or None if it can't be read."""
    if not src:
        return None
    try:
        data = (base / src).read_bytes()
    except OSError:
        return None
    mime = mimetypes.guess_type(src)[0] or "image/png"
    return f"data:{mime};base64,{base64.b64encode(data).decode('ascii')}"


def render_item(item: dict, base: Path) -> str:
    src = item.get("src", "")
    uri = data_uri(src, base)
    if uri is None:
        media = f'<div class="placeholder">missing image<br>{esc(src) or "(no src)"}</div>'
    else:
        media = f'<img src="{uri}" alt="{esc(item.get("caption", ""))}" loading="lazy">'
    sub = item.get("sub")
    sub_html = f'<span class="sub">{esc(sub)}</span>' if sub else ""
    caption = item.get("caption")
    cap_html = f"<figcaption>{esc(caption)}{sub_html}</figcaption>" if (caption or sub) else ""
    return f"<figure>{media}{cap_html}</figure>"


def render_group(group: dict, base: Path) -> str:
    layout = group.get("layout", "row")
    if layout not in LAYOUTS:
        layout = "row"
    parts = ['<div class="group">']
    if group.get("title"):
        parts.append(f"<h3>{esc(group['title'])}</h3>")
    if group.get("note"):
        parts.append(f'<p class="note">{esc(group["note"])}</p>')
    items = "".join(render_item(i, base) for i in group.get("items", []))
    parts.append(f'<div class="items {layout}">{items}</div>')
    parts.append("</div>")
    return "".join(parts)


def render_category(category: dict, base: Path) -> str:
    parts = ['<section class="category">']
    if category.get("badge"):
        parts.append(f'<span class="badge">{esc(category["badge"])}</span>')
    if category.get("title"):
        parts.append(f"<h2>{esc(category['title'])}</h2>")
    if category.get("intro"):
        parts.append(f'<p class="intro">{esc(category["intro"])}</p>')
    parts.extend(render_group(g, base) for g in category.get("groups", []))
    parts.append("</section>")
    return "".join(parts)


def render_palette(palette: list) -> str:
    if not palette:
        return ""
    swatches = []
    for entry in palette:
        if isinstance(entry, str):
            color, label = entry, entry
        elif isinstance(entry, dict):
            color = entry.get("value") or entry.get("color") or entry.get("hex") or ""
            label = entry.get("name") or color
        else:
            continue
        swatches.append(
            f'<li><span class="swatch" style="background:{esc(color)}"></span>{esc(label)}</li>'
        )
    return f'<ul class="palette">{"".join(swatches)}</ul>'


def build(spec: dict, base: Path) -> str:
    title = spec.get("title", "Design Board")
    head = [f"<header class=\"board\"><h1>{esc(title)}</h1>"]
    if spec.get("tagline"):
        head.append(f'<p class="tagline">{esc(spec["tagline"])}</p>')
    head.append(render_palette(spec.get("palette", [])))
    head.append("</header>")

    body = "".join(render_category(c, base) for c in spec.get("categories", []))
    footer = f'<footer class="board">{esc(spec["footer"])}</footer>' if spec.get("footer") else ""

    return (
        "<!doctype html>\n"
        '<html lang="en">\n<head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
        f"<title>{esc(title)}</title>\n<style>\n{CSS}\n</style>\n</head>\n<body>\n"
        f"{''.join(head)}\n<main>\n{body}\n</main>\n{footer}\n</body>\n</html>\n"
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build a self-contained HTML design board from a spec.")
    parser.add_argument("--spec", help="Spec JSON file (default: stdin).")
    parser.add_argument("--out", help="Output HTML file (default: stdout).")
    parser.add_argument(
        "--base",
        default=".",
        help="Directory item `src` paths resolve against (default: current directory).",
    )
    args = parser.parse_args(argv)

    raw = Path(args.spec).read_text(encoding="utf-8") if args.spec else sys.stdin.read()
    try:
        spec = json.loads(raw)
    except json.JSONDecodeError as exc:
        parser.error(f"invalid spec JSON: {exc}")

    html_out = build(spec, Path(args.base))

    if args.out:
        Path(args.out).write_text(html_out, encoding="utf-8")
    else:
        sys.stdout.write(html_out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
