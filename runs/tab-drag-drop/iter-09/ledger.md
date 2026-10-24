# Iteration 09 - tab-drag-drop

- **Date:** 2026-10-24T06:50:03+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Keep a dragged tab visible when it reorders before the LazyRow first visible item

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | | |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** _TODO_ (what from this iteration is kept)
- **Rejected:** _TODO_ (what was tried and dropped, with evidence)
- **Lesson candidate:** _TODO_ (a gotcha worth remembering - where does it go?)
- **Guardrail update:** _TODO_ (a rule for future work - where does it go?)
