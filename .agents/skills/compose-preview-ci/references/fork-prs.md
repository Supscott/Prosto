# Fork PRs — the two-stage render/publish split

The [single-job workflow](../SKILL.md#the-single-job-workflow) works for
pull requests raised from a branch in the repository itself. It **cannot**
work for a PR from a fork, and no `permissions:` block fixes that: on a
`pull_request` event from a fork, `GITHUB_TOKEN` is read-only for *every*
scope. The render push 403s, and the sticky comment can't be posted either
— commenting is a write too.

The failure is quiet. Same-repo PRs keep getting their diffs, so the
pipeline looks healthy; outside contributions just never get a comment.

## `pull_request_target` is not the fix

It hands out a write-scoped token and the repository's secrets — and
rendering previews means checking out the PR's own code and running its
Gradle build. That is arbitrary code execution with a write token: the
classic pwn request.

**A workflow that renders untrusted code must never hold the token that
publishes the result.** Split those two responsibilities across two
workflows instead.

## Stage 1 — render (untrusted)

The render job is read-only *by construction* and hands its output to the
publish job as an artifact.

```yaml
# .github/workflows/compose-preview.yml — untrusted. Renders, publishes nothing.
name: Compose Preview
on:
  push:
    branches: [main]
  pull_request:
    types: [opened, synchronize, reopened]
  workflow_dispatch:

permissions: {}          # nothing by default; each job asks for what it needs

# Two `synchronize` events on one PR would otherwise race: the older render
# can finish last, and its publisher then overwrites the shared render
# branches and sticky comments with stale pixels.
concurrency:
  group: compose-preview-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true

jobs:
  # Fork PRs: render only, in a job that is READ-ONLY BY CONSTRUCTION.
  render-fork:
    if: github.event.pull_request.head.repo.fork
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@v7
        with:
          persist-credentials: false
      - uses: ./.github/actions/setup           # your java + SDK + cache composite
      - uses: yschimke/compose-ai-tools/.github/actions/apply@v1.79.0
        with:
          phase: render
      - uses: actions/upload-artifact@v7
        with:
          name: compose-preview-handoff
          path: _compose_preview_handoff/
          if-no-files-found: error
          retention-days: 1

  # Everything trusted — same-repo PRs and the baseline push on `main` — keeps
  # the single-job path, which already holds the write token it needs.
  apply:
    if: ${{ !github.event.pull_request.head.repo.fork }}
    runs-on: ubuntu-latest
    permissions:
      contents: write
      pull-requests: write
    steps:
      - uses: actions/checkout@v7
        with:
          persist-credentials: false
      - uses: ./.github/actions/setup
      - uses: yschimke/compose-ai-tools/.github/actions/apply@v1.79.0
```

> **Keep `render-fork` and `apply` as two jobs.** The tempting refactor —
> one job with `phase: ${{ fork && 'render' || 'all' }}` — is wrong.
> GitHub normally downgrades a fork PR's token to read-only on its own, but
> an organization can enable *"send write tokens to workflows from fork pull
> requests"*, and then a shared job's requested write scopes are granted for
> real, while the action exports `github.token` into the environment the
> fork's Gradle build runs in. **Job-level `permissions:` is what makes the
> isolation hold regardless of that org setting.**

Also keep `persist-credentials: false` on the render checkout, so the token
isn't left in `.git/config` for the untrusted build to find.

## Stage 2 — publish (trusted)

Triggered by `workflow_run`, so it runs the **base branch's** code and never
executes anything from the PR.

```yaml
# .github/workflows/compose-preview-publish.yml — trusted. Pushes and comments.
name: Compose Preview Publish
on:
  workflow_run:
    workflows: [Compose Preview]
    types: [completed]

permissions:
  contents: write         # renders push to compose-preview/pr
  pull-requests: write    # sticky comment upsert
  actions: read           # cross-run artifact download

# Publishers can finish out of order too, so serialize them per head branch.
# NOT cancel-in-progress: a publisher that is already pushing should finish,
# and the next one supersedes it on the same shared branch anyway.
concurrency:
  group: compose-preview-publish-${{ github.event.workflow_run.head_repository.full_name }}-${{ github.event.workflow_run.head_branch }}
  cancel-in-progress: false

jobs:
  publish:
    # A failed render has nothing worth publishing, and a stale comment is
    # better than one built from a partial envelope.
    if: github.event.workflow_run.conclusion == 'success'
    runs-on: ubuntu-latest
    steps:
      # This checkout is the BASE branch's code, never the PR's.
      - uses: actions/checkout@v7
        with:
          persist-credentials: false

      # Only a fork run uploads a handoff. Same-repo runs publish inline and
      # upload nothing, so this workflow fires with nothing to do — that's the
      # common case, not an error.
      - id: fork
        run: |
          if [ "${{ github.event.workflow_run.head_repository.full_name }}" = "${{ github.repository }}" ]; then
            echo "is_fork=false" >> "$GITHUB_OUTPUT"
          else
            echo "is_fork=true" >> "$GITHUB_OUTPUT"
          fi

      - uses: actions/download-artifact@v7
        if: steps.fork.outputs.is_fork == 'true'
        with:
          name: compose-preview-handoff
          path: _compose_preview_handoff
          run-id: ${{ github.event.workflow_run.id }}
          github-token: ${{ secrets.GITHUB_TOKEN }}

      - id: pr
        if: steps.fork.outputs.is_fork == 'true'
        run: echo "number=$(cat _compose_preview_handoff/_pr_number)" >> "$GITHUB_OUTPUT"

      - uses: yschimke/compose-ai-tools/.github/actions/apply@v1.79.0
        if: steps.pr.outputs.number != ''
        with:
          phase: publish
          pr-number: ${{ steps.pr.outputs.number }}
```

Two details that look like sloppiness and aren't:

- **Deciding "is this a fork run?" from `head_repository`, not from whether
  the download succeeded.** A blanket `continue-on-error` on the download
  would also swallow a genuine artifact-service or auth failure on a real
  fork run, and let the publisher finish green having pushed nothing.
- **No `continue-on-error` on the download itself.** On a fork run, that
  failing is a real failure and should stay visible and re-runnable.

### Repeat comment-shaping inputs on the publish call

Inputs that shape the *comment* are consumed on the publish side, so any you
set on the render call must be set again here — `comment-on-empty-diff`,
`rerun-checkbox`, `ab-config`.

`only` / `skip` are the exception: **don't repeat them.** The render job's
resolved pipeline set travels in the handoff. Re-resolving from the publish
call's own inputs would default all four pipelines on, and the upsert for a
pipeline that never ran would patch its existing sticky comment to "no
changes".

## What travels between the jobs

`phase: render` stages one directory (`handoff-dir`, default
`_compose_preview_handoff`) holding everything the publish half reads off
disk: the staged PR renders and their push metadata, the fetched baselines,
the resolved base SHA, and the per-surface staging dirs.

The directory itself contains just two entries — `handoff.tar` and
`_pr_number`. Everything else lives inside the tarball because
`actions/upload-artifact` refuses any path containing a colon, and a Gradle
module path is full of them (`_pr_renders/renders/ai:sample:wear-gemini/…`).
Those directory names can't be sanitised: `_pr_renders` is pushed verbatim
to the render branch, so its layout *is* the image URL in the comment.
`_pr_number` stays outside so the caller can read it back.

Four values are in there because the publish job cannot recover them itself:

| File | Why it can't be recomputed |
|---|---|
| `_pr_number` | `github.event.workflow_run.pull_requests` is **empty for fork PRs** — the one case this whole path exists for. |
| `_scope_modules` | The publish job never checked the PR out, so it can't classify the diff. Without the render job's scope, a change-scoped run would report every unrendered module's baseline as a *Removed* preview. |
| `_pipelines` | The resolved `only` / `skip` set (see above). |
| `_ab_config` | Read from the checkout — which on the publish side is the *base* branch, so a PR that adds or edits an A/B config would be graded against the old grouping. |

Both sides of every comparison travel, not just the new renders: the
comparators fail closed on a missing baseline. An absent
`_resource_baselines/renders` would turn renderer anti-aliasing noise into a
false resource diff, and a missing `_notification_baseline_findings.json`
reads as an empty baseline — reporting every surviving preview as *Added*
and losing removals entirely.

`_previews.json` is rewritten on the way in: the CLI emits **absolute**
`pngPath` values pointing into the render runner's Gradle build directories,
so the envelope is rebased onto `<handoff-dir>/current/`.

## What this buys you, and what it doesn't

The publish job holds a write token, so be precise about what it runs: the
base branch's checkout, this action, and nothing else. It never checks out
the PR, never invokes Gradle, and never installs the CLI.

**The artifact is treated as hostile.** It was produced by a job that ran
the PR's own Gradle build, and the pipelines write their push metadata as
each surface completes — so a later pipeline, still executing fork code, can
rewrite what an earlier one staged. Push *control* therefore never comes
from the artifact: the destination branch, commit message and skip flag are
rebuilt on the publish side from the same literals the single-job path uses,
so a `_push_branch` rewritten to `main` aims nothing at the default branch.
A `.github/` tree found in a staging dir is dropped before the push, since
the push commits the directory wholesale and a workflow file landing on a
render branch would run on its own `push` trigger. **The staging dirs
contribute pixels; every decision is made from the action's own constants.**

If you add steps of your own to the publish job, hold that line — anything
read out of the handoff is PR-authored input.

It does **not** make the render itself trusted. A fork PR still runs its own
code on the render runner; that job just has nothing worth stealing (a
read-only token and no secrets).

## Canonical reference

The action's own README is the source of truth and is updated with the
action: [`.github/actions/apply/README.md` § Fork PRs](https://github.com/yschimke/compose-ai-tools/blob/main/.github/actions/apply/README.md#fork-prs).
