# Prosto Browser — Product & Landing Specification (SPEC.md)

This document specifies the core product philosophy, features, design system, and the web landing architecture for **Prosto**, the open-source thumb-first Android browser.

---

## 1. Product Overview & Vision

- **Product Name:** Prosto (package: `com.jestk.browser`)
- **Platform:** Android (minSdk: 26 / Android 8.0 Oreo, compileSdk: 35 / Android 15)
- **Tagline:** *"Browsing made simple. Tabs at your thumb."*
- **Primary Distribution:** GitHub Releases (`.apk`), Open Source (MIT License).
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
     - Replaces the 4-step friction cycle of traditional browsers (open tabs -> tap "+" -> tap URL bar -> type).
  2. **Tactile Spring Tab Reordering:**
     - Long-press and drag tabs directly within the dock.
     - Powered by Compose `Animatable` spring physics (`Spring.StiffnessMedium`, `Spring.DampingRatioLowBouncy`).
     - Neighbor tabs smoothly glide into new positions using `Modifier.animateItem()`.
  3. **Scroll-Driven Auto-Hide:**
     - Dock smoothly recedes off-screen on downward scroll below the first viewport height (`scrollY > viewportHeight`).
     - Content expands edge-to-edge while respecting system navigation bar insets.
     - Scrolling up immediately reveals the dock with 0ms latency.

### 2.2 Dynamic Search Engine Carousel
- **Description:** Real-time provider switcher built directly into the omnibox container.
- **Key Interactions:**
  - **Horizontal Fling:** Flicking horizontally across the carousel smoothly switches providers (Google, DuckDuckGo, Bing, Kagi, Yandex, Brave, Ecosia).
  - **Center Scaling & Focus Dimming:**
    - Active center provider dynamically scales to `1.15f` with full `1.0f` opacity, an accent rim, and an indicator pill dot.
    - Adjacent inactive providers smoothly scale down and dim to `0.45f` alpha.
  - **Custom Search Providers:**
    - Full CRUD support in Settings for custom engines using URL templates (e.g. `https://example.com/search?q=%s`).

### 2.3 Terminal-Speed Prefix Shortcuts
- **Description:** Quick-routing search prefixes inspired by power-user tools like Raycast and Alfred.
- **Default Shortcuts:**
  - `g <query>` → Google Search
  - `d <query>` → DuckDuckGo (Privacy)
  - `b <query>` → Bing Search
  - `k <query>` → Kagi Search
- **Behavior:**
  - Typing a recognized letter prefix followed by a space automatically snaps the engine indicator and routes the query directly to that provider upon submission.
  - Users can assign custom single-letter or short prefixes to any added search engine.

### 2.4 Silent Privacy Shield & Home Statistics
- **Engine:** Built-in ad and tracker blocking engine using compiled EasyList rulesets.
- **Smart Blocked Responses:**
  - Neutralizes blocked requests intelligently rather than returning 0-byte failures:
    - Blocked images → 1×1 transparent PNG.
    - Blocked beacons/analytics → HTTP 204 No Content.
    - Blocked scripts → Benign surrogate mocks (AdSense, Google Publisher Tags, ADFOX).
    - Prevents HTML5 video players and SPAs from hanging or throwing uncaught network errors.
- **Live Home Screen Statistics:**
  - Tactile `PrivacyProtectionCard` on the home screen displaying atomic counts of:
    - Total Ads Blocked.
    - Total Trackers Stopped.

### 2.5 Design System: Serene Canvas (STRICT NO BLUR)
- **Philosophy:** Clarity, speed, and spatial calm.
- **Strict Invariant:**
  - Completely purged runtime blur (`Modifier.blur()` / Haze shaders).
  - Blurring live WebViews forces Chromium's asynchronous hardware draw functor into offscreen `RenderNode` loops, causing severe screen flicker and frame drops on Android 12+ (SDK 31+).
- **Surfaces:**
  - Tactile solid surfaces with specular micro-borders (`SereneBorders.hairlineBorder`) and ambient dispersion drop shadows.
  - Smooth 120 FPS frame rate on any display refresh rate with low battery consumption.

### 2.6 Native Engineering & Performance
- **Language & Runtime:** Kotlin (JVM 17), Kotlin Coroutines, StateFlow.
- **UI Toolkit:** Jetpack Compose Material 3 (BOM 2025).
- **Storage:** AndroidX Room Database (v8 schema) with SQLite history deduplication.
- **Web Engine:** Managed WebView LRU pool with isolated lifecycle handling and zero context leaks.
- **Downloads:** In-app foreground service with resumable `.crdownload` chunks, CookieJar redirect sharing, and RFC-compliant filename resolution.
- **Binary Footprint:** ~4.0 MB release APK with full R8 minification and resource shrinking.
- **Test Suite:** 417 unit & Robolectric Native Graphics tests passing 100% green.

---

## 3. Landing Page Specifications (`landing/`)

### 3.1 Technology Stack
- **Framework:** [Astro v7+](https://astro.build) (Static Site Generation / Island Architecture).
- **Styling:** [Tailwind CSS v4](https://tailwindcss.com) with `@tailwindcss/vite`.
- **Typography:** Plus Jakarta Sans (Primary UI) and JetBrains Mono (Code/Shortcuts).
- **Performance Target:** 100/100 Google Lighthouse scores across Performance, Accessibility, Best Practices, and SEO.

### 3.2 Page Architecture & Component Map

```text
landing/
├── public/
│   └── favicon.svg                  # Compass-capsule geometric SVG icon
├── src/
│   ├── layouts/
│   │   └── Layout.astro             # Root HTML, SEO meta, Fonts, Theme init script, Background mesh
│   ├── components/
│   │   ├── Navbar.astro             # Sticky header, Brand, Nav anchors, Theme toggle, CTAs
│   │   ├── Hero.astro               # Core hook, metric badges, CTA buttons, Simulator host
│   │   ├── DeviceSimulator.astro    # Interactive phone mockup with 4 feature test scenarios
│   │   ├── ThumbReach.astro         # Ergonomic visual heatmap comparison (Danger Zone vs Thumb Arc)
│   │   ├── FeaturesBento.astro      # 5-card Bento grid (Dock, Carousel, Shortcuts, Shield, Serene Canvas)
│   │   ├── TechSpecs.astro          # Engineering deep dive (Compose, WebKit, Room, Downloads, Tests, 4MB)
│   │   ├── DownloadSection.astro    # Direct GitHub Releases APK download card & system specs
│   │   └── Footer.astro             # Brand info, repository links, license
│   ├── pages/
│   │   └── index.astro              # Single-page layout composition
│   └── styles/
│       └── global.css               # Serene Canvas design tokens, animations, custom utilities
├── astro.config.mjs                 # Astro configuration with Tailwind v4 Vite plugin
├── package.json                     # Dependencies & build scripts
├── README.md                        # Quickstart & build instructions
└── SPEC.md                          # This document
```

### 3.3 Interactive Device Simulator (`DeviceSimulator.astro`)
The centerpiece of the landing page is an interactive mobile simulator allowing users to experience the browser's ergonomics before downloading:

1. **Scenario 1 — 1-Swipe New Tab:**
   - Visualizes the home screen and Serene Canvas dock.
   - Users can drag or click the dock upwards to trigger the search state with a single gesture.
2. **Scenario 2 — Engine Carousel:**
   - Features the horizontal engine strip with Google, DuckDuckGo, Bing, and Kagi.
   - Clicking or scrolling engines triggers real-time center scaling (`1.15x`), active accent dot visibility, and query URL preview switching.
3. **Scenario 3 — Prefix Shortcuts:**
   - Interactive omnibox allowing live typing or clicking shortcut pills (`g `, `d `, `b `, `k `).
   - Dynamically highlights active engine badge or gracefully falls back to a neutral search state on deletion/unrecognized text.
4. **Scenario 4 — Privacy Shield:**
   - Visualizes the home screen's `PrivacyProtectionCard`.
   - Real-time counters showing blocked ads (1,482) and stopped trackers (419), explaining smart response surrogates.

### 3.4 Serene Canvas Color Mapping

| Design Token | Light Theme (`:root`) | Dark Theme (`html.dark`) | Android Engine Equivalent |
| :--- | :--- | :--- | :--- |
| **Canvas Floor** (`--bg-canvas`) | `#F1F7FC` | `#080F19` | `SereneCanvasFloor` / `DarkCanvasBase` |
| **Card Surface** (`--bg-card`) | `#FFFFFF` | `#1E293B` | `SereneCardSurface` / `DarkSurface` |
| **Elevated Card** (`--bg-card-elevated`) | `#F2F3FF` | `#243247` | `SereneSurfaceContainerLow` / `DarkSurfaceVariant` |
| **Dock Surface** (`--bg-dock`) | `rgba(255, 255, 255, 0.94)` | `rgba(13, 24, 36, 0.94)` | `DarkDockSurface` (80-94% opacity) |
| **Primary Brand** (`--brand-primary`) | `#2271B3` | `#2271B3` | `SereneBrandPrimary` |
| **Accent Glow** (`--brand-accent`) | `#60A5FA` | `#60A5FA` | `SereneAccent` / `DarkPrimary` |
| **Primary Text** (`--text-primary`) | `#0F172A` | `#F8FAFC` | `SereneDarkText` / `DarkTextHighContrast` |
| **Secondary Text** (`--text-secondary`)| `#414750` | `#94A3B8` | `SereneOnSurfaceVariant` / `DarkOnSurfaceVariant` |
| **Muted Meta** (`--text-muted`) | `#64748B` | `#64748B` | `SereneMutedSlate` |

### 3.5 Development & Build Commands

```bash
# Navigate to landing directory
cd landing

# Install dependencies
pnpm install

# Start development server (http://localhost:4321)
pnpm dev

# Build production static bundle (./dist/)
pnpm build

# Preview production build locally
pnpm preview
```

---

## 4. Open-Source Launch Roadmap

1. **Phase 1 — Landing Page Deployment:**
   - Host `landing/dist/` via GitHub Pages or Cloudflare Pages.
   - Point custom domain or GitHub repository homepage to the site.
2. **Phase 2 — GitHub Release v1.0.0:**
   - Build signed, release-minified `prosto-v1.0.0.apk`.
   - Publish GitHub Release with checksums (SHA-256) and release notes.
3. **Phase 3 — Open Source Community:**
   - Maintain `CONTRIBUTING.md`, issue templates, and automated PR preview workflows.
   - Expand package index to F-Droid repository.
