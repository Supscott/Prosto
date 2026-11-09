# Spec: Downloaded APK Opening & Execution

## Problem Statement
When a user downloads an `.apk` file (such as a release artifact from GitHub), the downloaded file cannot be opened or executed within the browser. Tapping the completed download either fails with "No app available to open this file", falls back to an unhandled MIME type (`application/octet-stream`), or is blocked because the app lacks Android's package installation permission declaration and unknown app sources handling. Furthermore, tapping "Open" on the download completion snackbar merely opens the downloads list screen rather than executing/opening the finished file.

## Solution
1. Declare `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` in `AndroidManifest.xml` (explicitly permitted by Google Play for web browsers).
2. Ensure MIME type resolution overrides `application/octet-stream` to `application/vnd.android.package-archive` whenever the file ends with `.apk`.
3. In `openDownloadedFile` / download opening helper:
   - For `.apk` files, check `context.packageManager.canRequestPackageInstalls()`.
   - If false, navigate the user to `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with `package:${context.packageName}` and inform them via a localized prompt.
   - If true (or once granted), launch the system Package Installer via `Intent(Intent.ACTION_VIEW)` with `application/vnd.android.package-archive` and `FLAG_GRANT_READ_URI_PERMISSION`. Do not wrap with `Intent.createChooser` which strips permissions for package installation.
4. On the download completion snackbar in `JestkApp.kt`, tapping "Open" immediately opens/executes the downloaded file using this installer flow instead of simply opening the downloads screen.
5. Provide localized string resources for all user prompts (EN & RU) adhering to the zero hardcoded strings invariant.

## User Stories
1. As a browser user who downloads an APK from GitHub or the web, I want to tap "Open" in the completion snackbar to immediately launch the Android package installer, so that I can install the app without searching through the filesystem.
2. As a browser user who taps a completed APK in the Downloads screen, I want the system package installer to prompt me with "Do you want to install this application?", so that I can review permissions and complete the installation.
3. As a browser user on Android 8+, when the browser does not yet have permission to install unknown apps, I want to be redirected to the system setting to enable it, so that I understand why the install didn't start immediately.
4. As a browser user downloading non-APK files, I want existing file opening behavior (images, PDFs, archives) to continue functioning via FileProvider.
5. As a Russian-speaking user, I want all installation prompts and snackbars to be cleanly translated in Russian without hardcoded English text.

## Implementation Decisions
- **Manifest**: Add `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` to `app/src/main/AndroidManifest.xml`.
- **MIME Resolution**: In `DownloadsScreen.kt` (and any shared opener helper), if `fileName.endsWith(".apk", ignoreCase = true)`, force MIME type to `application/vnd.android.package-archive`.
- **Package Installer Intent**:
  - Direct intent: `Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) }`.
  - Permission check: `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) { ... }`.
- **Snackbar "Open" action**: In `JestkApp.kt`, invoke `openDownloadedFile(context, download)` directly when tapping "Open".
- **Localization**: Add strings to `res/values/strings.xml` and `res/values-ru/strings.xml`.
