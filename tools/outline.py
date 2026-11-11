#!/usr/bin/env python3
"""A map of a large Kotlin file, so it can be read in parts instead of whole.

  python3 tools/outline.py FILE                 members: start line, length, visibility, name
  python3 tools/outline.py FILE --indent 0      top-level declarations instead of class members
  python3 tools/outline.py FILE --groups tabs=95-182 close=782-1037 ...
        calls between groups of lines and which groups use each member property, for
        planning a split (a call from one group into another becomes a dependency)

A member spans from its declaration to the next declaration at the same indent, so the
length includes its trailing comments. Calls are matched by name, including extension calls
(`state.tabsWithNewTab(`) and calls on other receivers, so the graph may over-report a
dependency but does not miss one.
"""

import argparse
import re

DECL = r"(?:(?:private|internal|public|protected|override|suspend|inline|operator|data|sealed|enum|abstract|open|lateinit|const|@\w+(?:\([^)]*\))?)\s+)*"


def members(lines, indent):
    pattern = re.compile(
        "^" + " " * indent + DECL
        + r"(fun|val|var|class|object|interface|init)\b\s*(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?(\w*)"
    )
    hits = []
    for i, line in enumerate(lines):
        match = pattern.match(line)
        if match:
            hits.append((i, match.group(1), match.group(2), "private" in line.split(match.group(1))[0]))
    result = []
    for k, (i, kind, name, private) in enumerate(hits):
        end = hits[k + 1][0] - 1 if k + 1 < len(hits) else len(lines) - 1
        result.append({"start": i + 1, "end": end + 1, "kind": kind, "name": name, "private": private})
    return result


def print_outline(items):
    for m in items:
        visibility = "p" if m["private"] else " "
        print(f"{m['start']:5} {m['end'] - m['start'] + 1:4} {visibility} {m['kind']} {m['name']}")


def print_graph(lines, items, groups):
    def group_of(line_no):
        for name, (a, b) in groups.items():
            if a <= line_no <= b:
                return name
        return "?"

    funcs = [m for m in items if m["kind"] == "fun" and m["name"]]
    props = [m for m in items if m["kind"] in ("val", "var") and m["name"]]
    edges, uses = {}, {}
    for m in funcs:
        source = group_of(m["start"])
        body = "\n".join(lines[m["start"]:m["end"]])  # skips the declaration line itself
        for callee in funcs:
            if callee["name"] == m["name"]:
                continue
            target = group_of(callee["start"])
            if target != source and re.search(r"\b" + re.escape(callee["name"]) + r"\s*[({]", body):
                edges.setdefault((source, target), set()).add(callee["name"])
        for prop in props:
            if re.search(r"\b" + re.escape(prop["name"]) + r"\b", body):
                uses.setdefault(prop["name"], set()).add(source)
    print("Calls between groups (caller -> callee group: functions):")
    for (a, b), names in sorted(edges.items()):
        print(f"  {a} -> {b}: {', '.join(sorted(names))}")
    print("Member properties used by more than one group:")
    for name, used_by in sorted(uses.items()):
        if len(used_by) > 1:
            print(f"  {name}: {', '.join(sorted(used_by))}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("file")
    parser.add_argument("--indent", type=int, default=4)
    parser.add_argument("--groups", nargs="*", default=None, help="name=start-end (line numbers)")
    args = parser.parse_args()
    lines = open(args.file, encoding="utf-8").read().splitlines()
    items = members(lines, args.indent)
    if args.groups is None:
        print_outline(items)
        return
    groups = {}
    for spec in args.groups:
        name, span = spec.split("=")
        a, b = span.split("-")
        groups[name] = (int(a), int(b))
    print_graph(lines, items, groups)


if __name__ == "__main__":
    main()
