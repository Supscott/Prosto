#!/usr/bin/env python3
"""Rejects Robolectric `@Config(sdk = [...])` pins to a modern SDK other than the default (PRO-112, PRO-121).

The default SDK is `sdk=` in app/src/test/resources/robolectric.properties. Mixing two modern SDKs in one
test JVM loads two Robolectric sandboxes, which once failed hundreds of tests with `UnsatisfiedLinkError`
after a `FileSystemAlreadyExistsException`. Pins to an older SDK (below 33) stay allowed: they test a
version branch of the app. An SDK given as an unknown `Build.VERSION_CODES` name counts as modern.

Enforced by check_invariants.py.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROPERTIES = Path("app/src/test/resources/robolectric.properties")
FIRST_MODERN_SDK = 33
VERSION_CODES = {
    "O": 26, "O_MR1": 27, "P": 28, "Q": 29, "R": 30, "S": 31, "S_V2": 32,
    "TIRAMISU": 33, "UPSIDE_DOWN_CAKE": 34, "VANILLA_ICE_CREAM": 35,
}
DEFAULT_SDK = re.compile(r"^\s*sdk\s*=\s*(\d+)\s*$", re.MULTILINE)
CONFIG_SDK = re.compile(r"@Config\s*\([^)]*?\bsdk\s*=\s*\[([^\]]*)\]", re.DOTALL)


def default_sdk(root):
    """The `sdk=` value of robolectric.properties, or None when the file or the key is missing."""
    path = root / PROPERTIES
    if not path.is_file():
        return None
    match = DEFAULT_SDK.search(path.read_text(encoding="utf-8"))
    return int(match.group(1)) if match else None


def sdk_value(token):
    """The API level of one array element; an unknown name is treated as a modern SDK."""
    token = token.strip()
    if token.isdigit():
        return int(token)
    return VERSION_CODES.get(token.rsplit(".", 1)[-1], FIRST_MODERN_SDK)


def modern_pins(text, default):
    """(line number, SDK as written) for each pinned SDK that is >= 33 and not the default."""
    pins = []
    for match in CONFIG_SDK.finditer(text):
        line = text.count("\n", 0, match.start(1)) + 1
        for token in match.group(1).split(","):
            if token.strip() and sdk_value(token) >= FIRST_MODERN_SDK and sdk_value(token) != default:
                pins.append((line, token.strip()))
    return pins


def check_robolectric_sdk_pins(root=ROOT):
    """Error lines for modern SDK pins other than the default (check_invariants.py entry point)."""
    default = default_sdk(root)
    if default is None:
        return [f"{PROPERTIES}: missing `sdk=<level>`; the Robolectric default SDK must be set (PRO-112)"]
    errors = []
    for file in sorted((root / "app" / "src" / "test").glob("**/*.kt")):
        for line, token in modern_pins(file.read_text(encoding="utf-8"), default):
            errors.append(
                f"{file.relative_to(root)}:{line}: @Config(sdk = [{token}]) pins a modern SDK other than the "
                f"default {default}; a second modern SDK loads another Robolectric sandbox and caused "
                f"mass UnsatisfiedLinkError failures (PRO-112). Drop the pin, or pin an SDK below "
                f"{FIRST_MODERN_SDK} to test a version branch."
            )
    return errors


def main():
    errors = check_robolectric_sdk_pins()
    for error in errors:
        print(error)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
