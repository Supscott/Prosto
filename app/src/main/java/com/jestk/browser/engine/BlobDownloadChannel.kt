package com.jestk.browser.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebMessagePortCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jestk.browser.R

/**
 * Reads a `blob:` download out of the page over a one-off message channel instead of a JS
 * bridge, so no name is added to `window` (review finding SEC-01).
 *
 * Kotlin first runs [buildScript], which waits for a message carrying the nonce, then [open]
 * posts that message with a port; the page answers on the port with the blob as a data URL.
 */
internal object BlobDownloadChannel {
    private const val TAG = "WebViewPool"

    private val FEATURES = listOf(
        WebViewFeature.CREATE_WEB_MESSAGE_CHANNEL,
        WebViewFeature.POST_WEB_MESSAGE,
        WebViewFeature.WEB_MESSAGE_PORT_SET_MESSAGE_CALLBACK,
        WebViewFeature.WEB_MESSAGE_PORT_CLOSE,
    )

    fun isSupported(): Boolean = FEATURES.all(WebViewFeature::isFeatureSupported)

    /**
     * Waits for the message carrying [nonce] and its port, reads [blobUrl] as a data URL and
     * answers on the port (an empty string on failure). It leaves nothing behind on `window`.
     */
    fun buildScript(blobUrl: String, nonce: String): String {
        val jsonUrl = org.json.JSONObject.quote(blobUrl)
        val jsonNonce = org.json.JSONObject.quote(nonce)

        return """
            (function() {
                var onMessage = function(event) {
                    if (event.data !== $jsonNonce || !event.ports || !event.ports.length) return;
                    window.removeEventListener('message', onMessage, true);
                    event.stopImmediatePropagation();
                    var port = event.ports[0];
                    var fail = function() { port.postMessage(''); };
                    var send = function(blob) {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            port.postMessage(typeof reader.result === 'string' ? reader.result : '');
                        };
                        reader.readAsDataURL(blob);
                    };
                    try {
                        var xhr = new XMLHttpRequest();
                        xhr.open('GET', $jsonUrl, true);
                        xhr.responseType = 'blob';
                        xhr.onload = function() {
                            if (this.status === 200 || this.status === 0) send(this.response); else fail();
                        };
                        xhr.onerror = function() {
                            fetch($jsonUrl)
                                .then(function(r) { return r.blob(); })
                                .then(send)
                                .catch(fail);
                        };
                        xhr.send();
                    } catch(e) { fail(); }
                };
                window.addEventListener('message', onMessage, true);
            })();
        """.trimIndent()
    }

    /** Posts the nonce with a port to the main frame; [onDataUrl] gets the page's answer. */
    fun open(
        webView: WebView,
        nonce: String,
        toastContext: Context,
        onDataUrl: (String) -> Unit,
    ) {
        try {
            val ports = WebViewCompat.createWebMessageChannel(webView)
            ports[0].setWebMessageCallback(object : WebMessagePortCompat.WebMessageCallbackCompat() {
                override fun onMessage(port: WebMessagePortCompat, message: WebMessageCompat?) {
                    port.close()
                    val dataUrl = message?.data
                    if (dataUrl.isNullOrEmpty()) {
                        Toast.makeText(toastContext, R.string.downloads_failed, Toast.LENGTH_SHORT).show()
                    } else {
                        onDataUrl(dataUrl)
                    }
                }
            })
            WebViewCompat.postWebMessage(webView, WebMessageCompat(nonce, arrayOf(ports[1])), Uri.parse("*"))
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to open the blob download channel", t)
            Toast.makeText(toastContext, R.string.downloads_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
