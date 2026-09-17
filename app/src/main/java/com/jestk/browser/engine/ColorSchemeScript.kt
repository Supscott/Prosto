package com.jestk.browser.engine

/**
 * Builds the JavaScript snippet injected into WebViews to support prefers-color-scheme polyfill
 * and domain-specific dark mode overrides (e.g. YouTube).
 */
fun buildColorSchemeScript(isDark: Boolean): String {
    return """
        (function() {
            try {
                var dark = $isDark;
                window.__jestkIsDark = dark;
                if (!window.__jestkThemeListeners) {
                    window.__jestkThemeListeners = [];
                }

                if (!window.__jestkOrigMatchMedia) {
                    window.__jestkOrigMatchMedia = window.matchMedia;
                }
                var origMatchMedia = window.__jestkOrigMatchMedia;

                window.matchMedia = function(q) {
                    if (q && q.indexOf('prefers-color-scheme') !== -1) {
                        var isDarkQuery = q.indexOf('dark') !== -1;
                        var currentDark = window.__jestkIsDark;
                        var matches = isDarkQuery ? currentDark : !currentDark;
                        return {
                            matches: matches,
                            media: q,
                            onchange: null,
                            addListener: function(fn) {
                                if (typeof fn === 'function' && window.__jestkThemeListeners.indexOf(fn) === -1) {
                                    window.__jestkThemeListeners.push(fn);
                                }
                            },
                            removeListener: function(fn) {
                                var idx = window.__jestkThemeListeners.indexOf(fn);
                                if (idx !== -1) window.__jestkThemeListeners.splice(idx, 1);
                            },
                            addEventListener: function(type, fn) {
                                if (type === 'change' && typeof fn === 'function' && window.__jestkThemeListeners.indexOf(fn) === -1) {
                                    window.__jestkThemeListeners.push(fn);
                                }
                            },
                            removeEventListener: function(type, fn) {
                                if (type === 'change') {
                                    var idx = window.__jestkThemeListeners.indexOf(fn);
                                    if (idx !== -1) window.__jestkThemeListeners.splice(idx, 1);
                                }
                            },
                            dispatchEvent: function() { return false; }
                        };
                    }
                    return origMatchMedia ? origMatchMedia.call(window, q) : null;
                };

                var evt = { matches: dark, media: '(prefers-color-scheme: dark)' };
                window.__jestkThemeListeners.forEach(function(fn) {
                    try { fn(evt); } catch(e) {}
                });

                if (location.hostname.indexOf('youtube.com') !== -1) {
                    if (dark) {
                        if (document.documentElement) document.documentElement.setAttribute('dark', 'true');
                        var app = document.querySelector('ytd-app, ytm-app');
                        if (app) app.setAttribute('dark', 'true');
                    } else {
                        if (document.documentElement) document.documentElement.removeAttribute('dark');
                        var app = document.querySelector('ytd-app, ytm-app');
                        if (app) app.removeAttribute('dark');
                    }
                }
            } catch(e) {}
        })();
    """.trimIndent()
}
