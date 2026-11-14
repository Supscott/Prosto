(function() {
    // The bridge is a random-name JavascriptInterface global. Capture it and delete the property
    // before anything else so no page script can see or call it.
    var bridge = window['@@BRIDGE_NAME@@'];
    try { delete window['@@BRIDGE_NAME@@']; } catch (e) {}
    try {
        var h = (location.hostname || '').toLowerCase();
        var p = (location.pathname || '').toLowerCase();
        if (h === 'challenges.cloudflare.com' || h.endsWith('.challenges.cloudflare.com') ||
            h === 'cloudflare.com' || h.endsWith('.cloudflare.com') ||
            h === 'cloudflarecaptcha.com' || h.endsWith('.cloudflarecaptcha.com') ||
            h === 'hcaptcha.com' || h.endsWith('.hcaptcha.com') ||
            h === 'recaptcha.net' || h.endsWith('.recaptcha.net') ||
            h === 'arkoselabs.com' || h.endsWith('.arkoselabs.com') ||
            p.indexOf('/cdn-cgi/challenge-platform') !== -1 ||
            window._cf_chl_opt ||
            (document && (document.getElementById('challenge-stage') || document.getElementById('challenge-form')))) {
            return;
        }

        // ScreenOrientation lock bridge: WebView rejects screen.orientation.lock(), so
        // fullscreen players cannot rotate. The methods are swapped only while the
        // document is fullscreen; the rest of the time pages (and bot checks) see the
        // untouched native functions.
        try {
            var orientationProto = globalThis.ScreenOrientation && globalThis.ScreenOrientation.prototype;
            var orientationBridge = bridge;
            var nativeLock = orientationProto && Object.getOwnPropertyDescriptor(orientationProto, 'lock');
            var nativeUnlock = orientationProto && Object.getOwnPropertyDescriptor(orientationProto, 'unlock');
            if (orientationBridge && nativeLock && nativeUnlock) {
                var patchedLock = function lock(orientation) {
                    try { orientationBridge.lockOrientation(orientation || 'landscape'); } catch(e) {}
                    return Promise.resolve();
                };
                var patchedUnlock = function unlock() {
                    try { orientationBridge.unlockOrientation(); } catch(e) {}
                };
                var patchDescriptor = function(nativeDescriptor, value) {
                    return {
                        value: value,
                        writable: nativeDescriptor.writable,
                        enumerable: nativeDescriptor.enumerable,
                        configurable: nativeDescriptor.configurable
                    };
                };
                // Players call lock() right after requestFullscreen(), before the
                // document is fullscreen, so that call reaches the native lock and
                // fails. Like Chrome's media controls, rotate to landscape when the
                // fullscreen element shows a landscape video.
                var lockForVideo = function(video) {
                    if (video.videoWidth > video.videoHeight) {
                        try { orientationBridge.lockOrientation('landscape'); } catch(e) {}
                    }
                };
                var autoLockFullscreenVideo = function(element) {
                    var video = element.tagName === 'VIDEO' ? element : (element.querySelector && element.querySelector('video'));
                    if (!video) return;
                    if (video.videoWidth > 0) {
                        lockForVideo(video);
                    } else {
                        video.addEventListener('loadedmetadata', function() {
                            if (document.fullscreenElement || document.webkitFullscreenElement) lockForVideo(video);
                        }, { once: true });
                    }
                };
                var onFullscreenChange = function() {
                    var fullscreenElement = document.fullscreenElement || document.webkitFullscreenElement;
                    var isFullscreen = !!fullscreenElement;
                    try {
                        Object.defineProperty(orientationProto, 'lock', isFullscreen ? patchDescriptor(nativeLock, patchedLock) : nativeLock);
                        Object.defineProperty(orientationProto, 'unlock', isFullscreen ? patchDescriptor(nativeUnlock, patchedUnlock) : nativeUnlock);
                    } catch(e) {}
                    if (isFullscreen) {
                        try { autoLockFullscreenVideo(fullscreenElement); } catch(e) {}
                    } else {
                        try { orientationBridge.unlockOrientation(); } catch(e) {}
                    }
                };
                document.addEventListener('fullscreenchange', onFullscreenChange, true);
                document.addEventListener('webkitfullscreenchange', onFullscreenChange, true);
            }
        } catch(e) {}

        // Check if ad blocking is active via bridge
        var adBlockerActive = true;
        if (bridge && typeof bridge.isEnabled === 'function') {
            try {
                adBlockerActive = bridge.isEnabled();
            } catch(e) {}
        }

        // 1. YouTube: the clean-player link interception always runs; the ad pruning
        // inside it checks adBlockerActive.
        /*@@YOUTUBE_FAST_START@@*/

        // If ad blocking is toggled off, exit before applying cosmetic CSS
        if (!adBlockerActive) {
            return;
        }

        // 2. Cosmetic CSS: the list-driven chunks the bridge returns for this document. A constructed stylesheet is not subject to the page's style-src CSP;
        // a <style> element is the fallback.
        var cosmetic = {};
        try {
            if (bridge && typeof bridge.cosmetic === 'function') {
                cosmetic = JSON.parse(bridge.cosmetic(location.href)) || {};
            }
        } catch(e) {}

        // 3. Scriptlets: list-driven [name, ...args] calls, run synchronously here so they patch the
        // page's main world before any page script. The bundle is closure-local and is evaluated
        // only for frames that have scriptlets; unknown names are ignored.
        try {
            var scriptletCalls = cosmetic.scriptlets;
            if (scriptletCalls && scriptletCalls.length) {
                var scriptletMap = (/*@@SCRIPTLETS@@*/)();
                for (var si = 0; si < scriptletCalls.length; si++) {
                    try {
                        var call = scriptletCalls[si];
                        var known = call && Object.prototype.hasOwnProperty.call(scriptletMap, call[0]);
                        if (known && typeof scriptletMap[call[0]] === 'function') {
                            scriptletMap[call[0]].apply(null, call.slice(1));
                        }
                    } catch(e) {}
                }
            }
        } catch(e) {}

        var cssText = cosmetic.css && cosmetic.css.length ? cosmetic.css.join('\n') : '';

        var cosmeticSheet = null;
        var cosmeticStyle = null;
        function injectStyle() {
            try {
                if (!cosmeticStyle && typeof CSSStyleSheet === 'function' && 'adoptedStyleSheets' in document) {
                    if (!cosmeticSheet) {
                        cosmeticSheet = new CSSStyleSheet();
                        cosmeticSheet.replaceSync(cssText);
                    }
                    if (document.adoptedStyleSheets.indexOf(cosmeticSheet) === -1) {
                        document.adoptedStyleSheets = document.adoptedStyleSheets.concat([cosmeticSheet]);
                    }
                    return;
                }
            } catch(e) { cosmeticSheet = null; }
            if (cosmeticStyle && cosmeticStyle.isConnected) return;
            if (!cosmeticStyle) {
                cosmeticStyle = document.createElement('style');
                cosmeticStyle.textContent = cssText;
            }
            var target = document.head || document.documentElement;
            if (target) {
                target.appendChild(cosmeticStyle);
            } else {
                document.addEventListener('DOMContentLoaded', injectStyle);
            }
        }
        if (cssText) injectStyle();

        // 4. Generic cosmetics: the list's class/id based hide rules apply to whichever classes and
        // ids the page actually uses, so collect them (initial DOM, added nodes, class/id attribute
        // changes), ask the bridge in small batches and append the returned rules. Skipped when the
        // frame has no cosmetic answer (ads off, challenge page, non-http frame) or $generichide.
        if (cosmetic.generichide === false && bridge && typeof bridge.generic === 'function' && window.MutationObserver) {
            var GENERIC_BATCH_TOKENS = 250;
            var GENERIC_CALLS_PER_TICK = 4;
            var GENERIC_MAX_TOKENS = 5000;
            var GENERIC_FLUSH_MS = 150;
            var GENERIC_SCAN_BUDGET_MS = 8;
            var GENERIC_MAX_QUEUE = 2000;
            var GENERIC_MAX_TOKEN_CHARS = 100;

            var seenTokens = new Set();
            var newClasses = [];
            var newIds = [];
            var scanQueue = [];
            var scanHead = 0;
            var tickTimer = 0;
            var capped = false;
            var genericObserver = null;

            var noteToken = function(kind, token) {
                if (!token || token.length > GENERIC_MAX_TOKEN_CHARS) return;
                var key = kind + token;
                if (seenTokens.has(key)) return;
                seenTokens.add(key);
                if (kind === '.') newClasses.push(token); else newIds.push(token);
                if (seenTokens.size >= GENERIC_MAX_TOKENS) capped = true;
            };

            var scanElement = function(el) {
                var id = el.id;
                if (typeof id === 'string' && id && !/\s/.test(id)) noteToken('#', id);
                var list = el.classList;
                if (list) {
                    for (var i = 0; i < list.length; i++) noteToken('.', list[i]);
                }
            };

            var scanTree = function(root) {
                if (capped) return;
                if (root.nodeType === 1) scanElement(root);
                if (!root.querySelectorAll) return;
                var all = root.querySelectorAll('[class],[id]');
                for (var i = 0; i < all.length && !capped; i++) scanElement(all[i]);
            };

            var appendGenericRules = function(text) {
                injectStyle();
                if (cosmeticSheet) {
                    var rules = text.split('\n');
                    for (var i = 0; i < rules.length; i++) {
                        try { cosmeticSheet.insertRule(rules[i], cosmeticSheet.cssRules.length); } catch(e) {}
                    }
                } else if (cosmeticStyle) {
                    cosmeticStyle.appendChild(document.createTextNode(text));
                }
            };

            var flushTokens = function() {
                var classes = newClasses.splice(0, GENERIC_BATCH_TOKENS);
                var ids = newIds.splice(0, GENERIC_BATCH_TOKENS - classes.length);
                if (!classes.length && !ids.length) return;
                try {
                    var css = bridge.generic(location.href, classes.join(' '), ids.join(' '));
                    if (typeof css === 'string' && css.length) appendGenericRules(css);
                } catch(e) {}
            };

            var tick = function() {
                tickTimer = 0;
                var start = Date.now();
                while (scanHead < scanQueue.length && Date.now() - start < GENERIC_SCAN_BUDGET_MS) {
                    scanTree(scanQueue[scanHead++]);
                }
                if (scanHead >= scanQueue.length) { scanQueue = []; scanHead = 0; }
                for (var n = 0; n < GENERIC_CALLS_PER_TICK && (newClasses.length || newIds.length); n++) flushTokens();
                if (capped && genericObserver && scanHead >= scanQueue.length) {
                    genericObserver.disconnect();
                    genericObserver = null;
                }
                if (scanHead < scanQueue.length || newClasses.length || newIds.length) scheduleTick();
            };

            var scheduleTick = function() {
                if (!tickTimer) tickTimer = setTimeout(tick, GENERIC_FLUSH_MS);
            };

            var queueTree = function(node) {
                if (scanQueue.length - scanHead < GENERIC_MAX_QUEUE) scanQueue.push(node);
            };

            genericObserver = new MutationObserver(function(records) {
                if (capped) return;
                for (var i = 0; i < records.length; i++) {
                    var record = records[i];
                    if (record.type === 'attributes') {
                        scanElement(record.target);
                    } else {
                        var added = record.addedNodes;
                        for (var j = 0; j < added.length; j++) {
                            if (added[j].nodeType === 1) queueTree(added[j]);
                        }
                    }
                }
                scheduleTick();
            });
            genericObserver.observe(document, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['class', 'id']
            });

            var scanWholeDocument = function() {
                if (document.documentElement) queueTree(document.documentElement);
                scheduleTick();
            };
            scanWholeDocument();
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', scanWholeDocument);
            }
        }
    } catch(e) {}
})();
