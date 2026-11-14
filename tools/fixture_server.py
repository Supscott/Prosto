#!/usr/bin/env python3
"""Serves tools/fixtures/adblock/ for desktop, phone and emulator checks of the content blocker.

Beyond static files it provides:
- `Content-Security-Policy` on csp-strict.html (a plain `http.server` cannot set it per file);
- `/fixture-filters.txt` with `{{HOST}}` replaced by the host name of the request;
- `/fx-block/*` and `/fx-important/*`: generated resources the blocking rules target;
- `/__hits`: JSON list of the request paths seen (what a blocker stopped never appears), and
  `/__hits/reset` to clear it.

Usage:
    python3 tools/fixture_server.py [--port 8765]    # serve on all interfaces
    python3 tools/fixture_server.py --self-test      # start on a free port and check the behaviour
"""

import argparse
import json
import re
import socket
import sys
import threading
import urllib.request
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

SITE = Path(__file__).resolve().parent / "fixtures" / "adblock"
CSP = "default-src 'self'; script-src 'self'; style-src 'self'"
GIF = bytes.fromhex("47494638396101000100800000000000ffffff21f90401000000002c00000000010001000002024401003b")

HITS: list = []
HITS_LOCK = threading.Lock()


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(SITE), **kwargs)

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (self.command, self.path))

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        if self.path.split("?", 1)[0] == "/csp-strict.html":
            self.send_header("Content-Security-Policy", CSP)
        super().end_headers()

    def send_bytes(self, body: bytes, content_type: str):
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == "/__hits":
            with HITS_LOCK:
                body = json.dumps(HITS).encode()
            return self.send_bytes(body, "application/json")
        if path == "/__hits/reset":
            with HITS_LOCK:
                HITS.clear()
            return self.send_bytes(b"reset\n", "text/plain")
        with HITS_LOCK:
            HITS.append(self.path)
        if path == "/fixture-filters.txt":
            host = re.sub(r":\d+$", "", self.headers.get("Host", "localhost"))
            text = (SITE / "fixture-filters.txt").read_text(encoding="utf-8").replace("{{HOST}}", host)
            return self.send_bytes(text.encode(), "text/plain; charset=utf-8")
        if path.startswith(("/fx-block/", "/fx-important/")):
            ext = path.rsplit(".", 1)[-1]
            if ext == "gif":
                return self.send_bytes(GIF, "image/gif")
            if ext == "js":
                return self.send_bytes(b"window.fxLoaded = (window.fxLoaded || []).concat(%s);\n" % json.dumps(path).encode(), "application/javascript")
            if ext == "css":
                return self.send_bytes(b"/* fixture */\n", "text/css")
            if ext == "json":
                return self.send_bytes(b"{}\n", "application/json")
            return self.send_bytes(b"", "application/octet-stream")
        return super().do_GET()


def lan_ip() -> str:
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("192.0.2.1", 9))
        return probe.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        probe.close()


def fetch(url: str):
    with urllib.request.urlopen(url, timeout=5) as response:
        return response.status, response.headers, response.read()


def self_test() -> int:
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    base = f"http://127.0.0.1:{server.server_address[1]}"
    failures = []
    try:
        _, headers, _ = fetch(base + "/csp-strict.html")
        if headers.get("Content-Security-Policy") != CSP:
            failures.append(f"csp-strict.html CSP header is {headers.get('Content-Security-Policy')!r}")
        _, headers, _ = fetch(base + "/index.html")
        if headers.get("Content-Security-Policy"):
            failures.append("index.html must not carry a CSP header")
        _, _, body = fetch(base + "/fixture-filters.txt")
        if b"{{HOST}}" in body or b"127.0.0.1##.fx-site-ad" not in body:
            failures.append("fixture-filters.txt placeholder was not expanded")
        fetch(base + "/__hits/reset")
        fetch(base + "/fx-block/ads.js")
        _, _, body = fetch(base + "/__hits")
        if json.loads(body) != ["/fx-block/ads.js"]:
            failures.append(f"hit log is {body!r}")
        _, _, index = fetch(base + "/index.html")
        for page in re.findall(rb'href="/([\w-]+\.html)"', index):
            fetch(base + "/" + page.decode())
    except Exception as error:
        failures.append(f"request failed: {error}")
    finally:
        server.shutdown()
    for failure in failures:
        print("FAIL:", failure)
    print("fixture server self-test:", "FAILED" if failures else "passed")
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    server = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    print(f"Phone (same Wi-Fi): http://{lan_ip()}:{args.port}/")
    print(f"Emulator:           http://10.0.2.2:{args.port}/")
    print(f"Desktop:            http://localhost:{args.port}/")
    print("Extra list:         <base>/fixture-filters.txt (the host name is filled in per request)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
