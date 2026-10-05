# AGENTS.md — Agent Guidelines for Hori

Welcome to the **Hori** project. This file provides critical operating guidelines, architectural standards, and workflow requirements for all AI agents working on this codebase.

---

## 1. Project Overview

- **Project Name:** Hori (package: `com.jestk.browser`)
- **Platform:** Android (minSdk: 26, targetSdk: 35, compileSdk: 35)
- **Description:** Hori is a fast, fluid, and gesture-driven Android web browser. Key features include:
  - Custom gesture-oriented bottom navigation bar and action dock.
  - Serene Canvas design system (NO BLUR).
  - Built-in ad-blocking engine utilizing EasyList rulesets.
  - Multi-tab management (visual grid, swipe-to-dismiss, persistent recent tabs).
  - Fullscreen video playback with Picture-in-Picture (PiP) support.
  - Room-backed local storage for tabs, search history, favorites, and downloads.

---

## 2. Core Language & Tech Stack

- **Language:** Kotlin (JVM Target 17).
- **UI Framework:** Jetpack Compose (Material 3, Compose BOM `2025.12.00`).
- **Styling & Effects:** Compose Material 3 (Serene Canvas, NO BLUR) with precision micro-borders and elevation shadows.
- **Asynchronous / Concurrency:** Kotlin Coroutines (`kotlinx.coroutines`) & `StateFlow` / `SharedFlow`.
- **Database & Persistence:** AndroidX Room (`2.6.1`) with KSP annotation processing.
- **Web Engine:** Android WebKit (`WebView`, `WebChromeClient`, `WebViewClient`) with custom pooling and lifecycle management.
- **Image Loading:** Coil Compose (`io.coil-kt:coil-compose:2.7.0`, `coil-svg`).

---

## 3. Documentation & Language Policy

- **All project documentation must be written in English.**
- This includes:
  - Source code comments, KDoc docstrings, and commit messages.
  - Markdown documentation under `docs/` and root `.md` files.
  - Entries in `docs/memory/MEMORY.md` and `docs/memory/LESSONS.md`.

---

## 4. Mandatory Pre-Flight Checklist (Start of Every Session)

> [!IMPORTANT]
> **BEFORE writing code or planning any implementation, every agent MUST:**
>
> 1. Read [`docs/memory/MEMORY.md`](file:///Users/ilya/CODING/browser/docs/memory/MEMORY.md) to understand current progress, active goals, and recent changes.
> 2. Read [`docs/memory/LESSONS.md`](file:///Users/ilya/CODING/browser/docs/memory/LESSONS.md) to review past pitfalls, bugs, and verified solutions.

Never skip this step — it ensures continuity across separate agent sessions and prevents regressions.

---

## 5. Project Structure

```text
/Users/ilya/CODING/browser/
├── AGENTS.md                                   # This agent guidelines document
├── DESIGN.md                                   # Visual style guide & design tokens
├── build.gradle.kts                            # Root build script
├── settings.gradle.kts                         # Module & repository declarations
├── gradle.properties                           # JVM and Android build flags
├── gradlew, gradlew.bat                        # Gradle wrapper binaries
├── app/
│   ├── build.gradle.kts                        # App module dependencies & SDK config
│   ├── proguard-rules.pro                      # R8 / ProGuard rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml             # Permissions, activities, file providers
│       │   ├── assets/                         # EasyList rules, adblock hosts, search engine icons
│       │   ├── res/                            # Android drawables, mipmaps, strings, colors, xml
│       │   └── java/com/jestk/browser/
│       │       ├── JestkApplication.kt         # Application entry point & container setup
│       │       ├── data/                       # Data layer
│       │       │   ├── local/                  # Room Database, entities, and DAOs
│       │       │   ├── mapper/                 # Entity <-> Domain mappers
│       │       │   └── repository/             # Repository implementations
│       │       ├── di/                         # Dependency injection (AppContainer)
│       │       ├── domain/                     # Business logic contracts
│       │       │   ├── model/                  # Domain models (Tab, DownloadItem, ThemeMode, etc.)
│       │       │   └── repository/             # Repository interfaces
│       │       ├── engine/                     # Web engine & network interception
│       │       │   ├── AdBlockerEngine.kt      # Content blocking & rule matching
│       │       │   ├── ColorSchemeScript.kt    # Web dark mode / theme scripts
│       │       │   ├── FaviconManager.kt       # Favicon fetching & caching
│       │       │   ├── JestkWebChromeClient.kt # WebChromeClient callbacks (progress, fullscreen)
│       │       │   ├── JestkWebViewClient.kt   # WebViewClient navigation & ad interception
│       │       │   ├── NestedWebView.kt        # Scroll-aware nested WebView
│       │       │   ├── SnapshotEngine.kt       # Tab thumbnail capturing
│       │       │   └── WebViewPool.kt          # WebView reuse & lifecycle pool
│       │       ├── ui/                         # Presentation layer (Jetpack Compose)
│       │       │   ├── MainActivity.kt         # Single activity host
│       │       │   ├── JestkApp.kt             # Top-level composable & navigation coordinator
│       │       │   ├── navigation/             # UnifiedBottomBar, ActionDock, ActiveTabPill, TabStrip
│       │       │   ├── overlays/               # Search overlay, sheets, context menus
│       │       │   ├── pip/                    # Picture-in-Picture lifecycle handling
│       │       │   ├── screens/                # Full-screen composables (Tabs, Downloads, Settings)
│       │       │   ├── theme/                  # Colors, Typography, Shapes, Themes
│       │       │   └── viewmodel/              # BrowserViewModel, UiState, UiEvents
│       │       └── util/                       # Helpers (DefaultBrowserHelper, etc.)
│       └── test/java/com/jestk/browser/        # Test suites (Unit & Visual)
│           ├── data/                           # Room DAO & Repository unit tests
│           ├── engine/                         # AdBlocker, Favicon, Script, & Client tests
│           ├── ui/                             # Visual preview tests & ViewModel tests
│           └── util/                           # Utility tests
└── docs/
    ├── memory/
    │   ├── MEMORY.md                           # Continuous agent state & work log
    │   └── LESSONS.md                          # Bug post-mortems and preventative lessons
    └── plans/                                  # Task specifications and implementation plans
```

---

## 6. Testing Strategy: Logic & Visual Coverage

Every new feature or bugfix must be covered by tests. Both business logic and UI appearance must be tested.

### 6.1 Logic Testing (JVM Unit Tests)

- **Scope:** ViewModels, Repositories, Room DAOs, Data Mappers, Browser Engine (`AdBlockerEngine`, `FaviconManager`, `ColorSchemeScript`, `JestkWebViewClient`, etc.).
- **Tools:** JUnit 4, Google Truth (`assertThat`), `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`), AndroidX Core Testing (`InstantTaskExecutorRule`), Room in-memory DB (`Room.inMemoryDatabaseBuilder`).
- **Requirement:** Ensure edge cases, state transitions, coroutine cancellation, and error cases are covered.

### 6.2 Visual & UI Testing (Previews & Screenshots)

- **Scope:** Composable layouts, bottom dock states, overlays, modals, responsive handling, and theme variations (Light / Dark).
- **Tools:** Robolectric Native Graphics (`@GraphicsMode(GraphicsMode.Mode.NATIVE)` + `@Config(sdk = [34])`), Jetpack Compose Previews, and the Compose Preview rendering plugin.
- **Requirement:**
  - Verify that visual layouts adhere to [`DESIGN.md`](file:///Users/ilya/CODING/browser/DESIGN.md) (e.g. Serene Canvas color tokens and dimensions).
  - Use Robolectric to render composables to PNG bitmaps when verifying complex UI states or visual regressions (see existing tests in `app/src/test/java/com/jestk/browser/ui/navigation/BottomBarPreviewTest.kt`).

---

## 7. Continuous Context Protocols

### 7.1 Memory Log — [`docs/memory/MEMORY.md`](file:///Users/ilya/CODING/browser/docs/memory/MEMORY.md)

At the conclusion of each task or work session, the agent **must** append a concise summary to `/Users/ilya/CODING/browser/docs/memory/MEMORY.md`:

- **What was completed:** High-level list of features, refactors, or fixes delivered.
- **Current State:** Current working branch or commit state, open questions, and next steps.
- **Key Architectural Decisions:** Any critical decisions that future agents need to know.

### 7.2 Lessons Learned — [`docs/memory/LESSONS.md`](file:///Users/ilya/CODING/browser/docs/memory/LESSONS.md)

Whenever a bug is encountered or fixed, the agent **must** log an entry into `/Users/ilya/CODING/browser/docs/memory/LESSONS.md`:

- **Bug Description:** What symptom or error occurred?
- **Root Cause:** How and why did the issue arise?
- **Fix & Prevention:** How was it fixed, and what pattern should future agents follow to avoid reintroducing it?
