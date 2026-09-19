# Lessons Learned & Bug Post-Mortems

This document tracks bugs encountered during development, their root causes, and verified fixes to avoid repeating mistakes.

---

## Log Template

```markdown
### [Bug Title / Short Description]
- **Date:** YYYY-MM-DD
- **Symptom:** What went wrong or failed?
- **Root Cause:** Why and how did it occur?
- **Resolution & Prevention:** How was it fixed, and how should future code prevent this?
```

---

## Resolved Bugs & Lessons

### Gradle configuration fails with `java.lang.IllegalArgumentException: 25.0.3`

- **Date:** 2026-09-19
- **Symptom:** Every Gradle sync/build failed during the configuration phase with `FAILURE: Build failed with an exception. * What went wrong: 25.0.3`, thrown from `org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse` during Kotlin DSL script compilation. The Gradle daemon itself started fine.
- **Root Cause:** Android Studio 2026.1 bundles JBR `25.0.3` and `.idea/gradle.xml` uses `gradleJvm = #GRADLE_LOCAL_JAVA_HOME`, which fell back to that JBR. Gradle 8.14.5 embeds a Kotlin/IntelliJ `JavaVersion` parser that cannot parse the Java 25 version string, so the Kotlin DSL compiler aborts before the build is configured. Nothing in the project sources was wrong.
- **Resolution & Prevention:** Pin the Gradle daemon JVM to a JDK that Gradle 8.14.x understands (JDK 23 here) with `org.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home` in `~/.gradle/gradle.properties`. That property wins over the client JVM, so Android Studio (and CLI runs from any shell) fork a JDK 23 daemon. Verified: client = JBR 25, daemon = JDK 23, `./gradlew help` → `BUILD SUCCESSFUL`.
- **Prevention:** When a Gradle failure happens before any task runs and the message is a bare Java version number, suspect the daemon JDK, not the build script. Keep the Gradle daemon on a JDK the wrapper supports (JDK 21 is the safest for AGP 8.7 / Kotlin 2.0.21); do not let the IDE's bundled JBR silently drive the daemon JVM.

### Settings & Downloads sheets never opened

- **Date:** 2026-02-20
- **Symptom:** Tapping Settings or Downloads in the ActionDock / popup menu did nothing — no sheet appeared.
- **Root Cause:** Two parallel pieces of state described the same UI. `OpenSettings`/`OpenDownloads` set `activeScreen = SETTINGS/DOWNLOADS` and reset `activeOverlay = NONE`, but `JestkApp` only rendered `SettingsSheet`/`DownloadsSheet` when `activeOverlay == SETTINGS/DOWNLOADS`. The render condition and the state the events actually wrote had drifted apart (the overlay-based wiring was correct until commit `075850c`; the screen state was added later without updating the renderer).
- **Resolution & Prevention:** Added `BrowserUiState.isSettingsVisible` / `isDownloadsVisible` predicates that accept either `activeScreen` or `activeOverlay`, made `JestkApp` render from those, and asserted them in `BrowserViewModelTest` / `BrowserViewModelDownloadsTest`. Keep a single render predicate derived from state; never duplicate "is this screen open" checks across ViewModel and UI.

### Page content flickers on Android 12+ after opening a site

- **Date:** 2026-09-19
- **Symptom:** Opening any website made the whole page viewport flicker (rapid blank/stale frames). It continued after the page finished loading and stopped only after switching to another tab and back. Android 12+ only.
- **Root Cause:** `JestkApp` applied `Modifier.haze(rootHazeState)` to `WebViewContainer`, i.e. the live WebView was a Haze source. Haze 1.1.1's `HazeNode.draw()` calls `contentLayer.record { drawContent() }` + `drawLayer(...)` whenever `canUseGraphicLayers()` is true, which is `SDK_INT >= 31 && canvas.isHardwareAccelerated`. So from Android 12 (SDK 31 — note: not 32, where the blur itself starts) the WebView is repainted through an offscreen `RenderNode` re-recorded every frame, and Chromium's functor output ends up stale/blank. A tab switch disposes the `HazeNode`, whose `onDetach()` releases `HazeState.contentLayer`, so the next draw records a fresh layer — hence the "fix by switching tabs".
- **Resolution & Prevention:** Do not put a live `WebView` (or any asynchronously repainting `AndroidView`) inside `Modifier.haze`/`hazeSource`. Over web pages the frosted glass uses Haze's scrim fallback; `Theme.liquidFrostedGlass` now passes an explicit `fallbackTint` so that fallback stays semi-transparent (Haze's `boostForFallback` multiplies alpha by `1 + blurRadius/72`, which turned the dock opaque). Guarded by `FrostedGlassFallbackTest`, which compares the tile rendered over a white vs a black background — identical pixels mean the fallback became opaque. When diagnosing a rendering bug, the SDK version threshold is the strongest clue: grep for the only SDK-gated code path before suspecting app logic.
