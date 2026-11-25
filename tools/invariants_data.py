"""Domain-layer and data-layer invariants: domain purity, no English sentinels in logic, and SQL
time-ordered queries with a tie-breaker."""

import re

from invariants_common import MAIN_DIR, ROOT, code_lines

DOMAIN_DIR = MAIN_DIR / "java" / "com" / "jestk" / "browser" / "domain"


def check_domain_layer_purity():
    """Verify the domain layer stays free of Android framework and Compose types.

    The domain is plain Kotlin: it must not reach for views, widgets, bitmaps or any other
    framework type. Platform data has to travel as plain values instead.
    """
    errors = []
    disallowed = re.compile(r"^import\s+(android\.|androidx\.compose\.)", re.MULTILINE)
    for file in DOMAIN_DIR.rglob("*.kt"):
        content = file.read_text(encoding="utf-8")
        for match in disallowed.finditer(content):
            errors.append(
                f"{file.relative_to(ROOT)}: imports disallowed package "
                f"({match.group(0).strip()})"
            )
    return errors


def check_no_english_sentinels():
    """English placeholder titles must not be stored or compared (DATA-6).

    Home tabs are identified by `Tab.isHomeTab`; blank titles and workspace names are
    rendered from resources (`tabDisplayTitle`, `workspaceDisplayName`).
    """
    errors = []
    pattern = re.compile(r'(==|!=|title\s*=|name\s*=)\s*"(Home|New Tab|Loading\.\.\.|Search|Personal|Work)"')
    for file in (MAIN_DIR / "java").rglob("*.kt"):
        for lineno, line in code_lines(file):
            if line.startswith("@Preview"):
                break  # preview fixtures below may use sample names
            if pattern.search(line):
                errors.append(f"{file.relative_to(ROOT)}:{lineno}: English sentinel in data/logic: {line.strip()}")
    return errors


def check_time_ordered_queries_have_tiebreaker():
    """Verify every SQL query ordered by a time column has a second, unique sort key.

    `System.currentTimeMillis()` repeats within a millisecond, so rows inserted together (tests,
    rapid actions) tie, and `ORDER BY timestamp DESC` alone returns them in any order. Add a
    deterministic tie-breaker: `ORDER BY timestamp DESC, id DESC`.
    """
    errors = []
    order_by = re.compile(r"ORDER BY\s+([^\"]*)", re.IGNORECASE)
    time_column = re.compile(r"(timestamp|At)$", re.IGNORECASE)
    for file in (ROOT / "app/src/main/java").rglob("*.kt"):
        for lineno, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
            for match in order_by.finditer(line):
                clause = re.split(r"\s+(LIMIT|OFFSET)\b", match.group(1), flags=re.IGNORECASE)[0]
                keys = [k.strip() for k in clause.split(",") if k.strip()]
                first = keys[0].split()[0] if keys else ""
                if time_column.search(first) and len(keys) < 2:
                    errors.append(
                        f"{file.relative_to(ROOT)}:{lineno}: ORDER BY {first} has no tie-breaker; "
                        "add a unique second key such as `, id DESC`."
                    )
    return errors
