# ADR 0001 — Notify & Redirect GitHub Release Update Flow

- **Status:** Accepted (Updated: Replaces self-installer with Notify & Redirect)
- **Date:** 2026-10-21 (Updated 2026-10-29)
- **Scope:** `engine/update/`, `ui/viewmodel/UpdateCoordinator.kt`, `ui/overlays/UpdateNotificationCard.kt`

## Context

Prosto is distributed as signed APKs attached to GitHub releases.
Initially, the application utilized an in-app package installer flow that downloaded APKs into app-private storage and invoked the system installer via `FileProvider` with `REQUEST_INSTALL_PACKAGES`.

However, Android platform security policies and Google Play Protect classify applications that download and trigger installations of arbitrary executable `.apk` packages outside authorized store channels as Potentially Harmful Applications (PHA / Trojan-Downloader), flagging builds as malicious. Furthermore, `REQUEST_INSTALL_PACKAGES` is heavily restricted.

## Decision: Notify & Redirect

1. **Permission Removal.** The `REQUEST_INSTALL_PACKAGES` permission is completely removed from `AndroidManifest.xml`.
2. **Detection.** On cold start and on app resume (throttled to 1 hour), the app queries `GET https://api.github.com/repos/Supscott/Prosto/releases/latest`. The release is newer when the semver of its `tag_name` is greater than the installed `versionName`.
3. **No In-App APK Streaming.** The app does not download, write, or store `.apk` binaries into private or cache storage.
4. **Notify.** When a newer version is detected (and not skipped), the app presents an in-app notification card (`UpdateNotificationCard`) on screen.
5. **Redirect.** Tapping "Update" launches an external `Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))` with `FLAG_ACTIVITY_NEW_TASK`, handing off the release page/download to the browser or system download manager. Standard system mechanisms handle the file safely and transparently.
6. **Skip / Dismiss.** "Skip" persists the version into settings so the user is not prompted again for that release; "Later" dismisses the notification for the current session.

## Consequences

- Completely eliminates Google Play Protect malware/downloader heuristics.
- No `REQUEST_INSTALL_PACKAGES` required.
- Transparent and user-controlled updates via official GitHub releases.
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
