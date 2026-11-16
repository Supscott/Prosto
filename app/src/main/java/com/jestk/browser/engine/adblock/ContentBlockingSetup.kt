package com.jestk.browser.engine.adblock

import android.content.pm.ActivityInfo
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jestk.browser.engine.AdBlockerScript
import com.jestk.browser.engine.JestkWebChromeClient
import com.jestk.browser.engine.NestedWebView
import com.jestk.browser.util.YouTubeUrlHelper
import com.jestk.browser.util.findActivity
import java.security.SecureRandom

/** What the page bridge needs from the app; read lazily on every call. */
class ContentBlockingHooks(
    val isAdsEnabled: () -> Boolean,
    val isTrackersEnabled: () -> Boolean,
    val isCleanPlayerEnabled: () -> Boolean,
    val onCleanPlayerRequested: ((url: String) -> Boolean)?,
)

/**
 * Registers the document-start bootstrap and the hidden [ContentBlockingBridge] on a WebView.
 * The bridge global gets a random per-process name and the bootstrap deletes it before page
 * scripts run, so nothing about Prosto shows up in `Object.getOwnPropertyNames(window)`.
 */
object ContentBlockingSetup {
    private const val TAG = "ContentBlockingSetup"
    private const val NAME_LENGTH = 14

    /** Letters only, not starting with an underscore; generated once per process. */
    internal val bridgeName: String by lazy { randomName() }

    /**
     * Name of the blob download bridge (registered by WebViewPool). The bootstrap captures and
     * deletes it like [bridgeName], and the blob download script reaches it through a window event
     * of this type, which no page listens for because it cannot know the name.
     */
    internal val blobBridgeName: String by lazy {
        generateSequence { randomName() }.first { it != bridgeName }
    }

    private fun randomName(): String {
        val letters = ('a'..'z') + ('A'..'Z')
        val random = SecureRandom()
        return String(CharArray(NAME_LENGTH) { letters[random.nextInt(letters.size)] })
    }

    /** Without document-start scripts there is no cosmetic filtering: network blocking only. */
    fun isCosmeticSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    fun install(webView: WebView, hooks: ContentBlockingHooks) {
        if (!isCosmeticSupported()) return
        try {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                AdBlockerScript.buildBootstrapScript(webView.context, bridgeName, blobBridgeName),
                setOf("*"),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to add the content bootstrap", t)
            return
        }
        try {
            webView.addJavascriptInterface(createBridge(webView, hooks), bridgeName)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to add the content bridge", t)
        }
    }

    internal fun createBridge(webView: WebView, hooks: ContentBlockingHooks) = ContentBlockingBridge(
        isEnabledProvider = hooks.isAdsEnabled,
        onCleanPlayerRequested = { url ->
            // Only YouTube pages may open the clean player, and only for a video URL.
            isYouTubeHost((webView as? NestedWebView)?.loadedUrl) &&
                YouTubeUrlHelper.extractVideoId(url) != null &&
                (hooks.onCleanPlayerRequested?.invoke(url) ?: false)
        },
        isCleanPlayerEnabledProvider = hooks.isCleanPlayerEnabled,
        onOrientationLockRequested = { orientation ->
            val act = webView.context.findActivity()
            act?.runOnUiThread {
                // A page may rotate the screen only while it shows fullscreen content.
                val isFullscreen = (webView.webChromeClient as? JestkWebChromeClient)?.isCustomViewShowing() == true
                if (isFullscreen) act.requestedOrientation = orientationFor(orientation)
            }
        },
        onOrientationUnlockRequested = {
            val act = webView.context.findActivity()
            act?.runOnUiThread { act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        },
        isTrackersEnabledProvider = hooks.isTrackersEnabled,
        isSiteAllowlisted = { frameUrl -> isSiteAllowlisted(webView as? NestedWebView, frameUrl) },
    )

    /** Blocking is off when the tab's page or the asking frame is on the allowlist for the tab's workspace. */
    internal fun isSiteAllowlisted(
        webView: NestedWebView?,
        frameUrl: String,
        allowlist: SiteAllowlist = SiteAllowlist.shared,
    ): Boolean {
        val workspaceId = webView?.workspaceId()
        return allowlist.isAllowlisted(webView?.loadedUrl, workspaceId) || allowlist.isAllowlisted(frameUrl, workspaceId)
    }

    private fun orientationFor(orientation: String?): Int = when (orientation) {
        "portrait", "portrait-primary" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        "portrait-secondary" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        "landscape-secondary" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun isYouTubeHost(url: String?): Boolean {
        val host = url?.let { UrlParser.parseUrl(it)?.host }?.lowercase() ?: return false
        return host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be"
    }
}
