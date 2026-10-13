package com.jestk.browser.engine

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import com.jestk.browser.util.findActivity
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.repository.DownloadRepository
import com.jestk.browser.engine.download.DownloadFileNameResolver
import com.jestk.browser.engine.download.HoriDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

class WebViewPool(
    private val applicationContext: Context,
    var downloadRepository: DownloadRepository? = null,
    var downloadManager: HoriDownloadManager? = null
) {

    companion object {
        const val MAX_CONCURRENT_WEBVIEWS = 5
    }

    var onDownloadStarted: ((DownloadItem) -> Unit)? = null
    var isAdBlockerEnabled: (() -> Boolean)? = null
    var onWebViewCreated: ((tabId: String, webView: WebView) -> Unit)? = null

    class AdBlockBridge(
        private val isEnabled: () -> Boolean
    ) {
        @JavascriptInterface
        fun isEnabled(): Boolean = isEnabled()
    }

    class BlobDownloadBridge(
        private val onBlobReceived: (dataUrl: String, contentDisposition: String?, mimeType: String?) -> Unit
    ) {
        @JavascriptInterface
        fun onBlobDownloaded(dataUrl: String, contentDisposition: String?, mimeType: String?) {
            onBlobReceived(dataUrl, contentDisposition, mimeType)
        }
    }

    private val pool = ConcurrentHashMap<String, WebView>()
    private val lruOrder = ConcurrentLinkedDeque<String>()
    private val mediaPlayingTabs = ConcurrentHashMap.newKeySet<String>()
    private var activeTabId: String? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentIsDark: Boolean? = null

    private fun touchLru(tabId: String) {
        lruOrder.remove(tabId)
        lruOrder.addFirst(tabId)
    }

    private fun trimPool(keepTabId: String) {
        while (pool.size > MAX_CONCURRENT_WEBVIEWS) {
            val iterator = lruOrder.descendingIterator()
            var evictedId: String? = null
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (candidate != keepTabId && !mediaPlayingTabs.contains(candidate)) {
                    evictedId = candidate
                    break
                }
            }
            if (evictedId == null) {
                val fallbackIterator = lruOrder.descendingIterator()
                while (fallbackIterator.hasNext()) {
                    val candidate = fallbackIterator.next()
                    if (candidate != keepTabId) {
                        evictedId = candidate
                        break
                    }
                }
            }
            if (evictedId != null) {
                removeWebView(evictedId)
            } else {
                break
            }
        }
    }

    fun setMediaPlaying(tabId: String, isPlaying: Boolean) {
        if (isPlaying) {
            mediaPlayingTabs.add(tabId)
            pool[tabId]?.let { wv ->
                try { wv.onResume() } catch (t: Throwable) {
                    Log.w("HoriEngine", "Failed to resume WebView for tab $tabId", t)
                }
            }
        } else {
            mediaPlayingTabs.remove(tabId)
            if (tabId != activeTabId) {
                pool[tabId]?.let { wv ->
                    try { wv.onPause() } catch (t: Throwable) {
                        Log.w("HoriEngine", "Failed to pause WebView for tab $tabId", t)
                    }
                }
            }
        }
    }

    fun onTabActivated(tabId: String, isMediaPlaying: Boolean = false) {
        activeTabId = tabId
        touchLru(tabId)
        if (isMediaPlaying) {
            mediaPlayingTabs.add(tabId)
        }
        pool.forEach { (id, webView) ->
            if (id == tabId) {
                try {
                    webView.onResume()
                } catch (t: Throwable) {
                    Log.w("HoriEngine", "Failed to resume WebView for active tab $tabId", t)
                }
            } else {
                val isPlaying = mediaPlayingTabs.contains(id)
                if (!isPlaying) {
                    try {
                        webView.onPause()
                    } catch (t: Throwable) {
                        Log.w("HoriEngine", "Failed to pause WebView for background tab $id", t)
                    }
                }
            }
        }
    }


    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(tabId: String, context: Context? = null): WebView {
        touchLru(tabId)
        val activity = context?.findActivity()
        val isActivityDestroyed = activity != null && (activity.isDestroyed || activity.isFinishing)
        val targetContext = if (isActivityDestroyed) {
            applicationContext
        } else {
            activity ?: context ?: applicationContext
        }

        val existing = pool[tabId]
        if (existing != null) {
            try {
                val wrapper = existing.context as? MutableContextWrapper
                if (wrapper != null && wrapper.baseContext != targetContext) {
                    wrapper.baseContext = targetContext
                }
                return existing
            } catch (t: Throwable) {
                Log.w("HoriEngine", "Failed to reuse existing WebView for tab $tabId, recreating", t)
                removeWebView(tabId)
            }
        }

        // Initialize with MutableContextWrapper using Activity context so Chromium can attach to Window
        // and display the soft keyboard when input fields are focused.
        val contextWrapper = MutableContextWrapper(targetContext)
        try {
            val webView = NestedWebView(contextWrapper).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    builtInZoomControls = true
                    displayZoomControls = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    databaseEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    setSupportZoom(true)
                }
                try {
                    val blobBridge = BlobDownloadBridge { dataUrl, contentDisposition, mimeType ->
                        coroutineScope.launch(Dispatchers.Main) {
                            handleDataUrlDownload(dataUrl, contentDisposition, mimeType, this@apply.context)
                        }
                    }
                    addJavascriptInterface(blobBridge, "JestkBlobBridge")
                } catch (t: Throwable) {
                    Log.w("HoriEngine", "Failed to add BlobDownloadBridge interface", t)
                }
                setupDownloadListener(this)
                setupAdBlocker(this)
            }

            currentIsDark?.let { applyThemeMode(webView, it) }

            pool[tabId] = webView
            trimPool(tabId)
            onWebViewCreated?.invoke(tabId, webView)
            return webView
        } catch (t: Throwable) {
            Log.w("HoriEngine", "Failed to initialize WebView with Activity context, falling back to Application context", t)
            contextWrapper.baseContext = applicationContext
            val fallbackWebView = NestedWebView(contextWrapper).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    builtInZoomControls = true
                    displayZoomControls = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    databaseEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    setSupportZoom(true)
                }
                try {
                    val blobBridge = BlobDownloadBridge { dataUrl, contentDisposition, mimeType ->
                        coroutineScope.launch(Dispatchers.Main) {
                            handleDataUrlDownload(dataUrl, contentDisposition, mimeType, this@apply.context)
                        }
                    }
                    addJavascriptInterface(blobBridge, "JestkBlobBridge")
                } catch (t2: Throwable) {
                    Log.w("HoriEngine", "Failed to add BlobDownloadBridge to fallback WebView", t2)
                }
                setupDownloadListener(this)
                setupAdBlocker(this)
            }
            currentIsDark?.let { applyThemeMode(fallbackWebView, it) }
            pool[tabId] = fallbackWebView
            trimPool(tabId)
            onWebViewCreated?.invoke(tabId, fallbackWebView)
            return fallbackWebView
        }
    }

    private fun setupAdBlocker(webView: WebView) {
        try {
            val bridge = AdBlockBridge { isAdBlockerEnabled?.invoke() ?: true }
            webView.addJavascriptInterface(bridge, "HoriAdBlockBridge")
        } catch (t: Throwable) {
            Log.w("HoriEngine", "Failed to add HoriAdBlockBridge interface", t)
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    AdBlockerScript.buildDocumentStartScript(),
                    setOf("*")
                )
            } catch (t: Throwable) {
                Log.w("HoriEngine", "Failed to add document start script for ad blocking", t)
            }
        }
    }

    private fun setupDownloadListener(webView: WebView) {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            handleDownload(url, userAgent, contentDisposition, mimeType, contentLength, webView.context, webView)
        }
    }

    fun handleDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        context: Context,
        webView: WebView? = null
    ) {
        val targetContext = context.findActivity() ?: applicationContext

        // Check for data: URI scheme
        if (url.startsWith("data:", ignoreCase = true)) {
            handleDataUrlDownload(url, contentDisposition, mimeType, targetContext)
            return
        }

        // Check for blob: URI scheme
        if (url.startsWith("blob:", ignoreCase = true)) {
            handleBlobDownload(url, contentDisposition, mimeType, targetContext, webView)
            return
        }

        try {
            val fileName = DownloadFileNameResolver.resolveFileName(url, contentDisposition, mimeType)
            val downloadMgr = downloadManager ?: downloadRepository?.let {
                HoriDownloadManager.getInstance(applicationContext, it)
            }
            val refererUrl = try { webView?.url } catch (_: Throwable) { null }

            Toast.makeText(targetContext, "Downloading $fileName...", Toast.LENGTH_SHORT).show()

            if (downloadMgr != null) {
                coroutineScope.launch {
                    try {
                        val downloadId = downloadMgr.enqueueDownload(
                            url = url,
                            userAgent = userAgent,
                            contentDisposition = contentDisposition,
                            mimeType = mimeType,
                            contentLength = contentLength,
                            referer = refererUrl,
                            customFileName = fileName
                        )
                        val item = downloadRepository?.getDownloadById(downloadId)
                        if (item != null) {
                            withContext(Dispatchers.Main) {
                                onDownloadStarted?.invoke(item)
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e("HoriEngine", "Failed to enqueue download", t)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("HoriEngine", "Download failed for $url", e)
            val targetContext = context.findActivity() ?: applicationContext
            try {
                Toast.makeText(targetContext, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Log.w("HoriEngine", "Failed to show download error toast", t)
            }
        }
    }

    private fun handleDataUrlDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        context: Context
    ) {
        val targetContext = context.findActivity() ?: applicationContext
        val downloadMgr = downloadManager ?: downloadRepository?.let {
            HoriDownloadManager.getInstance(applicationContext, it)
        }

        if (downloadMgr != null) {
            coroutineScope.launch {
                try {
                    val downloadId = downloadMgr.handleDataUrl(url, contentDisposition, mimeType)
                    val item = downloadRepository?.getDownloadById(downloadId)
                    if (item != null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(targetContext, "Downloading ${item.fileName}...", Toast.LENGTH_SHORT).show()
                            onDownloadStarted?.invoke(item)
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("HoriEngine", "Data URL download failed", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(targetContext, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun handleBlobDownload(
        blobUrl: String,
        contentDisposition: String?,
        mimeType: String?,
        context: Context,
        webView: WebView?
    ) {
        val targetContext = context.findActivity() ?: applicationContext
        val targetWebView = webView ?: pool.values.firstOrNull()
        if (targetWebView == null) {
            Toast.makeText(targetContext, "Download failed: Cannot resolve blob data", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(targetContext, "Preparing download...", Toast.LENGTH_SHORT).show()

        val safeDisposition = contentDisposition?.replace("\\", "\\\\")?.replace("'", "\\'") ?: ""
        val safeMime = mimeType?.replace("\\", "\\\\")?.replace("'", "\\'") ?: ""
        val safeUrl = blobUrl.replace("\\", "\\\\")?.replace("'", "\\'") ?: ""

        val js = """
            (function() {
                try {
                    var xhr = new XMLHttpRequest();
                    xhr.open('GET', '$safeUrl', true);
                    xhr.responseType = 'blob';
                    xhr.onload = function() {
                        if (this.status === 200 || this.status === 0) {
                            var reader = new FileReader();
                            reader.onloadend = function() {
                                if (window.JestkBlobBridge) {
                                    window.JestkBlobBridge.onBlobDownloaded(reader.result, '$safeDisposition', '$safeMime');
                                }
                            };
                            reader.readAsDataURL(this.response);
                        }
                    };
                    xhr.onerror = function() {
                        fetch('$safeUrl')
                            .then(function(r) { return r.blob(); })
                            .then(function(b) {
                                var reader = new FileReader();
                                reader.onloadend = function() {
                                    if (window.JestkBlobBridge) {
                                        window.JestkBlobBridge.onBlobDownloaded(reader.result, '$safeDisposition', '$safeMime');
                                    }
                                };
                                reader.readAsDataURL(b);
                            })
                            .catch(function(err) {});
                    };
                    xhr.send();
                } catch(e) {}
            })();
        """.trimIndent()

        try {
            targetWebView.post {
                targetWebView.evaluateJavascript(js, null)
            }
        } catch (t: Throwable) {
            Log.e("HoriEngine", "Failed to evaluate blob download script", t)
            Toast.makeText(targetContext, "Download failed: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun applyThemeMode(webView: WebView, isDark: Boolean) {
        val settings = webView.settings
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDark)
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(
                    settings,
                    WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
                )
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(
                    settings,
                    if (isDark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                )
            }

            injectColorSchemeScript(webView, isDark)
        } catch (t: Throwable) {
            Log.w("HoriEngine", "Failed to apply theme mode", t)
        }
    }

    fun injectColorSchemeScript(webView: WebView, isDark: Boolean) {
        val js = buildColorSchemeScript(isDark)
        try {
            webView.evaluateJavascript(js, null)
        } catch (t: Throwable) {
            Log.w("HoriEngine", "Failed to evaluate color scheme script", t)
        }
    }

    fun updateThemeMode(isDark: Boolean, context: Context? = null) {
        currentIsDark = isDark
        pool.values.forEach { webView ->
            applyThemeMode(webView, isDark)
        }
    }

    fun detachFromParent(tabId: String) {
        pool[tabId]?.let { detachFromParent(it) }
    }

    fun detachFromParent(webView: WebView) {
        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
        } catch (t: Throwable) {
            Log.w("HoriEngine", "Failed to detach WebView from parent", t)
        }
    }

    fun getWebView(tabId: String): WebView? = pool[tabId]

    fun removeWebView(tabId: String) {
        lruOrder.remove(tabId)
        mediaPlayingTabs.remove(tabId)
        if (activeTabId == tabId) {
            activeTabId = null
        }
        val webView = pool.remove(tabId) ?: return
        detachFromParent(webView)
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            (webView as? NestedWebView)?.loadedUrl = null
            webView.clearHistory()
            (webView as? ViewGroup)?.removeAllViews()
            webView.webChromeClient = null
            webView.webViewClient = android.webkit.WebViewClient()
            (webView.context as? MutableContextWrapper)?.baseContext = applicationContext
            webView.destroy()
        } catch (t: Throwable) {
            Log.w("HoriEngine", "Failed to destroy WebView for tab $tabId", t)
            try {
                (webView.context as? MutableContextWrapper)?.baseContext = applicationContext
            } catch (t2: Throwable) {
                Log.w("HoriEngine", "Failed to reset baseContext for tab $tabId", t2)
            }
        }
    }

    fun retainTabs(activeTabIds: Set<String>) {
        val toRemove = pool.keys().toList().filter { it !in activeTabIds }
        toRemove.forEach { removeWebView(it) }
    }

    fun clearAll() {
        lruOrder.clear()
        mediaPlayingTabs.clear()
        activeTabId = null
        val keys = pool.keys().toList()
        keys.forEach { removeWebView(it) }
        pool.clear()
    }

    fun size(): Int = pool.size
}
