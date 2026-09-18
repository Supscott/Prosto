# Design-parity lifecycle

Use this workflow when a Compose catalog comparison exposes a real design/code difference that must
remain visible across repeated spec, component, and preview changes.

The lifecycle support described here requires **compose-preview v1.35.0+** and
**design-parity v0.1.60+**. Older releases may publish the comparison without carrying or evaluating
the scoped acceptance statuses below.

## 1. Report and triage

Open the focused reference comparison and use its report link. File the generated body without
editing the fenced `compose-parity-locator/v1` block: the issue index parses that block to recover
the repository, catalog, component, served preview id, reference, variant, overrides, and optional
element selection. The page URL is only a reproduction link; it is not the identity.

For several components at once, tick their rows on the comparison wall's reference lane and use the
wall's own report: it carries one locator block per ticked row. At most one row per component — the
index refuses a body naming a component, preview or reference twice, and refuses the whole body, so
such an issue is filed and then indexed nowhere.

Apply one area label and the relevant parity label:

- `area:spec`, `area:component`, `area:preview`, `area:renderer`, or `area:comparison`
- `parity:regression`, `parity:known-difference`, `parity:verification-needed`,
  `parity:upstream`, or `parity:catalog`

The report form asks the last three itself — a "Where does it belong?" control beside the Summary,
whose value is filed as the label. Answer it there rather than after the fact: `parity:upstream`
when the difference belongs to the framework or design system the catalog builds on,
`parity:catalog` when the catalog's own code draws it wrongly, and `parity:verification-needed` —
the default — when you cannot tell yet.

Do not add per-component labels. Component identity belongs in the locator block. If the issue spans
components, keep one locator block per component in the same body; never reuse a preview in two
blocks.

## 2. Accept one scoped difference

Accept only a difference that is understood, deliberately allowed, and linked to an issue. Commit:

```text
.design-parity/
├── known-differences.json
└── known-differences/
    └── <acceptance-id>/
        ├── mask.png
        └── accepted-candidate.png
```

Each record in `known-differences.json` must include its stable `id`, mandatory `issue` URL, exact
catalog scope, artifact paths and SHA-256 hashes, reference hash, recorded canonical plane, and
`candidateTolerance`. Several records may link to one issue.

The mask is an 8-bit greyscale, non-interlaced, non-animated PNG with no transparency: `255` means
accepted and `0` means unaccepted; intermediate values are invalid. Keep the mask as small as the
understood difference permits. `accepted-candidate.png` records what those pixels looked like when
accepted. Both artifact paths are relative to that acceptance's own directory.

Start `candidateTolerance` at `2`; the v1 maximum is `8`. A tolerance that needs to be large is
evidence that the acceptance or its canonical plane is wrong, not a reason to widen it. For an
element-scoped record, preserve the selected `testTag`, canonical bounds, and a movement tolerance
no greater than `0.25` of the element's smaller dimension.

Review the acceptance like code: confirm the issue explains the decision, the mask covers no
unrelated pixels, both hashes match, and the raw score remains visible. An acceptance moves pixels
from the unaccepted region; it never erases the original finding.

## 3. Read the statuses

The comparison status and the issue lifecycle are separate axes:

| Status | Meaning | Action |
|---|---|---|
| `valid` | The accepted difference still matches its recorded scope and candidate. | Keep it while the issue remains open. |
| `resolved` | The candidate changed and the masked region now agrees with the reference. | Remove this record and its artifact directory in the fixing PR. |
| `invalidated` | A gate no longer matches. | Review the named causes; do not silently replace the acceptance. |
| `refused` | The record or artifacts are unsafe or malformed. | Repair or remove the acceptance; it suppresses nothing. |
| `out-of-scope` | This record belongs to another comparison. | No action in the current comparison. |

Invalidation names the reason: `candidate-changed`, `reference-changed`, `plane-changed`,
`element-ambiguous`, or `element-moved`. Re-author only after determining whether the code, design,
preview identity, or element selection changed intentionally.

The lifecycle is `open`, `closed`, or `unknown`, joined by canonical `owner/repo/number` rather than
by the hand-authored URL string. Only a closed issue with a non-resolved acceptance is **stale**.
`resolved` plus `closed` is completed verification awaiting cleanup. A missing or unreadable issue
index produces `unknown`; never infer closure from absence.

## 4. Verify and close atomically

When a fixing PR reports `resolved`:

1. Link the parity verification from the fixing PR and the tracking issue.
2. Delete every resolved record from this document and its matching
   `.design-parity/known-differences/<id>/` directory in that same PR.
3. Group records by canonical `owner/repo/number`, not by their raw issue URL.
4. Add `Closes owner/repo#number` to the PR description only when every acceptance linked to that
   issue has resolved **and** this `known-differences.json` is the issue's single owning document.
5. If single ownership cannot be established, still delete the locally resolved records, omit the
   closing keyword, and say that the issue remains open for a cross-catalog ownership check.

Do not close first and delete later: surviving records would become stale. Do not delete in an
earlier PR and close in a later one: the issue would lose the committed record connecting the
decision to its verification. Merging one PR performs both changes atomically and leaves that PR as
the durable link between the report, the fix, and closure.

With design-parity v0.1.60+, generate the cleanup from the fixing run rather than transcribing its
statuses:

```sh
npx design-parity run --repo . --components "$ALL" \
  --acceptance-evidence /tmp/design-parity-acceptances.json \
  --issue-index /tmp/parity-issues.json \
  --verification-url https://github.com/<owner>/<catalog>/pull/<fixing-pr>

npx design-parity resolve --repo . \
  --evidence /tmp/design-parity-acceptances.json \
  --owned-issue <owner>/<issue-repo>#<number> \
  --body-out /tmp/design-parity-resolution-pr.md
```

Review the resulting JSON/artifact deletions and PR body before committing. Omit `--owned-issue`
unless you have established the single owning document; the command will still perform safe local
cleanup and will explain why the issue remains open.

The issue-index argument is the catalog's published `parity/issues.json`. If it is missing,
unreadable, or contradictory, lifecycle is `unknown`; the run never infers closure from absence. A
positively closed issue with a non-resolved acceptance is stale and fails this explicit offline gate.

Single ownership is a v1 convention, not an offline guarantee. A run sees only one source repo and
one known-differences document, so local aggregation can establish that every sibling in this
document resolved, but it cannot prove that another catalog does not reference the same issue. The
safe fallback is always cleanup without automatic closure.

## Credential setup for issue-index publishing

The GitHub App used to update the catalog's published parity issue index must be installed on the
**catalog repository** with **Contents: write**. When creating its token, name both the owner and the
repositories explicitly:

```yaml
- uses: actions/create-github-app-token@<pinned-sha>
  id: app-token
  with:
    app-id: ${{ vars.APP_ID }}
    private-key: ${{ secrets.APP_PRIVATE_KEY }}
    owner: <catalog-owner>
    repositories: <catalog-repository>
```

Do not rely on the action's default repository selection. A token scoped to the workflow repository
instead of the catalog can return `403`, which otherwise looks like a successful run in which no
issues changed. Confirm the App installation, `Contents: write`, `owner`, and `repositories` before
debugging the indexer.

## Current limitation

Mask authoring has no export UI. The author must currently create the two PNGs and the JSON record by
hand. Treat that friction as a reason to keep acceptances rare and deliberate, not as permission to
replace them with a broad ignore rectangle.
