#!/usr/bin/env python3
"""Builds the fallback host lists from the standard filter lists in tools/filterlists/lists.json.

Outputs: app/src/main/assets/adblock_domains.txt (host blocking, used until the native engine is
loaded) and app/src/main/assets/adblock_popup_hosts.txt (`||host^$popup` rules, see below).

The engine only does host matching, so only rules of the form `||host^` (optionally with
`$options`) are used. A rule is kept only when its meaning survives that simplification:

- no options                          -> block the host everywhere (line: `host`)
- `third-party` and/or resource types -> block the host only as a third-party resource
                                         (line: `host$third-party`)
- positive `domain=` (site-scoped), `popup` (collected separately), `~third-party`, `badfilter`, `app=`, or any
  modifier the engine cannot honour (`redirect=`, `csp=`, `removeparam`, ...) -> dropped
- an unconditional exception `@@||host^` removes the host from the output

Popup hosts: a `||host^$popup` rule whose only other option is `third-party` is written to
adblock_popup_hosts.txt (one host per line); popup rules with other options are dropped, and an
unconditional `@@||host^` exception removes the host.

Usage:
    python3 tools/build_adblock_list.py          # regenerate the asset
    python3 tools/build_adblock_list.py --check  # exit 1 if the asset is out of date
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LISTS = ROOT / "tools" / "filterlists"
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "adblock_domains.txt"
POPUP_OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "adblock_popup_hosts.txt"
MANIFEST = LISTS / "lists.json"
HOSTS_SOURCE = "extra_hosts.txt"


def abp_sources():
    """File names of the standard-preset ads-engine lists, in manifest order.

    The privacy lists stay out of the fallback to keep the asset small; the native engine covers them.
    EasyList Cookie (a Strict list for the native engine) stays in the fallback because its host rules
    for consent-management scripts have always been part of it.
    """
    lists = json.loads(MANIFEST.read_text(encoding="utf-8"))["lists"]
    return [
        f"{entry['id']}.txt"
        for entry in lists
        if (entry["preset"] == "standard" and "ads" in entry["engine"]) or entry["id"] == "easylist-cookie"
    ]

# Shared infrastructure and link services that must never be blocked as a whole host, even if a
# future list update adds an unconditional rule for them.
NEVER_BLOCK = {
    "amazonaws.com", "cloudfront.net", "googleapis.com", "gstatic.com", "jsdelivr.net",
    "cdn77.org", "files.wordpress.com", "imgur.com", "i.imgur.com", "t.co", "bit.ly",
    "graph.facebook.com", "facebook.com", "google.com", "youtube.com", "yandex.ru",
}

RESOURCE_TYPES = {
    "script", "image", "stylesheet", "subdocument", "xmlhttprequest", "xhr", "media", "font",
    "object", "ping", "websocket", "other", "webrtc", "css", "frame",
}
NEUTRAL_OPTIONS = {"important", "match-case"}
THIRD_PARTY = {"third-party", "3p"}

HOST_RE = re.compile(r"^[a-z0-9_-]+(\.[a-z0-9_-]+)+$")
RULE_RE = re.compile(r"^(@@)?\|\|([^\^/$*|]+)\^(?:\$(.*))?$")


def classify(options: str):
    """Returns 'global', 'third-party', or None when the rule must be dropped."""
    if not options:
        return "global"
    third_party = False
    for raw in options.split(","):
        opt = raw.strip().lower()
        if opt in NEUTRAL_OPTIONS:
            continue
        if opt in THIRD_PARTY:
            third_party = True
        elif opt in RESOURCE_TYPES:
            third_party = True  # a type-restricted rule is approximated as "only as a resource"
        elif opt.startswith("domain="):
            if any(not target.startswith("~") for target in opt[len("domain="):].split("|")):
                return None  # scoped to specific sites
            third_party = True
        else:
            return None  # popup, ~third-party, badfilter, app=, redirect=, csp=, ...
    return "third-party" if third_party else "global"


def is_popup_only(options: str) -> bool:
    """True for `popup` optionally combined with `third-party` and nothing else."""
    opts = [opt.strip().lower() for opt in options.split(",")] if options else []
    return "popup" in opts and all(opt == "popup" or opt in THIRD_PARTY for opt in opts)


def parse_abp(path: Path, global_hosts: set, third_party_hosts: set, exceptions: set, popup_hosts: set):
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        line = line.strip()
        if not line or line[0] in "![" or "#" in line:
            continue
        match = RULE_RE.match(line)
        if not match:
            continue
        is_exception, host, options = match.group(1), match.group(2).lower(), match.group(3) or ""
        if not HOST_RE.match(host):
            continue
        kind = classify(options)
        if not is_exception and is_popup_only(options):
            popup_hosts.add(host)
        if is_exception:
            if kind == "global":
                exceptions.add(host)
            continue
        if kind == "global":
            global_hosts.add(host)
        elif kind == "third-party":
            third_party_hosts.add(host)


def parse_hosts(path: Path, global_hosts: set):
    for line in path.read_text(encoding="utf-8").splitlines():
        tokens = line.split("#", 1)[0].split()
        if not tokens:
            continue
        host = tokens[1] if tokens[0] in ("0.0.0.0", "127.0.0.1") and len(tokens) > 1 else tokens[0]
        host = host.lower()
        if HOST_RE.match(host):
            global_hosts.add(host)


def build():
    """Returns (adblock_domains.txt content, adblock_popup_hosts.txt content)."""
    sources = abp_sources()
    global_hosts, third_party_hosts, exceptions, popup_hosts = set(), set(), set(), set()
    for name in sources:
        parse_abp(LISTS / name, global_hosts, third_party_hosts, exceptions, popup_hosts)
    parse_hosts(LISTS / HOSTS_SOURCE, global_hosts)
    excluded = exceptions | NEVER_BLOCK
    global_hosts -= excluded
    third_party_hosts -= excluded | global_hosts
    popup_hosts -= excluded
    lines = [
        "# Generated by tools/build_adblock_list.py - do not edit by hand.",
        f"# Sources: {', '.join(sources + [HOSTS_SOURCE])}",
        f"# Hosts blocked everywhere: {len(global_hosts)}; third-party only: {len(third_party_hosts)}",
    ]
    lines += sorted(global_hosts)
    lines += sorted(f"{host}$third-party" for host in third_party_hosts)
    popup_lines = [
        "# Generated by tools/build_adblock_list.py - do not edit by hand.",
        f"# Sources: {', '.join(sources)}",
        f"# Hosts whose `$popup` rule opens no window: {len(popup_hosts)}",
    ]
    popup_lines += sorted(popup_hosts)
    return "\n".join(lines) + "\n", "\n".join(popup_lines) + "\n"


def main() -> int:
    outputs = list(zip((OUTPUT, POPUP_OUTPUT), build()))
    if "--check" in sys.argv:
        status = 0
        for path, content in outputs:
            current = path.read_text(encoding="utf-8") if path.exists() else ""
            if current != content:
                print(f"{path.relative_to(ROOT)} is out of date; run tools/build_adblock_list.py")
                status = 1
        return status
    for path, content in outputs:
        path.write_text(content, encoding="utf-8")
        print(content.splitlines()[2])
    return 0


if __name__ == "__main__":
    sys.exit(main())
