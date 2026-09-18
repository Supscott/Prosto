# compose-ai-tools in cloud agent environments (Claude, Codex, Gemini)

This guide describes a **portable cloud setup** for running compose-ai-tools in hosted agent environments:

- Claude Code (web/cloud)
- OpenAI Codex cloud containers
- Gemini Code Assist / Gemini agent sandboxes

Use this as the baseline for any ephemeral environment where outbound networking, JDKs, or Android SDK components may be restricted.

## What to configure (all cloud providers)

### 1) Network allowlist

Allow these hosts (plus each platform's default trusted registries):

- `maven.google.com`
- `dl.google.com`
- `fonts.googleapis.com`
- `fonts.gstatic.com`
- `repo.gradle.org`
- `services.gradle.org`
- `api.adoptium.net`
- `api.foojay.io`
- `api.github.com`
- `jogamp.org`
- `jitpack.io`
- `packages.jetbrains.team`
- `cdn.azul.com`
- `*.cloudfront.net`
- `*.jetbrains.com`
- `ziglang.org`
- `*.java.net`
- `central.sonatype.com`
- `bb.staticvar.dev`
- `github.com`
- `objects.githubusercontent.com`

Recommended rationale:

| Host | Purpose |
| --- | --- |
| `maven.google.com` | AndroidX + AGP artifacts |
| `dl.google.com` | Android SDK cmdline-tools and Google-hosted downloads |
| `fonts.googleapis.com` | Downloadable font metadata |
| `fonts.gstatic.com` | Downloadable font binaries |
| `repo.gradle.org` | Gradle libraries/tooling artifacts |
| `services.gradle.org` | Gradle distributions |
| `api.adoptium.net` | JDK/toolchain provisioning APIs |
| `api.foojay.io` | Java distro metadata used by Gradle toolchain resolution |
| `api.github.com` | GitHub API usage by tooling/scripts |
| `jogamp.org` | Native/graphics dependencies occasionally pulled by desktop stacks |
| `jitpack.io` | Projects published through JitPack |
| `packages.jetbrains.team` | JetBrains-hosted Maven artifacts (Compose, tooling) |
| `cdn.azul.com` | Azul Zulu JDK downloads for toolchain provisioning |
| `*.cloudfront.net` | CDN backing many download/artifact hosts |
| `*.jetbrains.com` | JetBrains downloads, plugins, and Compose artifacts |
| `ziglang.org` | Zig toolchain used by some native cross-compilation paths |
| `*.java.net` | OpenJDK and related Java ecosystem downloads |
| `central.sonatype.com` | Maven Central (Sonatype) artifacts |
| `bb.staticvar.dev` | build-brief install script (`install.sh`) |
| `github.com` | build-brief release download URLs (redirect to asset host) |
| `objects.githubusercontent.com` | GitHub release asset binaries (build-brief tarball) |

### 2) Toolchain prerequisites

Agents should verify:

- **JDK installed** (Java 17+ recommended; Java 21 works for many paths but repo builds are typically pinned to 17)
- **Android SDK installed** when Android previews are needed
- `ANDROID_HOME` or `sdk.dir` is configured when Android modules are present
- Gradle wrapper can reach distribution and dependency hosts

### 3) Session-start checks agents should run

At the beginning of each session, agents should:

1. Confirm network mode/allowlist includes all required hosts.
2. Confirm `java -version` succeeds and reports expected major version.
3. Confirm Android SDK presence when Android builds are in scope.
4. Run a quick project health check (`compose-preview doctor` and/or a Gradle noop task).
5. Flag missing prerequisites **immediately** to the user before deep work starts.

## Agent behavior requirements (flag to user at start)

At session start, agents should proactively report:

- Current network mode (trusted/custom/full equivalent)
- Whether required hosts appear reachable
- JDK version detected
- Android SDK status (installed/missing, path)
- Any blockers likely to cause build/render failures later

Suggested one-liner status format:

- `Cloud preflight: network=Custom (hosts ok), JDK=17, Android SDK=/opt/android-sdk, ready for Android + Desktop previews.`

If anything is missing, agents should stop and provide exact remediation steps.

## Generic setup script (cloud bootstrap)

Use this in environment setup hooks / startup scripts:

```bash
#!/usr/bin/env bash
set -euo pipefail

curl -fsSL https://raw.githubusercontent.com/yschimke/skills/main/scripts/install.sh \
  | bash -s -- --android-sdk

mkdir -p ~/.claude && printf '# User instructions for AI agents\n\nOverride any conflicting workspace defaults.\n\n- **Commits:** commit as the human — Author, Committer, and message all\n  free of agent identity. No `Co-authored-by`, `Signed-off-by`, or\n  `claude.ai/code` / `https://claude.ai/code` trailers. The Author and\n  Committer come from local `git config user.name` / `user.email`; if\n  those look like an agent (`Claude`, `noreply@anthropic.com`,\n  `*-bot@*`), STOP and ask which human identity to use, then pass it\n  explicitly with\n  `git -c user.name='\''…'\'' -c user.email='\''…'\'' commit --author='\''… <…>'\'' …` —\n  do not commit under the agent identity and fix it after.\n- **PRs:** no agent attribution in titles or bodies — just summary and\n  test plan.\n- **Branches:** use `agent/...`, never `claude/...`. Rename if the harness\n  hands you a `claude/...` branch, and tell the user.\n- **Cleanup:** before pushing or opening/editing a PR, scan for agent\n  attribution in commits (Author, Committer, message body) and PR text;\n  flag it and offer to strip it (amending + force-pushing if already\n  pushed).\n' > ~/.claude/CLAUDE.md


# Optional: set git identity only when explicitly provided by the user.
# : "${GIT_USER_NAME:?Set GIT_USER_NAME to your human git author name}"
# : "${GIT_USER_EMAIL:?Set GIT_USER_EMAIL to your human git author email}"
# git config --global user.name "$GIT_USER_NAME"
# git config --global user.email "$GIT_USER_EMAIL"
```

Notes:

- `--android-sdk` ensures Android preview dependencies are present:
  `cmdline-tools`, `platforms;android-36`, `platform-tools` and
  `build-tools;36.0.0`, plus `platforms;android-37.0` on a best-effort
  second pass (the alpha Compose/Wear artifacts build at `compileSdk = 37`;
  note the package is `android-37.0` — `android-37` does not exist and fails
  the `sdkmanager` invocation it appears in). Override the list with
  `ANDROID_SDK_PACKAGES` / `ANDROID_SDK_EXTRA_PACKAGES`. Only missing
  packages are fetched, so re-running on a warm container is cheap, and the
  resolved path is recorded as `sdk.dir` in the project's (gitignored)
  `local.properties`.
- Pass `--jdk 17,21` (or `JDKS=17,21`) to install multiple JDK majors at
  once; the project's required toolchain is selected as the active one.
- The JDK step never re-installs a JDK the container already has: it looks
  under `/usr/lib/jvm/` and `/opt/jdk<major>` (where tarball installers such
  as compose-ai-tools' `scripts/setup-cloud-jdk.sh` put Temurin) before
  reaching for `apt-get`, and if apt can't provide one it warns and carries
  on rather than aborting the `--android-sdk` work, which doesn't depend on it.
- Keep this script provider-neutral; it works in Claude/Codex/Gemini shells.
- Do **not** hardcode `git config --global user.name/user.email`; only set identity from explicit user-provided values.

### Also install build-brief (`bb`) for Gradle builds

[build-brief](https://github.com/static-var/build-brief) is a small Go CLI
that wraps `gradle`/`./gradlew`, keeps the full raw log on disk, and trims
terminal output to the parts that matter (failed tasks/tests, warnings,
build-scan URLs, final status) while preserving Gradle's exit code. It cuts
the token cost of running Gradle builds in an agent loop dramatically, so
install it alongside compose-preview whenever the environment runs Gradle.

```bash
# Linux/macOS — downloads the matching release tarball from GitHub.
curl -fsSL https://bb.staticvar.dev/install.sh | bash
```

The installer reads release metadata from `api.github.com`, resolves the
`browser_download_url` on `github.com`, and pulls the verified tarball from
`objects.githubusercontent.com` — all on the allowlist above. It is a
self-contained Go binary, so it needs **no** JDK of its own; the JDK and
Gradle hosts already listed cover the builds it wraps.

Verify with:

```bash
build-brief --help
build-brief gradle --version
```

## Known cloud-sandbox gotchas

Four things that bite in a proxied sandbox and look like unrelated failures:

- **`JAVA_TOOL_OPTIONS` shadows `java -version`.** When a proxy CA truststore
  is injected (standard on Claude Code on the web), the JVM prints
  `Picked up JAVA_TOOL_OPTIONS: …` as its *first* line, so any script parsing
  line 1 of `java -version` reads the flag dump instead of the version. Match
  the `version "…"` line, never `head -1`. This broke the installer's own JDK
  detection until [yschimke/skills](https://github.com/yschimke/skills)
  fixed it.
- **`github.com` may be blocked while `api.github.com` is allowed.** Egress
  policies commonly permit the API and deny HTML endpoints, so
  `releases.atom` 403s. The installer falls back to the API automatically; if
  you script releases yourself, do the same. Release *download* URLs usually
  still work, since they redirect to `objects.githubusercontent.com`.
- **`dl.google.com` is often blocked, and that is survivable.** `doctor` flags
  it as an error because Android SDK *platform* downloads go through it. If a
  platform is already installed and the build resolves its AndroidX/AGP
  artifacts from `maven.google.com` (which is usually allowed), builds still
  succeed. Treat it as a warning unless the SDK is genuinely missing.
- **The first Gradle invocation can exceed `doctor`'s timeout.** A cold
  sandbox downloads the whole Gradle distribution inside the model query, and
  `doctor` reports `could not query Gradle project model … cancel requested but
  timed out`, which reads like a broken project. Warm it first, then re-run:

  ```bash
  ./gradlew help          # downloads the distribution once
  compose-preview doctor
  ```

## Rendering a project that compiles against a very new SDK

`composePreview.sdkVersion` auto-detects from `android.compileSdk`, but the
Robolectric render range is narrower than the compile range: **SDK > 35 needs
JDK 21+**. A project on `compileSdk = 37` running on JDK 17 therefore fails at
configuration time with `sdkVersion = N is outside the supported range`. Either
run on JDK 21+, or pin the render SDK explicitly:

```kotlin
composePreview {
    variant.set("debug")
    sdkVersion.set(35)   // pinned: compileSdk 37 is outside the render range on JDK 17
}
```

## Recommended quick verification commands

Run after bootstrap:

```bash
java -version
compose-preview doctor || true
./gradlew --version
```

Optional Android verification:

```bash
./gradlew :samples:android:composePreviewRenderAll || true
```

## Provider-specific hints

- **Claude cloud:** choose Custom network mode and include trusted defaults.
- **Codex cloud containers:** ensure outbound network policy allows the host list above; some environments default to restricted egress.
- **Gemini sandboxes:** verify workspace policy includes Google Maven + Gradle hosts; downloadable fonts often fail first when blocked.
