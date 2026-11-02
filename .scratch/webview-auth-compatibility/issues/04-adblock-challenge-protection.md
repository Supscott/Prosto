# 04: AdBlocker Challenge & Verification Exclusions Resilience

**What to build:**
Ensure that security challenge endpoints, CAPTCHA frames (specifically Arkose Labs / FunCaptcha `arkoselabs.com`, `client-api.arkoselabs.com`, `arkose.com`, Cloudflare Turnstile `challenges.cloudflare.com`, `hcaptcha.com`, and Google `recaptcha.net`), and primary authentication challenge domains are never intercepted or blocked by the ad-blocker or tracking protection engines.

**Blocked by:** None (can start immediately).

**Status:** completed

- [x] Define an explicit allowlist in `AdBlockerEngine` for critical security verification and captcha domains:
  - `arkoselabs.com`, `client-api.arkoselabs.com`, `arkose.com`
  - `challenges.cloudflare.com`
  - `hcaptcha.com`
  - `recaptcha.net`
- [x] In `AdBlockerEngine.shouldBlock(parsedUrl)`: if the host matches an allowlisted verification domain, return `false` immediately before checking blocklists.
- [x] In `AdBlockerEngine.isTracker(parsedUrl)`: if the host matches an allowlisted verification domain, return `false` immediately so tracking protection does not intercept it.
- [x] Unit tests in `AdBlockerEngineTest` verify that requests to challenge endpoints (Arkose, Turnstile, hCaptcha, reCaptcha) pass through `shouldBlock` and `isTracker` without interception, even if matching wildcard filter rules exist.
