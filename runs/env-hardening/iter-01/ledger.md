# Iteration 01 - env-hardening

- **Date:** 2026-10-19T19:00:00+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Track visual baselines, force fresh preview capture, and consolidate lesson memory

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Delete the preview PNG and run the test with `--rerun-tasks` in `tools/capture.py` | Captured frame always reflects the current code; a stale frame becomes impossible |
| B | Keep the previous lazy generation (render only when the file is missing) | ~90 s faster per gate run, but the captured frame can lag behind the code |
| C | Add `!runs/baselines/` + `!runs/baselines/*.png` to `.gitignore` | The accepted look is versioned; the `visual` step cannot silently degrade to `INFO` on a fresh clone |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** Probe A and probe C. `tools/capture.py` now deletes the side-effect artifact, forces a re-render, and reports `created` / `changed` / `unchanged` by SHA-256; `runs/baselines/bottom_bar.png` is tracked. The pre-Harness^2 lesson log moved to `docs/archive/lessons_legacy.md` with a read-only banner, and `LESSONS.md` points at it explicitly.
- **Rejected:** Probe B. The lazy path was observed in the wild: the gate reported `visual PASS` while the compared frame was rendered at 19:44, identical in size to the baseline (22 823 B). A stale frame masks real UI regressions, which defeats the purpose of the gate.
- **Lesson candidate:** None added to `LESSONS.md`. The trap is mechanically prevented by `capture.py` and the memory budget is at 10/12, so prevention beats prose.
- **Guardrail update:** None. The invariant "never diff a build side-effect without forcing regeneration" lives in `tools/capture.py`; the versioning rule lives in `.gitignore`.

