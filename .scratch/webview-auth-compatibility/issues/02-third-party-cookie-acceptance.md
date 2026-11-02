# 02: Third-Party Cookie Acceptance for Cross-Origin Authentication

**What to build:**
Enable third-party cookie handling across all managed web views so that multi-origin authentication flows (such as `x.com` sharing cookies and security tokens with `twitter.com` and `api.twitter.com`) succeed without session dropouts or cookie rejection errors.

**Blocked by:** 01-user-agent-sanitization-and-storage-hardening.

**Status:** completed

- [x] Global cookie acceptance is enabled via `CookieManager.getInstance().setAcceptCookie(true)`.
- [x] Third-party cookie acceptance is enabled on primary `NestedWebView` instances via `CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)`.
- [x] Third-party cookie acceptance is enabled on fallback `NestedWebView` instances via `CookieManager.getInstance().setAcceptThirdPartyCookies(fallbackWebView, true)`.
- [x] Cookie configuration is applied during web view creation prior to loading target URLs.
- [x] Unit/Robolectric tests verify that created WebViews have third-party cookies enabled in `CookieManager`.
