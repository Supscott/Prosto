# Iteration 01 - new-design-restyle

- **Date:** 2026-10-20T17:25:03+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Restyle to new-design.md: teal #087E78 primary on #E9F6F2 canvas, Manrope titles + Inter body, new launcher icon

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Recolored the token set in `ui/theme/Color.kt` from cerulean to teal (`#087E78` primary, `#E9F6F2` canvas) and re-derived the dark scheme | The whole UI switches to the new brand hue with no per-screen edits |
| B | Swapped Plus Jakarta Sans for Manrope (titles) + Inter (body), font files instanced from the Google Fonts variable originals | Headings and body copy pick up the new type system |
| C | Rebuilt the launcher identity from `app/src/main/assets/icon/*.svg` (adaptive icon + legacy density PNGs) | The installed app shows the new teal swirl instead of the old blue compass |
| D | Swept the hardcoded canvas/pale-blue literals in previews and overlay icon backgrounds | Preview frames show the real mint canvas instead of the old blue-grey |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 3.96% of the frame, 5.68% blocks differ, mean delta 1.2 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** All four probes - the palette, the two-family typography, the new launcher icon and the preview sweep are kept.
- **Rejected:** Keeping the old blue category badge for `FileCategory.IMAGE` in `DownloadsScreen` - it is a per-category hue, not a brand token, and the other categories (pink, amber, violet, green) are equally distinct.
- **Lesson candidate:** None - the budget of active lessons in `LESSONS.md` is full and nothing new bit during this iteration.
- **Guardrail update:** The icon pipeline (source SVG -> adaptive icon + rasterized legacy PNGs) is recorded in `DESIGN.md` under "App Icon".
