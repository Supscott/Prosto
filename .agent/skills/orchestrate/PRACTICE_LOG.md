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
- 2026-11-20 — PRO-84 and PRO-86, two Sonnet UI agents built at the same time on the 8 GB machine:
  one preview run took 34 min, one Kotlin OOM, and the user saw the machine stall. Costs: 932k
  (41 turns) and 989k (56 turns), each with a mock-first review and one fix-up, plus 7 and 12
  Yield turns waiting on slow Gradle. After the repo-wide Gradle lock, PRO-82 (Sonnet) cost 244k in
  18 turns, and its build queued behind the main session's run with no OOM. PRO-81 (Haiku, no
  Gradle): 274k. → `tools/gradle_lock.sh` in verify/preview/install; parallel Gradle agents now
  overlap only while reading and editing.
- 2026-11-20 — Over wireless adb, `stayon usb` does nothing, and the 60 s screen timeout locked the
  phone while an install waited for the lock. → `wake` raises `screen_off_timeout`; `sleep` restores it.
- 2026-11-20 — PRO-83 (new prompt card + 5 dialogs moved onto it), Sonnet, 24 turns, 519k. Three
  fixes after the agent: the dim over the dock (my brief said "whole screen"; the user disliked
  it on the phone), a lone primary button wrapped onto its own line (the brief said only "wrap"),
  grey text in dark theme (the agent skipped the dark PNG). All three would have shown up in one
  preview before the migration. → Mock first for new visual components, edge-case layout in the
  brief, visual self-check in the template.
- 2026-11-20 — PRO-77…80, four parallel agents with pre-located briefs: Sonnet UI feature (PRO-80)
  22 turns 341k; Sonnet data+UI feature (PRO-78, 21 files) 27 turns 473k; Haiku shell harness
  (PRO-77) 253k; Sonnet research without Gradle (PRO-79) 134k. A research agent as a third
  parallel agent cost nothing extra in memory. After both merges `--all` showed 298 stale
  `NoSuchMethodError`s (→ step 6.5 note, PRO-81). Incognito sets FLAG_SECURE, so `shot` is black:
  colour checks of incognito UI go to the user (PRO-82).
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
- 2026-11-19 — 4 Sonnet agents + refactor-mover: refactor-mover with a written plan 165k (13 turns);
  settings UI 413k; PRO-66 593k (20 Monitor polls of its own background Gradle → repeated
  hand-backs); PRO-64 902k (a data+UI feature, plus chasing 188 failures that came from a concurrent
  Gradle run; `--all` on main was 1572/1572). → Foreground Gradle; rerun mass native errors once
  with LOW_MEM=1 instead of chasing them. A written move plan makes refactor-mover the cheapest run.
- 2026-11-19 — Agent worktrees started 18 commits behind local main (unpushed work), so agents
  lacked the files the brief named. → The brief template starts with `git merge --ff-only main`.
- 2026-11-18 — 9 issues (PRO-51…60): Haiku harness scripts 591k Haiku units ≈ 1/3 of the Sonnet
  price, correct; Sonnet features 462k/773k without the token-discipline block, 328k with it
  (20 turns, 0 whole-file Reads); a continued agent given a second task ~750k. → Discipline block
  in every brief, pre-located files, fresh agents, Haiku for scripts.
- 2026-11-18 — `--changed` missed related tests (→ `related_tests.py`, `--all` after the last
  merge); an agent put a BROWSABLE debug filter in the main manifest (→ review step 6.1).
