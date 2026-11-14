#!/usr/bin/env bash
# Serves tools/fixtures/adblock/ on all interfaces and prints the URLs for a phone (LAN) and an
# emulator (10.0.2.2). Usage: tools/fixture_server.sh [port]   (default 8765)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec python3 "$ROOT/tools/fixture_server.py" --port "${1:-8765}"
