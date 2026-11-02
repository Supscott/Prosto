# Agent Guidelines & Project Knowledge: Prosto Browser

## Core Rules for Coding Agents

### 1. Clarify Bug Repro Steps (Уточнение сценария воспроизведения)
- When a user reports a bug with generic or symptomatic descriptions (e.g. "sometimes X happens", "icon is wrong", "gesture breaks"), **ask the user to clarify the exact reproduction scenario/steps** if doing so will save investigation time, rather than guessing across multiple speculative paths.
- Examples of key details to clarify:
  - Sequence of user actions (e.g. cross-domain navigation vs opening a new tab vs restoring a session).
  - Specific URLs, network conditions, or state triggers.

### 2. CLI Execution & Verification
- **Prefix commands with `rtk`**: Use `rtk git status`, `rtk git diff`, `rtk tools/verify.sh` to minimize token overhead.
- **Fast verification**: Run `rtk tools/verify.sh` to run invariant checks and core engine/viewmodel tests in ~20 seconds.
- **Unit test target**: Always run `rtk ./gradlew testDebugUnitTest`. Never run bare `./gradlew test`, as `testReleaseUnitTest` fails without Robolectric release configuration.
- **Invariants**: Always verify `python3 tools/check_invariants.py` passes before finalizing changes.

### 3. Post-Implementation Reflection & Retrospective (Анализ работы и ретроспектива)
- After successfully implementing and verifying non-trivial fixes or features, conduct a brief retrospective analysis of the work done:
  - **Friction & Bottlenecks**: Identify what slowed down investigation or testing (e.g., ambiguous repro steps, long test feedback loops, obscure platform quirks).
  - **Mechanical Prevention**: Consider if this category of bug or regression can be prevented mechanically in the future (e.g., via `tools/check_invariants.py`, unit test invariants, or architectural seams).
  - **Process / Tooling Enhancements**: Suggest or implement concrete improvements to scripts, guidelines, or invariant checks so future tasks are faster and safer.

### 4. Architectural Rules & Gotchas

#### Favicon Subsystem
- **Single Source of Truth**: The only valid event for receiving a website's favicon is `WebChromeClient.onReceivedIcon(view, icon)`.
- **Never extract favicons in `WebViewClient`**: In Android Chromium WebView, `onPageStarted(view, url, favicon)` and `view.favicon` in `onPageFinished` frequently return the favicon of the *previous* page loaded in that WebView during cross-domain navigation.
- **Domain Guard**: Always verify `isSameDomain(tab.url, pageUrl)` before updating a tab's favicon or domain cache.
- **Cross-Domain Navigation**: Upon navigating to a different domain, always evict stale per-tab favicon files (`FaviconManager.deleteTabFavicons(tabId)`).

#### Compose & WebView State
- **No stale closures**: In `WebViewContainer`, do not use unkeyed `remember { ... }` blocks that capture volatile outer composition values (`currentTab`, `targetUrl`). Prefer `rememberUpdatedState` or look up current tab dynamically from `currentUiState.tabs.find { it.id == id }`.
- **No Blur**: Strict prohibition on `Modifier.blur()` and `dev.chrisbanes.haze` across the entire app.
- **Domain Purity**: Domain layer must remain free of Android framework and Compose types.
