# Iteration 01 - rename-prosto

- **Date:** 2026-10-20T18:05:17+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Rename the user-facing browser name from Hori to Prosto

## Probes

| Probe | Change tried                                                                                                                                                                                               | Expected effect                                                                           |
| ----- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| A     | Renamed every user-facing "Hori" literal to "Prosto": app label in both locales, default-browser prompts, settings about row, tab title fallback, download notifications, ad-block placeholder, User-Agent | The launcher label, settings, notifications and tab chrome all carry the new product name |
| B     | Left internal identifiers (class names, JS bridge, log tags) and dated audit archives alone                                                                                                                | No risky code churn; the rename stays user-facing only                                    |

## Telemetry

| Check      | Status | Detail                                                                                   |
| ---------- | ------ | ---------------------------------------------------------------------------------------- |
| invariants | PASS   | `python3 tools/check_invariants.py` -> exit 0                                            |
| tests      | PASS   | `./gradlew :app:testDebugUnitTest` -> exit 0                                             |
| capture    | PASS   | `python3 tools/capture.py` -> exit 0                                                     |
| visual     | PASS   | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory     | PASS   | `python3 tools/harness_reflect.py audit` -> exit 0                                       |

## Verdict

- **Adopted:** Probe A - all user-facing name literals now read Prosto.
- **Rejected:** Probe B - renaming internal identifiers (HoriDownloadService, HoriAdBlockBridge, log tags) and editing dated audit archives; the request was the browser name, not the codebase identifiers.
- **Lesson candidate:** None.
- **Guardrail update:** None.
