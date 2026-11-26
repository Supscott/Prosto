// Tests for tools/cdp_targets.mjs (PRO-171): visible-tab preference for the CDP target.
// Run: node --test tools/cdp_targets_test.mjs

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { pickTarget, isVisible } from './cdp_targets.mjs';

const page = (url, visible, attached = true) => ({
    type: 'page',
    url,
    description: JSON.stringify({ visible, attached }),
});

test('a visible page wins over a hidden one with the same prefix', () => {
    const list = [page('https://example.com/?t=1', false), page('https://example.com/?t=2', true)];
    const picked = pickTarget(list, 'https://example.com/');
    assert.equal(picked.target.url, 'https://example.com/?t=2');
    assert.equal(picked.hidden, false);
});

test('when only hidden pages match, the first one is used and flagged hidden', () => {
    const list = [page('https://example.com/?t=1', false), page('https://example.com/?t=2', false)];
    const picked = pickTarget(list, 'https://example.com/');
    assert.equal(picked.target.url, 'https://example.com/?t=1');
    assert.equal(picked.hidden, true);
});

test('a visible but detached page does not count as visible', () => {
    assert.equal(isVisible(page('https://a/', true, false)), false);
    const picked = pickTarget([page('https://a/', true, false)], 'https://a/');
    assert.equal(picked.hidden, true);
});

test('non-page targets and other URLs are ignored', () => {
    const list = [
        { type: 'service_worker', url: 'https://example.com/sw.js', description: '{}' },
        page('https://other.org/', true),
    ];
    assert.equal(pickTarget(list, 'https://example.com/'), null);
});

test('a missing or empty description counts as hidden', () => {
    const list = [{ type: 'page', url: 'https://example.com/' }];
    assert.equal(pickTarget(list, 'https://example.com/').hidden, true);
});
