---
name: harness-propose-probe-compose
description: "Harness² Task-Level Recursion: for complex or visually sensitive tasks, propose 2-3 competing approaches, probe them in parallel isolated subagents, and compose the best result from measured contrasts instead of guessing. Trigger words: approach, alternative, trade-off, probe, A/B, which implementation, try two ways."
license: MIT
---

# Harness² Propose–Probe–Compose (Task-Level Recursion)

**Task-level recursion** refines the execution approach for the current task through a
contrastive **Propose–Probe–Compose** step: propose approaches, probe their effects on the
same task, and compose the refined result from behavioural contrasts - without ground truth.

Use it when a single linear attempt would burn the main context window on trial and error:
procedural geometry, shader mathematics, physics on non-Euclidean surfaces, coordinate
wrapping, performance-critical systems, or anything visual that is hard to judge from a test
result.

Do **not** use it for boilerplate, small UI tweaks, or configuration changes - one subagent is
faster and cheaper there.

## Phase 1: Propose

1. Break the difficulty into 2-3 genuinely different technical approaches.
   - Example (seam smoothing): A) explicit vertex stitching during mesh generation,
     B) shared normal-averaging pass across chunk borders, C) analytic seam displacement.
2. Define the shared verification harness before probing: the same test command, the same
   capture command, and the same gate invocation for every approach:

   ```bash
   python3 tools/harness_gate.py --feature <feature>-probe-a --hypothesis "approach A"
   ```

3. State the acceptance criteria quantitatively: suite green, invariants green, and a reported
   visual delta (or a measured runtime cost) that separates the approaches.

## Phase 2: Probe (Parallel, Isolated)

Dispatch the approaches concurrently, each in its own workspace or branch, so the probes never
collide with the main tree:

```json
{
  "mode": "parallel",
  "tasks": [
    {
      "agent": "self",
      "task": "Implement approach A in <files>. Add tests in <test file>. Run `python3 tools/harness_gate.py --feature <feature>-probe-a --hypothesis \"approach A\"` and report the telemetry table plus the diff image path."
    },
    {
      "agent": "self",
      "task": "Implement approach B in <files>. Add tests in <test file>. Run `python3 tools/harness_gate.py --feature <feature>-probe-b --hypothesis \"approach B\"` and report the telemetry table plus the diff image path."
    }
  ]
}
```

Each probe reports the same telemetry shape, so the comparison is apples to apples.

## Phase 3: Contrast & Compose

Contrast on measured evidence, not on taste:

| Axis | Evidence |
|------|----------|
| Correctness | the gate reported the suite green |
| Invariants | the `lint`/invariant steps stayed green |
| Runtime cost | measured time or frame time of the probe |
| Visual impact | the reported visual delta plus the diff image (`runs/<feature>/iter-NN/shots/<shot>.diff.png`) - inspect it to see *where* frames differ |
| Fit | which result matches the existing structure of the codebase |

Then compose:

- Port the winning approach into the main tree; keep the complementary pieces of the runner-up
  (usually its edge-case tests).
- Re-run the gate on the composed result.
- Record the probes and the verdict in the iteration ledger, and the accepted frame as the new
  baseline.
