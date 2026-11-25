"""Compose and UI-code invariants: no blur, no continuous LaunchedEffect keys, no disk I/O in
composition, no test-environment branches, synchronous fullscreen attach, pull-only refresh
spinner and resolved dark theme."""

import re

from invariants_common import MAIN_DIR, ROOT, code_lines


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


def check_no_file_io_in_composition():
    """Composables must not touch the disk on every recomposition (UI-5).

    Load snapshots with `SnapshotImage`, which lets Coil read the file off the main thread.
    """
    errors = []
    ui_dir = MAIN_DIR / "java" / "com" / "jestk" / "browser" / "ui"
    for file in ui_dir.rglob("*.kt"):
        if "viewmodel" in file.parts:
            continue
        for lineno, line in code_lines(file):
            if re.search(r"\.exists\(\)", line) and not line.lstrip().startswith(("//", "*")):
                errors.append(f"{file.relative_to(ROOT)}:{lineno}: File.exists() in UI code; use SnapshotImage")
    return errors


def check_no_test_environment_detection():
    """Production code must not branch on being under test (UI-6)."""
    errors = []
    for file in (MAIN_DIR / "java").rglob("*.kt"):
        for lineno, line in code_lines(file):
            if "org.robolectric" in line or '"robolectric"' in line:
                errors.append(f"{file.relative_to(ROOT)}:{lineno}: test-environment detection in production code")
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
