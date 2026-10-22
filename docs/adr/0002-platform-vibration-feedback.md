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

**The app drives `android.os.Vibrator` directly with hardware feedback attributes.**

- `util/VibrationFeedback.kt` defines a two-method contract - `tick()` (light pulse for discrete
  steps like drag swap, carousel slot changes, and tab swipe dismissal) and `confirm()` (heavier
  pulse for long-press, pull-to-refresh activation threshold, and settings toggle confirmation) - plus
  a `LocalVibrationFeedback` composition local.
- `VibratorVibrationFeedback` resolves the vibrator through `VibratorManager` on API 31+ and
  `Context.VIBRATOR_SERVICE` below it.
- **Hardware Feedback & OS Setting Independence:** On API 33+, calls pass `VibrationAttributes`
  configured with `USAGE_HARDWARE_FEEDBACK`. On API 26-32 (including Android 12/12L), `AudioAttributes` with
  `USAGE_ASSISTANCE_SONIFICATION` is passed, avoiding runtime `NoSuchMethodError` while preventing the system
  "touch feedback" / "vibrate on tap" toggle from silencing vibrations initiated in the app. This makes Prosto's
  in-app Vibration setting the authoritative switch.
- **Predefined Waveforms with Resilient Fallback:** On API 29+, the engine attempts
  `VibrationEffect.createPredefined(EFFECT_TICK)` for `tick()` and
  `VibrationEffect.createPredefined(EFFECT_CLICK)` for `confirm()`. If the device ROM or hardware
  driver does not support predefined primitives or throws an exception, it safely falls back to
  `VibrationEffect.createOneShot()` with robust dwell durations (`TICK_DURATION_MS = 35L`,
  `CONFIRM_DURATION_MS = 60L`) ensuring physical actuator activation across all hardware classes.
- The user preference (`settings.isVibrationEnabled`, persisted in the settings table like
  every other toggle) is passed to the feedback as a **predicate that is read on every pulse**
  (`{ viewModel.isVibrationEnabled }`), not as cached state. The
  instance is stable for the whole activity lifetime (created once with `remember` in
  `MainActivity.setContent`), so gesture callbacks that captured it
  at composition time always observe the current preference without the gesture having to
  be restarted - and without the toggle having to invalidate any composition scope.
- `MainActivity` provides the composition local.

**Every call site now speaks `VibrationFeedback` instead of `HapticFeedback`.** The
actions covered by tactile feedback include:
1. Long-press on a tab in the bottom bar to initiate dragging (`confirm()`).
2. Swapping adjacent tab slots during drag-and-drop (`tick()`).
3. Snapping/selecting search engines in the search carousel (`tick()`).
4. Reordering search engines in the search engine management list (`tick()`).
5. Swiping to dismiss a tab in the tab overview grid (`tick()`).
6. Crossing the page reload trigger distance in `JestkSwipeRefreshLayout` (`confirm()`).
7. Toggling the Vibration switch from OFF to ON in Settings (`confirm()`).

Two supporting pieces exist for testability and are not part of the public contract:
`NoVibrationFeedback` is the no-op fallback installed by `LocalVibrationFeedback` when no
provider is present (previews, isolated tests), and `VibratorVibrationFeedback` has an
`internal` constructor that accepts a `Vibrator?` directly so a test can inject
`ShadowVibrator` without a `Context`.

## Consequences

- The app needs the `VIBRATE` permission (a normal permission, granted at install).
- The app's gesture feedback is decoupled from the Android system touch-feedback toggle via
  `VibrationAttributes.USAGE_HARDWARE_FEEDBACK`, making the *Vibration* toggle in Settings the single source of truth.
- Pull-to-refresh routes through `JestkSwipeRefreshLayout.onRefreshTriggerReached` to `VibrationFeedback.confirm()`
  at the exact point where releasing the spinner activates page reload, avoiding false-positive pulses at gesture start.
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
