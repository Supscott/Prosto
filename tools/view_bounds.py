#!/usr/bin/env python3
"""Bounds of the visible view of one class over time, for `tools/device.sh bounds` (PRO-169).

Reads the output of one device loop: a line "@@ <uptime seconds>" before each `dumpsys activity` dump
(device.sh bounds sends them in one adb shell call). Prints one line per sample:
"<ms since the first sample> <l,t,r,b>", or "none" when the dump has no visible view of the class.
The last visible view in drawing order wins (the topmost one).

    view_bounds.py <class-name> < samples.txt

<class-name> is the simple name (NestedWebView) or the fully qualified one
(com.jestk.browser.engine.NestedWebView); both match the same views (PRO-186).
A marker whose uptime is not a number is a one-line error (exit 1), not a traceback.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ui_nodes  # noqa: E402

MARK = "@@ "


def samples(lines):
    """(uptime seconds, dump text) for each marked sample; text before the first marker is ignored.
    Raises ValueError when a marker's uptime is not a number."""
    out = []
    for line in lines:
        if line.startswith(MARK):
            out.append((float(line[len(MARK):].strip()), []))
        elif out:
            out[-1][1].append(line)
    return [(uptime, "\n".join(body)) for uptime, body in out]


def visible_bounds(text, cls):
    """(l, t, r, b) of the last visible view whose class is cls (simple or fully qualified name), or None."""
    found = None
    for name, visible, box in ui_nodes.dumpsys_views(text):
        if visible and (name == cls or name.rsplit(".", 1)[-1] == cls):
            found = box
    return found


def main(argv):
    if len(argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    try:
        rows = samples(sys.stdin.read().splitlines())
    except ValueError:
        print("bounds: a sample marker has no numeric uptime (the device loop printed something else)", file=sys.stderr)
        return 1
    if not rows:
        print("bounds: no sample came back from the device", file=sys.stderr)
        return 1
    start = rows[0][0]
    for uptime, text in rows:
        box = visible_bounds(text, argv[1])
        print(f"{round((uptime - start) * 1000)} {','.join(map(str, box)) if box else 'none'}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
