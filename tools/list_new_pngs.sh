#!/usr/bin/env bash
# Prints the preview PNGs under <dir> that are newer than <marker>, the file preview.sh touches before
# its Gradle step, sorted by path. Prints nothing when the run wrote none.
# Usage: list_new_pngs.sh <dir> <marker>
set -euo pipefail

dir="$1"
marker="$2"
[[ -d "$dir" ]] || exit 0
find "$dir" -name '*preview*.png' -newer "$marker" | sort
