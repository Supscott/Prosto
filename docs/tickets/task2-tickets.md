# Tickets: Task 2 - Downloaded APK Execution & Opening

## Ticket 2.1: Manifest Permission & MIME Type Resolver
- **Title**: Declare REQUEST_INSTALL_PACKAGES and enforce APK MIME type
- **Blocked by**: None
- **What it delivers**:
  - Adds `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` in `AndroidManifest.xml`.
  - Fixes MIME type resolution in `DownloadsScreen.kt` and `DownloadFileNameResolver.kt` so `.apk` files always resolve to `application/vnd.android.package-archive` even if the server supplied `application/octet-stream`.
- **Verification**: Unit tests for MIME resolution of `.apk` with octet-stream headers.

## Ticket 2.2: Package Installer Flow & Permission Request
- **Title**: Implement Package Installer intent and Unknown App Sources navigation
- **Blocked by**: Ticket 2.1
- **What it delivers**:
  - Handles `canRequestPackageInstalls()` check on Android O+.
  - Launches `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` when permission is not granted.
  - Launches direct `Intent(ACTION_VIEW)` without chooser for APKs with read URI permission.
  - Adds localized strings in `res/values/strings.xml` and `res/values-ru/strings.xml`.
- **Verification**: Unit tests for opener logic and invariant checks for zero hardcoded strings.

## Ticket 2.3: Wire Snackbar "Open" Action to Direct File Opener
- **Title**: Trigger file opening directly when clicking "Open" on download completion snackbar
- **Blocked by**: Ticket 2.2
- **What it delivers**:
  - Clicking "Open" on the download completion snackbar in `JestkApp.kt` directly invokes the file opener rather than only opening the downloads list.
- **Verification**: UI preview test / unit test for download snackbar open action.
