# Iteration 04 - external-app-routing

- **Date:** 2026-10-20T09:53:35+00:00
- **Base ref:** `n/a`
- **Hypothesis:** fix review findings: two-tier suppression hierarchy, dismiss reason enum, clean dispatching, and scope hygiene

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | Replace boolean flags (`rememberDomain`, `timedOut`) with typed `ExternalAppDismissReason` enum (`EXPLICIT`, `TIMEOUT`, `TRANSIENT`) across `BrowserUiEvent`, `BrowserViewModel`, and `TabCoordinator` | Eliminates invalid state combinations and clarifies dismissal semantics across the pipeline |
| B | Fix suppression check hierarchy in `TabCoordinator`: explicit dismissal always suppresses, while timeout suppression is bypassed when `userInitiated == true`; deduplicate suppression check helper | Deliberate user gestures bypass timeout suppression without ignoring previous explicit dismissals |
| C | Encapsulate external routing check inside `JestkWebViewClient.loadUrlOrHandleExternal` helper | Removes feature envy / middle-man pattern from `JestkApp.kt` |
| D | Revert unrelated edits in `worktree-orchestrator/SKILL.md` and prune failed/intermediate iteration artifacts (`iter-01`, `iter-02`) | Enforces commit scope hygiene |
| E | Add regression tests in `TabCoordinatorTest` and `ExternalAppFlowTest` | Ensures explicit dismissals lock suppression against subsequent user gestures |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** Probes A, B, C, D, E. Clean enum-based dismissal reason, corrected suppression hierarchy with gesture bypass for timeouts only, encapsulated webview dispatching in `JestkWebViewClient.loadUrlOrHandleExternal`, regression tests in `TabCoordinatorTest` and `ExternalAppFlowTest`, and scope cleanup.
- **Rejected:** None.
- **Lesson candidate:** Covered by active `LESSONS.md` #11.
- **Guardrail update:** None needed. Regression tests lock the behavior.
