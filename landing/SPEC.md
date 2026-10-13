# Prosto Browser — Product & Landing Specification (SPEC.md)

This document specifies the core product philosophy, features, design system, and the web landing architecture for **Prosto**, the open-source thumb-first Android browser.

---

## 1. Product Overview & Vision

- **Product Name:** Prosto (package: `com.jestk.browser`)
- **Platform:** Android (minSdk: 26 / Android 8.0 Oreo, compileSdk: 35 / Android 15)
- **Tagline:** *"Browsing stripped to pure speed. Every action in a single swipe."*
- **Primary Distribution:** GitHub Releases (`.apk`), Open Source (GNU GPL v3.0).
- **Core Motivation:**
  Modern smartphones have grown past 6.5–6.8 inches. Traditional mobile browsers (Chrome, Firefox, Safari) keep their primary controls (address bar, tab switchers, new tab buttons) in the top 30% of the screen. Users must constantly shift their grip, stretch their thumbs, or use two hands.
  
  **Prosto re-architects the browser around the natural pivot arc of the thumb:**
  - 100% of primary controls are anchored in the bottom third of the display.
  - Zero multi-step menu diving for frequent actions.
  - Ultra-lightweight footprint (~4.0 MB APK).
  - Tactile, distraction-free Serene Canvas interface without battery-draining blur shaders.

---

## 2. Browser Feature Specifications

### 2.1 The Floating Gesture Dock (Core Feature)
- **Description:** A persistent bottom capsule floating comfortably above the Android navigation bar.
- **Key Interactions:**
  1. **1-Swipe New Tab + Search:**
     - Swiping upwards from anywhere on the dock immediately opens a new tab and focuses the search omnibox with the software keyboard visible.
  2. **Tactile Spring Tab Reordering:**
     - Long-press and drag tabs directly within the dock with spring physics.
     - Displaced neighboring tabs smoothly glide into new positions using `Modifier.animateItem()`.
  3. **Scroll-Driven Auto-Hide:**
     - Dock smoothly recedes off-screen on downward scroll below the first viewport height (`scrollY > viewportHeight`).
     - Scrolling up immediately reveals the dock with 0ms latency.

### 2.2 Cross-Engine Comparison (Floating Switcher)
- **Description:** Floating search engine switcher bar that appears directly above the dock on search results pages.
- **Key Interactions:**
  - Re-executes the user's exact query across Google, DuckDuckGo, Yandex, Bing without retyping a single character.
  - Customizable provider visibility and order with quick toggle popups.

### 2.3 Silent Privacy Shield & EasyList AdBlock
- **Engine:** Built-in ad and tracker blocking engine using compiled EasyList rulesets.
- **Smart Blocked Responses:**
  - 1×1 transparent PNG for blocked images.
  - HTTP 204 No Content for beacons and telemetry.
  - Benign surrogate mocks for tracking scripts.

### 2.4 Pure Architecture (4 MB)
- **Engine:** Hardware-accelerated Android WebKit (uses system WebView pool).
- **Footprint:** ~4.0 MB APK uncompressed, ~45 MB RAM baseline (compared to 190-250 MB Chromium packages).
- **Zero Telemetry:** Exactly 0 third-party analytics or tracker SDKs.

---

## 3. Landing Page Specifications (`landing/`)

### 3.1 Design Philosophy & Aesthetics
- **Style:** Hybrid Helium + Arc Search.
  - High-contrast, typography-driven speed manifesto with interactive tactile feature demonstrations.
  - Strict Dark-Only: deep obsidian / cosmic void (`#060913`, `#0a0f1d`), laser-sharp micro-borders (`rgba(255,255,255,0.08)`), electric cyan accents (`#38bdf8`), and Plus Jakarta Sans + JetBrains Mono typography.
  - Strictly NO BLUR (matching the project's Serene Canvas invariant).
- **Language:** English only.

### 3.2 Page Architecture & Component Map

```text
landing/
├── public/
│   └── favicon.svg                  # Compass-capsule geometric SVG icon
├── src/
│   ├── layouts/
│   │   └── Layout.astro             # Root HTML, SEO meta, strict dark mode, Fonts
│   ├── components/
│   │   ├── Navbar.astro             # Minimalist header with 4 MB badge, anchors, and APK download CTA
│   │   ├── Hero.astro               # Centered punchy hero with floating interactive dock & metrics
│   │   ├── FeaturesBento.astro      # 3 Super Bento Cards (Dock Gestures, Cross-Engine Loop, AdBlock 4 MB)
│   │   ├── TechSpecs.astro          # Helium-style engineering metrics and architecture comparison
│   │   ├── DownloadSection.astro    # High-converting APK download card with SHA-256 and release tags
│   │   ├── Footer.astro             # Clean repository links, license, and copyright
│   │   └── MediaPlaceholder.astro   # Standardized 'photo' and 'video' placeholders with exact aspect ratios
│   ├── pages/
│   │   └── index.astro              # Single-page layout composition
│   └── styles/
│       └── global.css               # Strict dark design tokens, micro-borders, and tactile utilities
├── astro.config.mjs                 # Astro configuration with Tailwind v4 Vite plugin
├── package.json                     # Dependencies & build scripts
├── README.md                        # Quickstart & build instructions
└── SPEC.md                          # This document
```

### 3.3 Media Placeholders (`MediaPlaceholder.astro`)
Reusable placeholder component for screenshots and screen recordings with exact proportions:
- `type`: `'photo' | 'video'`
- Default aspect ratios: `9/16` for mobile screen recordings, `16/10` for desktop/wide views.
- Visuals: Dark-tech grid pattern, glowing outline, play/camera icon, electric cyan badge, descriptive title and dimensions.

### 3.4 Build Commands

```bash
# In landing directory
pnpm build
```
