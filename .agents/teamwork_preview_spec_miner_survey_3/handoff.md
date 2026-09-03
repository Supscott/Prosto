# Handoff Report — Spec Miner Survey 3 (R3, R4, R6)

**Agent:** Spec Miner Survey 3 (Android Systems & WebView Architecture)  
**Recipient:** Orchestrator (`cfb57ab5-b59c-49fd-8e97-bd04a90c54e8`)  
**Working Directory:** `/Users/ilya/CODING/browser/.agents/teamwork_preview_spec_miner_survey_3`  
**Report Artifact:** `/Users/ilya/CODING/browser/.agents/teamwork_preview_spec_miner_survey_3/survey_r3_r4_r6.md`  

---

## 1. Observation

1. **Authoritative Specification Analysis**:
   - `ORIGINAL_REQUEST.md` (lines 23-32, 39-44):
     - R3 requires: In-memory `WebViewPool` storing live `WebView` instances per tab for instant switching and preserved scroll state; execute `(webView.parent as? ViewGroup)?.removeView(webView)` before recycling/attaching in `AndroidView`; keep background media playback alive (do not call `onPause()` or `pauseTimers()` on backgrounded tabs).
     - R4 requires: Capture WebView canvas thumbnails upon navigation and `onPageFinished`; must use `VisualStateCallback` (`postVisualStateCallback`) or `PixelCopy` prior to canvas drawing to guarantee Blink has finished rendering frames, preventing blank/white cards in tab grid; cache snapshot images to app storage and link paths in Room DB for instant cold-boot loading.
     - R6.1 requires: Pre-bundled ad/tracker ruleset in app assets loaded into fast `HashSet` at startup with subdomain matching in `WebViewClient.shouldInterceptRequest`; return empty `WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))`; toggleable via Settings.
     - R6.2 requires: HTML5 fullscreen handling via `WebChromeClient.onShowCustomView` and `onHideCustomView`; in-app draggable video mini-player when switching tabs while video is active; system-level PiP (`enterPictureInPictureMode`) on app backgrounding / `onUserLeaveHint`.
     - R6.3 requires: Back button handling: dismiss overlays -> `webView.goBack()` -> close active tab.
   - `spec.md` (lines 102-124):
     - Confirms native ad/tracker blocker intercepting `request.url.host` with empty `WebResourceResponse`.
     - Confirms video mini-player, `onShowCustomView`/`onHideCustomView`, system PiP on app backgrounding or tab switching.
     - Confirms `OnBackPressedDispatcher` hierarchical evaluation: Overlay open -> Dismiss overlay; else if `webView.canGoBack()` -> `webView.goBack()`; else -> Close active tab.
     - Confirms state persistence linking tabs, titles, and layout order in Room DB for cold boot restoration.

---

## 2. Logic Chain

1. **R3 Multi-WebView Pool & Compose Attachment**:
   - Jetpack Compose `AndroidView` manages views by attaching them to internal Compose `AndroidViewHolder` view groups.
   - Re-attaching an already-attached `WebView` throws `IllegalStateException`.
   - By implementing `(webView.parent as? ViewGroup)?.removeView(webView)` synchronously within `prepareForAttachment` and inside `AndroidView.factory` and `AndroidView.update`/`onRelease`, parent collisions are mathematically impossible.
   - Preserving background media requires avoiding `pauseTimers()` (which freezes all JS timers process-wide) and `onPause()` (which halts audio/video engines).
2. **R4 Reliable Page Snapshot Engine**:
   - Blink renders asynchronously via GPU render thread. `onPageFinished` fires when DOM is built, but before the GPU presents pixels.
   - Calling `WebView.postVisualStateCallback(requestId, callback)` ensures `callback.onComplete` fires only after Blink's compositor finishes rasterizing the frame.
   - Drawing the canvas in `onComplete` guarantees non-blank thumbnails.
   - Atomically writing scaled JPEGs to disk (`context.filesDir/snapshots/tab_${id}.jpg`) and storing the path in Room DB enables instant thumbnail hydration during cold boot without loading WebViews.
3. **R6.1 Native Ad & Tracker Blocker**:
   - Parsing rules from `assets/adblock/hosts.txt` into a `HashSet<String>` at startup provides $O(1)$ exact-match lookup.
   - A progressive subdomain parent walk (`sub.domain.com` -> `domain.com`) ensures comprehensive filtering with $O(k)$ depth per request.
   - Returning `WebResourceResponse("text/plain", "UTF-8", 200, "OK", headers, ByteArrayInputStream(ByteArray(0)))` cleanly suppresses network requests and tracker execution without client errors.
4. **R6.2 Video Systems & PiP**:
   - Managing `WebChromeClient.onShowCustomView` with a top-level fullscreen `FrameLayout` and `WindowInsetsControllerCompat` provides immersive HTML5 video playback.
   - Triggering `enterPictureInPictureMode(params)` in `onUserLeaveHint()` provides seamless OS-level background video playback.
5. **R6.3 Back Navigation**:
   - A unified 5-tier evaluation cascade (Fullscreen CustomView -> Modal Overlays -> WebView History -> Multi-tab close -> App finish) ensures deterministic back navigation across all application states.

---

## 3. Caveats

- Android System PiP requires API Level 26+ (`Build.VERSION.SDK_INT >= 26`) and device hardware feature support (`PackageManager.FEATURE_PICTURE_IN_PICTURE`). A capability check is required before invoking `enterPictureInPictureMode`.
- `VisualStateCallback` was introduced in API Level 23 (Android 6.0 Marshmallow), which is fully supported given the project's Min SDK is 26.
- In memory-constrained environments, large numbers of live WebViews (e.g. >20) may consume significant RAM; an LRU memory-trim policy can optionally suspend background tabs if `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` is received.

---

## 4. Conclusion

- The technical specifications, algorithms, data structures, and edge case matrices for R3, R4, and R6 have been fully probed, analyzed, and documented in `/Users/ilya/CODING/browser/.agents/teamwork_preview_spec_miner_survey_3/survey_r3_r4_r6.md`.
- All requirements are actionable and ready for implementation by Worker agents and test coverage by Test/Challenger agents across Milestones 2, 3, and 6.

---

## 5. Verification Method

- **Specification Verification**:
  - Inspect `/Users/ilya/CODING/browser/.agents/teamwork_preview_spec_miner_survey_3/survey_r3_r4_r6.md`.
  - Validate Section 7 (21 Features Discovered) and Section 8 (18 Edge Cases) against `ORIGINAL_REQUEST.md` and `spec.md`.
- **Planned Automated Verification**:
  - Unit tests: `WebViewPoolTest`, `AdBlockerEngineTest`, `BackNavigationHierarchyTest`.
  - Integration/Robolectric tests: `VisualStateCallbackSnapshotTest`, `ComposeAndroidViewDetachmentTest`, `PictureInPictureModeTest`.
