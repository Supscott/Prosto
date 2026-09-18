# Making preview CI cheap

A full render dominates CI latency: the reference repo's own all-module
render swings 23–32 minutes across runners. Four levers, roughly in the
order worth reaching for.

## 1. Split the pipelines across parallel jobs

The single step runs all four pipelines back-to-back in one job. Splitting
them across independent jobs drops wall time from the *sum* of the pipelines
to the *slowest one* — the two full renders (compose, and the a11y
re-render) otherwise serialize.

```yaml
jobs:
  apply:
    name: Compose previews
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: ./.github/actions/setup           # your java + SDK + cache composite
      - uses: yschimke/compose-ai-tools/.github/actions/apply@v1.79.0
        with:
          only: compose,resources

  a11y:
    name: A11y previews
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: ./.github/actions/setup
      - uses: yschimke/compose-ai-tools/.github/actions/apply@v1.79.0
        with:
          only: a11y,notifications
```

The jobs are independent — disjoint baseline/PR branches and disjoint
sticky-comment markers — so they never collide. Two grouping rules are not
negotiable:

- **Keep compose + resources together.** Resources is cheap and reuses the
  compose run's base SHA when available.
- **Keep notifications in the same job as, and after, a11y.** `apply` runs
  a11y first; the notifications pipeline stages the renders the a11y pass
  leaves in the shared workspace. On its own runner it stages only the two
  short-stem standard renders and **falsely reports every daemon-produced
  capture as removed**.

Watch out: `only: a11y` alone silently drops the notifications pipeline,
because `only` clears every surface it doesn't name. Write
`only: a11y,notifications` unless you deliberately render no notification
previews.

If you split like this, note that the second job is a **new check name** —
add it to branch protection if it should gate merges too. Keeping the first
job's id and name (`apply` / "Apply compose-preview") preserves any existing
required check.

## 2. Change-scoped rendering

Drop a JSON file at `.github/preview-scope.json` (override with the
`scope-config` input) and PR comment runs classify the diff before
rendering:

```json
{
  "scopedRoots": ["samples"],
  "ignorePaths": ["docs/**", "**/*.md"]
}
```

| Field | Meaning |
|---|---|
| `scopedRoots` | Directory prefixes whose modules are eligible for scoping. A changed file inside a module under one of these roots scopes the render to that module **plus every module that (transitively) depends on it** — the dependency graph is read from Gradle itself, so a shared-module change can never skip its dependents. |
| `ignorePaths` | `**`-style repo-relative globs for files that provably cannot change a rendered preview. A PR touching only these skips the pipelines entirely — docs-only PRs finish in under a minute. Module ownership wins: a markdown file *inside* a scoped module still scopes that module in, since module content can be fixture data. |

**Everything else is fail-open — full render.** A changed file outside the
scoped roots (build scripts, version catalogs, CI config), a file that can't
be attributed to a module (deleted module, loose file), a failed or missing
Gradle graph probe, or a build with no statically applied plugin. Scoping
can only skip work that provably cannot change a preview.

Semantics worth knowing before you enable it:

- Only the **compose** pipeline renders scoped. Resources / a11y /
  notifications treat a partial scope as a full run; only the "nothing
  render-affecting changed" case skips them.
- Baseline runs (push to the development branch) and `workflow_dispatch`
  reruns **always render everything**. `scope: full` forces it per
  invocation — the lever for a manual full rerun.
- The PR comment carries a `Change-scoped run: …` note. Out-of-scope
  baselines are treated as **unchanged**, never "Removed". Removals *inside*
  scoped modules are still detected.
- When the diff scopes to nothing but a previous push already posted sticky
  comments, the run falls back to full so the stale comment is refreshed
  rather than left behind.

A missing config file turns the feature off — purely additive.

> On a two-stage fork setup the render job's resolved scope travels in the
> handoff as `_scope_modules`. The publish job never checked the PR out, so
> without it a scoped run would report every unrendered module's baseline as
> *Removed*. See [fork-prs.md](./fork-prs.md).

## 3. The downloadable-font cache

On by default (`fonts-cache: false` opts out). Previews using
`Font(GoogleFont(...))` resolve faces through a machine-local cache; on CI
that starts empty every run, so every face is re-fetched.

**This is a correctness fix more than a speed one.** The Google Fonts CSS
API can serve a *different* face for the same key — a static sub-font at the
exact weight, or the family's variable TTF — and those carry different text
metrics. A run that resolved the other one renders its whole text layer
shifted by a fraction of a pixel, which shows up as a visual diff on a PR
that changed nothing. Caching the bytes means the face is chosen once
instead of re-rolled per run.

Two caveats:

- **Cache scope is per branch.** A PR run reads its own branch's cache and
  the default branch's, so the baseline runs on `main` are what keep PR runs
  warm. A brand-new face still costs one live fetch.
- **It narrows the window, it doesn't close it.** A face is still fetched
  live the first time it's seen, and whatever the API served then is what
  gets cached.

## 4. Timeouts are a ceiling, not a budget

The `timeout` input (seconds, per Gradle invocation) defaults to 600. That
is marginal for a large all-module render: identical jobs swing 23–32
minutes across runners, and on a below-median runner the render gets
cancelled mid-build — surfacing as "Render failed" / rc=2 with the baselines
frozen. Set it comfortably above the slowest observed run (the reference
repo uses `1800`). It exists to catch a hung build.

## Comment ergonomics

### A/B comparison of preview variants

By default the comment shows one "hero" render per function and collapses
the rest into "Other variants" links. To compare two variants of the *same*
function side-by-side horizontally, nominate them at
`.github/preview-abtest.json` (override with `ab-config`):

```json
{
  "groups": [
    {
      "function": "ButtonPreview",
      "module": "app",
      "variants": ["Control", "Treatment"],
      "label": "Button copy"
    }
  ]
}
```

| Field | Required | Meaning |
|---|---|---|
| `function` | yes | Composable function name (not the FQN). |
| `variants` | yes | ≥ 2 variant tokens — each the preview-id suffix (`@Preview` name, group, or `PARAM_<n>`). Columns render in this order. |
| `module` | no | Restrict the match to one module. |
| `label` | no | Heading above the side-by-side table. |

Variants can come from two `@Preview` annotations on one composable
(differentiated by `@Preview(name = …)`, including ones contributed by a
multi-preview meta-annotation), or two values of a `@PreviewParameter`
provider (matched by the `_PARAM_<index>` suffix).

A group is only surfaced when at least one of its variants actually changed
or is new, so unchanged groups don't post on no-op PRs. A missing or
malformed file is a no-op.

### Re-run checkbox

`rerun-checkbox: true` puts an unchecked **Re-run preview diff** item near
the top of the compose sticky comment. **The action only renders the
control** — the repository must handle the resulting `issue_comment: edited`
event and authorize the actor before calling GitHub's rerun API. It is
opt-in precisely so consumers without that handler don't get an inert
checkbox.

The reference handler is
[`pr-commands.yml`](https://github.com/yschimke/compose-ai-tools/blob/main/.github/workflows/pr-commands.yml):
it accepts the checkbox only on the bot-authored `<!-- preview-diff -->`
comment, checks the clicking actor has write-level access, updates the
comment with an in-progress status, and reruns the workflow on the PR's
current head SHA.

## Non-Gradle builds

`skip-render: true` makes the compose and resources pipelines reuse
pre-staged `_previews.json` / `_resources.json` at `$GITHUB_WORKSPACE`
instead of invoking `compose-preview show` / `show-resources`. That lets
Buck2 / Bazel / Amper builds drive the comment / baseline / push half of the
action with envelopes produced by the Phase A CLIs (`preview-discovery`,
`daemon-launch-builder`, `render-cli`).

Pair with `cli-version: none` if the CLI isn't needed at all — but note the
a11y and notifications pipelines still require it, so `only: compose,resources`
goes with that combination.

See
[`docs/NON_GRADLE_INTEGRATION.md`](https://github.com/yschimke/compose-ai-tools/blob/main/docs/NON_GRADLE_INTEGRATION.md)
for the envelope contract.

## Canonical reference

[`.github/actions/apply/README.md`](https://github.com/yschimke/compose-ai-tools/blob/main/.github/actions/apply/README.md)
— the full input table, updated with the action.
