#!/usr/bin/env python3
"""Builds app/src/main/assets/adblock/resources.json: uBlock Origin's `$redirect` resources in
adblock-rust's `Resource` format.

Source: the pinned uBlock Origin release tarball (UBO_TAG, checked against UBO_SHA256). The
resource names, aliases and parameter lists come from `src/js/redirect-resources.js`; the file
contents come from `src/web_accessible_resources/`.

adblock-rust 0.13.5 serde shape (`src/resources/mod.rs`): a JSON array of
    {"name": str, "aliases": [str], "kind": <ResourceType>, "content": str}
where `kind` is `{"mime": "<mime>"}` for a static resource or the string `"template"` for a
scriptlet template, and `content` is standard base64 of the file bytes. `dependencies` and
`permission` are optional and omitted here. Known mimes: text/css, image/gif, text/html,
application/javascript, application/json, audio/mp3, video/mp4, image/png, text/plain, text/xml.

Skipped: entries that take parameters (`params`, e.g. click2load.html) and entries that need a
trusted list (`requiresTrust`).

CB-09 hook: `--scriptlet-stubs path.json` merges a JSON array of extra Resource objects (the
scriptlet templates) into the output, replacing entries with the same name. Pass the same flag on
every regeneration so the stubs stay in the committed file.

Usage:
    python3 tools/build_adblock_resources.py [--scriptlet-stubs stubs.json]   # download and write
    python3 tools/build_adblock_resources.py --check                          # offline shape check
"""

import base64
import hashlib
import io
import json
import re
import sys
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "adblock" / "resources.json"

UBO_TAG = "1.76.2"
UBO_URL = f"https://github.com/gorhill/uBlock/archive/refs/tags/{UBO_TAG}.tar.gz"
UBO_SHA256 = "10d9a1f2436dc9fd56468dafe607fbb714198dd5b68196df445c604abf7d8171"

MIME_BY_EXT = {
    "css": "text/css", "gif": "image/gif", "html": "text/html", "js": "application/javascript",
    "json": "application/json", "mp3": "audio/mp3", "mp4": "video/mp4", "png": "image/png",
    "txt": "text/plain", "xml": "text/xml",
}
REQUIRED_NAMES = {"googletagmanager_gtm.js", "noopjs", "1x1.gif", "noopframe"}
ENTRY_RE = re.compile(r"\[\s*'([^']+)'\s*,\s*\{(.*?)\}\s*\]\s*,", re.S)


def parse_resources_js(source: str) -> list:
    """Returns [{name, aliases, params, requires_trust}] from redirect-resources.js."""
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    source = re.sub(r"(?m)^\s*//.*$", "", source)
    entries = []
    for name, body in ENTRY_RE.findall(source):
        alias_match = re.search(r"alias:\s*(\[[^\]]*\]|'[^']*')", body)
        aliases = re.findall(r"'([^']+)'", alias_match.group(1)) if alias_match else []
        entries.append({
            "name": name,
            "aliases": aliases,
            "params": "params:" in body,
            "requires_trust": "requiresTrust: true" in body,
        })
    return entries


def mime_for(name: str) -> str:
    ext = name.rsplit(".", 1)[-1] if "." in name else ""
    return MIME_BY_EXT.get(ext, "text/plain")


def download_tarball() -> bytes:
    request = urllib.request.Request(UBO_URL, headers={"User-Agent": "ProstoResourceBuilder/1.0"})
    with urllib.request.urlopen(request, timeout=180) as response:
        data = response.read()
    digest = hashlib.sha256(data).hexdigest()
    if digest != UBO_SHA256:
        raise SystemExit(f"uBlock Origin {UBO_TAG} tarball sha256 mismatch: {digest}")
    return data


def build(stubs: list) -> list:
    resources = {}
    with tarfile.open(fileobj=io.BytesIO(download_tarball()), mode="r:gz") as tar:
        members = {m.name.split("/", 1)[1]: m for m in tar.getmembers() if m.isfile() and "/" in m.name}
        js_source = tar.extractfile(members["src/js/redirect-resources.js"]).read().decode("utf-8")
        for entry in parse_resources_js(js_source):
            if entry["params"] or entry["requires_trust"]:
                continue
            member = members.get(f"src/web_accessible_resources/{entry['name']}")
            if member is None:
                raise SystemExit(f"resource file missing in tarball: {entry['name']}")
            content = base64.b64encode(tar.extractfile(member).read()).decode("ascii")
            resources[entry["name"]] = {
                "name": entry["name"],
                "aliases": entry["aliases"],
                "kind": {"mime": mime_for(entry["name"])},
                "content": content,
            }
    for stub in stubs:
        resources[stub["name"]] = stub
    return list(resources.values())


def validate(resources) -> list:
    errors = []
    if not isinstance(resources, list) or not resources:
        return ["resources.json must be a non-empty JSON array"]
    seen = set()
    for item in resources:
        name = item.get("name") if isinstance(item, dict) else None
        if not isinstance(name, str) or not name:
            errors.append(f"entry without a name: {str(item)[:60]}")
            continue
        seen.add(name)
        seen.update(item.get("aliases", []))
        kind = item.get("kind")
        if not (kind == "template" or (isinstance(kind, dict) and isinstance(kind.get("mime"), str))):
            errors.append(f"{name}: kind must be {{\"mime\": ...}} or \"template\"")
        if not isinstance(item.get("aliases"), list):
            errors.append(f"{name}: aliases must be a list")
        try:
            base64.b64decode(item.get("content", ""), validate=True)
        except ValueError:
            errors.append(f"{name}: content is not valid base64")
    for required in sorted(REQUIRED_NAMES - seen):
        errors.append(f"required resource missing: {required}")
    return errors


def main() -> int:
    if "--check" in sys.argv:
        if not OUTPUT.exists():
            print(f"{OUTPUT.relative_to(ROOT)} is missing; run tools/build_adblock_resources.py")
            return 1
        errors = validate(json.loads(OUTPUT.read_text(encoding="utf-8")))
        for error in errors:
            print(f"{OUTPUT.relative_to(ROOT)}: {error}")
        return 1 if errors else 0
    stubs = []
    if "--scriptlet-stubs" in sys.argv:
        stubs = json.loads(Path(sys.argv[sys.argv.index("--scriptlet-stubs") + 1]).read_text(encoding="utf-8"))
    resources = build(stubs)
    errors = validate(resources)
    if errors:
        print("\n".join(errors))
        return 1
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(resources, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(resources)} resources from uBlock Origin {UBO_TAG} -> {OUTPUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
