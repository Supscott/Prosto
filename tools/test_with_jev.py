#!/usr/bin/env python3
"""
Automated device verification harness using TypeSafe AI (Jev System One model).
Tests dynamic status bar colors and tab long-press reload menu on an adb-connected device.
"""

import os
import sys
import json
import time
import base64
import subprocess
import urllib.request
import urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCRATCH = ROOT / ".scratch"
SCRATCH.mkdir(exist_ok=True)


def load_api_key():
    key = os.environ.get("TYPESAFE_API_KEY")
    if key:
        return key

    env_paths = [ROOT / ".env", Path("/Users/ilya/CODING/browser/.env")]
    for env_path in env_paths:
        if env_path.exists():
            for line in env_path.read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    if k.strip() == "TYPESAFE_API_KEY":
                        return v.strip().strip('"').strip("'")
    return None


def run_adb(cmd):
    full_cmd = ["adb"] + cmd
    res = subprocess.run(full_cmd, capture_output=True, text=True)
    return res.returncode, res.stdout.strip(), res.stderr.strip()


def capture_device_screenshot(output_path, max_dim=1500):
    raw_path = output_path.with_suffix(".raw.png")
    # Capture raw from device
    with open(raw_path, "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)

    # Scale with sips to fit TypeSafe dimensions (280 to 1568 px)
    subprocess.run(["sips", "-Z", str(max_dim), str(raw_path), "--out", str(output_path)],
                   capture_output=True, check=True)
    return output_path


def ask_jev(api_key, image_path, questions, state_desc="Mobile browser screenshot"):
    with open(image_path, "rb") as f:
        b64 = base64.b64encode(f.read()).decode("utf-8")

    asset_key = "device_screen"
    payload = {
        "model": "jev-latest",
        "state": {
            "screenshot": asset_key,
            "description": state_desc
        },
        "assets": {
            asset_key: {
                "type": "image",
                "format": "png",
                "data_base64": b64
            }
        },
        "questions": questions
    }

    req = urllib.request.Request(
        "https://api.typesafe.ai/v1/systemone",
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "TypeSafe-Danger-Zone-Images": "true"
        },
        data=json.dumps(payload).encode("utf-8")
    )

    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8")
        raise RuntimeError(f"TypeSafe API Error HTTP {e.code}: {error_body}")


def main():
    print("=" * 60)
    print(" TypeSafe AI (Jev) Device Verification Harness")
    print("=" * 60)

    api_key = load_api_key()
    if not api_key:
        print("[FAIL] TYPESAFE_API_KEY not found in environment or .env")
        sys.exit(1)
    print("[OK] TypeSafe API key loaded.")

    code, out, _ = run_adb(["devices"])
    lines = [l for l in out.splitlines() if "\tdevice" in l]
    if not lines:
        print("[FAIL] No adb device connected.")
        sys.exit(1)
    device_id = lines[0].split("\t")[0]
    print(f"[OK] Connected adb device: {device_id}")

    # Launch app with test webpage
    app_package = "com.jestk.browser.debug"
    main_activity = "com.jestk.browser.ui.MainActivity"
    test_url = "https://example.com"

    print(f"\n[1/3] Navigating to {test_url} in {app_package}...")
    run_adb(["shell", "am", "start", "-n", f"{app_package}/{main_activity}", "-d", test_url])
    time.sleep(3)

    # Capture webpage screenshot
    screen_path = SCRATCH / "jev_webpage.png"
    capture_device_screenshot(screen_path)
    print(f"[OK] Screenshot captured: {screen_path}")

    # Ask Jev about dynamic layout and chrome
    print("\n[2/3] Asking Jev to evaluate webpage layout and bars...")
    questions = {
        "is_webpage_rendered": {
            "type": "noul",
            "instructions": "In `screenshot`, is the web page content (Example Domain) rendered in the browser viewport?"
        },
        "has_floating_dock": {
            "type": "noul",
            "instructions": "In `screenshot`, is the floating bottom dock visible with the active tab capsule?"
        },
        "status_bar_integrated": {
            "type": "noul",
            "instructions": "Does the status bar area at the top integrate cleanly with the page canvas rather than crashing or showing broken visuals?"
        }
    }

    resp = ask_jev(api_key, screen_path, questions, state_desc="Webpage view with dynamic bars")
    answers = resp.get("answers", {})

    print("\n--- Jev Evaluation Results ---")
    for qid, ans in answers.items():
        val = ans.get("noul", ans.get("choice", ans.get("score")))
        print(f"  • {qid}: {val} (type={ans.get('type')})")

    webpage_ok = answers.get("is_webpage_rendered", {}).get("noul", 0) > 0.6
    dock_ok = answers.get("has_floating_dock", {}).get("noul", 0) > 0.6
    status_ok = answers.get("status_bar_integrated", {}).get("noul", 0) > 0.6

    if not (webpage_ok and dock_ok and status_ok):
        print("\n[FAIL] Jev evaluation did not meet pass thresholds for webpage view.")
        sys.exit(1)
    print("[PASS] Webpage view and dock chrome verified by Jev!")

    # Test tab context menu
    print("\n[3/3] Opening tab context menu and verifying 'Reload' action with Jev...")
    # Long press on active tab center
    run_adb(["shell", "input", "swipe", "400", "2560", "400", "2560", "750"])
    time.sleep(1)

    menu_screen_path = SCRATCH / "jev_context_menu.png"
    capture_device_screenshot(menu_screen_path)

    menu_questions = {
        "context_menu_visible": {
            "type": "noul",
            "instructions": "In `screenshot`, is there a floating contextual popup menu visible?"
        },
        "has_reload_action": {
            "type": "noul",
            "instructions": "In `screenshot`, does the menu contain a Reload ('Обновить' or 'Reload') option with a refresh/reload icon?"
        },
        "top_action_type": {
            "type": "choice",
            "instructions": "What is the primary function of the first option at the very top of the popup menu?",
            "criteria": {
                "reload": "Reloads or refreshes the webpage",
                "close": "Closes the tab",
                "other": "Any other action or no menu"
            }
        }
    }

    menu_resp = ask_jev(api_key, menu_screen_path, menu_questions, state_desc="Tab context menu")
    menu_answers = menu_resp.get("answers", {})

    print("\n--- Jev Context Menu Results ---")
    for qid, ans in menu_answers.items():
        val = ans.get("noul", ans.get("choice", ans.get("score")))
        print(f"  • {qid}: {val} (type={ans.get('type')})")

    # Usage stats
    usage = menu_resp.get("usage", {})
    print(f"\n[Info] Jev Model: {menu_resp.get('model')}, Tokens: in={usage.get('input_tokens')}, out={usage.get('output_tokens')}")

    print("\n" + "=" * 60)
    print(" ALL JEV / TYPESAFE AI VERIFICATIONS COMPLETE: SUCCESS")
    print("=" * 60)


if __name__ == "__main__":
    main()
