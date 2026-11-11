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
  - Each run ends with `==> Tests: N/M passed` and one `FAIL Class.test: message` line per failure (`tools/test_summary.py`); no need to open the XML reports.
- **Visual UI Previews**: Run `rtk tools/preview.sh <TestName>` (e.g. `rtk tools/preview.sh BottomBarPreviewTest`) to render Compose previews to PNG in `build/` using Robolectric native graphics without deploying to a physical device.
- **Unit test target**: Always run `rtk ./gradlew testDebugUnitTest`. Never run bare `./gradlew test`, as `testReleaseUnitTest` fails without Robolectric release configuration.
- **Invariants**: Always verify `python3 tools/check_invariants.py` passes before finalizing changes.
- **On-Device Checks**: Use `rtk tools/device.sh <cmd>` instead of hand-written adb chains: `wake` (fails fast when the phone is locked: ask the user to unlock), `install`, `open <url>` (quotes URLs safely), `caller`, `back`, `top`, `visible`/`pages` (which WebView page is on screen, via CDP), `log <regex>`, `shot` (downscaled). Prefer `top`/`visible`/`log` over screenshots. Run `rtk tools/smoke.sh` after any change to WebView attachment, tab switching, intents or the Activity; it checks the on-screen page, the task stack and intent delivery and prints PASS/FAIL. Run `tools/device.sh sleep` when done. Never drive `com.jestk.browser` (the user's own install); the scripts refuse it.
- **Large files and refactoring**: Map a big file with `python3 tools/outline.py <file>` (members with line ranges; `--groups name=a-b ...` prints calls between groups for planning a split) and read it in parts. Review a move-only refactoring with `python3 tools/move_check.py <base> [<head>] [--sub OLD=NEW]`: moved lines cancel out, only real changes are listed. Delegate the moving itself to the `refactor-mover` agent (`.claude/agents/refactor-mover.md`, Sonnet) with a plan section and a base commit; keep design and review in the main session.
- **On-Device WebView Debugging**: See [`.agent/skills/adb-webview-testing/SKILL.md`](file:///Users/ilya/CODING/browser/.agent/skills/adb-webview-testing/SKILL.md) for ADB port forwarding, CDP WebSocket tracing, cookie/storage inspection, and headless Chrome DevTools commands.
- **DuckDuckGo Android Reference**: See [`.agent/skills/duckduckgo-reference/SKILL.md`](file:///Users/ilya/CODING/browser/.agent/skills/duckduckgo-reference/SKILL.md) for module paths, GitHub lookup recipes, and adaptation guidelines when implementing WebView edge cases, web compatibility, or privacy features.

### 3. Post-Implementation Reflection & Retrospective (Анализ работы и ретроспектива)
- After successfully implementing and verifying non-trivial fixes or features, conduct a brief retrospective analysis of the work done:
  - **Friction & Bottlenecks**: Identify what slowed down investigation or testing (e.g., ambiguous repro steps, long test feedback loops, obscure platform quirks).
  - **Mechanical Prevention**: Consider if this category of bug or regression can be prevented mechanically in the future (e.g., via `tools/check_invariants.py`, unit test invariants, or architectural seams).
  - **Process / Tooling Enhancements**: Suggest or implement concrete improvements to scripts, guidelines, or invariant checks so future tasks are faster and safer.
  - **Update LESSONS.md**: Use `python3 tools/harness_reflect.py add` to record new gotchas when relevant (observing the 10-12 active lesson budget).

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
- **DOM Stealth & Function Integrity**: Injected scripts must leave no trace a page can enumerate. Do not add `window.*` names at all (`enumerable: false` still shows in `Object.getOwnPropertyNames(window)`), and do not replace native functions (`matchMedia`, `screen.orientation.lock`, `window.open`): an own `toString` override is bypassed by `Function.prototype.toString.call(fn)`. If a native must be patched, patch it only while it is needed (see the fullscreen-only orientation bridge in `AdBlockerScript`). Theme goes through the Activity theme (`UiModeManager.setApplicationNightMode`), never through a `matchMedia` polyfill. Verify with the CDP probe in `docs/specs/2026-11-codebase-review.md` §5; `tools/check_invariants.py` enforces the basics.
- **Ad-Block List**: `app/src/main/assets/adblock_domains.txt` is generated by `tools/build_adblock_list.py` from `tools/filterlists/`; never edit it by hand. Never block a site on its own pages or the main-frame document.

### Code Hygiene & Localization
- **English-Only Documentation & Artifacts**: All project documentation, architectural decision records (ADRs), technical plans, task artifacts, code comments, and commit messages must be written strictly in English. Russian is reserved exclusively for user-facing localization resources (`res/values-ru/strings.xml`) and conversational dialogue when addressed by the user in Russian.
- **UI Localization (Zero Hardcoded Strings)**: Never hardcode user-facing strings in composables or dialogs. Every string must exist in both `res/values/strings.xml` and `res/values-ru/strings.xml`, accessed via `stringResource(R.string.*)`.
- **Zero Dead Code Policy**: Do not leave commented-out code blocks in source files. Delete dead code completely.
- **Modularity**: Keep files focused (<400–500 LOC). Separate reusable state holders, popups, and auxiliary rows into dedicated files. `tools/file_size_ratchet.py` (part of the invariants) fails a new source file over 500 lines and any file listed in `tools/file_size_baseline.json` that grows; put new code in a new file. After splitting a large file, run `python3 tools/file_size_ratchet.py --update` to lower its baseline.
