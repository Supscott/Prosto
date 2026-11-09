# Spec: Search Overlay Interactive Keyboard Dismissal & Drag

## Problem Statement
When a user opens the Search Overlay, the soft keyboard automatically appears. As the user attempts to scroll or swipe down through search history suggestions, favorites, or the search canvas, the keyboard currently remains static instead of smoothly tracking and collapsing along with the user's finger. Furthermore, if the user continues dragging down after the keyboard has fully collapsed, the search overlay should dismiss cleanly to return to the active browsing tab.

## Solution
Integrate interactive keyboard dismissal into the Search Overlay scroll container using Compose's `Modifier.imeNestedScroll()`.
1. As the user drags downward, the IME (soft keyboard) is dismissed synchronously with the finger motion.
2. If the user continues dragging downward past the boundary once the keyboard is fully hidden (or overscrolls), dismiss the search overlay (`BrowserUiEvent.DismissOverlay`).
3. Tapping outside the input field or swiping down on the search pill maintains existing dismissal behavior.

## User Stories
1. As a browser user, I want the soft keyboard to collapse interactively as I drag down over search suggestions, so that the screen transition feels fluid and responsive.
2. As a browser user, I want the search overlay to stay open with history/favorites accessible when I scroll through suggestions, so that I can browse my search history easily.
3. As a browser user, I want a continued downward swipe gesture after the keyboard has collapsed to dismiss the search overlay, so that I can return to my web page with a single continuous gesture.
4. As a browser user, I want tapping on the search input field after keyboard dismissal to re-open the keyboard seamlessly, so that I can resume typing without losing my input.
5. As an accessibility user, I want talkback and non-touch navigation to continue functioning with explicit expand/collapse actions, so that keyboard dismissal does not hinder accessibility.

## Implementation Decisions
- **Module & Composables**: `app/src/main/java/com/jestk/browser/ui/overlays/SearchOverlay.kt`
- **Seams & Mechanisms**:
  - Add `Modifier.imeNestedScroll()` to the scrollable container hosting search history suggestions.
  - Implement a `NestedScrollConnection` or custom gesture detection on the scroll container that tracks downward scroll delta. When `scrollState.value == 0` (or at scroll limit) and IME is hidden, downward swipe delta beyond touch slop triggers `onEvent(BrowserUiEvent.DismissOverlay)`.
- **Invariants**:
  - Adhere to Lesson 1: suspend immediately in gestures, do not freeze CPU.
  - Zero blur: maintain `prostoSkyBackground`.
  - Zero hardcoded strings.
