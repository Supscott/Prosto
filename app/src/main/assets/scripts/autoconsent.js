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
