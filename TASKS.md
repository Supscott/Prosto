# Orchestrator Task Board: 2026-10-06

## Wave 1 (Parallel Independent Tracks)

- [x] Track 1: `fix/search-selector` (Branch: `fix/search-selector`, Dir: `.worktrees/search-selector`)
  - Scope: Search Engine Carousel (`SearchEngineCarousel.kt`) - During scroll: expanded 160dp container, active selected provider scaled (1.15) with accent indicator/pill, adjacent providers dimmed (alpha 0.45).
  - [x] Worktree provisioned
  - [x] Subagent dispatched
  - [x] Verification gate passed
  - [x] Rebased on main & merged

- [x] Track 2: `fix/tab-drag-spring` (Branch: `fix/tab-drag-spring`, Dir: `.worktrees/tab-drag-spring`)
  - Scope: Floating dock tab drag&drop (`TabStripRow.kt`) - Snappy tactile spring animation on release (`Spring.StiffnessMedium`, `Spring.DampingRatioLowBouncy`), smooth offset & scale decay.
  - [x] Worktree provisioned
  - [x] Subagent dispatched
  - [x] Verification gate passed
  - [x] Rebased on main & merged

- [x] Track 3: `fix/tab-loading-leak` (Branch: `fix/tab-loading-leak`, Dir: `.worktrees/tab-loading-leak`)
  - Scope: Tab loading isolation (`TabCoordinator.kt`, `BrowserUiState.kt`) - Per-tab loading state (`tabLoadingProgress: Map<String, Float>`), prevent loading indicators from leaking across tab switches.
  - [x] Worktree provisioned
  - [x] Subagent dispatched
  - [x] Verification gate passed
  - [x] Rebased on main & merged

## Wave 2 (Sequential / Coupled Track)

- [ ] Track 4: `fix/favicon-cache` (Branch: `fix/favicon-cache`, Dir: `.worktrees/favicon-cache`)
  - Scope: Favicon persistence and instant dock loading (`FaviconManager.kt`, `TabCoordinator.kt`, `ActiveTabPill.kt`, `TabStripRow.kt`) - Domain-based persistent cache, instant lookup on navigation start without Google S2 leaks, eliminate flash of fallback letter glyph.
  - [x] Worktree provisioned
  - [x] Subagent dispatched
  - [x] Verification gate passed
  - [ ] Rebased on main & merged

## Finalization
- [ ] All feature branches merged sequentially into `main`
- [ ] Full unit & UI test suite verified (`./gradlew testDebugUnitTest`)
- [ ] Worktrees cleaned up & pruned
- [ ] `docs/memory/MEMORY.md` updated
- [ ] `docs/memory/LESSONS.md` updated
- [ ] User summary delivered
