#!/usr/bin/env python3
"""Mechanical invariant checks for Prosto Browser.

The checks live in topic modules (tools/invariants_*.py, shared paths in invariants_common.py);
this file only runs them in order and prints the result.

CHECKS is the single table: it drives both the calls (in table order) and the summary line.
Each row names its check functions (a tuple, run in order); a row with a name is listed in the
summary, in table order. Rows without a name run silently. Adding a check is one row.
"""

import sys
from typing import NamedTuple, Optional, Tuple

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


class Check(NamedTuple):
    fns: Tuple  # check functions, called in this order
    name: Optional[str] = None  # label in the summary line; None = not listed


CHECKS = (
    Check((check_no_local_home_paths,), "No Local Home Paths"),
    Check((check_script_modes,), "Script Modes"),
    Check((check_secure_window_switch_debug_only,)),
    Check((check_no_blur,), "No Blur"),
    Check((check_webkit_crash_resilience,), "WebKit Crash Resilience"),
    Check((check_domain_layer_purity,), "Domain Purity"),
    # Two calls, one summary name.
    Check((check_stable_favicon_paths, check_webview_client_no_favicon), "Favicon Safety"),
    Check((check_no_continuous_effect_keys,)),
    Check((check_localization_parity,), "Localization"),
    Check((check_no_hardcoded_ui_strings,)),
    Check((check_no_english_sentinels,), "No English Sentinels"),
    Check((check_no_file_io_in_composition,), "No Composition File I/O"),
    Check((check_no_test_environment_detection,), "No Test Detection"),
    Check((check_english_only_comments,), "English Comments"),
    Check((check_security_challenge_protection,), "Security Challenge Protection"),
    Check((check_injected_script_stealth,), "Script Stealth"),
    Check((check_fullscreen_view_attached_synchronously,), "Fullscreen Sync Attach"),
    Check((check_refresh_spinner_only_on_pull,), "Pull-Only Refresh Spinner"),
    Check((check_single_browser_activity,), "Single Browser Activity"),
    Check((check_file_sizes,), "File Size Ratchet"),
    Check((check_tasks_file_size,), "Short TASKS.md"),
    Check((check_dark_theme_resolved_once,), "Resolved Dark Theme"),
    Check((check_time_ordered_queries_have_tiebreaker,), "Ordered Query Tie-Breaker"),
    Check((check_unused_imports,), "Unused Imports"),
    Check((check_robolectric_sdk_pins,), "Robolectric SDK Pins"),
)


def summary_names():
    return [check.name for check in CHECKS if check.name is not None]


def main():
    all_errors = []
    for check in CHECKS:
        for fn in check.fns:
            all_errors.extend(fn())

    if all_errors:
        print("=== Architectural Invariant Violations ===")
        for err in all_errors:
            print(f"  FAIL: {err}")
        return 1

    print("All architectural invariants satisfied (" + ", ".join(summary_names()) + ").")
    return 0


if __name__ == "__main__":
    sys.exit(main())
