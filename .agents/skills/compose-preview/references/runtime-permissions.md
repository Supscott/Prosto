# Android runtime permissions

Composables that branch on runtime permission state (camera viewfinder vs.
"grant access" empty state, foreground-location prompt vs. live map, "photos
selected" vs. full photo-library access on Android 14+) render only one of
their branches per preview unless the agent pins which permissions the
hosted activity sees as granted.

Two surfaces, mirroring the rest of the skill:

- **Override per render** — `render_preview` accepts a `permissions`
  entry inside `overrides` to pin grant state for a single call without
  editing the `@Preview` annotation. Same shape applies to the CLI's
  per-render overrides (`renderNow.overrides.permissions` in the
  Gradle / CLI invocation path).
- **Observe per render** — the `compose/permissions` data product reports
  the permissions the just-rendered composition actually queried, plus
  the grant state each query resolved to. Use it to confirm a preview
  exercised the branch you intended.

The exact JSON shape of `overrides.permissions` and the
`compose/permissions` payload (manifest declarations vs. runtime
`checkSelfPermission` calls, special-permission handling, partial-access
states on Android 14+) is the upstream daemon contract. See
[`docs/daemon/PROTOCOL.md`](https://github.com/yschimke/compose-ai-tools/blob/main/docs/daemon/PROTOCOL.md)
(the `renderNow.overrides.permissions` entry) and
[`docs/daemon/DATA-PRODUCTS.md`](https://github.com/yschimke/compose-ai-tools/blob/main/docs/daemon/DATA-PRODUCTS.md)
(the `compose/permissions` row) in `compose-ai-tools` for the schema and the
producer's coverage notes.
This file is the agent-facing guidance: when to reach for it, how to
combine with other evidence, and how much confidence the result supports.

## When to pin permissions

- The composable calls `ContextCompat.checkSelfPermission` (or a wrapper
  like Accompanist's `rememberPermissionState`, the Jetpack
  `PermissionController`, or a feature-flag layer that gates on
  permissions). Without an override the preview sees Robolectric's
  defaults, which usually means everything denied — the granted branch
  never renders.
- You're reviewing a PR that changes a permission-gated UI. Render base
  and head with the same `overrides.permissions` to keep the comparison
  honest; without pinning, an unrelated default flip would show up as a
  spurious diff.
- You're auditing the denied branch (empty state, rationale dialog, "go
  to settings" CTA). Pin the relevant permissions to `DENIED` explicitly
  rather than relying on the default — that way a future default change
  in the renderer doesn't quietly turn the audit green.

## When to read `compose/permissions`

- After a render, confirm the preview actually queried the permissions
  you pinned. A composable that short-circuits on a feature flag or a
  fake repository won't touch the permission API at all; an empty
  `compose/permissions` payload tells you the override didn't do
  anything and the visual branch you're seeing isn't permission-driven.
- Combine with a screenshot when reviewing: payload says "CAMERA queried,
  resolved GRANTED", screenshot shows the viewfinder. Either alone is
  weaker evidence than the pair.
- Pair with `compose/semantics` or `a11y/hierarchy` when checking that
  the denied branch is itself accessible (rationale text reachable by
  TalkBack, "Open settings" button has a label, etc.) — runtime
  permissions and a11y bugs travel together.

## Failure modes

- **Override silently ignored.** If the consumer's Gradle config has the
  `compose/permissions` producer disabled, `overrides.permissions` is
  still honoured by the render, but you have no way to verify it from
  the data side. Enable the producer (same shape as other
  `previewExtensions` toggles — see
  [data-products.md](./data-products.md)) when you need ground truth.
- **Special permissions.** Some Android runtime permissions resolve
  through a separate path (e.g. `SYSTEM_ALERT_WINDOW`,
  `MANAGE_EXTERNAL_STORAGE`, notification listener access). The renderer
  models the standard `dangerous` group; treat coverage of special
  permissions as upstream-defined and check the daemon doc before
  relying on it.
- **Manifest gap.** A composable can call `checkSelfPermission` for a
  permission the module's `AndroidManifest.xml` doesn't declare. The
  renderer can still pin grant state, but the report flags the missing
  declaration — fix the manifest rather than working around it in the
  override.

## Related

- [`references/data-products.md`](./data-products.md) — how data
  products are advertised, subscribed, and fetched; failure-code table
  (`DataProductUnknown` etc.) applies to `compose/permissions` too.
- [`references/a11y.md`](./a11y.md) — pair permission-branch coverage
  with a11y checks on the same render.
- [`references/permissions.md`](./permissions.md) — unrelated despite
  the name: agent-tool allowlists for the harness, not Android
  runtime permissions.
