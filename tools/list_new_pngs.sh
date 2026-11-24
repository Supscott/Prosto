#!/usr/bin/env bash
# Prints the PNGs under <dir> that are newer than <marker>, the file preview.sh touches before its
# Gradle step, sorted by path. Prints nothing when the run wrote none. Any name counts (many preview
# tests write e.g. home_screen_dark_feedback_card.png); build intermediates are skipped, since the
# same Gradle run may rewrite merged resource PNGs.
# Usage: list_new_pngs.sh <dir> <marker>
set -euo pipefail

dir="$1"
marker="$2"
[[ -d "$dir" ]] || exit 0
find "$dir" \( -path '*/intermediates' -o -path '*/generated' -o -path '*/tmp' -o -path '*/kotlin' \) -prune \
    -o -name '*.png' -newer "$marker" -print | sort
