#!/usr/bin/env bash
set -euo pipefail

# Render Compose Previews to PNG using Robolectric native graphics
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TARGET="${1:-BottomBarPreviewTest}"

echo "==> Rendering previews for target: $TARGET"
./gradlew testDebugUnitTest --tests "*$TARGET*" --rerun

echo "==> Rendered preview images in app/build/:"
ls -lh app/build/*preview*.png 2>/dev/null || true
