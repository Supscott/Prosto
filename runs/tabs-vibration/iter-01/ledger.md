# Iteration 01 - tabs-vibration

- **Date:** 2026-10-21T19:18:11+00:00
- **Base ref:** `n/a`
- **Hypothesis:** tabs grid becomes 3 columns; vibration uses the platform vibrator with a settings toggle

## Probes

| Probe | Change tried | Expected effect |
| --- | --- | --- |
| A | `TabOverviewGrid`: `GridCells.Fixed(2)` -> `Fixed(3)`, gutter 14px -> 10px, card composable renamed `TwoColumnGridTabCard` -> `GridTabCard` | 3 preview cards per row on a 412dp screen, 4:5 card aspect ratio unchanged |
| B | `VibratorVibrationFeedback` on `android.os.Vibrator` (one-shot effects) instead of Compose `HapticFeedback` | pulses are produced even when the system touch-feedback setting is off |
| C | `isVibrationEnabled` persisted in the settings table, surfaced as an `INTERACTION` section toggle | the user can switch the feedback off |

## Telemetry

| Check | Status | Detail |
| --- | --- | --- |
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

Additional (not part of the gate):

| Check | Status | Detail |
| --- | --- | --- |
| `:app:lintDebug` | PASS | BUILD SUCCESSFUL |
| `:app:composePreviewRenderAll` | PARTIAL | 23/24 previews rendered; `activity__MainActivity` fails with a pre-existing `InvalidEchDataException` classpath error, unrelated to this change |
| visual review | PASS | `TabOverviewGridLightPreview` renders 3 columns; `SettingsScreenLightPreview` shows the new *Vibration* row under *INTERACTION* |

## Verdict

- **Adopted:** 3-column tab overview grid; platform-vibrator feedback with a persisted
  *Vibration* toggle; `VibrationFeedback` (`tick()` / `confirm()`) replaces the Compose
  `HapticFeedback` contract at every call site.
- **Rejected:** `VibrationEffect.createPredefined(EFFECT_TICK / EFFECT_HEAVY_CLICK)` - the
  primitives are not supported on every device and the call throws there; one-shot
  effects work everywhere. Making the enabled flag Compose state - the instance is
  long-lived and captured by gesture callbacks, so snapshot state would force the
  gesture scopes to be recreated on every toggle. Caching the flag on the instance and
  syncing it from a `SideEffect` - the effect only runs when its composition recomposes,
  and a composition that reads the preference only inside the effect is never invalidated
  by it, so the flag would stay stale.
- **Lesson candidate:** Compose `HapticFeedback` is gated by the system touch-feedback
  setting and silently no-ops - drive `Vibrator` directly for guaranteed feedback. The
  active lesson budget is full (12/12), so this is recorded in
  `docs/adr/0002-platform-vibration-feedback.md` instead of `LESSONS.md`; graduate an
  older entry before promoting it.
- **Guardrail update:** none. The lesson budget is at its cap, so no new `LESSONS.md`
  entry was added in this iteration.
