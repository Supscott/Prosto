# Getting access to a preview server

Some `compose-preview serve` deployments are gated. If you are an agent and a
request comes back `404` (a token-gated box says "not found" rather than
confirming it exists), or a live preview socket closes with
`github auth required`, you cannot see that server — and you should **not** ask
your human for the server's own `--token`. That token is permanent, unscoped,
unrevocable in practice, and once it is in your context it is in a transcript.

Ask for a grant instead. You print a link; a human opens it, checks a code, and
approves. You get a short-lived, scoped, revocable token.

The flow below uses the `compose-preview` CLI. If your only transport is MCP,
the same handshake is available as the `request_access` / `poll_access` tools on
the server's own MCP endpoint — see
[`references/catalog-mcp.md`](./catalog-mcp.md), which also covers what that
endpoint lets you do once you hold a grant. Everything on this page about
scopes, approvers and revocation applies either way.

## Ask

```bash
compose-preview auth request \
  --server https://preview.coo.ee \
  --scope live \
  --ttl 2h \
  --label "fix wear-m3-catalog#68"
```

```
Ask a human with access to https://preview.coo.ee to open this and approve:

  https://preview.coo.ee/agent-access/9c2Qk1pTf0Xb7hLm4nRzQA
  verification code: KX7M-9QD4

  They will be asked to grant: live · 2h · "fix wear-m3-catalog#68"
  The code above must match what they see on that page.

Waiting for approval (this request expires in 10m)…
```

**Relay that block to your human verbatim.** Both lines matter: the link is
where they approve, and the code is how they tell your request apart from
someone else's. Do not paraphrase the code, do not drop it because it looks
redundant, and do not "helpfully" shorten the URL.

The command then blocks until they approve, and stores the token it gets back.
After that, ordinary `compose-preview` commands against that host just work.

`--label` is shown to the approver as the *purpose*, so make it specific — an
issue number and what you are trying to see. "compose-preview CLI on some-host"
is what they get if you omit it, and it tells them nothing about whether to say
yes.

## Scopes: ask for the least that does the job

Cumulative — each includes the ones above it.

| `--scope` | What it lets you do | Ask for it when |
|---|---|---|
| `preview` (default) | Browse catalogs, fetch rendered previews, read `/status` | You need to *look* at published renders |
| `live` | Open live preview sessions (starts a render daemon on that machine) | You need to re-render with different knobs, themes, or device configs |
| `playground` | Compile and run Kotlin on that machine | Almost never — and most servers refuse to grant it at all |

A grant that falls short of what a route needs gets a `403` naming the scope it
lacks, not a sign-in redirect. That is the signal to ask your human for a wider
grant, not to retry.

`preview` covers reading what is already rendered. Anything that makes the
server *render for you* — `/render/<id>.png` with an override query, a `.svg` /
`.slots` / `.a11y` suffix, or `/bundle.zip` — is `live`, because it spends that
machine's CPU. If you expect to re-render with different themes, devices or
locales, ask for `live` up front rather than discovering it one `403` at a
time.

**A grant never covers the ingest lanes.** `POST /bundles/{name}` and
`POST /docs` — where a client contributes content to someone else's box — take
the operator's own token and refuse grants outright, whatever scope you hold. If
you genuinely need to publish something there, that is a separate conversation
with the operator, not a wider `--scope`.

Asking for more than you need is not free: `live` spends the host's CPU, and
`playground` is arbitrary code execution on someone else's box. A server's
operator caps what may be granted, and an approver can never pass on a
capability they do not hold themselves — so a request for `playground` commonly
comes back approved as `live`, which is not an error.

## While you wait, and after

```bash
compose-preview auth status     # what you hold, checked against the server
compose-preview auth token      # just the bearer, for curl or another tool
compose-preview auth revoke     # hand it back when you are done
```

If you would rather not hold a process open, `--no-wait` prints the link and
exits. The request is remembered locally, so once your human approves, the next
`auth status` — or `auth token` — collects the token for you. You do not have to
ask again.

`auth status` asks each server whether the grant it has on file is still live, so
a grant the operator revoked reads as gone rather than being reported as usable
right up until some other command fails with an unexplained `404`. A server it
cannot reach reads `unverified`; that is not the same as expired, and not a
reason to request a second grant.

`--json` emits JSON Lines — one compact document per line. A waiting
`auth request --json` gives you the request first (including the device secret,
if you would rather poll `POST /agent-access/poll` yourself) and then the grant.

**Revoke when the task is done.** A grant you no longer need is a credential
sitting on someone's machine for no reason. It expires on its own, but ending it
yourself is the right habit, and it costs one command.

## Things that will not work, so don't try them

- **Approving your own request.** The approval page needs a human identity — a
  GitHub session cookie, the operator's token, or on a private server both — and
  a grant is none of those. A grant cannot approve another grant, or revoke
  someone else's.
- **Treating the link as the credential.** It carries a request id, nothing
  more; the token is delivered to the process that asked, against a secret that
  never left it. So the link is safe to paste into chat — and useless if you
  were hoping to skip the approval.
- **Fetching the approval URL yourself to "check on it".** Approval is a POST
  from the page, deliberately, so that nothing which merely *fetches* a URL can
  grant access. Poll instead; that is what the poll route is for.
- **Retrying `auth request` in a loop** because the first one was not approved
  in thirty seconds. The routes are rate-limited per address, and a human is
  reading a consent page. One request, then wait.

## If the server does not offer this

`POST /agent-access/request` may 404, because the lane is not universal.

Whether it is on depends on how the server was started. Someone running
`compose-preview serve` by hand has it **off** unless they passed
`--agent-grants`. A box built from the published Docker image enables it
**automatically wherever it has someone who could approve** — GitHub OAuth
configured, or token-gated with an operator token — and only a fully open box
with no sign-in has neither, where the server refuses the lane outright rather
than letting anonymous visitors mint credentials.

So a 404 here is not "they forgot a flag": on the image it more likely means the
box genuinely has nobody who could approve. Say so plainly and let your human
decide. The fix is on their side (`--agent-grants`, or `SERVE_AGENT_GRANTS=1`
for the image, both documented in
[the preview-server docs](https://github.com/yschimke/compose-ai-tools/blob/main/docs/public-preview-server.md#granting-an-agent-temporary-access---agent-grants)),
and it is their call whether this box should be handing out credentials at all.
Do not fall back to asking for the operator token.
