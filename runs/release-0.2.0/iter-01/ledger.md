# Iteration 01 - release-0.2.0

- **Date:** 2026-10-21T10:25:00+00:00
- **Base ref:** `7bbb214`
- **Hypothesis:** Release prep: version bump to 0.2.0, stable signing key via repo secret, release workflow refuses throwaway keys, About section shows the real version

## Probes

| Probe | Change tried | Expected effect |
| --- | --- | --- |
| A | Inspect the signature of the published v0.1.0 APK with `apksigner verify --print-certs` | Confirms whether the installed app and a new release share a signing key - the precondition for the in-place update |
| B | Generate a PKCS12 release keystore (RSA 4096, alias `prosto`) and store it as the repository secrets `KEYSTORE_BASE64`, `KEY_ALIAS`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD` | Every release from v0.2.0 on is signed with the same key, so the in-app self-update can actually install the next version |
| C | Make the release workflow fail hard when `KEYSTORE_BASE64` is missing instead of generating a throwaway key | A future release can never silently rotate the key again and break the update chain |
| D | Bump `versionCode` to 2 and `versionName` to `0.2.0` | The tag `v0.2.0` and the installed `versionName` agree, so the semver check in `UpdateCoordinator` sees the release as newer |
| E | Replace the hardcoded "Prosto Browser v1.0" in the About footer with the version from `PackageManager` | The About section shows the real version instead of a stale literal |

## Telemetry

| Check | Status | Detail |
| --- | --- | --- |
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 (570 tests, 0 failures) |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 (12/12 active lessons) |

Extra checks outside the gate:

- `./gradlew :app:assembleRelease` -> BUILD SUCCESSFUL (R8 + resource shrinking + `lintVitalRelease`).
- The unsigned artifact reports `versionCode=2 versionName=0.2.0`, label `Prosto`.
- Signing the artifact locally with the stored keystore reproduces the workflow path
  exactly: `Verifies`, v2 + v3 signature schemes, certificate SHA-256
  `d4ecfbd566cab4fb27dc6b1f4431f97549366781e394fa175157c53da39c89d4`.

## Verdict

- **Adopted:** the stable keystore plus the hard failure in the release workflow, the
  version bump and the real version in the About section.
- **Rejected:** keeping the "generate a throwaway key" fallback. v0.1.0 was signed with
  such a key and it was deleted in the runner, so that key is unrecoverable and v0.2.0
  cannot be installed over v0.1.0 - the fallback is exactly what broke the update chain
  and would have broken it again on the next release.
- **Lesson candidate:** none new; the signing-key requirement is already recorded in
  `docs/adr/0001-github-release-self-update.md` and the keystore backup lives in
  `~/.prosto/README.md`.
- **Guardrail update:** the release workflow now refuses to publish without the signing
  secret, so a missing secret fails the run instead of shipping a broken update.
