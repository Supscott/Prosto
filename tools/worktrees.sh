#!/usr/bin/env bash
# Lists git worktrees with their merge state, and removes the ones that are done.
#
#   tools/worktrees.sh list            one line per worktree: state, uncommitted files, branch, path
#   tools/worktrees.sh prune-merged    remove worktrees whose branch is merged into main and that
#                                      have no uncommitted files, then delete every local branch
#                                      merged into main (git branch -D, ancestors only); asks first (--yes skips it)
#
# Never touches the main checkout, the current worktree, a dirty worktree, an unmerged branch or
# a "fresh" worktree (its branch has no commits of its own: an agent may be starting in it).
set -uo pipefail

BASE="${WORKTREES_BASE:-main}"
main_checkout="$(git worktree list --porcelain | awk '/^worktree/{print $2; exit}')"
current="$(git rev-parse --show-toplevel)"

# Prints "<state> <dirty-count> <branch> <path>" for each worktree with a branch.
rows() {
    git worktree list --porcelain | awk '/^worktree/{w=$2} /^branch/{sub("refs/heads/", "", $2); print w, $2}' |
        while read -r path branch; do
            # Read the whole reflog first: `| grep -q` under pipefail fails on a long one (SIGPIPE).
            reflog="$(git reflog show --format='%gs' "$branch" 2>/dev/null)"
            if ! grep -qE '^(commit|rebase|cherry-pick)' <<<"$reflog"; then
                # Never committed on: a worktree an agent may have just started in (it is merged
                # trivially). Kept; remove it by hand with `git worktree remove` once it is idle.
                state=fresh
            elif git merge-base --is-ancestor "$branch" "$BASE" 2>/dev/null; then
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
        current_branch="$(git rev-parse --abbrev-ref HEAD)"
        # Merged local branches, minus the base and the current one (a branch still checked out in a
        # kept worktree makes `git branch -d` refuse, which is fine).
        branches="$(git for-each-ref --format='%(refname:short)' --merged "$BASE" refs/heads |
            grep -vxF -e "$BASE" -e "$current_branch" || true)"
        if [[ -z "$candidates" && -z "$branches" ]]; then
            echo "nothing to prune"
            exit 0
        fi
        [[ -n "$candidates" ]] && { echo "Worktrees merged into $BASE, no uncommitted files:"; echo "$candidates" | sed 's/^/  /'; }
        [[ -n "$branches" ]] && echo "Local branches merged into $BASE: $(echo "$branches" | wc -l | tr -d ' ')"
        if [[ "${2:-}" != "--yes" ]]; then
            read -r -p "Remove these worktrees and branches? [y/N] " answer
            [[ "$answer" == "y" || "$answer" == "Y" ]] || { echo "kept"; exit 0; }
        fi
        [[ -n "$candidates" ]] && echo "$candidates" | while read -r path; do
            git worktree remove "$path" && echo "removed $path"
        done
        git worktree prune
        [[ -n "$branches" ]] && echo "$branches" | while read -r branch; do
            # -D: every listed branch is an ancestor of $BASE (--merged), so nothing is lost; -d would
            # refuse one whose remote copy has newer commits (the remote keeps them).
            git branch -D "$branch" >/dev/null 2>&1 && echo "deleted branch $branch"
        done
        exit 0
        ;;
    *)
        sed -n '2,/^[^#]/p' "${BASH_SOURCE[0]}" | grep '^#' | sed 's/^# \{0,1\}//'
        exit 1
        ;;
esac
