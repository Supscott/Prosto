# Iteration 01 - app-icon-prosto

- **Date:** 2026-10-20T23:40:00+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Use app-icon.svg as the launcher icon with a smaller logo

## Probes

| Probe | Change tried                                                                                                                                             | Expected effect                                                                       |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| A     | Scaled the adaptive-icon foreground mark from 92% down to 57.1% of the visible 72dp launcher mask, matching the proportion the mark has on the mint tile | The installed launcher icon shows a small logo on a mint circle, like app-icon.svg    |
| B     | Re-rasterized the legacy density mipmaps from app-icon.svg with a two-pass coverage un-premultiply                                                       | The legacy PNGs are faithful renders of app-icon.svg with transparent rounded corners |

## Telemetry

| Check      | Status | Detail                                                                                   |
| ---------- | ------ | ---------------------------------------------------------------------------------------- |
| invariants | PASS   | `python3 tools/check_invariants.py` -> exit 0                                            |
| tests      | PASS   | `./gradlew :app:testDebugUnitTest` -> exit 0                                             |
| capture    | PASS   | `python3 tools/capture.py` -> exit 0                                                     |
| visual     | PASS   | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory     | PASS   | `python3 tools/harness_reflect.py audit` -> exit 0                                       |

## Verdict

- **Adopted:** Probe A - the mark now sits at 57.1% of the visible launcher mask; Probe B - legacy mipmaps rendered from app-icon.svg with transparency.
- **Rejected:** Keeping the mark at the 66dp safe-zone maximum - that filled the mask and read as an oversized logo.
- **Lesson candidate:** None - the lesson budget is full and the rasterization recipe is recorded in `DESIGN.md` under "App Icon".
- **Guardrail update:** None.
