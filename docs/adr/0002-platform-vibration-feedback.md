# ADR 0002 — Vibration feedback is driven by the platform vibrator, not Compose haptics

- **Status:** Accepted
- **Date:** 2026-10-21
- **Scope:** `util/VibrationFeedback.kt`, `ui/MainActivity.kt`, `ui/navigation/`, `ui/overlays/`, `ui/screens/SettingsScreen.kt`, `ui/viewmodel/`, `data/`, `domain/`

## Context

Prosto fired haptic feedback through Compose's `LocalHapticFeedback`
(`HapticFeedbackType.LongPress` / `TextHandleMove`) on the gestures that need tactile
confirmation: long-pressing a tab, swapping a tab while dragging, crossing a slot in the
search-engine carousel and reordering search engines.

Users reported that *vibration was not working*. The cause is that
`View.performHapticFeedback()` is gated by the system **touch-feedback** setting: when the
device has touch feedback disabled, at a low intensity, or when the OEM maps the requested
constant to a silent event, the call is a no-op. Compose adds no fallback - it delegates
straight to the view. The app therefore had no reliable way to produce a pulse, and no way
for the user to turn the behaviour off.

## Decision

**The app drives `android.os.Vibrator` directly.**

- `util/VibrationFeedback.kt` defines a two-method contract - `tick()` (light pulse, 20 ms)
  and `confirm()` (heavier pulse, 50 ms) - plus a `LocalVibrationFeedback` composition local.
- `VibratorVibrationFeedback` resolves the vibrator through `VibratorManager` on API 31+ and
  `Context.VIBRATOR_SERVICE` below it, and emits one-shot `VibrationEffect`s. One-shot
  effects are used instead of `VibrationEffect.createPredefined()` because predefined
  effects are not supported on every device and would throw `IllegalArgumentException`.
  The app therefore needs the `VIBRATE` permission.
- The user preference (`settings.isVibrationEnabled`, persisted in the settings table like
  every other toggle) is passed to the feedback as a **predicate that is read on every pulse**
  (`{ viewModel.isVibrationEnabled }`), not as cached state. The
  instance is stable for the whole activity lifetime (created once with `remember` in
  `MainActivity.setContent`), so gesture callbacks that captured it
  at composition time always observe the current preference without the gesture having to
  be restarted - and without the toggle having to invalidate any composition scope.
- `MainActivity` provides the composition local.

**Every call site now speaks `VibrationFeedback` instead of `HapticFeedback`.** The
search-engine carousel and reorder state objects take the new type as an injected
parameter, exactly as they took `HapticFeedback` before, which keeps them unit-testable
with a plain fake.

Two supporting pieces exist for that testability and are not part of the public contract:
`NoVibrationFeedback` is the no-op fallback installed by `LocalVibrationFeedback` when no
provider is present (previews, isolated tests), and `VibratorVibrationFeedback` has an
`internal` constructor that accepts a `Vibrator?` directly so a test can inject
`ShadowVibrator` without a `Context`.

## Consequences

- The app now needs the `VIBRATE` permission (a normal permission, granted at install).
- The app's own gesture feedback no longer depends on the system touch-feedback setting, so
  the *Vibration* toggle in Settings is the single source of truth for it.
- One View-level haptic pulse is deliberately **outside** that switch: the pull-to-refresh
  pulse in `engine/NestedWebView.kt` still calls
  `performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)`. It is a WebView-internal
  mechanism and pulling it into the switch would mean threading the preference into the
  WebView setup. It is therefore still subject to the system touch-feedback setting.
- A device without a vibrator (`hasVibrator() == false`) is a no-op, not a crash.
- The Compose `HapticFeedback` API is no longer used anywhere in the app.

## Alternatives considered

- **Keep `HapticFeedback` and only add a settings toggle.** Rejected: it does not fix the
  reported problem - the feedback still disappears when the system touch-feedback setting
  is off.
- **Use `VibrationEffect.createPredefined(EFFECT_TICK / EFFECT_HEAVY_CLICK)`.** Rejected:
  the primitives are not supported on all devices, and the call throws on the ones that
  lack them. One-shot effects work everywhere.
- **Make the enabled flag Compose state.** Rejected: the flag lives on a long-lived
  instance that gesture callbacks capture; snapshot state would require re-creating the
  instance (and therefore re-creating the gesture scopes) on every toggle.
- **Cache the enabled flag on the instance and sync it from a `SideEffect`.** Rejected: a
  `SideEffect` only runs when its enclosing composition recomposes, and a composition that
  reads the preference only *inside* the effect is never invalidated by it - the flag would
  stay stale until something unrelated forced a recomposition.
