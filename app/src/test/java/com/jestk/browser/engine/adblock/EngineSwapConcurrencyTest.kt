package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.engine.AdBlockerEngine
import org.junit.After
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class EngineSwapConcurrencyTest {
    private val pool = Executors.newFixedThreadPool(6)

    @After
    fun tearDown() {
        pool.shutdownNow()
        AdBlockerEngine.resetForTests()
    }

    private fun newSet(blocked: String) =
        EngineSet(FakeContentMatcher(mapOf(blocked to 1)), FakeContentMatcher(mapOf(blocked to 1)), 1)

    @Test
    fun concurrentChecksDuringSwaps_neverThrowAndNeverSeeAnEnginelessWindow() {
        AdBlockerEngine.resetForTests()
        val store = AdBlockerEngine.engineStore
        store.swap(newSet("old.example"))
        val stop = CountDownLatch(1)
        val failures = AtomicInteger()
        val nullWindows = AtomicInteger()
        val checks = AtomicInteger()
        val readers = (1..4).map {
            pool.submit {
                while (stop.count > 0) {
                    try {
                        val set = store.current()
                        if (set == null) nullWindows.incrementAndGet() else set.ads.check("https://old.example/a", "https://s.test/", RequestType.SCRIPT)
                        AdBlockerEngine.decide("https://old.example/a", "https://s.test/", RequestType.SCRIPT, true, true)
                        checks.incrementAndGet()
                    } catch (_: Throwable) {
                        failures.incrementAndGet()
                    }
                }
            }
        }
        val swapper = pool.submit {
            repeat(300) { i -> store.swap(newSet(if (i % 2 == 0) "new.example" else "old.example")) }
            stop.countDown()
        }

        swapper.get(30, TimeUnit.SECONDS)
        readers.forEach { it.get(30, TimeUnit.SECONDS) }

        assertThat(failures.get()).isEqualTo(0)
        assertThat(nullWindows.get()).isEqualTo(0)
        assertThat(checks.get()).isGreaterThan(0)
    }
}
