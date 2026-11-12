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
