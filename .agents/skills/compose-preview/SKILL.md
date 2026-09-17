---
name: compose-preview
description: Render Compose @Preview functions to PNG outside Android Studio. Use this to verify UI changes, iterate on designs, and compare before/after states across Android (Jetpack Compose) and Compose Multiplatform Desktop projects.
---

# Compose Preview

Render `@Preview` composables to PNG images without launching Android Studio.
Works on both Android (Jetpack Compose via Robolectric) and Compose Multiplatform
Desktop (via `ImageComposeScene` + Skia).

Maintained at [github.com/yschimke/skills](https://github.com/yschimke/skills)
under `skills/compose-preview/`. The CLI, Gradle plugin, and renderer ship from
[github.com/yschimke/compose-ai-tools](https://github.com/yschimke/compose-ai-tools);
this skill documents how an agent drives them.

Run `compose-preview --version` to see the installed CLI bundle, `compose-preview doctor`
to compare against the latest release (warns when the local copy trails), and
`compose-preview update` to re-run the bootstrap installer.

## What this skill provides

- A Gradle plugin (`ee.schimke.composeai.preview`) that discovers `@Preview`
  annotations from compiled classes and registers rendering tasks.
- A `compose-preview` CLI that drives the Gradle build via the Tooling API
  and surfaces rendered PNG paths.
- A VS Code extension with a preview panel, CodeLens and hover actions on
  `@Preview` functions, and commands for rendering all or a single file.

## Gradle tasks

Applied to each module that declares the plugin:

| Task | Purpose |
|------|---------|
| `:<module>:composePreviewDiscover` | Scan compiled classes, emit `build/compose-previews/previews.json`. |
| `:<module>:composePreviewRenderAll` | Discover + render every `@Preview` to PNG under `build/compose-previews/`. |
| `:<module>:composePreviewDiscoverAndroidResources` | Walk `res/drawable*` + `res/mipmap*`, parse `AndroidManifest.xml`, emit `build/compose-previews/resources.json`. See [references/resource-previews.md](./references/resource-previews.md). |
| `:<module>:composePreviewRenderAndroidResources` | Render every discovered XML drawable / mipmap to PNG / GIF under `build/compose-previews/renders/resources/`. |

All Gradle-cacheable with strict configuration caching — unchanged inputs
produce no re-work.

## CLI

The CLI auto-detects the Gradle project root (walks up for `gradlew`) and, by
default, every module that has the plugin applied.

```
compose-preview <command> [options]

Commands:
  show     Discover + render previews; print id, path, sha256, changed flag
  list     List discovered previews
  render   Render previews; with --output copies a single match to disk
  a11y     Render previews and print ATF accessibility findings
  extensions run a11y-annotated-preview.render
           One-shot a11y hierarchy + ATF + annotated overlay render
  doctor   Verify Java 17+ + project compatibility (run before Setup)

Options:
  --module <name>      Target a single module (default: auto-detect)
  --variant <variant>  Android build variant (default: debug)
  --filter <pattern>   Case-insensitive substring match on preview id.
                       Narrows what Gradle renders, not just what prints
  --id <exact>         Exact match on preview id. Also narrows the render
  --json               Emit JSON (show, list)
  --output <path>      Copy matched preview PNG to this path (render)
  --progress           Print per-task milestone/heartbeat lines to stderr
  --verbose, -v        Full Gradle build output (implies --progress)
  --timeout <seconds>  Gradle build timeout (default: 300)
  --force=<reason>     Sanctioned escape hatch for stale renders: passes
                       --rerun-tasks to Gradle. Does NOT run :clean and
                       does NOT touch build/classes/. Logs the reason and
                       points at issue #924 — please report.
```

OSC 9;4 terminal progress (native taskbar/tab progress bar) is on by default
in a TTY and auto-disables when stdout is piped. Textual progress lines are
opt-in via `--progress`.

Exit codes: `0` success, `1` build failure, `2` render failure, `3` no previews.

`--json` output per entry includes the full `PreviewParams` (device, widthDp,
heightDp, fontScale, uiMode, …), the absolute `pngPath`, the `sha256` of
the PNG bytes, and a `changed` boolean computed against the previous
invocation. State is persisted per-module under
`<module>/build/compose-previews/.cli-state.json` and gets wiped by
`./gradlew clean`.

### The `counts` block, and what "no PNG" means

`show --json` wraps the rows in a versioned envelope whose `counts` block
summarises the run, so an agent can decide what to read without walking every
entry:

```json
"counts": { "total": 37, "changed": 1, "unchanged": 33, "missing": 1, "skipped": 2 }
```

The four buckets **partition** `total` — every preview is in exactly one, and
`changed + unchanged + missing + skipped == total`. What each one means:

| Bucket | Meaning |
|--------|---------|
| `changed` | At least one capture's `sha256` differs from the previous run. These are the PNGs worth reading. |
| `unchanged` | Rendered, and pixel-identical to last time. |
| `missing` | **No PNG, and that is a render failure** — the set `--missing-renders` gates on. Worth investigating. |
| `skipped` | No PNG, and the miss is *expected*: every absent capture is declared `optional`, or the preview is a kind that never emits a PNG (an `@XrSubspacePreview` composite). Not a failure. |

The same distinction shows up in the text output's per-row tags, so don't read
a bare `[no PNG]` off every empty row:

```
MainActivity (activity__MainActivity) [no PNG]
RedirectUriReceiverActivity (activity__RedirectUriReceiverActivity) [no PNG, optional]
SpatialPanelPreview (p.SpatialPanelPreview) [no PNG, by design]
```

Only `[no PNG]` is a failure, and it marks exactly the previews the
"Render task completed but produced no PNG for N of M preview(s)" summary
enumerates underneath. A `[no PNG, optional]` row is a best-effort capture that
was never guaranteed to render — a non-launcher activity that needs intent
extras discovery can't guess, a desktop `@ColorCatalog` sheet — so treat it as
information, not as something to fix. Per capture, the `optional` boolean on
each entry in `captures[]` carries the same fact in the JSON.

`skipped` and the qualified tags arrived together; a CLI bundle predating them
tags every empty row `[no PNG]` and emits no `skipped` key, and its buckets do
not add up to `total`. Check `compose-preview --version` before relying on a
residual computed from `counts`.

## Iterating on a design

`list` → edit → `show --json` → read the PNGs whose `changed: true`. Gradle
caching means re-renders only redo what changed; the `changed` flag lets
agents skip reading PNGs that didn't move. Always read the PNG after a UI
change — don't assume the change looks correct.

### Render only the preview you're iterating on

`--filter` / `--id` narrow **what Gradle renders**, not just what gets
printed. Asking for one preview used to render the whole module — measured at
317s against 3s on the CLI's own 64-preview sample — so this is the flag to
reach for when working on a single screen, rather than `--force` or deleting
`renders/` by hand.

```sh
compose-preview show --json --filter HomeScreen
```

What a narrowed run does to everything else:

- **Previews outside the request keep whatever PNG the previous run left on
  disk**; on a clean tree they simply have none. `show` scopes its counts to
  the request for that reason, so don't read a smaller total as previews
  having disappeared.
- **Change detection is unaffected.** A narrowed run carries the skipped
  previews' shas forward, so a later full render doesn't report them all as
  `changed`.
- **A filtered render is deliberately not build-cacheable**, so it can't
  poison a clean checkout — and because the filter is a task input, an
  unfiltered run afterwards re-renders everything.
- **`render --bundle` still renders the full module by design.** A bundle
  omits previews that have no PNG, so a narrowed bundle would ship exactly
  the one preview you asked for and nothing else.

For a long-lived **interaction** loop — clicking/typing by semantic ref
(not pixels), checking "did it change?" without reading a PNG, and diffing
semantics instead of pixels — see the Playwright-style, token-frugal
[references/agent-loop.md](./references/agent-loop.md).

### Don't spell render filenames by hand — read them from the manifest

A rendered file is named `<readable>-<digest>.<ext>`:

```
renders/ActivityListPreview_Devices_Large_Round-4f9c2a17.png
        └──────────── readable ──────────────┘ └ digest ┘
```

`<readable>` is the function name plus any `@Preview(name = …)` variant, with
non-alphanumeric runs collapsed to `_`. `<digest>` is 8 hex characters derived
from the preview id. It is what makes the name unique and stable: adding or
renaming any *other* preview never renames this one, and two previews can never
land on the same file — including on case-insensitive filesystems, and including
names that differ only in punctuation.

The practical consequence: **you cannot reconstruct a filename from a preview
id, and you shouldn't try.** Read `renderOutput` off the preview in
`previews.json` (or the `show --json` output), which is authoritative. Structural
suffixes are appended after the digest — `…-4f9c2a17_SCROLL_top.png`,
`…-4f9c2a17_PARAM_4.png` — so a glob on the readable prefix also works when you
just need "every capture of this preview".

Preview **ids** are unaffected and keep their full FQN
(`com.example.PreviewsKt.HomeScreenPreview`) — `--filter` / `--id`, history
folders and CLI state all still key by id.

## Vector (SVG) output, not just PNGs

The renderer can export a preview as **scalable vector art** as well as a
raster: `compose/semantics-wireframe` (a schematic structural wireframe) and
`compose/figma-svg` (a **layered, editable** design-fidelity SVG — each
composable a named `<g id>` layer, with real fills/strokes, editable text, and
token bindings). Reach for these when the target scales to arbitrary sizes or
must land as named layers in a design tool rather than flat pixels — they are
what the design-catalog/Figma skills import as crisp vectors. See
[references/data-products.md § SVG vector output](./references/data-products.md).

## Running other Gradle builds (use build-brief)

`compose-preview` is the right tool for **rendering previews** — prefer it
whenever the goal is to see a composable. For any **other** Gradle work an
agent needs to run directly (`build`, `assemble`, `test`,
`connectedCheck`, a custom task), reach for
[build-brief](https://github.com/static-var/build-brief) (`bb`) instead of
raw `./gradlew`. It wraps `gradle`/`./gradlew`, preserves the exit code,
keeps the full raw log on disk, and trims terminal output to failed
tasks/tests, warnings, build-scan URLs, and final status — typically a
90%+ token reduction on noisy builds.

```sh
# Install once (Linux/macOS); self-contained Go binary, no JDK of its own.
curl -fsSL https://bb.staticvar.dev/install.sh | bash

build-brief test
build-brief ./gradlew assembleDebug
build-brief gradle build
```

Guidance for agents: **prefer `compose-preview` for previews**; use
`build-brief` whenever you'd otherwise invoke Gradle directly so the build
output stays cheap to read. See
[references/agent-cloud.md](./references/agent-cloud.md) for the cloud
install/allowlist details.

## Designing composables for previewability

`@Preview` only calls composables with zero arguments (or all-default), so
anything taking a `ViewModel`, repository, or DI-injected service can't be
previewed directly. Apply **state hoisting**: split each screen into a
stateful wrapper (wires runtime deps) and a stateless inner composable that
takes state + callbacks. Preview the stateless layer with hand-rolled
fixtures.

**Agent guidance:** if asked to iterate on a composable that accepts a
ViewModel or injected dependency, first propose extracting a stateless
inner composable and preview that. The one-time extraction unlocks the
fast `compose-preview` iteration loop for every future change on that
screen. See [references/state-hoisting.md](./references/state-hoisting.md) for
the pattern with code.

## Setup

The plugin is on Maven Central — most projects already have `mavenCentral()`
in their plugin repositories, so no credentials or extra registry config.

**Agents: check first, install only when missing.** Run
`compose-preview --version && compose-preview doctor` to see whether the CLI
is already available — if it is, you're done. Don't blindly re-run the
installer between previews; the script is idempotent for same-version runs
but still does network probes.

If `compose-preview` isn't on `$PATH`, this skill ships a self-bootstrapping
stub. Invoking it once downloads the real CLI and re-execs:

```sh
bash "$SKILL_DIR/scripts/compose-preview" --version
```

(replace `$SKILL_DIR` with the absolute path to this skill bundle, e.g.
`~/.claude/plugins/yschimke-skills/skills/compose-preview/` or
`~/.claude/skills/compose-preview/`). Subsequent invocations of
`compose-preview` find the installed CLI on `$PATH` and skip the
bootstrap.

To install (or upgrade) explicitly, point any consumer at the canonical
installer:

```sh
curl -fsSL https://raw.githubusercontent.com/yschimke/skills/main/scripts/install.sh \
  | bash
compose-preview doctor
```

Re-running the same command upgrades to the latest release; pin a specific
version by appending it (`… | bash -s -- 1.79.0`).

`doctor` verifies Java 17+ on `PATH` (JDK 21/25 are fine — the renderer is
compiled to JDK 17 bytecode). If the install path isn't on `PATH`, the
script prints the exact command to add it.

From a Compose project root, install the MCP server descriptors:

```sh
compose-preview mcp install                  # auto-detects Antigravity
compose-preview mcp install --antigravity    # force the Antigravity config write
```

`mcp install` is a one-time bootstrap. If a render misbehaves, do **not**
re-run it and do **not** kill the daemon — run `compose-preview mcp doctor`
first and follow the verdict it prints. The supervisor respawns daemons
automatically on classpath changes. See
[references/mcp.md § Troubleshooting](./references/mcp.md#troubleshooting-first--when-not-to-act).

Apply the plugin in `<module>/build.gradle.kts` (replace the version with
the latest from
[compose-ai-tools releases](https://github.com/yschimke/compose-ai-tools/releases/latest)):

```kotlin
plugins {
    id("ee.schimke.composeai.preview") version "<latest>"
}

composePreview {
    variant.set("debug")   // Android build variant (default: "debug")
    sdkVersion.set(35)     // Robolectric SDK version (default: 35)
    enabled.set(true)      // set false to skip task registration
}
```

`sdkVersion` auto-detects from `android.compileSdk` when unset, but the render
range is narrower than the compile range: **SDK > 35 requires JDK 21+**. On a
project that compiles against a newer SDK (37 is current for the Android
samples) the build fails at configuration time with
`sdkVersion = N is outside the supported range`. Pin it explicitly, or run the
build on JDK 21+.

### Zero-Code Integration (Alternative)

You can apply the plugin dynamically without modifying the project's source code by using a Gradle init script. This is useful for agents operating in environments where they shouldn't or cannot modify the build files directly.

> **VS Code users:** the [`Compose Preview` extension](https://github.com/yschimke/compose-preview-vscode) already passes a bundled init script via `--init-script` on every Gradle invocation it makes, so its renders pick up Android / Compose projects with no extra setup. The instructions below are for CLI and CI flows that go through `./gradlew` directly.

Create a file named `~/.gradle/init.d/compose-ai-tools.gradle` with the following content:

```groovy
allprojects {
    buildscript {
        repositories {
            gradlePluginPortal()
            mavenCentral()
        }
        dependencies {
            classpath "ee.schimke.composeai.preview:ee.schimke.composeai.preview.gradle.plugin:latest.release"
        }
    }

    afterEvaluate { project ->
        if (System.getenv("COMPOSE_AI_TOOLS") == "true") {
            if (project.plugins.hasPlugin("com.android.application")) {
                if (!project.plugins.hasPlugin("ee.schimke.composeai.preview")) {
                    project.pluginManager.apply("ee.schimke.composeai.preview")
                    println "Applied ee.schimke.composeai.preview to ${project.name} via init script"
                }
            }
        }
    }
}
```

To enable it, set the environment variable:
```sh
export COMPOSE_AI_TOOLS=true
```

CMP Desktop projects additionally need
`implementation(compose.components.uiToolingPreview)` — the bundled `@Preview`
annotation has `SOURCE` retention and is invisible to classpath scanning
otherwise.

The Android variant relies on Robolectric with native graphics; the plugin
takes care of the relevant test/tooling dependencies. Agents MUST NOT run
internal tasks like `collectPreviewInfo` — they're wired by the plugin itself.

## Reference docs

Loaded on demand. Read only what the current task needs.

| Path | When to read |
|---|---|
| [references/permissions.md](./references/permissions.md) | Setting up agent allowlists; staging PNGs outside `build/`. |
| [references/server-access.md](./references/server-access.md) | Getting into a gated `serve` deployment: ask for a temporary scoped grant with `compose-preview auth request`, relay the link + verification code to a human, and revoke when done — instead of asking for the server's own `--token`. |
| [references/runtime-permissions.md](./references/runtime-permissions.md) | Pinning Android runtime permissions per render via `renderNow.overrides.permissions`; reading the `compose/permissions` data product. |
| [references/state-hoisting.md](./references/state-hoisting.md) | Full state-hoisting pattern with code examples. |
| [references/override-knobs.md](./references/override-knobs.md) | Author-declared editable values (`previewOverride*`): re-render a published bundle with new text / colours / counts and no source rebuild, and declare a closed value set so an axis shows its alternatives instead of a bare text field. |
| [references/capture-modes.md](./references/capture-modes.md) | Multi-preview annotations, `@AnimatedPreview` GIFs, `@SettledPreview` for content that arrives late, MCP scripted recordings, paused-clock snapshots, scrolling captures. |
| [references/a11y.md](./references/a11y.md) | ATF accessibility checks (`compose-preview a11y`). |
| [references/data-products.md](./references/data-products.md) | Structured per-render data (a11y findings + hierarchy, layout tree, recomposition heat-map, editable `compose/figma-svg` + wireframe **SVG vector** export, …) via MCP tools and on-disk Gradle output. |
| [references/mcp.md](./references/mcp.md) | Driving compose-preview from an MCP-aware agent host (push notifications, multi-workspace, in-process server bundled in the CLI). The **local** surface — daemons on your machine. |
| [references/catalog-mcp.md](./references/catalog-mcp.md) | The **remote** surface: a `compose-preview serve` deployment's own MCP endpoint (`POST /mcp`, from [compose-preview-server](https://github.com/yschimke/compose-preview-server)). Discover and render published catalogs with no checkout — the tool table, the `request_access` / `poll_access` handshake, snapshot vs made-to-order lanes, and the render timeline. |
| [references/agent-loop.md](./references/agent-loop.md) | Playwright-style, token-frugal interaction loop: target by semantic ref (not pixels, Desktop + Android), `observe=semantics\|hash`, `diff_semantics`, `render_preview crop` (one element), `render_matrix`, `record_preview emitTest=true`, and typed render-failure `kind`s. |
| [references/cmp-shared.md](./references/cmp-shared.md) | Compose Multiplatform `:shared` modules (`commonMain` previews via Desktop pipeline). |
| [references/resource-previews.md](./references/resource-previews.md) | Android XML resources (`<vector>`, `<animated-vector>`, `<adaptive-icon>`). |
| [references/wear-ui.md](./references/wear-ui.md) | Wear OS Material 3 Expressive design. |
| [references/wear-tiles.md](./references/wear-tiles.md) | Wear Tiles (protolayout, not Compose). |
| [references/remote-compose.md](./references/remote-compose.md) | Remote Compose dialect, `RemoteDocument`, and the wrapper + connector requirement for recorded `.rc` export. |
| [references/agent-cloud.md](./references/agent-cloud.md) | Running compose-preview in Claude Code cloud sandboxes (allowlist, JDK, install paths). |
| [references/vscode.md](./references/vscode.md) | VS Code extension (humans, not agents). |

## Related skill

PR-review workflows live in the sibling
[**compose-preview-review** skill](../compose-preview-review/SKILL.md):
authoring agent-opened PRs, and reviewing UI PRs locally (base + head
render, diff, text comment). Wiring the CI that does this automatically —
`compose-preview/main` baselines, PR-comment GitHub Actions, the fork-safe
two-stage split — is the
[**compose-preview-ci** skill](../compose-preview-ci/SKILL.md). The
bootstrap installer
([`scripts/install.sh`](https://raw.githubusercontent.com/yschimke/skills/main/scripts/install.sh))
sets all of them up together.
