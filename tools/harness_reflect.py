#!/usr/bin/env python3
"""Harness^2 experience reflector (portable): bounded agent memory with a
graduation policy.

    python3 tools/harness_reflect.py audit
    python3 tools/harness_reflect.py add --title "..." --cause "..." --do "..." --dont "..."
    python3 tools/harness_reflect.py graduate 3 --reason "covered by tests/test_x.py"

Policy:
- Maximum 10-12 active lessons (configurable with --max).
- Every lesson is structured as Cause / Do / Don't.
- A lesson that became impossible to trigger graduates to the archive file.
"""

import argparse
import re
import sys
from datetime import datetime
from pathlib import Path

DEFAULT_LESSONS = "LESSONS.md"
DEFAULT_ARCHIVE = "docs/archive/lessons_archive.md"
MAX_ACTIVE_LESSONS = 12
MIN_ACTIVE_LESSONS_WARNING = 10


def parse_lessons(content):
    """Parses a lessons file into (header, [lesson dicts])."""
    header_lines = []
    lessons = []
    current = None
    header_finished = False

    for line in content.splitlines():
        match = re.match(r"^##\s+(\d+)\.\s+(.*)$", line)
        if match:
            header_finished = True
            if current:
                lessons.append(current)
            current = {"num": int(match.group(1)), "title": match.group(2).strip(), "body_lines": []}
        elif not header_finished:
            header_lines.append(line)
        elif current:
            current["body_lines"].append(line)

    if current:
        lessons.append(current)
    return "\n".join(header_lines).strip(), lessons


def validate_lesson_body(body_lines):
    text = "\n".join(body_lines)
    return all(
        re.search(pattern, text)
        for pattern in (r"-\s+\*\*Cause:\*\*", r"-\s+\*\*Do:\*\*", r"-\s+\*\*Don't:\*\*")
    )


def audit(lessons_file, max_lessons=MAX_ACTIVE_LESSONS):
    if not lessons_file.exists():
        print(f"Error: {lessons_file} not found.")
        return 1

    _, lessons = parse_lessons(lessons_file.read_text(encoding="utf-8"))
    print("=== Harness^2 Experience Reflector: Memory Audit ===")
    print(f"Active lessons count: {len(lessons)} (Target: <= {max_lessons})")

    status_ok = True
    if len(lessons) > max_lessons:
        print("WARNING: active lessons exceed budget. Graduate resolved lessons first.")
        status_ok = False
    elif len(lessons) >= MIN_ACTIVE_LESSONS_WARNING:
        print("NOTICE: active lessons nearing maximum capacity.")

    for idx, lesson in enumerate(lessons, 1):
        valid = validate_lesson_body(lesson["body_lines"])
        if not valid:
            status_ok = False
        mark = "ok" if valid else "MISSING Cause/Do/Don't"
        print(f"  [{idx}] {lesson['title']} -> {mark}")

    print("=" * 52)
    return 0 if status_ok else 1


def graduate(lessons_file, archive_file, lesson_id, reason=""):
    if not lessons_file.exists():
        print(f"Error: {lessons_file} not found.")
        return 1

    content = lessons_file.read_text(encoding="utf-8")
    header, lessons = parse_lessons(content)
    target = next(
        (lesson for lesson in lessons if lesson["num"] == lesson_id), None
    )
    if not target:
        print(f"Error: lesson #{lesson_id} not found.")
        return 1

    archive_file.parent.mkdir(parents=True, exist_ok=True)
    if not archive_file.exists():
        archive_file.write_text(
            "# Lessons Archive (Graduated Invariants)\n\n"
            "Lessons that were permanently resolved by automated tests, assertions or "
            "architectural invariants.\n"
        )

    reason_str = f"\n- **Graduation Reason:** {reason}" if reason else ""
    entry = (
        f"\n## Retired Lesson: {target['title']}\n"
        f"- **Graduated On:** {datetime.now().strftime('%Y-%m-%d')}{reason_str}\n"
        + "\n".join(target["body_lines"]).strip()
        + "\n"
    )
    with archive_file.open("a", encoding="utf-8") as handle:
        handle.write(entry)

    remaining = [lesson for lesson in lessons if lesson["num"] != lesson_id]
    blocks = [header, ""]
    for idx, lesson in enumerate(remaining, 1):
        blocks.append(f"## {idx}. {lesson['title']}")
        blocks.append("\n".join(lesson["body_lines"]).strip())
        blocks.append("")
    lessons_file.write_text("\n".join(blocks).rstrip() + "\n", encoding="utf-8")

    print(f"Graduated lesson #{lesson_id} ('{target['title']}') to {archive_file}")
    print(f"Remaining active lessons: {len(remaining)}/{MAX_ACTIVE_LESSONS}")
    return 0


def add_lesson(lessons_file, title, cause, do, dont, max_lessons=MAX_ACTIVE_LESSONS):
    if not lessons_file.exists():
        print(f"Error: {lessons_file} not found.")
        return 1

    content = lessons_file.read_text(encoding="utf-8")
    header, lessons = parse_lessons(content)
    if len(lessons) >= max_lessons:
        print(
            f"Cannot add: budget full ({len(lessons)}/{max_lessons}). "
            "Graduate a resolved lesson first: harness_reflect.py graduate <id> --reason ..."
        )
        return 1

    block = (
        f"\n## {len(lessons) + 1}. {title.strip()}\n"
        f"- **Cause:** {cause.strip()}\n"
        f"- **Do:** {do.strip()}\n"
        f"- **Don't:** {dont.strip()}\n"
    )
    with lessons_file.open("a", encoding="utf-8") as handle:
        handle.write(block)
    print(f"Added lesson #{len(lessons) + 1}: '{title.strip()}'")
    print(f"Current active lessons: {len(lessons) + 1}/{max_lessons}")
    return 0


def main():
    parser = argparse.ArgumentParser(description="Harness^2 experience reflector")
    parser.add_argument("--lessons", default=DEFAULT_LESSONS)
    parser.add_argument("--archive", default=DEFAULT_ARCHIVE)
    parser.add_argument("--max", type=int, default=MAX_ACTIVE_LESSONS)
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("audit", help="Audit active lessons against count and format rules")

    grad = sub.add_parser("graduate", help="Retire an active lesson to the archive")
    grad.add_argument("id", type=int, help="Lesson number to graduate")
    grad.add_argument("--reason", default="", help="Why it can no longer happen")

    add = sub.add_parser("add", help="Add a new lesson")
    add.add_argument("--title", required=True)
    add.add_argument("--cause", required=True)
    add.add_argument("--do", required=True)
    add.add_argument("--dont", required=True)

    args = parser.parse_args()
    lessons_file = Path(args.lessons)
    archive_file = Path(args.archive)

    if args.command == "audit":
        return audit(lessons_file, args.max)
    if args.command == "graduate":
        return graduate(lessons_file, archive_file, args.id, args.reason)
    if args.command == "add":
        return add_lesson(lessons_file, args.title, args.cause, args.do, args.dont, args.max)
    return 2


if __name__ == "__main__":
    sys.exit(main())
