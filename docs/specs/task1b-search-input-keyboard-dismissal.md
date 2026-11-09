# Spec: Search Input Keyboard Dismissal and Conditional Overlay Dismissal

## Problem Statement
Previously, swiping down on the search input field (`HeroSearchInputField`) immediately dismissed the entire SearchOverlay. This broke the intended interaction described in `task1-search-keyboard-dismissal.md`, where downward gestures should first collapse the soft keyboard, and only dismiss the overlay if the keyboard is already collapsed or if the downward drag continues after the keyboard has fully hidden.

## Solution
Update the swipe down / drag logic on `HeroSearchInputField`:
1. When the soft keyboard is visible (`effectiveIsImeVisible == true`), a downward swipe/drag on the input field must collapse the keyboard (`keyboardController?.hide()`) while keeping the search overlay open.
2. When the soft keyboard is collapsed (`!effectiveIsImeVisible`), a downward swipe/drag on the input field dismisses the search overlay (`BrowserUiEvent.DismissOverlay`).
3. If the search engine dropdown sheet is open, the downward gesture dismisses the dropdown sheet first without closing the overlay.
4. Tapping on the input field after keyboard dismissal re-focuses and shows the keyboard.

## User Stories
1. As a browser user, when I swipe down on the search input field with the keyboard open, I want the keyboard to hide without closing my search screen, so that I can see the full favorites and history lists.
2. As a browser user, when the keyboard is already hidden, swiping down on the search input field should dismiss the search overlay and return to my web page.
3. As a browser user, if the search engine selector dropdown is open, swiping down on the input closes the dropdown first.

## Implementation Decisions
- **Composables**: `SearchOverlay.kt` and `SearchInputPill.kt`.
- In `SearchOverlay.kt`:
  Pass an `onSwipeDown` handler (or drag state) to `HeroSearchInputField` that checks `effectiveIsImeVisible`.
  - If `isDropdownOpen`: `isDropdownOpen = false`
  - Else if `effectiveIsImeVisible`: `keyboardController?.hide()`
  - Else: `onEvent(BrowserUiEvent.DismissOverlay)`
- In `SearchInputPill.kt`:
  Ensure continuous downward drag can transition from keyboard collapse to overlay dismiss if dragged sufficiently, or respect the conditional dismissal threshold.
- Update tests in `SearchOverlayInputTest.kt`.
