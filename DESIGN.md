---
name: Liquid Pastel Sunset
colors:
  surface: '#fff8f8'
  surface-dim: '#dfd8d9'
  surface-bright: '#fff8f8'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f9f2f3'
  surface-container: '#f3eced'
  surface-container-high: '#eee6e7'
  surface-container-highest: '#e8e1e2'
  on-surface: '#1e1b1c'
  on-surface-variant: '#42474e'
  inverse-surface: '#332f30'
  inverse-on-surface: '#f6eff0'
  outline: '#72777f'
  outline-variant: '#c2c7d0'
  surface-tint: '#33618d'
  primary: '#33618d'
  on-primary: '#ffffff'
  primary-container: '#7ba7d7'
  on-primary-container: '#003c65'
  inverse-primary: '#9ecafc'
  secondary: '#82533d'
  on-secondary: '#ffffff'
  secondary-container: '#ffc1a5'
  on-secondary-container: '#7a4d37'
  tertiary: '#854e60'
  on-tertiary: '#ffffff'
  tertiary-container: '#d391a5'
  on-tertiary-container: '#5a2a3b'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d0e4ff'
  primary-fixed-dim: '#9ecafc'
  on-primary-fixed: '#001d35'
  on-primary-fixed-variant: '#154974'
  secondary-fixed: '#ffdbcc'
  secondary-fixed-dim: '#f6b99d'
  on-secondary-fixed: '#321203'
  on-secondary-fixed-variant: '#663c27'
  tertiary-fixed: '#ffd9e2'
  tertiary-fixed-dim: '#f9b3c8'
  on-tertiary-fixed: '#360c1d'
  on-tertiary-fixed-variant: '#6a3748'
  background: '#fff8f8'
  on-background: '#1e1b1c'
  surface-variant: '#e8e1e2'
typography:
  display:
    fontFamily: Plus Jakarta Sans
    fontSize: 56px
    fontWeight: '600'
    lineHeight: 64px
    letterSpacing: -0.03em
  display-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 38px
    fontWeight: '600'
    lineHeight: 44px
    letterSpacing: -0.025em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 34px
    fontWeight: '600'
    lineHeight: 42px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 34px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 30px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 26px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 17px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: -0.005em
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 21px
    letterSpacing: 0em
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
    letterSpacing: 0.005em
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 15px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: -0.005em
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 17px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.02em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  space-xxs: 0.25rem
  space-xs: 0.5rem
  space-sm: 0.75rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
  space-2xl: 3rem
  space-3xl: 4rem
  gutter-mobile: 1rem
  gutter-tablet: 1.5rem
  gutter-desktop: 2rem
  margin-mobile: 1.25rem
  margin-tablet: 2rem
  margin-desktop: 3.5rem
---

## Brand & Style

This design system expresses a state of weightless tranquility, blending the sensory luxury of Apple Liquid Glass interfaces with the luminous calm of a dusk sky. The personality is serene, editorial, and tactile without being skeuomorphic. It balances organic ambient warmth with clean digital precision.

### Target Audience & Emotional Impact
Designed for lifestyle, high-end wellness, creative tools, and mindful productivity platforms where visual fatigue must be minimized. The interface should evoke a deep exhale—calm, airy, and softly focused—replacing digital strain with gentle luminescence.

### Design Movement & Core Expression
- **Liquid Glass & Translucency:** Optical depth established via heavy backdrop blurs (`backdrop-blur-2xl` to `backdrop-blur-3xl`), milky opacity layers, and hairline specular light borders (inner glows).
- **Soft Muted Sunset:** Pure light-toned pastels (powder sky blue, warm apricot, dusty rose) that melt into creamy, warm-tinted base foundations rather than stark whites or sterile grays.
- **Continuous Pill Geometry:** Continuous smooth corners (squircle and pill architectures) conforming to iOS ergonomics and high-craft tactile continuity.

## Colors

The palette discards high-saturation, neon, or acidic sunset gradients in favor of refined, milk-diffused daylight transitions. Every hue holds a soft, powdery undertone that preserves legibility and structural hierarchy without harsh visual spikes.

### Palette Architecture
- **Primary (`#7BA7D7` — Powder Sky Blue):** Represents clarity, active selection, and calm focus. Expanded with `#9BBFEA` for frosted tints and hover interactions.
- **Secondary (`#F6B99D` — Muted Apricot):** Evokes sunset warmth and golden hour ambiance. Used for complementary visual accents, badges, and progress indicators. Softened with `#FAD2BF` for delicate backdrop fills.
- **Tertiary (`#E8A4B8` — Dusty Rose):** A balanced, muted berry tint for tactile highlights, soft notifications, and gentle focal points. Complemented by `#F2C4D0` in diffuse surface washes.
- **Neutral Surface Baseline (`#FBF9F7` — Dreamy Cream):** Replaces clinical cool-white (#FFFFFF) with a warm alabaster milk substrate, creating cohesive atmospheric diffusion behind frosted glass planes.
- **Text & Foreground System:**
  - Primary Label: `#2D2A2B` (deep espresso-charcoal, preserving soft organic contrast).
  - Secondary Label: `#6B6668` (diffused mauve-slate).
  - Tertiary / Placeholder: `#9D9799` (cloud dusk).
  - Subtle Border / Specular Rim: `rgba(255, 255, 255, 0.65)` on highlight edges, and `rgba(123, 167, 215, 0.15)` on lower shadows.

## Typography

The typographic system utilizes Apple-inspired typographic discipline via Plus Jakarta Sans: refined rounded geometry, balanced aperture, and optical kerning that echoes iOS 18 human-interface sensibilities.

### Hierarchy & Application Guidelines
- **Optical Tightening:** Display and large headline roles use negative tracking (`-0.03em` to `-0.015em`) to retain a cohesive, magazine-level polish across glassy backdrops.
- **Legibility Across Translucency:** Avoid thin (`font-weight: 300`) cuts across frosted surfaces. Primary body defaults to Regular (`400`) at 15–17px, and labels use Medium (`500`) or SemiBold (`600`) to guarantee legibility against variable ambient refraction.
- **Line Heights:** Generous line heights ensure breathability and prevent content density from breaking the calming aesthetic.

## Layout & Spacing

The layout model is anchored on an 8-point harmonic grid system with fluid flex and fractional column scaling, simulating expansive canvas space rather than boxed-in containers.

### Adaptive Breakpoints
- **Compact (Mobile: 0px – 599px):** 4-column layout. Minimalist edge-to-edge floating sheets with 20px (`1.25rem`) side gutters. Navigation collapses into a floating bottom pill bar.
- **Medium (Tablet: 600px – 1023px):** 8-column layout. Side paddings scale to 32px (`2rem`). Split panes utilize layered glass trays over ambient sunset canvas.
- **Expanded (Desktop: 1024px+):** 12-column layout with a maximum container boundary of 1320px. 56px (`3.5rem`) outer page margins protect whitespace, allowing frosted cards to float unconstrained.

### Spacing Philosophy
Whitespace is treated as an active material. Generous internal card padding (at least `1.5rem` to `2rem`) prevents content elements from colliding with the refractive borders of liquid glass panels.

## Elevation & Depth

Depth is conveyed through optical physics: multi-layered glass refractions, light-refracting borders, and warm ambient backdrops instead of opaque drop shadows.

### The Liquid Glass Elevation Stack
1. **Canvas (Base Ground):** `#FBF9F7` overlaid with an ultra-soft, low-opacity ambient radial gradient mesh of `#9BBFEA` (top-left) drifting into `#FAD2BF` (center) and `#F2C4D0` (bottom-right).
2. **Glass Level 1 (Background Shelves & Cards):**
   - Background fill: `rgba(255, 255, 255, 0.52)`
   - Backdrop filter: `blur(24px) saturate(180%)`
   - Border: `1px solid rgba(255, 255, 255, 0.7)`
   - Inner highlight: `inset 0 1px 1px 0 rgba(255, 255, 255, 0.9)`
   - Shadow: `0 8px 32px -4px rgba(125, 120, 121, 0.06), 0 2px 6px -1px rgba(125, 120, 121, 0.03)`
3. **Glass Level 2 (Interactive Elements & Floating Bars):**
   - Background fill: `rgba(255, 255, 255, 0.72)`
   - Backdrop filter: `blur(32px) saturate(190%)`
   - Border: `1px solid rgba(255, 255, 255, 0.85)`
   - Inner highlight: `inset 0 1.5px 1.5px 0 rgba(255, 255, 255, 0.95)`
   - Shadow: `0 14px 40px -6px rgba(123, 167, 215, 0.12), 0 4px 12px -2px rgba(246, 185, 157, 0.1)`
4. **Glass Level 3 (Popovers, Modals & Sheets):**
   - Background fill: `rgba(251, 249, 247, 0.82)`
   - Backdrop filter: `blur(48px) saturate(200%)`
   - Border: `1px solid rgba(255, 255, 255, 0.95)`
   - Shadow: `0 24px 64px -12px rgba(107, 102, 104, 0.14), 0 8px 24px -4px rgba(232, 164, 184, 0.12)`

## Shapes

The design system adopts Level 3 (Pill-shaped) geometry, embracing continuous squircle math and full round caps inspired by iOS 18 dynamic UI modules.

### Corner Radii Conventions
- **Interactive Controls (Buttons, Inputs, Badges, Chips):** Continuous pill shape (`border-radius: 9999px` or full height radius).
- **Cards & Panes:** Fluid curvature at 24px (`1.5rem`) on mobile, scaling to 32px (`2rem`) on desktop.
- **Sheets & Floating Overlays:** 36px to 44px (`2.25rem` – `2.75rem`) corner curvature, harmonizing with native display bezels.
- **Glow & Rim Alignment:** Shapes feature double-stroke illusion: an outer hairline translucent border paired with a top-weighted specular inner reflection.

## Components

### Buttons
- **Primary Pill:** Background in soft powder sky blue gradient (`linear-gradient(135deg, #8EB5E0 0%, #7BA7D7 100%)`). White text (`#FFFFFF`) with subtle text shadow `0 1px 2px rgba(45, 42, 43, 0.12)`. Inner highlight: `inset 0 1px 1px rgba(255, 255, 255, 0.5)`. Pill radius (`9999px`), padding: `12px 24px`.
- **Frosted Glass Secondary:** `rgba(255, 255, 255, 0.6)` background, `backdrop-blur-xl`, border `1px solid rgba(255, 255, 255, 0.85)`. Text `#2D2A2B`. Hover state smoothly transitions background to `rgba(246, 185, 157, 0.25)` (muted peach wash).
- **Subtle Ghost Pill:** Transparent background, text `#6B6668`. Hover state fills with `rgba(123, 167, 215, 0.1)`.

### Chips & Filters
- **Default State:** Pill shape with `rgba(255, 255, 255, 0.5)` background, hairline border `rgba(255, 255, 255, 0.7)`. Typography: `label-md`.
- **Selected State:** Soft peach/apricot tint (`#FAD2BF` at 65% opacity) with dusty rose accent border (`rgba(232, 164, 184, 0.6)`), text `#2D2A2B`, and an ambient inner bloom.

### Form Inputs & Search Fields
- **Container:** Full pill radius (`9999px`) for single-line fields, or 24px squircle for text areas.
- **Surface:** `rgba(255, 255, 255, 0.45)` with `backdrop-blur-xl`.
- **Border:** `1px solid rgba(255, 255, 255, 0.6)`.
- **Focus State:** Elevated to `rgba(255, 255, 255, 0.8)`, border transitions to `#7BA7D7` with an exterior soft diffuse halo: `0 0 0 4px rgba(123, 167, 215, 0.18)`.
- **Placeholder:** `#9D9799`. Value text: `#2D2A2B`.

### Checkboxes & Toggle Switches
- **Toggle Switch:** Pill track in `rgba(125, 120, 121, 0.15)`, transitioning to `#7BA7D7` when active. Thumb is an opaque creamy white `#FFFFFF` disc with a gentle drop shadow (`0 3px 8px rgba(45, 42, 43, 0.12)`).
- **Checkbox:** Squircle radius (8px), frosted unselected border; selected state fills with dusty rose (`#E8A4B8`) displaying a white rounded checkmark.

### Cards & Container Panels
- **Structure:** Level 1 or Level 2 glass surfaces featuring rounded corners (32px).
- **Padding:** Generous 24px to 32px inner offset.
- **Divider Lines:** Never solid gray. Use translucent gradient hairline borders: `linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.8), transparent)`.

### Segmented Controls & Floating Bars
- **Floating Island Dock:** Pill wrapper with `rgba(255, 255, 255, 0.65)`, `backdrop-blur-2xl`, inner shadow glow, housing sliding pill selection tabs.
- **Active Pill Slider:** Pure white (`#FFFFFF`) capsule with soft diffuse shadow (`0 4px 14px rgba(123, 167, 215, 0.15)`), gliding under active labels without layout shift.