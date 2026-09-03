# Execution Plan — Jestk Browser for Android

## Architecture & Tracks
1. **Survey Phase**:
   - Spec Miner 1: R1 (Gradle, Project Structure, Architecture, Room DB Schemas & DAOs)
   - Spec Miner 2: R2 & R5 (Dual-Tier Bottom Bar, Auto-hide, Search, Tab Grid, Home, Settings)
   - Spec Miner 3: R3, R4 & R6 (WebViewPool, Lifecycle, Snapshot VisualStateCallback, AdBlocker HashSet, Media/PiP, Back Handling)

2. **Track 1: Parallel E2E Test Suite Creation (E2E Testing Track)**:
   - Setup robust JVM/Robolectric/Unit/E2E test harness & assertions.
   - Tier 1: Feature Coverage (>=5 tests per feature across R1-R6)
   - Tier 2: Boundary & Corner Cases (>=5 tests per feature)
   - Tier 3: Cross-Feature Interaction Cases (pairwise)
   - Tier 4: Real-World Scenarios (multi-tab browsing, ad-blocking flow, snapshot caching & cold boot restore)
   - Emit `TEST_READY.md`.

3. **Track 2: Implementation Track (Milestones)**:
   - **Milestone 1**: Scaffolding, Room DB (Tabs, History, Favorites, Settings), MVVM State & ViewModels.
   - **Milestone 2**: WebViewPool, Safe Detach/Attach, Background Audio/Video preservation, VisualStateCallback Snapshot Engine.
   - **Milestone 3**: Native Ad & Tracker Blocker (compiled blocklist assets + HashSet interceptor), WebChromeClient Fullscreen, PiP, Back Navigation.
   - **Milestone 4**: Dual-Tier Bottom Navigation Bar (Tier 1 tab strip with active pill, inactive circles, swipe gestures, auto-hide scroll translation; Tier 2 action dock).
   - **Milestone 5**: Overlays & Views (Search Modal with bias 0.55 & history stack, 3-Column Tab Grid with swipe-to-dismiss, Home Recents/Favorites, Settings Sheet).
   - **Milestone 6**: Integration, `./gradlew assembleDebug` verification, 100% E2E test pass (Tiers 1-4), Tier 5 Adversarial Hardening.

4. **Forensic Audit & Review Gates**:
   - Every milestone undergoes Explorer -> Worker -> 2 Reviewers -> 2 Challengers -> Forensic Auditor.
   - Strict pass gate criteria: Build passes, reviews approve, challenges pass, audit clean.
