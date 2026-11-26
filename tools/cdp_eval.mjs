// Evaluates a JS expression in the WebView page whose URL starts with <urlPrefix> over CDP and
// prints the JSON result. Used by `tools/device.sh js` (alias `eval`) and `tap`, which forward the CDP port first.
//
//   node tools/cdp_eval.mjs <urlPrefix> <expression> [port]
//
// The visible page is preferred when several match (cdp_targets.mjs, PRO-171). Before evaluating, the
// script waits up to 5 s for document.readyState to be 'interactive' or 'complete' (PRO-171, PRO-186);
// CDP_WAIT=0 skips the wait.
// CDP_GESTURE=1 runs the expression as a user gesture (window.open, link clicks that need one).
import { pickTarget } from './cdp_targets.mjs';

const [prefix, expr, port = '9333'] = process.argv.slice(2);
const waitMs = process.env.CDP_WAIT === '0' ? 0 : 5000;
setTimeout(() => { console.error('timeout: the page did not answer (app frozen or busy?)'); process.exit(2); }, 8000 + waitMs);
const list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
const picked = pickTarget(list, prefix);
if (!picked) { console.error('no target'); process.exit(1); }
if (picked.hidden) console.error(`cdp: only hidden pages match ${prefix}; using ${picked.target.url}`);
const ws = new WebSocket(picked.target.webSocketDebuggerUrl);

let nextId = 1;
const pending = new Map();
// Sends one CDP command and resolves with its reply.
function call(method, params) {
    return new Promise(resolve => {
        const id = nextId++;
        pending.set(id, resolve);
        ws.send(JSON.stringify({ id, method, params }));
    });
}
ws.onmessage = (m) => {
    const reply = JSON.parse(m.data);
    const resolve = pending.get(reply.id);
    if (resolve) { pending.delete(reply.id); resolve(reply); }
};

// Polls readyState until it is 'interactive' or 'complete', or waitMs passes; a page still loading is
// evaluated anyway, with a warning. 'interactive' is enough: the DOM is parsed and the scripts can run.
async function waitForLoad() {
    const deadline = Date.now() + waitMs;
    let state = 'unknown';
    while (Date.now() < deadline) {
        const reply = await call('Runtime.evaluate', { expression: 'document.readyState', returnByValue: true });
        state = reply.result?.result?.value ?? 'unknown';
        if (state === 'interactive' || state === 'complete') return;
        await new Promise(resolve => setTimeout(resolve, 100));
    }
    console.error(`cdp: the page is still loading after ${waitMs / 1000} s (readyState ${state}); evaluating anyway`);
}

ws.onopen = async () => {
    if (waitMs > 0) await waitForLoad();
    const reply = await call('Runtime.evaluate', {
        expression: expr, returnByValue: true, awaitPromise: true, userGesture: process.env.CDP_GESTURE === '1',
    });
    console.log(JSON.stringify(reply.result.result.value ?? reply.result));
    ws.close();
    process.exit(0);
};
