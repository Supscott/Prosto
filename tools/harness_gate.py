#!/usr/bin/env python3
"""Config-driven Harness^2 iteration gate (portable, stdlib only).

Runs the Definition of Done of a project as machine checks described in
`harness.config.json`, records the telemetry of the iteration, and opens a
ledger entry for the agent to reflect on.

    python3 tools/harness_gate.py --feature my-feature --hypothesis "what changes"

Step types (see references/stack-recipes.md):

    {"name": "tests",  "type": "command", "cmd": ["pytest", "-q"]}
    {"name": "shot",   "type": "command", "cmd": ["node", "tools/shot.js"],
     "artifacts": ["runs/shots/app.png"]}
    {"name": "visual", "type": "visual", "baseline": "runs/baselines/app.png",
     "current": "runs/shots/app.png", "stable": 0.08, "major": 0.2}

Artifacts:
    runs/<feature>/iter-NN/telemetry.json   machine readable results
    runs/<feature>/iter-NN/ledger.md         hypothesis / probes / verdict template

Exit code is 1 when any step FAILs.
"""

import argparse
import importlib.util
import json
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

_PNGDIFF_SPEC = importlib.util.spec_from_file_location(
    "pngdiff", Path(__file__).resolve().parent / "pngdiff.py"
)
assert _PNGDIFF_SPEC is not None and _PNGDIFF_SPEC.loader is not None
pngdiff = importlib.util.module_from_spec(_PNGDIFF_SPEC)
_PNGDIFF_SPEC.loader.exec_module(pngdiff)

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CONFIG = "harness.config.json"


def to_int(text, default=0):
    try:
        return int(text)
    except (TypeError, ValueError):
        return default


def load_config(path):
    if not path.exists():
        print(f"Error: config {path} not found. Start from harness.config.example.json")
        sys.exit(2)
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        print(f"Error: cannot read {path}: {exc}")
        sys.exit(2)


def tail(text, lines=20):
    return "\n".join(text.strip().splitlines()[-lines:])


def check(name, status, detail, **extra):
    entry = {"check": name, "status": status, "detail": detail}
    entry.update(extra)
    return entry


def run_command(step, root, timeout):
    cmd = step["cmd"]
    cwd = root / step.get("cwd", ".")
    try:
        proc = subprocess.run(
            cmd, cwd=cwd, capture_output=True, text=True, timeout=timeout
        )
        output = proc.stdout + proc.stderr
        code = proc.returncode
    except FileNotFoundError as exc:
        return check(step["name"], "FAIL", f"command not found: {exc}"), None
    except subprocess.TimeoutExpired:
        return check(step["name"], "FAIL", f"timed out after {timeout}s"), None

    detail = f"`{' '.join(cmd)}` -> exit {code}"
    status = "PASS" if code == 0 else "FAIL"
    if code != 0:
        detail += f"\n{tail(output)}"
    result = check(step["name"], status, detail, command=cmd, exit_code=code)

    missing = [
        a
        for a in step.get("artifacts", [])
        if not (root / a).exists() or (root / a).stat().st_size == 0
    ]
    if missing:
        result = check(
            step["name"],
            "FAIL",
            f"artifacts missing or empty: {', '.join(missing)}",
            command=cmd,
            exit_code=code,
        )
    return result, output


def run_visual(step, root, update_baseline):
    baseline = root / step["baseline"]
    current = root / step["current"]
    if not current.exists():
        return check(step["name"], "FAIL", f"no current image at {step['current']}")
    if update_baseline:
        baseline.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(current, baseline)
        return check(step["name"], "PASS", f"baseline updated -> {step['baseline']}")
    if not baseline.exists():
        return check(
            step["name"],
            "INFO",
            "no baseline yet - run with --update-baseline to accept this frame",
        )

    metrics = pngdiff.compare(
        baseline,
        current,
        threshold=step.get("threshold", 8),
        target_w=step.get("target_w", 160),
    )
    if metrics.get("status") != "compared":
        return check(step["name"], "WARN", f"size mismatch: {metrics}")

    cluster = metrics.get("largest_cluster_ratio", metrics["changed_ratio"])
    stable = step.get("stable", 0.08)
    major = step.get("major", 0.2)
    if cluster <= stable:
        label, status = "stable", "PASS"
    elif cluster <= major:
        label, status = "changed", "WARN"
    else:
        label, status = "major change", "WARN"

    diff_path = current.with_suffix(".diff.png")
    try:
        pngdiff.diff_image(
            baseline,
            current,
            diff_path,
            threshold=step.get("threshold", 8),
            target_w=step.get("target_w", 160),
        )
        diff_rel = str(diff_path.relative_to(root))
    except (OSError, ValueError):
        diff_rel = ""

    return check(
        step["name"],
        status,
        f"largest changed cluster {cluster * 100:.2f}% of the frame, "
        f"{metrics['changed_ratio'] * 100:.2f}% blocks differ, "
        f"mean delta {metrics['mean_abs_delta']:.1f} ({label})",
        changed_ratio=metrics["changed_ratio"],
        largest_cluster_ratio=cluster,
        cluster_count=metrics.get("cluster_count", 0),
        mean_abs_delta=metrics["mean_abs_delta"],
        diff_image=diff_rel,
    )


def next_iteration(runs_dir, feature):
    feature_dir = runs_dir / feature
    if not feature_dir.exists():
        return 1
    numbers = [
        to_int(p.name.split("-", 1)[1])
        for p in feature_dir.glob("iter-*")
        if p.is_dir() and p.name.split("-", 1)[1].isdigit()
    ]
    return max(numbers, default=0) + 1


def write_artifacts(runs_dir, feature, iteration, results, hypothesis, base):
    out_dir = runs_dir / feature / f"iter-{iteration:02d}"
    out_dir.mkdir(parents=True, exist_ok=True)

    payload = {
        "feature": feature,
        "iteration": iteration,
        "timestamp": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "base_ref": base,
        "hypothesis": hypothesis,
        "checks": results,
        "verdict": "FAIL" if any(r["status"] == "FAIL" for r in results) else "PASS",
    }
    (out_dir / "telemetry.json").write_text(json.dumps(payload, indent=2) + "\n")

    rows = "\n".join(
        f"| {r['check']} | {r['status']} | {r['detail'].replace('|', '/')} |"
        for r in results
    )
    ledger = f"""# Iteration {iteration:02d} - {feature}

- **Date:** {payload['timestamp']}
- **Base ref:** `{base}`
- **Hypothesis:** {hypothesis or "_TODO: state what this iteration tries to change_"}

## Probes

| Probe | Change tried | Expected effect |
|-------|--------------|-----------------|
| A | | |

## Telemetry

| Check | Status | Detail |
|-------|--------|--------|
{rows}

## Verdict

- **Adopted:** _TODO_ (what from this iteration is kept)
- **Rejected:** _TODO_ (what was tried and dropped, with evidence)
- **Lesson candidate:** _TODO_ (a gotcha worth remembering - where does it go?)
- **Guardrail update:** _TODO_ (a rule for future work - where does it go?)
"""
    (out_dir / "ledger.md").write_text(ledger)
    return out_dir


def main():
    ap = argparse.ArgumentParser(description="Harness^2 iteration gate")
    ap.add_argument("--config", default=DEFAULT_CONFIG)
    ap.add_argument("--feature", default="adhoc", help="feature slug for runs/<feature>/")
    ap.add_argument("--hypothesis", default="", help="what this iteration tries to change")
    ap.add_argument("--base", default="", help="reference recorded in the ledger")
    ap.add_argument("--skip", default="", help="comma separated step names to skip")
    ap.add_argument("--only", default="", help="comma separated step names to run")
    ap.add_argument("--update-baseline", action="store_true")
    ap.add_argument("--timeout", type=int, default=1800, help="per command step, seconds")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    config_path = Path(args.config)
    if not config_path.is_absolute():
        config_path = ROOT / config_path
    config = load_config(config_path)

    runs_dir = ROOT / config.get("runs_dir", "runs")
    steps = config.get("steps", [])
    skip = {s.strip() for s in args.skip.split(",") if s.strip()}
    only = {s.strip() for s in args.only.split(",") if s.strip()}

    feature = re.sub(r"[^a-z0-9._-]+", "-", args.feature.strip().lower()) or "adhoc"
    iteration = next_iteration(runs_dir, feature)

    results = []
    for step in steps:
        name = step.get("name", step.get("type", "step"))
        if name in skip or (only and name not in only):
            results.append(check(name, "SKIP", "not selected"))
            continue
        if step.get("type") == "visual":
            results.append(run_visual(step, ROOT, args.update_baseline))
        else:
            result, _ = run_command(step, ROOT, args.timeout)
            results.append(result)

    out_dir = write_artifacts(
        runs_dir, feature, iteration, results, args.hypothesis, args.base or "n/a"
    )

    failures = [r for r in results if r["status"] == "FAIL"]
    warnings = [r for r in results if r["status"] == "WARN"]

    if args.json:
        print(json.dumps(results, indent=2))
    else:
        print()
        print(f"Harness gate - {feature} iter-{iteration:02d}")
        print("-" * 78)
        for r in results:
            print(f"  {r['status']:<5} {r['check']:<26} {r['detail']}")
        print("-" * 78)
        try:
            rel = out_dir.relative_to(ROOT)
        except ValueError:
            rel = out_dir
        print(f"  artifacts: {rel}")
        print(
            f"  verdict:   {'FAIL' if failures else 'PASS'}"
            f"  ({len(failures)} failing, {len(warnings)} warnings)"
        )
        print()

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
