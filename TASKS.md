# Tasks

Open work shared by all agents. One line per task, with a spec link when one exists. Delete a
task when it is done: `git log` is the history. Keep this file under 40 lines.

## In progress
<!-- - Task — owner/session, branch -->

## Open
- Address bar drops a character near the start of typing (`github.com` became `gihub.com`, seen 3 times on device). Not investigated.
- Split `TabStripRow.kt` (1661), `HomeScreen.kt` (1489), `SettingsScreen.kt` (1241): move-only, use the `refactor-mover` agent. See phase 2 in [the split plan](docs/specs/2026-11-god-file-split.md).
- Move the JavaScript in `engine/AdBlockerScript.kt` (970) to `assets/` and load it from there.
- `BrowserViewModel.kt` is 522 lines, mostly handler wiring: let handlers call the `TabCoordinator` parts directly and shrink the facade (phase 2 of the split plan).
- Make Back handling a pure `resolveBackPress(state): BackAction` with table-driven tests (phase 2 of the split plan).
- `tools/smoke.sh`: add a tab switch through the dock, Back from the back/forward cache, and pull-to-refresh.
- Quick vertical swipe on the search carousel may fire `onClick` as a tap: the gesture in `SearchEngineCarousel.kt` treats any short release without `isDrag` as a tap; expose the vertical axis from `DragAxisLock` and skip the tap. Not verified on device.
- Check `TabStripDragGesture.kt` and `TabOverviewGridDrag.kt` for `change.position - down.position` offsets: wrong if the node moves during the gesture (the keyboard-drag jitter class); use per-event deltas.
- `swipeDownToDismiss` (`SearchInputPill.kt`): a keyboard drag that turns horizontal stays offset until release; unwind the dispatched delta.
- The global rtk hook makes auto mode resend rewritten commands once; keep it or remove it after a few sessions of use.

## Blocked
<!-- - Task — what it waits for -->
