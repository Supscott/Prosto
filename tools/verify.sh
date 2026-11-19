#!/usr/bin/env bash
set -euo pipefail

# Fast verification script for Prosto Browser
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Test classes added or edited on this branch (vs main) or in the working tree.
changed_paths() {
    local base
    base="$(git merge-base HEAD main 2>/dev/null || echo HEAD)"
    { git diff --name-only "$base"; git ls-files --others --exclude-standard; } | sort -u
}

# Extra rules for --changed: a change to these paths can break tests that never mention the
# file (adding assets/filters/standard/ads/prosto.txt broke EngineStoreSelectionTest), so the
# tests they cover run too. One rule per line: "path glob|test group|label".
EXTRA_TEST_RULES=(
    "tools/filterlists/*|adblock|ad-block filter list sources"
    "app/src/main/assets/filters/*|adblock|bundled filter lists"
    "app/src/main/assets/adblock/*|adblock|ad-block resources"
    "app/src/main/assets/adblock_*.txt|adblock|ad-block host lists"
    "app/src/main/assets/scripts/*|scripts|bundled content scripts"
)

# The Test.kt files of a test group. Helpers without "Test" in the name are not classes.
extra_test_files() {
    case "$1" in
        adblock) find app/src/test -name '*Test.kt' -path '*/engine/adblock/*' || true ;;
        scripts) grep -rl --include='*Test.kt' 'scripts/' app/src/test || true ;;
    esac
}

# Prints the test file paths --changed runs. One note per rule hit goes to stderr.
test_file_paths() {
    local paths rule glob rest group label hits f tests names
    paths="$(changed_paths)"
    printf '%s\n' "$paths" | grep -E '^app/src/test/java/.*Test\.kt$' || true
    # Unedited tests of edited classes (tools/related_tests.py notes each hit on stderr).
    printf '%s\n' "$paths" | python3 tools/related_tests.py
    for rule in "${EXTRA_TEST_RULES[@]}"; do
        glob="${rule%%|*}"
        rest="${rule#*|}"
        group="${rest%%|*}"
        label="${rest#*|}"
        hits=""
        while IFS= read -r f; do
            # shellcheck disable=SC2254
            case "$f" in
                $glob) hits="${hits:+$hits }$f" ;;
            esac
        done <<<"$paths"
        [[ -n "$hits" ]] || continue
        tests="$(extra_test_files "$group" | grep -E '^app/src/test/java/.*Test\.kt$' || true)"
        names="$(printf '%s\n' "$tests" | sed -e 's|.*/||' -e 's|\.kt$||' | paste -sd, -)"
        echo "extra tests: $label changed ($hits) -> added $names" >&2
        printf '%s\n' "$tests"
    done
}

# Gradle --tests pairs ("--tests" and the class name on separate lines) for --changed.
changed_test_args() {
    local f cls
    test_file_paths | sort -u | while IFS= read -r f; do
        [[ -f "$f" ]] || continue
        cls="${f#app/src/test/java/}"
        cls="${cls%.kt}"
        printf -- '--tests\n%s\n' "${cls//\//.}"
    done
}

CORE_TESTS=(
    --tests "com.jestk.browser.ui.viewmodel.TabCoordinatorTest"
    --tests "com.jestk.browser.ui.viewmodel.BrowserViewModelTest"
    --tests "com.jestk.browser.engine.JestkWebChromeClientTest"
)

# Prints the --tests filter that --changed would run, and exits without running anything.
if [[ "${1:-}" == --changed && "${2:-}" == --dry-run ]]; then
    dry_list="$(changed_test_args | paste - - | sed 's/\t/ /')"
    if [[ -z "$dry_list" ]]; then
        echo "(no changed test classes: --changed would run the core set)"
    else
        echo "$dry_list"
    fi
    exit 0
fi

# shellcheck source=tools/ensure_worktree_files.sh
source "$ROOT/tools/ensure_worktree_files.sh"
ensure_worktree_files "$ROOT"

echo "==> Checking architectural invariants..."
python3 tools/check_invariants.py

echo "==> Checking that the ad-block list matches tools/filterlists..."
python3 tools/build_adblock_list.py --check

echo "==> Checking that the bundled filter lists match tools/filterlists/lists.json..."
python3 tools/update_filterlists.py --check

echo "==> Checking the ad-block redirect resources..."
python3 tools/build_adblock_resources.py --check

echo "==> Self-testing the ad-block fixture server..."
python3 tools/fixture_server.py --self-test 2>/dev/null

if command -v node >/dev/null 2>&1; then
    echo "==> Checking the uBO scriptlet bundle..."
    node tools/build_scriptlets.mjs --check
    node_out=$(node --test tools/build_scriptlets.test.mjs tools/procedural_runner.test.mjs tools/content_bootstrap_blob.test.mjs 2>&1) || { echo "$node_out"; exit 1; }
    echo "$node_out" | grep -E "^# (tests|pass|fail)"
else
    echo "==> node not found: skipping the scriptlet bundle check"
fi

# Gradle output is long; the summary below lists totals and failures, so keep only its tail.
started=$(date +%s)
status=0
# pipefail makes the function return the Gradle status, not tail's.
# LOW_MEM=1 compiles in-process when the Kotlin daemon runs out of memory (parallel worktrees).
LOW_MEM_ARGS=()
if [[ "${LOW_MEM:-}" == "1" ]]; then
  LOW_MEM_ARGS=(-Pkotlin.compiler.execution.strategy=in-process -Pkotlin.daemon.jvmargs=-Xmx3g --max-workers=2)
fi
run_tests() { ./gradlew -q testDebugUnitTest ${LOW_MEM_ARGS[@]+"${LOW_MEM_ARGS[@]}"} "$@" 2>&1 | tail -40; }

case "${1:-}" in
    --all)
        echo "==> Running full test suite (testDebugUnitTest)..."
        run_tests || status=$?
        ;;
    --changed)
        # The core set is fixed, so a new test class runs only here or with --all.
        changed=()
        while IFS= read -r line; do changed+=("$line"); done < <(changed_test_args)
        if [[ ${#changed[@]} -eq 0 ]]; then
            echo "==> No changed test classes; running the core coordination & engine tests..."
            run_tests "${CORE_TESTS[@]}" || status=$?
        else
            echo "==> Running the $(( ${#changed[@]} / 2 )) test classes changed on this branch..."
            run_tests "${changed[@]}" || status=$?
        fi
        ;;
    --ui)
        echo "==> Running UI & preview tests (BottomBar, Dock, Dialogs)..."
        run_tests \
            --tests "com.jestk.browser.ui.navigation.BottomBarPreviewTest" \
            --tests "com.jestk.browser.ui.navigation.DockRefinementsTest" \
            --tests "com.jestk.browser.ui.navigation.TabContextMenuTest" || status=$?
        ;;
    *)
        echo "==> Running core coordination & engine unit tests (new test classes: --changed)..."
        run_tests "${CORE_TESTS[@]}" || status=$?
        ;;
esac

python3 tools/test_summary.py "$started"
if [[ $status -ne 0 ]]; then
    echo "==> Tests FAILED"
    exit "$status"
fi
echo "==> All checks passed!"
