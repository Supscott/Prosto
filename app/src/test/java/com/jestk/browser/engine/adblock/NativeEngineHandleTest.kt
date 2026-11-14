package com.jestk.browser.engine.adblock

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NativeEngineHandleTest {
    private class FakeApi(var created: Long = 7L) : NativeApi {
        val destroyed = AtomicInteger()
        val resources = mutableListOf<String>()
        var enterCheck: CountDownLatch? = null
        var releaseCheck: CountDownLatch? = null

        override fun createFromLists(lists: Array<String>) = created
        override fun createFromSerialized(bytes: ByteArray) = created
        override fun serialize(handle: Long): ByteArray? = byteArrayOf(1, 2)
        override fun useResources(handle: Long, resourcesJson: String): Boolean {
            resources += resourcesJson
            return true
        }
        override fun checkRequest(handle: Long, url: String, sourceUrl: String, type: Int): Int {
            enterCheck?.countDown()
            releaseCheck?.await(5, TimeUnit.SECONDS)
            return 1
        }
        override fun checkRequestRedirect(handle: Long, url: String, sourceUrl: String, type: Int) = "data:x"
        override fun urlCosmeticResources(handle: Long, url: String) = """{"hide":[".a"]}"""
        override fun hiddenClassIdSelectors(
            handle: Long,
            classes: Array<String>,
            ids: Array<String>,
            exceptions: Array<String>,
        ) = arrayOf(".a")
        override fun destroy(handle: Long) {
            destroyed.incrementAndGet()
        }
    }

    @Test fun zeroHandleReturnsNull() {
        assertNull(NativeEngineHandle.fromLists(listOf("a"), null, FakeApi(0L)))
        assertNull(NativeEngineHandle.fromSerialized(byteArrayOf(1), null, FakeApi(0L)))
    }

    @Test fun resourcesAreInstalled() {
        val api = FakeApi()
        NativeEngineHandle.fromLists(listOf("a"), "[]", api)!!
        assertEquals(listOf("[]"), api.resources)
    }

    @Test fun openHandleDelegates() {
        val h = NativeEngineHandle.fromLists(listOf("a"), null, FakeApi())!!
        assertTrue(h.check("u", "s", RequestType.SCRIPT).shouldBlock)
        assertEquals("data:x", h.redirectFor("u", "s", RequestType.SCRIPT))
        assertEquals(listOf(".a"), h.cosmeticResources("u").hideSelectors)
        assertEquals(listOf(".a"), h.hiddenClassIdSelectors(listOf("a"), emptyList(), emptySet()))
        assertEquals(2, h.serialize()!!.size)
    }

    @Test fun callsAfterCloseReturnSafeValues() {
        val h = NativeEngineHandle.fromLists(listOf("a"), null, FakeApi())!!
        h.close()
        assertFalse(h.isOpen)
        assertEquals(MatchResult.ALLOW, h.check("u", "s", RequestType.OTHER))
        assertNull(h.redirectFor("u", "s", RequestType.OTHER))
        assertEquals(CosmeticResources.EMPTY, h.cosmeticResources("u"))
        assertTrue(h.hiddenClassIdSelectors(listOf("a"), emptyList(), emptySet()).isEmpty())
        assertNull(h.serialize())
    }

    @Test fun doubleCloseDestroysOnce() {
        val api = FakeApi()
        val h = NativeEngineHandle.fromLists(listOf("a"), null, api)!!
        h.close()
        h.close()
        assertEquals(1, api.destroyed.get())
    }

    @Test fun closeWaitsForInFlightRead() {
        val api = FakeApi().apply {
            enterCheck = CountDownLatch(1)
            releaseCheck = CountDownLatch(1)
        }
        val h = NativeEngineHandle.fromLists(listOf("a"), null, api)!!
        val reader = Thread { h.check("u", "s", RequestType.SCRIPT) }.also { it.start() }
        assertTrue(api.enterCheck!!.await(5, TimeUnit.SECONDS))
        val closed = AtomicBoolean(false)
        val closer = Thread {
            h.close()
            closed.set(true)
        }.also { it.start() }
        Thread.sleep(150)
        assertFalse("close must wait for the reader", closed.get())
        assertEquals(0, api.destroyed.get())
        api.releaseCheck!!.countDown()
        reader.join(5000)
        closer.join(5000)
        assertTrue(closed.get())
        assertEquals(1, api.destroyed.get())
    }
}
