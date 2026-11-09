# Tickets: Task 1 - Search Keyboard Dismissal & Drag

## Ticket 1.1: Interactive Keyboard Dismissal via `imeNestedScroll`
- **Title**: Enable interactive soft keyboard tracking on SearchOverlay scrollable container
- **Blocked by**: None
- **What it delivers**:
  - Adds `Modifier.imeNestedScroll()` to the search suggestions scrollable container in `SearchOverlay.kt`.
  - Soft keyboard follows finger downward drag and collapses interactively.
- **Verification**: Compose UI tests in `SearchOverlayInputTest.kt` verifying `imeNestedScroll` is attached and scroll behavior operates correctly.

## Ticket 1.2: Downward Overscroll Overlay Dismissal
- **Title**: Dismiss SearchOverlay when dragging downwards after keyboard is collapsed
- **Blocked by**: Ticket 1.1
- **What it delivers**:
  - Adds a `NestedScrollConnection` that detects post-scroll downward drag when IME is collapsed and scroll position is at the boundary.
  - Calls `onEvent(BrowserUiEvent.DismissOverlay)` upon exceeding the drag threshold.
- **Verification**: Unit tests in `SearchOverlayInputTest.kt` simulating downward drag and verifying overlay dismissal event dispatch.
