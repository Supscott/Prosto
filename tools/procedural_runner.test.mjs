// Run with: node --test tools/procedural_runner.test.mjs
// Drives assets/scripts/procedural.js against a tiny fake DOM (no jsdom in this repo).
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const source = readFileSync(join(ROOT, 'app/src/main/assets/scripts/procedural.js'), 'utf8');

class El {
    constructor(tag, { id, cls = [], attrs = {}, text = '', css = {} } = {}, children = []) {
        this.localName = tag;
        this.nodeType = 1;
        this.attrs = new Map(Object.entries(attrs));
        if (id) this.attrs.set('id', id);
        if (cls.length) this.attrs.set('class', cls.join(' '));
        this.ownText = text;
        this.css = css;
        this.children = children;
        this.parentElement = null;
        this.world = null;
        children.forEach((c) => { c.parentElement = this; });
        const self = this;
        this.classList = {
            contains: (c) => (self.attrs.get('class') || '').split(' ').includes(c),
            remove(c) {
                self.attrs.set('class', (self.attrs.get('class') || '').split(' ').filter((x) => x !== c).join(' '));
                self.changed();
            },
        };
    }
    changed() { this.world?.record(); }
    get textContent() { return this.ownText + this.children.map((c) => c.textContent).join(''); }
    get nextElementSibling() {
        const sibs = this.parentElement?.children ?? [];
        return sibs[sibs.indexOf(this) + 1] ?? null;
    }
    all() { return this.children.flatMap((c) => [c, ...c.all()]); }
    hasAttribute(n) { return this.attrs.has(n); }
    getAttribute(n) { return this.attrs.has(n) ? this.attrs.get(n) : null; }
    getAttributeNames() { return [...this.attrs.keys()]; }
    setAttribute(n, v) { this.attrs.set(n, String(v)); this.changed(); }
    removeAttribute(n) { this.attrs.delete(n); this.changed(); }
    remove() {
        if (this.parentElement) this.parentElement.children = this.parentElement.children.filter((c) => c !== this);
        this.parentElement = null;
        this.changed();
    }
    // Compound selectors only: tag, .class, #id, [attr], [attr=value], *.
    matches(sel) {
        return sel.split(',').some((part) => {
            const m = part.trim().match(/^(\*|[a-z0-9]+)?((?:[.#][\w-]+|\[[^\]]+\])*)$/i);
            if (!m) throw new SyntaxError(`fake DOM cannot parse ${sel}`);
            if (m[1] && m[1] !== '*' && m[1] !== this.localName) return false;
            return (m[2].match(/[.#][\w-]+|\[[^\]]+\]/g) || []).every((t) => {
                if (t[0] === '.') return this.classList.contains(t.slice(1));
                if (t[0] === '#') return this.attrs.get('id') === t.slice(1);
                const [name, value] = t.slice(1, -1).split('=');
                return value === undefined ? this.attrs.has(name) : this.attrs.get(name) === value.replace(/"/g, '');
            });
        });
    }
    closest(sel) {
        for (let n = this; n; n = n.parentElement) if (n.matches(sel)) return n;
        return null;
    }
    querySelectorAll(sel) {
        const scoped = sel.match(/^:scope\s*(>?)\s*(.+)$/);
        if (scoped) {
            const pool = scoped[1] ? this.children : this.all();
            return pool.filter((n) => n.matches(scoped[2]));
        }
        return this.all().filter((n) => n.matches(sel));
    }
}

function setup(tree, { xpath = {}, path = '/', search = '', clockStep = 0 } = {}) {
    const html = new El('html', {}, [tree]);
    const observers = [];
    const timers = [];
    const listeners = {};
    const rules = [];
    const world = {
        records: 0,
        record() { observers.forEach((o) => o.records.push({})); },
    };
    for (const n of [html, ...html.all()]) n.world = world;
    class FakeObserver {
        constructor(cb) { this.cb = cb; this.records = []; this.options = null; this.disconnected = false; observers.push(this); }
        observe(_, options) { this.options = options; }
        takeRecords() { const r = this.records; this.records = []; return r; }
        disconnect() { this.disconnected = true; }
        deliver() { if (this.records.length) this.cb(this.records); }
    }
    const doc = {
        documentElement: html,
        querySelectorAll: (sel) => html.querySelectorAll(sel),
        evaluate: (expr, ctx) => {
            const nodes = (xpath[expr] ?? (() => []))(ctx);
            return { snapshotLength: nodes.length, snapshotItem: (i) => nodes[i] };
        },
        addEventListener: (type, fn) => { (listeners[type] ??= []).push(fn); },
    };
    const win = {
        location: { pathname: path, search },
        addEventListener: (type, fn) => { (listeners[type] ??= []).push(fn); },
    };
    let clock = 0;
    const env = {
        document: doc,
        window: win,
        now: () => (clock += clockStep),
        setTimeout: (fn, ms) => { timers.push({ fn, ms }); return timers.length; },
        MutationObserver: FakeObserver,
        getComputedStyle: (el, pseudo) => ({ getPropertyValue: (n) => el.css[`${pseudo || ''}|${n}`] ?? '' }),
        addRules: (text) => rules.push(text),
    };
    const ctx = vm.createContext({});
    const before = new Set(Object.getOwnPropertyNames(ctx));
    const run = (filters) => {
        vm.runInContext(`(${source})`, ctx)(env, filters);
        return Object.getOwnPropertyNames(ctx).filter((n) => !before.has(n));
    };
    const tick = () => { const t = timers.shift(); if (t) t.fn(); return t; };
    const drain = () => { while (timers.length) tick(); };
    return { html, env, observers, timers, listeners, rules, run, tick, drain, world };
}

const op = (type, arg) => ({ type, arg });
const css = (arg) => op('css-selector', arg);
const filter = (selector, action) => (action ? { selector, action } : { selector });
const marked = (page) => page.html.all().filter((n) => [...n.attrs.keys()].some((k) => /^a[a-z0-9]+$/.test(k) && k !== 'a')).map((n) => n.attrs.get('id') || n.localName);

function page() {
    return new El('body', {}, [
        new El('div', { id: 'ad1', cls: ['ad'], text: 'Sponsored post' }),
        new El('div', { id: 'plain', cls: ['post'], text: 'Hello world' }),
        new El('div', { id: 'box', cls: ['box'] }, [
            new El('span', { id: 'inner', cls: ['label'], text: 'Advertisement', attrs: { 'data-ad': 'yes' } }),
            new El('span', { id: 'sib', cls: ['label2'], text: 'x' }),
        ]),
    ]);
}

test('adds no globals and exposes nothing', () => {
    const p = setup(page());
    assert.deepEqual(p.run([filter([css('.ad')])]), []);
});

test('plain css filter hides through one stylesheet rule and a marker attribute', () => {
    const p = setup(page());
    p.run([filter([css('.ad')])]);
    p.drain();
    assert.deepEqual(marked(p), ['ad1']);
    assert.equal(p.rules.length, 1);
    assert.match(p.rules[0], /^\[a[a-z0-9]+~="h"\]\{display:none!important\}$/);
    const attrName = [...p.html.all().find((n) => n.attrs.get('id') === 'ad1').attrs.keys()].find((k) => k !== 'id' && k !== 'class');
    assert.ok(p.rules[0].startsWith(`[${attrName}~=`));
});

test('has-text matches plain text case-sensitively and /regex/flags', () => {
    const plain = setup(page());
    plain.run([filter([css('div'), op('has-text', 'Sponsored')]), filter([css('div'), op('has-text', 'sponsored')])]);
    plain.drain();
    assert.deepEqual(marked(plain), ['ad1']);
    const re = setup(page());
    re.run([filter([css('div'), op('has-text', '/^sponsored/i')])]);
    re.drain();
    assert.deepEqual(marked(re), ['ad1']);
});

test('min-text-length keeps long enough elements only', () => {
    const p = setup(page());
    p.run([filter([css('span'), op('min-text-length', '5')])]);
    p.drain();
    assert.deepEqual(marked(p), ['inner']);
});

test('upward takes a level count or an ancestor selector', () => {
    const byCount = setup(page());
    byCount.run([filter([css('#inner'), op('upward', '1')])]);
    byCount.drain();
    assert.deepEqual(marked(byCount), ['box']);
    const bySelector = setup(page());
    bySelector.run([filter([css('.label'), op('upward', 'body')])]);
    bySelector.drain();
    assert.deepEqual(marked(bySelector), ['body']);
});

test('xpath replaces the current set with the element results', () => {
    const p = setup(page(), { xpath: { '..': (n) => [n.parentElement, { nodeType: 3 }], '//span': (n) => n.querySelectorAll('span') } });
    p.run([filter([css('#inner'), op('xpath', '..')])]);
    p.drain();
    assert.deepEqual(marked(p), ['box']);
    const first = setup(page(), { xpath: { '//span': () => [] } });
    first.run([filter([op('xpath', '//span')])]);
    first.drain();
    assert.deepEqual(marked(first), []);
});

test('matches-css, matches-css-before and matches-css-after read computed styles', () => {
    const tree = new El('body', {}, [
        new El('div', { id: 'a', css: { '|display': 'flex' } }),
        new El('div', { id: 'b', css: { '::before|content': '"Ad"' } }),
        new El('div', { id: 'c', css: { '::after|content': '"Ad 2"' } }),
    ]);
    const p = setup(tree);
    p.run([
        filter([css('div'), op('matches-css', 'display: flex')]),
        filter([css('div'), op('matches-css-before', 'content: "Ad"')]),
        filter([css('div'), op('matches-css-after', 'content: /^"Ad \\d"$/')]),
    ]);
    p.drain();
    assert.deepEqual(marked(p), ['a', 'b', 'c']);
});

test('matches-attr supports bare names, name=value, quotes, wildcards and regexes', () => {
    const p = setup(page());
    p.run([
        filter([css('span'), op('matches-attr', 'data-ad')]),
        filter([css('div'), op('matches-attr', '"data-ad"="yes"')]),
        filter([css('.label2'), op('matches-attr', '/^class$/=label*')]),
    ]);
    p.drain();
    assert.deepEqual(marked(p), ['inner', 'sib']);
});

test('matches-path tests the pathname and search', () => {
    const on = setup(page(), { path: '/news/today', search: '?x=1' });
    on.run([filter([css('.ad'), op('matches-path', '/news/')])]);
    on.drain();
    assert.deepEqual(marked(on), ['ad1']);
    const off = setup(page(), { path: '/about' });
    off.run([filter([css('.ad'), op('matches-path', '/news/')]), filter([css('.ad'), op('matches-path', '/^\\/about$/')])]);
    off.drain();
    assert.deepEqual(marked(off), ['ad1']);
});

test('later css segments search inside, narrow, or step to siblings', () => {
    const p = setup(page());
    p.run([
        filter([css('.box'), op('has-text', 'Advertisement'), css(' .label')]),
        filter([css('.box'), op('has-text', 'Advertisement'), css('> .label2')]),
        filter([css('.ad'), op('min-text-length', '1'), css('.ad')]),
        filter([css('#inner'), op('min-text-length', '1'), css('+ .label2')]),
    ]);
    p.drain();
    assert.deepEqual(marked(p).sort(), ['ad1', 'inner', 'sib']);
});

test('actions: remove, style, remove-attr, remove-class', () => {
    const p = setup(page());
    p.run([
        filter([css('.post')], { type: 'remove' }),
        filter([css('.ad')], { type: 'style', arg: 'color: red !important' }),
        filter([css('#inner')], { type: 'remove-attr', arg: 'data-ad' }),
        filter([css('#sib')], { type: 'remove-class', arg: 'label2' }),
    ]);
    p.drain();
    const ids = p.html.all().map((n) => n.attrs.get('id'));
    assert.ok(!ids.includes('plain'));
    assert.deepEqual(marked(p), ['ad1']);
    assert.match(p.rules[0], /^\[a[a-z0-9]+~="s1"\]\{color: red !important\}$/);
    assert.equal(p.html.all().find((n) => n.attrs.get('id') === 'inner').hasAttribute('data-ad'), false);
    assert.equal(p.html.all().find((n) => n.attrs.get('id') === 'sib').classList.contains('label2'), false);
});

test('never removes or hides html, head and body', () => {
    const p = setup(page());
    p.run([filter([css('body')], { type: 'remove' }), filter([css('html')])]);
    p.drain();
    assert.equal(p.html.children.length, 1);
    assert.deepEqual(marked(p), []);
});

test('unknown operators, unknown actions and malformed filters are skipped safely', () => {
    const p = setup(page());
    p.run([
        filter([css('.ad'), op('matches-property', 'x')]),
        filter([css('.ad')], { type: 'launch-missiles' }),
        filter([css('.ad')], { type: 'style', arg: 'a{b:c}' }),
        filter([css('.ad')], { type: 'style', arg: 'background: url(x)' }),
        filter([css('.ad'), op('has-text', '/(/')]),
        filter([css('.ad'), op('upward', '999')]),
        filter([css('.ad'), op('min-text-length', 'abc')]),
        filter([css('.ad'), { type: 'has-text' }]),
        filter([css('#inner'), css('+ .a .b')]),
        { selector: [] },
        { selector: 'nope' },
        {},
        null,
        filter([css('.post')]),
    ]);
    p.drain();
    assert.deepEqual(marked(p), ['plain']);
});

test('an invalid css selector disables that filter without breaking the others', () => {
    const p = setup(page());
    p.run([filter([css('div[[')]), filter([css('.ad')])]);
    p.drain();
    assert.deepEqual(marked(p), ['ad1']);
});

test('runs again for new content, throttled to one pending pass', () => {
    const p = setup(page());
    p.run([filter([css('.late')])]);
    p.drain();
    assert.deepEqual(marked(p), []);
    const [observer] = p.observers;
    const late = new El('div', { id: 'late', cls: ['late'] });
    late.world = p.world;
    late.parentElement = p.html.children[0];
    p.html.children[0].children.push(late);
    for (let i = 0; i < 5; i++) observer.cb([{}]);
    assert.equal(p.timers.length, 1);
    p.drain();
    assert.deepEqual(marked(p), ['late']);
});

test('its own changes are dropped from the observer and trigger no new pass', () => {
    const p = setup(page());
    p.run([filter([css('.ad')]), filter([css('#inner')], { type: 'remove-attr', arg: 'data-ad' })]);
    p.drain();
    const [observer] = p.observers;
    assert.equal(observer.records.length, 0);
    observer.deliver();
    assert.equal(p.timers.length, 0);
});

test('observes DOM changes and only the attributes it needs', () => {
    const p = setup(page());
    p.run([filter([css('.ad'), op('has-text', 'x')], { type: 'remove-attr', arg: 'onclick' })]);
    const { options } = p.observers[0];
    assert.equal(options.childList, true);
    assert.equal(options.subtree, true);
    assert.equal(options.characterData, true);
    assert.deepEqual([...options.attributeFilter].sort(), ['class', 'id', 'onclick']);
    const wide = setup(page());
    wide.run([filter([css('span'), op('matches-attr', 'data-ad')])]);
    assert.equal(wide.observers[0].options.attributeFilter, undefined);
});

test('a pass over budget continues in a short follow-up tick', () => {
    const p = setup(page(), { clockStep: 6 });
    p.run([filter([css('.ad')]), filter([css('.post')]), filter([css('.box')]), filter([css('.label')])]);
    const first = p.tick();
    assert.equal(first.ms, 100);
    assert.ok(p.timers.length === 1 && p.timers[0].ms === 40, 'follow-up scheduled');
    p.drain();
    assert.deepEqual(marked(p).sort(), ['ad1', 'box', 'inner', 'plain']);
});

test('reruns after DOMContentLoaded and load', () => {
    const p = setup(page());
    p.run([filter([css('.ad')])]);
    p.drain();
    assert.equal(p.listeners.DOMContentLoaded.length, 1);
    assert.equal(p.listeners.load.length, 1);
    p.listeners.load[0]();
    assert.equal(p.timers.length, 1);
});

test('a frame whose filters are all unusable never observes the DOM', () => {
    const p = setup(page());
    p.run([filter([op('matches-property', 'x')]), null]);
    assert.equal(p.observers.length, 0);
    assert.equal(p.timers.length, 0);
});

test('accepts the exact JSON adblock-rust 0.13.5 emits', () => {
    // Captured from native/adblock-jni tests/engine.rs (procedural_operators_are_split_out_of_the_hide_list).
    const emitted = [
        '{"selector":[{"type":"css-selector","arg":".a"}],"action":{"type":"remove"}}',
        '{"selector":[{"type":"css-selector","arg":".ad"},{"type":"has-text","arg":"/sponsored/i"}]}',
        '{"selector":[{"type":"css-selector","arg":"div"},{"type":"matches-css","arg":"display: block"},{"type":"xpath","arg":"//a"}]}',
        '{"selector":[{"type":"css-selector","arg":".m"},{"type":"has-text","arg":"a"}],"action":{"type":"remove"}}',
    ].map((s) => JSON.parse(s));
    const p = setup(page(), { xpath: { '//a': () => [] } });
    assert.deepEqual(p.run(emitted), []);
    p.drain();
    assert.deepEqual(marked(p), ['ad1']);
});

test('the real bootstrap hands the frame\'s filters to the runner and its rules reach the cosmetic style', () => {
    const p = setup(page());
    const bootstrap = readFileSync(join(ROOT, 'app/src/main/assets/scripts/content_bootstrap.js'), 'utf8')
        .replace('/*@@YOUTUBE_FAST_START@@*/', () => '')
        .replace('/*@@SCRIPTLETS@@*/', () => 'function () { return {}; }')
        .replace('/*@@PROCEDURAL@@*/', () => source.trimEnd())
        .replaceAll('@@BLOB_BRIDGE_NAME@@', 'blobBridgeName')
        .replaceAll('@@BRIDGE_NAME@@', 'bridgeName');
    const styles = [];
    const document = Object.assign(p.env.document, {
        readyState: 'complete',
        head: { appendChild: (el) => styles.push(el) },
        createElement: () => ({ text: '', isConnected: true, set textContent(v) { this.text = v; }, appendChild(n) { this.text += n.text; } }),
        createTextNode: (text) => ({ text }),
        getElementById: () => null,
    });
    const answer = JSON.stringify({
        css: ['.x{display:none!important}'],
        generichide: true,
        exceptions: [],
        scriptlets: [],
        procedural: [filter([css('.ad'), op('has-text', 'Sponsored')])],
    });
    const sandbox = {
        document,
        location: { href: 'https://site.test/', hostname: 'site.test', pathname: '/', search: '' },
        bridgeName: { isEnabled: () => true, cosmetic: () => answer },
        MutationObserver: p.env.MutationObserver,
        setTimeout: p.env.setTimeout,
        getComputedStyle: p.env.getComputedStyle,
        addEventListener: p.env.window.addEventListener,
    };
    sandbox.window = sandbox;
    const ctx = vm.createContext(sandbox);
    const before = new Set(Object.getOwnPropertyNames(ctx));
    vm.runInContext(bootstrap, ctx);
    assert.deepEqual(Object.getOwnPropertyNames(ctx).filter((n) => !before.has(n)), [], 'no globals');
    assert.equal(sandbox.bridgeName, undefined, 'bridge deleted');
    p.drain();
    assert.deepEqual(marked(p), ['ad1']);
    assert.equal(styles.length, 1);
    assert.match(styles[0].text, /^\.x\{display:none!important\}\[a[a-z0-9]+~="h"\]\{display:none!important\}$/);
});

test('the real bootstrap never starts the runner for a frame without procedural filters', () => {
    const bootstrap = readFileSync(join(ROOT, 'app/src/main/assets/scripts/content_bootstrap.js'), 'utf8');
    assert.match(bootstrap, /cosmetic\.procedural && cosmetic\.procedural\.length/);
    assert.match(bootstrap, /if \(proceduralNatives\) \{/);
});
