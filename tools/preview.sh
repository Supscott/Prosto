#!/usr/bin/env bash
set -euo pipefail

# Render Compose Previews to PNG using Robolectric native graphics.
# Usage: tools/preview.sh [TestClass | TestClass.method]   (default BottomBarPreviewTest)
# The target goes to Gradle as --tests "*TARGET*", so a single method works too
# (tools/preview.sh SearchOverlayPreviewTest.renderLoading): use it to bisect a hung test.
# A test running longer than 240 s is killed by gradle/test-watchdog.gradle.kts; change the limit with
# ORG_GRADLE_PROJECT_testTimeoutSeconds=N in the environment.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck source=tools/ensure_worktree_files.sh
source "$ROOT/tools/ensure_worktree_files.sh"
ensure_worktree_files "$ROOT"
# shellcheck source=tools/gradle_lock.sh
source "$ROOT/tools/gradle_lock.sh"
gradle_lock "$@"

TARGET="${1:-BottomBarPreviewTest}"
# LOW_MEM=1 compiles in-process when the Kotlin daemon runs out of memory (parallel worktrees).
LOW_MEM_ARGS=()
if [[ "${LOW_MEM:-}" == "1" ]]; then
  LOW_MEM_ARGS=(-Pkotlin.compiler.execution.strategy=in-process -Pkotlin.daemon.jvmargs=-Xmx3g --max-workers=2)
fi

echo "==> Rendering previews for target: $TARGET"
# Marker for this run: only PNGs written after it are listed (older ones in app/build are stale).
# A fixed path in build/ (no EXIT trap here: gradle_lock.sh owns it), so a failed run leaks nothing.
MARKER="$ROOT/build/preview-run-start"
mkdir -p "$ROOT/build"
touch "$MARKER"
run_with_timeout ./gradlew testDebugUnitTest --tests "*$TARGET*" --rerun ${LOW_MEM_ARGS[@]+"${LOW_MEM_ARGS[@]}"}

new_pngs="$(bash "$ROOT/tools/list_new_pngs.sh" "$ROOT/app/build" "$MARKER")"
if [[ -n "$new_pngs" ]]; then
  echo "==> Rendered preview images written by this run:"
  echo "$new_pngs" | xargs ls -lh
else
  echo "==> No preview PNG was written by this run (none is newer than the start; check the Gradle output above)"
fi
