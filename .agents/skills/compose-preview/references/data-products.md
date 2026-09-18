# Data products

The renderer can produce structured data alongside each PNG — accessibility
findings, the a11y semantic hierarchy, layout trees, theme resolution,
recomposition heat-maps, and so on. Each is identified by a namespaced
*kind* string (`a11y/atf`, `a11y/hierarchy`, `compose/recomposition`, …)
with its own JSON schema.

Two surfaces:

- **MCP** — `list_data_products` / `get_preview_data` /
  `subscribe_preview_data` tools on the [`compose-preview-mcp`](https://github.com/yschimke/compose-ai-tools/blob/main/mcp/README.md)
  server. The right path for any agent that's already driving previews
  through MCP.
- **CLI / Gradle** — when a kind is enabled in the consumer's
  `composePreview { ... }` config, the renderer writes the same payload
  to `build/compose-previews/data/<previewId>/<kind>.json` after every
  render. CLI / CI consumers read those files directly. **No `--emit`
  flag** — kind selection is Gradle config, not CLI surface (see
  [`docs/daemon/DATA-PRODUCTS.md`](https://github.com/yschimke/compose-ai-tools/blob/main/docs/daemon/DATA-PRODUCTS.md)
  goal #6).

The full kind catalogue, per-kind schemas, transports, and re-render
cost notes live in
[`docs/daemon/DATA-PRODUCTS.md`](https://github.com/yschimke/compose-ai-tools/blob/main/docs/daemon/DATA-PRODUCTS.md).
That daemon doc is the human-facing contract. This skill keeps only the
agent-facing review guidance: which evidence is useful, how to combine it, and
how much confidence it supports.

## Agent evidence conventions

- Prefer combinations over single-product conclusions. For example, pair
  `a11y/atf` with `a11y/overlay`, `compose/semantics`, and `text/strings`;
  pair `i18n/translations` with `resources/used` and a locale screenshot. For
  layout-expansion / RTL bug review, render the same preview at
  `locale = "en-XA"` and `locale = "ar-XB"` (runtime pseudolocale, no consumer
  build config required) and diff the PNGs.
- Distinguish visible output from semantic output. Screenshots and
  `text/strings` describe what the user sees; `compose/semantics` and
  `a11y/hierarchy` describe assistive-technology intent.
- Cite evidence precisely: product kind, preview id, locale/device when
  relevant, and screenshot or extra path when available.
- Treat unavailable products as review context, not proof that the UI is
  correct.
- Use
  [`compose-preview-review/references/agent-audits.md`](../../compose-preview-review/references/agent-audits.md)
  for focused app-review checklists.

## Enabling a kind

Kinds are produced only when the consumer's Gradle config asks for them.
For accessibility:

```kotlin
composePreview {
    previewExtensions {
      a11y { enableAllChecks() }
    }
}
```

That switches on `a11y/atf` (findings) and `a11y/hierarchy` (semantic
tree). Other kinds will gain their own toggles as they ship — see
[a11y.md](./a11y.md) for the a11y-specific knobs.

A daemon advertises only the kinds whose producers it has wired. An
agent calling a kind that isn't advertised gets `DataProductUnknown`.

## MCP workflow (the agent path)

Three calls, one of which is optional but pays for itself on repeat use:

```jsonc
// 1. Discover what kinds the daemon advertises. Empty list = pre-D2
//    daemon or producers not wired (e.g. accessibility not enabled in
//    the consumer's build script).
{ "method": "tools/call", "params": { "name": "list_data_products",
  "arguments": { "workspaceId": "<id>" } } }

// 2. (Optional but recommended for repeat use.) Subscribe so the
//    daemon attaches the kind on every renderFinished. The MCP server
//    caches the latest payload per (uri, kind).
{ "method": "tools/call", "params": { "name": "subscribe_preview_data",
  "arguments": { "uri": "compose-preview://<id>/_module/com.example.Foo",
                 "kind": "a11y/hierarchy" } } }

// 3. Fetch. With a subscription in place, the response carries
//    `cached: true` and pays no daemon round-trip. Without a
//    subscription, this falls through to data/fetch (and auto-renders
//    the preview if it hasn't rendered yet).
{ "method": "tools/call", "params": { "name": "get_preview_data",
  "arguments": { "uri": "compose-preview://<id>/_module/com.example.Foo",
                 "kind": "a11y/hierarchy" } } }
```

`subscribe_preview_data` is sticky-while-visible: when the preview
leaves the daemon's `setVisible` set the subscription auto-drops, and
the agent re-subscribes when it comes back into view. Refcounted across
MCP sessions — a subscribed kind stays subscribed on the daemon as long
as any session holds an interest, and is released cleanly on the last
unsubscribe / disconnect.

## When to subscribe vs just fetch

- **One-shot question** ("does this preview have a11y issues?") — skip
  subscribe, call `get_preview_data` directly. It auto-renders if
  needed and returns one payload. ~one render's latency.
- **Repeated questions about the same preview** ("for each preview in
  this module, give me a11y findings") — subscribe first, then fetch.
  The first fetch warms the daemon; subsequent fetches are cache hits.
- **Always-on for a workspace** — currently no operator knob (the
  speculative `--attach-data-product` CLI flag was removed; the
  agent-side equivalent isn't built yet). Use per-preview
  `subscribe_preview_data` calls for now.

## Failure modes

| Wire error | Code | Meaning |
|---|---|---|
| `DataProductUnknown` | -32020 | Kind isn't advertised. Call `list_data_products` to see what's available, and check the consumer's Gradle config enabled the producer. |
| `DataProductNotAvailable` | -32021 | Preview has never rendered. `get_preview_data` already retries automatically; only surfaces if the auto-render itself failed. |
| `DataProductFetchFailed` | -32022 | Producer-side failure (bug in the renderer or a malformed preview). Details in `data`. |
| `DataProductBudgetExceeded` | -32023 | The daemon needed to re-render to compute the kind and the per-request budget tripped. Bump `daemon.dataFetchRerenderBudgetMs` if your previews are slow, or subscribe instead so the kind rides on every render. |

## CLI / Gradle consumers

When a kind's producer is enabled in `composePreview { ... }`, the
renderer writes its payload to
`build/compose-previews/data/<previewId>/<kind>.json` on every render
of `<previewId>`. CLI agents read those files directly — no MCP, no
tool calls. Same JSON shape either way (daemon and CLI share the
renderer-side producers).

For accessibility specifically the older
`build/compose-previews/accessibility-per-preview/<id>.json` location
stays for one release as a back-compat alias, then retires.

`layout/inspector` is Android-daemon backed by Compose `RootForTest`
carried on `PreviewContext.inspection`. Use it for layout-structure
questions: parent/child shape, bounds, measured size, constraints,
z-order, and inspectable modifier values. It is intentionally separate
from `compose/semantics` and
`a11y/hierarchy`; fetch those when the question is semantic intent or
assistive-technology output.

`compose/recomposition` is the agent-facing performance signal for
unnecessary recomposition. For review guidance, bad examples, and direct
composition-counter probes, use
[`compose-preview-review/references/agent-audits.md`](../../compose-preview-review/references/agent-audits.md).

`compose/theme` resolves the active Material 3 design system: `resolvedTokens`
(the `colorScheme`, `typography`, and `shapes`, keyed by role) plus `consumers`
— a per-node `{ nodeId, tokens }` list of which theme tokens each node actually
read. `nodeId` is the same id space as `compose/semantics`, so **join the two by
`nodeId`** to attribute an element to the exact code attribute it drew with
(e.g. `onSurface`, `bodyLarge`) and resolve its value via `resolvedTokens` —
instead of guessing from a rendered colour. Attribution is resolved-value, not
compiler-exact: typography is matched precisely, while a colour several roles
share is reported as the candidate role set (an `on*` role is pinned when the
node's container background identifies it). `consumers` is populated as of
compose-preview **v0.15.2** (schema v2); older producers return `consumers: []`,
in which case fall back to reading `resolvedTokens` and reverse-matching a
node's resolved colour yourself.

**SVG vector output** — two kinds export a preview as scalable vector art
instead of a fixed raster, both built from the same captured trees as the PNG:

- `compose/semantics-wireframe` — a schematic 2D wireframe of the semantics
  tree (SVG primary, baked PNG as a `png` extra). Structural truth: depth-cycled
  strokes, clickable stops accented, `clearAndSet` dashed. This is the `layout`
  (bordered) variant the design-catalog/Figma pipelines pair with spacing
  redlines.
- `compose/figma-svg` — a **layered, editable** design-fidelity SVG
  (`compose-figma.svg`). Every composable becomes a named `<g id="…">` group
  nested exactly as the code nests (so a Figma import lands each component as a
  named layer), with real fills/strokes from container tokens, editable rounded
  corners, editable `<text>` carrying the captured family/size/weight/colour,
  and `data-token` bindings for pairing with `figma-variables.json`. Opaque
  subtrees (`Image`/`Icon`/`Canvas`/charts) ride as `<image>` layers over a
  cropped raster so the vector stays whole. Fonts are embedded **by default** —
  each face rides as a WOFF2 data URI so the true typeface renders instead of a
  substituted `sans-serif`; generic faces (e.g. Roboto) are fetched from Google
  Fonts, degrading right back to `sans-serif` when a face can't be resolved
  (offline / network error). Opt out with `-Dcomposeai.svg.embedFonts=false`
  (renamed from `composeai.figma.embedFonts` — embedding applies to the SVG for
  any viewer, not just Figma) for a smaller vector-only SVG. The preview's own
  background is **not** injected: a `@Preview(showBackground = true)` render
  exports without the opaque rect (or Wear device-mask circle) it used to lay
  under the tree, so the import arrives as editable layers rather than sitting on
  a fill a designer has to delete — and the tree that declared the background
  generally paints that colour itself anyway. A baked-in background is hard to
  remove and easy to add back, so it is **requested per preview** through
  `PreviewOverrides.svgBackground`, which names a shape rather than a boolean:
  `NONE` (the default, nothing injected), `DEVICE` (the device-mask shape — a
  `<circle>` for a round Wear face, a vertical stadium for a tall Wear scroll
  export, the plain frame rect with no mask, corners left transparent),
  `CONTENT_SHAPE` (the outermost shaped layer's own silhouette — the pill under an
  `OutlinedButton`, the disc under a circular icon button), and `FULL_BLEED` (a
  plain rect to the corners regardless of any mask, painted outside the device
  clip). One surface defaults to `DEVICE` rather than `NONE`: a Wear **scrolling**
  screen, whose slice-stitched export paints no fill of its own — the slices are
  composited onto the capsule, not onto a root that draws the watch face — so
  background-free would leave its light `TimeText` chrome unreadable on a light
  canvas. Asking for `NONE` on that preview still turns it off. For a daemon-wide default
  instead of a per-preview request, pass `-Dcomposeai.svg.background=<mode>` (or
  `-PcomposePreview.svgBackground=<mode>` through the Gradle plugin); `true` and
  `false` remain aliases for `device` and `none`. This is the vector
  that **figma-catalog-import** inserts as an SVG (scales crisply) and that
  **compose-design-catalog** bakes into the bundle.

`test/failure` is daemon fetch-only. After a `renderFailed`
notification, call `get_preview_data(..., kind = "test/failure")` to
retrieve the latest failed-render postmortem for that preview: error
type/message/top stack frame, a bounded stack trace, and explicit v1
fallback fields for partial screenshot, pending effects, animation state,
and redacted snapshot summary.
