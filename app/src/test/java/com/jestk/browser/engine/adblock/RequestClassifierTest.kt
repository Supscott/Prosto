package com.jestk.browser.engine.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestClassifierTest {
    private fun type(url: String, main: Boolean = false, vararg h: Pair<String, String>) =
        RequestClassifier.type(url, main, h.toMap())

    private fun source(
        url: String = "https://cdn.example/a.js",
        main: Boolean = false,
        tab: String? = null,
        h: Array<Pair<String, String>> = emptyArray(),
    ) = RequestClassifier.sourceUrl(url, main, h.toMap(), tab)

    @Test fun mainFrameIsDocumentEvenWithHeaders() {
        assertEquals(RequestType.DOCUMENT, type("https://a.com/x.js", true, "Sec-Fetch-Dest" to "script"))
    }

    @Test fun secFetchDestTable() {
        val cases = mapOf(
            "document" to RequestType.SUBDOCUMENT, "iframe" to RequestType.SUBDOCUMENT,
            "frame" to RequestType.SUBDOCUMENT, "embed" to RequestType.SUBDOCUMENT,
            "object" to RequestType.SUBDOCUMENT, "script" to RequestType.SCRIPT,
            "worker" to RequestType.SCRIPT, "sharedworker" to RequestType.SCRIPT,
            "serviceworker" to RequestType.SCRIPT, "style" to RequestType.STYLESHEET,
            "image" to RequestType.IMAGE, "font" to RequestType.FONT, "audio" to RequestType.MEDIA,
            "video" to RequestType.MEDIA, "track" to RequestType.MEDIA,
            "empty" to RequestType.XMLHTTPREQUEST, "report" to RequestType.PING,
        )
        for ((dest, expected) in cases) {
            assertEquals(dest, expected, type("https://a.com/x", false, "Sec-Fetch-Dest" to dest))
        }
    }

    @Test fun secFetchDestWinsOverAcceptAndExtension() {
        assertEquals(
            RequestType.IMAGE,
            type("https://a.com/x.js", false, "Sec-Fetch-Dest" to "image", "Accept" to "text/html"),
        )
    }

    @Test fun unknownSecFetchDestFallsThrough() {
        assertEquals(RequestType.SCRIPT, type("https://a.com/x.js", false, "Sec-Fetch-Dest" to "manifest"))
    }

    @Test fun headerNamesAreCaseInsensitive() {
        assertEquals(RequestType.FONT, type("https://a.com/x", false, "sEC-fETCH-dEST" to "font"))
        assertEquals(RequestType.IMAGE, type("https://a.com/x", false, "ACCEPT" to "image/webp,*/*"))
    }

    @Test fun acceptTable() {
        assertEquals(RequestType.SUBDOCUMENT, type("https://a.com/x", false, "Accept" to "text/html,application/xhtml+xml"))
        assertEquals(RequestType.STYLESHEET, type("https://a.com/x", false, "Accept" to "text/css,*/*;q=0.1"))
        assertEquals(RequestType.IMAGE, type("https://a.com/x", false, "Accept" to "image/avif,image/webp,*/*"))
        assertEquals(RequestType.MEDIA, type("https://a.com/x", false, "Accept" to "video/mp4"))
        assertEquals(RequestType.MEDIA, type("https://a.com/x", false, "Accept" to "audio/ogg"))
        assertEquals(RequestType.XMLHTTPREQUEST, type("https://a.com/x", false, "Accept" to "application/json, text/plain"))
    }

    @Test fun acceptWildcardFallsToExtension() {
        assertEquals(RequestType.SCRIPT, type("https://a.com/x.js", false, "Accept" to "*/*"))
    }

    @Test fun extensionTable() {
        val cases = mapOf(
            "js" to RequestType.SCRIPT, "mjs" to RequestType.SCRIPT, "css" to RequestType.STYLESHEET,
            "png" to RequestType.IMAGE, "jpg" to RequestType.IMAGE, "jpeg" to RequestType.IMAGE,
            "gif" to RequestType.IMAGE, "webp" to RequestType.IMAGE, "avif" to RequestType.IMAGE,
            "svg" to RequestType.IMAGE, "ico" to RequestType.IMAGE, "bmp" to RequestType.IMAGE,
            "woff" to RequestType.FONT, "woff2" to RequestType.FONT, "ttf" to RequestType.FONT,
            "otf" to RequestType.FONT, "eot" to RequestType.FONT, "mp4" to RequestType.MEDIA,
            "webm" to RequestType.MEDIA, "m3u8" to RequestType.MEDIA, "mpd" to RequestType.MEDIA,
            "ts" to RequestType.MEDIA, "mp3" to RequestType.MEDIA, "m4a" to RequestType.MEDIA,
            "ogg" to RequestType.MEDIA, "aac" to RequestType.MEDIA, "html" to RequestType.SUBDOCUMENT,
            "htm" to RequestType.SUBDOCUMENT,
        )
        for ((ext, expected) in cases) assertEquals(ext, expected, type("https://a.com/p/file.$ext"))
    }

    @Test fun extensionIgnoresQueryAndFragmentAndCase() {
        assertEquals(RequestType.SCRIPT, type("https://a.com/a.js?v=1"))
        assertEquals(RequestType.SCRIPT, type("https://a.com/a.js#frag"))
        assertEquals(RequestType.SCRIPT, type("https://a.com/a.js?x=/b.png#c.css"))
        assertEquals(RequestType.IMAGE, type("https://a.com/PIC.PNG"))
        assertEquals(RequestType.OTHER, type("https://a.com/path?file=a.js"))
    }

    @Test fun hostDotsAreNotExtensions() {
        assertEquals(RequestType.OTHER, type("https://cdn.example.js"))
        assertEquals(RequestType.OTHER, type("https://a.com/dir.js/noext"))
    }

    @Test fun websocketScheme() {
        assertEquals(RequestType.WEBSOCKET, type("wss://a.com/socket"))
        assertEquals(RequestType.WEBSOCKET, type("ws://a.com/socket"))
    }

    @Test fun otherForUnknownAndOddUrls() {
        assertEquals(RequestType.OTHER, type("https://a.com/api/data"))
        assertEquals(RequestType.OTHER, type("data:image/png;base64,AAAA"))
        assertEquals(RequestType.OTHER, type("blob:https://a.com/uuid.js"))
        assertEquals(RequestType.OTHER, type("not a url"))
        assertEquals(RequestType.OTHER, type(""))
    }

    @Test fun sourceMainFrameIsUrlItself() {
        assertEquals("https://a.com/", source("https://a.com/", true, "https://tab/", arrayOf("Referer" to "https://r/")))
    }

    @Test fun sourcePrefersReferer() {
        assertEquals(
            "https://ref.com/page",
            source(tab = "https://tab.com/", h = arrayOf("referer" to "https://ref.com/page", "Origin" to "https://o.com")),
        )
    }

    @Test fun sourceFallsBackToOriginWithSlash() {
        assertEquals("https://o.com/", source(tab = "https://tab.com/", h = arrayOf("Origin" to "https://o.com")))
        assertEquals("https://o.com/", source(h = arrayOf("Origin" to "https://o.com/")))
    }

    @Test fun sourceSkipsNonHttpRefererAndOrigin() {
        assertEquals(
            "https://tab.com/",
            source(tab = "https://tab.com/", h = arrayOf("Referer" to "android-app://x", "Origin" to "null")),
        )
        assertEquals("https://tab.com/", source(tab = "https://tab.com/", h = arrayOf("Referer" to "https://")))
    }

    @Test fun sourceFallsBackToTabThenRequestUrl() {
        assertEquals("https://tab.com/", source(tab = "https://tab.com/"))
        assertEquals("https://cdn.example/a.js", source(tab = null))
        assertEquals("https://cdn.example/a.js", source(tab = ""))
    }
}
