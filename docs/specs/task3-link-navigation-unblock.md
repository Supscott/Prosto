# Spec: Unblock Legitimate Link Navigation (Ad Redirect Protection Precision)

## Problem Statement
In recent commits, ad redirect and popup protection was introduced in `JestkWebViewClient.kt` via `isBlockedAdNavigation`. However, the implementation checks `if (!sameSite && !isSearch && !isAuth)` on all main-frame navigations, and unconditionally blocks the navigation. As a result, when a user deliberately taps any legitimate link leading to another website (e.g. from GitHub to an external documentation site, from a blog or Wikipedia to an external reference), the browser blocks the navigation and shows a "Blocked Ad Redirect" banner. This completely breaks standard web browsing.

## Solution
Scope redirect protection so that it only blocks unsolicited navigations, while allowing all deliberate user navigations:
1. When `request.hasGesture()` is `true` (user initiated tap on a link), the navigation must NOT be blocked by the cross-domain redirect heuristic.
2. Only block automatic, script-initiated cross-domain redirects where `!request.hasGesture()`, OR when the target URL matches known ad/malware domain blocklists (`AdBlockerEngine.shouldBlock(url)`).
3. If a pop-up window or background redirect occurs without a user gesture, the protection prompt continues to offer protection.
4. Ensure `shouldOverrideUrlLoading` allows the WebView to load legitimate cross-domain links smoothly.

## User Stories
1. As a browser user, when I tap a link pointing to an external domain, I want the browser to navigate to that page immediately without showing a blocked redirect prompt.
2. As a browser user, when a website tries to automatically redirect me to an ad network or unwanted domain without my interaction, I want the browser to intercept the redirect and protect me.
3. As a browser user, when navigating to known advertising/tracking URLs, I want the ad blocker to intercept them regardless of gesture.
4. As a browser user, I want external app handoffs (e.g. Telegram, YouTube, custom schemes) to continue working as expected when tapping links.

## Implementation Decisions
- **File**: `app/src/main/java/com/jestk/browser/engine/JestkWebViewClient.kt`
- **Method**: `isBlockedAdNavigation(url: String, view: WebView?, hasGesture: Boolean)`
  - Direct blocklist match: `if (adBlockActive && AdBlockerEngine.shouldBlock(url)) return true`
  - Heuristic cross-domain redirect check: Only execute if `!hasGesture` (or if it's an unsolicited opener redirect). If the user tapped the link (`hasGesture == true`), bypass the cross-domain heuristic check and allow the navigation.
  - In `shouldOverrideUrlLoading(view, request)`: pass `request.hasGesture()` into `isBlockedAdNavigation(url, view, request.hasGesture())`.
- **Tests**: Add unit tests in `JestkWebViewClientTest.kt` verifying that:
  - User-initiated cross-domain link (`hasGesture = true`) is allowed (`false` from `shouldOverrideUrlLoading`).
  - Automatic cross-domain redirect (`hasGesture = false`) is intercepted.
  - Ad blocker blocklist URLs are blocked regardless of gesture.
