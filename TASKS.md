# Orchestrator Task Board: 2026-10-13 (Tech Debt Remediation)

## Pass 2: 2026-10-22 - Audit Re-verification (Branch `fix/tech-debt`) - COMPLETED

The audit of 2026-10-13 (`TECH_DEBT_AUDIT.md`) was re-verified line by line against the tree.
Most of its rows were already closed by the 2026-10-13 tracks; the rows below were the ones that
were genuinely still open, and they are now closed.

- [x] **NEW-ARCH-02** - The ad-blocker engine no longer holds a mutable `SettingsRepository`.
      It depends on the narrow `domain/repository/BlockedRequestStats` contract, and
      `data/repository/SettingsBlockedRequestStats` is the only place that knows how a suppressed
      request maps onto the stored counters.
- [x] **NEW-TYPE-01** - `domain/model/ExternalAppPrompt` no longer imports `android.graphics`.
      The icon travels as `domain/model/AppIcon` (PNG bytes with content-based equality) and is
      decoded to a `Bitmap` in the presentation layer only.
- [x] **NEW-ARCH-05** - `BrowserUiEvent.CheckDefaultBrowser` is gone; no UI event carries a
      `Context`. The UI resolves the status and dispatches `UpdateDefaultBrowserStatus(isDefault)`.
- [x] **NEW-DEP-01** - Removed the no-op `WebSettings.databaseEnabled` and the deprecated
      `setForceDark` / `setForceDarkStrategy`; dark mode runs on `setAlgorithmicDarkeningAllowed`.
- [x] **NEW-CONS-02** - Pruned the dead "Liquid Sunset" aliases and renamed the switch to
      `ProstoToggleSwitch`; the design system has a single name ("Prosto Canvas").
- [x] **NEW-DEP-02** - Removed the unused colour and string resources that Android Lint flagged.
- [x] **Guard hardening** - `tools/check_invariants.py` and `DomainArchitectureTest` now reject
      *every* `android.*` import in the domain layer, not a listed subset of packages.
- [x] **SEC-03** - The file chooser reaches `MainActivity` through the `FileChooserHandler`
      interface instead of a downcast to the concrete activity type.
- [x] **2026 toolchain upgrade path** - Verified in a throwaway probe and documented in
      [`docs/adr/0003-2026-toolchain-upgrade-path.md`](docs/adr/0003-2026-toolchain-upgrade-path.md);
      deliberately **not** applied, because no audit finding requires it and `compileSdk 37`
      would break CI. See the ADR for the two blockers that were found.

### Rows verified as already closed (no change needed)

`NEW-BUG-01`, `NEW-CRIT-01`, `NEW-RELIAB-01`, `NEW-PERF-01`, `NEW-ARCH-01`, `NEW-ARCH-03`,
`NEW-ARCH-04`, `NEW-LINT-01`, `NEW-CONS-01`, `NEW-CONS-03`, `CONS-03`, `CONS-04`, `CONS-05`,
`PERF-03`, `PERF-04`, `ERR-01`, `ERR-02`, `TYPE-03`, `NEW-CI-01`.

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
    - NEW-CONS-01: Consolidate fragmented clipboard copying into `com.jestk.browser.util.ClipboardHelper.kt` across `ActiveTabPill.kt`, `LinkContextMenuModal.kt`, `ImageContextMenuModal.kt`.
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
- [x] `LESSONS.md` updated
- [x] `docs/archive/lessons_archive.md` updated
- [x] User summary delivered
