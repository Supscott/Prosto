# Tickets: Task 1b - Search Input Keyboard Dismissal

## Ticket 1b.1: Conditional Swipe Down Handler on Search Input Field
- **Title**: Prevent immediate search overlay dismissal on input swipe down; dismiss keyboard first
- **Blocked by**: None
- **What it delivers**:
  - Update `HeroSearchInputField` / `SearchOverlay.kt` swipe down handling.
  - If keyboard is open: hides keyboard, keeps overlay open.
  - If keyboard is closed: dismisses overlay.
  - If dropdown open: closes dropdown.
- **Verification**: Compose UI tests in `SearchOverlayInputTest.kt` verifying swipe down behavior with keyboard open vs collapsed.
