# Iteration 03 - external-app-routing

- **Date:** 2026-10-20T09:28:54+00:00
- **Base ref:** `main@0cde85e`
- **Hypothesis:** Adopt two-tier suppression (explicit dismiss vs timeout) so that auto-discharged hints don't poison explicit user gestures; pass user-gesture flag through the whole pipeline

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Reproduce both reports with a JVM end-to-end test (`ExternalAppFlowTest`) that wires the real `BrowserViewModel` + `JestkWebViewClient` (no emulator): repeat external launch of an already-open URL after a dismissed pill; subframe `tg://` request; `intent://` with `browser_fallback_url` | All three assertions go red on unmodified `main`, proving the pipeline defects and giving a fast regression loop (3 failing tests in ~25 s) |
| B | Treat non-web subframe navigations and app-initiated `loadUrl()` as external navigations (`handleExternalNavigation`), instead of returning early on `!isForMainFrame` and letting only main-frame clicks reach the resolver | A site's "Open in app" control works whether it is an anchor, a hidden iframe, or a script-initiated navigation |
| C | Resolve `intent://` with relaxed filters (BROWSABLE, then DEFAULT, then flags 0), offer an installed target package instead of the Play Store, and load `S.browser_fallback_url` when the app is absent | No more silent no-ops for installed apps whose filters omit categories, and the site's own fallback page replaces a failed `intent://` navigation |
| D | Keep offers for background tabs (`pendingExternalAppPrompts`) and clear the per-tab dismissed-domain set whenever `openNewTab` starts a new navigation | The pill is not lost when the tab is not active yet, and reusing a tab for an identical URL no longer inherits an old dismissal |
| E | Pass `userInitiated` (`hasGesture()`) through the pipeline; suppress only passive offers on timeout; let gesture-initiated offers bypass timeout suppression | The pill reappears on the next deliberate tap even after the hint timed out, matching Chrome-like UX |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> 533 tests, 0 failures (66 suites) |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 (11/12 active lessons) |
| lint | PASS | `./gradlew :app:lintDebug` -> exit 0 |

## Device Validation

Real-device testing on `ZY22LQKJ7Z` (Android 14, `com.jestk.browser.debug`) confirmed the fix:

**Bug 1 — t.me "START BOT" button:**
- Page loads → passive `tg:resolve` attempt (gesture=false) offers Telegram, pill appears.
- 5 s timeout records `timedOut=[tg]`.
- User taps "START BOT" → `shouldOverride` with `gesture=true` → offer resolved → **published** despite `timedOut=[tg]`.
- Tapping "Open" launched Telegram (`START u0 {act=android.intent.action.VIEW dat=tg: flg=0x10000000 cmp=org.telegram.messenger/org.telegram.ui.LaunchActivity}`).

**Bug 2 — Instagram "Open in app" button:**
- Page loads → passive offer for instagram.com, times out → `timedOut=[instagram.com]`.
- User taps "Open Instagram" button (`intent://applink.instagram.com/...#Intent;...`) → `shouldOverride` with `gesture=true` → resolver returns Offer for `com.instagram.android` → **published** despite `timedOut=[instagram.com]`.
- Tapping "Open" launches Instagram.

Both symptoms fixed on a real device with logs confirming the two-tier suppression works.

## Verdict

- **Adopted:** Probes B, C, D, E. External-app resolution now has one entry point (`JestkWebViewClient.handleExternalNavigation`) used by main-frame clicks, subframe custom schemes, the deprecated callback, and app-initiated `LoadUrl` commands; `ExternalAppResolution.BrowserFallback` loads the site-declared fallback page; `intent://` resolution degrades through stricter-to-looser package queries and offers an installed target app instead of the Play Store; `TabCoordinator` queues offers for non-active tabs and resets per-tab suppression on every new external navigation; user-initiated offers bypass timeout suppression while still respecting explicit dismissal.
- **Rejected:** A curated scheme→package map (unproven need); enabling `setSupportMultipleWindows` (the reported "nothing happens" was explained by subframe/fallback paths, not missing multi-window; multi-window is a separate capability).
- **Lesson candidate:** `LESSONS.md` #12 — auto-dismissed transient hints must not become permanent suppression (separate suppression tiers for explicit dismissal vs timeout; pass `userInitiated` flag).
- **Guardrail update:** None needed. `ExternalAppFlowTest` now locks the user-visible pipeline (real ViewModel + real client) so a future refactor of either side breaks a test instead of silently disabling the pill. Also added `JestkWebViewClientTest` checks for `userInitiated` flag propagation from `hasGesture()` and from passive paths.