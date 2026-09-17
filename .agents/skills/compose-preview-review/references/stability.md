# Flaky and unstable previews

Preview pipelines compare renders by `sha256`, so a preview that renders
differently on identical code is noise in every downstream consumer:
phantom rows in PR diff comments, baseline "drift" on pushes that touch
no UI, and real regressions hiding in the flicker. Treat instability as
a defect to fix (or at least flag), never as a diff to rubber-stamp.

## Detecting instability

- **Render twice, diff the manifests.** Two back-to-back runs on the
  same commit should produce identical `sha256` per preview id:

  ```bash
  compose-preview show --json > run1.json
  compose-preview show --json > run2.json
  # or two ./gradlew <module>:composePreviewRenderAll runs
  ```

  Any id whose hash differs between the runs is unstable — no code
  archaeology needed.
- **CI symptoms:**
  - The `<!-- preview-diff -->` comment lists changed variants on a PR
    that touches no UI (e.g. a workflow-only or docs-only PR).
  - The same handful of previews flip-flops across unrelated PRs.
  - Baselines change on every push to main without corresponding UI
    commits.
- **Capture manifests:** `captures[]` entries whose `changed` flag
  toggles run-to-run point at scroll/animation captures that never
  settle.

## Common causes and fixes

| Cause | Telltale | Fix |
|---|---|---|
| Real clock — `System.currentTimeMillis()`, `LocalDate.now()`, `Clock.System.now()`, `Date()` | Clock faces, "today" headers, relative timestamps ("2m ago") shift every run; diffs cluster on time-displaying previews | Inject a clock (CompositionLocal or parameter) and pass a fixed instant in preview fixtures; never let a preview read wall time |
| Unseeded randomness — `Random()`, `Math.random()`, `UUID.randomUUID()`, `shuffled()` | Sample charts/lists differ per run | Seed the generator in fixtures, or precompute the sample data as constants |
| Infinite / looping animations | Progress indicators, pulses, shimmer render at an arbitrary frame | Capture at a declared time (see the compose-preview skill's capture modes); gate `rememberInfiniteTransition` behind `LocalInspectionMode` |
| Async / network images (Coil, Glide) | Placeholder vs loaded image races; sometimes blank | Use a preview/fake image loader in inspection mode; never fetch over the network in a preview |
| Unordered collections | List rows swap order between runs | Sort fixtures deterministically |
| Locale / timezone / number formats | Dates and decimals differ between machines | Pin `@Preview(locale = …)`; use fixed-zone times in fixtures |
| Font resolution / fallback | Text metrics shift by a pixel between environments | Bundle the fonts; compare renders only from the same toolchain (CI vs CI, not laptop vs CI) |
| Live device state (battery, connectivity, step counts on Wear) | Status-driven previews drift | Fake the data providers in preview fixtures |

## Review guidance

- **Attribute before you assess.** For each changed preview, ask: does
  the changed pixel region correspond to something this PR touched? A
  timestamp or clock face that "changed" on a CI-only PR is
  instability, not a regression — say so explicitly in the review.
- **Flag, don't ignore.** An unstable preview degrades every future
  review of that surface. Propose the stabilizing fix (usually a
  fixture-level fixed clock/seed/loader — see the table) or file a
  follow-up issue; don't just skip the row.
- **Verify the fix the same way you found it:** render twice after
  stabilizing and confirm identical hashes.
