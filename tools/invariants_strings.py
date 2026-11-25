"""Localization and comment-language invariants: both bundles carry the same keys, UI text and
toasts come from resources, and code comments are English."""

import re

from invariants_common import MAIN_DIR, ROOT, code_lines


def _resource_keys(path):
    content = path.read_text(encoding="utf-8")
    strings = set(re.findall(r'<string name="([^"]+)"', content))
    plurals = set(re.findall(r'<plurals name="([^"]+)"', content))
    return strings, plurals


def check_localization_parity():
    """Verify every user-facing string exists in both locales.

    The Russian and English bundles drifted silently while 79 literals bypassed resources
    entirely; a key that exists in one locale only renders as the default language.
    """
    values_en = MAIN_DIR / "res" / "values" / "strings.xml"
    values_ru = MAIN_DIR / "res" / "values-ru" / "strings.xml"
    if not values_en.exists() or not values_ru.exists():
        return ["string resource bundles not found"]

    en_strings, en_plurals = _resource_keys(values_en)
    ru_strings, ru_plurals = _resource_keys(values_ru)
    errors = []
    for key in sorted(en_strings - ru_strings):
        errors.append(f"values/strings.xml: string '{key}' is missing from values-ru")
    for key in sorted(ru_strings - en_strings):
        errors.append(f"values-ru/strings.xml: string '{key}' has no English counterpart")
    for key in sorted(en_plurals - ru_plurals):
        errors.append(f"values/strings.xml: plurals '{key}' are missing from values-ru")
    for key in sorted(ru_plurals - en_plurals):
        errors.append(f"values-ru/strings.xml: plurals '{key}' have no English counterpart")
    return errors


def check_no_hardcoded_ui_strings():
    """Verify user-facing text and accessibility labels come from localized resources.

    A literal cannot be translated, so every one of them is a mixed-language screen for
    non-English users. Glyph-only separators are not language and are allowed.
    """
    errors = []
    pattern = re.compile(r'\b(text|contentDescription)\s*=\s*"([^"]*)"')
    # Toasts and notifications are user-facing too (UI-7).
    call_pattern = re.compile(
        r'\b(Toast\.makeText\([^,]+,|setContentTitle\(|setContentText\(|setSubText\(|'
        r'addAction\(\s*\d+\s*,|createChooser\([^,]+,)\s*"([^"]*[A-Za-z][^"]*)"'
    )
    for file in (MAIN_DIR / "java").rglob("*.kt"):
        content = file.read_text(encoding="utf-8")
        for lineno, line in enumerate(content.splitlines(), 1):
            if line.lstrip().startswith("//"):
                continue
            for match in pattern.finditer(line):
                # str.isalpha() is Unicode-aware: Cyrillic counts as letters, "·" and "" do not.
                if any(ch.isalpha() for ch in match.group(2)):
                    errors.append(
                        f"{file.relative_to(ROOT)}:{lineno}: hardcoded {match.group(1)} "
                        f"literal {match.group(2)!r}"
                    )
            for match in call_pattern.finditer(line):
                errors.append(
                    f"{file.relative_to(ROOT)}:{lineno}: hardcoded toast/notification "
                    f"literal {match.group(2)!r}; use a string resource"
                )
    return errors


def check_english_only_comments():
    """Code comments are English-only (UI-8). Quoted page text the code matches is data."""
    errors = []
    cyrillic = re.compile(r"[Ѐ-ӿ]")
    allowlist = {"AdBlockerScript.kt"}  # documents the Russian consent-button labels it clicks
    for file in (MAIN_DIR / "java").rglob("*.kt"):
        if file.name in allowlist:
            continue
        for lineno, line in code_lines(file):
            stripped = line.lstrip()
            comment = None
            if stripped.startswith(("//", "*", "/*")):
                comment = stripped
            elif "//" in line and '"' not in line.split("//", 1)[0][-1:]:
                comment = line.split("//", 1)[1]
            if comment and cyrillic.search(comment):
                errors.append(f"{file.relative_to(ROOT)}:{lineno}: non-English comment")
    return errors
