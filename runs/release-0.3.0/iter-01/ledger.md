# Iteration 01 - release-0.3.0

- **Date:** 2026-10-22T12:50:51+00:00
- **Base ref:** `v0.2.0`
- **Hypothesis:** bump versionCode 3 / versionName 0.3.0 for the v0.3.0 release

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | `versionCode = 3`, `versionName = "0.3.0"` in `app/build.gradle.kts` | the tag and the installed build agree, so the in-app updater stops re-offering the release |
| B | local `assembleRelease` + `aapt2 dump badging` | the release variant reports versionCode 3 / versionName 0.3.0 and the label `Prosto` |
| C | local `apksigner sign` with `~/.prosto/release.jks` | the certificate matches the published v0.2.0 artifact, so v0.3.0 installs in place |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** version bump to 3 / 0.3.0; the signing key is unchanged, so the release installs over v0.2.0 without an uninstall.
- **Rejected:** none.
- **Lesson candidate:** none new - the tag/versionName contract and the key-continuity check are already covered by the release skill and by lesson #9 (key rotation breaks in-place updates).
- **Guardrail update:** none.
