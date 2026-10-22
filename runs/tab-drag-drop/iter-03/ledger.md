# Iteration 03 - tab-drag-drop

- **Date:** 2026-10-22T16:49:50+00:00
- **Base ref:** `n/a`
- **Hypothesis:** variant 1: center crossing for inactive tab dragging over active tab, edge overlap for active tab

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Implemented Variant 1: active tab uses edge overlap (`visualEnd/Start > targetTriggerX`), while small inactive tab entering wide active tab triggers on crossing the active tab's center (`visualCenter >/< targetCenter`) | Eliminates infinite ping-pong swap loop and rapid vibration when dragging inactive tabs over active tabs, while preserving instant full-pill response for active tab |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** Variant 1: asymmetric threshold calculation where wide items trigger on leading edge overlap (50% target width) and narrow items crossing wider items trigger on target center crossing in [`TabStripDragState.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/TabStripDragState.kt).
- **Rejected:** Naive edge overlap for narrow items entering wide items, which caused the narrow item's new slot to be 100+dp away from the finger and immediately trigger reverse swaps.
- **Lesson candidate:** In asymmetric drag-and-drop rows, narrow items entering wide items must trigger on crossing the wide item's center rather than its entrance edge, otherwise the new slot center is far from the finger and triggers immediate reverse oscillation.
- **Guardrail update:** Added unit test `onDrag_inactiveTabEnteringActiveTab_swapsWhenCrossingActiveTabCenter` verifying single vibration and zero oscillation on hover.
