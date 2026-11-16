function prostoProcedural(env, filters) {
    // Procedural cosmetic filters (adblock-rust ProceduralOrActionFilter objects):
    //   {selector: [{type, arg}, ...], action?: {type, arg?}}
    // Inlined into content_bootstrap.js as a function expression and called only for frames that have
    // such filters. It adds no globals and replaces no natives; the natives it needs arrive through
    // `env`, captured by the bootstrap before page scripts run. Hiding and `style` live in the cosmetic
    // stylesheet, one rule per token, keyed on a random marker attribute (the only per-element trace).
    // Every step is monotonic: when the time budget runs out it returns fewer nodes, never wrong ones.
    var doc = env.document, win = env.window, now = env.now, setTimer = env.setTimeout, getStyle = env.getComputedStyle;
    var BUDGET_MS = 10, BASE_DELAY_MS = 100, RETRY_MS = 40, MAX_NODES = 5000, MAX_FILTERS = 1000;
    var deadline = 0;
    var attr = 'a' + Math.random().toString(36).slice(2, 10);
    var steps = {}, actions = {}, has = Object.prototype.hasOwnProperty;

    function over() { return now() > deadline; }
    function unique(list) {
        var seen = new Set(), out = [];
        for (var i = 0; i < list.length && out.length < MAX_NODES; i++) {
            if (list[i] && !seen.has(list[i])) { seen.add(list[i]); out.push(list[i]); }
        }
        return out;
    }
    function toArray(nodeList) {
        var out = [];
        for (var i = 0; i < nodeList.length && i < MAX_NODES; i++) out.push(nodeList[i]);
        return out;
    }
    // fn returns a node, an array of nodes or null; stops early once the budget is spent.
    function flatMap(nodes, fn) {
        var out = [];
        for (var i = 0; i < nodes.length; i++) {
            if ((i & 31) === 31 && over()) break;
            var r = fn(nodes[i]);
            if (Array.isArray(r)) for (var j = 0; j < r.length; j++) out.push(r[j]);
            else if (r) out.push(r);
        }
        return unique(out);
    }
    function keep(nodes, test) {
        return flatMap(nodes, function(n) { return test(n) ? n : null; });
    }

    // `/re/flags` is a regular expression; anything else is literal text, with `*` as a wildcard
    // when `exact` (the whole value must match) and a plain substring test otherwise.
    function matcher(arg, exact) {
        var m = /^\/(.+)\/([imsu]*)$/.exec(arg);
        if (m) { var re = new RegExp(m[1], m[2]); return function(s) { return re.test(s); }; }
        if (!exact) return function(s) { return s.indexOf(arg) !== -1; };
        var glob = new RegExp('^' + arg.replace(/[.+?^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*') + '$');
        return function(s) { return glob.test(s); };
    }
    function unquote(s) {
        var m = /^(["'])([\s\S]*)\1$/.exec(s);
        return m ? m[2].replace(/\\(.)/g, '$1') : s;
    }

    steps['css-selector'] = function(arg, first) {
        if (first) return function() { return toArray(doc.querySelectorAll(arg)); };
        // Later segments are relative to the previous result: " .x" / "> .x" search inside,
        // "+ .x" / "~ .x" look at siblings, anything else narrows the set.
        var m = /^(\s*)([>+~]?)\s*([\s\S]*)$/.exec(arg), comb = m[2], rest = m[3];
        if (!comb && !m[1]) return function(nodes) { return keep(nodes, function(n) { return n.matches(arg); }); };
        if (comb === '+' || comb === '~') {
            if (/[\s>+~]/.test(rest)) return null;
            return function(nodes) {
                return flatMap(nodes, function(n) {
                    var out = [];
                    for (var s = n.nextElementSibling; s; s = comb === '~' ? s.nextElementSibling : null) {
                        if (s.matches(rest)) out.push(s);
                    }
                    return out;
                });
            };
        }
        var scoped = ':scope ' + (comb ? '> ' : '') + rest;
        return function(nodes) { return flatMap(nodes, function(n) { return toArray(n.querySelectorAll(scoped)); }); };
    };
    steps['has-text'] = function(arg) {
        var test = matcher(arg, false);
        return function(nodes) { return keep(nodes, function(n) { return test(n.textContent || ''); }); };
    };
    steps['min-text-length'] = function(arg) {
        var min = parseInt(arg, 10);
        if (!(min >= 0)) return null;
        return function(nodes) { return keep(nodes, function(n) { return (n.textContent || '').length >= min; }); };
    };
    steps['upward'] = function(arg) {
        if (/^\d+$/.test(arg)) {
            var levels = parseInt(arg, 10);
            if (levels < 1 || levels > 255) return null;
            return function(nodes) {
                return flatMap(nodes, function(n) {
                    for (var i = 0; i < levels && n; i++) n = n.parentElement;
                    return n;
                });
            };
        }
        return function(nodes) {
            return flatMap(nodes, function(n) { return n.parentElement && n.parentElement.closest(arg); });
        };
    };
    steps['xpath'] = function(arg) {
        return function(nodes) {
            return flatMap(nodes, function(n) {
                var snap = doc.evaluate(arg, n, null, 7, null), out = [];
                for (var i = 0; i < snap.snapshotLength; i++) {
                    var item = snap.snapshotItem(i);
                    if (item.nodeType === 1) out.push(item);
                }
                return out;
            });
        };
    };
    function matchesCss(pseudo) {
        return function(arg) {
            var colon = arg.indexOf(':');
            if (colon < 1) return null;
            var name = arg.slice(0, colon).trim(), test = matcher(arg.slice(colon + 1).trim(), true);
            return function(nodes) {
                return keep(nodes, function(n) { return test(getStyle(n, pseudo).getPropertyValue(name)); });
            };
        };
    }
    steps['matches-css'] = matchesCss(null);
    steps['matches-css-before'] = matchesCss('::before');
    steps['matches-css-after'] = matchesCss('::after');
    steps['matches-attr'] = function(arg) {
        var m = /^\s*("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|[^=]*?)\s*(?:=\s*([\s\S]*?))?\s*$/.exec(arg);
        if (!m || !m[1]) return null;
        var nameTest = matcher(unquote(m[1]), true);
        var valueTest = m[2] === undefined ? null : matcher(unquote(m[2]), true);
        return function(nodes) {
            return keep(nodes, function(n) {
                var names = n.getAttributeNames();
                for (var i = 0; i < names.length; i++) {
                    if (nameTest(names[i]) && (!valueTest || valueTest(n.getAttribute(names[i]) || ''))) return true;
                }
                return false;
            });
        };
    };
    steps['matches-path'] = function(arg) {
        var test = matcher(arg, false);
        return function(nodes) { return test(win.location.pathname + win.location.search) ? nodes : []; };
    };

    var rulesAdded = {};
    function marker(token, declarations) {
        return function(n) {
            if (n === doc.documentElement || n.localName === 'head') return;
            if (!rulesAdded[token]) {
                rulesAdded[token] = true;
                env.addRules('[' + attr + '~="' + token + '"]{' + declarations + '}');
            }
            var cur = n.getAttribute(attr);
            if (cur === null) n.setAttribute(attr, token);
            else if ((' ' + cur + ' ').indexOf(' ' + token + ' ') === -1) n.setAttribute(attr, cur + ' ' + token);
        };
    }
    // Action factories return the per-element applier, or null for an argument they cannot honour.
    actions['hide'] = function() { return marker('h', 'display:none!important'); };
    actions['style'] = function(arg, index) {
        if (/[{}<>\\]|\/\*|url\(|image-set\(|@import/i.test(arg)) return null;
        return marker('s' + index, arg);
    };
    actions['remove'] = function() {
        return function(n) {
            if (n.localName !== 'body' && n.localName !== 'head' && n !== doc.documentElement) n.remove();
        };
    };
    actions['remove-attr'] = function(arg) {
        if (!/^[A-Za-z_:][\w:.-]*$/.test(arg)) return null;
        return function(n) { if (n.hasAttribute(arg)) n.removeAttribute(arg); };
    };
    actions['remove-class'] = function(arg) {
        if (!/^[^\s"'\/]+$/.test(arg)) return null;
        return function(n) { if (n.classList.contains(arg)) n.classList.remove(arg); };
    };

    // Returns {run, dead, strikes} or null when any operator or the action is unknown or malformed.
    function compile(raw, index) {
        var parts = raw && raw.selector;
        if (!Array.isArray(parts) || !parts.length) return null;
        var chain = [];
        if (parts[0].type !== 'css-selector') {
            chain.push(parts[0].type === 'xpath' ? function() { return [doc]; } : function() { return toArray(doc.querySelectorAll('*')); });
        }
        for (var i = 0; i < parts.length; i++) {
            var op = parts[i];
            if (!op || typeof op.arg !== 'string' || !has.call(steps, op.type)) return null;
            var step = steps[op.type](op.arg, chain.length === 0);
            if (!step) return null;
            chain.push(step);
        }
        var act = raw.action, type = act ? act.type : 'hide';
        if (!has.call(actions, type) || (act && type !== 'remove' && typeof act.arg !== 'string')) return null;
        var apply = actions[type](act && act.arg, index);
        if (!apply) return null;
        return {
            dead: false,
            strikes: 0,
            run: function() {
                var nodes = [];
                for (var s = 0; s < chain.length; s++) {
                    nodes = chain[s](nodes);
                    if (!nodes.length) return;
                }
                for (var k = 0; k < nodes.length; k++) apply(nodes[k]);
            }
        };
    }

    var list = [], needsText = false, allAttrs = false, attrNames = ['class', 'id'];
    for (var f = 0; f < filters.length && f < MAX_FILTERS; f++) {
        try {
            var compiled = compile(filters[f], f);
            if (!compiled) continue;
            list.push(compiled);
            var ops = filters[f].selector, act = filters[f].action;
            for (var o = 0; o < ops.length; o++) {
                if (ops[o].type === 'has-text' || ops[o].type === 'min-text-length') needsText = true;
                if (ops[o].type === 'matches-attr') allAttrs = true;
                if (/^matches-css/.test(ops[o].type)) attrNames.push('style');
            }
            if (act && act.type === 'remove-attr') attrNames.push(act.arg);
        } catch (e) {}
    }
    if (!list.length) return;

    var live = list.length, timer = 0, cursor = 0, remaining = live, delay = BASE_DELAY_MS, observer = null;

    function schedule(ms) {
        if (!timer) timer = setTimer(tick, ms);
    }
    function tick() {
        timer = 0;
        if (observer) observer.takeRecords();
        var start = now(), ran = 0;
        deadline = start + BUDGET_MS;
        while (remaining > 0 && !over()) {
            var filter = list[cursor];
            cursor = (cursor + 1) % list.length;
            remaining--;
            if (filter.dead) continue;
            try { filter.run(); } catch (e) { filter.dead = true; live--; continue; }
            // A filter that alone overruns the budget three times in a row is too heavy for this page.
            if (over() && ran === 0 && ++filter.strikes > 2) { filter.dead = true; live--; }
            else if (!over()) filter.strikes = 0;
            ran++;
        }
        // Whatever this pass changed in the DOM is ours: drop those records so they trigger no new pass.
        if (observer) observer.takeRecords();
        if (!live) { if (observer) observer.disconnect(); return; }
        delay = Math.max(BASE_DELAY_MS, (now() - start) * 10);
        if (remaining > 0) schedule(RETRY_MS);
    }
    function kick() {
        remaining = list.length;
        schedule(delay);
    }

    try {
        observer = new env.MutationObserver(kick);
        var options = { childList: true, subtree: true, characterData: needsText, attributes: true };
        if (!allAttrs) options.attributeFilter = unique(attrNames);
        observer.observe(doc, options);
    } catch (e) { observer = null; }
    doc.addEventListener('DOMContentLoaded', kick, { once: true });
    win.addEventListener('load', kick, { once: true });
    kick();
}
