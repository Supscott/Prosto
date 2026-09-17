---
name: compose-preview-ci
description: Set up or repair GitHub Actions CI that renders Compose @Preview composables and posts before/after diff comments on pull requests. Use when wiring preview diffs into a repo for the first time, when preview comments don't appear on fork PRs (the two-stage render/publish split), when migrating off the legacy preview-baselines / preview-comment / a11y-report / notification-previews actions, when a run fails with "no modules have the compose-preview plugin applied" (CLI/plugin version skew), or when preview CI is too slow and needs change-scoping or parallel jobs. Pairs with the compose-preview and compose-preview-review skills.
---

# Compose Preview — CI setup

Wiring the `compose-preview` CI surface into a repository: a
`compose-preview/main` baselines branch, sticky before/after diff comments
on pull requests, and the choices that decide whether one workflow is
enough or you need two.

This skill is about **standing the pipeline up and keeping it working**.
For *reading* the diffs it produces — reviewing a UI PR, triaging a flaky
preview — see the [**compose-preview-review**](../compose-preview-review/SKILL.md)
skill. For driving the renderer by hand, see
[**compose-preview**](../compose-preview/SKILL.md).

## Source

Maintained at [github.com/yschimke/skills](https://github.com/yschimke/skills)
under `skills/compose-preview-ci/`. The composite actions described here ship
from [yschimke/compose-ai-tools](https://github.com/yschimke/compose-ai-tools);
the canonical, always-current input reference is that repo's
[`.github/actions/apply/README.md`](https://github.com/yschimke/compose-ai-tools/blob/main/.github/actions/apply/README.md).
When this skill and that README disagree, the README wins — check it before
telling a user an input doesn't exist.

## Decide the shape first

Everything is one composite action, `apply`. What differs is how many jobs
and workflows you wrap around it.

| Situation | Shape | Read |
|---|---|---|
| PRs come from branches in the repo itself | **One job**, one workflow | [§ The single-job workflow](#the-single-job-workflow) |
| PRs can come from **forks** (any public repo, OSS) | **Two stages**: unprivileged render + trusted publish | [references/fork-prs.md](./references/fork-prs.md) |
| Renders take long enough to hurt | Split the pipelines across parallel jobs, and/or scope by diff | [references/cost-and-scoping.md](./references/cost-and-scoping.md) |
| Repo still wired to four separate legacy actions | Migrate to `apply` | [§ Migrating from the legacy actions](#migrating-from-the-legacy-actions) |
| Non-Gradle build (Bazel, Buck2, Amper) | `skip-render: true` + `cli-version: none` | [references/cost-and-scoping.md § Non-Gradle builds](./references/cost-and-scoping.md#non-gradle-builds) |

**The fork question is the one to ask first**, because it is the only one
that changes the *file layout* rather than a few inputs — and it is the one
that silently produces a half-working setup if you get it wrong. A
single-job workflow on a repo that takes fork PRs doesn't fail loudly; it
just never comments on the contributions from outside, because on a
`pull_request` event from a fork `GITHUB_TOKEN` is read-only for every
scope regardless of the `permissions:` block.

Ask the user, or infer it: a private repo with no outside contributors is
safely single-job. Anything public should assume forks.

## What the pipeline produces

Four pipelines run off one action:

| Pipeline | Baseline branch | What it captures |
|---|---|---|
| `compose` | `compose-preview/main` | Rendered `@Preview` PNGs + `baselines.json` (preview id → SHA-256) |
| `resources` | `compose-preview/resources/main` | Rendered resource previews (drawables, colours) |
| `a11y` | `compose-preview/a11y/main` | Annotated `.a11y.png` overlays + `findings.json` (ATF checks) |
| `notifications` | `compose-preview/notifications/main` | Notification surface captures + `.notification.json` sidecars |

The baseline branches do double duty: each has a generated `README.md` with
inline images, so the branch is a **browsable gallery** on GitHub as well as
the "before" side every PR diffs against.

On PRs the same action renders, compares against the baselines, pushes the
new renders to `compose-preview/pr`, and upserts a **sticky comment** (one
per pipeline, keyed by an HTML marker) with the before/after.

`a11y` and `notifications` are no-ops unless pointed at a Gradle module via
`a11y-module` / `notifications-module`. Narrow what runs with `only:` /
`skip:`.

## The single-job workflow

```yaml
# .github/workflows/compose-preview.yml
name: Compose Preview
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
    types: [opened, synchronize, reopened]
  workflow_dispatch:

permissions:
  contents: write          # baselines push + compose-preview/pr branch
  pull-requests: write     # upserts the sticky PR comment

concurrency:
  group: compose-preview-${{ github.event.pull_request.number || github.ref }}
  # PR runs supersede each other; see the warning below about baseline runs.
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

jobs:
  apply:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
        with:
          persist-credentials: false
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v6
      - uses: yschimke/compose-ai-tools/.github/actions/apply@v1.79.0
        with:
          # cli-version defaults to `auto` — leave it alone (see below).
          a11y-module: ''                       # '' = auto-detect all modules
          notifications-module: samples:android # '' = skip the pipeline
```

`mode` defaults to `auto` and is picked from the event: a `push` to
`development-branch` (default `main`) → **baseline**, a `pull_request` →
**comment**. Override with `mode: baseline | comment | skip` for one-off
`workflow_dispatch` runs.

> **Do not `cancel-in-progress` baseline runs.** A cancelled baseline run
> doesn't just waste a render — it strands the baseline at an older commit,
> and every subsequent PR then reports the skipped commits' output as its
> own New/Changed/Removed. Back-to-back merges make that the norm rather
> than the exception. Gate cancellation on `github.event_name ==
> 'pull_request'`, as above. Queueing is bounded, not unbounded: GitHub
> keeps at most one *pending* run per group.

## Pin the CLI: leave `cli-version` on `auto`

**This is the single most common way the action breaks**, so it is worth
being explicit even though the default is now correct.

Pinning the *action* ref (`apply@v1.79.0`) does **not** pin the *CLI*. Under
the old `cli-version: latest`, every new compose-ai-tools release
auto-installed the newest CLI against a still-pinned Gradle plugin. A CLI
newer than the applied plugin cannot discover that older plugin, so the
render finds zero preview modules and the pipeline fails repo-wide — on
somebody else's release, not on your diff — with a misleading:

```
✗ no modules have the compose-preview plugin applied
compose pipeline: No preview modules discovered ... skipping.
```

`cli-version: auto` (the default) removes this: it reads the plugin version
pinned in your checkout — a version-catalog `[plugins]` entry, or a literal
`id("…") version "…"` in a build script — and installs the CLI at *exactly*
that version. Declare the plugin version once and the CLI follows it:

```toml
# gradle/libs.versions.toml
[versions]
composePreviewPlugin = "1.79.0"

[plugins]
composePreview = { id = "ee.schimke.composeai.preview", version.ref = "composePreviewPlugin" }
```

Renovate bumps that one entry on each release and `auto` picks it up — no
`cli-version` or `catalog-key` wiring needed. When no plugin is pinned (the
auto-inject / zero-code path, where the CLI injects a matching plugin via
`--init-script`) `auto` falls back to the project's
`composePreview.version` pin in `gradle.properties` if there is one, else to
`latest` — both correct there, because the injected plugin always matches
the installed CLI.

Other values: `latest` (opts back into floating), `catalog` +
`catalog-key` (pin to a dedicated `[versions]` key), a literal version,
`source` (build from the checkout — internal CI only), `none` (skip install).

**The skew guardrail** backs this up independently: after install, `apply`
compares the resolved CLI against the pinned plugin and, when the CLI is
newer, fails fast with an actionable message instead of the confusing "no
modules" error downstream. `skew-check: fail` (default) | `warn` | `off`.
It only acts when it can read a concrete plugin version and both sides are
clean releases, so projects deliberately tracking `latest` with no pinned
plugin are never tripped.

## Migrating from the legacy actions

The four original composite actions — `preview-baselines`,
`preview-comment`, `a11y-report`, `notification-previews` — were replaced
by the unified `apply` action in `v0.11.6`. They survive as **deprecated
shims that forward to `apply`**.

If you find a project still wired to any of them, migrate as part of the
next change touching CI. The migration is mechanical and collapses four
workflow files — plus their duplicated checkout / Java / Gradle setup —
into the single `compose-preview.yml` above:

| Legacy action | Replacement |
|---|---|
| `preview-baselines` | `apply` with `only: compose,resources` (mode auto-selects `baseline` on push) |
| `preview-comment` | `apply` with `only: compose,resources` (mode auto-selects `comment` on PR) |
| `a11y-report` | `apply` with `only: a11y` + `a11y-module:` |
| `notification-previews` | `apply` with `only: notifications` + `notifications-module:` |

Two things to carry over deliberately:

- The shims default `cli-version` to `latest`, not `auto`, because they
  forward to a *pinned released* `apply` that may predate the `auto`
  resolver. Once you call `apply` directly, **drop the input** and take the
  `auto` default.
- `a11y-report`'s `baseline-branch` / `pr-branch` inputs were already
  ignored — `apply` hardcodes the branch names. Delete them rather than
  translating them.

## When it goes wrong

| Symptom | Cause | Fix |
|---|---|---|
| `no modules have the compose-preview plugin applied` | CLI newer than the pinned plugin | Use `cli-version: auto` (default); check `skew-check` isn't `off` |
| No comment at all on some PRs | Those PRs are from forks | Two-stage split — [references/fork-prs.md](./references/fork-prs.md) |
| PR reports many phantom New + Removed previews | Baseline branch is stale — a baseline run on `main` was cancelled | Stop cancelling baseline runs (see the warning above), then re-run the workflow on `main` |
| Comment says "no changes" for a pipeline that didn't run | `only` / `skip` mismatch across a two-stage split | The render job's resolved pipeline set travels in the handoff; don't re-specify `only`/`skip` on the publish call |
| Run cancelled mid-build, reported as "Render failed" / rc=2 | Per-invocation `timeout` too low for a cold runner | Raise `timeout` (seconds). It is a hung-build ceiling, not a budget — keep it well above the slowest observed run |
| A preview that legitimately can't render fails the gate | Default `missing-renders: fail` | Mark that capture `optional` at discovery rather than downgrading the gate to `warn`. `show` then reports it as `[no PNG, optional]` and counts it under `counts.skipped`, not `counts.missing` |

## Reference docs

| Path | When to read |
|---|---|
| [references/fork-prs.md](./references/fork-prs.md) | The two-stage render/publish split: both workflow files, why `pull_request_target` is not the answer, what travels in the handoff, and what the trusted job is and isn't allowed to trust. |
| [references/cost-and-scoping.md](./references/cost-and-scoping.md) | Making preview CI cheap: parallel pipeline jobs, change-scoped rendering (`.github/preview-scope.json`), A/B variant comparison, the re-run checkbox, and non-Gradle builds. |

## Related

- [**compose-preview-review**](../compose-preview-review/SKILL.md) — reading
  the diffs: reviewing a UI PR, agent-authored PR bodies, triaging flaky
  previews.
- [**compose-preview**](../compose-preview/SKILL.md) — the renderer itself:
  CLI, Gradle plugin, capture modes, accessibility checks.
