# Iteration 02 - tabs-vibration

- **Date:** 2026-10-21T19:27:35+00:00
- **Base ref:** `n/a`
- **Hypothesis:** tabs grid 3 columns; platform vibrator feedback gated by a persisted settings toggle

## Probes

| Probe | Change tried | Expected effect |
| --- | --- | --- |
| A | Enable/disable is read as a predicate on every pulse (`{ viewModel.uiState.value.settings.isVibrationEnabled }`) instead of a cached flag synced from a `SideEffect` | the toggle takes effect immediately, without waiting for an unrelated recomposition |

## Telemetry

| Check | Status | Detail |
| --- | --- | --- |
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 (576 tests, 0 failures, 0 skipped) |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

Additional (not part of the gate):

| Check | Status | Detail |
| --- | --- | --- |
| `:app:lintDebug` | PASS | BUILD SUCCESSFUL |

## Verdict

- **Adopted:** the predicate form of the enable check. Iteration 01 cached the flag on the
  feedback instance and synced it from a `SideEffect`; the effect only runs when its
  composition recomposes, and a composition that reads the preference only *inside* the
  effect is never invalidated by it, so the toggle would have stayed stale until an
  unrelated state change forced a recomposition. Reading the preference at pulse time
  removes the dependency on recomposition entirely.
- **Rejected:** caching the flag on the instance (stale, see above); a second
  `collectAsStateWithLifecycle()` in `MainActivity` (unnecessary once the preference is
  read from the state flow's current value).
- **Lesson candidate:** a `SideEffect` that reads state *inside* the effect body creates no
  recomposition dependency - the read must happen during composition. The active lesson
  budget is full (12/12), so this stays in `docs/adr/0002-platform-vibration-feedback.md`
  until an older entry is graduated.
- **Guardrail update:** none.
