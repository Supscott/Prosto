# Iteration 02 - external-app-routing

- **Date:** 2026-10-20T09:24:28+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Adopt two-tier suppression (explicit dismiss vs timeout) so that auto-discharged hints don't poison explicit user gestures; pass user-gesture flag through the whole pipeline

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | | |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | FAIL | `python3 tools/capture.py` -> exit 1
	at java.rmi/sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(TCPTransport.java:720)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
	at java.base/java.lang.Thread.run(Thread.java:1575)


FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:kspDebugKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Internal compiler error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

BUILD FAILED in 10s |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** _TODO_ (what from this iteration is kept)
- **Rejected:** _TODO_ (what was tried and dropped, with evidence)
- **Lesson candidate:** _TODO_ (a gotcha worth remembering - where does it go?)
- **Guardrail update:** _TODO_ (a rule for future work - where does it go?)
