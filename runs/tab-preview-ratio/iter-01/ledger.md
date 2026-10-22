# Iteration 01 - tab-preview-ratio

- **Date:** 2026-10-22T15:44:15+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Tab preview card uses 9:16 aspect ratio and preview image starts from top instead of center

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | `GridTabCard`: update card aspect ratio from `4f / 5f` to `9f / 16f`, and set `alignment = Alignment.TopCenter` on `AsyncImage` | Tab preview card adopts 9:16 aspect ratio, and webpage snapshot renders starting from the top rather than vertically centered |
| B | `TabOverviewGridPreviewTest`: added tests asserting card aspect ratio matches 9:16 and snapshots render with top alignment | Automated JVM regression coverage |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** 9:16 card aspect ratio in `GridTabCard` and `Alignment.TopCenter` in Coil `AsyncImage`. Updated living architecture in `DESIGN.md`. Added unit test in `TabOverviewGridPreviewTest` verifying 9:16 aspect ratio and snapshot rendering.
- **Rejected:** None.
- **Lesson candidate:** None. Active lesson budget is at capacity (12/12).
- **Guardrail update:** None. Existing invariant checkers pass.
