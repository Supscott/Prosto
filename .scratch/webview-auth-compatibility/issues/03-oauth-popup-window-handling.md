# 03: OAuth Popup & Multi-Window Request Handling

**What to build:**
Enable multi-window support (`setSupportMultipleWindows(true)`) in the web engine, and implement popup window request and closure handling in `JestkWebChromeClient` (`onCreateWindow` and `onCloseWindow`). When a site executes `window.open` for an OAuth login dialog or secondary window, allocate a new tab via `tabCoordinator.openNewTab("")`, bind its WebView instance to `(resultMsg.obj as WebViewTransport).webView`, and dispatch `resultMsg.sendToTarget()`. This preserves `window.opener` so OAuth `window.opener.postMessage(...)` callbacks work. When the popup calls `window.close()`, invoke `onCloseWindow` to close the popup tab.

**Blocked by:** 01-user-agent-sanitization-and-storage-hardening.

**Status:** completed

- [x] Enable `setSupportMultipleWindows(true)` on primary and fallback WebViews in `WebViewPool`.
- [x] Add `onCreateWindowRequest: ((view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?) -> Boolean)?` and `onCloseWindowRequest: ((tabId: String) -> Unit)?` callbacks to `JestkWebChromeClient`.
- [x] Override `onCreateWindow(view, isDialog, isUserGesture, resultMsg)` in `JestkWebChromeClient` to invoke `onCreateWindowRequest` and return true when handled.
- [x] Override `onCloseWindow(window)` in `JestkWebChromeClient` to invoke `onCloseWindowRequest(tabId)`.
- [x] Wire the callbacks in `WebViewContainer` and `JestkApp`:
  - When `onCreateWindowRequest` fires: create a new tab in `tabCoordinator`, obtain its WebView, attach it to `WebViewTransport`, and send `resultMsg`.
  - When `onCloseWindowRequest` fires: invoke `tabCoordinator.closeTab(tabId)`.
- [x] Unit tests in `JestkWebChromeClientTest` and `TabCoordinatorTest` verify `onCreateWindow` and `onCloseWindow` callback dispatch and transport binding.
