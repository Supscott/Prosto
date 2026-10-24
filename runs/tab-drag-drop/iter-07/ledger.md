# Iteration 07 - tab-drag-drop

- **Date:** 2026-10-22T18:30:46+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Hoisting the long-press drag gesture from LazyRow items to the stable LazyRow node prevents Compose from cancelling the pointer stream when a reorder recreates the dragged item's LayoutNode

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Clamp the drag delta against the visible item's screen bounds (iter-05/06) | Keep the tab within the dock during the gesture; masks but does not remove the cancel |
| B | `systemGestureExclusion` + `try/finally` cancel on the item gesture (iter-05/06) | Rule out the OS back gesture as the source of the cancel |
| C | Reproduce the real gesture in a temporary Robolectric test with a manual clock | Capture the cancel stack and build a left/right + active-crossing matrix |
| D | Host the long-press drag loop on the stable `LazyRow` node and hit-test the tab id from the down position | Pointer stream survives arbitrary reorders because the gesture node is never disposed |

## Evidence

Probe C produced the cancellation chain, triggered while the drag was in progress:

```
HitPathTracker.removePointerInputModifierNode
  -> Node.dispatchCancel
  -> SuspendingPointerInputModifierNodeImpl.onCancelPointerInput
  -> TabStripRow.kt onDragCancel
  -> TabStripDragState.onDragCancel
UiApplier.remove -> LayoutNode.onChildRemoved -> LayoutNodeSubcompositionsState.subcompose
```

The trigger is `LazyRow` disposing the dragged item's `LayoutNode` when the item's slot content
type changes (active `ActiveTabPill` vs. inactive `InactiveTabButton`). Measured matrix:

| Dragged tab | Direction | Crosses active tab | Result |
|---|---|---|---|
| inactive | right | no | reorder applied |
| inactive | left | no | reorder applied |
| inactive | left | yes | synthesized cancel, spring-back |
| active | left | yes (swaps with inactive) | synthesized cancel, spring-back |
| active | right | no | reorder applied |

With four tabs and the active tab at index 0, swaps 3->2->1 survived and 1->0 cancelled, matching
the "cannot drop on the first slot" symptom. After the cancel, `onDragEnd` observed
`isDragging == false` and returned early, so no `ReorderTabs` event was ever dispatched.

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** Probe D. `TabStripDragGesture.kt` hosts the gesture on the `LazyRow` node, resolves
  the tab id from the down position via `LazyListState.tabIdAt`, and drives the existing
  `TabStripDragState`. `ActiveTabPill` / `InactiveTabButton` no longer own drag loops or menu
  state; the context menu is controlled by `TabStripRow` through `contextMenuTabId`.
  Regression coverage: `TabStripRowDragTest` (inactive across active to slot 0, active across
  inactive to slot 0, tap still selects, long-press still opens the menu).
- **Rejected:** Probes A and B. They treat the symptom (position clamping, OS gesture exclusion)
  and cannot prevent a cancel produced by Compose node disposal.
- **Lesson candidate:** Merged into `LESSONS.md` lesson 3: do not host a long-lived drag gesture
  inside a reorderable `LazyList` item; reordering replaces the item's `LayoutNode` and synthesizes
  a pointer cancel. Host the gesture on the stable list node and hit-test the item id.
- **Guardrail update:** `TabStripRowDragTest` now mechanically covers the exact paths that used to
  cancel, so the regression is gated by the test suite rather than by review.
