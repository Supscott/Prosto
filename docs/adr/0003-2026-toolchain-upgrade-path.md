# ADR 0003 — The 2026 toolchain upgrade is documented as a verified path, not applied now

- **Status:** Accepted
- **Date:** 2026-10-22
- **Scope:** `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`

## Context

An initial tech debt roadmap asked for "strict forward-compatibility for the target
2026 latest stable ecosystem" and names a target matrix: AGP `9.4+`, Gradle `9.1+`, Kotlin
`2.4.10`, Room `3.0.2`, Coil `3.6.0`, Compose BOM `2026.x`, compileSdk `36/37`.

That matrix was authored against version data that does not hold up:

- **Room `3.0.2` does not exist.** The latest published Room is **2.8.5**. Room's own versioning
  jumped `2.6 -> 2.7 -> 2.8`; there is no `3.x` line on Google Maven.
- **Kotlin `2.4.10` is not the latest stable** (`2.4.21` is), but more importantly **AGP 9.4.1
  bundles Kotlin `2.2.10`** through its built-in Kotlin support. The Compose compiler plugin must
  match the bundled Kotlin, so "AGP 9.4 + Kotlin 2.4" is not a combination that exists.
- **KSP `2.2.10-2.0.2`** (the version AGP 9.4.1's own POM pins) **cannot be used with AGP 9's
  built-in Kotlin** — see below.

The plan's *action* column, however, is not a version-bump list. It asks to "prepare build
scripts", to "ensure no deprecated Gradle 8 APIs", to "configure `room.schemaLocation`",
to "declare okhttp explicitly", to "structure `FaviconManager.toImageModel()` to return clean
models compatible with Coil 3", and to "replace `LocalClipboardManager` with `LocalClipboard`".
**Every one of those actions is already satisfied in the tree.**

The remaining question is whether the *actual version cut-over* belongs in this pass.

## Decision

**The version cut-over is not applied in this pass. The upgrade path is verified and recorded
here so that it can be executed as a self-contained change later.**

### What was verified

The target stack was validated in a throwaway probe project outside the repository
(`/tmp/agp9probe`), so no conclusion here rests on a guess. The probe builds a minimal app with
Room, Compose and KSP and reaches a signed-ready `assembleDebug`:

| Component | Repo today | Verified target | Status |
|---|---|---|---|
| Gradle | 8.14.5 | 9.8.2 | builds |
| AGP | 8.13.2 | 9.4.1 | builds |
| Kotlin | 2.0.21 (KGP plugin) | 2.2.10 (AGP built-in) | builds |
| KSP | 2.0.21-1.0.28 | **2.3.12** | builds — **2.2.10-2.0.2 does not** |
| Room | 2.6.1 | 2.8.5 | builds |
| Compose BOM | 2025.12.00 | 2026.09.00 | builds |
| compileSdk | 35 | `37` + `compileSdkMinor = 2` | builds |
| Coil | 2.7.0 (`io.coil-kt`) | 3.6.3 (`io.coil-kt.coil3`) | not yet exercised in-app |

Two non-obvious blockers were found and are the reason the upgrade is not a one-line change:

1. **KSP and AGP 9's built-in Kotlin disagree about source sets.** With KSP `2.2.10-2.0.2` the
   build fails during *configuration*:
   ```
   Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin.
     Kotlin source set 'debug' contains: [.../generated/ksp/debug/kotlin, .../generated/ksp/debug/java]
   ```
   The offending DSL is emitted by KSP, not by the app's own build script. KSP `2.3.12` does not
   trip the check and the probe builds unchanged with it. (The error message offers
   `android.disallowKotlinSourceSets=false` as a suppression; the KSP upgrade is the better fix
   because the suppression re-enables the DSL that AGP 9 is trying to retire.)
2. **`compileSdk 37` is not installable as a plain platform.** The SDK ships `android-37.2`, a
   *minor-version* platform (`AndroidVersion.ApiLevel=37.2`), which AGP 9.4 addresses through the
   separate `compileSdkMinor` property. A `compileSdk = 37` alone does not resolve to it.

### Why the cut-over is deferred

- **No audit finding requires it.** `TECH_DEBT_AUDIT.md` (the authoritative audit) contains no
  toolchain finding. Every finding it lists is about code: layering, typing, resource hygiene,
  observability, deprecated API use.
- **CI would break.** `compileSdk 37` requires the `android-37.2` platform, which the
  `ubuntu-latest` GitHub runner does not preinstall; the workflow would need an explicit
  `sdkmanager` step for a minor-version package that cannot be named reliably today.
- **It is a behavioural change, not a refactor.** Moving `compileSdk`/`targetSdk` to 36/37 opts
  the app into the new edge-to-edge defaults. That needs on-device validation, not a green unit
  suite, and it is a different kind of risk from the layering defects this pass set out to fix.
- **The blast radius is wide.** Coil 3 renames the `coil.*` package to `coil3.*` across seven
  files, Compose BOM 2026.09 brings API changes on top, and Room 2.8.5 moves the build onto
  KSP2. Bundling all of that with the audit fixes would make the diff unreviewable and would
  make a regression impossible to attribute.

### What the codebase already does so the upgrade stays cheap

The compatibility-shaped work is done and does not need to be redone:

- `FaviconManager` exposes a sealed `FaviconImageModel` (`Asset` / `LocalFile` / `Remote`) with a
  `toCoilModel()` bridge, so the Coil 3 package rename touches one adapter instead of seven
  call sites.
- The Compose clipboard is accessed through `LocalClipboard`, not the deprecated
  `LocalClipboardManager`.
- Icons use `Icons.AutoMirrored.*`.
- Room exports schemas (`exportSchema = true`, `room.schemaLocation`), keeps its DAOs
  `suspend`, and ships `MIGRATION_7_8`.
- `okhttp` is an explicit direct dependency, so Coil 3 dropping its transitive OkHttp changes
  nothing.

## Consequences

- The repository stays on a supported, green toolchain (AGP 8.13.2 / Gradle 8.14.5 / Kotlin
  2.0.21 / compileSdk 35). Nothing about this pass depends on the upgrade.
- The 2026 upgrade is now a known-good change rather than an open question: the exact versions,
  the two blockers and their fixes, and the CI implication are all written down.
- A future developer does not have to re-derive the KSP/`kotlin.sourceSets` incompatibility, which is
  the single most likely thing to burn time on.
- If the upgrade is later performed, it should be its own branch and its own commit, with verification
  tests passing, so that a behavioural regression in the new toolchain is distinguishable from
  a regression in the code.

## Alternatives considered

- **Apply the upgrade now, in the same branch.** Rejected: it mixes a multi-thousand-line
  dependency/API migration with the layering fixes, and it would land a `compileSdk` bump that
  CI cannot yet build.
- **Apply only `compileSdk 36` and keep AGP 8.** Rejected: it buys nothing — AGP 8.13.2 already
  supports compileSdk 36 — while still forcing a platform change on the CI runners.
- **Keep `KSP 2.2.10-2.0.2` and suppress the error with
  `android.disallowKotlinSourceSets=false`.** Rejected for the migration: it keeps the retired
  DSL alive instead of moving to the KSP line that already uses `android.sourceSets`.
