# Spec: Clean YouTube Player becomes opt-in (default OFF)

## 1. Problem
Clean YouTube Player intercepts every YouTube navigation automatically when enabled (which is the current default ON). Users who want to like, read comments, or keep watch history have no in-context way to stay on the native page — closing the overlay does not suppress future auto-opens. The setting is undiscoverable inside Settings.

## 2. Goals
- Auto-open no longer happens for new installs and existing installs are reset to OFF (forced, Q6).
- Users can still opt in via Settings toggle; when ON, previous auto behavior is restored (Variant A, Q2).
- Users can open the clean player manually without flipping the persistent setting (Q7).
- Scope is YouTube only (including Shorts, youtu.be, embed domains) (Q5). Other surfaces are unchanged.

## 3. Non-goals
- No per-site / per-video memory (Q4: single global flag).
- No change to Fast-Start ad logic, HTML5 fullscreen, mini-player, PiP, or Open-in-App prompt (Q9: left as-is).
- No new modes (Ask-first prompt) or per-host allowlist.

## 4. Design
### 4.1 Setting semantics (Variant A)
`isCleanYouTubePlayerEnabled` becomes an **auto-open** flag, default `false`.
- `false`: `shouldInterceptForCleanPlayer()` returns false for omnibox, link click, `onPageStarted`, SPA `pushState`/click interceptions. Video plays on native YouTube page.
- `true`: behavior reverts to prior auto interception (5 paths, all already gated by this flag).

### 4.2 Forced migration to OFF
Existing persisted `true` is reset to `false` exactly once. After that, user choice is preserved. A marker key `CLEAN_PLAYER_FORCED_OFF_V1` stores `"1"` once migration ran. If marker is absent, migration writes setting to `false` and marker to `1`.

Where: `SettingsRepositoryImpl.getCurrentSettings()` is the read path used at session restore before observing flows.

### 4.3 Manual entry (FAB)
A floating pill `CleanPlayerFab` is shown when:
- `YouTubeUrlHelper.extractVideoId(currentUrl) != null`
- `cleanPlayerVideoId == null` (player not open)
- not fullscreen, not home screen, no blocking overlay/screen (settings/downloads/home/menu/search)

Click is **one-shot**: dispatch `OpenCleanPlayer(videoId, currentUrl, startSeconds)` directly, bypassing `shouldInterceptForCleanPlayer()`. Persisted setting is unchanged. Closing the overlay returns to the native page. If the user wants persistent auto, they flip the Settings toggle.

Placement: `Alignment.BottomEnd`, aligned above the bottom dock (same bottom offset logic as the undo snackbar).

### 4.4 UI copy
- Settings title keeps `clean_player_setting_title`.
- Summary updated to describe auto-open: `Automatically open YouTube videos in a clean player` (EN) / `Автоматически открывать видео YouTube в чистом плеере` (RU).
- New strings: `clean_player_fab_label`, `clean_player_fab_content_desc` in both locales.

## 5. Affected code
- `domain/model/BrowserSettings.kt`: default `true -> false`.
- `data/mapper/Mappers.kt`: fallback `true -> false`.
- `data/repository/SettingsRepositoryImpl.kt`: fallback `true -> false` + migration in `getCurrentSettings()`.
- `data/local/entity/SettingKeys.kt`: add `CLEAN_PLAYER_FORCED_OFF_V1`.
- `ui/JestkApp.kt`: render `CleanPlayerFab`; click constructs start seconds via `YouTubeUrlHelper`.
- `ui/overlays/CleanPlayerFab.kt` (new): small focused composable (<400 LOC invariant).
- `res/values/strings.xml` + `values-ru/strings.xml`: updated summary + new FAB strings.
- `app/src/test/.../SettingsRepositoryTest.kt`: default/migration coverage.

## 6. Invariants
- Zero hardcoded user strings in Compose; all via `R.string`.
- No Blur / no GraphicsLayer around WebView.
- Domain purity preserved (migration lives in data layer only).

## 7. Acceptance criteria
1. Fresh install: `isCleanYouTubePlayerEnabled == false`; no overlay on watch / shorts / youtu.be / omnibox / SPA navigation.
2. Existing install with stored `true`: first `getCurrentSettings()` returns `false`; next user toggle to `true` persists and restores auto.
3. FAB visible only on YouTube video pages, hidden when overlay is open/fullscreen/home.
4. Tapping FAB opens clean player one-shot; closing returns to page; setting unchanged.
5. Flipping Settings `ON` immediately restores auto interception.
6. `verify.sh` and `testDebugUnitTest` pass; invariants pass.

---

# Tickets

## T1 — Flip default to OFF and force-reset existing installs
Estimate: S
- Change three defaults to `false`.
- Add marker key and one-time migration in `SettingsRepositoryImpl.getCurrentSettings()`.
- Tests: `cleanPlayerDefaultsToDisabled`, `forcedMigration_resetsTrueOnce`, `forcedMigration_doesNotRevertUserOptIn`.
- Accept: criteria 1, 2, 6.

## T2 — Manual FAB to open clean player without changing setting
Estimate: M
- Add `CleanPlayerFab` composable + wiring in `JestkApp.kt`.
- Dispatch `OpenCleanPlayer` directly on click (one-shot). Hide per visibility rules.
- Preview/test FAB visibility matrix where feasible.
- Accept: criteria 3, 4, 5.

## T3 — Localization and copy update
Estimate: S
- Update `clean_player_setting_summary` EN+RU to auto-open copy.
- Add `clean_player_fab_label` / `clean_player_fab_content_desc` EN+RU.
- Accept: invariants localization check passes.
