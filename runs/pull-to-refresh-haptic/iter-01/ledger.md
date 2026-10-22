# Iteration 01 - pull-to-refresh-haptic

- **Date:** 2026-10-22T16:20:51+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Trigger pull to refresh vibration only when reaching the true refresh trigger distance in JestkSwipeRefreshLayout

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Move pull-to-refresh haptic callback from `NestedWebView` (which only tracked 40dp slop to activate nested scrolling) to `JestkSwipeRefreshLayout` (which tracks `totalUnconsumed >= progressViewEndOffset`) | Trigger vibration at the exact refresh threshold where releasing reloads page |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** `JestkSwipeRefreshLayout.onRefreshTriggerReached` observing `currentUnconsumed >= progressViewEndOffset` to pulse tactile feedback exactly when the pull threshold is crossed.
- **Rejected:** Triggering haptic inside `NestedWebView` at `accumulatedPullDistance >= pullThresholdPx` (which prematurely vibrated at initial 40dp slop before the spinner even moved).
- **Lesson candidate:** Pull-to-refresh haptics must not be bound to child scroll activation slop (which fires at gesture start), but to the parent `SwipeRefreshLayout` refresh trigger distance.
- **Guardrail update:** Documented in [ADR 0002](docs/adr/0002-platform-vibration-feedback.md).
