# Iteration 01 - auto-update

- **Date:** 2026-10-21T09:41:42+00:00
- **Base ref:** `n/a`
- **Hypothesis:** GitHub-release self-update: auto-update toggle, background APK download, install on next cold start, in-app Install/Install Later/Skip card on home + settings

## Probes

| Probe | Change tried | Expected effect |
| --- | --- | --- |
| A | Detect the release with a semver comparison of `tag_name` against the installed `versionName` (`compareVersions`, pure domain function) instead of a `versionCode` in the asset name | The release convention stays "tag = v\<versionName\>" and no extra naming rule is imposed on the release job; `1.10.0` must beat `1.9.0` (numeric, not lexicographic) |
| B | Install through the system package installer (`ACTION_VIEW` + FileProvider) at the **next cold start** instead of a silent `PackageInstaller` install | The OS never kills the app while a browsing session is live; the update becomes active after the app is closed and reopened |
| C | Trigger the GitHub check once per cold start (no polling, no periodic timer) | One request per app launch stays far below the 60 req/h unauthenticated GitHub rate limit and costs no battery |
| D | Reuse one in-app card (`UpdateNotificationCard`) for the home screen and the settings screen, driven by a single `AppUpdateUiState` | The "notification on home and settings pages" requirement is met with one component and one state machine instead of two parallel UIs |
| E | Persist only two settings (`isAutoUpdateEnabled`, `skippedUpdateVersion`) and derive the pending APK from the file name in `filesDir/updates/` | No extra Room keys, and a stale APK that is no longer newer than the installed build is deleted on the next start |

## Telemetry

| Check | Status | Detail |
| --- | --- | --- |
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 (570 tests, 0 failures) |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 (12/12 active lessons) |

New tests (18): `AppVersionTest` (9) covers the semver comparison, `GithubReleaseParserTest` (6)
covers the release payload parsing, `UpdateNotificationCardTest` (3) renders the card in the
available / downloading / ready states, and `SettingsRepositoryTest` gained 3 cases for the
auto-update flag and the skipped version.

## Verdict

- **Adopted:** the whole flow - cold-start check, background download into app-private
  storage, install handed to the system installer on the next cold start, and the shared
  in-app card for the manual path. Rationale and alternatives are recorded in
  `docs/adr/0001-github-release-self-update.md`.
- **Rejected:** silent install via `PackageInstaller` (`USER_ACTION_NOT_REQUIRED`) - it lets
  the OS kill the app during the install and would close a live browsing session; parsing
  the `versionCode` out of the asset name or out of the downloaded APK - both add release
  ceremony or a full download before the app can tell whether the release is newer.
- **Lesson candidate:** the Kotlin analysis server serves a stale module snapshot, so its
  "Unresolved reference" errors on freshly added files and resources are false positives -
  recorded as lesson 12 in `LESSONS.md`.
- **Guardrail update:** none needed in `AGENTS.md`; the release convention (tag
  `v\<versionName\>`, APK asset in the release, same signing key) lives in the ADR.

## Device Validation

Not performed. The mechanism depends on a published GitHub release, the "install unknown
apps" grant and a real install of the release APK, which cannot be produced from this
environment. The logic is covered by JVM/Robolectric tests; the installer hand-off and the
cold-start install still need one manual pass on a device with a real release attached.
