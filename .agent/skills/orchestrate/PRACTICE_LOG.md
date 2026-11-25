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
- 2026-11-25 — PRO-140…160 (11 Todo + 10 follow-ups closed or merged; phone absent, so 9 wait
  in In Review). Haiku harness 89–313k/8–25 turns (one 3-issue split 610k); Sonnet 260k (one pill
  issue), 375k (3 small UI issues); Sonnet 1082k/62 turns for 3 unrelated test issues, one of
  which hung a Gradle worker on an endless spinner (→ PRO-164). Opus orchestrator 1590k/74 turns,
  half of the calls Linear writes. → Group only issues that share files; filing each agent's
  findings and launching a fresh small Haiku agent per follow-up kept every fix cheap. Two
  AGENTS.md rebase conflicts (parallel edits to adjacent bullets) were resolved by a scripted merge.
- 2026-11-24 — PRO-121…139 (15 Todo issues closed, 1 already done): Sonnet 189–817k (UI pair
  233k/25, Room test 189k/17), PRO-121 SDK move 1016k with 308 Edits over 74 files, Haiku harness
  485–504k, device 833k/148 over 3 runs (one BLOCKED on a locked phone), Opus orchestrator
  1665k/72. PRO-127's brief assumed `NO_HIDE_DESCENDANTS` suffices; it shipped green and failed
  on the phone (uncompressed dumps ignore importance); a SendMessage fix-up + TEMP log fixed it.
  Reading harness diffs again found 2 bugs (a leaked temp dir, a `*preview*` name filter that
  hid most PNGs). → Skill step 4: a11y fetch flags; scripted step for mass mechanical edits.
- 2026-11-24 — PRO-106…124 (17 issues closed): Haiku harness 229–459k/20–39 turns, Sonnet UI and
  data 325–764k, mover 359k, reviewer 127k/9, device 618–671k/113–121, Opus orchestrator
  2586k/87. Reading the Haiku harness diffs found 3 bugs their own checks missed (a pathspec that
  skipped 2 of 3 tests, `tapui ""` tapping a switch, a saved timeout lost on an adb failure).
  PRO-112's first fix passed its reproduction and was closed, then 232 failures came back: the
  real cause was a race (concurrent native loads), found by a Sonnet run (1150k/82). → Keep
  reading harness diffs; never close a flaky failure on one green reproduction, repeat it and
  prove the mechanism.
- 2026-11-23 — PRO-95…105 (11 issues): Sonnet downloads 489k/41 turns, UI 198k/20, mover 231k/18,
  Haiku harness 484k/44, device 572k/97, Opus orchestrator 2179k/72. First `prosto-reviewer` run
  (158k, 11 turns): 4 of its findings fixed (boxing on the main thread, TalkBack stops, unconsumed
  hint, name limit), 1 rejected (`isTrusted` would drop names on `a.click()`). → Keep step 6.0.
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
