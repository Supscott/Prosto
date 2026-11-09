# Tickets: Task 3 - Unblock Legitimate Link Navigation

## Ticket 3.1: Gesture-Aware Redirect Protection in JestkWebViewClient
- **Title**: Scope cross-domain ad redirect check to non-gesture navigations
- **Blocked by**: None
- **What it delivers**:
  - Update `isBlockedAdNavigation(url, view, hasGesture)` in `JestkWebViewClient.kt`.
  - Legitimate user clicks (`hasGesture == true`) bypass the cross-domain redirect heuristic.
  - Known ad blocklist URLs remain blocked regardless of gesture.
  - Scripted auto-redirects without gestures (`hasGesture == false`) remain intercepted.
- **Verification**: TDD unit tests in `JestkWebViewClientTest.kt` covering both user gestures and non-gesture redirects.
