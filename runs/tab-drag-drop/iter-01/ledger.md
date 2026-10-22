# Iteration 01 - tab-drag-drop

- **Date:** 2026-10-22T16:29:17+00:00
- **Base ref:** `n/a`
- **Hypothesis:** edge overlap drag and drop with slot shift compensation and haptic scale bump

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Edge overlap trigger (`visualEnd > targetSlotStart + 50% minWidth`) with exact slot shift compensation (`visualStart - newSlotStart`) and scale bump | Active tab reorders immediately upon overlapping neighboring tab by 50% without needing to drag center to slot center, preserving finger tracking without teleport jumps |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** Edge overlap trigger (50% overlap of smaller item), exact slot shift compensation (`visualStart - newSlotStart`), hysteresis protection against reverse flicker, and tactile scale bump (`swapScaleBump`) in [`TabStripDragState.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/TabStripDragState.kt) and [`TabStripRow.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/TabStripRow.kt).
- **Rejected:** Old center-to-center slot threshold (`(width1 + width2)/2 + spacing`) which forced active tab to travel excessively far and jump by subtracting symmetric center distance.
- **Lesson candidate:** Asymmetric item drag-and-drop requires slot shift compensation by actual new slot origin (`visualStart - newSlotStart`) rather than center-to-center distance, plus directional hysteresis to avoid oscillation when small items cross large ones.
- **Guardrail update:** Enforced via comprehensive unit test suite in [`TabStripDragSpringTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/navigation/TabStripDragSpringTest.kt).
