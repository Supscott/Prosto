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
- 2026-11-26 — 7 issues, orchestrator 51 turns/1023k Opus units. The menus agent (PRO-194/197/192:
  data events + UI, 31 files, mock → wiring → review fix-up) ran 105 turns/2099k Sonnet units, 196
  Edits. Comparable single-feature runs: 356k (PRO-196), 814k (PRO-193/195). → a data+UI feature
  over ~25 files goes to two agents even after a mock (SKILL.md step 4; it was skipped). Two items
  of PRO-177 were already on main (9b7e95f); the agent found that only after reading the code. →
  before briefing, `git log --oneline -S<API name>` for each named fix. Device runs of 7 and 4
  checks: 301k/71 turns and 221k/61 turns.
- 2026-11-26 — Orchestrator 57 turns, 1245k Opus units for 7 issues (2026-11-20: 4950k).
  `prosto-reviewer` on a 1177-line branch (PRO-172): 8 turns, 115k, 2 real bugs → review every
  Kotlin branch over ~100 lines. `prosto-device` with 13 checks: 1237k; 6 checks 114k; a 2-check
  recheck 57k → one device run under ~8 checks. A device check found what no unit test could
  (WebView getUserMedia failed until MODIFY_AUDIO_SETTINGS was declared) → PRO-186.
- 2026-11-25 — Grouping 2–3 small issues per Sonnet agent: 67–68 turns, ~470–700k per issue.
  Haiku harness agent (2 issues): 758k, 31 turns. Device agents 152–304k; a BLOCKED run (locked
  phone) costs ~19k. Miss: a coder that ran only the default verify set let a lint crash through
  (PRO-180) → coders finish with `verify.sh --changed`.
- 2026-11-25 — PRO-140…160: Haiku harness 89–313k; Sonnet 260–375k for small UI issues; Sonnet
  1082k/62 turns for 3 unrelated test issues, one hung a Gradle worker (→ PRO-164). → Group only
  issues that share files; a fresh small Haiku agent per follow-up kept fixes cheap.
- 2026-11-23 — PRO-95…105: Sonnet downloads 489k/41 turns, UI 198k/20; Haiku harness 484k/44;
  device 572k/97; Opus orchestrator 2179k/72. First `prosto-reviewer` run (158k, 11 turns): 4 of
  5 findings fixed, 1 rejected (`isTrusted` would drop names on `a.click()`) → keep the review step.
- 2026-11-22 — PRO-93 (Sonnet, 59 turns, 913k) hung its test JVM twice (TestScope used as the
  engine scope) and held the Gradle lock ~26 min → "One shared test run" rule; PRO-97.
- 2026-11-20 — Orchestrator transcript: 189 turns, 4950k; half its Bash calls were device commands
  → `prosto-device` agent runs the checks. `/cost` $14.43, Opus main session $11.25 (~210k context
  per call).
- 2026-11-20 — Two builds at once on the 8 GB machine: a 34 min preview run and a Kotlin OOM
  (932k and 989k units) → `tools/gradle_lock.sh` in verify, preview and install.
- 2026-11-20 — On battery over wireless adb, `stayon usb` did nothing and the screen locked
  mid-install → `wake` raises `screen_off_timeout`; `sleep` restores it.
- 2026-11-20 — PRO-83 (new prompt card, Sonnet, 24 turns, 519k): three visual fixes after the
  agent → mock first for new visual components; edge-case layout in the brief.
- 2026-11-20 — PRO-77…80 with pre-located briefs; a research agent as a third parallel agent cost
  no memory. → pre-located briefs (*Baselines*).
- 2026-11-19 — PRO-75 incognito (35 files), one Sonnet agent: 79 turns, 2008k units; its design
  assumed `ProfileStore.deleteProfile` worked on a loaded profile, which it refuses → check
  platform API contracts before the design; split >25 files.
- 2026-11-19 — Device checks found 3 bugs that 1572 green tests missed → never mark a UI issue
  Done before the phone check.
- 2026-11-19 — Background Gradle polled 20 times (593k); a concurrent Gradle run caused 188
  failures (902k) → foreground Gradle, LOW_MEM=1 rerun instead of chasing.
- 2026-11-18 — PRO-51…60: the token-discipline block cut Sonnet runs to 328k from 462–773k;
  `--changed` missed related tests (→ `related_tests.py`).
