#!/usr/bin/env bash
set -euo pipefail

# Fast verification script for Prosto Browser
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> Checking architectural invariants..."
python3 tools/check_invariants.py

echo "==> Checking that the ad-block list matches tools/filterlists..."
python3 tools/build_adblock_list.py --check

if [[ "${1:-}" == "--all" ]]; then
    echo "==> Running full test suite (testDebugUnitTest)..."
    ./gradlew testDebugUnitTest
elif [[ "${1:-}" == "--ui" ]]; then
    echo "==> Running UI & preview tests (BottomBar, Dock, Dialogs)..."
    ./gradlew testDebugUnitTest \
        --tests "com.jestk.browser.ui.navigation.BottomBarPreviewTest" \
        --tests "com.jestk.browser.ui.navigation.DockRefinementsTest" \
        --tests "com.jestk.browser.ui.navigation.TabContextMenuTest"
else
    echo "==> Running core coordination & engine unit tests..."
    ./gradlew testDebugUnitTest \
        --tests "com.jestk.browser.ui.viewmodel.TabCoordinatorTest" \
        --tests "com.jestk.browser.ui.viewmodel.BrowserViewModelTest" \
        --tests "com.jestk.browser.engine.JestkWebChromeClientTest"
fi

echo "==> All checks passed!"
