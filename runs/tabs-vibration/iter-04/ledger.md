# Iteration 04 - tabs-vibration

- **Date:** 2026-10-21T20:10:00+00:00
- **Base ref:** `b209123` (HEAD, uncommitted working tree)
- **Hypothesis:** a two-axis review of the vibration + grid change found no behavioural
  defect, but the change was hard to review and several claims in
  `docs/adr/0002-platform-vibration-feedback.md` did not match the code. This iteration
  fixes the findings without changing the feature's behaviour.

## Probes

| Probe | Change tried | Expected effect |
| --- | --- | --- |
| A | Revert the ktlint-style reformat of `SearchEngineCarousel.kt`, `SearchEngineReorderList.kt`, `SearchOverlay.kt`, `MainActivity.kt` and 5 test files, keeping only the semantic edits (haptic -> vibration) | the diff shows the feature, not the formatter; diff shrinks from 2191 to ~500 changed lines |
| B | Extract the INTERACTION / vibration section into `ui/screens/SettingsInteractionSection.kt` and the tab preview card into `ui/overlays/TabOverviewGridCard.kt` | AGENTS.md §6.7 - the touched UI files stop growing and `TabOverviewGrid.kt` drops under 500 LOC |
| C | Extract `vibrateOnSlotChange` (`SearchEngineCarousel.kt`) and `historyItemQuery` (`SearchOverlay.kt`) | AGENTS.md baseline - the copy-pasted blocks collapse into one call site each |
| D | Add `BrowserViewModel.isVibrationEnabled` | the 4-level `uiState.value.settings.…` walk in `MainActivity` becomes a named accessor |
| E | Localize the remaining hardcoded section headers (`APPEARANCE`, `GENERAL`, `PRIVACY & PROTECTION`, `ABOUT & PROSTO`) | AGENTS.md §6.5 - no hardcoded user-facing string next to the new localized one |
| F | Add `TabOverviewGridPreviewTest` (3-columns-per-row assertion) and a `SettingsScreen` assertion for the vibration row | AGENTS.md §7.2 - the changed composables have visual coverage |
| G | Add `VibrationFeedbackTest` cases for the 20 ms / 50 ms durations and for the enabled predicate being re-read on every pulse | the behaviour iteration 02 adopted is actually pinned by a test |
| H | Correct ADR 0002 (scope, "single source of truth", instance lifetime, undocumented `NoVibrationFeedback` + test seam) | the decision record matches the code |

## Telemetry

| Check | Status | Detail |
| --- | --- | --- |
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 (584 testcases, 0 failures) |
| lint | PASS | `./gradlew :app:lintDebug` -> BUILD SUCCESSFUL |

Additional (not part of the gate):

| Check | Status | Detail |
| --- | --- | --- |
| diff size | PASS | `git diff HEAD` went from 2191 to 716 changed lines, of which 193 are the `GridTabCard` move |
| `GridTabCard` move | PASS | token-identical body, verified by comparison against the pre-move source |
| reformat removal | PASS | whitespace-stripped content of every rebuilt file is identical to the pre-revert content, and all string literals in the four re-formatted files are unchanged |

## Verdict

- **Adopted:** every probe above. The feature's behaviour is unchanged; only the diff shape,
  the file layout, the test coverage and the ADR text changed.
- **Rejected:** splitting `SettingsScreen.kt` further (its remaining 1289 LOC, and
  `TabStripRow.kt` 711 / `BrowserViewModel.kt` 929 / `SearchEngineCarousel.kt` 802 /
  `SearchEngineReorderList.kt` 782) - these are pre-existing AGENTS.md §6.7 violations that
  this change no longer worsens. They are repo-wide debt, not part of this feature.
- **Rejected:** localizing the remaining hardcoded settings strings (`"Ad Blocker"`,
  `"Protected"`, `"Search Engine"`, …) - pre-existing §6.5 violations outside the reviewed
  diff; only the section headers adjacent to the new row were in scope.
- **Lesson candidate:** the ktlint-style reformat in the previous iterations was not a
  deliberate style choice - it is what `pi-lens`'s ktlint autofix produces when a file is
  modified out of band, and the repository is not ktlint-clean at `HEAD` (825 violations in
  the touched files alone). It is therefore noise in a review, not a house style. Candidate
  for `LESSONS.md`, but the active lesson budget is still full (12/12), so it stays here.
- **Guardrail update:** none. The lesson budget is at its cap, so no new `LESSONS.md` entry
  was added in this iteration.
