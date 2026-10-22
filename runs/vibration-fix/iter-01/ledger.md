# Iteration 01 - vibration-fix

- **Date:** 2026-10-22T15:55:01+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Fix vibration feedback with predefined haptic effects, hardware feedback attributes, and complete action wiring

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Use `VibrationEffect.createPredefined` with hardware feedback `VibrationAttributes` and fallback to 35ms/60ms | Fix physical actuation and bypass OS touch setting silencing |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** `VibratorVibrationFeedback` using `VibrationAttributes.USAGE_HARDWARE_FEEDBACK` (API 31+) / `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION` (API 26-30), predefined waveforms (`EFFECT_TICK` / `EFFECT_CLICK`), resilient one-shot fallback (35ms / 60ms), and full wiring across all 7 agreed interaction points (dock drag long-press, dock swap, search carousel, search reorder, tab grid swipe close, pull-to-refresh, settings toggle confirmation).
- **Rejected:** Unspecified `VibrationAttributes` with 20ms one-shot duration (was swallowed by system touch haptics toggle and hardware motor inertia).
- **Lesson candidate:** `Vibrator.vibrate(VibrationEffect)` on Android 12+ defaults to `USAGE_TOUCH` / `USAGE_UNKNOWN` and is dropped if system touch-haptics is disabled.
- **Guardrail update:** Documented in [ADR 0002](docs/adr/0002-platform-vibration-feedback.md).
