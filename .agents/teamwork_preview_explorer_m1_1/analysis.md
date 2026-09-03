# Milestone 1: Project Scaffolding & Build Configuration — Detailed Analysis & Implementation Blueprint

**Document ID:** `M1-EXPLORER-ANALYSIS`  
**Working Directory:** `/Users/ilya/CODING/browser/.agents/teamwork_preview_explorer_m1_1`  
**Target Milestone:** Milestone 1 (Project Scaffolding, Room DB & Architecture)  
**Authoritative References:** `ORIGINAL_REQUEST.md`, `spec.md`, `PROJECT.md`, `survey_r1.md`, `TEST_INFRA.md`  
**Target Platform:** Android (Min SDK 26, Target SDK 35, JVM 17, Kotlin 2.0.21, Compose BOM 2024.10.01)

---

## 1. Executive Summary

Milestone 1 establishes the foundational scaffolding and architecture for **Jestk Browser for Android**. This analysis provides ready-to-implement file blueprints and exact configurations for:
1. **Root Build Infrastructure:** `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, and `gradlew.bat`.
2. **App Module Configuration:** `app/build.gradle.kts`, `app/proguard-rules.pro`, and `app/src/main/AndroidManifest.xml` targeting Android 15 (SDK 35), Min SDK 26, JVM 17, Kotlin 2.0.21, Jetpack Compose with the first-party Kotlin 2.0 Compose compiler plugin, Room 2.6.1 with KSP, AndroidX Lifecycle 2.8.7, and Coil 2.7.0.
3. **Android Resources:** `res/values/strings.xml`, `res/values/themes.xml`, `res/values/colors.xml`, and vector drawables.
4. **Application & Manual DI Architecture:** `JestkApplication.kt` and `AppContainer.kt` providing non-reflective, deterministic dependency injection for repositories, Room database, and ViewModel factories.

---

## 2. Compatibility Matrix & Version Pinning

| Component | Version | Rationale & Compatibility Notes |
|---|---|---|
| **Android Gradle Plugin (AGP)** | `8.7.2` | Full Android 15 (API 35) support, compatible with Gradle 8.10.2 and Kotlin 2.0.21. |
| **Gradle Distribution** | `8.10.2` (bin) | Supports Java 17/21 toolchains and AGP 8.7.x. |
| **Kotlin** | `2.0.21` | Modern compiler with enhanced K2 compiler pipeline and multiplatform stability. |
| **Compose Compiler Plugin** | `2.0.21` (`org.jetbrains.kotlin.plugin.compose`) | Built into Kotlin 2.0+, replacing legacy `composeOptions.kotlinCompilerExtensionVersion`. |
| **KSP (Kotlin Symbol Processing)** | `2.0.21-1.0.28` | Exact match for Kotlin 2.0.21, required for Room compiler annotation processing. |
| **Room Database** | `2.6.1` | SQLite abstraction with Kotlin Coroutines / Flow support and KSP compiler. |
| **Compose BOM** | `2024.10.01` | Pins Material 3, UI, Foundation, and Runtime versions deterministically. |
| **AndroidX Lifecycle** | `2.8.7` | Provides `lifecycle-runtime-compose` and `lifecycle-viewmodel-compose`. |
| **AndroidX WebKit** | `1.12.1` | Enhanced WebView features (VisualStateCallback, SafeBrowsing, WebResourceResponse). |
| **Coil Compose** | `2.7.0` | Lightweight image loader for site favicons and disk snapshot rendering. |
| **Robolectric & Test Tools** | `4.14.1` / Truth `1.4.4` | JVM-level headless unit testing without requiring an emulator. |

---

## 3. Detailed File Blueprints

### 3.1 Root Scaffolding Files

#### 3.1.1 `/Users/ilya/CODING/browser/settings.gradle.kts`
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

#### 3.1.2 `/Users/ilya/CODING/browser/build.gradle.kts` (Root)
```kotlin
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
```

#### 3.1.3 `/Users/ilya/CODING/browser/gradle.properties`
```properties
# JVM Memory and encoding
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8

# AndroidX and Build Features
android.useAndroidX=true
android.nonTransitiveRClass=true

# Kotlin Code Style
kotlin.code.style=official
```

#### 3.1.4 `/Users/ilya/CODING/browser/gradle/wrapper/gradle-wrapper.properties`
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

#### 3.1.5 `/Users/ilya/CODING/browser/gradlew` & `/Users/ilya/CODING/browser/gradlew.bat`
- Standard Gradle 8.10.2 wrapper scripts allowing CLI execution `./gradlew assembleDebug` and `./gradlew testDebugUnitTest`.
- The Unix `gradlew` script must have executable file permissions (`chmod +x`).

---

### 3.2 App Module Build & Manifest

#### 3.2.1 `/Users/ilya/CODING/browser/app/build.gradle.kts`
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
    implementation("androidx.compose.material:material") // PullRefreshIndicator & pullRefresh

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

#### 3.2.2 `/Users/ilya/CODING/browser/app/proguard-rules.pro`
```proguard
# Room Database keep rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# WebView & JavaScript interface rules
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Domain & Entity models serialization
-keep class com.jestk.browser.domain.model.** { *; }
-keep class com.jestk.browser.data.local.entity.** { *; }

# Coil image loader
-keep class coil.** { *; }
-dontwarn coil.**
```

#### 3.2.3 `/Users/ilya/CODING/browser/app/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Network Permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".JestkApplication"
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher_foreground"
        android:label="@string/app_name"
        android:roundIcon="@drawable/ic_launcher_foreground"
        android:supportsRtl="true"
        android:theme="@style/Theme.JestkBrowser"
        android:usesCleartextTraffic="true"
        android:hardwareAccelerated="true">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize"
            android:supportsPictureInPicture="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize">
            
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <!-- Web Link Handler Intent Filter -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="http" />
                <data android:scheme="https" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

---

### 3.3 Android Resources

#### 3.3.1 `/Users/ilya/CODING/browser/app/src/main/res/values/strings.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Jestk</string>
    <string name="search_placeholder">Search or enter website name</string>
    <string name="new_tab">New Tab</string>
    <string name="home">Home</string>
    <string name="search">Search</string>
    <string name="tab_overview">All Tabs</string>
    <string name="settings">Settings</string>
    <string name="close_tab">Close Tab</string>
    <string name="close_all_tabs">Close All Tabs</string>
    <string name="copy_url">Copy URL</string>
    <string name="add_to_favorites">Add to Favorites</string>
    <string name="remove_favorite">Remove Favorite</string>
    <string name="clear_history">Clear History</string>
    <string name="recent_tabs_title">Recent Tabs</string>
    <string name="favorites_title">Favorites</string>
    <string name="show_all_favorites">Show All Favorites</string>
    <string name="theme_title">Appearance</string>
    <string name="theme_system">System Default</string>
    <string name="theme_light">Light</string>
    <string name="theme_dark">Dark</string>
    <string name="search_engine_title">Search Engine</string>
    <string name="search_engine_duckduckgo">DuckDuckGo</string>
    <string name="search_engine_google">Google</string>
    <string name="search_engine_bing">Bing</string>
    <string name="ad_blocker_title">Ad &amp; Tracker Blocker</string>
    <string name="ad_blocker_summary">Block known advertising and tracking domains</string>
    <string name="github_link">GitHub Repository</string>
    <string name="jestk_link">Jestk.com</string>
</resources>
```

#### 3.3.2 `/Users/ilya/CODING/browser/app/src/main/res/values/themes.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.JestkBrowser" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:windowLightNavigationBar">true</item>
    </style>
</resources>
```

#### 3.3.3 `/Users/ilya/CODING/browser/app/src/main/res/values/colors.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#007AFF</color>
    <color name="background_light">#F2F2F7</color>
    <color name="background_dark">#1C1C1E</color>
    <color name="surface_light">#FFFFFF</color>
    <color name="surface_dark">#2C2C2E</color>
    <color name="text_primary_light">#000000</color>
    <color name="text_primary_dark">#FFFFFF</color>
</resources>
```

#### 3.3.4 `/Users/ilya/CODING/browser/app/src/main/res/drawable/ic_launcher_foreground.xml`
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#007AFF"
        android:pathData="M54,20 C35.2,20 20,35.2 20,54 C20,72.8 35.2,88 54,88 C72.8,88 88,72.8 88,54 C88,35.2 72.8,20 54,20 Z M54,28 C68.4,28 80,39.6 80,54 C80,68.4 68.4,80 54,80 C39.6,80 28,68.4 28,54 C28,39.6 39.6,28 54,28 Z" />
    <path
        android:fillColor="#007AFF"
        android:pathData="M54,34 L62,50 L46,58 Z" />
</vector>
```

---

### 3.4 Application & Manual DI Architecture

#### 3.4.1 `/Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/JestkApplication.kt`
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

#### 3.4.2 `/Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/di/AppContainer.kt`
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

---

### 3.5 Domain Model Signatures & Java Test Interoperability

To ensure 100% interoperability with Java test suites in `app/src/test/java/com/jestk/browser/e2e/tier1/`, all domain models must use `@JvmOverloads` constructor annotations and exact parameter ordering:

#### `Tab.kt`
```kotlin
package com.jestk.browser.domain.model

data class Tab @JvmOverloads constructor(
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

#### `HistoryItem.kt`
```kotlin
package com.jestk.browser.domain.model

data class HistoryItem @JvmOverloads constructor(
    val id: Long = 0,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val visitCount: Int = 1,
    val faviconUrl: String? = null
)
```

#### `FavoriteItem.kt`
```kotlin
package com.jestk.browser.domain.model

data class FavoriteItem @JvmOverloads constructor(
    val id: Long = 0,
    val title: String,
    val url: String,
    val position: Int = 0,
    val iconUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

#### `BrowserSettings.kt`
```kotlin
package com.jestk.browser.domain.model

data class BrowserSettings @JvmOverloads constructor(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val adBlockerEnabled: Boolean = true
)
```

---

## 4. Edge Cases, Corner Cases & Pitfalls

1. **Compose Compiler Version Incompatibility:**  
   In Kotlin 2.0.0+, the Compose Compiler Gradle plugin (`org.jetbrains.kotlin.plugin.compose`) MUST be used in root `build.gradle.kts` and app `build.gradle.kts`. Do not specify `composeOptions { kotlinCompilerExtensionVersion = "..." }` as that triggers a build failure in Kotlin 2.0.
2. **KSP Version Alignment:**  
   KSP version `2.0.21-1.0.28` must match Kotlin version `2.0.21` exactly. Mismatched KSP versions cause Gradle plugin resolution failures during sync.
3. **Java 17 Toolchain Compatibility:**  
   Gradle JVM arguments must supply adequate heap (`-Xmx2048m`) and UTF-8 encoding. `compileOptions` and `kotlinOptions.jvmTarget` must both target Java 17.
4. **WindowSoftInputMode & Manifest Configurations:**  
   `android:windowSoftInputMode="adjustResize"` and `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize"` are critical to prevent activity re-creation on keyboard toggle or PiP transition.
5. **Hardware Acceleration:**  
   `android:hardwareAccelerated="true"` in `AndroidManifest.xml` is required for WebView rendering and `VisualStateCallback`/`PixelCopy` canvas capture.

---

## 5. Verification & Test Plan

1. **Gradle Build Verification:**
   - `./gradlew assembleDebug` compiles cleanly with zero errors.
2. **JVM Unit Test Execution:**
   - `./gradlew testDebugUnitTest` runs all Tier 1 E2E tests (`ArchitectureAndDatabaseCoverageTest`, `RepositoryStateFlowCoverageTest`, `ColdBootSessionRestoreCoverageTest`, `SafeComposeDetachmentCoverageTest`, `WebViewPoolCoverageTest`, `VisualStateCallbackSnapshotCoverageTest`, `BackgroundMediaPlaybackCoverageTest`) with 100% pass rate.
