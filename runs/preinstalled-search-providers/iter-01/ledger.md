# Iteration 01 - preinstalled-search-providers

- **Date:** 2026-10-20T12:05:19+00:00
- **Base ref:** `n/a`
- **Hypothesis:** Add Google, DuckDuckGo, Brave, Kagi, Ecosia, Startpage as preinstalled search engines, remove Bing, remove preinstalled SVG logos, and download icons after first start

## Probes

| Probe | Change tried | Expected effect |
|---|---|---|
| A | Replace preinstalled SVG assets with runtime download via `SearchEngineIconDownloader` and configure Google, DuckDuckGo, Brave, Kagi, Ecosia, Startpage while removing Bing | Built-in providers configured without bundled SVG assets; icons downloaded to local storage on first launch; all 546 unit and visual tests pass |

## Telemetry

| Check | Status | Detail |
|---|---|---|
| invariants | PASS | `python3 tools/check_invariants.py` -> exit 0 |
| tests | PASS | `./gradlew :app:testDebugUnitTest` -> exit 0 |
| capture | PASS | `python3 tools/capture.py` -> exit 0 |
| visual | PASS | largest changed cluster 0.00% of the frame, 0.00% blocks differ, mean delta 0.0 (stable) |
| memory | PASS | `python3 tools/harness_reflect.py audit` -> exit 0 |

## Verdict

- **Adopted:** Preinstalled search providers list (Google, DuckDuckGo, Brave, Kagi, Ecosia, Startpage) with respective shortcuts (`g`, `d`, `b`, `k`, `e`, `s`); removed Bing; removed bundled SVG assets from `app/src/main/assets/icons/`; background non-blocking icon downloader caching to `search_icons/` on initial start.
- **Rejected:** Blocking network icon download inside `ensureDefaultEnginesSeeded()` (must run in dedicated coroutine scope to avoid blocking session restore and tab navigation in offline/unit test environments).
- **Lesson candidate:** Background asset downloaders must run in separate unblocking coroutine scopes rather than synchronously in startup initialization pathways.
- **Guardrail update:** N/A (existing coroutine hygiene and web engine contracts in `AGENTS.md` and `LESSONS.md` remain valid).
