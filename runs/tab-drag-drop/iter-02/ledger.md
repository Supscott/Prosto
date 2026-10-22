# Iteration 02 - tab-drag-drop

- **Date:** 2026-10-22T16:39:35+00:00
- **Base ref:** `n/a`
- **Hypothesis:** clean code-review findings: dead code removal, layout metrics, proper hysteresis boundary, cancelled bump jobs

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Removed dead `computeSlotDistance`, introduced `TabStripLayoutMetrics` data holder, tied hysteresis margin to target trigger boundary (`targetTriggerX +/- hysteresis`), and managed `swapBumpJob` lifecycle | Zero dead code, proper density-scaled hysteresis on actual boundary conditions without premature air-swaps, clean coroutine hygiene |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** Removal of dead `computeSlotDistance`, `TabStripLayoutMetrics` data holder, boundary-anchored hysteresis check (`targetTriggerX +/- hysteresis`), tracked `swapBumpJob` with cancellation, and extracted `effectiveScale` in [`TabStripRow.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/TabStripRow.kt) and [`TabStripDragState.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/TabStripDragState.kt).
- **Rejected:** Unmanaged fire-and-forget bump animation coroutines and naive mid-air hysteresis check that bypassed slot trigger boundaries.
- **Lesson candidate:** Hysteresis in drag-and-drop must adjust the target boundary (`triggerX +/- margin`) rather than replacing it with a delta check, and animation jobs should always be tracked and cancelled before re-triggering.
- **Guardrail update:** Enforced by unit tests in [`TabStripDragSpringTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/navigation/TabStripDragSpringTest.kt).
