package com.jestk.browser.testing

import android.content.Context
import android.os.Looper
import android.view.View
import coil.Coil
import coil.EventListener
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Makes Coil loads deterministic in Robolectric preview tests. Coil decodes on background
 * dispatchers, so a capture taken right after composition can land before or after an
 * `AsyncImage` finishes (PRO-144). [install] swaps the singleton loader for one that counts
 * in-flight requests and serves http(s) images from [FakeFaviconFetcher] (no network); [awaitIdle]
 * pumps the main looper until none is left.
 */
class CoilIdleTracker private constructor() {
    private val started = AtomicInteger(0)
    private val finished = AtomicInteger(0)

    /** Data of the requests in flight (one entry per request), listed in the timeout message. */
    private val pending = CopyOnWriteArrayList<String>()

    private val listener =
        object : EventListener {
            override fun onStart(request: ImageRequest) {
                started.incrementAndGet()
                pending.add(request.data.toString())
            }

            override fun onCancel(request: ImageRequest) {
                finished.incrementAndGet()
                pending.remove(request.data.toString())
            }

            override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                finished.incrementAndGet()
                pending.remove(request.data.toString())
            }

            override fun onError(request: ImageRequest, result: ErrorResult) {
                finished.incrementAndGet()
                pending.remove(request.data.toString())
            }
        }

    /**
     * Pumps the main looper until every started request has finished, then lets Compose redraw.
     * A request that never finishes usually belongs to an `AsyncImage` laid out with a zero-size
     * constraint (a preview narrower than its content): Coil waits for a size forever. The failure
     * message lists the pending request data.
     */
    fun awaitIdle(timeoutMs: Long = 10_000, failOnTimeout: Boolean = true) {
        val looper = shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        do {
            looper.idleFor(Duration.ofMillis(50))
            if (started.get() == finished.get()) break
            Thread.sleep(10)
        } while (System.nanoTime() < deadline)
        check(!failOnTimeout || started.get() == finished.get()) {
            "Coil requests still in flight after ${timeoutMs}ms: started=${started.get()} finished=${finished.get()}; pending=${pending.toList()}"
        }
        looper.idleFor(Duration.ofMillis(200))
    }

    /**
     * [awaitIdle], then measures and lays [view] out again so the loaded images are in the next draw.
     * Strict: [FakeFaviconFetcher] serves every http(s) request locally, so a request still in flight
     * after the timeout is a real hang and fails the test.
     */
    fun awaitIdle(view: View, width: Int, height: Int) {
        awaitIdle()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    companion object {
        fun install(context: Context): CoilIdleTracker {
            val tracker = CoilIdleTracker()
            Coil.setImageLoader(
                offlineImageLoaderBuilder(context)
                    .eventListener(tracker.listener)
                    .build(),
            )
            return tracker
        }

        fun uninstall() {
            Coil.reset()
        }
    }
}
