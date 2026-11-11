# Splitting the three most-edited god files

Status: planned. Line numbers refer to commit `4f7aefb`.

## Why these three

| File | Lines | Commits in 3 months | Problem |
|---|---|---|---|
| `ui/JestkApp.kt` | 1002 | 89 | One 861-line composable: effects, system bars, viewport, overlays, pills, dialogs |
| `ui/viewmodel/BrowserViewModel.kt` | 1494 | 71 | `onEvent` is one 949-line `when` with 126 branches |
| `ui/viewmodel/TabCoordinator.kt` | 1973 | 39 | 56 functions over 9 unrelated concerns |

Almost every task touches one of them, and none fits in one read (roughly 11 tokens per line),
so agents read them in fragments and miss related branches (`closeTab` has three).

Target: every resulting file under 400 lines, cohesive enough that a change needs one or two
files. Not a goal: smaller than ~150 lines; fragments that are always read together are worse
than one file.

## Rules for whoever executes a step

1. **Move, do not rewrite.** Function bodies move verbatim. Allowed edits: the enclosing
   class/file, constructor parameters, `this`/receiver adjustments, visibility (`private` →
   `internal` only where a moved piece is now called from another file), and one-line
   delegations. No renames of public API, no logic changes, no "while I am here" fixes, no
   comment rewording. KDoc and comments travel with their code.
2. **Public API stays the same in phase 1.** `TabCoordinator`'s methods, `BrowserViewModel`'s
   public members, `BrowserUiEvent` class names and `JestkApp`'s signature do not change, so
   no test changes are needed. A test that must change means the step broke the rule.
3. **One step = one commit**, in the order below. After each step:
   - `rtk tools/verify.sh --all` (must print `Tests: N/N passed`);
   - `rtk tools/smoke.sh --install` when the phone is available (JestkApp and TabCoordinator steps);
   - `python3 tools/file_size_ratchet.py --update` to lower the baseline, committed with the step.
4. Do not edit `tools/file_size_baseline.json` by hand and never raise an entry.
5. Report: the list of new files with line counts, and anything that could not be moved
   verbatim and why.

Review method: `git diff --color-moved=zebra --color-moved-ws=allow-indentation-change HEAD~1`
shows moved blocks in their own colors; only the remaining plain additions/deletions need
reading.

---

## 1. JestkApp.kt (1002 → ~300)

Pure Compose extraction. Each block becomes an `internal` composable in `ui/app/` taking
the values it reads as parameters (`uiState`, `onEvent`, `webViewPool`, …). Do not pass
`BrowserViewModel` into new composables except where the block already calls the view model
directly (navigation commands, pool binding).

| New file | Contents (current lines) | ~Lines |
|---|---|---|
| `ui/app/NavigationCommandEffect.kt` | `LaunchedEffect(viewModel)` collecting `navigationCommands` (194–260) | 75 |
| `ui/app/WebViewPoolBindingEffects.kt` | tab-id retain effect and pool listener wiring (168–192) | 35 |
| `ui/app/SystemBarsEffects.kt` | bar color resolution, status/nav bar appearance, fullscreen orientation and immersive effects (inside the theme, ~270–360) | 110 |
| `ui/app/AppOverlays.kt` | the `AnimatedVisibility` overlays and dedicated full screens (~530–666) | 150 |
| `ui/app/AppNotificationPills.kt` | undo-close, download, external-app pills, app-open confirmation, update and blocked-ad banners (~667–845) | 190 |
| `ui/app/AppDialogs.kt` | link/image context menus, add favorite, search engine dialog, insecure HTTP warning, feedback and folder sheets (~846–953) | 120 |
| `JestkApp.kt` keeps | state collection, `BackHandler`, workspace switcher, main viewport `Box`, bottom bar, fullscreen video layer, mini player | ~300 |

Gotchas:
- Keep `rememberUpdatedState` values where they are read inside long-lived effects; pass the
  `State` or a lambda, not a snapshot value, into extracted effects.
- The main viewport hosts the live `WebViewContainer`. Do not move it, and do not wrap it in a
  new layout or `graphicsLayer` (flicker and the no-blur rule).

## 2. TabCoordinator.kt (1973 → facade ~170 + 9 parts)

Keep `TabCoordinator` as a thin facade with an **unchanged constructor and public methods**,
so `BrowserViewModel` and the 1427-line `TabCoordinatorTest` do not change. It builds the parts
and delegates each public method in one line.

### Shared state

New `TabCoordinatorContext` (`ui/viewmodel/tabs/TabCoordinatorContext.kt`, ~120 lines) holds
what several parts use:

- constructor dependencies: repositories, `webViewPool`, `tabOperationMutex`,
  `getCurrentState`, `updateUiState`, `emitNavigation`, `scope`, `ioDispatcher`;
- shared maps and caches: `tabThemeColors`, `tabBottomThemeColors`, `tabLoadingProgress`,
  the three external-app maps, `cachedAllTabs`, `cachedAllFolders`,
  `recentlyClosedTabsByWorkspace`, `activeWorkspaceIdFlow`;
- two helpers used across parts: `holdUntilFirstPaintIfRecreated` (1160) and
  `checkAndPruneEmptyFolder` (1639).

The facade keeps exposing `tabLoadingProgress` and the other `internal val` maps by
delegating to the context (`BrowserViewModelTest` reads `tabLoadingProgress`).

### Parts (`ui/viewmodel/tabs/`)

| Class | Functions (current lines) | ~Lines | Uses parts |
|---|---|---|---|
| `ExternalAppPrompts` | `handleExternalAppOffered` … `dismissExternalAppPrompt` (95–182) | 95 | — |
| `TabActivation` | `selectTab`, `activateTabInternal`, `switchAdjacentTab` (663–781, 1025–1037) | 150 | ExternalAppPrompts |
| `TabOpening` | `openNewTabForSearch`, `loadFallbackTabs`, `tabsWithNewTab`, `activateNewTabState`, `openNewTab`, `openPopupTab`, `openTabInBackground`, `openRecentTab` (495–662) | 175 | TabActivation |
| `TabClosing` | `closeTab`, `undoCloseTab`, `closeAllTabs`, `clearRecentlyClosedTab` (782–1024, 1957–1967) | 270 | TabActivation |
| `TabFolders` | `reorderTabsAndFolders`, folder functions (1038–1074, 1525–1742 minus `checkAndPruneEmptyFolder`) | 260 | TabActivation, TabOpening, TabClosing |
| `PageEvents` | `navigateToUrl`, `handlePageStarted`, `handlePageFinished`, `handleTitleReceived`, `handleProgressChanged`, `handleStopLoading`, `handleNavState` (1075–1371 minus 1160–1165) | 290 | — |
| `PageAppearance` | `handleSnapshot`, `handleUpdateFavicon`, `handleThemeColor`, `handleThemeColors` (1372–1477) | 110 | — |
| `TabFavorites` | `addCurrentToFavorites`, `addFavorite`, `deleteFavorite`, `moveFavoriteToWorkspace` (1478–1524, 1968–1973) | 65 | — |
| `Workspaces` | `switchWorkspace`, `switchWorkspaceInternal`, `createWorkspace`, `updateWorkspace`, `deleteWorkspace`, `moveTabToWorkspace` (1743–1956) | 220 | TabActivation |
| `TabSessionSync` | `restoreSession`, `observeDatabaseFlows` (183–494) | 315 | — |

The cross-part calls above were extracted mechanically from the current code and form a DAG,
so construct the parts in this order: context, ExternalAppPrompts, TabActivation, TabOpening,
TabClosing, TabFolders, PageEvents, PageAppearance, TabFavorites, Workspaces, TabSessionSync.

Gotchas:
- `closeTab` clears per-tab runtime state (theme colors, progress, the three external-app
  maps, pool, `WebViewStateStore`, `externalAppTabIds`) before its three branches. Keep that
  block intact and first.
- Everything that takes `tabOperationMutex` must take the same instance from the context.
  Never create a mutex in a part.
- `@Volatile` caches move into the context with their annotations.

## 3. BrowserViewModel.kt (1494 → ~450 + 7 handlers)

### Route events by domain

Give every event a domain marker in `BrowserUiEvent.kt`. In the same package, declare sealed
sub-interfaces, e.g. `sealed interface TabUiEvent : BrowserUiEvent`, and change each event's
supertype from `: BrowserUiEvent` to its domain interface. Class names and nesting stay
(`BrowserUiEvent.OpenNewTab` keeps compiling everywhere). Each event extends exactly one domain
interface, so `onEvent` stays exhaustive:

```kotlin
fun onEvent(event: BrowserUiEvent) = when (event) {
    is TabUiEvent -> tabEvents.handle(event)
    is OverlayUiEvent -> overlayEvents.handle(event)
    // one line per domain
}
```

Put the sub-interfaces in a new `BrowserUiEventDomains.kt`. `BrowserUiEvent.kt` is
already at its size baseline; changing a supertype keeps its line count.

### Handlers (`ui/viewmodel/events/`)

Follow the existing `SearchCoordinator`/`DownloadCoordinator` pattern: a class with
explicit dependencies (`getCurrentState`, `updateUiState`, `scope`, coordinators, lambdas for
view model helpers such as `loadUrl` and `dispatch = ::onEvent` for branches that re-dispatch).

| Handler | Events (onEvent lines) | ~Lines |
|---|---|---|
| `TabEvents` | tabs, workspaces, undo, folders, `OpenTabInBackground` (268–372, 943–956, 1112–1214) | 230 |
| `OverlayEvents` | `OpenOverlay` … `OpenDownloads`, menus, bottom bar, scroll, link and image context menus (377–452, 826–843, 919–942, 961–1017) | 210 |
| `PageEvents` | `LoadUrl`, `SubmitSearch`, `UpdateSearchQuery`, Go/Reload/Stop, `SwitchSearchEngineForQuery`, page callbacks (478–558) | 85 |
| `LibraryEvents` | favorites, history, search engines and their dialogs (559–623, 771–825) | 125 |
| `SettingsEvents` | `Set*Enabled`, theme, search engine, update, feedback, default browser, downloads (453–477, 624–758, 957–960) | 175 |
| `SafetyEvents` | external app prompts, insecure HTTP warning, blocked ad navigation (759–770, 1018–1111) | 110 |
| `MediaEvents` | video playing, fullscreen, clean player (844–914) | 75 |

`HandleBackPress` (915) and `handleBackNavigation` (1353–1486) move to `BackPressHandler.kt`
(~140), with the `when` branch routing to it.

What stays in `BrowserViewModel`: constructor, coordinators, state flows, `init`, the small
public functions (160–265), `loadUrl`/`schedulePreconnect`/`submitSearch`, `handleWebScroll`
and the router: ~450.

Gotchas:
- Branches that call `onEvent(...)` recursively must call `dispatch(...)` so the event goes
  through the router again, not straight to the same handler.
- `viewModelScope` is passed as `scope`; handlers never create scopes.
- Keep the `Log.d(TAG, "Navigation command: …")` lines; tests and on-device debugging grep them.

---

## Execution

| Step | Who | Why |
|---|---|---|
| Plan (this document) | Opus | Boundaries, shared state, construction order |
| 1. JestkApp | Sonnet | Mechanical Compose extraction; smoke test covers the WebView hosting |
| 2. TabCoordinator | Sonnet, then Opus review | Facade keeps tests unchanged; review the context wiring |
| 3. BrowserViewModel | Sonnet, then Opus review | Marker interfaces plus handlers; review re-dispatch paths |

Steps 1 and 2 touch disjoint files and can run in parallel worktrees; step 3 after step 2,
because both edit `ui/viewmodel`. The baseline JSON is regenerated after each merge, never
merged by hand.

## Phase 2 (later, optional)

- `BrowserViewModel` handlers call the `TabCoordinator` parts directly; the facade shrinks
  and its delegations are deleted together with the matching test indirection.
- `BackPressHandler` becomes a pure function `resolveBackPress(state): BackAction` with
  table-driven unit tests, no view model needed.
- Same treatment for the Compose screens: `TabStripRow` (1661), `HomeScreen` (1489),
  `SettingsScreen` (1241), and moving the JavaScript in `AdBlockerScript.kt` to `assets/`.
