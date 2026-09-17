# Implementation Plan: Hori Browser UX & Engine Enhancements (Refined)

## 1. Selected TickTick Tasks

| Task ID | Title | Component | Wave |
|---|---|---|---|
| `6aaba491b47cbd1af4c66aea` | refactor(tabs): убрать переключение вкладок через скролл по открытой вкладке | Tabs UI | Wave 1 |
| `6aaba4d1b47cbd1af4c66b0a` | refactor(tabs): закрытие вкладки открывает вкладку, которая слева от закрытой. Если слева нет вкладок, то открывается вкладка справа | ViewModel / Tabs | Wave 1 |
| `6aaba4aeb47cbd1af4c66afa` | bug: не отображаются фавикон в избранном | Favorites / FaviconManager | Wave 1 |
| `6aa9962d17fc4c9f41f3547c` | feat: добавить EasyList.txt & EasyListCookie.txt для блокировки рекламы и трекеров | AdBlockerEngine | Wave 1 |
| `6aac12ebb47cbd1af4c66b45` | Feat: Рекомендация сделать браузер по умолчанию, если он не установлен по умолчанию. Отображать в настройках и на Home. Если он установлен по умолчанию, то убрать все упоминания о том, чтобы сделать браузер по умолчанию | Home / Settings / System | Wave 2 |
| `6aa463aa17fc4c9f41f35414` | feat: долгое нажатие на фото открывает модалку "скопировать, скачать, превью (открывает фото в новой вкладке на весь экран)" | WebView / Image Modal | Wave 2 |

*(Note: Download tasks `6aa50dcdb47c807d1ba43f9a` and `6aac240b17fc4c9f41f354c8` are scheduled for a separate dedicated subsystem batch).*

---

## 2. Architecture & File Breakdown

### Wave 1: Core Navigation, Engine & Data Layer

#### 1. Tab UX Refactor (`6aaba491b47cbd1af4c66aea` & `6aaba4d1b47cbd1af4c66b0a`)
- **`ActiveTabPill.kt`**:
  - Remove horizontal swipe pointer gesture (`detectHorizontalDragGestures`) from the active tab pill so swiping/scrolling across the open tab pill does not trigger tab switching.
  - Clean up unused `onSwipeNext` / `onSwipePrev` gesture callbacks.
- **`TabStripRow.kt`**:
  - Remove swipe callbacks from `ActiveTabPill` invocation.
- **`BrowserViewModel.kt`**:
  - In `closeTab(tabId: String)`:
    - Capture the original tabs list `val originalTabs = _uiState.value.tabs`.
    - If `tabId != _uiState.value.activeTabId`: retain `_uiState.value.activeTabId` untouched.
    - If `tabId == _uiState.value.activeTabId`:
      - Calculate closing index: `val closedIdx = originalTabs.indexOfFirst { it.id == tabId }`.
      - If `remainingTabs.isEmpty()`: create new Home tab (`tabRepository.createTab(url = "", title = "Home")`) and switch cleanly.
      - If `closedIdx > 0`: activate `originalTabs[closedIdx - 1]` (tab to the left).
      - If `closedIdx == 0`: activate `remainingTabs.first()` (tab to the right).
- **`BrowserViewModelTest.kt`**:
  - Add unit test coverage:
    - `closeTab_whenActiveTabClosed_selectsLeftAdjacentTab`
    - `closeTab_whenFirstTabClosed_selectsRightAdjacentTab`
    - `closeTab_whenInactiveTabClosed_keepsActiveTabUnchanged`
    - `closeTab_whenOnlyTabClosed_createsNewHomeTab`

#### 2. Favorites Favicons & AdBlocker Engine (`6aaba4aeb47cbd1af4c66afa` & `6aa9962d17fc4c9f41f3547c`)
- **`FaviconManager.kt`**:
  - Restore `extractFallbackFaviconUrl(url: String?): String?` with strict validation:
    - Return `null` for blank, null, `about:`, `file:`, `data:`, `content:`.
    - Return `null` for `localhost`, `127.0.0.1`, private IP ranges (`192.168.`, `10.`, `172.16.`), or `.local`.
    - For valid `http`/`https` public host, return `"https://www.google.com/s2/favicons?sz=128&domain=${host.removePrefix("www.")}"`.
- **`BrowserViewModel.kt`**:
  - In `addCurrentToFavorites()`:
    - Avoid transient tab disk path (`favicons/favicon_$tabId.png`) which gets deleted when a tab closes.
    - Store remote or fallback favicon: `val iconUrl = if (currentTab?.faviconUrl?.startsWith("http") == true) currentTab.faviconUrl else FaviconManager.extractFallbackFaviconUrl(current.currentUrl)`.
- **`FavoritesGrid.kt`**:
  - Robust icon rendering: `AsyncImage` with `onSuccess`/`onError` and fallback to vector icon.
- **`AdBlockerEngine.kt`**:
  - Make initialization asynchronous on `Dispatchers.IO`.
  - Cold start immediately with built-in essential hosts (`defaultHosts`) so app launch is instant (no main-thread ANR).
  - Background parser for `EasyList.txt` & `EasyListCookie.txt`:
    - Extract domain blocking rules matching ABP syntax: `||domain.com^` (extract `domain.com`).
    - Discard element hiding rules (`##`, `#?#`), whitelist rules (`@@`), and comments (`!`, `[`).
    - Deduplicate into `blockedHosts` atomically.
- **`AdBlockerEngineTest.kt`**:
  - Unit tests verifying domain rule extraction from EasyList and EasyListCookie, subdomain blocking, and benign URL allowlisting.

---

### Wave 2: UI Overlays & Features

#### 3. Default Browser Recommendation (`6aac12ebb47cbd1af4c66b45`)
- **`DefaultBrowserHelper.kt`** [NEW]:
  - `isDefaultBrowser(context: Context): Boolean`:
    - Android Q+ (API 29+): `RoleManager.isRoleHeld(RoleManager.ROLE_BROWSER)`
    - Pre-Q (API < 29): `PackageManager.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == context.packageName`
  - `createSetDefaultBrowserIntent(context: Context): Intent`:
    - Android Q+: `roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)`
    - Pre-Q: `Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)`
- **`BrowserUiState.kt`**:
  - Add `isDefaultBrowser: Boolean = true` (default to true to avoid UI flickering until checked).
- **`BrowserViewModel.kt`**:
  - `checkDefaultBrowser(context: Context)` updates `_uiState.update { it.copy(isDefaultBrowser = isDefault) }`.
- **`MainActivity.kt`**:
  - On `onResume()`, dispatch `checkDefaultBrowser(this)` so returning from system settings instantly updates UI.
- **`HomeScreen.kt`**:
  - Dismissible Liquid-styled prompt card recommending setting Hori as default browser only when `!isDefaultBrowser`. If `isDefaultBrowser == true`, render nothing.
- **`SettingsSheet.kt`**:
  - "Set as Default Browser" setting row only when `!isDefaultBrowser`. If `isDefaultBrowser == true`, hide completely.

#### 4. Image Long-Press Context Menu Modal (`6aa463aa17fc4c9f41f35414`)
- **`ImageContextMenuBottomSheet.kt`** [NEW]:
  - Liquid frosted glass bottom sheet (`LiquidShapes`, `liquidFrostedGlass`).
  - Thumbnail preview of the image.
  - Three action rows:
    1. **Скопировать** (Copies image URL to clipboard, shows confirmation toast)
    2. **Скачать** (Downloads image file via `WebViewPool.handleDownload`, handling HTTP/HTTPS and data URIs)
    3. **Превью (открывает фото в новой вкладке на весь экран)** (Creates new tab with image URL, selects it, closes modal)
- **`WebViewContainer.kt`**:
  - In `setupWebViewLongClick`:
    - `WebView.HitTestResult.IMAGE_TYPE`: `hitTest.extra` is the image URL -> emit `ShowImageContextMenu(imageUrl)`.
    - `WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE`: use `requestFocusNodeHref` and extract `msg.data.getString("src")` for the image URL (to avoid using anchor link URL as image URL).
- **`BrowserUiEvent.kt`**:
  - Add `ShowImageContextMenu(imageUrl: String)`, `DismissImageContextMenu`, `CopyImageUrl(imageUrl: String)`, `PreviewImageInNewTab(imageUrl: String)`.
- **`BrowserUiState.kt`**:
  - Add `selectedImageUrl: String? = null`.
- **`BrowserViewModel.kt`**:
  - Handle `ShowImageContextMenu`, `DismissImageContextMenu`, `CopyImageUrl`, `PreviewImageInNewTab`.
- **`JestkApp.kt`**:
  - Render `ImageContextMenuBottomSheet` when `uiState.selectedImageUrl != null`.

---

## 3. Definition of Done (DoD)
1. All 6 tasks implemented and edge cases accounted for.
2. All unit tests passing (`rtk ./gradlew testDebugUnitTest`).
3. Compose previews verified.
4. Tasks transitioned in TickTick from `todo` to `in progress`, and finally to `test`.
