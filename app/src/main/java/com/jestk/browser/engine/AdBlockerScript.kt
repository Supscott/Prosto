package com.jestk.browser.engine

import android.util.Log
import android.webkit.WebView

/**
 * Provides client-side ad-blocking logic including:
 * 1. Cosmetic filtering CSS to collapse ad slots and placeholders to 0 dimensions.
 * 2. Scriptlet surrogates (mocks) for common ad network SDKs (Google AdSense, GPT, Yandex) to stop infinite loading spinners.
 * 3. DOM Sanitizer utilizing MutationObserver to hide dynamic ad wrappers and single-child ad parent containers.
 */
object AdBlockerScript {

    private const val TAG = "AdBlockerScript"

    /**
     * Common ad container and slot CSS selectors.
     */
    val AD_SELECTORS = listOf(
        // Google AdSense & DoubleClick
        "ins.adsbygoogle",
        ".adsbygoogle",
        "[id^=\"google_ads_iframe\"]",
        "[id^=\"div-gpt-ad\"]",
        "[data-google-query-id]",
        "[data-ad-client]",
        "[data-ad-slot]",
        "[data-ad-unit]",
        "[data-ad-format]",
        "iframe[src*=\"googlesyndication.com\"]",
        "iframe[src*=\"doubleclick.net\"]",

        // Yandex Direct, ADFOX & RTB
        "[id^=\"adfox\"]",
        "div[id^=\"adfox\"]",
        "[class*=\"adfox\"]",
        "[data-adfox]",
        ".yandex-adaptive",
        "[id^=\"yandex_rtb_\"]",
        "[id^=\"yandex_\"]",
        "[id^=\"ya_partner\"]",
        ".ya-partner",
        "[class*=\"ya-distr\"]",
        "div[data-ya-unit]",

        // Universal ad slots, banners, and sponsored containers (including Next.js CSS module patterns)
        ".ad-container",
        ".ad-wrapper",
        ".ad-box",
        ".ad-banner",
        ".ad-slot",
        ".ad-header",
        ".ad-footer",
        ".ad-sidebar",
        ".advertisement",
        ".banner-ad",
        ".sponsor-container",
        "[class*=\"ad-slot\"]",
        "[class*=\"ad_slot\"]",
        "[class*=\"sponsored-post\"]",
        "[class*=\"SponsoredMessage\"]",
        "[class*=\"BannerWrapper\"]",
        "[class*=\"advert-banner\"]",
        "[class*=\"native-ad\"]",
        "[data-ad-placeholder]",
        "[data-advertisement]",
        "[class*=\"bannerContainer\"]",
        "[class*=\"banner_container\"]",
        "[class*=\"banner-container\"]",
        "[class*=\"incontentBanner\"]",
        "[class*=\"IncontentBanner\"]",
        "[class*=\"incontent_banner\"]",
        "[class*=\"RightsideBanners\"]",
        "[class*=\"BannersBlock\"]",
        "[class*=\"adFeedback\"]",
        "[class*=\"banner-wrapper\"]",
        "[class*=\"banner_wrapper\"]",
        "[class*=\"bannerWrap\"]",
        "[class*=\"banner-block\"]",
        "[class*=\"banner_block\"]",
        "[class*=\"bannerBlock\"]",
        "[class*=\"tovarnayaCourusel\"]",
        "[class*=\"tovarnayaCarousel\"]"
    )

    /**
     * Builds the raw CSS string for cosmetic filtering.
     */
    fun buildCosmeticCss(): String {
        val selectorString = AD_SELECTORS.joinToString(",\n")
        return """
            $selectorString {
                display: none !important;
                height: 0 !important;
                min-height: 0 !important;
                max-height: 0 !important;
                margin: 0 !important;
                padding: 0 !important;
                visibility: hidden !important;
                opacity: 0 !important;
                pointer-events: none !important;
            }
        """.trimIndent()
    }

    /**
     * Builds the YouTube scriptlet (YouTube hosts only):
     * 1. Clean-player link interception (clicks and `history.pushState/replaceState`), always on.
     * 2. When the ad blocker is on (`adBlockerActive` from the enclosing document-start script),
     *    prunes adPlacements, playerAds, adSlots and adBreakHeartbeatParams from
     *    `ytInitialPlayerResponse`, `ytInitialData`, `ytplayer`, `JSON.parse`,
     *    `Response.prototype.json/text` and XHR `responseText/response`, and fast-forwards
     *    fallback ad breaks.
     */
    fun buildYouTubeFastStartScript(): String {
        return """
            (function() {
                try {
                    var host = location.hostname || '';
                    if (host.indexOf('youtube.com') === -1 && host.indexOf('youtu.be') === -1) {
                        return;
                    }

                    // Clean YouTube Player SPA & Link Interceptor
                    function tryCleanPlayer(targetUrl) {
                        if (!window.HoriAdBlockBridge || typeof window.HoriAdBlockBridge.openCleanPlayer !== 'function') {
                            return false;
                        }
                        try {
                            var handled = window.HoriAdBlockBridge.openCleanPlayer(targetUrl);
                            if (handled) {
                                var vids = document.querySelectorAll('video');
                                for (var i = 0; i < vids.length; i++) {
                                    try { vids[i].pause(); } catch(e) {}
                                }
                                return true;
                            }
                        } catch(e) {}
                        return false;
                    }

                    document.addEventListener('click', function(e) {
                        try {
                            var target = e.target;
                            while (target && target.tagName !== 'A' && target !== document.body) {
                                target = target.parentElement;
                            }
                            if (target && target.tagName === 'A' && target.href) {
                                var href = target.href;
                                if (href.indexOf('/watch') !== -1 || href.indexOf('/shorts/') !== -1 || href.indexOf('youtu.be/') !== -1) {
                                    if (tryCleanPlayer(href)) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        e.stopImmediatePropagation();
                                    }
                                }
                            }
                        } catch(err) {}
                    }, true);

                    var origPushState = history.pushState;
                    history.pushState = function(state, unused, url) {
                        if (url) {
                            var fullUrl = typeof url === 'string' ? (url.indexOf('http') === 0 ? url : (location.origin || '') + (url.indexOf('/') === 0 ? '' : '/') + url) : '';
                            if (fullUrl && (fullUrl.indexOf('/watch') !== -1 || fullUrl.indexOf('/shorts/') !== -1)) {
                                if (tryCleanPlayer(fullUrl)) {
                                    return;
                                }
                            }
                        }
                        return origPushState.apply(this, arguments);
                    };

                    var origReplaceState = history.replaceState;
                    history.replaceState = function(state, unused, url) {
                        if (url) {
                            var fullUrl = typeof url === 'string' ? (url.indexOf('http') === 0 ? url : (location.origin || '') + (url.indexOf('/') === 0 ? '' : '/') + url) : '';
                            if (fullUrl && (fullUrl.indexOf('/watch') !== -1 || fullUrl.indexOf('/shorts/') !== -1)) {
                                if (tryCleanPlayer(fullUrl)) {
                                    return;
                                }
                            }
                        }
                        return origReplaceState.apply(this, arguments);
                    };

                    if (typeof adBlockerActive !== 'undefined' && !adBlockerActive) {
                        return;
                    }

                    // 1. Prune ad properties from player responses recursively (including array streams)
                    function pruneAdProps(obj, depth) {
                        if (!obj || typeof obj !== 'object') return obj;
                        depth = depth || 0;
                        if (depth > 10) return obj;
                        try {
                            if (Array.isArray(obj)) {
                                for (var i = 0; i < obj.length; i++) {
                                    pruneAdProps(obj[i], depth + 1);
                                }
                                return obj;
                            }
                            if ('adPlacements' in obj) delete obj.adPlacements;
                            if ('playerAds' in obj) delete obj.playerAds;
                            if ('adSlots' in obj) delete obj.adSlots;
                            if ('adBreakHeartbeatParams' in obj) delete obj.adBreakHeartbeatParams;
                            if (obj.playerResponse && typeof obj.playerResponse === 'object') {
                                pruneAdProps(obj.playerResponse, depth + 1);
                            }
                            if (obj.watchNextResponse && typeof obj.watchNextResponse === 'object') {
                                pruneAdProps(obj.watchNextResponse, depth + 1);
                            }
                            if (obj.response && typeof obj.response === 'object') {
                                pruneAdProps(obj.response, depth + 1);
                            }
                        } catch(e) {}
                        return obj;
                    }

                    function pruneRawText(text) {
                        if (typeof text !== 'string') return text;
                        if (text.indexOf('adPlacements') !== -1 || text.indexOf('playerAds') !== -1 || text.indexOf('adSlots') !== -1) {
                            return text.replace(/"adPlacements"/g, '"no_adPlacements"')
                                       .replace(/"playerAds"/g, '"no_playerAds"')
                                       .replace(/"adSlots"/g, '"no_adSlots"')
                                       .replace(/"adBreakHeartbeatParams"/g, '"no_adBreakHeartbeatParams"');
                        }
                        return text;
                    }

                    var _initialPlayerResp = undefined;
                    try {
                        Object.defineProperty(window, 'ytInitialPlayerResponse', {
                            get: function() { return _initialPlayerResp; },
                            set: function(val) { _initialPlayerResp = pruneAdProps(val); },
                            configurable: true,
                            enumerable: true
                        });
                    } catch(e) {}

                    var _initialData = undefined;
                    try {
                        Object.defineProperty(window, 'ytInitialData', {
                            get: function() { return _initialData; },
                            set: function(val) { _initialData = pruneAdProps(val); },
                            configurable: true,
                            enumerable: true
                        });
                    } catch(e) {}

                    var _ytplayer = undefined;
                    try {
                        Object.defineProperty(window, 'ytplayer', {
                            get: function() { return _ytplayer; },
                            set: function(val) {
                                try {
                                    if (val && val.config && val.config.args) {
                                        if (typeof val.config.args.raw_player_response === 'string') {
                                            var p = JSON.parse(val.config.args.raw_player_response);
                                            pruneAdProps(p);
                                            val.config.args.raw_player_response = JSON.stringify(p);
                                        }
                                        if (typeof val.config.args.player_response === 'string') {
                                            var p2 = JSON.parse(val.config.args.player_response);
                                            pruneAdProps(p2);
                                            val.config.args.player_response = JSON.stringify(p2);
                                        }
                                    }
                                } catch(e) {}
                                _ytplayer = val;
                            },
                            configurable: true,
                            enumerable: true
                        });
                    } catch(e) {}

                    try {
                        var origParse = JSON.parse;
                        JSON.parse = function() {
                            var res = origParse.apply(this, arguments);
                            if (res && typeof res === 'object') {
                                pruneAdProps(res);
                            }
                            return res;
                        };
                    } catch(e) {}

                    try {
                        if (window.Response && Response.prototype) {
                            if (Response.prototype.json) {
                                var origJson = Response.prototype.json;
                                Response.prototype.json = function() {
                                    return origJson.apply(this, arguments).then(function(data) {
                                        if (data && typeof data === 'object') {
                                            pruneAdProps(data);
                                        }
                                        return data;
                                    });
                                };
                            }
                            if (Response.prototype.text) {
                                var origText = Response.prototype.text;
                                Response.prototype.text = function() {
                                    var url = this.url || '';
                                    var needsPrune = url.indexOf('/player') !== -1 || url.indexOf('/get_watch') !== -1 || url.indexOf('/watch') !== -1 || url.indexOf('youtubei') !== -1;
                                    return origText.apply(this, arguments).then(function(t) {
                                        return (needsPrune || (t && t.indexOf('adPlacements') !== -1)) ? pruneRawText(t) : t;
                                    });
                                };
                            }
                        }
                    } catch(e) {}

                    try {
                        if (window.XMLHttpRequest && XMLHttpRequest.prototype) {
                            var origXhrOpen = XMLHttpRequest.prototype.open;
                            XMLHttpRequest.prototype.open = function(method, url) {
                                this.__yt_url = typeof url === 'string' ? url : '';
                                return origXhrOpen.apply(this, arguments);
                            };

                            var origDescText = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText');
                            if (origDescText && origDescText.get) {
                                Object.defineProperty(XMLHttpRequest.prototype, 'responseText', {
                                    get: function() {
                                        var val = origDescText.get.call(this);
                                        var url = this.__yt_url || '';
                                        if (url.indexOf('/player') !== -1 || url.indexOf('/get_watch') !== -1 || url.indexOf('youtubei') !== -1 || (val && val.indexOf('adPlacements') !== -1)) {
                                            return pruneRawText(val);
                                        }
                                        return val;
                                    },
                                    configurable: true,
                                    enumerable: true
                                });
                            }

                            var origDescResp = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'response');
                            if (origDescResp && origDescResp.get) {
                                Object.defineProperty(XMLHttpRequest.prototype, 'response', {
                                    get: function() {
                                        var val = origDescResp.get.call(this);
                                        if (typeof val === 'string') {
                                            var url = this.__yt_url || '';
                                            if (url.indexOf('/player') !== -1 || url.indexOf('/get_watch') !== -1 || url.indexOf('youtubei') !== -1 || val.indexOf('adPlacements') !== -1) {
                                                return pruneRawText(val);
                                            }
                                        } else if (val && typeof val === 'object') {
                                            pruneAdProps(val);
                                        }
                                        return val;
                                    },
                                    configurable: true,
                                    enumerable: true
                                });
                            }
                        }
                    } catch(e) {}

                    // 2. Fallback ad-skip accelerator for ad overlays (desktop & mobile)
                    function skipAdSlot() {
                        try {
                            var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-overlay-close-button, button.ytp-ad-skip-button, button[aria-label*="Skip"], button[class*="ytp-ad-skip-button"]');
                            if (skipBtn) {
                                skipBtn.click();
                            }
                            var adVideos = document.querySelectorAll('.ad-showing video, .ad-interrupting video');
                            for (var i = 0; i < adVideos.length; i++) {
                                var v = adVideos[i];
                                try {
                                    v.muted = true;
                                    v.playbackRate = 16.0;
                                    if (!isNaN(v.duration) && v.duration > 0 && !isNaN(v.currentTime)) {
                                        v.currentTime = v.duration;
                                    }
                                } catch(e) {}
                            }
                        } catch(e) {}
                    }

                    var hookedVideos = new WeakSet();
                    function monitorVideo(v) {
                        if (!v || hookedVideos.has(v)) return;
                        hookedVideos.add(v);
                        v.addEventListener('timeupdate', function() {
                            if (document.querySelector('.ad-showing, .ad-interrupting')) {
                                skipAdSlot();
                            }
                        });
                    }

                    function initVideos() {
                        var videos = document.querySelectorAll('video');
                        for (var i = 0; i < videos.length; i++) {
                            monitorVideo(videos[i]);
                        }
                    }

                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', initVideos);
                    } else {
                        initVideos();
                    }

                    if (window.MutationObserver && (document.documentElement || document.body)) {
                        var ytObserver = new MutationObserver(function(mutations) {
                            skipAdSlot();
                            for (var i = 0; i < mutations.length; i++) {
                                var added = mutations[i].addedNodes;
                                if (!added) continue;
                                for (var j = 0; j < added.length; j++) {
                                    var n = added[j];
                                    if (!n || n.nodeType !== 1) continue;
                                    if (n.tagName === 'VIDEO') {
                                        monitorVideo(n);
                                    } else if (n.querySelectorAll) {
                                        var vList = n.querySelectorAll('video');
                                        for (var k = 0; k < vList.length; k++) {
                                            monitorVideo(vList[k]);
                                        }
                                    }
                                }
                            }
                        });
                        ytObserver.observe(document.documentElement || document.body, {
                            childList: true,
                            subtree: true
                        });
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }

    /**
     * Builds the complete combined document start script including:
     * - Surrogates (Google AdSense, GPT, Yandex)
     * - YouTube Fast-Start (JSON player response pruning & ad skip accelerator)
     * - Immediate style element creation
     * - MutationObserver to collapse dynamic ad containers and empty parent wrappers
     */
    fun buildDocumentStartScript(): String {
        val cssContent = buildCosmeticCss().replace("\\", "\\\\").replace("`", "\\`").replace("\n", " ")
        val ytScript = buildYouTubeFastStartScript()
        return """
            (function() {
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
                        var orientationBridge = window.HoriAdBlockBridge;
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
                            var onFullscreenChange = function() {
                                var isFullscreen = !!(document.fullscreenElement || document.webkitFullscreenElement);
                                try {
                                    Object.defineProperty(orientationProto, 'lock', isFullscreen ? patchDescriptor(nativeLock, patchedLock) : nativeLock);
                                    Object.defineProperty(orientationProto, 'unlock', isFullscreen ? patchDescriptor(nativeUnlock, patchedUnlock) : nativeUnlock);
                                } catch(e) {}
                                if (!isFullscreen) {
                                    try { orientationBridge.unlockOrientation(); } catch(e) {}
                                }
                            };
                            document.addEventListener('fullscreenchange', onFullscreenChange, true);
                            document.addEventListener('webkitfullscreenchange', onFullscreenChange, true);
                        }
                    } catch(e) {}

                    // Check if ad blocking is active via bridge
                    var adBlockerActive = true;
                    if (window.HoriAdBlockBridge && typeof window.HoriAdBlockBridge.isEnabled === 'function') {
                        try {
                            adBlockerActive = window.HoriAdBlockBridge.isEnabled();
                        } catch(e) {}
                    }

                    // 1. YouTube: the clean-player link interception always runs; the ad pruning
                    // inside it checks adBlockerActive.
                    $ytScript

                    // If ad blocking is toggled off, exit before applying cosmetic CSS and DOM cleaners
                    if (!adBlockerActive) {
                        return;
                    }

                    // 3. Inject Cosmetic CSS Style Tag
                    var cosmeticStyle = null;
                    function injectStyle() {
                        if (cosmeticStyle && cosmeticStyle.isConnected) return;
                        if (!cosmeticStyle) {
                            cosmeticStyle = document.createElement('style');
                            cosmeticStyle.textContent = `$cssContent`;
                        }
                        var target = document.head || document.documentElement;
                        if (target) {
                            target.appendChild(cosmeticStyle);
                        } else {
                            document.addEventListener('DOMContentLoaded', injectStyle);
                        }
                    }
                    injectStyle();

                    // 3. Dynamic DOM Cleaner / MutationObserver
                    var AD_SELECTOR = 'ins.adsbygoogle, [id^="google_ads_iframe"], [id^="div-gpt-ad"], [id^="yandex_rtb_"], [id^="adfox"], [class*="bannerContainer"], [class*="incontentBanner"], [class*="BannersBlock"], [class*="RightsideBanners"], [class*="adFeedback"], [class*="tovarnayaCourusel"], .yandex-adaptive, .ya-partner, .ad-container, .ad-banner, .advertisement, [data-ad-placeholder]';

                    function collapseElement(el) {
                        try {
                            el.style.setProperty('display', 'none', 'important');
                            el.style.setProperty('height', '0', 'important');
                            el.style.setProperty('min-height', '0', 'important');
                            var parent = el.parentElement;
                            if (parent && parent.children.length === 1 && !parent.textContent.trim()) {
                                parent.style.setProperty('display', 'none', 'important');
                                parent.style.setProperty('height', '0', 'important');
                                parent.style.setProperty('min-height', '0', 'important');
                            }
                        } catch(e) {}
                    }

                    var pendingNodes = [];
                    var debounceTimer = null;

                    function processPendingNodes() {
                        debounceTimer = null;
                        if (!pendingNodes.length) return;
                        var nodes = pendingNodes;
                        pendingNodes = [];
                        try {
                            for (var i = 0; i < nodes.length; i++) {
                                var node = nodes[i];
                                if (!node || node.nodeType !== 1) continue;
                                if (node.matches && node.matches(AD_SELECTOR)) {
                                    collapseElement(node);
                                }
                                if (node.querySelectorAll) {
                                    var matches = node.querySelectorAll(AD_SELECTOR);
                                    for (var j = 0; j < matches.length; j++) {
                                        collapseElement(matches[j]);
                                    }
                                }
                            }
                        } catch(err) {}
                    }

                    function scheduleSanitize() {
                        if (debounceTimer !== null) return;
                        if (window.requestAnimationFrame) {
                            debounceTimer = window.requestAnimationFrame(processPendingNodes);
                        } else {
                            debounceTimer = setTimeout(processPendingNodes, 100);
                        }
                    }

                    if (window.MutationObserver && (document.documentElement || document.body)) {
                        var observer = new MutationObserver(function(mutations) {
                            injectStyle();
                            for (var i = 0; i < mutations.length; i++) {
                                var added = mutations[i].addedNodes;
                                if (added) {
                                    for (var j = 0; j < added.length; j++) {
                                        if (added[j].nodeType === 1) {
                                            pendingNodes.push(added[j]);
                                        }
                                    }
                                }
                            }
                            if (pendingNodes.length > 0) {
                                scheduleSanitize();
                            }
                        });
                        observer.observe(document.documentElement || document.body, {
                            childList: true,
                            subtree: true
                        });
                    }

                    function sanitizeInitial() {
                        injectStyle();
                        var root = document.body || document.documentElement;
                        if (root) {
                            pendingNodes.push(root);
                            processPendingNodes();
                        }
                    }

                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', sanitizeInitial);
                    } else {
                        sanitizeInitial();
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }

    /**
     * Evaluates the ad blocker script on the provided WebView safely.
     */
    fun injectAdBlocker(webView: WebView) {
        try {
            val script = buildDocumentStartScript()
            webView.evaluateJavascript(script, null)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to inject ad blocker script into WebView", t)
        }
    }

    /**
     * Builds the Autoconsent Scriptlet for automated dismissal and rejection of cookie banners.
     * Follows a 3-tier hierarchy:
     * 1. Locate and click "Reject All" / "Отклонить все" / "Refuse" / "Deny".
     * 2. Locate and click "Necessary only" / "Только необходимые" / "Essential".
     * 3. Fallback: hide CMP banner & backdrop elements, and unlock document.body and document.documentElement scroll styles.
     * Prevents infinite loops and re-triggering via data-hori-consent-handled attribute.
     */
    fun buildAutoConsentScript(): String {
        return """
            (function() {
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

                    var CMP_CONTAINER_SELECTORS = [
                        '#onetrust-consent-sdk',
                        '#onetrust-banner-sdk',
                        '#CybotCookiebotDialog',
                        '#didomi-host',
                        '#didomi-notice',
                        '#didomi-consent-popup',
                        '.qc-cmp2-container',
                        '.qc-cmp-cleanslate',
                        '.iubenda-cs-container',
                        '[data-testid="gdpr-popup"]',
                        '#truste-consent-track',
                        '#cmpbox'
                    ];

                    // Generic banners are only clicked through, never hidden: the selectors are
                    // broad enough to match unrelated page elements.
                    var GENERIC_BANNER_SELECTORS = [
                        '[class*="cookie-warning"]',
                        '[class*="cookie-banner"]',
                        '[class*="cookie_banner"]',
                        '[class*="CookieBanner"]',
                        '[id*="cookie-banner"]',
                        '[id*="cookie-notice"]',
                        '[class*="cookie-consent"]',
                        '[class*="cookie_consent"]',
                        '[class*="CookieConsent"]',
                        '[id*="cookie-consent"]'
                    ];

                    var REJECT_SELECTORS = [
                        '#onetrust-reject-all-handler',
                        '.ot-pc-refuse-all-handler',
                        '#CybotCookiebotDialogBodyLevelButtonLevelOptinDeclineAll',
                        '#CybotCookiebotDialogBodyButtonDecline',
                        '#didomi-notice-disagree-button',
                        'button[id*="didomi-notice-disagree"]',
                        '.iubenda-cs-reject-btn',
                        'button.iubenda-cs-reject-btn',
                        'button[mode="disagree"]',
                        'button[id*="reject" i]',
                        'button[class*="reject" i]',
                        'button[data-testid*="reject" i]',
                        'button[aria-label*="reject" i]',
                        'a[id*="reject" i]',
                        'a[class*="reject" i]',
                        '[data-action="reject"]',
                        '[data-action="decline"]',
                        '[data-action="refuse"]'
                    ];

                    var NECESSARY_SELECTORS = [
                        'button[id*="necessary" i]',
                        'button[id*="essential" i]',
                        'button[class*="necessary" i]',
                        'button[class*="essential" i]',
                        'button[data-testid*="necessary" i]',
                        'button[data-testid*="essential" i]',
                        '[data-action="necessary"]',
                        '[data-action="essential"]'
                    ];

                    var BACKDROP_SELECTORS = [
                        '#onetrust-pc-dark-filter',
                        '.onetrust-pc-dark-filter',
                        '.qc-cmp2-ui-backdrop',
                        '.didomi-popup-backdrop',
                        '[class*="cookie"][class*="backdrop"]',
                        '[class*="consent"][class*="backdrop"]',
                        '[class*="cookie-overlay"]',
                        '[class*="cookie_overlay"]'
                    ];

                    var PRIORITY_1_TEXTS = [
                        'reject all', 'reject', 'refuse all', 'refuse', 'deny all', 'deny',
                        'decline all', 'decline', 'disagree', 'do not accept', 'opt out',
                        'отклонить все', 'отклонить всё', 'отклонить', 'отказаться от всех',
                        'отказаться', 'не согласен', 'запретить все', 'запретить', 'не принимать'
                    ];

                    var PRIORITY_2_TEXTS = [
                        'necessary only', 'only necessary', 'essential only', 'only essential',
                        'save preferences', 'save settings', 'strictly necessary', 'reject optional',
                        'use necessary', 'только необходимые', 'необходимые', 'только обязательные',
                        'обязательные', 'сохранить выбор', 'сохранить настройки', 'принять только необходимые'
                    ];

                    function clickEl(el) {
                        try {
                            el.click();
                            return true;
                        } catch(e) {}
                        try {
                            var evt = new MouseEvent('click', { bubbles: true, cancelable: true, view: window });
                            el.dispatchEvent(evt);
                            return true;
                        } catch(e) {}
                        return false;
                    }

                    function unlockScroll() {
                        try {
                            var targets = [document.documentElement, document.body];
                            for (var i = 0; i < targets.length; i++) {
                                var el = targets[i];
                                if (!el) continue;
                                el.style.setProperty('overflow', 'auto', 'important');
                                el.style.setProperty('overflow-y', 'auto', 'important');
                                el.style.setProperty('overflow-x', 'auto', 'important');
                                el.style.setProperty('touch-action', 'auto', 'important');
                                el.style.removeProperty('max-height');
                                el.style.removeProperty('height');
                                var lockClasses = ['modal-open', 'overflow-hidden', 'no-scroll', 'didomi-popup-open', 'ot-pc-open', 'ot-no-scroll', 'has-cookie-bar', 'cookie-banner-open'];
                                for (var j = 0; j < lockClasses.length; j++) {
                                    el.classList.remove(lockClasses[j]);
                                }
                            }
                        } catch(err) {}
                    }

                    function hideBackdrops() {
                        try {
                            var backdropSelector = BACKDROP_SELECTORS.join(', ');
                            var backdrops = document.querySelectorAll(backdropSelector);
                            for (var b = 0; b < backdrops.length; b++) {
                                backdrops[b].style.setProperty('display', 'none', 'important');
                                backdrops[b].style.setProperty('visibility', 'hidden', 'important');
                                backdrops[b].style.setProperty('pointer-events', 'none', 'important');
                            }
                        } catch(err) {}
                    }

                    function matchesTextList(text, textList) {
                        if (!text) return false;
                        var lower = text.trim().toLowerCase();
                        for (var i = 0; i < textList.length; i++) {
                            var candidate = textList[i];
                            if (lower === candidate || lower.indexOf(candidate) === 0 || lower.indexOf(' ' + candidate) !== -1) {
                                return true;
                            }
                        }
                        return false;
                    }

                    function findButtonByText(container, textList) {
                        try {
                            var candidates = container.querySelectorAll('button, [role="button"], a.btn, a[class*="button" i], input[type="button"], input[type="submit"]');
                            for (var i = 0; i < candidates.length; i++) {
                                var c = candidates[i];
                                if (c.getAttribute('data-hori-consent-handled') === 'true') continue;
                                var txt = (c.innerText || c.textContent || c.value || '').trim();
                                if (matchesTextList(txt, textList)) {
                                    return c;
                                }
                            }
                        } catch(e) {}
                        return null;
                    }

                    function processContainer(container, allowHideFallback) {
                        if (!container) return false;
                        if (container.getAttribute('data-hori-consent-handled') === 'true') return false;

                        // Priority 1: Reject All selectors
                        for (var i = 0; i < REJECT_SELECTORS.length; i++) {
                            var btn = container.querySelector(REJECT_SELECTORS[i]);
                            if (btn && btn.getAttribute('data-hori-consent-handled') !== 'true') {
                                btn.setAttribute('data-hori-consent-handled', 'true');
                                container.setAttribute('data-hori-consent-handled', 'true');
                                clickEl(btn);
                                hideBackdrops();
                                unlockScroll();
                                return true;
                            }
                        }

                        // Priority 1: Text search inside container
                        var textRejectBtn = findButtonByText(container, PRIORITY_1_TEXTS);
                        if (textRejectBtn) {
                            textRejectBtn.setAttribute('data-hori-consent-handled', 'true');
                            container.setAttribute('data-hori-consent-handled', 'true');
                            clickEl(textRejectBtn);
                            hideBackdrops();
                            unlockScroll();
                            return true;
                        }

                        // Priority 2: Necessary Only selectors
                        for (var j = 0; j < NECESSARY_SELECTORS.length; j++) {
                            var necBtn = container.querySelector(NECESSARY_SELECTORS[j]);
                            if (necBtn && necBtn.getAttribute('data-hori-consent-handled') !== 'true') {
                                necBtn.setAttribute('data-hori-consent-handled', 'true');
                                container.setAttribute('data-hori-consent-handled', 'true');
                                clickEl(necBtn);
                                hideBackdrops();
                                unlockScroll();
                                return true;
                            }
                        }

                        // Priority 2: Text search inside container
                        var textNecBtn = findButtonByText(container, PRIORITY_2_TEXTS);
                        if (textNecBtn) {
                            textNecBtn.setAttribute('data-hori-consent-handled', 'true');
                            container.setAttribute('data-hori-consent-handled', 'true');
                            clickEl(textNecBtn);
                            hideBackdrops();
                            unlockScroll();
                            return true;
                        }

                        // Priority 3: Fallback DOM hiding + Scroll Unlock (known CMPs only)
                        if (!allowHideFallback) return false;
                        container.setAttribute('data-hori-consent-handled', 'true');
                        container.style.setProperty('display', 'none', 'important');
                        container.style.setProperty('visibility', 'hidden', 'important');
                        container.style.setProperty('pointer-events', 'none', 'important');
                        container.style.setProperty('height', '0', 'important');
                        hideBackdrops();
                        unlockScroll();
                        return true;
                    }

                    function scanPage() {
                        try {
                            var handledAny = false;
                            var groups = [[CMP_CONTAINER_SELECTORS, true], [GENERIC_BANNER_SELECTORS, false]];
                            for (var g = 0; g < groups.length; g++) {
                                var selectors = groups[g][0];
                                for (var i = 0; i < selectors.length; i++) {
                                    var matched = document.querySelectorAll(selectors[i]);
                                    for (var m = 0; m < matched.length; m++) {
                                        if (processContainer(matched[m], groups[g][1])) {
                                            handledAny = true;
                                        }
                                    }
                                }
                            }
                            if (handledAny) {
                                unlockScroll();
                            }
                        } catch(err) {}
                    }

                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', scanPage);
                    } else {
                        scanPage();
                    }

                    if (window.MutationObserver && (document.documentElement || document.body)) {
                        var consentDebounce = null;
                        var observer = new MutationObserver(function() {
                            if (consentDebounce !== null) return;
                            consentDebounce = setTimeout(function() {
                                consentDebounce = null;
                                scanPage();
                            }, 120);
                        });
                        observer.observe(document.documentElement || document.body, {
                            childList: true,
                            subtree: true
                        });
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }

    /**
     * Injects the autoconsent scriptlet on the provided WebView safely.
     */
    fun injectAutoConsent(webView: WebView) {
        try {
            val script = buildAutoConsentScript()
            webView.evaluateJavascript(script, null)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to inject autoconsent script into WebView", t)
        }
    }
}
