<div align="center">

  <img src="landing/public/favicon.svg" alt="Prosto Browser Logo" width="80" height="80" />

  # Prosto Browser

  **Browsing made simple. Tabs at your thumb.**

  An ultra-fast, gesture-driven Android browser crafted around one-handed ergonomics, tactile physics, and zero distractions.

  [![Release](https://img.shields.io/github/v/release/Supscott/browser?style=flat-square&color=2563EB&label=Release)](https://github.com/Supscott/browser/releases)
  [![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-3B82F6.svg?style=flat-square)](https://www.gnu.org/licenses/gpl-3.0)
  [![Android Min SDK](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-34D399.svg?style=flat-square)](https://developer.android.com/about/versions/oreo)
  [![APK Size](https://img.shields.io/badge/APK%20Size-~4.0%20MB-10B981.svg?style=flat-square)](https://github.com/Supscott/browser/releases)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7C3AED.svg?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
  [![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202025.12-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](CONTRIBUTING.md)

</div>

> **Naming:** the app ships to users as **Prosto**. "Hori" is the internal project and package
> name (`com.jestk.browser`) used by `AGENTS.md` and the release tooling.

---

## 💡 Why Prosto?

Smartphones have grown to 6.7"+ displays, yet traditional mobile browsers still stick their address bars and tab buttons into the unreachable top 30% of the screen. Users are constantly forced to shift grips, stretch thumbs, or use two hands just to open a link or switch a tab.

**Prosto re-architects mobile web navigation around the natural pivot arc of the thumb:**
- **100% thumb-reachable:** All primary navigation, tab switching, and search controls reside in the bottom third of the display.
- **Prosto Canvas design system:** Solid, tactile surfaces with precision micro-borders and elevation shadows — strictly **NO BLUR** shaders that waste CPU cycles and battery.
- **Ultra-lean footprint:** Less than ~4.0 MB APK, optimized with R8 minification, starting instantaneously without bloat.
- **Zero telemetry:** Completely private by design. No trackers, no analytics, no phoning home.

---

## ✨ Key Features

### 🚀 Floating Gesture Dock
- **1-Swipe New Tab + Search:** Swipe upward from anywhere on the floating bottom dock to instantly spawn a new tab with the search omnibox focused and the keyboard open.
- **Tactile Spring Tab Reordering:** Long-press and drag tabs directly across the bottom strip. Tabs spring into place smoothly using Jetpack Compose spring physics (`Modifier.animateItem()`).
- **Scroll-Driven Auto-Hide:** As you scroll down past the first viewport, the dock gracefully recedes off-screen to give you edge-to-edge reading space. A slight upward scroll brings it right back.

### 🔍 Dynamic Search Carousel & Power Shortcuts
- **Instant Provider Switching:** Fling horizontally across search engines right in the omnibox container (Google, DuckDuckGo, Brave, Kagi, Ecosia, Startpage).
- **Power Prefixes:** Quick-switch search engines right as you type, inspired by Alfred and Raycast:
  - `g <query>` → Google
  - `d <query>` → DuckDuckGo
  - `b <query>` → Brave
  - `k <query>` → Kagi
  - `e <query>` → Ecosia
  - `s <query>` → Startpage
- **Custom Search Providers:** Add, edit, reorder, or toggle visible search providers via URL templates (`https://example.com/search?q=%s`).

### 🛡️ Built-in Ad & Tracker Blocker
- **Network-Level Interception:** Intercepts unwanted advertising, telemetry, and tracking requests using EasyList rule matching.
- **Cosmetic CSS Injection & Scriptlet Surrogates:** Collapses empty ad containers and provides benign stubs for common ad scripts, avoiding broken layouts and video player lockups.
- **Live Privacy Statistics:** View real-time counters of blocked ads and stopped trackers right on your home dashboard.

### 📥 Resumable In-App Download Manager
- **Foreground Background Service:** Robust in-app download subsystem (`HoriDownloadService`) with notifications and cancel/pause controls.
- **Resumable Transfers:** Automatically writes partial downloads to `.crdownload` and resumes via HTTP `Range` headers.
- **Session Forwarding:** CookieJar-backed redirects allow downloading from authenticated servers and GitHub release assets.

---

## 📱 Screenshots & Visuals

```text
┌───────────────────────────────────────┐
│  Search or enter website name         │  <-- Clean Hero Input
│  ┌─────────┐ ┌─────────┐ ┌─────────┐  │
│  │ Google  │ │ DuckGo  │ │  Kagi   │  │  <-- Dynamic Carousel
│  └─────────┘ └─────────┘ └─────────┘  │
│                                       │
│  ┌─────────────────────────────────┐  │
│  │ 🛡️ 1,420 Ads  •  382 Trackers    │  │  <-- Privacy Shield
│  └─────────────────────────────────┘  │
│                                       │
│             [ Web Content ]           │
│                                       │
│    ╭─────────────────────────────╮    │
│    │ [Tab 1: Docs] (Tab 2) (+) ≡ │    │  <-- Floating Gesture Dock
│    ╰─────────────────────────────╯    │
└───────────────────────────────────────┘
```

---

## 📥 Download & Installation

### GitHub Releases (Recommended)
You can download the latest production-ready APK directly from [GitHub Releases](https://github.com/Supscott/browser/releases):

| Version | Download Link | Requirements |
| :--- | :--- | :--- |
| **Latest (v0.2.0)** | [📥 Download prosto-v0.2.0.apk](https://github.com/Supscott/browser/releases/download/v0.2.0/prosto-v0.2.0.apk) | Android 8.0+ (Oreo, API 26+) |

### F-Droid / IzzyOnDroid
> [!NOTE]
> Inclusion in community repositories (F-Droid & IzzyOnDroid) is planned. Stay tuned to our [Releases](https://github.com/Supscott/browser/releases) and [Issues](https://github.com/Supscott/browser/issues).

---

## 🛠️ Building from Source

### Prerequisites
- **JDK:** OpenJDK 17 or JDK 23 (Gradle daemon tested with toolchain JVM 23).
- **Android SDK:** Compile SDK 35, Build Tools 35.0.0.
- **Gradle:** Wrapper included (Gradle 8.14+).

### Clone & Compile

```bash
# Clone the repository
git clone https://github.com/Supscott/browser.git
cd browser

# Build debug APK
./gradlew assembleDebug

# Build optimized release APK (with R8 minification)
./gradlew assembleRelease

# The generated APK will be located at:
# app/build/outputs/apk/release/app-release-unsigned.apk
```

Local release builds are unsigned. CI signs release artifacts with the repository secret
keystore — see `.github/workflows/release.yml`.

### Running Tests

Prosto maintains comprehensive automated test coverage (600+ unit and Robolectric tests):

```bash
# Run all JVM unit and Robolectric Native Graphics visual tests
./gradlew :app:testDebugUnitTest

# Check the architectural invariants (no blur, domain purity, crash resilience)
python3 tools/check_invariants.py
```

---

## 🏗️ Architecture & Technology Stack

Prosto follows modern Android Clean Architecture principles with Jetpack Compose:

```text
com.jestk.browser/
├── JestkApplication.kt        # App lifecycle & container initialization
├── di/AppContainer.kt         # Clean dependency injection container
├── data/
│   ├── local/                 # Room Database (v8) with SQLite 3.18 support
│   ├── mapper/                # Entity <-> Domain model mappings
│   └── repository/            # History, Favorites, Tabs, and Settings repositories
├── domain/                    # Clean contracts and domain models
├── engine/
│   ├── AdBlockerEngine.kt     # EasyList blocking, path filters & cosmetic rules
│   ├── FaviconManager.kt      # Domain-aware persistent favicon cache (zero flicker)
│   ├── JestkWebViewClient.kt  # Chromium WebKit navigation & interception
│   ├── WebViewPool.kt         # Reusable instance lifecycle pool
│   └── download/              # Foreground resumable download service
└── ui/
    ├── MainActivity.kt        # Single activity container
    ├── navigation/            # Floating Dock, ActionDock, ActiveTabPill, TabStrip
    ├── overlays/              # SearchOverlay, ProviderCarousel, ContextMenus
    ├── screens/               # TabOverviewGrid, SettingsScreen, DownloadsScreen
    ├── theme/                 # Prosto Canvas design system (Strictly NO BLUR)
    └── viewmodel/             # BrowserViewModel with specialized Coordinators
```

- **UI:** Jetpack Compose with Material 3 (BOM `2025.12.00`).
- **Database:** AndroidX Room `2.6.1` with KSP.
- **Engine:** Android WebKit with custom pooling and nested scroll integration.
- **Networking & Images:** OkHttp 4.12.0, Coil Compose 2.7.0.
- **Concurrency:** Kotlin Coroutines & `StateFlow` / `SharedFlow`.

The `domain/` layer is framework-free: it must not import anything from `android.*`, and both
`tools/check_invariants.py` and `DomainArchitectureTest` enforce that on every build.

---

## 🗺️ Roadmap

- [x] Floating bottom gesture dock with 1-swipe search and spring drag-and-drop tab reordering.
- [x] Prosto Canvas design system with solid tactile surfaces.
- [x] Integrated ad and tracker blocking with live statistics.
- [x] In-app resumable background download manager.
- [x] Dynamic search engine carousel and prefix shortcuts.
- [ ] Custom ad-block filter list URL subscriptions (EasyList, uBlock origin formats).
- [ ] One-tap Desktop site request switcher.
- [ ] Export and import of bookmarks and search history (HTML / JSON).
- [ ] Submission to F-Droid and IzzyOnDroid.

---

## 🤝 Contributing

Contributions are welcome! Whether it's reporting a bug, proposing a new feature, or submitting a pull request:

1. Check existing [Issues](https://github.com/Supscott/browser/issues) or create a new one.
2. Read our [Contributing Guidelines](CONTRIBUTING.md).
3. Ensure all tests pass before submitting (`./gradlew :app:testDebugUnitTest`).
4. Follow the existing Kotlin and Compose code style.

---

## 📜 License

Prosto Browser is licensed under the [GNU General Public License v3.0 (GPL-3.0)](LICENSE).  
You are free to use, modify, study, and distribute this software under the terms of the GPL-3.0.

---

<div align="center">
  Crafted with care for your thumb. If you enjoy using Prosto, give us a ⭐ on GitHub!
</div>
