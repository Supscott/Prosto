# Specification: Task 4 — Fix Link Navigation Crash (`target="_blank"` / Popups)

## 1. Problem Statement
When navigating external links that open in a new tab/window (such as `<a target="_blank">`, `window.open()`, or links in YouTube video descriptions), the application crashes.
Previous changes excised `window.open` interception from `AdBlockerScript.kt`, allowing Chromium to invoke `WebChromeClient.onCreateWindow`. However, `WindowTransportHandler` immediately binds a newly created, detached `WebView` to `WebView.WebViewTransport` and calls `resultMsg.sendToTarget()` before the WebView is attached to the Activity window or view hierarchy. This causes native Chromium or runtime crashes when the popup WebContents attempts to attach to an unhosted view. Furthermore, profile mismatching in `WebViewPool`, lack of opener profile inheritance, and tag collisions cause the WebView to be recreated or destroyed mid-flight.

## 2. Requirements & Desired Behavior
1. **Crash-Free Popup / `target="_blank"` Navigation**:
   - Tapping any link opening in a new tab (`target="_blank"`, `window.open`, YouTube description links) must never crash the app.
   - The new tab must open cleanly in the user's active workspace and begin loading the target URL.
2. **Profile & Workspace Inheritance**:
   - When a popup is opened from an existing tab, the new tab must inherit the opener tab's workspace profile so cookies and session credentials are retained (vital for OAuth and external auth flows).
   - If no profile is explicitly defined, default gracefully to the active workspace profile.
3. **Safe Transport Binding & Window Attachment**:
   - `resultMsg.sendToTarget()` must be dispatched safely only when the destination WebView is ready to receive native WebContents, or the transport must be handled with appropriate error guards.
   - If `transport.webView` is unattached or `resultMsg` is invalid, degrade gracefully without throwing an uncaught exception.
4. **Loading State & Home Overlay Suppression**:
   - When a popup tab is initiated with a pending load (`isWebLoading = true`), the UI must not prematurely treat it as the Home Screen overlay merely because `tab.url` is empty while loading.
5. **Architectural Invariants**:
   - Pass `tools/check_invariants.py` (No Blur, WebKit Crash Resilience, Domain Purity, Favicon Safety, Localization, Security Challenge Protection).
   - All user-facing strings must be localized in `res/values/strings.xml` and `res/values-ru/strings.xml`.

## 3. Technical Approach
- Inspect and refine `WindowTransportHandler.kt` to ensure `WebViewTransport` is assigned and dispatched safely.
- In `JestkWebChromeClient.onCreateWindow`, pass opener tab details (tabId, profile) so `WebViewPool.getOrCreateWebView` receives the correct profile immediately.
- Ensure `WebViewPool` does not trigger a profile mismatch recreate/destroy cycle for newly opened popups.
- In `TabCoordinator.openPopupTab`, correctly configure the new tab with its workspace ID and loading state.
- Add comprehensive Robolectric tests in `JestkWebChromeClientTest.kt` and/or `TabCoordinatorTest.kt` simulating `onCreateWindow` with `WebViewTransport`.
