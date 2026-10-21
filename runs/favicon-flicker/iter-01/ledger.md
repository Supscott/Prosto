# Iteration 01 - favicon-flicker

- **Date:** 2026-10-20T16:41:00+00:00
- **Base ref:** `n/a`
- **Hypothesis:** stable favicon path plus placeholder-only fallback removes dock favicon flicker

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Instrumented `ActiveTabPill` / `TabCoordinator` with `FaviconDebug` logs and captured `screenrecord` of the dock on the connected device | Reveal what changes per frame and how often the favicon URL is republished |
| B | Read the on-device SQLite `tabs` row and the `files/favicons` directory while the icon flickered | Confirm whether the persisted path changes or only the in-memory one |
| C | Decompiled `coil.key.FileKeyer` from `coil-base-2.7.0.aar` | Establish how the image loader keys its cache for `File` models |

## Evidence

- `handleUpdateFavicon` fired every ~250 ms on `minimax.io` with a **different** file name each time
  (`favicon_<tabId>_1792514655080.png`, `..._1792514655322.png`, ...): the page reports its icon several
  times per second.
- Frame analysis of `runs`/device capture: the favicon slot dropped to background brightness in
  12 of 407 frames over 6.8 s (~0.26 s period) while the neighbouring title region stayed constant.
- `coil.key.FileKeyer.key()` returns `"<path>:<lastModified>"`, so a new file name (or a rewritten
  file) invalidates the loader cache entry.
- The previous file was deleted on every save, so the icon the UI still pointed at could no longer be
  loaded: Coil reported an error and the pill fell back to the title initial - the "letter" flicker.

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** stable per-tab favicon path (`favicon_<tabId>.png`), atomic replace, skip identical
  writes; `SiteFavicon` placeholder shown only when no icon is available; redundant favicon state
  and database writes skipped.
- **Rejected:** keeping the timestamped file name and only damping the UI - the path itself is the
  contract every consumer binds to, so the churn stays.
- **Lesson candidate:** timestamped favicon file names make the dock icon flicker -> `LESSONS.md` #12.
- **Guardrail update:** `tools/check_invariants.py` gained `check_stable_favicon_paths` and
  `check_no_continuous_effect_keys`.
