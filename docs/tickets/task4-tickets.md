# Tickets: Task 4 — Fix Link Navigation Crash (`target="_blank"` / Popups)

## Ticket 4.1: Profile Inheritance & Safe Transport Handling in WindowTransportHandler
- **File**: `app/src/main/java/com/jestk/browser/engine/WindowTransportHandler.kt`, `app/src/main/java/com/jestk/browser/ui/views/WebViewContainer.kt`, `app/src/main/java/com/jestk/browser/ui/JestkApp.kt`
- **Task**:
  - Update `WindowTransportHandler.handleCreateWindow` to accept and pass the opener's `profileName` into `webViewPool.getOrCreateWebView`.
  - Wrap `resultMsg.sendToTarget()` in exception guards to prevent unhandled Chromium runtime crashes.
  - Ensure the newly created WebView tag and profile match what `WebViewContainer` expects, preventing mid-flight `removeWebView()` or `destroy()` calls.

## Ticket 4.2: Tab State Coordination & Unit Test Verification
- **File**: `app/src/main/java/com/jestk/browser/ui/viewmodel/TabCoordinator.kt`, `app/src/test/java/com/jestk/browser/engine/JestkWebChromeClientTest.kt`
- **Task**:
  - In `TabCoordinator.openPopupTab`, ensure the new tab is properly bound to the active workspace, marked with `isHome = false`, and initialized with active loading state.
  - Add unit tests verifying `handleCreateWindow` with `profileName` inheritance, null checks, and error resilience.
  - Verify `python3 tools/check_invariants.py` and `rtk tools/verify.sh` pass cleanly.
