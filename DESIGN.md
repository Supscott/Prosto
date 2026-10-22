---
name: Prosto Canvas
colors:
  surface: "#ffffff"
  surface-dim: "#d2ddda"
  surface-bright: "#ffffff"
  surface-container-lowest: "#ffffff"
  surface-container-low: "#f2faf7"
  surface-container: "#ecf5f2"
  surface-container-high: "#e6efec"
  surface-container-highest: "#dee9e6"
  on-surface: "#171d1b"
  on-surface-variant: "#3f4947"
  inverse-surface: "#2b3230"
  inverse-on-surface: "#eff1f0"
  outline: "#6f7977"
  outline-variant: "#bec9c6"
  surface-tint: "#087e78"
  primary: "#087e78"
  on-primary: "#ffffff"
  primary-container: "#a5f2e9"
  on-primary-container: "#00201d"
  inverse-primary: "#56dbcf"
  secondary: "#4a6360"
  on-secondary: "#ffffff"
  secondary-container: "#cce8e3"
  on-secondary-container: "#06201d"
  tertiary: "#416277"
  on-tertiary: "#ffffff"
  tertiary-container: "#c4e8ff"
  on-tertiary-container: "#001e31"
  error: "#ba1a1a"
  on-error: "#ffffff"
  error-container: "#ffdad6"
  on-error-container: "#93000a"
  primary-fixed: "#a5f2e9"
  primary-fixed-dim: "#83d5cb"
  on-primary-fixed: "#00201d"
  on-primary-fixed-variant: "#003732"
  secondary-fixed: "#cce8e3"
  secondary-fixed-dim: "#b1ccc8"
  on-secondary-fixed: "#06201d"
  on-secondary-fixed-variant: "#1d3532"
  tertiary-fixed: "#a5f2e9"
  tertiary-fixed-dim: "#83d5cb"
  on-tertiary-fixed: "#00201d"
  on-tertiary-fixed-variant: "#003732"
  background: "#e9f6f2"
  on-background: "#171d1b"
  surface-variant: "#dee9e6"
typography:
  headline-xl:
    fontFamily: Manrope
    fontSize: 32px
    fontWeight: "700"
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Manrope
    fontSize: 26px
    fontWeight: "700"
    lineHeight: 34px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Manrope
    fontSize: 20px
    fontWeight: "600"
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Manrope
    fontSize: 17px
    fontWeight: "600"
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: "400"
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: "400"
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: "400"
    lineHeight: 18px
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: "600"
    lineHeight: 16px
    letterSpacing: 0.04em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: "500"
    lineHeight: 14px
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 0.75rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.25rem
  space-xl: 2rem
---

## Brand & Style

The design system establishes a tranquil, distraction-free gateway to the internet. Built around the ethos of clarity, breathing room, and weightless interaction, the mobile interface translates the calm workspace of the desktop product into an ultra-ergonomic touch-first experience.

Targeted at knowledge workers, curators, and mindful internet users, the system evokes mental clarity, effortless flow, and spatial calm.

The visual style blends **Modern Minimalism** with **Glassmorphism and Tactile Airiness**:

- Pure, soft backgrounds infused with calm mint undertones.
- Elevated floating card modules with soft atmospheric dispersion shadows.
- Distinct pill-shaped controls designed for fluid thumb interactions.
- Minimal visual chrome that recedes naturally to prioritize the user's content.

## Colors

The palette is tuned around balanced teal tones and airy mint-washed neutrals:

- **Primary (`#087E78`)**: Deep teal used for active selections, interactive icons, prominent buttons, and branded moments.
- **Accent (`#14A79D`)**: Bright jade tone used for focus rings, hover indicators, active badges, and subtle glows.
- **Surface / Background (`#E9F6F2`)**: An atmospheric, very pale mint that acts as the primary canvas floor, softening contrast compared to stark white.
- **Canvas / Cards (`#FFFFFF`)**: Pure crisp white reserved for elevated cards, modal sheets, and the floating dock bar.
- **Dark Text (`#171D1B`)**: Deep ink-teal for crisp typography with superior readability.
- **Muted Slate (`#5F6E6C`)**: Secondary text, tab metadata, inactive icons, and structural line dividers.

### Dark Theme Palette (Figma Reference)

The dark mode palette adopts deep teal-black tones and crisp slate contrasts:

- **Canvas Floor (`#08100F`)**: Deep teal-black background base for the canvas viewport.
- **Dock Base (`#0C1A18`)**:
  - Dock Container: `#0C1A18` at **80% opacity** (`rgba(12, 26, 24, 0.80)`) with elevation shadow.
  - Active Tab Capsule: `#0C1A18` at **100% opacity** (`#0C1A18`) with light elevation shadow.
- **Card Surface (`#171D1C`)**: Solid elevated surface for cards, menus, and sheets.
- **Text & Foreground (`#DEE4E2`)**: Crisp off-white for primary headlines and active tab titles.
- **Secondary Slate (`#BEC9C6`)**: Secondary labels, inactive icons, and metadata in dark mode.
- **Muted Slate (`#5F6E6C`)**: Structural dividers and borders.
- **Accent Teal (`#00504A`)**: Deep teal container/accent tint.
- **Primary Jade (`#56DBCF`)**: Primary actions and interactive highlights.
- **Accent Light Jade (`#A5F2E9`)**: Focused rings, badges, and glows.

### Hierarchy & Tint Rules

- Floating Dock Bar:
  - Light theme: `#FFFFFF` at **80% opacity** (`rgba(255, 255, 255, 0.80)`) with tactile elevation shadow.
  - Dark theme: `#0C1A18` at **80% opacity** (`rgba(12, 26, 24, 0.80)`) with tactile elevation shadow.
- Active Tab Capsule:
  - Light theme: `#FFFFFF` at **100% opacity** with light elevation shadow (2.dp), no outline.
  - Dark theme: `#0C1A18` at **100% opacity** with light elevation shadow (2.dp), no outline.
  - Favicon rendered directly without background squircle container.
  - Title styled in **bold** typography.

## Typography

The design system pairs **Manrope** for titles with **Inter** for body text. Manrope brings contemporary, rounded geometry and open counters to headings; Inter keeps body copy and labels highly legible at small sizes with its neutral, engineered rhythm.

- **Scale & Weight**:
  - `headline-xl` is reserved for central home screen greetings ("Where to next?").
  - `headline-sm` anchors section titles such as "Favorite Spaces" and "Recently Closed".
  - Section subtitles and meta counters use uppercase tracking with `label-md`.
  - Content URLs and secondary timestamps render in `body-sm` using `#5F6E6C`.

## Layout & Spacing

Mobile layouts follow a relaxed fluid column structure bounded by safe zones:

- **Horizontal Canvas Margin**: 16px (`1rem`) on standard mobile displays, expanding to 24px (`1.5rem`) on compact tablets or foldable unfolded screens.
- **Vertical Rhythm**:
  - Sections are spaced 32px (`space-xl`) apart to maintain the calm, expansive feel seen on desktop.
  - Interactive grid elements (e.g., Favorite Spaces tiles) utilize a 2-column or 4-column layout with 12px (`gutter`) gaps.
  - The tab overview grid uses a 3-column layout with 10px (`gutter`) gaps; preview cards keep the 4:5 aspect ratio.
- **Floating Dock Clearance**: Bottom page content always reserves 96px of inset scrolling padding (`space-xl` + dock clearance) to avoid obstruction by the bottom navigation bar and home gesture bar.

## Elevation & Depth

Visual hierarchy uses multi-layered ambient light, avoiding harsh dropshadows or heavy outlines:

- **Base Layer (Level 0)**: Tone `#E9F6F2`, flat canvas foundation.
- **Surface Layer (Level 1 - Cards & Lists)**: Pure white `#FFFFFF` with an ambient diffused shadow:
  - `box-shadow: 0 4px 20px -2px rgba(23, 29, 27, 0.04), 0 2px 6px -1px rgba(23, 29, 27, 0.02);`
  - Subtle micro-border: `1px solid rgba(220, 230, 227, 0.8)`.
- **Floating Controls (Level 2 - Omnibox, Modals, Action Dock)**:
  - Background: `rgba(255, 255, 255, 0.92)` with `backdrop-filter: blur(20px) saturate(180%)`.
  - Ambient elevation shadow: `box-shadow: 0 12px 36px -4px rgba(8, 126, 120, 0.12), 0 4px 12px rgba(23, 29, 27, 0.06);`
  - Rim highlight border: `1px solid rgba(255, 255, 255, 0.8)`.

## Shapes

The interface embraces a continuous **pill and ultra-soft shape hierarchy** (`roundedness: 3`):

- **Pills (`9999px`)**: Omnibar search fields, tab switchers, category chips, primary FABs, and floating dock bars.
- **Cards & Sheets (`24px` to `28px`)**: Quick-dial space tiles, context menus, and bottom modal sheets.
- **App Icons / Favicons (`14px` to `16px`)**: Rounded squircle containers within favorite grid items.

## App Icon

The launcher identity is the brand swirl from `app/src/main/assets/icon`:

- **Source of truth**: `app/src/main/assets/icon/app-icon.svg` - the teal `#087E78` mark on a `#E9F6F2` mint tile with `rx=20`.
- **Adaptive icon**: `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` use a `#E9F6F2` background layer plus `res/drawable/ic_launcher_foreground.xml`.
  The mark is placed at **57.1% of the visible 72dp launcher mask** - the same proportion it has on the mint tile - so the logo never fills the icon.
- **Legacy mipmaps**: the density PNGs under `res/mipmap-*/` are faithful renders of `app-icon.svg` with a transparent outside.
  `qlmanage -t` composites SVG thumbnails on opaque white, so the tile is recovered in two passes: one render of the mint tile alone yields the coverage (alpha) map,
  one render of the full artwork yields the colours; un-premultiplying the colours against the alpha map restores the rounded tile with transparent corners.

## Components

### 1. Omnibar & Search Field

- Fully pill-shaped (`border-radius: 9999px`), 52px height.
- Background: `#FFFFFF` with active inner glow (`ring: 2px solid #14A79D`).
- Leading search icon in `#5F6E6C`, placeholder text in `#5F6E6C`, trailing shortcut indicator or voice action.

### 2. Floating Dock Bar (Mobile Tab Bar)

- Persistent floating capsule positioned 16px above the device bottom indicator.
- Dock surface: `#FFFFFF` at 80% opacity (Light) / `#0C1A18` at 80% opacity (Dark) with tactile elevation shadow.
- Houses current tabs carousel, quick "+" action, and drawer menu toggle.
- Tab chips inside dock:
  - Active: `#FFFFFF` 100% (Light) / `#0C1A18` 100% (Dark) pill with light elevation shadow (2.dp), no outline. Favicon rendered without squircle background box. Title in bold weight.
  - Inactive: transparent background, `#5F6E6C` (Light) / `#BEC9C6` (Dark) icon/text.
- Favicon source of truth: a tab favicon lives under a stable per-tab path (`favicon_<tabId>.png`).
  The path never changes while the tab lives, so the dock never reloads the icon while the page
  keeps running. The text initial is a fallback only: it appears when no icon is available (no
  source, or the load failed) and is never drawn underneath a loading or loaded icon.

### 3. Space & Favorite Cards

- Surface: `#FFFFFF` card, 20px padding.
- Center-aligned or left-aligned layouts featuring a 44×44px soft mint squircle icon backdrop (`#E9F6F2`) hosting a 20px primary teal icon.
- Title in `headline-sm` with domain metadata in `body-sm` (`#5F6E6C`).

### 4. Recently Closed List Item

- Horizontal card item with 12px vertical padding, 16px horizontal padding.
- Separated by hairline borders (`1px solid #F1F5F9`) or grouped inside rounded parent containers.
- Features square rounded favicon container, title, subtle timestamp right-aligned.

### 5. Chips & Filters

- Compact height (32px), pill-shaped.
- Background `#FFFFFF` or `#E9F6F2`, border `1px solid rgba(95, 110, 108, 0.15)`, text `body-sm` `#171D1B`.

### 6. Action Menu & Context Popover

- Floats as an anchored sheet with 24px radius, white frosted glass, subtle divider lines, and 48px touch targets for each menu row.

### 7. Settings Row (INTERACTION section)

- Icon tile (34px, 10px radius) + title/summary pair + `LiquidSunsetSwitch`, inside an `InsetGroupedCard`.
- The *Vibration* row is the only switch that controls the app's tactile feedback: it drives the platform vibrator, not the system touch-feedback setting (see `docs/adr/0002-platform-vibration-feedback.md`).
- Section headers are localized resources (`settings_section_*`), never hardcoded labels.
