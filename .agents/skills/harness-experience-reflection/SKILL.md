---
name: harness-experience-reflection
description: "Harness² Harnessing-Level Recursion: run at the end of a task or after a hard bug to extract evidence-backed lessons into LESSONS.md, manage the lesson budget and graduation policy, and update the agent guardrails. Trigger words: lesson, lessons, guardrail, reflection, retrospect, what went wrong, postmortem."
license: MIT
---

# Harness² Experience Reflection (Harnessing-Level Recursion)

Based on Google Research's **Harness²** framework, **harnessing-level recursion** updates a
persistent *improvement harness* with lessons and refined guardrails extracted from execution
traces. Without it every task starts from the same harness, which is what "the agent never
improves" looks like.

Agent memory is bounded and high-signal (`LESSONS.md`, maximum 10-12 active lessons). The
reflector makes sure runtime discoveries become part of the operating environment without
bloating the context window.

## When to Run

- **Mandatory**: as the last step of the Definition of Done, before declaring a task complete.
- **On demand**: after diagnosing a hard bug, an unexpected tool or engine behaviour, a flaky
  test, or a visual artifact you could not explain.
- **Trigger**: "I lost 30 minutes to something that is not written down anywhere."

## The 5-Step Protocol

### 0. Collect the Evidence

Do not reflect from memory. Run the gate and read what it produced:

```bash
python3 tools/harness_gate.py --feature <feature> --hypothesis "..."
```

It writes `runs/<feature>/iter-NN/telemetry.json` (check results, deltas, findings) and
`runs/<feature>/iter-NN/ledger.md` (probes and verdict). The reflection is grounded in that
telemetry, and the ledger records the *adopted harness change* - the channel `LESSONS.md` does
not cover.

### 1. Audit the Memory State

```bash
python3 tools/harness_reflect.py audit
```

- `< 10` active lessons: room for new ones.
- `10-12`: near saturation, look for a lesson to graduate.
- `> 12`: hard violation, graduate before adding.

### 2. Check Graduation Eligibility

A lesson can be retired when it became impossible to trigger: covered by an automated test, an
assertion, a type, a linter rule, or a new invariant in the architecture document.

```bash
python3 tools/harness_reflect.py graduate <LESSON_ID> --reason "covered by tests/test_x.py"
```

The lesson moves to the archive file with a timestamp and the remaining lessons are renumbered.

### 3. Extract High-Signal Lessons

A lesson is worth writing when it is **evidence-backed** (it happened, with logs, a stack trace
or a capture) and **not obvious** (a reader would have hit the same wall). Structure it as
**Cause**, **Do**, **Don't**.

```bash
python3 tools/harness_reflect.py add \
  --title "Short descriptive title" \
  --cause "The exact mechanical reason the thing behaved unexpectedly" \
  --do "The prescribed action and diagnostic technique" \
  --dont "The specific anti-pattern to avoid"
```

### 4. Propagate to the Guardrails

Route the learning to the right place (see `references/components.md` in the bootstrap kit):

| Learning | Store |
|----------|-------|
| A non-obvious gotcha | `LESSONS.md` (Cause/Do/Don't) |
| A rule for future work | the agent instructions file (`AGENTS.md`), one or two lines |
| A changed contract or invariant | `ARCHITECTURE.md` section, or a new ADR under `docs/adr/` |
| A repeatable procedure (3+ times) | a skill in the project skill directory |
| A new reference frame | the accepted baseline of the visual diff |

### 5. Accept the New Reference Frame

If the iteration intentionally changed the look of the product, accept it as the new baseline so
the next iteration does not report a false regression:

```bash
python3 tools/harness_gate.py --feature <feature> --update-baseline
```

## Pitfalls

- Do not write a lesson for a typo or an ordinary oversight; the budget is 12 slots and they are
  the most valuable ones you have.
- Do not put a procedure into `LESSONS.md`; procedures go into skills or the instructions file.
- Do not graduate a lesson that is still possible - graduation means "cannot happen again".
