#!/usr/bin/env python3
"""Reads a `uiautomator dump` XML on stdin. Used by `tools/device.sh ui`, `tapui` and `tap <selector>`.

    ui_nodes.py list            one line per labelled node: text | content-desc | bounds, plus
                                " | checked=true|false" for checkable nodes (switches)
    ui_nodes.py list --native   the same without the nodes inside a WebView (page content): only
                                the native views (dock, pills, prompts) stay (PRO-186)
    ui_nodes.py find <label>    centre "x y" of the first node whose text or content-desc
                                equals <label>; exit 1 when there is none
    ui_nodes.py webview         "l t r b" of the largest android.webkit.WebView; exit 1 when none
    ui_nodes.py webview-from-dumpsys
                                reads `dumpsys activity <component>` text instead of XML; prints the
                                same "l t r b" for the largest visible WebView (works when uiautomator
                                returns nothing on a very tall page); exit 1 when none
    ui_nodes.py tap-point <webview "l t r b"> <cdp-result>
                                device "x y" for the CSS centre that the selector expression
                                returned (see device.sh tap); exit 1 with the reason otherwise
"""
import json
import re
import sys
import xml.etree.ElementTree as ET


def _labelled(elements):
    """(text, desc, bounds, checked) per node that has a label or is checkable.

    `checked` is "true" or "false" for checkable nodes (uiautomator's `checkable` attribute)
    and None for the others.
    """
    for node in elements:
        text, desc = node.get("text", ""), node.get("content-desc", "")
        checkable = node.get("checkable") == "true"
        if text or desc or checkable:
            checked = node.get("checked", "false") if checkable else None
            yield text, desc, node.get("bounds", ""), checked


def nodes(root):
    """Labelled nodes of the whole dump, page content included."""
    return _labelled(root.iter("node"))


def _outside_webview(element):
    """Nodes in document order that are not inside a WebView (the WebView node itself is kept)."""
    for child in element:
        if child.tag != "node":
            continue
        yield child
        if child.get("class") != "android.webkit.WebView":
            yield from _outside_webview(child)


def native_nodes(root):
    """Labelled nodes outside every WebView: the dock, the pills and the prompts (ui --native, PRO-186)."""
    return _labelled(_outside_webview(root))


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


# One view line of `dumpsys activity <component>`: "<indent><class>{<hash> <flags> <flags> l,t-r,b ...}".
# The first flag character is the visibility (V visible, I invisible, G gone); bounds are relative to the parent.
_VIEW_LINE = re.compile(r"^(\s*)([\w.$]+)\{[0-9a-f]+ (\S+) \S+ (-?\d+),(-?\d+)-(-?\d+),(-?\d+)")


def dumpsys_views(text):
    """(class name, visible, (l, t, r, b)) per view of a `dumpsys activity <component>` dump, in drawing order.

    The "View Hierarchy:" section lists every view with bounds relative to its parent, so the absolute
    position is the sum of the offsets along the path from the window's root. `visible` is False when the
    view or an ancestor is invisible or gone (the pool keeps one WebView per tab, most of them hidden).
    Scroll offsets are ignored (the layouts above the WebView do not scroll). The window is assumed to
    start at the screen's origin, as the full-screen activity does. A later view is drawn above an earlier
    one. Unlike `uiautomator dump`, this keeps working on a very tall page (PRO-148).
    """
    section_indent = None
    stack = []  # (indent, abs_left, abs_top, visible) of the open ancestors
    for line in text.splitlines():
        stripped = line.lstrip()
        indent = len(line) - len(stripped)
        if stripped.startswith("View Hierarchy:"):
            section_indent, stack = indent, []
            continue
        if section_indent is None:
            continue
        if stripped and indent <= section_indent:
            section_indent, stack = None, []  # the next section ("Looper ...") ends the hierarchy
            continue
        match = _VIEW_LINE.match(line)
        if not match:
            continue
        view_indent = len(match.group(1))
        while stack and stack[-1][0] >= view_indent:
            stack.pop()
        left, top, right, bottom = (int(match.group(n)) for n in (4, 5, 6, 7))
        base_left, base_top, parent_visible = (stack[-1][1], stack[-1][2], stack[-1][3]) if stack else (0, 0, True)
        abs_left, abs_top = base_left + left, base_top + top
        visible = parent_visible and match.group(3)[0] == "V"
        stack.append((view_indent, abs_left, abs_top, visible))
        yield match.group(2), visible, (abs_left, abs_top, abs_left + right - left, abs_top + bottom - top)


def webview_from_dumpsys(text):
    """Screen bounds (l, t, r, b) of the largest visible WebView in a `dumpsys activity <component>` dump.

    Returns None when no WebView is visible (see dumpsys_views for how the bounds are computed).
    """
    best = None
    for name, visible, box in dumpsys_views(text):
        if not visible or not name.endswith("WebView"):
            continue
        area = (box[2] - box[0]) * (box[3] - box[1])
        if area > 0 and (best is None or area > best[0]):
            best = (area, box)
    return best[1] if best else None


def tap_point(webview, cdp_result):
    """Device pixels for the centre of a selected element, and how many matches were skipped.

    `cdp_result` is what cdp_eval.mjs prints: a JSON string holding "[cssX, cssY, innerWidth,
    innerHeight, devicePixelRatio, skipped]" (skipped = hidden matches passed over; optional, 0 when
    absent), "null" when no element matches, or {"total", "selector"} when matches exist but none is
    shown. Raises ValueError with the reason when the point cannot be tapped.
    """
    value = json.loads(cdp_result)
    if isinstance(value, str):  # the expression returns JSON.stringify(...)
        value = json.loads(value)
    if value is None:
        raise ValueError("no element matches the selector")
    if isinstance(value, dict):
        raise ValueError(f"tap: {value['total']} match(es) for {value['selector']}, none visible")
    css_x, css_y, view_w, view_h, dpr = value[:5]
    skipped = value[5] if len(value) > 5 else 0
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
    return x, y, skipped


def main() -> int:
    argv = sys.argv[1:]
    if argv[:1] == ["tap-point"] and len(argv) == 3:
        numbers = list(map(int, argv[1].split()))
        if len(numbers) != 4:
            print("tap-point: the WebView must be 'l t r b'", file=sys.stderr)
            return 2
        try:
            x, y, skipped = tap_point(tuple(numbers), argv[2])
        except ValueError as error:
            print(error, file=sys.stderr)
            return 1
        if skipped:
            print(f"tap: skipped {skipped} hidden match(es)", file=sys.stderr)
        print(x, y)
        return 0

    if argv == ["webview-from-dumpsys"]:
        box = webview_from_dumpsys(sys.stdin.read())
        if box is None:
            print("no visible WebView in the dumpsys view hierarchy", file=sys.stderr)
            return 1
        print(*box)
        return 0

    try:
        root = ET.fromstring(sys.stdin.read())
    except ET.ParseError as error:
        # Exit 2 (not 1): callers read exit 1 as "the label is not on screen".
        print(f"ui_nodes: the UI dump is not valid XML ({error})", file=sys.stderr)
        return 2
    if argv in (["list"], ["list", "--native"]):
        listed = native_nodes(root) if argv[-1] == "--native" else nodes(root)
        for text, desc, bounds, checked in listed:
            suffix = "" if checked is None else f" | checked={checked}"
            print(f"{text} | {desc} | {bounds}{suffix}")
        return 0
    if argv == ["webview"]:
        box = webview_bounds(root)
        if box is None:
            print("no WebView on screen", file=sys.stderr)
            return 1
        print(*box)
        return 0
    if len(argv) == 2 and argv[0] == "find":
        # An empty label would match every unlabelled (checkable) node, so it never matches.
        for text, desc, bounds, _checked in nodes(root):
            if argv[1] and argv[1] in (text, desc):
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
                print((x1 + x2) // 2, (y1 + y2) // 2)
                return 0
        return 1
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
