# 01: User-Agent Sanitization & Storage Engine Hardening

**What to build:**
When WebViews are instantiated in the web engine (both primary and fallback instances), sanitize their default User-Agent string to conform to standalone Google Chrome on Android by stripping embedded container indicators (specifically `;\s*wv\b` and `Version/\d+(\.\d+)+\s*`). Additionally, enable HTML5 database storage (`databaseEnabled = true`) and allow JavaScript to open windows (`javaScriptCanOpenWindowsAutomatically = true`). (Note: `setSupportMultipleWindows(true)` is deferred to Ticket 03 to ensure multi-window handling is fully wired without dropping requests).

**Blocked by:** None (can start immediately).

**Status:** completed

- [x] Create a pure `UserAgentSanitizer.sanitize(userAgent: String?): String` utility with regex-based removal of `;\s*wv\b` and `Version/\d+(\.\d+)+\s*`.
- [x] Primary `NestedWebView` in `WebViewPool.getOrCreateWebView` sets sanitized `userAgentString`.
- [x] Fallback `NestedWebView` in `WebViewPool.getOrCreateWebView` sets sanitized `userAgentString`.
- [x] Both primary and fallback WebViews configure `databaseEnabled = true` and `javaScriptCanOpenWindowsAutomatically = true`.
- [x] Comprehensive unit tests verify `UserAgentSanitizer` for standard Chrome, various Android WebView signatures, custom strings, and edge cases.
- [x] Unit tests verify that newly created WebViews in `WebViewPool` receive sanitized UA and storage settings.
