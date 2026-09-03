## 2026-09-02T19:22:45Z

You are the Implementation Worker for Milestone 1 (Project Scaffolding, Room Database & Architecture).
Your working directory is: /Users/ilya/CODING/browser/.agents/teamwork_preview_worker_m1_1
Project root: /Users/ilya/CODING/browser

MANDATORY: Read the authoritative sources and Explorer reports before starting:
- /Users/ilya/CODING/browser/ORIGINAL_REQUEST.md
- /Users/ilya/CODING/browser/spec.md
- /Users/ilya/CODING/browser/PROJECT.md
- /Users/ilya/CODING/browser/.agents/teamwork_preview_explorer_m1_1/analysis.md
- /Users/ilya/CODING/browser/.agents/teamwork_preview_explorer_m1_2/analysis.md
- /Users/ilya/CODING/browser/.agents/teamwork_preview_explorer_m1_3/analysis.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Scope & Tasks for Milestone 1:
1. Create all root Gradle build files: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`. Ensure `gradlew` has executable permissions (`chmod +x gradlew`).
2. Create `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml` targeting SDK 35 (Android 15), Min SDK 26, JVM 17, Kotlin 2.0.21, Compose Compiler Gradle plugin, KSP 2.0.21-1.0.28, Room 2.6.1, Coil 2.7.0, Coroutines 1.9.0, Lifecycle 2.8.7.
3. Create Android resources in `app/src/main/res/`: `values/strings.xml`, `values/themes.xml`, `values/colors.xml`, launcher mipmap/drawables.
4. Implement `com.jestk.browser.JestkApplication` and `com.jestk.browser.di.AppContainer`.
5. Implement Room Entities in `com.jestk.browser.data.local.entity`:
   - `TabEntity.kt` (with indices on `position`, `lastActiveTimestamp`)
   - `HistoryEntity.kt` (with indices on `timestamp`, `url`)
   - `FavoriteEntity.kt` (with indices on `position`)
   - `SettingEntity.kt` & `SettingKeys.kt`
6. Implement Room DAOs in `com.jestk.browser.data.local.dao`:
   - `TabDao.kt` (Flow queries, suspend mutations, `@Transaction setActiveTab`, `@Transaction reorderTabs`)
   - `HistoryDao.kt` (Flow queries, suspend mutations, `@Transaction recordVisit` with 5-min aggregation)
   - `FavoriteDao.kt` (Flow queries, suspend mutations, `@Transaction reorderFavorites`)
   - `SettingDao.kt` (Flow queries, get/set suspend functions)
7. Implement `com.jestk.browser.data.local.Converters` and `com.jestk.browser.data.local.AppDatabase` (singleton + `createInMemory` test factory).
8. Implement Domain models in `com.jestk.browser.domain.model`:
   - `Tab.kt`, `HistoryItem.kt`, `FavoriteItem.kt`, `BrowserSettings.kt`, `ThemeMode.kt`, `SearchEngine.kt`, `OverlayType.kt`. Add `@JvmOverloads` on data class constructors for Java test interop.
   - Entity-Domain mapping extension functions in `com.jestk.browser.data.mapper.Mappers.kt`.
9. Implement Repository interfaces & implementations:
   - `TabRepository.kt` & `TabRepositoryImpl.kt`
   - `HistoryRepository.kt` & `HistoryRepositoryImpl.kt`
   - `FavoriteRepository.kt` & `FavoriteRepositoryImpl.kt`
   - `SettingsRepository.kt` & `SettingsRepositoryImpl.kt`
10. Implement MVVM ViewModels & State in `com.jestk.browser.ui.viewmodel`:
    - `BrowserUiState.kt`, `BrowserUiEvent.kt`, `BrowserViewModel.kt`.
    - Cold boot session restoration logic.
11. Implement unit test suites in `app/src/test/java/com/jestk/browser/`:
    - `data/local/TabDaoTest.kt`, `HistoryDaoTest.kt`, `FavoriteDaoTest.kt`, `SettingDaoTest.kt`, `ConvertersTest.kt`
    - `data/repository/TabRepositoryTest.kt`, `HistoryRepositoryTest.kt`, `FavoriteRepositoryTest.kt`, `SettingsRepositoryTest.kt`
    - `ui/viewmodel/BrowserViewModelTest.kt`
12. Execute verification commands via `rtk`:
    - `rtk ./gradlew assembleDebug`
    - `rtk ./gradlew testDebugUnitTest`
    Document all build/test results in your handoff report.
