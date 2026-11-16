package com.jestk.browser.engine

/** The script that reads a page's theme color (meta tag, header background, page background). */
internal object ThemeColorScript {
    /** [isPageStarted]: the body is not painted yet, so its background is not a reliable answer. */
    fun build(isPageStarted: Boolean): String =
        """
            (function() {
                try {
                    var host = location.hostname || '';
                    if (host.indexOf('apple.com') !== -1) {
                        return '#161617';
                    }
                    if (host.indexOf('youtube.com') !== -1) {
                        var isDark = document.documentElement.hasAttribute('dark') || 
                                     document.documentElement.getAttribute('dark') === 'true' ||
                                     document.querySelector('ytd-app[dark], ytm-app[dark]');
                        return isDark ? '#0f0f0f' : '#ffffff';
                    }

                    var meta = document.querySelector('meta[name="theme-color"]');
                    if (meta && meta.content) {
                        var c = meta.content.trim();
                        if (c) return c;
                    }

                    function getBg(el) {
                        if (!el) return null;
                        var style = window.getComputedStyle(el);
                        var bg = style ? style.backgroundColor : null;
                        if (bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'rgba(0,0,0,0)') {
                            return bg.trim();
                        }
                        return null;
                    }

                    var headerSelectors = [
                        '#globalnav',
                        '#ac-globalnav',
                        'header',
                        'nav',
                        '[role="banner"]',
                        '.navbar',
                        '#navbar',
                        '.header',
                        '#header',
                        '.top-bar',
                        '#top-bar',
                        '[data-header]'
                    ];
                    for (var i = 0; i < headerSelectors.length; i++) {
                        var headerEl = document.querySelector(headerSelectors[i]);
                        var headerBg = getBg(headerEl);
                        if (headerBg) return headerBg;
                    }

                    try {
                        var topEl = document.elementFromPoint(window.innerWidth / 2, 10);
                        var curr = topEl;
                        while (curr && curr !== document.documentElement && curr !== document.body) {
                            var topBg = getBg(curr);
                            if (topBg) return topBg;
                            curr = curr.parentElement;
                        }
                    } catch(e) {}

                    var appBg = getBg(document.querySelector('ytm-app')) ||
                                getBg(document.querySelector('ytd-app')) ||
                                getBg(document.querySelector('#app')) ||
                                getBg(document.querySelector('#root')) ||
                                getBg(document.querySelector('main'));
                    if (appBg) return appBg;

                    if (!$isPageStarted) {
                        var bodyBg = getBg(document.body) || getBg(document.documentElement);
                        if (bodyBg) return bodyBg;
                    }
                    return '';
                } catch(e) {
                    return '';
                }
            })()
            """.trimIndent()
}
