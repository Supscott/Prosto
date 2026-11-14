// Run with: node --test tools/build_scriptlets.test.mjs
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const bundleText = readFileSync(join(ROOT, 'app/src/main/assets/scripts/scriptlets.js'), 'utf8');
const stubs = JSON.parse(readFileSync(join(ROOT, 'tools/filterlists/scriptlet_stubs.json'), 'utf8'));

function load() {
    const sandbox = {};
    sandbox.window = sandbox;
    sandbox.self = sandbox;
    sandbox.document = { location: { href: 'https://example.com/', hostname: 'example.com' } };
    const ctx = vm.createContext(sandbox);
    const before = new Set(Object.getOwnPropertyNames(ctx));
    const table = vm.runInContext(bundleText, ctx)();
    const added = Object.getOwnPropertyNames(ctx).filter((n) => !before.has(n));
    return { table, added };
}

test('bundle evaluates, defines no globals and is frozen', () => {
    const { table, added } = load();
    assert.deepEqual(added, []);
    assert.ok(Object.isFrozen(table));
});

test('required scriptlets exist', () => {
    const { table } = load();
    for (const n of ['json-prune', 'set-constant', 'abort-on-property-read', 'prevent-window-open', 'no-setTimeout-if']) {
        assert.equal(typeof table[n], 'function', n);
        assert.equal(typeof table[`${n}.js`], 'function', `${n}.js`);
    }
});

test('every stub name and alias resolves to the same function', () => {
    const { table } = load();
    assert.ok(stubs.resources.length > 30);
    for (const r of stubs.resources) {
        const fn = table[r.name];
        assert.equal(typeof fn, 'function', r.name);
        for (const a of r.aliases) { assert.equal(table[a], fn, `${r.name} alias ${a}`); }
        const body = JSON.parse(Buffer.from(r.content, 'base64').toString());
        assert.equal(r.kind, 'template');
        assert.equal(body.length, 11);
        assert.equal(typeof table[body[0]], 'function', body[0]);
        assert.equal(body[1], '{{1}}');
        assert.equal(body[10], '{{10}}');
    }
});

test('trusted and isolated-world scriptlets are absent', () => {
    const { table } = load();
    for (const n of Object.keys(table)) { assert.ok(!n.startsWith('trusted-'), n); }
    assert.equal(table['set-cookie'], undefined);
});
