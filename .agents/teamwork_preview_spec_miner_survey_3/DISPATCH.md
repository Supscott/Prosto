## 2026-09-02T19:10:52Z
You are a Spec Miner for Jestk Browser for Android.
Your working directory is: /Users/ilya/CODING/browser/.agents/teamwork_preview_spec_miner_survey_3
Project root: /Users/ilya/CODING/browser

MANDATORY: Read the authoritative sources:
- /Users/ilya/CODING/browser/ORIGINAL_REQUEST.md
- /Users/ilya/CODING/browser/spec.md

Your focus is: **R3. Multi-WebView Pool & Lifecycle**, **R4. Reliable Page Snapshot Capture**, and **R6. Native Ad/Tracker Blocker & Media/PiP/Back Navigation**.
Analyze and extract all precise requirements, technical specifications, and algorithms for:
1. `WebViewPool`:
   - In-memory cache of live `WebView` instances keyed by tab ID.
   - Safe detachment: `(webView.parent as? ViewGroup)?.removeView(webView)` before recycling/attaching inside Compose `AndroidView` to prevent parent collisions and memory leaks.
   - Retain scroll state, back/forward history, and DOM state.
   - Background tab lifecycle: Do NOT call `onPause()` or `pauseTimers()` on background tabs so HTML5 audio/video streams and background execution continue uninterrupted.
2. Reliable Page Snapshot Engine (R4):
   - Capture WebView canvas thumbnails on navigation and `onPageFinished`.
   - Must use `VisualStateCallback` (`postVisualStateCallback`) or `PixelCopy` prior to canvas drawing to guarantee Blink has finished rendering frames, preventing blank/white cards in tab overview.
   - Save scaled bitmap snapshots to application disk storage cache and update snapshot file paths in Room DB.
3. Native Ad & Tracker Blocker (R6.1):
   - Pre-bundled ad/tracker ruleset in app assets (e.g. hostnames/domains list).
   - Loaded into fast `HashSet` at startup with subdomain matching algorithm.
   - `WebViewClient.shouldInterceptRequest(view, request)`: check `request.url.host`, if blacklisted return empty `WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))`.
   - Toggleable via Settings state.
4. Video & Media Systems (R6.2):
   - HTML5 Fullscreen: `WebChromeClient.onShowCustomView` and `onHideCustomView` with fullscreen container management.
   - In-app draggable video mini-player when switching tabs while video is playing.
   - Android System PiP (`enterPictureInPictureMode`) on app backgrounding/`onUserLeaveHint` when video playback is active.
5. Back Navigation Hierarchy:
   - OnBackPressedDispatcher / BackHandler: Overlays active -> Dismiss overlay; else if `webView.canGoBack()` -> `webView.goBack()`; else -> Close active tab.

Write your exhaustive specification report to:
`/Users/ilya/CODING/browser/.agents/teamwork_preview_spec_miner_survey_3/survey_r3_r4_r6.md`
And write your final handoff to:
`/Users/ilya/CODING/browser/.agents/teamwork_preview_spec_miner_survey_3/handoff.md`
Update your `progress.md` with timestamps as you work.
When done, message your parent with a concise completion notification citing the report path.
