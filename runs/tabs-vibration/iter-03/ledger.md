# Iteration 03 - tabs-vibration

- **Date:** 2026-10-21T19:30:27+00:00
- **Base ref:** `n/a`
- **Hypothesis:** final: 3-column tab grid + platform vibrator feedback with a persisted settings toggle

## Probes

| Probe | Change tried | Expected effect |
| --- | --- | --- |
| A | Rename the remaining "haptic" identifiers (`haptic` -> `vibration`, `onSwapHaptic` -> `onSwapVibration`, `lastHapticSlot` -> `lastVibrationSlot`) in the carousel, reorder list, tab strip drag state and their tests | the vocabulary matches the new mechanism; no behavioural change |
| B | Re-run the gate, lint and the preview render | everything stays green and the two rendered screens still look right |

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
| preview render | PASS | `TabOverviewGridLightPreview` shows 3 columns; `SettingsScreenLightPreview` shows the *Vibration* row under *INTERACTION* |

## Verdict

- **Adopted:** the vocabulary cleanup on top of iterations 01 and 02.
- **Rejected:** nothing new. The pull-to-refresh pulse in `engine/NestedWebView.kt` still uses
  the View-level `performHapticFeedback(KEYBOARD_TAP)`; it is a separate mechanism outside the
  app's vibration switch, and pulling it into the switch would require threading the setting
  into the WebView setup.
- **Lesson candidate:** none new; the candidates stay in `docs/adr/0002-platform-vibration-feedback.md`
  because the active lesson budget is full (12/12).
- **Guardrail update:** none.
