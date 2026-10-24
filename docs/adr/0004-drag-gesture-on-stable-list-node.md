# ADR 0004 — Tab drag-reorder gesture lives on the stable `LazyRow` node, not on its items

- **Status:** Accepted
- **Date:** 2026-10-22
- **Scope:** `ui/navigation/TabStripRow.kt`, `ui/navigation/TabStripDragGesture.kt`, `ui/navigation/ActiveTabPill.kt`, `ui/navigation/TabStripDragState.kt`

## Context

The tab strip is a `LazyRow` whose items are the active tab (a wide `ActiveTabPill`) and the
inactive tabs (small circular `InactiveTabButton`s). Both item composables used to own a
long-press `awaitEachGesture` loop that drove `TabStripDragState` and the reorder.

Users reported that a tab could not be dropped into the first slot, and that dragging left
near the dock edge snapped back. A temporary Robolectric reproduction of the real gesture
captured the cause:

```
HitPathTracker.removePointerInputModifierNode
  -> Node.dispatchCancel
  -> SuspendingPointerInputModifierNodeImpl.onCancelPointerInput
UiApplier.remove -> LayoutNode.onChildRemoved -> LayoutNodeSubcompositionsState.subcompose
```

When a reorder moves the dragged tab into a slot whose content type changes (inactive circle
vs. active pill, or vice versa), `LazyRow` does not reuse the item's `LayoutNode`. Disposing
that node detaches its pointer-input modifier and Compose synthesizes a pointer cancel. The
gesture loop correctly interpreted the cancel as `onDragCancel`, so the state sprang back and
`onDragEnd` observed `isDragging == false` and dispatched no `ReorderTabs` event.

The measured failure matrix matched the reports: drags survived until they crossed the active
tab, and dragging the active tab left cancelled on its first swap. Rightward drags that never
crossed the active tab worked.

Clamping the translation (`clampDragDelta`) and excluding system gestures
(`systemGestureExclusion`) were tried first; both mask the symptom but cannot prevent a cancel
originating inside Compose's node lifecycle.

## Decision

**Host the long-press drag gesture on the stable `LazyRow` node and resolve the dragged item
from the pointer position.**

- `TabStripDragGesture.kt` provides `rememberTabStripDragReorderModifier`, mounted on the
  `LazyRow` modifier chain. That node is never replaced by item reordering, so its pointer
  stream survives arbitrary swaps.
- On `awaitFirstDown`, the tab id is hit-tested from `LazyListState.layoutInfo.visibleItemsInfo`
  using the down position. Spacing halves are included so presses in the gap still hit the
  nearest item.
- The gesture only claims the pointer after the long-press timeout: a long press without
  movement opens the context menu, a long press plus horizontal movement beyond touch slop
  starts the drag, and short taps continue to be handled by the items' own `clickable`s.
  Events are observed on `PointerEventPass.Initial` and consumed after the claim so child
  clickables cancel cleanly.
- `TabStripDragState` is unchanged: the gesture feeds the same `onDragStart` / `onDrag` /
  `onDragEnd` / `onDragCancel` API, including the existing edge clamp and auto-scroll path.
- Item composables (`ActiveTabPill`, `InactiveTabButton`) no longer own drag loops. The
  context menu is controlled by `TabStripRow` through `contextMenuTabId`, so the menu survives
  item recreation.
- `TabStripRowDragTest` covers the two paths that used to cancel (inactive tab across the
  active tab to slot 0, active tab across inactive tabs to slot 0) plus tap-to-select and
  long-press-to-open-menu.

## Consequences

- Reordering can no longer cancel the gesture, regardless of how many slots change content
  type during a drag.
- Drag behavior is centralized in one gesture host; per-item drag plumbing and its duplicated
  state are gone. New item visuals do not need to be aware of dragging.
- The gesture performs a hit test against the current layout each time a press starts, so it
  depends on `LazyListState` being laid out; presses before first layout are ignored.
- This pattern is recorded in `LESSONS.md` lesson 3 and must be reused for any future
  reorderable list with drag interactions.
