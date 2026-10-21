package com.jestk.browser.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.data.repository.FavoriteRepositoryImpl
import com.jestk.browser.data.repository.HistoryRepositoryImpl
import com.jestk.browser.data.repository.RecentTabRepositoryImpl
import com.jestk.browser.data.repository.SettingsRepositoryImpl
import com.jestk.browser.data.repository.TabRepositoryImpl
import com.jestk.browser.engine.ExternalAppResolver
import com.jestk.browser.engine.JestkWebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * End-to-end (JVM) regression tests for the "Open in app" notification pipeline.
 *
 * These wire the real [BrowserViewModel] to a real [JestkWebViewClient] exactly like
 * `JestkApp`/`WebViewContainer` do, so they exercise the full chain:
 * WebKit callback -> resolver -> coordinator -> UI state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExternalAppFlowTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var tabRepo: TabRepositoryImpl
    private lateinit var historyRepo: HistoryRepositoryImpl
    private lateinit var favoriteRepo: FavoriteRepositoryImpl
    private lateinit var settingsRepo: SettingsRepositoryImpl
    private lateinit var recentTabRepo: RecentTabRepositoryImpl
    private lateinit var context: Context

    private val redditUrl = "https://www.reddit.com/r/android/comments/123"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        ExternalAppResolver.clearBrowserPackagesCache()
        db = AppDatabase.createInMemory(context)
        tabRepo = TabRepositoryImpl(db.tabDao(), testDispatcher)
        historyRepo = HistoryRepositoryImpl(db.historyDao(), testDispatcher)
        favoriteRepo = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher)
        settingsRepo = SettingsRepositoryImpl(db.settingDao(), testDispatcher)
        recentTabRepo = RecentTabRepositoryImpl(db.recentTabDao(), testDispatcher)
        registerRedditApp()
        registerTelegramApp()
    }

    @After
    fun teardown() {
        ExternalAppResolver.clearBrowserPackagesCache()
        Dispatchers.resetMain()
        db.close()
    }

    private fun createViewModel(): BrowserViewModel =
        BrowserViewModel(
            tabRepository = tabRepo,
            historyRepository = historyRepo,
            favoriteRepository = favoriteRepo,
            settingsRepository = settingsRepo,
            recentTabRepository = recentTabRepo,
            ioDispatcher = testDispatcher,
        )

    /** Mirrors the production wiring in `JestkApp.onWebViewCreated`. */
    private fun createClient(viewModel: BrowserViewModel, tabId: String): JestkWebViewClient =
        JestkWebViewClient(
            tabId = tabId,
            isAdBlockerEnabled = { false },
            onPageStarted = { id, url -> viewModel.onEvent(BrowserUiEvent.OnPageStarted(id, url)) },
            onPageFinished = { id, url, title -> viewModel.onEvent(BrowserUiEvent.OnPageFinished(id, url, title)) },
            onNavigationStateChanged = { id, back, forward ->
                viewModel.onEvent(BrowserUiEvent.OnNavigationStateChanged(id, back, forward))
            },
            onSnapshotReady = { _, _ -> },
            isOpenInAppsEnabled = { viewModel.uiState.value.settings.isOpenInAppsEnabled },
            onExternalAppOffered = { id, prompt, userInitiated ->
                viewModel.onEvent(BrowserUiEvent.OnExternalAppOffered(id, prompt, userInitiated))
            },
        )

    private fun registerRedditApp() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(redditUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.reddit.frontpage"
                name = "com.reddit.frontpage.MainActivity"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.reddit.frontpage"
                    nonLocalizedLabel = "Reddit"
                }
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, resolveInfo)
    }

    private fun registerTelegramApp() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=durov"))
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "org.telegram.messenger"
                name = "org.telegram.ui.LaunchActivity"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "org.telegram.messenger"
                    nonLocalizedLabel = "Telegram"
                }
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, resolveInfo)
    }

    private fun customSchemeRequest(url: String, isForMainFrame: Boolean): WebResourceRequest =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)
            override fun isForMainFrame(): Boolean = isForMainFrame
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = true
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }

    // ---------------------------------------------------------------------
    // Bug 1: navigating to a site from another app must offer the native app.
    // ---------------------------------------------------------------------

    @Test
    fun externalLink_pillAppears_whenUrlIsAlreadyOpenInRestoredTab() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // First external launch (e.g. link tapped in Telegram).
            viewModel.onEvent(BrowserUiEvent.OpenNewTab(redditUrl))
            advanceUntilIdle()
            val tabId = viewModel.uiState.value.activeTabId!!
            val webView = WebView(context)
            val client = createClient(viewModel, tabId)

            client.onPageStarted(webView, redditUrl, null)
            assertThat(viewModel.uiState.value.externalAppPrompt).isNotNull()

            // The pill was dismissed by the user.
            viewModel.onEvent(BrowserUiEvent.DismissExternalAppPrompt(ExternalAppDismissReason.EXPLICIT))
            assertThat(viewModel.uiState.value.externalAppPrompt).isNull()

            // Later the user taps the same link in Telegram again.
            viewModel.onEvent(BrowserUiEvent.OpenNewTab(redditUrl))
            advanceUntilIdle()
            val reopenedTabId = viewModel.uiState.value.activeTabId!!

            client.onPageStarted(webView, redditUrl, null)

            // The notification must be offered again for a new external navigation.
            assertThat(viewModel.uiState.value.externalAppPrompt).isNotNull()
            assertThat(reopenedTabId).isEqualTo(tabId)
        }

    // ---------------------------------------------------------------------
    // Bug 2: a website's own "Open in app" control must open the app.
    // ---------------------------------------------------------------------

    @Test
    fun timedOutHint_doesNotBlockUserInitiatedHandoffFromSiteButton() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://example.com"))
            advanceUntilIdle()
            val tabId = viewModel.uiState.value.activeTabId!!
            val webView = WebView(context)
            val client = createClient(viewModel, tabId)

            // t.me-style page: a scripted, gesture-less attempt offers Telegram...
            client.handleExternalNavigation(
                webView,
                "tg://resolve?domain=durov",
                userInitiated = false,
            )
            assertThat(viewModel.uiState.value.externalAppPrompt).isNotNull()

            // ...the hint times out while the user does nothing...
            viewModel.onEvent(
                BrowserUiEvent.DismissExternalAppPrompt(ExternalAppDismissReason.TIMEOUT)
            )
            assertThat(viewModel.uiState.value.externalAppPrompt).isNull()

            // ...another passive attempt stays suppressed (no nagging)...
            client.handleExternalNavigation(
                webView,
                "tg://resolve?domain=durov",
                userInitiated = false,
            )
            assertThat(viewModel.uiState.value.externalAppPrompt).isNull()

            // ...but a deliberate tap on the site's own "Open in app" button works.
            client.handleExternalNavigation(
                webView,
                "tg://resolve?domain=durov",
                userInitiated = true,
            )
            assertThat(viewModel.uiState.value.externalAppPrompt).isNotNull()
            assertThat(viewModel.uiState.value.externalAppPrompt?.appPackage)
                .isEqualTo("org.telegram.messenger")
        }

    @Test
    fun explicitDismiss_suppressesSubsequentUserInitiatedHandoffOnSameTab() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://example.com"))
            advanceUntilIdle()
            val tabId = viewModel.uiState.value.activeTabId!!
            val webView = WebView(context)
            val client = createClient(viewModel, tabId)

            client.handleExternalNavigation(
                webView,
                "tg://resolve?domain=durov",
                userInitiated = true,
            )
            assertThat(viewModel.uiState.value.externalAppPrompt).isNotNull()

            // User explicitly clicked "X" to dismiss the pill
            viewModel.onEvent(
                BrowserUiEvent.DismissExternalAppPrompt(ExternalAppDismissReason.EXPLICIT)
            )
            assertThat(viewModel.uiState.value.externalAppPrompt).isNull()

            // Subsequent user tap must remain suppressed because the user explicitly dismissed it
            client.handleExternalNavigation(
                webView,
                "tg://resolve?domain=durov",
                userInitiated = true,
            )
            assertThat(viewModel.uiState.value.externalAppPrompt).isNull()
        }

    @Test
    fun customSchemeFromSubframe_offersAppInsteadOfDoingNothing() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://example.com"))
            advanceUntilIdle()
            val tabId = viewModel.uiState.value.activeTabId!!
            val webView = WebView(context)
            val client = createClient(viewModel, tabId)

            // Hidden iframe technique widely used by "Open in app" banners.
            val handled = client.shouldOverrideUrlLoading(
                webView,
                customSchemeRequest("tg://resolve?domain=durov", isForMainFrame = false),
            )

            assertThat(handled).isTrue()
            assertThat(viewModel.uiState.value.externalAppPrompt).isNotNull()
            assertThat(viewModel.uiState.value.externalAppPrompt?.appPackage)
                .isEqualTo("org.telegram.messenger")
        }

    @Test
    fun intentUrlWithBrowserFallback_loadsFallbackPage() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(BrowserUiEvent.OpenNewTab("https://example.com"))
            advanceUntilIdle()
            val tabId = viewModel.uiState.value.activeTabId!!
            val webView = WebView(context)
            val client = createClient(viewModel, tabId)

            val fallback = "https://example.com/app-not-installed"
            val intentUrl = "intent://open#Intent;scheme=myapp;S.browser_fallback_url=" +
                Uri.encode(fallback) + ";end"

            client.shouldOverrideUrlLoading(
                webView,
                customSchemeRequest(intentUrl, isForMainFrame = true),
            )

            assertThat(shadowOf(webView).lastLoadedUrl).isEqualTo(fallback)
        }
}
