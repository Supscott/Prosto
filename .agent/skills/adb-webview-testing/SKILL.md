---
name: adb-webview-testing
description: "Inspect, debug, and trace Android WebView state (DOM, cookies, network, Service Workers, Client Hints, console) on real devices via ADB and Chrome DevTools Protocol (CDP)."
---

# ADB WebView Testing & Remote Debugging

This skill guides coding agents through inspecting, debugging, and tracing real Android `WebView` instances running on a connected device via ADB and Chrome DevTools Protocol (CDP) WebSocket without requiring desktop Chrome UI.

---

## 1. Quick Device & App Discovery

### Check Connected Devices
```bash
rtk adb devices
```

### Identify Package Name & PID
```bash
# Verify debug build is installed
rtk adb shell pm list packages | grep jestk

# Find active app PID
rtk adb shell pidof com.jestk.browser.debug
```

### Locate WebView DevTools Remote Socket
Android Chromium WebView publishes abstract UNIX domain sockets for debuggable apps:
```bash
rtk adb shell cat /proc/net/unix | grep webview_devtools_remote
```
Output example:
`@webview_devtools_remote_19886` (where `19886` is the process ID).

---

## 2. Forward DevTools Port & List Targets

### Forward Port to Localhost
```bash
rtk adb forward tcp:9222 localabstract:webview_devtools_remote_<PID>
```

### Inspect Open Pages, Tabs, and Service Workers
```bash
curl -s http://127.0.0.1:9222/json/list | jq .
```
Each target provides:
- `id`: Target ID
- `title`: Page title
- `url`: Current loaded URL
- `type`: `"page"`, `"service_worker"`, or `"iframe"`
- `webSocketDebuggerUrl`: WebSocket URL for CDP communication (`ws://127.0.0.1:9222/devtools/page/<id>`)

---

## 3. Remote Inspection via Node.js CDP Scripts

Use modern Node.js (v22+ with built-in `WebSocket`) to inspect or drive the page. Save scratch scripts in `<appDataDir>/brain/<conversation-id>/scratch/`.

### Pattern A: Evaluate JavaScript on the Page
```javascript
// scratch/eval_page.js
async function run() {
  const res = await fetch('http://127.0.0.1:9222/json/list');
  const targets = await res.json();
  const target = targets.find(t => t.type === 'page' && t.url.includes('example.com'));
  if (!target) throw new Error('Target not found');

  const ws = new WebSocket(target.webSocketDebuggerUrl);
  let id = 1;
  const send = (method, params = {}) => ws.send(JSON.stringify({ id: id++, method, params }));

  ws.onopen = () => {
    send('Runtime.enable');
    send('Runtime.evaluate', {
      expression: `({
        url: location.href,
        title: document.title,
        ua: navigator.userAgent,
        brands: navigator.userAgentData?.brands,
        cookies: document.cookie
      })`,
      returnByValue: true
    });
  };

  ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    if (data.result?.result) {
      console.log('Result:', JSON.stringify(data.result.result.value, null, 2));
      ws.close();
      process.exit(0);
    }
  };
}
run();
```

### Pattern B: Inspect Cookies & Partition Keys
Inspect cookies including `HttpOnly`, `SameSite`, and `partitionKey`:
```javascript
// scratch/check_cookies.js
async function run() {
  const res = await fetch('http://127.0.0.1:9222/json/list');
  const targets = await res.json();
  const target = targets.find(t => t.type === 'page');
  const ws = new WebSocket(target.webSocketDebuggerUrl);

  ws.onopen = () => {
    ws.send(JSON.stringify({
      id: 1,
      method: 'Network.getCookies',
      params: { urls: [target.url] }
    }));
  };

  ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    if (data.result?.cookies) {
      console.log('Cookies:', JSON.stringify(data.result.cookies, null, 2));
      ws.close();
      process.exit(0);
    }
  };
}
run();
```

### Pattern C: Trace Network Requests & Subresource Headers
Monitor network requests in real time to diagnose 403s, missing headers, or Cloudflare challenges:
```javascript
// scratch/trace_network.js
async function run() {
  const res = await fetch('http://127.0.0.1:9222/json/list');
  const targets = await res.json();
  const target = targets.find(t => t.type === 'page');
  const ws = new WebSocket(target.webSocketDebuggerUrl);

  ws.onopen = () => {
    ws.send(JSON.stringify({ id: 1, method: 'Network.enable' }));
  };

  ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    if (data.method === 'Network.requestWillBeSent') {
      const req = data.params.request;
      console.log(`[REQ] ${req.method} ${req.url}`);
      console.log('Headers:', JSON.stringify(req.headers, null, 2));
    } else if (data.method === 'Network.responseReceived') {
      const resp = data.params.response;
      console.log(`[RESP] ${resp.status} ${resp.url}`);
      if (resp.headers['cf-mitigated']) {
        console.log('Cloudflare Mitigated:', resp.headers['cf-mitigated']);
      }
    }
  };
}
run();
```

### Pattern D: Inspect CacheStorage & Service Workers
PWA Service Workers (e.g. Serwist / Workbox) can cache stale error responses:
```javascript
// scratch/check_cache.js
async function run() {
  const res = await fetch('http://127.0.0.1:9222/json/list');
  const targets = await res.json();
  const target = targets.find(t => t.type === 'page');
  const ws = new WebSocket(target.webSocketDebuggerUrl);

  ws.onopen = () => {
    ws.send(JSON.stringify({
      id: 1,
      method: 'Runtime.evaluate',
      params: {
        expression: `(async () => {
          const names = await caches.keys();
          const summary = {};
          for (const name of names) {
            const cache = await caches.open(name);
            const keys = await cache.keys();
            summary[name] = keys.length;
          }
          return summary;
        })()`,
        awaitPromise: true,
        returnByValue: true
      }
    }));
  };

  ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    if (data.result?.result) {
      console.log('Caches:', JSON.stringify(data.result.result.value, null, 2));
      ws.close();
      process.exit(0);
    }
  };
}
run();
```

---

## 4. Deploying & Launching on Device

Use `tools/device.sh` for routine steps (it refuses the user's own `com.jestk.browser` install):
```bash
rtk tools/device.sh wake          # fails with LOCKED when the phone needs the user to unlock it
rtk tools/device.sh install       # ./gradlew :app:installDebug, retried once
rtk tools/device.sh start         # launch from the launcher
rtk tools/device.sh open "<url>"  # ACTION_VIEW like another app; any URL characters are safe
rtk tools/device.sh visible       # URL of the WebView page on screen (CDP), no screenshot needed
rtk tools/smoke.sh                # PASS/FAIL device smoke test of tabs, links and Back
```

### Filter Logcat for Engine Events
```bash
rtk adb logcat -d -s JestkWebViewClient:* WebViewPool:* AdBlockerEngine:* chromium:*
```
