# Spec: Cut out window.open monkey-patching in AdBlockerScript

## Problem Statement
In `AdBlockerScript.kt`, a monkey-patch on `window.open` was injected as part of ad redirect protection. It checked if targetHost differed from currentHost, and returned `null` for non-search/non-auth domains. Because YouTube (m.youtube.com) uses `window.open(url, '_blank')` for links in the video description (such as creator social links, external references, sponsor links), this monkey-patch intercepted and swallowed those clicks, causing nothing to happen when tapped.

## Solution
Completely cut out the `window.open = patchedOpen` monkey-patching logic and its enclosing block from `AdBlockerScript.kt`:
1. Remove lines 630-664 in `AdBlockerScript.kt` where `redirectProtectionActive` checked `isRedirectProtectionEnabled` and replaced `window.open`.
2. Native `window.open` behavior is restored; popups continue to be handled through the native `WebChromeClient.onCreateWindow` and `JestkWebViewClient` seams.
3. Keep the WebView-level navigation protections (e.g. `JestkWebViewClient` redirect protection banner and EasyList ad blocking).
4. Update unit tests in `AdBlockerScriptTest.kt` to ensure script compilation and assertions pass.

## User Stories
1. As a browser user watching YouTube, when I tap on any link in the video description or comments, the link should open cleanly in a new tab or browser window without being silently dropped.
2. As a browser user on any site using `window.open`, legitimate popups and window openings should work without script-level interference.

## Implementation Decisions
- **File**: `app/src/main/java/com/jestk/browser/engine/AdBlockerScript.kt`
- Remove the `window.open` interception script block.
- Update `AdBlockerScriptTest.kt` (remove assertions expecting `origWindowOpen` or `patchedOpen`).
