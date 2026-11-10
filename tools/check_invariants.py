#!/usr/bin/env python3
"""Mechanical invariant checks for Prosto Browser."""

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
    """Verify Prosto Canvas (STRICT NO BLUR) invariant."""
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


def check_webview_client_no_favicon():
    """Verify JestkWebViewClient never handles or delegates favicon callbacks.

    In Android WebView, onPageStarted(view, url, favicon) and view.favicon in
    onPageFinished frequently yield the PREVIOUS page's favicon during cross-domain
    navigations. WebChromeClient.onReceivedIcon is the sole canonical source.
    """
    if not WEB_CLIENT.exists():
        return [f"{WEB_CLIENT.relative_to(ROOT)} not found"]
    content = WEB_CLIENT.read_text(encoding="utf-8")
    errors = []
    if "onFaviconReceived" in content:
        errors.append(
            f"{WEB_CLIENT.relative_to(ROOT)}: must not handle or invoke onFaviconReceived"
        )
    if ".favicon" in content:
        errors.append(
            f"{WEB_CLIENT.relative_to(ROOT)}: must not access view.favicon (stale on navigation)"
        )
    return errors


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
    allowed = {"", "·"}
    for file in (MAIN_DIR / "java").rglob("*.kt"):
        content = file.read_text(encoding="utf-8")
        for lineno, line in enumerate(content.splitlines(), 1):
            if line.lstrip().startswith("//"):
                continue
            for match in pattern.finditer(line):
                if match.group(2) not in allowed:
                    errors.append(
                        f"{file.relative_to(ROOT)}:{lineno}: hardcoded {match.group(1)} "
                        f"literal {match.group(2)!r}"
                    )
    return errors


def check_security_challenge_protection():
    """Verify security challenge domains are exempted from content scripts and client hints are pure."""
    errors = []
    ad_blocker_engine = MAIN_DIR / "java" / "com" / "jestk" / "browser" / "engine" / "AdBlockerEngine.kt"
    ad_blocker_script = MAIN_DIR / "java" / "com" / "jestk" / "browser" / "engine" / "AdBlockerScript.kt"
    ua_metadata_provider = MAIN_DIR / "java" / "com" / "jestk" / "browser" / "engine" / "UserAgentMetadataProvider.kt"

    if ad_blocker_engine.exists():
        content = ad_blocker_engine.read_text(encoding="utf-8")
        if "challenges.cloudflare.com" not in content or "cloudflare.com" not in content:
            errors.append(f"{ad_blocker_engine.relative_to(ROOT)}: AUTH_SECURITY_ALLOWLIST missing Cloudflare domains")

    if ad_blocker_script.exists():
        content = ad_blocker_script.read_text(encoding="utf-8")
        if "challenges.cloudflare.com" not in content or "cloudflarecaptcha.com" not in content:
            errors.append(f"{ad_blocker_script.relative_to(ROOT)}: missing security challenge domain guards")

    if WEB_CLIENT.exists():
        content = WEB_CLIENT.read_text(encoding="utf-8")
        if "challenge-platform" not in content or "isAllowlistedAuthDomain" not in content:
            errors.append(f"{WEB_CLIENT.relative_to(ROOT)}: missing security challenge checks in script injection")

    if ua_metadata_provider.exists():
        content = ua_metadata_provider.read_text(encoding="utf-8")
        if "buildOrUpdateMetadata" not in content:
            errors.append(f"{ua_metadata_provider.relative_to(ROOT)}: missing buildOrUpdateMetadata method")

    return errors


def check_injected_script_stealth():
    """Keep page-visible artefacts of injected scripts out of the code (review BOT-1/2/3, WV-3).

    - No replacement of window.matchMedia and no fake `fn.toString = function` "stealth": both are
      detectable with Function.prototype.toString.call(fn).
    - No `window.__*` names: they show up in Object.getOwnPropertyNames(window).
    - addJavascriptInterface only where a reviewed bridge lives.
    - JestkWebViewClient is built in one place (ui/views/WebViewClientBinder.kt).
    - Raw filter lists stay in tools/filterlists, out of the APK.
    """
    errors = []
    forbidden = {
        "window.matchMedia =": "replaces window.matchMedia",
        ".toString = function": "fakes a native toString",
        "window.__": "uses a window.__* global",
    }
    bridge_files = {"WebViewPool.kt", "CleanVideoPlayerOverlay.kt"}
    for file in MAIN_DIR.rglob("*.kt"):
        content = file.read_text(encoding="utf-8")
        rel = file.relative_to(ROOT)
        for needle, why in forbidden.items():
            if needle in content:
                errors.append(f"{rel}: {why} ({needle!r})")
        if "addJavascriptInterface(" in content and file.name not in bridge_files:
            errors.append(f"{rel}: addJavascriptInterface outside the reviewed bridge files")
        if file.name not in {"JestkWebViewClient.kt", "WebViewClientBinder.kt"} and re.search(r"\bJestkWebViewClient\(", content):
            errors.append(f"{rel}: builds JestkWebViewClient outside WebViewClientBinder.kt")
    assets = MAIN_DIR / "assets"
    for file in assets.glob("*.txt"):
        if file.name != "adblock_domains.txt":
            errors.append(f"{file.relative_to(ROOT)}: raw filter list in APK assets (keep it in tools/filterlists)")
    return errors


def main():
    all_errors = []
    all_errors.extend(check_no_blur())
    all_errors.extend(check_webkit_crash_resilience())
    all_errors.extend(check_domain_layer_purity())
    all_errors.extend(check_stable_favicon_paths())
    all_errors.extend(check_webview_client_no_favicon())
    all_errors.extend(check_no_continuous_effect_keys())
    all_errors.extend(check_localization_parity())
    all_errors.extend(check_no_hardcoded_ui_strings())
    all_errors.extend(check_security_challenge_protection())
    all_errors.extend(check_injected_script_stealth())

    if all_errors:
        print("=== Architectural Invariant Violations ===")
        for err in all_errors:
            print(f"  FAIL: {err}")
        return 1

    print(
        "All architectural invariants satisfied (No Blur, WebKit Crash Resilience, "
        "Domain Purity, Favicon Safety, Localization, Security Challenge Protection, Script Stealth)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
