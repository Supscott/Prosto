package com.jestk.browser.engine

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.engine.adblock.BlockPage
import com.jestk.browser.engine.adblock.BlockPageAction
import com.jestk.browser.engine.adblock.BlockPageActions
import com.jestk.browser.engine.adblock.EngineSet
import com.jestk.browser.engine.adblock.FakeContentMatcher
import com.jestk.browser.engine.adblock.MatchResult
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** shouldInterceptRequest delegates to the network policy; the rule details live in NetworkBlockPolicyTest. */
@RunWith(RobolectricTestRunner::class)
class JestkWebViewClientNetworkTest {
    private val block = MatchResult.BLOCK or MatchResult.MATCHED
    private val gif = "data:image/gif;base64,R0lGODlhAQABAAAAACw="

    private val ads = FakeContentMatcher(
        blocked = mapOf(
            "ads.example/banner.js" to block,
            "ads.example/pixel.gif" to (block or MatchResult.REDIRECT),
            "doc-blocked.example" to block,
        ),
        redirects = mapOf("ads.example/pixel.gif" to gif),
    )
    private val privacy = FakeContentMatcher(blocked = mapOf("tracker.example/t.js" to block))

    private val blockedResources = mutableListOf<Pair<String, Boolean>>()
    private val blockPageActions = mutableListOf<Triple<String, BlockPageAction, String>>()
    private var allowedHost: String? = null

    @Before
    fun setUp() {
        AdBlockerEngine.engineStore.swap(EngineSet(ads, privacy, rulesCount = 2))
    }

    @After
    fun tearDown() {
        AdBlockerEngine.engineStore.swap(null)
    }

    private fun client(adsOn: Boolean = true, trackersOn: Boolean = true) = JestkWebViewClient(
        tabId = "tab_1",
        isAdBlockerEnabled = { adsOn },
        onPageStarted = { _, _ -> },
        onPageFinished = { _, _, _ -> },
        onNavigationStateChanged = { _, _, _ -> },
        onSnapshotReady = { _, _ -> },
        onBlockedResource = { url, tracker -> blockedResources += url to tracker },
        isTrackingProtectionEnabled = { trackersOn },
        isDocumentAllowed = { url -> allowedHost?.let { url.contains(it) } == true },
        onBlockPageAction = { id, action, url -> blockPageActions += Triple(id, action, url) },
    )

    private fun request(url: String, mainFrame: Boolean = false) = object : android.webkit.WebResourceRequest {
        override fun getUrl(): android.net.Uri = android.net.Uri.parse(url)
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    @Test
    fun block_returnsAResponseAndReportsTheResource() {
        val response = client().shouldInterceptRequest(null, request("https://ads.example/banner.js"))

        assertThat(response).isNotNull()
        assertThat(blockedResources).containsExactly("https://ads.example/banner.js" to false)
    }

    @Test
    fun trackerBlock_isReportedAsTracker() {
        client().shouldInterceptRequest(null, request("https://tracker.example/t.js"))

        assertThat(blockedResources).containsExactly("https://tracker.example/t.js" to true)
    }

    @Test
    fun redirect_returnsTheRedirectResource() {
        val response = client().shouldInterceptRequest(null, request("https://ads.example/pixel.gif"))

        assertThat(response!!.mimeType).isEqualTo("image/gif")
    }

    @Test
    fun allow_returnsNullAndReportsNothing() {
        val response = client().shouldInterceptRequest(null, request("https://news.example/app.js"))

        assertThat(response).isNull()
        assertThat(blockedResources).isEmpty()
    }

    @Test
    fun disabledToggles_allowEverything() {
        val c = client(adsOn = false, trackersOn = false)

        assertThat(c.shouldInterceptRequest(null, request("https://ads.example/banner.js"))).isNull()
        assertThat(c.shouldInterceptRequest(null, request("https://tracker.example/t.js"))).isNull()
    }

    @Test
    fun mainFrameDocumentMatch_servesTheLocalBlockPageWithItsButtons() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = WebView(context)

        val response = client().shouldInterceptRequest(view, request("https://doc-blocked.example/x", mainFrame = true))

        assertThat(response!!.mimeType).isEqualTo("text/html")
        val html = response.data.readBytes().toString(Charsets.UTF_8)
        assertThat(html).contains(context.getString(com.jestk.browser.R.string.blocked_document_title))
        assertThat(html).doesNotContain("<script")
        assertThat(html).contains(BlockPageActions.url(BlockPageAction.PROCEED))
        assertThat(html).contains(BlockPageActions.url(BlockPageAction.GO_BACK))
        assertThat(html).contains(BlockPage.TITLE_MARKER)
        assertThat(blockedResources).isEmpty()
    }

    @Test
    fun mainFrameWithoutMatch_loadsNormally() {
        val response = client().shouldInterceptRequest(null, request("https://news.example/", mainFrame = true))

        assertThat(response).isNull()
    }

    @Test
    fun mainFrame_proceedAnywayHostLoadsNormally() {
        allowedHost = "doc-blocked.example"

        val response = client().shouldInterceptRequest(null, request("https://doc-blocked.example/x", mainFrame = true))

        assertThat(response).isNull()
    }

    private fun blockPageView(title: String = "Page blocked${BlockPage.TITLE_MARKER}", url: String = "https://doc-blocked.example/x"): WebView {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return object : WebView(context) {
            override fun getTitle() = title
            override fun getUrl() = url
        }
    }

    @Test
    fun blockPageButtons_reportTheActionAndNeverLoad() {
        val c = client()
        val view = blockPageView()

        val proceed = c.shouldOverrideUrlLoading(view, request(BlockPageActions.url(BlockPageAction.PROCEED), mainFrame = true))
        val back = c.shouldOverrideUrlLoading(view, request(BlockPageActions.url(BlockPageAction.GO_BACK), mainFrame = true))

        assertThat(proceed).isTrue()
        assertThat(back).isTrue()
        assertThat(blockPageActions).containsExactly(
            Triple("tab_1", BlockPageAction.PROCEED, "https://doc-blocked.example/x"),
            Triple("tab_1", BlockPageAction.GO_BACK, "https://doc-blocked.example/x"),
        ).inOrder()
    }

    @Test
    fun forgedBlockPageAction_doesNothing() {
        val c = client()

        // Wrong token on a block page, and a right token while no block page is showing.
        assertThat(c.shouldOverrideUrlLoading(blockPageView(), request("prosto-block://proceed/forged", mainFrame = true))).isTrue()
        val normalPage = blockPageView(title = "News", url = "https://news.example/")
        assertThat(c.shouldOverrideUrlLoading(normalPage, request(BlockPageActions.url(BlockPageAction.PROCEED), mainFrame = true))).isTrue()
        assertThat(c.shouldOverrideUrlLoading(null, request(BlockPageActions.url(BlockPageAction.PROCEED), mainFrame = true))).isTrue()

        assertThat(blockPageActions).isEmpty()
    }
}
