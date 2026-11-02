# Specification: WebView Authentication & Login Compatibility

## Problem Statement

Users of the browser cannot authenticate on major modern web services that enforce strict bot-detection, cross-origin security token validation, or popup-based OAuth handshakes. Most notably, attempting to log into X (formerly Twitter) fails with the error *"Sorry, you are not allowed to log in at this time"*, and single sign-on (SSO) or OAuth authentication on services utilizing secondary domains or popup windows fails or hangs indefinitely.

From the user's perspective:
1. Entering valid credentials on `x.com` / `twitter.com` results in an immediate security refusal instead of successfully logging in.
2. Social login buttons and single sign-on flows that launch popup windows (`window.open`) either do nothing when tapped or freeze without opening the authorization dialog.
3. Services that split authentication across multiple related domains or subdomains lose session state, causing infinite login redirect loops or authentication rejections.

## Solution

1. **User-Agent Sanitization**:
   Sanitize the web engine's default User-Agent string to match standalone Google Chrome on Android. Strip embedded container identifiers (specifically `;\s*wv\b` and `Version/\d+(\.\d+)+\s*`), ensuring anti-bot and security firewalls recognize the browser as an authentic mobile browser rather than an embedded automation container.

2. **Cross-Origin & Third-Party Cookie Acceptance**:
   Explicitly configure the cookie manager to accept third-party cookies across all managed web views. This ensures cross-origin authentication handshakes (such as `x.com` exchanging authentication and CSRF tokens with `twitter.com` and `api.twitter.com`) retain session state and cookies across redirects.

3. **Complete HTML5 Storage & Window Configuration**:
   Enable HTML5 database storage alongside existing DOM storage. Enable window opening permissions (`javaScriptCanOpenWindowsAutomatically = true`). Enable multiple windows support in coordination with window creation handling.

4. **OAuth Popup / Window Transport Handling**:
   Implement popup window request and closure handling in the web engine's chrome client (`onCreateWindow` and `onCloseWindow`). When a web application requests a new window for authentication (via `window.open`), create a new tab, bind its `WebView` instance to `WebViewTransport`, and send the result message so `window.opener` remains intact. When authentication completes and the script calls `window.close()`, close the popup tab cleanly.

5. **Authentication & Security Challenge Resilience**:
   Ensure the ad-blocking and tracking protection engine does not intercept or break critical authentication and bot-challenge endpoints (specifically Arkose Labs / FunCaptcha verification frames, Cloudflare Turnstile, reCAPTCHA, and primary login endpoints), preventing silent authentication aborts.

## User Stories

1. As a user logging into X (Twitter) on `x.com`, I want to submit my username and password and successfully reach my timeline, so that I can use my account without security blocks.
2. As a user navigating modern websites with anti-bot protection (Cloudflare, Arkose Labs, Datadome), I want my browser's User-Agent to match standard Chrome for Android, so that I am not flagged as an embedded webview or automated scraper.
3. As a user signing into a service that redirects between distinct authentication domains (e.g., `x.com` and `twitter.com`), I want cookies and session tokens to persist across both origins, so that I do not encounter session dropouts or login loops.
4. As a user tapping a "Log in with Apple", "Log in with Google", or third-party OAuth button that opens a popup window, I want the authorization dialog to open in a tab, so that I can complete authentication.
5. As a user authenticating on websites that rely on HTML5 local databases and storage for offline tokens or session state, I want database storage enabled, so that the web app initializes without script errors.
6. As a user with active AdBlocker protection, I want anti-bot verification challenges (like interactive captchas) to render and complete normally, so that security checks do not fail silently in the background.
7. As a user opening multiple tabs that require authentication, I want newly pooled and fallback web views to consistently apply identical User-Agent and cookie configurations, so that login behavior is dependable across all tabs.
8. As a user completing a popup login flow, I want `window.opener` to remain connected so that the popup can post messages back to the parent tab, and I want the popup to close automatically when authentication finishes.
9. As a privacy-conscious user, I want AdBlocker and Tracking Protection to continue blocking actual advertisements and cross-site trackers while strictly preserving necessary authentication infrastructure.
10. As a user encountering a transient security cooldown on a website, I want the browser to not exacerbate the issue with non-standard browser signatures on subsequent retries.

## Implementation Decisions

- **User-Agent Filtering Strategy**:
  - The web engine will read the platform's default User-Agent string from the active web settings and transform it by stripping `;\s*wv\b` and `Version/\d+(\.\d+)+\s*`.
  - The transformed string will be set as `userAgentString` on both initial and fallback web engine instances during creation, without introducing arbitrary device spoofing or hardcoded version strings that would drift over time.

- **Cookie Management Lifecycle**:
  - The platform cookie manager will be configured on every web view creation to enable cookies globally and enable third-party cookies specifically for that view instance.
  - Cookie acceptance will be applied immediately during web view creation before any initial URL navigation occurs.

- **Storage & Engine Settings Expansion**:
  - Web engine settings will explicitly enable database storage and allow JavaScript to open windows automatically. Multiple windows support (`setSupportMultipleWindows(true)`) will be enabled in conjunction with the chrome client window lifecycle.

- **Window Opening & Tab Delegation**:
  - The web chrome client will handle window creation requests via `onCreateWindow(view, isDialog, isUserGesture, resultMsg)` and closures via `onCloseWindow(window)`.
  - When a popup is requested, a new tab is created via `tabCoordinator.openNewTab("")`, its `WebView` is bound to `(resultMsg?.obj as? WebView.WebViewTransport)?.webView = targetWebView`, and `resultMsg?.sendToTarget()` is dispatched. This preserves `window.opener` so postMessage callbacks function correctly.
  - When `onCloseWindow` fires, `tabCoordinator.closeTab(tabId)` is invoked to tear down the popup tab.

- **AdBlock Protection Safety**:
  - The ad-blocking and request interception engine will maintain an allowlist for known authentication challenge domains (`arkoselabs.com`, `client-api.arkoselabs.com`, `arkose.com`, `challenges.cloudflare.com`, `hcaptcha.com`, `recaptcha.net`), evaluated before host and tracker blocking logic.

## Testing Decisions

- **What makes a good test**:
  - Tests must verify observable configuration, public interface behavior, and contract compliance without relying on internal layout structure or brittle string matching.
- **Web Engine Settings Seam**:
  - Unit tests will verify that created web view instances have their User-Agent transformed correctly (absence of `; wv` and `Version/4.0`), have database settings enabled, and have third-party cookie handling configured.
- **WebChromeClient Window Seam**:
  - Unit tests for `JestkWebChromeClient` will verify that `onCreateWindow` binds the transport webview and returns true, and `onCloseWindow` notifies the window close callback.
- **AdBlocker Engine Seam**:
  - Unit tests will verify that authentication verification and captcha domains are not incorrectly flagged as blockable ad hosts or trackers.

## Out of Scope

- Implementing full Google OAuth / Play Integrity attestation for native Google Sign-In SDKs (Google accounts web login will benefit from sanitized UA and third-party cookies, but Google's server-side Play Services requirements are out of scope).
- Adding custom UI for per-site cookie permission dialogs or cookie whitelists.
- Modifying the visual tab strip or dock UI for popup tab animations.

## Further Notes

- Respects existing `WebViewPool` concurrency and LRU eviction limits.
- Adheres to Prosto Browser's zero-dependency, minimal-overhead philosophy.
