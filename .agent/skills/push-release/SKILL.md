---
name: push-release
description: "Release a new version of Prosto Browser: bumps versionCode/versionName, generates clean Release Notes (What's New + Bug Fixes), creates tag, pushes, and creates a draft GitHub Release."
---

# Push Release Skill

This skill automates the full release process for **Prosto Browser**:
1. Checks the repository state and assists with uncommitted changes.
2. Analyzes commit history since the previous tag.
3. Determines and bumps `versionCode` and `versionName` in `app/build.gradle.kts`.
4. Drafts structured Release Notes in English without emojis.
5. Commits the version bump and creates a Git tag.
6. Pushes the branch and tag in a single atomic push command (running `.git/hooks/pre-push` checks once).
7. Creates a draft GitHub release with the formatted notes.
8. Monitors the CI workflow (`release.yml`), verifying APK build, signing, and automatic publishing.

---

## Release Notes Specification

Release notes must follow this exact format:
- **Language**: English
- **Style**: No emojis, clean, concise
- **Sections**:
  - `### What's New`: Features with a title and a short 1-sentence description.
  - `### Bug Fixes`: Fixes as bullet points without extra description.

### Template

```markdown
### What's New
- Feature Title: Short explanation of what was added or changed.

### Bug Fixes
- Short description of the fix
```

*Rules:*
- Categorize `feat(...)` commits under `### What's New`. Translate technical commit titles into user-facing feature names with a brief description.
- Categorize `fix(...)` commits under `### Bug Fixes`. Keep each bullet concise without elaboration.
- If there are no new features, omit `### What's New`.
- If there are no bug fixes, omit `### Bug Fixes`.
- Do NOT include marketing slogans, APK download links, or requirements tables (GitHub and CI handle download assets).

---

## Release Procedure

Follow these steps sequentially:

### 1. Check Working Tree
Ensure on `main` and check if the working tree is clean:
```bash
rtk git status
```
If there are uncommitted changes:
- If they are unrelated or should be part of the release, prompt the user with `ask_question`:
  - "Commit changes before releasing" (provide commit message)
  - "Stash changes"
  - "Abort release"

### 2. Determine Changes & Versions
Find the latest release tag:
```bash
PREV_TAG=$(rtk git describe --tags --abbrev=0 2>/dev/null || echo "")
echo "Previous tag: $PREV_TAG"
```

Inspect commits since the previous tag:
```bash
rtk git log ${PREV_TAG}..HEAD --oneline
```

Inspect current `versionCode` and `versionName` in `app/build.gradle.kts`:
```bash
rtk grep -n "version" app/build.gradle.kts
```

#### Version Determination:
- If the user explicitly provided a target version (e.g. `/push-release 0.7.0`):
  - Set `versionName` to the given version (strip any leading `v`).
  - Increment `versionCode` by `+1`.
- If the user did NOT provide a version:
  - Analyze commit messages:
    - If any `feat` commits are present: bump the **minor** version (e.g. `0.6.1` -> `0.7.0`).
    - If only `fix` or maintenance commits are present: bump the **patch** version (e.g. `0.6.1` -> `0.6.2`).
  - Calculate `versionCode = current versionCode + 1`.
  - Use `ask_question` to ask the user to confirm the proposed version and preview the release notes.

### 3. Generate Release Notes
Draft the release notes based on the commit log following the specification above.

### 4. Update Version in Code
Edit `app/build.gradle.kts` to update `versionCode` and `versionName`.

### 5. Git Commit and Tag
```bash
rtk git add app/build.gradle.kts
rtk git commit -m "chore(release): bump to v${NEW_VERSION}"
rtk git tag -a "v${NEW_VERSION}" -m "Release v${NEW_VERSION}"
```

### 6. Push to GitHub (Single Command)
Push both `main` branch and the new tag in a **single command** to avoid running `.git/hooks/pre-push` multiple times:
```bash
rtk git push origin main "v${NEW_VERSION}"
```
> Note: The repository has `.git/hooks/pre-push` installed which automatically runs architectural invariants check, unit tests (`testDebugUnitTest`), and Android Lint (`lintDebug`). Pushing branch and tag together executes these checks once.

### 7. Create Draft GitHub Release
Create the draft release using GitHub CLI, associating it with the tag:
```bash
rtk gh release create "v${NEW_VERSION}" \
  --draft \
  --title "v${NEW_VERSION}" \
  --notes "${RELEASE_NOTES}"
```

### 8. Monitor CI & Report
1. Verify that GitHub Actions triggered the release workflow:
```bash
rtk gh run list --workflow="Release Build & Publish" -L 1
```
2. Wait or inspect the workflow execution:
```bash
rtk gh run watch
```
3. Once completed:
   - Verify that the release is published (`gh release view "v${NEW_VERSION}"`).
   - Confirm that `prosto.apk` and `checksums.txt` are attached to the release.
   - Report the final release link and status to the user.
