# Tasks

Open work shared by all agents. One line per task, with a spec link when one exists. Delete a
task when it is done: `git log` is the history. Keep this file under 40 lines.

## In progress
<!-- - Task — owner/session, branch -->

## Open
- PRO-14: Suno is not installed on the test phone, so the visibility fix for installed apps is unverified; a tap on an uninstalled app's link shows a nameless "Open in app" pill that opens Play Store. Decide: dialog, "Install <app>" wording, or Chrome-style straight to Play.
- Home card previews in the tab grid (and the old live preview) crop the top of Home on tall 9:20 screens: they start at "Protection Active". Align the render to the top instead of centring it in `HomePreview`.
- `tools/smoke.sh`: add a tab switch through the dock, Back from the back/forward cache, and pull-to-refresh. Needs a phone to find the dock coordinates for `device.sh tap`.
- `QUERY_ALL_PACKAGES` (PRO-14/15): Play Console needs a declaration for it (browser use case); fill it in before the next store release.
- The global rtk hook makes auto mode resend rewritten commands once; keep it or remove it after a few sessions of use.
- BUG-04 (`TECH_DEBT_AUDIT.md`): smoke steps 4/4b fail with many tabs, also on `02b1b0f`. After Back from an external-link tab, reopening shows an older tab instead of the previous external one.
- BUG-05: blob downloads lose the `a[download]` filename. Send it back over `BlobDownloadChannel`.
- Split `TabOverviewGrid.kt` (891 lines): its 575-line composable needs new sub-composable boundaries, so it is not a move-only job. Split `BrowserViewModelTest`/`TabCoordinatorTest` by handler (TEST-01).
- PiP: add `setSourceRectHint` from the fullscreen video bounds (lint `PictureInPictureIssue`).

## Blocked
<!-- - Task — what it waits for -->
