---
name: compose-preview-design-board
description: Assemble rendered Compose @Preview PNGs into a single self-contained HTML design board for Claude Design and other design-tool imports. Use when turning a set of rendered previews into one coherent design brief — grouped, captioned, and ordered — rather than handing over loose screenshots. Pairs with the compose-preview skill.
---

# Compose Preview — Design Board

Assemble the PNGs produced by `compose-preview` into a single, self-contained
HTML **design board** — categories, groups, captions, and layout — so a set of
renders travels as one coherent brief instead of loose screenshots. The board
is built for import into Claude Design (and similar design tools).

This skill assumes the **compose-preview** skill is installed — it owns the
renderer, CLI, and Gradle plugin that produce the PNGs this skill arranges.
Check first with `compose-preview --version`; if it's missing, ask the user to
run the bootstrap installer (which covers the compose-preview skills):

```sh
curl -fsSL https://raw.githubusercontent.com/yschimke/skills/main/scripts/install.sh \
  | bash
```

## Source

This skill is maintained at
[github.com/yschimke/skills](https://github.com/yschimke/skills) under
`skills/compose-preview-design-board/`. To check for updates, compare the
installed copy against `main` (e.g. `git ls-remote
https://github.com/yschimke/skills HEAD`). The renderer and CLI that produce
the input PNGs ship from
[github.com/yschimke/compose-ai-tools](https://github.com/yschimke/compose-ai-tools).

## When to use this skill

- You have a set of rendered `@Preview` PNGs and want to hand them to a designer
  or to Claude Design as a structured brief — grouped by feature/screen, with
  captions and intent notes — not a flat folder of images.
- You want one portable artifact (a single HTML file, images inlined) that can
  be opened in a browser, served from a branch / GitHub Pages, or dropped into a
  design tool.

For *rendering* the previews, see the **compose-preview** skill. For *reviewing*
a UI PR by diffing base vs head renders, see **compose-preview-review**. This
skill is the export/presentation step that sits on top of those.

## Workflow

1. **Render previews.** Use the compose-preview CLI to produce PNGs and the
   manifest of what was rendered:

   ```sh
   compose-preview show --module <m> --json > /tmp/<m>-show.json
   ```

2. **Write a board spec.** A JSON document describing how to arrange the renders
   (categories → groups → items, each item pointing at a PNG path). See
   [Board spec schema](#board-spec-schema) below.

3. **Generate the HTML.** Build one self-contained file from the spec — every
   referenced PNG inlined as base64, missing PNGs shown as visible placeholders
   rather than failing the build, and **deterministic output** (no timestamps or
   random ids, so re-runs diff cleanly). Run your project's builder if it ships
   one; otherwise generate it from the [contract below](#the-builder-script):

   ```sh
   # spec may also be piped on stdin instead of --spec
   python3 scripts/build-design-board.py --spec board-spec.json --out design-board.html
   ```

4. **Import to Claude Design.** Pick the route that fits:
   - **Web capture (best fidelity)** — open `design-board.html` in a browser, or
     serve it (GitHub Pages / a branch), and use Claude Design's web capture on
     the page.
   - **File upload** — drag the `.html` (or the individual PNGs) into Claude
     Design's drop zone.
   - **Connect the GitHub repo** for design-system context alongside the board.

   Grouping, captions, and flow order travel with the images, so the board reads
   as one brief rather than a pile of screenshots.

   **Targeting Figma instead?** Don't hand-place these renders into Figma from
   here — use the [**figma-catalog-import**](../figma-catalog-import/SKILL.md)
   skill. It owns the Figma side (the `@design-parity/figma-plugin`, reconcile
   by `componentId`, mode-awareness) and takes either a whole-system catalog or
   this board's curated render set. Claude Design is a light HTML/PNG drop-in and
   stays here; Figma is the one heavy destination and lives there.

## Board spec schema

JSON, top-down:

| Level | Fields |
|---|---|
| **Top level** | `title`, `tagline`, `footer`, optional `palette` (colour swatches), `categories[]` |
| **Category** | `badge`, `title`, `intro`, `groups[]` |
| **Group** | `title`, `note` (the design intent / rationale), `layout`, `items[]` |
| **Item** | `src` (PNG path from compose-preview), `caption`, optional `sub` (state / size / theme) |

`layout` controls how a group's items are arranged:

- `"row"` — left-to-right wrapping; good for phone screens.
- `"grid"` — auto-fill grid; good for theme swatches and tiles.
- `"wide"` — full-width frames; good for size matrices.

## The builder script

A reference builder ships beside this `SKILL.md` at
[`scripts/build-design-board.py`](scripts/build-design-board.py) — a small,
dependency-free Python 3 script (no `pip install`). It reads the spec, inlines
every image, and writes one self-contained HTML file. Swap in your project's own
builder if you have one, as long as it holds the same contract:

- reads the spec from `--spec <file>` **or** stdin, and writes to `--out <file>`
  **or** stdout;
- inlines every item `src` PNG as a `data:image/png;base64,…` URI, so the output
  is one file with **no external dependencies**;
- renders a missing or unreadable PNG as a **visible placeholder** — never fails
  the build;
- emits **deterministic** HTML — no timestamps, random ids, or absolute paths —
  so re-runs diff cleanly;
- resolves item `src` paths against `--base` (default: the current directory),
  so a spec stays portable — never hardcode an absolute checkout path like
  `/home/user/<project>`, or the board breaks in every other checkout.

## Related

- [**compose-preview** skill](../compose-preview/SKILL.md) — render the
  `@Preview` PNGs this board is built from: CLI, Gradle plugin, capture modes.
- [**compose-preview-review** skill](../compose-preview-review/SKILL.md) —
  review a UI PR by rendering base and head and diffing them.
- [**compose-design-catalog** skill](../compose-design-catalog/SKILL.md) — the
  whole-system sibling of this skill: instead of a curated subset, catalog an
  entire component system into a durable, tool-neutral bundle.
- [**figma-catalog-import** skill](../figma-catalog-import/SKILL.md) — take a
  board or catalog **into Figma** (plugin + reconcile). The Figma destination
  adapter for both arrangers.
