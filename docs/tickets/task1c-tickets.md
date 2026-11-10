# Tickets: Task 1c — Smooth Interactive Keyboard Drag on Search Input

## Ticket 1c.1: Integrate Nested Scroll Dispatching on Search Input Field
- **File**: `app/src/main/java/com/jestk/browser/ui/overlays/SearchInputPill.kt`, `SearchOverlay.kt`
- **Task**:
  - Update `HeroSearchInputField` / `swipeDownToDismiss` to participate in Compose's nested scroll hierarchy (`imeNestedScroll()`).
  - Dispatch vertical drag deltas using `NestedScrollDispatcher` or `Modifier.scrollable(orientation = Orientation.Vertical)` so that downward drag directly feeds `imeNestedScroll()`.
  - Ensure horizontal gesture slop remains functional for search engine switching.
  - Satisfy Lesson 1: suspend immediately inside any `awaitEachGesture` loops.

## Ticket 1c.2: Two-Stage Dismissal & Unit Test Verification
- **File**: `app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt`, `app/src/test/java/com/jestk/browser/ui/overlays/SearchOverlayInputTest.kt`
- **Task**:
  - Retain the two-stage dismissal logic: Stage 1 interactively collapses the keyboard; Stage 2 (or dragging when keyboard is already collapsed) dismisses the search overlay.
  - Add or update Robolectric unit tests in `SearchOverlayInputTest.kt` verifying that downward gestures on the input field dispatch scroll deltas and trigger dismiss events appropriately.
  - Verify `rtk tools/verify.sh` passes cleanly.
