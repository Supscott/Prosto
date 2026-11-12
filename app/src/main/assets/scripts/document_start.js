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
        if (window.HoriAdBlockBridge && typeof window.HoriAdBlockBridge.isEnabled === 'function') {
            try {
                adBlockerActive = window.HoriAdBlockBridge.isEnabled();
            } catch(e) {}
        }

        // 1. YouTube: the clean-player link interception always runs; the ad pruning
        // inside it checks adBlockerActive.
        /*@@YOUTUBE_FAST_START@@*/

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
                cosmeticStyle.textContent = `@@COSMETIC_CSS@@`;
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
