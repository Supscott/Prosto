package com.jestk.browser.engine

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.ExternalAppPrompt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class JestkWebViewClientTest {

    @Test
    fun onPageStarted_ignoresEmptyAndAboutBlank() {
        var startedUrl: String? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, url -> startedUrl = url },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> }
        )

        client.onPageStarted(null, "", null)
        assertThat(startedUrl).isNull()

        client.onPageStarted(null, "about:blank", null)
        assertThat(startedUrl).isNull()

        client.onPageStarted(null, "https://example.com", null)
        assertThat(startedUrl).isEqualTo("https://example.com")
    }

    @Test
    fun onPageFinished_ignoresEmptyAndAboutBlank() {
        var finishedUrl: String? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, url, _ -> finishedUrl = url },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> }
        )

        client.onPageFinished(null, "")
        assertThat(finishedUrl).isNull()

        client.onPageFinished(null, "about:blank")
        assertThat(finishedUrl).isNull()

        client.onPageFinished(null, "https://example.com")
        assertThat(finishedUrl).isEqualTo("https://example.com")
    }

    @Test
    fun shouldInterceptRequest_blocksAndInvokesTracking() {
        var interceptedTracker: Boolean? = null
        var interceptedUrl: String? = null

        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { true },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> },
            onBlockedResource = { url, isTracker ->
                interceptedUrl = url
                interceptedTracker = isTracker
            }
        )

        val adRequest = object : android.webkit.WebResourceRequest {
            override fun getUrl(): android.net.Uri = android.net.Uri.parse("https://doubleclick.net/ad.js")
            override fun isForMainFrame(): Boolean = false
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }

        val adResponse = client.shouldInterceptRequest(null, adRequest)
        assertThat(adResponse).isNotNull()
        assertThat(interceptedUrl).isEqualTo("https://doubleclick.net/ad.js")
        assertThat(interceptedTracker).isFalse()

        val trackerRequest = object : android.webkit.WebResourceRequest {
            override fun getUrl(): android.net.Uri = android.net.Uri.parse("https://google-analytics.com/analytics.js")
            override fun isForMainFrame(): Boolean = false
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }

        val trackerResponse = client.shouldInterceptRequest(null, trackerRequest)
        assertThat(trackerResponse).isNotNull()
        assertThat(interceptedUrl).isEqualTo("https://google-analytics.com/analytics.js")
        assertThat(interceptedTracker).isTrue()
    }

    @Test
    fun doUpdateVisitedHistory_withValidUrlAndTitle_invokesPageStartedAndTitleReceived() {
        var startedUrl: String? = null
        var receivedTitle: String? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, url -> startedUrl = url },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> },
            onTitleReceived = { _, title -> receivedTitle = title }
        )

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val webView = android.webkit.WebView(context)

        client.doUpdateVisitedHistory(webView, "https://example.com/spa-route", false)
        assertThat(startedUrl).isEqualTo("https://example.com/spa-route")
        // Title on empty webView is null/empty so onTitleReceived is not invoked
        assertThat(receivedTitle).isNull()
    }

    @Test
    fun doUpdateVisitedHistory_withBlankOrAboutBlankUrl_doesNotInvokePageStarted() {
        var startedUrl: String? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, url -> startedUrl = url },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> }
        )

        client.doUpdateVisitedHistory(null, "", false)
        assertThat(startedUrl).isNull()

        client.doUpdateVisitedHistory(null, "about:blank", false)
        assertThat(startedUrl).isNull()
    }

    @Test
    fun shouldOverrideUrlLoading_withUnresolvableCustomScheme_returnsTrue() {
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> }
        )

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val webView = android.webkit.WebView(context)

        // Custom unknown scheme returns true to prevent WebView error
        val shouldOverride = client.shouldOverrideUrlLoading(webView, "unknownscheme://path")
        assertThat(shouldOverride).isTrue()
    }

    @Test
    fun shouldOverrideUrlLoading_whenDisabled_allowsHttp() {
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> },
            isOpenInAppsEnabled = { false }
        )

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val webView = android.webkit.WebView(context)

        val shouldOverride = client.shouldOverrideUrlLoading(webView, "https://example.com")
        assertThat(shouldOverride).isFalse()
    }

    @Test
    fun onPageStarted_offersExternalApp_whenDedicatedAppInstalled() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ExternalAppResolver.clearBrowserPackagesCache()
        val pm = context.packageManager

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
        shadowOf(pm).addResolveInfoForIntent(vkIntent, resolveInfo)

        var offeredPrompt: ExternalAppPrompt? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> },
            onExternalAppOffered = { _, prompt, _ -> offeredPrompt = prompt }
        )

        val webView = android.webkit.WebView(context)
        client.onPageStarted(webView, "https://vk.ru/feed", null)

        assertThat(offeredPrompt).isNotNull()
        assertThat(offeredPrompt?.appPackage).isEqualTo("com.vkontakte.android")
    }

    @Test
    fun doUpdateVisitedHistory_offersExternalApp_whenDedicatedAppInstalled() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ExternalAppResolver.clearBrowserPackagesCache()
        val pm = context.packageManager

        val ytIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtube.com/watch?v=123"))
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.google.android.youtube"
                name = "com.google.android.youtube.WatchActivity"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.google.android.youtube"
                }
            }
        }
        shadowOf(pm).addResolveInfoForIntent(ytIntent, resolveInfo)

        var offeredPrompt: ExternalAppPrompt? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> },
            onExternalAppOffered = { _, prompt, _ -> offeredPrompt = prompt }
        )

        val webView = android.webkit.WebView(context)
        client.doUpdateVisitedHistory(webView, "https://youtube.com/watch?v=123", false)

        assertThat(offeredPrompt).isNotNull()
        assertThat(offeredPrompt?.appPackage).isEqualTo("com.google.android.youtube")
    }

    @Test
    fun onRenderProcessGone_cleansUpViewAndReturnsTrue() {
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> }
        )

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val parentLayout = android.widget.FrameLayout(context)
        val webView = android.webkit.WebView(context)
        parentLayout.addView(webView)

        assertThat(webView.parent).isNotNull()

        val detail = object : android.webkit.RenderProcessGoneDetail() {
            override fun didCrash(): Boolean = true
            override fun rendererPriorityAtExit(): Int = 0
        }

        val handled = client.onRenderProcessGone(webView, detail)

        assertThat(handled).isTrue()
        assertThat(webView.parent).isNull()
    }

    private fun webRequest(
        url: String,
        isForMainFrame: Boolean,
        gesture: Boolean = true
    ): android.webkit.WebResourceRequest =
        object : android.webkit.WebResourceRequest {
            override fun getUrl(): android.net.Uri = android.net.Uri.parse(url)
            override fun isForMainFrame(): Boolean = isForMainFrame
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = gesture
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }

    private fun registerTelegramApp(context: android.content.Context) {
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
        shadowOf(context.packageManager).addResolveInfoForIntent(telegramIntent, resolveInfo)
    }

    @Test
    fun shouldOverrideUrlLoading_subframeCustomScheme_offersAppAndBlocksNavigation() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ExternalAppResolver.clearBrowserPackagesCache()
        registerTelegramApp(context)

        var offeredPrompt: ExternalAppPrompt? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> },
            onExternalAppOffered = { _, prompt, _ -> offeredPrompt = prompt }
        )
        val webView = android.webkit.WebView(context)

        val handled = client.shouldOverrideUrlLoading(
            webView,
            webRequest("tg://resolve?domain=test", isForMainFrame = false)
        )

        assertThat(handled).isTrue()
        assertThat(offeredPrompt?.appPackage).isEqualTo("org.telegram.messenger")
    }

    @Test
    fun shouldOverrideUrlLoading_subframeHttps_loadsNormallyWithoutPrompt() {
        var offeredPrompt: ExternalAppPrompt? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> },
            onExternalAppOffered = { _, prompt, _ -> offeredPrompt = prompt }
        )

        val handled = client.shouldOverrideUrlLoading(
            null,
            webRequest("https://ads.example.com/frame", isForMainFrame = false)
        )

        assertThat(handled).isFalse()
        assertThat(offeredPrompt).isNull()
    }

    @Test
    fun shouldOverrideUrlLoading_intentWithBrowserFallback_loadsFallbackPage() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ExternalAppResolver.clearBrowserPackagesCache()
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> }
        )
        val webView = android.webkit.WebView(context)

        val fallback = "https://example.com/download"
        val intentUrl = "intent://open#Intent;scheme=myapp;S.browser_fallback_url=" +
            android.net.Uri.encode(fallback) + ";end"

        val handled = client.shouldOverrideUrlLoading(
            webView,
            webRequest(intentUrl, isForMainFrame = true)
        )

        assertThat(handled).isTrue()
        assertThat(shadowOf(webView).lastLoadedUrl).isEqualTo(fallback)
    }

    @Test
    fun handleExternalNavigation_withProgrammaticCustomScheme_offersApp() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ExternalAppResolver.clearBrowserPackagesCache()
        registerTelegramApp(context)

        var offeredPrompt: ExternalAppPrompt? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> },
            onExternalAppOffered = { _, prompt, _ -> offeredPrompt = prompt }
        )
        val webView = android.webkit.WebView(context)

        val handled = client.handleExternalNavigation(webView, "tg://resolve?domain=test")

        assertThat(handled).isTrue()
        assertThat(offeredPrompt?.appPackage).isEqualTo("org.telegram.messenger")
    }

    @Test
    fun shouldOverrideUrlLoading_reportsUserGestureOriginOfTheOffer() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ExternalAppResolver.clearBrowserPackagesCache()
        registerTelegramApp(context)

        val gestures = mutableListOf<Boolean>()
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> },
            onExternalAppOffered = { _, _, userInitiated -> gestures += userInitiated }
        )
        val webView = android.webkit.WebView(context)

        // A scripted redirect (t.me fires tg:resolve on page load) has no gesture.
        client.shouldOverrideUrlLoading(
            webView,
            webRequest("tg://resolve?domain=test", isForMainFrame = true, gesture = false)
        )
        // A tap on the site's "Open in app" button does.
        client.shouldOverrideUrlLoading(
            webView,
            webRequest("tg://resolve?domain=test", isForMainFrame = true, gesture = true)
        )

        assertThat(gestures).containsExactly(false, true).inOrder()
    }

    @Test
    fun onPageStarted_reportsPassiveOrigin() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ExternalAppResolver.clearBrowserPackagesCache()
        val tmeUrl = "https://t.me/animates"
        val tmeIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(tmeUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "org.telegram.messenger"
                name = "org.telegram.ui.LaunchActivity"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "org.telegram.messenger"
                }
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(tmeIntent, resolveInfo)

        val gestures = mutableListOf<Boolean>()
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> },
            onExternalAppOffered = { _, _, userInitiated -> gestures += userInitiated }
        )
        val webView = android.webkit.WebView(context)

        client.onPageStarted(webView, tmeUrl, null)

        assertThat(gestures).containsExactly(false)
    }
}
