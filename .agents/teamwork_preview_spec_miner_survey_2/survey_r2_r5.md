# Specification Mining Report: R2 (Dual-Tier Bottom Bar & Auto-Hide) and R5 (Views & Overlays)

**Project:** Jestk Browser for Android  
**Target Platform:** Android (Min SDK 26, Target SDK 35 / JVM 17)  
**UI Stack:** Kotlin + Jetpack Compose + Material 3 + Android System WebView + Room DB  
**Authoritative Sources:** `/Users/ilya/CODING/browser/ORIGINAL_REQUEST.md`, `/Users/ilya/CODING/browser/spec.md`  
**Date:** 2026-09-02T19:15:00Z  

---

## Executive Summary

This report establishes the complete, authoritative specification for the UI, interaction models, gesture systems, view overlays, and dynamic navigation mechanisms of **Jestk Browser for Android**, specifically covering:
1. **R2: Dual-Tier Bottom Navigation Bar & Dynamic Auto-Hide**:
   - Tier 1: Horizontal Tab Strip (`LazyRow`) containing the 50% width active tab pill, circular inactive tab buttons, and trailing `+` blank tab button.
   - Tier 2: Persistent 4-button action dock (Home, Search, All Tabs Grid, Settings) with standard Material 3 icons.
   - Dynamic Auto-Hide Engine: Scroll delta translation tracking WebView scroll down/up using `NestedScrollConnection`.
2. **R5: Views & Overlays**:
   - **Search Overlay**: Vertical bias 0.55 modal with soft keyboard autofocus via `SoftwareKeyboardController.show()` in `LaunchedEffect(Unit)`, upward-scrolling fading history stack (`LazyColumn`), and configurable search engine query generation (DuckDuckGo default).
   - **All Tabs Overview (Visual Grid)**: Full-screen 3-column vertical grid (`GridCells.Fixed(3)`) with real-time bitmap snapshot cards, footer metadata (favicon, title, close button), and horizontal swipe-to-dismiss gesture.
   - **Home Screen**: 6-card horizontal recent tabs carousel (`LazyRow`), favorites responsive grid with 6-item default limit and "Show All Favorites" button, clean empty state bookmark prompt.
   - **Settings Sheet**: Theme selector (`System Default`, `Light`, `Dark`), Search engine selector (`DuckDuckGo`, `Google`, `Bing`), Ad blocker toggle, and clickable internal/external links (`GitHub`, `Jestk.com`).
   - **System Gestures & Lifecycle**: Pull-to-refresh integration at `scrollY == 0` and strict multi-tier back button dispatcher hierarchy.

---

## Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | R2 Tab Strip | Active Tab Pill (50% Width) | Centered pill occupying exactly 2/4 (50%) screen width with site favicon (left) and truncated title/domain (center). | Screen width, active `TabSession` (favicon bitmap, title, url). | Rendered pill with elevation/border, favicon image, single-line text with ellipsis. | If favicon is null, renders fallback Web/Globe vector icon; if title empty, displays hostname or "New Tab". | `spec.md` §3.1, `ORIGINAL_REQUEST.md` R2 |
| 2 | R2 Tab Strip | Active Pill Single Tap | Single tap on active pill opens in-place Search Overlay pre-filled with the active tab's current URL. | Tap pointer event on active pill. | Triggers `UIOverlayState.Search(prefilledUrl = tab.url, origin = ActivePill)`. | If URL is blank/about:blank, pre-fills empty string. | `spec.md` §3.1, `ORIGINAL_REQUEST.md` R2 |
| 3 | R2 Tab Strip | Active Pill Swipe Navigation | Horizontal swipe left or right on the active pill instantly switches to the next or previous adjacent open tab. | Drag gesture (`detectHorizontalDragGestures` / drag delta exceeding swipe threshold e.g. 50dp). | Switches `activeTabIndex` to `activeTabIndex - 1` (swipe right) or `activeTabIndex + 1` (swipe left), animates tab strip. | Bound-checked; no-op if swiping left on last tab or right on first tab (with subtle bounce/overscroll resistance). | `spec.md` §3.1, `ORIGINAL_REQUEST.md` R2 |
| 4 | R2 Tab Strip | Inactive Tab Circular Buttons | Inactive tabs rendered as fixed-size circular buttons displaying only the site favicon. | Inactive `TabSession` items from tabs list. | Circular surface with centered favicon bitmap or fallback icon. | Missing favicon renders default placeholder icon inside circular surface. | `spec.md` §3.1, `ORIGINAL_REQUEST.md` R2 |
| 5 | R2 Tab Strip | Inactive Tab Single Tap | Single tap on inactive tab circular button switches active tab focus to that tab and smoothly scrolls it into view. | Tap pointer event on inactive tab button. | Updates `activeTabId` in state, scrolls `LazyRow` state to center/reveal active tab. | If tab ID not found in state, ignores click. | `spec.md` §3.1, `ORIGINAL_REQUEST.md` R2 |
| 6 | R2 Tab Strip | Inactive Tab Long Press Menu | Long press on inactive tab displays contextual popup menu with 3 actions: `Close Tab`, `Copy URL`, `Add to Favorites`. | Long-press pointer event on inactive tab button. | Contextual `DropdownMenu` / popover anchored to the tab button. | Menu auto-dismisses on outside tap or back press. | `spec.md` §3.1, `ORIGINAL_REQUEST.md` R2 |
| 7 | R2 Tab Strip | Inactive Tab Menu Action: Close Tab | Closes the target inactive tab from the context menu, freeing/recycling its WebView instance and updating Room DB. | Menu item click (`Close Tab`). | Tab removed from tabs list; DB row deleted; tab strip animated re-layout. | If closing last remaining tab, spawns new blank tab. | `spec.md` §3.1 |
| 8 | R2 Tab Strip | Inactive Tab Menu Action: Copy URL | Copies the target inactive tab's URL to Android `ClipboardManager`. | Menu item click (`Copy URL`). | URL string copied to system clipboard; toast or snackbar confirmation. | Handles empty/null URL by copying empty string or showing error toast. | `spec.md` §3.1 |
| 9 | R2 Tab Strip | Inactive Tab Menu Action: Add to Favorites | Adds the target inactive tab's URL, title, and favicon to Room DB Favorites table. | Menu item click (`Add to Favorites`). | Inserts `FavoriteEntity` into DB; displays confirmation feedback. | If URL already exists in favorites, updates timestamp or ignores duplicate silently. | `spec.md` §3.1 |
| 10 | R2 Tab Strip | Trailing New Tab (`+`) Button | Persistent `+` icon button at the end of the `LazyRow` scroll row to spawn a blank tab. | Click event on `+` button. | Creates new `TabSession(url = "about:blank" or Home)`, sets as active tab, scrolls tab strip to end. | DB insert failure logged and handled gracefully in ViewModel. | `spec.md` §3.1, `ORIGINAL_REQUEST.md` R2 |
| 11 | R2 Tab Strip | Dynamic Positioning Order | Inactive tabs are positioned dynamically to the left and right of the active tab matching visual creation order. | Ordered list of `TabSession` in UI state. | `LazyRow` items rendered in exact tab index order: tabs `[0..active-1]`, active pill, tabs `[active+1..N-1]`, then `+` button. | List updates smoothly animated via Compose `Modifier.animateItemPlacement()`. | `spec.md` §3.1 |
| 12 | R2 Action Bar | Persistent Dock Layout | Grounded bottom dock housing 4 primary icon buttons: Home, Search, All Tabs (Grid), Settings. | Bottom bar container state, current overlay state. | Material 3 surface with 4 evenly spaced icon buttons. | Dock stays grounded at screen bottom above navigation bar insets. | `spec.md` §3.2, `ORIGINAL_REQUEST.md` R2 |
| 13 | R2 Action Bar | Home Button | Tapping Home button switches the active viewport to Home Dashboard view (or loads Home on active tab). | Click on Home icon button (`Icons.Default.Home`). | Sets `isHomeActive = true` or `UIOverlayState.Home`, displaying Home Screen. | If already on Home Screen, scrolls Home view to top. | `spec.md` §3.2, `ORIGINAL_REQUEST.md` R2 |
| 14 | R2 Action Bar | Search Button | Tapping Search button launches the Search Overlay with blank/new query and immediate autofocus. | Click on Search icon button (`Icons.Default.Search`). | Sets `UIOverlayState.Search(prefilledUrl = "", origin = ActionDock)`. | Triggers keyboard show immediately. | `spec.md` §3.2, `ORIGINAL_REQUEST.md` R2 |
| 15 | R2 Action Bar | All Tabs (Grid) Button | Tapping Grid button opens the 3-column Visual Tab Overview modal screen. | Click on Grid icon button (`Icons.Default.GridView`). | Sets `UIOverlayState.TabsGrid`. | Renders full-screen 3-column grid of live tab snapshot cards. | `spec.md` §3.2, `ORIGINAL_REQUEST.md` R2 |
| 16 | R2 Action Bar | Settings Button | Tapping Settings button opens the Settings sheet. | Click on Settings icon button (`Icons.Default.Settings`). | Sets `UIOverlayState.Settings` (modal bottom sheet or dialog). | Sheet slides up smoothly. | `spec.md` §3.2, `ORIGINAL_REQUEST.md` R2 |
| 17 | R2 Auto-Hide | Dynamic Scroll Translation | Smoothly translates entire dual-tier bottom bar downward out of view on WebView downward scroll, reveals on upward scroll. | `NestedScrollConnection.onPreScroll` delta (`available.y`), active WebView scroll events. | `offsetY` pixel translation applied via `Modifier.offset { IntOffset(0, offsetY) }` clamped between 0 and `barHeightPx`. | If page cannot scroll (content height <= viewport), translation remains 0 (fully visible). Reset to 0 when overlay opens. | `spec.md` §2.1, `ORIGINAL_REQUEST.md` R2 |
| 18 | R5 Search | Slide-in Modal at Bias 0.55 | Search modal overlay positioning input box vertically slightly below screen midpoint (`verticalBias = 0.55`). | Search overlay activation event. | Modal container with input box aligned at vertical bias 0.55 of available height. | Adapts safely to IME window insets so input stays visible above software keyboard. | `spec.md` §4.2, `ORIGINAL_REQUEST.md` R5 |
| 19 | R5 Search | Soft Keyboard Autofocus | Automatically requests input focus and shows soft keyboard immediately upon Search Overlay launch. | `LaunchedEffect(Unit)` on overlay composition. | `focusRequester.requestFocus()` + `softwareKeyboardController.show()`. | Uses explicit `SoftwareKeyboardController.show()` to avoid Android Compose focus-without-keyboard bug. | `spec.md` §4.2, `ORIGINAL_REQUEST.md` R5 |
| 20 | R5 Search | Upward-Scrolling Fading History Stack | Search history stack list (`LazyColumn`) rendered directly ABOVE input box, scrolling upward and fading toward the top edge. | History records from Room DB History table (`HistoryEntity`). | Upward `LazyColumn` with top alpha gradient fade (`graphicsLayer` / `Brush.verticalGradient`). | Empty history shows subtle "No recent searches" placeholder or empty space. | `spec.md` §4.2, `ORIGINAL_REQUEST.md` R5 |
| 21 | R5 Search | History Item Tap | Tapping any history item in the stack populates query or navigates immediately. | Tap on history item row. | Updates search input text or immediately dispatches search/navigation and closes overlay. | Handles special characters and encoded URLs properly. | `spec.md` §4.2 |
| 22 | R5 Search | Search Engine Dispatch (DuckDuckGo Default) | Dispatches search query to DuckDuckGo template (`https://duckduckgo.com/?q={query}`) unless overridden in Settings. | Raw text query string. | URL formatted as `https://duckduckgo.com/?q=<encoded_query>`, active tab navigates to URL. | URL query parameter properly URL-encoded with `URLEncoder.encode(query, "UTF-8")`. | `spec.md` §4.2, `ORIGINAL_REQUEST.md` R5 |
| 23 | R5 Search | URL vs Search Query Detection | Evaluates input to determine if it is a direct URL (e.g. `example.com`, `http://...`, `https://...`, `localhost`) or a search phrase. | Input string from search bar. | Direct URL: loads `https://<input>` directly; Search phrase: formats using search engine URL template. | Malformed URL falls back to search engine query. | `spec.md` §4.2 |
| 24 | R5 Search | History Persistence on Search | Every submitted search query and URL navigation is persisted to Room DB History table. | Submitted query / visited URL, title, timestamp. | Room DB `HistoryDao.insert(HistoryEntity(...))`. | Duplicate queries update access timestamp or create new history entry. | `ORIGINAL_REQUEST.md` R1, R5 |
| 25 | R5 Tab Grid | 3-Column Visual Tab Overview | Full-screen visual tab overview displaying live/cached bitmap snapshots in exactly 3 columns. | Tab list from `TabViewModel` / Room DB with snapshot file paths. | `LazyVerticalGrid(columns = GridCells.Fixed(3))` rendering cards. | If snapshot missing, renders placeholder background with domain/title text. | `spec.md` §4.3, `ORIGINAL_REQUEST.md` R5 |
| 26 | R5 Tab Grid | Snapshot Card Footer | Footer on each tab card displaying site favicon, truncated title, and an explicit close (`x`) icon button. | `TabSession` metadata. | Card footer with favicon image, text label, and close `IconButton`. | Title truncated with ellipsis; close button has 48dp minimum touch target area. | `spec.md` §4.3, `ORIGINAL_REQUEST.md` R5 |
| 27 | R5 Tab Grid | Horizontal Swipe-to-Dismiss | Horizontal swipe gesture on individual tab cards in the 3-column grid closes the tab. | Horizontal drag/swipe gesture on card (`SwipeToDismissBox` / custom pointerInput). | Tab closed with animated exit transition, snapshot file unlinked, DB record deleted. | If active tab closed, activates next tab or spawns new blank tab. | `spec.md` §4.3, `ORIGINAL_REQUEST.md` R5 |
| 28 | R5 Tab Grid | Tab Selection from Grid | Single tap on any tab card in the grid selects that tab and returns to the WebView browsing view. | Tap on tab card. | Sets `activeTabId`, closes All Tabs Overview, focuses selected tab WebView. | Restores previous scroll position and live state instantly. | `spec.md` §4.3 |
| 29 | R5 Home | Recent Tabs Carousel (Top) | Horizontal carousel (`LazyRow`) at top of Home Screen showing up to 6 most recently active tabs. | Top 6 tabs sorted by `lastAccessedTimestamp` desc. | Horizontal row of preview thumbnail cards with favicon and title. | If fewer than 6 tabs open, displays available count; if 0 open tabs, hides section. | `spec.md` §4.1, `ORIGINAL_REQUEST.md` R5 |
| 30 | R5 Home | Favorites Responsive Grid | 2D Grid displaying bookmarked items below recent tabs. | Favorites list from Room DB `FavoritesDao`. | Responsive grid with favicon/icon and bookmark title/hostname. | Clean empty state bookmark prompt when favorites list is empty. | `spec.md` §4.1, `ORIGINAL_REQUEST.md` R5 |
| 31 | R5 Home | Favorites 6-Item Hard Limit & Expansion | Hard limit of 6 items displayed in default view; if items > 6, displays "Show All Favorites" button to expand. | Favorites count in DB > 6. | Default: 6 items + "Show All Favorites" button. Expanded: All items + "Show Less" button. | Toggling expansion smoothly animates grid height. | `spec.md` §4.1, `ORIGINAL_REQUEST.md` R5 |
| 32 | R5 Home | Favorites Item Tap & Management | Tapping favorite item navigates to URL; long-press opens edit/delete options. | Tap / Long press on favorite item. | Tap: navigates active tab to URL. Long-press: shows options (Delete, Edit, Open in New Tab). | DB delete operation removes record and updates UI reactively via `Flow`. | `spec.md` §4.1 |
| 33 | R5 Settings | Profile Placeholder Section | Top section in Settings sheet showing user profile placeholder marked `Coming Soon`. | User profile state. | Header card with avatar placeholder and "Coming Soon" badge. | Non-clickable / informational only. | `spec.md` §4.4, `ORIGINAL_REQUEST.md` R5 |
| 34 | R5 Settings | Theme Mode Selector | Theme selector offering: `System Default` (Default), `Light`, `Dark`. | Selected theme enum from Room DB Settings. | Radio group / segmented buttons; instantly updates app Compose `MaterialTheme` (dark/light color scheme). | Value persisted to DB/Preferences; survives app restart. | `spec.md` §4.4, `ORIGINAL_REQUEST.md` R5 |
| 35 | R5 Settings | Search Engine Selector | Search engine selector offering: `DuckDuckGo` (Default), `Google`, `Bing`. | Selected search engine enum from Room DB Settings. | Radio group / dropdown; updates default query template string. | Value persisted to DB; instantly updates Search Overlay query generator. | `spec.md` §4.4, `ORIGINAL_REQUEST.md` R5 |
| 36 | R5 Settings | Ad & Tracker Blocker Toggle | Switch to enable/disable native ad & tracker blocker. | Boolean toggle state from Room DB Settings. | Switch UI; toggles `isAdBlockerEnabled` in `AdBlockerEngine` / `WebViewClient`. | Persisted to DB; applies immediately to active and new WebView requests. | `spec.md` §4.4, `ORIGINAL_REQUEST.md` R5 |
| 37 | R5 Settings | Hyperlinks (GitHub & Jestk.com) | Clickable link buttons for GitHub (repo url) and Jestk.com (opens `https://jestk.com` in new tab). | Click on hyperlink item. | Opens URL in a new tab (or external intent/browser). | Closes Settings sheet and activates newly spawned tab. | `spec.md` §4.4, `ORIGINAL_REQUEST.md` R5 |
| 38 | R5 Gestures | Pull-to-Refresh | Pull down from top of viewport when `webView.scrollY == 0` triggers page reload. | Pull-down touch gesture at top of page. | Shows Material 3 pull-refresh spinner indicator; invokes `webView.reload()`. | Pull gesture ignored if `webView.scrollY > 0` (preserves in-page web scrolling). Spinner disappears on `onPageFinished`. | `spec.md` §5.3 |
| 39 | R5 Gestures | Hierarchical Back Button Handling | Android system back gesture handled strictly in hierarchical sequence. | System back button / gesture (`BackHandler`). | 1. Dismiss Search Overlay -> 2. Dismiss All Tabs Grid -> 3. Dismiss Settings Sheet -> 4. `webView.goBack()` -> 5. Close active tab -> 6. Exit app. | If only 1 tab open and cannot go back, exits activity cleanly or goes to Home. | `spec.md` §5.3, `ORIGINAL_REQUEST.md` R6 |

---

## Edge Cases

| # | Feature | Input | Observed / Specified Behavior |
|---|---------|-------|-------------------------------|
| 1 | R2 Tab Strip | User creates 50+ tabs | `LazyRow` maintains smooth 60fps scrolling without rendering off-screen tabs; active tab remains centered/visible via `LazyListState.animateScrollToItem()`. |
| 2 | R2 Tab Strip | Single tab open (tab count == 1) | Active tab pill occupies 50% screen width; no inactive tab circles displayed; swipe left/right gesture is disabled / rubber-bands with no tab switch; trailing `+` button remains accessible. |
| 3 | R2 Tab Strip | Extremely long website title (>100 chars) | Text is truncated with an ellipsis (`TextOverflow.Ellipsis`), single line (`maxLines = 1`), leaving space for favicon on the left and avoiding pill container overflow. |
| 4 | R2 Tab Strip | Webpage has no favicon or favicon loading fails | Displays default local fallback vector icon (Globe / Web icon `Icons.Default.Public` / `Icons.Outlined.Language`) tinted with onSurfaceVariant. |
| 5 | R2 Tab Strip | Swiping rapidly across multiple tabs in active pill | Each horizontal swipe gesture triggers an immediate switch to adjacent tab index (`currentIndex ± 1`), updating `activeTabId` and loading the corresponding pooled `WebView` without lag. |
| 6 | R2 Tab Strip | Long press inactive tab at screen edge | Context menu (`DropdownMenu`) calculates available window space and pops up above or below the circular tab button without clipping off-screen. |
| 7 | R2 Tab Strip | Close tab from long-press menu when it is the only inactive tab | Inactive tab is removed; active tab remains unchanged; tab strip re-centers the active tab pill. |
| 8 | R2 Auto-Hide | Webpage content is shorter than viewport (no scrolling possible) | `NestedScrollConnection` detects zero scrollable delta; bottom bar remains permanently pinned at bottom (offset 0). |
| 9 | R2 Auto-Hide | User scrolls down rapidly then scrolls up 5 pixels | Downward scroll hides the bar (`offsetY = barHeightPx`); any upward scroll delta immediately begins revealing the bar proportionally or triggers animated full reveal. |
| 10 | R2 Auto-Hide | User opens Search Overlay or Settings while bar is translated out of view | Bottom bar offset is immediately reset to 0 (fully visible) and auto-hide is locked while overlays are open. |
| 11 | R2 Auto-Hide | Software Keyboard opens while bottom bar is visible | Bottom bar is either docked above the IME insets (`WindowInsets.ime`) or hidden temporarily to maximize typing viewport for Search Overlay. |
| 12 | R5 Search | Search input starts with "localhost:8080" or "192.168.1.1" | Smart URL detector identifies IP/localhost as a direct web URL, prepends `http://`, and navigates directly instead of dispatching to DuckDuckGo search. |
| 13 | R5 Search | Search query contains special characters (`#`, `&`, `?`, `+`, non-ASCII/Unicode) | Query is safely URL-encoded using `URLEncoder.encode(query, "UTF-8")` before constructing search engine URL (`https://duckduckgo.com/?q=%23...`), preventing corrupted URLs. |
| 14 | R5 Search | User opens Search Overlay on a slow device with keyboard lag | `SoftwareKeyboardController.show()` called inside `LaunchedEffect(Unit)` with `FocusRequester.requestFocus()` guarantees keyboard pops up reliably without focus desync. |
| 15 | R5 Search | User has 100+ history records in DB | History stack `LazyColumn` efficiently queries and streams paginated/limited history records (e.g. top 20 recent), scrolling upward with gradient fade at top edge. |
| 16 | R5 Search | User taps a history item in the upward stack | Query text in input box is updated immediately with history title/query, triggering instant live search or direct navigation. |
| 17 | R5 Tab Grid | Fast swipe-to-dismiss on multiple tab cards in 3-column grid | Each swipe triggers `SwipeToDismissBox` dismissal animation, deletes tab from state and Room DB, removes cached snapshot bitmap from disk, and shifts grid items smoothly with `animateItemPlacement()`. |
| 18 | R5 Tab Grid | User closes all tabs from the 3-column grid | When the last remaining tab is dismissed, the system automatically creates a fresh blank tab / navigates to Home Screen, preventing a blank/crashed activity state. |
| 19 | R5 Tab Grid | Tab snapshot bitmap is not yet captured or file corrupted | Grid card displays a styled fallback card with website title, domain, and fallback globe icon with subtle gradient background. |
| 20 | R5 Home | Total favorites count is exactly 6 items | Grid displays all 6 items; no "Show All Favorites" button is shown. |
| 21 | R5 Home | Total favorites count is 7 or more items | Grid displays exactly 6 items in default view, followed by a prominent "Show All Favorites (N)" button. Clicking button expands the grid to display all N favorites with a "Show Less" toggle. |
| 22 | R5 Home | User has zero favorites | Displays an aesthetic empty state: "No favorites yet. Long-press any tab or use menu to bookmark sites" with an Add Favorite button or illustration. |
| 23 | R5 Home | User clicks a recent tab card that was already active | Dismisses Home screen, switches viewport to the live `WebView` for that tab without reloading the page or losing scroll position. |
| 24 | R5 Settings | User changes Theme from System Default to Dark | Compose `MaterialTheme` recomposes immediately with dark color palette across all views, bottom bar, overlays, and home screen without activity restart. |
| 25 | R5 Settings | User changes Search Engine to Google or Bing | Setting is immediately saved to Room DB; subsequent searches in Search Overlay format queries with `https://www.google.com/search?q={query}` or `https://www.bing.com/search?q={query}`. |
| 26 | R5 Gestures | Pull-to-refresh while scrolled midway down a long webpage (`scrollY > 0`) | `PullToRefreshBox` consumes no drag delta; touch events pass directly to `WebView` for smooth in-page upward scrolling. Only triggers reload if user pulls down at top of page (`scrollY == 0`). |
| 27 | R5 Gestures | Back button pressed while Search Overlay is open | Back handler dismisses Search Overlay, restores previous WebView viewport; does NOT navigate WebView backward or close tab. |
| 28 | R5 Gestures | Back button pressed while on Home Screen with 1 open tab | Exits app or minimizes activity safely without leaving app in invalid state. |

---

## Detailed Technical Specification

```
+=============================================================================+
|                      JESTK BROWSER VIEWPORT CONTAINER                       |
|                                                                             |
|  [ Live WebView ]  OR  [ Home Screen ]  OR  [ Visual 3-Column Tab Grid ]     |
|                                                                             |
|  +-----------------------------------------------------------------------+  |
|  | [Search Overlay (Modal Bias: 0.55)]                                   |  |
|  |   - Fading Upward History Stack (LazyColumn)                          |  |
|  |   - Search Input Bar (Autofocus Soft Keyboard)                        |  |
|  +-----------------------------------------------------------------------+  |
+=============================================================================+
| DUAL-TIER BOTTOM NAVIGATION BAR (Auto-Hide on scroll down, Reveal on up)   |
| +-------------------------------------------------------------------------+ |
| | TIER 1: [ (o) (o) | [Favicon] Active Tab Pill (50% W) | (o) (+) ]       | |
| +-------------------------------------------------------------------------+ |
| | TIER 2: [ (Home)        (Search)        (All Tabs)        (Settings) ]  | |
| +-------------------------------------------------------------------------+ |
+=============================================================================+
```

---

### 1. R2. Dual-Tier Bottom Navigation Bar

#### 1.1 Visual Hierarchy & Layout Geometry
The bottom navigation bar is pinned to the bottom of the display above the system navigation bar insets (`WindowInsets.navigationBars`). It consists of two stacked tiers:
- **Tier 1 (Horizontal Tab Strip)**: Height: `52.dp`, Padding: `horizontal = 8.dp, vertical = 4.dp`.
- **Tier 2 (Action Bar / Dock)**: Height: `56.dp`, Padding: `horizontal = 16.dp, vertical = 4.dp`.
- **Total Bottom Bar Height**: `108.dp` (plus system navigation bar inset padding).
- **Surface Appearance**: Safari-inspired iOS minimalist aesthetic. `Surface(color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp), tonalElevation = 3.dp, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))` with subtle border or top divider (`color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)`).

#### 1.2 Tier 1: Horizontal Tab Strip (`LazyRow`)
A horizontally scrollable row containing:
1. **Inactive Tab Circular Buttons (Left)**:
   - Size: `40.dp x 40.dp` circle (`shape = CircleShape`).
   - Background: `MaterialTheme.colorScheme.surfaceVariant`.
   - Content: Site Favicon bitmap (`20.dp x 20.dp`) centered. If null, fallback `Icons.Default.Public` (Globe).
   - Interactions:
     - Single Tap: Invokes `onSelectTab(tab.id)`, centers tab in strip, updates `activeTabId`.
     - Long Press: Opens `DropdownMenu` with options:
       - `Close Tab` (Icon: `Icons.Default.Close`): Calls `onCloseTab(tab.id)`.
       - `Copy URL` (Icon: `Icons.Default.ContentCopy`): Calls `onCopyUrl(tab.url)`.
       - `Add to Favorites` (Icon: `Icons.Default.BookmarkBorder`): Calls `onAddToFavorites(tab)`.
2. **Active Tab Pill (Center)**:
   - Width: Exactly **50% (2/4) of available screen width** (`Modifier.width(LocalConfiguration.current.screenWidthDp.dp * 0.5f)` or dynamic box layout width calculation).
   - Height: `40.dp`.
   - Shape: Full rounded pill (`RoundedCornerShape(20.dp)`).
   - Background: `MaterialTheme.colorScheme.primaryContainer` (or subtle elevated tonal surface).
   - Content Layout (`Row`):
     - Left: Favicon image (`20.dp x 20.dp`, padding `start = 10.dp`).
     - Center: Page Title / Hostname (`Text(text = displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)`), filling remaining horizontal space (`Modifier.weight(1f)`).
   - Interactions:
     - **Single Tap**: Opens Search Overlay pre-filled with the active tab's current URL (`onOpenSearch(activeTab.url)`).
     - **Swipe Left / Right Gesture**:
       - Detected via `Modifier.pointerInput(Unit) { detectHorizontalDragGestures { ... } }` with a delta accumulator and trigger threshold (e.g. `±40.dp`).
       - Swipe Right (`delta > threshold`): Switches to previous tab (`activeTabIndex - 1`).
       - Swipe Left (`delta < -threshold`): Switches to next tab (`activeTabIndex + 1`).
3. **Inactive Tab Circular Buttons (Right)**:
   - Rendered identically to left inactive tabs, maintaining tab creation order.
4. **Trailing Action (`+` Button)**:
   - Size: `40.dp x 40.dp` circle button.
   - Icon: `Icons.Default.Add` (tint = `MaterialTheme.colorScheme.primary`).
   - Interaction: Single Tap calls `onNewTab()`, creating a new blank tab, focusing it, and scrolling the tab strip to the end.

#### 1.3 Tier 2: Persistent Action Bar / Dock
A grounded horizontal dock with 4 icon buttons evenly distributed (`Arrangement.SpaceAround`):
1. **Home**:
   - Icon: `Icons.Outlined.Home` (Active: `Icons.Filled.Home`).
   - Content Description: `"Home"`.
   - Action: `onOpenHome()` — sets viewport to Home Dashboard.
2. **Search**:
   - Icon: `Icons.Default.Search`.
   - Content Description: `"Search"`.
   - Action: `onOpenSearch(prefilledUrl = "")` — launches Search Overlay with empty input and autofocus.
3. **All Tabs (Grid)**:
   - Icon: `Icons.Outlined.GridView` (or `Icons.Default.Tab`).
   - Content Description: `"All Tabs"`.
   - Action: `onOpenTabsGrid()` — opens full-screen 3-column tab overview.
4. **Settings**:
   - Icon: `Icons.Outlined.Settings` (or `Icons.Default.Tune`).
   - Content Description: `"Settings"`.
   - Action: `onOpenSettings()` — opens Settings sheet.

#### 1.4 Dynamic Auto-Hide Engine
- **Implementation**: Jetpack Compose `NestedScrollConnection` attached to the root Scaffold or WebView viewport.
- **Scroll Math**:
  ```kotlin
  val bottomBarHeightPx = with(LocalDensity.current) { 108.dp.toPx() }
  val bottomBarOffsetHeightPx = remember { mutableFloatStateOf(0f) }

  val nestedScrollConnection = remember {
      object : NestedScrollConnection {
          override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
              val delta = available.y
              val newOffset = bottomBarOffsetHeightPx.floatValue + delta
              bottomBarOffsetHeightPx.floatValue = newOffset.coerceIn(-bottomBarHeightPx, 0f)
              return Offset.Zero
          }
      }
  }
  ```
- **Translation Application**:
  ```kotlin
  Modifier
      .offset { IntOffset(x = 0, y = -bottomBarOffsetHeightPx.floatValue.roundToInt()) }
  ```
- **Rules**:
  - Downward scroll (`available.y < 0`): `bottomBarOffsetHeightPx` moves toward `-bottomBarHeightPx` (translating bar down off-screen).
  - Upward scroll (`available.y > 0`): `bottomBarOffsetHeightPx` moves toward `0f` (translating bar back onto screen).
  - When any overlay (Search, Tab Grid, Settings) opens: `bottomBarOffsetHeightPx.floatValue = 0f` (force revealed).
  - If content is non-scrollable: bar stays fully revealed at `0f`.

---

### 2. R5. Views & Overlays

#### 2.1 Search Overlay
A focused modal overlay sliding over the active content with the following specifications:
1. **Vertical Bias & Geometry**:
   - The Search Input Box is positioned vertically slightly below the screen midpoint at **vertical bias: 0.55** (`Alignment.BiasAlignment(0f, 0.1f)` or `y = screenHeight * 0.55f`).
   - Adapts to Software Keyboard insets (`Modifier.imePadding()`) to remain visible above the keyboard.
2. **Autofocus Soft Keyboard Engine**:
   - `FocusRequester` attached to the `TextField`.
   - Explicit invocation of `SoftwareKeyboardController.show()` inside a `LaunchedEffect(Unit)` with a slight delay or frame await to guarantee keyboard activation:
     ```kotlin
     val focusRequester = remember { FocusRequester() }
     val keyboardController = LocalSoftwareKeyboardController.current

     LaunchedEffect(Unit) {
         focusRequester.requestFocus()
         keyboardController?.show()
     }
     ```
3. **Search Input Box**:
   - Pre-filled with active tab URL when opened from active pill, or blank when opened from dock Search button.
   - `TextField` styling: Rounded shape (`28.dp`), leading search icon, trailing clear ('x') icon when text is non-empty.
   - Keyboard Options: `ImeAction.Search` / `ImeAction.Go`, `KeyboardType.Uri`.
   - Keyboard Actions: `onSearch = { query -> submitSearch(query) }`.
4. **Search Query vs URL Evaluator**:
   - If input starts with `http://` or `https://` -> direct navigation.
   - If input matches hostname pattern (e.g. `domain.tld`, `localhost`, `192.168.x.x`) -> prepend `https://` and navigate directly.
   - Otherwise -> format search query using search engine template:
     - **DuckDuckGo (Default)**: `https://duckduckgo.com/?q={query}`
     - **Google**: `https://www.google.com/search?q={query}`
     - **Bing**: `https://www.bing.com/search?q={query}`
   - Query string encoded with `URLEncoder.encode(query, "UTF-8")`.
5. **Upward-Scrolling Fading History Stack**:
   - Rendered **above** the input box in the top half of the screen.
   - `LazyColumn(reverseLayout = true)` or vertical column displaying recent queries/history.
   - Gradient Fade Mask applied to the top edge:
     ```kotlin
     Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
         .drawWithContent {
             drawContent()
             drawRect(
                 brush = Brush.verticalGradient(
                     colors = listOf(Color.Transparent, Color.Black),
                     startY = 0f,
                     endY = 100.dp.toPx()
                 ),
                 blendMode = BlendMode.DstIn
             )
         }
     ```
   - Each history item: History clock icon (`Icons.Default.History`), query text, timestamp/URL, and tap handler to populate input or execute search.
6. **History Record Persistence**:
   - Submitting a search or visiting a URL inserts/updates a record in Room DB `HistoryEntity(url, title, timestamp)`.

---

#### 2.2 All Tabs Overview (Visual Grid)
Full-screen modal displaying all open tabs in a visual grid:
1. **Grid Layout**:
   - Vertical grid with **exactly 3 columns**: `LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp))`.
2. **Snapshot Card Structure**:
   - Card container: `Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp))`.
   - Active Tab Indicator: Highlight border (`BorderStroke(2.dp, MaterialTheme.colorScheme.primary)`) if `tab.id == activeTabId`.
   - Aspect Ratio Thumbnail: `16:9` or `4:3` scaled bitmap snapshot loaded from disk file path (`tab.snapshotPath`) captured via `VisualStateCallback` / `PixelCopy`.
   - Fallback Thumbnail: Background tonal surface with centered globe icon if snapshot pending or file missing.
   - Footer Row:
     - Left: Favicon image (`16.dp x 16.dp`).
     - Center: Truncated Title (`Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)`).
     - Right: Close button (`IconButton` with `Icons.Default.Close`, size `24.dp`, touch target `48.dp`).
3. **Gestures**:
   - **Horizontal Swipe-to-Dismiss**:
     - Implemented via `SwipeToDismissBox` or `Modifier.pointerInput` horizontal drag.
     - Swiping card left or right beyond threshold dismisses the tab with exit animation (`shrinkVertically` + `fadeOut`).
     - Automatically closes the tab in state, deletes DB record, removes cached snapshot bitmap.
     - If all tabs are closed, automatically spawns a new blank tab.
   - **Card Tap**: Selects tab, closes All Tabs Overview, focuses selected tab WebView.
4. **Header / Action Controls**:
   - Top App Bar with "Tabs (N)", "New Tab (+)" button, and "Done" button.

---

#### 2.3 Home Screen
Replaces WebView viewport when Home icon is tapped or on a fresh blank tab:
1. **Recent Tabs Carousel (Top Section)**:
   - Header: `"Recent Tabs"`.
   - Horizontal carousel: `LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp))`.
   - Displays up to **6 most recently active tabs** (ordered by `lastAccessedTimestamp` descending).
   - Card: Scaled thumbnail snapshot (`width = 140.dp, height = 90.dp`), rounded corners (`8.dp`), footer with favicon and title.
   - Tapping a card switches directly to that tab and dismisses Home Screen.
2. **Favorites Grid (Bottom Section)**:
   - Header: `"Favorites"`.
   - 2D Grid: `LazyVerticalGrid` or responsive flow grid.
   - **Hard Limit**: Exactly **6 items** displayed in default view.
   - **"Show All Favorites" Expansion**:
     - If total favorites count > 6: Appends a prominent `"Show All Favorites"` text button below the grid.
     - Tapping toggles between expanded (all items) and collapsed (6 items) with `"Show Less"` button.
   - **Empty State**:
     - If favorites list is empty: Displays clean empty-state bookmark prompt ("No favorites yet. Long-press any tab or use menu to bookmark sites") with an optional bookmark illustration.
   - Favorite Item Component:
     - Circular icon container (`48.dp x 48.dp`) with site favicon, plus bookmark title below (`maxLines = 1, overflow = TextOverflow.Ellipsis`).
     - Tap: Navigates active tab to bookmarked URL.
     - Long-press: Shows context menu (`Open in New Tab`, `Edit Favorite`, `Delete Favorite`).

---

#### 2.4 Settings Sheet / View
Modal Bottom Sheet (`ModalBottomSheet`) or settings view containing:
1. **Profile Section**:
   - Header item with user avatar icon, title `"User Profile"`, and status badge `"Coming Soon"`.
2. **Theme Mode Selector**:
   - Label: `"Theme"`.
   - Options (Radio buttons / segmented control):
     - `System Default` (Default value = 0)
     - `Light` (Value = 1)
     - `Dark` (Value = 2)
   - Behavior: Instantly updates app `MaterialTheme` and persists selection to Room DB Settings.
3. **Search Engine Selector**:
   - Label: `"Search Engine"`.
   - Options (Radio buttons / dropdown):
     - `DuckDuckGo` (Default) -> `https://duckduckgo.com/?q={query}`
     - `Google` -> `https://www.google.com/search?q={query}`
     - `Bing` -> `https://www.bing.com/search?q={query}`
   - Behavior: Persists selection to Room DB Settings; immediately used by Search Overlay.
4. **Ad & Tracker Blocker Toggle**:
   - Label: `"Ad & Tracker Blocker"`.
   - Subtitle: `"Block known advertisements and tracking scripts"`.
   - Control: `Switch(checked = isAdBlockerEnabled, onCheckedChange = { ... })`.
   - Behavior: Toggles blocking in `WebViewClient.shouldInterceptRequest`. Default is `true` (ON).
5. **Hyperlinks**:
   - `GitHub` Button: Opens repository GitHub link in a new tab.
   - `Jestk.com` Button: Opens `https://jestk.com` in a new tab.
6. **About / App Info**:
   - App title `"Jestk Browser for Android"`, version string (`"1.0.0"`).

---

#### 2.5 System Gestures & Lifecycle Handling

##### Pull-to-Refresh
- Integrated via Material 3 `PullToRefreshBox` or Compose `PullRefreshIndicator` wrapping the WebView viewport.
- **Gesture Qualification**: Pull-to-refresh is only enabled when the active `WebView` is at the top of the webpage (`webView.scrollY == 0`).
- **Action**: Calls `webView.reload()`, displays spinning refresh indicator until `WebChromeClient.onProgressChanged` reaches 100 or `WebViewClient.onPageFinished` fires.

##### Hierarchical Back Button Handling
Managed via Compose `BackHandler` with strict precedence:
1. **Search Overlay Open**: Back press dismisses Search Overlay.
2. **All Tabs Grid Open**: Back press dismisses All Tabs Grid, returning to active tab.
3. **Settings Sheet Open**: Back press dismisses Settings Sheet.
4. **WebView Can Go Back (`webView.canGoBack()` == true)**: Calls `webView.goBack()`.
5. **Multiple Tabs Open (`tabs.size > 1`)**: Closes current active tab, focuses adjacent tab.
6. **Single Tab Open at Root**: Minimizes activity / exits app safely.

---

## State Models & Interface Contracts

### 1. Room Entities & DAOs

```kotlin
// Tab Entity
@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val faviconUrl: String? = null,
    val snapshotPath: String? = null,
    val orderIndex: Int,
    val lastAccessedTimestamp: Long = System.currentTimeMillis()
)

// Favorite Entity
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val faviconUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// History Entity
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val queryOrUrl: String,
    val title: String,
    val isUrl: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// Settings Entity
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1, // Single-row configuration
    val themeMode: ThemeMode = ThemeMode.SYSTEM_DEFAULT,
    val searchEngine: SearchEngine = SearchEngine.DUCK_DUCK_GO,
    val isAdBlockerEnabled: Boolean = true
)

enum class ThemeMode { SYSTEM_DEFAULT, LIGHT, DARK }
enum class SearchEngine(val queryTemplate: String) {
    DUCK_DUCK_GO("https://duckduckgo.com/?q=%s"),
    GOOGLE("https://www.google.com/search?q=%s"),
    BING("https://www.bing.com/search?q=%s")
}
```

### 2. UI State & Overlay Models

```kotlin
sealed interface UIOverlayState {
    data object None : UIOverlayState
    data class Search(val prefilledUrl: String = "", val origin: SearchOrigin = SearchOrigin.ACTION_DOCK) : UIOverlayState
    data object TabsGrid : UIOverlayState
    data object Settings : UIOverlayState
    data object Home : UIOverlayState
}

enum class SearchOrigin {
    ACTIVE_PILL,
    ACTION_DOCK,
    NEW_TAB
}

data class BrowserUIState(
    val tabs: List<TabSession> = emptyList(),
    val activeTabId: String? = null,
    val overlayState: UIOverlayState = UIOverlayState.None,
    val favorites: List<FavoriteEntity> = emptyList(),
    val recentHistory: List<HistoryEntity> = emptyList(),
    val settings: SettingsEntity = SettingsEntity(),
    val isRefreshing: Boolean = false,
    val bottomBarOffsetY: Float = 0f
)
```

---

## Verification & Test Matrix

To guarantee robust, zero-regression implementation for R2 and R5, the following test cases must be implemented:

| Test ID | Test Category | Target Feature | Verification Criteria |
|---------|---------------|----------------|-----------------------|
| TEST-R2-01 | Unit / Compose | Active Tab Pill Width | Active pill width measures exactly 50% (0.5x) of parent screen width in `LazyRow`. |
| TEST-R2-02 | Unit / Compose | Active Pill Tap | Tapping active pill transitions `UIOverlayState` to `Search` with `prefilledUrl` matching active tab URL. |
| TEST-R2-03 | Unit / Compose | Active Pill Swipe Left/Right | Drag gesture with delta > 40dp switches `activeTabIndex` to previous/next index. |
| TEST-R2-04 | Unit / Compose | Inactive Tab Tap | Tapping inactive circular button selects tab and scrolls item into view. |
| TEST-R2-05 | Unit / Compose | Inactive Tab Long Press | Long press displays `DropdownMenu` with `Close Tab`, `Copy URL`, and `Add to Favorites`. |
| TEST-R2-06 | Unit / Compose | Trailing Add Button | Tapping `+` creates new tab with `about:blank`, increments tab count, and selects new tab. |
| TEST-R2-07 | Unit / Compose | Action Dock Navigation | Tapping Home, Search, Grid, Settings icon buttons activates corresponding view / overlay states. |
| TEST-R2-08 | Unit / Compose | Auto-Hide Scroll Translation | Downward scroll delta translates bottom bar offset down to `108dp`; upward scroll restores offset to `0dp`. |
| TEST-R5-01 | Unit / Compose | Search Modal Bias 0.55 | Search modal layout places input bar at vertical bias 0.55 of available height. |
| TEST-R5-02 | Unit / Compose | Search Autofocus & Keyboard | `LaunchedEffect(Unit)` requests focus and calls `softwareKeyboardController.show()`. |
| TEST-R5-03 | Unit / Compose | Search History Fading Stack | History stack renders above search bar in `LazyColumn` with top-edge vertical gradient fade. |
| TEST-R5-04 | Unit / Compose | Search Engine Query Formatting | Queries format to `https://duckduckgo.com/?q={query}` (default), or Google/Bing based on settings. |
| TEST-R5-05 | Unit / Compose | Tab Grid 3-Column Layout | Grid renders with exactly 3 columns (`GridCells.Fixed(3)`) displaying snapshot cards with footers. |
| TEST-R5-06 | Unit / Compose | Tab Grid Swipe-to-Dismiss | Swiping tab card horizontally dismisses tab, unlinks snapshot, and deletes database record. |
| TEST-R5-07 | Unit / Compose | Home Screen Recents Carousel | Carousel displays top 6 recent tabs ordered by `lastAccessedTimestamp` descending. |
| TEST-R5-08 | Unit / Compose | Home Screen Favorites Limit | Grid displays hard limit 6 favorites by default; displays "Show All Favorites" button when count > 6. |
| TEST-R5-09 | Unit / Compose | Settings Theme & Engine | Changing theme or search engine updates state and persists to database immediately. |
| TEST-R5-10 | Unit / Compose | Back Button Precedence | Back press dismisses overlays in order: Search -> Grid -> Settings -> WebView back -> Close tab. |
| TEST-R5-11 | Unit / Compose | Pull-to-Refresh Handling | Pulling down at `scrollY == 0` triggers `webView.reload()`; pull down at `scrollY > 0` is ignored. |
