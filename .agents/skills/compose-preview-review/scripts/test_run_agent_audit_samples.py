#!/usr/bin/env python3
"""Unit tests for run-agent-audit-samples helpers.

Runs standalone (``python3 test_run_agent_audit_samples.py``) so the script
stays consumable as a curl-and-execute artifact for compose-ai-tools CI.
"""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path


SCRIPT = Path(__file__).with_name("run-agent-audit-samples.py")


def _load_module():
    spec = importlib.util.spec_from_file_location("run_agent_audit_samples", SCRIPT)
    assert spec and spec.loader, f"could not load spec for {SCRIPT}"
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_extract_findings_v2_present() -> None:
    module = _load_module()
    finding = {"level": "WARNING", "type": "SpeakableTextPresentCheck"}
    preview = {
        "id": "com.example.SaveToolbarPreview",
        "dataExtensions": {
            "a11y": {
                "schema": "compose-preview-a11y/v2",
                "payload": {"findings": [finding], "annotatedPath": "x.png"},
            }
        },
    }
    assert module.extract_a11y_findings(preview) == [finding]


def test_extract_findings_v2_empty_wins_over_v1() -> None:
    # A v2-aware CLI reporting zero findings must not silently fall through to
    # any stale v1 list that happens to coexist; that would mask regressions.
    module = _load_module()
    preview = {
        "dataExtensions": {"a11y": {"payload": {"findings": []}}},
        "a11yFindings": [{"level": "ERROR"}],
    }
    assert module.extract_a11y_findings(preview) == []


def test_extract_findings_v1_fallback() -> None:
    module = _load_module()
    finding = {"level": "ERROR", "type": "TouchTargetSizeCheck"}
    preview = {"id": "com.example.X", "a11yFindings": [finding]}
    assert module.extract_a11y_findings(preview) == [finding]


def test_extract_findings_absent() -> None:
    module = _load_module()
    assert module.extract_a11y_findings({"id": "com.example.X"}) == []


def main() -> int:
    tests = [
        test_extract_findings_v2_present,
        test_extract_findings_v2_empty_wins_over_v1,
        test_extract_findings_v1_fallback,
        test_extract_findings_absent,
    ]
    failed = 0
    for test in tests:
        try:
            test()
        except AssertionError as exc:
            failed += 1
            print(f"FAIL {test.__name__}: {exc}", file=sys.stderr)
        else:
            print(f"ok {test.__name__}")
    if failed:
        print(f"{failed} test(s) failed", file=sys.stderr)
        return 1
    print(f"{len(tests)} test(s) passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
