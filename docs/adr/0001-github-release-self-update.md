# ADR 0001 — Self-update from GitHub releases, installed on the next cold start

- **Status:** Accepted
- **Date:** 2026-10-21
- **Scope:** `engine/update/`, `ui/viewmodel/UpdateCoordinator.kt`, `ui/overlays/UpdateNotificationCard.kt`

## Context

Prosto is distributed as signed APKs attached to GitHub releases, not through Google Play.
Users therefore have no way to receive a new version other than manually downloading and
side-loading an APK.

Requirements:

1. A new release must be detected automatically.
2. The app must never terminate itself while the user is browsing - a browser that closes
   itself mid-session destroys the session.
3. The new version must become active after the app is closed and reopened.
4. With auto-update disabled, the user must be offered the update with the explicit
   choices *Install*, *Install Later* and *Skip*.

## Decision

**Detection.** On every cold start (once per app process) the app queries
`GET https://api.github.com/repos/Supscott/Prosto/releases/latest`. The release is newer
when the semver of its `tag_name` (with the `v` prefix stripped) is greater than the
installed `versionName`. Releases without an `.apk` asset are ignored.

**Download.** The release APK is streamed with OkHttp into app-private storage
(`filesDir/updates/prosto-<version>.apk`, written to a `.part` file first and renamed on
completion). The download runs in the background and never blocks the UI.

**Installation.** The APK is handed to the *system package installer* through a
`FileProvider` content URI. It is never installed silently, so the OS never kills the
running process behind the user's back.

**Timing.** With auto-update enabled the download is triggered automatically, but the
install is deferred to the **next cold start**: the app is not browsing anything yet, so
the replacement cannot interrupt a session. The user is told the update is ready through
a system notification whose tap action opens the installer directly. With auto-update
disabled the app shows an in-app notification card on the home and the settings screen
with *Install*, *Install Later* and *Skip*.

**Skip.** "Skip" persists the version in the settings table, so that version is never
offered again; "Install Later" only hides the card for the current session and the update
is offered again on the next start.

## Consequences

- Release convention: the git tag must be `v<versionName>` and the `versionName` in
  `app/build.gradle.kts` must be bumped for every release.
- Release APKs must be signed with the **same key** as the installed build, otherwise the
  system installer rejects them with a signature mismatch.
- The app needs `REQUEST_INSTALL_PACKAGES` and the user has to grant "install unknown
  apps" once. The app sends the user to that system screen when the permission is
  missing, and never installs without it.
- `REQUEST_INSTALL_PACKAGES` is only acceptable for this distribution channel. If Prosto
  is ever published on Google Play, this mechanism must be replaced by the Play in-app
  update API.
- The check runs once per cold start, which stays well inside the unauthenticated GitHub
  API rate limit of 60 requests per hour and per IP.

## Alternatives considered

- **Silent install via `PackageInstaller` (`USER_ACTION_NOT_REQUIRED`).** Rejected: the OS
  kills the app process during the install, which would close a live browsing session -
  explicitly forbidden by requirement 2.
- **Parse the `versionCode` out of the release asset name.** Rejected: it imposes a strict
  asset-naming rule on the release job, while a semver tag is the convention humans
  already follow.
- **Parse the `versionCode` from the downloaded APK manifest.** Rejected: it requires the
  full download before the app can decide whether the release is newer.
