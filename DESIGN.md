---
name: Serene Canvas
colors:
  surface: "#faf8ff"
  surface-dim: "#d2d9f4"
  surface-bright: "#faf8ff"
  surface-container-lowest: "#ffffff"
  surface-container-low: "#f2f3ff"
  surface-container: "#eaedff"
  surface-container-high: "#e2e7ff"
  surface-container-highest: "#dae2fd"
  on-surface: "#131b2e"
  on-surface-variant: "#414750"
  inverse-surface: "#283044"
  inverse-on-surface: "#eef0ff"
  outline: "#717781"
  outline-variant: "#c1c7d2"
  surface-tint: "#0062a2"
  primary: "#005893"
  on-primary: "#ffffff"
  primary-container: "#2271b3"
  on-primary-container: "#eaf1ff"
  inverse-primary: "#9dcaff"
  secondary: "#0060ac"
  on-secondary: "#ffffff"
  secondary-container: "#64a8fe"
  on-secondary-container: "#003c70"
  tertiary: "#4f565a"
  on-tertiary: "#ffffff"
  tertiary-container: "#686e73"
  on-tertiary-container: "#ebf1f6"
  error: "#ba1a1a"
  on-error: "#ffffff"
  error-container: "#ffdad6"
  on-error-container: "#93000a"
  primary-fixed: "#d1e4ff"
  primary-fixed-dim: "#9dcaff"
  on-primary-fixed: "#001d35"
  on-primary-fixed-variant: "#00497c"
  secondary-fixed: "#d4e3ff"
  secondary-fixed-dim: "#a4c9ff"
  on-secondary-fixed: "#001c39"
  on-secondary-fixed-variant: "#004883"
  tertiary-fixed: "#dde3e8"
  tertiary-fixed-dim: "#c1c7cc"
  on-tertiary-fixed: "#161c20"
  on-tertiary-fixed-variant: "#41484c"
  background: "#faf8ff"
  on-background: "#131b2e"
  surface-variant: "#dae2fd"
typography:
  headline-xl:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: "700"
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 26px
    fontWeight: "700"
    lineHeight: 34px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: "600"
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 17px
    fontWeight: "600"
    lineHeight: 24px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: "400"
    lineHeight: 24px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: "400"
    lineHeight: 20px
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 13px
    fontWeight: "400"
    lineHeight: 18px
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: "600"
    lineHeight: 16px
    letterSpacing: 0.04em
  label-sm:
    fontFamily: Plus Jakarta Sans
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

- Pure, soft backgrounds infused with cool sky undertones.
- Elevated floating card modules with soft atmospheric dispersion shadows.
- Distinct pill-shaped controls designed for fluid thumb interactions.
- Minimal visual chrome that recedes naturally to prioritize the user's content.

## Colors

The palette is tuned around balanced cerulean tones and airy sky-washed neutrals:

- **Primary (`#2271B3`)**: Deep sky blue used for active selections, interactive icons, prominent buttons, and branded moments.
- **Accent (`#60A5FA`)**: Clear day-sky tone used for focus rings, hover indicators, active badges, and subtle glows.
- **Surface / Background (`#F1F7FC`)**: An atmospheric, very pale blue-grey that acts as the primary canvas floor, softening contrast compared to stark white.
- **Canvas / Cards (`#FFFFFF`)**: Pure crisp white reserved for elevated cards, modal sheets, and the floating dock bar.
- **Dark Text (`#0F172A`)**: Rich midnight navy for crisp typography with superior readability.
- **Muted Slate (`#64748B`)**: Secondary text, tab metadata, inactive icons, and structural line dividers.

### Dark Theme Palette (Figma Reference)

The dark mode palette adopts deep midnight blues and crisp slate contrasts:

- **Canvas Floor (`#080F19`)**: Deep midnight background base for the canvas viewport.
- **Dock Base (`#0D1824`)**:
  - Dock Container: `#0D1824` at **80% opacity** (`rgba(13, 24, 36, 0.80)`) with elevation shadow.
  - Active Tab Capsule: `#0D1824` at **100% opacity** (`#0D1824`) with light elevation shadow.
- **Card Surface (`#1E293B`)**: Solid elevated surface for cards, menus, and sheets.
- **Text & Foreground (`#F8FAFC`)**: Crisp off-white for primary headlines and active tab titles.
- **Secondary Slate (`#94A3B8`)**: Secondary labels, inactive icons, and metadata in dark mode.
- **Muted Slate (`#64748B`)**: Structural dividers and borders.
- **Accent Navy (`#1E3A8A`)**: Deep blue container/accent tint.
- **Primary Sky (`#2271B3`)**: Primary actions and interactive highlights.
- **Accent Light Blue (`#60A5FA`)**: Focused rings, badges, and glows.

### Hierarchy & Tint Rules

- Floating Dock Bar:
  - Light theme: `#FFFFFF` at **80% opacity** (`rgba(255, 255, 255, 0.80)`) with tactile elevation shadow.
  - Dark theme: `#0D1824` at **80% opacity** (`rgba(13, 24, 36, 0.80)`) with tactile elevation shadow.
- Active Tab Capsule:
  - Light theme: `#FFFFFF` at **100% opacity** with light elevation shadow (2.dp), no outline.
  - Dark theme: `#0D1824` at **100% opacity** with light elevation shadow (2.dp), no outline.
  - Favicon rendered directly without background squircle container.
  - Title styled in **bold** typography.

## Typography

The design system exclusively utilizes **Plus Jakarta Sans** for its contemporary, rounded geometry and open counters. It provides an optimal balance between technical cleanliness and friendly warmth.

- **Scale & Weight**:
  - `headline-xl` is reserved for central home screen greetings ("Where to next?").
  - `headline-sm` anchors section titles such as "Favorite Spaces" and "Recently Closed".
  - Section subtitles and meta counters use uppercase tracking with `label-md`.
  - Content URLs and secondary timestamps render in `body-sm` using `#64748B`.

## Layout & Spacing

Mobile layouts follow a relaxed fluid column structure bounded by safe zones:

- **Horizontal Canvas Margin**: 16px (`1rem`) on standard mobile displays, expanding to 24px (`1.5rem`) on compact tablets or foldable unfolded screens.
- **Vertical Rhythm**:
  - Sections are spaced 32px (`space-xl`) apart to maintain the calm, expansive feel seen on desktop.
  - Interactive grid elements (e.g., Favorite Spaces tiles) utilize a 2-column or 4-column layout with 12px (`gutter`) gaps.
- **Floating Dock Clearance**: Bottom page content always reserves 96px of inset scrolling padding (`space-xl` + dock clearance) to avoid obstruction by the bottom navigation bar and home gesture bar.

## Elevation & Depth

Visual hierarchy uses multi-layered ambient light, avoiding harsh dropshadows or heavy outlines:

- **Base Layer (Level 0)**: Tone `#F1F7FC`, flat canvas foundation.
- **Surface Layer (Level 1 - Cards & Lists)**: Pure white `#FFFFFF` with an ambient diffused shadow:
  - `box-shadow: 0 4px 20px -2px rgba(15, 23, 42, 0.04), 0 2px 6px -1px rgba(15, 23, 42, 0.02);`
  - Subtle micro-border: `1px solid rgba(226, 232, 240, 0.8)`.
- **Floating Controls (Level 2 - Omnibox, Modals, Action Dock)**:
  - Background: `rgba(255, 255, 255, 0.92)` with `backdrop-filter: blur(20px) saturate(180%)`.
  - Ambient elevation shadow: `box-shadow: 0 12px 36px -4px rgba(34, 113, 179, 0.12), 0 4px 12px rgba(15, 23, 42, 0.06);`
  - Rim highlight border: `1px solid rgba(255, 255, 255, 0.8)`.

## Shapes

The interface embraces a continuous **pill and ultra-soft shape hierarchy** (`roundedness: 3`):

- **Pills (`9999px`)**: Omnibar search fields, tab switchers, category chips, primary FABs, and floating dock bars.
- **Cards & Sheets (`24px` to `28px`)**: Quick-dial space tiles, context menus, and bottom modal sheets.
- **App Icons / Favicons (`14px` to `16px`)**: Rounded squircle containers within favorite grid items.

## Components

### 1. Omnibar & Search Field

- Fully pill-shaped (`border-radius: 9999px`), 52px height.
- Background: `#FFFFFF` with active inner glow (`ring: 2px solid #60A5FA`).
- Leading search icon in `#64748B`, placeholder text in `#64748B`, trailing shortcut indicator or voice action.

### 2. Floating Dock Bar (Mobile Tab Bar)

- Persistent floating capsule positioned 16px above the device bottom indicator.
- Dock surface: `#FFFFFF` at 80% opacity (Light) / `#0D1824` at 80% opacity (Dark) with tactile elevation shadow.
- Houses current tabs carousel, quick "+" action, and drawer menu toggle.
- Tab chips inside dock:
  - Active: `#FFFFFF` 100% (Light) / `#0D1824` 100% (Dark) pill with light elevation shadow (2.dp), no outline. Favicon rendered without squircle background box. Title in bold weight.
  - Inactive: transparent background, `#64748B` (Light) / `#94A3B8` (Dark) icon/text.

### 3. Space & Favorite Cards

- Surface: `#FFFFFF` card, 20px padding.
- Center-aligned or left-aligned layouts featuring a 44×44px soft blue squircle icon backdrop (`#F1F7FC`) hosting a 20px primary blue icon.
- Title in `headline-sm` with domain metadata in `body-sm` (`#64748B`).

### 4. Recently Closed List Item

- Horizontal card item with 12px vertical padding, 16px horizontal padding.
- Separated by hairline borders (`1px solid #F1F5F9`) or grouped inside rounded parent containers.
- Features square rounded favicon container, title, subtle timestamp right-aligned.

### 5. Chips & Filters

- Compact height (32px), pill-shaped.
- Background `#FFFFFF` or `#F1F7FC`, border `1px solid rgba(100, 116, 139, 0.15)`, text `body-sm` `#0F172A`.

### 6. Action Menu & Context Popover

- Floats as an anchored sheet with 24px radius, white frosted glass, subtle divider lines, and 48px touch targets for each menu row.
