# Iteration 02 - favicon-flicker

- **Date:** 2026-10-20T17:03:04+00:00
- **Base ref:** `n/a`
- **Hypothesis:** stable favicon path, atomic writes and placeholder-only fallback remove the dock favicon flicker

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Rebuilt and installed the fixed APK on the connected device, then recorded the dock while the page kept reporting its icon | The favicon slot must stay at full brightness for every frame |
| B | Recorded a scroll sequence that hides and re-shows the dock | The icon must be present in the first frame the dock is visible again |
| C | Inspected the on-device favicon directory and the `tabs` row after the run | One stable file per tab, no timestamped leftovers |

## Evidence

- Idle recording of the dock on `minimax.io`: 475 consecutive frames, favicon region brightness
  min 58.7 / max 58.8 - no drop, no placeholder frame. Before the fix the same region dropped out
  roughly every 0.26 s.
- Scroll recording: the dock re-enters with the favicon already painted; the frame where the dock
  becomes visible again is at full icon brightness, and no frame shows the title initial.
- On disk: `favicon_<tabId>.png` (single stable file), the legacy `favicon_<tabId>_<ts>.png` file is
  removed, and the database row points at the stable path.
- `handleUpdateFavicon` now fires once per real icon change instead of ~4 times per second.

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** the whole change set - stable favicon path with atomic, content-aware writes, the
  shared `SiteFavicon` placeholder contract, the guard against redundant favicon writes, the
  `SiteFavicon` reuse in the dock pill, tab strip and tab grid, and the two new invariant checks.
- **Rejected:** rewriting the dock hide/show mechanism to keep the dock composed - the reload it
  caused was a single frame and disappeared once the favicon path stopped changing.
- **Lesson candidate:** timestamped favicon file names make the dock icon flicker -> `LESSONS.md` #12.
- **Guardrail update:** `tools/check_invariants.py` -> `check_stable_favicon_paths` (no timestamp in
  favicon file names) and `check_no_continuous_effect_keys`; `DESIGN.md` documents the favicon
  contract for the dock.
