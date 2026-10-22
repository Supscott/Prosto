# Iteration 01 - tech-debt-audit

- **Date:** 2026-10-22T12:16:20+00:00
- **Base ref:** `main`
- **Hypothesis:** close the open findings of the 2026-10-12 tech-debt audit: NEW-ARCH-02, NEW-TYPE-01, NEW-ARCH-05, NEW-DEP-01, plus the Prosto Canvas naming and resource hygiene

## Probes

| Probe | Change tried | Expected effect |
|---|---|---|
| A | Decouple the ad-blocker engine from persistence: replace the mutable `SettingsRepository` field on `AdBlockerEngine` with the narrow `domain/repository/BlockedRequestStats` contract, implemented once by `data/repository/SettingsBlockedRequestStats` | the engine layer no longer holds a Room-backed repository; the mapping from a suppressed request to the stored counters lives in one place in the data layer |
| B | Carry the external-app icon as bytes: `domain/model/ExternalAppPrompt` loses `android.graphics.Bitmap` and gains `domain/model/AppIcon` (PNG bytes, `contentEquals`/`contentHashCode`); the `Drawable -> Bitmap` step is replaced by `Drawable -> AppIcon` in the engine, and the `Bitmap` is decoded only in the presentation layer | the domain layer stays free of every `android.*` import without losing the icon |
| C | Remove `Context` from the UI event contract: `BrowserUiEvent.CheckDefaultBrowser(context)` is deleted, `BrowserViewModel.checkDefaultBrowser()` goes with it, and the callers resolve the status themselves and dispatch `UpdateDefaultBrowserStatus(isDefault)` | no UI event carries an Android `Context`; the ViewModel layer no longer imports `android.content.Context` |
| D | Remove the deprecated WebView APIs: the no-op `WebSettings.databaseEnabled = true` and both `setForceDark` / `setForceDarkStrategy` blocks are deleted; dark mode runs on `setAlgorithmicDarkeningAllowed` plus the injected colour-scheme script | no deprecated WebSettings calls remain, and dark mode keeps working |
| E | Prosto Canvas naming and resource hygiene: rename the remaining `Serene*` design tokens to `Prosto*`, drop the dead "Liquid Sunset" alias composables, rename the switch to `ProstoToggleSwitch`, and remove the unused colour/string resources | one name for the design system, and no lint-flagged dead resources |
| F | Harden the domain-purity guard: `tools/check_invariants.py` and `DomainArchitectureTest` now reject *every* `import android.*` in `domain/` instead of an enumerated subset of packages | a `android.graphics` import can no longer slip past the guard — the exact hole NEW-TYPE-01 came through |
| G | Verify the 2026 toolchain upgrade path in a throwaway probe outside the repository (AGP 9.4.1 + Gradle 9.8.2 + built-in Kotlin 2.2.10 + KSP 2.3.12 + Room 2.8.5 + Compose BOM 2026.09.00 + `compileSdk 37`/`compileSdkMinor 2`) and record the result in `docs/adr/0003-2026-toolchain-upgrade-path.md` | the forward-compatibility question is answered with evidence instead of a version guess, and the cut-over is deliberately not applied |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** the probe set above. Iteration 02 re-ran the same gate after the concurrent
  naming pass landed and confirmed the result still holds (606 tests, 0 failures; visual diff
  pixel-identical).
- **Rejected:** probe G as a *cut-over*; see ADR 0003 for the two blockers (KSP `2.2.10-2.0.2`
  emits the `kotlin.sourceSets` DSL that AGP 9 forbids, and `compileSdk 37` needs a separate
  `compileSdkMinor = 2`).
- **Lesson candidate:** lesson 12 (ByteArray value semantics) went to `LESSONS.md`.
- **Guardrail update:** the domain-purity guard now forbids all `android.*` imports.
