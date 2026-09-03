## 2026-09-02T19:10:52Z
You are a Spec Miner for Jestk Browser for Android.
Your working directory is: /Users/ilya/CODING/browser/.agents/teamwork_preview_spec_miner_survey_2
Project root: /Users/ilya/CODING/browser

MANDATORY: Read the authoritative sources:
- /Users/ilya/CODING/browser/ORIGINAL_REQUEST.md
- /Users/ilya/CODING/browser/spec.md

Your focus is: **R2. Dual-Tier Bottom Navigation Bar & Dynamic Auto-Hide** AND **R5. Views & Overlays**.
Analyze and extract all precise requirements, technical specifications, UI components, and gestures for:
1. Dual-Tier Bottom Bar:
   - Tier 1: Horizontal Tab Strip (`LazyRow`):
     - Active Tab Pill: 50% (2/4) screen width, site favicon (left) + truncated title/domain (centered), single tap opens in-place Search Overlay pre-filled with URL, swipe left/right switches tabs.
     - Inactive Tabs: circular favicon buttons, single tap switches tab & scrolls into view, long-press contextual popup menu (`Close Tab`, `Copy URL`, `Add to Favorites`).
     - Trailing `+` button to create new blank tab.
   - Tier 2: Action Bar / Dock: Home, Search, All Tabs (Grid), Settings icon buttons with standard Material 3 icons.
   - Dynamic Auto-Hide Behavior: Smooth translation offset down on WebView scroll down, reveal on scroll up (via NestedScrollConnection or scroll offset state).
2. Views & Overlays:
   - Search Overlay: Slide-in modal positioned at vertical bias 0.55, autofocus soft keyboard with explicit `SoftwareKeyboardController.show()` in `LaunchedEffect(Unit)`, upward-scrolling fading history stack list (`LazyColumn`), DuckDuckGo default query template (`https://duckduckgo.com/?q={query}`).
   - All Tabs Overview (Visual Grid): Full-screen 3-column vertical grid, live bitmap snapshot cards with footer (favicon, title, close button), horizontal swipe-to-dismiss gesture to close tabs.
   - Home Screen: 6-card recent tabs horizontal carousel (`LazyRow`) at top with thumbnail + favicon + title; Favorites grid below (hard limit 6 items in default view, "Show All Favorites" button if >6 items).
   - Settings Sheet: Theme selector (`System Default`, `Light`, `Dark`), Search engine selector (`DuckDuckGo`, `Google`, `Bing`), Ad blocker toggle, Clickable links (`GitHub`, `Jestk.com`).
   - Pull-to-refresh & Back button handling (Overlays -> webView.goBack() -> close tab).

Write your exhaustive specification report to:
`/Users/ilya/CODING/browser/.agents/teamwork_preview_spec_miner_survey_2/survey_r2_r5.md`
And write your final handoff to:
`/Users/ilya/CODING/browser/.agents/teamwork_preview_spec_miner_survey_2/handoff.md`
Update your `progress.md` with timestamps as you work.
When done, message your parent with a concise completion notification citing the report path.
