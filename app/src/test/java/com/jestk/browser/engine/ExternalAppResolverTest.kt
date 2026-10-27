package com.jestk.browser.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ExternalAppResolverTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        packageManager = context.packageManager
        ExternalAppResolver.clearBrowserPackagesCache()
    }

    @Test
    fun extractDomain_handlesVariousFormats() {
        assertThat(ExternalAppResolver.extractDomain("https://www.youtube.com/watch?v=123")).isEqualTo("youtube.com")
        assertThat(ExternalAppResolver.extractDomain("http://sub.example.org/path")).isEqualTo("sub.example.org")
        assertThat(ExternalAppResolver.extractDomain("tg://resolve?domain=test")).isEqualTo("tg")
        assertThat(ExternalAppResolver.extractDomain("intent://instagram.com/#Intent;package=com.instagram.android;end")).isEqualTo("instagram.com")
        assertThat(ExternalAppResolver.extractDomain("")).isEqualTo("")
    }

    @Test
    fun isSearchEngineDomain_identifiesKnownEngines() {
        assertThat(ExternalAppResolver.isSearchEngineDomain("google.com")).isTrue()
        assertThat(ExternalAppResolver.isSearchEngineDomain("www.google.com")).isTrue()
        assertThat(ExternalAppResolver.isSearchEngineDomain("yandex.ru")).isTrue()
        assertThat(ExternalAppResolver.isSearchEngineDomain("duckduckgo.com")).isTrue()
        assertThat(ExternalAppResolver.isSearchEngineDomain("bing.com")).isTrue()
        assertThat(ExternalAppResolver.isSearchEngineDomain("search.yahoo.com")).isTrue()
        assertThat(ExternalAppResolver.isSearchEngineDomain("youtube.com")).isFalse()
        assertThat(ExternalAppResolver.isSearchEngineDomain("reddit.com")).isFalse()
    }

    @Test
    fun resolve_internalSchemes_returnsNoApp() {
        assertThat(ExternalAppResolver.resolve(context, "about:blank", "tab_1")).isEqualTo(ExternalAppResolution.NoApp)
        assertThat(ExternalAppResolver.resolve(context, "data:text/html,test", "tab_1")).isEqualTo(ExternalAppResolution.NoApp)
        assertThat(ExternalAppResolver.resolve(context, "javascript:void(0)", "tab_1")).isEqualTo(ExternalAppResolution.NoApp)
    }

    @Test
    fun resolve_searchEngineUrls_returnsNoApp() {
        val result = ExternalAppResolver.resolve(context, "https://www.google.com/search?q=query", "tab_1")
        assertThat(result).isEqualTo(ExternalAppResolution.NoApp)

        val resultYandex = ExternalAppResolver.resolve(context, "https://yandex.ru/search/?text=test", "tab_1")
        assertThat(resultYandex).isEqualTo(ExternalAppResolution.NoApp)
    }

    @Test
    fun resolve_uninstalledIntentScheme_returnsOfferWithPlayStore() {
        val url = "intent://details#Intent;scheme=https;package=com.unknown.nonexistent.app;end"
        val result = ExternalAppResolver.resolve(context, url, "tab_1")

        assertThat(result).isInstanceOf(ExternalAppResolution.Offer::class.java)
        val offer = result as ExternalAppResolution.Offer
        assertThat(offer.isCustomScheme).isTrue()
        assertThat(offer.prompt.isInstalled).isFalse()
        assertThat(offer.prompt.appPackage).isEqualTo("com.unknown.nonexistent.app")
        assertThat(offer.prompt.intentUri).contains("market://details?id=com.unknown.nonexistent.app")
    }

    @Test
    fun resolve_installedCustomScheme_returnsOffer() {
        // Register Telegram activity in Robolectric shadow
        val telegramIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("tg://resolve?domain=test"))
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "org.telegram.messenger"
                name = "org.telegram.ui.LaunchActivity"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "org.telegram.messenger"
                }
            }
        }
        shadowOf(packageManager).addResolveInfoForIntent(telegramIntent, resolveInfo)

        val result = ExternalAppResolver.resolve(context, "tg://resolve?domain=test", "tab_1")
        assertThat(result).isInstanceOf(ExternalAppResolution.Offer::class.java)
        val offer = result as ExternalAppResolution.Offer
        assertThat(offer.isCustomScheme).isTrue()
        assertThat(offer.prompt.isInstalled).isTrue()
        assertThat(offer.prompt.appPackage).isEqualTo("org.telegram.messenger")
    }

    @Test
    fun resolve_dedicatedAppForHttps_returnsOfferWithNotCustomScheme() {
        val youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val viewIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(youtubeUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.google.android.youtube"
                name = "com.google.android.youtube.WatchActivity"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.google.android.youtube"
                }
            }
        }
        shadowOf(packageManager).addResolveInfoForIntent(viewIntent, resolveInfo)

        val result = ExternalAppResolver.resolve(context, youtubeUrl, "tab_1")
        assertThat(result).isInstanceOf(ExternalAppResolution.Offer::class.java)
        val offer = result as ExternalAppResolution.Offer
        assertThat(offer.isCustomScheme).isFalse()
        assertThat(offer.prompt.isInstalled).isTrue()
        assertThat(offer.prompt.appPackage).isEqualTo("com.google.android.youtube")
        assertThat(offer.prompt.domain).isEqualTo("youtube.com")
    }

    @Test
    fun resolve_genericBrowserForHttps_returnsNoApp() {
        // Register Chrome as handling all urls including probe url
        val probeIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://generic-browser-probe.example.internal")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val chromeResolve = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.chrome"
                name = "com.google.android.apps.chrome.Main"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.android.chrome"
                }
            }
        }
        shadowOf(packageManager).addResolveInfoForIntent(probeIntent, chromeResolve)

        val targetUrl = "https://example.com/page"
        val targetIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        shadowOf(packageManager).addResolveInfoForIntent(targetIntent, chromeResolve)

        val result = ExternalAppResolver.resolve(context, targetUrl, "tab_1")
        assertThat(result).isEqualTo(ExternalAppResolution.NoApp)
    }

    @Test
    fun areDomainAliases_identifiesKnownAliases() {
        assertThat(ExternalAppResolver.areDomainAliases("vk.ru", "vk.com")).isTrue()
        assertThat(ExternalAppResolver.areDomainAliases("m.vk.com", "vk.ru")).isTrue()
        assertThat(ExternalAppResolver.areDomainAliases("wb.ru", "wildberries.ru")).isTrue()
        assertThat(ExternalAppResolver.areDomainAliases("x.com", "twitter.com")).isTrue()
        assertThat(ExternalAppResolver.areDomainAliases("instagram.com", "google.com")).isFalse()
        assertThat(ExternalAppResolver.areDomainAliases(null, "vk.com")).isFalse()
    }

    @Test
    fun resolve_domainAlias_resolvesAppRegisteredUnderCanonicalHost() {
        // App is registered for vk.com, but user navigates to vk.ru
        val vkIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://vk.com/feed"))
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.vkontakte.android"
                name = "com.vkontakte.android.MainActivity"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.vkontakte.android"
                }
            }
        }
        shadowOf(packageManager).addResolveInfoForIntent(vkIntent, resolveInfo)

        val result = ExternalAppResolver.resolve(context, "https://vk.ru/feed", "tab_1")
        assertThat(result).isInstanceOf(ExternalAppResolution.Offer::class.java)
        val offer = result as ExternalAppResolution.Offer
        assertThat(offer.isCustomScheme).isFalse()
        assertThat(offer.prompt.isInstalled).isTrue()
        assertThat(offer.prompt.appPackage).isEqualTo("com.vkontakte.android")
        assertThat(offer.prompt.domain).isEqualTo("vk.ru")
    }

    @Test
    fun resolve_redditUrl_resolvesAppRegisteredUnderWwwSubdomain() {
        // Reddit app registers www.reddit.com in its intent filter
        val redditIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.reddit.com/r/android")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.reddit.frontpage"
                name = "com.reddit.frontpage.MainActivity"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.reddit.frontpage"
                }
            }
        }
        shadowOf(packageManager).addResolveInfoForIntent(redditIntent, resolveInfo)

        // User visits https://reddit.com/r/android (without www.)
        val result = ExternalAppResolver.resolve(context, "https://reddit.com/r/android", "tab_1")
        assertThat(result).isInstanceOf(ExternalAppResolution.Offer::class.java)
        val offer = result as ExternalAppResolution.Offer
        assertThat(offer.isCustomScheme).isFalse()
        assertThat(offer.prompt.isInstalled).isTrue()
        assertThat(offer.prompt.appPackage).isEqualTo("com.reddit.frontpage")
        assertThat(offer.prompt.domain).isEqualTo("reddit.com")
    }

    @Test
    fun resolve_knownAppInstalled_offersAppEvenWhenIntentFilterHasNoExactMatchForRootUrl() {
        val pkg = "com.reddit.frontpage"
        val packageInfo = android.content.pm.PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
                name = "Reddit"
            }
        }
        shadowOf(packageManager).installPackage(packageInfo)

        val result = ExternalAppResolver.resolve(context, "https://reddit.com", "tab_1")
        assertThat(result).isInstanceOf(ExternalAppResolution.Offer::class.java)
        val offer = result as ExternalAppResolution.Offer
        assertThat(offer.prompt.appPackage).isEqualTo("com.reddit.frontpage")
        assertThat(offer.prompt.isInstalled).isTrue()
        assertThat(offer.prompt.domain).isEqualTo("reddit.com")
    }

    @Test
    fun resolve_vkRu_resolvesVkAppWhenInstalled() {
        val pkg = "com.vkontakte.android"
        val packageInfo = android.content.pm.PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
                name = "ВКонтакте"
            }
        }
        shadowOf(packageManager).installPackage(packageInfo)

        val result = ExternalAppResolver.resolve(context, "https://vk.ru", "tab_1")
        assertThat(result).isInstanceOf(ExternalAppResolution.Offer::class.java)
        val offer = result as ExternalAppResolution.Offer
        assertThat(offer.prompt.appPackage).isEqualTo("com.vkontakte.android")
        assertThat(offer.prompt.isInstalled).isTrue()
        assertThat(offer.prompt.domain).isEqualTo("vk.ru")
    }

    @Test
    fun resolve_unresolvableCustomScheme_returnsUnresolvableScheme() {
        val result = ExternalAppResolver.resolve(context, "unknownscheme://random/path", "tab_1")
        assertThat(result).isEqualTo(ExternalAppResolution.UnresolvableScheme)
    }

    @Test
    fun resolve_intentSchemeWithBrowserFallback_returnsFallbackPage() {
        val fallback = "https://example.com/download"
        val url = "intent://open#Intent;scheme=myapp;S.browser_fallback_url=" +
            android.net.Uri.encode(fallback) + ";end"

        val result = ExternalAppResolver.resolve(context, url, "tab_1")

        assertThat(result).isInstanceOf(ExternalAppResolution.BrowserFallback::class.java)
        assertThat((result as ExternalAppResolution.BrowserFallback).url).isEqualTo(fallback)
    }

    @Test
    fun resolve_uninstalledIntentWithBrowserFallback_prefersFallbackOverPlayStore() {
        val fallback = "https://example.com/download"
        val url = "intent://open#Intent;scheme=myapp;package=com.missing.app;" +
            "S.browser_fallback_url=" + android.net.Uri.encode(fallback) + ";end"

        val result = ExternalAppResolver.resolve(context, url, "tab_1")

        assertThat(result).isInstanceOf(ExternalAppResolution.BrowserFallback::class.java)
        assertThat((result as ExternalAppResolution.BrowserFallback).url).isEqualTo(fallback)
    }

    @Test
    fun resolve_intentSchemeForInstalledPackageWithoutBrowsableFilter_doesNotOfferLaunch() {
        val pkg = "com.example.deepapp"
        val packageInfo = android.content.pm.PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
                name = "Deep App"
            }
        }
        shadowOf(packageManager).installPackage(packageInfo)

        // The app is installed but no activity advertises the scheme with CATEGORY_BROWSABLE.
        val url = "intent://open#Intent;scheme=deepapp;package=$pkg;end"
        val result = ExternalAppResolver.resolve(context, url, "tab_1")

        // Must not offer to launch non-browsable activities
        assertThat(result).isEqualTo(ExternalAppResolution.UnresolvableScheme)
    }
}
