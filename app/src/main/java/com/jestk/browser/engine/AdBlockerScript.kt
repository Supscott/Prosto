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
     * Builds the complete combined document start script including:
     * - Surrogates (Google AdSense, GPT, Yandex)
     * - Immediate style element creation
     * - MutationObserver to collapse dynamic ad containers and empty parent wrappers
     */
    fun buildDocumentStartScript(): String {
        val cssContent = buildCosmeticCss().replace("\\", "\\\\").replace("`", "\\`").replace("\n", " ")
        return """
            (function() {
                try {
                    if (window.__hori_adblock_initialized) return;
                    window.__hori_adblock_initialized = true;

                    // Check if ad blocking is disabled via bridge
                    if (window.HoriAdBlockBridge && typeof window.HoriAdBlockBridge.isEnabled === 'function') {
                        if (!window.HoriAdBlockBridge.isEnabled()) {
                            return;
                        }
                    }

                    // 1. Install Surrogates immediately
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

                    // 2. Inject Cosmetic CSS Style Tag
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
