package com.jestk.browser.engine.adblock

/** Resource type passed across the JNI boundary; codes are fixed by the JNI contract. */
enum class RequestType(val code: Int) {
    OTHER(0),
    DOCUMENT(1),
    SUBDOCUMENT(2),
    IMAGE(3),
    STYLESHEET(4),
    SCRIPT(5),
    FONT(6),
    MEDIA(7),
    XMLHTTPREQUEST(8),
    WEBSOCKET(9),
    PING(10),
}
