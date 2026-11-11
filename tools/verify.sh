#!/usr/bin/env bash
set -euo pipefail

# Fast verification script for Prosto Browser
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> Checking architectural invariants..."
python3 tools/check_invariants.py

echo "==> Checking that the ad-block list matches tools/filterlists..."
python3 tools/build_adblock_list.py --check

# Gradle output is long; the summary below lists totals and failures, so keep only its tail.
started=$(date +%s)
status=0
# pipefail makes the function return the Gradle status, not tail's.
run_tests() { ./gradlew -q testDebugUnitTest "$@" 2>&1 | tail -40; }

if [[ "${1:-}" == "--all" ]]; then
    echo "==> Running full test suite (testDebugUnitTest)..."
    run_tests || status=$?
elif [[ "${1:-}" == "--ui" ]]; then
    echo "==> Running UI & preview tests (BottomBar, Dock, Dialogs)..."
    run_tests \
        --tests "com.jestk.browser.ui.navigation.BottomBarPreviewTest" \
        --tests "com.jestk.browser.ui.navigation.DockRefinementsTest" \
        --tests "com.jestk.browser.ui.navigation.TabContextMenuTest" || status=$?
else
    echo "==> Running core coordination & engine unit tests..."
    run_tests \
        --tests "com.jestk.browser.ui.viewmodel.TabCoordinatorTest" \
        --tests "com.jestk.browser.ui.viewmodel.BrowserViewModelTest" \
        --tests "com.jestk.browser.engine.JestkWebChromeClientTest" || status=$?
fi

python3 tools/test_summary.py "$started"
if [[ $status -ne 0 ]]; then
    echo "==> Tests FAILED"
    exit "$status"
fi
echo "==> All checks passed!"
