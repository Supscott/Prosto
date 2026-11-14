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
- `JestkBlobBridge` is still a visible `window` global (the stealth rule forbids it): move it behind a captured-and-deleted random-name bridge like `ContentBlockingSetup`.
- Procedural cosmetics (`procedural` array: `:has-text`, `:upward`, `:xpath`) are not applied: the bridge drops them. A procedural-filter runner would add the remaining uBO cosmetic coverage (CB-08 follow-up).
- Content blocking device pass (phone needed; nothing ran on a device): fill `docs/specs/2026-11-content-blocking-baseline.md` (cold start, PSS Standard vs Strict, `AdblockLatency` p50/p99), bridge-delete CDP probe in main frame and cross-origin iframe, every `tools/fixtures/adblock` page, `AdBlockHeaders` log (is `Sec-Fetch-Dest` present?), SW requests on YouTube, Google/Yandex/Arkose popups, Strict download/drop, allowlist per workspace, Russian news site vs the retired static selectors.
- Popunder rule assumes the opener's redirect has no gesture; Chrome's transient activation may flag it. Check on `popunder.html` and two real sites, then tune `PopunderDetector.DEFAULT_THRESHOLD_MS`.
- YouTube ad pruning in `youtube_fast_start.js` stays: www.youtube.com's uBO rules need trusted scriptlets (`trusted-replace-xhr-response`), which are not bundled. Decide whether to bundle trusted scriptlets for uBO's own lists.
- The bootstrap now carries the 167 KB scriptlet bundle and every frame parses it; measure per-frame cost on a phone and serve it only to frames with scriptlets if it shows.
- Universal APK grows ~10.7 MB (7.8 MB uncompressed `.so` for 3 ABIs): ship an app bundle or ABI splits before the next store release.
- `$document` block page: it stays in tab/browsing history, has no in-page buttons, and "Proceed anyway" allows the host app-wide (reuses `allowedAdNavigationUrls`).

## Blocked
<!-- - Task — what it waits for -->
