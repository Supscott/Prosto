# Catalog MCP (a remote preview server, over MCP)

There are **two** compose-preview MCP surfaces, and they are not the same
server. Pick the right one first — most confusion here is an agent aiming
local instructions at a remote box, or the reverse.

| | [`references/mcp.md`](./mcp.md) — **local daemon MCP** | **catalog MCP** (this file) |
| --- | --- | --- |
| What it drives | daemons on *your* machine, over a checkout you can build | a `compose-preview serve` deployment someone else runs |
| Transport | stdio, launched by your agent host | Streamable HTTP, `POST https://<host>/mcp` |
| Needs a checkout | yes | no |
| Credential | none — it is your machine | a scoped grant, always |
| Can register projects, watch files, rebuild | yes | no — those stay local |
| Ships from | [`compose-ai-tools`](https://github.com/yschimke/compose-ai-tools) (`mcp/`) | [`compose-preview-server`](https://github.com/yschimke/compose-preview-server) |

Reach for catalog MCP when you have **a server URL and no checkout**: reviewing
a published catalog, comparing a preview against what it looked like last week,
or pulling a11y data for a component you cannot build locally. If you have the
repo, the local daemon MCP is cheaper and more capable — use that.

Upstream design doc, which this file summarises for agents:
[`docs/design/CATALOG_MCP.md`](https://github.com/yschimke/compose-preview-server/blob/main/docs/design/CATALOG_MCP.md).

## It is opt-in, and it always needs a grant

The endpoint exists only if the operator started the server with
`--catalog-mcp` (container: `SERVE_CATALOG_MCP=1`). It cannot be turned on
without a working agent-grant lane — a server started with `--catalog-mcp` and
no `--agent-grants` refuses at startup rather than exposing an anonymous
machine API. So there is no such thing as an open catalog MCP, even on a box
whose ordinary catalog pages are public.

A `POST /mcp` with no credential answers `401` with `WWW-Authenticate: Bearer`
and an `X-Compose-Preview-Agent-Access` header naming the absolute
grant-request URL. That header **is** the discovery mechanism — read it rather
than guessing the flow.

## Getting in without leaving MCP

[`references/server-access.md`](./server-access.md) covers the grant model —
the scopes, what to ask for, what an approver can and cannot do, and why you
never ask for the server's own `--token`. **Read it first; all of it applies
here.** The only thing this file adds is that a client which speaks MCP and
nothing else does not need the `compose-preview auth` CLI:

1. `tools/call request_access` — optionally `scope`, `ttlSeconds`, `label`,
   `capabilities`. Returns `approveUrl`, `userCode`, and a `deviceSecret` to
   keep.
2. **Show the human both the link and the code**, verbatim, and ask them to
   check the code on the page matches before approving. Same rule as the CLI
   flow: the code is how they tell your request from someone else's.
3. `tools/call poll_access` with `requestId` + `deviceSecret`. It **holds the
   call open** and answers the moment the human decides — `waitSeconds`
   defaults to 8 and may be raised to 30. A timeout answers `pending`; just
   call again. Do not busy-poll: each poll is a whole round trip through you,
   which is exactly what the waiting call exists to avoid.
4. Send the token as `Authorization: Bearer <token>` on every later call.

These two tools mirror `POST /agent-access/request` and
`POST /agent-access/poll` exactly — same bodies, same per-address rate limit —
so use whichever transport you already have, not both.

**`initialize`, `ping`, `tools/list`, `request_access` and `poll_access` need
no credential. Everything that reads a catalog does.** The gate is per message,
not per endpoint, so an unauthenticated client can still complete the handshake
and discover the way in.

**Grants live in memory.** A server restart or redeploy drops every grant
regardless of its remaining TTL. A sudden `401` mid-task is that, not a bug —
ask for a new grant the same way you asked for the first.

## The tool surface

Scope is cumulative: `live` includes `preview`. Ask for `live` up front if you
expect to re-render, rather than discovering it one `403` at a time.

| Tool | Scope | What it is for |
| --- | --- | --- |
| `status` | `preview` | Readiness and the aggregate catalog set. |
| `list_projects` | `preview` | Every remote catalog, with its stable id and preview count. |
| `list_previews` | `preview` | Previews and published metadata, across every catalog or one named catalog. |
| `resources/list`, `resources/read` | `preview` | The **published snapshot** lane — read a preview's already-rendered PNG. No render is spent. |
| `render_preview` | `live` | The **made-to-order** lane: render one preview, optionally with `overrides`. |
| `render_matrix` | `live` | One preview across the cross-product of `axes`, in a single call. |
| `list_devices` | `preview` | The ids the `device` override accepts, with each frame's dp size and density. |
| `history_list` | `preview` | One preview's render timeline. |
| `history_diff` | `preview` | Did the last publish move this preview? |
| `history_read` | `preview` | One historical render's pixels, by `commit` or `blob`. |
| `diff_semantics` | `live` | Compare two previews' semantics by authored `testTag`. |
| `list_data_products` | `preview` | Which structured products a preview exposes. |
| `get_preview_data` | `live` | Fetch the merged a11y or annotation product. |
| `list-all-documentation`, `get-documentation-for-story` | `preview` | Storybook-MCP-compatible discovery aliases. |
| `preview-stories` | `live` | Storybook-MCP-compatible rendering alias. |

Every catalog tool takes either a `uri`, or a `catalog` + `previewId` pair.
`list_projects` is how you learn the catalog ids; nothing needs client
reconfiguration when a catalog is added or retired.

### `resources/read` before `render_preview`

They are different lanes, not two spellings of one. `resources/read` returns a
snapshot that already exists and costs the host nothing; `render_preview`
spends that machine's CPU and needs `live`. If you only want to *look* at a
published render, read the resource.

### Observations: stay token-frugal

`render_preview` defaults to a semantics observation, not pixels — the same
stance as the local loop in [`references/agent-loop.md`](./agent-loop.md), and
for the same reason. `observe` takes:

- `semantics` (default) / `hash` — cheap; enough to answer "did this move?"
- `png` — pixels, when you actually need to look.
- `svg` — the `compose/figma-svg` vector export, returned as SVG **source** in
  a text block, not as a base64 image. That is deliberate: almost no MCP client
  renders SVG from an image block, and a vector consumer (a Figma round-trip, a
  diff, a DOM capture) wants the markup. `list_previews` reports `svgAvailable`
  per preview, so check there instead of asking and reading the refusal.
- `scroll-png` / `scroll-svg` — the full-page capture of a scrollable screen
  rather than the viewport crop.

`render_matrix` is the one to reach for when comparing axes: the cells share a
single catalog lease and are reported together, so N combinations cost one
round trip instead of N. It is capped, and `observe` there is `hash` or `png`.

### History, and telling a change from a flake

`history_list` reports whether a preview is **unstable** — re-renders
differently on every publish — alongside its timeline, and `history_diff`
carries the same flag. Check it before reporting a diff as a regression: a
difference on a nondeterministic preview is not a change. `history_diff`
compares metadata, so "did the bytes move?" is answered without fetching either
image; reach for `history_read` only when you want the pixels.

Where the server holds the timeline it comes back inline. Where the catalog is
published from a delivery branch, the manifest lives on that branch and
`history_list` tells you where to fetch it rather than pretending to have it.

## See also

- [`references/server-access.md`](./server-access.md) — the grant model, the
  scopes, and what to do when the lane is not offered at all.
- [`references/mcp.md`](./mcp.md) — the local daemon MCP, for when you have a
  checkout.
- [`references/data-products.md`](./data-products.md) — the `kind` vocabulary
  `list_data_products` / `get_preview_data` speak.
- [`references/agent-loop.md`](./agent-loop.md) — the token-frugal
  observe/diff loop these tools mirror.
