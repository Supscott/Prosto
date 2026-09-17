# Capture modes

Beyond a plain `@Preview`, the renderer supports multi-variant fan-out,
continuous animation capture, deterministic timeline snapshots, MCP scripted
recordings, and scrolling captures.

## Multi-preview annotations

Functions can declare multiple `@Preview` variants via meta-annotations (e.g.
`@PreviewFontScale`, `@WearPreviewDevices`, `@WearPreviewFontScales`). Each
variant appears as its own entry in `previews.json` with a unique id, so all
CLI commands address them individually — no variant index needed.

## `@AnimatedPreview`: continuous GIF capture (Android only)

Use `@AnimatedPreview` from `ee.schimke.composeai:preview-annotations` when
the goal is to capture an actual animation artifact. This is the first choice
for spinners, progress indicators, and other moving previews that should be
verified as motion rather than as several unrelated PNG snapshots.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.CircularProgressIndicator
import ee.schimke.composeai.preview.AnimatedPreview

@Preview(
    name = "Animated Circular Progress",
    device = "id:wearos_large_round",
    showSystemUi = true,
    showBackground = true,
)
@AnimatedPreview(durationMs = 1200, frameIntervalMs = 100, showCurves = false)
@Composable
fun AnimatedCircularProgressPreview() {
    CircularProgressIndicator()
}
```

The rendered artifact is a GIF under `build/compose-previews/renders/`. For
`durationMs = 1200` and `frameIntervalMs = 100`, expect 13 frames: frame 0 plus
one frame every 100ms through 1200ms.

> **Always pin `heightDp` on an `@AnimatedPreview`.** With an unbounded height
> the capture silently falls back to a **single still** and writes *PNG bytes to
> a `.gif` filename* (seen on renderer 0.17.17). Nothing fails and the file
> looks plausible, but every consumer that trusts the extension — browser,
> Figma import, the preview server — gets a broken asset. Pinning both
> `widthDp` and `heightDp` produces a real GIF from the identical composable.
>
> Verify rather than assume, since the failure is silent — check the magic
> bytes, not the extension:
>
> ```sh
> head -c3 build/compose-previews/renders/<preview>.gif   # expect: GIF
> ```

**Where to put motion fixtures.** Animation previews are usually catalog
fixtures rather than app code. Put them in the **`src/debug` source set**: the
plugin renders the `debug` variant, so they are discovered like any other
`@Preview`, but they never ship in a release build. Drive a *real* component
through a real state change (a `LaunchedEffect` loop flipping the state the
component already takes) rather than re-implementing the animation — otherwise
the GIF documents the fixture instead of the product.

Use `durationMs = 0` to auto-detect finite animations. Indeterminate or
infinite animations usually need a positive `durationMs` so the renderer knows
how long to record. Set `showCurves = false` for ordinary visual regression or
agent inspection; set it to `true` when you specifically want curve diagnostics
in the output.

Add the annotations artifact to the previewed module:

```kotlin
// libs.versions.toml
[versions]
composePreviewAnnotations = "0.8.12"

[libraries]
compose-preview-annotations = {
    module = "ee.schimke.composeai:preview-annotations",
    version.ref = "composePreviewAnnotations"
}
```

```kotlin
// build.gradle.kts
implementation(libs.compose.preview.annotations)
```

Direct dependency form:

```kotlin
implementation("ee.schimke.composeai:preview-annotations:<version>")
```

If you are using a locally installed snapshot CLI/plugin, the matching
`preview-annotations:<snapshot>` artifact may not be published. Use the latest
published `preview-annotations` artifact unless you have also published the
snapshot annotations artifact locally. The renderer discovers annotations by
FQN, so a published annotations artifact can still work with a newer snapshot
renderer.

## `@SettledPreview`: wait for a late arrival, then capture the still

Use `@SettledPreview` from `ee.schimke.composeai:preview-annotations` when a
component's content is driven in by **time** rather than by a gesture, so a
plain `@Preview` captures its *first* frame and publishes an empty container.

The two shapes that hit this:

- a reveal — `LaunchedEffect { delay(…); animateTo(…) }`, as in Wear's
  `ConfirmationDialogContent`, whose children start at `alpha = 0`;
- a **deferred value** — a field whose content is written after the first
  composition, as in Material 3's `DateInputTextField`, which publishes its
  label resting on top of the date it should have floated above.

```kotlin
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.composeai.preview.SettledPreview

@SettledPreview                  // auto: advance until quiescent, bounded by maxMs (default 1000)
@Preview(name = "Confirmation", showBackground = true)
@Composable
fun ConfirmationPreview() { /* … */ }

@SettledPreview(afterMs = 600)   // exact: advance exactly 600ms, then capture
@Preview(name = "Snackbar", showBackground = true)
@Composable
fun SnackbarPreview() { /* … */ }
```

Prefer `afterMs` when you know the timing — it is one advance rather than a
frame-by-frame walk. Auto mode is the right default when the animation is
*internal to a stock component* and you have no way to know how long it runs.

### Hoist it onto your multi-preview annotation

`@SettledPreview` targets `ANNOTATION_CLASS` as well as `FUNCTION`, so a
catalog whose stickers all wrap stock design-system composables can settle
every one of them at once instead of hunting for the affected components:

```kotlin
@SettledPreview
@Preview(name = "Light", group = "modes")
@Preview(name = "Dark", group = "modes", uiMode = UI_MODE_NIGHT_YES)
annotation class StickerPreview
```

That is usually the right shape, because the animation is internal: an author
writing a sticker that merely calls `DatePicker()` has no way to know a label
tween is running inside it.

### It does not combine with a motion product

Putting `@SettledPreview` on the **same function** as `@AnimatedPreview`,
`@InteractionPreview`, or `@FocusedPreview(gif = true)` is **reported as a
discovery warning and the settle is dropped** — you get the motion artifact and
an *unsettled* still.

This is not an arbitrary restriction. Every capture of one preview renders from
one composition against one paused clock, and the two want opposite things from
it: the GIF needs the timeline from its start, the settled still needs a
coordinate near the end, and virtual time does not rewind. Whichever ran first
would spoil the other.

**Split them across two preview functions** — each then owns its own
composition, and both get what they asked for.

### What it costs, and where it applies

- Only **still** captures are settled. A `@ScrollingPreview` LONG/GIF product
  runs its own post-scroll settle, and an explicit `advanceTimeMillis` is a
  snapshot of a coordinate you chose — neither is touched.
- Auto mode walks the window in frame-sized steps, so it is proportional to
  `maxMs`. A settled capture is costed accordingly and lands in the **heavy**
  bucket at the default window, which keeps it out of the render-on-every-save
  fast tier. Keep the default unless a reveal genuinely runs longer.
- An animation that never ends (an `InfiniteTransition`, an indeterminate
  progress indicator) can't quiesce, so it captures at the `maxMs` bound. The
  annotation belongs on a reveal, not on a spinner.
- `maxMs` is clamped to 5000ms.

Batch renders honour the settle on **both** backends (Android/Robolectric and
CMP Desktop). `compose-preview serve` honours it on the Android backend; the
desktop daemon does not yet, so a settled CMP preview viewed live still shows
its first frame while its published PNG is settled
([#4238](https://github.com/yschimke/compose-ai-tools/issues/4238)).

## `@CaptureGutter`: keep a shadow that falls outside the component

A wrapped capture is cropped to the composable's measured size, so anything
drawn *outside* its own bounds is cut at the edge of the image — an elevation
shadow, a focus ring, a badge that overhangs its anchor.

The obvious workaround is to pad the preview body:

```kotlin
@Preview
@Composable
fun ElevatedButtonSticker() = Box(Modifier.padding(4.dp)) { ElevatedButton(…) { … } }   // don't
```

It keeps the shadow, and it costs more than it looks. The padding is *inside*
the bounds: the component now measures in a smaller box, the canvas grows by
the padding, and every consumer that fits a render to a column scales the
component down to make room for margin it cannot see. On a sticker sheet
laying five emphases of one button side by side, the one that padded for its
shadow draws ~7% smaller than its four siblings, for a reason that has nothing
to do with the design
([m3-catalog#179](https://github.com/yschimke/m3-catalog/issues/179)).

`@CaptureGutter` moves the gutter out of the component tree and into the
capture:

```kotlin
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.composeai.preview.CaptureGutter

@CaptureGutter(all = 4, bottom = 5)   // dp; bottom is deeper — M3 shadows are offset downward
@Preview
@Composable
fun ElevatedButtonSticker() = ElevatedButton(onClick = {}) { Text("Elevated") }
```

The renderer enlarges the scene, measures the composable against the
constraints it would have had without a gutter, and places it inset. So the
component's measured size is byte-identical to what it was before the
annotation, the shadow has room, and the gutter travels in `previews.json` as
`params.captureGutter` — a declared fact a consumer can subtract, rather than
anonymous transparent pixels it has to guess at.

`all` sets every edge; `start` / `top` / `end` / `bottom` override it per edge.
Edges are leading/trailing, so an overhang stays on the same side of the
component under RTL. Each is capped at 64 dp — past that you want a framed
canvas, which is what `@Preview(widthDp = …)` is for.

**Fixed axes grow too.** `@Preview(widthDp = 360)` plus a 4 dp gutter renders
368 dp wide with a 360 dp component in it. The rule is the same on both kinds
of axis: the component measures what it declared, and the gutter is added
around it. A catalog that hand-rolled this by declaring a 368 dp frame can
collapse back to 360 and say what the extra 8 dp is for.

### Hoist it onto your multi-preview annotation

`@CaptureGutter` targets `ANNOTATION_CLASS` as well as `FUNCTION`, so a catalog
whose elevated stickers share one shadow level declares the gutter once:

```kotlin
@CaptureGutter(all = 4, bottom = 5)
@Preview(name = "Light", group = "modes")
@Preview(name = "Dark", group = "modes", uiMode = UI_MODE_NIGHT_YES)
annotation class ElevatedStickerPreview
```

### Where it applies

Every render lane — Android (Robolectric) and CMP Desktop, batch and the
**live** daemon lane (`compose-preview serve`, the VS Code panel) alike — grows
the canvas by the same dp, so a gutter never moves the published bounds on one
lane and not the other, and a preview does not change size when a viewer toggles
PNG↔Live. It reaches:

- a preview's **still** capture, including a `@FocusedPreview` still;
- its **motion products** — an `@AnimatedPreview` GIF, an `@InteractionPreview`
  recording. Every frame of a recording shares one canvas, exactly as a still
  does, so the gutter is as well-defined there
  ([#4452](https://github.com/yschimke/compose-ai-tools/issues/4452),
  [#4443](https://github.com/yschimke/compose-ai-tools/issues/4443)).

### Not with `@ScrollingPreview`

`@CaptureGutter` **cannot be combined with `@ScrollingPreview`** on the same
function — a non-zero gutter alongside it is **rejected at discovery**: the
function is skipped with a warning naming it and produces nothing. A gutter is a
component's overdraw margin ("the component draws this far past its bounds"); a
scroll capture has no such bounds — a `LONG` stitch is the scrolled extent, a
`GIF` the declared viewport, and even a settled `END`/`TOP` frame is one viewport
of a screen. There is no edge for a gutter to sit on, so the two are a
contradiction, not a combination — remove one annotation. (An all-zero
`@CaptureGutter()` is equivalent to no annotation, so it does not trip the
rejection.) See [#4467](https://github.com/yschimke/compose-ai-tools/issues/4467).

## Manual clock snapshots (Android only)

The Android renderer pauses the Compose `mainClock` and advances by a fixed
step before capture, so infinite animations
(`CircularProgressIndicator`, `rememberInfiniteTransition`, `withFrameNanos`
loops) terminate deterministically instead of hanging the idling resource.
You don't need to call `awaitIdle` or `mainClock.advanceTimeBy` yourself.

To capture one composable at multiple timeline points, stack
`@RoboComposePreviewOptions` from Roborazzi — each `ManualClockOptions`
entry becomes its own capture with a `_TIME_<ms>ms` id suffix:

```kotlin
import com.github.takahirom.roborazzi.annotations.ManualClockOptions
import com.github.takahirom.roborazzi.annotations.RoboComposePreviewOptions

@Preview(name = "Spinner", showBackground = true)
@RoboComposePreviewOptions(
    manualClockOptions = [
        ManualClockOptions(advanceTimeMillis = 0L),
        ManualClockOptions(advanceTimeMillis = 500L),
        ManualClockOptions(advanceTimeMillis = 1500L),
    ],
)
@Composable
fun SpinnerPreview() { /* … */ }
```

Requires `implementation(libs.roborazzi.annotations)` (or
`com.github.takahirom.roborazzi:roborazzi-annotations`). Each capture
appears in the CLI's `captures[]` with `advanceTimeMillis` set.

Caveats: a11y mode disables the paused clock (ATF needs live semantics), so
don't combine it with timeline fan-outs. CMP Desktop has no per-preview
clock control — pick a static frame if you need determinism.

## MCP `record_preview`: scripted/live recording

Use MCP `record_preview` when an agent needs a scripted recording with a
preview URI, daemon state, input events, or a non-GIF output such as APNG. For
an animation that does not require real interaction, a no-op pointer script is
enough to define the recording duration:

```json
{
  "uri": "compose-preview://<workspace>/_wear/ee.example.WearPreviewsKt.AnimatedCircularProgressPreview_Animated Circular Progress",
  "fps": 10,
  "scale": 1.0,
  "format": "apng",
  "events": [
    { "tMs": 0, "kind": "input.pointerDown", "pixelX": 227, "pixelY": 227 },
    { "tMs": 1200, "kind": "input.pointerUp", "pixelX": 227, "pixelY": 227 }
  ]
}
```

Scripts can also include audit/control markers. Today only `recording.probe`
is dispatched; `state.save`, `state.restore`, `lifecycle.event`, and
`preview.reload` are advertised on the daemon's `dataExtensions` as
`supported = false` roadmap entries, and `record_preview` rejects them up
front (compose-ai-tools#714).

```json
{
  "uri": "compose-preview://<workspace>/<module>/<preview>",
  "events": [
    { "tMs": 0, "kind": "input.click", "pixelX": 120, "pixelY": 40 },
    { "tMs": 200, "kind": "recording.probe", "label": "after-click" }
  ]
}
```

Events with the same `tMs` form a single script step. Control events in that
step are applied before the frame for that timestamp is captured, so colocate
a verification `recording.probe` with the input that should change state.

Always inspect `scriptEvents` in the metadata. Input and `recording.probe`
events may be `applied` or `unsupported` (the daemon's defense-in-depth path
for events MCP didn't reject — older MCP servers or direct daemon clients).
Non-input script event ids are namespaced and must be advertised under
`capabilities.dataExtensions[].recordingScriptEvents[]` with
`supported = true`.

`observe` controls how much rides back. The default `observe="frames"` returns
the structured per-frame observation only — `recordingId`, `mimeType`,
`sizeBytes`, `frameCount`, `durationMs`, `frameWidthPx`, `frameHeightPx`,
`frames[]` (per-frame sha256 + changed-pixel counts), `changedFrameCount`, and
`scriptEvents[]` — with **no inline media**, since a recording's base64 bytes
scale with `fps × duration` and can dwarf a single PNG (compose-ai-tools#1860).
The encoded artifact is still on disk at `videoPath`; pass `observe="media"`
only when you need the encoded bytes inline (APNG as an image block, mp4/webm as
an embedded resource). Raw frames are also written under:

```text
<module>/build/compose-previews/daemon-recordings/frames/<recordingId>/frame-00000.png
```

## Verification tips

When ImageMagick is available, agents can verify animation output without
depending only on GIF/APNG playback:

```sh
identify build/compose-previews/renders/<preview>.gif
shasum -a 256 build/compose-previews/daemon-recordings/frames/rec-1/frame-*.png
compare -metric AE frame-00000.png frame-00006.png /tmp/frame-diff.png
```

Useful checks: frame count matches the requested duration/fps, hashes differ
across representative frames, and `compare -metric AE` reports changed pixels.

## Scrolling captures

For previews that exercise scrollable content (`LazyColumn`,
`TransformingLazyColumn`, `LazyRow`, …), add `@ScrollingPreview` from
`ee.schimke.composeai:preview-annotations`:

```kotlin
import ee.schimke.composeai.preview.ScrollMode
import ee.schimke.composeai.preview.ScrollingPreview

@Preview(name = "End", showBackground = true)
@ScrollingPreview(modes = [ScrollMode.END])
@Composable
fun MyListEndPreview() { MyList() }

// One function → two captures. Produces `..._SCROLL_top.png` (initial
// frame) and `..._SCROLL_end.png` (scrolled to content end).
@Preview(name = "Scroll", showBackground = true)
@ScrollingPreview(modes = [ScrollMode.TOP, ScrollMode.END])
@Composable
fun MyListTopAndEndPreview() { MyList() }

@WearPreviewLargeRound
@ScrollingPreview(modes = [ScrollMode.LONG])
@Composable
fun MyListLongPreview() { MyList() }
```

Modes:

- `TOP` — initial unscrolled frame. Useful alongside END/LONG in a single
  function so a sibling preview isn't needed.
- `END` — scrolls to content end, captures one frame.
- `LONG` — stitches slices into one tall PNG covering the full scrollable
  extent. On round Wear faces the output is clipped to a capsule shape
  (half-circle top, rectangular middle, half-circle bottom).

Knobs: `maxScrollPx` caps scroll distance on END/LONG (`0` = unbounded);
`reduceMotion = true` (default) disables Wear `TransformingLazyColumn`
transforms that would otherwise vary slice-to-slice. Only vertical scrolling
is supported. `@ScrollingPreview` is Android-only.

Don't combine it with `@CaptureGutter` — a scroll capture has no component edge
for a gutter to sit on, so the pair is rejected at discovery (see
[`@CaptureGutter`](#capturegutter-keep-a-shadow-that-falls-outside-the-component)
above).

Filenames: single-mode → plain `renders/<id>.png`; multi-mode →
`renders/<id>_SCROLL_<mode>.png`, emitted in enum order (TOP, END, LONG).
Each capture is a separate entry in the CLI's `captures[]` with `scroll`
set.
