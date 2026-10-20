# Iteration 01 - external-app-routing

- **Date:** 2026-10-20T07:37:03+00:00
- **Base ref:** `main@0cde85e`
- **Hypothesis:** Route every external-app entry point (subframe custom schemes, programmatic loads) through one resolver, honour browser_fallback_url, and reset per-tab prompt suppression on new external navigations

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Reproduce both reports with a JVM end-to-end test (`ExternalAppFlowTest`) that wires the real `BrowserViewModel` + `JestkWebViewClient` (no emulator): repeat external launch of an already-open URL after a dismissed pill; subframe `tg://` request; `intent://` with `browser_fallback_url` | All three assertions go red on unmodified `main`, proving the pipeline defects and giving a fast regression loop (3 failing tests in ~25 s) |
| B | Treat non-web subframe navigations and app-initiated `loadUrl()` as external navigations (`handleExternalNavigation`), instead of returning early on `!isForMainFrame` and letting only main-frame clicks reach the resolver | A site's "Open in app" control works whether it is an anchor, a hidden iframe, or a script-initiated navigation |
| C | Resolve `intent://` with relaxed filters (BROWSABLE, then DEFAULT, then flags 0), offer an installed target package instead of the Play Store, and load `S.browser_fallback_url` when the app is absent | No more silent no-ops for installed apps whose filters omit categories, and the site's own fallback page replaces a failed `intent://` navigation |
| D | Keep offers for background tabs (`pendingExternalAppPrompts`) and clear the per-tab dismissed-domain set whenever `openNewTab` starts a new navigation | The pill is not lost when the tab is not active yet, and reusing a tab for an identical URL no longer inherits an old dismissal |
| E | Let the 5 s timeout re-offer the app after it expires | Rejected: it makes the pill reappear on every same-domain SPA route change; per-tab suppression is the intended anti-spam behaviour |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> 530 tests, 0 failures (66 suites) |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 (11/12 active lessons) |
| lint | PASS | `./gradlew :app:lintDebug` -> exit 0 |

## Verdict

- **Adopted:** Probes B, C, D. External-app resolution now has one entry point (`JestkWebViewClient.handleExternalNavigation`) used by main-frame clicks, subframe custom schemes, the deprecated callback, and app-initiated `LoadUrl` commands; `ExternalAppResolution.BrowserFallback` loads the site-declared fallback page; `intent://` resolution degrades through stricter-to-looser package queries and offers an installed target app instead of the Play Store; `TabCoordinator` queues offers for non-active tabs and resets per-tab suppression on every new external navigation.
- **Rejected:** Probe E (timeout re-offer). A timed-out hint that reappears on every SPA route would be noise; the real repeat-visit bug was suppression leaking through `TabRepositoryImpl.createTab` URL dedup, fixed by clearing the set in `openNewTab` instead. Also rejected: a curated scheme→package map (unproven need) and enabling `setSupportMultipleWindows` (the reported "nothing happens" is explained by the subframe/fallback paths; multi-window is a separate capability).
- **Lesson candidate:** `LESSONS.md` #11 — external-app handoffs bypass `shouldOverrideUrlLoading` outside main-frame link clicks (subframes, programmatic loads, no `onPageStarted` for custom schemes).
- **Guardrail update:** None needed. `ExternalAppFlowTest` now locks the user-visible pipeline (real ViewModel + real client) so a future refactor of either side breaks a test instead of silently disabling the pill.
