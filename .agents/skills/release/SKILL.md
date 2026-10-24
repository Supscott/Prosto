---
name: release
description: Prepare, publish, or retry a versioned Prosto Android release when asked to release this repository.
---

# Prosto Release

Use this skill for a versioned Android release from this repository. The release workflow is `.github/workflows/release.yml`: it runs on pushes of `v*.*.*` tags, runs `:app:testDebugUnitTest` and `:app:assembleRelease`, signs the APK with repository secrets, and creates a GitHub release.

## Prepare

1. Read `LESSONS.md` and `HARNESS.md` before planning, as required by `AGENTS.md`.
2. Inspect the current branch, working tree, `origin`, release tags, and `versionCode` / `versionName` in `app/build.gradle.kts`. Confirm the selected release version with the user if they have not provided one. Keep the Gradle `versionName` equal to the tag without the `v` prefix, and increment `versionCode` monotonically.
3. Make and review all release changes. Run `./gradlew :app:testDebugUnitTest` before tagging. Fix any failure and rerun the failing test plus the full suite; do not hide asynchronous test races with longer fixed sleeps. The GitHub workflow still performs its own test and release build.
4. Create a release commit and annotated `vX.Y.Z` tag only after the version and tested commit agree. Check that the tag resolves to the release commit.

## Publish

Push the intended branch and the release tag to the verified `origin` only when the user has authorized publication. After pushing, verify the refs and report that the GitHub workflow has started; call the release complete only after the workflow succeeds and the release exists.

## Retry a failed tag

First inspect the failed workflow and correct its cause. Rerun the failing test and full unit suite before retrying. If the failed tag already exists remotely, moving it rewrites a published ref: get the user's explicit approval for that exact tag, read its current remote object ID, and update it with `--force-with-lease` against that ID. Never force-push a branch. Confirm the remote tag now points to the verified commit and report that the workflow has been retriggered.
