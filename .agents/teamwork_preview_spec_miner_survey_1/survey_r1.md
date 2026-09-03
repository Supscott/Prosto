# Jestk Browser for Android — R1 Technical Specification & Architectural Blueprint

**Focus Area:** R1. Project Scaffolding, Architecture & Room Database Persistence  
**Authoritative Sources:** `/Users/ilya/CODING/browser/ORIGINAL_REQUEST.md`, `/Users/ilya/CODING/browser/spec.md`  
**Target Platform:** Android (Min SDK 26, Target SDK 35, JVM 17, Kotlin 2.0+, Jetpack Compose BOM)

---

## 1. Executive Architectural Summary

Jestk Browser is a modern, high-performance Android web browser built with Kotlin, Jetpack Compose, Material 3, Android System WebView, and Room Database. The architecture strictly adheres to Clean Architecture and MVVM (Model-View-ViewModel) with unidirectional data flow (`StateFlow`).

The foundational layer (**R1**) establishes:
- Android 15 (SDK 35) Gradle build infrastructure with Kotlin 2.0+ and Compose Compiler Gradle plugin.
- An offline-first, crash-resilient Room persistence layer managing tab states, browsing history, user favorites, and configuration preferences.
- Repository pattern isolating SQLite / Room operations behind reactive `Flow` interfaces.
- Manual Dependency Injection container (`AppContainer`) ensuring clean lifecycle management without reflection or heavyweight DI frameworks.
- Deterministic cold-boot session restoration logic restoring active tabs, tab strip order, and disk-cached thumbnail snapshots.

---

## 2. Android Gradle Project Scaffolding

### 2.1 File Tree Layout
```
/Users/ilya/CODING/browser/
├── build.gradle.kts                      # Root build configuration & plugin declarations
├── settings.gradle.kts                   # Plugin repositories, dependency resolution & project inclusion
├── gradle.properties                     # JVM args, AndroidX flags, Kotlin style
├── gradlew                               # Unix Gradle wrapper shell script
├── gradlew.bat                           # Windows Gradle wrapper batch script
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties     # Gradle 8.10.2 binary distribution URL
└── app/
    ├── build.gradle.kts                  # App module plugins, SDKs, dependencies, KSP, Compose options
    ├── proguard-rules.pro                # Proguard / R8 keep rules
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml       # App declarations, permissions (INTERNET, PiP, etc.)
        │   ├── assets/
        │   │   └── adblock/              # Pre-bundled ad/tracker blocklist rules
        │   ├── java/com/jestk/browser/
        │   │   ├── JestkApplication.kt   # Application class initializing AppContainer
        │   │   ├── di/
        │   │   │   └── AppContainer.kt   # Manual DI Container & ViewModel Factory
        │   │   ├── data/
        │   │   │   ├── local/
        │   │   │   │   ├── AppDatabase.kt
        │   │   │   │   ├── Converters.kt
        │   │   │   │   ├── dao/
        │   │   │   │   │   ├── TabDao.kt
        │   │   │   │   │   ├── HistoryDao.kt
        │   │   │   │   │   ├── FavoriteDao.kt
        │   │   │   │   │   └── SettingDao.kt
        │   │   │   │   └── entity/
        │   │   │   │       ├── TabEntity.kt
        │   │   │   │       ├── HistoryEntity.kt
        │   │   │   │       ├── FavoriteEntity.kt
        │   │   │   │       └── SettingEntity.kt
        │   │   │   └── repository/
        │   │   │       ├── TabRepositoryImpl.kt
        │   │   │       ├── HistoryRepositoryImpl.kt
        │   │   │       ├── FavoriteRepositoryImpl.kt
        │   │   │       └── SettingsRepositoryImpl.kt
        │   │   ├── domain/
        │   │   │   ├── model/
        │   │   │   │   ├── Tab.kt
        │   │   │   │   ├── HistoryItem.kt
        │   │   │   │   ├── FavoriteItem.kt
        │   │   │   │   ├── ThemeMode.kt
        │   │   │   │   ├── SearchEngine.kt
        │   │   │   │   └── BrowserSettings.kt
        │   │   │   └── repository/
        │   │   │       ├── TabRepository.kt
        │   │   │       ├── HistoryRepository.kt
        │   │   │       ├── FavoriteRepository.kt
        │   │   │       └── SettingsRepository.kt
        │   │   ├── ui/
        │   │   │   ├── MainActivity.kt
        │   │   │   ├── theme/
        │   │   │   │   ├── Color.kt
        │   │   │   │   ├── Theme.kt
        │   │   │   │   └── Type.kt
        │   │   │   └── viewmodel/
        │   │   │       ├── BrowserViewModel.kt
        │   │   │       ├── BrowserUiState.kt
        │   │   │       └── BrowserUiEvent.kt
        │   │   └── util/
        │   │       └── SnapshotManager.kt
        │   └── res/
        │       ├── drawable/
        │       ├── mipmap-*/
        │       └── values/
        │           ├── strings.xml
        │           └── themes.xml
        ├── test/                         # JVM Unit Tests (Robolectric, Room, Repositories, ViewModels)
        │   └── java/com/jestk/browser/
        │       ├── data/local/
        │       │   ├── TabDaoTest.kt
        │       │   ├── HistoryDaoTest.kt
        │       │   └── FavoriteDaoTest.kt
        │       ├── data/repository/
        │       │   └── TabRepositoryTest.kt
        │       └── ui/viewmodel/
        │           └── BrowserViewModelTest.kt
        └── androidTest/                  # On-Device / Instrumentation Tests
            └── java/com/jestk/browser/
                └── AppDatabaseMigrationTest.kt
```

### 2.2 Complete Build Configuration Specifications

#### 2.2.1 `settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "jestk-browser"
include(":app")
```

#### 2.2.2 `build.gradle.kts` (Root Project)
```kotlin
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
```

#### 2.2.3 `gradle.properties`
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

#### 2.2.4 `gradle/wrapper/gradle-wrapper.properties`
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

#### 2.2.5 `app/build.gradle.kts`
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.jestk.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jestk.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi"
        )
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // AndroidX Core & Lifecycle
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Jetpack Compose BOM & UI
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material:material") // For PullRefreshIndicator & pullRefresh

    // Android WebKit & System WebView
    implementation("androidx.webkit:webkit:1.12.1")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Image Loading (Favicons, Snapshot Thumbnails)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Debug Tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Unit Testing (JVM)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("com.google.truth:truth:1.4.4")

    // Instrumented Testing
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

---

## 3. Room Database Specification & Entity Schemas

The local database is named `jestk_browser.db` and is managed by Room with `exportSchema = false` (or pre-configured schema directory).

### 3.1 `TabEntity` (Table: `tabs`)
Stores persistent state for all open browser tabs.

| Field Name | Kotlin Type | SQLite Column | Constraint / Default | Purpose / Description |
|---|---|---|---|---|
| `id` | `String` | `TEXT` | `PRIMARY KEY` | UUID string (e.g. `UUID.randomUUID().toString()`) matching `WebViewPool` instance key. |
| `url` | `String` | `TEXT` | `NOT NULL`, default `""` | Current web page URL. Empty string or `about:blank` represents Home view. |
| `title` | `String` | `TEXT` | `NOT NULL`, default `"New Tab"` | Page title extracted from WebView, truncated/displayed in UI. |
| `snapshotPath` | `String?` | `TEXT` | `NULLABLE`, default `null` | Absolute or relative file path in app cache to the rendered PNG/WEBP thumbnail. |
| `faviconUrl` | `String?` | `TEXT` | `NULLABLE`, default `null` | Extracted site favicon URL or local cached icon path. |
| `lastActiveTimestamp` | `Long` | `INTEGER` | `NOT NULL`, default `System.currentTimeMillis()` | Timestamp of last user focus, used for Recent Tabs carousel ordering. |
| `position` | `Int` | `INTEGER` | `NOT NULL`, default `0` | Visual display index in horizontal Tab Strip and Tab Overview grid. |
| `isIncognito` | `Boolean` | `INTEGER` | `NOT NULL`, default `false` (0) | If true, history and persistent cache are bypassed. |
| `isActive` | `Boolean` | `INTEGER` | `NOT NULL`, default `false` (0) | Flags the currently active session tab for instant session restore. |
| `canGoBack` | `Boolean` | `INTEGER` | `NOT NULL`, default `false` (0) | Cached backward navigation availability. |
| `canGoForward` | `Boolean` | `INTEGER` | `NOT NULL`, default `false` (0) | Cached forward navigation availability. |

**Indices:**
- `Index(value = ["position"])`
- `Index(value = ["lastActiveTimestamp"])`

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

### 3.2 `HistoryEntity` (Table: `history`)
Stores browsing history entries with search indexing and visit aggregation.

| Field Name | Kotlin Type | SQLite Column | Constraint / Default | Purpose / Description |
|---|---|---|---|---|
| `id` | `Long` | `INTEGER` | `PRIMARY KEY AUTOINCREMENT` | Unique history record identifier. |
| `url` | `String` | `TEXT` | `NOT NULL` | Visited webpage URL. |
| `title` | `String` | `TEXT` | `NOT NULL` | Webpage title for suggestion rendering. |
| `timestamp` | `Long` | `INTEGER` | `NOT NULL` | Epoch millisecond of visit timestamp. |
| `visitCount` | `Int` | `INTEGER` | `NOT NULL`, default `1` | Frequency counter for ranking suggestions. |
| `faviconUrl` | `String?` | `TEXT` | `NULLABLE`, default `null` | Site icon URL. |

**Indices:**
- `Index(value = ["timestamp"])`
- `Index(value = ["url"])`

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

### 3.3 `FavoriteEntity` (Table: `favorites`)
Stores user bookmarks displayed in the Home Screen 2D grid.

| Field Name | Kotlin Type | SQLite Column | Constraint / Default | Purpose / Description |
|---|---|---|---|---|
| `id` | `Long` | `INTEGER` | `PRIMARY KEY AUTOINCREMENT` | Unique bookmark identifier. |
| `url` | `String` | `TEXT` | `NOT NULL` | Destination bookmark URL. |
| `title` | `String` | `TEXT` | `NOT NULL` | Display label in Home Screen grid. |
| `position` | `Int` | `INTEGER` | `NOT NULL`, default `0` | Manual sort position in favorites grid. |
| `iconUrl` | `String?` | `TEXT` | `NULLABLE`, default `null` | Favicon or custom icon representation. |
| `createdAt` | `Long` | `INTEGER` | `NOT NULL` | Creation timestamp. |

**Indices:**
- `Index(value = ["position"])`

```kotlin
package com.jestk.browser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["position"])
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

### 3.4 `SettingEntity` (Table: `settings`)
Key-value configuration store for browser settings.

| Field Name | Kotlin Type | SQLite Column | Constraint / Default | Purpose / Description |
|---|---|---|---|---|
| `key` | `String` | `TEXT` | `PRIMARY KEY` | Configuration key name (e.g. `theme_mode`, `search_engine`, `ad_blocker_enabled`). |
| `value` | `String` | `TEXT` | `NOT NULL` | Stored preference value. |

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

**Predefined Setting Keys & Default Values:**
- `SettingKeys.THEME_MODE`: `"SYSTEM"` (`"SYSTEM"`, `"LIGHT"`, `"DARK"`)
- `SettingKeys.SEARCH_ENGINE`: `"DUCKDUCKGO"` (`"DUCKDUCKGO"`, `"GOOGLE"`, `"BING"`)
- `SettingKeys.AD_BLOCKER_ENABLED`: `"true"` (`"true"`, `"false"`)
- `SettingKeys.ACTIVE_TAB_ID`: `""` (Stores ID of active tab)

---

## 4. DAOs & Database Class Interfaces

### 4.1 `TabDao`
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

    @Query("SELECT * FROM tabs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTab(): TabEntity?

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
    suspend fun updateTabDetails(id: String, url: String, title: String, canGoBack: Boolean, canGoForward: Boolean)

    @Transaction
    suspend fun setActiveTab(activeId: String) {
        clearActiveFlags()
        markActive(activeId, System.currentTimeMillis())
    }

    @Query("UPDATE tabs SET isActive = 0")
    suspend fun clearActiveFlags()

    @Query("UPDATE tabs SET isActive = 1, lastActiveTimestamp = :timestamp WHERE id = :id")
    suspend fun markActive(id: String, timestamp: Long)

    @Transaction
    suspend fun reorderTabs(orderedTabs: List<TabEntity>) {
        orderedTabs.forEachIndexed { index, tab ->
            updatePosition(tab.id, index)
        }
    }

    @Query("UPDATE tabs SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)
}
```

---

### 4.2 `HistoryDao`
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

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 15")
    fun searchHistoryFlow(query: String): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryEntity): Long

    @Query("SELECT * FROM history WHERE url = :url ORDER BY timestamp DESC LIMIT 1")
    suspend fun getRecentByUrl(url: String): HistoryEntity?

    @Query("UPDATE history SET timestamp = :timestamp, visitCount = visitCount + 1, title = :title WHERE id = :id")
    suspend fun updateVisit(id: Long, title: String, timestamp: Long)

    @Transaction
    suspend fun recordVisit(url: String, title: String) {
        val existing = getRecentByUrl(url)
        val now = System.currentTimeMillis()
        if (existing != null && (now - existing.timestamp) < 300_000) { // 5-minute aggregation
            updateVisit(existing.id, title.ifEmpty { existing.title }, now)
        } else {
            insertHistory(
                HistoryEntity(
                    url = url,
                    title = title.ifEmpty { url },
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

    @Query("DELETE FROM history")
    suspend fun clearAllHistory()
}
```

---

### 4.3 `FavoriteDao`
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

    @Query("SELECT * FROM favorites ORDER BY position ASC, createdAt ASC LIMIT :limit")
    fun getFavoritesLimitedFlow(limit: Int = 6): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE url = :url LIMIT 1")
    suspend fun getFavoriteByUrl(url: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity): Long

    @Update
    suspend fun updateFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteFavoriteById(id: Long)

    @Query("DELETE FROM favorites")
    suspend fun clearAllFavorites()

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun getFavoritesCount(): Int

    @Transaction
    suspend fun reorderFavorites(orderedFavorites: List<FavoriteEntity>) {
        orderedFavorites.forEachIndexed { index, fav ->
            updatePosition(fav.id, index)
        }
    }

    @Query("UPDATE favorites SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)
}
```

---

### 4.4 `SettingDao`
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: SettingEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)
}
```

---

### 4.5 `AppDatabase` Class & Singleton Provider
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
    }
}
```

---

## 5. Domain Models & Repository Specifications

### 5.1 Domain Models
Clean separation of persistence entities from domain representations:

```kotlin
package com.jestk.browser.domain.model

data class Tab(
    val id: String,
    val url: String,
    val title: String,
    val snapshotPath: String? = null,
    val faviconUrl: String? = null,
    val lastActiveTimestamp: Long = System.currentTimeMillis(),
    val position: Int = 0,
    val isIncognito: Boolean = false,
    val isActive: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

data class HistoryItem(
    val id: Long,
    val url: String,
    val title: String,
    val timestamp: Long,
    val visitCount: Int,
    val faviconUrl: String? = null
)

data class FavoriteItem(
    val id: Long,
    val url: String,
    val title: String,
    val position: Int,
    val iconUrl: String? = null,
    val createdAt: Long
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class SearchEngine(val displayName: String, val searchUrlTemplate: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s");

    fun buildSearchUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return String.format(searchUrlTemplate, encoded)
    }
}

data class BrowserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val adBlockerEnabled: Boolean = true
)
```

---

### 5.2 Repository Interfaces & Contracts

#### 5.2.1 `TabRepository`
```kotlin
package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.Tab
import kotlinx.coroutines.flow.Flow

interface TabRepository {
    val allTabs: Flow<List<Tab>>
    val recentTabs: Flow<List<Tab>>

    suspend fun getInitialTabs(): List<Tab>
    suspend fun getTabById(id: String): Tab?
    suspend fun createTab(url: String = "", title: String = "New Tab", isIncognito: Boolean = false): Tab
    suspend fun updateTab(tab: Tab)
    suspend fun updateTabDetails(id: String, url: String, title: String, canGoBack: Boolean, canGoForward: Boolean)
    suspend fun updateSnapshot(id: String, snapshotPath: String)
    suspend fun setActiveTab(id: String)
    suspend fun closeTab(id: String)
    suspend fun closeAllTabs()
    suspend fun reorderTabs(orderedTabs: List<Tab>)
}
```

#### 5.2.2 `HistoryRepository`
```kotlin
package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.HistoryItem
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    val allHistory: Flow<List<HistoryItem>>
    fun searchHistory(query: String): Flow<List<HistoryItem>>

    suspend fun recordVisit(url: String, title: String)
    suspend fun deleteHistoryItem(id: Long)
    suspend fun clearHistory()
}
```

#### 5.2.3 `FavoriteRepository`
```kotlin
package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.FavoriteItem
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    val allFavorites: Flow<List<FavoriteItem>>
    val topFavorites: Flow<List<FavoriteItem>> // Limit 6

    suspend fun addFavorite(title: String, url: String, iconUrl: String? = null): Long
    suspend fun removeFavorite(id: Long)
    suspend fun isFavorite(url: String): Boolean
    suspend fun reorderFavorites(orderedFavorites: List<FavoriteItem>)
}
```

#### 5.2.4 `SettingsRepository`
```kotlin
package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settingsFlow: Flow<BrowserSettings>

    suspend fun getSettings(): BrowserSettings
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setSearchEngine(engine: SearchEngine)
    suspend fun setAdBlockerEnabled(enabled: Boolean)
}
```

---

## 6. MVVM Architecture & Unidirectional Data Flow

### 6.1 UI State Machine (`BrowserUiState`)
```kotlin
package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.HistoryItem
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode

sealed interface OverlayState {
    data object None : OverlayState
    data object HomeScreen : OverlayState
    data class Search(val prefilledQuery: String = "") : OverlayState
    data object TabGrid : OverlayState
    data object Settings : OverlayState
}

data class BrowserUiState(
    // Tabs & Sessions
    val tabs: List<Tab> = emptyList(),
    val recentTabs: List<Tab> = emptyList(),
    val activeTabId: String? = null,

    // Active Viewport / Overlay
    val activeOverlay: OverlayState = OverlayState.None,

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
    val bottomBarVisible: Boolean = true,
    val bottomBarOffsetY: Float = 0f,

    // Media & Fullscreen
    val isVideoPlaying: Boolean = false,
    val isFullscreenCustomView: Boolean = false
) {
    val activeTab: Tab?
        get() = tabs.find { it.id == activeTabId }

    val activeTabIndex: Int
        get() = tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
}
```

---

### 6.2 UI Events & Intents (`BrowserUiEvent`)
```kotlin
package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.HistoryItem
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
    data class OpenOverlay(val overlay: OverlayState) : BrowserUiEvent
    data object DismissOverlay : BrowserUiEvent
    data object OpenHome : BrowserUiEvent
    data object OpenSearch : BrowserUiEvent
    data object OpenTabGrid : BrowserUiEvent
    data object OpenSettings : BrowserUiEvent

    // Web Navigation
    data class LoadUrl(val url: String) : BrowserUiEvent
    data class SubmitSearch(val query: String) : BrowserUiEvent
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

    // System Back Handling
    data object HandleBackPress : BrowserUiEvent
}
```

---

### 6.3 `BrowserViewModel` Implementation Blueprint
```kotlin
package com.jestk.browser.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowserViewModel(
    private val tabRepository: TabRepository,
    private val historyRepository: HistoryRepository,
    private val favoriteRepository: FavoriteRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    init {
        restoreSession()
        observeDatabaseFlows()
    }

    private fun restoreSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val initialTabs = tabRepository.getInitialTabs()
            val initialSettings = settingsRepository.getSettings()

            val activeTab = if (initialTabs.isEmpty()) {
                val newTab = tabRepository.createTab(url = "", title = "Home")
                tabRepository.setActiveTab(newTab.id)
                newTab
            } else {
                initialTabs.find { it.isActive } ?: initialTabs.first().also {
                    tabRepository.setActiveTab(it.id)
                }
            }

            _uiState.update { state ->
                state.copy(
                    tabs = initialTabs.ifEmpty { listOf(activeTab) },
                    activeTabId = activeTab.id,
                    currentUrl = activeTab.url,
                    pageTitle = activeTab.title,
                    canGoBack = activeTab.canGoBack,
                    canGoForward = activeTab.canGoForward,
                    settings = initialSettings,
                    activeOverlay = if (activeTab.url.isEmpty()) OverlayState.HomeScreen else OverlayState.None
                )
            }
        }
    }

    private fun observeDatabaseFlows() {
        viewModelScope.launch {
            tabRepository.allTabs.collect { tabs ->
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
            tabRepository.recentTabs.collect { recents ->
                _uiState.update { it.copy(recentTabs = recents) }
            }
        }

        viewModelScope.launch {
            favoriteRepository.allFavorites.collect { favorites ->
                _uiState.update { it.copy(favorites = favorites) }
            }
        }

        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
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
            is BrowserUiEvent.DismissOverlay -> _uiState.update { it.copy(activeOverlay = OverlayState.None) }
            is BrowserUiEvent.OpenHome -> _uiState.update { it.copy(activeOverlay = OverlayState.HomeScreen) }
            is BrowserUiEvent.OpenSearch -> {
                val current = _uiState.value.currentUrl
                _uiState.update { it.copy(activeOverlay = OverlayState.Search(current), searchQuery = current) }
            }
            is BrowserUiEvent.OpenTabGrid -> _uiState.update { it.copy(activeOverlay = OverlayState.TabGrid) }
            is BrowserUiEvent.OpenSettings -> _uiState.update { it.copy(activeOverlay = OverlayState.Settings) }

            is BrowserUiEvent.LoadUrl -> loadUrl(event.url)
            is BrowserUiEvent.SubmitSearch -> submitSearch(event.query)
            is BrowserUiEvent.GoBack -> handleBackNavigation()
            is BrowserUiEvent.GoForward -> { /* delegate to webview */ }
            is BrowserUiEvent.Reload -> { /* delegate to webview */ }
            is BrowserUiEvent.StopLoading -> _uiState.update { it.copy(isLoading = false) }

            is BrowserUiEvent.OnPageStarted -> handlePageStarted(event.tabId, event.url)
            is BrowserUiEvent.OnPageFinished -> handlePageFinished(event.tabId, event.url, event.title)
            is BrowserUiEvent.OnProgressChanged -> handleProgressChanged(event.tabId, event.progress)
            is BrowserUiEvent.OnNavigationStateChanged -> handleNavState(event.tabId, event.canGoBack, event.canGoForward)
            is BrowserUiEvent.OnSnapshotCaptured -> handleSnapshot(event.tabId, event.snapshotPath)

            is BrowserUiEvent.AddCurrentTabToFavorites -> addCurrentToFavorites()
            is BrowserUiEvent.RemoveFavorite -> viewModelScope.launch(Dispatchers.IO) { favoriteRepository.removeFavorite(event.id) }
            is BrowserUiEvent.ToggleFavoritesExpanded -> _uiState.update { it.copy(isFavoritesExpanded = !it.isFavoritesExpanded) }
            is BrowserUiEvent.DeleteHistoryItem -> viewModelScope.launch(Dispatchers.IO) { historyRepository.deleteHistoryItem(event.id) }
            is BrowserUiEvent.ClearAllHistory -> viewModelScope.launch(Dispatchers.IO) { historyRepository.clearHistory() }
            is BrowserUiEvent.SelectHistorySuggestion -> loadUrl(event.item.url)

            is BrowserUiEvent.SetThemeMode -> viewModelScope.launch(Dispatchers.IO) { settingsRepository.setThemeMode(event.mode) }
            is BrowserUiEvent.SetSearchEngine -> viewModelScope.launch(Dispatchers.IO) { settingsRepository.setSearchEngine(event.engine) }
            is BrowserUiEvent.SetAdBlockerEnabled -> viewModelScope.launch(Dispatchers.IO) { settingsRepository.setAdBlockerEnabled(event.enabled) }

            is BrowserUiEvent.OnWebScroll -> handleWebScroll(event.deltaY)
            is BrowserUiEvent.HandleBackPress -> handleBackNavigation()
        }
    }

    private fun openNewTab(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val created = tabRepository.createTab(url = url, title = if (url.isEmpty()) "Home" else "Loading...")
            tabRepository.setActiveTab(created.id)
            _uiState.update {
                it.copy(
                    activeTabId = created.id,
                    currentUrl = url,
                    pageTitle = created.title,
                    activeOverlay = if (url.isEmpty()) OverlayState.HomeScreen else OverlayState.None
                )
            }
        }
    }

    private fun selectTab(tabId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            tabRepository.setActiveTab(tabId)
            val selected = tabRepository.getTabById(tabId)
            _uiState.update {
                it.copy(
                    activeTabId = tabId,
                    currentUrl = selected?.url.orEmpty(),
                    pageTitle = selected?.title.orEmpty(),
                    canGoBack = selected?.canGoBack ?: false,
                    canGoForward = selected?.canGoForward ?: false,
                    activeOverlay = if (selected?.url.isNullOrEmpty()) OverlayState.HomeScreen else OverlayState.None
                )
            }
        }
    }

    private fun closeTab(tabId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            tabRepository.closeTab(tabId)
            val currentTabs = tabRepository.getInitialTabs()
            if (currentTabs.isEmpty()) {
                val newTab = tabRepository.createTab(url = "", title = "Home")
                tabRepository.setActiveTab(newTab.id)
                _uiState.update {
                    it.copy(
                        activeTabId = newTab.id,
                        currentUrl = "",
                        pageTitle = "Home",
                        activeOverlay = OverlayState.HomeScreen
                    )
                }
            } else if (_uiState.value.activeTabId == tabId) {
                val nextActive = currentTabs.first()
                tabRepository.setActiveTab(nextActive.id)
                _uiState.update {
                    it.copy(
                        activeTabId = nextActive.id,
                        currentUrl = nextActive.url,
                        pageTitle = nextActive.title,
                        activeOverlay = if (nextActive.url.isEmpty()) OverlayState.HomeScreen else OverlayState.None
                    )
                }
            }
        }
    }

    private fun closeAllTabs() {
        viewModelScope.launch(Dispatchers.IO) {
            tabRepository.closeAllTabs()
            val newTab = tabRepository.createTab(url = "", title = "Home")
            tabRepository.setActiveTab(newTab.id)
            _uiState.update {
                it.copy(
                    activeTabId = newTab.id,
                    currentUrl = "",
                    pageTitle = "Home",
                    activeOverlay = OverlayState.HomeScreen
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
        viewModelScope.launch(Dispatchers.IO) {
            tabRepository.reorderTabs(orderedTabs)
        }
    }

    private fun loadUrl(rawUrl: String) {
        val finalUrl = when {
            rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
            rawUrl.contains(".") && !rawUrl.contains(" ") -> "https://$rawUrl"
            else -> _uiState.value.settings.searchEngine.buildSearchUrl(rawUrl)
        }

        val activeId = _uiState.value.activeTabId ?: return
        _uiState.update {
            it.copy(
                currentUrl = finalUrl,
                isLoading = true,
                loadingProgress = 0.1f,
                activeOverlay = OverlayState.None
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            tabRepository.updateTabDetails(activeId, finalUrl, finalUrl, canGoBack = false, canGoForward = false)
        }
    }

    private fun submitSearch(query: String) {
        loadUrl(query)
    }

    private fun handlePageStarted(tabId: String, url: String) {
        if (_uiState.value.activeTabId == tabId) {
            _uiState.update { it.copy(currentUrl = url, isLoading = true, loadingProgress = 0.15f) }
        }
    }

    private fun handlePageFinished(tabId: String, url: String, title: String) {
        if (_uiState.value.activeTabId == tabId) {
            _uiState.update {
                it.copy(
                    currentUrl = url,
                    pageTitle = title,
                    isLoading = false,
                    loadingProgress = 1.0f
                )
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            tabRepository.updateTabDetails(tabId, url, title, canGoBack = true, canGoForward = false)
            if (url.isNotEmpty() && !url.startsWith("about:")) {
                historyRepository.recordVisit(url, title)
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
        viewModelScope.launch(Dispatchers.IO) {
            tabRepository.updateSnapshot(tabId, path)
        }
    }

    private fun addCurrentToFavorites() {
        val current = _uiState.value
        if (current.currentUrl.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                favoriteRepository.addFavorite(
                    title = current.pageTitle.ifEmpty { current.currentUrl },
                    url = current.currentUrl
                )
            }
        }
    }

    private fun handleWebScroll(deltaY: Float) {
        // Dynamic auto-hide: scroll down (deltaY > 0) hides bar; scroll up (deltaY < 0) reveals bar
        if (deltaY > 10f && _uiState.value.bottomBarVisible) {
            _uiState.update { it.copy(bottomBarVisible = false) }
        } else if (deltaY < -10f && !_uiState.value.bottomBarVisible) {
            _uiState.update { it.copy(bottomBarVisible = true) }
        }
    }

    private fun handleBackNavigation() {
        val current = _uiState.value
        when {
            current.activeOverlay != OverlayState.None -> {
                _uiState.update { it.copy(activeOverlay = OverlayState.None) }
            }
            current.canGoBack -> {
                // Trigger webView.goBack() via action channel or delegated event
            }
            else -> {
                current.activeTabId?.let { closeTab(it) }
            }
        }
    }
}
```

---

## 7. Dependency Injection & Application Architecture

### 7.1 `AppContainer` Implementation
Manual dependency injection ensures deterministic startup performance, zero reflective overhead, and complete testability without Dagger/Hilt complexity.

```kotlin
package com.jestk.browser.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.SettingsRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import com.jestk.browser.ui.viewmodel.BrowserViewModel

interface AppContainer {
    val database: AppDatabase
    val tabRepository: TabRepository
    val historyRepository: HistoryRepository
    val favoriteRepository: FavoriteRepository
    val settingsRepository: SettingsRepository
    val viewModelFactory: ViewModelProvider.Factory
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    override val tabRepository: TabRepository by lazy {
        TabRepositoryImpl(database.tabDao())
    }

    override val historyRepository: HistoryRepository by lazy {
        HistoryRepositoryImpl(database.historyDao())
    }

    override val favoriteRepository: FavoriteRepository by lazy {
        FavoriteRepositoryImpl(database.favoriteDao())
    }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(database.settingDao())
    }

    override val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            BrowserViewModel(
                tabRepository = tabRepository,
                historyRepository = historyRepository,
                favoriteRepository = favoriteRepository,
                settingsRepository = settingsRepository
            )
        }
    }
}
```

### 7.2 `JestkApplication` Class
```kotlin
package com.jestk.browser

import android.app.Application
import com.jestk.browser.di.AppContainer
import com.jestk.browser.di.DefaultAppContainer

class JestkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(applicationContext)
    }
}
```

---

## 8. Cold Boot Session Restoration Logic

### 8.1 Step-by-Step Restoration Protocol

1. **Database & Disk Initialization**:
   - `JestkApplication.onCreate()` constructs `DefaultAppContainer`.
   - `AppDatabase` singleton is lazily created on background dispatcher.
   - `SnapshotManager` ensures snapshot cache directory exists at `context.filesDir/snapshots/` or `context.cacheDir/snapshots/`.

2. **Session Retrieval (`restoreSession`)**:
   - Query `tabDao.getAllTabs()` synchronously or in background IO coroutine.
   - Query `settingDao.getAllSettings()` to load user theme mode, default search engine, and ad blocker status.

3. **Fallback & Cold Start Resolution**:
   - **Case A: Zero Tabs in DB (Fresh Install or All Closed)**:
     - Automatically spawn a default home tab:
       ```kotlin
       val newTab = TabEntity(
           id = UUID.randomUUID().toString(),
           url = "",
           title = "Home",
           position = 0,
           isActive = true,
           lastActiveTimestamp = System.currentTimeMillis()
       )
       tabDao.insertTab(newTab)
       ```
     - Initialize `BrowserUiState` with active tab and `OverlayState.HomeScreen`.
   - **Case B: Persisted Tabs Exist**:
     - Locate tab where `isActive == true`. If none has `isActive == true`, select the tab with maximum `lastActiveTimestamp` or `position == 0`.
     - Verify on-disk presence for each tab's `snapshotPath`:
       - If the file exists on disk: retain `snapshotPath`.
       - If file is missing: set `snapshotPath = null` (tab grid renders fallback placeholder until new frame is captured).

4. **Compose UI Binding**:
   - `BrowserViewModel` publishes initial `BrowserUiState` via `uiState.asStateFlow()`.
   - Jetpack Compose immediately renders the active tab pill in Tier 1 tab strip and tab grid cards without layout jumps or blank screens.
   - `WebViewPool` receives the restored `activeTabId` and prepares the active `WebView` instance, keeping background tabs deferred in memory.

---

## 9. Features Discovered & Specification Matrix

## Features Discovered
| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | Scaffolding | Android 15 Target SDK 35 & Min SDK 26 | Gradle configuration supporting modern Android 15 features while backwards compatible to Android 8.0 (API 26). | Gradle build scripts | Compiled APK | Build error on incompatible API calls | ORIGINAL_REQUEST.md § R1 |
| 2 | Scaffolding | Kotlin 2.0+ Compose Compiler Plugin | Integration of `org.jetbrains.kotlin.plugin.compose` replacing legacy compiler extension versioning. | Kotlin 2.0.21, Compose BOM | Compiled Compose classes | Incompatible compiler plugin compilation error | spec.md § 1 |
| 3 | Persistence | Room Tab Entity & Session Persistence | Relational table storing tab UUID, URL, title, snapshot image path, position, and active flag. | Tab metadata, position indices | Persisted SQLite rows | Fallback destructive migration or in-memory default | ORIGINAL_REQUEST.md § R1, spec.md § 5.3 |
| 4 | Persistence | Room History Entity & Search Indexing | Visited web page records with timestamp, visit count, and title search querying. | URL, page title, timestamp | History list Flow, query suggestions | Empty list on missing query match | spec.md § 4.2, 5.3 |
| 5 | Persistence | Room Favorites Grid Persistence | Persistent storage of user bookmarks with sort position and limit-6 preview query. | Bookmark title, URL, icon | Favorites list Flow | Default empty state bookmark prompt | spec.md § 4.1 |
| 6 | Persistence | Key-Value Settings Store | Persistent preferences for ThemeMode (System, Light, Dark), SearchEngine (DuckDuckGo, Google, Bing), and AdBlocker toggle. | Key/Value strings | Domain enum settings | Default fallback to SYSTEM / DUCKDUCKGO / true | spec.md § 4.4 |
| 7 | Architecture | Manual DI AppContainer | Pure Kotlin dependency container providing repository and database singletons without reflection. | Application Context | Repositories, Database, ViewModelFactory | OutOfMemory or NullPointerException prevented by lazy val | ORIGINAL_REQUEST.md § R1 |
| 8 | MVVM | Unidirectional StateFlow UI State | Single immutable `BrowserUiState` driving all Compose screens with sealed event intents (`BrowserUiEvent`). | UI user events, Web callbacks | Updated StateFlow emission | Coroutine cancellation or state fallback | ORIGINAL_REQUEST.md § R1, spec.md § 1 |
| 9 | Lifecycle | Cold Boot Session Restoration | Resumes exact tab order, active tab index, and snapshot cards from Room DB on cold start. | App startup trigger | Restored tabs and active session | Auto-creates clean Home tab if DB empty | ORIGINAL_REQUEST.md § R1, spec.md § 5.3 |
| 10 | Navigation | Dynamic Bottom Bar Auto-Hide | Translates bottom dock off-screen on downward WebView scroll; restores on upward scroll. | `OnWebScroll(deltaY)` event | `bottomBarVisible` boolean / offset | Ignored on minor touch jitters (< 10px) | spec.md § 2.1 |
| 11 | Search | Search Engine URL Formatter | Encodes query and constructs destination URL for DuckDuckGo, Google, or Bing. | User search string | Formatted query URL | Defaults to DuckDuckGo if unset | spec.md § 4.2, 4.4 |
| 12 | Navigation | Back Press Hierarchy Dispatcher | Triages back button: Dismiss Overlay -> `webView.goBack()` -> Close Active Tab. | System back gesture / button | Navigation action | Closes app only when last single empty tab is closed | spec.md § 5.3 |
| 13 | Testing | In-Memory Database & Robolectric Support | Test harness executing DAOs, Repositories, and ViewModels on JVM using `Room.inMemoryDatabaseBuilder`. | Test coroutine dispatcher, mock context | Verified database transactions | Assertion errors on schema/logic mismatch | ORIGINAL_REQUEST.md § Acceptance Criteria |

---

## 10. Edge Cases & Resilience Strategy

## Edge Cases
| # | Feature | Input / Scenario | Observed / Required Behavior |
|---|---------|------------------|------------------------------|
| 1 | Cold Boot Restore | App launched after clean install with zero database rows. | `restoreSession()` detects empty list, creates a default Home tab with position `0` and `isActive = true`, and sets `OverlayState.HomeScreen`. |
| 2 | Cold Boot Restore | Multiple tabs marked `isActive = 1` due to crash during tab switch. | `restoreSession()` disambiguates by choosing the tab with the highest `lastActiveTimestamp`, executes `tabDao.setActiveTab(chosenId)` to clean flags. |
| 3 | Cold Boot Restore | Tab entity contains a `snapshotPath` but the image file was deleted by OS cache cleaner. | `SnapshotManager` checks `File(path).exists()`; if false, returns null, allowing UI to show default site icon / title card until page re-renders. |
| 4 | Tab Closure | User closes the active tab when 3 tabs are open. | DB deletes target tab; ViewModel selects adjacent tab (e.g. index `(closedIndex - 1).coerceAtLeast(0)`), updates active flag, and displays new tab. |
| 5 | Tab Closure | User closes the last remaining tab in the browser. | DB deletes last tab; ViewModel immediately creates a clean default tab (`url = ""`, `title = "Home"`) to prevent empty screen or invalid state. |
| 6 | Tab Reordering | User drags tab from index 4 to index 0 in Tab Strip or Visual Grid. | Executes `@Transaction` `reorderTabs` updating `position` columns atomically, ensuring no duplicate positions or gaps. |
| 7 | Search Overlay Launch | User taps active tab pill containing long URL with query params. | Active pill opens Search Overlay pre-filled with raw URL; keyboard requests focus via `LaunchedEffect(Unit)` with `SoftwareKeyboardController.show()`. |
| 8 | History Aggregation | User navigates between sub-pages on same domain within 5 minutes. | `recordVisit` detects recent visit for URL, increments `visitCount`, updates `timestamp` and `title` without cluttering history with duplicates. |
| 9 | Invalid URL Input | User enters `"kotlin coroutines flow"` in URL bar instead of a valid URL scheme. | `loadUrl()` detects absence of protocol and presence of spaces, passes string to `searchEngine.buildSearchUrl()` creating DuckDuckGo search URL. |
| 10 | Hostname Only Input | User enters `"reddit.com"` in URL bar without `"https://"`. | `loadUrl()` detects domain format without scheme and automatically prefixes `"https://"`. |
| 11 | Settings Corrupted | Key-value settings table contains invalid string for `theme_mode`. | Enum parsing `ThemeMode.valueOf(str)` wrapped in `runCatching` defaulting safely to `ThemeMode.SYSTEM`. |
| 12 | Memory Stress / Process Death | Android OS kills app process in background while 10 tabs are open. | State is preserved in SQLite; on user return, ViewModel reloads exact tab list, order, active tab, and snapshots from Room. |

---

## 11. Verification & Test Plan

1. **Gradle Build Verification**:
   - Validate syntax and compatibility of `settings.gradle.kts`, `build.gradle.kts`, and `app/build.gradle.kts`.
   - Ensure JVM 17 target compatibility and Android SDK 35 compilation.

2. **Room DAO Unit Tests**:
   - Execute in-memory Room test suite covering `TabDao`, `HistoryDao`, `FavoriteDao`, and `SettingDao`.
   - Verify transaction atomicity (`setActiveTab`, `recordVisit`, `reorderTabs`).

3. **Repository Flow Tests**:
   - Test `TabRepositoryImpl` using `kotlinx-coroutines-test` `TestScope` and `Turbine` / `StateFlow.first()`.
   - Verify that adding, closing, and updating tabs emits fresh list states to observers.

4. **ViewModel State Flow Tests**:
   - Verify initial cold boot session restoration logic.
   - Verify UI event handling for tab switching, search overlay dispatching, and dynamic bottom bar scrolling.
