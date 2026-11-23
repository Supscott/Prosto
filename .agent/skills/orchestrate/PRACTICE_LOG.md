# Orchestrate: practice log

The evidence behind the rules in `SKILL.md`. It is not loaded with the skill: read it when you
change a rule, and add an entry when you do.

How to keep it useful:
- One entry per finding: the date, what was measured or what failed, the numbers
  (`tools/agent_cost.py` units, turns), and `→` the rule it changed. Newest first.
- **Prune stale details.** They mislead more than they help. Delete a detail once it no longer
  helps judge a rule: a bug since fixed in a tool, a file, port or flag that has changed, a fact
  about one issue. When a rule is replaced, update or delete the entry that justified the old
  rule. When a rule is settled and its numbers are in the skill's *Baselines*, shorten the entry
  to one line or drop it.
- Keep the file under ~60 lines.

## Entries
- 2026-11-23 — PRO-95…105 (11 issues): Sonnet downloads 489k/41 turns, UI 198k/20, mover 231k/18,
  Haiku harness 484k/44, device 572k/97, Opus orchestrator 2179k/72. First `prosto-reviewer` run
  (158k, 11 turns): 4 of its findings fixed (boxing on the main thread, TalkBack stops, unconsumed
  hint, name limit), 1 rejected (`isTrusted` would drop names on `a.click()`). → Keep step 6.0.
  One failed test made the next full runs report 435 and 266 false failures until
  `app/build/test-results/testDebugUnitTest` was deleted (PRO-112).
- 2026-11-22 — PRO-93 (Sonnet, 59 turns, 913k) hung its test JVM twice at 0% CPU (its test used
  the TestScope as the engine scope; a `withContext(Main)` hop deadlocked the Robolectric looper)
  and held the Gradle lock ~26 min, so PRO-94 (Sonnet, 35 turns, 436k) waited; the user saw it
  first. Haiku PRO-91+92: 246k. After the user asked, both agents committed after their own tests
  and one `verify.sh --all` on main passed 1671/1671. → "One shared test run" rule; PRO-97
  (Gradle test timeout).
- 2026-11-20 — Main-session transcript of the 2026-11-20 orchestration (`agent_cost.py` works on
  it): 189 turns, 40M cache reads, 4950k Opus units. Of 228 Bash calls about half were device
  commands (`device.sh`/`smoke.sh`/adb); of 92 edits, 80 were skills, docs and `tools/`, 13 were
  `app/`. Per `/cost`, one main call cost ~$0.067, more than the whole Haiku run ($0.04).
  → `prosto-device` agent (Sonnet) runs the checks; no late harness edits in the orchestrator,
  batch them to one Haiku agent; measure the orchestrator from its transcript. Not measured
  yet: the first `prosto-device` run's cost and whether the auto-mode classifier lets a
  subagent run adb.
- 2026-11-20 — `/cost` for the whole session: $14.43, of which the Opus main session $11.25
  (78%): 35.4M cache-read tokens over 169 requests, about 210k of context per call. Sonnet agents
  $3.13, Haiku $0.04. The orchestrator, not the agents, was the cost; it did device checks, two
  small fixes and harness edits late, at full context, and took three unrelated follow-up tasks
  without a /clear. Per the usage report, unused skills added ~15k tokens per request.
  → Measure the orchestrator; small fixes in the main session only while its context is small;
  /clear after the pipeline; the `prosto-coder` agent type holds the standing brief rules;
  unused plugins and skills turned off in `.claude/settings.local.json`.
- 2026-11-20 — PRO-84 and PRO-86 built at the same time on the 8 GB machine: a 34 min preview
  run, a Kotlin OOM, a stall the user saw; 932k and 989k units, driven by build length (Yield
  was the cheapest way to wait). After the lock, PRO-82 ran with no OOM; the main session's
  install queued 125 s. → `tools/gradle_lock.sh` in verify/preview/install.
- 2026-11-20 — The phone was on battery (`dumpsys battery`: no USB/AC power) over wireless adb,
  so `stayon usb` had no effect, and its 60 s screen timeout locked it while an install waited
  for the lock. → `wake` raises `screen_off_timeout`; `sleep` restores it.
- 2026-11-20 — PRO-83 (new prompt card + 5 dialogs moved onto it), Sonnet, 24 turns, 519k. Three
  fixes after the agent: the dim over the dock (my brief said "whole screen"; the user disliked
  it on the phone), a lone primary button wrapped onto its own line (the brief said only "wrap"),
  grey text in dark theme (the agent skipped the dark PNG). All three would have shown up in one
  preview before the migration. → Mock first for new visual components, edge-case layout in the
  brief, visual self-check in the template.
- 2026-11-20 — PRO-77…80 with pre-located briefs: numbers in *Baselines*; a research agent as a
  third parallel agent cost no memory. Its follow-ups (stale classes, FLAG_SECURE) became PRO-81/82.
- 2026-11-19 — PRO-75 incognito (35 files) on one Sonnet agent: 79 turns, 2008k units. Haiku did
  two harness issues (PRO-70+71) for 375k. Green tests plus lint missed that
  `ProfileStore.deleteProfile` refuses any profile loaded in this process; the brief's design
  assumed it worked. The agent's "platform assumptions" list named it, and the device check
  confirmed it. → Check platform API contracts before designing; split features of more than
  25 files.
- 2026-11-19 — Device checks found 3 bugs that 1572 green tests missed: quick tiles clipped at font
  scale 1.15 (fixed height), the per-site exclusion skipped every userInitiated offer (link taps are
  userInitiated), the folder chip covered the open menu. → UI brief rules; never mark a UI issue
  Done before the phone check.
- 2026-11-19 — PRO-66 polled its background Gradle 20 times with Monitor (593k, repeated
  hand-backs); PRO-64 chased 188 failures caused by a concurrent Gradle run (902k). → Foreground
  Gradle, LOW_MEM=1 rerun instead of chasing; the first sign of what the Gradle lock now prevents.
  Worktrees started 18 commits behind main → `git merge --ff-only main` first.
- 2026-11-18 — PRO-51…60: the token-discipline block cut Sonnet runs to 328k from 462–773k; a
  continued agent ~750k; Haiku harness correct (→ now in `prosto-coder.md` and *Baselines*).
- 2026-11-18 — `--changed` missed related tests (→ `related_tests.py`, `--all` after the last
  merge); an agent put a BROWSABLE debug filter in the main manifest (→ review step 6.1).
