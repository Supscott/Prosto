#!/usr/bin/env bash
set -euo pipefail

# Render Compose Previews to PNG using Robolectric native graphics
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
# No EXIT trap here: gradle_lock.sh already owns it.
MARKER_DIR="$(mktemp -d)"
MARKER="$MARKER_DIR/run-start"
touch "$MARKER"
./gradlew testDebugUnitTest --tests "*$TARGET*" --rerun ${LOW_MEM_ARGS[@]+"${LOW_MEM_ARGS[@]}"}

new_pngs="$(bash "$ROOT/tools/list_new_pngs.sh" "$ROOT/app/build" "$MARKER")"
rm -rf "$MARKER_DIR"
if [[ -n "$new_pngs" ]]; then
  echo "==> Rendered preview images written by this run:"
  echo "$new_pngs" | xargs ls -lh
else
  echo "==> No preview PNG was written by this run (none is newer than the start; check the Gradle output above)"
fi
