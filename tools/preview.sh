#!/usr/bin/env bash
set -euo pipefail

# Render Compose Previews to PNG using Robolectric native graphics
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck source=tools/ensure_worktree_files.sh
source "$ROOT/tools/ensure_worktree_files.sh"
ensure_worktree_files "$ROOT"

TARGET="${1:-BottomBarPreviewTest}"
# LOW_MEM=1 compiles in-process when the Kotlin daemon runs out of memory (parallel worktrees).
LOW_MEM_ARGS=()
if [[ "${LOW_MEM:-}" == "1" ]]; then
  LOW_MEM_ARGS=(-Pkotlin.compiler.execution.strategy=in-process -Pkotlin.daemon.jvmargs=-Xmx3g --max-workers=2)
fi

echo "==> Rendering previews for target: $TARGET"
./gradlew testDebugUnitTest --tests "*$TARGET*" --rerun ${LOW_MEM_ARGS[@]+"${LOW_MEM_ARGS[@]}"}

echo "==> Rendered preview images in app/build/:"
ls -lh app/build/*preview*.png 2>/dev/null || true
