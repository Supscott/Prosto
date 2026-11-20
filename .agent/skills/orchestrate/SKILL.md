---
name: orchestrate
description: "Work through the Linear Todo queue (team PRO) as an orchestrator: triage the issues, ask the user's decisions up front, delegate to subagents in parallel worktrees with token-lean briefs, review, merge, verify on the phone and update Linear. Use when asked to implement Linear tasks, work the todo list, or orchestrate subagents."
---

# Orchestrate: Linear Todo → merged, verified work

> **Living skill.** This file records the best orchestration practice found so far. When a
> session finds a better one (cheaper, faster or safer, measured with `tools/agent_cost.py` or
> shown by a real failure), update the rule here, add an entry with the date and the evidence to
> `PRACTICE_LOG.md` (next to this file; read it first, prune stale details as its header says),
> and delete what the new rule replaces. Keep this file under 200 lines.

## Pipeline (run in this order when the skill is invoked)

### 1. Read the queue
- `list_issues(team="PRO", state="Todo", fields=["id","title","priority","labels"])` first: titles
  only, cheap. Then `get_issue(id, fields=["description"])` only for the issues you will work on.
- If the user named an order ("first PRO-58, PRO-59, then the rest"), keep it. Otherwise sort by
  priority, then put `verif` issues before unverified ones.
- Look at `git status` too: uncommitted user changes may belong to one of the issues. Ask, and
  if they do, save them as a patch (`git diff > <scratchpad>/x.patch`) for that issue's agent.

### 2. Triage each issue into one bucket
| Bucket | Sign | Action |
|---|---|---|
| Needs a decision | "нужно обсудить", vague UX, several valid behaviours | ask in step 3 |
| Ready | clear spec or confirmed bug with a code pointer | delegate |
| Needs the phone | repro steps on a device, unverified bug | do last, in the main session |
| Outward-facing | posts to GitHub or other services from the user's account | ask permission; never act alone |

### 3. Ask once, before delegating
One `AskUserQuestion` call with every open decision (up to 4 questions). Make options concrete
(read the code first: current values, current structure) and put the recommendation first. Check
whether the phone is connected (`adb devices`); if it is not, say so now so the user can plug it in
while the agents work.

### 4. Plan the delegation
- **Group by files.** Small issues touching the same files → one agent. A task that builds on
  another's new API (e.g. PRO-59 used PRO-60's swipe pill) waits for it and gets that API named
  in its brief.
- **Parallelism:** at most 2 agents that run Gradle at the same time (8 GB machine). Shell/Python
  harness agents that do not run Gradle can be a third.
- **Model:** Haiku for shell/Python harness scripts, Sonnet for Kotlin/Compose features, the main
  session (Opus) for design, review, merging, device checks and small fixes.
- **Fresh agent per task.** Do not hand a new task to a finished agent: its whole history is
  re-read on every turn (measured: ~750k units vs 330–460k fresh). SendMessage is for short fix-ups
  of the agent's own work.
- **Check platform API contracts before writing the design.** When the brief's design rests on a
  WebView/Android lifecycle call (profile deletion, cookie flush, destroy), read its reference
  page first: the brief is copied faithfully, so a wrong premise ships green and fails on the phone.
- **Split a feature that spans data, engine and UI** (>25 files) into two agents (data+engine,
  then UI with the first one's API named); one agent for all of it ran 79 turns.
- **Small fixes yourself.** A change of a few lines (a missing callback, a file-size squeeze, a
  manifest move) is cheaper in the main session than a new agent round.
- **Mock first for a new visual component or pattern** (not for tweaks that follow an existing
  one). The brief says: build the component plus one preview, commit, stop and report the PNG
  paths. Review the PNGs in the main session; ask the user only on a taste decision (e.g. dim the
  dock or not). Then continue the same agent with SendMessage (a short follow-up of its own work).
  Spell out edge-case layout in the brief: what wraps and how, what the dim covers, colours in
  both themes.

### 5. Brief template (Agent tool: `isolation: "worktree"`, `run_in_background: true`)
```
Implement Linear PRO-NN in the Prosto Android browser (Kotlin/Compose) in this worktree.
FIRST: `git merge --ff-only main` (the worktree may start from a stale commit; name the commit
the log must show after it).
<issue text + the user's decisions, pasted: do not make the agent call Linear>
Where to look (already located): <files, functions, line ranges, existing APIs to reuse>.
Design: <the decisions that are yours, not the agent's>. New code in new files; add only a call
line to shared host files (e.g. AppNotificationPills.kt, BrowserUiEvent.kt has a per-file rule).
Token discipline: Batch all independent Read/Grep/Edit calls of a step into ONE message. Map a big
file with `python3 tools/outline.py` and read it by offset/limit; never read a >300-line file
whole. Don't re-read a file after editing it, and don't re-run a passing check. Keep narration
minimal.
Rules: Edit/Write only, one command per Bash call (no && chains), prefix with rtk, English
comments, strings in both locales, baseline files cannot grow.
Verify: `rtk tools/verify.sh --changed` and `rtk tools/verify.sh` (`--ui` for UI work; render
previews with `tools/preview.sh` and look at the PNGs).
UI work: also render a preview at fontScale 1.3 (the user's phone is at 1.15); no fixed heights
around text. Name the real trigger of each event in the design (a link tap is userInitiated too).
Visual self-check: open EVERY PNG you render and compare it with the preview of the closest
existing component (e.g. the Menu card). Look for a lone element wrapped onto its own line,
secondary text that is hard to read in dark theme, and parts that look detached.
Run Gradle in the foreground (no run_in_background, no Monitor polling). If a run shows mass
Robolectric native errors (UnsatisfiedLinkError, SQLite open) across unrelated classes, it is the
shared machine: rerun once with `LOW_MEM=1`, then report; do not chase them.
Commit "<type>(<scope>): <summary> (PRO-NN)" + the Co-Authored-By line.
Report in <=12 lines: branch, files, test result lines, platform assumptions a device check must confirm.
```

### 6. Review and merge each branch as it finishes
1. `git diff --stat main...<branch>`, then read only the key files. Look for: release-build
   exposure (debug-only code in `src/main`, BROWSABLE intent filters), missing failure paths,
   leftovers the report admits ("not covered: …").
2. `git -C <worktree> rebase main` (linear history; resolve doc conflicts by hand: keep both edits).
3. If the main checkout has uncommitted user work in the same files, set it aside with a tagged
   stash (`git stash push -m <tag> -- <files>`) and drop it by tag once the merge contains it.
4. `git merge --ff-only <branch>`, then `rtk tools/verify.sh --changed` on main: parallel
   branches can break the 500-line cap or each other's tests after the rebase.
5. After the last merge: `rtk tools/verify.sh --all` once (it now also runs Android Lint). `--changed` now includes tests of edited
   classes (`tools/related_tests.py`), but the full suite is the final word before reporting green.
   Mass `NoSuchMethodError` after a merge that changed a data class = stale incremental test
   classes: delete `app/build/tmp/kotlin-classes/debugUnitTest` and
   `app/build/kotlin/compileDebugUnitTestKotlin`, rerun (`tools/verify.sh` does this automatically and reruns once).

### 7. Device checks (main session; agents cannot see the phone)
- `tools/device.sh wake`, `install`, `rtk tools/smoke.sh` (always after Activity/intent/tab changes).
- Turn each agent's "platform assumptions" list into checks. Prefer `ui`/`visible`/`eval`/`log`
  over screenshots; take a `shot` only for layout.
- Time-based features: age the persisted state instead of waiting (e.g. rewrite
  `shared_prefs/<name>.xml` via `adb shell run-as com.jestk.browser.debug`, then cold start).
- `requestFullscreen()` needs a real tap: inject a button and press it with
  `device.sh tap <url-prefix> <selector>`; `CDP_GESTURE=1` is not enough.
- Need to know who calls a WebView callback? Add a temporary `Log.d(TAG, "x", Throwable())`,
  install, repro, read the stack, then revert it before committing.
- Box investigations: when a site-specific behaviour (e.g. YouTube's own player logic) blocks
  the last step, commit the generic fix and file the rest as a `verif` issue with the evidence.
- `tools/device.sh sleep` when done.

### 8. Update Linear
- Start: move worked issues to *In Progress* (save_issue/save_comment are allowlisted: send them all in one message).
- After merge: a comment with commit ids, what changed and the remaining device checks.
- After device verification: *Done*. Anything waiting on the user (not reproduced → repro steps,
  a decision, permission for an outward action, a check only the user can do): comment, then
  *In Review*. *Todo* is only for work an agent can start now. Before the final report, sweep
  `In Progress`/`In Review` too: older issues whose device check already passed go to *Done*.
- Every new finding (follow-up, harness gap, near-limit file) → its own issue, duplicate search first.

### 9. Measure and improve
- `python3 tools/agent_cost.py <output_file of each agent>`: turns, tokens, weighted units.
- Compare against the *Baselines* below; if a new approach wins, update this skill (see the banner).
- Finish with the session retrospective from AGENTS.md §3 and fix small harness gaps at once.

## Baselines (`tools/agent_cost.py` units, 2026-11; evidence in `PRACTICE_LOG.md`)
- Sonnet UI feature with the discipline block: 330–520k, 20–27 turns (without it: 460–770k).
- Sonnet data+UI feature: 470–900k; over 25 files ~2000k, so split it.
- Haiku shell/Python harness: 250–590k Haiku units (about 1/3 of the Sonnet price).
- Sonnet research without Gradle: ~130k. refactor-mover with a written plan: ~165k.
- A finished agent given a new task: ~750k. Use a fresh agent instead.
