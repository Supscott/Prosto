// Chooses the CDP page target for a URL prefix (used by cdp_eval.mjs, PRO-171).
// Several WebView pages can match one prefix (one per open tab): the visible one wins, the same
// test `tools/device.sh visible` applies (description: visible and attached).

// True when the target's description (a JSON string from /json/list) says it is on screen.
export function isVisible(target) {
    let info = {};
    try {
        info = JSON.parse(target.description || '{}');
    } catch {
        info = {};
    }
    return Boolean(info.visible && info.attached);
}

// { target, hidden } for the first visible page whose URL starts with prefix, else for the first
// matching page with hidden: true; null when no page matches.
export function pickTarget(list, prefix) {
    const pages = list.filter(x => x.type === 'page' && x.url.startsWith(prefix));
    if (pages.length === 0) return null;
    const visible = pages.find(isVisible);
    return visible ? { target: visible, hidden: false } : { target: pages[0], hidden: true };
}
