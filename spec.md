# Jestk Browser for Android — Technical Specification (`spec.md`)

## 1. System Overview &amp; Tech Stack
* **Platform:** Android (Min SDK 26 / Target SDK 34+)
* **Language:** Kotlin
* **UI Toolkit:** Jetpack Compose + Material 3
* **Rendering Engine:** Android System `WebView` (integrated into Compose via `AndroidView`)
* **Persistence:** Room Database (State, Tabs, History, Favorites, Settings)
* **Architecture:** MVVM with Clean Architecture principles and unidirectional data flow (StateFlow)
* **Design Language:** Safari-inspired iOS minimalist aesthetic (subtle rounded containers, blur/surface tonality, bottom-heavy control hierarchy).

---

## 2. Core UI Layout Architecture

The screen is partitioned into two functional layers:



| WebView Container |

| (or Home / Grid / Settings) |



| [Tab Strip: (o) ( Active Tab 2/4 ) (o)]| &lt;- Top Tier

| [ (Home) (Search) (Grid) (Gear) ] | &lt;- Bottom Tier



### 2.1 Dynamic Auto-Hide Behavior  
* Scrolling down on the `WebView` smoothly translates the entire bottom navigation bar downward out of view.  
* Scrolling up smoothly reveals the navigation bar.  
  
  
  
## 3. Navigation Bar (Dual-Tier Bottom Bar)  
  
### 3.1 Tier 1: Horizontal Tab Strip  
A horizontally scrollable row (`LazyRow`) hosting all open sessions directly above the action buttons.  
* **Active Tab (Pill):**  
  * Occupies **2/4 (50%)** of the available screen width.  
  * Displays: Site Favicon (left) + Title / Hostname (centered, truncated with ellipsis).  
  * Single tap: Opens the in-place URL editor.  
  * Swipe left/right on the active tab: Switches instantly to the adjacent tab.  
* **Inactive Tabs (Circles):**  
  * Rendered as fixed-size circular buttons showing only the site's Favicon.  
  * Positioned dynamically to the left and right of the active tab, matching their visual creation order.  
  * Single tap: Switches active focus to that tab and scrolls it into view.  
  * Long press: Displays a contextual popup menu (`Close Tab`, `Copy URL`, `Add to Favorites`).  
* **Trailing Action:** A persistent `+` icon button at the end of the scroll row to spawn a blank tab.  
  
### 3.2 Tier 2: Persistent Action Bar  
A grounded dock housing 4 primary icon buttons:  
* **Home:** Switches the active viewport to the Home Dashboard view.  
* **Search:** Launches the Search Overlay with immediate input autofocus.  
* **All Tabs (Grid):** Opens the 3-column Visual Tab Overview.  
* **Settings:** Opens the Settings sheet.  
  
---  
  
## 4. Views &amp; Overlays  
  
### 4.1 Home Screen  
Replaces the `WebView` viewport when triggered or on a fresh blank tab.  
* **Recent Tabs Section (Top):**  
  * Horizontal carousel (`LazyRow`) of the **6 most recently active tabs**.  
  * Displays thumbnail card + favicon + page title.  
* **Favorites Section (Below Recents):**  
  * Responsive 2D Grid displaying bookmarked items.  
  * Hard limit of **6 items** in default view.  
  * If items &gt; 6: Appends a "Show All Favorites" button below the grid to expand.  
  
### 4.2 Search Overlay  
A focused modal overlay sliding over the active content.  
* **Input Box:** Positioned vertically slightly below the screen midpoint (`bias: 0.55`).  
* **Autofocus:** Automatically opens the soft keyboard and requests input focus on launch.  
* **Search History Stack:**  
  * Rendered directly **above** the input box.  
  * Vertical list (`LazyColumn`) scrolling upward and fading toward the top edge.  
  * Tap on an item populates the query.  
* **Default Search Query:** Dispatches queries to DuckDuckGo (`https://duckduckgo.com/?q={query}`) unless overridden in Settings.  
  
### 4.3 All Tabs Overview (Visual Grid)  
A full-screen overview displaying snapshots of all active browser instances.  
* **Layout:** Vertical grid with exactly **3 columns**.  
* **Card Structure:** Scaled real-time bitmap capture of the `WebView` canvas with a footer containing the Favicon, title, and an explicit close (`x`) button.  
* **Gestures:** Horizontal swipe-to-dismiss on individual cards to close tabs.  
  
### 4.4 Settings View  
Simple settings sheet:  
* **Profile:** Placeholder section (`Coming Soon`).  
* **Theme:** Selector with options: `System Default` (Default), `Light`, `Dark`.  
* **Search Engine:** Selector with options: `DuckDuckGo` (Default), `Google`, `Bing`.  
* **Hyperlinks:** Clickable internal link buttons:  
  * `GitHub` (placeholder URL)  
  * `Jestk.com` (opens `https://jestk.com` in a new tab)  
  
---  
  
## 5. Web Platform Features &amp; Systems  
  
### 5.1 Native Ad &amp; Tracker Blocker  
* Implemented via `WebViewClient.shouldInterceptRequest(view, request)`.  
* Injects a local compiled blocklist (e.g., EasyList hostnames loaded into a HashSet/Bloom Filter).  
* Intercepts `request.url.host`: if matched against the blacklist, returns an empty `WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))`.  
  
### 5.2 Video Mini-Player (Picture-in-Picture)  
* **Custom Fullscreen Handling:** Overrides `WebChromeClient.onShowCustomView` and `onHideCustomView`.  
* **Picture-in-Picture (PiP):** Supports system-level Android PiP (`enterPictureInPictureMode`) when backgrounding the app or switching tabs while HTML5 `&lt;video&gt;` playback is active.  
  
### 5.3 Navigation &amp; State Persistence  
* **Back Button Handling:**  
  * Uses `OnBackPressedDispatcher`.  
  * If an overlay (Search, Grid, Settings) is open: Dismisses the overlay.  
  * Else if the active `WebView` has backward history (`webView.canGoBack()`): Calls `webView.goBack()`.  
  * Else: Closes the current active tab.  
* **Pull-to-Refresh:**  
  * Native Compose `PullRefreshIndicator` wrapping the `WebView` container.  
* **State Persistence:**  
  * Tab schemas, URLs, titles, and layout order are continuously mirrored in Room DB.  
  * App cold starts automatically restore the previous browsing session without losing tabs.  


