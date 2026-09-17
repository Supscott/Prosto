# Editable knobs (`previewOverride*`)

A `@Preview` takes no arguments, so everything it shows is baked at authoring
time. **Override knobs** are the opt-in escape hatch: name a value in the
composable, and it becomes editable — re-renderable from the CLI, the preview
server's viewer, or an MCP client — **without a source rebuild**.

```kotlin
// build.gradle.kts
implementation("ee.schimke.composeai:data-preview-overrides-runtime:<version>")
```

```kotlin
@Preview(showBackground = true)
@Composable
fun ShoppingListPreview() {
    val title = previewOverrideString("title", default = "Shopping list")
    val accent = previewOverrideColor("accent", default = Color(0xFF3366FF))
    val rows = previewOverrideInt("rowCount", default = 3)

    Column {
        Text(title, color = accent)
        repeat(rows) { i ->
            // An INDEXED knob: one editable value per row, not one shared by all.
            Text(previewOverrideString("rowLabel", default = "Item ${i + 1}", index = i))
        }
    }
}
```

Each lookup returns the seeded value, or the author's default when nothing is
seeded — so the preview renders identically to a plain one until someone edits
it. Nothing is mocked and no DI is involved; this is orthogonal to
[state hoisting](./state-hoisting.md), which is about making a composable
previewable at all.

## The helpers

| Helper | Type | Notes |
| --- | --- | --- |
| `previewOverrideString(key, default, index)` | `String` | Free text. |
| `previewOverrideInt` / `Float` | `Int` / `Float` | Number field. A count fed into `repeat(n)` is the usual case. |
| `previewOverrideBoolean` | `Boolean` | Checkbox. |
| `previewOverrideColor` | `Color` | Carried as `#AARRGGBB`. |
| `previewOverrideDp` | `Dp` | Carried as a float. |
| `previewOverrideFont(key, default, suggestions, googleFonts)` | `String` | Family name, autocompleting over `suggestions` and (by default) the full Google Fonts list, while staying free-text. |
| `previewOverrideChoice(key, default, options)` | `String` | **Closed value set** — see below. |

`index` is null for a scalar knob. Pass `0, 1, 2, …` inside a `repeat` to get
one editable value per item; the wire key becomes `rowLabel[2]`.

## Closed value sets

A plain string knob publishes its *current* value and nothing about what else it
could be. A size axis renders as a text field reading `s` — correct, and useless
for discovering that `xs` / `m` / `l` / `xl` exist. You either read the source or
guess the spelling and watch the render refuse to move.

`previewOverrideChoice` declares the alphabet alongside the value, and a viewer
renders it as a picker instead:

```kotlin
val size = previewOverrideChoice(
    "size",
    default = "s",
    options = listOf(
        PreviewOverrideOption("xs", "Extra small"),
        PreviewOverrideOption("s", "Small"),
        PreviewOverrideOption("m", "Medium"),
    ),
)
```

The label is what the control shows; the **wire value stays the slug** the
composable reads, so seeding and any existing links are unaffected. When the
values already read as words, pass them bare:

```kotlin
val shape = previewOverrideChoice("shape", default = "round", values = listOf("round", "square"))
```

Two things worth knowing:

- **Nothing enforces the set at render time.** A seed outside it still reaches
  the composable, and the viewer adds it to the picker rather than dropping it —
  so a stale link keeps rendering what it says instead of failing or silently
  snapping to another value.
- **Use it for an axis, not for prose.** A closed set is right for a size, a
  shape, a state, a density. A label or a title is free text and should stay
  `previewOverrideString`.

## Driving them

The declared set travels with the render as the `compose/overrides` data product
— a `previews/<id>.overrides.json` sidecar in a packed bundle — so a consumer
can enumerate *what is editable* before editing anything.

```sh
# Re-render a PUBLISHED bundle with new values. No source, no Gradle, no rebuild.
compose-preview bundle render ./bundle.png --knob title="Groceries" --knob size=xl -o out/
```

```sh
# Serve it and edit interactively: the viewer's Overrides panel lists every
# declared knob, and each edit re-renders. The same values work as URL params.
compose-preview serve --bundle app=./bundle.png
#   → /p/<id>?knob.title=Groceries&knob.size=xl
```

An MCP client seeds the same values through `renderNow.overrides.namedOverrides`
(see [mcp.md](./mcp.md)).

Because the knob is resolved at render time from a value the *caller* supplies,
one baked bundle re-skins to any of these without going near the source tree —
which is what makes an override knob different from just authoring another
`@Preview`.

## The second format: parameter knobs

Everything above declares a knob by **executing a lookup in the composable
body**. There is a second, newer format that declares it by the **function
signature** instead — an ordinary parameter with a default:

```kotlin
@Preview @Composable
fun BasketPreview(title: String = "Basket", rows: Int = 3) { … }
```

Both formats are live and publish the same `PreviewOverrideDeclaration`, so a
viewer's Overrides panel and an MCP client treat them alike. The differences
that matter to you:

| | `previewOverride*` | Parameter knob |
| --- | --- | --- |
| Preview body | one harness call per knob | nothing — no harness dependency at all |
| Value seeding | typed values written into a process-static controller | ordinary argument passing |
| Default comes from | the author's call site | read back out of the compiled body |
| Published in a packed bundle's `previews/<id>.overrides.json` | yes | daemon lanes and the offline bake, yes |

**A default it cannot read back is a knob it cannot declare.** Defaults that
are expressions — `stringResource(...)`, `Color(0xFF3366FF)` — are not
recoverable from the compiled body, so those parameters do not become knobs.
`Color` and `Dp` are not seedable kinds either. If a preview you expected to be
editable shows no knob, this is usually why: it is not a bug to report, it is
the format's boundary. `previewOverride*` remains correct for those, and for the
indexed case (`previewOverrideString(..., index = i)`), which the parameter form
has no equivalent for because a parameter list is fixed-arity.

### A closed set, as an `enum class`

The parameter form's answer to `previewOverrideChoice` is to declare the
parameter as an enum. Its constants **are** the closed set:

```kotlin
enum class Emphasis { Filled, Tonal, Outlined }

@Preview @Composable
fun EmphasisPreview(emphasis: Emphasis = Emphasis.Tonal) { … }
```

Discovery records the kind as `ENUM` with the constants as `options`, and both
renderers declare it `optionsExhaustive = true`, so a viewer draws exactly the
picker `previewOverrideChoice` produces. It is stricter than the string it
replaces — a `when` over it is exhaustive, so a constant added later is a
compile error rather than a branch that silently falls through.

### `@KnobValue`, when the wire value is not the constant's name

By default a constant answers to its own name. That is right for a knob written
from scratch and **wrong for one being migrated**: seeds already written into
`@OverrideVariant(strings = ["iconSize=extra-large"])`, into existing links, and
into the design kit the renders are compared against all speak the old
vocabulary, and renaming the value unbinds every one of them — the render then
falls back to the author default with no diagnostic anywhere. Plenty of real
values cannot be Kotlin identifiers at all (`12-sided cookie`, `0.0`, `24s`), so
"name the constant after the value" is not always available even in principle.

Declare the text on the constant instead:

```kotlin
enum class IconSize {
  @KnobValue("default") Default,
  @KnobValue("large") Large,
  @KnobValue("extra-large") ExtraLarge,
}
```

Everything downstream then speaks the declared vocabulary — the knob's
`options`, the picker, an `@OverrideVariant` seed, and the declared default.
Two constants claiming the same text make a seed ambiguous, so the enum reports
no options and **stops being a knob** rather than binding whichever was read
first; an enum with no resolvable options is likewise not a knob at all, because
an empty picker is worse than the text box it replaced.

Full migration record, including which sample shapes can and cannot move:
[`docs/design/PARAMETER_KNOB_MIGRATION.md`](https://github.com/yschimke/compose-ai-tools/blob/main/docs/design/PARAMETER_KNOB_MIGRATION.md).
