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
     * Builds the surrogate mocks for ad APIs (Google AdSense, GPT, Yandex)
     * preventing pages from waiting indefinitely for callbacks or throwing TypeError exceptions.
     */
    fun buildSurrogateScript(): String {
        return """
            (function() {
                try {
                    // Google Ad status check stub (anti-adblock bypass)
                    window.google_ad_status = 1;

                    // Google AdSense stub
                    window.adsbygoogle = window.adsbygoogle || [];
                    window.adsbygoogle.loaded = true;
                    window.adsbygoogle.push = function(arg) {
                        return 0;
                    };

                    // Google Publisher Tag (GPT) stub
                    window.googletag = window.googletag || {};
                    window.googletag.cmd = window.googletag.cmd || [];
                    window.googletag.cmd.push = function(fn) {
                        if (typeof fn === 'function') {
                            try { fn(); } catch(e) {}
                        }
                        return 0;
                    };
                    window.googletag.apiReady = true;
                    var dummyService = {
                        addEventListener: function() { return dummyService; },
                        setTargeting: function() { return dummyService; },
                        enableSingleRequest: function() { return dummyService; },
                        enableAsyncRendering: function() { return dummyService; },
                        collapseEmptyDivs: function() { return dummyService; },
                        clearTargeting: function() { return dummyService; },
                        refresh: function() { return dummyService; },
                        getSlots: function() { return []; }
                    };
                    window.googletag.pubads = function() {
                        return dummyService;
                    };
                    window.googletag.enableServices = function() {};
                    window.googletag.display = function() {};
                    window.googletag.sizeMapping = function() {
                        return {
                            addSize: function() { return this; },
                            build: function() { return []; }
                        };
                    };

                    // Yandex Direct & ADFOX stubs
                    window.yaContextCb = window.yaContextCb || [];
                    window.yaContextCb.push = function(fn) {
                        if (typeof fn === 'function') {
                            try { fn(); } catch(e) {}
                        }
                    };
                    window.Ya = window.Ya || {};
                    window.Ya.adfoxCode = window.Ya.adfoxCode || {
                        create: function(config) {
                            if (config && config.containerId) {
                                var el = document.getElementById(config.containerId);
                                if (el) {
                                    el.style.setProperty('display', 'none', 'important');
                                    el.style.setProperty('height', '0', 'important');
                                    el.style.setProperty('min-height', '0', 'important');
                                }
                            }
                        },
                        createScroll: function(config) {
                            if (config && config.containerId) {
                                var el = document.getElementById(config.containerId);
                                if (el) {
                                    el.style.setProperty('display', 'none', 'important');
                                    el.style.setProperty('height', '0', 'important');
                                    el.style.setProperty('min-height', '0', 'important');
                                }
                            }
                        },
                        destroy: function() {},
                        clearSession: function() {},
                        reload: function() {},
                        hbCallbacks: []
                    };
                    window.Ya.adfoxCode.hbCallbacks = window.Ya.adfoxCode.hbCallbacks || [];
                    window.Ya.Context = window.Ya.Context || {
                        AdvManager: {
                            render: function(config) {
                                if (config && config.renderTo) {
                                    var el = typeof config.renderTo === 'string' ? document.getElementById(config.renderTo) : config.renderTo;
                                    if (el) {
                                        el.style.setProperty('display', 'none', 'important');
                                        el.style.setProperty('height', '0', 'important');
                                        el.style.setProperty('min-height', '0', 'important');
                                    }
                                }
                            },
                            destroy: function() {}
                        }
                    };

                    // X5 Media stub
                    window.x5media = window.x5media || {
                        init: function() { return Promise.resolve(); },
                        render: function() {}
                    };
                } catch(e) {}
            })();
        """.trimIndent()
    }

    /**
     * Builds the YouTube Fast-Start ad bypass scriptlet.
     * Combines multi-layer mitigations against YouTube's anti-adblock throttling and delays:
     * 1. Spoofs window.google_ad_status = 1 to prevent YouTube's deliberate ad-block detection delay.
     * 2. Prunes adPlacements, playerAds, adSlots, and adBreakHeartbeatParams from:
     *    - window.ytInitialPlayerResponse, window.ytInitialData, window.ytplayer
     *    - JSON.parse
     *    - Response.prototype.json and Response.prototype.text
     *    - XMLHttpRequest.prototype.responseText and response
     * 3. Injects isInlinePlaybackNoAd: true into contentPlaybackContext across JSON.stringify,
     *    Object.assign, fetch, and XMLHttpRequest to prevent YouTube's InnerTube API from assigning
     *    ad slots on SPA navigations.
     * 4. Accelerates and auto-skips fallback ad breaks (.ad-showing video, .ad-interrupting video).
     * 5. Runs an autoplay watchdog to trigger video.play() on fresh watch navigations if stalled at 0.0s.
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
                            if (obj.playbackContext && obj.playbackContext.contentPlaybackContext) {
                                obj.playbackContext.contentPlaybackContext.isInlinePlaybackNoAd = true;
                            }
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

                    // 2. Inject isInlinePlaybackNoAd into player requests (uBlock Origin / iter.ca anti-throttling)
                    try {
                        var origStringify = JSON.stringify;
                        JSON.stringify = function(val, replacer, space) {
                            if (val && typeof val === 'object') {
                                try {
                                    if (val.contentPlaybackContext && typeof val.contentPlaybackContext === 'object') {
                                        val.contentPlaybackContext.isInlinePlaybackNoAd = true;
                                    }
                                    if (val.playbackContext && val.playbackContext.contentPlaybackContext && typeof val.playbackContext.contentPlaybackContext === 'object') {
                                        val.playbackContext.contentPlaybackContext.isInlinePlaybackNoAd = true;
                                    }
                                } catch(e) {}
                            }
                            return origStringify.apply(this, arguments);
                        };
                    } catch(e) {}

                    try {
                        var realAssign = Object.assign;
                        Object.assign = function() {
                            var res = realAssign.apply(Object, arguments);
                            try {
                                if (res && typeof res === 'object') {
                                    if (typeof res.body === 'string' && res.body.indexOf('contentPlaybackContext') !== -1) {
                                        if (res.body.indexOf('"isInlinePlaybackNoAd":true') === -1) {
                                            res.body = res.body.replace('"contentPlaybackContext":{', '"contentPlaybackContext":{"isInlinePlaybackNoAd":true,');
                                        }
                                    } else if (res.contentPlaybackContext && typeof res.contentPlaybackContext === 'object') {
                                        res.contentPlaybackContext.isInlinePlaybackNoAd = true;
                                    }
                                }
                            } catch(e) {}
                            return res;
                        };
                    } catch(e) {}

                    try {
                        if (window.XMLHttpRequest && XMLHttpRequest.prototype) {
                            var origXhrOpen = XMLHttpRequest.prototype.open;
                            XMLHttpRequest.prototype.open = function(method, url) {
                                this.__yt_url = typeof url === 'string' ? url : '';
                                return origXhrOpen.apply(this, arguments);
                            };

                            if (XMLHttpRequest.prototype.send) {
                                var origXhrSend = XMLHttpRequest.prototype.send;
                                XMLHttpRequest.prototype.send = function(body) {
                                    if (body && typeof body === 'string' && body.indexOf('contentPlaybackContext') !== -1) {
                                        if (body.indexOf('"isInlinePlaybackNoAd":true') === -1) {
                                            body = body.replace('"contentPlaybackContext":{', '"contentPlaybackContext":{"isInlinePlaybackNoAd":true,');
                                        }
                                    }
                                    return origXhrSend.call(this, body);
                                };
                            }

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

                    try {
                        var realFetch = window.fetch;
                        if (typeof realFetch === 'function') {
                            window.fetch = function(resource, init) {
                                if (init && init.body && typeof init.body === 'string' && init.body.indexOf('contentPlaybackContext') !== -1) {
                                    if (init.body.indexOf('"isInlinePlaybackNoAd":true') === -1) {
                                        init.body = init.body.replace('"contentPlaybackContext":{', '"contentPlaybackContext":{"isInlinePlaybackNoAd":true,');
                                    }
                                }
                                return realFetch.apply(this, arguments);
                            };
                        }
                    } catch(e) {}

                    // 3. Fallback ad-skip accelerator for ad overlays (desktop & mobile)
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
                                    var p = v.play();
                                    if (p && typeof p.catch === 'function') p.catch(function() {});
                                } catch(e) {}
                            }
                        } catch(e) {}
                    }

                    // 4. Autoplay & gesture watchdog on SPA navigation
                    var userInteractedPause = false;
                    var lastWatchUrl = location.href;

                    function monitorVideo(v) {
                        if (!v || v.__hori_yt_hooked) return;
                        v.__hori_yt_hooked = true;
                        v.addEventListener('timeupdate', function() {
                            if (document.querySelector('.ad-showing, .ad-interrupting')) {
                                skipAdSlot();
                            }
                        });
                        v.addEventListener('pause', function() {
                            if (v.currentTime > 0.5) {
                                userInteractedPause = true;
                            }
                        });
                    }

                    function checkWatchPlayback() {
                        try {
                            if (location.href !== lastWatchUrl) {
                                lastWatchUrl = location.href;
                                userInteractedPause = false;
                            }
                            if (location.pathname.indexOf('/watch') !== -1 && !userInteractedPause) {
                                var v = document.querySelector('video');
                                var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                                if (player && typeof player.getPlayerState === 'function') {
                                    var state = player.getPlayerState();
                                    if ((state === -1 || state === 3 || (state === 2 && (!v || v.currentTime === 0))) && !document.querySelector('.ad-showing, .ad-interrupting')) {
                                        if (typeof player.playVideo === 'function') {
                                            player.playVideo();
                                        }
                                    }
                                }
                                if (v && v.paused && v.currentTime === 0 && !v.ended && !document.querySelector('.ad-showing, .ad-interrupting')) {
                                    var p = v.play();
                                    if (p && typeof p.catch === 'function') {
                                        p.catch(function() {});
                                    }
                                }
                            }
                        } catch(e) {}
                    }
                    setInterval(checkWatchPlayback, 300);

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
                    if (window.__hori_adblock_initialized) return;
                    window.__hori_adblock_initialized = true;

                    // Check if ad blocking is active via bridge
                    var adBlockerActive = true;
                    if (window.HoriAdBlockBridge && typeof window.HoriAdBlockBridge.isEnabled === 'function') {
                        try {
                            adBlockerActive = window.HoriAdBlockBridge.isEnabled();
                        } catch(e) {}
                    }

                    // 1. Install YouTube Fast-Start protection immediately regardless of toggle
                    // to prevent artificial 10-15s buffering and SABR backoffs on YouTube:
                    $ytScript

                    // If ad blocking is toggled off, exit before applying cosmetic CSS and DOM cleaners
                    if (!adBlockerActive) {
                        return;
                    }

                    // 2. Install Surrogates immediately
                    window.adsbygoogle = window.adsbygoogle || [];
                    window.adsbygoogle.loaded = true;
                    window.adsbygoogle.push = function(arg) { return 0; };

                    window.googletag = window.googletag || {};
                    window.googletag.cmd = window.googletag.cmd || [];
                    window.googletag.cmd.push = function(fn) {
                        if (typeof fn === 'function') {
                            try { fn(); } catch(e) {}
                        }
                        return 0;
                    };
                    window.googletag.apiReady = true;
                    var dummyService = {
                        addEventListener: function() { return dummyService; },
                        setTargeting: function() { return dummyService; },
                        enableSingleRequest: function() { return dummyService; },
                        enableAsyncRendering: function() { return dummyService; },
                        collapseEmptyDivs: function() { return dummyService; },
                        clearTargeting: function() { return dummyService; },
                        refresh: function() { return dummyService; },
                        getSlots: function() { return []; }
                    };
                    window.googletag.pubads = function() { return dummyService; };
                    window.googletag.enableServices = function() {};
                    window.googletag.display = function() {};
                    window.googletag.sizeMapping = function() {
                        return {
                            addSize: function() { return this; },
                            build: function() { return []; }
                        };
                    };

                    // Yandex Direct & ADFOX stubs
                    window.yaContextCb = window.yaContextCb || [];
                    window.yaContextCb.push = function(fn) {
                        if (typeof fn === 'function') {
                            try { fn(); } catch(e) {}
                        }
                    };
                    window.Ya = window.Ya || {};
                    window.Ya.adfoxCode = window.Ya.adfoxCode || {
                        create: function(config) {
                            if (config && config.containerId) {
                                var el = document.getElementById(config.containerId);
                                if (el) {
                                    el.style.setProperty('display', 'none', 'important');
                                    el.style.setProperty('height', '0', 'important');
                                    el.style.setProperty('min-height', '0', 'important');
                                }
                            }
                        },
                        createScroll: function(config) {
                            if (config && config.containerId) {
                                var el = document.getElementById(config.containerId);
                                if (el) {
                                    el.style.setProperty('display', 'none', 'important');
                                    el.style.setProperty('height', '0', 'important');
                                    el.style.setProperty('min-height', '0', 'important');
                                }
                            }
                        },
                        destroy: function() {},
                        clearSession: function() {},
                        reload: function() {},
                        hbCallbacks: []
                    };
                    window.Ya.adfoxCode.hbCallbacks = window.Ya.adfoxCode.hbCallbacks || [];
                    window.Ya.Context = window.Ya.Context || {
                        AdvManager: {
                            render: function(config) {
                                if (config && config.renderTo) {
                                    var el = typeof config.renderTo === 'string' ? document.getElementById(config.renderTo) : config.renderTo;
                                    if (el) {
                                        el.style.setProperty('display', 'none', 'important');
                                        el.style.setProperty('height', '0', 'important');
                                        el.style.setProperty('min-height', '0', 'important');
                                    }
                                }
                            },
                            destroy: function() {}
                        }
                    };

                    // X5 Media stub
                    window.x5media = window.x5media || {
                        init: function() { return Promise.resolve(); },
                        render: function() {}
                    };

                    // 3. Inject Cosmetic CSS Style Tag
                    function injectStyle() {
                        if (document.getElementById('__hori_adblock_css')) return;
                        var style = document.createElement('style');
                        style.id = '__hori_adblock_css';
                        style.textContent = `$cssContent`;
                        var target = document.head || document.documentElement;
                        if (target) {
                            target.appendChild(style);
                        } else {
                            document.addEventListener('DOMContentLoaded', function() {
                                var t = document.head || document.documentElement;
                                if (t && !document.getElementById('__hori_adblock_css')) {
                                    t.appendChild(style);
                                }
                            });
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
}
