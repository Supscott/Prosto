package com.jestk.browser.engine

import com.jestk.browser.engine.adblock.ContentBlockingSetup

/**
 * The script WebViewPool runs in the page to get a `blob:` download (which only a frame of the
 * blob's origin can read) to the hidden blob download bridge as a data URL.
 *
 * With document-start scripts the content bootstrap in every frame holds the bridge: this script
 * posts the request to the main frame and all nested frames of the blob's origin, and the
 * bootstrap there reads the blob, adds the link's `download` name and calls the bridge. Without
 * them the bridge is still a (randomly named) global and the main frame reads the blob itself.
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

        return """
            (function() {
                try {
                    var direct = window[$jsonBridge];
                    if (direct) {
                        var read = function(blob) {
                            var reader = new FileReader();
                            reader.onloadend = function() {
                                direct.onBlobDownloaded($jsonUrl, reader.result, $jsonDisposition, $jsonMime || blob.type || '');
                            };
                            reader.readAsDataURL(blob);
                        };
                        fetch($jsonUrl).then(function(r) { return r.blob(); }).then(read).catch(function(err) {});
                        return;
                    }
                    var origin = new URL($jsonUrl).origin;
                    var request = {};
                    request[$jsonBridge] = [$jsonUrl, $jsonDisposition, $jsonMime];
                    var post = function(w) {
                        try { w.postMessage(request, origin === 'null' ? '*' : origin); } catch (e) {}
                        try { for (var i = 0; i < w.frames.length; i++) post(w.frames[i]); } catch (e) {}
                    };
                    post(window);
                } catch (e) {}
            })();
        """.trimIndent()
    }
}
