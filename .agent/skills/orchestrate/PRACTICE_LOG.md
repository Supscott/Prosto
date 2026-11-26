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
- 2026-11-26 — Orchestrator 57 turns, 1245k Opus units for 7 issues (2026-11-20: 189 turns,
  4950k). `prosto-reviewer` on a 1177-line branch (PRO-172): 8 turns, 115k Sonnet, 9 findings,
  2 real bugs (both fixed by a SendMessage fix-up) → review every Kotlin branch over ~100 lines.
  `prosto-device` with 13 checks: 187 turns, 1237k Sonnet; 6 checks 29 turns/114k; a 2-check
  recheck 16 turns/57k → one device run under ~8 checks. Coders: PRO-172 feature (26 files)
  54 turns 1159k; PRO-179 menu item 34 turns 656k; tests PRO-177/178 50 turns 753k; README
  19 turns 271k; Haiku device.sh harness 37 turns 748k. A device check found what no unit test
  could (WebView getUserMedia failed until MODIFY_AUDIO_SETTINGS was declared) → permission
  checks reset the user-fixed flag between runs (PRO-186).
- 2026-11-25 — Grouping 2–3 small issues per Sonnet agent: 67–68 turns, ~470–700k per issue.
  Haiku harness agent (2 issues): 758k, 31 turns. Device agents 152–304k; a BLOCKED run (locked
  phone) costs ~19k. Miss: a coder that ran only the default verify set let a lint crash through
  (PRO-180) → coders finish with `verify.sh --changed`.
- 2026-11-25 — PRO-140…160: Haiku harness 89–313k; Sonnet 260–375k for small UI issues; Sonnet
  1082k/62 turns for 3 unrelated test issues, one hung a Gradle worker (→ PRO-164). → Group only
  issues that share files; a fresh small Haiku agent per follow-up kept fixes cheap.
- 2026-11-24 — PRO-106…139 (32 issues): Haiku harness 229–504k; Sonnet UI/data 189–817k; device
  833k/148 over 3 runs; Opus orchestrator 1665–2586k/72–87 turns. PRO-127's brief assumed
  `NO_HIDE_DESCENDANTS` hides views from TalkBack; it shipped green and failed on the phone
  (uncompressed dumps ignore importance) → a11y fetch flags in the brief. Reading Haiku harness
  diffs found 3 bugs their own checks missed (→ keep reading harness diffs). PRO-112's first fix
  passed one reproduction, then 232 failures came back: a race (concurrent native loads) → never
  close a flaky failure on one green run; prove the mechanism.
- 2026-11-23 — PRO-95…105: Sonnet downloads 489k/41 turns, UI 198k/20; Haiku harness 484k/44;
  device 572k/97; Opus orchestrator 2179k/72. First `prosto-reviewer` run (158k, 11 turns): 4 of
  5 findings fixed, 1 rejected (`isTrusted` would drop names on `a.click()`) → keep the review step.
- 2026-11-22 — PRO-93 (Sonnet, 59 turns, 913k) hung its test JVM twice at 0% CPU (the TestScope
  was used as the engine scope; a `withContext(Main)` hop deadlocked the Robolectric looper) and
  held the Gradle lock ~26 min; the user saw it first → "One shared test run" rule; PRO-97.
- 2026-11-20 — The orchestrator's own transcript: 189 turns, 40M cache reads, 4950k units; half
  of its 228 Bash calls were device commands, and 80 of 92 edits were skills, docs and `tools/`
  → `prosto-device` agent runs the checks; late harness edits go to one Haiku agent. Session
  `/cost` $14.43, of which the Opus main session $11.25 (~210k context per call); Haiku $0.04.
- 2026-11-20 — Two builds at once on the 8 GB machine: a 34 min preview run and a Kotlin OOM
  (932k and 989k units) → `tools/gradle_lock.sh` in verify, preview and install.
- 2026-11-20 — On battery over wireless adb, `stayon usb` had no effect and the 60 s screen
  timeout locked the phone mid-install → `wake` raises `screen_off_timeout`; `sleep` restores it.
- 2026-11-20 — PRO-83 (new prompt card, Sonnet, 24 turns, 519k): three fixes after the agent (a
  dim over the dock the user disliked, a lone button wrapped onto its own line, grey text in dark
  theme). One preview before the migration would have shown all three → mock first for new
  visual components; edge-case layout in the brief.
- 2026-11-20 — PRO-77…80 with pre-located briefs: a research agent as a third parallel agent
  cost no memory. → pre-located briefs (*Baselines*).
- 2026-11-19 — PRO-75 incognito (35 files) on one Sonnet agent: 79 turns, 2008k units; its
  design assumed `ProfileStore.deleteProfile` worked for a loaded profile, which it refuses; the
  device check confirmed it → check platform API contracts before the design; split >25 files.
- 2026-11-19 — Device checks found 3 bugs that 1572 green tests missed (quick tiles clipped at
  font scale 1.15; per-site exclusion skipped link taps; folder chip covered an open menu) →
  never mark a UI issue Done before the phone check.
- 2026-11-19 — Background Gradle polled 20 times with Monitor (593k); a concurrent Gradle run
  caused 188 failures (902k) → foreground Gradle, LOW_MEM=1 rerun instead of chasing.
- 2026-11-18 — PRO-51…60: the token-discipline block cut Sonnet runs to 328k from 462–773k; a
  continued agent ~750k; Haiku harness correct. `--changed` missed related tests (→
  `related_tests.py`); an agent put a BROWSABLE debug filter in the main manifest (→ review step 6.1).
