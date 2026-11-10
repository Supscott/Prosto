# Specification: Smooth Fullscreen Video Playback & Player Resilience

## Problem Statement

When users watch embedded HTML5 videos on websites (such as YouTube, VK, Vimeo, Rutube, streaming portals, and news sites) and tap the fullscreen button, the video abruptly stops, reloads from the network, and rebuffers from scratch, displaying a loading spinner. In comparison browsers like DuckDuckGo, fullscreen expansion is instantaneous and uninterrupted, with the video continuing playback seamlessly.

Furthermore, when Clean Player is active, navigating from Clean Player to the native site can trigger recursive interception loops if the user intends to stay on the web page, and embed requests on certain WebView versions can fail if the HTTP Referer header is stripped.

## Solution

Adopt the proven Chromium WebView media presentation patterns from DuckDuckGo:
1. Eliminate hardware surface destruction by removing forced screen orientation locking and unnecessary DOM aspect-ratio query cycles during fullscreen transition.
2. Ensure custom fullscreen views maintain a stable attachment lifecycle without recycling or tearing down the video surface in the UI hierarchy.
3. Stop disruptive JavaScript DOM injections (`v.play()`) that race against website player state machines during fullscreen transitions.
4. Make viewport padding transitions instantaneous during fullscreen activation to prevent continuous resize event storms.
5. Harden Clean Player navigation with a temporary bypass guard when opening the native website, and guarantee explicit embed Referer header propagation in request interception.

## User Stories

1. As a browser user, I want embedded HTML5 videos to expand into fullscreen immediately when I tap the player's fullscreen button, so that my viewing experience is smooth and uninterrupted.
2. As a browser user, I want the video buffer to remain intact when entering fullscreen, so that I do not experience repeated network loading spinners or audio/video stutters.
3. As a browser user, I want video playback to continue uninterrupted at the exact timestamp and playback state when toggling fullscreen mode.
4. As a browser user, I want a landscape video to rotate to landscape in fullscreen, even with system auto-rotate off, without the video reloading.
5. As a browser user, I want vertical videos (such as Shorts, Reels, and clips) to remain playable in fullscreen without being forcibly distorted into landscape.
6. As a browser user, I want the swipe-down gesture to exit fullscreen smoothly without video decoder crashes or lingering black overlays.
7. As a browser user, I want website video players (YouTube, VK, Video.js, etc.) to manage their own playback state without the browser firing conflicting external play commands into the DOM.
8. As a Clean Player user, when I tap "Open on YouTube" or "Watch on site", I want to land on the genuine site without the browser recursively intercepting the navigation back into the clean player overlay.
9. As a Clean Player user, I want video embeds on privacy-enhanced domains (`youtube-nocookie.com/embed`) to reliably load across all Android WebView versions without Error 150 or Error 153.
10. As a browser user, I want system bars (status bar and navigation bar) to hide in immersive mode when watching video in fullscreen, maximizing viewable area.

## Implementation Decisions

- **Orientation (revised 2026-11-11)**: The reload was not caused by rotation. Chromium detaches the page before `onShowCustomView`, and the page stays `hidden` until the fullscreen view is attached to a window; YouTube tears down its MediaSource on that `visibilitychange`. With the view attached synchronously (below), rotating is safe. YouTube calls `screen.orientation.lock('landscape')` right after `requestFullscreen()`, before the document is fullscreen, so the call hits WebView's native lock and fails. The document-start orientation bridge therefore locks to `SENSOR_LANDSCAPE` on `fullscreenchange` when the fullscreen element shows a landscape video (like Chrome's media controls), which also works with system auto-rotate off, as in DuckDuckGo.
- **Surface Lifecycle Stability**: Custom views passed by the Chromium engine via `WebChromeClient.onShowCustomView` must be attached synchronously inside the callback (`FullscreenViewHost`, enforced by `tools/check_invariants.py`) and keep a single, uninterrupted attachment to the layout container. The UI layer must not detach the view during recomposition or configuration changes.
- **Elimination of DOM Aspect Ratio Polling**: The asynchronous multi-attempt DOM polling loop (`queryVideoAspectRatioAndAdjustOrientation`) shall be removed. Video orientation shall not be double-toggled via late asynchronous callbacks.
- **Zero JS Playback Interference**: The custom scriptlet that forcibly queries all `<video>` tags and invokes `.play()` (`resumeVideoPlayback`) shall be deleted. Chromium and the website's HTML5 player shall maintain full authority over video playback state.
- **Instant Fullscreen Viewport Transition**: When fullscreen custom view is active, bottom bar offsets and system insets padding must snap instantly to zero without animating over duration tweens, eliminating viewport resize thrashing.
- **Clean Player Handoff Guard**: A temporary single-navigation bypass token shall be set when the user explicitly triggers "Open on site" from Clean Player, preventing the main frame navigation interceptor from re-opening Clean Player until navigation completes.
- **Embed Referer Injection**: In the network interception seam (`shouldInterceptRequest`), requests to YouTube privacy-enhanced embed endpoints shall receive an explicit Referer header if missing, mirroring DuckDuckGo's embed referer provider.

## Testing Decisions

- Tests must exercise external behavior through existing seams:
  - `JestkWebChromeClientTest`: Verify that `onShowCustomView` sets fullscreen state without injecting JavaScript or forcing landscape orientation overrides, and handles multiple custom view events without tearing down active playback.
  - `JestkWebViewClientTest`: Verify that `shouldOverrideUrlLoading` respects the Clean Player bypass token when user requests native web playback, and that `shouldInterceptRequest` handles embed headers correctly.
  - `BrowserViewModelTest`: Verify state transitions for fullscreen custom views and orientation states.
  - Invariants check: Run `python3 tools/check_invariants.py` and ensure zero regressions across architectural standards.

## Out of Scope

- Picture-in-Picture (PiP) architecture redesign (retained as-is).
- Changes to ad-block rulesets or cosmetic DOM cleaners.
- Duck Player in-page banner UI / "Always Ask" dialog (can be a separate future enhancement).

## Further Notes

- Implements key architectural lessons identified during DuckDuckGo Android (`duckduckgo/Android`) reference analysis.
