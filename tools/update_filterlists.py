#!/usr/bin/env python3
"""Downloads the filter lists named in tools/filterlists/lists.json and ships them as assets.

Each list is fetched over HTTPS, preprocessed (uBlock Origin `!#include` and `!#if` directives
are resolved, because adblock-rust treats them as comments), written to
tools/filterlists/<id>.txt, and copied to
app/src/main/assets/filters/standard/<engine>/<id>.txt for every engine it feeds (Standard
preset only: Strict lists are downloaded at runtime and never ship in the APK).

Preprocessing environment (what a Chromium-based Android WebView can do): `env_chromium`,
`env_mobile` and `ext_ublock` are true; `env_firefox`, `env_safari`, `env_mv3`, `ext_ubol`, `ext_abp`,
`cap_html_filtering` and `cap_ipaddress` are false.

Usage:
    python3 tools/update_filterlists.py          # download, update sha256, refresh assets
    python3 tools/update_filterlists.py --check  # offline: assets match tools/filterlists and sha256
"""

import hashlib
import json
import re
import shutil
import sys
import urllib.request
from pathlib import Path
from urllib.parse import urljoin

ROOT = Path(__file__).resolve().parent.parent
LISTS_DIR = ROOT / "tools" / "filterlists"
MANIFEST = LISTS_DIR / "lists.json"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "filters"
CATALOG = ROOT / "app" / "src" / "main" / "assets" / "adblock" / "lists.json"

TRUE_FLAGS = {"env_chromium", "env_mobile", "ext_ublock"}
KNOWN_FLAGS = TRUE_FLAGS | {
    "env_firefox", "env_safari", "env_mv3", "ext_ubol", "ext_abp", "cap_html_filtering", "cap_ipaddress",
}
USER_AGENT = "ProstoFilterListUpdater/1.0"


def load_manifest() -> list:
    return json.loads(MANIFEST.read_text(encoding="utf-8"))["lists"]


def save_manifest(lists: list) -> None:
    MANIFEST.write_text(json.dumps({"lists": lists}, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def fetch(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=180) as response:
        return response.read().decode("utf-8")


def evaluate(condition: str) -> bool:
    condition = condition.strip()
    negate = condition.startswith("!")
    flag = condition.lstrip("!")
    if flag not in KNOWN_FLAGS:
        raise ValueError(f"unknown preprocessor flag: {condition!r}")
    return (flag in TRUE_FLAGS) != negate


def preprocess(text: str, base_url: str, depth: int = 0) -> str:
    """Resolves `!#include` (relative to base_url) and `!#if/!#else/!#endif` blocks."""
    if depth > 5:
        raise ValueError("include nesting too deep")
    out = []
    stack = []  # each frame: [parent_active, branch_active]
    for line in text.splitlines():
        stripped = line.strip()
        active = all(frame[1] for frame in stack)
        if stripped.startswith("!#if "):
            stack.append([active, evaluate(stripped[5:])])
        elif stripped == "!#else":
            stack[-1][1] = not stack[-1][1]
        elif stripped == "!#endif":
            stack.pop()
        elif not active:
            continue
        elif stripped.startswith("!#include "):
            target = urljoin(base_url, stripped[len("!#include "):].strip())
            out.append(preprocess(fetch(target), target, depth + 1).rstrip("\n"))
        else:
            out.append(line)
    if stack:
        raise ValueError("unbalanced !#if")
    return "\n".join(out) + "\n"


def catalog_text(lists: list) -> str:
    """The metadata the runtime updater needs (CB-13): id, engines, preset and URL of every list."""
    entries = [{k: e[k] for k in ("id", "engine", "preset", "url")} for e in lists]
    return json.dumps({"lists": entries}, indent=2, ensure_ascii=False) + "\n"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def asset_paths(entry: dict):
    # Only the Standard preset ships in the APK; Strict lists are downloaded at runtime when the
    # user picks Strict (CB-13), so they stay in tools/filterlists only.
    if entry["preset"] != "standard":
        return
    for engine in entry["engine"]:
        yield ASSETS / entry["preset"] / engine / f"{entry['id']}.txt"


def update() -> int:
    lists = load_manifest()
    for entry in lists:
        print(f"fetching {entry['id']} <- {entry['url']}")
        text = preprocess(fetch(entry["url"]), entry["url"])
        head = text.lstrip()[:200]
        if "<html" in head.lower() or "<!doctype" in head.lower() or len(text) < 1000:
            print(f"  {entry['id']}: response does not look like a filter list")
            return 1
        (LISTS_DIR / f"{entry['id']}.txt").write_text(text, encoding="utf-8")
    shutil.rmtree(ASSETS, ignore_errors=True)
    for entry in lists:
        source = LISTS_DIR / f"{entry['id']}.txt"
        entry["sha256"] = sha256(source)
        for target in asset_paths(entry):
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
    save_manifest(lists)
    CATALOG.parent.mkdir(parents=True, exist_ok=True)
    CATALOG.write_text(catalog_text(lists), encoding="utf-8")
    return 0


def check() -> int:
    errors = []
    expected_assets = set()
    for entry in load_manifest():
        source = LISTS_DIR / f"{entry['id']}.txt"
        if not source.exists():
            errors.append(f"missing tools/filterlists/{source.name}")
            continue
        if sha256(source) != entry.get("sha256"):
            errors.append(f"{source.name}: sha256 differs from lists.json")
        if not entry["url"].startswith("https://"):
            errors.append(f"{entry['id']}: url is not HTTPS")
        for target in asset_paths(entry):
            expected_assets.add(target)
            if not target.exists() or target.read_bytes() != source.read_bytes():
                errors.append(f"{target.relative_to(ROOT)} does not match {source.name}")
    if not CATALOG.exists() or CATALOG.read_text(encoding="utf-8") != catalog_text(load_manifest()):
        errors.append(f"{CATALOG.relative_to(ROOT)} does not match lists.json")
    for existing in ASSETS.rglob("*.txt"):
        if existing not in expected_assets:
            errors.append(f"{existing.relative_to(ROOT)} is not listed in lists.json")
    for error in errors:
        print(error)
    if errors:
        print("run tools/update_filterlists.py")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(check() if "--check" in sys.argv else update())
