# Technical Analysis: Room Database Architecture, Entities & DAOs

**Component:** Milestone 1 — Room Database Persistence Layer  
**Author:** Explorer 2 (Milestone 1 — Database & DAO Specialist)  
**Target Package:** `com.jestk.browser.data.local`  
**Dependencies:** Room 2.6.1 (`androidx.room:room-runtime`, `androidx.room:room-ktx`, `androidx.room:room-compiler`), Kotlin Coroutines 1.9.0, AndroidX Core, KSP

---

## 1. Executive Summary

The persistence architecture for **Jestk Browser** is designed to provide an offline-first, crash-resilient local storage layer supporting the browser's core feature set:
- Tab session persistence, ordering, and snapshot linkage for instant cold boot recovery.
- Browsing history with time-window aggregation and fast fuzzy search indexing.
- User bookmarks (Favorites) for the Home Screen 2D grid with custom position ordering.
- Application-level key-value settings storage (theme, search engine, ad blocker toggle).

All queries exposing changing state return reactive `kotlinx.coroutines.flow.Flow` streams to power unidirectional MVVM state updates in `BrowserViewModel`. Mutating operations utilize Kotlin `suspend` functions and atomic `@Transaction` annotations for data consistency.

---

## 2. Room Entity Architecture & Schema Design

### 2.1 Database Overview
- **Database File:** `jestk_browser.db`
- **Schema Version:** `1`
- **Export Schema:** `false` (in initial scaffold, with schema generation option enabled)
- **Migration Strategy:** `fallbackToDestructiveMigration()` during development/initial release.

---

### 2.2 `TabEntity` (Table: `tabs`)

The `tabs` table stores metadata and session state for every open browser tab.

#### Column Definitions & Constraints
| Column Name | SQLite Type | Kotlin Type | Constraints / Default | Purpose |
|---|---|---|---|---|
| `id` | `TEXT` | `String` | `PRIMARY KEY` | Unique tab UUID (e.g. `UUID.randomUUID().toString()`), mapped 1:1 with `WebViewPool` instances. |
| `url` | `TEXT` | `String` | `NOT NULL`, default `""` | Current web page URL. Empty string (`""`) or `about:blank` represents Home view. |
| `title` | `TEXT` | `String` | `NOT NULL`, default `"New Tab"` | Page title extracted from WebView, rendered on active pill and grid cards. |
| `snapshotPath` | `TEXT` | `String?` | `NULLABLE`, default `null` | Absolute path in app storage to cached thumbnail image captured via `postVisualStateCallback`. |
| `faviconUrl` | `TEXT` | `String?` | `NULLABLE`, default `null` | Remote or local URI for the tab's favicon icon. |
| `lastActiveTimestamp` | `INTEGER` | `Long` | `NOT NULL`, default `System.currentTimeMillis()` | Millisecond timestamp of last user interaction, used for Recent Tabs carousel ordering. |
| `position` | `INTEGER` | `Int` | `NOT NULL`, default `0` | Visual display position in the horizontal tab strip and 3-column tab grid. |
| `isIncognito` | `INTEGER` | `Boolean` | `NOT NULL`, default `false` | If true, history tracking and session restoration bypass this tab. |
| `isActive` | `INTEGER` | `Boolean` | `NOT NULL`, default `false` | Flags the currently focused tab for instant cold-boot restore. |
| `canGoBack` | `INTEGER` | `Boolean` | `NOT NULL`, default `false` | Cached backward navigation availability in WebView history. |
| `canGoForward` | `INTEGER` | `Boolean` | `NOT NULL`, default `false` | Cached forward navigation availability in WebView history. |

#### Indices
- `Index(value = ["position"])`: Optimizes `ORDER BY position ASC` queries for Tab Strip and Tab Grid.
- `Index(value = ["lastActiveTimestamp"])`: Optimizes `ORDER BY lastActiveTimestamp DESC LIMIT :limit` queries for Recent Tabs.

#### Kotlin Implementation (`TabEntity.kt`)
```kotlin
package com.jestk.browser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tabs",
    indices = [
        Index(value = ["position"]),
        Index(value = ["lastActiveTimestamp"])
    ]
)
data class TabEntity(
    @PrimaryKey
    val id: String,
    val url: String = "",
    val title: String = "New Tab",
    val snapshotPath: String? = null,
    val faviconUrl: String? = null,
    val lastActiveTimestamp: Long = System.currentTimeMillis(),
    val position: Int = 0,
    val isIncognito: Boolean = false,
    val isActive: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)
```

---

### 2.3 `HistoryEntity` (Table: `history`)

The `history` table logs user navigation visits, providing data for the Search Overlay upward-fading history stack and autocomplete suggestions.

#### Column Definitions & Constraints
| Column Name | SQLite Type | Kotlin Type | Constraints / Default | Purpose |
|---|---|---|---|---|
| `id` | `INTEGER` | `Long` | `PRIMARY KEY AUTOINCREMENT` | Auto-generated unique record ID. |
| `url` | `TEXT` | `String` | `NOT NULL` | Destination webpage URL. |
| `title` | `TEXT` | `String` | `NOT NULL` | Webpage title displayed in suggestion items. |
| `timestamp` | `INTEGER` | `Long` | `NOT NULL`, default `System.currentTimeMillis()` | Millisecond timestamp of the visit. |
| `visitCount` | `INTEGER` | `Int` | `NOT NULL`, default `1` | Total visit frequency, used for suggestion ranking. |
| `faviconUrl` | `TEXT` | `String?` | `NULLABLE`, default `null` | Favicon URL for visual history rendering. |

#### Indices
- `Index(value = ["timestamp"])`: Optimizes `ORDER BY timestamp DESC` chronological queries.
- `Index(value = ["url"])`: Optimizes exact URL lookup during visit aggregation (`SELECT * FROM history WHERE url = :url`).

#### Kotlin Implementation (`HistoryEntity.kt`)
```kotlin
package com.jestk.browser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["url"])
    ]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val visitCount: Int = 1,
    val faviconUrl: String? = null
)
```

---

### 2.4 `FavoriteEntity` (Table: `favorites`)

The `favorites` table stores user bookmarks displayed in the Home Screen 2D Grid.

#### Column Definitions & Constraints
| Column Name | SQLite Type | Kotlin Type | Constraints / Default | Purpose |
|---|---|---|---|---|
| `id` | `INTEGER` | `Long` | `PRIMARY KEY AUTOINCREMENT` | Auto-generated unique bookmark ID. |
| `url` | `TEXT` | `String` | `NOT NULL` | Bookmarked URL. |
| `title` | `TEXT` | `String` | `NOT NULL` | User-visible label on bookmark icon. |
| `position` | `INTEGER` | `Int` | `NOT NULL`, default `0` | Order in the 2D Home Screen Grid. |
| `iconUrl` | `TEXT` | `String?` | `NULLABLE`, default `null` | Bookmark favicon or custom icon URL. |
| `createdAt` | `INTEGER` | `Long` | `NOT NULL`, default `System.currentTimeMillis()` | Creation timestamp for tie-breaking sorts. |

#### Indices
- `Index(value = ["position"])`: Optimizes `ORDER BY position ASC, createdAt ASC` queries.
- `Index(value = ["url"])`: Optimizes checking if a URL is already bookmarked.

#### Kotlin Implementation (`FavoriteEntity.kt`)
```kotlin
package com.jestk.browser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["position"]),
        Index(value = ["url"])
    ]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val position: Int = 0,
    val iconUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

---

### 2.5 `SettingEntity` (Table: `settings`)

The `settings` table provides persistent key-value configuration storage.

#### Column Definitions & Constraints
| Column Name | SQLite Type | Kotlin Type | Constraints / Default | Purpose |
|---|---|---|---|---|
| `key` | `TEXT` | `String` | `PRIMARY KEY` | Unique setting key name. |
| `value` | `TEXT` | `String` | `NOT NULL` | Serialized setting value. |

#### Predefined Configuration Keys & Defaults
```kotlin
package com.jestk.browser.data.local.entity

object SettingKeys {
    const val THEME_MODE = "theme_mode"           // Values: "SYSTEM", "LIGHT", "DARK"
    const val SEARCH_ENGINE = "search_engine"     // Values: "DUCKDUCKGO", "GOOGLE", "BING"
    const val AD_BLOCKER_ENABLED = "ad_blocker_enabled" // Values: "true", "false"
    const val ACTIVE_TAB_ID = "active_tab_id"     // Values: UUID string of active tab
}
```

#### Kotlin Implementation (`SettingEntity.kt`)
```kotlin
package com.jestk.browser.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
```

---

## 3. Data Access Objects (DAOs) & Transaction Logic

### 3.1 `TabDao` Interface

The `TabDao` manages all database interactions for browser tabs, including reactive updates to tab lists and atomic active tab switches.

#### Core Capabilities:
1. Reactive `Flow<List<TabEntity>>` for tab strip and visual tab overview.
2. LRU query `getRecentTabsFlow(limit)` for the Home Screen carousel.
3. Transactional active tab switching (`setActiveTab`) ensuring only one tab is marked `isActive = true`.
4. Transactional reordering (`reorderTabs`) updating positions sequentially.

#### Kotlin Implementation (`TabDao.kt`)
```kotlin
package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jestk.browser.data.local.entity.TabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY position ASC")
    fun getAllTabsFlow(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs ORDER BY position ASC")
    suspend fun getAllTabs(): List<TabEntity>

    @Query("SELECT * FROM tabs WHERE id = :id LIMIT 1")
    suspend fun getTabById(id: String): TabEntity?

    @Query("SELECT * FROM tabs WHERE id = :id LIMIT 1")
    fun getTabByIdFlow(id: String): Flow<TabEntity?>

    @Query("SELECT * FROM tabs ORDER BY lastActiveTimestamp DESC LIMIT :limit")
    fun getRecentTabsFlow(limit: Int = 6): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs ORDER BY lastActiveTimestamp DESC LIMIT :limit")
    suspend fun getRecentTabs(limit: Int = 6): List<TabEntity>

    @Query("SELECT * FROM tabs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTab(): TabEntity?

    @Query("SELECT * FROM tabs WHERE isActive = 1 LIMIT 1")
    fun getActiveTabFlow(): Flow<TabEntity?>

    @Query("SELECT COUNT(*) FROM tabs")
    suspend fun getTabsCount(): Int

    @Query("SELECT MAX(position) FROM tabs")
    suspend fun getMaxPosition(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: TabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabs(tabs: List<TabEntity>)

    @Update
    suspend fun updateTab(tab: TabEntity)

    @Update
    suspend fun updateTabs(tabs: List<TabEntity>)

    @Delete
    suspend fun deleteTab(tab: TabEntity)

    @Query("DELETE FROM tabs WHERE id = :id")
    suspend fun deleteTabById(id: String)

    @Query("DELETE FROM tabs")
    suspend fun deleteAllTabs()

    @Query("UPDATE tabs SET snapshotPath = :snapshotPath WHERE id = :id")
    suspend fun updateSnapshotPath(id: String, snapshotPath: String)

    @Query("UPDATE tabs SET url = :url, title = :title, canGoBack = :canGoBack, canGoForward = :canGoForward WHERE id = :id")
    suspend fun updateTabDetails(
        id: String,
        url: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean
    )

    @Query("UPDATE tabs SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)

    @Query("UPDATE tabs SET isActive = 0")
    suspend fun clearActiveFlags()

    @Query("UPDATE tabs SET isActive = 1, lastActiveTimestamp = :timestamp WHERE id = :id")
    suspend fun markActive(id: String, timestamp: Long)

    @Transaction
    suspend fun setActiveTab(activeId: String) {
        clearActiveFlags()
        markActive(activeId, System.currentTimeMillis())
    }

    @Transaction
    suspend fun reorderTabs(orderedTabs: List<TabEntity>) {
        orderedTabs.forEachIndexed { index, tab ->
            updatePosition(tab.id, index)
        }
    }

    @Transaction
    suspend fun reorderTabIds(orderedTabIds: List<String>) {
        orderedTabIds.forEachIndexed { index, tabId ->
            updatePosition(tabId, index)
        }
    }
}
```

---

### 3.2 `HistoryDao` Interface

The `HistoryDao` manages browsing history records, autocomplete query searches, and 5-minute aggregation logic.

#### 5-Minute Aggregation Logic:
When `recordVisit(url, title)` is executed:
1. Check if a history row with the same `url` was recorded within the last 5 minutes (`now - existing.timestamp < 300,000 ms`).
2. If yes: Update existing row's `timestamp` to `now`, increment `visitCount` by 1, and update `title` (if non-empty).
3. If no: Insert a new `HistoryEntity` with `visitCount = 1` and `timestamp = now`.

#### Kotlin Implementation (`HistoryDao.kt`)
```kotlin
package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.jestk.browser.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistoryFlow(limit: Int = 20): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 20): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun searchHistoryFlow(query: String, limit: Int = 15): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun searchHistory(query: String, limit: Int = 15): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE url = :url ORDER BY timestamp DESC LIMIT 1")
    suspend fun getRecentByUrl(url: String): HistoryEntity?

    @Query("SELECT COUNT(*) FROM history")
    suspend fun getHistoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryEntity): Long

    @Query("UPDATE history SET timestamp = :timestamp, visitCount = visitCount + 1, title = :title WHERE id = :id")
    suspend fun updateVisit(id: Long, title: String, timestamp: Long)

    @Transaction
    suspend fun recordVisit(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        val existing = getRecentByUrl(url)
        val now = System.currentTimeMillis()
        val effectiveTitle = if (title.isNotBlank()) title else url
        if (existing != null && (now - existing.timestamp) < 300_000L) {
            updateVisit(existing.id, if (title.isNotBlank()) title else existing.title, now)
        } else {
            insertHistory(
                HistoryEntity(
                    url = url,
                    title = effectiveTitle,
                    timestamp = now,
                    visitCount = 1
                )
            )
        }
    }

    @Delete
    suspend fun deleteHistory(item: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM history WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteHistoryBefore(cutoffTimestamp: Long)

    @Query("DELETE FROM history")
    suspend fun clearAllHistory()
}
```

---

### 3.3 `FavoriteDao` Interface

The `FavoriteDao` manages bookmark creation, deletion, custom grid reordering, and limit-capped queries for the Home Screen.

#### Core Capabilities:
1. `getFavoritesLimitedFlow(limit: Int = 6)`: Returns up to 6 favorites for default Home screen view.
2. `getAllFavoritesFlow()`: Returns complete list when user taps "Show All Favorites".
3. `reorderFavorites`: Transaction updating `position` index for all items.

#### Kotlin Implementation (`FavoriteDao.kt`)
```kotlin
package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jestk.browser.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY position ASC, createdAt ASC")
    fun getAllFavoritesFlow(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY position ASC, createdAt ASC")
    suspend fun getAllFavorites(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites ORDER BY position ASC, createdAt ASC LIMIT :limit")
    fun getFavoritesLimitedFlow(limit: Int = 6): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE url = :url LIMIT 1")
    suspend fun getFavoriteByUrl(url: String): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE id = :id LIMIT 1")
    suspend fun getFavoriteById(id: Long): FavoriteEntity?

    @Query("SELECT COUNT(*) > 0 FROM favorites WHERE url = :url")
    suspend fun isFavorite(url: String): Boolean

    @Query("SELECT COUNT(*) > 0 FROM favorites WHERE url = :url")
    fun isFavoriteFlow(url: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun getFavoritesCount(): Int

    @Query("SELECT MAX(position) FROM favorites")
    suspend fun getMaxPosition(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity): Long

    @Update
    suspend fun updateFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteFavoriteById(id: Long)

    @Query("DELETE FROM favorites WHERE url = :url")
    suspend fun deleteFavoriteByUrl(url: String)

    @Query("DELETE FROM favorites")
    suspend fun clearAllFavorites()

    @Query("UPDATE favorites SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Transaction
    suspend fun reorderFavorites(orderedFavorites: List<FavoriteEntity>) {
        orderedFavorites.forEachIndexed { index, fav ->
            updatePosition(fav.id, index)
        }
    }
}
```

---

### 3.4 `SettingDao` Interface

The `SettingDao` manages key-value preference pairs.

#### Kotlin Implementation (`SettingDao.kt`)
```kotlin
package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jestk.browser.data.local.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    fun getSettingFlow(key: String): Flow<String?>

    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Query("SELECT * FROM settings")
    fun getAllSettingsFlow(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings")
    suspend fun getAllSettings(): List<SettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: SettingEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)

    @Query("DELETE FROM settings")
    suspend fun clearAllSettings()
}
```

---

## 4. Type Converters & Database Singleton Architecture

### 4.1 Type Converters (`Converters.kt`)

`Converters.kt` ensures seamless serialization of rich data types (such as `Date`, `Uri`, and `List<String>`) into SQLite primitives (`INTEGER`, `TEXT`).

#### Kotlin Implementation (`Converters.kt`)
```kotlin
package com.jestk.browser.data.local

import android.net.Uri
import androidx.room.TypeConverter
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromUriString(value: String?): Uri? {
        return value?.let { Uri.parse(it) }
    }

    @TypeConverter
    fun uriToString(uri: Uri?): String? {
        return uri?.toString()
    }

    @TypeConverter
    fun fromStringList(value: String?): List<String>? {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split("|||")
    }

    @TypeConverter
    fun stringListToString(list: List<String>?): String? {
        if (list.isNullOrEmpty()) return null
        return list.joinToString("|||")
    }
}
```

---

### 4.2 Room Database Singleton (`AppDatabase.kt`)

`AppDatabase` is the centralized SQLite database manager. It exposes abstract getter functions for all DAOs, utilizes the `@TypeConverters(Converters::class)` annotation, and provides a double-checked locking singleton accessor `getInstance(context)`.

#### Kotlin Implementation (`AppDatabase.kt`)
```kotlin
package com.jestk.browser.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jestk.browser.data.local.dao.FavoriteDao
import com.jestk.browser.data.local.dao.HistoryDao
import com.jestk.browser.data.local.dao.SettingDao
import com.jestk.browser.data.local.dao.TabDao
import com.jestk.browser.data.local.entity.FavoriteEntity
import com.jestk.browser.data.local.entity.HistoryEntity
import com.jestk.browser.data.local.entity.SettingEntity
import com.jestk.browser.data.local.entity.TabEntity

@Database(
    entities = [
        TabEntity::class,
        HistoryEntity::class,
        FavoriteEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tabDao(): TabDao
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun settingDao(): SettingDao

    companion object {
        private const val DATABASE_NAME = "jestk_browser.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
            }
        }

        fun createInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
            .allowMainThreadQueries()
            .build()
        }
    }
}
```

---

## 5. Unit Testing Strategy for Room Persistence

### 5.1 Test Harness & Execution Architecture
- **Execution Target:** JVM Unit Tests (`./gradlew testDebugUnitTest`)
- **Test Runner:** Robolectric (`@RunWith(RobolectricTestRunner::class)`) for full Android framework compatibility on JVM.
- **In-Memory Database:** Created via `Room.inMemoryDatabaseBuilder` with `.allowMainThreadQueries()`.
- **Coroutines & Asynchronous Testing:** `kotlinx.coroutines.test.runTest` for deterministic virtual-time execution.
- **Assertions:** Google Truth (`assertThat(...)`) for clear assertion failure messages.

---

### 5.2 `TabDaoTest.kt` Specification

Covers all CRUD operations, ordering invariants, and transaction atomicity for `TabDao`.

```kotlin
package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.TabDao
import com.jestk.browser.data.local.entity.TabEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class TabDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var tabDao: TabDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        tabDao = db.tabDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveTab_returnsInsertedTab() = runTest {
        val tab = TabEntity(
            id = "tab-1",
            url = "https://jestk.com",
            title = "Jestk",
            position = 0,
            isActive = true
        )
        tabDao.insertTab(tab)

        val retrieved = tabDao.getTabById("tab-1")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.id).isEqualTo("tab-1")
        assertThat(retrieved?.url).isEqualTo("https://jestk.com")
        assertThat(retrieved?.title).isEqualTo("Jestk")
        assertThat(retrieved?.isActive).isTrue()
    }

    @Test
    fun getAllTabsFlow_returnsTabsInPositionOrder() = runTest {
        val tab2 = TabEntity(id = "tab-2", position = 2, title = "Tab 2")
        val tab0 = TabEntity(id = "tab-0", position = 0, title = "Tab 0")
        val tab1 = TabEntity(id = "tab-1", position = 1, title = "Tab 1")

        tabDao.insertTabs(listOf(tab2, tab0, tab1))

        val tabs = tabDao.getAllTabsFlow().first()
        assertThat(tabs.map { it.id }).containsExactly("tab-0", "tab-1", "tab-2").inOrder()
    }

    @Test
    fun setActiveTab_clearsPreviousActiveFlagsAndMarksNewActive() = runTest {
        val tab1 = TabEntity(id = "tab-1", isActive = true)
        val tab2 = TabEntity(id = "tab-2", isActive = false)
        val tab3 = TabEntity(id = "tab-3", isActive = false)

        tabDao.insertTabs(listOf(tab1, tab2, tab3))

        // Switch active tab to tab-2
        tabDao.setActiveTab("tab-2")

        val activeTab = tabDao.getActiveTab()
        assertThat(activeTab?.id).isEqualTo("tab-2")

        val allTabs = tabDao.getAllTabs()
        val activeCount = allTabs.count { it.isActive }
        assertThat(activeCount).isEqualTo(1)
        assertThat(allTabs.find { it.id == "tab-1" }?.isActive).isFalse()
        assertThat(allTabs.find { it.id == "tab-2" }?.isActive).isTrue()
        assertThat(allTabs.find { it.id == "tab-3" }?.isActive).isFalse()
    }

    @Test
    fun reorderTabs_updatesPositionsSequentially() = runTest {
        val tabA = TabEntity(id = "tab-a", position = 0)
        val tabB = TabEntity(id = "tab-b", position = 1)
        val tabC = TabEntity(id = "tab-c", position = 2)

        tabDao.insertTabs(listOf(tabA, tabB, tabC))

        // Reorder to [C, A, B]
        tabDao.reorderTabs(listOf(tabC, tabA, tabB))

        val reordered = tabDao.getAllTabs()
        assertThat(reordered.map { it.id }).containsExactly("tab-c", "tab-a", "tab-b").inOrder()
        assertThat(reordered.find { it.id == "tab-c" }?.position).isEqualTo(0)
        assertThat(reordered.find { it.id == "tab-a" }?.position).isEqualTo(1)
        assertThat(reordered.find { it.id == "tab-b" }?.position).isEqualTo(2)
    }

    @Test
    fun getRecentTabsFlow_ordersByLastActiveTimestampDescending() = runTest {
        val tab1 = TabEntity(id = "tab-1", lastActiveTimestamp = 1000L)
        val tab2 = TabEntity(id = "tab-2", lastActiveTimestamp = 3000L)
        val tab3 = TabEntity(id = "tab-3", lastActiveTimestamp = 2000L)

        tabDao.insertTabs(listOf(tab1, tab2, tab3))

        val recent = tabDao.getRecentTabsFlow(limit = 2).first()
        assertThat(recent.map { it.id }).containsExactly("tab-2", "tab-3").inOrder()
    }

    @Test
    fun updateSnapshotPath_updatesTargetTabSnapshotOnly() = runTest {
        val tab = TabEntity(id = "tab-1", snapshotPath = null)
        tabDao.insertTab(tab)

        tabDao.updateSnapshotPath("tab-1", "/cache/snapshots/tab-1.jpg")

        val updated = tabDao.getTabById("tab-1")
        assertThat(updated?.snapshotPath).isEqualTo("/cache/snapshots/tab-1.jpg")
    }

    @Test
    fun deleteTabById_removesSpecificTab() = runTest {
        val tab1 = TabEntity(id = "tab-1")
        val tab2 = TabEntity(id = "tab-2")
        tabDao.insertTabs(listOf(tab1, tab2))

        tabDao.deleteTabById("tab-1")

        val remaining = tabDao.getAllTabs()
        assertThat(remaining.map { it.id }).containsExactly("tab-2")
    }
}
```

---

### 5.3 `HistoryDaoTest.kt` Specification

Covers visit recording, 5-minute aggregation logic, search autocomplete queries, and history clearing.

```kotlin
package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.HistoryDao
import com.jestk.browser.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class HistoryDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var historyDao: HistoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        historyDao = db.historyDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun recordVisit_initialVisit_createsNewHistoryRecord() = runTest {
        historyDao.recordVisit("https://kotlinlang.org", "Kotlin Programming")

        val history = historyDao.getAllHistoryFlow().first()
        assertThat(history).hasSize(1)
        assertThat(history[0].url).isEqualTo("https://kotlinlang.org")
        assertThat(history[0].title).isEqualTo("Kotlin Programming")
        assertThat(history[0].visitCount).isEqualTo(1)
    }

    @Test
    fun recordVisit_subsequentVisitWithin5Minutes_aggregatesVisitCount() = runTest {
        historyDao.recordVisit("https://kotlinlang.org", "Kotlin")
        historyDao.recordVisit("https://kotlinlang.org", "Kotlin Programming Language")

        val history = historyDao.getAllHistoryFlow().first()
        assertThat(history).hasSize(1)
        assertThat(history[0].url).isEqualTo("https://kotlinlang.org")
        assertThat(history[0].title).isEqualTo("Kotlin Programming Language")
        assertThat(history[0].visitCount).isEqualTo(2)
    }

    @Test
    fun searchHistoryFlow_matchesQueryInTitleOrUrl() = runTest {
        historyDao.insertHistory(
            HistoryEntity(url = "https://github.com/jetpack-compose", title = "Compose Repo", timestamp = 1000L)
        )
        historyDao.insertHistory(
            HistoryEntity(url = "https://developer.android.com", title = "Android Docs", timestamp = 2000L)
        )
        historyDao.insertHistory(
            HistoryEntity(url = "https://kotlinlang.org/docs", title = "Coroutines Guide", timestamp = 3000L)
        )

        val resultsByTitle = historyDao.searchHistoryFlow("Guide").first()
        assertThat(resultsByTitle.map { it.url }).containsExactly("https://kotlinlang.org/docs")

        val resultsByUrl = historyDao.searchHistoryFlow("github").first()
        assertThat(resultsByUrl.map { it.url }).containsExactly("https://github.com/jetpack-compose")
    }

    @Test
    fun clearAllHistory_deletesAllRecords() = runTest {
        historyDao.recordVisit("https://site1.com", "Site 1")
        historyDao.recordVisit("https://site2.com", "Site 2")

        historyDao.clearAllHistory()

        val history = historyDao.getAllHistoryFlow().first()
        assertThat(history).isEmpty()
    }
}
```

---

### 5.4 `FavoriteDaoTest.kt` Specification

Covers bookmark insertion, duplicate checking, limit capping (e.g. 6 items), reordering, and deletion.

```kotlin
package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.FavoriteDao
import com.jestk.browser.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class FavoriteDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var favoriteDao: FavoriteDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        favoriteDao = db.favoriteDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndQueryFavorites_maintainsPositionOrder() = runTest {
        val fav0 = FavoriteEntity(url = "https://jestk.com", title = "Jestk", position = 0)
        val fav1 = FavoriteEntity(url = "https://duckduckgo.com", title = "DuckDuckGo", position = 1)
        val fav2 = FavoriteEntity(url = "https://github.com", title = "GitHub", position = 2)

        favoriteDao.insertFavorite(fav1)
        favoriteDao.insertFavorite(fav0)
        favoriteDao.insertFavorite(fav2)

        val favorites = favoriteDao.getAllFavoritesFlow().first()
        assertThat(favorites.map { it.url }).containsExactly(
            "https://jestk.com",
            "https://duckduckgo.com",
            "https://github.com"
        ).inOrder()
    }

    @Test
    fun getFavoritesLimitedFlow_respectsLimit() = runTest {
        for (i in 0 until 10) {
            favoriteDao.insertFavorite(
                FavoriteEntity(url = "https://site$i.com", title = "Site $i", position = i)
            )
        }

        val limited = favoriteDao.getFavoritesLimitedFlow(limit = 6).first()
        assertThat(limited).hasSize(6)
        assertThat(limited.map { it.position }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
    }

    @Test
    fun isFavorite_returnsCorrectStatus() = runTest {
        favoriteDao.insertFavorite(FavoriteEntity(url = "https://jestk.com", title = "Jestk"))

        assertThat(favoriteDao.isFavorite("https://jestk.com")).isTrue()
        assertThat(favoriteDao.isFavorite("https://unknown.com")).isFalse()
    }

    @Test
    fun deleteFavoriteById_removesItem() = runTest {
        val id = favoriteDao.insertFavorite(FavoriteEntity(url = "https://jestk.com", title = "Jestk"))
        assertThat(favoriteDao.getFavoritesCount()).isEqualTo(1)

        favoriteDao.deleteFavoriteById(id)
        assertThat(favoriteDao.getFavoritesCount()).isEqualTo(0)
    }
}
```

---

### 5.5 `SettingDaoTest.kt` Specification

Covers setting upsert, retrieval, deletion, and reactive flow updates.

```kotlin
package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.SettingDao
import com.jestk.browser.data.local.entity.SettingEntity
import com.jestk.browser.data.local.entity.SettingKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class SettingDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var settingDao: SettingDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        settingDao = db.settingDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun setAndGetSetting_persistsValue() = runTest {
        settingDao.setSetting(SettingEntity(key = SettingKeys.THEME_MODE, value = "DARK"))

        val value = settingDao.getSetting(SettingKeys.THEME_MODE)
        assertThat(value).isEqualTo("DARK")
    }

    @Test
    fun setSetting_overwritesExistingKey() = runTest {
        settingDao.setSetting(SettingEntity(key = SettingKeys.SEARCH_ENGINE, value = "DUCKDUCKGO"))
        settingDao.setSetting(SettingEntity(key = SettingKeys.SEARCH_ENGINE, value = "GOOGLE"))

        val value = settingDao.getSetting(SettingKeys.SEARCH_ENGINE)
        assertThat(value).isEqualTo("GOOGLE")
    }

    @Test
    fun getSettingFlow_emitsUpdates() = runTest {
        settingDao.setSetting(SettingEntity(key = SettingKeys.AD_BLOCKER_ENABLED, value = "true"))
        val value1 = settingDao.getSettingFlow(SettingKeys.AD_BLOCKER_ENABLED).first()
        assertThat(value1).isEqualTo("true")

        settingDao.setSetting(SettingEntity(key = SettingKeys.AD_BLOCKER_ENABLED, value = "false"))
        val value2 = settingDao.getSettingFlow(SettingKeys.AD_BLOCKER_ENABLED).first()
        assertThat(value2).isEqualTo("false")
    }

    @Test
    fun deleteSetting_removesKey() = runTest {
        settingDao.setSetting(SettingEntity(key = SettingKeys.THEME_MODE, value = "LIGHT"))
        settingDao.deleteSetting(SettingKeys.THEME_MODE)

        val value = settingDao.getSetting(SettingKeys.THEME_MODE)
        assertThat(value).isNull()
    }
}
```

---

### 5.6 `ConvertersTest.kt` Specification

Verifies bidirectional serialization fidelity for `Date`, `Uri`, and `List<String>`.

```kotlin
package com.jestk.browser.data.local

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun dateConverters_bidirectionalFidelity() {
        val now = Date()
        val timestamp = converters.dateToTimestamp(now)
        val reconstructed = converters.fromTimestamp(timestamp)

        assertThat(timestamp).isEqualTo(now.time)
        assertThat(reconstructed).isEqualTo(now)
        assertThat(converters.dateToTimestamp(null)).isNull()
        assertThat(converters.fromTimestamp(null)).isNull()
    }

    @Test
    fun uriConverters_bidirectionalFidelity() {
        val uri = Uri.parse("https://jestk.com/search?q=android")
        val string = converters.uriToString(uri)
        val reconstructed = converters.fromUriString(string)

        assertThat(string).isEqualTo("https://jestk.com/search?q=android")
        assertThat(reconstructed).isEqualTo(uri)
        assertThat(converters.uriToString(null)).isNull()
        assertThat(converters.fromUriString(null)).isNull()
    }

    @Test
    fun stringListConverters_bidirectionalFidelity() {
        val list = listOf("alpha", "beta", "gamma")
        val serialized = converters.stringListToString(list)
        val deserialized = converters.fromStringList(serialized)

        assertThat(serialized).isEqualTo("alpha|||beta|||gamma")
        assertThat(deserialized).containsExactly("alpha", "beta", "gamma").inOrder()
        assertThat(converters.stringListToString(null)).isNull()
        assertThat(converters.fromStringList(null)).isEmpty()
    }
}
```

---

## 6. Synthesis with Peer Explorers & Downstream Integration

- **Integration with Explorer 1 (Build, Resources & DI):**
  - Room compiler dependency `androidx.room:room-compiler:2.6.1` is declared with `ksp(...)` in `app/build.gradle.kts`.
  - `AppContainer` initializes `AppDatabase.getInstance(context)` and provides `tabDao`, `historyDao`, `favoriteDao`, and `settingDao` instances to Repositories.
- **Integration with Explorer 3 (Domain Repositories & ViewModel):**
  - `TabRepositoryImpl` consumes `TabDao` and maps `TabEntity <-> Tab`.
  - `HistoryRepositoryImpl` consumes `HistoryDao` and maps `HistoryEntity <-> HistoryItem`.
  - `FavoriteRepositoryImpl` consumes `FavoriteDao` and maps `FavoriteEntity <-> FavoriteItem`.
  - `SettingsRepositoryImpl` consumes `SettingDao` and maps `SettingEntity <-> BrowserSettings`.
- **Integration with Milestone 2 (WebView Pool & Snapshot Engine):**
  - `tabDao.updateSnapshotPath(tabId, filePath)` connects visual frame rendering to disk cache and database.
- **Integration with E2E Testing Track:**
  - In-memory database initialization supports both unit testing and full-tier E2E mock workflows.
