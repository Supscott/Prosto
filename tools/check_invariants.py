#!/usr/bin/env python3
"""Mechanical invariant checks for Prosto Browser.

The checks live in topic modules (tools/invariants_*.py, shared paths in invariants_common.py);
this file only runs them in order and prints the result.
"""

import sys

from file_size_ratchet import check_file_sizes
from invariants_data import (
    check_domain_layer_purity,
    check_no_english_sentinels,
    check_time_ordered_queries_have_tiebreaker,
)
from invariants_engine import (
    check_injected_script_stealth,
    check_secure_window_switch_debug_only,
    check_security_challenge_protection,
    check_single_browser_activity,
    check_stable_favicon_paths,
    check_webkit_crash_resilience,
    check_webview_client_no_favicon,
)
from invariants_repo import (
    check_no_local_home_paths,
    check_script_modes,
    check_tasks_file_size,
)
from invariants_strings import (
    check_english_only_comments,
    check_localization_parity,
    check_no_hardcoded_ui_strings,
)
from invariants_ui import (
    check_dark_theme_resolved_once,
    check_fullscreen_view_attached_synchronously,
    check_no_blur,
    check_no_continuous_effect_keys,
    check_no_file_io_in_composition,
    check_no_test_environment_detection,
    check_refresh_spinner_only_on_pull,
)
from robolectric_sdk_pins import check_robolectric_sdk_pins
from unused_imports import check_unused_imports


def main():
    all_errors = []
    all_errors.extend(check_no_local_home_paths())
    all_errors.extend(check_script_modes())
    all_errors.extend(check_secure_window_switch_debug_only())
    all_errors.extend(check_no_blur())
    all_errors.extend(check_webkit_crash_resilience())
    all_errors.extend(check_domain_layer_purity())
    all_errors.extend(check_stable_favicon_paths())
    all_errors.extend(check_webview_client_no_favicon())
    all_errors.extend(check_no_continuous_effect_keys())
    all_errors.extend(check_localization_parity())
    all_errors.extend(check_no_hardcoded_ui_strings())
    all_errors.extend(check_no_english_sentinels())
    all_errors.extend(check_no_file_io_in_composition())
    all_errors.extend(check_no_test_environment_detection())
    all_errors.extend(check_english_only_comments())
    all_errors.extend(check_security_challenge_protection())
    all_errors.extend(check_injected_script_stealth())
    all_errors.extend(check_fullscreen_view_attached_synchronously())
    all_errors.extend(check_refresh_spinner_only_on_pull())
    all_errors.extend(check_single_browser_activity())
    all_errors.extend(check_file_sizes())
    all_errors.extend(check_tasks_file_size())
    all_errors.extend(check_dark_theme_resolved_once())
    all_errors.extend(check_time_ordered_queries_have_tiebreaker())
    all_errors.extend(check_unused_imports())
    all_errors.extend(check_robolectric_sdk_pins())

    if all_errors:
        print("=== Architectural Invariant Violations ===")
        for err in all_errors:
            print(f"  FAIL: {err}")
        return 1

    print(
        "All architectural invariants satisfied (No Blur, WebKit Crash Resilience, "
        "Domain Purity, Favicon Safety, Localization, Security Challenge Protection, Script Stealth, "
        "Fullscreen Sync Attach, Pull-Only Refresh Spinner, Single Browser Activity, No English Sentinels, No Composition File I/O, No Test Detection, "
        "English Comments, File Size Ratchet, Short TASKS.md, Resolved Dark Theme, Ordered Query Tie-Breaker, No Local Home Paths, Script Modes, Unused Imports in changed files, Robolectric SDK Pins)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
