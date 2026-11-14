package com.jestk.browser.engine.adblock

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Owns one native engine pointer. Calls hold the read lock; [close] holds the write lock so the
 * handle is never destroyed under an in-flight call. After close every call returns its safe value.
 */
internal class NativeEngineHandle private constructor(
    private val api: NativeApi,
    private var handle: Long,
) {
    private val lock = ReentrantReadWriteLock()

    val isOpen: Boolean get() = lock.read { handle != 0L }

    fun check(url: String, sourceUrl: String, type: RequestType): MatchResult = lock.read {
        if (handle == 0L) MatchResult.ALLOW else MatchResult(api.checkRequest(handle, url, sourceUrl, type.code))
    }

    fun redirectFor(url: String, sourceUrl: String, type: RequestType): String? = lock.read {
        if (handle == 0L) null else api.checkRequestRedirect(handle, url, sourceUrl, type.code)
    }

    fun cosmeticResources(url: String): CosmeticResources = lock.read {
        if (handle == 0L) CosmeticResources.EMPTY else CosmeticResources.parse(api.urlCosmeticResources(handle, url))
    }

    fun hiddenClassIdSelectors(classes: List<String>, ids: List<String>, exceptions: Set<String>): List<String> =
        lock.read {
            if (handle == 0L) {
                emptyList()
            } else {
                api.hiddenClassIdSelectors(
                    handle,
                    classes.toTypedArray(),
                    ids.toTypedArray(),
                    exceptions.toTypedArray(),
                ).toList()
            }
        }

    fun serialize(): ByteArray? = lock.read { if (handle == 0L) null else api.serialize(handle) }

    /** Idempotent; waits for in-flight calls. */
    fun close() {
        lock.write {
            if (handle != 0L) {
                api.destroy(handle)
                handle = 0L
            }
        }
    }

    companion object {
        fun fromLists(
            lists: List<String>,
            resourcesJson: String?,
            api: NativeApi = AdblockNative,
        ): NativeEngineHandle? = wrap(api, api.createFromLists(lists.toTypedArray()), resourcesJson)

        fun fromSerialized(
            bytes: ByteArray,
            resourcesJson: String?,
            api: NativeApi = AdblockNative,
        ): NativeEngineHandle? = wrap(api, api.createFromSerialized(bytes), resourcesJson)

        private fun wrap(api: NativeApi, handle: Long, resourcesJson: String?): NativeEngineHandle? {
            if (handle == 0L) return null
            if (resourcesJson != null) api.useResources(handle, resourcesJson)
            return NativeEngineHandle(api, handle)
        }
    }
}
