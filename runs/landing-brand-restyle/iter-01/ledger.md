# Iteration 01 - landing-brand-restyle

- **Date:** 2026-10-21T00:00:00+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Re-tint the landing site to the Prosto teal brand, swap Manrope/Inter fonts and use the app icon

## Probes

| Probe | Change tried                                                                                                                                              | Expected effect                                              |
| ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| A     | Re-tinted the whole landing palette to the app dark theme: canvas #0E1513, panels #101614, text #DEE4E2, primary jade #56DBCF, mint #A5F2E9, teal #7FD8CB | The marketing site reads as the same product as the app      |
| B     | Swapped the type system to Manrope titles + Inter body and dropped Instrument Serif                                                                       | Headings and copy match the app typography                   |
| C     | Replaced the header mark and favicon with the app icon (mint tile + teal swirl)                                                                           | The site and the installed app share one identity            |
| D     | Renamed the accent tokens acid/aqua/lilac to brand/mint/teal and the .serif class to .accent                                                              | No code still refers to the old palette or the dropped serif |

## Telemetry

| Check      | Status | Detail                                                                                   |
| ---------- | ------ | ---------------------------------------------------------------------------------------- |
| invariants | PASS   | `python3 tools/check_invariants.py` -> exit 0                                            |
| tests      | PASS   | `./gradlew :app:testDebugUnitTest` -> exit 0                                             |
| capture    | PASS   | `python3 tools/capture.py` -> exit 0                                                     |
| visual     | PASS   | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory     | PASS   | `python3 tools/harness_reflect.py audit` -> exit 0                                       |

## Verdict

- **Adopted:** All four probes - the landing now uses the app's dark-theme palette, Manrope/Inter typography and the app icon.
- **Rejected:** A light mint landing - the user chose to keep the dark canvas and re-tint it; the app's own dark theme (`#0E1513`) is the reference.
- **Lesson candidate:** Added to `LESSONS.md` - a color-rewriting regex that consumes the closing parenthesis of `rgba()` silently corrupts the stylesheet.
- **Guardrail update:** Two lessons graduated to the archive (both now enforced by `tools/check_invariants.py`).
