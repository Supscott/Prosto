// Evaluates a JS expression in the WebView page whose URL starts with <urlPrefix> over CDP and
// prints the JSON result. Used by `tools/device.sh eval`, which forwards the CDP port first.
//
//   node tools/cdp_eval.mjs <urlPrefix> <expression> [port]
//
// CDP_GESTURE=1 runs the expression as a user gesture (window.open, link clicks that need one).
const [prefix, expr, port = '9333'] = process.argv.slice(2);
setTimeout(() => { console.error('timeout: the page did not answer (app frozen or busy?)'); process.exit(2); }, 8000);
const list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
const t = list.find(x => x.type === 'page' && x.url.startsWith(prefix));
if (!t) { console.error('no target'); process.exit(1); }
const ws = new WebSocket(t.webSocketDebuggerUrl);
ws.onopen = () => ws.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression: expr, returnByValue: true, awaitPromise: true, userGesture: process.env.CDP_GESTURE === '1' } }));
ws.onmessage = (m) => { const r = JSON.parse(m.data); if (r.id === 1) { console.log(JSON.stringify(r.result.result.value ?? r.result)); ws.close(); process.exit(0); } };
