# The agent loop — semantic, token-frugal interaction (Playwright-style)

A tight, token-frugal feedback loop over Compose UI — the way Playwright
gave web agents one over the DOM. The loop **targets elements by semantic
reference, not pixels**, and lets an agent observe, diff, and assert on a
preview without reading a PNG on every step.

Use this loop when you are *interacting with* and *regression-checking* a
preview in a long-lived MCP session. For a one-shot "what does this look
like?" render, the PNG path in the main skill is still simplest — read the
image. The loop pays off once you are clicking, typing, re-rendering, and
asking "did anything change?" repeatedly, where reading a full PNG each
time is the expensive part.

## Availability

The loop ships in **compose-ai-tools v0.14.0**. Check your bundle with
`compose-preview --version` (and `compose-preview doctor` to compare
against the latest release); `compose-preview update` re-runs the
installer if you're behind.

All of the tools below are exposed both through the MCP server (see
[mcp.md](./mcp.md)) and the `compose-preview` CLI. Semantic *input*
(clicking/typing by ref) resolves on **both Desktop (Skiko) and Android
(Robolectric)** as of v0.14.0 — a target that resolves to no node (or
more than one) is reported unmatched and the input is dropped, so check
state afterward rather than assuming the click landed.

## Why it's token-frugal

Reading a rendered PNG inline costs ~5–8 KB of base64 — roughly
**1.5–2.3k tokens** per preview. Most loop steps don't need the pixels;
they need to know *what changed* or *what's on screen*. The loop swaps the
PNG read for a structured signal:

| Step | Old cost | Loop cost |
|---|---|---|
| "Did it change?" | read PNG (~1.5k tok) | `observe="hash"` — SHA256 + dimensions (~minimal) |
| "What's on screen?" | read PNG + eyeball it | `observe="semantics"` — semantics tree + hash (~few hundred tok) |
| "What changed vs. before?" | read 2 PNGs + compare | `diff_semantics` — structured delta (~few hundred tok/changed preview, no PNG reads) |
| "Let me *see* the one element that moved" | read the whole PNG (~1.5k tok) | `render_preview crop` — just that element's rectangle (~few hundred tok) |
| Multi-state coverage (device × locale × …) | read N PNGs (N × ~1.5k) | `render_matrix` — per-cell hashes (~`cellCount × 40` tok, 24-cell cap) |

Read a PNG only when a delta says something visual moved and you need to
*see* it — the same discipline as the `changed` flag in the main render
loop, extended to interaction and semantics.

## Playwright → compose-preview

| Playwright | compose-preview |
|---|---|
| `getByRole` / `getByTestId` locators | target by `ref` / `testTag` / `role`+`text` |
| `toMatchAriaSnapshot` | `diff_semantics` (pixel-free semantics regression) |
| aria snapshot | `render_preview observe="semantics"` (the default) |
| `page.screenshot()` (full page) | `render_preview observe="png"` (opt in for pixels) |
| `locator.screenshot()` (one element) | `render_preview crop={ ref \| testTag \| role+text }` |
| codegen (record → script) | `record_preview emitTest=true` (record → Compose UI test) |

## The capabilities

### 1. Stable semantic refs

Every `ComposeSemanticsNode` gets a deterministic, content-independent
**`ref`**, assigned by priority `testTag > role > generic`, with
sibling-index disambiguation. Unlike Compose's per-render `nodeId` (which
is reassigned each render), the `ref`:

- **survives content edits** — text/label are deliberately excluded from
  the ref, so a copy change is a *field change on the same node*, not a
  remove + add;
- works as both an **interaction handle** (target it for clicks) and a
  **diff match key** (line up before/after nodes).

This is the foundation the other features build on. You rarely call it
directly — refs show up in `observe="semantics"` output and `diff_semantics`
deltas, and you pass them back as input targets.

### 2. Target by semantic ref, not pixels

Interactive input (`click`, type, …) accepts an optional semantic target
instead of `pixelX`/`pixelY`:

- `ref` — a stable node ref from a prior `observe`/diff;
- `testTag` — a Compose `Modifier.testTag(...)`;
- `role` + `text` — e.g. a button labelled "Save".

The daemon projects the live semantics tree, resolves the target to the
node's centre, and dispatches the pointer event there. Explicit
`pixelX`/`pixelY` still win when both are present (back-compat). Unresolved
or ambiguous targets are logged and the input is dropped — interactive
input is fire-and-forget, so check state afterward (an `observe` or a
diff) rather than assuming the click landed.

```json
// click the node tagged "save-button", no coordinates
{ "uri": "compose-preview://...", "target": { "testTag": "save-button" }, "action": "CLICK" }
```

Targeting by ref is **robust to layout shifts** — pixel coordinates break
the moment padding or font scale changes; a `testTag` doesn't.

### 3. Token-frugal `observe` modes

`render_preview` takes an `observe` argument with three modes:

| `observe` | Returns | Cost | Use for |
|---|---|---|---|
| `"semantics"` (default) | semantics tree + SHA256 + width/height, **no base64** | ~few hundred tok | inspecting structure, grabbing refs to target |
| `"hash"` | SHA256 + dimensions only | ~minimal | a cheap "did it change?" gate |
| `"png"` | base64 PNG | ~1.5k tok | when you actually need to *see* it |

Dimensions come straight from the PNG `IHDR` header without decoding the
image, so even `hash` is cheap. **The default is `"semantics"`** — the
snapshot-default / screenshot-on-demand split that makes the MCP loop
token-frugal (issue #1787). A bare `render_preview` returns the structured
snapshot; **opt into `"png"` explicitly** when you need pixels:

```json
{ "uri": "compose-preview://..." }                       // default: semantics snapshot
{ "uri": "compose-preview://...", "observe": "hash" }    // gate: did it change?
{ "uri": "compose-preview://...", "observe": "png" }     // pixels, on demand
```

`resources/read` still returns the PNG blob unchanged, so the VS Code panel
and other pixel consumers are unaffected — only the agent-loop *tool*
default went structured-first.

A good loop: a bare `render_preview` (or `observe="hash"`) after an edit →
if it moved, the `semantics` snapshot (or `diff_semantics`) tells you *what*
moved → request `observe="png"` only if something visual needs eyeballing.

### 4. `diff_semantics` — pixel-free regression

The pixel-free analogue of Playwright's `toMatchAriaSnapshot`. Fetches the
semantics payloads from two live preview URIs and compares them by stable
`ref`:

```json
diff_semantics(baseUri, headUri)
```

Both are `compose-preview://` URIs — compare the *same* preview before and
after an edit, or two related previews. Returns `{ schema, summary, delta }`,
where `delta` buckets into:

- **added** nodes (new in head),
- **removed** nodes (gone in head),
- **changed fields** on existing nodes (e.g. text edited).

Because matching is by `ref`, a text edit is reported as a *field change on
the same node*, not a remove + add, and positional bound jitter is ignored.
That makes it a cheap, deterministic "what changed?" signal — a few hundred
tokens, no PNG reads — suitable as the default regression check in a loop,
reserving full visual-diff PNG reads for when the semantics delta is empty
but you still suspect a purely visual change.

### 5. Crop to one element

`render_preview` takes an optional `crop` that returns **only one
element's rectangle** instead of the full frame — the Compose analogue of
Playwright's `locator.screenshot()`. Set *either* a semantic target or
explicit render-pixel bounds:

```json
{ "uri": "compose-preview://...", "crop": { "ref": "btn:save" } }        // by ref
{ "uri": "compose-preview://...", "crop": { "testTag": "save-button" } } // by tag
{ "uri": "compose-preview://...", "crop": { "left": 0, "top": 0, "right": 200, "bottom": 80 } }
```

`crop` honours `observe`: the default `png` returns the cropped image plus
a small metadata block (resolved region, `ref`, source dimensions, sha);
`hash`/`semantics` return the crop's sha + dimensions (a region-scoped
change signal), and `semantics` also includes the matched node's subtree.

This is the **natural partner to `diff_semantics`**: when the diff says
"ref X changed", crop just ref X to look — a "one label moved" review goes
from a ~3k-token full-frame PNG pair to a few-hundred-token crop pair.
Reach for `crop` whenever you need to *see* a change but already know
which element moved; reserve the full-frame `observe="png"` for layout-wide
or "I don't know where it moved" cases.

### 6. Record → Compose UI test

`record_preview` gains `emitTest=true`: instead of an ephemeral
video/image, it emits a **durable, compilable Compose UI test** from the
recorded interaction.

- Each `input.click` carrying semantic data (`testTag`, `role`, or `text`)
  becomes a stable assertion, e.g. `onNodeWithTag("save-button").performClick()`
  — not pixel coordinates.
- `recording.probe` markers become labelled `// TODO assert` stubs for you
  to fill in.
- Pixel-only clicks (no semantic target resolved) become re-record hints in
  comments rather than fabricated assertions — the generator only emits what
  it could actually resolve; unresolved steps are marked skipped.
- Events are ordered by timestamp; the test scaffolds as
  `setContent { <Preview>() }` with the method derived from the preview's
  FQN. Variant previews share their base composable, so confirm the
  `setContent` call and add imports.

This is codegen: drive the preview once by semantic target, get a
regression test that pins the behaviour you just exercised.

### 7. Matrix render (multi-state, per-cell hashes)

`render_matrix` sweeps a preview across `device × locale × uiMode ×
fontScale` and returns **per-cell hashes** rather than N full PNGs —
roughly `cellCount × ~40` tokens instead of `cellCount × ~1.5k` PNG reads,
capped at 24 cells. Use it to confirm a change is stable across the state
matrix; pull the PNG for only the cells whose hash moved.

### 8. Structured render failures (typed `kind` + fix hint)

When a render fails, the daemon doesn't hand back an opaque stack trace —
it classifies the failure into a typed `kind` and, for recognized skew
signatures, a one-line fix hint. Read the `kind`/hint instead of parsing
the throwable:

| `kind` | Means | Typical fix hint |
|---|---|---|
| `compile` | Source didn't compile | Fix the compile error the daemon surfaces. |
| `runtime` | Threw while composing/rendering | e.g. "preview must be a `@Composable` with no required params"; Robolectric SDK below `compileSdk` → set `composePreview.sdkVersion`. |
| `capture` | Robolectric capture path failed | e.g. allow `$HOME/.robolectric-download-lock` in a restricted sandbox; usually a Robolectric × `compileSdk` skew. |
| `timeout` | Render exceeded the deadline | Retry / widen the timeout; check for an infinite recomposition. |
| `internal` | Unclassified daemon error | Report it. |

Act on the `kind` directly — a `capture` lock failure is a sandbox
allowlist fix, not a code change; a `runtime` "not `@Composable`" is a
state-hoisting fix (extract a zero-arg preview). Don't re-run the
installer or kill the daemon on a failed render — see
[mcp.md § Troubleshooting](./mcp.md#troubleshooting-first--when-not-to-act).

## Putting it together — a loop

1. **Inspect.** `observe="semantics"` to learn the tree and grab refs/tags.
2. **Act.** Interactive input with `target: { testTag }` (or `ref` /
   `role`+`text`) — no pixels.
3. **Gate.** `observe="hash"` (or `render_matrix` for multi-state) — did
   anything move?
4. **Explain.** If it moved, `diff_semantics(before, after)` for the
   structured delta.
5. **See.** When the delta names a `ref`, `render_preview crop={ ref }` to
   see just that element; reserve a full `observe="png"` for layout-wide
   changes or when you don't yet know where it moved.
6. **Pin.** When the behaviour is right, `record_preview emitTest=true` to
   capture it as a Compose UI test.

Every step before #5 avoids a PNG read entirely, and #5 reads a cropped
element rather than the full frame where it can — that's where the token
budget goes. A failed render short-circuits to its typed `kind` + fix hint
(§8) rather than a stack trace.

## See also

- [mcp.md](./mcp.md) — the MCP server that exposes these tools, plus
  `render_preview` overrides/force and the resource catalogue.
- [capture-modes.md](./capture-modes.md) — scripted recordings, animation
  captures, scrolling.
- [data-products.md](./data-products.md) — structured per-render data
  (a11y findings, layout tree, recomposition heat-map) alongside the PNG.
- [compose-preview-review](../../compose-preview-review/SKILL.md) — using
  base+head renders for PR review (the diff workflow `diff_semantics`
  complements).
