---
name: site-fix
description: "Fix one website that misbehaves in Prosto: ads or popups that get through, a page broken by blocking, a cookie banner, or a site that fails only in Prosto (anti-bot challenge, missing feature). Reproduces on the phone, finds whether blocking, an injected script or WebView compatibility is the cause, fixes it the sanctioned way (usually a rule in tools/filterlists/prosto.txt), verifies and files the result. Use when the user names a site and a symptom."
argument-hint: "<url> \"<symptom>\""
---

# Site fix: one site, one symptom → verified fix

Input: a URL and a symptom. If the steps to reproduce are unclear (needs login? first tap only?
only in incognito?), ask once before starting: guessing costs more than the question.

## 1. Reproduce on the phone

```
rtk tools/device.sh wake          # fails when locked: ask the user to unlock
rtk tools/device.sh install       # only if the installed build is older than the tree
rtk tools/device.sh open '<url>'
rtk tools/device.sh visible       # the page on screen
rtk tools/device.sh eval '<url-prefix>' '<js>'   # DOM probe, prints JSON
```

Prefer `eval`, `ui`, `log` over `shot`. Never drive `com.jestk.browser` (the user's install).
For "the first tap opens a popup" use `tap <url-prefix> <selector>`: it is a real user gesture.

## 2. Find the cause (one of three)

1. **Blocking.** Menu → `tapui 'Disable blocking on this site'` → `tapui 'Disable'`. The page
   reloads. If the symptom flips, the cause is a filter rule or a missing one. Turn blocking back
   on afterwards (`tapui 'Enable blocking on this site'`).
2. **Injected script** (`assets/scripts/*.js`: autoconsent, content bootstrap, procedural,
   scriptlets). Suspect it when the page detects a bot, loops on a challenge, or breaks with
   blocking off too. Run the CDP fingerprint probe from `docs/specs/2026-11-codebase-review.md`
   §5 against Chrome on the same phone; see LESSONS.md #9.
3. **WebView compatibility** (UA / Client Hints, intents, downloads, fullscreen, media). Look up
   how DuckDuckGo handles it with the `duckduckgo-reference` skill before designing anything.

State the cause and its evidence to the user before fixing.

## 3. Fix

**Filter rule (most site fixes).** Add it to `tools/filterlists/prosto.txt` with a `!` comment
above it: the site, what the page does, and why the upstream lists miss it (see the existing
`mangadot.net` entry). uBO syntax works: `##selector`, `##+js(scriptlet, args)`, procedural
`:has-text()`, `||host^$popup`. Then:

```
python3 tools/update_filterlists.py --offline
python3 tools/build_adblock_list.py
```

Never edit `app/src/main/assets/filters/**`, `adblock_domains.txt`, `adblock_popup_hosts.txt`
or `assets/adblock/resources.json` by hand (a hook blocks it).

Hard rules:
- Never block a site on its own pages or the main-frame document.
- Never touch challenge domains (`challenges.cloudflare.com`, `cloudflarecaptcha.com`,
  `/cdn-cgi/challenge-platform`) or a page titled "Just a moment...".
- Injected scripts add no `window.*` names and replace no natives.

**Code fix** (cause 2 or 3): treat it as a normal task: a test first, and delegate to
`prosto-coder` if it is more than a few lines.

## 4. Verify

```
rtk tools/verify.sh --changed     # picks up the filter-list and content-script tests itself
rtk tools/device.sh install
rtk tools/device.sh open '<url>'  # the symptom is gone, and the page still works
rtk tools/device.sh sleep
```

Check that the page still works, not only that the ad or popup is gone: log in, scroll, and
play a video if the site has one.

## 5. Report and file

- Commit on a branch: `fix(adblock): <site>: <what>` (or `fix(compat): ...`).
- Linear (team PRO): close the issue if there is one, or file what is left. A rule that
  upstream lists should carry: say so in the issue, so the rule can be deleted from
  `prosto.txt` once uBlockOrigin/uAssets has it.
- Tell the user the cause, the fix and how it was checked on the phone, in 5 lines or fewer.
