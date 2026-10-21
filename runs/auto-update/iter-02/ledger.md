# Iteration 02 - auto-update

- **Date:** 2026-10-21T09:50:06+00:00
- **Base ref:** `n/a`
- **Hypothesis:** cleanup: pending APK filtering, install-permission check, card button row

## Probes

| Probe | Change tried | Expected effect |
| --- | --- | --- |
| A | Drop the `SDK_INT < O` branch in `canInstallPackages()` (minSdk is 26, so `canRequestPackageInstalls()` always exists) | One less dead branch in the install path |
| B | Filter the pending APKs without destructuring the unused `file` element, and drop the zero-width `Spacer` from the card button row | No dead code, no lint noise in the new files |
| C | Re-run the whole gate after the cleanup | Invariants, 570 tests, capture and visual diff stay green, so the cleanup changed no behaviour |

## Telemetry

| Check | Status | Detail |
| --- | --- | --- |
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 (570 tests, 0 failures) |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 (12/12 active lessons) |

## Verdict

- **Adopted:** the cleanup - a smaller install path with no dead branches, and the same
  behaviour (the gate output is identical to iteration 01).
- **Rejected:** nothing new was tried; this iteration only removed code.
- **Lesson candidate:** none beyond lesson 12, which was already recorded in iteration 01.
- **Guardrail update:** none.
