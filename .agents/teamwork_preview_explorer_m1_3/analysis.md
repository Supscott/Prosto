# Milestone 1: Domain Models, Repositories, MVVM State & ViewModels — Technical Blueprint

## 1. Executive Summary

This document provides the definitive implementation blueprint for **Milestone 1 (Repositories, MVVM State & ViewModels)** of Jestk Browser for Android. It details the exact domain data contracts, mapping extensions, repository interfaces and implementations, ViewModel architecture with reactive unidirectional data flow (`StateFlow`), cold boot session restoration logic, and comprehensive unit test suites.

All designs are strictly aligned with:
- `ORIGINAL_REQUEST.md` (R1 Scaffolding, Architecture, Room persistence, MVVM StateFlow, Session Restoration)
- `spec.md` (§ 1 System Overview, § 2 Core UI Layout, § 3 Navigation Bar, § 4 Views & Overlays, § 5 Web Platform & Persistence)
- `PROJECT.md` (Architecture, Feature Inventory, Interface Contracts, Code Layout)
- Existing Tier 1 E2E test suites in `app/src/test/java/com/jestk/browser/e2e/tier1/`

---

## 2. Domain Models & Mapping Extensions

### 2.1 Package: `com.jestk.browser.domain.model`

#### 2.1.1 `Tab.kt`
The domain model represents an active or background browsing session tab.
Constructor parameter ordering guarantees seamless compatibility with existing Java unit tests (`new Tab(id, url, title, timestamp, snapshotPath, position)`):

```kotlin
package com.jestk.browser.domain.model

data class Tab(
    val id: String,
    val url: String = "",
    val title: String = "New Tab",
    val lastActiveTimestamp: Long = System.currentTimeMillis(),
    val snapshotPath: String? = null,
    val position: Int = 0,
    val faviconUrl: String? = null,
    val isIncognito: Boolean = false,
    val isActive: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)
```

#### 2.1.2 `HistoryItem.kt`
Browsing history entry with visit timestamp and frequency aggregation.
```kotlin
package com.jestk.browser.domain.model

data class HistoryItem(
    val id: Long = 0,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val visitCount: Int = 1,
    val faviconUrl: String? = null
)
```

#### 2.1.3 `FavoriteItem.kt`
User bookmarks shown on the Home screen 2D grid.
```kotlin
package com.jestk.browser.domain.model

data class FavoriteItem(
    val id: Long = 0,
    val title: String,
    val url: String,
    val position: Int = 0,
    val iconUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

#### 2.1.4 `ThemeMode.kt`
```kotlin
package com.jestk.browser.domain.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
```

#### 2.1.5 `SearchEngine.kt`
Defines available search engines and formatting logic for queries:
```kotlin
package com.jestk.browser.domain.model

import java.net.URLEncoder

enum class SearchEngine(val displayName: String, val searchUrlTemplate: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s");

    fun buildSearchUrl(query: String): String {
        val encoded = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }
        return String.format(searchUrlTemplate, encoded)
    }
}
```

#### 2.1.6 `BrowserSettings.kt`
```kotlin
package com.jestk.browser.domain.model

data class BrowserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val isAdBlockerEnabled: Boolean = true
)
```

#### 2.1.7 `OverlayType.kt`
Tracks active modal overlay state in the browser viewport:
```kotlin
package com.jestk.browser.domain.model

enum class OverlayType {
    NONE,
    HOME,
    SEARCH,
    TAB_GRID,
    SETTINGS
}
```

---

### 2.2 Data Mapping Extensions: `com.jestk.browser.data.local.entity`

#### 2.2.1 Tab Entity Mappings (`TabMapping.kt`)
```kotlin
package com.jestk.browser.data.local.entity

import com.jestk.browser.domain.model.Tab

fun TabEntity.toDomain(): Tab = Tab(
    id = id,
    url = url,
    title = title,
    lastActiveTimestamp = lastActiveTimestamp,
    snapshotPath = snapshotPath,
    position = position,
    faviconUrl = faviconUrl,
    isIncognito = isIncognito,
    isActive = isActive,
    canGoBack = canGoBack,
    canGoForward = canGoForward
)

fun Tab.toEntity(): TabEntity = TabEntity(
    id = id,
    url = url,
    title = title,
    lastActiveTimestamp = lastActiveTimestamp,
    snapshotPath = snapshotPath,
    position = position,
    faviconUrl = faviconUrl,
    isIncognito = isIncognito,
    isActive = isActive,
    canGoBack = canGoBack,
    canGoForward = canGoForward
)
```

#### 2.2.2 History Entity Mappings (`HistoryMapping.kt`)
```kotlin
package com.jestk.browser.data.local.entity

import com.jestk.browser.domain.model.HistoryItem

fun HistoryEntity.toDomain(): HistoryItem = HistoryItem(
    id = id,
    url = url,
    title = title,
    timestamp = timestamp,
    visitCount = visitCount,
    faviconUrl = faviconUrl
)

fun HistoryItem.toEntity(): HistoryEntity = HistoryEntity(
    id = id,
    url = url,
    title = title,
    timestamp = timestamp,
    visitCount = visitCount,
    faviconUrl = faviconUrl
)
```

#### 2.2.3 Favorite Entity Mappings (`FavoriteMapping.kt`)
```kotlin
package com.jestk.browser.data.local.entity

import com.jestk.browser.domain.model.FavoriteItem

fun FavoriteEntity.toDomain(): FavoriteItem = FavoriteItem(
    id = id,
    title = title,
    url = url,
    position = position,
    iconUrl = iconUrl,
    createdAt = createdAt
)

fun FavoriteItem.toEntity(): FavoriteEntity = FavoriteEntity(
    id = id,
    title = title,
    url = url,
    position = position,
    iconUrl = iconUrl,
    createdAt = createdAt
)
```

#### 2.2.4 Setting Entity & Keys (`SettingEntity.kt` & `SettingKeys.kt`)
```kotlin
package com.jestk.browser.data.local.entity

import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode

object SettingKeys {
    const val THEME_MODE = "theme_mode"
    const val SEARCH_ENGINE = "search_engine"
    const val AD_BLOCKER_ENABLED = "ad_blocker_enabled"
}

fun List<SettingEntity>.toBrowserSettings(): BrowserSettings {
    val map = associate { it.key to it.value }
    val themeMode = map[SettingKeys.THEME_MODE]?.let { raw ->
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    } ?: ThemeMode.SYSTEM

    val searchEngine = map[SettingKeys.SEARCH_ENGINE]?.let { raw ->
        runCatching { SearchEngine.valueOf(raw) }.getOrDefault(SearchEngine.DUCKDUCKGO)
    } ?: SearchEngine.DUCKDUCKGO

    val isAdBlockerEnabled = map[SettingKeys.AD_BLOCKER_ENABLED]?.toBooleanStrictOrNull() ?: true

    return BrowserSettings(
        themeMode = themeMode,
        searchEngine = searchEngine,
        isAdBlockerEnabled = isAdBlockerEnabled
    )
}
```

---

## 3. Repository Interfaces & Implementations

### 3.1 `TabRepository` (`com.jestk.browser.domain.repository.TabRepository`)

```kotlin
package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.Tab
import kotlinx.coroutines.flow.Flow

interface TabRepository {
    fun getAllTabs(): Flow<List<Tab>>
    fun getRecentTabs(limit: Int = 6): Flow<List<Tab>>
    suspend fun getInitialTabs(): List<Tab>
    suspend fun getTabById(id: String): Tab?
    fun getTabByIdFlow(id: String): Flow<Tab?>
    suspend fun createTab(url: String = "", title: String = "New Tab", isIncognito: Boolean = false): Tab
    suspend fun updateTab(tab: Tab)
    suspend fun updateTabDetails(id: String, url: String, title: String, canGoBack: Boolean, canGoForward: Boolean)
    suspend fun updateSnapshot(id: String, snapshotPath: String)
    suspend fun setActiveTab(id: String)
    suspend fun deleteTab(id: String)
    suspend fun closeTab(id: String)
    suspend fun deleteAllTabs()
    suspend fun closeAllTabs()
    suspend fun reorderTabs(orderedTabs: List<Tab>)

    val allTabs: Flow<List<Tab>>
        get() = getAllTabs()

    val recentTabs: Flow<List<Tab>>
        get() = getRecentTabs(6)
}
```

### 3.2 `TabRepositoryImpl` (`com.jestk.browser.data.repository.TabRepositoryImpl`)

```kotlin
package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.TabDao
import com.jestk.browser.data.local.entity.toDomain
import com.jestk.browser.data.local.entity.toEntity
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.repository.TabRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class TabRepositoryImpl(
    private val tabDao: TabDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TabRepository {

    override fun getAllTabs(): Flow<List<Tab>> =
        tabDao.getAllTabsFlow()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun getRecentTabs(limit: Int): Flow<List<Tab>> =
        tabDao.getRecentTabsFlow(limit)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getInitialTabs(): List<Tab> = withContext(ioDispatcher) {
        tabDao.getAllTabs().map { it.toDomain() }
    }

    override suspend fun getTabById(id: String): Tab? = withContext(ioDispatcher) {
        tabDao.getTabById(id)?.toDomain()
    }

    override fun getTabByIdFlow(id: String): Flow<Tab?> =
        tabDao.getTabByIdFlow(id)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override suspend fun createTab(url: String, title: String, isIncognito: Boolean): Tab = withContext(ioDispatcher) {
        val existingTabs = tabDao.getAllTabs()
        val nextPosition = existingTabs.size
        val newTab = Tab(
            id = UUID.randomUUID().toString(),
            url = url,
            title = title.ifEmpty { if (url.isEmpty()) "Home" else "New Tab" },
            lastActiveTimestamp = System.currentTimeMillis(),
            snapshotPath = null,
            position = nextPosition,
            isIncognito = isIncognito,
            isActive = false,
            canGoBack = false,
            canGoForward = false
        )
        tabDao.insertTab(newTab.toEntity())
        newTab
    }

    override suspend fun updateTab(tab: Tab) = withContext(ioDispatcher) {
        tabDao.updateTab(tab.toEntity())
    }

    override suspend fun updateTabDetails(
        id: String,
        url: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) = withContext(ioDispatcher) {
        tabDao.updateTabDetails(id, url, title, canGoBack, canGoForward)
    }

    override suspend fun updateSnapshot(id: String, snapshotPath: String) = withContext(ioDispatcher) {
        tabDao.updateSnapshotPath(id, snapshotPath)
    }

    override suspend fun setActiveTab(id: String) = withContext(ioDispatcher) {
        tabDao.setActiveTab(id)
    }

    override suspend fun deleteTab(id: String) = withContext(ioDispatcher) {
        tabDao.deleteTabById(id)
        val remaining = tabDao.getAllTabs()
        tabDao.reorderTabs(remaining)
    }

    override suspend fun closeTab(id: String) = deleteTab(id)

    override suspend fun deleteAllTabs() = withContext(ioDispatcher) {
        tabDao.deleteAllTabs()
    }

    override suspend fun closeAllTabs() = deleteAllTabs()

    override suspend fun reorderTabs(orderedTabs: List<Tab>) = withContext(ioDispatcher) {
        tabDao.reorderTabs(orderedTabs.map { it.toEntity() })
    }
}
```

---

### 3.3 `HistoryRepository` (`com.jestk.browser.domain.repository.HistoryRepository`)

```kotlin
package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.HistoryItem
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getAllHistory(): Flow<List<HistoryItem>>
    fun getRecentHistory(limit: Int = 20): Flow<List<HistoryItem>>
    fun searchHistory(query: String): Flow<List<HistoryItem>>
    suspend fun recordVisit(url: String, title: String)
    suspend fun deleteHistory(id: Long)
    suspend fun deleteHistoryItem(id: Long)
    suspend fun clearHistory()

    val allHistory: Flow<List<HistoryItem>>
        get() = getAllHistory()
}
```

### 3.4 `HistoryRepositoryImpl` (`com.jestk.browser.data.repository.HistoryRepositoryImpl`)

```kotlin
package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.HistoryDao
import com.jestk.browser.data.local.entity.toDomain
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.repository.HistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class HistoryRepositoryImpl(
    private val historyDao: HistoryDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : HistoryRepository {

    override fun getAllHistory(): Flow<List<HistoryItem>> =
        historyDao.getAllHistoryFlow()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun getRecentHistory(limit: Int): Flow<List<HistoryItem>> =
        historyDao.getRecentHistoryFlow(limit)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun searchHistory(query: String): Flow<List<HistoryItem>> =
        historyDao.searchHistoryFlow(query)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun recordVisit(url: String, title: String) = withContext(ioDispatcher) {
        if (url.isNotBlank() && !url.startsWith("about:")) {
            historyDao.recordVisit(url, title)
        }
    }

    override suspend fun deleteHistory(id: Long) = withContext(ioDispatcher) {
        historyDao.deleteHistoryById(id)
    }

    override suspend fun deleteHistoryItem(id: Long) = deleteHistory(id)

    override suspend fun clearHistory() = withContext(ioDispatcher) {
        historyDao.clearAllHistory()
    }
}
```

---

### 3.5 `FavoriteRepository` (`com.jestk.browser.domain.repository.FavoriteRepository`)

```kotlin
package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.FavoriteItem
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    fun getFavorites(): Flow<List<FavoriteItem>>
    fun getFavoritesLimited(limit: Int = 6): Flow<List<FavoriteItem>>
    suspend fun addFavorite(title: String, url: String, iconUrl: String? = null): Long
    suspend fun deleteFavorite(id: Long)
    suspend fun removeFavorite(id: Long)
    suspend fun isFavorite(url: String): Boolean
    suspend fun reorderFavorites(orderedFavorites: List<FavoriteItem>)
    suspend fun getFavoritesCount(): Int

    val allFavorites: Flow<List<FavoriteItem>>
        get() = getFavorites()

    val topFavorites: Flow<List<FavoriteItem>>
        get() = getFavoritesLimited(6)
}
```

### 3.6 `FavoriteRepositoryImpl` (`com.jestk.browser.data.repository.FavoriteRepositoryImpl`)

```kotlin
package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.FavoriteDao
import com.jestk.browser.data.local.entity.FavoriteEntity
import com.jestk.browser.data.local.entity.toDomain
import com.jestk.browser.data.local.entity.toEntity
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.repository.FavoriteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FavoriteRepositoryImpl(
    private val favoriteDao: FavoriteDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FavoriteRepository {

    override fun getFavorites(): Flow<List<FavoriteItem>> =
        favoriteDao.getAllFavoritesFlow()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun getFavoritesLimited(limit: Int): Flow<List<FavoriteItem>> =
        favoriteDao.getFavoritesLimitedFlow(limit)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun addFavorite(title: String, url: String, iconUrl: String?): Long = withContext(ioDispatcher) {
        val existing = favoriteDao.getFavoriteByUrl(url)
        if (existing != null) {
            existing.id
        } else {
            val count = favoriteDao.getFavoritesCount()
            val entity = FavoriteEntity(
                title = title.ifEmpty { url },
                url = url,
                position = count,
                iconUrl = iconUrl,
                createdAt = System.currentTimeMillis()
            )
            favoriteDao.insertFavorite(entity)
        }
    }

    override suspend fun deleteFavorite(id: Long) = withContext(ioDispatcher) {
        favoriteDao.deleteFavoriteById(id)
    }

    override suspend fun removeFavorite(id: Long) = deleteFavorite(id)

    override suspend fun isFavorite(url: String): Boolean = withContext(ioDispatcher) {
        favoriteDao.getFavoriteByUrl(url) != null
    }

    override suspend fun reorderFavorites(orderedFavorites: List<FavoriteItem>) = withContext(ioDispatcher) {
        favoriteDao.reorderFavorites(orderedFavorites.map { it.toEntity() })
    }

    override suspend fun getFavoritesCount(): Int = withContext(ioDispatcher) {
        favoriteDao.getFavoritesCount()
    }
}
```

---

### 3.7 `SettingsRepository` (`com.jestk.browser.domain.repository.SettingsRepository`)

```kotlin
package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<BrowserSettings>
    suspend fun getCurrentSettings(): BrowserSettings
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setSearchEngine(engine: SearchEngine)
    suspend fun setAdBlockerEnabled(enabled: Boolean)

    val settingsFlow: Flow<BrowserSettings>
        get() = getSettings()
}
```

### 3.8 `SettingsRepositoryImpl` (`com.jestk.browser.data.repository.SettingsRepositoryImpl`)

```kotlin
package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.SettingDao
import com.jestk.browser.data.local.entity.SettingEntity
import com.jestk.browser.data.local.entity.SettingKeys
import com.jestk.browser.data.local.entity.toBrowserSettings
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(
    private val settingDao: SettingDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SettingsRepository {

    override fun getSettings(): Flow<BrowserSettings> =
        settingDao.getAllSettingsFlow()
            .map { list -> list.toBrowserSettings() }
            .flowOn(ioDispatcher)

    override suspend fun getCurrentSettings(): BrowserSettings = withContext(ioDispatcher) {
        val themeStr = settingDao.getSetting(SettingKeys.THEME_MODE)
        val searchStr = settingDao.getSetting(SettingKeys.SEARCH_ENGINE)
        val adBlockerStr = settingDao.getSetting(SettingKeys.AD_BLOCKER_ENABLED)

        val theme = themeStr?.let { runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM) } ?: ThemeMode.SYSTEM
        val search = searchStr?.let { runCatching { SearchEngine.valueOf(it) }.getOrDefault(SearchEngine.DUCKDUCKGO) } ?: SearchEngine.DUCKDUCKGO
        val adBlocker = adBlockerStr?.toBooleanStrictOrNull() ?: true

        BrowserSettings(themeMode = theme, searchEngine = search, isAdBlockerEnabled = adBlocker)
    }

    override suspend fun setThemeMode(mode: ThemeMode) = withContext(ioDispatcher) {
        settingDao.setSetting(SettingEntity(SettingKeys.THEME_MODE, mode.name))
    }

    override suspend fun setSearchEngine(engine: SearchEngine) = withContext(ioDispatcher) {
        settingDao.setSetting(SettingEntity(SettingKeys.SEARCH_ENGINE, engine.name))
    }

    override suspend fun setAdBlockerEnabled(enabled: Boolean) = withContext(ioDispatcher) {
        settingDao.setSetting(SettingEntity(SettingKeys.AD_BLOCKER_ENABLED, enabled.toString()))
    }
}
```

---

## 4. MVVM Architecture & ViewModels

### 4.1 UI State Machine (`BrowserUiState.kt`)

```kotlin
package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab

data class BrowserUiState(
    // Tabs & Sessions
    val tabs: List<Tab> = emptyList(),
    val recentTabs: List<Tab> = emptyList(),
    val activeTabId: String? = null,

    // Active Viewport / Overlay
    val activeOverlay: OverlayType = OverlayType.NONE,

    // Web Navigation & Progress (for active tab)
    val currentUrl: String = "",
    val pageTitle: String = "",
    val isLoading: Boolean = false,
    val loadingProgress: Float = 0f,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,

    // Search Overlay State
    val searchQuery: String = "",
    val searchHistorySuggestions: List<HistoryItem> = emptyList(),

    // Favorites & Recents
    val favorites: List<FavoriteItem> = emptyList(),
    val isFavoritesExpanded: Boolean = false,

    // Settings
    val settings: BrowserSettings = BrowserSettings(),

    // UI Dynamics & Gestures
    val isBottomBarVisible: Boolean = true,
    val bottomBarOffsetY: Float = 0f,

    // Media & Fullscreen
    val isVideoPlaying: Boolean = false,
    val isFullscreenCustomView: Boolean = false,
    val fullscreenCustomView: Any? = null
) {
    val activeTab: Tab?
        get() = tabs.find { it.id == activeTabId }

    val activeTabIndex: Int
        get() = tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)

    val bottomBarVisible: Boolean
        get() = isBottomBarVisible

    val loadProgress: Int
        get() = (loadingProgress * 100).toInt()
}
```

---

### 4.2 UI Events & Intents (`BrowserUiEvent.kt`)

```kotlin
package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode

sealed interface BrowserUiEvent {
    // Tab Management
    data class OpenNewTab(val url: String = "") : BrowserUiEvent
    data class SelectTab(val tabId: String) : BrowserUiEvent
    data class CloseTab(val tabId: String) : BrowserUiEvent
    data object CloseAllTabs : BrowserUiEvent
    data class ReorderTabs(val orderedTabs: List<Tab>) : BrowserUiEvent
    data object SwipeNextTab : BrowserUiEvent
    data object SwipePrevTab : BrowserUiEvent

    // Overlays
    data class OpenOverlay(val overlay: OverlayType) : BrowserUiEvent
    data object DismissOverlay : BrowserUiEvent
    data object OpenHome : BrowserUiEvent
    data class OpenSearch(val prefilledUrl: String? = null) : BrowserUiEvent
    data object OpenTabGrid : BrowserUiEvent
    data object OpenSettings : BrowserUiEvent

    // Web Navigation & Actions
    data class LoadUrl(val url: String) : BrowserUiEvent
    data class SubmitSearch(val query: String) : BrowserUiEvent
    data class UpdateSearchQuery(val query: String) : BrowserUiEvent
    data object GoBack : BrowserUiEvent
    data object GoForward : BrowserUiEvent
    data object Reload : BrowserUiEvent
    data object StopLoading : BrowserUiEvent

    // Web Callbacks
    data class OnPageStarted(val tabId: String, val url: String) : BrowserUiEvent
    data class OnPageFinished(val tabId: String, val url: String, val title: String) : BrowserUiEvent
    data class OnProgressChanged(val tabId: String, val progress: Int) : BrowserUiEvent
    data class OnNavigationStateChanged(val tabId: String, val canGoBack: Boolean, val canGoForward: Boolean) : BrowserUiEvent
    data class OnSnapshotCaptured(val tabId: String, val snapshotPath: String) : BrowserUiEvent

    // Favorites & History
    data object AddCurrentTabToFavorites : BrowserUiEvent
    data class AddFavorite(val title: String, val url: String) : BrowserUiEvent
    data class RemoveFavorite(val id: Long) : BrowserUiEvent
    data object ToggleFavoritesExpanded : BrowserUiEvent
    data class DeleteHistoryItem(val id: Long) : BrowserUiEvent
    data object ClearAllHistory : BrowserUiEvent
    data class SelectHistorySuggestion(val item: HistoryItem) : BrowserUiEvent

    // Settings
    data class SetThemeMode(val mode: ThemeMode) : BrowserUiEvent
    data class SetSearchEngine(val engine: SearchEngine) : BrowserUiEvent
    data class SetAdBlockerEnabled(val enabled: Boolean) : BrowserUiEvent

    // Dynamic UI Translation
    data class OnWebScroll(val deltaY: Float) : BrowserUiEvent
    data class SetBottomBarVisibility(val visible: Boolean) : BrowserUiEvent

    // Media & Fullscreen
    data class SetVideoPlaying(val isPlaying: Boolean) : BrowserUiEvent
    data class SetFullscreenCustomView(val view: Any?, val isFullscreen: Boolean) : BrowserUiEvent

    // System Back Handling
    data object HandleBackPress : BrowserUiEvent
}
```

---

### 4.3 Navigation Action Channels (`NavigationCommand.kt`)

```kotlin
package com.jestk.browser.ui.viewmodel

sealed interface NavigationCommand {
    data class LoadUrl(val url: String) : NavigationCommand
    data object GoBack : NavigationCommand
    data object GoForward : NavigationCommand
    data object Reload : NavigationCommand
    data object StopLoading : NavigationCommand
}
```

---

### 4.4 `BrowserViewModel` Implementation (`BrowserViewModel.kt`)

```kotlin
package com.jestk.browser.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowserViewModel(
    private val tabRepository: TabRepository,
    private val historyRepository: HistoryRepository,
    private val favoriteRepository: FavoriteRepository,
    private val settingsRepository: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _navigationCommands = MutableSharedFlow<NavigationCommand>(extraBufferCapacity = 16)
    val navigationCommands: SharedFlow<NavigationCommand> = _navigationCommands.asSharedFlow()

    init {
        restoreSession()
        observeDatabaseFlows()
    }

    private fun restoreSession() {
        viewModelScope.launch(ioDispatcher) {
            val initialTabs = tabRepository.getInitialTabs()
            val initialSettings = settingsRepository.getCurrentSettings()

            val activeTab: Tab = if (initialTabs.isEmpty()) {
                val newTab = tabRepository.createTab(url = "", title = "Home")
                tabRepository.setActiveTab(newTab.id)
                newTab.copy(isActive = true)
            } else {
                val found = initialTabs.find { it.isActive }
                    ?: initialTabs.maxByOrNull { it.lastActiveTimestamp }
                    ?: initialTabs.first()
                tabRepository.setActiveTab(found.id)
                found.copy(isActive = true)
            }

            val tabsWithActive = if (initialTabs.isEmpty()) {
                listOf(activeTab)
            } else {
                initialTabs.map { it.copy(isActive = it.id == activeTab.id) }
            }

            _uiState.update { state ->
                state.copy(
                    tabs = tabsWithActive,
                    activeTabId = activeTab.id,
                    currentUrl = activeTab.url,
                    pageTitle = activeTab.title,
                    canGoBack = activeTab.canGoBack,
                    canGoForward = activeTab.canGoForward,
                    settings = initialSettings,
                    activeOverlay = if (activeTab.url.isEmpty() || activeTab.url == "about:blank") OverlayType.HOME else OverlayType.NONE
                )
            }
        }
    }

    private fun observeDatabaseFlows() {
        viewModelScope.launch {
            tabRepository.getAllTabs().collectLatest { tabs ->
                _uiState.update { state ->
                    val activeId = state.activeTabId ?: tabs.firstOrNull()?.id
                    val currentTab = tabs.find { it.id == activeId }
                    state.copy(
                        tabs = tabs,
                        activeTabId = activeId,
                        currentUrl = currentTab?.url ?: state.currentUrl,
                        pageTitle = currentTab?.title ?: state.pageTitle
                    )
                }
            }
        }

        viewModelScope.launch {
            tabRepository.getRecentTabs(6).collectLatest { recents ->
                _uiState.update { it.copy(recentTabs = recents) }
            }
        }

        viewModelScope.launch {
            favoriteRepository.getFavorites().collectLatest { favorites ->
                _uiState.update { it.copy(favorites = favorites) }
            }
        }

        viewModelScope.launch {
            settingsRepository.getSettings().collectLatest { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun onEvent(event: BrowserUiEvent) {
        when (event) {
            is BrowserUiEvent.OpenNewTab -> openNewTab(event.url)
            is BrowserUiEvent.SelectTab -> selectTab(event.tabId)
            is BrowserUiEvent.CloseTab -> closeTab(event.tabId)
            is BrowserUiEvent.CloseAllTabs -> closeAllTabs()
            is BrowserUiEvent.ReorderTabs -> reorderTabs(event.orderedTabs)
            is BrowserUiEvent.SwipeNextTab -> switchAdjacentTab(1)
            is BrowserUiEvent.SwipePrevTab -> switchAdjacentTab(-1)

            is BrowserUiEvent.OpenOverlay -> _uiState.update { it.copy(activeOverlay = event.overlay) }
            is BrowserUiEvent.DismissOverlay -> _uiState.update { it.copy(activeOverlay = OverlayType.NONE) }
            is BrowserUiEvent.OpenHome -> _uiState.update { it.copy(activeOverlay = OverlayType.HOME) }
            is BrowserUiEvent.OpenSearch -> {
                val prefill = event.prefilledUrl ?: _uiState.value.currentUrl
                _uiState.update { it.copy(activeOverlay = OverlayType.SEARCH, searchQuery = prefill) }
                updateHistorySuggestions(prefill)
            }
            is BrowserUiEvent.OpenTabGrid -> _uiState.update { it.copy(activeOverlay = OverlayType.TAB_GRID) }
            is BrowserUiEvent.OpenSettings -> _uiState.update { it.copy(activeOverlay = OverlayType.SETTINGS) }

            is BrowserUiEvent.LoadUrl -> loadUrl(event.url)
            is BrowserUiEvent.SubmitSearch -> submitSearch(event.query)
            is BrowserUiEvent.UpdateSearchQuery -> {
                _uiState.update { it.copy(searchQuery = event.query) }
                updateHistorySuggestions(event.query)
            }
            is BrowserUiEvent.GoBack -> handleGoBack()
            is BrowserUiEvent.GoForward -> _navigationCommands.tryEmit(NavigationCommand.GoForward)
            is BrowserUiEvent.Reload -> _navigationCommands.tryEmit(NavigationCommand.Reload)
            is BrowserUiEvent.StopLoading -> {
                _navigationCommands.tryEmit(NavigationCommand.StopLoading)
                _uiState.update { it.copy(isLoading = false) }
            }

            is BrowserUiEvent.OnPageStarted -> handlePageStarted(event.tabId, event.url)
            is BrowserUiEvent.OnPageFinished -> handlePageFinished(event.tabId, event.url, event.title)
            is BrowserUiEvent.OnProgressChanged -> handleProgressChanged(event.tabId, event.progress)
            is BrowserUiEvent.OnNavigationStateChanged -> handleNavState(event.tabId, event.canGoBack, event.canGoForward)
            is BrowserUiEvent.OnSnapshotCaptured -> handleSnapshot(event.tabId, event.snapshotPath)

            is BrowserUiEvent.AddCurrentTabToFavorites -> addCurrentToFavorites()
            is BrowserUiEvent.AddFavorite -> viewModelScope.launch(ioDispatcher) {
                favoriteRepository.addFavorite(event.title, event.url)
            }
            is BrowserUiEvent.RemoveFavorite -> viewModelScope.launch(ioDispatcher) {
                favoriteRepository.deleteFavorite(event.id)
            }
            is BrowserUiEvent.ToggleFavoritesExpanded -> _uiState.update { it.copy(isFavoritesExpanded = !it.isFavoritesExpanded) }
            is BrowserUiEvent.DeleteHistoryItem -> viewModelScope.launch(ioDispatcher) {
                historyRepository.deleteHistory(event.id)
            }
            is BrowserUiEvent.ClearAllHistory -> viewModelScope.launch(ioDispatcher) {
                historyRepository.clearHistory()
            }
            is BrowserUiEvent.SelectHistorySuggestion -> loadUrl(event.item.url)

            is BrowserUiEvent.SetThemeMode -> viewModelScope.launch(ioDispatcher) {
                settingsRepository.setThemeMode(event.mode)
            }
            is BrowserUiEvent.SetSearchEngine -> viewModelScope.launch(ioDispatcher) {
                settingsRepository.setSearchEngine(event.engine)
            }
            is BrowserUiEvent.SetAdBlockerEnabled -> viewModelScope.launch(ioDispatcher) {
                settingsRepository.setAdBlockerEnabled(event.enabled)
            }

            is BrowserUiEvent.OnWebScroll -> handleWebScroll(event.deltaY)
            is BrowserUiEvent.SetBottomBarVisibility -> _uiState.update { it.copy(isBottomBarVisible = event.visible) }

            is BrowserUiEvent.SetVideoPlaying -> _uiState.update { it.copy(isVideoPlaying = event.isPlaying) }
            is BrowserUiEvent.SetFullscreenCustomView -> _uiState.update {
                it.copy(fullscreenCustomView = event.view, isFullscreenCustomView = event.isFullscreen)
            }

            is BrowserUiEvent.HandleBackPress -> handleBackNavigation()
        }
    }

    private fun openNewTab(url: String) {
        viewModelScope.launch(ioDispatcher) {
            val title = if (url.isEmpty() || url == "about:blank") "Home" else "Loading..."
            val created = tabRepository.createTab(url = url, title = title)
            tabRepository.setActiveTab(created.id)
            _uiState.update {
                it.copy(
                    activeTabId = created.id,
                    currentUrl = url,
                    pageTitle = title,
                    isLoading = url.isNotEmpty(),
                    activeOverlay = if (url.isEmpty() || url == "about:blank") OverlayType.HOME else OverlayType.NONE
                )
            }
            if (url.isNotEmpty() && url != "about:blank") {
                _navigationCommands.tryEmit(NavigationCommand.LoadUrl(url))
            }
        }
    }

    private fun selectTab(tabId: String) {
        viewModelScope.launch(ioDispatcher) {
            tabRepository.setActiveTab(tabId)
            val selected = tabRepository.getTabById(tabId)
            _uiState.update {
                it.copy(
                    activeTabId = tabId,
                    currentUrl = selected?.url.orEmpty(),
                    pageTitle = selected?.title.orEmpty(),
                    canGoBack = selected?.canGoBack ?: false,
                    canGoForward = selected?.canGoForward ?: false,
                    activeOverlay = if (selected?.url.isNullOrEmpty() || selected?.url == "about:blank") OverlayType.HOME else OverlayType.NONE
                )
            }
        }
    }

    private fun closeTab(tabId: String) {
        viewModelScope.launch(ioDispatcher) {
            tabRepository.closeTab(tabId)
            val remainingTabs = tabRepository.getInitialTabs()
            if (remainingTabs.isEmpty()) {
                val newTab = tabRepository.createTab(url = "", title = "Home")
                tabRepository.setActiveTab(newTab.id)
                _uiState.update {
                    it.copy(
                        activeTabId = newTab.id,
                        currentUrl = "",
                        pageTitle = "Home",
                        activeOverlay = OverlayType.HOME
                    )
                }
            } else if (_uiState.value.activeTabId == tabId) {
                val nextActive = remainingTabs.first()
                tabRepository.setActiveTab(nextActive.id)
                _uiState.update {
                    it.copy(
                        activeTabId = nextActive.id,
                        currentUrl = nextActive.url,
                        pageTitle = nextActive.title,
                        canGoBack = nextActive.canGoBack,
                        canGoForward = nextActive.canGoForward,
                        activeOverlay = if (nextActive.url.isEmpty() || nextActive.url == "about:blank") OverlayType.HOME else OverlayType.NONE
                    )
                }
            }
        }
    }

    private fun closeAllTabs() {
        viewModelScope.launch(ioDispatcher) {
            tabRepository.closeAllTabs()
            val newTab = tabRepository.createTab(url = "", title = "Home")
            tabRepository.setActiveTab(newTab.id)
            _uiState.update {
                it.copy(
                    activeTabId = newTab.id,
                    currentUrl = "",
                    pageTitle = "Home",
                    activeOverlay = OverlayType.HOME
                )
            }
        }
    }

    private fun switchAdjacentTab(delta: Int) {
        val currentTabs = _uiState.value.tabs
        if (currentTabs.size <= 1) return
        val currentIndex = _uiState.value.activeTabIndex
        val targetIndex = (currentIndex + delta).coerceIn(0, currentTabs.lastIndex)
        if (targetIndex != currentIndex) {
            selectTab(currentTabs[targetIndex].id)
        }
    }

    private fun reorderTabs(orderedTabs: List<Tab>) {
        viewModelScope.launch(ioDispatcher) {
            tabRepository.reorderTabs(orderedTabs)
        }
    }

    private fun loadUrl(rawUrl: String) {
        val trimmed = rawUrl.trim()
        val finalUrl = when {
            trimmed.isEmpty() -> ""
            trimmed == "about:blank" -> ""
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> _uiState.value.settings.searchEngine.buildSearchUrl(trimmed)
        }

        val activeId = _uiState.value.activeTabId ?: return
        if (finalUrl.isEmpty()) {
            _uiState.update {
                it.copy(
                    currentUrl = "",
                    pageTitle = "Home",
                    isLoading = false,
                    activeOverlay = OverlayType.HOME
                )
            }
            viewModelScope.launch(ioDispatcher) {
                tabRepository.updateTabDetails(activeId, "", "Home", canGoBack = false, canGoForward = false)
            }
            return
        }

        _uiState.update {
            it.copy(
                currentUrl = finalUrl,
                isLoading = true,
                loadingProgress = 0.1f,
                activeOverlay = OverlayType.NONE
            )
        }
        viewModelScope.launch(ioDispatcher) {
            tabRepository.updateTabDetails(activeId, finalUrl, finalUrl, canGoBack = false, canGoForward = false)
        }
        _navigationCommands.tryEmit(NavigationCommand.LoadUrl(finalUrl))
    }

    private fun submitSearch(query: String) {
        loadUrl(query)
    }

    private fun updateHistorySuggestions(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchHistorySuggestions = emptyList()) }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            historyRepository.searchHistory(query).collectLatest { results ->
                _uiState.update { it.copy(searchHistorySuggestions = results) }
            }
        }
    }

    private fun handlePageStarted(tabId: String, url: String) {
        if (_uiState.value.activeTabId == tabId) {
            _uiState.update { it.copy(currentUrl = url, isLoading = true, loadingProgress = 0.15f) }
        }
    }

    private fun handlePageFinished(tabId: String, url: String, title: String) {
        val displayTitle = title.ifEmpty { url }
        if (_uiState.value.activeTabId == tabId) {
            _uiState.update {
                it.copy(
                    currentUrl = url,
                    pageTitle = displayTitle,
                    isLoading = false,
                    loadingProgress = 1.0f
                )
            }
        }
        viewModelScope.launch(ioDispatcher) {
            tabRepository.updateTabDetails(tabId, url, displayTitle, canGoBack = true, canGoForward = false)
            if (url.isNotEmpty() && !url.startsWith("about:")) {
                historyRepository.recordVisit(url, displayTitle)
            }
        }
    }

    private fun handleProgressChanged(tabId: String, progress: Int) {
        if (_uiState.value.activeTabId == tabId) {
            _uiState.update {
                it.copy(
                    loadingProgress = (progress / 100f).coerceIn(0f, 1f),
                    isLoading = progress < 100
                )
            }
        }
    }

    private fun handleNavState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        if (_uiState.value.activeTabId == tabId) {
            _uiState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
        }
    }

    private fun handleSnapshot(tabId: String, path: String) {
        viewModelScope.launch(ioDispatcher) {
            tabRepository.updateSnapshot(tabId, path)
        }
    }

    private fun addCurrentToFavorites() {
        val current = _uiState.value
        if (current.currentUrl.isNotEmpty() && !current.currentUrl.startsWith("about:")) {
            viewModelScope.launch(ioDispatcher) {
                favoriteRepository.addFavorite(
                    title = current.pageTitle.ifEmpty { current.currentUrl },
                    url = current.currentUrl
                )
            }
        }
    }

    private fun handleWebScroll(deltaY: Float) {
        if (deltaY > 10f && _uiState.value.isBottomBarVisible) {
            _uiState.update { it.copy(isBottomBarVisible = false) }
        } else if (deltaY < -10f && !_uiState.value.isBottomBarVisible) {
            _uiState.update { it.copy(isBottomBarVisible = true) }
        }
    }

    private fun handleGoBack() {
        _navigationCommands.tryEmit(NavigationCommand.GoBack)
    }

    private fun handleBackNavigation() {
        val current = _uiState.value
        when {
            current.activeOverlay != OverlayType.NONE -> {
                _uiState.update { it.copy(activeOverlay = OverlayType.NONE) }
            }
            current.canGoBack -> {
                _navigationCommands.tryEmit(NavigationCommand.GoBack)
            }
            else -> {
                current.activeTabId?.let { closeTab(it) }
            }
        }
    }
}
```

---

## 5. Cold Boot Session Restoration Deep Dive

### 5.1 Step-by-Step Restoration Protocol

1. **Database & Disk Initialization**:
   - `JestkApplication` constructs `AppContainer`.
   - `AppDatabase` singleton is initialized.
   - `SnapshotManager` ensures snapshot directory exists at `context.filesDir/snapshots/`.

2. **Session Retrieval (`restoreSession`)**:
   - `tabRepository.getInitialTabs()` executes on IO dispatcher, loading all `TabEntity` rows ordered by `position ASC`.
   - `settingsRepository.getCurrentSettings()` queries the `settings` table.

3. **Fallback & State Resolution**:
   - **Case 1 (Empty Database / Fresh Boot)**:
     - Detects `initialTabs.isEmpty()`.
     - Spawns default tab: `id = UUID.randomUUID()`, `url = ""`, `title = "Home"`, `position = 0`, `isActive = true`.
     - Inserts into Room DB and sets `activeTabId`.
     - Sets `BrowserUiState.activeOverlay = OverlayType.HOME`.
   - **Case 2 (Persisted Tabs Exist)**:
     - Disambiguates active tab: selects tab where `isActive == true`. If multiple or none, selects `maxByOrNull { it.lastActiveTimestamp }`.
     - Executes `tabDao.setActiveTab(chosen.id)` to enforce atomic single active tab.
     - Validates snapshot paths: if `snapshotPath` is non-null and points to a missing file on disk, `SnapshotManager` / UI gracefully treats it as `null` to render placeholder until the page frame is captured.
     - Sets `activeOverlay = OverlayType.NONE` (or `OverlayType.HOME` if active tab's URL is empty).

4. **UI State & WebViewPool Hand-off**:
   - `_uiState` emits the restored session state to Compose UI.
   - Active tab pill immediately populates with the title and favicon.
   - 3-column Visual Tab Overview immediately displays restored cards in correct order.
   - `WebViewPool` receives restored `activeTabId` and warms up the active `WebView`.

---

## 6. Unit Testing Architecture & Test Suites

### 6.1 Test Harness Setup
All Unit Tests run on JVM with:
- `RobolectricTestRunner` or in-memory Room database `Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()`
- `kotlinx.coroutines.test.StandardTestDispatcher` and `runTest`
- `Truth.assertThat` assertions

### 6.2 `TabRepositoryTest`
Verifies:
- Creating tabs appends correct positions.
- Reactive `getAllTabs()` emissions on insertion, deletion, and reordering.
- `setActiveTab` clearing previous active flags and marking new target.
- `updateSnapshot` and `updateTabDetails`.

### 6.3 `HistoryRepositoryTest`
Verifies:
- `recordVisit` within 5 minutes aggregates `visitCount` without duplicating rows.
- `recordVisit` with different URLs creates distinct records.
- `searchHistory` performs case-insensitive substring matching against title and URL.
- `clearHistory` purges all records.

### 6.4 `FavoriteRepositoryTest`
Verifies:
- Adding favorites increments count and sets position.
- Adding duplicate URL returns existing ID without inserting duplicate row.
- `getFavoritesLimited(6)` respects the 6-item cap.
- `reorderFavorites` updates position order atomically.

### 6.5 `SettingsRepositoryTest`
Verifies:
- Default settings match `SYSTEM`, `DUCKDUCKGO`, `true`.
- Updating `ThemeMode`, `SearchEngine`, and `isAdBlockerEnabled` persists to DB and emits updated `BrowserSettings`.
- Corrupted string in DB falls back safely to default enum values without throwing.

### 6.6 `BrowserViewModelTest`
Verifies:
- Cold boot on empty DB restores 1 Home tab with `OverlayType.HOME`.
- Cold boot on existing tabs restores active tab correctly.
- `BrowserUiEvent.OpenNewTab` creates tab and switches active tab.
- `BrowserUiEvent.CloseTab` switches to adjacent tab; closing last tab creates fresh Home tab.
- `BrowserUiEvent.LoadUrl` formats hostnames (`"example.com"` -> `"https://example.com"`) and search queries (`"hello world"` -> DuckDuckGo URL).
- `BrowserUiEvent.HandleBackPress` adheres strictly to hierarchy: Dismiss Overlay -> WebView GoBack -> Close Tab.
- `BrowserUiEvent.OnWebScroll` toggles `isBottomBarVisible`.

---

## 7. Edge Cases & Resilience Strategy Matrix

| # | Scenario | Root Risk | Architectural Resolution |
|---|----------|-----------|--------------------------|
| 1 | Cold Boot with 0 tabs | Blank UI or Crash | `restoreSession()` creates Home tab `position=0, isActive=true, url=""` and sets `OverlayType.HOME`. |
| 2 | Multiple `isActive=1` rows | Ambiguous active tab | Selects tab with maximum `lastActiveTimestamp` and executes `@Transaction setActiveTab()` to clean flags. |
| 3 | Snapshot file deleted from disk | `FileNotFoundException` | UI / Snapshot loader checks `File.exists()`; if missing, falls back to site favicon/title card. |
| 4 | User closes last open tab | App enters zombie state | `closeTab()` detects empty list and spawns fresh Home tab immediately. |
| 5 | Reordering tabs in grid/strip | Duplicate position indices | `@Transaction reorderTabs` loops through list and assigns contiguous `0..N-1` indices. |
| 6 | URL without scheme entered | Navigation failure | `loadUrl()` detects absence of protocol: if domain-like (`reddit.com`), prepends `"https://"`; otherwise formats as search query. |
| 7 | Corrupted settings string in DB | `IllegalArgumentException` in Enum parsing | Wrapped in `runCatching { Enum.valueOf() }.getOrDefault(DEFAULT)`. |
| 8 | Rapid tab switching | State race conditions | Unidirectional event queue processed sequentially in `viewModelScope`. |

---

## 8. Milestone 1 Implementation Plan

1. **Domain Models**:
   - `Tab.kt`, `HistoryItem.kt`, `FavoriteItem.kt`, `ThemeMode.kt`, `SearchEngine.kt`, `BrowserSettings.kt`, `OverlayType.kt`.
2. **Entity Mapping Extensions**:
   - `TabMapping.kt`, `HistoryMapping.kt`, `FavoriteMapping.kt`, `SettingMapping.kt`.
3. **Repository Interfaces & Implementations**:
   - `TabRepository.kt` & `TabRepositoryImpl.kt`
   - `HistoryRepository.kt` & `HistoryRepositoryImpl.kt`
   - `FavoriteRepository.kt` & `FavoriteRepositoryImpl.kt`
   - `SettingsRepository.kt` & `SettingsRepositoryImpl.kt`
4. **Dependency Injection**:
   - `AppContainer.kt` & `DefaultAppContainer.kt`
   - `JestkApplication.kt`
5. **MVVM ViewModels & State**:
   - `BrowserUiState.kt`
   - `BrowserUiEvent.kt`
   - `NavigationCommand.kt`
   - `BrowserViewModel.kt`
6. **Unit Test Suite**:
   - `TabRepositoryTest.kt`
   - `HistoryRepositoryTest.kt`
   - `FavoriteRepositoryTest.kt`
   - `SettingsRepositoryTest.kt`
   - `BrowserViewModelTest.kt`
