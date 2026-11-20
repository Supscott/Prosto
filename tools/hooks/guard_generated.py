#!/usr/bin/env python3
"""PreToolUse hook: refuse Edit/Write on generated files and name the generator instead.

Claude Code passes the tool call as JSON on stdin. Exit code 2 blocks the call and shows stderr
to the model. The generators write these files themselves, so they are not affected.
"""

import json
import re
import sys

ASSETS = r"app/src/main/assets/"
FILTERS_HINT = (
    "edit tools/filterlists/prosto.txt (site fixes) or tools/filterlists/lists.json, "
    "then run `python3 tools/update_filterlists.py --offline`"
)
RULES = [
    (ASSETS + r"(adblock_domains|adblock_popup_hosts)\.txt$", FILTERS_HINT
     + " and `python3 tools/build_adblock_list.py`"),
    (ASSETS + r"filters/", FILTERS_HINT),
    (ASSETS + r"adblock/resources\.json$", "run `python3 tools/build_adblock_resources.py`"),
    (ASSETS + r"scripts/scriptlets\.js$", "run `node tools/build_scriptlets.mjs`"),
    (r"tools/filterlists/scriptlet_stubs\.json$", "run `node tools/build_scriptlets.mjs`"),
    (r"app/schemas/.+\.json$",
     "Room exports this schema at build time: change the entity/migration and build"),
]


def main():
    try:
        payload = json.load(sys.stdin)
    except ValueError:
        return 0
    path = (payload.get("tool_input") or {}).get("file_path") or ""
    path = path.replace("\\", "/")
    for pattern, hint in RULES:
        if re.search(r"(^|/)" + pattern, path):
            print(f"Blocked: {path} is a generated file, never edited by hand. "
                  f"Instead, {hint}.", file=sys.stderr)
            return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
