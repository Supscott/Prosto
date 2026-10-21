# Iteration 01 - landing-redesign

- **Date:** 2026-10-20T10:43:16+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Replace the Astro landing page with an interactive React/Three.js site and add a browser smoke test

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Rebuild `landing/` as Vite + React 19 + TypeScript + Tailwind v4 instead of the previous Astro components | A single-page site that can host 3D, scroll choreography, and an interactive dock demo without Astro islands |
| B | Add a React Three Fiber hero with a procedural browser phone, floating tabs, halo rings, and pointer-reactive motion | A distinctive hero that works without shipping a real screenshot and scales from 320 px to 1920 px |
| C | Build the dock, tab strip, engine rail, quick-search sheet, and custom-engine form as a working in-page demo backed by `localStorage` | The page proves the product's core gesture behavior instead of only describing it |
| D | Add `scripts/smoke.mjs` and `npm run smoke` using Playwright | A repeatable browser-level regression check for the interactive demo and reduced-motion behavior |
| E | Keep a programmatic-scroll guard around engine selection during smooth rail scrolling | Explicitly chosen or newly added engines stay active even when the rail cannot center them |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 (no Android code changed) |
| build | PASS | `npm run build` -> `tsc --noEmit` and Vite build exit 0 |
| smoke | PASS | `npm run smoke` -> 11/11 browser checks pass (desktop Chrome, 390x844 mobile emulation plus a reduced-motion context) |
| tests | SKIP | Android `:app:testDebugUnitTest` not selected; no app code changed |
| capture | SKIP | Android bottom-bar capture not selected; landing visuals were verified manually and through the smoke test |
| visual | SKIP | Android baseline diff not selected; landing visuals have no accepted baseline yet |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 (12/12 active lessons) |

## Verdict

- **Adopted:** Probes A through E. The landing directory is now a Vite/React/TypeScript/Tailwind v4 site with a React Three Fiber hero, GSAP ScrollTrigger reveals, Lenis smooth scrolling, an interactive dock demo, responsive layouts down to 320 px, reduced-motion support, and a committed Playwright smoke test (`landing/scripts/smoke.mjs`, `npm run smoke`). The dock demo covers drag-to-search, tab switching, engine rail selection, custom engine creation, search results, and `localStorage` persistence.
- **Rejected:** Keeping the previous Astro component stack (superseded by the interactive requirements); relying on `scrollIntoViewIfNeeded` alone in browser checks (Lenis offsets made the target scroll unreliable, so the smoke test uses explicit `scrollIntoView` plus a settle wait).
- **Lesson candidate:** `LESSONS.md` #12 - programmatic smooth scroll can override the selected carousel item. Fixed by setting the active engine explicitly and holding a short programmatic-scroll guard while the rail animation runs.
- **Guardrail update:** The landing smoke test now locks the interactive demo behavior; the Android harness steps remain intentionally scoped to `app/` and were not affected by this iteration.
