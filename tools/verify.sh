#!/usr/bin/env bash
set -euo pipefail

# Fast verification script for Prosto Browser
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> Checking architectural invariants..."
python3 tools/check_invariants.py

if [[ "${1:-}" == "--all" ]]; then
    echo "==> Running full test suite (testDebugUnitTest)..."
    ./gradlew testDebugUnitTest
else
    echo "==> Running core coordination & engine unit tests..."
    ./gradlew testDebugUnitTest \
        --tests "com.jestk.browser.ui.viewmodel.TabCoordinatorTest" \
        --tests "com.jestk.browser.ui.viewmodel.BrowserViewModelTest" \
        --tests "com.jestk.browser.engine.JestkWebChromeClientTest"
fi

echo "==> All checks passed!"
