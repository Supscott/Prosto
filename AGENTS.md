# Agent Guidelines & Project Knowledge: Prosto Browser

Welcome to the **Prosto** project. This file provides operational rules, architectural standards, and workflow guidelines for all AI coding agents working on this codebase.

---

## Pre-Flight Checklist (Start of Non-Trivial Tasks)

> [!IMPORTANT]
> **BEFORE writing code or planning any implementation:**
> 1. Read [`LESSONS.md`](file:///Users/ilya/CODING/browser/LESSONS.md) to review active high-signal gotchas and traps discovered on this codebase.
> 2. Run `rtk tools/verify.sh` to ensure a green baseline before starting changes.
> 3. Read [`TASKS.md`](TASKS.md): open work shared by all agents. When you start a listed task, move it to *In progress* with your branch; when you finish, delete it and add any follow-up you leave behind (one line each).

---

## Core Rules for Coding Agents

### 1. Clarification & Inquiries (Уточнение сценариев и параметров)
- **Bug reproduction**: When a user reports a bug with generic or symptomatic descriptions (e.g. "sometimes X happens", "icon is wrong", "gesture breaks"), **ask the user to clarify the exact reproduction scenario/steps** if doing so will save investigation time, rather than guessing across multiple speculative paths (e.g. cross-domain navigation vs opening a new tab vs restoring a session).
- **UI & Cosmetic tweaks (Context-First Inquiries)**: When asked to adjust subjective UI properties (e.g. "make spacing slightly smaller", "tweak padding"), **inspect the code/tokens first to find the current value** (e.g. `8.dp`), and offer concrete options (*"Currently spacing is 8.dp. Should we try 4.dp or 6.dp?"*). Never ask raw, context-free questions like *"How many dp do you want?"*.

### 2. CLI Execution & Verification
- **Prefix commands with `rtk`**: Use `rtk git status`, `rtk git diff`, `rtk tools/verify.sh` to minimize token overhead.
- **Fast verification**:
  - `rtk tools/verify.sh`: Invariants + core coordination and engine tests (~20s).
  - `rtk tools/verify.sh --ui`: Invariants + UI previews and bottom dock tests (~25s).
  - `rtk tools/verify.sh --all`: Full unit test suite (`testDebugUnitTest`).
  - `rtk tools/verify.sh --changed`: Invariants + the test classes added or edited on the branch (vs `main`) or in the working tree. The default run uses a fixed set, so a new test class runs only with `--changed` or `--all`. `--changed` also adds the tests that cover changed filter lists, ad-block assets and content scripts (one `extra tests:` line per rule hit); `--changed --dry-run` prints the selection without Gradle.
  - Each run ends with `==> Tests: N/M passed` and one `FAIL Class.test: message` line per failure (`tools/test_summary.py`); no need to open the XML reports.
- **Visual UI Previews**: Run `rtk tools/preview.sh <TestName>` (e.g. `rtk tools/preview.sh BottomBarPreviewTest`) to render Compose previews to PNG in `build/` using Robolectric native graphics without deploying to a physical device.
- **Unit test target**: Always run `rtk ./gradlew testDebugUnitTest`. Never run bare `./gradlew test`, as `testReleaseUnitTest` fails without Robolectric release configuration. In coroutine tests `advanceUntilIdle()` skips `backgroundScope` work; use `runCurrent()` there.
- **Invariants**: Always verify `python3 tools/check_invariants.py` passes before finalizing changes.
- **On-Device Checks**: Use `rtk tools/device.sh <cmd>` instead of hand-written adb chains: `wake` (fails fast when the phone is locked: ask the user to unlock), `install`, `open <url>` (quotes URLs safely), `caller`, `back`, `top`, `visible`/`pages` (which WebView page is on screen, via CDP), `eval <url-prefix> <js>` (run JS in that page, prints JSON; `CDP_GESTURE=1` runs it as a user gesture; `location.origin` comes from the URL, read `self.origin` for a sandboxed page), `ui` (labelled nodes on screen), `tapui <label>`, `tap`/`swipe` (`tap <url-prefix> <css-selector>` taps an element's centre with a real touch, so the page sees a user gesture), `serve` (the ad-block fixture at `http://127.0.0.1:8765/` via `adb reverse`), `reset` (wipe the debug app's data), `log <regex>`, `shot` (downscaled). Prefer `top`/`visible`/`log`/`ui` over screenshots. Put lock-screen checks last: after a lock the user must unlock the phone; test with Home where it covers the same path. Run `rtk tools/smoke.sh` after any change to WebView attachment, tab switching, intents or the Activity; it checks the on-screen page, the task stack and intent delivery and prints PASS/FAIL. Run `tools/device.sh sleep` when done. Never drive `com.jestk.browser` (the user's own install); the scripts refuse it.
- **Shell and tool calls**: the shell is zsh, so quote globs and URLs (`--include='*.kt'`, `'https://a/?q=1'`), or it fails with "no matches found". In auto mode send a Bash command or a Linear write on its own, not in parallel with another call: otherwise both may come back with "no verdict".
- **Large files and refactoring**: Map a big file with `python3 tools/outline.py <file>` (members with line ranges; `--groups name=a-b ...` prints calls between groups for planning a split) and read it in parts. Review a move-only refactoring with `python3 tools/move_check.py <base> [<head>] [--sub OLD=NEW]`: moved lines cancel out, only real changes are listed. Delegate the moving itself to the `refactor-mover` agent (`.claude/agents/refactor-mover.md`, Sonnet) with a plan section and a base commit; keep design and review in the main session.
- **Parallel agents**: run at most two Gradle builds at once (one per worktree). On this 8 GB machine three parallel agents ran the Kotlin daemon out of memory (`OutOfMemoryError`); if it happens, rerun with `-Pkotlin.compiler.execution.strategy=in-process -Pkotlin.daemon.jvmargs=-Xmx3g --max-workers=2`. Tell a delegated agent to edit code with Write/Edit (not heredoc or Python edits) and to run one command per Bash call: the worktree guard rejects compound commands. Ask it to list the platform assumptions its design relies on (WebView, Chromium, Android behaviour): those become the first device checks in the main session, since it cannot use the phone. `tools/worktrees.sh list` shows every worktree with its merge state; `prune-merged` removes merged clean ones after asking. A new worktree lacks the git-ignored `local.properties` and `TASKS.md`; `tools/verify.sh` and `tools/preview.sh` copy them from the main checkout (`tools/ensure_worktree_files.sh`), but copy them by hand before a bare Gradle run.
- **On-Device WebView Debugging**: See [`.agent/skills/adb-webview-testing/SKILL.md`](file:///Users/ilya/CODING/browser/.agent/skills/adb-webview-testing/SKILL.md) for ADB port forwarding, CDP WebSocket tracing, cookie/storage inspection, and headless Chrome DevTools commands.
- **DuckDuckGo Android Reference**: See [`.agent/skills/duckduckgo-reference/SKILL.md`](file:///Users/ilya/CODING/browser/.agent/skills/duckduckgo-reference/SKILL.md) for module paths, GitHub lookup recipes, and adaptation guidelines when implementing WebView edge cases, web compatibility, or privacy features.

### 3. Post-Implementation Reflection & Retrospective (Анализ работы и ретроспектива)
- After successfully implementing and verifying non-trivial fixes or features, conduct a brief retrospective analysis of the work done:
  - **Friction & Bottlenecks**: Identify what slowed down investigation or testing (e.g., ambiguous repro steps, long test feedback loops, obscure platform quirks).
  - **Mechanical Prevention**: Consider if this category of bug or regression can be prevented mechanically in the future (e.g., via `tools/check_invariants.py`, unit test invariants, or architectural seams).
  - **Process / Tooling Enhancements**: Suggest or implement concrete improvements to scripts, guidelines, or invariant checks so future tasks are faster and safer.
  - **Update LESSONS.md**: Use `python3 tools/harness_reflect.py add` to record new gotchas when relevant (observing the 10-12 active lesson budget).
- **End of every successful session** (not only non-trivial fixes): before the final report, analyse the session as a whole: what got in the way, what could have gone better, and what in the harness (scripts, invariants, docs, agent prompts) would make the next session faster or safer. Fix the small ones right away. File every other one in Linear: team PRO, labels `agent`, `harness` and `Improvement` (or `Bug`), status Todo, one issue each; search for a duplicate first. There is no local harness task file.
- **Bugs and improvements found along the way**: anything you notice outside the task at hand (a bug in nearby code, a missing rule, a test or harness gap, a design idea) goes to Linear before the session ends, not only into the final report. One issue each, team PRO, labels `agent` plus `Bug` or `Improvement`, and also `harness` when it concerns the harness (scripts, invariants, test tooling, docs, skills, agent prompts); status Todo; search for a duplicate first (`list_issues` with a query) and link related issues. Add the label `verif` only when the problem is confirmed (reproduced, seen on a device, or proven in code with the evidence written in the issue) and needs no further verification before work starts; leave it off guesses and ideas that still need measuring, and say in the description which it is.

---

## Architectural Invariants & Code Standards

### Favicon Subsystem
- **Single Source of Truth**: The only valid event for receiving a website's favicon is `WebChromeClient.onReceivedIcon(view, icon)`.
- **Never extract favicons in `WebViewClient`**: In Android Chromium WebView, `onPageStarted(view, url, favicon)` and `view.favicon` in `onPageFinished` frequently return the favicon of the *previous* page loaded in that WebView during cross-domain navigation.
- **Domain Guard**: Always verify `isSameDomain(tab.url, pageUrl)` before updating a tab's favicon or domain cache.
- **Cross-Domain Navigation**: Upon navigating to a different domain, always evict stale per-tab favicon files (`FaviconManager.deleteTabFavicons(tabId)`).

### Compose & WebView State
- **No stale closures**: In `WebViewContainer`, do not use unkeyed `remember { ... }` blocks that capture volatile outer composition values (`currentTab`, `targetUrl`). Prefer `rememberUpdatedState` or look up current tab dynamically from `currentUiState.tabs.find { it.id == id }`.
- **No Blur**: Strict prohibition on `Modifier.blur()` and `dev.chrisbanes.haze` across the entire app. Live `WebView` inside `GraphicsLayer` or dynamic blur causes severe screen flicker on Android 12+.
- **Domain Layer Purity**: Domain layer must remain free of Android framework UI types (`android.view.*`, `android.widget.*`, `android.graphics.*`) and Compose types. Domain models must remain pure Kotlin.

### Anti-Bot & Content Scripts Standards (Cloudflare / Turnstile / WAF)
- **UserAgentMetadata Purity**: Never construct synthetic brand lists or artificial GREASE versions from scratch. Mirror DuckDuckGo: read native `WebSettingsCompat.getUserAgentMetadata(settings)`, keep Chromium's genuine GREASE brand, versions, and entropy fields untouched, and only map `"Android WebView"` -> `"Google Chrome"`.
- **Security Challenge Exemption**: Never inject ad-blocking, auto-consent, or user scripts into authentication and security challenge domains (`challenges.cloudflare.com`, `cloudflarecaptcha.com`, `/cdn-cgi/challenge-platform`).
- **DOM Stealth & Function Integrity**: Injected scripts must leave no trace a page can enumerate. Do not add `window.*` names at all (`enumerable: false` still shows in `Object.getOwnPropertyNames(window)`), and do not replace native functions (`matchMedia`, `screen.orientation.lock`, `window.open`): an own `toString` override is bypassed by `Function.prototype.toString.call(fn)`. If a native must be patched, patch it only while it is needed (see the fullscreen-only orientation bridge in `assets/scripts/content_bootstrap.js`). Theme goes through the Activity theme (`UiModeManager.setApplicationNightMode`), never through a `matchMedia` polyfill. Verify with the CDP probe in `docs/specs/2026-11-codebase-review.md` §5; `tools/check_invariants.py` enforces the basics.
- **Ad-Block List**: `app/src/main/assets/adblock_domains.txt` is generated by `tools/build_adblock_list.py` from the standard lists in `tools/filterlists/lists.json`; never edit it (or `adblock_popup_hosts.txt`, `assets/filters/**`, `assets/adblock/resources.json`) by hand: regenerate with `tools/update_filterlists.py` / `tools/build_adblock_resources.py`. The one hand-edited list is `tools/filterlists/prosto.txt` (site fixes the upstream lists lack, `"local": true` in `lists.json`): edit it there, then run `tools/update_filterlists.py --offline`. Never block a site on its own pages or the main-frame document.

### Code Hygiene & Localization
- **English-Only Documentation & Artifacts**: All project documentation, architectural decision records (ADRs), technical plans, task artifacts, code comments, and commit messages must be written strictly in English. Russian is reserved exclusively for user-facing localization resources (`res/values-ru/strings.xml`) and conversational dialogue when addressed by the user in Russian.
- **UI Localization (Zero Hardcoded Strings)**: Never hardcode user-facing strings in composables or dialogs. Every string must exist in both `res/values/strings.xml` and `res/values-ru/strings.xml`, accessed via `stringResource(R.string.*)`.
- **Zero Dead Code Policy**: Do not leave commented-out code blocks in source files. Delete dead code completely.
- **Modularity**: Keep files focused (<400–500 LOC). Separate reusable state holders, popups, and auxiliary rows into dedicated files. `tools/file_size_ratchet.py` (part of the invariants) fails a new source file over 500 lines and any file listed in `tools/file_size_baseline.json` that grows; put new code in a new file. After splitting a large file, run `python3 tools/file_size_ratchet.py --update` to lower its baseline. A file at its baseline cannot grow at all: before adding lines, extract a self-contained piece first (e.g. `engine/CssColorParser.kt` out of `JestkWebViewClient.kt`). New `BrowserUiEvent` types go in their own file next to it (e.g. `ui/viewmodel/BackgroundPlaybackEvents.kt`).
