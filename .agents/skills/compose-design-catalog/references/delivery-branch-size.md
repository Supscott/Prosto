# Delivery-branch size and retention

Use this when a clone is unexpectedly large, a `design-artifacts/<system>` branch has accumulated
many generated snapshots, or a catalog owner is considering `split-per-preview`.

## Distinguish checkout size from Git history

Linked worktrees contain a small `.git` file; their objects live in the primary clone's common Git
directory. Measure both the checked-out files and the shared object database:

```sh
du -sh .
git rev-parse --git-common-dir
git count-objects -vH
```

Then measure the current delivery snapshot and its commit count:

```sh
branch=origin/design-artifacts/<system>
git rev-list --count "$branch"
git ls-tree -lr "$branch" |
  awk '{ bytes += $4 } END { printf "tip bytes: %d; files: %d\n", bytes, NR }'
```

`git rev-list --objects --all` combined with `git cat-file --batch-check` maps large reachable
blobs back to paths. Repeated `bundle/previews/**`, `bundle/modules/**`, and `bundle/bundle.png`
objects implicate appended catalog history, not the source checkout.

Also inspect commit subjects for repeated source SHAs:

```sh
git log --format='%h %ad %s' --date=short "$branch"
```

Two catalog commits naming the same source SHA should normally have identical trees and be skipped.
Diff such a pair. Hundreds of tiny PNG/SVG changes indicate capture or packing nondeterminism;
large repeated executable bundles indicate a split/classpath shape problem.

## Prevent the next growth cycle

Prefer this reusable-workflow shape:

```yaml
publish-live-bundle: true
defer-figma-svg: true
split-per-preview: false
```

The shared workflow already defaults `split-per-preview` to `false`; do not override it merely to
make a catalog live. `publish-live-bundle` provides the per-module executable bundles used by a
trusted serve host.

If a consumer demonstrably requires per-preview executable bundles, choose
`split-mode: full-shared-classpath`. Use `full` only when every split must work as a self-contained
offline download. Stabilise clocks, animation state, random data, fonts, locale, and capture order
before treating Git deduplication as a retention mechanism.

## One-time branch re-root

Appending generated snapshots is intentional because render history links to delivery-branch
commits. Re-rooting trades that history for clone size. Treat it as a destructive external action:
get the repository owner's explicit approval, check that no publisher is running, and record the
old tip before proceeding.

The reset must remove `history.json` and `preview-index.json`; copying them into the new root would
leave the served catalog pointing at commits the rewrite made unreachable. Preserve every other
current-tip path, including independently published parity files.

From a clean temporary clone with the target branch fetched:

```sh
target=design-artifacts/<system>
old_tip=$(git rev-parse "origin/$target")
tree=$(git rev-parse "$old_tip^{tree}")

git read-tree "$tree"
git update-index --force-remove history.json preview-index.json
trimmed_tree=$(git write-tree)
new_tip=$(git commit-tree "$trimmed_tree" \
  -m "chore(design-artifacts): reset generated history")

git push --force-with-lease="refs/heads/$target:$old_tip" \
  origin "${new_tip}:refs/heads/$target"
```

Do not use a blind `--force`. The exact lease aborts if a catalog or parity publisher moved the
branch after inspection. Rerun the normal design-artifacts workflow after the reset; it recreates
fresh bounded revision indexes from the new root. Confirm the served catalog and live lane before
considering the operation complete. Keep braces around `new_tip`: zsh treats the colon after an
unbraced parameter as a filename modifier and silently mangles the refspec.

GitHub may retain unreachable objects internally for a while, but fresh clones stop downloading
them as soon as no ref reaches them.

## Reclaim an existing local clone

After the remote rewrite, an existing clone still has the old objects until its remote-tracking ref
and reflogs no longer retain them and Git garbage-collects them. First fetch and verify the rewritten
tip. Garbage collection and reflog expiry affect every linked worktree sharing the common object
database, so require explicit approval and ensure no Git process is active before running them.

```sh
git fetch --prune origin
git reflog expire --expire=now --all
git gc --prune=now
git count-objects -vH
```

For developers who never inspect generated branches, a separate source-only clone using narrow or
negative fetch refspecs is safer than repeatedly downloading artifact history. Do not silently
change a shared clone's refspec: linked worktrees and preview-review workflows may rely on those
remote-tracking branches.
