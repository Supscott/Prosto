#!/usr/bin/env python3
"""Token cost of subagent runs, read from their JSONL transcripts.

Usage: python3 tools/agent_cost.py <transcript.jsonl> [...]

A background agent's transcript is the `output_file` its launch result names. For each file
this prints the model, the API turns, the raw token counts and one weighted figure in
"input-token units": input + 0.1 x cache reads + 1.25 x cache writes + 5 x output, which
follows the API price ratios within one model. Compare runs of the same model with it;
across models multiply by the model's input price.

Every turn re-reads the whole context from the cache, so cache reads usually dominate:
fewer turns (parallel tool calls) and a smaller context (a fresh agent instead of
continuing one with a long history) are what lower the cost.
"""
import json
import sys
from collections import Counter


def summarize(path):
    seen, tools, models = set(), Counter(), Counter()
    tok = Counter()
    with open(path) as f:
        for line in f:
            try:
                msg = json.loads(line).get("message") or {}
            except ValueError:
                continue
            usage, mid = msg.get("usage"), msg.get("id")
            if usage and mid not in seen:
                seen.add(mid)
                models[msg.get("model", "?")] += 1
                tok["in"] += usage.get("input_tokens", 0)
                tok["out"] += usage.get("output_tokens", 0)
                tok["cache_read"] += usage.get("cache_read_input_tokens", 0)
                tok["cache_write"] += usage.get("cache_creation_input_tokens", 0)
            content = msg.get("content")
            if isinstance(content, list):
                for c in content:
                    if isinstance(c, dict) and c.get("type") == "tool_use":
                        tools[c["name"]] += 1
    units = tok["in"] + 0.1 * tok["cache_read"] + 1.25 * tok["cache_write"] + 5 * tok["out"]
    model = models.most_common(1)[0][0] if models else "?"
    top = ", ".join(f"{n}:{c}" for n, c in tools.most_common(5))
    print(f"{path.rsplit('/', 1)[-1][:16]:16} {model:24} turns={len(seen):3} "
          f"out={tok['out']:>7} cache_read={tok['cache_read']:>9} cache_write={tok['cache_write']:>7} "
          f"units={units / 1000:7.0f}k  tools[{top}]")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    for p in sys.argv[1:]:
        summarize(p)
