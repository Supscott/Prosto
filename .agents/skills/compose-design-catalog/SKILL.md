---
name: compose-design-catalog
description: Generate an importable design-artifact sticker sheet for a whole Compose component system (Compose M3, Wear Compose M3, Glimmer, Glance/Wear widgets). Use when you want a code-derived component catalog — each component in its primary modes, in two variants (ideal render + bordered layout), with extracted design tokens and accessibility greenlines — laid out for import into Figma, Google Stitch, or Claude Design. Pairs with the compose-preview and compose-preview-design-board skills.
---

# Compose Design Catalog

Render a Compose **component system** and export it as an importable **sticker
sheet**: every component in its primary modes, in two variants (the `ideal`
render and a `layout` render that borders every composable), with the system's
design tokens and an accessibility **greenline** layer extracted automatically
from the render — not transcribed from a spec.

This skill is the system-wide, sticker-sheet sibling of
**compose-preview-design-board** (which arranges an arbitrary set of renders
into one HTML brief). It assumes the **compose-preview** skill is installed —
that skill owns the renderer, CLI, and Gradle plugin. Check first with
`compose-preview --version`; if missing, run the bootstrap installer:

```sh
curl -fsSL https://raw.githubusercontent.com/yschimke/skills/main/scripts/install.sh \
  | bash
```

## Code is the source of truth

This pipeline is **code-led**. Every value on the sheet — padding, corner
radius, type, colour, touch-target size, `maxLines` / overflow — comes from the
renderer's own data products, so the catalog is correct by construction.
Published design kits (the Material 3 / Wear OS / Glimmer / Widget Figma kits)
are **seed/reference only**: use them for the component inventory and for naming
parity, never as authority. A kit/render divergence is a bug in the kit.

## When to use this skill

- You want a designer-ready catalog of a component library (yours, or a system
  like Material 3) generated from the code, refreshable on every change.
- You want the two-variant treatment — an `ideal` render *and* a bordered
  `layout` render — plus tokens and a11y annotations, in one bundle.
- You want artifacts that import into Figma / Stitch / Claude Design, on a
  branch a designer can pull from.

For *one-off* sets of renders → **compose-preview-design-board**. For *rendering*
or *reviewing* → **compose-preview** / **compose-preview-review**.

## Prerequisite: a catalog module

`@Preview` discovery is local-module only, so the components must be authored as
`@Preview` functions in a Gradle module that depends on the target library
(`androidx.compose.material3`, `androidx.wear.compose.material3`,
`androidx.xr.glimmer:glimmer`, `androidx.glance`, …). Author **one `@Preview`
per component × primary mode**, padded, with the breakpoints the system's kit
documents (e.g. `compact` / `medium` / `expanded` for M3; small/large round for
Wear). See the `samples/design-catalog-*` modules in
[yschimke/compose-ai-tools](https://github.com/yschimke/compose-ai-tools).

### Cataloguing an existing app (the cheap path)

A dedicated catalog module is right when you are documenting a *component
library*. When you are cataloguing an **app that already has `@Preview`
functions**, don't author a parallel set — point the spec at the previews that
are already there. Adoption is then a plugin line plus `catalog.spec.json`,
with **no change to UI code**, and the sheet stays honest because it renders the
same previews the team already maintains.

The [compose-samples catalogs](https://github.com/yschimke/compose-samples/tree/agent/preview-catalogs)
are built this way: JetNews covers 22 of its 23 existing previews without
touching a single composable.

Two rules make this work well:

- **Group by feature, not by widget.** The value of a sample/app catalog is
  showing what the app is *for* — adaptive postures, the states of a screen, an
  RTL mirror — so `groups`/`section` should follow that, not alphabetical
  component names.
- **Put catalog-only fixtures in `src/debug`.** Anything you *do* need to add
  (motion fixtures, a composed feature shot) belongs in the debug source set:
  the plugin renders the `debug` variant, so they are discovered like any other
  preview, but they never reach a release build.

## Declare & validate the spec (`catalog.spec.json`)

The catalog's inventory, grouping, captions, sections and per-component variants
are declared in a hand-authored **`catalog.spec.json`** committed next to the
module. Each component's `preview` **must equal an exact `@Preview` function
name** — a mistyped or renamed name renders nothing and surfaces only as a late
"missing" entry at the *end* of the (long) render. Its shape is documented by
[`scripts/design-artifacts/catalog.spec.schema.json`](https://github.com/yschimke/compose-ai-tools/blob/main/scripts/design-artifacts/catalog.spec.schema.json)
(reference it via `$schema` for editor validation).

Two build-free helpers in compose-ai-tools' `scripts/design-artifacts/` scan the
module's Kotlin source directly — no Gradle build, no render — so you author and
check the spec before spending a render:

```sh
# Scaffold a starter spec from the module's @Preview functions (one flat
# "Components" group; caption and regroup from there):
node scripts/design-artifacts/init-catalog-spec.mjs \
  --module :app --system my-system --title "My System" --out catalog.spec.json

# Resolve every `preview` (component + variant) against the discovered functions,
# with typo suggestions, structural checks, and coverage gaps. Exits non-zero on
# errors, so it runs as the pre-flight in design-artifacts.yml before the render:
node scripts/design-artifacts/validate-catalog-spec.mjs --spec catalog.spec.json
```

### Breakpoints: one card, or a card per size

A multipreview (`@WearPreviewDevices`, a local `@CatalogWearBreakpoints`) renders
one function at several device sizes, and the join keys on **function name** — so
by default they all fold into one entry carrying one image per size, tagged from
the spec's `breakpoints` table:

```jsonc
"breakpoints": [
  { "size": "smallRound", "device": "id:wearos_small_round", "widthDp": 192 },
  { "size": "largeRound", "device": "id:wearos_large_round", "widthDp": 227 }
]
```

Declare each by `device` (matched first) *and* `widthDp` (the fallback): a width
is a fingerprint two devices can share, and an undeclared device falls back to
the generic Material width class, making two renders indistinguishable on that
axis — the export warns when it sees one. A Wear catalog that declares no
`breakpoints` inherits the standard round table.

### Reproducing a kit: what splits, and what folds

A design-led catalog — one whose job is to reproduce a *published kit* rather than to publish its
own system — needs one taxonomy decision made consistently, and the tempting rule is the wrong one.

The kit models variation as **variant properties on a component set**: one `Button` set with
`Style = Filled | Tonal | Outline | Child`, one selection set with `Type = Checkbox | Radio |
Switch`. "One kit set is one catalog component" is the right default, and it is what keeps a sheet
browsable — 35 shapes are cells of one card, not 35 cards.

But it cannot be the whole rule, because some of those axes are **separate functions in code**:

> An axis **splits** into a component per value when its values are separate composables; it
> **folds** into cells when they are arguments to one.

`Style=` on the Wear kit's `Button` set splits — `Button`, `FilledTonalButton`, `OutlinedButton`,
`ChildButton` — because which one you call is the choice a reader of the catalog is making. The same
`Style=` on its `Button-Compact` set *folds*, because Compose ships one `CompactButton` that takes
emphasis as `colors`: there is no second function to choose, so there is nothing to split. The test
is the call site, not the word — "emphasis always splits" gets the second case wrong.

Two consequences worth planning for: components that split share their set's node (that is fine and
expected — each cell can still name the kit's own value via `kitAxis`/`kitValue`), and ids follow the
**code's** names while membership follows the **kit's** (`Button/Outlined` for the kit's
`Style=Outline`, because `OutlinedButton` is what a reader greps for).

### Determinism is a capture-state problem, not just a clock problem

Everyone remembers to pin the clock. The subtler cases are components whose *resting* state is not
what the kit draws, or is not stable:

- **A component at rest may be the wrong picture.** A swipe-to-reveal at rest is indistinguishable
  from the card underneath it, and every kit cell draws what the gesture uncovers — so seed its
  state (`rememberRevealState(RightRevealing)`) and publish the revealed component. What you pin is
  where the capture *starts*; the gesture still works in a live session.
- **Anything animated must be pinned, not merely started.** An indeterminate progress indicator, a
  placeholder shimmer, a loading spinner: a capture is one frame of it, and the frame differs on
  every publish. Give progress a fixed value, and keep a shimmer to the live lane.
- **Anything derived from "now" must be a literal.** A date picker opened on today, a time picker on
  now, a relative timestamp — each makes the delivery branch's history noise rather than change.

The cost of getting this wrong is invisible in review and expensive later: the render is green, the
diff is real, and nobody can tell a change from a re-render.

### Look at the renders, and then make a machine look at them

A catalog can be green end to end and still publish **blank cards**. The build compiles, discovery
finds the preview, the render succeeds, the bundle publishes — and the sticker is an empty frame.
Nothing in that pipeline asserts a component *drew* anything.

Review does not catch it either, and on a dark-first catalog it is nearly invisible: a sticker that
drew nothing looks exactly like a sticker that drew something dark.

Three real causes, all of which shipped green before being caught by eye:

- **A missing `Modifier.align`.** On Wear's page and scroll indicators, alignment is what makes the
  component lay out at all — without it they collapse to nothing, not to a mispositioned rail.
- **An unsettled animation.** A component whose content animates in from a `LaunchedEffect` captures
  as its first frame, and if that frame is `alpha = 0` the sticker is empty.
- **A component captured in the wrong state** — an at-rest swipe-to-reveal, a collapsed
  scroll-revealed button.

So render locally before publishing. An Android/Robolectric module needs no CLI at all —
`./gradlew :<module>:composePreviewRender` writes the PNGs — and the render is cheap enough
(seconds, for a couple of hundred previews) to be part of the edit loop rather than a CI-only step.

Then make it a test, because eyes do not scale to a growing sheet:

```kotlin
// composePreview { renderBeforeUnitTests.set(true) } puts the real renderer output where a unit
// test can read it — the same PNGs CI publishes, not a fixture.
@Test fun `no sticker publishes an empty frame`() { /* fail any capture with ~no visible pixels */ }
```

Assert that a sticker drew **something**, not that it drew the right thing: a real lower bound is a
per-component judgement that needs re-tuning on every legitimate change, while "not blank" never
does. And verify the guard fails — revert the fix, watch it name the offender, put the fix back. A
render test that has never failed is a render test that might be reading the wrong directory.

### Record what you did NOT reproduce

A design-led catalog is a claim about coverage, so the claim needs to be checkable. Commit a row per
published kit set carrying either the components that reproduce it or a **stated reason** it is
absent, and test it in both directions:

- a set with neither is an unstated gap;
- an **exclusion whose node something now references** is a decision nobody is making any more.

The second direction is the one that pays off over time. A kit routinely publishes things the code
cannot draw — a size the library has no counterpart for, a style whose painter overload only exists
on some components, an asset that is app content rather than a component — and each of those is a
fact worth writing down once. Without the reverse check, the note survives the limitation and starts
lying.

The same test catches the cheaper mistake: a variant cell **seeded but not implemented**. It renders
green, and it publishes the default picture under another cell's name.

### Dark-first systems

Wear draws its components on a black watch face, so a Wear catalog's component
sticker is a **single dark capture** on a transparent background — `modes:
["dark"]` in the spec, `display.surface: "dark"` so the server's front door
stages the hero on dark rather than washing a light-on-transparent sticker out,
and a local `@Preview(showBackground = false)` multipreview rather than a
light/dark pair.

That single mode is load-bearing beyond presentation: a dark-only catalog cannot
project a `design-map.json` today, because the projector pairs a component's
reference with its `_Light` capture. Wire parity accordingly — see
[design-parity-review](../design-parity-review/references/ci.md#a-dark-only-catalog-projects-zero-components).

When you want a **card per breakpoint** — its own id and caption — use `select`
rather than splitting the `@Preview` in the module (splitting costs the
multipreview's other axes, e.g. `@WearPreviewFontScales`):

```jsonc
{ "componentId": "Home/SmallRound", "preview": "HomeListViewPreview",
  "select": { "size": "smallRound" }, "caption": "Home — small round." },
{ "componentId": "Home/LargeRound", "preview": "HomeListViewPreview",
  "select": { "size": "largeRound" }, "caption": "Home — large round." }
```

Two entries may share one `preview` as long as each selects a different value. An
**annotation-led** inventory says the same thing in code with
`@CatalogComponent(id = "Layout/List", perBreakpoint = true)`, which yields
`Layout/List/smallRound`, `Layout/List/largeRound`, … — one per breakpoint the
function actually rendered at, in `breakpoints` order. It's a flag, not a list:
the multipreview below it already decides the devices, so the names come from the
renders. One breakpoint keeps the plain id; none resolved keeps the component
whole and warns. Adopting `perBreakpoint` on a published catalog **moves those
sticker URLs**, which is why it's opt-in — the preview server already
disambiguates merely *colliding* card labels on its own. A spec entry always
overrides the annotation. Full rules:
[`docs/design/DESIGN_CATALOGS.md`](https://github.com/yschimke/compose-ai-tools/blob/main/docs/design/DESIGN_CATALOGS.md).

Discovery recognises `@Preview` and any `annotation class` meta-annotated with it
(`@CatalogModes`, `@CatalogTemplate`, …); pass `--preview-annotation <Name>` for a
multipreview annotation imported from another module. The authoritative check
stays the render + completeness gate — this is the fast local/CI pre-flight.

> **Wear catalogs always need this flag.** Wear previews are conventionally
> annotated `@WearPreviewDevices` / `@WearPreviewFontScales` / `@WearPreviewLargeRound`,
> which live in `androidx.wear.compose.ui.tooling.preview` — an external
> artifact the source scan cannot see. Without the flags the validator reports
> **`discovered 0 @Preview function(s)`** and fails every entry, which reads
> like a broken spec rather than a missing flag:
>
> ```sh
> node validate-catalog-spec.mjs --spec catalog.wear.spec.json \
>   --preview-annotation WearPreviewDevices \
>   --preview-annotation WearPreviewFontScales
> ```
>
> Record the required flags in the spec's `$comment` so the next run doesn't
> rediscover this. The same applies to any app-defined multipreview annotation
> declared in a different module from the previews that use it.

### A bilingual catalog: the `locales` axis

A project whose previews fan out by **locale** could not be catalogued at all.
`@LocalePreviews` on one function mints `LanguageToggleButtonPreview_en` and
`…_ja`; the spec names the base function, both renders match it, and the
catalog's axes — `variant` / `state` / `theme` / `size` / `props` — had nowhere
to say "this is the Japanese one". The two arms folded onto the same output key
and the build was refused with **`duplicate output axes`**. Nothing was
misconfigured, so the error reads like a spec bug when it is a missing
declaration.

Declare the locales the sheet covers, and the arms become distinct renders:

```jsonc
"locales": ["en", "ja"]
```

An id whose trailing segment names a declared locale gets a `locale` **props**
axis — its own sticker (`…__locale-ja.png`), its own manifest entry, sitting
beside its sibling the way a `fontScale` variant does. Matching works like
`modes`: case-insensitive, longest-first, and only at a segment boundary, so
`ja` inside `Ninja` is not a locale. An id naming no declared locale stays
untagged and remains the component's primary sticker.

Locale is deliberately **not** wired into `modePriority` — deferring a whole
language to the live server is a different decision from deferring a palette.
Declaring `locales` costs nothing for a single-locale catalog, which is every
catalog that omits the field.

### Themes an imported project cannot declare (`themes`)

Themes normally start at a `@ThemeCatalog` provider — an annotation from this
toolchain. An **import** (someone else's repository, catalogued without their
involvement) has no dependency on it and never will, so for as long as themes
were annotation-only every imported catalog served an **empty Theme control** —
not because the upstream had one theme, but because nothing could say it had
more.

A spec's `themes[]` closes that, the same way `groups` closes missing
`@CatalogComponent` annotations: the inventory is written down in the import,
and the providers are generated into the throwaway checkout before anything
compiles. Discovery then scans an ordinary module that declares its themes, and
nothing downstream learns an import was involved — the chips,
`?theme=theme:<providerFqn>`, and one `themes/<fqn>.dtcg.json` per theme all
follow.

An entry is a **shape**, not a snippet, so it carries which themes are one
family and which are light or dark:

| `kind` | The upstream shape |
| --- | --- |
| `enum` | one theme composable over an enum's constants — `AppThemeWithBackground(LIGHT) { }` |
| `functions` | a whole composable per theme — `ThunderbirdBoltTheme { }` |
| `modes` | one composable with a boolean dark parameter — `ElementTheme(darkTheme = true) { }` |
| `arguments` | one composable, a named-argument list per theme |
| `wrapper` | a raw Kotlin body — the escape hatch |

```jsonc
"themes": [
  { "kind": "enum", "group": "Pocket Casts",
    "composable": "au.com.shiftyjelly.pocketcasts.compose.AppThemeWithBackground",
    "enum": "au.com.shiftyjelly.pocketcasts.ui.theme.Theme.ThemeType",
    "values": ["LIGHT", "DARK", "EXTRA_DARK", "ELECTRIC"] }
]
```

Two things that look like omissions and are not:

- **The constants are listed, not reflected.** Generation runs before the
  upstream compiles, so there is nothing to reflect against — and a listed
  constant that does not exist fails the module's compile *by name*, which
  beats a catalog that is quietly one palette short.
- **Nothing extra is baked.** Generated themes are a *live* axis: the switcher
  re-renders through the published bundle and the serve host's theme cache, the
  way `modePriority`'s deferred palettes do. Baking nine palettes × every
  component is exactly the cost this design avoids.

**First-party catalogs annotate their providers beside the code and omit
`themes` entirely.** The step is a no-op for them. Reach for the field only
when you cannot put an annotation in the source you are cataloguing.

## Workflow

1. **Render the system with its data products.** Ask the renderer for the
   captures plus the wireframe, theme, semantics, and a11y products:

   ```sh
   compose-preview show --module samples:design-catalog-m3 \
     --with-extension a11y,theme,semantics,semantics-wireframe --json \
     > /tmp/m3-show.json
   ```

   - `capture` PNGs → the `ideal` variant.
   - `compose/semantics-wireframe` (PNG/SVG) → the `layout` variant (bordered).
   - `compose/theme` → the token set (`colorScheme` + `typography` + `shapes`).
   - `compose/semantics` v6 → per-node bounds, padding, `textOverflow`
     (`maxLines` / `lineCount` / `truncated`).
   - `a11y/atf` + `a11y/touchTargets` → the greenline findings.

2. **Build and write the catalog.** The maintained path is the
   `generate-design-catalog.mjs` driver: it renders to a portable bundle with
   `compose-preview bundle pack --with-semantics`, joins it to `catalog.spec.json`
   (matching each component's `preview` to the rendered function name), and writes
   the importable bundle. This is exactly what `design-artifacts.yml` runs:

   ```sh
   compose-preview bundle pack --module samples:design-catalog-m3 --with-semantics \
     -o build/m3-bundle.png
   node scripts/design-artifacts/generate-design-catalog.mjs \
     --spec catalog.spec.json --renders build/m3-bundle.png --out out/ \
     --renderer "$(compose-preview --version | head -1)"
   ```

   **Scoping a pack — use the file flags for any generated list.** `bundle pack`
   takes `--id` (pack only these previews) and `--exclude-preview-id` (skip
   these), and both are **comma-separated**. A preview id may itself contain a
   comma: `@Preview(widthDp = 227, heightDp = 200)` mints
   `…AppCardRemote_width=227dp,height=200dp,dpi=320`. Passing such an id through
   the comma-separated flags splits it into three, which fails in two different
   ways depending on which flag you used:

   - `--exclude-preview-id` — a plain pattern matches by **substring**, so the
     orphaned fragment `dpi=320` matches every preview in the module and the
     render dies with *"excluded every one of the N preview(s) — nothing would
     render"*.
   - `--id` — the render survives (same substring matching), but
     `composePreviewBundle` matches ids **exactly** and fails with *"preview id
     not found: …AppCardRemote_width=227dp"*, naming the first fragment.

   So for anything generated — a shard list, a deferred palette, a design-map
   projection — pass a **file**, one id per line, which has no delimiter to
   collide with:

   ```sh
   compose-preview bundle pack --module :remote-catalog --with-semantics \
     --id-file slice-previews.txt \
     --exclude-preview-id-file slice-excluded.txt \
     -o build/candidates.bundle.png
   ```

   Both refuse an unreadable **or empty** file rather than falling back to an
   empty selection — an empty list means *everything* to both flags, so the
   quiet failure would be packing or rendering the whole catalog while reporting
   success. If you have nothing to select, omit the flag.

   Requires compose-preview **1.41.0+** (`--id-file`); `--exclude-preview-id-file`
   landed in 1.40.0. Hand-written lists of comma-free ids are fine on the inline
   flags.

   Under the hood the driver feeds the render's data products through
   `@design-parity/candidate`'s mappers (`nativeFindings`,
   `semanticsToSemanticTree`, `composeThemeToTokens`) into
   `@design-parity/catalog-export`. To build a catalog **without** a spec file
   (e.g. a custom pipeline), call that library directly:

   ```ts
   import { buildCatalog, writeCatalog } from "@design-parity/catalog-export";

   const catalog = buildCatalog(
     { system: "compose-m3", title: "Compose Material 3",
       library: ["androidx.compose.material3:material3"],
       renderer: "compose-preview 0.17.2" },
     sources, // one ComponentSource per component (ideal+layout images, tokens,
              // semantics, findings) from the mappers above
   );

   await writeCatalog(catalog, ".design-artifacts/compose-m3", {
     sourceRoot: "build/compose-previews",
   });
   ```

   Pass `--source-repo <owner>/<repo>` whenever you generate from a **consumer**
   repo. It defaults to `yschimke/compose-ai-tools`, so a bundle built anywhere
   else silently bakes README/asset links pointing at the wrong repository —
   they resolve, they're just wrong, which is worse than a 404.

   > **`@PreviewParameter` previews cannot carry a catalog component.** A
   > preview whose composable takes a `@PreviewParameter` argument renders its
   > PNG fine, but the renderer emits **no data products for it at all** — no
   > `compose/semantics`, layout, fonts or `figma-svg`. The completeness gate
   > then refuses to publish the whole catalog with
   > `no semantics for: <componentId>` … `incomplete render — refusing to
   > publish`. The message names the component, not the cause, so it reads as a
   > bad spec.
   >
   > Don't reach for `--allow-incomplete` (it publishes a sheet with holes) and
   > don't drop the component. Add a **zero-argument wrapper preview** in
   > `src/debug` that calls the same composable with a literal fixture, and
   > point the spec at the wrapper — the full data-product set comes back.
   >
   > Check for this *before* spending a render: any `@PreviewParameter` in the
   > previews a spec references will hit it. It bites Wear catalogs hardest —
   > 9 of Jetcaster's 12 Wear previews take a `@PreviewParameter`.

3. **Import.** The bundle is tool-neutral first, Figma second:

   ```
   catalog.json            # index: components, both variants, greenlines, optional screen graph
   tokens.dtcg.json        # W3C DTCG token set — Figma Variables / Tokens Studio / Style Dictionary / Claude Design
   figma-variables.json    # Figma variable-collection projection (light/dark as modes)
   images/<component>/<variant>__<state>[__theme][__size].png
   wireframes/<component>.svg  # baked structural vector — placed as a true vector node on Figma import
   ```

   - **Claude Design / Stitch** — import the PNGs + `catalog.json`; the DTCG file
     seeds tokens. Pair with **compose-preview-design-board** to wrap the same
     renders as a browsable HTML brief.
   - **Figma** — import `tokens.dtcg.json` via a DTCG/Tokens-Studio plugin, or
     create variables from `figma-variables.json`; place the variant PNGs as the
     sticker-sheet frames. The maintained path for this is the
     **figma-catalog-import** skill (the import-hop sibling of this one): it
     drives the `@design-parity/figma-plugin`, decides the import case
     (code-led vs design-led × new vs existing file), and reconciles in place
     instead of delete-and-rebuild. Declaring a screen graph in the catalog
     spec (`screens: [{ id, title?, related }]`) turns a code-led import into
     structured per-screen diff pages rather than one flat sheet.

4. **Deliver on a per-system branch.** Publish the generated `out/` to a
   `design-artifacts/<system>` branch (`design-artifacts/compose-m3`,
   `.../wear-m3`, `.../glimmer`, `.../glance-wear`) — the surface a designer pulls
   from, and what the public preview server (`preview.coo.ee`) fetches and serves
   at `/<system>/`.

   Each publish **appends a commit on the branch tip** rather than force-pushing
   a fresh orphan, so the delivery branch carries a per-regeneration history and
   a sticker that moved can be traced back to the source commit that moved it
   (each commit subject names the render date and the short `main` SHA):

   ```bash
   git log --oneline origin/design-artifacts/compose-m3 -- images/button-filled/
   ```

   Regeneration is automatic on the triggers that matter: `design-artifacts.yml`
   runs on a **push to `main`** that touches a catalog or the export driver
   (scoped to just the affected systems, so a one-catalog merge regenerates one
   branch), at the tail of a **published release**, and on a **weekly cron**.
   **Renderer / plugin / CLI changes are deliberately excluded from the push
   trigger** — they do change the output, but they're touched by most merges and
   each system's render is expensive. That drift is picked up by the cron and the
   release chain; dispatch manually if it needs to land sooner.

   Don't hand-roll the pipeline in a consumer repo — call the reusable workflow,
   which is the same one compose-ai-tools' own catalogs use:

   ```yaml
   publish:
     if: ${{ github.repository == 'you/your-repo' }}
     permissions:
       contents: write
     uses: yschimke/compose-ai-tools/.github/workflows/design-artifacts-reusable.yml@main
     with:
       system: your-system
       spec: catalog.spec.json
       module: ':app'
       # Keep one executable bundle per module for trusted live rendering. Do not
       # multiply it into one executable bundle per preview unless a consumer has
       # demonstrated that it cannot route through the module bundle.
       publish-live-bundle: true
       defer-figma-svg: true
       split-per-preview: false
   ```

   > **Publishing a SECOND catalog from the same repo? Add `design-map-command`
   > to both jobs.** `system`, `spec` and `module` vary per call, but
   > `design-map.json` is read from the repo root either way — so the sheet that
   > does not own the committed map is scored against the other one's. Every
   > mapping dangles and the board publishes `coverage.percent: 0`, which looks
   > exactly like a catalog nobody has annotated yet. The input (compose-ai-tools
   > **v1.54.0** and later) lets each job project its own map first:
   >
   > ```yaml
   >       design-map-command: >
   >         ./gradlew :second-module:composePreviewDiscover --stacktrace &&
   >         scripts/design-map.sh second-module
   > ```
   >
   > Set it on the parity workflow too, and make sure that projection passes
   > `--prefix <module-dir>` — it defaults to `catalog`, so a second module's
   > code handles otherwise name files that do not exist. Failure modes:
   > [design-parity-review →
   > CI](../design-parity-review/references/ci.md#two-catalogs-in-one-repo).

   > **A published catalog is static unless you opt into a live bundle.**
   > `publish-live-bundle` and `split-per-preview` both default to **`false`**.
   > Leave `publish-live-bundle` off and the branch gets rendered PNGs and nothing executable, so
   > the server has no daemon to launch: it badges the catalog *"serves baked PNG
   > snapshots only — its delivery branch publishes no live bundle"* and the
   > device, theme and knob controls in the viewer do nothing. Nothing fails and
   > no warning is printed — the publish succeeds and quietly ships a static sheet.
   > `publish-live-bundle: true` carries one executable bundle per discovered module under
   > `bundle/` and records `liveBundle` in `catalog.json`; that is the normal live-catalog shape.
   > Keep `split-per-preview: false` unless an actual consumer requires an independently
   > addressable executable bundle for every preview.
   >
   > **Liveness and trust are independent gates**, and the status page shows them
   > in adjacent columns, which invites conflating them. Trust is about whether
   > the server will execute *your* branch (`trust/producers.json`); the live
   > bundle is about whether the branch contains anything to execute. A catalog
   > can read `✓ trusted` and still serve baked PNGs — that's a missing bundle,
   > not a trust problem, and no amount of trust config fixes it.
   >
   > `defer-figma-svg: true` is the normal companion to a live bundle. Pure editable SVGs are a
   > daemon data product, so publishing them eagerly adds coordinate-derived files that churn when
   > capture packing order changes; raster-backed hybrid SVGs remain static. The workflow requires
   > a live bundle before it can defer them, because the trusted daemon is what regenerates them.
   >
   > If a per-preview split is genuinely required, use `full-shared-classpath` instead of `full`
   > unless offline self-contained downloads are themselves a requirement. `full` repeats the
   > classpath in every split; any classpath change then rewrites every copy, so delivery-branch
   > growth scales with preview count. `view-only` is the baked tier for a render the serve host
   > cannot drive. Android/Robolectric and CMP desktop catalogs are both live-capable — what matters
   > is that the host bakes the matching daemon, not the platform.
   >
   > Before enabling a split, and whenever a generated-artifact checkout becomes unexpectedly
   > large, read [Delivery-branch size and retention](references/delivery-branch-size.md). It covers
   > measuring reachable history, identifying duplicate same-source publishes, safely re-rooting a
   > bloated branch once, and reclaiming an existing clone after the remote rewrite.

   > **Publish before you register.** A serve host that fetches
   > `design-artifacts/<system>` reconciles its catalog list by *fetching each
   > branch*, so registering a system whose branch does not exist yet fails the
   > reconcile — on preview.coo.ee that is `HTTP 502 — catalog <system> not
   > published: could not fetch …` and one rejected seed entry fails the whole
   > run, even though every other catalog was accepted. Land the first
   > design-artifacts publish, confirm the branch exists, then open the
   > registration change. A registration that raced the publish is fixed by
   > re-running the config job once the branch is there — nothing to revert.

   > **`embed-deps` when a dep isn't on Central or Google Maven.** The serve box
   > rebuilds the live classpath from the Maven coordinates in the bundle, and it
   > resolves from exactly two repos — `repo1.maven.org/maven2` and
   > `dl.google.com/dl/android/maven2`. A dep from anywhere else (JitPack, a
   > company repo, `androidx.dev` snapshots) can't be fetched, the daemon fails to
   > build its classpath, and the catalog **silently** falls back to baked PNGs
   > with `livebundle-unavailable`. `embed-deps: true` carries the reachable jars
   > inside the bundle under `libs/` instead; it costs bundle size, so leave it
   > off when every dep is on the two public repos.
   >
   > Check before you publish rather than reading it off the status page
   > afterwards: list the coordinates the module actually resolves and probe both
   > repos for each `<artifact>-<version>.pom`. Watch for repos that are declared
   > but inert — a `pluginManagement` repo serves build-time compiler plugins and
   > never contributes a runtime classpath entry, and an env-gated repo (the
   > `COMPOSE_SNAPSHOT_ID` branch in the compose-samples `settings.gradle.kts`)
   > contributes nothing when CI doesn't set the variable. Both look alarming in a
   > grep and neither affects the live bundle.

## Source

Maintained at [github.com/yschimke/skills](https://github.com/yschimke/skills)
under `skills/compose-design-catalog/`. The export library
(`@design-parity/catalog-export`) lives in
[yschimke/design-parity](https://github.com/yschimke/design-parity); the
renderer/CLI in
[yschimke/compose-ai-tools](https://github.com/yschimke/compose-ai-tools).
