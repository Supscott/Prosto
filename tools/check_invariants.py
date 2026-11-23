#!/usr/bin/env python3
"""Mechanical invariant checks for Prosto Browser."""

import re
import subprocess
import sys
from pathlib import Path

from file_size_ratchet import check_file_sizes

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
    # Toasts and notifications are user-facing too (UI-7).
    call_pattern = re.compile(
        r'\b(Toast\.makeText\([^,]+,|setContentTitle\(|setContentText\(|setSubText\(|'
        r'addAction\(\s*\d+\s*,|createChooser\([^,]+,)\s*"([^"]*[A-Za-z][^"]*)"'
    )
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
            for match in call_pattern.finditer(line):
                errors.append(
                    f"{file.relative_to(ROOT)}:{lineno}: hardcoded toast/notification "
                    f"literal {match.group(2)!r}; use a string resource"
                )
    return errors


def _code_lines(file):
    for lineno, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
        yield lineno, line


def check_no_english_sentinels():
    """English placeholder titles must not be stored or compared (DATA-6).

    Home tabs are identified by `Tab.isHomeTab`; blank titles and workspace names are
    rendered from resources (`tabDisplayTitle`, `workspaceDisplayName`).
    """
    errors = []
    pattern = re.compile(r'(==|!=|title\s*=|name\s*=)\s*"(Home|New Tab|Loading\.\.\.|Search|Personal|Work)"')
    for file in (MAIN_DIR / "java").rglob("*.kt"):
        for lineno, line in _code_lines(file):
            if line.startswith("@Preview"):
                break  # preview fixtures below may use sample names
            if pattern.search(line):
                errors.append(f"{file.relative_to(ROOT)}:{lineno}: English sentinel in data/logic: {line.strip()}")
    return errors


def check_no_file_io_in_composition():
    """Composables must not touch the disk on every recomposition (UI-5).

    Load snapshots with `SnapshotImage`, which lets Coil read the file off the main thread.
    """
    errors = []
    ui_dir = MAIN_DIR / "java" / "com" / "jestk" / "browser" / "ui"
    for file in ui_dir.rglob("*.kt"):
        if "viewmodel" in file.parts:
            continue
        for lineno, line in _code_lines(file):
            if re.search(r"\.exists\(\)", line) and not line.lstrip().startswith(("//", "*")):
                errors.append(f"{file.relative_to(ROOT)}:{lineno}: File.exists() in UI code; use SnapshotImage")
    return errors


def check_no_test_environment_detection():
    """Production code must not branch on being under test (UI-6)."""
    errors = []
    for file in (MAIN_DIR / "java").rglob("*.kt"):
        for lineno, line in _code_lines(file):
            if "org.robolectric" in line or '"robolectric"' in line:
                errors.append(f"{file.relative_to(ROOT)}:{lineno}: test-environment detection in production code")
    return errors


def check_english_only_comments():
    """Code comments are English-only (UI-8). Quoted page text the code matches is data."""
    errors = []
    cyrillic = re.compile(r"[\u0400-\u04FF]")
    allowlist = {"AdBlockerScript.kt"}  # documents the Russian consent-button labels it clicks
    for file in (MAIN_DIR / "java").rglob("*.kt"):
        if file.name in allowlist:
            continue
        for lineno, line in _code_lines(file):
            stripped = line.lstrip()
            comment = None
            if stripped.startswith(("//", "*", "/*")):
                comment = stripped
            elif "//" in line and '"' not in line.split("//", 1)[0][-1:]:
                comment = line.split("//", 1)[1]
            if comment and cyrillic.search(comment):
                errors.append(f"{file.relative_to(ROOT)}:{lineno}: non-English comment")
    return errors


def check_security_challenge_protection():
    """Verify security challenge domains are exempted from content scripts and client hints are pure."""
    errors = []
    ad_blocker_engine = MAIN_DIR / "java" / "com" / "jestk" / "browser" / "engine" / "adblock" / "AuthDomainAllowlist.kt"
    page_scripts = [
        MAIN_DIR / "assets" / "scripts" / "content_bootstrap.js",
        MAIN_DIR / "assets" / "scripts" / "autoconsent.js",
    ]
    ua_metadata_provider = MAIN_DIR / "java" / "com" / "jestk" / "browser" / "engine" / "UserAgentMetadataProvider.kt"

    if ad_blocker_engine.exists():
        content = ad_blocker_engine.read_text(encoding="utf-8")
        if "challenges.cloudflare.com" not in content or "cloudflare.com" not in content:
            errors.append(f"{ad_blocker_engine.relative_to(ROOT)}: AUTH_SECURITY_ALLOWLIST missing Cloudflare domains")

    for script in page_scripts:
        if not script.exists():
            errors.append(f"{script.relative_to(ROOT)} not found")
            continue
        content = script.read_text(encoding="utf-8")
        if "challenges.cloudflare.com" not in content or "cloudflarecaptcha.com" not in content:
            errors.append(f"{script.relative_to(ROOT)}: missing security challenge domain guards")

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
    - addJavascriptInterface only where a reviewed bridge lives; the content bridge is captured and
      deleted by assets/scripts/content_bootstrap.js before page scripts run.
    - JestkWebViewClient is built in one place (ui/views/WebViewClientBinder.kt).
    - Raw filter lists stay in tools/filterlists, out of the APK.
    """
    errors = []
    forbidden = {
        "window.matchMedia =": "replaces window.matchMedia",
        ".toString = function": "fakes a native toString",
        "window.__": "uses a window.__* global",
    }
    bridge_files = {"WebViewPool.kt", "CleanVideoPlayerOverlay.kt", "ContentBlockingSetup.kt"}
    for file in MAIN_DIR.rglob("*.kt"):
        content = file.read_text(encoding="utf-8")
        rel = file.relative_to(ROOT)
        for needle, why in forbidden.items():
            if needle in content:
                errors.append(f"{rel}: {why} ({needle!r})")
        if "addJavascriptInterface(" in content and file.name not in bridge_files:
            errors.append(f"{rel}: addJavascriptInterface outside the reviewed bridge files")
        # Site WebViews: a fixed bridge name is a window global any page can enumerate.
        if file.name in {"WebViewPool.kt", "ContentBlockingSetup.kt"} and re.search(r'addJavascriptInterface\([^)]*,\s*"', content):
            errors.append(f"{rel}: site WebView bridge registered under a fixed name (use a random, captured-and-deleted name)")
        if file.name not in {"JestkWebViewClient.kt", "WebViewClientBinder.kt"} and re.search(r"\bJestkWebViewClient\(", content):
            errors.append(f"{rel}: builds JestkWebViewClient outside WebViewClientBinder.kt")
    assets = MAIN_DIR / "assets"
    bootstrap = assets / "scripts" / "content_bootstrap.js"
    if bootstrap.exists() and "delete window[" not in bootstrap.read_text(encoding="utf-8"):
        errors.append(f"{bootstrap.relative_to(ROOT)}: must capture and delete the hidden bridge (delete window[...])")
    for file in assets.rglob("*.js"):
        content = file.read_text(encoding="utf-8")
        if "HoriAdBlockBridge" in content:
            errors.append(f"{file.relative_to(ROOT)}: HoriAdBlockBridge is gone; the bridge name is random and deleted")
        for needle, why in forbidden.items():
            if needle in content:
                errors.append(f"{file.relative_to(ROOT)}: {why} ({needle!r})")
    for file in assets.glob("*.txt"):
        if file.name not in {"adblock_domains.txt", "adblock_popup_hosts.txt"}:
            errors.append(f"{file.relative_to(ROOT)}: raw filter list in APK assets (keep it in tools/filterlists)")
    return errors


def check_fullscreen_view_attached_synchronously():
    """The onShowCustomView view must be attached inside the callback, not after recomposition.

    Chromium reports the page hidden until the fullscreen view is in a window; YouTube reloads and
    pauses the video on that visibilitychange (see ui/views/FullscreenViewHost.kt).
    """
    binder = MAIN_DIR / "java" / "com" / "jestk" / "browser" / "ui" / "views" / "WebViewClientBinder.kt"
    if not binder.exists():
        return [f"{binder.relative_to(ROOT)} not found"]
    content = binder.read_text(encoding="utf-8")
    block = content.split("onFullscreenCustomViewChanged = ", 1)[-1].split("onVideoPlayingStateChanged", 1)[0]
    if "fullscreenViewHost" not in block or ".attach(" not in block:
        return [
            f"{binder.relative_to(ROOT)}: onFullscreenCustomViewChanged must attach the view to "
            "FullscreenViewHost before dispatching the state event"
        ]
    return []


def check_single_browser_activity():
    """MainActivity must stay a single instance (launchMode singleTask).

    WebViewPool is process-wide and a WebView has one parent. With the default launch mode every
    link opened from another app (ACTION_VIEW) creates another MainActivity with its own
    ViewModel and WebView container; they steal WebViews from each other and the dock switches
    tabs while the screen keeps showing another tab's page. singleTask routes links to
    onNewIntent of the one instance.
    """
    manifest = MAIN_DIR / "AndroidManifest.xml"
    content = manifest.read_text(encoding="utf-8")
    match = re.search(r'<activity\b[^>]*android:name="\.ui\.MainActivity"[^>]*>', content, re.S)
    if not match or 'android:launchMode="singleTask"' not in match.group(0):
        return [f'{manifest.relative_to(ROOT)}: MainActivity must declare android:launchMode="singleTask"']
    return []


def check_refresh_spinner_only_on_pull():
    """Page loads must not drive the pull-to-refresh spinner (Chrome shows only a progress bar).

    The spinner belongs to the pull gesture; `JestkSwipeRefreshLayout.syncWithPageLoading` hides
    it after the reload that the pull started. Binding `isRefreshing` to `isLoading` puts a
    spinner over every navigation.
    """
    errors = []
    # Index 0 of a SwipeRefreshLayout is its spinner circle until the spinner first shows, so a
    # positional lookup of the tab container silently fails (tab switching then does nothing).
    positional = re.compile(r"swipeRefresh\.getChildAt\(")
    for file in MAIN_DIR.rglob("*.kt"):
        for number, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
            if positional.search(line):
                errors.append(
                    f"{file.relative_to(ROOT)}:{number}: find the tab container with "
                    "JestkSwipeRefreshLayout.tabsContainer, not by child index"
                )
    pattern = re.compile(r"isRefreshing\s*=\s*[\w.]*isLoading")
    for file in MAIN_DIR.rglob("*.kt"):
        for number, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
            if pattern.search(line):
                errors.append(
                    f"{file.relative_to(ROOT)}:{number}: isRefreshing is bound to page loading; "
                    "use JestkSwipeRefreshLayout.syncWithPageLoading"
                )
    return errors


TASKS_FILE = ROOT / "TASKS.md"
MAX_TASKS_LINES = 40


def check_tasks_file_size():
    """TASKS.md stays short enough to read at the start of every task (done items are deleted)."""
    if not TASKS_FILE.exists():
        return ["TASKS.md is missing"]
    lines = len(TASKS_FILE.read_text(encoding="utf-8").splitlines())
    if lines > MAX_TASKS_LINES:
        return [f"TASKS.md: {lines} lines, over {MAX_TASKS_LINES}. Delete done tasks; git log is the history."]
    return []


def check_dark_theme_resolved_once():
    """Verify UI code reads the resolved theme instead of comparing the raw setting.

    `themeMode == ThemeMode.DARK` is false in the default System mode even when the phone is
    dark, so such a screen stays light (PRO-18). Composables read `LocalIsDarkTheme.current`,
    which `JestkBrowserTheme` resolves from the setting and the system once.
    """
    errors = []
    pattern = re.compile(r"themeMode\s*==\s*ThemeMode\.DARK")
    for file in (ROOT / "app/src/main/java").rglob("*.kt"):
        for lineno, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
            if pattern.search(line):
                errors.append(
                    f"{file.relative_to(ROOT)}:{lineno}: compares themeMode with ThemeMode.DARK; "
                    "read LocalIsDarkTheme.current so the System theme mode is honoured."
                )
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


def check_secure_window_switch_debug_only():
    """The adb switch that drops FLAG_SECURE (PRO-82) must stay debug-only.

    Its code checks FLAG_DEBUGGABLE, and no manifest under src/main may declare the intent.
    """
    switch = MAIN_DIR / "java" / "com" / "jestk" / "browser" / "ui" / "DebugSecureWindowSwitch.kt"
    errors = []
    if "FLAG_DEBUGGABLE" not in switch.read_text(encoding="utf-8"):
        errors.append(f"{switch.relative_to(ROOT)}: the FLAG_SECURE switch must check FLAG_DEBUGGABLE")
    for manifest in MAIN_DIR.rglob("AndroidManifest.xml"):
        text = manifest.read_text(encoding="utf-8")
        if "prosto-debug" in text:
            errors.append(f"{manifest.relative_to(ROOT)}: prosto-debug intents belong in app/src/debug only")
    return errors


def check_no_local_home_paths():
    """The repo is public: no tracked file (binaries included) may carry a macOS home path.

    Use repo-relative paths, or derive the main checkout from `git rev-parse --git-common-dir`.
    Native libraries are built with --remap-path-prefix (tools/build_native.sh).
    """
    pattern = "/" + "Users/[^/[:space:]]+/"
    result = subprocess.run(
        ["git", "grep", "-l", "-E", pattern],
        cwd=ROOT, capture_output=True, text=True,
    )
    return [
        f"{path}: contains a local home path ({pattern}); use a repo-relative path"
        for path in result.stdout.splitlines()
    ]


def check_script_modes():
    """Every git-tracked tools/**/*.sh with a shebang must be mode 100755.

    Modes come from the index (`git ls-files -s`), so a missing `chmod +x` plus
    `git update-index --chmod=+x` shows up even when the working copy is executable.
    """
    result = subprocess.run(
        ["git", "ls-files", "-s", "--", "tools"],
        cwd=ROOT, capture_output=True, text=True,
    )
    errors = []
    for line in result.stdout.splitlines():
        # Format: "<mode> <object> <stage>\t<path>"
        meta, _, rel = line.partition("\t")
        mode = meta.split()[0] if meta else ""
        if not rel.endswith(".sh"):
            continue
        with open(ROOT / rel, "rb") as handle:
            first_line = handle.readline()
        if not first_line.startswith(b"#!"):
            continue
        if mode != "100755":
            errors.append(f"{rel}: has a shebang but index mode is {mode}; run chmod +x and git update-index --chmod=+x")
    return errors


def main():
    all_errors = []
    all_errors.extend(check_no_local_home_paths())
    all_errors.extend(check_script_modes())
    all_errors.extend(check_secure_window_switch_debug_only())
    all_errors.extend(check_no_blur())
    all_errors.extend(check_webkit_crash_resilience())
    all_errors.extend(check_domain_layer_purity())
    all_errors.extend(check_stable_favicon_paths())
    all_errors.extend(check_webview_client_no_favicon())
    all_errors.extend(check_no_continuous_effect_keys())
    all_errors.extend(check_localization_parity())
    all_errors.extend(check_no_hardcoded_ui_strings())
    all_errors.extend(check_no_english_sentinels())
    all_errors.extend(check_no_file_io_in_composition())
    all_errors.extend(check_no_test_environment_detection())
    all_errors.extend(check_english_only_comments())
    all_errors.extend(check_security_challenge_protection())
    all_errors.extend(check_injected_script_stealth())
    all_errors.extend(check_fullscreen_view_attached_synchronously())
    all_errors.extend(check_refresh_spinner_only_on_pull())
    all_errors.extend(check_single_browser_activity())
    all_errors.extend(check_file_sizes())
    all_errors.extend(check_tasks_file_size())
    all_errors.extend(check_dark_theme_resolved_once())
    all_errors.extend(check_time_ordered_queries_have_tiebreaker())

    if all_errors:
        print("=== Architectural Invariant Violations ===")
        for err in all_errors:
            print(f"  FAIL: {err}")
        return 1

    print(
        "All architectural invariants satisfied (No Blur, WebKit Crash Resilience, "
        "Domain Purity, Favicon Safety, Localization, Security Challenge Protection, Script Stealth, "
        "Fullscreen Sync Attach, Pull-Only Refresh Spinner, Single Browser Activity, No English Sentinels, No Composition File I/O, No Test Detection, "
        "English Comments, File Size Ratchet, Short TASKS.md, Resolved Dark Theme, Ordered Query Tie-Breaker, No Local Home Paths, Script Modes)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
