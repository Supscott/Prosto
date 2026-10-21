# Iteration 03 - favicon-flicker

- **Date:** 2026-10-20T17:20:00+00:00
- **Base ref:** `n/a`
- **Hypothesis:** the favicon guards must survive a future PR, not only a local harness run

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Searched the repo for every caller of `tools/check_invariants.py` | Find out whether the invariant guards are part of CI |
| B | Added a "Run Architectural Invariant Checks" step to `.github/workflows/ci.yml` | Every push/PR enforces the invariants, including the newly graduated lesson #2 |

## Evidence

- `tools/check_invariants.py` was referenced only by `harness.config.json` (local gate) and by the
  documentation - `ci.yml` ran `testDebugUnitTest` and `lintDebug` only. A PR reintroducing
  `LaunchedEffect(scrollState.value)` (graduated lesson #2) or a timestamped favicon file name would
  have stayed green in CI.
- After the change all five invariants (`no_blur`, `webkit_crash_resilience`, `domain_layer_purity`,
  `stable_favicon_paths`, `no_continuous_effect_keys`) run on every push and pull request.

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** CI step for the architectural invariants; the favicon contract is now guarded at
  three levels (behavior test in CI, source-level invariant in CI and the gate, documented contract
  in `DESIGN.md`).
- **Rejected:** adding the invariants as a Gradle task - the checks are source-level and already
  run in two seconds as a standalone script.
- **Lesson candidate:** none - the reflection of this iteration is a wiring fix, not a new gotcha.
- **Guardrail update:** `.github/workflows/ci.yml` - "Run Architectural Invariant Checks".
