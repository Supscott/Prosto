#!/usr/bin/env python3
"""Reads a `uiautomator dump` XML on stdin. Used by `tools/device.sh ui`, `tapui` and `tap <selector>`.

    ui_nodes.py list            one line per labelled node: text | content-desc | bounds
    ui_nodes.py find <label>    centre "x y" of the first node whose text or content-desc
                                equals <label>; exit 1 when there is none
    ui_nodes.py webview         "l t r b" of the largest android.webkit.WebView; exit 1 when none
    ui_nodes.py tap-point <webview "l t r b"> <cdp-result>
                                device "x y" for the CSS centre that the selector expression
                                returned (see device.sh tap); exit 1 with the reason otherwise
"""
import json
import re
import sys
import xml.etree.ElementTree as ET


def nodes(root):
    for node in root.iter("node"):
        text, desc = node.get("text", ""), node.get("content-desc", "")
        if text or desc:
            yield text, desc, node.get("bounds", "")


def parse_bounds(bounds):
    """(l, t, r, b) from "[l,t][r,b]", or None when the string is not a rectangle."""
    numbers = list(map(int, re.findall(r"-?\d+", bounds)))
    return tuple(numbers) if len(numbers) == 4 else None


def webview_bounds(root):
    """Bounds of the WebView with the largest area, or None when the screen has none."""
    best = None
    for node in root.iter("node"):
        if node.get("class") != "android.webkit.WebView":
            continue
        box = parse_bounds(node.get("bounds", ""))
        if box is None:
            continue
        area = (box[2] - box[0]) * (box[3] - box[1])
        if best is None or area > best[0]:
            best = (area, box)
    return best[1] if best else None


def tap_point(webview, cdp_result):
    """Device pixels for the centre of a selected element.

    `cdp_result` is what cdp_eval.mjs prints: a JSON string holding "[cssX, cssY, innerWidth,
    innerHeight, devicePixelRatio]", or "null" when no element matches. Raises ValueError with
    the reason when the point cannot be tapped.
    """
    value = json.loads(cdp_result)
    if isinstance(value, str):  # the expression returns JSON.stringify(...)
        value = json.loads(value)
    if value is None:
        raise ValueError("no element matches the selector")
    css_x, css_y, view_w, view_h, dpr = value
    if not (0 <= css_x <= view_w and 0 <= css_y <= view_h):
        raise ValueError(
            f"the centre of the element ({css_x:.0f}, {css_y:.0f}) is outside the viewport "
            f"({view_w}x{view_h} CSS px); scroll it into view first"
        )
    left, top, right, bottom = webview
    x = left + round(css_x * dpr)
    y = top + round(css_y * dpr)
    if not (left <= x < right and top <= y < bottom):
        raise ValueError("the element's centre is outside the WebView (covered or off screen)")
    return x, y


def main() -> int:
    argv = sys.argv[1:]
    if argv[:1] == ["tap-point"] and len(argv) == 3:
        numbers = list(map(int, argv[1].split()))
        if len(numbers) != 4:
            print("tap-point: the WebView must be 'l t r b'", file=sys.stderr)
            return 2
        try:
            x, y = tap_point(tuple(numbers), argv[2])
        except ValueError as error:
            print(error, file=sys.stderr)
            return 1
        print(x, y)
        return 0

    root = ET.fromstring(sys.stdin.read())
    if argv == ["list"]:
        for text, desc, bounds in nodes(root):
            print(f"{text} | {desc} | {bounds}")
        return 0
    if argv == ["webview"]:
        box = webview_bounds(root)
        if box is None:
            print("no WebView on screen", file=sys.stderr)
            return 1
        print(*box)
        return 0
    if len(argv) == 2 and argv[0] == "find":
        for text, desc, bounds in nodes(root):
            if argv[1] in (text, desc):
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
                print((x1 + x2) // 2, (y1 + y2) // 2)
                return 0
        return 1
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
