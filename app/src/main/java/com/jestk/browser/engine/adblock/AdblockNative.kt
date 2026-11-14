package com.jestk.browser.engine.adblock

/** The handle-based native calls, extracted so the lock logic can be tested without the `.so`. */
internal interface NativeApi {
    fun createFromLists(lists: Array<String>): Long
    fun createFromSerialized(bytes: ByteArray): Long
    fun serialize(handle: Long): ByteArray?
    fun useResources(handle: Long, resourcesJson: String): Boolean
    fun checkRequest(handle: Long, url: String, sourceUrl: String, type: Int): Int
    fun checkRequestRedirect(handle: Long, url: String, sourceUrl: String, type: Int): String?
    fun urlCosmeticResources(handle: Long, url: String): String
    fun hiddenClassIdSelectors(
        handle: Long,
        classes: Array<String>,
        ids: Array<String>,
        exceptions: Array<String>,
    ): Array<String>
    fun destroy(handle: Long)
}

/** JNI bindings to libadblock_jni. Symbols: `Java_com_jestk_browser_engine_adblock_AdblockNative_*`. */
internal object AdblockNative : NativeApi {
    external fun nativeVersion(): String
    external override fun createFromLists(lists: Array<String>): Long
    external override fun createFromSerialized(bytes: ByteArray): Long
    external override fun serialize(handle: Long): ByteArray?
    external override fun useResources(handle: Long, resourcesJson: String): Boolean
    external override fun checkRequest(handle: Long, url: String, sourceUrl: String, type: Int): Int
    external override fun checkRequestRedirect(handle: Long, url: String, sourceUrl: String, type: Int): String?
    external override fun urlCosmeticResources(handle: Long, url: String): String
    external override fun hiddenClassIdSelectors(
        handle: Long,
        classes: Array<String>,
        ids: Array<String>,
        exceptions: Array<String>,
    ): Array<String>
    external override fun destroy(handle: Long)

    private val loaded: Boolean by lazy {
        try {
            System.loadLibrary("adblock_jni")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Loads the native library once; returns false when it is unavailable. */
    fun load(): Boolean = loaded
}
