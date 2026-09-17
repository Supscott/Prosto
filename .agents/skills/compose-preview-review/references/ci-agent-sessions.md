# Reviewing and authoring from a CI agent session (`claude.yml`)

Some repos wire up mention-triggered agent sessions: commenting
`@claude <request>` on an issue or PR (or applying a `claude` label)
starts a Claude Code session on a GitHub Actions runner via
`anthropics/claude-code-action`, with the whole thread as context. The
canonical design doc is
[compose-ai-tools `docs/AGENT_INVOCATION.md`](https://github.com/yschimke/compose-ai-tools/blob/main/docs/AGENT_INVOCATION.md).

If you are that session, this skill's local-review defaults shift in a
few important ways.

## What's different from a local review

| Local review | CI agent session |
|---|---|
| `gh` CLI available | Usually not installed — use the GitHub MCP tools the action provides (comments, reviews, CI status). |
| `compose-preview` CLI assumed installed | Often absent, and the Bash allowlist is typically `./gradlew` + read-only `git` only. Render with the Gradle plugin instead: `./gradlew <module>:composePreviewRenderAll`. |
| Image publishing needs explicit consent | The workflow's system prompt grants it: commit renders to your working branch (the PR branch, or the `agent/…` branch the action created) and push. Never push renders anywhere else. |
| Render base in a worktree | Prefer **reusing published renders** (see below) over re-rendering the base. |

## Embedding pixels: push first, then link

GitHub comments posted via the API can't carry attachments, so the only
durable way to put images inline is commit + raw URL:

1. Render the affected previews.
2. **Commit the PNGs to your working branch and push.**
3. Only then embed them in the comment / PR body as
   `![](https://raw.githubusercontent.com/<owner>/<repo>/<commit-sha>/<path>.png)`
   — pin to the **commit SHA**, not the branch name, so the images
   survive squash-merge and branch deletion.

Describing an image is not evidence; the reviewer must see the pixels
inline. If the affected surface has no `@Preview`, add one — that also
enrolls it in the repo's preview-diff CI for every future PR.

## Discover the repo's preview CI before rendering anything

Most repos in this ecosystem already render and diff previews in CI.
Look before you render — CI output is a free, already-hosted "before":

```bash
ls .github/workflows/
git grep -l 'compose-preview\|preview-diff\|apply' .github/workflows/
```

Workflows and artifacts to look for:

| Signal | What it gives you |
|---|---|
| `compose-preview.yml` (the unified `apply` action) | `compose-preview/main` branch = rendered baselines for main (your "before"); on PRs it posts a sticky `<!-- preview-diff -->` comment with before/after images hosted on `compose-preview/pr`, pinned to commit SHAs. |
| Sticky `<!-- preview-diff -->` PR comment | Read it **first** and cite its images instead of re-rendering; only render what it doesn't cover (e.g. a preview you just added). It refreshes on every push, so re-check after you push. |
| `<!-- a11y-report -->` PR comment / `compose-preview/a11y/*` branches | Accessibility findings + annotated overlays for the same previews. |
| `design-parity.yml`, `design-artifacts.yml`, `catalog.spec.json` | The repo publishes a design catalog (design-parity pipeline); UI changes may also shift the published sticker sheet, worth a mention in review. |
| Legacy `preview-comment.yml` / `preview-baselines.yml` etc. | Same data via the deprecated four-action setup — and a migration opportunity, see the [compose-preview-ci skill](../../compose-preview-ci/SKILL.md). |

Baseline branches double as a base render you didn't have to produce:
`compose-preview/main` has the PNGs and `baselines.json`
(preview id → sha256) for the merge base, so a diff can often be
computed from one head render plus the published manifest.

## Reviewing: trust but verify the CI diff

When the preview-diff comment exists, your review job is interpretation,
not re-rendering: read the changed/new/removed images, connect each
delta to a line in the diff, and flag anything unexplained. Two checks
worth doing every time:

- **Unexplained changes** — a changed preview whose source (and
  transitive dependencies) the PR doesn't touch is either baseline
  drift or an unstable preview. Don't report it as a regression; triage
  it with [stability.md](./stability.md).
- **Missing changes** — source changed but its previews didn't. The new
  code path may be behind a parameter no preview exercises; ask for a
  variant that hits it.

## "Resuming" across mentions

Each `@claude` mention is a fresh run that re-reads the whole thread and
your previously pushed branch. Structure comments so your future self
can pick up: state what was rendered, which commit the images are
pinned to, and what remains. On an open PR, repeated mentions stack
commits on the same branch — re-render only what changed since your
last push.
