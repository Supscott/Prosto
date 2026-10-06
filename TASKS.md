# Orchestrator Task Board: 2026-10-06

## Wave 1 (Parallel Independent Tracks) - COMPLETED
- [x] Track 1: `fix/search-selector` (Merged into `main`)
- [x] Track 2: `fix/tab-drag-spring` (Merged into `main`)
- [x] Track 3: `fix/tab-loading-leak` (Merged into `main`)

## Wave 2 (Sequential Track) - COMPLETED
- [x] Track 4: `fix/favicon-cache` (Merged into `main`)

## Wave 3 (Sequential Polish Tracks)

- [ ] Track 5: `fix/dock-spring-teleport` (Branch: `fix/dock-spring-teleport`, Dir: `.worktrees/dock-spring-teleport`)
  - Scope: Tab Drag Spring Animation & Teleport Elimination (`TabStripRow.kt`, `ActiveTabPill.kt`)
    - Disable `LazyRow` scroll during drag/settle (`userScrollEnabled = !dragState.isDragging && !dragState.isSettling`).
    - Handle pointer input on `PointerEventPass.Initial`, preventing premature gesture cancellation by parent scroll.
    - Add `Modifier.animateItem()` to `LazyRow` items for fluid physics-based placement of neighbor tabs.
    - Accurate width-based swap thresholds (148dp active pill vs 38dp circle).
    - Snappy spring drop settlement without teleporting.
  - [ ] Worktree provisioned
  - [ ] Subagent dispatched
  - [ ] Verification gate passed
  - [ ] Rebased on main & merged

- [ ] Track 6: `fix/tab-title-url-sync` (Branch: `fix/tab-title-url-sync`, Dir: `.worktrees/tab-title-url-sync`)
  - Scope: Stale Title & URL Fix (`JestkWebViewClient.kt`, `JestkWebChromeClient.kt`, `JestkApp.kt`, `WebViewContainer.kt`, `BrowserUiEvent.kt`, `BrowserViewModel.kt`, `TabCoordinator.kt`)
    - Wire `WebChromeClient.onReceivedTitle` to `BrowserUiEvent.OnTitleReceived` and `TabCoordinator.handleTitleReceived`.
    - Implement `doUpdateVisitedHistory` in `JestkWebViewClient` for redirects, pushState, and SPA navigation.
    - Reset lingering previous titles to "Loading..." / clean host in `handlePageStarted`.
    - Fix `TabCoordinator.observeDatabaseFlows()` to prioritize `state.activeTabId` over stale Room flags.
    - Sync live WebView URL/title on `selectTab`.
  - [ ] Worktree provisioned
  - [ ] Subagent dispatched
  - [ ] Verification gate passed
  - [ ] Rebased on main & merged

## Finalization
- [ ] All feature branches merged sequentially into `main`
- [ ] Full unit & UI test suite verified (`./gradlew testDebugUnitTest`)
- [ ] Worktrees cleaned up & pruned
- [ ] `docs/memory/MEMORY.md` updated
- [ ] `docs/memory/LESSONS.md` updated
- [ ] User summary delivered
