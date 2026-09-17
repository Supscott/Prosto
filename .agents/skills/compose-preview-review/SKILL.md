---
name: compose-preview-review
description: Review pull requests that change Compose UI by rendering @Preview composables on base and head and diffing them. Use when reviewing a UI PR locally or from a CI agent session (@claude mention), authoring an agent-opened PR that touches UI, or triaging flaky or unstable previews (time/random/animation). Pairs with the compose-preview skill; for wiring the CI that posts those diffs, see the compose-preview-ci skill.
---

# Compose Preview — Review

Workflows for reviewing pull requests that touch Compose UI and authoring
agent-opened PRs that include preview screenshots.

Setting the CI up is a separate job with a separate skill —
[**compose-preview-ci**](../compose-preview-ci/SKILL.md) owns baselines
branches, the `apply` action, and the fork-safe two-stage split. This skill
is about reading what that CI produces (and rendering by hand when it isn't
there).

This skill assumes the **compose-preview** skill is installed — it owns
the renderer, CLI, and Gradle plugin. Check first with
`compose-preview --version`; if it's missing, ask the user to run the
bootstrap installer (which covers every skill in the bundle):

```sh
curl -fsSL https://raw.githubusercontent.com/yschimke/skills/main/scripts/install.sh \
  | bash
```

## Source

This skill is maintained at
[github.com/yschimke/skills](https://github.com/yschimke/skills) under
`skills/compose-preview-review/`. To check for updates, compare the
installed copy against `main` (e.g. `git ls-remote
https://github.com/yschimke/skills HEAD`). The CLI, renderer, and
GitHub Actions referenced below ship from
[github.com/yschimke/compose-ai-tools](https://github.com/yschimke/compose-ai-tools).

## When to use this skill

Pick the workflow that matches the task:

| Task | Read |
|---|---|
| Review a PR locally that touches UI | [references/agent-pr.md § Reviewing a PR](./references/agent-pr.md#reviewing-a-pr-agent-workflow) |
| Review or author from a **CI agent session** (`@claude` mention / `claude.yml` on an Actions runner) | [references/ci-agent-sessions.md](./references/ci-agent-sessions.md) |
| Author an agent-opened PR that touches UI | [references/agent-pr.md § Authoring an Agent PR](./references/agent-pr.md#authoring-an-agent-pr-body-structure) |
| Triage a flaky or unstable preview (time, randomness, animation, network images) | [references/stability.md](./references/stability.md) |
| Report, accept, verify, and close a design-parity difference | [references/design-parity-lifecycle.md](./references/design-parity-lifecycle.md) |
| Wire `compose-preview/main` baselines + PR-comment CI for a project (or migrate from the legacy four-action setup) | [**compose-preview-ci** skill](../compose-preview-ci/SKILL.md) |
| Render previews on base and head and diff them | [references/agent-pr.md § Render base and head locally](./references/agent-pr.md#1-render-base-and-head-locally) |

## Quick reference: review a UI PR locally

1. **Check what preview CI the project already has, first.** Scan
   `.github/workflows/` for `compose-preview.yml` (the `apply` action),
   legacy preview actions, and design-parity/design-artifacts pipelines
   — see
   [references/ci-agent-sessions.md § Discover the repo's preview CI](./references/ci-agent-sessions.md#discover-the-repos-preview-ci-before-rendering-anything).
   If a sticky `<!-- preview-diff -->` comment is already on the PR,
   read it and cite it instead of re-rendering. See
   [references/agent-pr.md § Optional: integrate with apply CI in comment mode](./references/agent-pr.md#6-optional-integrate-with-apply-ci-in-comment-mode-rare).

2. **Render base and head.** Use a worktree so the working copy stays put:

   ```bash
   BASE=$(gh pr view <N> --json baseRefName -q .baseRefName)
   git worktree add ../_pr_base "origin/$BASE"
   (cd ../_pr_base && compose-preview show --json) > base.json
   compose-preview show --json > head.json
   git worktree remove ../_pr_base
   ```

3. **Diff** by `id` + `sha256`. Bucket into changed / new / removed.

4. **Read** the PNGs for changed and new entries — that's the visual
   context the human reviewer will lack.

5. **Post a text-only review comment** summarising deltas. Image upload
   only with explicit consent — see
   [references/agent-pr.md § Uploading images](./references/agent-pr.md#3-uploading-images-only-with-explicit-consent).

## Reference docs

| Path | When to read |
|---|---|
| [references/agent-pr.md](./references/agent-pr.md) | Full PR review + agent PR authoring guidance: comment structure, image hosting choices, things to flag, integration with the unified `apply` CI action when present. |
| [references/ci-agent-sessions.md](./references/ci-agent-sessions.md) | Running this skill inside a mention-triggered CI agent session (`claude.yml`): Gradle-only rendering, commit-SHA-pinned image embedding, discovering and reusing the repo's existing preview-diff CI. |
| [references/stability.md](./references/stability.md) | Flaky / unstable previews: detection (render twice, CI symptoms), common causes (clock, randomness, animations, network images, locale), fixes, and how to review a suspect diff. |
| [references/agent-audits.md](./references/agent-audits.md) | Agent audit recipes and data-product documentation clusters: accessibility, localisation, Wear clipping, resources, theme, traces, and failure triage. |
| [references/mcp-review.md](./references/mcp-review.md) | Driving a PR review through the MCP server (two-workspace base+head flow, push notifications, edit-on-top iteration). |
| [references/design-parity-lifecycle.md](./references/design-parity-lifecycle.md) | Catalog-owner workflow for reporting, triage, scoped acceptance, status review, verification, and safe atomic closure. |

## Related

- [**compose-preview** skill](../compose-preview/SKILL.md) — running the
  renderer itself: CLI, Gradle plugin, `@Preview` design patterns,
  capture modes (animations, scrolling), accessibility checks.
