# Iteration 05 - tab-drag-drop

- **Date:** 2026-10-22T17:21:22+00:00
- **Base ref:** `n/a`
- **Hypothesis:** dock drag boundary clamping and gesture cancellation recovery

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Dock edge boundary clamping (`clampDragDelta`), `try...finally` cancellation recovery with `!dragChange.pressed || dragChange.isConsumed` checks in pointer inputs, fail-safe circuit breaker on auto-scroll `visibleItem == null`, and unmount `DisposableEffect` cleanup | Dragged tabs cannot escape dock boundaries into Android back gesture zones; any pointer interruption, system cancel, or uncomposition cleanly triggers `onDragCancel()`, preventing dock freezes and state deadlocks |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** `clampDragDelta` within dock boundaries, `try...finally` wrapped drag pointer loops in [`ActiveTabPill.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/ActiveTabPill.kt) and [`TabStripRow.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/ui/navigation/TabStripRow.kt), auto-scroll boundary checks (`canScrollForward` only when not at `lastIndex`), and `DisposableEffect` unmount cleanup.
- **Rejected:** Unbounded drag translation allowing tabs to float outside dock capsule and collide with system navigation gesture zones.
- **Lesson candidate:** In custom Compose pointer drag gestures, always wrap the event loop in `try...finally` and check `!dragChange.pressed` / `dragChange.isConsumed` rather than solely `changedToUp()`, because Android system gesture cancellation (back gesture at screen edge) cancels the coroutine via `CancellationException` and skips post-loop cleanup unless guarded by `finally`.
- **Guardrail update:** Added cancellation and edge auto-scroll boundary unit tests in [`TabStripDragSpringTest.kt`](file:///Users/ilya/CODING/browser/app/src/test/java/com/jestk/browser/ui/navigation/TabStripDragSpringTest.kt).
