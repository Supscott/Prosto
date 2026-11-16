package com.jestk.browser.engine

import com.jestk.browser.engine.adblock.ContentBlockingSetup

/**
 * The script WebViewPool runs in the page to read a `blob:` download (which only the page can
 * read) and hand it to the hidden blob download bridge as a data URL.
 */
internal object BlobDownloadScript {
    fun build(
        blobUrl: String,
        contentDisposition: String?,
        mimeType: String?
    ): String {
        val jsonUrl = org.json.JSONObject.quote(blobUrl)
        val jsonDisposition = org.json.JSONObject.quote(contentDisposition ?: "")
        val jsonMime = org.json.JSONObject.quote(mimeType ?: "")

        val jsonBridge = org.json.JSONObject.quote(ContentBlockingSetup.blobBridgeName)

        // The bridge is normally captured and deleted by the content bootstrap, so the result goes
        // through a window event named after it; without document-start scripts the bridge is still
        // a (randomly named) global and is called directly.
        return """
            (function() {
                try {
                    var deliver = function(dataUrl) {
                        var direct = window[$jsonBridge];
                        if (direct) {
                            direct.onBlobDownloaded($jsonUrl, dataUrl, $jsonDisposition, $jsonMime);
                        } else {
                            window.dispatchEvent(new CustomEvent($jsonBridge, { detail: [$jsonUrl, dataUrl, $jsonDisposition, $jsonMime] }));
                        }
                    };
                    var read = function(blob) {
                        var reader = new FileReader();
                        reader.onloadend = function() { deliver(reader.result); };
                        reader.readAsDataURL(blob);
                    };
                    var xhr = new XMLHttpRequest();
                    xhr.open('GET', $jsonUrl, true);
                    xhr.responseType = 'blob';
                    xhr.onload = function() {
                        if (this.status === 200 || this.status === 0) read(this.response);
                    };
                    xhr.onerror = function() {
                        fetch($jsonUrl)
                            .then(function(r) { return r.blob(); })
                            .then(read)
                            .catch(function(err) {});
                    };
                    xhr.send();
                } catch(e) {}
            })();
        """.trimIndent()
    }
}
