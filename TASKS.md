# Orchestrator Task Board: 2026-10-13 (Tech Debt Remediation)

## Wave 1 (Parallel Independent Tracks) - COMPLETED

- [x] Track 1: `feature/engine-fixes` (Dir: `.worktrees/engine-fixes`)
  - Scope: Engine Reliability, Download Fixes & Lint Standards
    - NEW-BUG-01: Fix download redirect renaming and MediaStore desync in `HoriDownloadManager.kt:341-410`.
    - NEW-CRIT-01: Add `tools:targetApi="27"` to `android:windowLightNavigationBar` in `res/values/themes.xml` and `res/values-night/themes.xml`.
    - NEW-RELIAB-01: Implement `onRenderProcessGone` crash recovery in `JestkWebViewClient.kt`.
    - PERF-03: CoroutineScope hygiene for adblock increment telemetry in `JestkWebViewClient.kt`.
    - Unit tests in `HoriDownloadManagerTest` and `JestkWebViewClientTest`.
  - [x] Worktree provisioned
  - [x] Subagent dispatched
  - [x] Verification gate passed
  - [x] Rebased on main & merged

- [x] Track 2: `feature/ui-decomposition` (Dir: `.worktrees/ui-decomposition`)
  - Scope: UI Monolith Decomposition & Scroll Performance
    - NEW-ARCH-03: Decompose `SearchEngineCarousel.kt` monolith; extract `SearchEngineSwitcherRow` and `SearchEngineSwitcherEditPopup` into `com.jestk.browser.ui.navigation.SearchEngineSwitcherRow.kt`.
    - NEW-ARCH-04: Decompose `TabStripRow.kt`; extract `TabStripDragState` into `com.jestk.browser.ui.navigation.TabStripDragState.kt`.
    - NEW-PERF-01: Debounce `SearchOverlay.kt:182-198` pagination scroll with `snapshotFlow { scrollState.value }.filter { it <= 20 }`.
    - Verification via unit/Robolectric tests.
  - [x] Worktree provisioned
  - [x] Subagent dispatched
  - [x] Verification gate passed
  - [x] Rebased on main & merged

- [x] Track 3: `feature/code-hygiene` (Dir: `.worktrees/code-hygiene`)
  - Scope: Code Hygiene, Utility Consolidation & Dead Code Pruning
    - NEW-ARCH-01: Prune 116 lines of dead commented-out Account & Sync stubs in `SettingsScreen.kt:238-353`.
    - CONS-03: Consolidate duplicated `findActivity()` in `WebViewPool.kt:37` and `JestkApp.kt:107` into `com.jestk.browser.util.ContextExtensions.kt`.
    - NEW-CONS-01: Consolidate fragmented clipboard copying into `com.jestk.browser.util.ClipboardHelper.kt` across `ActiveTabPill.kt`, `LinkContextMenuBottomSheet.kt`, `ImageContextMenuBottomSheet.kt`.
    - NEW-LINT-01: Fix `context.getString()` in Compose lambdas in context menus.
    - CONS-05 & NEW-CONS-03: Localize hardcoded English strings in `AddEditSearchEngineDialog.kt` and `HomeScreen.kt`.
    - Verification via unit/Robolectric tests.
  - [x] Worktree provisioned
  - [x] Subagent dispatched
  - [x] Verification gate passed
  - [x] Rebased on main & merged

## Finalization
- [x] All feature branches merged sequentially into `main`
- [x] Full unit & UI test suite verified (`./gradlew testDebugUnitTest` - 514 tests 100% green)
- [x] Android Lint verified (`./gradlew :app:lintDebug` - 0 errors)
- [x] Worktrees cleaned up & pruned
- [x] `docs/memory/MEMORY.md` updated
- [x] `docs/memory/LESSONS.md` updated
- [x] User summary delivered
