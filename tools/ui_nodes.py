#!/usr/bin/env python3
"""Reads a `uiautomator dump` XML on stdin. Used by `tools/device.sh ui` and `tapui`.

    ui_nodes.py list            one line per labelled node: text | content-desc | bounds
    ui_nodes.py find <label>    centre "x y" of the first node whose text or content-desc
                                equals <label>; exit 1 when there is none
"""
import re
import sys
import xml.etree.ElementTree as ET


def nodes(root):
    for node in root.iter("node"):
        text, desc = node.get("text", ""), node.get("content-desc", "")
        if text or desc:
            yield text, desc, node.get("bounds", "")


def main() -> int:
    root = ET.fromstring(sys.stdin.read())
    if sys.argv[1:2] == ["list"]:
        for text, desc, bounds in nodes(root):
            print(f"{text} | {desc} | {bounds}")
        return 0
    if len(sys.argv) == 3 and sys.argv[1] == "find":
        for text, desc, bounds in nodes(root):
            if sys.argv[2] in (text, desc):
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
                print((x1 + x2) // 2, (y1 + y2) // 2)
                return 0
        return 1
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
