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
- Tutorial scenes (`Tutorial*Scene.kt`): check the motion on a phone; the dock and folder story texts still mention reorder and "New folder", which the scenes do not show.
- Feedback card now shows on Home only after the tutorial is done; move it to a bottom sheet after N days or a successful action.

## Blocked
<!-- - Task — what it waits for -->
