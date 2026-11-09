# Tickets: Task 3b - Cut out window.open Interception in AdBlockerScript

## Ticket 3b.1: Remove window.open monkey patch from AdBlockerScript
- **Title**: Cut out window.open monkey-patch from AdBlockerScript
- **Blocked by**: None
- **What it delivers**:
  - Removes lines 630-664 in `AdBlockerScript.kt` that patched `window.open`.
  - Links in YouTube video descriptions and other sites using `window.open` now open normally.
  - Updates assertions in `AdBlockerScriptTest.kt`.
- **Verification**: `AdBlockerScriptTest.kt` passes and invariants pass.
