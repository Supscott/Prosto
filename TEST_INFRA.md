# E2E Test Infra: Jestk Browser for Android (`TEST_INFRA.md`)

## Test Philosophy
- **Opaque-box & Requirement-driven**: Derived directly from `ORIGINAL_REQUEST.md` and `spec.md`, independent of internal implementation details.
- **Methodology**: Category-Partition + Boundary Value Analysis (BVA) + Pairwise Combinatorial Testing + Real-World Workload Scenarios.
- **Verification Environment**: JVM Unit & Robolectric/Compose tests executed via `./gradlew testDebugUnitTest` and AndroidX test runner.

---

## Feature Inventory & Test Coverage Mapping
Total Features identified: 35

| # | Feature Area | Requirement Reference | Tier 1 (Coverage) | Tier 2 (Boundary) | Tier 3 (Pairwise) |
|---|--------------|----------------------|:-----------------:|:-----------------:|:-----------------:|
| 1 | Gradle & Architecture | ORIGINAL_REQUEST §R1 | ≥5 tests | ≥5 tests | ✓ |
| 2 | Room Database & DAOs | ORIGINAL_REQUEST §R1 | ≥5 tests | ≥5 tests | ✓ |
| 3 | Repositories & StateFlow | ORIGINAL_REQUEST §R1 | ≥5 tests | ≥5 tests | ✓ |
| 4 | Cold Boot Session Restore | ORIGINAL_REQUEST §R1 | ≥5 tests | ≥5 tests | ✓ |
| 5 | WebViewPool & Detachment | ORIGINAL_REQUEST §R3 | ≥5 tests | ≥5 tests | ✓ |
| 6 | Background Media Playback | ORIGINAL_REQUEST §R3 | ≥5 tests | ≥5 tests | ✓ |
| 7 | Snapshot VisualStateCallback | ORIGINAL_REQUEST §R4 | ≥5 tests | ≥5 tests | ✓ |
| 8 | Disk Snapshot Caching | ORIGINAL_REQUEST §R4 | ≥5 tests | ≥5 tests | ✓ |
| 9 | Ad/Tracker Host Ruleset | ORIGINAL_REQUEST §R6 | ≥5 tests | ≥5 tests | ✓ |
| 10| Fast HashSet Subdomain Matching | ORIGINAL_REQUEST §R6 | ≥5 tests | ≥5 tests | ✓ |
| 11| Ad Blocker Request Interceptor | ORIGINAL_REQUEST §R6 | ≥5 tests | ≥5 tests | ✓ |
| 12| Video Fullscreen & PiP | ORIGINAL_REQUEST §R6 | ≥5 tests | ≥5 tests | ✓ |
| 13| Back Navigation Hierarchy | ORIGINAL_REQUEST §R6 | ≥5 tests | ≥5 tests | ✓ |
| 14| Tier 1 Active Tab Pill | ORIGINAL_REQUEST §R2 | ≥5 tests | ≥5 tests | ✓ |
| 15| Tier 1 Inactive Tab Circles | ORIGINAL_REQUEST §R2 | ≥5 tests | ≥5 tests | ✓ |
| 16| Tier 1 Trailing Add Button | ORIGINAL_REQUEST §R2 | ≥5 tests | ≥5 tests | ✓ |
| 17| Tier 2 Action Bar Dock | ORIGINAL_REQUEST §R2 | ≥5 tests | ≥5 tests | ✓ |
| 18| Dynamic Auto-Hide Scroll | ORIGINAL_REQUEST §R2 | ≥5 tests | ≥5 tests | ✓ |
| 19| Search Modal Overlay (0.55 bias)| ORIGINAL_REQUEST §R5 | ≥5 tests | ≥5 tests | ✓ |
| 20| Search Soft Keyboard Autofocus | ORIGINAL_REQUEST §R5 | ≥5 tests | ≥5 tests | ✓ |
| 21| Upward Fading History Stack | ORIGINAL_REQUEST §R5 | ≥5 tests | ≥5 tests | ✓ |
| 22| Search Query Dispatch Engine | ORIGINAL_REQUEST §R5 | ≥5 tests | ≥5 tests | ✓ |
| 23| 3-Column Visual Tab Grid | ORIGINAL_REQUEST §R5 | ≥5 tests | ≥5 tests | ✓ |
| 24| Tab Grid Swipe-to-Dismiss | ORIGINAL_REQUEST §R5 | ≥5 tests | ≥5 tests | ✓ |
| 25| Home Recent Tabs Carousel (6 max)| ORIGINAL_REQUEST §R5 | ≥5 tests | ≥5 tests | ✓ |
| 26| Home Favorites Grid (<=6 + ShowAll)| ORIGINAL_REQUEST §R5 | ≥5 tests | ≥5 tests | ✓ |
| 27| Settings Sheet Options & Theme | ORIGINAL_REQUEST §R5 | ≥5 tests | ≥5 tests | ✓ |
| 28| Pull-to-Refresh Indicator | spec.md §5.3 | ≥5 tests | ≥5 tests | ✓ |

---

## Test Architecture
- **Test Runner**: `./gradlew testDebugUnitTest`
- **Pass/Fail Semantics**: All test suites must execute and pass with 0 failures, 0 errors.
- **Directory Layout**:
  - `app/src/test/java/com/jestk/browser/e2e/tier1/` — Feature coverage tests
  - `app/src/test/java/com/jestk/browser/e2e/tier2/` — Boundary and edge case tests
  - `app/src/test/java/com/jestk/browser/e2e/tier3/` — Pairwise and cross-feature interaction tests
  - `app/src/test/java/com/jestk/browser/e2e/tier4/` — Real-world workload and end-to-end user scenario tests

---

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Multi-Tab Browsing & Lifecycle Workflow | Tab Creation, Switching, WebView Detach/Attach, State Preservation, Tab Reordering | High |
| 2 | Cold Boot Restore with Disk-Cached Snapshots | Database Hydration, Snapshot Path Loading, Active Index Recovery, UI Grid Rendering | High |
| 3 | Ad Blocking & Tracker Interception with Settings Toggle | Asset Loading, HashSet Subdomain Lookup, Empty WebResourceResponse, Setting Toggle Pass-Through | Medium |
| 4 | Search & Navigation Flow | Search Overlay autofocus, Query Evaluation, DuckDuckGo URL routing, History Stack Update | Medium |
| 5 | Tab Grid Management & Swipe-to-Dismiss | Visual Grid 3-Column Layout, Swipe Gesture Tab Dismissal, Database Deletion, Active Tab Reassignment | High |
| 6 | Video Playback, Fullscreen & PiP Lifecycle | CustomView Fullscreen Presentation, Background Transition, System PiP Triggering, Audio Continuation | High |
| 7 | Bottom Bar Auto-Hide & Back Navigation Hierarchy | NestedScroll Dynamic Translation, Overlays Dismissal, History Back, Tab Close Priority | Medium |

---

## Coverage Thresholds
- **Tier 1 (Feature Coverage)**: ≥ 140 test cases (≥5 per major feature)
- **Tier 2 (Boundary & Corner Cases)**: ≥ 140 test cases (≥5 per boundary condition)
- **Tier 3 (Cross-Feature Combinations)**: ≥ 30 pairwise interaction test cases
- **Tier 4 (Real-World Scenarios)**: ≥ 7 comprehensive workflow scenario suites
- **Total Minimum Target**: ≥ 320 test assertions across all tiers
