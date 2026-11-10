# Specification: Task 1c — Smooth Interactive Keyboard Drag on Search Input

## 1. Problem Statement
In the Search overlay, dragging downward on the history suggestions list smoothly and interactively moves the soft keyboard (IME) pixel-by-pixel with the user's finger via Compose's `Modifier.imeNestedScroll()`.
In contrast, dragging downward on the search input pill (`HeroSearchInputField`) does not track interactively under the finger. Instead, it triggers a discrete `keyboardController?.hide()` call only after crossing a threshold or releasing the touch, causing Android's canned non-interactive slide-down animation.
The user expects dragging downward on the search input pill to track smoothly and interactively under the finger, identical to swiping above the input on the suggestions list.

## 2. Requirements & Desired Behavior
1. **Interactive IME Tracking**: Vertical downward drag gestures on `HeroSearchInputField` / `SearchInputPill` must feed into Compose's nested scroll system (`imeNestedScroll()`) so that the soft keyboard moves in real time under the finger.
2. **Two-Stage Dismissal Coordination**:
   - First stage: When the keyboard is visible, dragging down collapses the keyboard interactively in lockstep with the touch movement.
   - Second stage: Once the keyboard is fully hidden (or if dragging starts when the keyboard is already hidden), continuing the downward drag past the threshold dismisses the search overlay.
3. **No Tap or Horizontal Gesture Interference**:
   - Tapping the search input must continue to focus and show the keyboard without delay.
   - Horizontal swiping (search engine switching carousel) must retain touch slop priority and not conflict with vertical IME drag.
4. **Invariant Compliance**:
   - Must strictly satisfy Lesson 1: suspend immediately upon entering `awaitEachGesture` (no early returns before suspending pointer calls).
   - Prohibit `Modifier.blur()` and keep domain purity.
   - Zero hardcoded strings (all user-facing strings localized in `strings.xml` and `values-ru/strings.xml`).

## 3. Technical Approach
- Apply `Modifier.imeNestedScroll()` on the shared parent container or input field.
- Connect `HeroSearchInputField` to the nested scrolling pipeline using `Modifier.scrollable(orientation = Orientation.Vertical, ...)` or a `NestedScrollDispatcher` / `NestedScrollConnection` so that downward drag deltas are dispatched to the nested scroll chain as `NestedScrollSource.UserInput`.
- When the IME consumes the delta, the keyboard translates smoothly under the finger. When the IME is unconsumed (keyboard fully down), remaining downward delta is accumulated to trigger overlay dismissal.
