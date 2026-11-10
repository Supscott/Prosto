# Codebase Review — Anti-Bot, Ad Blocker, WebView Core (2026-11)

Status: **P0/P1 fixed (2026-11-11), P2 items open.** Each finding has an ID; commits reference it.

| ID | Status | Commit / note |
|---|---|---|
| AB-1, AB-2, AB-3 | fixed | `fix(adblock)` — generator `tools/build_adblock_list.py`, two-tier list, no self-blocking; regression tests on the shipped asset |
| AB-4, AB-5 | fixed | `fix(adblock)` — auto-consent limited to known CMPs; overlay scan removed |
| BOT-1, BOT-2 | fixed, verified on device | `fix(antibot)` — probe matches Chrome for `matchMedia`, `orientation.lock`, `JSON.parse`, no extra globals |
| BOT-3 | mitigated | `fix(antibot)` — native caller checks; the two bridge names are still visible → move to `addWebMessageListener` (open) |
| BOT-4 | won't fix | `X-Requested-With` cannot be disabled: `REQUESTED_WITH_HEADER_ALLOW_LIST` is unsupported on WebView 156; DuckDuckGo sends it too |
| WV-1 | fixed, verified on device | `fix(webview)` — CDP `Page.crash` → tab recreated and reloaded |
| WV-2 | fixed | `fix(webview)` — hidden tabs paused (DuckDuckGo parity) |
| WV-3, WV-4, WV-5 | fixed | `fix(webview)`, `fix(antibot)` — WV-5 still worth a device check on a 3-D Secure payment |
| WV-6 | partly fixed | WebView setup deduplicated; script injection moved to `onPageFinished`; apple.com/youtube.com theme hacks remain |
| DL-1, DL-2, DL-3 | fixed | `fix(downloads)` — 416 regression test |
| DATA-1 | resolved by deletion | `refactor(cleanup)` |
| DATA-2…6, DL-4, UI-1…8 | open (P2) | see sections 4 and 6 |
| Prevention | done | `tools/check_invariants.py` Script Stealth check; `verify.sh` checks the ad-block list; LESSONS #12 and CLAUDE.md rewritten |


Method:
1. Manual line-by-line read of the WebView core: `WebViewPool`, `JestkWebViewClient`,
   `JestkWebChromeClient`, `NestedWebView`, `UserAgentMetadataProvider`, `UserAgentSanitizer`,
   `AdBlockerEngine`, `AdBlockerScript`, `ColorSchemeScript`, `WindowTransportHandler`, plus the
   client wiring in `JestkApp.kt` / `WebViewContainer.kt`.
2. Comparison with DuckDuckGo Android (`develop`, shallow clone).
3. Executable checks: a throwaway Robolectric probe against the real `adblock_domains.txt`, and a
   Python re-parse of the shipped EasyList files.
4. Broad sweep of the rest of the codebase by cheaper review agents; every finding kept below was
   re-checked by hand. Unverified items are marked **(unverified)**.
5. On-device fingerprint comparison Prosto vs Chrome — see section 5.

Severity: **P0** breaks sites or users today · **P1** likely bot-detection signal, data loss or
resource leak · **P2** correctness/maintainability debt.

---

## 1. How the ad blocker actually works

There are three independent layers. Only layer A uses the filter lists.

**A. Network blocking (`JestkWebViewClient.shouldInterceptRequest` → `AdBlockerEngine.shouldBlock`)**

- At start-up `AdBlockerEngine.initialize()` loads `assets/adblock_domains.txt` (48 374 hostnames)
  into a hash set. `EasyList.txt`, `EasyListCookie.txt` and `adblock_hosts.txt` are only a
  fallback used when that file is missing, i.e. never in practice — they ship in the APK as ~3 MB
  of dead weight (plus a stray `.DS_Store`).
- The precompiled file was produced from EasyList `||domain^` rules only. Everything else in
  EasyList is ignored: path rules, `@@` exceptions, `##` cosmetic rules, and — crucially — the
  options `$third-party`, `$script`, `$popup`, `$domain=`. A rule is either "block this host
  everywhere" or nothing. No generator script is checked in, so nobody can reproduce the file.
- Decision for a request (`shouldBlock(parsed, documentUrl)`):
  - same site as the page → block only if `isExplicitAdHost` (host label is `ad`, `ads`, `an`,
    `bs`, `ssp`, `target`, `banner`, …) or `isExplicitAdPath` (`/ads/`, `/banner…`, `ads.js` …);
  - other site → block if the host or any parent domain is in the set, or a hard-coded path rule
    matches.
- A blocked request gets a fake `200 OK`: 1×1 PNG for images, a JS stub for scripts, `{}` for
  JSON, `204` for beacons/media, with forged CORS headers that echo the requester's origin and
  allow credentials.
- The "tracker" flag (`isTracker`) does not decide blocking — it only splits the statistics and
  lets the "Tracking protection" toggle keep a request blocked while "Ad blocker" is off.

**B. Navigation blocking (`shouldOverrideUrlLoading` → `isBlockedAdNavigation`)**

- A main-frame navigation to a blocked host is cancelled and the "blocked ad navigation" banner is
  shown.
- "Redirect protection": any main-frame cross-site navigation **without a user gesture** is
  cancelled, except when coming from a search engine or going to an auth host.

**C. Page scripts (`AdBlockerScript`, injected into every frame of every site)**

- Registered once per WebView with `addDocumentStartJavaScript(…, setOf("*"))`, and additionally
  re-run with `evaluateJavascript` in both `onPageStarted` and `onPageFinished` (guarded by a
  `__hori_adblock_initialized` flag).
- Contents: a `ScreenOrientation.lock/unlock` polyfill; YouTube ad pruning (patches `JSON.parse`,
  `Response.prototype.json/text`, XHR `responseText/response`, `history.pushState/replaceState`)
  that runs **even when the ad blocker is off**; global stubs `adsbygoogle`, `googletag`,
  `yaContextCb`, `Ya`, `x5media`; a cosmetic CSS `<style>` with ~80 selectors; a MutationObserver
  that collapses ad nodes and, on every mutation batch, scans every `div, a, span` on the page
  for "click-jacking overlays"; and the auto-consent script (cookie banners), injected separately.

---

## 2. P0 — breaks sites today

### AB-1 · The domain list blocks 69 hosts that EasyList only blocks on specific sites
`assets/adblock_domains.txt` contains hosts whose only EasyList rules carry a positive `domain=`
option, so the rule was meant for one site and is applied to all of them. Confirmed with the real
engine (Robolectric probe):

| URL | Page | Result |
|---|---|---|
| `https://my-bucket.s3.amazonaws.com/photo.jpg` | `example.com` | blocked |
| `https://i.imgur.com/abc.jpg` | `reddit.com` | blocked |
| `https://t.co/abc` (link tap on X) | — | blocked |
| `https://bit.ly/abc` | — | blocked |
| `https://cdn.jwplayer.com/libraries/x.js` | news site | blocked |
| `https://files.wordpress.com/…` | blog | blocked |
| `https://players.brightcove.net/…` | news site | blocked |

Full list: `amazonaws.com, imgur.com, i.imgur.com, t.co, t.ly, bit.ly, b.link, rx.link,
cdn77.org, files.wordpress.com, jwplayer.com, jwpcdn.com, players.brightcove.net, rumble.com,
geo.dailymotion.com, gyazo.com, ibb.co, binance.com, temu.com, s.click.aliexpress.com,
cookielaw.org, cookiepro.com, cmp.quantcast.com, consent.trustarc.com, privacy-mgmt.com,
tags.tiqcdn.com, …` (69 total; regenerate with the script from AB-3).

In addition 1 081 hosts come from `$popup`-only rules and 2 904 from rules restricted to a
resource type or `$third-party`; all are blocked unconditionally.

**Fix:** AB-3 (correct generator). Until then, remove the 69 hosts by hand.
**Verify:** a unit test that loads the real asset and asserts the URLs above are not blocked.

### AB-2 · First-party heuristics block whole legitimate sites
`AdBlockerEngine.isExplicitAdHost` treats a host *starting with* `target.`, `bs.`, `ssp.`, `an.`,
`ad.`, `ads.`, `banner.` … as an ad host, and `isExplicitAdPath` matches `/ads/`, `/banner*`
anywhere. These run for same-site requests, including the main document, because
`shouldInterceptRequest` passes `loadedUrl` (already set by `NestedWebView.loadUrl`). Confirmed:

- `https://www.target.com/` on `target.com` → **main document blocked** (the whole site is dead).
- `https://bs.to/…`, `https://ssp.example.org/app.js` → blocked on their own site.
- `https://avito.ru/ads/123`, `https://example.com/banners/logo.png` → blocked on their own site.

**Fix:** never block `request.isForMainFrame`; delete the label/path heuristics for first-party
requests (EasyList already covers real ad subdomains). If some heuristic is kept, it must be
anchored to a list of known ad-network registrable domains, not to arbitrary labels.
**Verify:** unit tests for the URLs above; on device, open `target.com` with the blocker on.

### AB-3 · No reproducible list generator; `$third-party` is ignored at runtime
**Fix:** add `tools/build_adblock_list.py` that:
1. keeps only `||host^` rules with no options, or with options limited to `third-party` and
   resource types;
2. drops rules with `domain=` (positive), `popup`, `badfilter`, `app=`, and hosts covered by an
   `@@||host^` exception;
3. writes a second file (or a flag per host) for `$third-party` rules, so the engine blocks those
   hosts only when the request is not same-site.
Delete the raw `EasyList*.txt` / `adblock_hosts.txt` / `.DS_Store` from `assets/` (keep the
inputs outside `src/main/assets`, e.g. `tools/filterlists/`).
**Verify:** CI step that regenerates the list and fails on diff; AB-1 test.

### AB-4 · Auto-consent hides unrelated page elements and resets page layout
`buildAutoConsentScript` treats any element matching `[aria-label*="cookie" i]`,
`[aria-label*="consent" i]`, `[class*="cookie-banner"]` … as a consent banner. If it cannot find a
"reject" button it hides the element (`display:none`) and calls `unlockScroll()`, which forces
`position: static !important` and `overflow: auto !important` on `<html>` and `<body>`. A recipe
site's "Cookies" navigation, or any app that positions `body`, breaks. `button[mode="secondary"]`
as a "reject" selector clicks arbitrary secondary buttons inside matched containers.
DuckDuckGo uses the maintained `@duckduckgo/autoconsent` rule set with per-CMP detection.
**Fix:** restrict to known CMP containers (OneTrust, Cookiebot, Didomi, Quantcast, iubenda,
TrustArc, Usercentrics); drop the generic `aria-label` / `class*=` selectors and the
`position:static` reset; never hide without a positive CMP detection.

### AB-5 · Click-jacking scan runs on every DOM mutation and hides real overlays
`neutralizeClickjackingOverlays()` runs `querySelectorAll('div, a, span')` plus
`getComputedStyle` + `getBoundingClientRect` for **every element**, on every animation frame that
had mutations. On heavy SPAs (feeds, maps, YouTube) this is a forced-layout loop. It also hides
any transparent, text-less, fixed/absolute element ≥ 70 % of the viewport with `z-index ≥ 999` —
exactly what video players, map gesture layers, lightboxes and carousels use as their tap layer.
**Fix:** delete it. Popup/redirect abuse is already handled in `shouldOverrideUrlLoading` and
`onCreateWindow`.
**Verify (on device):** play a video on a site with a custom player, pan a map; no dead taps.

### WV-1 · A renderer crash leaves destroyed WebViews in the pool
`JestkWebViewClient.onRenderProcessGone` calls `view.destroy()` but never tells `WebViewPool`.
All WebViews of the app share one renderer process, so after an OOM kill the callback fires for
every pooled tab; the pool then keeps handing out up to 5 destroyed WebViews
(`getOrCreateWebView` returns `pool[tabId]` as-is), which render blank and ignore `loadUrl`.
DuckDuckGo handles the callback by recreating the tab's WebView.
**Fix:** call `webViewPool.removeWebView(tabId)` (pool owns destroy) and mark the tab as needing a
reload; the next activation creates a fresh WebView and loads `tab.url`.
**Verify:** `adb shell am kill` is not enough; use `chrome://crash` in a tab (or
`adb shell kill -9 <sandboxed_process pid>`), then switch tabs.

---

## 3. P1 — likely bot-detection signals

The client-hints part (`UserAgentMetadataProvider`, `UserAgentSanitizer`) is correct and matches
DuckDuckGo: native GREASE brand kept, only "Android WebView" is renamed, reduced UA string.
The remaining signals all come from **scripts the app injects into every page — including the
Cloudflare "Just a moment…" interstitial**, which is served on the site's own domain, not on
`challenges.cloudflare.com`, so the exemption list does not cover it. The DOM checks in the
exemption (`window._cf_chl_opt`, `#challenge-stage`) run at document start, before the DOM
exists, so they never match.

### BOT-1 · `ColorSchemeScript` replaces `window.matchMedia`
`buildColorSchemeScript` is evaluated on every page start/finish and on every theme change,
**without the security-challenge exemption**. It:
- replaces `window.matchMedia` with a plain JS function, so
  `Function.prototype.toString.call(matchMedia)` returns source instead of `[native code]`;
- returns a plain object instead of a `MediaQueryList` (`instanceof MediaQueryList === false`);
- leaves three enumerable globals: `__jestkIsDark`, `__jestkThemeListeners`,
  `__jestkOrigMatchMedia`;
- matches any query containing the substring `dark`, so `not (prefers-color-scheme: dark)` is
  wrong.
This contradicts both "DOM Stealth & Function Integrity" rules in `CLAUDE.md`. DuckDuckGo has no
such polyfill.
**Fix:** delete the script. With `targetSdk ≥ 33`, WebView derives `prefers-color-scheme` from the
hosting context's theme (`isLightTheme`); keep `setAlgorithmicDarkeningAllowed` and make the
WebView's context carry the app's night mode (`AppCompatDelegate` / a `ContextThemeWrapper` with
the right `uiMode`). The YouTube `dark` attribute hack goes with it.
**Verify:** section 5 probe — `matchMediaToString` must be native, no `__jestk*` globals; on a
test page, `prefers-color-scheme` follows the in-app toggle.

### BOT-2 · Ad-block stubs and patches on every page
On every site and frame, `buildDocumentStartScript` creates `window.adsbygoogle`,
`window.googletag` (with `apiReady = true` but no real API), `window.yaContextCb`, `window.Ya`,
`window.x5media`, and patches `ScreenOrientation.prototype.lock/unlock`. The `toString` "stealth"
assigns an own `toString` property on the function, which
`Function.prototype.toString.call(screen.orientation.lock)` bypasses. On YouTube,
`JSON.parse`, `Response.prototype.json/text`, `history.pushState/replaceState` and the XHR
getters are replaced with non-native functions, **even when the ad blocker is disabled**.
**Fix:**
- install surrogates only when the matching network request was actually blocked (the
  `createSmartBlockedResponse` JS stubs already do this — the page-level copies are redundant);
- skip the main frame of a challenge page by checking the response, not the DOM (Cloudflare
  interstitials return `cf-mitigated: challenge`; an `onPageStarted` exemption can key off the
  previous blocked/403 state), or simply never inject before `DOMContentLoaded` on a page whose
  title is "Just a moment...";
- wrap patched natives with a proper `toString` proxy (DuckDuckGo content-scope-scripts
  `DDGProxy` pattern: patch `Function.prototype.toString` once, via a `WeakMap` of wrapped
  functions);
- gate the YouTube script on the ad-blocker toggle.

### BOT-3 · JS bridges are reachable by every origin and frame
`HoriAdBlockBridge` (`isEnabled`, `openCleanPlayer(url)`, `lockOrientation`, `unlockOrientation`)
and `JestkBlobBridge.onBlobDownloaded(dataUrl, …)` are registered with `addJavascriptInterface`,
so any page or third-party iframe can call them: rotate the screen, open the clean player with an
arbitrary URL, or start a download of arbitrary content without a user gesture. They are also
visible as `window.HoriAdBlockBridge` (a known WebView fingerprint).
DuckDuckGo uses `WebViewCompat.addWebMessageListener` with `allowedOriginRules` for privileged
messaging.
**Fix:** move to `addWebMessageListener`; check `isMainFrame` and the source origin in the
listener; require that a blob download was announced by `DownloadListener` first (match the blob
URL) instead of accepting any `dataUrl`.

### BOT-4 · Headers / platform signals — measured on device
See section 5 (X-Requested-With, `window.chrome`, Notification permission).

---

## 4. P1/P2 — WebView core

### WV-2 · WebViews are never paused **(P1)**
No code calls `WebView.onPause()` or `pauseTimers()` for the main WebViews (only the clean-player
overlay does). Background tabs keep running JS, timers, media and network forever; when the app
goes to the background all 5 pooled WebViews keep running.
**Fix:** `onPause()` every non-active WebView in `WebViewPool.onTabActivated`; in the activity's
`onPause`/`onResume`, call `pauseTimers()`/`resumeTimers()` (global) plus `onPause()`/`onResume()`
on the active WebView, keeping media-playing tabs alive only if background playback is a feature.

### WV-3 · Two divergent client builders **(P1)**
`JestkWebViewClient` + `JestkWebChromeClient` are built in `JestkApp.kt:186-295`
(`onWebViewCreated`) and in `WebViewContainer.kt:272-370`. The `JestkApp` copy runs first and sets
`tag` + `webChromeClient`, so the `WebViewContainer` copy only runs in edge cases. They differ
(insecure-host check via `viewModel.isInsecureHostAllowed` vs `allowedInsecureHosts.contains`,
file-chooser fallback, profile fallback), and both capture `isDark` in a non-updating closure
(`LaunchedEffect(webViewPool, viewModel)` / unkeyed `remember`), so the injected colour scheme
uses the theme from first composition.
**Fix:** one `WebViewClientFactory` (engine or ui/views) that takes a small interface backed by
the ViewModel and reads live state; delete the other copy.

### WV-4 · Downloads silently dropped after `WebViewPool.shutdown()` **(P1)**
`handleDownload` and `handleDataUrlDownload` launch on `coroutineScope` directly
(`WebViewPool.kt:592, 637`) instead of `activeScope()`, contradicting the KDoc of `shutdown()`.
**Fix:** use `activeScope()`.

### WV-5 · Redirect protection cancels legitimate cross-site redirects **(P1, unverified)**
Any main-frame cross-site navigation without `hasGesture()` is cancelled unless the source is a
search engine or the target is an auth host. JS/meta redirects (3-D Secure bank pages, SSO
hand-offs, link shorteners, "continue to site" pages) have no gesture. `request.isRedirect` is
not considered.
**Fix:** only treat a navigation as an ad redirect when the target host is in the ad list, or the
navigation happens in a popup opened without a gesture; allow server redirects
(`isRedirect == true`) of a navigation that itself was allowed.
**Verify (on device):** a 3-D Secure test payment; `t.co` link after AB-1.

### WV-6 · Smaller WebView issues (P2)
- `WebViewPool.getOrCreateWebView`: the 35-line WebView setup is duplicated for the fallback
  path; when the first attempt throws after construction, the first WebView is leaked (never
  destroyed). Extract `createConfiguredWebView(context)`.
- `JestkWebViewClient.onPageStarted/onPageFinished`: `validUrl.contains("apple.com")` /
  `contains("youtube.com")` hard-coded theme colours match any URL containing the text (e.g. a
  search for "apple.com"). Use the parsed host, or drop the special cases.
- The ad-block script string (~50 KB) is rebuilt by `buildDocumentStartScript()` on every page
  start and finish. Build it once (`by lazy`).
- `evaluateJavascript` in `onPageStarted` can run in the previous document. With
  `DOCUMENT_START_SCRIPT` supported (all current WebViews), the `evaluateJavascript` re-injection
  is redundant; keep it only as a fallback when the feature is missing.
- `checkExternalAppOffer` runs twice per navigation (`onPageStarted` and
  `doUpdateVisitedHistory`).
- `JestkWebViewClient.telemetryScope` is unused; the deprecated
  `shouldOverrideUrlLoading(view, String)` overload is never called on `minSdk 26` — remove both.
- Toasts in `WebViewPool` ("Downloading …", "Download failed: …", "Preparing download...") are
  hard-coded English.
- `TabCoordinator.kt:1570` creates the Home tab without `workspaceId`, so it lands in the default
  workspace when a folder is collapsed in another workspace.

### DL / DATA · Downloads and data layer (verified by hand)
- **DL-1 (P1)** `HoriDownloadManager.kt:328` — on HTTP 416 during resume the code calls
  `performDownload(...)` recursively from inside the running job; the guard at `:270`
  (`activeJobs[id]?.isActive == true`) makes that call a no-op. The row stays `DOWNLOADING`
  forever, neither callback fires, and the foreground service never stops.
  **Fix:** loop inside the job (`while (true) { … if (416) { reset; continue } … }`) instead of
  recursing.
- **DL-2 (P1)** `HoriDownloadManager.kt:332-336` — the non-2xx `throw` happens before the
  `try/finally` that closes `response`, so every failed download leaks an OkHttp connection.
  **Fix:** `response.use { … }` around the whole body handling.
- **DL-3 (P2)** `HoriDownloadManager.kt:343-345, 377` — resume answered with `200` truncates the
  part file but keeps `downloadedBytes`, so progress exceeds the real size. Reset
  `downloadedBytes = 0` whenever the response is not `206`.
- **DL-4 (P2)** `atomicMoveTo` result ignored (`HoriDownloadManager.kt:524`,
  `SnapshotEngine.kt:223`) — a failed move is reported as success.
- **DATA-1 (P1, resolved by deletion — see §6)** `HistoryDao.kt:179` — `DELETE FROM history WHERE url = (SELECT url … WHERE
  id = :id)` deletes that URL from **every** workspace. Add `AND workspaceId = (SELECT
  workspaceId …)`.
- **DATA-2 (P2)** `RecentTabEntity` primary key is `url` only, although rows carry
  `workspaceId`; the same URL in two workspaces overwrites one row. Needs a migration to a
  composite key `(url, workspaceId)`.
- **DATA-3 (P2)** Settings are parsed twice (`SettingsRepositoryImpl.getCurrentSettings` vs
  `Mappers.toBrowserSettings`); only one applies the `CLEAN_PLAYER_FORCED_OFF_V1` migration.
- **DATA-4 (P2)** Three different "is this a search engine host" lists
  (`SearchEngine.matchesUrl`, `UrlSecurityUtils.SEARCH_ENGINE_DOMAINS`,
  `ExternalAppResolver.SEARCH_ENGINE_HOSTS`) give different answers.
- **DATA-5 (P2)** `ExternalAppResolver.resolve` does PackageManager IPC (+ icon decode) inside
  `shouldOverrideUrlLoading` on the main thread.
- **DATA-6 (P2)** English sentinels stored as data and compared in logic: tab title `"Home"` /
  `"New Tab"`, workspace names `"Personal"`, `"Work"`, `"Workspace N"`. A real page titled
  "Home" is treated as the home tab. Replace with `Tab.isHomeTab` (already exists) and resolve
  display strings from resources at render time.

### UI · UI layer (from the agent sweep; items marked ✔ re-checked by hand)
- **UI-1 (P2) ✔** `SearchEngineCarousel.kt:336, 360` — `onEngineSelected` fires twice on
  tap-to-select (before and after the animation).
- **UI-2 (P2) ✔** `BlockedAdNavigationDialog.kt:91-96` — the 5 s auto-dismiss checks
  `isDragging` once; a banner held at expiry never dismisses.
- **UI-3 (P2)** `TabCoordinator` — several `updateUiState { state -> … }` lambdas ignore `state`
  and write a `tabs` list computed outside the update (`:494-501, :541-549, :620-627, :713-716,
  :797-808, :932-933`), so concurrent DB/favicon/title updates can be lost.
- **UI-4 (P2)** `SearchOverlay.kt:285-301` — the load-more `LaunchedEffect` reads
  `uiState.searchHistorySuggestions` / `onEvent` without `rememberUpdatedState`.
- **UI-5 (P2)** `File(path).exists()` in composition (`TabOverviewGridCard.kt:228`,
  `TabOverviewGridDragGhost.kt:184, 192`, `FolderOverviewGridCard.kt:217, 227`,
  `WorkspaceOverviewCard.kt:210`) — main-thread disk I/O on every recomposition.
- **UI-6 (P2) ✔** `ActionDock.kt:273-281` — production code detects Robolectric via
  `Class.forName`; use `LocalInspectionMode` or an injected flag.
- **UI-7 (P2) ✔** Hard-coded user-facing strings: `ActionDock` menu labels, toasts in
  `ActiveTabPill`/`TabStripRow`/`HomeScreen`/`SettingsScreen`/`SearchInputPill`/`ClipboardHelper`,
  `ProstoRecentTabCard.formatRelativeTime` ("Just now", "Yesterday"), `TabOverviewGrid` tab
  counter, `HoriDownloadService` notifications, `FileSizeFormatter` units.
- **UI-8 (P2)** Russian code comments in `SettingsScreen.kt`, `BlockedAdNavigationDialog.kt`,
  `ImageContextMenuModal.kt`, `ExternalAppNotificationPill.kt`.

---

## 5. On-device fingerprint comparison (Prosto vs Chrome)

Device: motorola edge 60 fusion, WebView 156.0.8078.47. Probe:
`scratchpad/probe.js` via CDP (`webview_devtools_remote_<pid>` / `chrome_devtools_remote`).

Pages: `https://httpbin.org/headers` (server-side view) and `https://example.com/` (JS view).
Prosto = debug build on WebView 156.0.8078.47; Chrome = 157.0.8103.50.

| Signal | Prosto | Chrome | Verdict |
|---|---|---|---|
| `X-Requested-With` request header | `com.jestk.browser.debug` | absent | **ours, fix (BOT-4)** |
| `Sec-CH-UA` / `userAgentData` brands, full version list | `Not:A-Brand;8`, `Chromium;156`, `Google Chrome;156` | `Not-A.Brand;99`, `Google Chrome;157`, `Chromium;157` | OK — GREASE differs per build, structure matches |
| `User-Agent` | reduced `Android 10; K … Chrome/156.0.0.0` | same format | OK |
| `matchMedia` toString / `instanceof MediaQueryList` | JS source / `false` | `[native code]` / `true` | **ours, fix (BOT-1)** |
| `screen.orientation.lock` toString | JS source; own `toString` prop | `[native code]` | **ours, fix (BOT-2)** |
| Extra `window` props vs a clean iframe | `HoriAdBlockBridge`, `JestkBlobBridge`, `__hori_adblock_initialized`, `adsbygoogle`, `googletag`, `yaContextCb`, `Ya`, `x5media`, `__jestkIsDark`, `__jestkThemeListeners`, `__jestkOrigMatchMedia`, `__hori_autoconsent_initialized` | none | **ours, fix (BOT-1/2/3)** — note `defineProperty(enumerable:false)` does not hide a name from `getOwnPropertyNames` |
| `<style id="__hori_adblock_css">` in DOM | present | absent | ours, minor — use a nameless style or `adoptedStyleSheets` |
| `window.chrome` | `undefined` | `{loadTimes, csi}` | WebView-inherent. Do **not** fake it (a synthetic `window.chrome` is a classic, detectable stealth artefact) |
| `Notification` API / `permissions.query('notifications')` | missing / `denied` | `default` / `prompt` | WebView-inherent, same in DuckDuckGo |
| `navigator.languages` | `en-GB, ru-RU, en-US` (system locales) | `en-US, en, ru` (Chrome setting) | OK — differs per user anyway |

### BOT-4 · `X-Requested-With: <package>` on every request (P1)
Android WebView sends the app's package name in `X-Requested-With` on every request. Real Chrome
never sends it, so any WAF can tell "embedded WebView" from one header, before any JS runs.
**Fix:** `WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())` when
`WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST` is supported.
**Verify:** `httpbin.org/headers` shows no `X-Requested-With`.

---

## 6. Cleanup (dead code, duplicates) — applied

Landed as a separate `refactor(cleanup)` commit; `tools/verify.sh --all` green.

**Removed**
- Files: `DownloadsSheet.kt`, `SettingsSheet.kt`, `CleanPlayerFab.kt`, `SettingsInteractionSection.kt`.
- Composables/overloads: `ActiveTabPill(tab, displayTitle, displayDomain, …)`, `ActiveTabCenterCapsule`,
  `BottomBar()`, `DualTierBottomBar()`, `specularBorder`, `Modifier.prostoCardSurface`,
  `shouldRemoveTabFromFolderOnDrop`.
- Functions: `TabCoordinator.reorderTabs`, `UpdateCoordinator.onCheckNow`, top-level
  `DownloadsScreen.openDownloadedFile`, `UserAgentMetadataProvider.buildMetadata`,
  `FaviconManager.getApplicationContext`, `JestkWebViewClient.telemetryScope` and the deprecated
  `shouldOverrideUrlLoading(WebView, String)` overload (two tests moved to the request overload).
- The whole per-item history delete chain (`BrowserUiEvent.DeleteHistoryItem` →
  `SearchCoordinator.deleteHistoryItem` → `HistoryRepository.deleteHistory/deleteHistoryItem` →
  `HistoryDao.deleteHistoryById/deleteHistory`). No UI ever sent the event, so **DATA-1 is resolved
  by deletion**; a future "delete history item" feature must scope the query by `workspaceId`.
- UI events: `OnFaviconReceived`, `DownloadStarted`, `SelectHistorySuggestion`.
- State/model aliases: `BrowserUiState.bottomBarVisible/loadProgress`,
  `BrowserSettings.adBlockerEnabled`, `SearchEngine.iconAssetUrl`.
- Repository methods: `FavoriteRepository.removeFavorite`, `RecentTabRepository.updateTitle`,
  `SettingsRepository.getAutoConsentEnabled`.
- DAO methods: `FavoriteDao.isFavoriteFlow/updateFavorite/clearFavoritesByWorkspace/clearAllFavorites`,
  `SettingDao.clearAllSettings/getBlockedAdsCountFlow/getBlockedTrackersCountFlow`,
  `TabDao.getActiveTabFlow/clearActiveFlags/markActive/getTabsByFolderIdFlow`.
- Parameters: `ActiveTabPill(onSwipeNext, onSwipePrev)`, `TabStripRow(dockBg)`,
  `WebViewPool.updateThemeMode(context)`.
- 40 unused colour/brush tokens in `ui/theme/Color.kt`; 9 unused string resources (both locales);
  101 unused imports.

**Kept on purpose**
- Code used only by tests (`AdBlockerEngine.isReady/containsHost/awaitInitialization`,
  `buildSurrogateScript`, `BrowserViewModel.incrementBlockedAds/Trackers`,
  `FavoriteRepository.getFavoritesLimited`, `WorkspaceRepository.updateWorkspacePositions`,
  `DownloadRepository.clearAllDownloads`, …): removing it means rewriting tests; handle when the
  owning area is touched.
- `workspace_personal`, `workspace_work`, `workspace_tabs_count` strings: they are the missing
  localizations for UI-7 / DATA-6.
- Room `Converters`: referenced by `@TypeConverters`; drop together with the annotation in a
  schema change, not in a cleanup.
- Raw filter lists in `assets/`: removed together with AB-3.
- Agent false positives (still used): `TabCoordinator.reorderTabsAndFolders`,
  `DownloadFileOpener.openDownloadedFile`.

**Duplicates worth merging** (fix tickets, not mechanical cleanup): WebView client builders (WV-3),
ad-block surrogate JS (3 copies), host-extraction helpers (5 copies), search-engine host lists
(DATA-4), favicon domain guard in `TabCoordinator` (3 copies), close-tab branches in
`TabCoordinator` (3 copies), swipe-to-dismiss in `BlockedAdNavigationDialog` /
`ExternalAppNotificationPill`, thumbnail/favicon rendering in overview cards (4 copies),
`"default_personal"` literal (25 places; `Workspace.DEFAULT_PERSONAL_ID` exists).

---

## 7. Mechanical prevention (add to `tools/check_invariants.py`)

1. **No `window.matchMedia =`, `JSON.parse =`, `history.pushState =` assignments** in Kotlin
   string literals outside an allow-listed, proxy-wrapped helper.
2. **No `addJavascriptInterface`** outside an allow-list (clean-player overlay, which loads only
   our own page).
3. **No enumerable `window.__*` globals**: forbid `window.__` assignments in injected scripts;
   require `Object.defineProperty(..., { enumerable: false })`.
4. **Ad list test**: unit test loads `assets/adblock_domains.txt` and asserts a "never block" set
   (`amazonaws.com`, `imgur.com`, `t.co`, `bit.ly`, `target.com`, `cdn77.org`,
   `files.wordpress.com`, main-frame of any site).
5. **No raw filter lists under `src/main/assets`.**
6. **Single client builder**: forbid `JestkWebViewClient(` construction outside the factory.
