#!/usr/bin/env bash
# Lists git worktrees with their merge state, and removes the ones that are done.
#
#   tools/worktrees.sh list            one line per worktree: state, uncommitted files, branch, path
#   tools/worktrees.sh prune-merged    remove worktrees whose branch is merged into main and that
#                                      have no uncommitted files; asks first (--yes skips the question)
#
# Never touches the main checkout, the current worktree, a dirty worktree or an unmerged branch.
# Branches are kept; delete them with `git branch -d` if wanted.
set -uo pipefail

BASE="${WORKTREES_BASE:-main}"
main_checkout="$(git worktree list --porcelain | awk '/^worktree/{print $2; exit}')"
current="$(git rev-parse --show-toplevel)"

# Prints "<state> <dirty-count> <branch> <path>" for each worktree with a branch.
rows() {
    git worktree list --porcelain | awk '/^worktree/{w=$2} /^branch/{sub("refs/heads/", "", $2); print w, $2}' |
        while read -r path branch; do
            if git merge-base --is-ancestor "$branch" "$BASE" 2>/dev/null; then
                state=merged
            else
                state="unmerged+$(git rev-list --count "$BASE..$branch")"
            fi
            dirty="$(git -C "$path" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
            echo "$state $dirty $branch $path"
        done
}

case "${1:-list}" in
    list)
        rows | sort | awk '{printf "%-12s dirty=%-3s %s  %s\n", $1, $2, $3, $4}'
        ;;
    prune-merged)
        candidates="$(rows | awk -v m="$main_checkout" -v c="$current" '$1 == "merged" && $2 == 0 && $4 != m && $4 != c {print $4}')"
        if [[ -z "$candidates" ]]; then
            echo "nothing to prune"
            exit 0
        fi
        echo "Merged into $BASE, no uncommitted files:"
        echo "$candidates" | sed 's/^/  /'
        if [[ "${2:-}" != "--yes" ]]; then
            read -r -p "Remove these $(echo "$candidates" | wc -l | tr -d ' ') worktrees? [y/N] " answer
            [[ "$answer" == "y" || "$answer" == "Y" ]] || { echo "kept"; exit 0; }
        fi
        echo "$candidates" | while read -r path; do
            git worktree remove "$path" && echo "removed $path"
        done
        git worktree prune
        ;;
    *)
        sed -n '2,/^[^#]/p' "${BASH_SOURCE[0]}" | grep '^#' | sed 's/^# \{0,1\}//'
        exit 1
        ;;
esac
