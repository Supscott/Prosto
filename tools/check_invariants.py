#!/usr/bin/env python3
"""Mechanical invariant checks for Hori (Harness^2 gate step)."""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN_DIR = ROOT / "app" / "src" / "main"
DOMAIN_DIR = MAIN_DIR / "java" / "com" / "jestk" / "browser" / "domain"
WEB_CLIENT = (
    MAIN_DIR
    / "java"
    / "com"
    / "jestk"
    / "browser"
    / "engine"
    / "JestkWebViewClient.kt"
)
FAVICON_MANAGER = (
    MAIN_DIR / "java" / "com" / "jestk" / "browser" / "engine" / "FaviconManager.kt"
)


def check_no_blur():
    """Verify Serene Canvas (STRICT NO BLUR) invariant."""
    errors = []
    for file in MAIN_DIR.rglob("*.kt"):
        content = file.read_text(encoding="utf-8")
        if "Modifier.blur(" in content:
            errors.append(f"{file.relative_to(ROOT)}: uses Modifier.blur()")
        if "dev.chrisbanes.haze" in content:
            errors.append(f"{file.relative_to(ROOT)}: imports dev.chrisbanes.haze")
    return errors


def check_webkit_crash_resilience():
    """Verify WebKit onRenderProcessGone crash resilience invariant."""
    if not WEB_CLIENT.exists():
        return [f"{WEB_CLIENT.relative_to(ROOT)} not found"]
    content = WEB_CLIENT.read_text(encoding="utf-8")
    if "override fun onRenderProcessGone" not in content:
        return [
            f"{WEB_CLIENT.relative_to(ROOT)}: does not override onRenderProcessGone"
        ]
    return []


def check_domain_layer_purity():
    """Verify domain layer does not import Android view or widget packages."""
    errors = []
    disallowed = re.compile(r"^import\s+android\.(view|widget)\.", re.MULTILINE)
    for file in DOMAIN_DIR.rglob("*.kt"):
        content = file.read_text(encoding="utf-8")
        if disallowed.search(content):
            errors.append(
                f"{file.relative_to(ROOT)}: imports disallowed android.view/widget package"
            )
    return errors


def check_stable_favicon_paths():
    """Verify favicon file paths stay stable across icon reports.

    Pages report their icon on every navigation and, for dynamic sites, several times per second.
    A path that embeds a timestamp publishes a new URL on every report and makes every consumer
    reload the icon, which shows up as a flickering favicon in the dock.
    """
    if not FAVICON_MANAGER.exists():
        return [f"{FAVICON_MANAGER.relative_to(ROOT)} not found"]
    content = FAVICON_MANAGER.read_text(encoding="utf-8")
    if "currentTimeMillis" in content:
        return [
            f"{FAVICON_MANAGER.relative_to(ROOT)}: favicon file names must not embed a timestamp"
        ]
    return []


def check_no_continuous_effect_keys():
    """Verify LaunchedEffect is never keyed by a continuous scroll/drag value."""
    errors = []
    pattern = re.compile(r"LaunchedEffect\(\s*[^)]*?(\.value|OffsetPx|ScrollY|scrollY)")
    for file in MAIN_DIR.rglob("*.kt"):
        content = file.read_text(encoding="utf-8")
        for match in pattern.finditer(content):
            errors.append(
                f"{file.relative_to(ROOT)}: LaunchedEffect keyed by a continuous value "
                f"({match.group(0).strip()[:60]})"
            )
    return errors


def main():
    all_errors = []
    all_errors.extend(check_no_blur())
    all_errors.extend(check_webkit_crash_resilience())
    all_errors.extend(check_domain_layer_purity())
    all_errors.extend(check_stable_favicon_paths())
    all_errors.extend(check_no_continuous_effect_keys())

    if all_errors:
        print("=== Architectural Invariant Violations ===")
        for err in all_errors:
            print(f"  FAIL: {err}")
        return 1

    print("All architectural invariants satisfied (No Blur, WebKit Crash Resilience, Domain Purity).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
