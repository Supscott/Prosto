<div align="center">

  <img src="landing/public/favicon.svg" alt="Prosto Browser Logo" width="80" height="80" />

  # Prosto Browser

  **Browsing made simple. Tabs at your thumb.**

  An ultra-fast, gesture-driven Android browser crafted around one-handed ergonomics, tactile physics, and zero distractions.

  [![Release](https://img.shields.io/github/v/release/Supscott/Prosto?style=flat-square&color=2563EB&label=Release)](https://github.com/Supscott/Prosto/releases)
  [![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-3B82F6.svg?style=flat-square)](https://www.gnu.org/licenses/gpl-3.0)
  [![Android Min SDK](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-34D399.svg?style=flat-square)](https://developer.android.com/about/versions/oreo)
  [![APK Size](https://img.shields.io/badge/APK%20Size-~4.0%20MB-10B981.svg?style=flat-square)](https://github.com/Supscott/Prosto/releases)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7C3AED.svg?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
  [![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202025.12-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](CONTRIBUTING.md)

</div>

---

## 💡 Why Prosto?

Smartphones have grown to 6.7"+ displays, yet traditional mobile browsers still keep address bars and navigation controls at the top of the screen.

**Prosto re-architects mobile web navigation around the natural pivot arc of the thumb:**
- **100% Thumb-Reachable:** All primary navigation, tab switching, and omnibox controls live in the bottom third of the display.
- **Prosto Canvas Design:** Solid, tactile surfaces with precision micro-borders and elevation shadows — strictly **no blur** shaders wasting battery or GPU cycles.
- **Ultra-Lean Footprint:** Under ~4.0 MB APK, optimized with R8, starts instantaneously.
- **Privacy by Default:** Zero telemetry, no analytics, no phoning home.

---

## ✨ Key Features

### 🚀 Floating Gesture Dock
- **1-Swipe New Tab & Search:** Swipe up from the bottom dock to instantly open a new tab with keyboard focused.
- **Tactile Tab Reordering:** Drag and drop tabs across the dock strip with smooth spring physics.
- **Scroll-Driven Auto-Hide:** Gracefully recedes off-screen while scrolling down to maximize reading space; returns on a slight upward flick.

### 🔍 Dynamic Search Carousel & Prefixes
- **Instant Provider Switching:** Fling horizontally across search engines (Google, DuckDuckGo, Brave, Kagi, Ecosia, Startpage) directly in the omnibox.
- **Power Prefixes:** Quick-switch search engines right as you type (e.g. `g <query>` for Google, `d <query>` for DuckDuckGo, `k <query>` for Kagi).
- **Custom Providers:** Add, edit, or reorder search engines with custom URL templates.

### 🛡️ High-Performance Ad & Tracker Blocker
- **Sub-Millisecond Interception:** 48,000+ domain rules evaluated via compact hash-bucket index without stalling network threads.
- **Zero-Flicker Cosmetic Filtering:** Debounced cosmetic CSS injection and script surrogates collapse empty ad containers without page shifts.
- **Batched Privacy Statistics:** Live counters of blocked ads and trackers on your home dashboard with zero UI jank.

### 📥 Resumable Download Manager
- **Foreground Background Downloads:** Reliable downloads with pause, resume, and cancellation support.
- **Automatic Recovery:** Resumes interrupted transfers via HTTP `Range` headers and `.crdownload` staging.

---

## 📥 Download & Installation

Get the latest release directly from GitHub:

[![Download APK](https://img.shields.io/badge/Download-prosto--v0.6.0.apk-2563EB?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Supscott/Prosto/releases/download/v0.6.0/prosto-v0.6.0.apk)

> Requires **Android 8.0+ (Oreo, API 26+)**. See all versions and checksums on the [Releases page](https://github.com/Supscott/Prosto/releases).

---

## 🛠️ Building from Source

### Prerequisites
- **JDK:** OpenJDK 17 or JDK 23
- **Android SDK:** Compile SDK 35, Build Tools 35.0.0
- **Gradle:** Wrapper included

```bash
# Build debug APK
./gradlew assembleDebug

# Build optimized release APK (with R8 minification)
./gradlew assembleRelease

# Run unit and Robolectric tests
./gradlew :app:testDebugUnitTest

# Check architectural invariants (no blur, domain purity)
python3 tools/check_invariants.py
```

---

## 🏗️ Architecture & Tech Stack

Prosto follows Clean Architecture principles with Jetpack Compose:
- **UI:** Jetpack Compose with Material 3 (BOM `2025.12.00`)
- **Database:** AndroidX Room with SQLite 3.18 support
- **Engine:** Android WebKit with custom pooling and nested scroll integration
- **Networking:** OkHttp 4.12 & Coil Compose
- **Concurrency:** Kotlin Coroutines & `StateFlow`
- **Domain Purity:** The `domain/` layer is pure Kotlin with zero Android framework dependencies.

---

## 🗺️ Planned Features

- [ ] Custom ad-block filter list URL subscriptions (EasyList, uBlock origin formats)
- [ ] One-tap Desktop site request switcher
- [ ] Bookmarks and search history export/import (HTML / JSON)
- [ ] Community repository distribution (F-Droid & IzzyOnDroid)

---

## 🤝 Contributing

Contributions are welcome! Whether it's reporting a bug, proposing a new feature, or submitting a pull request:
1. Check existing [Issues](https://github.com/Supscott/Prosto/issues) or create a new one.
2. Read our [Contributing Guidelines](CONTRIBUTING.md).
3. Ensure all tests pass before submitting (`./gradlew :app:testDebugUnitTest`).

---

## 📜 License

Prosto Browser is licensed under the [GNU General Public License v3.0 (GPL-3.0)](LICENSE).
