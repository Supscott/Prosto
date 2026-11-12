# Tasks

Open work shared by all agents. One line per task, with a spec link when one exists. Delete a
task when it is done: `git log` is the history. Keep this file under 40 lines.

## In progress
<!-- - Task — owner/session, branch -->

## Open
- Device check for branch `agent/todo-sweep` (no phone was connected): PRO-13 dock spacing, PRO-14/15 "Open app" dialog (Suno, Linear), address bar inline completion (no dropped keys, highlight look), quick vertical swipe on the engine carousel, keyboard drag turning sideways, tab strip after the split (effects now start from child composables). Then run `tools/smoke.sh`.
- `tools/smoke.sh`: add a tab switch through the dock, Back from the back/forward cache, and pull-to-refresh. Needs a phone to find the dock coordinates for `device.sh tap`.
- `QUERY_ALL_PACKAGES` (PRO-14/15): Play Console needs a declaration for it (browser use case); fill it in before the next store release.
- The global rtk hook makes auto mode resend rewritten commands once; keep it or remove it after a few sessions of use.

## Blocked
<!-- - Task — what it waits for -->
