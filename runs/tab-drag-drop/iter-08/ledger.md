# Iteration 08 - tab-drag-drop

- **Date:** 2026-10-24T06:17:13+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Prevent left-edge drag clamping from reversing motion after reorder

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Added a Robolectric Compose drag that starts with an eight-tab strip scrolled to the active tab, drags left through the first slot, holds at the dock edge, then moves beyond it | Capture any rightward jump/oscillation while the pointer keeps moving left and verify the final tab order |
| B | Ran the new test before changing the clamp | Red: the dragged tab oscillated at the left edge (`15 → 63 → 15 → 63`) and the assertion failed |
| C | Kept an already-out-of-bounds drag offset from being coerced toward the boundary against the pointer direction | Green: the edge-motion assertion and final reorder both pass |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** The drag clamp returns no outward movement when the current translation is already beyond a boundary; inward movement is limited to reaching the boundary. This prevents `coerceIn` from producing a delta opposite to the pointer and reversing tab swaps.
- **Rejected:** Increasing the left edge buffer was deferred. The red test isolated a reversed delta in the existing clamp, so changing the bound would leave that cause intact.
- **Lesson candidate:** No new active lesson. The UI regression test now covers the left-edge oscillation, and the stable-list gesture host is already documented in `LESSONS.md`.
- **Guardrail update:** Added `dragActiveTabPastLeftEdge_whenStripStartsScrolled_doesNotJumpBack` to `TabStripRowDragTest`; no additional invariant was needed.
