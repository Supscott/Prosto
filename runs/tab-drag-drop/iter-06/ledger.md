# Iteration 06 - tab-drag-drop

- **Date:** 2026-10-22T17:32:14+00:00
- **Base ref:** `n/a`
- **Hypothesis:** systemGestureExclusion and safe auto-scroll left edge drag

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Excluded dock capsule from system back gestures via `Modifier.systemGestureExclusion()`, eliminated false drag cancellation on `visibleItem == null` in auto-scroll loop, and removed spurious `isConsumed` check on `PointerEventPass.Initial` | Dragging near or at the left edge of the dock no longer triggers the OS back gesture and no longer resets/cancels the drag gesture |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** `systemGestureExclusion` on dock row in [`BottomBar.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/BottomBar.kt), auto-scroll loop skip-and-continue on `visibleItem == null`, clean pointer lifecycle without child consumption interference.
- **Rejected:** Background auto-scroll loops triggering `dragState.onDragCancel()`, and checking `isConsumed` on `PointerEventPass.Initial` which conflicts with clickable child buttons.
- **Lesson candidate:** Bottom navigation bars near screen edges must apply `Modifier.systemGestureExclusion()` to prevent Android's edge navigation back gesture from intercepting horizontal drag-and-drop gestures near the screen bezel.
- **Guardrail update:** All 21 drag-and-drop spring unit tests pass; verified visual fidelity.
