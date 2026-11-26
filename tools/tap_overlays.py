#!/usr/bin/env python3
"""Native overlays over a tap point, for `tools/device.sh tap <url-prefix> <selector>` (PRO-166).

Compose draws the dock and the "Group N tabs" pill inside one full-screen ComposeView, so
`dumpsys activity` cannot show them. Two sources are read instead:
  - dumpsys: the visible views drawn after the WebView (containers are skipped: they draw nothing);
  - uiautomator `dump --compressed`: the clickable or labelled nodes listed after the WebView
    (the WebView's own subtree is skipped, so page content never counts as an overlay).
Both are in drawing order: a later entry is drawn above an earlier one.

    tap_overlays.py plan "<l t r b>" <x> <y> <dumpsys-file> <uia-xml-file>
                        prints "clear", or "covered<TAB><scroll-dy or ->" + TAB + <label>, where
                        scroll-dy is the device-pixel scroll (positive = down) that moves the point
                        into the larger band of the WebView the overlay leaves clear, "-" when none.
    tap_overlays.py index <cdp-result>
                        the index of the element tap chose (the sixth value of the page result)
    tap_overlays.py band "<l t r b>" <y>
                        exit 0 when device row y is in the bottom quarter of the WebView, where the dock
                        and the pills sit (the only place uiautomator is read for, PRO-170); exit 1 otherwise
    tap_overlays.py moved <before-cdp-result> <after-cdp-result>
                        "moved" when the element's centre changed after a scroll, "stuck" when the page
                        could not scroll any further (PRO-170)
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ui_nodes  # noqa: E402

# Views that only group other views draw nothing themselves, so they never cover a tap.
_CONTAINER = re.compile(r"(Layout|Group|Holder|Handler|ViewStub|ComposeView|DecorView|ViewGroup|(^|\.)View)$")


def _area(box):
    return (box[2] - box[0]) * (box[3] - box[1])


def _intersects(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def dumpsys_overlays(text, webview):
    """Visible non-container views drawn after the WebView (the last view with the WebView's bounds)."""
    views = list(ui_nodes.dumpsys_views(text))
    start = None
    for index, (name, visible, box) in enumerate(views):
        if visible and box == webview and name.endswith("WebView"):
            start = index
    if start is None:
        return []
    found = []
    for name, visible, box in views[start + 1:]:
        if visible and not _CONTAINER.search(name) and _area(box) > 0 and _intersects(box, webview):
            found.append({"cls": name, "id": "", "text": "", "desc": "", "box": box})
    return found


def uia_overlays(xml_text, webview):
    """Clickable or labelled uiautomator nodes after the WebView's subtree that overlap it."""
    if not xml_text.strip():
        return []
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return []
    nodes = list(root.iter("node"))
    largest = None  # (index, area) of the largest WebView node
    for index, node in enumerate(nodes):
        box = ui_nodes.parse_bounds(node.get("bounds", ""))
        if node.get("class") == "android.webkit.WebView" and box and (largest is None or _area(box) > largest[1]):
            largest = (index, _area(box))
    if largest is None:
        return []
    web_node = nodes[largest[0]]
    # Pre-order: the WebView's own subtree follows it (full dumps only) and is skipped.
    later = nodes[largest[0] + len(list(web_node.iter("node"))):]
    found = []
    for node in later:
        box = ui_nodes.parse_bounds(node.get("bounds", ""))
        labelled = node.get("clickable") == "true" or node.get("text") or node.get("content-desc")
        if box and _area(box) > 0 and _intersects(box, webview) and labelled:
            found.append({
                "cls": node.get("class", ""), "id": node.get("resource-id", ""),
                "text": node.get("text", ""), "desc": node.get("content-desc", ""), "box": box,
            })
    return found


def covering(overlays, x, y):
    """The overlay drawn on top at device point (x, y), or None."""
    for overlay in reversed(overlays):
        left, top, right, bottom = overlay["box"]
        if left <= x < right and top <= y < bottom:
            return overlay
    return None


def scroll_dy(webview, box, y):
    """Device pixels (positive = down) that move the point at y to the middle of the larger WebView
    band the overlay leaves clear, or None when neither band above nor below it has any height."""
    bands = [band for band in ((webview[1], box[1]), (box[3], webview[3])) if band[1] - band[0] > 0]
    if not bands:
        return None
    top, bottom = max(bands, key=lambda band: band[1] - band[0])
    return y - (top + bottom) // 2


def in_bottom_band(webview, y):
    """True when device row y is in the bottom quarter of the WebView (the dock and the pills live there)."""
    top, bottom = webview[1], webview[3]
    return y >= top + (bottom - top) * 3 / 4


def centre_y(cdp_result):
    """The element's centre Y (CSS px) from a cdp_eval.mjs page result (the same shape chosen_index reads)."""
    value = json.loads(cdp_result)
    if isinstance(value, str):  # the expression returns JSON.stringify(...)
        value = json.loads(value)
    return value[1]


def describe(overlay):
    """One line naming the overlay: class, resource id, text, content description and bounds."""
    left, top, right, bottom = overlay["box"]
    parts = [overlay["cls"]]
    if overlay["id"]:
        parts.append(f"id={overlay['id']}")
    if overlay["text"]:
        parts.append(f'text="{overlay["text"]}"')
    if overlay["desc"]:
        parts.append(f'desc="{overlay["desc"]}"')
    parts.append(f"bounds=[{left},{top}][{right},{bottom}]")
    return " ".join(parts).replace("\t", " ").replace("\n", " ")


def plan(webview, x, y, dumpsys_text, uia_xml):
    """"clear", or "covered<TAB>scroll-dy-or-dash<TAB>label" (see the module docstring)."""
    overlays = dumpsys_overlays(dumpsys_text, webview) + uia_overlays(uia_xml, webview)
    overlay = covering(overlays, x, y)
    if overlay is None:
        return "clear"
    dy = scroll_dy(webview, overlay["box"], y)
    return f"covered\t{'-' if dy is None else dy}\t{describe(overlay)}"


def chosen_index(cdp_result):
    """Index of the match tap_selector chose: the sixth value, which counts the matches passed over."""
    value = json.loads(cdp_result)
    if isinstance(value, str):  # the expression returns JSON.stringify(...)
        value = json.loads(value)
    return value[5] if isinstance(value, list) and len(value) > 5 else 0


def main(argv):
    if len(argv) == 3 and argv[1] == "index":
        print(chosen_index(argv[2]))
        return 0
    if len(argv) == 4 and argv[1] == "band":
        webview = tuple(int(n) for n in argv[2].split())
        if len(webview) != 4:
            print("tap_overlays: the WebView must be 'l t r b'", file=sys.stderr)
            return 2
        return 0 if in_bottom_band(webview, int(argv[3])) else 1
    if len(argv) == 4 and argv[1] == "moved":
        moved = abs(centre_y(argv[3]) - centre_y(argv[2])) >= 1
        print("moved" if moved else "stuck")
        return 0
    if len(argv) == 7 and argv[1] == "plan":
        webview = tuple(int(n) for n in argv[2].split())
        if len(webview) != 4:
            print("tap_overlays: the WebView must be 'l t r b'", file=sys.stderr)
            return 2
        dumpsys_text = Path(argv[5]).read_text(errors="replace")
        uia_xml = Path(argv[6]).read_text(errors="replace")
        print(plan(webview, int(argv[3]), int(argv[4]), dumpsys_text, uia_xml))
        return 0
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
