// Run with: node --test tools/content_bootstrap_blob.test.mjs
// Drives the blob download section of assets/scripts/content_bootstrap.js against fake
// window/document objects (no jsdom in this repo).
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const BLOB_BRIDGE = 'blobBridgeXyz';
const source = readFileSync(join(ROOT, 'app/src/main/assets/scripts/content_bootstrap.js'), 'utf8')
    .replaceAll('@@BRIDGE_NAME@@', 'bridgeXyz')
    .replaceAll('@@BLOB_BRIDGE_NAME@@', BLOB_BRIDGE)
    .replace('/*@@SCRIPTLETS@@*/', 'function () { return {}; }')
    .replace('/*@@PROCEDURAL@@*/', 'function () {}');

const ORIGIN = 'https://site.example';

/**
 * Runs the bootstrap. The page path is a challenge path, so the script returns right after the
 * blob section and the rest of it (cosmetics, scriptlets) needs no fakes.
 */
function boot({ links = [], title = '' } = {}) {
    const listeners = { message: [], click: [] };
    const delivered = [];
    const fetched = [];
    const window = {
        [BLOB_BRIDGE]: {
            onBlobDownloaded: (url, dataUrl, disposition, mime) => delivered.push({ url, dataUrl, disposition, mime }),
        },
        addEventListener: (type, fn) => (listeners[type] ||= []).push(fn),
    };
    const document = {
        title,
        querySelector(selector) {
            const href = /href="([^"]+)"/.exec(selector)?.[1];
            return links.find((l) => l.href === href && l.download) ?? null;
        },
        getElementById: () => null,
    };
    class XMLHttpRequest {
        open(method, url) { this.url = url; }
        send() {
            fetched.push(this.url);
            this.status = 200;
            this.response = { type: 'text/plain' };
            this.onload();
        }
    }
    class FileReader {
        readAsDataURL(blob) {
            this.result = `data:${blob.type};base64,eA==`;
            this.onloadend();
        }
    }
    const context = vm.createContext({
        window,
        document,
        location: { origin: ORIGIN, hostname: 'site.example', pathname: '/cdn-cgi/challenge-platform/' },
        CSS: { escape: (s) => s },
        URL,
        XMLHttpRequest,
        FileReader,
        fetch: () => Promise.reject(new Error('unused')),
    });
    context.globalThis = context;
    vm.runInContext(source, context);

    const post = (data) => {
        let stopped = false;
        const event = { data, stopImmediatePropagation: () => { stopped = true; } };
        listeners.message.forEach((fn) => fn(event));
        return stopped;
    };
    const click = (link) => {
        const target = { closest: (sel) => (sel.includes('blob:') && link.href.startsWith('blob:') ? link : null) };
        listeners.click.forEach((fn) => fn({ target }));
    };
    return { window, delivered, fetched, post, click };
}

const link = (href, download) => ({ href, download, getAttribute: (n) => (n === 'download' ? download : null) });
const request = (url, disposition = '', mime = '') => ({ [BLOB_BRIDGE]: [url, disposition, mime] });

test('the bridge global is deleted before page scripts run', () => {
    const { window } = boot();
    assert.equal(BLOB_BRIDGE in window, false);
});

test('a request keyed by the bridge name is stopped and the blob is delivered', () => {
    const url = `blob:${ORIGIN}/1`;
    const page = boot({ links: [link(url, 'report.csv')] });
    assert.equal(page.post(request(url, '', 'text/csv')), true);
    assert.deepEqual(page.fetched, [url]);
    assert.equal(page.delivered.length, 1);
    assert.equal(page.delivered[0].disposition, "attachment; filename*=UTF-8''report.csv");
    assert.equal(page.delivered[0].mime, 'text/csv');
    assert.match(page.delivered[0].dataUrl, /^data:/);
});

test('other messages reach the page', () => {
    const page = boot();
    assert.equal(page.post({ other: 1 }), false);
    assert.equal(page.post('text'), false);
    assert.equal(page.post({ [BLOB_BRIDGE]: ['only one'] }), false);
    assert.deepEqual(page.fetched, []);
});

test('a blob of another origin is not read', () => {
    const page = boot();
    assert.equal(page.post(request('blob:https://other.example/1')), true);
    assert.deepEqual(page.fetched, []);
    assert.deepEqual(page.delivered, []);
});

test('the disposition from the download listener wins over the link name', () => {
    const url = `blob:${ORIGIN}/2`;
    const page = boot({ links: [link(url, 'link.txt')] });
    page.post(request(url, 'attachment; filename="server.txt"'));
    assert.equal(page.delivered[0].disposition, 'attachment; filename="server.txt"');
});

test('a tapped link that the page removed still names the file', () => {
    const url = `blob:${ORIGIN}/3`;
    const page = boot({ title: 'Page title' });
    page.click(link(url, 'tapped.zip'));
    page.post(request(url));
    assert.equal(page.delivered[0].disposition, "attachment; filename*=UTF-8''tapped.zip");
});

test('a link that was never in the page falls back to the page title', () => {
    const url = `blob:${ORIGIN}/4`;
    const page = boot({ title: '  Sales report  ' });
    page.post(request(url));
    assert.equal(page.delivered[0].disposition, "attachment; filename*=UTF-8''Sales%20report");
});

test('without a link name or title the Kotlin side picks the name', () => {
    const page = boot();
    page.post(request(`blob:${ORIGIN}/5`));
    assert.equal(page.delivered[0].disposition, '');
});
