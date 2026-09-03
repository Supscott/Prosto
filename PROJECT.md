# Project: Jestk Browser for Android (`PROJECT.md`)

## Architecture
Jestk Browser is an iOS/Safari-inspired modern Android web browser built using Kotlin, Jetpack Compose, Material 3, Android System WebView, and Room Database.
The system is partitioned into clean layers following MVVM and Unidirectional Data Flow (UDF):

```
+-----------------------------------------------------------------------------------+
|                                 UI Layer (Compose)                                |
|  - MainActivity / JestkApp Scaffold                                               |
|  - Viewport (Active WebView Container / Home Screen / Tab Grid / Settings / Video)|
|  - Dual-Tier Bottom Bar (Tier 1: Tab Strip LazyRow; Tier 2: Action Dock)          |
|  - Overlays (Search Modal bias 0.55, Long-press Menu, Video Mini-Player)          |
+-----------------------------------------------------------------------------------+
                                         | StateFlow & Events
                                         v
+-----------------------------------------------------------------------------------+
|                              ViewModel Layer (MVVM)                               |
|  - BrowserViewModel (exposes BrowserUiState StateFlow, handles BrowserUiEvent)    |
+-----------------------------------------------------------------------------------+
                                         |
         +-------------------------------+-------------------------------+
         |                               |                               |
         v                               v                               v
+------------------+           +-------------------+           +--------------------+
|   WebViewPool    |           |  Snapshot Engine  |           |  AdBlocker Engine  |
| - Live instances |           | - VisualState     |           | - Compiled ruleset |
| - Safe detachment|           |   Callback capture|           | - Fast HashSet     |
| - Background play|           | - Disk caching    |           | - Subdomain walk   |
+------------------+           +-------------------+           +--------------------+
         |                               |                               |
         +-------------------------------+-------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                             Repository & Data Layer                               |
|  - TabRepository, HistoryRepository, FavoriteRepository, SettingsRepository       |
|  - AppDatabase (Room): TabEntity, HistoryEntity, FavoriteEntity, SettingEntity     |
|  - Manual DI Container: AppContainer                                              |
+-----------------------------------------------------------------------------------+
```

---

## Feature Inventory
Every requirement and discovered feature from the Survey phase is enumerated here and assigned to an explicit milestone.

| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Gradle Scaffolding & Setup | Android 15 (SDK 35), Min SDK 26, JVM 17, Compose BOM, Material3, Room KSP | M1 | Survey R1 |
| 2 | Room Database & Entities | TabEntity, HistoryEntity, FavoriteEntity, SettingEntity with indices & converters | M1 | Survey R1 |
| 3 | Room DAOs & Transactions | TabDao, HistoryDao, FavoriteDao, SettingDao with reactive Flow & batch queries | M1 | Survey R1 |
| 4 | Repository Layer | TabRepository, HistoryRepository, FavoriteRepository, SettingsRepository | M1 | Survey R1 |
| 5 | Manual DI Container | AppContainer & ViewModelFactory providing clean lifecycle-aware dependencies | M1 | Survey R1 |
| 6 | MVVM State & ViewModels | BrowserViewModel, BrowserUiState StateFlow, BrowserUiEvent handling | M1 | Survey R1 |
| 7 | Session Restoration Logic | Deterministic cold-boot loading of tabs, active index, and cached thumbnails | M1 | Survey R1 |
| 8 | WebViewPool Architecture | In-memory pool of live WebView instances keyed by Tab ID preserving state | M2 | Survey R3 |
| 9 | Safe Compose Detachment | `(webView.parent as? ViewGroup)?.removeView(webView)` preventing crashes | M2 | Survey R3 |
| 10 | Background Media Playback | Preventing `onPause()` / `pauseTimers()` on background tabs for audio/video | M2 | Survey R3 |
| 11 | VisualStateCallback Snapshot | Frame-complete canvas capture via `postVisualStateCallback` / `PixelCopy` | M2 | Survey R4 |
| 12 | Disk Snapshot Caching | Atomic write of scaled thumbnails to app disk cache and Room DB linkage | M2 | Survey R4 |
| 13 | Ad/Tracker Host Ruleset | Pre-bundled asset file of ad and tracker domain rules | M3 | Survey R6 |
| 14 | Fast HashSet & Subdomain Match | $O(1)$ domain lookup and progressive parent subdomain traversal | M3 | Survey R6 |
| 15 | Request Interceptor | `WebViewClient.shouldInterceptRequest` returning empty `WebResourceResponse` | M3 | Survey R6 |
| 16 | HTML5 Fullscreen Support | `WebChromeClient.onShowCustomView` / `onHideCustomView` with layout toggles | M3 | Survey R6 |
| 17 | Video Mini-Player & PiP | Draggable in-app mini-player & system-level `enterPictureInPictureMode` | M3 | Survey R6 |
| 18 | Back Navigation Hierarchy | Overlays -> `webView.goBack()` -> Close tab deterministic dispatch | M3 | Survey R6 |
| 19 | Tier 1 Tab Strip (Active Pill)| 50% screen width pill with favicon, truncated title, tap & swipe gestures | M4 | Survey R2 |
| 20 | Tier 1 Inactive Tab Buttons | Circular favicon buttons with tap to switch and long-press contextual menu | M4 | Survey R2 |
| 21 | Tier 1 Trailing Action Button| Persistent `+` button to open new blank tab | M4 | Survey R2 |
| 22 | Tier 2 Action Bar Dock | 4 persistent icon buttons: Home, Search, All Tabs Grid, Settings | M4 | Survey R2 |
| 23 | Dynamic Auto-Hide Engine | `NestedScrollConnection` translating bottom bar on scroll down / up | M4 | Survey R2 |
| 24 | Search Modal Overlay | 0.55 bias modal with autofocus keyboard (`SoftwareKeyboardController`) | M5 | Survey R5 |
| 25 | Upward Fading History Stack | `LazyColumn` above search input with top alpha fade brush | M5 | Survey R5 |
| 26 | Search Query Routing | URL vs search query dispatch with DuckDuckGo / Google / Bing engines | M5 | Survey R5 |
| 27 | 3-Column Tab Grid Overview | Full-screen 3-column vertical grid displaying live bitmap snapshot cards | M5 | Survey R5 |
| 28 | Grid Card Swipe-to-Dismiss | Horizontal swipe gesture on tab cards to close tabs | M5 | Survey R5 |
| 29 | Home Screen Recents Carousel | Horizontal carousel (`LazyRow`) showing top 6 most recent tabs | M5 | Survey R5 |
| 30 | Home Screen Favorites Grid | Responsive 2D grid with 6-item default cap + "Show All Favorites" button | M5 | Survey R5 |
| 31 | Settings Sheet | Theme mode (System/Light/Dark), Search engine, Ad blocker toggle, links | M5 | Survey R5 |
| 32 | Pull-to-Refresh | Compose `PullRefreshIndicator` wrapping WebView container at `scrollY == 0` | M5 | Survey R5 |
| 33 | Clean Build & Packaging | `./gradlew assembleDebug` compiling with zero errors and clean APK | M6 | Survey R1-R6 |
| 34 | 100% E2E Test Pass (Tiers 1-4)| Complete pass of opaque-box E2E test suite across all user requirements | M6 | E2E Track |
| 35 | Adversarial Hardening (Tier 5)| White-box challenger test generation and edge case hardening | M6 | Implementation |

---

## Milestones

| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| E2E | E2E Testing Track | Test infra, Tier 1-4 test suite generation, and `TEST_READY.md` publication | Survey | IN_PROGRESS |
| M1 | Scaffolding, Room DB & Architecture | Gradle setup, Room entities/DAOs, Repositories, DI, MVVM State/ViewModel, Unit tests | Survey | IN_PROGRESS |
| M2 | WebView Pool, Lifecycle & Snapshot Engine | `WebViewPool`, detach/attach, background media, `VisualStateCallback` snapshot capture & caching | M1 | PLANNED |
| M3 | Ad Blocker, Video/PiP & Navigation | Ad blocker HashSet engine, `WebViewClient` interceptor, `WebChromeClient` fullscreen/PiP, Back hierarchy | M1 | PLANNED |
| M4 | Dual-Tier Bottom Navigation Bar | Tier 1 tab strip (Active pill, inactive circles, swipe, menu), Tier 2 dock, Auto-hide scroll translation | M1 | PLANNED |
| M5 | Views & Overlays | Search Overlay (bias 0.55, autofocus, history stack), 3-Column Tab Grid, Home Screen, Settings Sheet | M1, M2, M4 | PLANNED |
| M6 | Integration, Build & E2E Test Pass | App assembly, `./gradlew assembleDebug`, passing 100% E2E tests (T1-T4) & Tier 5 adversarial hardening | M1-M5, E2E | PLANNED |

---

## Interface Contracts

### Data Layer & Repositories
- `TabRepository`:
  - `fun getAllTabs(): Flow<List<Tab>>`
  - `suspend fun getTabById(id: String): Tab?`
  - `fun getRecentTabs(limit: Int): Flow<List<Tab>>`
  - `suspend fun createTab(url: String, title: String): Tab`
  - `suspend fun updateTab(tab: Tab)`
  - `suspend fun deleteTab(id: String)`
  - `suspend fun setActiveTab(id: String)`
  - `suspend fun updateSnapshot(id: String, path: String)`
- `HistoryRepository`:
  - `fun getHistory(limit: Int): Flow<List<HistoryItem>>`
  - `fun searchHistory(query: String): Flow<List<HistoryItem>>`
  - `suspend fun recordVisit(url: String, title: String)`
  - `suspend fun deleteHistory(id: Long)`
  - `suspend fun clearHistory()`
- `FavoriteRepository`:
  - `fun getFavorites(): Flow<List<FavoriteItem>>`
  - `fun getFavoritesLimited(limit: Int): Flow<List<FavoriteItem>>`
  - `suspend fun addFavorite(title: String, url: String)`
  - `suspend fun deleteFavorite(id: Long)`
- `SettingsRepository`:
  - `fun getSettings(): Flow<BrowserSettings>`
  - `suspend fun setThemeMode(mode: ThemeMode)`
  - `suspend fun setSearchEngine(engine: SearchEngine)`
  - `suspend fun setAdBlockerEnabled(enabled: Boolean)`

### WebView Engine & Lifecycle Contracts
- `WebViewPool`:
  - `fun getOrCreateWebView(tabId: String, context: Context): WebView`
  - `fun prepareForAttachment(tabId: String)`: synchronously detaches parent if present via `(parent as? ViewGroup)?.removeView(webView)`
  - `fun removeWebView(tabId: String)`: destroys and cleans up tab instance
  - `fun clearAll()`
- `SnapshotManager`:
  - `fun captureSnapshot(webView: WebView, tabId: String, onComplete: (String?) -> Unit)`: calls `postVisualStateCallback` and renders bitmap to `filesDir/snapshots/tab_${tabId}.jpg`
- `AdBlockerEngine`:
  - `fun initialize(context: Context)`
  - `fun shouldBlock(url: String?): Boolean`: checks `HashSet` and subdomain parent chains
  - `fun createBlockedResponse(): WebResourceResponse`

### ViewModel UI State (`BrowserUiState`)
- `tabs: List<Tab>`
- `activeTabId: String?`
- `activeTab: Tab?`
- `recentTabs: List<Tab>`
- `favorites: List<FavoriteItem>`
- `history: List<HistoryItem>`
- `searchHistorySuggestions: List<HistoryItem>`
- `settings: BrowserSettings`
- `activeOverlay: OverlayType?` (`SEARCH`, `TAB_GRID`, `SETTINGS`, `NONE`)
- `isBottomBarVisible: Boolean` / `bottomBarOffset: Float`
- `fullscreenCustomView: View?`
- `isVideoPlaying: Boolean`
- `isLoading: Boolean` / `loadProgress: Int`

---

## Code Layout

```
/Users/ilya/CODING/browser/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/
        │   │   └── adblock/hosts.txt
        │   ├── java/com/jestk/browser/
        │   │   ├── JestkApplication.kt
        │   │   ├── di/
        │   │   │   └── AppContainer.kt
        │   │   ├── data/
        │   │   │   ├── local/
        │   │   │   │   ├── AppDatabase.kt
        │   │   │   │   ├── Converters.kt
        │   │   │   │   ├── dao/ (TabDao, HistoryDao, FavoriteDao, SettingDao)
        │   │   │   │   └── entity/ (TabEntity, HistoryEntity, FavoriteEntity, SettingEntity)
        │   │   │   └── repository/ (TabRepositoryImpl, HistoryRepositoryImpl, FavoriteRepositoryImpl, SettingsRepositoryImpl)
        │   │   ├── domain/
        │   │   │   ├── model/ (Tab, HistoryItem, FavoriteItem, BrowserSettings, ThemeMode, SearchEngine)
        │   │   │   └── repository/ (TabRepository, HistoryRepository, FavoriteRepository, SettingsRepository)
        │   │   ├── engine/
        │   │   │   ├── WebViewPool.kt
        │   │   │   ├── JestkWebViewClient.kt
        │   │   │   ├── JestkWebChromeClient.kt
        │   │   │   ├── AdBlockerEngine.kt
        │   │   │   └── SnapshotEngine.kt
        │   │   ├── ui/
        │   │   │   ├── MainActivity.kt
        │   │   │   ├── JestkApp.kt
        │   │   │   ├── navigation/ (BottomNavBar, TabStripRow, ActiveTabPill, InactiveTabCircle, ActionDock)
        │   │   │   ├── overlays/ (SearchOverlay, TabOverviewGrid, SettingsSheet)
        │   │   │   ├── views/ (WebViewContainer, HomeScreen, RecentTabsCarousel, FavoritesGrid, FullscreenVideoContainer)
        │   │   │   ├── theme/ (Color, Theme, Type)
        │   │   │   └── viewmodel/ (BrowserViewModel, BrowserUiState, BrowserUiEvent)
        │   │   └── util/ (UrlUtils, SnapshotUtils, KeyboardUtils)
        │   └── res/
        │       ├── drawable/
        │       ├── mipmap-*/
        │       └── values/ (strings.xml, themes.xml)
        └── test/
            └── java/com/jestk/browser/ (E2E & Unit Test Suites)
```
