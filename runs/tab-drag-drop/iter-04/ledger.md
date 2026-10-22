# Iteration 04 - tab-drag-drop

- **Date:** 2026-10-22T16:58:27+00:00
- **Base ref:** `n/a`
- **Hypothesis:** edge auto-scrolling during tab drag & drop when approaching dock boundaries

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Implemented proportional edge auto-scrolling via `computeAutoScrollDelta` and continuous `LaunchedEffect` in `TabStripRow.kt`, feeding scroll delta directly into `onDrag` | When a tab is dragged near dock edges (<24dp), LazyRow smoothly auto-scrolls at proportional speed and continuously reorders off-screen tabs under the finger without jumping |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** Pure `computeAutoScrollDelta` helper in [`TabStripDragState.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/TabStripDragState.kt) and edge auto-scrolling loop in [`TabStripRow.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/TabStripRow.kt).
- **Rejected:** Fixed-speed step scrolling without boundary dampening.
- **Lesson candidate:** When auto-scrolling during drag & drop in LazyRow, feeding `consumedScroll` directly into `dragState.onDrag(deltaX = consumed)` keeps the dragged item perfectly stationary on screen under the finger while the row slides underneath.
- **Guardrail update:** Added unit test suite for edge auto-scrolling in [`TabStripDragSpringTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/navigation/TabStripDragSpringTest.kt).
