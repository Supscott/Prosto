"""WebView, favicon, security-challenge and injected-script invariants (engine and browser shell)."""

import re

from invariants_common import MAIN_DIR, ROOT

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


THIRD_PARTY_FAVICON_SERVICES = (
    "s2/favicons",
    "gstatic.com/faviconV2",
    "icons.duckduckgo.com",
    "favicon.yandex.net",
    "favicon.im",
)
# Recognises the URLs older versions stored, so they can be mapped to the site's own icon (PRO-184).
FAVICON_SERVICE_ALLOWED = {"SiteIcon.kt"}


def check_no_third_party_favicon_service():
    """Verify no source under app/src/main names a third-party favicon service.

    A favicon service learns every domain it is asked about, so site icons come from the site itself
    (PRO-184). Only the legacy-URL mapper may name Google's, to recognise rows stored by old versions.
    """
    errors = []
    for path in sorted((MAIN_DIR / "java").rglob("*.kt")):
        if path.name in FAVICON_SERVICE_ALLOWED:
            continue
        content = path.read_text(encoding="utf-8")
        for service in THIRD_PARTY_FAVICON_SERVICES:
            if service in content:
                errors.append(
                    f"{path.relative_to(ROOT)}: must not name the favicon service '{service}'; "
                    "load the icon from the site itself (SiteIcon)"
                )
    return errors


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
