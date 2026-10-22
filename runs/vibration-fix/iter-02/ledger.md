# Iteration 02 - vibration-fix

- **Date:** 2026-10-22T16:08:24+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Fix vibration feedback with API 33 gate, AudioAttributes fallback, clean pull-to-refresh tests, and robust predefined handling

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | API 33 gate for `VibrationAttributes` with API 26-32 `AudioAttributes` and `Throwable` catch | Prevent `NoSuchMethodError` on Android 12 (API 31/32) and ensure clean fallback |
| B | Move pull-to-refresh gesture test to `PullToRefreshGestureTest.kt` and simplify Elvis branching | Resolve divergent test change and eliminate redundant code |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** API 33 gate for `VibrationAttributes`, API 26-32 `AudioAttributes` fallback, `Throwable` handling for runtime crashes, idiomatic Elvis operator in `NestedWebView`, dedicated test in `PullToRefreshGestureTest.kt`.
- **Rejected:** Setting `VibrationAttributes` directly on API 31/32 (causes `NoSuchMethodError` on `vibrator.vibrate(VibrationEffect, VibrationAttributes)`).
- **Lesson candidate:** `Vibrator.vibrate(VibrationEffect, VibrationAttributes)` is API 33+ only despite `VibrationAttributes` existing in API 31.
- **Guardrail update:** Documented in [ADR 0002](docs/adr/0002-platform-vibration-feedback.md).
